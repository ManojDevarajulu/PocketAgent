package dev.pocketagent.agent

import dev.pocketagent.device.ActionResult
import dev.pocketagent.device.NotificationPort
import dev.pocketagent.device.PhoneNotification
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OtpQuickActionTest {
    private val now = 10_000_000L
    private val minute = 60_000L

    private class Port(var access: Boolean = true, var items: List<PhoneNotification> = emptyList(), var failure: Throwable? = null) : NotificationPort {
        var recentCalls = 0
        override fun hasAccess() = access
        override suspend fun recent(): List<PhoneNotification> {
            recentCalls++
            failure?.let { throw it }
            return items
        }
        override suspend fun reply(key: String, text: String) = ActionResult.Ok
    }

    private fun n(
        text: String?,
        agoMs: Long,
        title: String? = "VM-HDFCBK",
        pkg: String = "com.google.android.apps.messaging",
        label: String? = "Messages",
        lines: List<String>? = null,
    ) = PhoneNotification(
        key = "k${text}$agoMs", packageName = pkg, appLabel = label, title = title, conversation = null,
        lines = lines ?: listOfNotNull<String>(text), postedAtMs = now - agoMs, canReply = false,
    )

    private fun run(port: Port) = runBlocking { OtpQuickAction(port) { now }.run() }

    @Test fun newestCodeWins() {
        val port = Port(items = listOf(
            n("Your OTP is 222222", 5 * minute),
            n("Your OTP is 111111", 2 * minute),
        ))
        assertEquals(OtpResult.Found("111111", "VM-HDFCBK", 2), run(port))
    }

    @Test fun newestWinsRegardlessOfListOrder() {
        val port = Port(items = listOf(
            n("Your OTP is 111111", 2 * minute),
            n("Your OTP is 222222", 5 * minute),
        ))
        assertEquals("111111", (run(port) as OtpResult.Found).code)
    }

    @Test fun exactlyTenMinutesIsStillAccepted() {
        val r = run(Port(items = listOf(n("code 482913", 10 * minute))))
        assertEquals(OtpResult.Found("482913", "VM-HDFCBK", 10), r)
    }

    @Test fun tenMinutesAndOneSecondIsRefused() {
        assertEquals(OtpResult.NoneRecent, run(Port(items = listOf(n("code 482913", 10 * minute + 1_000)))))
    }

    @Test fun ignoresNotificationsWithoutACode() {
        val port = Port(items = listOf(
            n("See you at 5", 1 * minute),
            n("Your OTP is 333333", 4 * minute),
        ))
        assertEquals("333333", (run(port) as OtpResult.Found).code)
    }

    @Test fun onlyCodelessNotificationsGiveNoneRecent() {
        assertEquals(OtpResult.NoneRecent, run(Port(items = listOf(n("lunch?", minute)))))
    }

    @Test fun codeInTheTitleCounts() {
        val r = run(Port(items = listOf(n(null, 3 * minute, title = "654321 is your code", lines = listOf("tap to view")))))
        assertEquals(OtpResult.Found("654321", "654321 is your code", 3), r)
    }

    @Test fun codeInAnyLineCounts() {
        val r = run(Port(items = listOf(n(null, minute, lines = listOf("hello", "G-123456 is your code")))))
        assertEquals("123456", (r as OtpResult.Found).code)
    }

    @Test fun bankAndBlockedSourcesAreIncluded() {
        val r = run(Port(items = listOf(n("OTP 778899 for your payment", 2 * minute, title = "VM-HDFCBK", pkg = "com.phonepe.app", label = "PhonePe"))))
        assertEquals(OtpResult.Found("778899", "VM-HDFCBK", 2), r)
    }

    @Test fun fromFallsBackToAppLabelThenPackage() {
        val a = run(Port(items = listOf(n("code 112233", minute, title = null, label = "Messages"))))
        assertEquals("Messages", (a as OtpResult.Found).from)
        val b = run(Port(items = listOf(n("code 112233", minute, title = null, label = null, pkg = "com.x.y"))))
        assertEquals("com.x.y", (b as OtpResult.Found).from)
    }

    @Test fun noAccessDoesNotReadNotifications() {
        val port = Port(access = false, items = listOf(n("code 112233", minute)))
        assertEquals(OtpResult.NoAccess, run(port))
        assertEquals(0, port.recentCalls)
    }

    @Test fun emptyListIsNoneRecent() {
        assertEquals(OtpResult.NoneRecent, run(Port()))
    }

    @Test fun futureTimestampClampsToZeroMinutes() {
        val r = run(Port(items = listOf(n("code 112233", -5 * minute))))
        assertEquals(OtpResult.Found("112233", "VM-HDFCBK", 0), r)
    }

    @Test fun minutesAgoIsFlooredNotRounded() {
        val r = run(Port(items = listOf(n("code 112233", 2 * minute + 59_000))))
        assertEquals(2, (r as OtpResult.Found).minutesAgo)
    }

    @Test fun aFailingPortGivesNoneRecentNotACrashOrNoAccess() {
        assertEquals(OtpResult.NoneRecent, run(Port(failure = IllegalStateException("boom"))))
    }

    @Test fun cancellationIsNotSwallowed() {
        assertFailsWith<CancellationException> { run(Port(failure = CancellationException("stop"))) }
    }

    @Test fun hasNoAiClient() {
        val params = OtpQuickAction::class.java.constructors.single().parameterTypes
        assertTrue(params.isNotEmpty())
        assertFalse(params.any { it.name.contains("LlmClient") })
    }

    @Test fun triggerWordInTheTitleFindsTheCodeInALine() {
        val r = run(Port(items = listOf(n(null, minute, title = "Login OTP", lines = listOf("Use 4829 within 5 min")))))
        assertEquals(OtpResult.Found("4829", "Login OTP", 1), r)
    }

    @Test fun digitsWithNoTriggerAnywhereAreNotACode() {
        assertEquals(OtpResult.NoneRecent, run(Port(items = listOf(n(null, minute, title = "Rahul", lines = listOf("Room 4821 at 5pm"))))))
    }

    @Test fun aNumericShortCodeTitleDoesNotHideTheRealCode() {
        val port = Port(items = listOf(n(null, minute, title = "22000", lines = listOf("G-482913 is your Google verification code"))))
        assertEquals(OtpResult.Found("482913", "22000", 1), run(port))
    }
}
