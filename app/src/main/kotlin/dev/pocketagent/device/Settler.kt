package dev.pocketagent.device

import kotlinx.coroutines.delay

/**
 * Waits for the screen to stop changing. [onEvent] may be called from any thread;
 * [settle] suspends until [quietMs] pass without events, or [maxMs] after the call.
 */
class Settler(
    private val now: () -> Long,
    private val quietMs: Long = 300,
    private val maxMs: Long = 3_000,
) {
    @Volatile
    private var lastEvent = Long.MIN_VALUE

    fun onEvent() {
        lastEvent = now()
    }

    suspend fun settle() {
        val start = now()
        while (true) {
            val t = now()
            val quiet = t - maxOf(lastEvent, start)
            val elapsed = t - start
            if (quiet >= quietMs || elapsed >= maxMs) return
            delay(minOf(quietMs - quiet, maxMs - elapsed))
        }
    }
}
