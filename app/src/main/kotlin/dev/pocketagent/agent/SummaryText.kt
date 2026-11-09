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
        return if (summary.isEmpty()) tokens else "Agent's summary: $summary\n$tokens"
    }
}
