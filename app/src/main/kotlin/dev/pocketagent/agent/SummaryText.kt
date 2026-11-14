package dev.pocketagent.agent

/** Words for the summary card; the model's own text only ever appears in [body], cut and cleaned. */
object SummaryText {
    private const val MAX_SUMMARY_CHARS = 300

    /** Stored in task history instead of the summary when the task read notifications. */
    const val HIDDEN_SUMMARY = "Summary hidden (it used your notifications)"

    /** The summary to store on disk: a task that read notifications never keeps its summary. */
    fun forHistory(outcome: TaskOutcome): String =
        if (outcome.usedNotifications) HIDDEN_SUMMARY else outcome.summary

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

    /** What the card says under the title: the result in plain words, cleaned and cut. Nothing technical (no tokens, steps or timings). */
    fun body(outcome: TaskOutcome): String =
        outcome.summary.take(MAX_SUMMARY_CHARS).map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
}
