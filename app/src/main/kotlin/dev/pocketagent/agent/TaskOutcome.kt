package dev.pocketagent.agent

import dev.pocketagent.llm.TokenUsage

enum class EndReason { DONE, STEP_LIMIT, STUCK, INVALID_CALLS, REFUSED, AUTH_ERROR, LLM_ERROR, BLOCKED_APP, DEVICE_UNAVAILABLE }

data class TaskOutcome(val reason: EndReason, val success: Boolean, val summary: String, val usage: TokenUsage = TokenUsage())
