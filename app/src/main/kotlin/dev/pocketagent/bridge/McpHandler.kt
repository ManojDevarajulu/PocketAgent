package dev.pocketagent.bridge

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** How a request from the computer ended. [Refused] means nothing ran. */
sealed interface TaskReply {
    data class Done(val success: Boolean, val summary: String) : TaskReply
    data class Refused(val reason: String) : TaskReply
}

/** What the bridge needs from the phone: ask its owner, run the task, say how it went. */
fun interface PhoneTasks {
    suspend fun run(task: String): TaskReply
}

/**
 * The Model Context Protocol, the small part of it a phone needs: one tool, `run_task`. It speaks JSON-RPC and
 * validates every field, because the sender is another machine on the network. It never reads the screen and has no
 * other tool, so a paired computer can ask for a task but can never see or tap anything on its own.
 */
class McpHandler(private val tasks: PhoneTasks) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The JSON-RPC response for [body], or null for a notification, which gets no reply. */
    suspend fun handle(body: String): String? {
        val element = try {
            json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            return failure(null, PARSE_ERROR, "Parse error")
        }
        // Batches are not part of the protocol revisions this supports.
        val request = element as? JsonObject ?: return failure(null, INVALID_REQUEST, "Invalid request")
        val id = request["id"]
        if (id != null && !(id is JsonPrimitive && id !is JsonNull)) return failure(null, INVALID_REQUEST, "Invalid id")
        val method = (request["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return failure(id, INVALID_REQUEST, "Invalid request")
        // A request without an id is a notification: nothing is sent back, whatever it asked.
        if (id == null) return null
        return when (method) {
            "initialize" -> success(id, initializeResult(request["params"]))
            "ping" -> success(id, buildJsonObject {})
            "tools/list" -> success(id, toolList())
            "tools/call" -> call(id, request["params"])
            else -> failure(id, METHOD_NOT_FOUND, "Method not found")
        }
    }

    private fun initializeResult(params: JsonElement?): JsonObject {
        val asked = ((params as? JsonObject)?.get("protocolVersion") as? JsonPrimitive)?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", if (asked in SUPPORTED) asked else LATEST)
            putJsonObject("capabilities") { putJsonObject("tools") {} }
            putJsonObject("serverInfo") {
                put("name", "pocket-agent")
                put("version", "0.1.0")
            }
            put(
                "instructions",
                "This is a phone. run_task asks its owner to approve a task, then does it in the phone's apps. " +
                    "It can take a few minutes, and the phone may refuse if it is locked or busy.",
            )
        }
    }

    private fun toolList(): JsonObject = buildJsonObject {
        putJsonArray("tools") {
            add(
                buildJsonObject {
                    put("name", TOOL)
                    put(
                        "description",
                        "Ask the phone to do something in its apps, for example 'play lofi on YouTube'. The phone's owner " +
                            "is asked to approve every request on the phone first, and anything that sends, calls or pays is " +
                            "confirmed again. Returns what happened.",
                    )
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("task") {
                                put("type", "string")
                                put("description", "What to do, in plain words ($MAX_TASK characters at most).")
                            }
                        }
                        putJsonArray("required") { add("task") }
                        put("additionalProperties", false)
                    }
                },
            )
        }
    }

    private suspend fun call(id: JsonElement, params: JsonElement?): String {
        val p = params as? JsonObject ?: return failure(id, INVALID_PARAMS, "Invalid params")
        val name = (p["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (name != TOOL) return failure(id, INVALID_PARAMS, "Unknown tool")
        val args = p["arguments"] as? JsonObject ?: return failure(id, INVALID_PARAMS, "Missing arguments")
        if (args.keys.any { it != "task" }) return failure(id, INVALID_PARAMS, "Unexpected argument")
        val task = (args["task"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        if (task.isNullOrEmpty() || task.length > MAX_TASK || task.any { it.isISOControl() && it != '\n' }) {
            return failure(id, INVALID_PARAMS, "task must be 1 to $MAX_TASK characters of plain text")
        }
        return when (val reply = tasks.run(task)) {
            is TaskReply.Done -> success(id, textResult(reply.summary, isError = !reply.success))
            is TaskReply.Refused -> success(id, textResult(reply.reason, isError = true))
        }
    }

    private fun textResult(text: String, isError: Boolean): JsonObject = buildJsonObject {
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
        put("isError", isError)
    }

    private fun success(id: JsonElement, result: JsonObject): String =
        buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }.toString()

    private fun failure(id: JsonElement?, code: Int, message: String): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        putJsonObject("error") { put("code", code); put("message", message) }
    }.toString()

    companion object {
        const val TOOL = "run_task"
        const val MAX_TASK = 2_000
        private const val LATEST = "2025-06-18"
        private val SUPPORTED = setOf("2024-11-05", "2025-03-26", "2025-06-18")
        private const val PARSE_ERROR = -32700
        private const val INVALID_REQUEST = -32600
        private const val METHOD_NOT_FOUND = -32601
        private const val INVALID_PARAMS = -32602
    }
}
