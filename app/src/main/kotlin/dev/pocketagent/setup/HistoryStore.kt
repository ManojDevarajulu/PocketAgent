package dev.pocketagent.setup

/** One finished task. Only what the user typed and how it went: never anything that was on the screen. */
data class HistoryEntry(
    val id: Long,
    val task: String,
    val ok: Boolean,
    val summary: String,
    val startedAt: Long,
    val durationMs: Long,
    val tokens: Long,
)

/**
 * The list behind the Activity tab. It lives in the same private store as the settings, newest first, capped at
 * [MAX_ENTRIES], and can be switched off or cleared. Text is trimmed and control characters are removed, so a stored
 * line can never break the format.
 */
class HistoryStore(private val store: KeyValueStore, private val now: () -> Long = System::currentTimeMillis) {
    var enabled: Boolean
        get() = store.getBoolean(ENABLED, true)
        set(value) = store.putBoolean(ENABLED, value)

    fun all(): List<HistoryEntry> =
        (store.getString(KEY) ?: "").split(ENTRY).mapNotNull { parse(it) }

    fun find(id: Long): HistoryEntry? = all().firstOrNull { it.id == id }

    /** Adds a finished task. Does nothing while history is switched off. Returns the saved entry, or null. */
    fun add(task: String, ok: Boolean, summary: String, startedAt: Long, tokens: Long): HistoryEntry? {
        if (!enabled) return null
        val t = clean(task, MAX_TASK)
        if (t.isEmpty()) return null
        val ended = now()
        val entry = HistoryEntry(
            id = maxOf(ended, (all().firstOrNull()?.id ?: 0L) + 1),
            task = t,
            ok = ok,
            summary = clean(summary, MAX_SUMMARY),
            startedAt = startedAt,
            durationMs = (ended - startedAt).coerceAtLeast(0),
            tokens = tokens.coerceAtLeast(0),
        )
        write((listOf(entry) + all()).take(MAX_ENTRIES))
        return entry
    }

    fun remove(id: Long) = write(all().filter { it.id != id })

    fun clear() = store.putString(KEY, null)

    private fun write(entries: List<HistoryEntry>) {
        store.putString(KEY, if (entries.isEmpty()) null else entries.joinToString(ENTRY.toString()) { serialize(it) })
    }

    private fun serialize(e: HistoryEntry) =
        listOf(e.id, if (e.ok) 1 else 0, e.startedAt, e.durationMs, e.tokens, e.task, e.summary).joinToString(FIELD.toString())

    private fun parse(line: String): HistoryEntry? {
        val p = line.split(FIELD)
        if (p.size != 7) return null
        return HistoryEntry(
            id = p[0].toLongOrNull() ?: return null,
            ok = p[1] == "1",
            startedAt = p[2].toLongOrNull() ?: return null,
            durationMs = p[3].toLongOrNull() ?: return null,
            tokens = p[4].toLongOrNull() ?: return null,
            task = p[5],
            summary = p[6],
        )
    }

    private fun clean(text: String, max: Int): String =
        text.map { if (it.isISOControl() || it == FIELD || it == ENTRY) ' ' else it }.joinToString("").trim().replace(Regex(" {2,}"), " ").take(max)

    companion object {
        const val MAX_ENTRIES = 100
        const val MAX_TASK = 300
        const val MAX_SUMMARY = 400
        private const val KEY = "history.v1"
        private const val ENABLED = "history.enabled"
        private const val FIELD = '\u001F'
        private const val ENTRY = '\u001E'
    }
}
