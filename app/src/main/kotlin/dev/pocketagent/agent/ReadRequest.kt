package dev.pocketagent.agent

private val readTriggers = listOf(
    "read", "tell me", "what does", "what did", "what's on", "summarize", "summarise",
    "padh", "padho", "batao", "bata", "सुनाओ", "पढ़ो", "बताओ",
)

/** Each trigger as a whole word (or phrase), so "ready", "already" or "thread" never count as "read". Works for Devanagari too. */
private val readPatterns = readTriggers.map { Regex("(?<![\\p{L}\\p{M}])" + Regex.escape(it) + "(?![\\p{L}\\p{M}])") }

/** True if the task asks to hear content (a message, a notification) rather than act on it. */
fun asksToRead(task: String): Boolean {
    // A typographic apostrophe ("what’s on") counts as a plain one.
    val text = task.lowercase().replace('’', '\'')
    return readPatterns.any { it.containsMatchIn(text) }
}
