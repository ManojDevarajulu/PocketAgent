package dev.pocketagent.agent

import dev.pocketagent.device.ScreenNode

/**
 * The one-line caption shown next to the bubble. Built here from the action and the target's label, never from
 * text the model wrote about its own plan, so it cannot be made to say something misleading. Password fields
 * are never named.
 */
object Captions {
    private const val MAX_LABEL = 30

    val readingNotifications: String = "Reading your notifications"

    /** Sender and app come from a notification, which any app or stranger can write, so both are cut to one short line. */
    fun forReply(sender: String, app: String): String = "Replying to ${oneLine(sender)} in ${oneLine(app)}"

    fun forAction(call: ToolCall, target: ScreenNode?): String = when (call) {
        is ToolCall.Tap -> "Tapping " + (labelOf(target) ?: "an item")
        is ToolCall.TypeText -> (if (call.submit) "Searching in " else "Typing into ") + (labelOf(target) ?: "a field")
        is ToolCall.Scroll -> "Scrolling ${call.direction.name.lowercase()}"
        ToolCall.Back -> "Going back"
        ToolCall.Home -> "Going home"
        is ToolCall.OpenApp -> "Opening " + oneLine(call.name).ifEmpty { "an app" }
        is ToolCall.Directions -> "Starting directions"
        is ToolCall.Dial -> "Opening the phone app"
        is ToolCall.TextMessage -> "Writing a text message"
        is ToolCall.WebSearch -> "Searching the web"
        is ToolCall.SetAlarm -> "Setting an alarm for %02d:%02d".format(call.hour, call.minute)
        is ToolCall.SetTimer -> "Starting a timer"
        is ToolCall.OpenUrl -> "Opening a web link"
        is ToolCall.AddEvent -> "Adding a calendar event"
        ToolCall.ReadNotifications -> readingNotifications
        is ToolCall.ReplyNotification -> "Replying to a message"
        ToolCall.Screenshot -> "Taking a screenshot"
        is ToolCall.Wait -> "Waiting"
        is ToolCall.AskUser -> "Asking you"
        is ToolCall.Done -> "Finishing"
    }

    private fun labelOf(target: ScreenNode?): String? =
        target?.takeUnless { it.isPassword }?.label()?.let { oneLine(it) }?.takeIf { it.isNotEmpty() }

    /** One short line: line breaks and control characters become spaces, and long text is cut without splitting a surrogate pair. */
    internal fun oneLine(raw: String, max: Int = MAX_LABEL): String {
        val text = raw.map { if (it.isISOControl() || it == ' ' || it == ' ') ' ' else it }.joinToString("").trim()
        if (text.length <= max) return text
        var cut = max
        if (Character.isHighSurrogate(text[cut - 1])) cut--
        return text.substring(0, cut) + "…"
    }
}
