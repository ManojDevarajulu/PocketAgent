package dev.pocketagent.android

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.pocketagent.device.Bounds
import dev.pocketagent.device.NodeIds
import dev.pocketagent.device.RawNode
import dev.pocketagent.device.Screen
import dev.pocketagent.device.ScreenNode
import dev.pocketagent.device.TreeConverter
import dev.pocketagent.device.WindowKind
import dev.pocketagent.device.WindowPicker
import dev.pocketagent.device.WindowSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap

/**
 * Copies the live accessibility tree into a [Screen]. Every call to the platform here is a binder call, so all of
 * it runs on [Dispatchers.Default]. Decisions are left to the pure units: [WindowPicker] chooses the window and
 * [TreeConverter] builds the nodes, ids and caps.
 */
internal class ScreenReader(private val service: AccessibilityService) {
    private val ownPackage: String = service.packageName
    private val picker = WindowPicker(ownPackage)
    private val converter = TreeConverter(MAX_DEPTH, MAX_NODES)

    /** One observation: the screen the model sees, and the live node behind each of its ids. */
    class Reading(val screen: Screen, val handles: Map<String, AccessibilityNodeInfo>)

    /** The window [WindowPicker] chooses right now. [id] is null when no window qualifies. */
    class CurrentWindow(
        val id: Int?,
        val root: AccessibilityNodeInfo?,
        val bounds: Bounds?,
        val ownWindowBounds: List<Bounds>,
    ) {
        /** The root node's package, or empty when the window has no readable root. */
        val packageName: String get() = root?.packageName?.toString().orEmpty()
    }

    suspend fun current(): CurrentWindow = withContext(Dispatchers.Default) { pickWindow() }

    /** Reads the chosen window. A missing or invisible root gives an empty screen: a root group with no children. */
    suspend fun read(observation: Int): Reading = withContext(Dispatchers.Default) {
        val window = pickWindow()
        val copier = Copier()
        val raw = window.root?.let { copier.copy(it, depth = 0) }
        val packageName = raw?.packageName ?: window.packageName
        val converted = raw?.let { converter.convert(it, observation) }
        val root = converted?.root
        val screenRoot = root ?: emptyRoot(observation, packageName, window.bounds)
        val handles = HashMap<String, AccessibilityNodeInfo>()
        converted?.rawById?.forEach { (id, node) -> copier.liveFor(node)?.let { handles[id] = it } }
        Reading(
            Screen(
                observation = observation,
                packageName = packageName,
                appLabel = appLabel(packageName),
                root = screenRoot,
                ownWindowBounds = window.ownWindowBounds,
            ),
            handles,
        )
    }

    private fun pickWindow(): CurrentWindow {
        val windows: List<AccessibilityWindowInfo> = service.windows ?: emptyList()
        val roots = HashMap<Int, AccessibilityNodeInfo?>()
        val summaries = windows.map { window ->
            val root = window.root
            roots[window.id] = root
            val rect = Rect()
            window.getBoundsInScreen(rect)
            WindowSummary(
                id = window.id,
                packageName = root?.packageName?.toString(),
                kind = kindOf(window.type),
                active = window.isActive,
                bounds = rect.toBounds(),
            )
        }
        val choice = picker.pick(summaries)
        val id = choice.windowId
        return CurrentWindow(
            id = id,
            root = id?.let { roots[it] },
            bounds = summaries.firstOrNull { it.id == id }?.bounds,
            ownWindowBounds = choice.ownWindowBounds,
        )
    }

    private fun emptyRoot(observation: Int, packageName: String, bounds: Bounds?) = ScreenNode(
        id = NodeIds.idFor(observation, 0),
        role = "group",
        text = null,
        description = null,
        packageName = packageName,
        bounds = bounds ?: Bounds(0, 0, 0, 0),
    )

    /** The name the user knows the app by, from [PackageManager] only, never from screen content. */
    private fun appLabel(packageName: String): String? {
        if (packageName.isEmpty()) return null
        val pm = service.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString().trim().ifEmpty { null }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Copies nodes in the same depth-first order [TreeConverter] visits them, and stops reading where the converter
     * would stop keeping: below invisible nodes, below [MAX_DEPTH], and after [MAX_NODES] visible nodes. Each copy
     * remembers its live node by identity, because [RawNode] equality is by value.
     */
    private class Copier {
        private val live = IdentityHashMap<RawNode, AccessibilityNodeInfo>()
        private var visibleCount = 0

        fun liveFor(raw: RawNode): AccessibilityNodeInfo? = live[raw]

        fun copy(node: AccessibilityNodeInfo, depth: Int): RawNode {
            val visible = node.isVisibleToUser
            if (visible) visibleCount++
            val children = ArrayList<RawNode>()
            if (visible && depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    if (visibleCount >= MAX_NODES) break
                    val child = node.getChild(i) ?: continue
                    children.add(copy(child, depth + 1))
                }
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val raw = RawNode(
                className = node.className?.toString(),
                text = node.text?.toString(),
                description = node.contentDescription?.toString(),
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                isPassword = node.isPassword,
                visibleToUser = visible,
                packageName = node.packageName?.toString().orEmpty(),
                bounds = rect.toBounds(),
                children = children,
            )
            live[raw] = node
            return raw
        }
    }

    companion object {
        const val MAX_DEPTH = 60
        const val MAX_NODES = 1_500

        private fun kindOf(type: Int): WindowKind = when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.APPLICATION
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.INPUT_METHOD
            AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.SYSTEM
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowKind.ACCESSIBILITY_OVERLAY
            else -> WindowKind.OTHER
        }
    }
}

internal fun Rect.toBounds(): Bounds = Bounds(left, top, right, bottom)
