package dev.pocketagent.device

/**
 * Turns notifications into the text the model sees. Notification text is untrusted, so every field is
 * cleaned: it cannot close the wrapper tag or start a line that looks like an item. Items arrive already
 * masked by the filter; this class does not filter.
 */
class NotificationSerializer {
    data class Result(val text: String, val keysById: Map<String, String>)

    fun serialize(items: List<PhoneNotification>, nowMs: Long): Result {
        val keys = LinkedHashMap<String, String>()
        val out = StringBuilder("<notification_data>\n")
        if (items.isEmpty()) out.append("(no recent notifications)\n")
        for ((i, n) in items.withIndex()) {
            val id = "n${i + 1}"
            keys[id] = n.key
            out.append('[').append(id).append("] ").append(header(n, nowMs)).append('\n')
            for (line in n.lines) {
                val clean = cleanUntrusted(line, MAX_LINE)
                if (clean.isNotEmpty()) out.append("  ").append(clean).append('\n')
            }
        }
        return Result(out.append("</notification_data>").toString(), keys)
    }

    private fun header(n: PhoneNotification, nowMs: Long): String {
        val app = cleanUntrusted(n.appLabel ?: n.packageName, MAX_FIELD)
        val title = n.title?.let { cleanUntrusted(it, MAX_FIELD) }.orEmpty()
        val conversation = n.conversation?.let { cleanUntrusted(it, MAX_FIELD) }.orEmpty()
        val who = when {
            conversation.isNotEmpty() && title.isNotEmpty() -> "$conversation: $title"
            conversation.isNotEmpty() -> conversation
            else -> title
        }
        return listOfNotNull(
            app,
            who.ifEmpty { null },
            age(nowMs - n.postedAtMs),
            if (n.canReply) "can reply" else null,
        ).joinToString(" · ")
    }

    private fun age(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "<1 min ago"
            minutes < 60 -> "$minutes min ago"
            else -> "${minutes / 60} h ago"
        }
    }

    private companion object {
        const val MAX_LINE = 300
        const val MAX_FIELD = 80
    }
}
