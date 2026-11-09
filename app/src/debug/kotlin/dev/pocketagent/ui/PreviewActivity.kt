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
import dev.pocketagent.ui.kit.Screens
import dev.pocketagent.ui.kit.Skin

/**
 * Debug-only gallery with the same navigation the app will have: tabs cross-fade, pushed screens slide in
 * over the old one with a parallax, back slides out. The small button at the top right jumps between screens.
 * Start one directly: `adb shell am start -n dev.pocketagent/.ui.PreviewActivity --es screen home --ez dark true`.
 */
class PreviewActivity : Activity() {
    private val tabs = listOf("home", "activity", "settings")
    private var dark = false
    private var topInset = 0
    private var bottomInset = 0
    private lateinit var host: FrameLayout
    private lateinit var content: FrameLayout
    private val stack = ArrayList<Pair<String, View>>()

    private val nav: Nav by lazy {
        Nav(tab = { show(tabs[it], Transition.FADE, replace = true) }, push = { show(it, Transition.PUSH) }, back = { pop() })
    }

    private enum class Transition { NONE, FADE, PUSH }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = intent.getBooleanExtra("dark", false)
        Skin.ink = intent.getStringExtra("skin") == "ink"
        window.setDecorFitsSystemWindows(false)
        host = FrameLayout(this)
        content = FrameLayout(this)
        host.addView(content, FrameLayout.LayoutParams(-1, -1))
        host.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = bars.top
            bottomInset = bars.bottom
            (content.layoutParams as FrameLayout.LayoutParams).apply { topMargin = topInset; bottomMargin = bottomInset }
            content.requestLayout()
            insets
        }
        setContentView(host)
        applyBars()
        host.addView(menuButton())
        show(intent.getStringExtra("screen") ?: "home", Transition.NONE, replace = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("dark")) dark = intent.getBooleanExtra("dark", dark)
        if (intent.hasExtra("skin")) Skin.ink = intent.getStringExtra("skin") == "ink"
        applyBars()
        stack.clear(); content.removeAllViews()
        show(intent.getStringExtra("screen") ?: "home", Transition.NONE, replace = true)
    }

    @Deprecated("Preview only")
    override fun onBackPressed() {
        if (stack.size > 1) pop() else super.onBackPressed()
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

    private fun build(name: String): View {
        val c = themed()
        return when (name) {
            "welcome" -> Screens.welcome(c, nav)
            "notice" -> Screens.notice(c, nav)
            "fix" -> Screens.home(c, needsAttention = true, nav = nav)
            "activity" -> Screens.activity(c, nav)
            "detail" -> Screens.activityDetail(c, nav)
            "settings" -> Screens.settings(c, nav)
            else -> Screens.home(c, nav = nav)
        }
    }

    private fun show(name: String, kind: Transition, replace: Boolean = false) {
        val view = build(name)
        val old = stack.lastOrNull()?.second
        if (replace && stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(name to view)
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
        val w = content.width.toFloat().coerceAtLeast(1f)
        when (kind) {
            Transition.NONE -> old?.let { content.removeView(it) }
            Transition.FADE -> {
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(220).setInterpolator(Kit.ease).withEndAction {
                    old?.let { content.removeView(it) }
                }.start()
            }
            Transition.PUSH -> {
                view.elevation = Kit.dpf(this, 24)
                view.translationX = w
                view.animate().translationX(0f).setDuration(420).setInterpolator(Kit.ease).start()
                old?.animate()?.translationX(-w * 0.28f)?.setDuration(420)?.setInterpolator(Kit.ease)?.withEndAction {
                    old.visibility = View.INVISIBLE
                }?.start()
            }
        }
    }

    private fun pop() {
        if (stack.size < 2) return
        val top = stack.removeAt(stack.lastIndex).second
        val below = stack.last().second
        val w = content.width.toFloat()
        below.visibility = View.VISIBLE
        below.animate().translationX(0f).setDuration(380).setInterpolator(Kit.ease).start()
        top.animate().translationX(w).setDuration(380).setInterpolator(Kit.ease).withEndAction {
            content.removeView(top)
        }.start()
    }

    private fun menuButton(): View {
        val c = themed()
        val b = Kit.text(c, "···", 20f, 700, R.color.pa_ink).apply {
            gravity = Gravity.CENTER
            background = Kit.rounded(Kit.color(c, R.color.pa_fill), Kit.dpf(c, 18))
            layoutParams = FrameLayout.LayoutParams(Kit.dp(c, 36), Kit.dp(c, 36), Gravity.TOP or Gravity.END)
                .apply { topMargin = 0; marginEnd = Kit.dp(c, 12) }
            translationY = Kit.dpf(c, 6)
        }
        host.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = bars.top; bottomInset = bars.bottom
            (content.layoutParams as FrameLayout.LayoutParams).apply { topMargin = topInset; bottomMargin = bottomInset }
            (b.layoutParams as FrameLayout.LayoutParams).topMargin = topInset + Kit.dp(c, 2)
            content.requestLayout()
            insets
        }
        b.setOnClickListener {
            PopupMenu(this, b).apply {
                listOf("welcome", "notice", "home", "fix", "activity", "detail", "settings").forEach { menu.add(it) }
                menu.add(if (dark) "Light mode" else "Dark mode")
                menu.add(if (Skin.ink) "Skin: tonal blue" else "Skin: ink")
                setOnMenuItemClickListener {
                    val t = it.title.toString()
                    if (t.startsWith("Skin")) {
                        Skin.ink = !Skin.ink
                        val cur = stack.lastOrNull()?.first ?: "home"
                        applyBars(); stack.clear(); content.removeAllViews()
                        show(cur, Transition.NONE, replace = true)
                    } else if (t.endsWith("mode")) {
                        dark = !dark
                        val cur = stack.lastOrNull()?.first ?: "home"
                        applyBars(); stack.clear(); content.removeAllViews()
                        show(cur, Transition.NONE, replace = true)
                    } else {
                        stack.clear(); content.removeAllViews()
                        show(t, Transition.FADE, replace = true)
                    }
                    true
                }
            }.show()
        }
        return b
    }
}
