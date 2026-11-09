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

    private fun shifted(d: Int): List<Bounds> {
        val b = observed.bounds
        return listOf(
            b.copy(left = b.left + d), b.copy(left = b.left - d), b.copy(top = b.top + d), b.copy(top = b.top - d),
            b.copy(right = b.right + d), b.copy(right = b.right - d), b.copy(bottom = b.bottom + d), b.copy(bottom = b.bottom - d),
        )
    }

    @Test fun boundsMovedBy3pxPasses() =
        shifted(3).forEach { assertTrue(revalidator.nodeStillMatches(observed, live(bounds = it)), "$it") }

    @Test fun boundsMovedBy4pxPasses() =
        shifted(4).forEach { assertTrue(revalidator.nodeStillMatches(observed, live(bounds = it)), "$it") }

    @Test fun boundsMovedBy5pxFails() =
        shifted(5).forEach { assertFalse(revalidator.nodeStillMatches(observed, live(bounds = it)), "$it") }

    @Test fun differentPackageFails() = assertFalse(revalidator.nodeStillMatches(observed, live(pkg = "com.other")))

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
