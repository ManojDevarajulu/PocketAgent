package dev.pocketagent.safety

import dev.pocketagent.device.PhoneNotification

/**
 * Chooses which notifications the model may see: drops Pocket Agent's own, ongoing and group-summary ones, anything
 * older than 24 hours, blocked apps, and bank or payment messages from any app. What is left is newest first, at
 * most 15, with the last 5 lines each, codes masked in title and lines, and every line cut to 300 characters.
 */
class NotificationFilter(private val blocked: BlockedApps, private val ownPackage: String) {
    fun forModel(all: List<PhoneNotification>, nowMs: Long): List<PhoneNotification> =
        all.asSequence()
            .filterNot { it.packageName == ownPackage || it.ongoing || it.groupSummary }
            .filter { nowMs - it.postedAtMs <= MAX_AGE_MS }
            .filterNot { blocked.isBlocked(it.packageName, it.appLabel) }
            .filterNot { isMoney(it) }
            .sortedByDescending { it.postedAtMs }
            .take(MAX_COUNT)
            .map { n ->
                n.copy(
                    title = n.title?.let { CodeFinder.mask(it).take(MAX_LINE) },
                    conversation = n.conversation?.let { CodeFinder.mask(it).take(MAX_LINE) },
                    lines = n.lines.takeLast(MAX_LINES).map { CodeFinder.mask(it).take(MAX_LINE) },
                )
            }
            .toList()

    /** Title and conversation are checked both as a sender header and as text, alone and with every line. */
    private fun isMoney(n: PhoneNotification): Boolean {
        val names = listOfNotNull(n.title, n.conversation)
        if (names.any { MoneyMessages.isMoney(it, it) }) return true
        if (n.lines.any { MoneyMessages.isMoney(null, it) }) return true
        return names.any { name -> n.lines.any { MoneyMessages.isMoney(name, it) } }
    }

    private companion object {
        const val MAX_AGE_MS = 24 * 60 * 60_000L
        const val MAX_COUNT = 15
        const val MAX_LINES = 5
        const val MAX_LINE = 300
    }
}
