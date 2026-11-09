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

/** The end-of-task card. It closes on tap or after 15 seconds; the 15 seconds only run while the phone is unlocked. */
class SummaryCard(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var card: LinearLayout? = null
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val autoClose = object : Runnable {
        override fun run() {
            if (keyguard?.isKeyguardLocked == true) handler.postDelayed(this, 15_000L) else close()
        }
    }

    fun show(title: String, body: String) {
        close()
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
        if (Overlays.add(context, root, params)) {
            card = root
            handler.postDelayed(autoClose, 15_000L)
        }
    }

    fun close() {
        handler.removeCallbacks(autoClose)
        card?.let { Overlays.remove(context, it) }
        card = null
    }
}
