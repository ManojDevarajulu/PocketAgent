package dev.pocketagent.device

/**
 * One chat message as a notification line. The phone owner's own messages are marked "Me: ", so the model can tell
 * them apart and never drafts a reply to the user's own last line. Another sender keeps their name; a sender with
 * no name gives just the text.
 */
internal fun messageLine(sender: String?, fromOwner: Boolean, text: String): String = when {
    fromOwner -> "Me: $text"
    sender.isNullOrBlank() -> text
    else -> "$sender: $text"
}
