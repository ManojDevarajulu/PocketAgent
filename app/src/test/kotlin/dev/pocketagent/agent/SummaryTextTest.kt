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
    ) = TaskOutcome(reason, success, summary, usage)

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
    fun bodyCutsTheSummaryAt300Characters() {
        val body = SummaryText.body(outcome(summary = "a".repeat(400)))
        assertEquals("Agent's summary: " + "a".repeat(300) + "\nTokens: 10 in, 5 out", body)
    }

    @Test
    fun bodyReplacesLineBreaks() {
        val body = SummaryText.body(outcome(summary = "one\ntwo\r\nthree\tfour"))
        assertEquals("Agent's summary: one two  three four\nTokens: 10 in, 5 out", body)
    }

    @Test
    fun bodyShowsTokenTotals() {
        val body = SummaryText.body(outcome(usage = TokenUsage(1200, 34, 500)))
        assertEquals("Agent's summary: ok\nTokens: 1200 in, 34 out, 500 cached", body)
    }

    @Test
    fun bodyOmitsEmptySummary() {
        assertEquals("Tokens: 10 in, 5 out", SummaryText.body(outcome(summary = "")))
    }

    @Test
    fun bodyShowsTimeWhenTheModelWasCalled() {
        val body = SummaryText.body(outcome().copy(stats = TaskStats(steps = 7, modelMs = 29_400, totalMs = 34_200)))
        assertEquals("Agent's summary: ok\nTime: 34 s, 7 steps, 29 s of it waiting for the model\nTokens: 10 in, 5 out", body)
    }

    @Test
    fun shortTimesKeepOneDecimal() {
        assertEquals("Time: 4.3 s, 1 step, 3.0 s of it waiting for the model", SummaryText.time(TaskStats(1, 2_960, 4_260)))
        assertEquals(null, SummaryText.time(TaskStats()))
    }
}
