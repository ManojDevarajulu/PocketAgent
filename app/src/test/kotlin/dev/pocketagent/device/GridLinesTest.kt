package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridLinesTest {
    @Test fun aTallPhoneGetsOneRoundStepForBothAxes() {
        val step = GridLines.stepFor(2400)
        assertEquals(200, step)
        assertEquals(listOf(200, 400, 600, 800, 1000), GridLines.marks(1080, step))
        assertEquals(11, GridLines.marks(2400, step).size)
    }

    @Test fun theStepGrowsWithTheScreenAndStaysRound() {
        listOf(800, 1280, 2400, 3200, 6000).forEach {
            val step = GridLines.stepFor(it)
            assertTrue(step % 50 == 0, "$it -> $step")
            assertTrue(it / step <= 12 || step == 1000, "$it -> $step")
        }
    }

    @Test fun nothingForAnEmptySideOrStep() {
        assertEquals(emptyList(), GridLines.marks(0, 200))
        assertEquals(emptyList(), GridLines.marks(1080, 0))
    }
}
