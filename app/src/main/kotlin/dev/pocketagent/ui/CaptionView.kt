package dev.pocketagent.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit

/** A small pill near the top of the screen saying what the agent is doing. It never takes touches. */
class CaptionView(private val context: Context) {
    private val label: TextView = Kit.text(context, "", 14f, 500, R.color.pa_ink)
    private val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val h = Overlays.dp(context, 14)
        val v = Overlays.dp(context, 9)
        setPadding(h, v, h, v)
        background = Kit.rounded(
            Kit.color(context, R.color.pa_card), Overlays.dp(context, 18).toFloat(),
            Kit.color(context, R.color.pa_line), Overlays.dp(context, 1),
        )
        elevation = Overlays.dp(context, 6).toFloat()
        addView(
            View(context).apply { background = Kit.rounded(Kit.color(context, R.color.pa_accent), Overlays.dp(context, 4).toFloat()) },
            LinearLayout.LayoutParams(Overlays.dp(context, 8), Overlays.dp(context, 8)).apply { marginEnd = Overlays.dp(context, 9) },
        )
        addView(label)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val params = Overlays.params(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
    ).also { it.y = Overlays.dp(context, 72) }
    private var shown = false
    private var text: String? = null
    private var visible = true

    fun setText(text: String?) {
        this.text = text?.takeIf { it.isNotEmpty() }
        refresh()
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        refresh()
    }

    fun close() {
        if (shown) Overlays.remove(context, view)
        shown = false
    }

    private fun refresh() {
        val t = text
        if (t == null) {
            close()
            return
        }
        val changed = label.text.toString() != t
        label.text = t
        // Hiding keeps the window and only makes it invisible; removing it would emit window events that delay the next settle.
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (shown) {
            Overlays.update(context, view, params)
            if (changed) {
                // a new line of text fades up into place
                label.alpha = 0f
                label.translationY = Overlays.dp(context, 4).toFloat()
                label.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(Kit.ease).start()
            }
        } else {
            shown = Overlays.add(context, view, params)
            view.alpha = 0f
            view.translationY = -Overlays.dp(context, 12).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(Kit.ease).start()
        }
    }
}
