package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenTest {
    private val tree = Screen(
        0, "com.app", "App",
        node(
            "a0", bounds = Bounds(0, 0, 100, 100),
            children = listOf(
                node("a1", bounds = Bounds(0, 0, 100, 50), children = listOf(node("a2", bounds = Bounds(0, 0, 50, 50)))),
                node("a3", bounds = Bounds(0, 50, 100, 100)),
            ),
        ),
    )

    @Test
    fun boundsContainsIsHalfOpen() {
        val b = Bounds(10, 10, 20, 20)
        assertTrue(b.contains(10, 10))
        assertTrue(b.contains(19, 19))
        assertFalse(b.contains(20, 15))
        assertFalse(b.contains(15, 20))
    }

    @Test
    fun labelPrefersTextThenDescriptionAndIsTrimmed() {
        assertEquals("Hi", node("a1", text = "  Hi  ").label())
        assertEquals("Desc", node("a1", text = "  ", description = "Desc").label())
        assertNull(node("a1", text = null, description = " ").label())
    }

    @Test
    fun findReturnsNodeById() {
        assertEquals("a2", tree.find("a2")?.id)
        assertNull(tree.find("zz"))
    }

    @Test
    fun allIsDepthFirst() {
        assertEquals(listOf("a0", "a1", "a2", "a3"), tree.all().map { it.id }.toList())
    }

    @Test
    fun parentOfReturnsParentAndNullForRoot() {
        assertEquals("a1", tree.parentOf(tree.find("a2")!!)?.id)
        assertNull(tree.parentOf(tree.root))
    }

    @Test
    fun deepestAtReturnsLastNodeInDepthFirstOrderContainingPoint() {
        assertEquals("a2", tree.deepestAt(10, 10)?.id)
        assertEquals("a1", tree.deepestAt(60, 10)?.id)
        assertEquals("a3", tree.deepestAt(10, 60)?.id)
        assertNull(tree.deepestAt(500, 500))
    }
}
