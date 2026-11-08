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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiCompatClientTest {
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
        checkNotNull(javaClass.getResourceAsStream("/llm/openai/$name.json")) { "missing $name" }
            .bufferedReader().use { it.readText() }

    private fun status(code: Int) = HttpResponse(code, emptyMap(), """{"error":{"message":"nope sk-secret"}}""")

    private val tools = listOf(
        ToolDefinition("tap", "Tap", buildJsonObject { put("type", "object") }),
        ToolDefinition("back", "Back", buildJsonObject { put("type", "object") }),
    )

    private fun config(
        baseUrl: String = "https://openrouter.ai/api/v1",
        apiKey: String = "sk-secret",
        supportsImages: Boolean = true,
        contextTokens: Int = 128_000,
    ) = ProviderConfig(
        ProviderKind.OPENAI_COMPAT, baseUrl, apiKey, "openai/gpt-5.1", supportsImages,
        contextTokens = contextTokens, maxOutputTokens = 2048,
    )

    private fun session(t: FakeTransport, cfg: ProviderConfig = config()) =
        OpenAiCompatClient(cfg, HttpCaller(t)).newSession("You drive a phone.", tools)

    private fun JsonElement.role() = jsonObject.getValue("role").jsonPrimitive.content
    private fun JsonElement.parts() = jsonObject.getValue("content").jsonArray.map { it.jsonObject }
    private fun JsonObject.type() = getValue("type").jsonPrimitive.content
    private fun JsonObject.text() = getValue("text").jsonPrimitive.content
    private fun roles(r: Recorded) = r.messages.map { it.role() }

    @Test
    fun requestHasTheRightUrlHeadersAndFields() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        session(t, config(baseUrl = "https://openrouter.ai/api/v1/")).send(Turn("Task: x"))
        val r = t.requests.single()
        assertEquals("https://openrouter.ai/api/v1/chat/completions", r.url)
        assertEquals("Bearer sk-secret", r.headers["Authorization"])
        assertEquals("application/json", r.headers["Content-Type"])
        assertEquals("openai/gpt-5.1", r.body.getValue("model").jsonPrimitive.content)
        assertEquals(2048, r.body.getValue("max_tokens").jsonPrimitive.content.toInt())
        assertEquals("system", r.messages[0].role())
        assertEquals("You drive a phone.", r.messages[0].jsonObject.getValue("content").jsonPrimitive.content)
        val tool = r.body.getValue("tools").jsonArray.first().jsonObject
        assertEquals("function", tool.type())
        val fn = tool.getValue("function").jsonObject
        assertEquals("tap", fn.getValue("name").jsonPrimitive.content)
        assertEquals("Tap", fn.getValue("description").jsonPrimitive.content)
        assertEquals("object", fn.getValue("parameters").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("auto", r.body.getValue("tool_choice").jsonPrimitive.content)
        assertFalse(r.body.getValue("parallel_tool_calls").jsonPrimitive.boolean)
        for (k in listOf("temperature", "top_p")) assertFalse(k in r.body, k)
        assertFalse(r.rawBody.contains("sk-secret"))
        assertFalse(r.url.contains("sk-secret"))
        assertEquals(1, r.headers.values.count { it.contains("sk-secret") })
    }

    @Test
    fun noAuthHeaderWhenKeyIsEmpty() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        session(t, config(baseUrl = "http://localhost:11434/v1", apiKey = "")).send(Turn("Task: x"))
        assertEquals("http://localhost:11434/v1/chat/completions", t.requests.single().url)
        assertFalse(t.requests.single().headers.keys.any { it.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun strictIsNeverSent() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        session(t).send(Turn("Task: x"))
        assertFalse(t.requests.single().rawBody.contains("strict"))
    }

    @Test
    fun openAiHostUsesMaxCompletionTokens() = runTest {
        val openai = FakeTransport(listOf(reply("tool_calls")))
        session(openai, config(baseUrl = "https://api.openai.com/v1")).send(Turn("a"))
        val ob = openai.requests.single().body
        assertEquals(2048, ob.getValue("max_completion_tokens").jsonPrimitive.content.toInt())
        assertFalse("max_tokens" in ob)
        val other = FakeTransport(listOf(reply("tool_calls")))
        session(other, config(baseUrl = "https://openrouter.ai/api/v1")).send(Turn("a"))
        val b = other.requests.single().body
        assertEquals(2048, b.getValue("max_tokens").jsonPrimitive.content.toInt())
        assertFalse("max_completion_tokens" in b)
        val lookalike = FakeTransport(listOf(reply("tool_calls")))
        session(lookalike, config(baseUrl = "https://api.openai.com.evil.example/v1")).send(Turn("a"))
        assertTrue("max_tokens" in lookalike.requests.single().body)
    }

    @Test
    fun a400AfterParallelToolCallsRetriesOnceWithoutItAndRemembers() = runTest {
        val t = FakeTransport(listOf(status(400), reply("tool_calls"), reply("tool_calls_2")))
        val s = session(t)
        assertIs<LlmResult.Call>(s.send(Turn("a")))
        assertEquals(2, t.requests.size)
        assertTrue("parallel_tool_calls" in t.requests[0].body)
        assertFalse("parallel_tool_calls" in t.requests[1].body)
        assertEquals(2, t.requests[1].messages.size)
        assertIs<LlmResult.Call>(s.send(Turn("", ToolResult("call_abc1", "ok", false))))
        assertEquals(3, t.requests.size)
        assertFalse("parallel_tool_calls" in t.requests[2].body)
    }

    @Test
    fun aFailedRetryIsNotRemembered() = runTest {
        val t = FakeTransport(listOf(status(400), status(400), reply("tool_calls")))
        val s = session(t)
        assertIs<LlmResult.Failed>(s.send(Turn("a")))
        assertEquals(2, t.requests.size)
        assertIs<LlmResult.Call>(s.send(Turn("a")))
        assertTrue("parallel_tool_calls" in t.requests[2].body)
    }

    @Test
    fun toolCallsReplyBecomesCall() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        val r = assertIs<LlmResult.Call>(session(t).send(Turn("a")))
        assertEquals("call_abc1", r.callId)
        assertEquals("tap", r.name)
        assertEquals(buildJsonObject { put("node_id", "a5") }, r.input)
    }

    @Test
    fun reasoningFieldIsIgnored() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        val s = session(t)
        val r = assertIs<LlmResult.Call>(s.send(Turn("a")))
        assertFalse(r.toString().contains("tap the button"))
        assertEquals(TokenUsage(100, 30, 20), s.usage)
    }

    @Test
    fun onlyTheFirstToolCallIsKept() = runTest {
        val t = FakeTransport(listOf(reply("two_calls")))
        val r = assertIs<LlmResult.Call>(session(t).send(Turn("a")))
        assertEquals("call_1", r.callId)
        assertEquals("tap", r.name)
    }

    @Test
    fun droppedToolCallsAreAnsweredInTheNextRequest() = runTest {
        val t = FakeTransport(listOf(reply("two_calls"), reply("stop")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("screen", ToolResult("call_1", "tapped", false)))
        val m = t.requests[1].messages
        assertEquals(listOf("system", "user", "assistant", "tool", "tool", "tool", "user"), roles(t.requests[1]))
        assertEquals(3, m[2].jsonObject.getValue("tool_calls").jsonArray.size)
        assertEquals("call_1", m[3].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals("tapped", m[3].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("call_2", m[4].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals("call_3", m[5].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals("ignored: one action per step", m[4].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("ignored: one action per step", m[5].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun argumentsThatAreNotJsonOrNotAnObjectBecomeACallWithEmptyInput() = runTest {
        for ((name, id) in listOf("bad_arguments" to "call_bad", "array_arguments" to "call_arr")) {
            val t = FakeTransport(listOf(reply(name), reply("stop")))
            val s = session(t)
            val r = assertIs<LlmResult.Call>(s.send(Turn("a")))
            assertEquals(LlmResult.Call(id, "tap", JsonObject(emptyMap())), r)
            s.send(Turn("", ToolResult(id, "error", true)))
            // a string that is not a JSON object is replayed as "{}", so strict servers accept the history
            val assistant = t.requests[1].messages[2].jsonObject
            val args = assistant.getValue("tool_calls").jsonArray[0].jsonObject.getValue("function").jsonObject
                .getValue("arguments").jsonPrimitive.content
            assertEquals("{}", args)
        }
    }

    @Test
    fun validArgumentsAreReplayedByteForByte() = runTest {
        val spaced = """{ "node_id" :  "a1" }"""
        val body = """{"id":"g","choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
            "tool_calls":[{"id":"c1","type":"function","function":{"name":"tap","arguments":"{ \"node_id\" :  \"a1\" }"}}]}}]}"""
        val t = FakeTransport(listOf(HttpResponse(200, emptyMap(), body), reply("stop")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("", ToolResult("c1", "ok", false)))
        val args = t.requests[1].messages[2].jsonObject.getValue("tool_calls").jsonArray[0].jsonObject
            .getValue("function").jsonObject.getValue("arguments").jsonPrimitive.content
        assertEquals(spaced, args)
    }

    @Test
    fun stopWithoutCallAndLengthAreNoCall() = runTest {
        assertEquals(LlmResult.NoCall("All done."), session(FakeTransport(listOf(reply("stop")))).send(Turn("a")))
        assertEquals(LlmResult.NoCall("Partial answ"), session(FakeTransport(listOf(reply("length")))).send(Turn("a")))
    }

    @Test
    fun contentFilterIsRefused() = runTest {
        assertEquals(LlmResult.Refused(null), session(FakeTransport(listOf(reply("content_filter")))).send(Turn("a")))
    }

    @Test
    fun toolResultIsARoleToolMessageWithTheCallId() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls"), reply("stop")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("screen", ToolResult("call_abc1", "tapped", false)))
        val m = t.requests[1].messages
        assertEquals(listOf("system", "user", "assistant", "tool", "user"), roles(t.requests[1]))
        assertEquals("call_abc1", m[3].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals("tapped", m[3].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun imageGoesInAFollowingUserMessageAsImageUrlDataUri() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        session(t).send(Turn("Task: x", image = byteArrayOf(1, 2, 3)))
        val parts = t.requests.single().messages[1].parts()
        assertEquals(listOf("text", "image_url"), parts.map { it.type() })
        assertEquals(
            "data:image/png;base64,AQID",
            parts[1].getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content,
        )
    }

    @Test
    fun imageIsDroppedWhenTheModelHasNoVision() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        session(t, config(supportsImages = false)).send(Turn("Task: x", image = byteArrayOf(1)))
        assertEquals(listOf("text"), t.requests.single().messages[1].parts().map { it.type() })
    }

    @Test
    fun systemNoteIsAnAppTipsPartBeforeTheScreenText() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls")))
        session(t).send(Turn("Task: x", systemNote = "be careful"))
        val parts = t.requests.single().messages[1].parts()
        assertEquals("<app_tips>be careful</app_tips>", parts[0].text())
        assertEquals("Task: x", parts[1].text())
    }

    @Test
    fun emptyTurnTextIsOmitted() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls"), reply("stop")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("", ToolResult("call_abc1", "ok", false)))
        assertEquals(listOf("system", "user", "assistant", "tool"), roles(t.requests[1]))
    }

    @Test
    fun assistantMessageWithToolCallsIsReplayedInHistory() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls"), reply("stop")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("b", ToolResult("call_abc1", "ok", false)))
        val sent = t.requests[1].messages
        assertEquals("a", sent[1].parts().single().text())
        val assistant = sent[2].jsonObject
        assertEquals("assistant", assistant.role())
        val call = assistant.getValue("tool_calls").jsonArray.single().jsonObject
        assertEquals("call_abc1", call.getValue("id").jsonPrimitive.content)
        assertEquals("""{"node_id":"a5"}""", call.getValue("function").jsonObject.getValue("arguments").jsonPrimitive.content)
    }

    @Test
    fun aFailedSendLeavesNothingBehind() = runTest {
        val t = FakeTransport(listOf(status(401), reply("tool_calls")))
        val s = session(t)
        assertIs<LlmResult.Failed>(s.send(Turn("a")))
        s.send(Turn("a"))
        assertEquals(listOf("system", "user"), roles(t.requests[1]))
    }

    @Test
    fun emptyReplyIsNoCallAndItsMessagesAreCarriedForward() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls"), reply("empty"), reply("stop")))
        val s = session(t)
        s.send(Turn("Task: a"))
        assertEquals(LlmResult.NoCall(""), s.send(Turn("screen 2", ToolResult("call_abc1", "ok", false))))
        s.send(Turn("nudge"))
        val m = t.requests[2].messages
        assertEquals(listOf("system", "user", "assistant", "tool", "user"), roles(t.requests[2]))
        assertEquals("call_abc1", m[3].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals(listOf("screen 2", "nudge"), m[4].parts().map { it.text() })
    }

    @Test
    fun emptyFirstReplyMergesConsecutiveUserMessages() = runTest {
        val t = FakeTransport(listOf(reply("empty"), reply("stop")))
        val s = session(t)
        assertEquals(LlmResult.NoCall(""), s.send(Turn("Task: a")))
        s.send(Turn("again"))
        assertEquals(listOf("system", "user"), roles(t.requests[1]))
        assertEquals(listOf("Task: a", "again"), t.requests[1].messages[1].parts().map { it.text() })
    }

    private val screen = "<screen_data app=\"x\">" + "z".repeat(3000) + "</screen_data>"

    private suspend fun runThreeTurns(contextTokens: Int): FakeTransport {
        val t = FakeTransport(listOf(reply("tool_calls"), reply("tool_calls_2"), reply("stop")))
        val s = session(t, config(contextTokens = contextTokens))
        s.send(Turn("Task: do x\n$screen", image = byteArrayOf(1)))
        s.send(Turn("two\n$screen", ToolResult("call_abc1", "r1 $screen", false), image = byteArrayOf(2)))
        s.send(Turn("three\n$screen", ToolResult("call_back1", "r2", false), image = byteArrayOf(3)))
        return t
    }

    @Test
    fun olderObservationsAreStubbedAbove70PercentOfContext() = runTest {
        val t = runThreeTurns(contextTokens = 4000)
        val m = t.requests[2].messages
        assertEquals(
            listOf("system", "user", "assistant", "tool", "user", "assistant", "tool", "user"),
            roles(t.requests[2]),
        )
        val first = m[1].parts()
        assertEquals(listOf("text", "text"), first.map { it.type() })
        assertEquals("Task: do x\n[earlier screen omitted]", first[0].text())
        assertEquals("[earlier screenshot omitted]", first[1].text())
        val second = m[4].parts()
        assertEquals(listOf("text", "text"), second.map { it.type() })
        assertEquals("two\n[earlier screen omitted]", second[0].text())
        // tool messages, assistant messages and the latest user message are untouched
        assertTrue(m[3].jsonObject.getValue("content").jsonPrimitive.content.contains("<screen_data"))
        assertEquals("call_abc1", m[3].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals(
            "call_abc1",
            m[2].jsonObject.getValue("tool_calls").jsonArray[0].jsonObject.getValue("id").jsonPrimitive.content,
        )
        val last = m[7].parts()
        assertEquals(listOf("text", "image_url"), last.map { it.type() })
        assertTrue(last[0].text().contains("</screen_data>"))
        // trimming is applied to a copy: the first request still carried the full screen
        assertTrue(t.requests[0].messages[1].parts()[0].text().contains("zzz"))
    }

    @Test
    fun aScreenshotOnlyMessageKeepsAPlaceholderWhenTrimmed() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls"), reply("tool_calls_2"), reply("stop")))
        val s = session(t, config(contextTokens = 4000))
        s.send(Turn("Task: do x"))
        s.send(Turn("", ToolResult("call_abc1", "screenshot attached", false), image = ByteArray(9000)))
        s.send(Turn("three", ToolResult("call_back1", "r2", false)))
        val m = t.requests[2].messages
        assertEquals("user", m[4].role())
        val old = m[4].parts()
        assertEquals(listOf("text"), old.map { it.type() })
        assertEquals("[earlier screenshot omitted]", old.single().text())
        for (msg in m) {
            val c = msg.jsonObject.getValue("content")
            if (c is JsonArray) assertTrue(c.isNotEmpty())
        }
    }

    private suspend fun replayed(body: String): JsonObject {
        val t = FakeTransport(listOf(HttpResponse(200, emptyMap(), body), reply("stop")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("b"))
        return t.requests[1].messages[2].jsonObject
    }

    @Test
    fun onlyWhitelistedAssistantFieldsAreReplayed() = runTest {
        val a = replayed(
            """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":"","refusal":null,
            "reasoning":"hmm","reasoning_content":"x","annotations":[],"audio":null,
            "tool_calls":[{"id":"c1","type":"function","index":0,"function":{"name":"tap","arguments":"{\"node_id\":\"a1\"}"}}]}}]}""",
        )
        assertEquals(setOf("role", "content", "tool_calls"), a.keys)
        assertTrue(a.getValue("content") is kotlinx.serialization.json.JsonNull)
        val call = a.getValue("tool_calls").jsonArray.single().jsonObject
        assertEquals(setOf("id", "type", "function"), call.keys)
        assertEquals(setOf("name", "arguments"), call.getValue("function").jsonObject.keys)
        assertEquals("""{"node_id":"a1"}""", call.getValue("function").jsonObject.getValue("arguments").jsonPrimitive.content)
    }

    @Test
    fun extraContentOnAToolCallIsReplayedIntact() = runTest {
        val a = replayed(
            """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"reasoning":"hmm",
            "tool_calls":[{"id":"c1","type":"function","index":0,"extra_content":{"google":{"thought_signature":"sig=="}},
            "function":{"name":"back","arguments":"{}"}}]}}]}""",
        )
        assertFalse("reasoning" in a)
        val call = a.getValue("tool_calls").jsonArray.single().jsonObject
        assertEquals(setOf("id", "type", "function", "extra_content"), call.keys)
        assertEquals("""{"google":{"thought_signature":"sig=="}}""", call.getValue("extra_content").toString())
    }

    @Test
    fun aTextOnlyReplyIsReplayedWithoutToolCalls() = runTest {
        val a = replayed("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"hi","tool_calls":[],"reasoning":"r"}}]}""")
        assertEquals(setOf("role", "content"), a.keys)
        assertEquals("hi", a.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun noTrimmingBelowTheThreshold() = runTest {
        val t = runThreeTurns(contextTokens = 128_000)
        val m = t.requests[2].messages
        assertEquals(listOf("text", "image_url"), m[1].parts().map { it.type() })
        assertTrue(m[1].parts()[0].text().contains("<screen_data"))
        assertEquals(listOf("text", "image_url"), m[4].parts().map { it.type() })
    }

    @Test
    fun usageAccumulates() = runTest {
        val t = FakeTransport(listOf(reply("tool_calls"), reply("tool_calls_2")))
        val s = session(t)
        s.send(Turn("a"))
        s.send(Turn("", ToolResult("call_abc1", "ok", false)))
        assertEquals(TokenUsage(inputTokens = 100 + 10, outputTokens = 35, cachedInputTokens = 20), s.usage)
    }

    @Test
    fun httpFailuresMapToFailedWithoutTheKey() = runTest {
        val r = assertIs<LlmResult.Failed>(session(FakeTransport(listOf(status(401)))).send(Turn("a")))
        assertEquals(FailureKind.AUTH, r.kind)
        assertFalse(r.message.contains("sk-secret"))
    }

    @Test
    fun malformedRepliesAreFailedWithAFixedMessage() = runTest {
        for (body in listOf("not json", """{"choices":[]}""", """{"id":"x"}""", """[1]""")) {
            val t = FakeTransport(listOf(HttpResponse(200, emptyMap(), body)))
            val r = assertIs<LlmResult.Failed>(session(t).send(Turn("a")))
            assertEquals(FailureKind.OTHER, r.kind)
            assertFalse(r.message.contains(body))
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
}
