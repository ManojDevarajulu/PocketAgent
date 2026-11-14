package dev.pocketagent.agent

import dev.pocketagent.llm.TokenUsage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryTextTest {
    private fun outcome(
        reason: EndReason = EndReason.DONE,
        success: Boolean = true,
        summary: String = "ok",
        usage: TokenUsage = TokenUsage(10, 5),
        usedNotifications: Boolean = false,
    ) = TaskOutcome(reason, success, summary, usage).copy(usedNotifications = usedNotifications)

    @Test
    fun everyEndReasonHasATitle() {
        for (reason in EndReason.entries) {
            val title = SummaryText.title(AgentState.Finished(outcome(reason, success = false)))
            assertTrue(title.isNotBlank(), "no title for $reason")
        }
    }

    @Test
    fun titlesMatchTheSpec() {
        fun title(reason: EndReason, success: Boolean = false) =
            SummaryText.title(AgentState.Finished(outcome(reason, success)))
        assertEquals("Done", title(EndReason.DONE, success = true))
        assertEquals("Not finished", title(EndReason.STEP_LIMIT))
        assertEquals("Not finished", title(EndReason.STUCK))
        assertEquals("Not finished", title(EndReason.INVALID_CALLS))
        assertEquals("Declined by the model", title(EndReason.REFUSED))
        assertEquals("Key rejected", title(EndReason.AUTH_ERROR))
        assertEquals("Model error", title(EndReason.LLM_ERROR))
        assertEquals("Blocked app", title(EndReason.BLOCKED_APP))
        assertEquals("Phone unavailable", title(EndReason.DEVICE_UNAVAILABLE))
    }

    @Test
    fun doneNotSuccessfulIsNotFinished() {
        assertEquals("Not finished", SummaryText.title(AgentState.Finished(outcome(EndReason.DONE, success = false))))
    }

    @Test
    fun stoppedTitleIsFixed() {
        assertEquals("Stopped by you", SummaryText.stoppedTitle())
    }

    @Test
    fun bodyIsJustTheSummaryCutAt300Characters() {
        assertEquals("a".repeat(300), SummaryText.body(outcome(summary = "a".repeat(400))))
    }

    @Test
    fun bodyReplacesLineBreaks() {
        assertEquals("one two  three four", SummaryText.body(outcome(summary = "one\ntwo\r\nthree\tfour")))
    }

    @Test
    fun bodyIsEmptyWhenThereIsNoSummary() {
        assertEquals("", SummaryText.body(outcome(summary = "")))
    }

    @Test
    fun bodyNeverMentionsTokensCostTimeOrSteps() {
        val busy = outcome(usage = TokenUsage(1200, 34, 500)).copy(stats = TaskStats(steps = 7, modelMs = 29_400, totalMs = 34_200))
        val body = SummaryText.body(busy).lowercase()
        listOf("token", "cost", "cached", "step", "waiting for the model", "agent's summary").forEach {
            assertTrue(it !in body, "\"$it\" should not be shown to the user: $body")
        }
        assertEquals("ok", SummaryText.body(busy))
    }

    @Test
    fun historyHidesNotificationSummaries() =
        assertEquals(SummaryText.HIDDEN_SUMMARY, SummaryText.forHistory(outcome(usedNotifications = true, summary = "Mom: dinner at 8")))

    @Test
    fun historyHidesFailedAndEmptyNotificationSummaries() {
        assertEquals(SummaryText.HIDDEN_SUMMARY, SummaryText.forHistory(outcome(success = false, usedNotifications = true, summary = "Code 123456")))
        assertEquals(SummaryText.HIDDEN_SUMMARY, SummaryText.forHistory(outcome(usedNotifications = true, summary = "")))
    }

    @Test
    fun hiddenSummaryTextIsFixed() =
        assertEquals("Summary hidden (it used your notifications)", SummaryText.HIDDEN_SUMMARY)

    @Test
    fun historyKeepsOtherSummaries() {
        assertEquals("Opened Maps", SummaryText.forHistory(outcome(summary = "Opened Maps")))
        assertEquals("Checked notifications settings", SummaryText.forHistory(outcome(summary = "Checked notifications settings")))
        assertEquals("", SummaryText.forHistory(outcome(summary = "")))
    }

    @Test
    fun bodyStillShowsFullSummaryWhenNotificationsWereUsed() {
        assertEquals("Mom: dinner at 8", SummaryText.body(outcome(usedNotifications = true, summary = "Mom: dinner at 8")))
    }
}
