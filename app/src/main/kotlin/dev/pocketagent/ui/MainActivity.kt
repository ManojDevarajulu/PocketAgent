package dev.pocketagent.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Toast
import dev.pocketagent.android.PocketAccessibilityService
import dev.pocketagent.android.SetupStores
import dev.pocketagent.llm.CheckResult
import dev.pocketagent.setup.AccessibilityState
import dev.pocketagent.setup.MicState
import dev.pocketagent.setup.SetupFacts
import dev.pocketagent.setup.SetupModel
import dev.pocketagent.setup.SetupStep
import dev.pocketagent.setup.StepStatus
import dev.pocketagent.ui.kit.Attention
import dev.pocketagent.ui.kit.HomeState
import dev.pocketagent.ui.kit.Nav
import dev.pocketagent.ui.kit.Screens
import dev.pocketagent.ui.kit.SettingsState
import dev.pocketagent.ui.kit.Shell

/**
 * The app: welcome on first run, then Home, Activity and Settings. Provider, key, model and the accessibility
 * steps still use the older [SetupActivity] until those cards are redrawn in the new style.
 */
class MainActivity : Activity() {
    private val repository by lazy { SetupStores.repository(this) }
    private val settings by lazy { SetupStores.settings(this) }
    private lateinit var content: FrameLayout
    private lateinit var shell: Shell

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
        shell.reset(if (settings.noticeAccepted) "home" else "welcome")
    }

    override fun onResume() {
        super.onResume()
        if (::shell.isInitialized && shell.current in setOf("home", "settings")) shell.refresh()
    }

    @Deprecated("The shell handles back for pushed screens")
    override fun onBackPressed() {
        if (!shell.pop()) super.onBackPressed()
    }

    // --- screens ---

    private fun build(name: String, nav: Nav): View = when (name) {
        "welcome" -> Screens.welcome(this, nav)
        "notice" -> Screens.notice(this, nav, onAccept = {
            settings.noticeAccepted = true
            shell.reset("home", Shell.Transition.FADE)
        })
        "activity" -> Screens.activity(this, nav, empty = true)
        "settings" -> Screens.settings(this, settingsState(), nav)
        else -> Screens.home(this, homeState(), nav)
    }

    private fun accessibilityOn() = AccessibilityState.isEnabled(
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        packageName,
        PocketAccessibilityService::class.java.name,
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
        )
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
            recent = emptyList(),
            usage = null,
            onRun = { text ->
                val host = TaskScreenHost.current
                if (host == null) {
                    Toast.makeText(this, "Switch on Pocket Agent in accessibility settings first.", Toast.LENGTH_LONG).show()
                } else {
                    host.startSetupTask(text)
                }
            },
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
            onMic = { openSetup() },
            showHistory = false,
        )
    }
}
