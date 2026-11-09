package dev.pocketagent.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit
import dev.pocketagent.ui.kit.Screens

/**
 * Debug-only gallery. Shows each screen with sample data in light or dark so the design can be checked on a
 * real phone. Start one directly: `adb shell am start -n dev.pocketagent/.ui.PreviewActivity --es screen home --ez dark true`.
 */
class PreviewActivity : Activity() {
    private val screens = linkedMapOf<String, (Context) -> View>(
        "welcome" to Screens::welcome,
        "notice" to Screens::notice,
        "home" to { c -> Screens.home(c) },
        "fix" to { c -> Screens.home(c, needsAttention = true) },
        "activity" to Screens::activity,
        "detail" to Screens::activityDetail,
        "settings" to Screens::settings,
    )
    private var current = "home"
    private var dark = false
    private lateinit var host: FrameLayout
    private var topInset = 0
    private var bottomInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = intent.getStringExtra("screen")?.takeIf { it in screens } ?: current
        dark = intent.getBooleanExtra("dark", false)
        window.setDecorFitsSystemWindows(false)
        host = FrameLayout(this)
        host.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = bars.top
            bottomInset = bars.bottom
            render()
            insets
        }
        setContentView(host)
        render()
    }

    private fun themed(): Context {
        val cfg = Configuration(resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return createConfigurationContext(cfg).also { it.setTheme(R.style.Theme_PocketAgent) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("screen")?.takeIf { it in screens }?.let { current = it }
        if (intent.hasExtra("dark")) dark = intent.getBooleanExtra("dark", dark)
        render()
    }

    private fun render() {
        val c = themed()
        host.removeAllViews()
        host.setBackgroundColor(c.getColor(R.color.pa_bg))
        val body = screens.getValue(current)(c)
        host.addView(body, FrameLayout.LayoutParams(-1, -1).apply { topMargin = topInset + Kit.dp(c, 52); bottomMargin = bottomInset })
        window.insetsController?.setSystemBarsAppearance(
            if (dark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )

        // floating switcher: never part of the design, only a way to move around
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Kit.dp(c, 8), Kit.dp(c, 6), Kit.dp(c, 8), Kit.dp(c, 6))
            background = Kit.rounded(0xE6101010.toInt(), Kit.dpf(c, 22))
        }
        val names = screens.keys.toList() + (if (dark) "light" else "dark")
        names.forEach { n ->
            bar.addView(
                Kit.text(c, n, 13f, if (n == current) 700 else 500, R.color.pa_on_navy).apply {
                    alpha = if (n == current) 1f else .6f
                    setPadding(Kit.dp(c, 9), Kit.dp(c, 8), Kit.dp(c, 9), Kit.dp(c, 8))
                    setOnClickListener {
                        if (n == "dark" || n == "light") dark = !dark else current = n
                        render()
                    }
                },
            )
        }
        host.addView(
            android.widget.HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(bar) },
            FrameLayout.LayoutParams(-2, Kit.dp(c, 44), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = topInset + Kit.dp(c, 4) },
        )
    }
}
