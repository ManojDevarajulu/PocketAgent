package dev.pocketagent.agent

import org.junit.Test
import kotlin.test.assertEquals

class BubbleLogicTest {
    private fun outcome(success: Boolean) = TaskOutcome(EndReason.DONE, success, "s")

    private fun logic(state: AgentState, taskScreenOpen: Boolean = false) = BubbleLogic().apply {
        this.taskScreenOpen = taskScreenOpen
        onState(state)
    }

    @Test
    fun idleTapOpensTheTaskScreen() {
        assertEquals(BubbleCommand.OPEN_TASK_SCREEN, logic(AgentState.Idle).onTap())
    }

    @Test
    fun idleLongPressDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.Idle).onLongPress())
    }

    @Test
    fun idleTapWithTaskScreenOpenDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.Idle, taskScreenOpen = true).onTap())
    }

    @Test
    fun runningTapDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.Running).onTap())
    }

    @Test
    fun runningLongPressCancels() {
        assertEquals(BubbleCommand.CANCEL, logic(AgentState.Running).onLongPress())
    }

    @Test
    fun waitingTapDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.WaitingForUser).onTap())
    }

    @Test
    fun waitingLongPressCancels() {
        assertEquals(BubbleCommand.CANCEL, logic(AgentState.WaitingForUser).onLongPress())
    }

    @Test
    fun doneTapOpensTheTaskScreen() {
        assertEquals(BubbleCommand.OPEN_TASK_SCREEN, logic(AgentState.Finished(outcome(true))).onTap())
    }

    @Test
    fun doneLongPressDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.Finished(outcome(true))).onLongPress())
    }

    @Test
    fun doneTapWithTaskScreenOpenDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.Finished(outcome(true)), taskScreenOpen = true).onTap())
    }

    @Test
    fun errorTapOpensTheTaskScreen() {
        assertEquals(BubbleCommand.OPEN_TASK_SCREEN, logic(AgentState.Stopped).onTap())
    }

    @Test
    fun errorLongPressDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.Stopped).onLongPress())
    }

    @Test
    fun errorTapWithTaskScreenOpenDoesNothing() {
        assertEquals(BubbleCommand.NONE, logic(AgentState.Stopped, taskScreenOpen = true).onTap())
    }

    @Test
    fun stateMappingCoversEveryAgentState() {
        assertEquals(BubbleMode.IDLE, logic(AgentState.Idle).mode)
        assertEquals(BubbleMode.RUNNING, logic(AgentState.Running).mode)
        assertEquals(BubbleMode.WAITING, logic(AgentState.WaitingForUser).mode)
        assertEquals(BubbleMode.DONE, logic(AgentState.Finished(outcome(true))).mode)
        assertEquals(BubbleMode.ERROR, logic(AgentState.Finished(outcome(false))).mode)
        assertEquals(BubbleMode.ERROR, logic(AgentState.Stopped).mode)
    }

    @Test
    fun returnToIdleOnlyLeavesDoneAndError() {
        val staying = mapOf(
            BubbleMode.RUNNING to AgentState.Running,
            BubbleMode.WAITING to AgentState.WaitingForUser,
        )
        for ((mode, state) in staying) {
            val l = logic(state)
            l.returnToIdle()
            assertEquals(mode, l.mode)
        }
        for (state in listOf(AgentState.Finished(outcome(true)), AgentState.Finished(outcome(false)), AgentState.Stopped)) {
            val l = logic(state)
            l.returnToIdle()
            assertEquals(BubbleMode.IDLE, l.mode)
        }
    }

    @Test
    fun resultDelayIsThreeSeconds() {
        assertEquals(3_000L, BUBBLE_RESULT_MS)
    }
}
