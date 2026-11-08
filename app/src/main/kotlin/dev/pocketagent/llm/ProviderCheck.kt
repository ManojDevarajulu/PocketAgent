package dev.pocketagent.llm

import dev.pocketagent.agent.ToolDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The outcome of the setup check. [Other] carries a fixed phrase, never provider text or the key. */
sealed interface CheckResult {
    data object Works : CheckResult
    data object NoToolCalls : CheckResult
    data object BadKey : CheckResult
    data object Unreachable : CheckResult
    data class Other(val message: String) : CheckResult
}

/** One real request proving the chosen model answers with a tool call. */
object ProviderCheck {
    private const val PING = "ping"

    private val pingTool = ToolDefinition(
        name = PING,
        description = "Does nothing. Call it to show that tool calls work.",
        inputSchema = JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(emptyMap()),
                "required" to JsonArray(emptyList()),
                "additionalProperties" to JsonPrimitive(false),
            ),
        ),
    )

    suspend fun run(client: LlmClient): CheckResult {
        val session = client.newSession("Call the ping tool.", listOf(pingTool))
        return when (val result = session.send(Turn("Call the ping tool now."))) {
            is LlmResult.Call -> if (result.name == PING) CheckResult.Works else CheckResult.NoToolCalls
            is LlmResult.NoCall -> CheckResult.NoToolCalls
            is LlmResult.Refused -> CheckResult.Other("The model declined the test request.")
            is LlmResult.Failed -> when (result.kind) {
                FailureKind.AUTH -> CheckResult.BadKey
                FailureKind.NETWORK -> CheckResult.Unreachable
                else -> CheckResult.Other("The test request failed.")
            }
        }
    }
}
