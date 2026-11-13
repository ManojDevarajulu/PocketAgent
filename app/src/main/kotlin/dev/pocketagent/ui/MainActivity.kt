package dev.pocketagent.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Toast
import dev.pocketagent.android.PocketAccessibilityService
import dev.pocketagent.android.PocketNotificationListener
import dev.pocketagent.android.SetupStores
import dev.pocketagent.llm.CheckResult
import dev.pocketagent.setup.AccessibilityState
import dev.pocketagent.setup.MicState
import dev.pocketagent.setup.SetupFacts
import dev.pocketagent.setup.SetupModel
import dev.pocketagent.setup.SetupStep
import dev.pocketagent.setup.isListenerEnabled
import dev.pocketagent.setup.StepStatus
import dev.pocketagent.ui.kit.Attention
import dev.pocketagent.ui.kit.DefaultShortcuts
import dev.pocketagent.ui.kit.ThemeMode
import dev.pocketagent.ui.kit.HomeState
import dev.pocketagent.ui.kit.Nav
import dev.pocketagent.ui.kit.Screens
import dev.pocketagent.ui.kit.SettingsState
import dev.pocketagent.ui.kit.Shell

/**
 * The app: welcome on first run, then Home, Activity and Settings. Provider, key, model and the accessibility
 * steps still use the older [SetupActivity] until those cards are redrawn in the new style.
 */
class MainActivity : ThemedActivity() {
    private val repository by lazy { SetupStores.repository(this) }
    private val settings by lazy { SetupStores.settings(this) }
    private val history by lazy { SetupStores.history(this) }
    private lateinit var content: FrameLayout
    private lateinit var shell: Shell
    private var editingShortcuts = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        val host = FrameLayout(this)
        content = FrameLayout(this)
        host.addView(content, FrameLayout.LayoutParams(-1, -1))
        host.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            (content.layoutParams as FrameLayout.LayoutParams).apply { topMargin = bars.top; bottomMargin = bars.bottom }
            content.requestLayout()
            insets
        }
        setContentView(host)
        shell = Shell(this, content, ::build)
        shell.reset(savedInstanceState?.getString("screen") ?: if (settings.noticeAccepted) "home" else "welcome")
    }

    override fun onResume() {
        super.onResume()
        if (::shell.isInitialized && shell.current in setOf("home", "settings", "activity")) shell.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shell.current?.let { outState.putString("screen", it) }
    }

    @Deprecated("The shell handles back for pushed screens")
    override fun onBackPressed() {
        if (!shell.pop()) super.onBackPressed()
    }

    // --- screens ---

    private fun build(name: String, nav: Nav): View = when {
        name == "welcome" -> Screens.welcome(this, nav)
        name == "notice" -> Screens.notice(this, nav, onAccept = {
            settings.noticeAccepted = true
            shell.reset("home", Shell.Transition.FADE)
        })
        name == "activity" -> Screens.activity(this, nav, rows = HistoryFormat.rows(history.all()))
        name == "settings" -> Screens.settings(this, settingsState(), nav)
        name == "blocked" -> Screens.blockedApps(this, nav)
        name == "licences" -> Screens.licences(this, nav)
        name.startsWith("detail:") -> detailScreen(name.removePrefix("detail:").toLongOrNull(), nav)
        else -> Screens.home(this, homeState(), nav)
    }

    private fun detailScreen(id: Long?, nav: Nav): View {
        val entry = id?.let { history.find(it) } ?: return Screens.activity(this, nav, rows = HistoryFormat.rows(history.all()))
        return Screens.activityDetail(
            this, nav,
            HistoryFormat.detail(entry, System.currentTimeMillis(),
                onRun = { runTask(entry.task) },
                onDelete = {
                    history.remove(entry.id)
                    shell.reset("activity", Shell.Transition.FADE)
                },
            ),
        )
    }

    private fun runTask(text: String) {
        val host = TaskScreenHost.current
        if (host == null) {
            Toast.makeText(this, "Switch on Pocket Agent in accessibility settings first.", Toast.LENGTH_LONG).show()
        } else {
            host.startSetupTask(text)
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found to open $url", Toast.LENGTH_LONG).show()
        }
    }

    private fun accessibilityOn() = AccessibilityState.isEnabled(
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        packageName,
        PocketAccessibilityService::class.java.name,
    )

    private fun notificationsOn(): Boolean = isListenerEnabled(
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners"),
        ComponentName(this, PocketNotificationListener::class.java).flattenToString(),
    )

    private fun micOn() = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun facts(): SetupFacts {
        val config = repository.load()
        return SetupFacts(
            noticeAccepted = settings.noticeAccepted,
            acceptedCustomHost = settings.acceptedCustomHost,
            config = config,
            modelCheck = if (repository.modelCheckPassed && config != null) CheckResult.Works else null,
            accessibilityOn = accessibilityOn(),
            microphone = if (micOn()) MicState.GRANTED else if (settings.microphoneAsked) MicState.DENIED else MicState.NOT_ASKED,
            bubbleOn = settings.bubbleOn,
            testTaskDone = repository.testTaskDone,
            notificationsOn = notificationsOn(),
        )
    }

    /** Settings > Microphone and the Home mic button: ask for the permission, or open the app's permission page if it is already on. */
    private fun requestMic() {
        if (micOn()) {
            openSettingsPage(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_REQUEST_MIC, true))
        }
    }

    private fun openSettingsPage(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openSetup()
        }
    }

    private fun openSetup() = startActivity(Intent(this, SetupActivity::class.java))

    private fun openAccessibility() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            openSetup()
        }
    }

    private fun homeState(): HomeState {
        val eval = SetupModel.evaluate(facts())
        val current = eval.steps.firstOrNull { it.status == StepStatus.CURRENT }?.step
        val attention = when {
            eval.isReady -> null
            current == SetupStep.ACCESSIBILITY && eval.of(SetupStep.MODEL_CHECK).status == StepStatus.DONE ->
                Attention("Accessibility is off", "Pocket Agent can't tap or read the screen until you turn it on.", "Turn it on") { openAccessibility() }
            else -> Attention("Finish setting up", "Add your AI provider, key and model, then switch on accessibility.", "Continue setup") { openSetup() }
        }
        return HomeState(
            statusDetail = "Model works · Accessibility on" + if (settings.bubbleOn) " · Bubble on" else "",
            attention = attention,
            recent = HistoryFormat.recent(history.all()),
            usage = HistoryFormat.todayLine(history.all()),
            shortcuts = settings.shortcuts ?: DefaultShortcuts,
            editingShortcuts = editingShortcuts,
            onEditShortcuts = { editingShortcuts = !editingShortcuts; shell.refresh() },
            onRemoveShortcut = { gone ->
                settings.shortcuts = (settings.shortcuts ?: DefaultShortcuts).filter { it != gone }
                shell.refresh()
            },
            onAddShortcut = {
                ShortcutDialog.show(this) { text ->
                    settings.shortcuts = ((settings.shortcuts ?: DefaultShortcuts) + text).distinct()
                    shell.refresh()
                }
            },
            onRun = { text -> runTask(text) },
            micGranted = { micOn() },
            onNeedMic = { requestMic() },
        )
    }

    private fun settingsState(): SettingsState {
        val config = repository.load()
        val version = try {
            "Version " + packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "Pocket Agent"
        }
        return SettingsState(
            version = version,
            provider = config?.host?.removePrefix("api.")?.removePrefix("www.") ?: "Not set",
            keySaved = !config?.apiKey.isNullOrEmpty(),
            accessibilityOn = accessibilityOn(),
            micOn = micOn(),
            bubbleOn = settings.bubbleOn,
            onBubble = { settings.bubbleOn = it },
            onProvider = { openSetup() },
            onAccessibility = { openAccessibility() },
            onMic = { requestMic() },
            showHistory = true,
            historyOn = history.enabled,
            onHistory = { history.enabled = it },
            onClearHistory = {
                SheetDialogs.confirm(this, "Clear history?", "This removes the list of tasks on this phone. It can't be undone.", "Clear") {
                    history.clear()
                    shell.refresh()
                }
            },
            onRiskyInfo = {
                SheetDialogs.info(
                    this, "Ask before risky actions",
                    "Pocket Agent always asks before anything that sends, pays, posts or deletes. This is a safety rule and cannot be turned off.",
                )
            },
            onStar = { openUrl(REPO) },
            onReport = { openUrl("$REPO/issues/new") },
            theme = ThemeMode.current(this),
            onTheme = { mode ->
                if (mode != ThemeMode.current(this)) {
                    ThemeMode.set(this, mode)
                    recreate()
                }
            },
        )
    }

    private companion object {
        const val REPO = "https://github.com/ManojDevarajulu/PocketAgent"
    }
}
