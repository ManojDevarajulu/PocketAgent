package dev.pocketagent.device

enum class Direction { UP, DOWN, LEFT, RIGHT }

sealed interface DeviceAction {
    data class Tap(val node: ScreenNode) : DeviceAction
    data class TapAt(val x: Int, val y: Int) : DeviceAction
    data class TypeText(val node: ScreenNode, val text: String) : DeviceAction
    data class Scroll(val direction: Direction, val node: ScreenNode?) : DeviceAction
    data object Back : DeviceAction
    data object Home : DeviceAction
    data class OpenApp(val name: String) : DeviceAction
}

sealed interface ActionResult {
    data object Ok : ActionResult
    data class Failed(val reason: String) : ActionResult
}

/** Everything the agent needs from the phone. The Android implementation comes later; tests use fakes. */
interface DevicePort {
    /** Waits for the screen to settle, leaves out Pocket Agent's own windows, and assigns ids with [NodeIds]. */
    suspend fun readScreen(): Screen

    /**
     * Re-checks the target's label and bounds against the observation first. On a mismatch it returns
     * `Failed("screen changed")` and does nothing.
     */
    suspend fun perform(action: DeviceAction): ActionResult

    /** A PNG, or null when a secure window blocks screenshots. */
    suspend fun screenshot(): ByteArray?
}
