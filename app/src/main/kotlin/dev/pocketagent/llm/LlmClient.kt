package dev.pocketagent.llm

import dev.pocketagent.agent.ToolDefinition
import kotlinx.serialization.json.JsonObject

/** Provider-agnostic. A session owns the provider-native, append-only history, so the agent loop never sees it. */
interface LlmClient {
    val supportsImages: Boolean
    fun newSession(systemPrompt: String, tools: List<ToolDefinition>): LlmSession
}

interface LlmSession {
    suspend fun send(turn: Turn): LlmResult

    /** Running totals for this session. */
    val usage: TokenUsage
}

/** The answer to a tool call. [callId] is the id of the call it answers. */
data class ToolResult(val callId: String, val text: String, val isError: Boolean)

/**
 * One message to the model. [text] may be empty when [toolResult] carries the whole message. [systemNote] is
 * trusted text (a bundled playbook), never screen content. [image] is a PNG.
 */
data class Turn(
    val text: String,
    val toolResult: ToolResult? = null,
    val image: ByteArray? = null,
    val systemNote: String? = null,
)

sealed interface LlmResult {
    data class Call(val callId: String, val name: String, val input: JsonObject) : LlmResult
    data class NoCall(val text: String) : LlmResult
    data class Refused(val explanation: String?) : LlmResult

    /** Retries have already been done by the client. */
    data class Failed(val kind: FailureKind, val message: String) : LlmResult
}

enum class FailureKind { AUTH, NETWORK, RATE_LIMIT, SERVER, OTHER }
