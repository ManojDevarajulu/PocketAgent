package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GesturePlannerTest {
    private val screen = Bounds(0, 0, 1000, 2000)
    private val planner = GesturePlanner(screen, edgeInsetPx = 50)
    private val box = Bounds(100, 400, 500, 1000)

    @Test fun tapPointIsTheCentre() = assertEquals(Point(300, 700), planner.tapPoint(box))

    @Test fun tapPointOfATargetPartlyOffScreenIsInsideTheSafeArea() =
        assertEquals(Point(925, 1925), planner.tapPoint(Bounds(900, 1900, 1200, 2200)))

    @Test fun tapPointOfATargetOnTheEdgeIsPulledIn() =
        assertEquals(Point(75, 75), planner.tapPoint(Bounds(0, 0, 100, 100)))

    @Test fun tapPointOfATargetOutsideTheSafeAreaFallsBackToItsCentre() =
        assertEquals(Point(500, 1000), planner.tapPoint(Bounds(0, 0, 20, 20)))

    @Test fun swipeDownMovesTheFingerUp() =
        assertEquals(SwipePath(Point(300, 850), Point(300, 550)), planner.swipe(Direction.DOWN, box))

    @Test fun swipeUpMovesTheFingerDown() =
        assertEquals(SwipePath(Point(300, 550), Point(300, 850)), planner.swipe(Direction.UP, box))

    @Test fun swipeRightMovesTheFingerLeft() =
        assertEquals(SwipePath(Point(400, 700), Point(200, 700)), planner.swipe(Direction.RIGHT, box))

    @Test fun swipeLeftMovesTheFingerRight() =
        assertEquals(SwipePath(Point(200, 700), Point(400, 700)), planner.swipe(Direction.LEFT, box))

    @Test fun swipeStaysInsideTheTargetBounds() {
        for (d in Direction.values()) {
            val p = planner.swipe(d, box)
            assertTrue(box.contains(p.from.x, p.from.y) && box.contains(p.to.x, p.to.y), "$d")
        }
    }

    @Test fun swipeAvoidsTheEdges() {
        for (d in Direction.values()) {
            val p = planner.swipe(d, screen)
            for (pt in listOf(p.from, p.to)) {
                assertTrue(pt.x in 50..950 && pt.y in 50..1950, "$d $pt")
            }
        }
    }

    @Test fun nullBoundsSwipesAcrossTheMiddle() =
        assertEquals(SwipePath(Point(500, 1475), Point(500, 525)), planner.swipe(Direction.DOWN, null))
}
