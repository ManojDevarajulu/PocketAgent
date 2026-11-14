package dev.pocketagent.agent

enum class BubbleMode { IDLE, RUNNING, WAITING, DONE, ERROR, RECORDING }

enum class BubbleCommand { NONE, OPEN_TASK_SCREEN, CANCEL, STOP_RECORDING }

const val BUBBLE_RESULT_MS = 3_000L

/** What the floating bubble shows and what its gestures do; the overlay only draws and forwards touches. */
class BubbleLogic {
    /** Set by the UI while the task screen is in front, so a tap on the bubble does not reopen it. */
    var taskScreenOpen: Boolean = false

    var mode: BubbleMode = BubbleMode.IDLE
        private set

    /** While a routine is being recorded the bubble is a stop button, and task state does not change it. */
    fun setRecording(on: Boolean) {
        mode = if (on) BubbleMode.RECORDING else BubbleMode.IDLE
    }

    fun onState(state: AgentState) {
        if (mode == BubbleMode.RECORDING) return
        mode = when (state) {
            AgentState.Idle -> BubbleMode.IDLE
            AgentState.Running -> BubbleMode.RUNNING
            AgentState.WaitingForUser -> BubbleMode.WAITING
            is AgentState.Finished -> if (state.outcome.success) BubbleMode.DONE else BubbleMode.ERROR
            AgentState.Stopped -> BubbleMode.ERROR
        }
    }

    fun onTap(): BubbleCommand = when (mode) {
        BubbleMode.IDLE, BubbleMode.DONE, BubbleMode.ERROR ->
            if (taskScreenOpen) BubbleCommand.NONE else BubbleCommand.OPEN_TASK_SCREEN
        BubbleMode.RUNNING, BubbleMode.WAITING -> BubbleCommand.NONE
        BubbleMode.RECORDING -> BubbleCommand.STOP_RECORDING
    }

    fun onLongPress(): BubbleCommand = when (mode) {
        BubbleMode.RUNNING, BubbleMode.WAITING -> BubbleCommand.CANCEL
        BubbleMode.IDLE, BubbleMode.DONE, BubbleMode.ERROR, BubbleMode.RECORDING -> BubbleCommand.NONE
    }

    /** The overlay calls this [BUBBLE_RESULT_MS] after DONE or ERROR. */
    fun returnToIdle() {
        if (mode == BubbleMode.DONE || mode == BubbleMode.ERROR) mode = BubbleMode.IDLE
    }
}
