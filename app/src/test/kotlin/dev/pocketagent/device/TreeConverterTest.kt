package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TreeConverterTest {
    private val box = Bounds(0, 0, 100, 100)

    private fun raw(
        className: String? = "android.view.View",
        text: String? = null,
        editable: Boolean = false,
        scrollable: Boolean = false,
        isPassword: Boolean = false,
        visible: Boolean = true,
        children: List<RawNode> = emptyList(),
    ) = RawNode(className, text, null, false, editable, scrollable, isPassword, visible, "com.app", box, children)

    private fun ScreenNode.flat(): List<ScreenNode> = listOf(this) + children.flatMap { it.flat() }

    @Test
    fun invisibleNodesAndTheirSubtreesAreDropped() {
        val tree = raw(children = listOf(
            raw(text = "kept"),
            raw(text = "hidden", visible = false, children = listOf(raw(text = "under hidden"))),
        ))
        val out = TreeConverter().convert(tree, 0)
        assertEquals(listOf("kept"), out.root!!.children.map { it.text })
        assertEquals(2, out.rawById.size)
    }

    @Test
    fun rolesFollowTheClassNameTable() {
        fun role(cls: String?, editable: Boolean = false, scrollable: Boolean = false) =
            TreeConverter().convert(raw(cls, editable = editable, scrollable = scrollable), 0).root!!.role
        assertEquals("button", role("android.widget.Button"))
        assertEquals("button", role("android.widget.ImageButton"))
        assertEquals("image", role("android.widget.ImageView"))
        assertEquals("text", role("android.widget.TextView"))
        assertEquals("input", role("android.widget.EditText"))
        assertEquals("input", role("android.widget.TextView", editable = true))
        assertEquals("checkbox", role("android.widget.CheckBox"))
        assertEquals("checkbox", role("android.widget.Switch"))
        assertEquals("checkbox", role("android.widget.RadioButton"))
        assertEquals("list", role("androidx.recyclerview.widget.RecyclerView"))
        assertEquals("list", role("android.widget.ListView"))
        assertEquals("list", role("android.widget.ScrollView"))
        assertEquals("list", role("android.widget.GridView"))
        assertEquals("list", role("android.view.ViewGroup", scrollable = true))
        assertEquals("group", role("android.widget.FrameLayout"))
    }

    @Test
    fun passwordFlagIsKept() {
        val out = TreeConverter().convert(raw("android.widget.EditText", isPassword = true), 0)
        assertTrue(out.root!!.isPassword)
    }

    @Test
    fun idsComeFromNodeIdsInDepthFirstOrder() {
        val tree = raw(children = listOf(raw(children = listOf(raw())), raw()))
        assertEquals(listOf("a0", "a1", "a2", "a3"), TreeConverter().convert(tree, 0).root!!.flat().map { it.id })
        assertEquals(listOf("b0", "b1", "b2", "b3"), TreeConverter().convert(tree, 1).root!!.flat().map { it.id })
    }

    @Test
    fun depthIsCappedAt60() {
        var chain = raw()
        repeat(99) { chain = raw(children = listOf(chain)) }
        val out = TreeConverter().convert(chain, 0)
        assertEquals(61, out.root!!.flat().size)
    }

    @Test
    fun totalIsCappedAt1500() {
        val tree = raw(children = List(2_000) { raw(text = "n$it") })
        val out = TreeConverter().convert(tree, 0)
        val nodes = out.root!!.flat()
        assertEquals(1_500, nodes.size)
        assertEquals((0 until 1_499).map { "n$it" }, out.root!!.children.map { it.text })
        assertEquals(1_500, out.rawById.size)
    }

    @Test
    fun nullClassNameIsAGroup() {
        assertEquals("group", TreeConverter().convert(raw(null), 0).root!!.role)
    }

    @Test
    fun rawByIdMapsEveryKeptNode() {
        val child = raw(text = "c")
        val tree = raw(children = listOf(child, raw(visible = false)))
        val out = TreeConverter().convert(tree, 2)
        assertEquals(setOf("c0", "c1"), out.rawById.keys)
        assertEquals(tree, out.rawById["c0"])
        assertEquals(child, out.rawById["c1"])
        assertNotNull(out.root)
    }

    @Test
    fun invisibleRootGivesNullRoot() {
        val out = TreeConverter().convert(raw(visible = false, children = listOf(raw())), 0)
        assertNull(out.root)
        assertTrue(out.rawById.isEmpty())
    }
}
