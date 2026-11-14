package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridLinesTest {
    @Test fun aPhoneWidthGetsAboutEightRoundLines() {
        assertEquals(listOf(200, 400, 600, 800, 1000), GridLines.marks(1080))
    }

    @Test fun aPhoneHeightGetsRoundNumbersToo() {
        val marks = GridLines.marks(2400)
        assertTrue(marks.size in 4..12, "$marks")
        assertTrue(marks.all { it % 100 == 0 }, "$marks")
        assertTrue(marks.all { it in 1 until 2400 })
    }

    @Test fun nothingForAnEmptySide() {
        assertEquals(emptyList(), GridLines.marks(0))
    }
}
