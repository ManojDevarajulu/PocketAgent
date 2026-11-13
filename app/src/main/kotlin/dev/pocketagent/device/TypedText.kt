package dev.pocketagent.device

/**
 * Checks that a field really holds the text the agent asked to type. Android's "set text" can report success
 * while an app ignores it (Google Maps, for one, stacks two input fields in its search box), and the agent
 * would then believe the text was entered and keep trying. Only case and runs of whitespace are ignored.
 */
object TypedText {
    fun matches(requested: String, actual: CharSequence?): Boolean =
        actual != null && normalise(actual) == normalise(requested)

    private fun normalise(text: CharSequence): String =
        text.toString().trim().replace(WHITESPACE, " ").lowercase()

    private val WHITESPACE = Regex("\\s+")
}
