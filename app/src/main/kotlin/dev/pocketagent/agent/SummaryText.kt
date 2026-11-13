package dev.pocketagent.agent

/** Words for the summary card; the model's own text only ever appears in [body], cut and cleaned. */
object SummaryText {
    private const val MAX_SUMMARY_CHARS = 300

    fun title(state: AgentState.Finished): String {
        val outcome = state.outcome
        return when (outcome.reason) {
            EndReason.DONE -> if (outcome.success) "Done" else "Not finished"
            EndReason.STEP_LIMIT, EndReason.STUCK, EndReason.INVALID_CALLS -> "Not finished"
            EndReason.REFUSED -> "Declined by the model"
            EndReason.AUTH_ERROR -> "Key rejected"
            EndReason.LLM_ERROR -> "Model error"
            EndReason.BLOCKED_APP -> "Blocked app"
            EndReason.DEVICE_UNAVAILABLE -> "Phone unavailable"
        }
    }

    fun stoppedTitle(): String = "Stopped by you"

    fun body(outcome: TaskOutcome): String {
        val summary = outcome.summary.take(MAX_SUMMARY_CHARS).map { if (it.isISOControl()) ' ' else it }.joinToString("")
        val usage = outcome.usage
        val cached = if (usage.cachedInputTokens > 0) ", ${usage.cachedInputTokens} cached" else ""
        val tokens = "Tokens: ${usage.inputTokens} in, ${usage.outputTokens} out$cached"
        val footer = time(outcome.stats)?.let { "$it\n$tokens" } ?: tokens
        return if (summary.isEmpty()) footer else "Agent's summary: $summary\n$footer"
    }

    /** "Time: 34 s, 7 steps, 29 s of it waiting for the model", or null when no model call was made. */
    internal fun time(stats: TaskStats): String? {
        if (stats.steps <= 0) return null
        val steps = if (stats.steps == 1) "1 step" else "${stats.steps} steps"
        return "Time: ${seconds(stats.totalMs)}, $steps, ${seconds(stats.modelMs)} of it waiting for the model"
    }

    /** Whole seconds from 10 s up, one decimal below that. Built by hand so the locale never changes the separator. */
    private fun seconds(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0) + 50) / 100
        return if (tenths >= 100) "${(tenths + 5) / 10} s" else "${tenths / 10}.${tenths % 10} s"
    }
}
