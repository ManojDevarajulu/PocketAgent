package dev.pocketagent.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import dev.pocketagent.R

/** A small caption near the top of the screen that never takes touches. */
class CaptionView(private val context: Context) {
    private val view = TextView(context).apply {
        setTextColor(context.getColor(R.color.caption_text))
        textSize = 15f
        gravity = Gravity.CENTER
        val pad = Overlays.dp(context, 12)
        setPadding(pad, pad / 2, pad, pad / 2)
        background = GradientDrawable().apply {
            setColor(context.getColor(R.color.caption_background))
            cornerRadius = Overlays.dp(context, 16).toFloat()
        }
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
        view.text = t
        // Hiding keeps the window and only makes it invisible; removing it would emit window events that delay the next settle.
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (shown) Overlays.update(context, view, params) else shown = Overlays.add(context, view, params)
    }
}
