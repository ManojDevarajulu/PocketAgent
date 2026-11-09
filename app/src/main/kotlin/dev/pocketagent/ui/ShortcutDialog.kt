package dev.pocketagent.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit
import dev.pocketagent.ui.kit.Kit.color
import dev.pocketagent.ui.kit.Kit.dp
import dev.pocketagent.ui.kit.Kit.dpf

/** A small sheet for naming a new shortcut: one text field and Save. The text is what the agent is asked to do. */
object ShortcutDialog {
    fun show(activity: Activity, onSave: (String) -> Unit) {
        val c = activity
        val dialog = Dialog(c)
        val card = Kit.vstack(c).apply {
            background = Kit.rounded(color(c, R.color.pa_card), dpf(c, 28))
            setPadding(dp(c, 20), dp(c, 22), dp(c, 20), dp(c, 18))
        }
        card.addView(Kit.text(c, "New shortcut", 20f, 600), Kit.lp())
        card.addView(
            Kit.text(c, "Write what you want done. One tap on Home will run it.", 14f, 400, R.color.pa_muted).apply {
                setPadding(0, dp(c, 4), 0, 0)
            },
            Kit.lp(),
        )
        val field = Kit.field(c, "For example: Turn on do not disturb").apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(80))
        }
        card.addView(field, Kit.lp().apply { topMargin = dp(c, 16) })
        val save = Kit.primary(c, "Save shortcut") {
            val t = field.text.toString().trim()
            if (t.isNotEmpty()) {
                onSave(t)
                dialog.dismiss()
            }
        }
        card.addView(save, Kit.lp().apply { topMargin = dp(c, 16) })
        card.addView(
            Kit.text(c, "Cancel", 16f, 500, R.color.pa_muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(c, 14), 0, dp(c, 2))
                Kit.pressable(this, 1f) { dialog.dismiss() }
            },
            Kit.lp(),
        )
        val outer = FrameLayout(c).apply { setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12)) }
        outer.addView(card, FrameLayout.LayoutParams(-1, -2))
        dialog.setContentView(outer)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setDimAmount(0.45f)
        }
        dialog.show()
        card.alpha = 0f
        card.translationY = dpf(c, 60)
        card.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(Kit.ease).start()
        field.requestFocus()
    }
}

/** Small confirm and info sheets in the app's style, for destructive actions and short explanations. */
object SheetDialogs {
    fun confirm(activity: Activity, title: String, message: String, action: String, onConfirm: () -> Unit) =
        show(activity, title, message, action, onConfirm)

    fun info(activity: Activity, title: String, message: String) = show(activity, title, message, null, null)

    private fun show(activity: Activity, title: String, message: String, action: String?, onConfirm: (() -> Unit)?) {
        val c = activity
        val dialog = Dialog(c)
        val card = Kit.vstack(c).apply {
            background = Kit.rounded(color(c, R.color.pa_card), dpf(c, 28))
            setPadding(dp(c, 22), dp(c, 24), dp(c, 22), dp(c, 18))
        }
        card.addView(Kit.text(c, title, 20f, 600), Kit.lp())
        card.addView(
            Kit.text(c, message, 15f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 8), 0, 0); setLineSpacing(dpf(c, 3), 1f) },
            Kit.lp(),
        )
        if (action != null && onConfirm != null) {
            val row = Kit.hstack(c, 12)
            row.addView(Kit.secondary(c, "Cancel") { dialog.dismiss() }, Kit.weight())
            row.addView(
                Kit.secondary(c, action) { dialog.dismiss(); onConfirm() }.apply { setTextColor(color(c, R.color.pa_bad)) },
                Kit.weight(),
            )
            card.addView(row, Kit.lp().apply { topMargin = dp(c, 20) })
        } else {
            card.addView(Kit.primary(c, "Got it") { dialog.dismiss() }, Kit.lp().apply { topMargin = dp(c, 20) })
        }
        val outer = FrameLayout(c).apply { setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12)) }
        outer.addView(card, FrameLayout.LayoutParams(-1, -2))
        dialog.setContentView(outer)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.45f)
        }
        dialog.show()
        card.alpha = 0f
        card.translationY = dpf(c, 60)
        card.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(Kit.ease).start()
    }
}
