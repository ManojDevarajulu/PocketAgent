package dev.pocketagent.device

enum class WindowKind { APPLICATION, INPUT_METHOD, SYSTEM, ACCESSIBILITY_OVERLAY, OTHER }

data class WindowSummary(
    val id: Int,
    val packageName: String?,
    val kind: WindowKind,
    val active: Boolean,
    val bounds: Bounds,
)

/** [windowId] is the window to read, or null when none qualifies. [ownWindowBounds] are our own windows. */
data class WindowChoice(val windowId: Int?, val ownWindowBounds: List<Bounds>)

/** Picks which window to read. Our own windows are collected for the safety gate and never chosen. */
class WindowPicker(private val ownPackage: String) {
    fun pick(windows: List<WindowSummary>): WindowChoice {
        val (own, others) = windows.partition { it.packageName == ownPackage }
        val active = others.firstOrNull { it.active && it.kind != WindowKind.INPUT_METHOD }
        // Main path: right after Go (and at a bubble tap) our own window is still active, so fall back to the first app window.
        val chosen = active ?: others.firstOrNull { it.kind == WindowKind.APPLICATION }
        return WindowChoice(chosen?.id, own.map { it.bounds })
    }
}
