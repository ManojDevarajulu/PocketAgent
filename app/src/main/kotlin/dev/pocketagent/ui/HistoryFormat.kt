package dev.pocketagent.ui

import dev.pocketagent.setup.HistoryEntry
import dev.pocketagent.ui.kit.ActivityDetail
import dev.pocketagent.ui.kit.ActivityRow
import dev.pocketagent.ui.kit.RecentItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Turns saved history into the words and numbers the screens show. */
object HistoryFormat {
    fun rows(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<ActivityRow> =
        entries.map { ActivityRow(it.id, it.ok, it.task, sub(it), relative(it.startedAt, now), day(it.startedAt, now)) }

    fun recent(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<RecentItem> =
        entries.take(3).map { RecentItem(it.ok, it.task, sub(it), relative(it.startedAt, now), it.id) }

    fun detail(e: HistoryEntry, now: Long, onRun: () -> Unit, onDelete: () -> Unit): ActivityDetail = ActivityDetail(
        ok = e.ok,
        title = e.task,
        whenText = SimpleDateFormat(if (day(e.startedAt, now) == "Today") "'Today,' h:mm a" else "d MMMM, h:mm a", Locale.getDefault()).format(Date(e.startedAt)),
        result = e.summary,
        duration = duration(e.durationMs),
        tokens = tokens(e.tokens),
        onRun = onRun,
        onDelete = onDelete,
    )

    /** "Today: 3 tasks, about 18k tokens used." for the foot of Home, or null when nothing ran today. */
    fun todayLine(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): String? {
        val today = entries.filter { day(it.startedAt, now) == "Today" }
        if (today.isEmpty()) return null
        val t = today.sumOf { it.tokens }
        return "Today: ${today.size} ${if (today.size == 1) "task" else "tasks"}" + if (t > 0) ", about ${tokens(t)} tokens used." else "."
    }

    private fun sub(e: HistoryEntry): String = e.summary.ifBlank { duration(e.durationMs) }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s < 60) "$s s" else "${s / 60} min ${s % 60} s"
    }

    fun tokens(n: Long): String = when {
        n <= 0 -> "none"
        n < 1000 -> n.toString()
        else -> String.format(Locale.US, "%.1fk", n / 1000.0)
    }

    private fun relative(t: Long, now: Long): String {
        val d = (now - t).coerceAtLeast(0)
        return when {
            d < 60_000 -> "now"
            d < 3_600_000 -> "${d / 60_000} min"
            d < 86_400_000 -> "${d / 3_600_000} h"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
        }
    }

    private fun day(t: Long, now: Long): String {
        fun key(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        val k = key(t)
        val today = key(now)
        return when (k) {
            today -> "Today"
            today - 1 -> "Yesterday"
            else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(t))
        }
    }
}
