package dev.pocketagent.agent

/** Spots an agent that keeps acting without the screen changing. Screens are compared as text, never by hash, so two different screens can never be mistaken for one. */
class StuckDetector(private val limit: Int = 3) {
    private var baseline: String? = null
    private var repeats = 0

    /**
     * Call after every action with the serialized screen. The first call sets the baseline. Returns true once
     * the screen has stayed identical to the baseline for [limit] actions in a row. Any change starts a new count.
     */
    fun afterAction(serializedScreen: String): Boolean {
        if (serializedScreen == baseline) {
            repeats++
        } else {
            baseline = serializedScreen
            repeats = 0
        }
        return repeats >= limit
    }

    fun reset() {
        baseline = null
        repeats = 0
    }
}
