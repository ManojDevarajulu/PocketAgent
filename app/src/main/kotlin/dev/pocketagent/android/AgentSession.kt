package dev.pocketagent.android

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import dev.pocketagent.R
import dev.pocketagent.agent.AgentLoop
import dev.pocketagent.agent.AgentRunner
import dev.pocketagent.agent.AgentState
import dev.pocketagent.agent.AgentUi
import dev.pocketagent.agent.BubbleCommand
import dev.pocketagent.agent.BubbleLogic
import dev.pocketagent.agent.ConfirmRequest
import dev.pocketagent.agent.SummaryText
import dev.pocketagent.agent.VoiceUi
import dev.pocketagent.device.DeviceAction
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
import dev.pocketagent.safety.NotificationFilter
import dev.pocketagent.safety.SafetyGate
import dev.pocketagent.setup.ConfigRepository
import dev.pocketagent.setup.HistoryStore
import dev.pocketagent.setup.SetupSettings
import dev.pocketagent.ui.BubbleOverlay
import dev.pocketagent.ui.CaptionView
import dev.pocketagent.ui.ConfirmCard
import dev.pocketagent.ui.OverlayUi
import dev.pocketagent.ui.SetupActivity
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
class AgentSession(
    private val service: PocketAccessibilityService,
    private val repository: ConfigRepository,
    private val settings: SetupSettings,
    private val history: HistoryStore,
) : TaskScreenHost {
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
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private var hiddenForRead = false
    private var hiddenForLock = false
    private val lockPoll = object : Runnable {
        override fun run() {
            if (closed) return
            syncBubble()
            val locked = keyguard?.isKeyguardLocked == true
            if (locked != hiddenForLock) {
                hiddenForLock = locked
                applyOverlayVisibility()
            }
            main.postDelayed(this, LOCK_POLL_MS)
        }
    }
    private val device = AccessibilityDevice(service, settler, ::setOverlaysHidden) { closed }

    private val blocked = BlockedApps()
    private val gate = SafetyGate(blocked, ownPackage)
    private val serializer = ScreenSerializer(ownPackage, maxChars = MAX_SCREEN_CHARS)
    private val playbooks = loadPlaybooks()

    /** Loaded at each task start; null when there is no valid stored config, so a bubble tap opens setup instead. */
    private fun currentClient(): LlmClient? {
        val config = repository.load() ?: return null
        return try {
            Providers.create(config, HttpCaller(OkHttpTransport()))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** True for the task started from the setup screen, so a success can mark the test task done. */
    private var setupTaskRunning = false
    private var taskText = ""
    private var taskStartedAt = 0L

    /**
     * The loop only reaches the overlays through this wrapper. Caption and waiting changes are applied on the main
     * thread and only while a task is live, so a caption posted just before a cancel or [close] cannot reappear.
     */
    private val guardedUi = guarded { activeUi }

    /** What a [VoiceUi] wraps: the overlays, with the same guard, since it also shows captions from the loop's thread. */
    private val guardedOverlayUi = guarded { overlayUi }

    /**
     * Delegates to [target]; caption and waiting changes run on the main thread (at once when already there, so a
     * change forwarded from the main thread keeps its order with ones posted from the loop) and only while a task is live.
     */
    private fun guarded(target: () -> AgentUi): AgentUi = object : AgentUi {
        override suspend fun confirm(request: ConfirmRequest): Boolean = target().confirm(request)
        override suspend fun ask(question: String): String? = target().ask(question)
        override fun onCaption(caption: String) = onMainWhileLive { target().onCaption(caption) }
        override fun onWaitingForUser(waiting: Boolean) = onMainWhileLive { target().onWaitingForUser(waiting) }
    }

    private fun onMainWhileLive(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (taskIsLive()) block()
        } else {
            main.post { if (taskIsLive()) block() }
        }
    }

    /**
     * The UI of the current task: a [VoiceUi] wrapping [overlayUi] for a spoken task with Talk back on, else [overlayUi].
     * Written on the main thread before the runner starts; the loop reads it from its own thread.
     */
    @Volatile
    private var activeUi: AgentUi = overlayUi

    /** The voice UI of the current or just-finished task, kept after the end so its last line can still be stopped. */
    private var voiceUi: VoiceUi? = null

    /** Created once per service life, the first time Talk back may be needed, so the engine is ready before a task starts. */
    private var speaker: TtsSpeaker? = null
    private var listener: RecognizerListener? = null

    /** True when this task wanted voice but speech was not available; the summary card then says so, once per service run. */
    private var speechMissing = false
    private var speechNoteShown = false

    /** Set just before the runner starts a task; the loop factory reads it once. */
    private var pendingClient: LlmClient? = null

    private val runner = AgentRunner(
        scope,
        { ui ->
            AgentLoop(
                checkNotNull(pendingClient), device, gate, playbooks, serializer, ui,
                notifications = AndroidNotifications,
                notificationFilter = NotificationFilter(blocked, ownPackage),
            )
        },
        guardedUi,
    )

    /** Read at bubble tap, handed to the next task, dropped when the task starts or the task screen is dismissed. */
    private var savedScreen: Screen? = null
    private var tapJob: Job? = null

    init {
        TaskScreenHost.current = this
        main.post(lockPoll)
        scope.launch(Dispatchers.Main) { runner.state.collect(::onState) }
    }

    fun showBubble() = syncBubble()

    /** The bubble follows the setup switch; it is only removed while no task is live, so a running task can always be stopped. */
    private fun syncBubble() {
        if (closed) return
        if (settings.bubbleOn) bubble.show() else if (!taskIsLive()) bubble.hide()
    }

    fun onEvent() = settler.onEvent()

    /**
     * The Quick Settings tile: close the notification shade, give the screen a moment to settle so the shade is not
     * what gets read, then do what a bubble tap does.
     */
    fun startFromTile() {
        if (closed || taskIsLive()) return
        val shade = if (Build.VERSION.SDK_INT >= 31) {
            AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
        } else {
            AccessibilityService.GLOBAL_ACTION_BACK
        }
        service.performGlobalAction(shade)
        main.postDelayed({ startBubbleTap() }, TILE_SETTLE_MS)
    }

    /** Hide the bubble, read the screen without settling, show the bubble, keep the screen, open the task screen. */
    fun startBubbleTap() {
        if (closed || tapJob?.isActive == true) return
        summaryCard.close()
        // The previous task's last spoken line stops with its card.
        if (!taskIsLive()) stopVoice()
        if (currentClient() == null) {
            openSetup()
            return
        }
        logic.taskScreenOpen = true
        // The speech engine starts up in the background while the user speaks the task.
        if (settings.talkBack) createVoiceEngines()
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

    override fun startTask(text: String, spoken: Boolean) {
        logic.taskScreenOpen = false
        val screen = savedScreen
        savedScreen = null
        val task = text.trim()
        val client = currentClient()
        if (closed || screen == null || client == null || task.isEmpty() || task.length > TaskInputActivity.MAX_TEXT) {
            dropHandlesIfNoTask()
            return
        }
        begin(task, screen, client, fromSetup = false, spoken = spoken)
    }

    private fun begin(task: String, screen: Screen, client: LlmClient, fromSetup: Boolean, spoken: Boolean = false) {
        // Every start runs on the main thread and the runner refuses a second task, so a live task keeps its UI.
        if (taskIsLive()) return
        pendingClient = client
        setupTaskRunning = fromSetup
        // A new task silences whatever the previous one was still saying.
        stopVoice()
        speechMissing = false
        val voice = if (spoken && settings.talkBack) voiceFor(task) else null
        voiceUi = voice
        activeUi = voice ?: overlayUi
        if (runner.start(task, screen)) {
            taskText = task
            taskStartedAt = System.currentTimeMillis()
            voice?.start()
        } else {
            setupTaskRunning = false
            stopVoice()
        }
    }

    private fun createVoiceEngines() {
        if (closed) return
        // An engine error leaves a speaker unusable for good; a fresh one lets the next task speak again.
        if (speaker?.failed == true) {
            speaker?.shutdown()
            speaker = null
        }
        if (speaker == null) speaker = TtsSpeaker(service)
        if (listener == null) listener = RecognizerListener(service)
    }

    /** A voice UI for [task], or null (and the note on the summary card) when the speech engine is not ready. */
    private fun voiceFor(task: String): VoiceUi? {
        createVoiceEngines()
        val tts = speaker
        val ears = listener
        if (tts == null || ears == null || !tts.available) {
            speechMissing = true
            return null
        }
        return VoiceUi(guardedOverlayUi, tts, ears, task)
    }

    /** Stops speech and listening of the voice UI, if any, and goes back to the plain overlays. */
    private fun stopVoice() {
        voiceUi?.stop()
        voiceUi = null
        activeUi = overlayUi
    }

    /** The test task of the setup screen: go Home, read the settled screen, run the task there. */
    override fun startSetupTask(text: String) {
        val task = text.trim()
        if (closed || task.isEmpty() || task.length > TaskInputActivity.MAX_TEXT || taskIsLive()) return
        val client = currentClient() ?: return
        scope.launch {
            val screen = try {
                device.perform(DeviceAction.Home)
                device.readScreen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeviceUnavailable) {
                withContext(Dispatchers.Main) { tapFailed(e.reason) }
                return@launch
            } catch (e: RuntimeException) {
                withContext(Dispatchers.Main) { tapFailed(null) }
                return@launch
            }
            withContext(Dispatchers.Main) { if (!closed) begin(task, screen, client, fromSetup = true) }
        }
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
        main.removeCallbacks(lockPoll)
        runner.cancel()
        scope.cancel()
        stopVoice()
        speaker?.shutdown()
        listener?.shutdown()
        speaker = null
        listener = null
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
            is AgentState.Finished -> {
                if (setupTaskRunning && state.outcome.success) repository.testTaskDone = true
                record(state.outcome.success, SummaryText.forHistory(state.outcome), state.outcome.usage.inputTokens + state.outcome.usage.outputTokens)
                setupTaskRunning = false
                // Not stopped afterwards, so the result is heard in full; the next task, a cancel or close() stops it.
                if (!taskIsLive()) {
                    voiceUi?.end(state.outcome)
                    activeUi = overlayUi
                }
                endTask(SummaryText.title(state), SummaryText.body(state.outcome))
            }
            AgentState.Stopped -> {
                setupTaskRunning = false
                record(false, "Stopped by you", 0)
                if (!taskIsLive()) stopVoice()
                endTask(SummaryText.stoppedTitle(), "")
            }
            else -> Unit
        }
    }

    /** Saves the finished task to Activity (only the task text and how it went), once per task. */
    private fun record(ok: Boolean, summary: String, tokens: Long) {
        if (taskText.isEmpty()) return
        history.add(taskText, ok, summary, taskStartedAt, tokens)
        taskText = ""
    }

    /** Runs once per ending; skipped when a newer task already started before this state was collected. */
    private fun endTask(title: String, body: String) {
        if (taskIsLive()) return
        overlayUi.closeAll()
        TaskInputActivity.closeAnswerScreen()
        device.dropHandles()
        summaryCard.show(title, withSpeechNote(body))
    }

    /** Adds the unavailable-speech note to [body] the first time a voice task had to run silently. */
    private fun withSpeechNote(body: String): String {
        if (!speechMissing) return body
        speechMissing = false
        if (speechNoteShown) return body
        speechNoteShown = true
        return if (body.isEmpty()) SPEECH_UNAVAILABLE_NOTE else "$body\n\n$SPEECH_UNAVAILABLE_NOTE"
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
            BubbleCommand.CANCEL -> {
                runner.cancel()
                stopVoice()
            }
            BubbleCommand.NONE -> Unit
        }
    }

    /** Hides (true) or shows (false) the bubble and caption on the main thread; does nothing once closed. */
    private suspend fun setOverlaysHidden(hidden: Boolean) {
        withContext(Dispatchers.Main) {
            if (closed) return@withContext
            hiddenForRead = hidden
            applyOverlayVisibility()
        }
    }

    /** Bubble and caption show only when neither a screen read or gesture nor the lock screen hides them. */
    private fun applyOverlayVisibility() {
        val visible = !hiddenForRead && !hiddenForLock
        bubble.setVisible(visible)
        caption.setVisible(visible)
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

    private fun openSetup() {
        try {
            service.startActivity(Intent(service, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Nothing else to show.
        } catch (e: SecurityException) {
            // Nothing else to show.
        }
    }

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
        const val LOCK_POLL_MS = 500L
        const val TILE_SETTLE_MS = 450L
        const val PLAYBOOK_DIR = "playbooks"
        const val SPEECH_UNAVAILABLE_NOTE = "Speech isn't available on this phone, so Pocket Agent stayed silent."
    }
}
