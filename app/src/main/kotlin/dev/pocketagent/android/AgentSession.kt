package dev.pocketagent.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import dev.pocketagent.BuildConfig
import dev.pocketagent.R
import dev.pocketagent.agent.AgentLoop
import dev.pocketagent.agent.AgentRunner
import dev.pocketagent.agent.AgentState
import dev.pocketagent.agent.AgentUi
import dev.pocketagent.agent.BubbleCommand
import dev.pocketagent.agent.BubbleLogic
import dev.pocketagent.agent.ConfirmRequest
import dev.pocketagent.agent.SummaryText
import dev.pocketagent.device.DeviceUnavailable
import dev.pocketagent.device.Screen
import dev.pocketagent.device.ScreenSerializer
import dev.pocketagent.device.Settler
import dev.pocketagent.llm.HttpCaller
import dev.pocketagent.llm.LlmClient
import dev.pocketagent.llm.OkHttpTransport
import dev.pocketagent.llm.Providers
import dev.pocketagent.playbook.PlaybookStore
import dev.pocketagent.safety.BlockedApps
import dev.pocketagent.safety.SafetyGate
import dev.pocketagent.ui.BubbleOverlay
import dev.pocketagent.ui.CaptionView
import dev.pocketagent.ui.ConfirmCard
import dev.pocketagent.ui.OverlayUi
import dev.pocketagent.ui.SummaryCard
import dev.pocketagent.ui.TaskInputActivity
import dev.pocketagent.ui.TaskScreenHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Owns the runner, the device and the overlays for the life of the service (S3 spec §2, §4, §6 and §7).
 * Every method is called on the main thread, and every field below is read and written there, except [closed],
 * which the device also reads from its own threads.
 */
class AgentSession(private val service: PocketAccessibilityService) : TaskScreenHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val ownPackage: String = service.packageName

    @Volatile
    private var closed = false

    private val settler = Settler(now = SystemClock::uptimeMillis)
    private val logic = BubbleLogic()
    private val bubble = BubbleOverlay(service, logic, ::onBubbleCommand)
    private val caption = CaptionView(service)
    private val confirmCard = ConfirmCard(service)
    private val summaryCard = SummaryCard(service)
    private val overlayUi = OverlayUi(confirmCard, caption, ::openAnswerScreen)
    private val device = AccessibilityDevice(service, settler, ::setOverlaysHidden) { closed }

    private val blocked = BlockedApps()
    private val gate = SafetyGate(blocked, ownPackage)
    private val serializer = ScreenSerializer(ownPackage, maxChars = MAX_SCREEN_CHARS)
    private val playbooks = loadPlaybooks()

    /** Null when there is no valid provider config: a bubble tap then starts no task (spec §7). */
    private val client: LlmClient? = DevConfig.load()?.let { config ->
        try {
            Providers.create(config, HttpCaller(OkHttpTransport()))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * The loop only reaches the overlays through this wrapper. Caption and waiting changes are applied on the main
     * thread and only while a task is live, so a caption posted just before a cancel or [close] cannot reappear.
     */
    private val guardedUi = object : AgentUi {
        override suspend fun confirm(request: ConfirmRequest): Boolean = overlayUi.confirm(request)
        override suspend fun ask(question: String): String? = overlayUi.ask(question)
        override fun onCaption(caption: String) {
            main.post { if (taskIsLive()) overlayUi.onCaption(caption) }
        }
        override fun onWaitingForUser(waiting: Boolean) {
            main.post { if (taskIsLive()) overlayUi.onWaitingForUser(waiting) }
        }
    }

    private val runner = AgentRunner(
        scope,
        { ui -> AgentLoop(checkNotNull(client), device, gate, playbooks, serializer, ui) },
        guardedUi,
    )

    /** Read at bubble tap, handed to the next task, dropped when the task starts or the task screen is dismissed. */
    private var savedScreen: Screen? = null
    private var tapJob: Job? = null

    init {
        TaskScreenHost.current = this
        scope.launch(Dispatchers.Main) { runner.state.collect(::onState) }
    }

    fun showBubble() {
        if (!closed) bubble.show()
    }

    fun onEvent() = settler.onEvent()

    /** Hide the bubble, read the screen without settling, show the bubble, keep the screen, open the task screen. */
    fun startBubbleTap() {
        if (closed || tapJob?.isActive == true) return
        summaryCard.close()
        if (client == null) {
            summaryCard.show(service.getString(R.string.no_model_title), noModelBody())
            return
        }
        logic.taskScreenOpen = true
        tapJob = scope.launch {
            val screen = try {
                setOverlaysHidden(true)
                try {
                    device.readScreenNow()
                } finally {
                    withContext(NonCancellable) { setOverlaysHidden(false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeviceUnavailable) {
                withContext(Dispatchers.Main) { tapFailed(e.reason) }
                return@launch
            } catch (e: RuntimeException) {
                // A binder call failed while reading; nothing was saved.
                withContext(Dispatchers.Main) { tapFailed(null) }
                return@launch
            }
            withContext(Dispatchers.Main) { openTaskScreen(screen) }
        }
    }

    override fun startTask(text: String) {
        logic.taskScreenOpen = false
        val screen = savedScreen
        savedScreen = null
        val task = text.trim()
        if (closed || screen == null || client == null || task.isEmpty() || task.length > TaskInputActivity.MAX_TEXT) {
            dropHandlesIfNoTask()
            return
        }
        runner.start(task, screen)
    }

    override fun dropSavedScreen() {
        logic.taskScreenOpen = false
        savedScreen = null
        dropHandlesIfNoTask()
    }

    override fun completeAnswer(answer: String?) {
        overlayUi.completeAnswer(answer)
    }

    /** Cancels the runner and scope, removes every overlay, drops the screen and node map, clears the holder. */
    fun close() {
        if (closed) return
        // Read before setting closed: taskIsLive() is false once the session is closed.
        val taskWasLive = taskIsLive()
        closed = true
        runner.cancel()
        scope.cancel()
        overlayUi.closeAll()
        TaskInputActivity.closeAnswerScreen()
        bubble.close()
        caption.close()
        confirmCard.close()
        summaryCard.close()
        main.removeCallbacksAndMessages(null)
        logic.taskScreenOpen = false
        savedScreen = null
        device.dropHandles()
        if (TaskScreenHost.current === this) TaskScreenHost.current = null
        if (taskWasLive) {
            // The overlays are gone with the service, so a Toast is the only way left to say so (spec §7).
            Toast.makeText(service.applicationContext, R.string.service_stopped, Toast.LENGTH_LONG).show()
        }
    }

    // --- State ---

    private fun onState(state: AgentState) {
        if (closed) return
        logic.onState(state)
        // render() also schedules the Done/Error return to Idle after BUBBLE_RESULT_MS, and cancels a pending one
        // on every call, so a newer state always wins.
        bubble.render(logic.mode)
        val live = state is AgentState.Running || state is AgentState.WaitingForUser
        service.subscribeToTaskEvents(live)
        when (state) {
            is AgentState.Finished -> endTask(SummaryText.title(state), SummaryText.body(state.outcome))
            AgentState.Stopped -> endTask(SummaryText.stoppedTitle(), "")
            else -> Unit
        }
    }

    /** Runs once per ending; skipped when a newer task already started before this state was collected. */
    private fun endTask(title: String, body: String) {
        if (taskIsLive()) return
        overlayUi.closeAll()
        TaskInputActivity.closeAnswerScreen()
        device.dropHandles()
        summaryCard.show(title, body)
    }

    private fun taskIsLive(): Boolean {
        if (closed) return false
        val state = runner.state.value
        return state is AgentState.Running || state is AgentState.WaitingForUser
    }

    // --- Bubble ---

    private fun onBubbleCommand(command: BubbleCommand) {
        if (closed) return
        when (command) {
            BubbleCommand.OPEN_TASK_SCREEN -> startBubbleTap()
            BubbleCommand.CANCEL -> runner.cancel()
            BubbleCommand.NONE -> Unit
        }
    }

    /** Hides (true) or shows (false) the bubble and caption on the main thread; does nothing once closed. */
    private suspend fun setOverlaysHidden(hidden: Boolean) {
        withContext(Dispatchers.Main) {
            if (closed) return@withContext
            bubble.setVisible(!hidden)
            caption.setVisible(!hidden)
        }
    }

    private fun tapFailed(reason: String?) {
        logic.taskScreenOpen = false
        dropHandlesIfNoTask()
        if (closed) return
        summaryCard.show(
            service.getString(R.string.phone_unavailable_title),
            reason ?: service.getString(R.string.read_failed),
        )
    }

    private fun openTaskScreen(screen: Screen) {
        if (closed) return
        savedScreen = screen
        val intent = Intent(service, TaskInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            dropSavedScreen()
        } catch (e: SecurityException) {
            dropSavedScreen()
        }
    }

    private fun openAnswerScreen(question: String) {
        if (!taskIsLive()) {
            overlayUi.completeAnswer(null)
            return
        }
        val intent = Intent(service, TaskInputActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(TaskInputActivity.EXTRA_QUESTION, question)
        try {
            service.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            overlayUi.completeAnswer(null)
        } catch (e: SecurityException) {
            overlayUi.completeAnswer(null)
        }
    }

    private fun dropHandlesIfNoTask() {
        if (!taskIsLive()) device.dropHandles()
    }

    private fun noModelBody(): String =
        service.getString(if (BuildConfig.DEBUG) R.string.no_model_debug else R.string.no_model_release)

    /** The bundled playbooks. Their validity is enforced by the unit tests; a bad file here means none are used. */
    private fun loadPlaybooks(): PlaybookStore {
        val files = try {
            service.assets.list(PLAYBOOK_DIR).orEmpty().filter { it.endsWith(".md") }.associateWith { name ->
                service.assets.open("$PLAYBOOK_DIR/$name").use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } catch (e: IOException) {
            emptyMap()
        }
        return try {
            PlaybookStore.load(files, blocked)
        } catch (e: IllegalArgumentException) {
            PlaybookStore.load(emptyMap(), blocked)
        }
    }

    private companion object {
        const val MAX_SCREEN_CHARS = 12_000
        const val PLAYBOOK_DIR = "playbooks"
    }
}
