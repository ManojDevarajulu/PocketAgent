package dev.pocketagent.agent

/**
 * What the user is asked to approve. [detail] is the last text the agent typed (200 characters at most), so the
 * user can see what a "Send" tap would send. It is kept in memory only.
 */
data class ConfirmRequest(val action: String, val target: String, val app: String, val detail: String? = null)

/** Everything the agent needs from the user interface. */
interface AgentUi {
    /** True if the user approves. The loop gives up waiting after its confirmation timeout and counts that as no. */
    suspend fun confirm(request: ConfirmRequest): Boolean

    /** The user's answer, or null if there was none. */
    suspend fun ask(question: String): String?

    fun onCaption(caption: String)

    fun onWaitingForUser(waiting: Boolean)
}
