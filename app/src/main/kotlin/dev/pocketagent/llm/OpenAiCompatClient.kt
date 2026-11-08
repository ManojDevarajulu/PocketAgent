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
 * OpenAI-compatible Chat Completions (OpenRouter, OpenAI, Gemini, DeepSeek, Groq, Ollama). Each session keeps the
 * provider-native history append-only: the assistant message is stored exactly as received, and a turn joins the
 * history only after its request succeeded. Old screens are stubbed out of a per-request copy, never the history.
 */
class OpenAiCompatClient(private val config: ProviderConfig, private val caller: HttpCaller) : LlmClient {
    override val supportsImages: Boolean = config.supportsImages

    override fun newSession(systemPrompt: String, tools: List<ToolDefinition>): LlmSession =
        Session(systemPrompt, tools)

    private inner class Session(private val systemPrompt: String, private val tools: List<ToolDefinition>) : LlmSession {
        /** Everything after the system message. Append-only. */
        private val history = mutableListOf<JsonObject>()
        private var usageTotal = TokenUsage()

        /** Tool-call ids from the last reply that the agent loop did not answer. */
        private var unanswered: List<String> = emptyList()

        /** Set once a retry without `parallel_tool_calls` succeeded; it is then never sent again. */
        private var parallelRejected = false

        /** Messages whose request got an empty reply; they go first in the next request. */
        private var pending: List<JsonObject> = emptyList()

        override val usage: TokenUsage get() = usageTotal

        override suspend fun send(turn: Turn): LlmResult {
            val sent = layout(pending + turnMessages(turn))
            if (sent.isEmpty()) return LlmResult.Failed(FailureKind.OTHER, "Nothing to send")
            val messages = trimmed(listOf(systemMessage()) + history + sent)

            var outcome = post(messages, !parallelRejected)
            if (!parallelRejected && outcome is HttpOutcome.Failure && outcome.status == 400) {
                outcome = post(messages, false)
                if (outcome is HttpOutcome.Success) parallelRejected = true
            }
            val body = when (outcome) {
                is HttpOutcome.Failure -> return LlmResult.Failed(outcome.kind, outcome.message)
                is HttpOutcome.Success -> outcome.body
            }
            return parseReply(body, sent)
        }

        private suspend fun post(messages: List<JsonObject>, withParallel: Boolean): HttpOutcome {
            val headers = buildMap {
                put("Content-Type", "application/json")
                if (config.apiKey.isNotEmpty()) put("Authorization", "Bearer ${config.apiKey}")
            }
            val url = config.baseUrl.trimEnd('/') + "/chat/completions"
            return caller.post(url, headers, requestBody(messages, withParallel).toString())
        }

        private fun requestBody(messages: List<JsonObject>, withParallel: Boolean) = buildJsonObject {
            put("model", config.model)
            // OpenAI's newer models reject max_tokens; everything else still expects it.
            put(if (config.host == OPENAI_HOST) "max_completion_tokens" else "max_tokens", config.maxOutputTokens)
            put("messages", JsonArray(messages))
            putJsonArray("tools") {
                for (tool in tools) {
                    add(
                        buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.inputSchema)
                            }
                        },
                    )
                }
            }
            put("tool_choice", "auto")
            if (withParallel) put("parallel_tool_calls", false)
        }

        private fun systemMessage() = buildJsonObject {
            put("role", "system")
            put("content", systemPrompt)
        }

        /** Tool messages for this turn, then one user message when there is anything to put in it. */
        private fun turnMessages(turn: Turn): List<JsonObject> {
            val out = mutableListOf<JsonObject>()
            turn.toolResult?.let { out += toolMessage(it.callId, it.text) }
            for (id in unanswered) if (id != turn.toolResult?.callId) out += toolMessage(id, IGNORED)
            val parts = buildJsonArray {
                turn.systemNote?.takeIf { it.isNotEmpty() }?.let { add(textPart("<app_tips>$it</app_tips>")) }
                if (turn.text.isNotEmpty()) add(textPart(turn.text))
                if (turn.image != null && config.supportsImages) {
                    add(
                        buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(turn.image))
                            }
                        },
                    )
                }
            }
            if (parts.isNotEmpty()) out += userMessage(parts.toList())
            return out
        }

        /**
         * Tool messages must directly follow the assistant message that made the calls, so they all go first;
         * the user messages become one, with their parts in order.
         */
        private fun layout(messages: List<JsonObject>): List<JsonObject> {
            val toolMessages = messages.filter { it.str("role") == "tool" }
            val parts = messages.filter { it.str("role") == "user" }.flatMap { it["content"] as JsonArray }
            return toolMessages + if (parts.isEmpty()) emptyList() else listOf(userMessage(parts))
        }

        private fun toolMessage(id: String, text: String) = buildJsonObject {
            put("role", "tool")
            put("tool_call_id", id)
            put("content", text)
        }

        private fun userMessage(parts: List<JsonElement>) = buildJsonObject {
            put("role", "user")
            put("content", JsonArray(parts))
        }

        private fun textPart(text: String) = buildJsonObject {
            put("type", "text")
            put("text", text)
        }

        /**
         * Above 70% of the context window (about 3 characters per token), older user messages lose their screen
         * dump and images. Works on a copy; tool, assistant and the latest user messages are never touched.
         */
        private fun trimmed(messages: List<JsonObject>): List<JsonObject> {
            val estimate = messages.sumOf { it.toString().length } / CHARS_PER_TOKEN
            if (estimate <= config.contextTokens * TRIM_THRESHOLD) return messages
            val lastUser = messages.indexOfLast { it.str("role") == "user" }
            return messages.mapIndexed { i, m -> if (i < lastUser && m.str("role") == "user") stubbed(m) else m }
        }

        private fun stubbed(message: JsonObject): JsonObject {
            val parts = (message["content"] as? JsonArray) ?: return message
            // An image becomes a placeholder, so a trimmed user message is never empty.
            val kept = parts.map { part ->
                val obj = part as? JsonObject
                val text = obj?.str("text")
                when {
                    obj?.str("type") == "image_url" -> textPart(SCREENSHOT_OMITTED)
                    obj != null && text != null -> textPart(text.replace(SCREEN_DATA, OMITTED))
                    else -> part
                }
            }
            return userMessage(kept)
        }

        private fun parseReply(body: String, sent: List<JsonObject>): LlmResult {
            val malformed = LlmResult.Failed(FailureKind.OTHER, "The provider's reply could not be read")
            val reply = try {
                Json.parseToJsonElement(body) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return malformed
            val choice = (reply["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return malformed
            val message = choice["message"] as? JsonObject ?: return malformed
            val rawCalls = message["tool_calls"]
            val calls = if (rawCalls == null || rawCalls is JsonNull) emptyList() else {
                (rawCalls as? JsonArray)?.map { it as? JsonObject ?: return malformed } ?: return malformed
            }
            if (calls.any { it.str("id") == null || (it["function"] as? JsonObject)?.str("name") == null }) {
                return malformed
            }
            val usageDelta = usageOf(reply["usage"] as? JsonObject)

            if (choice.str("finish_reason") == "content_filter") {
                usageTotal += usageDelta
                return LlmResult.Refused(null)
            }

            val text = contentText(message["content"])
            if (calls.isEmpty() && text.isEmpty()) {
                // An empty assistant message is invalid on replay: keep the unsent messages for the next send.
                pending = sent
                unanswered = emptyList()
                usageTotal += usageDelta
                return LlmResult.NoCall("")
            }

            val result: LlmResult = if (calls.isEmpty()) {
                LlmResult.NoCall(text)
            } else {
                val first = calls.first()
                val function = first["function"] as JsonObject
                LlmResult.Call(first.str("id")!!, function.str("name")!!, argumentsOf(function["arguments"]))
            }

            history += sent
            pending = emptyList()
            history += assistantMessage(text, calls)
            unanswered = calls.drop(1).map { it.str("id")!! }
            usageTotal += usageDelta
            return result
        }

        /** Only standard fields are replayed (plus Gemini's `extra_content`); reasoning and the rest are dropped. */
        private fun assistantMessage(text: String, calls: List<JsonObject>) = buildJsonObject {
            put("role", "assistant")
            put("content", if (text.isNotEmpty()) JsonPrimitive(text) else JsonNull)
            if (calls.isNotEmpty()) {
                putJsonArray("tool_calls") {
                    for (call in calls) {
                        val function = call["function"] as JsonObject
                        add(
                            buildJsonObject {
                                put("id", call.str("id")!!)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", function.str("name")!!)
                                    put("arguments", function["arguments"] ?: JsonPrimitive(""))
                                }
                                call["extra_content"]?.let { put("extra_content", it) }
                            },
                        )
                    }
                }
            }
        }

        /** The arguments arrive as a JSON string; anything that is not a JSON object becomes an empty input. */
        private fun argumentsOf(arguments: JsonElement?): JsonObject {
            if (arguments is JsonObject) return arguments
            val raw = (arguments as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return JsonObject(emptyMap())
            return try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: JsonObject(emptyMap())
        }

        private fun contentText(content: JsonElement?): String = when (content) {
            is JsonPrimitive -> if (content is JsonNull) "" else content.content
            is JsonArray -> content.mapNotNull { (it as? JsonObject)?.str("text") }.joinToString("\n")
            else -> ""
        }

        private fun usageOf(usage: JsonObject?): TokenUsage {
            fun n(o: JsonObject?, key: String) =
                (o?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toLongOrNull() ?: 0L
            val cached = n(usage?.get("prompt_tokens_details") as? JsonObject, "cached_tokens")
            return TokenUsage(
                inputTokens = (n(usage, "prompt_tokens") - cached).coerceAtLeast(0),
                outputTokens = n(usage, "completion_tokens"),
                cachedInputTokens = cached,
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val OPENAI_HOST = "api.openai.com"
        const val IGNORED = "ignored: one action per step"
        const val OMITTED = "[earlier screen omitted]"
        const val SCREENSHOT_OMITTED = "[earlier screenshot omitted]"
        const val CHARS_PER_TOKEN = 3
        const val TRIM_THRESHOLD = 0.7
        val SCREEN_DATA = Regex("<screen_data.*?</screen_data>", RegexOption.DOT_MATCHES_ALL)
    }
}
