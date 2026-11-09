package dev.pocketagent.setup

/** Short messages for the codes of `SpeechRecognizer.ERROR_*`. Pure, so it is tested on the JVM. */
object SpeechErrors {
    fun messageFor(errorCode: Int): String = when (errorCode) {
        6, 7 -> "Nothing was heard. Try again, or type instead."
        1, 2, 4, 11 -> "The speech recogniser needs a connection right now. Type instead."
        8 -> "The speech recogniser is busy. Try again in a moment."
        9 -> "The microphone is not allowed. Turn it on in the setup screen."
        12, 13 -> "Your phone's language is not available for voice yet. Type instead."
        else -> "Could not listen. Type instead."
    }
}
