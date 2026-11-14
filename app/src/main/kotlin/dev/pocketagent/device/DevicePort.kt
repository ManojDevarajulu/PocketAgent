package dev.pocketagent.device

enum class Direction { UP, DOWN, LEFT, RIGHT }

sealed interface DeviceAction {
    data class Tap(val node: ScreenNode) : DeviceAction
    data class TapAt(val x: Int, val y: Int) : DeviceAction
    /** [submit] presses the keyboard's Enter or Search key after the text is in. */
    data class TypeText(val node: ScreenNode, val text: String, val submit: Boolean = false) : DeviceAction
    data class Scroll(val direction: Direction, val node: ScreenNode?) : DeviceAction
    data object Back : DeviceAction
    data object Home : DeviceAction
    data class OpenApp(val name: String) : DeviceAction

    /** Hands a ready-made request to another app through an Android intent. See [Shortcut]. */
    data class OpenShortcut(val shortcut: Shortcut) : DeviceAction

    /**
     * A reply to a notification. It exists so the gate can judge it; it goes out through the notification
     * port, never through the device, which refuses it.
     */
    data class ReplyToNotification(
        val key: String,
        val packageName: String,
        val appLabel: String?,
        val sender: String,
        val text: String,
    ) : DeviceAction
}

/**
 * One-step requests to other apps. None of them sends, calls or pays: a call or a text only opens with the
 * number and words filled in, and the Call or Send tap that follows is still checked by the gate.
 */
sealed interface Shortcut {
    /** Starts turn-by-turn directions in a maps app. */
    data class Directions(val destination: String) : Shortcut

    /** Opens the phone app with [number] typed in. It does not call. */
    data class Dial(val number: String) : Shortcut

    /** Opens a new text message to [number] with [body] filled in. It does not send. */
    data class TextMessage(val number: String, val body: String) : Shortcut

    /** Runs a web search in the phone's default search app. */
    data class WebSearch(val query: String) : Shortcut

    /** Sets an alarm for [hour]:[minute] (24-hour clock) without opening the clock app. */
    data class SetAlarm(val hour: Int, val minute: Int) : Shortcut

    /** Starts a countdown timer of [seconds] without opening the clock app. */
    data class SetTimer(val seconds: Int) : Shortcut

    /** Opens a web link ([url] is http or https) in the default browser. */
    data class OpenUrl(val url: String) : Shortcut

    /** Opens a new calendar event with these details filled in, in local time. It does not save. */
    data class AddEvent(val title: String, val start: java.time.LocalDateTime, val minutes: Int) : Shortcut
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
