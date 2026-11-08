package dev.pocketagent.agent

import dev.pocketagent.device.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs at most one task at a time and publishes its [state]. [cancel] stops the task at once: the coroutine is
 * cancelled, so a pending model call or confirmation ends and a pending action never runs. Going back from
 * `Finished` or `Stopped` to `Idle` is the UI's job.
 */
class AgentRunner(
    private val scope: CoroutineScope,
    private val newLoop: (AgentUi) -> AgentLoop,
    private val ui: AgentUi,
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** Each task gets its own token, so a task that was cancelled can never overwrite the state of a newer one. */
    private var current: Any? = null

    /** False, and nothing changes, if a task is already running or waiting for the user. */
    fun start(task: String, screen: Screen): Boolean = synchronized(lock) {
        if (mutableState.value is AgentState.Running || mutableState.value is AgentState.WaitingForUser) return false
        val token = Any()
        current = token
        mutableState.value = AgentState.Running
        job = scope.launch {
            val result = try {
                newLoop(forwardingUi(token)).run(task, screen)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The text is deliberately generic: internal error messages are not shown to the user.
                TaskOutcome(EndReason.LLM_ERROR, false, "Something went wrong inside the app. Please try again.")
            }
            setIfCurrent(token, AgentState.Finished(result))
        }
        true
    }

    /** Stops the running task. Does nothing when no task is running. */
    fun cancel() {
        synchronized(lock) {
            if (mutableState.value !is AgentState.Running && mutableState.value !is AgentState.WaitingForUser) return
            job?.cancel()
            job = null
            current = null
            mutableState.value = AgentState.Stopped
        }
    }

    private fun setIfCurrent(token: Any, new: AgentState) = synchronized(lock) {
        if (current === token) mutableState.value = new
    }

    /** Passes everything to the real UI, and mirrors the waiting flag into [state]. */
    private fun forwardingUi(token: Any) = object : AgentUi {
        override suspend fun confirm(request: ConfirmRequest) = ui.confirm(request)
        override suspend fun ask(question: String) = ui.ask(question)
        override fun onCaption(caption: String) = ui.onCaption(caption)
        override fun onWaitingForUser(waiting: Boolean) {
            setIfCurrent(token, if (waiting) AgentState.WaitingForUser else AgentState.Running)
            ui.onWaitingForUser(waiting)
        }
    }
}
