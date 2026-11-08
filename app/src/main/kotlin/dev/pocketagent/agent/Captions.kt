package dev.pocketagent.agent

import dev.pocketagent.device.ScreenNode

/**
 * The one-line caption shown next to the bubble. Built here from the action and the target's label, never from
 * text the model wrote about its own plan, so it cannot be made to say something misleading. Password fields
 * are never named.
 */
object Captions {
    private const val MAX_LABEL = 30

    fun forAction(call: ToolCall, target: ScreenNode?): String = when (call) {
        is ToolCall.Tap -> "Tapping " + (labelOf(target) ?: "an item")
        is ToolCall.TypeText -> "Typing into " + (labelOf(target) ?: "a field")
        is ToolCall.Scroll -> "Scrolling ${call.direction.name.lowercase()}"
        ToolCall.Back -> "Going back"
        ToolCall.Home -> "Going home"
        is ToolCall.OpenApp -> "Opening " + shorten(call.name).ifEmpty { "an app" }
        ToolCall.Screenshot -> "Taking a screenshot"
        is ToolCall.Wait -> "Waiting"
        is ToolCall.AskUser -> "Asking you"
        is ToolCall.Done -> "Finishing"
    }

    private fun labelOf(target: ScreenNode?): String? =
        target?.takeUnless { it.isPassword }?.label()?.let { shorten(it) }?.takeIf { it.isNotEmpty() }

    /** One short line: line breaks and control characters become spaces, and long text is cut without splitting a surrogate pair. */
    private fun shorten(raw: String): String {
        val text = raw.map { if (it.isISOControl() || it == ' ' || it == ' ') ' ' else it }.joinToString("").trim()
        if (text.length <= MAX_LABEL) return text
        var cut = MAX_LABEL
        if (Character.isHighSurrogate(text[cut - 1])) cut--
        return text.substring(0, cut) + "…"
    }
}
