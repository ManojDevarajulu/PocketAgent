package dev.pocketagent.agent

enum class EndReason { DONE, STEP_LIMIT, STUCK, INVALID_CALLS, REFUSED, AUTH_ERROR, LLM_ERROR, BLOCKED_APP }

data class TaskOutcome(val reason: EndReason, val success: Boolean, val summary: String)
