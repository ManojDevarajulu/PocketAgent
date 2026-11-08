package dev.pocketagent.device

class ConvertedTree(val root: ScreenNode?, val rawById: Map<String, RawNode>)

/** Turns a [RawNode] tree into [ScreenNode]s, dropping invisible subtrees and capping depth and size. */
class TreeConverter(private val maxDepth: Int = 60, private val maxNodes: Int = 1_500) {
    /** Returns the root (null when [raw] itself is not visible) and a map from each kept node's id to its [RawNode], so the caller can find the live node behind an id. */
    fun convert(raw: RawNode, observation: Int): ConvertedTree {
        val rawById = LinkedHashMap<String, RawNode>()
        var count = 0

        fun visit(node: RawNode, depth: Int): ScreenNode {
            val id = NodeIds.idFor(observation, count++)
            rawById[id] = node
            val kids = ArrayList<ScreenNode>()
            if (depth < maxDepth) {
                for (child in node.children) {
                    if (count >= maxNodes) break
                    if (child.visibleToUser) kids.add(visit(child, depth + 1))
                }
            }
            return ScreenNode(
                id = id,
                role = roleOf(node),
                text = node.text,
                description = node.description,
                clickable = node.clickable,
                editable = node.editable,
                scrollable = node.scrollable,
                isPassword = node.isPassword,
                packageName = node.packageName,
                bounds = node.bounds,
                children = kids,
            )
        }

        if (!raw.visibleToUser || maxNodes <= 0) return ConvertedTree(null, emptyMap())
        return ConvertedTree(visit(raw, 0), rawById)
    }

    private fun roleOf(node: RawNode): String {
        val name = node.className.orEmpty()
        return when {
            node.editable || name.endsWith("EditText") -> "input"
            name.endsWith("CheckBox") || name.endsWith("Switch") || name.endsWith("RadioButton") -> "checkbox"
            name.endsWith("Button") -> "button"
            name.endsWith("ImageView") -> "image"
            name.endsWith("TextView") -> "text"
            LIST_SUFFIXES.any { name.endsWith(it) } || node.scrollable -> "list"
            else -> "group"
        }
    }

    private companion object {
        val LIST_SUFFIXES = listOf("RecyclerView", "ListView", "ScrollView", "GridView")
    }
}
