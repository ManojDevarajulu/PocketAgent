package dev.pocketagent.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.pocketagent.agent.Listener
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's speech recognizer behind [Listener], with the same settings as `ui/VoiceInput`: free-form, the phone's
 * own language, offline preferred. Each [listen] beeps, hears one utterance with a fresh recognizer and destroys it.
 * Every recognizer call runs on the main thread. Errors never escape: they mean null. Nothing heard is logged or kept.
 */
class RecognizerListener(context: Context) : Listener {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** Set when the recognizer says the phone's language cannot be recognised at all. */
    @Volatile private var unsupported = false
    @Volatile private var closed = false

    /** Main thread only: cancels the utterance in progress, if any. */
    private var cancelActive: (() -> Unit)? = null

    override val available: Boolean
        get() = !unsupported && !closed && micAllowed() && try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (e: Exception) {
            false
        }

    override suspend fun listen(): String? {
        if (!available) return null
        return try {
            withContext(Dispatchers.Main) {
                beep()
                withTimeoutOrNull(LISTEN_LIMIT_MS) { hear() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Includes a SecurityException when the mic permission was just revoked.
        }
    }

    override fun stop() {
        main.post { cancelActive?.invoke() }
    }

    /** Stops any utterance in progress. The listener stays unavailable afterwards. */
    fun shutdown() {
        closed = true
        stop()
    }

    /** Main thread. A short start tone, finished before the recognizer opens the mic so it is not heard. */
    private suspend fun beep() {
        val tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
        } catch (e: Exception) {
            return // No tone is not a reason to skip listening.
        }
        try {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS)
            delay(TONE_MS + 50L)
        } finally {
            tone.release()
        }
    }

    /** Main thread. One utterance from a fresh recognizer, which is destroyed on every path. */
    private suspend fun hear(): String? = suspendCancellableCoroutine { cont ->
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (e: Exception) {
            null
        }
        if (recognizer == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        var finished = false // Main thread only; a late callback after cancel or stop does nothing.
        var cancel: () -> Unit = {}
        fun finish(text: String?) {
            if (finished) return
            finished = true
            if (cancelActive === cancel) cancelActive = null
            try {
                recognizer.destroy()
            } catch (e: Exception) {
                // Already released.
            }
            if (cont.isActive) cont.resume(text)
        }
        cancel = {
            if (!finished) {
                try {
                    recognizer.cancel()
                } catch (e: Exception) {
                    // Destroyed next anyway.
                }
                finish(null)
            }
        }
        cancelActive = cancel
        cont.invokeOnCancellation { main.post { cancel() } }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) = finish(first(results))

            override fun onError(error: Int) {
                if (error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE) unsupported = true
                finish(null)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            finish(null)
        }
    }

    private fun micAllowed(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val TONE_MS = 100
        const val TONE_VOLUME = 80
        const val LISTEN_LIMIT_MS = 20_000L

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED and ERROR_LANGUAGE_UNAVAILABLE (API 31; minSdk is 30).
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
