package dev.pocketagent.device

/** A rectangle in screen pixels. Half-open: the left and top edges are inside, the right and bottom edges are not. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom
}

/** One element of the screen. [id] is valid only for the observation that produced it. */
data class ScreenNode(
    val id: String,
    val role: String,
    val text: String?,
    val description: String?,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val isPassword: Boolean = false,
    val packageName: String,
    val bounds: Bounds,
    val children: List<ScreenNode> = emptyList(),
) {
    /** The first non-blank of text and description, trimmed, or null when both are blank. */
    fun label(): String? = listOf(text, description).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
}

/** One reading of the screen. [ownWindowBounds] are Pocket Agent's own windows, kept for the safety gate. */
data class Screen(
    val observation: Int,
    val packageName: String,
    val appLabel: String?,
    val root: ScreenNode,
    val ownWindowBounds: List<Bounds> = emptyList(),
) {
    /** Every node, depth-first, parents before children. */
    fun all(): Sequence<ScreenNode> = sequence {
        val pending = ArrayDeque<ScreenNode>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            yield(node)
            for (i in node.children.indices.reversed()) pending.addLast(node.children[i])
        }
    }

    fun find(id: String): ScreenNode? = all().firstOrNull { it.id == id }

    fun parentOf(node: ScreenNode): ScreenNode? = all().firstOrNull { parent -> parent.children.any { it.id == node.id } }

    /** The last node in depth-first order whose bounds contain the point, which is the most deeply nested one. */
    fun deepestAt(x: Int, y: Int): ScreenNode? = all().lastOrNull { it.bounds.contains(x, y) }
}

object NodeIds {
    /** Ids look like `c12`: a letter that changes with every observation, so a stale id never matches a new node. */
    fun idFor(observation: Int, index: Int): String = "${'a' + observation % 26}$index"
}
