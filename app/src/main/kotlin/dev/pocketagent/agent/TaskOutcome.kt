package dev.pocketagent.agent

import dev.pocketagent.llm.TokenUsage

enum class EndReason { DONE, STEP_LIMIT, STUCK, INVALID_CALLS, REFUSED, AUTH_ERROR, LLM_ERROR, BLOCKED_APP, DEVICE_UNAVAILABLE }

data class TaskOutcome(
    val reason: EndReason,
    val success: Boolean,
    val summary: String,
    val usage: TokenUsage = TokenUsage(),
    val stats: TaskStats = TaskStats(),
)

/**
 * Where a task's time went: [steps] model calls, [modelMs] of them spent waiting for the model, [totalMs] from the
 * first call to the end. The rest is the phone acting, the screen settling, and the user answering cards.
 */
data class TaskStats(val steps: Int = 0, val modelMs: Long = 0, val totalMs: Long = 0)
