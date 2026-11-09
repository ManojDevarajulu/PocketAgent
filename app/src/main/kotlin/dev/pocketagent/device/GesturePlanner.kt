package dev.pocketagent.device

data class Point(val x: Int, val y: Int)

data class SwipePath(val from: Point, val to: Point)

/** Plans gesture coordinates that stay clear of the system-gesture edges of the screen. */
class GesturePlanner(private val screen: Bounds, private val edgeInsetPx: Int) {
    private val safe = Bounds(
        screen.left + edgeInsetPx,
        screen.top + edgeInsetPx,
        screen.right - edgeInsetPx,
        screen.bottom - edgeInsetPx,
    )

    fun tapPoint(target: Bounds): Point {
        val area = clip(target)
        return Point((area.left + area.right) / 2, (area.top + area.bottom) / 2)
    }

    /** [direction] is the way the content scrolls, so the finger moves the opposite way. */
    fun swipe(direction: Direction, within: Bounds?): SwipePath {
        val area = if (within == null) safe else clip(within)
        val cx = (area.left + area.right) / 2
        val cy = (area.top + area.bottom) / 2
        val w = area.right - area.left
        val h = area.bottom - area.top
        val near = 25
        val far = 75
        return when (direction) {
            Direction.DOWN -> SwipePath(Point(cx, area.top + h * far / 100), Point(cx, area.top + h * near / 100))
            Direction.UP -> SwipePath(Point(cx, area.top + h * near / 100), Point(cx, area.top + h * far / 100))
            Direction.RIGHT -> SwipePath(Point(area.left + w * far / 100, cy), Point(area.left + w * near / 100, cy))
            Direction.LEFT -> SwipePath(Point(area.left + w * near / 100, cy), Point(area.left + w * far / 100, cy))
        }
    }

    /** [b] intersected with the safe area; the safe area itself when they do not overlap. */
    private fun clip(b: Bounds): Bounds {
        val r = Bounds(
            maxOf(b.left, safe.left), maxOf(b.top, safe.top),
            minOf(b.right, safe.right), minOf(b.bottom, safe.bottom),
        )
        return if (r.left < r.right && r.top < r.bottom) r else safe
    }
}
