package dev.pocketagent.agent

sealed interface AgentState {
    data object Idle : AgentState
    data object Running : AgentState
    data object WaitingForUser : AgentState
    data class Finished(val outcome: TaskOutcome) : AgentState
    data object Stopped : AgentState
}
