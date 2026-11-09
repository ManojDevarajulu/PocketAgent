package dev.pocketagent.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit

/**
 * The end-of-task card. It closes on tap or 15 seconds after it appears on screen.
 * The window is a TYPE_ACCESSIBILITY_OVERLAY, which would draw over the lock screen and cover the fingerprint
 * scanner, so while the keyguard is locked the window is removed; the card is shown again, with a fresh
 * 15 seconds, once the phone is unlocked. Main thread only.
 */
class SummaryCard(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private var card: android.view.View? = null
    private var title = ""
    private var body = ""
    private var pending = false
    private val autoClose = Runnable { dismiss() }
    private val poll = Runnable { sync() }

    fun show(title: String, body: String) {
        close()
        this.title = title
        this.body = body
        pending = true
        sync()
    }

    fun close() {
        handler.removeCallbacks(poll)
        handler.removeCallbacks(autoClose)
        removeWindow()
        pending = false
        title = ""
        body = ""
    }

    /** Puts the window on screen only while unlocked, and keeps watching for lock changes until closed. */
    private fun sync() {
        if (!pending) return
        handler.removeCallbacks(poll)
        val locked = keyguard?.isKeyguardLocked == true
        if (locked) {
            if (card != null) {
                handler.removeCallbacks(autoClose)
                removeWindow()
            }
        } else if (card == null) {
            if (addWindow()) {
                handler.postDelayed(autoClose, 15_000L)
            } else {
                close()
                return
            }
        }
        handler.postDelayed(poll, POLL_MS)
    }

    private fun removeWindow() {
        card?.let { Overlays.remove(context, it) }
        card = null
    }

    private fun addWindow(): Boolean {
        val c = context
        val outer = LinearLayout(c)
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(Overlays.dp(c, 16), Overlays.dp(c, 14), Overlays.dp(c, 16), Overlays.dp(c, 16))
            background = Kit.rounded(Kit.color(c, R.color.pa_card), Overlays.dp(c, 24).toFloat(), Kit.color(c, R.color.pa_line), Overlays.dp(c, 1))
            elevation = Overlays.dp(c, 14).toFloat()
            setOnClickListener { dismiss() }
        }
        root.addView(
            ImageView(c).apply {
                setImageResource(R.drawable.logo_mark)
                layoutParams = LinearLayout.LayoutParams(Overlays.dp(c, 36), Overlays.dp(c, 36)).apply { marginEnd = Overlays.dp(c, 12) }
            },
        )
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(Kit.text(c, "Pocket Agent", 13f, 500, R.color.pa_muted), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Kit.text(c, "now", 12f, 400, R.color.pa_muted))
        col.addView(top, LinearLayout.LayoutParams(-1, -2))
        col.addView(Kit.text(c, title, 17f, 600).apply { setPadding(0, Overlays.dp(c, 3), 0, 0) }, LinearLayout.LayoutParams(-1, -2))
        if (body.isNotBlank()) {
            col.addView(
                Kit.text(c, body, 14f, 400, R.color.pa_muted).apply {
                    setPadding(0, Overlays.dp(c, 4), 0, 0)
                    setLineSpacing(Overlays.dp(c, 2).toFloat(), 1f)
                },
                LinearLayout.LayoutParams(-1, -2),
            )
        }
        root.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val margin = Overlays.dp(c, 16)
        val params = Overlays.params(
            c.resources.displayMetrics.widthPixels - 2 * margin,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        ).also { it.y = margin }
        outer.addView(root, LinearLayout.LayoutParams(-1, -2))
        if (!Overlays.add(c, outer, params)) return false
        card = outer
        // slides up from the bottom and settles
        outer.alpha = 0f
        outer.translationY = Overlays.dp(c, 80).toFloat()
        outer.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(Kit.ease).start()
        return true
    }

    /** Slides the card away, then removes it. */
    private fun dismiss() {
        val v = card ?: return
        handler.removeCallbacks(poll)
        handler.removeCallbacks(autoClose)
        pending = false
        v.animate().alpha(0f).translationY(Overlays.dp(context, 60).toFloat()).setDuration(240).setInterpolator(Kit.ease)
            .withEndAction { close() }.start()
    }

    private companion object {
        const val POLL_MS = 500L
    }
}
