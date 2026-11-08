package dev.pocketagent.llm

import dev.pocketagent.fakes.FakeLlm
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProviderCheckTest {
    private fun check(result: LlmResult) = runTest {
        val fake = FakeLlm(listOf(result))
        val r = ProviderCheck.run(fake)
        results += r
        fakes += fake
    }

    private val results = mutableListOf<CheckResult>()
    private val fakes = mutableListOf<FakeLlm>()

    @Test
    fun pingCallWorks() {
        check(LlmResult.Call("1", "ping", JsonObject(emptyMap())))
        assertEquals(CheckResult.Works, results.single())
    }

    @Test
    fun sendsOnePingToolAndOneNonEmptyTurn() {
        check(LlmResult.Call("1", "ping", JsonObject(emptyMap())))
        val fake = fakes.single()
        assertEquals("Call the ping tool.", fake.systemPrompt)
        val tool = fake.tools.single()
        assertEquals("ping", tool.name)
        assertEquals("object", tool.inputSchema["type"]!!.jsonPrimitive.content)
        assertEquals(0, (tool.inputSchema["properties"] as JsonObject).size)
        assertEquals(0, tool.inputSchema["required"]!!.jsonArray.size)
        assertFalse(tool.inputSchema["additionalProperties"]!!.jsonPrimitive.boolean)
        assertTrue(fake.turns.single().text.isNotBlank())
    }

    @Test
    fun noCallMeansNoToolCalls() {
        check(LlmResult.NoCall("hello"))
        assertEquals(CheckResult.NoToolCalls, results.single())
    }

    @Test
    fun anotherToolNameMeansNoToolCalls() {
        check(LlmResult.Call("1", "tap", JsonObject(emptyMap())))
        assertEquals(CheckResult.NoToolCalls, results.single())
    }

    @Test
    fun authFailureIsBadKey() {
        check(LlmResult.Failed(FailureKind.AUTH, "x"))
        assertEquals(CheckResult.BadKey, results.single())
    }

    @Test
    fun networkFailureIsUnreachable() {
        check(LlmResult.Failed(FailureKind.NETWORK, "x"))
        assertEquals(CheckResult.Unreachable, results.single())
    }

    @Test
    fun refusedAndOtherFailuresAreOtherWithAFixedMessage() {
        for (r in listOf(
            LlmResult.Refused("secret-key-123"),
            LlmResult.Failed(FailureKind.SERVER, "secret-key-123"),
            LlmResult.Failed(FailureKind.RATE_LIMIT, "secret-key-123"),
            LlmResult.Failed(FailureKind.OTHER, "secret-key-123"),
        )) {
            results.clear()
            check(r)
            val other = assertIs<CheckResult.Other>(results.single())
            assertTrue(other.message.isNotBlank())
            assertFalse(other.message.contains("secret"))
        }
    }

    private class NoNetwork : HttpTransport {
        override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse =
            error("not used")
    }

    private fun config(kind: ProviderKind, url: String = "https://example.com", key: String = "sk-test") =
        ProviderConfig(kind, url, key, "m", supportsImages = true)

    @Test
    fun createRefusesAnInvalidConfig() {
        val e = assertFailsWith<IllegalArgumentException> {
            Providers.create(config(ProviderKind.ANTHROPIC, url = "ftp://x", key = "sk-leaky-key"), HttpCaller(NoNetwork()))
        }
        assertTrue(e.message!!.contains("https"))
        assertFalse(e.message!!.contains("sk-leaky-key"))
    }

    @Test
    fun createPicksTheClientByKind() {
        val caller = HttpCaller(NoNetwork())
        assertIs<AnthropicClient>(Providers.create(config(ProviderKind.ANTHROPIC), caller))
        assertIs<OpenAiCompatClient>(Providers.create(config(ProviderKind.OPENAI_COMPAT), caller))
    }
}
