package dev.pocketagent.safety

import dev.pocketagent.device.PhoneNotification

/**
 * Chooses which notifications the model may see: drops Pocket Agent's own, ongoing and group-summary ones, anything
 * older than 24 hours, blocked apps, and bank or payment messages from any app. What is left is newest first, at
 * most 15, with the last 5 lines each, codes masked in every field when any field has a trigger word, and every
 * line cut to 300 characters.
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
            .map { n -> masked(n) }
            .toList()

    /**
     * Codes are masked across the whole notification: a trigger word in any field (even a line dropped by the
     * five-line cap) masks codes in every field, so "Login OTP" as the title hides the code in the text.
     */
    private fun masked(n: PhoneNotification): PhoneNotification {
        val lines = n.lines.takeLast(MAX_LINES)
        val all = CodeFinder.maskAll(listOfNotNull(n.title, n.conversation) + n.lines)
        var i = 0
        val title = n.title?.let { all[i++] }
        val conversation = n.conversation?.let { all[i++] }
        val shown = all.drop(i + n.lines.size - lines.size)
        return n.copy(
            title = title?.take(MAX_LINE),
            conversation = conversation?.take(MAX_LINE),
            lines = shown.map { it.take(MAX_LINE) },
        )
    }

    /** Title and conversation get the stricter name check; lines get the message check, with the title as sender. */
    private fun isMoney(n: PhoneNotification): Boolean {
        val names = listOfNotNull(n.title, n.conversation)
        if (names.any { MoneyMessages.isMoneyName(it) }) return true
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
