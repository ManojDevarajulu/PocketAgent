package dev.pocketagent.device

/**
 * Turns a screen into the text the model sees. Everything on the screen is untrusted, so this class
 * keeps it from escaping its wrapper or forging node lines, and leaves out password fields and
 * Pocket Agent's own windows.
 */
class ScreenSerializer(private val ownPackage: String, private val maxChars: Int = 24_000) {

    fun serialize(screen: Screen): String {
        val lines = ArrayList<String>()
        collect(screen.root, depth = 0, ancestorLabel = null, out = lines)

        val out = StringBuilder()
        out.append("<screen_data app=\"").append(cleanUntrusted(screen.packageName, MAX_LABEL))
            .append("\" label=\"").append(cleanUntrusted(screen.appLabel.orEmpty(), MAX_LABEL)).append("\">\n")
        for ((i, line) in lines.withIndex()) {
            if (out.length + line.length + 1 > maxChars) {
                out.append("[truncated: ").append(lines.size - i).append(" more nodes]\n")
                break
            }
            out.append(line).append('\n')
        }
        return out.append("</screen_data>").toString()
    }

    /** Depth-first. Unkept nodes are skipped and their children promoted one level up. */
    private fun collect(node: ScreenNode, depth: Int, ancestorLabel: String?, out: MutableList<String>) {
        if (node.isPassword || node.packageName == ownPackage) return
        val label = node.label()
        val flags = buildList {
            if (node.clickable) add("clickable")
            if (node.editable) add("editable")
            if (node.scrollable) add("scrollable")
        }
        val kept = label != null || flags.isNotEmpty()
        if (kept) out += line(node, label, flags, borrowed = if (label == null) ancestorLabel else null, depth)
        for (child in node.children) {
            collect(child, if (kept) depth + 1 else depth, label ?: ancestorLabel, out)
        }
    }

    private fun line(node: ScreenNode, label: String?, flags: List<String>, borrowed: String?, depth: Int): String =
        buildString {
            repeat(depth) { append("  ") }
            append('[').append(node.id).append("] ").append(cleanUntrusted(node.role, MAX_ROLE))
            if (label != null) append(" \"").append(cleanUntrusted(label, MAX_LABEL)).append('"')
            flags.forEach { append(' ').append(it) }
            if (borrowed != null) append(" in \"").append(cleanUntrusted(borrowed, MAX_BORROWED)).append('"')
        }

    private companion object {
        const val MAX_LABEL = 120
        const val MAX_ROLE = 20
        const val MAX_BORROWED = 60
    }
}
