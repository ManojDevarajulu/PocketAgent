package dev.pocketagent.agent

import dev.pocketagent.device.Direction
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One validated action chosen by the model. */
sealed interface ToolCall {
    data class Tap(val nodeId: String?, val x: Int?, val y: Int?, val irreversible: Boolean) : ToolCall
    data class TypeText(val nodeId: String, val text: String, val submit: Boolean = false) : ToolCall
    data class Scroll(val direction: Direction, val nodeId: String?) : ToolCall
    data object Back : ToolCall
    data object Home : ToolCall
    data class OpenApp(val name: String) : ToolCall
    data class Directions(val destination: String) : ToolCall
    data class Dial(val number: String) : ToolCall
    data class TextMessage(val number: String, val body: String) : ToolCall
    data class WebSearch(val query: String) : ToolCall
    data object Screenshot : ToolCall
    data class Wait(val seconds: Int) : ToolCall
    data class AskUser(val question: String) : ToolCall
    data class Done(val summary: String, val success: Boolean) : ToolCall
}

data class ToolDefinition(val name: String, val description: String, val inputSchema: JsonObject)

/**
 * The tools the model may call. One table drives both the JSON schemas sent to the model and the checks applied
 * to what comes back, so the two cannot drift apart. Model output is never trusted: every field is checked for
 * type, length and range, and any field that is not in the schema is rejected.
 */
object Tools {
    private enum class Kind { STRING, INTEGER, BOOLEAN, CHOICE }

    private class Field(
        val name: String,
        val kind: Kind,
        val description: String,
        val required: Boolean = true,
        val min: Int = 0,
        val max: Int = 0,
        val choices: List<String> = emptyList(),
    )

    private class Spec(val name: String, val description: String, val fields: List<Field>, val build: (Map<String, Any>) -> ToolCall)

    private const val MAX_COORDINATE = 100_000

    /** Digits with the usual separators, and at least three digits. Anything else is not a phone number. */
    private val PHONE_NUMBER = Regex("""\+?[0-9 ()*#.\-]{3,30}""")

    private fun phoneNumber(raw: String): String {
        require(PHONE_NUMBER.matches(raw) && raw.count { it.isDigit() } >= 3) { "number must be a phone number made of digits" }
        return raw
    }

    private fun numberField() = Field("number", Kind.STRING, "The phone number, digits only, with an optional leading +.", min = 3, max = 30)
    private val DIRECTIONS = Direction.entries.associateBy { it.name.lowercase() }

    private fun nodeId(description: String, required: Boolean) =
        Field("node_id", Kind.STRING, description, required, min = 1, max = 20)

    private val specs: List<Spec> = listOf(
        Spec(
            "tap",
            "Tap an element by its node_id from the screen data. Use x and y only when the element has no node_id, and never give both.",
            listOf(
                nodeId("The node_id of the element to tap.", required = false),
                Field("x", Kind.INTEGER, "Horizontal pixel position, always together with y.", required = false, min = 0, max = MAX_COORDINATE),
                Field("y", Kind.INTEGER, "Vertical pixel position, always together with x.", required = false, min = 0, max = MAX_COORDINATE),
                Field("irreversible", Kind.BOOLEAN, "Set to true if this tap sends, pays, posts or deletes anything.", required = false),
            ),
        ) { a ->
            val nodeId = a["node_id"] as String?
            val x = a["x"] as Int?
            val y = a["y"] as Int?
            val onePointCoordinate = (x != null) != (y != null)
            val hasCoordinates = x != null && y != null
            require(!onePointCoordinate && hasCoordinates != (nodeId != null)) {
                "give either node_id or x and y, never both and never neither"
            }
            ToolCall.Tap(nodeId, x, y, a["irreversible"] as Boolean? ?: false)
        },
        Spec(
            "type",
            "Replace the text of an editable field with the given text.",
            listOf(
                nodeId("The node_id of the editable field.", required = true),
                Field("text", Kind.STRING, "The text to enter.", min = 1, max = 1000),
                Field(
                    "submit",
                    Kind.BOOLEAN,
                    "Set to true to press the keyboard's Enter or Search key after typing, so a search runs in the same " +
                        "step. Use it for search boxes and address bars, not for message boxes: tap their Send button instead.",
                    required = false,
                ),
            ),
        ) { a -> ToolCall.TypeText(a["node_id"] as String, a["text"] as String, a["submit"] as Boolean? ?: false) },
        Spec(
            "scroll",
            "Scroll the screen, or a scrollable element, in a direction.",
            listOf(
                Field("direction", Kind.CHOICE, "Which way to scroll.", choices = DIRECTIONS.keys.toList()),
                nodeId("The node_id of the scrollable element. Leave out to scroll the whole screen.", required = false),
            ),
        ) { a -> ToolCall.Scroll(DIRECTIONS.getValue(a["direction"] as String), a["node_id"] as String?) },
        Spec("back", "Press the Back button.", emptyList()) { ToolCall.Back },
        Spec("home", "Go to the home screen.", emptyList()) { ToolCall.Home },
        Spec(
            "open_app",
            "Open an installed app by its name, for example WhatsApp.",
            listOf(Field("name", Kind.STRING, "The name of the app.", min = 1, max = 100)),
        ) { a -> ToolCall.OpenApp(a["name"] as String) },
        Spec(
            "directions",
            "Start turn-by-turn directions to an address or place in the maps app, in one step. Use it for any route or address.",
            listOf(Field("destination", Kind.STRING, "The full address or place name.", min = 1, max = 300)),
        ) { a -> ToolCall.Directions(a["destination"] as String) },
        Spec(
            "dial",
            "Open the phone app with a number typed in. It does not call: the call button is a separate tap.",
            listOf(numberField()),
        ) { a -> ToolCall.Dial(phoneNumber(a["number"] as String)) },
        Spec(
            "text_message",
            "Open a new SMS to a phone number with the message filled in. It does not send: the send button is a separate tap.",
            listOf(numberField(), Field("body", Kind.STRING, "The message text.", min = 1, max = 1000)),
        ) { a -> ToolCall.TextMessage(phoneNumber(a["number"] as String), a["body"] as String) },
        Spec(
            "web_search",
            "Search the web for a query in one step. Not for addresses or routes: use directions for those.",
            listOf(Field("query", Kind.STRING, "What to search for.", min = 1, max = 300)),
        ) { a -> ToolCall.WebSearch(a["query"] as String) },
        Spec("screenshot", "Get a screenshot of the current screen. Use it only when the screen data lacks what you need.", emptyList()) { ToolCall.Screenshot },
        Spec(
            "wait",
            "Wait for slow content to load.",
            listOf(Field("seconds", Kind.INTEGER, "How long to wait.", min = 1, max = 5)),
        ) { a -> ToolCall.Wait(a["seconds"] as Int) },
        Spec(
            "ask_user",
            "Ask the user a question and wait for the answer. Use it when the task is ambiguous or you need something only the user knows.",
            listOf(Field("question", Kind.STRING, "The question to ask.", min = 1, max = 500)),
        ) { a -> ToolCall.AskUser(a["question"] as String) },
        Spec(
            "done",
            "End the task. Set success to false if the task could not be completed.",
            listOf(
                Field("summary", Kind.STRING, "What was done, or why it could not be done.", min = 1, max = 500),
                Field("success", Kind.BOOLEAN, "Whether the task was completed."),
            ),
        ) { a -> ToolCall.Done(a["summary"] as String, a["success"] as Boolean) },
    )

    val definitions: List<ToolDefinition> = specs.map { ToolDefinition(it.name, it.description, schemaOf(it)) }

    fun parse(name: String, input: JsonObject): Result<ToolCall> {
        val spec = specs.firstOrNull { it.name == name }
            ?: return Result.failure(IllegalArgumentException("unknown tool: $name"))
        return try {
            input.keys.firstOrNull { key -> spec.fields.none { it.name == key } }
                ?.let { throw IllegalArgumentException("unexpected field: $it") }
            val args = HashMap<String, Any>()
            for (field in spec.fields) {
                val element = input[field.name]
                if (element == null) {
                    require(!field.required) { "missing field: ${field.name}" }
                    continue
                }
                args[field.name] = validate(field, element)
            }
            Result.success(spec.build(args))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    private fun validate(field: Field, element: JsonElement): Any {
        val primitive = element as? JsonPrimitive
        val text = primitive?.takeIf { it.isString }?.content
        return when (field.kind) {
            Kind.STRING -> {
                require(text != null) { "${field.name} must be a string" }
                require(text.length in field.min..field.max) { "${field.name} must be ${field.min} to ${field.max} characters" }
                text
            }
            Kind.CHOICE -> {
                require(text != null && text in field.choices) { "${field.name} must be one of ${field.choices.joinToString(", ")}" }
                text
            }
            Kind.INTEGER -> {
                val number = primitive?.takeUnless { it.isString }?.content?.toIntOrNull()
                require(number != null && number in field.min..field.max) {
                    "${field.name} must be an integer from ${field.min} to ${field.max}"
                }
                number
            }
            Kind.BOOLEAN -> {
                val flag = primitive?.takeUnless { it.isString }?.content?.toBooleanStrictOrNull()
                requireNotNull(flag) { "${field.name} must be true or false" }
            }
        }
    }

    /** Only keywords that strict tool use supports. Length and range limits go in the description and are enforced by [parse]. */
    private fun schemaOf(spec: Spec): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            for (field in spec.fields) {
                putJsonObject(field.name) {
                    put("type", if (field.kind == Kind.INTEGER) "integer" else if (field.kind == Kind.BOOLEAN) "boolean" else "string")
                    put("description", describe(field))
                    if (field.kind == Kind.CHOICE) putJsonArray("enum") { field.choices.forEach { add(it) } }
                }
            }
        }
        putJsonArray("required") { spec.fields.filter { it.required }.forEach { add(it.name) } }
        put("additionalProperties", false)
    }

    private fun describe(field: Field): String = when (field.kind) {
        Kind.STRING -> "${field.description} (${field.min} to ${field.max} characters)"
        Kind.INTEGER -> "${field.description} (${field.min} to ${field.max})"
        else -> field.description
    }
}
