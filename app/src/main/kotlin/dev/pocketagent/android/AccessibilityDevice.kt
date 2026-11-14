package dev.pocketagent.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.provider.AlarmClock
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import dev.pocketagent.device.ActionResult
import dev.pocketagent.device.AppMatcher
import dev.pocketagent.device.AppResolution
import dev.pocketagent.device.Bounds
import dev.pocketagent.device.DeviceAction
import dev.pocketagent.device.DevicePort
import dev.pocketagent.device.DeviceUnavailable
import dev.pocketagent.device.Direction
import dev.pocketagent.device.GesturePlanner
import dev.pocketagent.device.LauncherApp
import dev.pocketagent.device.LiveNodeState
import dev.pocketagent.device.Point
import dev.pocketagent.device.Revalidator
import dev.pocketagent.device.Screen
import dev.pocketagent.device.ScreenNode
import dev.pocketagent.device.Shortcut
import dev.pocketagent.device.Settler
import dev.pocketagent.device.SwipePath
import dev.pocketagent.device.TypedText
import dev.pocketagent.safety.BlockedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * [DevicePort] on top of the accessibility service (spec §4 and §5). It only moves data between the platform and
 * the pure units; every decision is theirs: [Revalidator] before each action, [GesturePlanner] for gesture
 * coordinates, [AppMatcher] (with [BlockedApps]) for open_app.
 */
class AccessibilityDevice(
    private val service: AccessibilityService,
    private val settler: Settler,
    /** Hides (true) or shows (false) the bubble and caption on the main thread. */
    private val hideOverlays: suspend (hidden: Boolean) -> Unit,
    private val isClosed: () -> Boolean,
) : DevicePort {
    private val ownPackage: String = service.packageName
    private val reader = ScreenReader(service)
    private val revalidator = Revalidator(ownPackage, tolerancePx = BOUNDS_TOLERANCE_PX)
    private val blockedApps = BlockedApps()
    private val appMatcher = AppMatcher(blockedApps)
    private val keyguard: KeyguardManager? = service.getSystemService(KeyguardManager::class.java)
    private val observationCounter = AtomicInteger(0)

    /** The latest observation and its live nodes. Replaced by every read, cleared by [dropHandles]. */
    @Volatile
    private var observation: ScreenReader.Reading? = null

    override suspend fun readScreen(): Screen {
        ensureUsable()
        settler.settle()
        ensureUsable()
        return withOverlaysHidden { readAndKeep() }
    }

    /**
     * Reads without settling, for the bubble tap: the screen is already still. The caller hides and shows the
     * bubble around this call; our own windows are left out of the observation either way.
     */
    suspend fun readScreenNow(): Screen {
        ensureUsable()
        return readAndKeep()
    }

    /** Forgets the live node map, so no later action can use a handle from an old task. */
    fun dropHandles() {
        observation = null
    }

    override suspend fun perform(action: DeviceAction): ActionResult {
        ensureUsable()
        return withContext(Dispatchers.Default) {
            when (action) {
                is DeviceAction.Tap -> tap(action.node)
                is DeviceAction.TapAt -> tapAt(action.x, action.y)
                is DeviceAction.TypeText -> typeText(action.node, action.text, action.submit)
                is DeviceAction.Scroll -> scroll(action.direction, action.node)
                DeviceAction.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK, "could not go back")
                DeviceAction.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME, "could not go home")
                is DeviceAction.OpenApp -> openApp(action.name)
                is DeviceAction.OpenShortcut -> openShortcut(action.shortcut)
                // Replies go through NotificationPort, never the screen.
                is DeviceAction.ReplyToNotification -> ActionResult.Failed("not a screen action")
            }
        }
    }

    override suspend fun screenshot(): ByteArray? {
        ensureUsable()
        return withOverlaysHidden {
            when (val first = capture()) {
                is Capture.Taken -> first.bitmap?.let { encode(it) }
                Capture.RateLimited -> {
                    delay(SCREENSHOT_RETRY_MS)
                    ensureUsable()
                    (capture() as? Capture.Taken)?.bitmap?.let { encode(it) }
                }
                Capture.Refused -> null
            }
        }
    }

    // --- Reading ---

    private suspend fun readAndKeep(): Screen {
        val reading = reader.read(observationCounter.getAndIncrement())
        // A read that finishes after the session closed must not keep live nodes behind dropHandles().
        if (isClosed()) throw DeviceUnavailable("Pocket Agent's accessibility service was turned off")
        observation = reading
        return reading.screen
    }

    private fun ensureUsable() {
        if (keyguard?.isKeyguardLocked == true) throw DeviceUnavailable("The phone locked")
        if (isClosed()) throw DeviceUnavailable("Pocket Agent's accessibility service was turned off")
    }

    private suspend fun <T> withOverlaysHidden(block: suspend () -> T): T {
        hideOverlays(true)
        try {
            return block()
        } finally {
            withContext(NonCancellable) { hideOverlays(false) }
        }
    }

    // --- Re-validation (spec §5, T3, rule 13) ---

    /**
     * The live node behind [node] when it still matches the observation, or null. The node must belong to the
     * latest observation, refresh, and pass [Revalidator.nodeStillMatches] against the window chosen right now.
     */
    private suspend fun validatedNode(node: ScreenNode): AccessibilityNodeInfo? {
        val current = observation ?: return null
        // An unknown app (empty package) never passes, matching the coordinate actions.
        if (current.screen.packageName.isEmpty()) return null
        if (current.screen.find(node.id) != node) return null
        val live = current.handles[node.id] ?: return null
        val activeWindowId = reader.current().id
        val state = if (live.refresh()) {
            LiveNodeState(
                label = labelOf(live),
                bounds = boundsOf(live),
                visibleToUser = live.isVisibleToUser,
                packageName = live.packageName?.toString().orEmpty(),
                inActiveWindow = activeWindowId != null && live.windowId == activeWindowId,
            )
        } else {
            null
        }
        return live.takeIf { revalidator.nodeStillMatches(node, state) }
    }

    /**
     * A coordinate tap is checked against a fresh reading of the chosen window: the deepest node at the point
     * must have the same label as before ([Revalidator.pointStillMatches]). Two stricter checks than the spec:
     * the point must lie on an observed node, and the app in front must be the same one, because two unlabelled
     * points in different apps would otherwise look alike.
     */
    private suspend fun pointStillMatches(x: Int, y: Int): Boolean {
        val current = observation ?: return false
        // An unknown foreground app ("" package, no readable root) escaped the block-list check: never tap in it.
        if (current.screen.packageName.isEmpty()) return false
        // A point outside every observed node is outside the window the model saw (another split-screen half,
        // a system bar). Revalidator would pass it when the live point is unlabelled too; refuse it here.
        if (current.screen.deepestAt(x, y) == null) return false
        val fresh = reader.read(current.screen.observation).screen
        if (fresh.packageName.isEmpty() || fresh.packageName != current.screen.packageName) return false
        return revalidator.pointStillMatches(current.screen, x, y, fresh.deepestAt(x, y)?.label())
    }

    // --- Actions ---

    private suspend fun tap(node: ScreenNode): ActionResult {
        val live = validatedNode(node) ?: return SCREEN_CHANGED
        val clickable = generateSequence(live) { it.parent }.take(MAX_ANCESTORS).firstOrNull { it.isClickable }
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return ActionResult.Ok
        val bounds = boundsOf(live)
        val point = planner().tapPoint(bounds)
        // The planner falls back to the middle of the screen when the node lies wholly in the edge area;
        // tapping there would hit something else, so refuse instead.
        if (!bounds.contains(point.x, point.y)) return ActionResult.Failed("could not tap here")
        return tapGesture(point)
    }

    private suspend fun tapAt(x: Int, y: Int): ActionResult {
        if (!pointStillMatches(x, y)) return SCREEN_CHANGED
        return tapGesture(Point(x, y))
    }

    private suspend fun typeText(node: ScreenNode, text: String, submit: Boolean): ActionResult {
        val live = validatedNode(node) ?: return SCREEN_CHANGED
        // A second check behind the gate: password fields are never typed into.
        if (node.isPassword || live.isPassword) return ActionResult.Failed("password fields are never typed into")
        // Focus may report false on a field that already has focus, so only setting the text decides.
        live.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!live.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return ActionResult.Failed("could not type here")
        // "Set text" can report success while the app ignores it, so read the field again and check.
        delay(TYPE_SETTLE_MS)
        live.refresh()
        val actual = if (live.isShowingHintText) null else live.text
        return if (TypedText.matches(text, actual)) {
            if (submit) pressEnter(live) else ActionResult.Ok
        } else {
            ActionResult.Failed(
                "the field did not take the text. If it already holds other text, clear it with its clear button, " +
                    "or try another input field",
            )
        }
    }

    /** The keyboard's Enter or Search key, sent to the field itself, so no gesture lands anywhere else. */
    private fun pressEnter(field: AccessibilityNodeInfo): ActionResult {
        val enter = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        return if (field.actionList.any { it.id == enter } && field.performAction(enter)) {
            ActionResult.Ok
        } else {
            ActionResult.Failed("the text is in, but this field has no Enter key; tap the search button instead")
        }
    }

    private suspend fun scroll(direction: Direction, node: ScreenNode?): ActionResult {
        val current = observation ?: return SCREEN_CHANGED
        val target = node ?: largestScrollable(current.screen)
        if (target == null) {
            // Nothing scrollable was seen: swipe across the middle of the screen, but only in the same, known app.
            // An unknown app ("" package) escaped the block-list check, so it is never acted in.
            if (current.screen.packageName.isEmpty()) return SCREEN_CHANGED
            if (reader.current().packageName != current.screen.packageName) return SCREEN_CHANGED
            return swipeGesture(planner().swipe(direction, null))
        }
        val live = validatedNode(target) ?: return SCREEN_CHANGED
        val scrollAction = scrollActionFor(direction)
        if (live.actionList.any { it.id == scrollAction.id } && live.performAction(scrollAction.id)) {
            return ActionResult.Ok
        }
        val bounds = boundsOf(live)
        val path = planner().swipe(direction, bounds)
        // As with taps: a swipe the planner moved out of the node would scroll something else.
        if (!bounds.contains(path.from.x, path.from.y) || !bounds.contains(path.to.x, path.to.y)) {
            return ActionResult.Failed("could not scroll here")
        }
        return swipeGesture(path)
    }

    private fun global(action: Int, failure: String): ActionResult =
        if (service.performGlobalAction(action)) ActionResult.Ok else ActionResult.Failed(failure)

    /** Rule 15: launcher apps of this user only, matched by [AppMatcher], which also refuses blocked packages. */
    private fun openApp(name: String): ActionResult {
        val pm = service.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        val components = LinkedHashMap<LauncherApp, ComponentName>()
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            if (activity.packageName == ownPackage) continue
            val app = LauncherApp(info.loadLabel(pm).toString(), activity.packageName)
            components.putIfAbsent(app, ComponentName(activity.packageName, activity.name))
        }
        return when (val resolution = appMatcher.resolve(name, components.keys.toList())) {
            is AppResolution.Refused -> ActionResult.Failed(resolution.reason)
            is AppResolution.Launch -> {
                val component = components.getValue(resolution.app)
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    service.startActivity(intent)
                    ActionResult.Ok
                } catch (e: RuntimeException) {
                    ActionResult.Failed("could not open ${resolution.app.label}")
                }
            }
        }
    }

    /**
     * Each shortcut is tried as a list of intents, most specific first: directions prefer Google Maps' own
     * navigation link and fall back to any maps app. The app that would receive the intent is looked up first,
     * and a blocked app (or Pocket Agent itself) is refused before anything opens.
     */
    private fun openShortcut(shortcut: Shortcut): ActionResult {
        val (candidates, what) = when (shortcut) {
            is Shortcut.Directions -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(shortcut.destination))),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(shortcut.destination))),
            ) to "show directions"
            is Shortcut.Dial -> listOf(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", shortcut.number, null))) to "dial a number"
            is Shortcut.TextMessage -> listOf(
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", shortcut.number, null)).putExtra("sms_body", shortcut.body),
            ) to "write a text message"
            is Shortcut.WebSearch -> listOf(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, shortcut.query),
            ) to "search the web"
            is Shortcut.SetAlarm -> listOf(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, shortcut.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, shortcut.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            ) to "set an alarm"
            is Shortcut.SetTimer -> listOf(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, shortcut.seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            ) to "start a timer"
            is Shortcut.OpenUrl -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse(shortcut.url)).addCategory(Intent.CATEGORY_BROWSABLE),
            ) to "open a web link"
            is Shortcut.AddEvent -> {
                val begin = shortcut.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                listOf(
                    Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                        .putExtra(android.provider.CalendarContract.Events.TITLE, shortcut.title)
                        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, begin + shortcut.minutes * 60_000L),
                ) to "add a calendar event"
            }
        }
        val pm = service.packageManager
        for (intent in candidates) {
            val target = resolvedActivity(intent) ?: continue
            val label = target.loadLabel(pm).toString()
            val pkg = target.activityInfo?.packageName.orEmpty()
            if (pkg == ownPackage || blockedApps.isBlocked(pkg, label)) return ActionResult.Failed("$label is on the block list")
            return try {
                service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionResult.Ok
            } catch (e: RuntimeException) {
                ActionResult.Failed("could not $what")
            }
        }
        return ActionResult.Failed("no app on this phone can $what")
    }

    private fun resolvedActivity(intent: Intent): ResolveInfo? {
        val pm = service.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    // --- Gestures ---

    private suspend fun tapGesture(point: Point): ActionResult {
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        return gesture(path, TAP_MS)
    }

    private suspend fun swipeGesture(swipe: SwipePath): ActionResult {
        val path = Path().apply {
            moveTo(swipe.from.x.toFloat(), swipe.from.y.toFloat())
            lineTo(swipe.to.x.toFloat(), swipe.to.y.toFloat())
        }
        return gesture(path, SWIPE_MS)
    }

    /** One stroke, with our overlays hidden until the platform reports the gesture finished or cancelled. */
    private suspend fun gesture(path: Path, durationMs: Long): ActionResult {
        val description = try {
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
        } catch (e: IllegalArgumentException) {
            return ActionResult.Failed("could not send the gesture")
        }
        return withOverlaysHidden {
            suspendCancellableCoroutine { cont ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(ActionResult.Ok)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(GESTURE_INTERRUPTED)
                    }
                }
                if (!service.dispatchGesture(description, callback, null) && cont.isActive) {
                    cont.resume(ActionResult.Failed("could not send the gesture"))
                }
            }
        }
    }

    // --- Screenshots ---

    private sealed interface Capture {
        /** [bitmap] is a software copy, or null when the copy failed. */
        class Taken(val bitmap: Bitmap?) : Capture
        data object RateLimited : Capture
        /** A secure window, or any other refusal: the caller returns null. */
        data object Refused : Capture
    }

    private suspend fun capture(): Capture = suspendCancellableCoroutine { cont ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            Dispatchers.Default.asExecutor(),
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    var hardware: Bitmap? = null
                    // A throw here would reach the uncaught handler and leave the caller suspended forever,
                    // so any failure to wrap or copy the buffer is treated like a refused screenshot.
                    val result: Capture = try {
                        hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        Capture.Taken(hardware?.copy(Bitmap.Config.ARGB_8888, false))
                    } catch (e: RuntimeException) {
                        Capture.Refused
                    } finally {
                        hardware?.recycle()
                        buffer.close()
                    }
                    if (cont.isActive) cont.resume(result) else (result as? Capture.Taken)?.bitmap?.recycle()
                }

                override fun onFailure(errorCode: Int) {
                    val result = if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        Capture.RateLimited
                    } else {
                        Capture.Refused
                    }
                    if (cont.isActive) cont.resume(result)
                }
            },
        )
    }

    /** Scales so the long side is at most [SCREENSHOT_LONG_SIDE_PX], then encodes as PNG. */
    private suspend fun encode(bitmap: Bitmap): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val longSide = maxOf(bitmap.width, bitmap.height)
            val scaled = if (longSide > SCREENSHOT_LONG_SIDE_PX) {
                val scale = SCREENSHOT_LONG_SIDE_PX.toDouble() / longSide
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            val ok = scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (scaled !== bitmap) scaled.recycle()
            if (ok) out.toByteArray() else null
        } finally {
            bitmap.recycle()
        }
    }

    // --- Helpers ---

    /** A planner for the current display, so rotation and split screen are always taken into account. */
    private fun planner(): GesturePlanner {
        val metrics = service.getSystemService(WindowManager::class.java).currentWindowMetrics
        val screen = metrics.bounds
        val gestures = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())
        val minimum = (MIN_EDGE_DP * service.resources.displayMetrics.density).roundToInt()
        // GesturePlanner keeps one inset on every side; cap it so the safe area never turns inside out.
        val inset = maxOf(gestures.left, gestures.top, gestures.right, gestures.bottom, minimum)
            .coerceAtMost(minOf(screen.width(), screen.height()) / 4)
        return GesturePlanner(screen.toBounds(), inset)
    }

    private fun largestScrollable(screen: Screen): ScreenNode? = screen.all()
        .filter { it.scrollable }
        .maxByOrNull { (it.bounds.right - it.bounds.left).toLong() * (it.bounds.bottom - it.bounds.top).toLong() }

    private fun scrollActionFor(direction: Direction): AccessibilityAction = when (direction) {
        Direction.UP -> AccessibilityAction.ACTION_SCROLL_UP
        Direction.DOWN -> AccessibilityAction.ACTION_SCROLL_DOWN
        Direction.LEFT -> AccessibilityAction.ACTION_SCROLL_LEFT
        Direction.RIGHT -> AccessibilityAction.ACTION_SCROLL_RIGHT
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Bounds = Rect().also { node.getBoundsInScreen(it) }.toBounds()

    /** The live label with exactly [ScreenNode.label] semantics, by building a throwaway node. */
    private fun labelOf(node: AccessibilityNodeInfo): String? = ScreenNode(
        id = "",
        role = "",
        text = node.text?.toString(),
        description = node.contentDescription?.toString(),
        packageName = "",
        bounds = Bounds(0, 0, 0, 0),
    ).label()

    private companion object {
        const val BOUNDS_TOLERANCE_PX = 4
        const val TAP_MS = 50L

        /** Time an app gets to apply typed text before the field is read back. */
        const val TYPE_SETTLE_MS = 150L
        const val SWIPE_MS = 300L
        const val MIN_EDGE_DP = 24
        const val MAX_ANCESTORS = 60
        const val SCREENSHOT_LONG_SIDE_PX = 1_280
        const val SCREENSHOT_RETRY_MS = 1_000L
        val SCREEN_CHANGED = ActionResult.Failed("screen changed")
        val GESTURE_INTERRUPTED = ActionResult.Failed("gesture interrupted")
    }
}
