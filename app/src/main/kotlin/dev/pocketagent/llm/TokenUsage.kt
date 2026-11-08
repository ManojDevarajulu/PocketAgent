package dev.pocketagent.llm

/** Tokens used by a session. [cachedInputTokens] is the part of [inputTokens] that was served from a prompt cache. */
data class TokenUsage(val inputTokens: Long = 0, val outputTokens: Long = 0, val cachedInputTokens: Long = 0) {
    operator fun plus(other: TokenUsage) = TokenUsage(
        inputTokens + other.inputTokens,
        outputTokens + other.outputTokens,
        cachedInputTokens + other.cachedInputTokens,
    )
}
