package dev.pocketagent.agent

/** One suggestion on the task screen: what the user sees ([title], [subtitle]) and the full task it sends ([task]). */
data class QuickTask(val title: String, val subtitle: String, val task: String)

/**
 * The suggestions on the task screen. The title is a short label, but the task is written out in full: a model
 * that is only told "Take me to an address" has to guess the app and the goal, and small or free models often
 * guess wrong. The user sees the full task in the box and can edit it before sending.
 */
object QuickTasks {
    /** The task box accepts this many characters. */
    const val MAX_TASK_CHARS = 2000

    val all: List<QuickTask> = listOf(
        QuickTask(
            title = "Take me to an address",
            subtitle = "From a message or a map link",
            task = "Find the address in the message on this screen and start directions to it with the directions tool. " +
                "Only if that fails, open Google Maps, clear any old text in its search box, then type the address.",
        ),
        QuickTask(
            title = "Read this screen out",
            subtitle = "A short summary of what is here",
            task = "Read what is on this screen and tell me a short summary of it. Do not change anything.",
        ),
        QuickTask(
            title = "Reply to this",
            subtitle = "Drafts it, asks before sending",
            task = "Write a short, polite reply to the latest message on this screen and type it into the reply box, " +
                "then press send.",
        ),
    )
}
