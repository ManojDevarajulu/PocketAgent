package dev.pocketagent.llm

import dev.pocketagent.agent.ToolDefinition
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Anthropic Messages API. Each session keeps the provider-native history: the assistant's whole `content` array is
 * stored unchanged (thinking blocks must be replayed byte for byte), and a turn joins the history only after its
 * request succeeded.
 */
class AnthropicClient(private val config: ProviderConfig, private val caller: HttpCaller) : LlmClient {
    override val supportsImages: Boolean = config.supportsImages

    override fun newSession(systemPrompt: String, tools: List<ToolDefinition>): LlmSession =
        Session(systemPrompt, tools)

    private inner class Session(private val systemPrompt: String, private val tools: List<ToolDefinition>) : LlmSession {
        private val history = mutableListOf<JsonObject>()
        private var usageTotal = TokenUsage()

        /** Tool-use ids from the last reply that the agent loop did not answer. */
        private var unanswered: List<String> = emptyList()

        /** Set once a retry without the optional fields succeeded; they are then never sent again. */
        private var optionalFieldsRejected = false

        override val usage: TokenUsage get() = usageTotal

        override suspend fun send(turn: Turn): LlmResult {
            val userMessage = userMessage(turn) ?: return LlmResult.Failed(FailureKind.OTHER, "Nothing to send")
            val messages = history + userMessage

            val withOptional = !optionalFieldsRejected && hasOptionalFields()
            var outcome = post(messages, withOptional)
            if (withOptional && outcome is HttpOutcome.Failure && outcome.status == 400) {
                outcome = post(messages, false)
                if (outcome is HttpOutcome.Success) optionalFieldsRejected = true
            }
            val body = when (outcome) {
                is HttpOutcome.Failure -> return LlmResult.Failed(outcome.kind, outcome.message)
                is HttpOutcome.Success -> outcome.body
            }
            return parseReply(body, userMessage)
        }

        private suspend fun post(messages: List<JsonObject>, withOptional: Boolean): HttpOutcome {
            val headers = buildMap {
                put("x-api-key", config.apiKey)
                put("anthropic-version", "2023-06-01")
                put("content-type", "application/json")
                if (withOptional && fallbacksEnabled()) put("anthropic-beta", FALLBACK_BETA)
            }
            val url = config.baseUrl.trimEnd('/') + "/v1/messages"
            return caller.post(url, headers, requestBody(messages, withOptional).toString())
        }

        private fun hasOptionalFields() = config.effort != null || fallbacksEnabled()

        private fun fallbacksEnabled() = config.host == ANTHROPIC_HOST && config.model in FALLBACK_MODELS

        private fun requestBody(messages: List<JsonObject>, withOptional: Boolean) = buildJsonObject {
            put("model", config.model)
            put("max_tokens", config.maxOutputTokens)
            putJsonArray("system") {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", systemPrompt)
                        put("cache_control", EPHEMERAL)
                    },
                )
            }
            putJsonArray("tools") {
                for (tool in tools) {
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.inputSchema)
                            put("strict", true)
                        },
                    )
                }
            }
            putJsonObject("tool_choice") {
                put("type", "auto")
                put("disable_parallel_tool_use", true)
            }
            put("messages", JsonArray(messages))
            put("cache_control", EPHEMERAL)
            if (withOptional) {
                config.effort?.let { effort -> putJsonObject("output_config") { put("effort", effort) } }
                if (fallbacksEnabled()) put("fallbacks", "default")
            }
        }

        /** Null when the turn has nothing to send (the API rejects empty content). */
        private fun userMessage(turn: Turn): JsonObject? {
            val blocks = buildJsonArray {
                turn.toolResult?.let { r ->
                    add(toolResult(r.callId, r.text, r.isError))
                    for (id in unanswered.filter { it != r.callId }) add(toolResult(id, IGNORED, true))
                }
                if (turn.toolResult == null) for (id in unanswered) add(toolResult(id, IGNORED, true))
                if (turn.image != null && config.supportsImages) {
                    add(
                        buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/png")
                                put("data", Base64.getEncoder().encodeToString(turn.image))
                            }
                        },
                    )
                }
                turn.systemNote?.takeIf { it.isNotEmpty() }?.let { add(textBlock("<app_tips>$it</app_tips>")) }
                if (turn.text.isNotEmpty()) add(textBlock(turn.text))
            }
            if (blocks.isEmpty()) return null
            return buildJsonObject {
                put("role", "user")
                put("content", blocks)
            }
        }

        private fun toolResult(id: String, text: String, isError: Boolean) = buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", id)
            put("content", text)
            if (isError) put("is_error", true)
        }

        private fun textBlock(text: String) = buildJsonObject {
            put("type", "text")
            put("text", text)
        }

        private fun parseReply(body: String, userMessage: JsonObject): LlmResult {
            val malformed = LlmResult.Failed(FailureKind.OTHER, "The provider's reply could not be read")
            val reply = try {
                Json.parseToJsonElement(body) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return malformed
            val content = reply["content"] as? JsonArray ?: return malformed
            if (content.isEmpty() || content.any { it !is JsonObject }) return malformed
            val blocks = content.map { it as JsonObject }
            val toolUses = blocks.filter { it.str("type") == "tool_use" }
            if (toolUses.any { it.str("id") == null || it.str("name") == null }) return malformed

            val stopReason = reply.str("stop_reason")
            val result: LlmResult = when {
                stopReason == "refusal" ->
                    LlmResult.Refused((reply["stop_details"] as? JsonObject)?.str("explanation"))
                toolUses.isNotEmpty() -> {
                    val first = toolUses.first()
                    LlmResult.Call(
                        first.str("id")!!,
                        first.str("name")!!,
                        first["input"] as? JsonObject ?: JsonObject(emptyMap()),
                    )
                }
                else -> LlmResult.NoCall(blocks.filter { it.str("type") == "text" }.mapNotNull { it.str("text") }.joinToString("\n"))
            }

            history += userMessage
            history += buildJsonObject {
                put("role", "assistant")
                put("content", content)
            }
            unanswered = if (result is LlmResult.Call) toolUses.drop(1).map { it.str("id")!! } else emptyList()
            usageTotal += usageOf(reply["usage"] as? JsonObject)
            return result
        }

        private fun usageOf(usage: JsonObject?): TokenUsage {
            fun n(key: String) = (usage?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toLongOrNull() ?: 0L
            return TokenUsage(
                inputTokens = n("input_tokens") + n("cache_creation_input_tokens"),
                outputTokens = n("output_tokens"),
                cachedInputTokens = n("cache_read_input_tokens"),
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val ANTHROPIC_HOST = "api.anthropic.com"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val IGNORED = "ignored: one action per step"
        val FALLBACK_MODELS = setOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")
        val EPHEMERAL: JsonElement = buildJsonObject { put("type", "ephemeral") }
    }
}
