package dev.pocketagent.safety

import dev.pocketagent.device.PhoneNotification
import org.junit.Test
import kotlin.test.assertEquals

class NotificationFilterTest {
    private val now = 100 * 60 * 60_000L
    private val own = "dev.pocketagent"
    private val filter = NotificationFilter(BlockedApps(), own)

    private fun n(
        pkg: String,
        title: String?,
        text: String,
        ageMin: Long = 1,
        label: String? = null,
        key: String = "$pkg-$title-$ageMin",
        lines: List<String> = listOf(text),
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
        conversation: String? = null,
    ) = PhoneNotification(key, pkg, label, title, conversation, lines, now - ageMin * 60_000L, false, ongoing, groupSummary)

    private fun keys(vararg items: PhoneNotification) = filter.forModel(items.toList(), now).map { it.key }

    @Test fun blockedAppDroppedByPackageAndLabel() {
        assertEquals(listOf("keep"), keys(
            n("com.phonepe.app", "Rahul", "hi", key = "pkg"),
            n("com.example.chat", "Rahul", "hi", label = "HDFC Bank", key = "label"),
            n("com.example.chat", "Rahul", "hi", label = "Chat", key = "keep"),
        ))
    }

    @Test fun bankSmsDropped() {
        assertEquals(listOf("keep"), keys(
            n("com.google.android.apps.messaging", "VM-HDFCBK", "Hello", key = "bank"),
            n("com.google.android.apps.messaging", "Mom", "Dinner at 8?", key = "keep"),
        ))
    }

    @Test fun whatsAppBankAlertIsDropped() {
        assertEquals(listOf("keep"), keys(
            n("com.whatsapp", "HDFC Bank", "Rs 500 debited", key = "bank"),
            n("com.whatsapp", "Rahul", "Dinner at 8?", key = "keep"),
        ))
    }

    @Test fun moneyInAnyLineDropsNotificationEvenWhenFirstLineIsPlain() {
        assertEquals(listOf("keep"), keys(
            n("com.whatsapp", "Rahul", "", key = "late", lines = listOf("hello", "Rs 500 debited from A/c")),
            n("com.whatsapp", "Rahul", "", key = "keep", lines = listOf("hello", "see you")),
        ))
    }

    @Test fun ownOngoingAndGroupSummaryDropped() {
        assertEquals(listOf("keep"), keys(
            n(own, "Agent", "running", key = "own"),
            n("com.example.chat", "Rahul", "hi", key = "ongoing", ongoing = true),
            n("com.example.chat", "Rahul", "hi", key = "summary", groupSummary = true),
            n("com.example.chat", "Rahul", "hi", key = "keep"),
        ))
    }

    @Test fun olderThan24HoursDropped() {
        assertEquals(listOf("fresh", "edge"), keys(
            n("com.example.chat", "A", "hi", ageMin = 24 * 60 + 1, key = "old"),
            n("com.example.chat", "A", "hi", ageMin = 24 * 60, key = "edge"),
            n("com.example.chat", "A", "hi", ageMin = 5, key = "fresh"),
        ))
    }

    @Test fun codesMaskedInLinesAndTitle() {
        val out = filter.forModel(listOf(n("com.example.chat", "Code 482913", "482913 is your OTP")), now).single()
        assertEquals(listOf("•••••• is your OTP"), out.lines)
        assertEquals("Code ••••••", out.title)
    }

    @Test fun capsTo15NewestFirst() {
        val input = (1..20).map { n("com.example.chat", "T$it", "hi", ageMin = it.toLong(), key = "k$it") }.shuffled()
        assertEquals((1..15).map { "k$it" }, filter.forModel(input, now).map { it.key })
    }

    @Test fun capsLinesAndLength() {
        val eight = (1..8).map { "line$it" }
        val out = filter.forModel(listOf(n("com.example.chat", "A", "", lines = eight + "x".repeat(400))), now).single()
        assertEquals(listOf("line5", "line6", "line7", "line8", "x".repeat(300)), out.lines)
        val short = filter.forModel(listOf(n("com.example.chat", "A", "", lines = listOf("y".repeat(300)))), now).single()
        assertEquals(listOf("y".repeat(300)), short.lines)
    }

    @Test fun bankStyleConversationNameDropped() {
        assertEquals(listOf("keep"), keys(
            n("com.whatsapp", "Rahul", "hi", conversation = "HDFC Bank alerts", key = "bank"),
            n("com.whatsapp", "Rahul", "hi", conversation = "VM-HDFCBK", key = "header"),
            n("com.whatsapp", "Rahul", "hi", conversation = "Family", key = "keep"),
        ))
    }

    @Test fun codeInConversationNameMasked() {
        val out = filter.forModel(listOf(n("com.whatsapp", "Rahul", "hi", conversation = "Code 482913")), now).single()
        assertEquals("Code ••••••", out.conversation)
    }

    @Test fun ordinaryConversationNameKeptUnchangedAndLongOneCut() {
        val out = filter.forModel(listOf(
            n("com.whatsapp", "Rahul", "hi", conversation = "Family", key = "a"),
            n("com.whatsapp", "Rahul", "hi", conversation = "g".repeat(400), key = "b", ageMin = 2),
        ), now)
        assertEquals("Family", out[0].conversation)
        assertEquals("g".repeat(300), out[1].conversation)
    }

    @Test fun moneyLineBeyondFiveLineCapStillDropsNotification() {
        val lines = listOf("a", "Rs 500 debited from A/c", "c", "d", "e", "f", "g", "h")
        assertEquals(listOf("keep"), keys(
            n("com.whatsapp", "Rahul", "", lines = lines, key = "drop"),
            n("com.whatsapp", "Rahul", "", lines = lines.map { "ok" }, key = "keep"),
        ))
    }

    @Test fun moneyOnlyTitleWithPlainLinesDropped() {
        assertEquals(listOf("keep"), keys(
            n("com.whatsapp", "AX-PAYTMB", "hello there", key = "drop"),
            n("com.whatsapp", "Rahul", "hello there", key = "keep"),
        ))
    }

    @Test fun bankWordInTitleWithPlainLinesDropped() {
        assertEquals(listOf("keep"), keys(
            n("com.whatsapp", "HDFC Bank", "hello there", key = "drop"),
            n("com.whatsapp", "Rahul", "hello there", key = "keep"),
        ))
    }

    @Test fun namesThatOnlyStartLikeMoneyWordsAreKept() {
        assertEquals(listOf("a", "b", "c", "d"), keys(
            n("com.whatsapp", "Bankey", "hello", key = "a", ageMin = 1),
            n("com.whatsapp", "Loanna", "hello", key = "b", ageMin = 2),
            n("com.whatsapp", "Rahul", "hello", conversation = "Bankim", key = "c", ageMin = 3),
            n("com.whatsapp", "Balancer", "hello", key = "d", ageMin = 4),
        ))
    }
}
