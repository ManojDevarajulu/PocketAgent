package dev.pocketagent.llm

import dev.pocketagent.agent.AgentLoop
import dev.pocketagent.agent.EndReason
import dev.pocketagent.device.ScreenSerializer
import dev.pocketagent.device.node
import dev.pocketagent.device.screenOf
import dev.pocketagent.fakes.FakeDevice
import dev.pocketagent.fakes.FakeUi
import dev.pocketagent.playbook.PlaybookStore
import dev.pocketagent.safety.BlockedApps
import dev.pocketagent.safety.SafetyGate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The real [AgentLoop] with a real client and a real [HttpCaller] over a scripted transport. On every request the
 * transport checks that the history is valid for the provider's format, so a broken replay fails with a clear message.
 */
class ClientLoopIntegrationTest {
    private val own = "dev.pocketagent"

    private class ScriptedTransport(
        private val replies: List<String>,
        private val validate: (JsonObject) -> Unit,
    ) : HttpTransport {
        var requests = 0
            private set

        override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
            val request = requests++
            check(request < replies.size) { "Script exhausted: request ${request + 1} but only ${replies.size} replies" }
            try {
                validate(Json.parseToJsonElement(body).jsonObject)
            } catch (e: IllegalStateException) {
                throw AssertionError("Invalid history in request ${request + 1}: ${e.message}")
            }
            return HttpResponse(200, emptyMap(), replies[request])
        }
    }

    private fun runLoop(client: LlmClient) = runTest {
        val screens = List(20) { screenOf(node("b1", role = "button", text = "Item $it", clickable = true)) }
        val loop = AgentLoop(
            client, FakeDevice(screens), SafetyGate(BlockedApps(), own),
            PlaybookStore.load(emptyMap(), BlockedApps()), ScreenSerializer(own), FakeUi(),
        )
        val outcome = loop.run("scroll around", screens[0])
        assertEquals(EndReason.DONE, outcome.reason, outcome.summary)
    }

    private fun str(m: JsonObject, key: String) = (m[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // ---- OpenAI-compatible ----

    private fun openAiCall(id: String, name: String, arguments: String) = buildJsonObject {
        putJsonArray("choices") {
            add(
                buildJsonObject {
                    put("finish_reason", "tool_calls")
                    putJsonObject("message") {
                        put("role", "assistant")
                        put("content", JsonNull)
                        putJsonArray("tool_calls") {
                            add(
                                buildJsonObject {
                                    put("id", id)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", name)
                                        put("arguments", arguments)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }.toString()

    private fun openAiText(text: String) = buildJsonObject {
        putJsonArray("choices") {
            add(
                buildJsonObject {
                    put("finish_reason", "stop")
                    putJsonObject("message") {
                        put("role", "assistant")
                        put("content", text)
                    }
                },
            )
        }
    }.toString()

    private fun validateOpenAi(request: JsonObject) {
        val messages = (request["messages"] as JsonArray).map { it.jsonObject }
        var pending = emptySet<String>()
        for ((i, m) in messages.withIndex()) {
            val role = str(m, "role")
            if (role != "tool") check(pending.isEmpty()) { "message $i ($role) comes before tool answers for $pending" }
            when (role) {
                "assistant" -> {
                    val calls = (m["tool_calls"] as? JsonArray ?: JsonArray(emptyList())).map { it.jsonObject }
                    for (c in calls) {
                        val raw = str(c.getValue("function").jsonObject, "arguments")
                        val parsed = try {
                            raw?.let { Json.parseToJsonElement(it) }
                        } catch (_: SerializationException) {
                            null
                        }
                        check(parsed is JsonObject) { "tool call ${str(c, "id")} has arguments that are not a JSON object: $raw" }
                    }
                    pending = calls.map { str(it, "id")!! }.toSet()
                    check(pending.size == calls.size) { "duplicate tool call ids in message $i" }
                    if (calls.isEmpty()) check(m["content"] != null && m["content"] !is JsonNull) { "assistant message $i is empty" }
                }
                "tool" -> {
                    val id = str(m, "tool_call_id")
                    check(id in pending) { "tool message $i answers $id, which is not an open call (open: $pending)" }
                    pending = pending - id!!
                }
                "user" -> {
                    val content = m["content"]
                    check(content !is JsonArray || content.isNotEmpty()) { "user message $i has an empty content array" }
                }
            }
        }
        check(pending.isEmpty()) { "tool calls $pending are never answered" }
    }

    @Test
    fun openAiClientDrivesTheLoopToDoneWithAValidHistoryOnEveryRequest() {
        val replies = listOf(
            openAiCall("c1", "scroll", """{"direction":"down"}"""),
            openAiCall("c2", "tap", "not json"),
            openAiCall("c3", "scroll", """{ "direction": "up" }"""),
            openAiText("I will think about it."),
            openAiCall("c4", "scroll", """{"direction":"down"}"""),
            openAiText(""),
            openAiCall("c5", "screenshot", "{}"),
            openAiCall("c6", "done", """{"summary":"ok","success":true}"""),
        )
        val transport = ScriptedTransport(replies, ::validateOpenAi)
        val config = ProviderConfig(ProviderKind.OPENAI_COMPAT, "https://openrouter.ai/api/v1", "sk-test", "m", supportsImages = true)
        runLoop(OpenAiCompatClient(config, HttpCaller(transport)))
        assertEquals(replies.size, transport.requests)
    }

    // ---- Anthropic ----

    private fun anthropicToolUse(id: String, name: String, input: JsonObject) = buildJsonObject {
        put("stop_reason", "tool_use")
        putJsonArray("content") {
            add(buildJsonObject { put("type", "text"); put("text", "Working.") })
            add(
                buildJsonObject {
                    put("type", "tool_use")
                    put("id", id)
                    put("name", name)
                    put("input", input)
                },
            )
        }
    }.toString()

    private fun anthropicEnd(vararg texts: String) = buildJsonObject {
        put("stop_reason", "end_turn")
        put("content", buildJsonArray { texts.forEach { t -> add(buildJsonObject { put("type", "text"); put("text", t) }) } })
    }.toString()

    private fun validateAnthropic(request: JsonObject) {
        val messages = (request["messages"] as JsonArray).map { it.jsonObject }
        var open = emptySet<String>()
        for ((i, m) in messages.withIndex()) {
            val role = str(m, "role")
            check(role == if (i % 2 == 0) "user" else "assistant") { "message $i has role $role; roles must alternate starting with user" }
            val content = m["content"] as JsonArray
            check(content.isNotEmpty()) { "message $i has an empty content array" }
            val blocks = content.map { it.jsonObject }
            for (b in blocks) {
                if (str(b, "type") == "text") check(!str(b, "text").isNullOrEmpty()) { "message $i has an empty text block" }
            }
            if (role == "user") {
                val answered = blocks.filter { str(it, "type") == "tool_result" }.map { str(it, "tool_use_id")!! }
                check(answered.size == answered.toSet().size) { "message $i answers a tool call twice: $answered" }
                check(answered.toSet() == open) { "message $i answers $answered but the open tool calls are $open" }
            } else {
                open = blocks.filter { str(it, "type") == "tool_use" }.map { str(it, "id")!! }.toSet()
            }
        }
        check(messages.size % 2 == 1) { "the request must end with a user message" }
    }

    @Test
    fun anthropicClientDrivesTheLoopToDoneWithAValidHistoryOnEveryRequest() {
        val replies = listOf(
            anthropicToolUse("t1", "scroll", buildJsonObject { put("direction", "down") }),
            anthropicToolUse("t2", "tap", buildJsonObject { put("node_id", 5) }),
            anthropicToolUse("t3", "scroll", buildJsonObject { put("direction", "up") }),
            anthropicEnd("I will think about it."),
            anthropicToolUse("t4", "scroll", buildJsonObject { put("direction", "down") }),
            """{"stop_reason":"end_turn","content":[]}""",
            anthropicToolUse("t5", "screenshot", JsonObject(emptyMap())),
            anthropicToolUse("t6", "done", buildJsonObject { put("summary", "ok"); put("success", true) }),
        )
        val transport = ScriptedTransport(replies, ::validateAnthropic)
        val config = ProviderConfig(ProviderKind.ANTHROPIC, "https://api.anthropic.com", "sk-test", "m", supportsImages = true)
        runLoop(AnthropicClient(config, HttpCaller(transport)))
        assertEquals(replies.size, transport.requests)
    }
}
