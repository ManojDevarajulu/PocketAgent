package dev.pocketagent.device

/**
 * A shorter way to show a screen the model has mostly seen already. Every node the model could act on (clickable,
 * editable or scrollable) is always listed, with its fresh id, so nothing it can tap is ever missing and the rule
 * that ids belong to the latest screen only is untouched. Only plain text lines that are identical to a line of the
 * previous screen are left out, replaced by a count, because the model already has that text in its history.
 */
object ScreenDiff {
    private val ID = Regex("""\[[a-z]\d+\]""")
    private const val ACTIONABLE_1 = " clickable"
    private const val ACTIONABLE_2 = " editable"
    private const val ACTIONABLE_3 = " scrollable"

    /** The shortened screen, or null when the full one should be sent. [minSaving] is the smallest worthwhile cut, 0 to 1. */
    fun shorten(previous: String?, current: String, minSaving: Double = 0.25): String? {
        if (previous == null) return null
        val before = parse(previous) ?: return null
        val now = parse(current) ?: return null
        // A different app or a different label is a new screen: show it whole.
        if (before.header != now.header) return null
        val seen = HashMap<String, Int>()
        before.lines.forEach { seen.merge(key(it), 1, Int::plus) }

        val out = StringBuilder()
        var omitted = 0
        fun flush() {
            if (omitted > 0) out.append("... ").append(omitted).append(" unchanged text lines omitted\n")
            omitted = 0
        }
        for (line in now.lines) {
            val k = key(line)
            val left = seen[k] ?: 0
            if (!actionable(line) && left > 0) {
                seen[k] = left - 1
                omitted++
            } else {
                flush()
                if (left > 0) seen[k] = left - 1
                out.append(line).append('\n')
            }
        }
        flush()
        val body = out.toString()
        val shortened = now.header.replaceFirst(">", " diff=\"true\">") + "\n" + body + "</screen_data>"
        return if (shortened.length <= current.length * (1 - minSaving)) shortened else null
    }

    private class Parsed(val header: String, val lines: List<String>)

    private fun parse(text: String): Parsed? {
        val lines = text.split('\n')
        if (lines.size < 2 || !lines.first().startsWith("<screen_data ") || lines.last() != "</screen_data>") return null
        val header = lines.first()
        // A truncated screen has a marker line; do not try to be clever about it.
        if (lines.any { it.startsWith("[truncated:") }) return null
        return Parsed(header, lines.subList(1, lines.size - 1))
    }

    /** The line without its id, so the same node matches across observations. Indentation stays: it is the tree. */
    private fun key(line: String): String = ID.replaceFirst(line, "[")

    private fun actionable(line: String): Boolean =
        line.contains(ACTIONABLE_1) || line.contains(ACTIONABLE_2) || line.contains(ACTIONABLE_3)
}
