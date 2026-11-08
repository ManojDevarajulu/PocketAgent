package dev.pocketagent.device

import kotlin.math.abs

/** What the device saw when it refreshed the live node. Null instead of this means the refresh failed. */
data class LiveNodeState(
    val label: String?,
    val bounds: Bounds,
    val visibleToUser: Boolean,
    val packageName: String,
    val inActiveWindow: Boolean,
)

/** Decides, just before an action, whether what is on screen is still what the model chose. */
class Revalidator(private val ownPackage: String, private val tolerancePx: Int = 4) {
    fun nodeStillMatches(observed: ScreenNode, live: LiveNodeState?): Boolean {
        if (live == null) return false
        if (!live.visibleToUser || !live.inActiveWindow) return false
        if (live.packageName == ownPackage) return false
        if (live.label != observed.label()) return false
        val a = observed.bounds
        val b = live.bounds
        return abs(a.left - b.left) <= tolerancePx && abs(a.top - b.top) <= tolerancePx &&
            abs(a.right - b.right) <= tolerancePx && abs(a.bottom - b.bottom) <= tolerancePx
    }

    /** [liveLabel] is the label of the deepest live node at the point; a point with no label must still have none. */
    fun pointStillMatches(observed: Screen, x: Int, y: Int, liveLabel: String?): Boolean =
        observed.deepestAt(x, y)?.label() == liveLabel
}
