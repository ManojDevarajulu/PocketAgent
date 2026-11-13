package dev.pocketagent.agent

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickTasksTest {
    @Test
    fun keepsTheSuggestionsInOrder() {
        assertEquals(
            listOf("Catch me up", "Reply to this", "Take me to an address", "What's this?", "Latest OTP"),
            QuickTasks.all.map { it.title },
        )
    }

    @Test
    fun everyTaskIsMoreSpecificThanItsTitle() {
        QuickTasks.all.filter { it.onPhone == null }.forEach {
            assertTrue(it.task.length > it.title.length + 20, "${it.title}: task is too vague")
            assertFalse(it.task.equals(it.title, ignoreCase = true), it.title)
        }
    }

    @Test
    fun addressTaskNamesTheAppAndTheGoal() {
        val task = QuickTasks.all.first { it.title == "Take me to an address" }.task
        assertTrue("Google Maps" in task, task)
        assertTrue("directions" in task.lowercase(), task)
    }

    @Test
    fun addressTaskTellsTheAgentToClearAnOldSearch() {
        val task = QuickTasks.all.first { it.title == "Take me to an address" }.task
        assertTrue("clear" in task.lowercase(), task)
    }

    @Test
    fun tasksFitTheInputBoxAndAreSingleLinePlainText() {
        QuickTasks.all.forEach {
            assertTrue(it.task.length <= QuickTasks.MAX_TASK_CHARS, it.title)
            assertTrue(it.task.none { c -> c.isISOControl() }, it.title)
        }
    }

    @Test
    fun replyTaskDoesNotPromiseToSendWithoutAsking() {
        val task = QuickTasks.all.first { it.title == "Reply to this" }.task.lowercase()
        assertFalse("without asking" in task)
        assertFalse("don't ask" in task)
    }

    @Test
    fun onlyLatestOtpRunsOnThePhoneWithNoTaskText() {
        val onPhone = QuickTasks.all.filter { it.onPhone != null }
        assertEquals(listOf("Latest OTP"), onPhone.map { it.title })
        assertEquals(OnPhone.LATEST_OTP, onPhone.single().onPhone)
        assertEquals("", onPhone.single().task)
        assertTrue(QuickTasks.all.filter { it.onPhone == null }.all { it.task.isNotBlank() })
    }

    @Test
    fun readThisScreenOutIsGone() {
        assertTrue(QuickTasks.all.none { it.title == "Read this screen out" })
    }

    @Test
    fun catchMeUpUsesNotificationsAndNeverReplies() {
        val t = QuickTasks.all.first { it.title == "Catch me up" }
        assertTrue("read_notifications" in t.task)
        assertTrue("Do not reply" in t.task)
        // Without notification access the tool is not offered, so the task must not depend on it.
        assertTrue("if you have a read_notifications tool" in t.task, t.task)
        assertTrue("otherwise open my messaging apps" in t.task, t.task)
        assertEquals("New messages, in short", t.subtitle)
    }

    @Test
    fun whatsThisChecksForScamsAndChangesNothing() {
        val t = QuickTasks.all.first { it.title == "What's this?" }
        assertTrue("scam" in t.task)
        assertTrue("Do not change anything" in t.task)
        assertEquals("Explains, checks for scams", t.subtitle)
    }
}
