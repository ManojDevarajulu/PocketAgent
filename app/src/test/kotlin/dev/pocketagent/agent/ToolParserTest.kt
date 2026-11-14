package dev.pocketagent.agent

import dev.pocketagent.device.Direction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolParserTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    private fun parse(tool: String, text: String = "{}") = Tools.parse(tool, json(text))
    private fun message(result: Result<ToolCall>) = result.exceptionOrNull()?.message.orEmpty()

    private fun assertRejected(result: Result<ToolCall>, expected: String) {
        assertTrue(result.isFailure, "expected a failure but got ${result.getOrNull()}")
        assertTrue(message(result).contains(expected), "message \"${message(result)}\" should mention \"$expected\"")
    }

    // ---- tap ----

    @Test
    fun parsesTapByNode() {
        assertEquals(ToolCall.Tap("a3", null, null, false), parse("tap", """{"node_id":"a3"}""").getOrThrow())
    }

    @Test
    fun parsesTapByCoordinatesWithIrreversible() {
        assertEquals(ToolCall.Tap(null, 10, 20, true), parse("tap", """{"x":10,"y":20,"irreversible":true}""").getOrThrow())
    }

    @Test
    fun rejectsTapWithBothNodeAndCoordinates() {
        assertRejected(parse("tap", """{"node_id":"a3","x":10,"y":20}"""), "either node_id or x and y")
    }

    @Test
    fun rejectsTapWithNeither() {
        assertRejected(parse("tap", """{"irreversible":false}"""), "either node_id or x and y")
    }

    @Test
    fun rejectsTapWithOnlyOneCoordinate() {
        assertRejected(parse("tap", """{"x":10}"""), "either node_id or x and y")
        assertRejected(parse("tap", """{"node_id":"a3","x":10}"""), "either node_id or x and y")
        assertRejected(parse("tap", """{"node_id":"a3","y":10}"""), "either node_id or x and y")
    }

    @Test
    fun rejectsNegativeAndNonIntegerCoordinates() {
        assertRejected(parse("tap", """{"x":-1,"y":5}"""), "x must be an integer")
        assertRejected(parse("tap", """{"x":10.5,"y":5}"""), "x must be an integer")
        assertRejected(parse("tap", """{"x":"10","y":5}"""), "x must be an integer")
    }

    @Test
    fun rejectsBadNodeIds() {
        assertRejected(parse("tap", """{"node_id":""}"""), "node_id must be 1 to 20 characters")
        assertRejected(parse("tap", """{"node_id":"${"a".repeat(21)}"}"""), "node_id must be 1 to 20 characters")
        assertRejected(parse("tap", """{"node_id":12}"""), "node_id must be a string")
        assertRejected(parse("tap", """{"node_id":null}"""), "node_id must be a string")
        assertRejected(parse("tap", """{"node_id":{"id":"a1"}}"""), "node_id must be a string")
    }

    @Test
    fun irreversibleMustBeABoolean() {
        assertRejected(parse("tap", """{"node_id":"a1","irreversible":"true"}"""), "irreversible must be true or false")
    }

    // ---- type, scroll, open_app, wait, ask_user, done ----

    @Test
    fun parsesTypeAndEnforcesLengths() {
        assertEquals(ToolCall.TypeText("a1", "hello"), parse("type", """{"node_id":"a1","text":"hello"}""").getOrThrow())
        assertTrue(parse("type", """{"node_id":"a1","text":"${"x".repeat(1000)}"}""").isSuccess)
        assertRejected(parse("type", """{"node_id":"a1","text":""}"""), "text must be 1 to 1000 characters")
        assertRejected(parse("type", """{"node_id":"a1","text":"${"x".repeat(1001)}"}"""), "text must be 1 to 1000 characters")
        assertRejected(parse("type", """{"text":"hello"}"""), "missing field: node_id")
        assertEquals(ToolCall.TypeText("a1", "milk", submit = true), parse("type", """{"node_id":"a1","text":"milk","submit":true}""").getOrThrow())
        assertRejected(parse("type", """{"node_id":"a1","text":"milk","submit":"yes"}"""), "submit must be true or false")
    }

    @Test
    fun parsesScrollInEveryDirection() {
        mapOf("up" to Direction.UP, "down" to Direction.DOWN, "left" to Direction.LEFT, "right" to Direction.RIGHT).forEach { (word, direction) ->
            assertEquals(ToolCall.Scroll(direction, null), parse("scroll", """{"direction":"$word"}""").getOrThrow())
        }
        assertEquals(ToolCall.Scroll(Direction.DOWN, "a7"), parse("scroll", """{"direction":"down","node_id":"a7"}""").getOrThrow())
    }

    @Test
    fun rejectsBadScrollDirection() {
        assertRejected(parse("scroll", """{"direction":"diagonal"}"""), "direction must be one of up, down, left, right")
        assertRejected(parse("scroll", """{"direction":"UP"}"""), "direction must be one of")
        assertRejected(parse("scroll", "{}"), "missing field: direction")
    }

    @Test
    fun parsesOpenAppAndEnforcesLength() {
        assertEquals(ToolCall.OpenApp("WhatsApp"), parse("open_app", """{"name":"WhatsApp"}""").getOrThrow())
        assertRejected(parse("open_app", """{"name":""}"""), "name must be 1 to 100 characters")
        assertRejected(parse("open_app", """{"name":"${"x".repeat(101)}"}"""), "name must be 1 to 100 characters")
    }

    @Test
    fun openAppRejectsPackageUrlAndIntentFields() {
        listOf("package", "url", "intent").forEach { field ->
            assertRejected(parse("open_app", """{"name":"WhatsApp","$field":"x"}"""), "unexpected field: $field")
        }
    }

    @Test
    fun rejectsWaitOutOfRange() {
        assertRejected(parse("wait", """{"seconds":0}"""), "seconds must be an integer from 1 to 5")
        assertRejected(parse("wait", """{"seconds":6}"""), "seconds must be an integer from 1 to 5")
        assertRejected(parse("wait", """{"seconds":"3"}"""), "seconds must be an integer from 1 to 5")
        assertEquals(ToolCall.Wait(1), parse("wait", """{"seconds":1}""").getOrThrow())
        assertEquals(ToolCall.Wait(5), parse("wait", """{"seconds":5}""").getOrThrow())
    }

    @Test
    fun parsesAskUserAndDone() {
        assertEquals(ToolCall.AskUser("Which Mom?"), parse("ask_user", """{"question":"Which Mom?"}""").getOrThrow())
        assertRejected(parse("ask_user", """{"question":"${"q".repeat(501)}"}"""), "question must be 1 to 500 characters")
        assertEquals(ToolCall.Done("Sent it", true), parse("done", """{"summary":"Sent it","success":true}""").getOrThrow())
        assertRejected(parse("done", """{"summary":"Sent it"}"""), "missing field: success")
        assertRejected(parse("done", """{"summary":"","success":true}"""), "summary must be 1 to 500 characters")
    }

    @Test
    fun toolsWithoutInputsTakeNoFields() {
        assertEquals(ToolCall.Back, parse("back").getOrThrow())
        assertEquals(ToolCall.Home, parse("home").getOrThrow())
        assertEquals(ToolCall.Screenshot, parse("screenshot").getOrThrow())
        assertRejected(parse("back", """{"x":1}"""), "unexpected field: x")
    }

    @Test
    fun rejectsUnknownTool() {
        assertRejected(parse("swipe"), "unknown tool: swipe")
    }

    // ---- definitions ----

    @Test
    fun definitionNamesMatchSpec() {
        val names = Tools.definitions.map { it.name }
        assertEquals(20, names.size)
        assertEquals(
            setOf(
                "tap", "type", "scroll", "back", "home", "open_app", "screenshot", "wait", "ask_user", "done",
                "directions", "dial", "text_message", "web_search", "read_notifications", "reply_notification",
                "set_alarm", "set_timer", "open_url", "add_calendar_event",
            ),
            names.toSet(),
        )
    }

    @Test
    fun everyDefinitionIsStrictCompatible() {
        val unsupported = setOf("minLength", "maxLength", "minimum", "maximum", "pattern", "format", "oneOf", "anyOf", "allOf")
        Tools.definitions.forEach { def ->
            val schema = def.inputSchema
            assertEquals("object", schema.getValue("type").jsonPrimitive.content, def.name)
            assertFalse(schema.getValue("additionalProperties").jsonPrimitive.boolean, "${def.name} must forbid extra fields")
            val properties = schema.getValue("properties").jsonObject
            schema.getValue("required").jsonArray.forEach {
                assertTrue(it.jsonPrimitive.content in properties, "${def.name}: required ${it.jsonPrimitive.content} is not a property")
            }
            assertTrue(def.description.isNotBlank(), "${def.name} needs a description")
            assertTrue(unsupported.none { "\"$it\":" in schema.toString() }, "${def.name} uses a keyword strict mode does not support")
        }
    }

    @Test
    fun everySchemaPropertyIsAcceptedByTheParser() {
        Tools.definitions.forEach { def ->
            val properties = def.inputSchema.getValue("properties").jsonObject
            properties.forEach { (field, spec) ->
                val sample: JsonPrimitive = when (spec.jsonObject.getValue("type").jsonPrimitive.content) {
                    "integer" -> JsonPrimitive(1)
                    "boolean" -> JsonPrimitive(true)
                    else -> JsonPrimitive(spec.jsonObject["enum"]?.jsonArray?.first()?.jsonPrimitive?.content ?: "a1")
                }
                val result = Tools.parse(def.name, JsonObject(mapOf(field to sample)))
                assertFalse(message(result).contains("unexpected field"), "${def.name}.$field is in the schema but the parser rejects it")
            }
        }
    }

    @Test
    fun requiredListMatchesWhatTheParserRequires() {
        val tap = Tools.definitions.single { it.name == "tap" }
        assertEquals(JsonArray(emptyList()), tap.inputSchema.getValue("required"), "tap fields are all optional individually")
        val done = Tools.definitions.single { it.name == "done" }
        assertEquals(setOf("summary", "success"), done.inputSchema.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet())
    }

    // ---- one-step shortcuts ----

    @Test
    fun parsesTheShortcuts() {
        assertEquals(ToolCall.Directions("221B Baker St"), parse("directions", """{"destination":"221B Baker St"}""").getOrThrow())
        assertEquals(ToolCall.Dial("+44 20 7946 0958"), parse("dial", """{"number":"+44 20 7946 0958"}""").getOrThrow())
        assertEquals(ToolCall.TextMessage("5551234", "on my way"), parse("text_message", """{"number":"5551234","body":"on my way"}""").getOrThrow())
        assertEquals(ToolCall.WebSearch("weather today"), parse("web_search", """{"query":"weather today"}""").getOrThrow())
    }

    @Test
    fun parsesOpenUrlOnlyForWebLinks() {
        assertEquals(ToolCall.OpenUrl("https://example.com/a?b=1"), parse("open_url", """{"url":"https://example.com/a?b=1"}""").getOrThrow())
        listOf("file:///sdcard/x.txt", "intent://x#Intent;end", "javascript:alert(1)", "https://a.com/x y", "example.com/page").forEach {
            assertTrue(parse("open_url", """{"url":"$it"}""").isFailure, it)
        }
    }

    @Test
    fun parsesCalendarEvent() {
        val ok = parse("add_calendar_event", """{"title":"Dentist","date":"2026-11-20","time":"15:30","duration_minutes":45}""").getOrThrow()
        assertEquals(ToolCall.AddEvent("Dentist", java.time.LocalDateTime.of(2026, 11, 20, 15, 30), 45), ok)
        assertTrue(parse("add_calendar_event", """{"title":"x","date":"2026-13-40","time":"15:30","duration_minutes":45}""").isFailure)
        assertTrue(parse("add_calendar_event", """{"title":"x","date":"2026-11-20","time":"25:61","duration_minutes":45}""").isFailure)
        assertTrue(parse("add_calendar_event", """{"title":"x","date":"2026-11-20","time":"15:30","duration_minutes":0}""").isFailure)
    }

    @Test
    fun parsesAlarmAndTimer() {
        assertEquals(ToolCall.SetAlarm(6, 30), parse("set_alarm", """{"hour":6,"minute":30}""").getOrThrow())
        assertEquals(ToolCall.SetAlarm(0, 0), parse("set_alarm", """{"hour":0,"minute":0}""").getOrThrow())
        assertEquals(ToolCall.SetTimer(600), parse("set_timer", """{"seconds":600}""").getOrThrow())
        assertEquals(ToolCall.SetTimer(86_400), parse("set_timer", """{"seconds":86400}""").getOrThrow())
    }

    @Test
    fun alarmAndTimerRejectOutOfRangeValues() {
        assertRejected(parse("set_alarm", """{"hour":24,"minute":0}"""), "hour must be an integer from 0 to 23")
        assertRejected(parse("set_alarm", """{"hour":6,"minute":60}"""), "minute must be an integer from 0 to 59")
        assertRejected(parse("set_alarm", """{"hour":6}"""), "missing field: minute")
        assertRejected(parse("set_timer", """{"seconds":0}"""), "seconds must be an integer from 1 to 86400")
        assertRejected(parse("set_timer", """{"seconds":86401}"""), "seconds must be an integer from 1 to 86400")
        assertRejected(parse("set_timer", """{"seconds":60,"label":"x"}"""), "unexpected field: label")
    }

    @Test
    fun shortcutsRejectBadInput() {
        assertRejected(parse("dial", """{"number":"call mom"}"""), "number must be a phone number")
        assertRejected(parse("dial", """{"number":"+-()"}"""), "number must be a phone number")
        assertRejected(parse("dial", """{"number":"tel:555*1234"}"""), "number must be a phone number")
        assertRejected(parse("text_message", """{"number":"5551234"}"""), "missing field: body")
        assertRejected(parse("directions", """{"destination":""}"""), "destination must be 1 to 300 characters")
        assertRejected(parse("web_search", """{"query":"x","extra":1}"""), "unexpected field: extra")
    }

    // ---- notifications ----

    @Test
    fun parsesReadNotifications() {
        assertEquals(ToolCall.ReadNotifications, parse("read_notifications").getOrThrow())
    }

    @Test
    fun readNotificationsRejectsAnyField() {
        assertRejected(parse("read_notifications", """{"limit":5}"""), "unexpected field: limit")
        assertRejected(parse("read_notifications", """{"id":"n1"}"""), "unexpected field: id")
    }

    @Test
    fun parsesReplyNotification() {
        assertEquals(ToolCall.ReplyNotification("n2", "on my way"), parse("reply_notification", """{"id":"n2","text":"on my way"}""").getOrThrow())
        assertEquals(ToolCall.ReplyNotification("n123", "x"), parse("reply_notification", """{"id":"n123","text":"x"}""").getOrThrow())
        val longest = "a".repeat(200)
        assertEquals(longest, (parse("reply_notification", """{"id":"n1","text":"$longest"}""").getOrThrow() as ToolCall.ReplyNotification).text)
    }

    @Test
    fun rejectsBadNotificationIds() {
        for (bad in listOf("x1", "n", "n1234", "N1", " n1", "n1 ", "n-1", "1")) {
            assertRejected(parse("reply_notification", """{"id":"$bad","text":"hi"}"""), "id must be")
        }
    }

    @Test
    fun notificationIdRegexSemantics() {
        assertEquals(ToolCall.ReplyNotification("n01", "hi"), parse("reply_notification", """{"id":"n01","text":"hi"}""").getOrThrow())
        assertRejected(parse("reply_notification", """{"id":"n1\n","text":"hi"}"""), "id must be")
        assertRejected(parse("reply_notification", "{\"id\":\"n१\",\"text\":\"hi\"}"), "id must be")
    }

    @Test
    fun replyToolDescriptionStatesTheLimit() {
        val reply = Tools.definitions.single { it.name == "reply_notification" }
        assertTrue(reply.description.contains("200 characters"), reply.description)
    }

    @Test
    fun rejectsBadReplyText() {
        assertRejected(parse("reply_notification", """{"id":"n1","text":""}"""), "text must be 1 to 200 characters")
        // The card shows at most 200 characters, so a reply may not be longer than what the user sees.
        val tooLong = "a".repeat(201)
        assertRejected(parse("reply_notification", """{"id":"n1","text":"$tooLong"}"""), "text must be 1 to 200 characters")
        assertRejected(parse("reply_notification", """{"id":"n1","text":5}"""), "text must be a string")
    }

    @Test
    fun rejectsReplyWithMissingOrExtraFields() {
        assertRejected(parse("reply_notification", """{"text":"hi"}"""), "missing field: id")
        assertRejected(parse("reply_notification", """{"id":"n1"}"""), "missing field: text")
        assertRejected(parse("reply_notification", """{"id":"n1","text":"hi","send":true}"""), "unexpected field: send")
    }

    @Test
    fun notificationToolSetNamesExactlyTheTwoTools() {
        assertEquals(setOf("read_notifications", "reply_notification"), Tools.NOTIFICATION_TOOLS)
        assertTrue(Tools.NOTIFICATION_TOOLS.all { name -> Tools.definitions.any { it.name == name } })
    }
}
