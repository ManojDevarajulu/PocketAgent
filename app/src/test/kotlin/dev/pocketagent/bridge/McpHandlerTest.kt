package dev.pocketagent.bridge

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpHandlerTest {
    private val asked = ArrayList<String>()
    private var reply: TaskReply = TaskReply.Done(true, "Played it.")
    private val handler = McpHandler { task -> asked.add(task); reply }

    private suspend fun rpc(body: String): JsonObject = Json.parseToJsonElement(handler.handle(body)!!).jsonObject

    @Test fun initializeAnswersWithTheClientsVersionWhenSupported() = runTest {
        val r = rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}""")
        assertEquals("2024-11-05", r["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("pocket-agent", r["result"]!!.jsonObject["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val unknown = rpc("""{"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}""")
        assertEquals("2025-06-18", unknown["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
    }

    @Test fun notificationsGetNoReply() = runTest {
        assertNull(handler.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
    }

    @Test fun listsExactlyOneTool() = runTest {
        val tools = rpc("""{"jsonrpc":"2.0","id":3,"method":"tools/list"}""")["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(listOf("run_task"), tools.map { it.jsonObject["name"]!!.jsonPrimitive.content })
    }

    @Test fun runTaskPassesTheTaskToThePhoneAndReturnsItsSummary() = runTest {
        val r = rpc("""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"run_task","arguments":{"task":" play lofi "}}}""")
        assertEquals(listOf("play lofi"), asked)
        val result = r["result"]!!.jsonObject
        assertEquals("Played it.", result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(false, result["isError"]!!.jsonPrimitive.boolean)
    }

    @Test fun aFailedOrRefusedTaskIsAnErrorResultNotAProtocolError() = runTest {
        reply = TaskReply.Refused("The phone is locked.")
        val r = rpc("""{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"run_task","arguments":{"task":"x"}}}""")
        assertEquals(true, r["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean)
        assertEquals("The phone is locked.", r["result"]!!.jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        reply = TaskReply.Done(false, "Could not.")
        val r2 = rpc("""{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"run_task","arguments":{"task":"x"}}}""")
        assertEquals(true, r2["result"]!!.jsonObject["isError"]!!.jsonPrimitive.boolean)
    }

    @Test fun badArgumentsNeverReachThePhone() = runTest {
        val bad = listOf(
            """{"name":"run_task","arguments":{"task":""}}""",
            """{"name":"run_task","arguments":{"task":"${"a".repeat(2001)}"}}""",
            """{"name":"run_task","arguments":{"task":"x","extra":1}}""",
            """{"name":"run_task","arguments":{"task":5}}""",
            """{"name":"run_task","arguments":{"task":"bell\u0007"}}""",
            """{"name":"run_task"}""",
            """{"name":"read_screen","arguments":{}}""",
        )
        for (params in bad) {
            val r = rpc("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":$params}""")
            assertEquals(-32602, r["error"]!!.jsonObject["code"]!!.jsonPrimitive.int, params)
        }
        assertTrue(asked.isEmpty(), asked.toString())
    }

    @Test fun malformedAndUnknownRequestsGetProtocolErrors() = runTest {
        assertEquals(-32700, rpc("{not json")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(-32600, rpc("[1,2]")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(-32600, rpc("""{"jsonrpc":"2.0","id":{"a":1},"method":"ping"}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(-32601, rpc("""{"jsonrpc":"2.0","id":8,"method":"resources/list"}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    @Test fun theIdIsEchoedBack() = runTest {
        assertEquals("abc", rpc("""{"jsonrpc":"2.0","id":"abc","method":"ping"}""")["id"]!!.jsonPrimitive.content)
    }
}
