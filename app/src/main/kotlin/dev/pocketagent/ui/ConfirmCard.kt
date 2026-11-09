package dev.pocketagent.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R
import dev.pocketagent.agent.ConfirmRequest

/** Bottom sheet asking the user to allow or decline one action. */
class ConfirmCard(private val context: Context) {
    private var card: View? = null

    fun show(request: ConfirmRequest, onResult: (Boolean) -> Unit) {
        close()
        val pad = Overlays.dp(context, 20)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.card_background))
                val r = Overlays.dp(context, 20).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        root.addView(line(context.getString(R.string.confirm_title), 20f, true))
        root.addView(line(context.getString(R.string.confirm_action, request.action), 16f, false))
        root.addView(line(context.getString(R.string.confirm_target, request.target), 16f, false))
        root.addView(line(context.getString(R.string.confirm_app, request.app), 16f, false))
        request.detail?.let { root.addView(line(context.getString(R.string.confirm_will_send, it), 16f, false)) }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad, 0, 0)
        }
        val gap = pad / 4
        fun slot() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
            it.marginStart = gap
            it.marginEnd = gap
        }
        buttons.addView(button(R.string.confirm_decline, R.color.button_decline, R.color.card_text, false, onResult), slot())
        buttons.addView(button(R.string.confirm_allow, R.color.button_allow, R.color.button_allow_text, true, onResult), slot())
        root.addView(buttons)

        val params = Overlays.params(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        if (Overlays.add(context, root, params)) card = root else onResult(false)
    }

    fun close() {
        card?.let { Overlays.remove(context, it) }
        card = null
    }

    private fun button(label: Int, background: Int, textColor: Int, result: Boolean, onResult: (Boolean) -> Unit) =
        Button(context).apply {
            setText(label)
            setTextColor(context.getColor(textColor))
            setBackgroundColor(context.getColor(background))
            filterTouchesWhenObscured = true
            // Swallow touches that arrived through another window drawn over this one.
            setOnTouchListener { _, event ->
                (event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0
            }
            setOnClickListener {
                close()
                onResult(result)
            }
        }

    private fun line(value: String, size: Float, bold: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(context.getColor(R.color.card_text))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, Overlays.dp(context, 4), 0, Overlays.dp(context, 4))
    }
}
