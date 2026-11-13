package dev.pocketagent.agent

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickTasksTest {
    @Test
    fun keepsTheThreeSuggestionsInOrder() {
        assertEquals(
            listOf("Take me to an address", "Read this screen out", "Reply to this"),
            QuickTasks.all.map { it.title },
        )
    }

    @Test
    fun everyTaskIsMoreSpecificThanItsTitle() {
        QuickTasks.all.forEach {
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
}
