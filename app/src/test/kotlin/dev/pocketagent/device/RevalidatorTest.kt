package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevalidatorTest {
    private val revalidator = Revalidator(ownPackage = "dev.pocketagent")
    private val observed = node("a1", role = "button", text = "Send", clickable = true, bounds = Bounds(100, 200, 300, 400))

    private fun live(
        label: String? = "Send",
        bounds: Bounds = observed.bounds,
        visible: Boolean = true,
        pkg: String = "com.app",
        active: Boolean = true,
    ) = LiveNodeState(label, bounds, visible, pkg, active)

    @Test fun sameNodeMatches() = assertTrue(revalidator.nodeStillMatches(observed, live()))

    @Test fun labelChangedFails() = assertFalse(revalidator.nodeStillMatches(observed, live(label = "Delete")))

    @Test fun boundsMovedBy3pxPasses() {
        val b = observed.bounds
        listOf(
            b.copy(left = b.left + 3), b.copy(top = b.top - 3), b.copy(right = b.right + 3), b.copy(bottom = b.bottom - 3),
        ).forEach { assertTrue(revalidator.nodeStillMatches(observed, live(bounds = it)), "$it") }
    }

    @Test fun boundsMovedBy4pxPasses() {
        assertTrue(revalidator.nodeStillMatches(observed, live(bounds = observed.bounds.copy(left = observed.bounds.left + 4))))
    }

    @Test fun boundsMovedBy5pxFails() {
        val b = observed.bounds
        listOf(
            b.copy(left = b.left + 5), b.copy(top = b.top - 5), b.copy(right = b.right + 5), b.copy(bottom = b.bottom - 5),
        ).forEach { assertFalse(revalidator.nodeStillMatches(observed, live(bounds = it)), "$it") }
    }

    @Test fun refreshFailureFails() = assertFalse(revalidator.nodeStillMatches(observed, null))

    @Test fun invisibleFails() = assertFalse(revalidator.nodeStillMatches(observed, live(visible = false)))

    @Test fun ownPackageFails() = assertFalse(revalidator.nodeStillMatches(observed, live(pkg = "dev.pocketagent")))

    @Test fun windowNoLongerActiveFails() = assertFalse(revalidator.nodeStillMatches(observed, live(active = false)))

    @Test fun unlabelledNodeStaysMatching() {
        val blank = node("a2", bounds = observed.bounds)
        assertTrue(revalidator.nodeStillMatches(blank, live(label = null)))
        assertFalse(revalidator.nodeStillMatches(blank, live(label = "Send")))
    }

    private val screen = screenOf(observed)

    @Test fun pointWithSameLabelMatches() = assertTrue(revalidator.pointStillMatches(screen, 150, 250, "Send"))

    @Test fun pointLabelChangedFails() = assertFalse(revalidator.pointStillMatches(screen, 150, 250, "Delete"))

    @Test fun unlabelledPointMustStayUnlabelled() {
        val blankScreen = screenOf(node("a1", bounds = Bounds(0, 0, 100, 100)))
        assertTrue(revalidator.pointStillMatches(blankScreen, 50, 50, null))
        assertFalse(revalidator.pointStillMatches(blankScreen, 50, 50, "Pay"))
    }
}
