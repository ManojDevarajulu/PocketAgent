package dev.pocketagent.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.pocketagent.setup.SpeechErrors

/** Wraps one [SpeechRecognizer]. The phone's own language is used; offline recognition is requested first. Nothing is logged or stored. */
class VoiceInput(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            setListening(false)
            first(results)?.let(onFinal)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let(onPartial)
        }

        override fun onError(error: Int) {
            setListening(false)
            onError(SpeechErrors.messageFor(error))
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Starts listening, or stops and delivers what was heard. */
    fun toggle() {
        if (listening) {
            recognizer?.stopListening()
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        setListening(true)
        r.startListening(intent)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun setListening(value: Boolean) {
        if (listening != value) {
            listening = value
            onListening(value)
        }
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
