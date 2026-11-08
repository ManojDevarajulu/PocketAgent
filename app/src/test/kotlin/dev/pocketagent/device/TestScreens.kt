package dev.pocketagent.device

internal val FULL = Bounds(0, 0, 1080, 2400)

internal fun node(
    id: String,
    role: String = "group",
    text: String? = null,
    description: String? = null,
    clickable: Boolean = false,
    editable: Boolean = false,
    scrollable: Boolean = false,
    isPassword: Boolean = false,
    packageName: String = "com.app",
    bounds: Bounds = FULL,
    children: List<ScreenNode> = emptyList(),
) = ScreenNode(id, role, text, description, clickable, editable, scrollable, isPassword, packageName, bounds, children)

/** A screen whose unlabelled root group holds [children]. */
internal fun screenOf(
    vararg children: ScreenNode,
    appLabel: String? = "App",
    packageName: String = "com.app",
) = Screen(0, packageName, appLabel, node("a0", children = children.toList()))

internal fun screenWithText(text: String) = screenOf(node("a1", role = "text", text = text))

internal fun screenWithNodes(count: Int) = screenOf(
    *Array(count) { node(NodeIds.idFor(0, it + 1), role = "button", text = "Item $it", clickable = true) },
)

/** Loads a redacted tree fixture from src/test/resources/trees. See trees/README.md for the format. */
internal fun loadTree(name: String, packageName: String): Screen {
    val text = checkNotNull(object {}.javaClass.getResource("/trees/$name")) { "missing fixture $name" }.readText()
    class Row(val index: Int, val depth: Int, val role: String, val flags: String, val text: String?, val desc: String?, val bounds: Bounds)

    val rows = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.mapIndexed { index, line ->
        val p = line.split('\t')
        val b = p[5].split(',').map { it.toInt() }
        Row(index, p[0].toInt(), p[1], p[2], p[3].ifEmpty { null }, p[4].ifEmpty { null }, Bounds(b[0], b[1], b[2], b[3]))
    }
    var cursor = 0
    fun build(depth: Int): List<ScreenNode> {
        val result = mutableListOf<ScreenNode>()
        while (cursor < rows.size && rows[cursor].depth == depth) {
            val r = rows[cursor++]
            val children = build(depth + 1)
            result += ScreenNode(
                id = NodeIds.idFor(0, r.index), role = r.role, text = r.text, description = r.desc,
                clickable = 'c' in r.flags, editable = 'e' in r.flags, scrollable = 's' in r.flags, isPassword = 'p' in r.flags,
                packageName = packageName, bounds = r.bounds, children = children,
            )
        }
        return result
    }
    val roots = build(0)
    check(roots.size == 1) { "fixture $name must have exactly one root, found ${roots.size}" }
    return Screen(0, packageName, null, roots.single())
}
