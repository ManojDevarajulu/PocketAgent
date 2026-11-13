package dev.pocketagent.fakes

import dev.pocketagent.agent.AgentUi
import dev.pocketagent.agent.ConfirmRequest
import dev.pocketagent.agent.ToolDefinition
import dev.pocketagent.device.ActionResult
import dev.pocketagent.device.DeviceAction
import dev.pocketagent.device.DevicePort
import dev.pocketagent.device.DeviceUnavailable
import dev.pocketagent.device.NotificationPort
import dev.pocketagent.device.PhoneNotification
import dev.pocketagent.device.Screen
import dev.pocketagent.llm.LlmClient
import dev.pocketagent.llm.LlmResult
import dev.pocketagent.llm.LlmSession
import dev.pocketagent.llm.TokenUsage
import dev.pocketagent.llm.Turn

/** Replays a scripted list of results and records every session and every turn it was sent. */
class FakeLlm(
    private val script: List<LlmResult>,
    override val supportsImages: Boolean = true,
    private val usage: TokenUsage = TokenUsage(),
) : LlmClient {
    var sessions = 0
        private set
    var systemPrompt: String? = null
        private set
    var tools: List<ToolDefinition> = emptyList()
        private set
    val turns = mutableListOf<Turn>()

    override fun newSession(systemPrompt: String, tools: List<ToolDefinition>): LlmSession {
        sessions++
        this.systemPrompt = systemPrompt
        this.tools = tools
        val reported = usage
        return object : LlmSession {
            override val usage: TokenUsage get() = reported
            override suspend fun send(turn: Turn): LlmResult {
                turns += turn
                check(turns.size <= script.size) { "FakeLlm script exhausted after ${script.size} results" }
                return script[turns.size - 1]
            }
        }
    }
}

/** Returns the scripted screens in order from [readScreen] (the last one repeats) and records every action. */
class FakeDevice(
    private val screens: List<Screen>,
    private val screenshotBytes: ByteArray? = byteArrayOf(1, 2, 3),
    private val failReadsFrom: Int? = null,
    private val resultFor: (DeviceAction) -> ActionResult = { ActionResult.Ok },
) : DevicePort {
    val actions = mutableListOf<DeviceAction>()
    var screenshotCalls = 0
        private set
    var reads = 0
        private set

    override suspend fun readScreen(): Screen {
        if (failReadsFrom != null && reads >= failReadsFrom) throw DeviceUnavailable("The phone locked")
        return screens[minOf(reads++, screens.lastIndex)]
    }

    override suspend fun perform(action: DeviceAction): ActionResult {
        actions += action
        return resultFor(action)
    }

    override suspend fun screenshot(): ByteArray? {
        screenshotCalls++
        return screenshotBytes
    }
}

class FakeUi : AgentUi {
    var onConfirm: suspend (ConfirmRequest) -> Boolean = { true }
    var onAsk: suspend (String) -> String? = { null }
    val confirmRequests = mutableListOf<ConfirmRequest>()
    val questions = mutableListOf<String>()
    val captions = mutableListOf<String>()
    val waiting = mutableListOf<Boolean>()

    override suspend fun confirm(request: ConfirmRequest): Boolean {
        confirmRequests += request
        return onConfirm(request)
    }

    override suspend fun ask(question: String): String? {
        questions += question
        return onAsk(question)
    }

    override fun onCaption(caption: String) {
        captions += caption
    }

    override fun onWaitingForUser(waiting: Boolean) {
        this.waiting += waiting
    }
}

/** Serves a scripted notification list and records every reply. */
class FakeNotifications(
    var access: Boolean = true,
    var items: List<PhoneNotification> = emptyList(),
) : NotificationPort {
    val replies = mutableListOf<Pair<String, String>>()
    var replyResult: ActionResult = ActionResult.Ok

    override fun hasAccess(): Boolean = access
    override suspend fun recent(): List<PhoneNotification> = items
    override suspend fun reply(key: String, text: String): ActionResult {
        replies += key to text
        return replyResult
    }
}
