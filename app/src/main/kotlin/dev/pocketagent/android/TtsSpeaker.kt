package dev.pocketagent.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.pocketagent.agent.Speaker
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's text-to-speech engine behind [Speaker], in the phone's own language. Speech takes transient, ducking
 * audio focus and gives it back when it ends; losing focus (a call, another app) stops it. Every engine call runs on
 * the main thread. Errors never escape: the speaker goes quiet and, when the engine is unusable, reports
 * [available] false. Spoken text is never logged or kept.
 */
class TtsSpeaker(context: Context) : Speaker {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio: AudioManager? = appContext.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopNow()
        }, main)
        .build()

    /** Completes with true once the engine is ready, or false when it failed, timed out or was shut down. */
    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var usable = false

    /** Main thread only. */
    private var tts: TextToSpeech? = null
    private var current: String? = null
    private var hasFocus = false

    /** Utterances a [say] is waiting on, by id. Completed from the engine's own thread. */
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val ids = AtomicLong()
    private val saying = AtomicInteger()
    private val generation = AtomicInteger()

    override val available: Boolean get() = usable

    /** True once the engine failed, timed out or was shut down; such a speaker never speaks again. */
    val failed: Boolean get() = ready.isCompleted && !usable

    init {
        main.post(::start)
    }

    override suspend fun say(text: String) {
        saying.incrementAndGet()
        val id = "say-${ids.incrementAndGet()}"
        try {
            if (!awaitReady()) return
            // QUEUE_FLUSH: a prompt replaces any caption from sayLatest, never waits behind or overlaps one.
            val done = withContext(Dispatchers.Main) {
                CompletableDeferred<Unit>().also { waiting[id] = it }.takeIf { speakNow(text, id) }
            } ?: return
            // A bound in case the engine dies without calling back; spoken text is capped well below this.
            if (withTimeoutOrNull(SAY_LIMIT_MS) { done.await() } == null) withContext(Dispatchers.Main) { stopIfCurrent(id) }
        } catch (e: CancellationException) {
            main.post { stopIfCurrent(id) }
            throw e
        } catch (e: Exception) {
            // Any engine failure leaves the speaker quiet; the task goes on.
        } finally {
            waiting.remove(id)
            saying.decrementAndGet()
        }
    }

    override fun sayLatest(text: String) {
        if (saying.get() > 0 || ready.isCompleted && !usable) return
        val gen = generation.get()
        scope.launch {
            try {
                if (!awaitReady()) return@launch
                // Checked on the main thread right before speaking, so a say that started meanwhile wins.
                if (saying.get() == 0 && generation.get() == gen) speakNow(text, "latest-${ids.incrementAndGet()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Dropped quietly.
            }
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        main.post(::stopNow)
    }

    /** Releases the engine. The speaker stays unavailable afterwards. */
    fun shutdown() {
        generation.incrementAndGet()
        scope.cancel()
        main.post {
            stopNow()
            fail()
        }
    }

    private fun start() {
        if (ready.isCompleted) return
        // onInit may run inside the constructor when there is no engine, so it only posts; configure runs after.
        tts = try {
            TextToSpeech(appContext) { status -> main.post { configure(status) } }
        } catch (e: Exception) {
            null
        }
        if (tts == null) fail()
    }

    private fun configure(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null || ready.isCompleted) {
            fail()
            return
        }
        try {
            engine.setAudioAttributes(attributes)
            // If the phone's language has no voice, the engine keeps its own default voice rather than going silent.
            engine.setLanguage(Locale.getDefault())
            engine.setOnUtteranceProgressListener(progress)
            usable = true
            ready.complete(true)
        } catch (e: Exception) {
            fail()
        }
    }

    /** Waits up to [INIT_LIMIT_MS] for the engine; a slow or failed start makes the speaker unavailable. */
    private suspend fun awaitReady(): Boolean {
        val ok = withTimeoutOrNull(INIT_LIMIT_MS) { ready.await() }
        if (ok == null) main.post(::fail)
        return ok == true && usable
    }

    /** Main thread. Returns true when the engine accepted [text]. */
    private fun speakNow(text: String, id: String): Boolean {
        val engine = tts ?: return false
        if (!usable) return false
        return try {
            if (!requestFocus()) return false // A call or another app holds audio; stay quiet.
            current = id
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS) {
                true
            } else {
                fail() // The engine is no longer bound.
                false
            }
        } catch (e: Exception) {
            fail()
            false
        }
    }

    /** Main thread. */
    private fun stopNow() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Nothing more to stop.
        }
        current = null
        abandonFocus()
        waiting.values.forEach { it.complete(Unit) }
    }

    /** Main thread. Stops only if [id] is still the utterance playing, so a later one is left alone. */
    private fun stopIfCurrent(id: String) {
        if (current == id) stopNow()
    }

    /** Main thread. Marks the engine unusable and releases it. */
    private fun fail() {
        usable = false
        ready.complete(false)
        val engine = tts
        tts = null
        current = null
        abandonFocus()
        waiting.values.forEach { it.complete(Unit) }
        try {
            engine?.shutdown()
        } catch (e: Exception) {
            // Already gone.
        }
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val manager = audio ?: return true
        hasFocus = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        try {
            audio?.abandonAudioFocusRequest(focus)
        } catch (e: Exception) {
            // Focus is released with the process anyway.
        }
    }

    /** Called on the engine's own thread. */
    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) = finished(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
    }

    private fun finished(id: String?) {
        if (id == null) return
        waiting[id]?.complete(Unit)
        main.post {
            if (current == id) {
                current = null
                abandonFocus()
            }
        }
    }

    private companion object {
        const val INIT_LIMIT_MS = 3_000L
        const val SAY_LIMIT_MS = 60_000L
    }
}
