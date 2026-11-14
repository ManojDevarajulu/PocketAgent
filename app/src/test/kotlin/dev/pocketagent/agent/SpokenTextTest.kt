package dev.pocketagent.agent

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpokenTextTest {
    private fun outcome(success: Boolean, summary: String = "") =
        TaskOutcome(if (success) EndReason.DONE else EndReason.STUCK, success, summary)

    @Test fun fixedStrings() {
        assertEquals("On it.", SpokenText.started)
        assertEquals("Sorry, say yes or no.", SpokenText.reAsk)
        assertEquals("There's no text to read.", SpokenText.noDetail)
    }

    @Test fun confirmDoesNotRepeatTargetOrApp() {
        assertEquals("Replying to Rahul in WhatsApp? Say yes or no.",
            SpokenText.confirm(ConfirmRequest("Replying to Rahul in WhatsApp", "Rahul", "WhatsApp", "on my way")))
        assertEquals("Tapping Send in Gmail? Say yes or no.", SpokenText.confirm(ConfirmRequest("Tapping Send", "Send", "Gmail")))
        assertEquals("Tapping an item, a position on the screen, in Gmail? Say yes or no.",
            SpokenText.confirm(ConfirmRequest("Tapping an item", "a position on the screen", "Gmail")))
    }

    @Test fun detail() {
        assertEquals("It says: on my way. Say yes or no.", SpokenText.detail(ConfirmRequest("a", "b", "c", "on my way")))
        assertTrue(SpokenText.detail(ConfirmRequest("a", "b", "c")).startsWith(SpokenText.noDetail))
    }

    @Test fun ending() {
        assertEquals("Done.", SpokenText.ending(outcome(true, "running late"), false))
        assertEquals("Done. running late", SpokenText.ending(outcome(true, "running late"), true))
        assertEquals("Couldn't finish. Check the screen.", SpokenText.ending(outcome(false, "x"), true))
    }

    @Test fun longAndControlTextIsCleaned() {
        val raw = "a\nb\u0007c" + "x".repeat(500)
        for (s in listOf(SpokenText.caption(raw), SpokenText.question(raw), SpokenText.detail(ConfirmRequest("a", "b", "c", raw)), SpokenText.ending(outcome(true, raw), true))) {
            assertTrue(s.length <= SpokenText.MAX, s.length.toString())
            assertFalse(s.any { it.isISOControl() })
        }
        assertEquals("a b c", SpokenText.caption("a\nb\u0007c"))
    }

    @Test fun longVariablePartKeepsTheSuffix() {
        val longDetail = SpokenText.detail(ConfirmRequest("a", "b", "c", "d".repeat(200)))
        assertTrue(longDetail.endsWith(". Say yes or no."), longDetail)
        assertTrue(longDetail.startsWith("It says: ddd"))
        assertTrue(longDetail.length <= SpokenText.MAX, longDetail.length.toString())

        val longConfirm = SpokenText.confirm(ConfirmRequest("Sending " + "w".repeat(300), "Rahul", "WhatsApp"))
        assertTrue(longConfirm.endsWith("? Say yes or no."), longConfirm)
        assertTrue(longConfirm.startsWith("Sending www"))
        assertTrue(longConfirm.length <= SpokenText.MAX, longConfirm.length.toString())
    }
}
