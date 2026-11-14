package dev.pocketagent.ui

import dev.pocketagent.setup.HistoryEntry
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryFormatTest {
    private val now = 1_800_000_000_000L

    private fun entry(ok: Boolean = true, summary: String = "Opened Maps", tokens: Long = 18_000) =
        HistoryEntry(1, "task", ok, summary, now - 60_000, 34_000, tokens)

    @Test fun theHomeFooterCountsTasksAndNothingElse() {
        assertEquals("Today: 1 task.", HistoryFormat.todayLine(listOf(entry()), now))
        assertEquals("Today: 2 tasks.", HistoryFormat.todayLine(listOf(entry(), entry()), now))
        assertEquals(null, HistoryFormat.todayLine(emptyList(), now))
    }

    @Test fun aRowWithNoSummaryJustSaysHowItEnded() {
        assertEquals("Done", HistoryFormat.rows(listOf(entry(summary = "")), now).single().sub)
        assertEquals("Stopped", HistoryFormat.rows(listOf(entry(ok = false, summary = "")), now).single().sub)
    }

    @Test fun nothingTechnicalReachesTheScreens() {
        val shown = HistoryFormat.rows(listOf(entry()), now).single().toString() +
            HistoryFormat.recent(listOf(entry()), now).single().toString() +
            HistoryFormat.todayLine(listOf(entry()), now)
        assertTrue(!shown.contains("token", ignoreCase = true) && !shown.contains("18"), shown)
    }
}
