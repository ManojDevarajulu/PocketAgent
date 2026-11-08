package dev.pocketagent.llm

import dev.pocketagent.agent.ToolDefinition
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnthropicClientTest {
    private class Recorded(val url: String, val headers: Map<String, String>, val rawBody: String) {
        val body: JsonObject = Json.parseToJsonElement(rawBody).jsonObject
        val messages: JsonArray get() = body.getValue("messages").jsonArray
    }

    /** Each step is an [HttpResponse] or a [Throwable]; the last step repeats. */
    private class FakeTransport(private val script: List<Any>) : HttpTransport {
        val requests = mutableListOf<Recorded>()

        override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
            requests += Recorded(url, headers, body)
            val step = script[minOf(requests.size - 1, script.size - 1)]
            if (step is Throwable) throw step
            return step as HttpResponse
        }
    }

    private fun reply(name: String) = HttpResponse(200, emptyMap(), load(name))

    private fun load(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/llm/anthropic/$name.json")) { "missing $name" }
            .bufferedReader().use { it.readText() }

    private fun status(code: Int) = HttpResponse(code, emptyMap(), """{"error":{"message":"nope sk-secret"}}""")

    private val tools = listOf(
        ToolDefinition("tap", "Tap", buildJsonObject { put("type", "object") }),
        ToolDefinition("back", "Back", buildJsonObject { put("type", "object") }),
    )

    private fun config(
        baseUrl: String = "https://api.anthropic.com",
        model: String = "claude-opus-5-5",
        effort: String? = null,
        supportsImages: Boolean = true,
    ) = ProviderConfig(
        ProviderKind.ANTHROPIC, baseUrl, "sk-secret", model, supportsImages,
        maxOutputTokens = 2048, effort = effort,
    )

    private fun session(t: FakeTransport, cfg: ProviderConfig = config()) =
        AnthropicClient(cfg, HttpCaller(t)).newSession("You drive a phone.", tools)

    private fun contentTypes(message: JsonElement) =
        message.jsonObject.getValue("content").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content }

    @Test
    fun requestHasTheRightUrlHeadersAndFields() = runTest {
        val t = FakeTransport(listOf(reply("tool_use")))
        session(t, config(baseUrl = "https://api.anthropic.com/")).send(Turn("Task: x"))
        val r = t.requests.single()
        assertEquals("https://api.anthropic.com/v1/messages", r.url)
        assertEquals("sk-secret", r.headers["x-api-key"])
        assertEquals("2023-06-01", r.headers["anthropic-version"])
        assertEquals("application/json", r.headers["content-type"])
        assertEquals("claude-opus-5-5", r.body.getValue("model").jsonPrimitive.content)
        assertEquals(2048, r.body.getValue("max_tokens").jsonPrimitive.content.toInt())
        val system = r.body.getValue("system").jsonArray.single().jsonObject
        assertEquals("text", system.getValue("type").jsonPrimitive.content)
        assertEquals("You drive a phone.", system.getValue("text").jsonPrimitive.content)
        assertEquals("ephemeral", system.getValue("cache_control").jsonObject.getValue("type").jsonPrimitive.content)
        val tool = r.body.getValue("tools").jsonArray.first().jsonObject
        assertEquals("tap", tool.getValue("name").jsonPrimitive.content)
        assertEquals("Tap", tool.getValue("description").jsonPrimitive.content)
        assertTrue(tool.getValue("strict").jsonPrimitive.boolean)
        assertEquals("object", tool.getValue("input_schema").jsonObject.getValue("type").jsonPrimitive.content)
        val choice = r.body.getValue("tool_choice").jsonObject
        assertEquals("auto", choice.getValue("type").jsonPrimitive.content)
        assertTrue(choice.getValue("disable_parallel_tool_use").jsonPrimitive.boolean)
        for (k in listOf("temperature", "top_p", "top_k", "thinking")) assertFalse(k in r.body, k)
    }

    @Test
    fun keyIsOnlyInTheHeader() = runTest {
        val t = FakeTransport(listOf(reply("tool_use")))
        session(t).send(Turn("Task: x"))
        val r = t.requests.single()
        assertFalse(r.url.contains("sk-secret"))
        assertFalse(r.rawBody.contains("sk-secret"))
        assertEquals(1, r.headers.values.count { it == "sk-secret" })
    }

    @Test
    fun effortIsSentOnlyWhenConfigured() = runTest {
        val with = FakeTransport(listOf(reply("tool_use")))
        session(with, config(effort = "high")).send(Turn("a"))
        assertEquals("high", with.requests.single().body.getValue("output_config").jsonObject.getValue("effort").jsonPrimitive.content)
        val without = FakeTransport(listOf(reply("tool_use")))
        session(without).send(Turn("a"))
        assertFalse("output_config" in without.requests.single().body)
    }

    @Test
    fun fallbacksOnlyForListedModelsOnTheAnthropicHost() = runTest {
        for (model in listOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")) {
            val t = FakeTransport(listOf(reply("tool_use")))
            session(t, config(model = model)).send(Turn("a"))
            val r = t.requests.single()
            assertEquals("server-side-fallback-2026-07-01", r.headers["anthropic-beta"], model)
            assertEquals("default", r.body.getValue("fallbacks").jsonPrimitive.content, model)
        }
        val other = FakeTransport(listOf(reply("tool_use")))
        session(other, config(model = "claude-haiku-4-5")).send(Turn("a"))
        assertFalse("anthropic-beta" in other.requests.single().headers)
        assertFalse("fallbacks" in other.requests.single().body)
    }

    @Test
    fun fallbacksNotSentToALookAlikeHost() = runTest {
        val t = FakeTransport(listOf(reply("tool_use")))
        session(t, config(baseUrl = "https://api.anthropic.com.evil.test")).send(Turn("a"))
        val r = t.requests.single()
        assertFalse("anthropic-beta" in r.headers)
        assertFalse("fallbacks" in r.body)
    }

    @Test
    fun a400AfterEffortOrFallbacksRetriesOnceWithoutThemAndRemembers() = runTest {
        val t = FakeTransport(listOf(status(400), reply("tool_use"), reply("tool_use_2")))
        val s = session(t, config(effort = "high"))
        assertIs<LlmResult.Call>(s.send(Turn("a")))
        assertEquals(2, t.requests.size)
        assertTrue("output_config" in t.requests[0].body && "fallbacks" in t.requests[0].body)
        assertFalse("output_config" in t.requests[1].body)
        assertFalse("fallbacks" in t.requests[1].body)
        assertFalse("anthropic-beta" in t.requests[1].headers)
        assertIs<LlmResult.Call>(s.send(Turn("b", ToolResult("toolu_01", "ok", false))))
        assertEquals(3, t.requests.size)
        assertFalse("output_config" in t.requests[2].body)
        assertFalse("fallbacks" in t.requests[2].body)
    }

    @Test
    fun a400ThatPersistsWithoutTheOptionalFieldsForgetsNothing() = runTest {
        val t = FakeTransport(listOf(status(400), status(400), reply("tool_use")))
        val s = session(t, config(effort = "high"))
        val first = assertIs<LlmResult.Failed>(s.send(Turn("a")))
        assertEquals(FailureKind.OTHER, first.kind)
        assertEquals(2, t.requests.size)
        assertIs<LlmResult.Call>(s.send(Turn("a")))
        assertTrue("output_config" in t.requests[2].body)
        assertTrue("fallbacks" in t.requests[2].body)
    }

    @Test
    fun a400WithNothingOptionalToDropIsNotRetried() = runTest {
        val t = FakeTransport(listOf(status(400)))
        val r = session(t, config(model = "claude-haiku-4-5")).send(Turn("a"))
        assertIs<LlmResult.Failed>(r)
        assertEquals(1, t.requests.size)
    }

    @Test
    fun toolUseReplyBecomesCall() = runTest {
        val t = FakeTransport(listOf(reply("tool_use")))
        val r = assertIs<LlmResult.Call>(session(t).send(Turn("a")))
        assertEquals("toolu_01", r.callId)
        assertEquals("tap", r.name)
        assertEquals(buildJsonObject { put("node_id", "a5") }, r.input)
    }

    @Test
    fun onlyTheFirstToolUseIsKept() = runTest {
        val t = FakeTransport(listOf(reply("two_tools")))
        val r = assertIs<LlmResult.Call>(session(t).send(Turn("a")))
        assertEquals("toolu_A", r.callId)
        assertEquals("tap", r.name)
    }

    @Test
    fun droppedToolUsesAreAnsweredInTheNextRequest() = runTest {
        val t = FakeTransport(listOf(reply("two_tools"), reply("tool_use")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("b", ToolResult("toolu_A", "done", false)))
        val blocks = t.requests[1].messages[2].jsonObject.getValue("content").jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("tool_result", "tool_result", "tool_result", "text"),
            blocks.map { it.getValue("type").jsonPrimitive.content },
        )
        assertEquals(
            listOf("toolu_A", "toolu_B", "toolu_C"),
            blocks.take(3).map { it.getValue("tool_use_id").jsonPrimitive.content },
        )
        assertNull(blocks[0]["is_error"]?.takeIf { it.jsonPrimitive.boolean })
        for (extra in blocks.subList(1, 3)) {
            assertTrue(extra.getValue("is_error").jsonPrimitive.boolean)
            assertEquals("ignored: one action per step", extra.getValue("content").jsonPrimitive.content)
        }
    }

    @Test
    fun textOnlyReplyIsNoCall() = runTest {
        val a = session(FakeTransport(listOf(reply("end_turn")))).send(Turn("a"))
        assertEquals(LlmResult.NoCall("I am done talking."), a)
        val b = session(FakeTransport(listOf(reply("max_tokens")))).send(Turn("a"))
        assertEquals(LlmResult.NoCall("Cut off"), b)
    }

    @Test
    fun refusalStopReasonIsRefusedWithExplanationOrNull() = runTest {
        assertEquals(
            LlmResult.Refused("This request was declined."),
            session(FakeTransport(listOf(reply("refusal")))).send(Turn("a")),
        )
        assertEquals(LlmResult.Refused(null), session(FakeTransport(listOf(reply("refusal_bare")))).send(Turn("a")))
    }

    @Test
    fun unknownStopReasonWithNoToolIsNoCall() = runTest {
        assertEquals(LlmResult.NoCall("Paused."), session(FakeTransport(listOf(reply("unknown_stop")))).send(Turn("a")))
    }

    @Test
    fun toolResultAndTextAreSentInOneUserMessage() = runTest {
        val t = FakeTransport(listOf(reply("tool_use"), reply("tool_use_2"), reply("tool_use")))
        val s = session(t)
        s.send(Turn("Task: x"))
        s.send(Turn("screen", ToolResult("toolu_01", "tapped", true)))
        s.send(Turn("", ToolResult("toolu_02", "went back", false)))
        val second = t.requests[1].messages.last().jsonObject
        assertEquals("user", second.getValue("role").jsonPrimitive.content)
        val blocks = second.getValue("content").jsonArray.map { it.jsonObject }
        assertEquals(listOf("tool_result", "text"), blocks.map { it.getValue("type").jsonPrimitive.content })
        assertEquals("toolu_01", blocks[0].getValue("tool_use_id").jsonPrimitive.content)
        assertEquals("tapped", blocks[0].getValue("content").jsonPrimitive.content)
        assertTrue(blocks[0].getValue("is_error").jsonPrimitive.boolean)
        assertEquals("screen", blocks[1].getValue("text").jsonPrimitive.content)
        assertEquals(listOf("tool_result"), contentTypes(t.requests[2].messages.last()))
    }

    @Test
    fun imageGoesAfterTheToolResultAsBase64Png() = runTest {
        val t = FakeTransport(listOf(reply("tool_use"), reply("tool_use")))
        val s = session(t)
        s.send(Turn("Task: x"))
        s.send(Turn("screen", ToolResult("toolu_01", "ok", false), image = byteArrayOf(1, 2, 3)))
        val blocks = t.requests[1].messages.last().jsonObject.getValue("content").jsonArray.map { it.jsonObject }
        assertEquals(listOf("tool_result", "image", "text"), blocks.map { it.getValue("type").jsonPrimitive.content })
        val source = blocks[1].getValue("source").jsonObject
        assertEquals("base64", source.getValue("type").jsonPrimitive.content)
        assertEquals("image/png", source.getValue("media_type").jsonPrimitive.content)
        assertEquals("AQID", source.getValue("data").jsonPrimitive.content)
    }

    @Test
    fun imageIsDroppedWhenTheClientDoesNotSupportImages() = runTest {
        val t = FakeTransport(listOf(reply("tool_use")))
        session(t, config(supportsImages = false)).send(Turn("screen", image = byteArrayOf(1)))
        assertEquals(listOf("text"), contentTypes(t.requests.single().messages.single()))
    }

    @Test
    fun systemNoteIsItsOwnAppTipsBlockBeforeTheScreenText() = runTest {
        val t = FakeTransport(listOf(reply("tool_use")))
        session(t).send(Turn("SCREEN", systemNote = "use the search bar"))
        val blocks = t.requests.single().messages.single().jsonObject.getValue("content").jsonArray.map { it.jsonObject }
        assertEquals(2, blocks.size)
        assertEquals("<app_tips>use the search bar</app_tips>", blocks[0].getValue("text").jsonPrimitive.content)
        assertEquals("SCREEN", blocks[1].getValue("text").jsonPrimitive.content)
    }

    @Test
    fun assistantContentIncludingThinkingBlocksIsReplayedUnchanged() = runTest {
        val t = FakeTransport(listOf(reply("tool_use"), reply("tool_use_2")))
        val s = session(t)
        s.send(Turn("Task: x"))
        s.send(Turn("screen", ToolResult("toolu_01", "ok", false)))
        val original = Json.parseToJsonElement(load("tool_use")).jsonObject.getValue("content")
        val replayed = t.requests[1].messages[1].jsonObject
        assertEquals("assistant", replayed.getValue("role").jsonPrimitive.content)
        assertEquals(original, replayed.getValue("content"))
        assertEquals(original.toString(), replayed.getValue("content").toString())
    }

    @Test
    fun historyIsAppendOnly() = runTest {
        val t = FakeTransport(listOf(reply("tool_use"), reply("tool_use_2"), reply("tool_use")))
        val s = session(t)
        s.send(Turn("Task: x"))
        s.send(Turn("one", ToolResult("toolu_01", "ok", false)))
        s.send(Turn("two", ToolResult("toolu_02", "ok", false)))
        for (i in 1..2) {
            val earlier = t.requests[i - 1].messages
            assertEquals(earlier.toList(), t.requests[i].messages.take(earlier.size))
            assertEquals(earlier.size + 2, t.requests[i].messages.size)
        }
    }

    @Test
    fun usageAccumulatesInputOutputAndCachedTokens() = runTest {
        val t = FakeTransport(listOf(reply("tool_use"), reply("tool_use_2")))
        val s = session(t)
        assertEquals(TokenUsage(), s.usage)
        s.send(Turn("a"))
        assertEquals(TokenUsage(inputTokens = 120, outputTokens = 30, cachedInputTokens = 5), s.usage)
        s.send(Turn("b", ToolResult("toolu_01", "ok", false)))
        assertEquals(TokenUsage(inputTokens = 320, outputTokens = 40, cachedInputTokens = 120), s.usage)
    }

    @Test
    fun automaticCachingIsRequested() = runTest {
        val t = FakeTransport(listOf(reply("tool_use")))
        session(t).send(Turn("a"))
        assertEquals(
            "ephemeral",
            t.requests.single().body.getValue("cache_control").jsonObject.getValue("type").jsonPrimitive.content,
        )
    }

    @Test
    fun httpFailuresMapToFailed() = runTest {
        val t = FakeTransport(listOf(status(401)))
        val r = assertIs<LlmResult.Failed>(session(t).send(Turn("a")))
        assertEquals(FailureKind.AUTH, r.kind)
        assertFalse(r.message.contains("sk-secret"))
    }

    @Test
    fun aRetryDoesNotDuplicateTheUserMessage() = runTest {
        val t = FakeTransport(listOf(status(400), reply("tool_use")))
        val s = session(t, config(effort = "high"))
        s.send(Turn("a"))
        assertEquals(1, t.requests[0].messages.size)
        assertEquals(1, t.requests[1].messages.size)
    }

    @Test
    fun aFailedSendLeavesNoUserMessageBehind() = runTest {
        val t = FakeTransport(listOf(status(401), reply("tool_use")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("a"))
        assertEquals(1, t.requests.last().messages.size)
    }

    @Test
    fun malformedJsonReplyIsFailedOther() = runTest {
        for (body in listOf("not json", "{}", """{"stop_reason":"end_turn","content":"x"}""")) {
            val t = FakeTransport(listOf(HttpResponse(200, emptyMap(), body)))
            val r = assertIs<LlmResult.Failed>(session(t).send(Turn("a")), body)
            assertEquals(FailureKind.OTHER, r.kind)
        }
    }

    @Test
    fun cancellationPropagates() = runTest {
        val t = FakeTransport(listOf(CancellationException("cancelled")))
        var caught: Throwable? = null
        val job = launch {
            try {
                session(t).send(Turn("a"))
            } catch (e: CancellationException) {
                caught = e
                throw e
            }
        }
        runCurrent()
        assertNotNull(caught)
        assertTrue(job.isCancelled)
    }

    @Test
    fun networkFailureMapsToNetwork() = runTest {
        val t = FakeTransport(listOf(IOException("boom")))
        val job = launch {
            val r = assertIs<LlmResult.Failed>(session(t).send(Turn("a")))
            assertEquals(FailureKind.NETWORK, r.kind)
        }
        testScheduler.advanceUntilIdle()
        assertTrue(job.isCompleted)
    }

    @Test
    fun refusalWithEmptyContentIsStillRefused() = runTest {
        val s = session(FakeTransport(listOf(reply("refusal_empty"))))
        assertEquals(LlmResult.Refused("Declined, empty."), s.send(Turn("a")))
    }

    @Test
    fun emptyEndTurnIsNoCallAndItsUserBlocksAreCarriedForward() = runTest {
        val t = FakeTransport(listOf(reply("tool_use"), reply("empty_end_turn"), reply("tool_use_2")))
        val s = session(t)
        s.send(Turn("Task: x"))
        assertEquals(LlmResult.NoCall(""), s.send(Turn("screen", ToolResult("toolu_01", "tapped", false))))
        assertEquals(TokenUsage(inputTokens = 127, outputTokens = 30, cachedInputTokens = 5), s.usage)
        assertIs<LlmResult.Call>(s.send(Turn("Reply with exactly one tool call.")))
        val messages = t.requests[2].messages.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.getValue("role").jsonPrimitive.content })
        val blocks = messages.last().getValue("content").jsonArray.map { it.jsonObject }
        assertEquals(listOf("tool_result", "text", "text"), blocks.map { it.getValue("type").jsonPrimitive.content })
        assertEquals("toolu_01", blocks[0].getValue("tool_use_id").jsonPrimitive.content)
        assertEquals("screen", blocks[1].getValue("text").jsonPrimitive.content)
        assertEquals("Reply with exactly one tool call.", blocks[2].getValue("text").jsonPrimitive.content)
        for (m in messages) assertTrue(m.getValue("content").jsonArray.isNotEmpty())
    }

    @Test
    fun refusalWithContentIsStillRefused() = runTest {
        assertEquals(
            LlmResult.Refused(null),
            session(FakeTransport(listOf(reply("refusal_bare")))).send(Turn("a")),
        )
    }
}
