package dev.pocketagent.agent

private val readTriggers = listOf(
    "read", "tell me", "what does", "what did", "what's on", "summarize", "summarise",
    "padh", "padho", "batao", "bata", "सुनाओ", "पढ़ो", "बताओ",
)

/** True if the task asks to hear content (a message, a notification) rather than act on it. */
fun asksToRead(task: String): Boolean {
    val text = task.lowercase()
    return readTriggers.any { it in text }
}
