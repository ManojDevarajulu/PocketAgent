package dev.pocketagent.agent

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StuckDetectorTest {
    @Test
    fun stuckAfterThreeActionsOnSameScreen() {
        val d = StuckDetector()
        d.afterAction("A") // baseline
        assertFalse(d.afterAction("A"))
        assertFalse(d.afterAction("A"))
        assertTrue(d.afterAction("A"))
    }

    @Test
    fun staysStuckWhileTheScreenKeepsNotChanging() {
        val d = StuckDetector()
        repeat(4) { d.afterAction("A") }
        assertTrue(d.afterAction("A"))
    }

    @Test
    fun changingScreenResetsCount() {
        val d = StuckDetector()
        listOf("A", "A", "B", "B", "B").forEach { assertFalse(d.afterAction(it), it) }
        assertTrue(d.afterAction("B"))
    }

    @Test
    fun neverStuckWhenEveryScreenDiffers() {
        val d = StuckDetector()
        repeat(100) { assertFalse(d.afterAction("screen $it")) }
    }

    @Test
    fun differentScreensWithTheSameHashAreNotConfused() {
        assertTrue("Aa".hashCode() == "BB".hashCode(), "test premise: these strings collide")
        val d = StuckDetector()
        d.afterAction("Aa")
        d.afterAction("BB") // a change, so the count restarts here
        assertFalse(d.afterAction("BB"))
        assertFalse(d.afterAction("BB"))
        assertTrue(d.afterAction("BB"))
    }

    @Test
    fun resetForgetsHistory() {
        val d = StuckDetector()
        repeat(4) { d.afterAction("A") }
        d.reset()
        assertFalse(d.afterAction("A"), "first call after reset is a new baseline")
        assertFalse(d.afterAction("A"))
    }

    @Test
    fun limitIsConfigurable() {
        val d = StuckDetector(limit = 2)
        d.afterAction("A")
        assertFalse(d.afterAction("A"))
        assertTrue(d.afterAction("A"))
    }
}
