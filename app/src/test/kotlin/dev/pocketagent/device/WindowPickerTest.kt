package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowPickerTest {
    private val own = "dev.pocketagent"
    private val picker = WindowPicker(own)

    private fun win(
        id: Int,
        pkg: String?,
        kind: WindowKind = WindowKind.APPLICATION,
        active: Boolean = false,
        bounds: Bounds = Bounds(0, 0, 100, 100),
    ) = WindowSummary(id, pkg, kind, active, bounds)

    @Test
    fun oneAppWindowIsChosen() {
        val choice = picker.pick(listOf(win(1, "com.app", active = true)))
        assertEquals(1, choice.windowId)
        assertEquals(emptyList(), choice.ownWindowBounds)
    }

    @Test
    fun splitScreenChoosesTheActiveHalf() {
        val choice = picker.pick(listOf(win(1, "com.a"), win(2, "com.b", active = true)))
        assertEquals(2, choice.windowId)
    }

    @Test
    fun dialogOverAnAppIsChosenWhenActive() {
        val choice = picker.pick(listOf(win(1, "com.app"), win(2, "com.app", WindowKind.OTHER, active = true)))
        assertEquals(2, choice.windowId)
    }

    @Test
    fun keyboardIsNeverChosen() {
        val choice = picker.pick(
            listOf(win(1, "com.ime", WindowKind.INPUT_METHOD, active = true), win(2, "com.app")),
        )
        assertEquals(2, choice.windowId)
    }

    @Test
    fun ownWindowsAreCollectedAndNeverChosen() {
        val bubble = Bounds(0, 0, 10, 10)
        val task = Bounds(0, 0, 50, 50)
        val choice = picker.pick(
            listOf(
                win(1, own, WindowKind.ACCESSIBILITY_OVERLAY, active = true, bounds = bubble),
                win(2, own, bounds = task),
                win(3, "com.app"),
            ),
        )
        assertEquals(listOf(bubble, task), choice.ownWindowBounds)
        assertEquals(3, choice.windowId)
        assertNull(picker.pick(listOf(win(1, own, active = true))).windowId)
    }

    @Test
    fun notificationShadeIsChosenUnchanged() {
        val choice = picker.pick(
            listOf(win(1, "com.app"), win(2, "com.android.systemui", WindowKind.SYSTEM, active = true)),
        )
        assertEquals(2, choice.windowId)
    }

    @Test
    fun noQualifyingWindowGivesNull() {
        assertNull(picker.pick(emptyList()).windowId)
        assertNull(picker.pick(listOf(win(1, "com.ime", WindowKind.INPUT_METHOD, active = true))).windowId)
    }
}
