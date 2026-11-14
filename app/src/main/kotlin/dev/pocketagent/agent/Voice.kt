package dev.pocketagent.agent

/** Speaks to the user. Implementations must be safe to call from any thread. */
interface Speaker {
    /** False when there is no working speech engine; the task then stays silent. */
    val available: Boolean

    /** Speaks [text] and returns when it has been spoken in full. Cancelling the call stops the speech. */
    suspend fun say(text: String)

    /** Speaks [text] without waiting, replacing an earlier [sayLatest]. Dropped while a [say] is in progress. */
    fun sayLatest(text: String)

    fun stop()
}

/** Hears one answer from the user. Implementations must be safe to call from any thread. */
interface Listener {
    /** False when there is no speech recognizer or no microphone permission. */
    val available: Boolean

    /** One utterance, or null on silence or error. Cancelling the call stops the recognizer. */
    suspend fun listen(): String?

    fun stop()
}
