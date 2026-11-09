package dev.pocketagent.ui

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R

/**
 * The end-of-task card. It closes on tap or 15 seconds after it appears on screen.
 * The window is a TYPE_ACCESSIBILITY_OVERLAY, which would draw over the lock screen and cover the fingerprint
 * scanner, so while the keyguard is locked the window is removed; the card is shown again, with a fresh
 * 15 seconds, once the phone is unlocked. Main thread only.
 */
class SummaryCard(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private var card: LinearLayout? = null
    private var title = ""
    private var body = ""
    private var pending = false
    private val autoClose = Runnable { close() }
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
        val pad = Overlays.dp(context, 20)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.card_background))
                cornerRadius = Overlays.dp(context, 20).toFloat()
            }
            setOnClickListener { close() }
        }
        root.addView(TextView(context).apply {
            text = title
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.card_text))
        })
        root.addView(TextView(context).apply {
            text = body
            textSize = 15f
            setTextColor(context.getColor(R.color.card_muted))
            setPadding(0, Overlays.dp(context, 8), 0, 0)
        })
        val margin = Overlays.dp(context, 16)
        val params = Overlays.params(
            context.resources.displayMetrics.widthPixels - 2 * margin,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        ).also { it.y = margin }
        if (!Overlays.add(context, root, params)) return false
        card = root
        return true
    }

    private companion object {
        const val POLL_MS = 500L
    }
}
