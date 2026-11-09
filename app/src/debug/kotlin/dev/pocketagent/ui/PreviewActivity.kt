package dev.pocketagent.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.PopupMenu
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit
import dev.pocketagent.ui.kit.Nav
import dev.pocketagent.ui.kit.Samples
import dev.pocketagent.ui.kit.Screens
import dev.pocketagent.ui.kit.Shell

/**
 * Debug-only gallery of every screen with sample data, same navigation and motion as the app. The "..." button
 * jumps between screens and flips light and dark. Start one directly:
 * `adb shell am start -n dev.pocketagent/.ui.PreviewActivity --es screen home --ez dark true`.
 */
class PreviewActivity : Activity() {
    private var dark = false
    private lateinit var host: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var shell: Shell

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = intent.getBooleanExtra("dark", false)
        window.setDecorFitsSystemWindows(false)
        host = FrameLayout(this)
        content = FrameLayout(this)
        host.addView(content, FrameLayout.LayoutParams(-1, -1))
        val menu = menuButton()
        host.addView(menu)
        host.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            (content.layoutParams as FrameLayout.LayoutParams).apply { topMargin = bars.top; bottomMargin = bars.bottom }
            content.requestLayout()
            insets
        }
        setContentView(host)
        shell = Shell(this, content) { name, nav -> build(name, nav) }
        applyBars()
        open(intent.getStringExtra("screen") ?: "home")
    }

    /** Sheets and setup are their own windows; everything else is a screen in the shell. */
    private fun open(name: String) {
        when (name) {
            "sheet" -> startActivity(Intent(this, TaskInputActivity::class.java))
            "answer" -> startActivity(Intent(this, TaskInputActivity::class.java).putExtra(TaskInputActivity.EXTRA_QUESTION, "Which Priya should I message: Sharma or Nair?"))
            "setup" -> startActivity(Intent(this, SetupActivity::class.java))
            else -> shell.reset(name)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("dark")) dark = intent.getBooleanExtra("dark", dark)
        applyBars()
        open(intent.getStringExtra("screen") ?: "home")
    }

    @Deprecated("Preview only")
    override fun onBackPressed() {
        if (!shell.pop()) super.onBackPressed()
    }

    private fun themed(): Context {
        val cfg = Configuration(resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return createConfigurationContext(cfg).also { it.setTheme(R.style.Theme_PocketAgent) }
    }

    private fun applyBars() {
        window.insetsController?.setSystemBarsAppearance(
            if (dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )
        host.setBackgroundColor(Kit.color(themed(), R.color.pa_bg))
    }

    private fun build(name: String, nav: Nav): View {
        val c = themed()
        return when (name) {
            "welcome" -> Screens.welcome(c, nav)
            "notice" -> Screens.notice(c, nav)
            "fix" -> Screens.home(c, Samples.fix, nav)
            "activity" -> Screens.activity(c, nav)
            "empty" -> Screens.activity(c, nav, empty = true)
            "detail" -> Screens.activityDetail(c, nav)
            "settings" -> Screens.settings(c, Samples.settings, nav)
            else -> Screens.home(c, Samples.home, nav)
        }
    }

    private fun menuButton(): View {
        val c = themed()
        val b = Kit.text(c, "...", 20f, 700, R.color.pa_ink).apply {
            gravity = Gravity.CENTER
            background = Kit.rounded(Kit.color(c, R.color.pa_fill), Kit.dpf(c, 18))
            layoutParams = FrameLayout.LayoutParams(Kit.dp(c, 36), Kit.dp(c, 36), Gravity.BOTTOM or Gravity.START)
                .apply { marginStart = Kit.dp(c, 12); bottomMargin = Kit.dp(c, 96) }
        }
        b.setOnClickListener {
            PopupMenu(this, b).apply {
                listOf("welcome", "notice", "home", "fix", "activity", "empty", "detail", "settings").forEach { menu.add(it) }
                menu.add("task sheet"); menu.add("answer sheet"); menu.add("setup")
                menu.add(if (dark) "Light mode" else "Dark mode")
                setOnMenuItemClickListener {
                    val t = it.title.toString()
                    if (t == "task sheet") {
                        startActivity(Intent(this@PreviewActivity, TaskInputActivity::class.java))
                    } else if (t == "answer sheet") {
                        startActivity(Intent(this@PreviewActivity, TaskInputActivity::class.java).putExtra(TaskInputActivity.EXTRA_QUESTION, "Which Priya should I message: Sharma or Nair?"))
                    } else if (t == "setup") {
                        startActivity(Intent(this@PreviewActivity, SetupActivity::class.java))
                    } else if (t.endsWith("mode")) {
                        dark = !dark
                        applyBars()
                        shell.reset(shell.current ?: "home")
                    } else {
                        shell.reset(t, Shell.Transition.FADE)
                    }
                    true
                }
            }.show()
        }
        return b
    }
}
