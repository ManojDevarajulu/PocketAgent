package dev.pocketagent.ui

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit
import dev.pocketagent.ui.kit.Kit.color
import dev.pocketagent.ui.kit.Kit.dp
import dev.pocketagent.ui.kit.Kit.dpf

/** What the task screen needs from the session. The session sets [current] while the service is on. */
interface TaskScreenHost {
    fun startTask(text: String)
    fun dropSavedScreen()
    fun completeAnswer(answer: String?)
    fun startSetupTask(text: String)

    companion object {
        @Volatile
        var current: TaskScreenHost? = null
    }
}

/**
 * The task sheet: it rises from the bottom over whatever app you were in, frosted where the phone allows blur,
 * with a rounded composer above the keyboard. With [EXTRA_QUESTION] it answers a question from the agent instead.
 */
class TaskInputActivity : Activity() {
    private var question: String? = null
    private var handled = false
    private var closing = false
    private var voice: VoiceInput? = null
    private lateinit var sheet: LinearLayout
    private lateinit var scrim: View
    private lateinit var composer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        question = intent.getStringExtra(EXTRA_QUESTION)
        if (question != null) answerInstance = this
        window.setDecorFitsSystemWindows(false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        // Real blur of the app behind, where the device allows it; otherwise a plain, slightly stronger scrim.
        val blur = windowManager.isCrossWindowBlurEnabled
        if (blur) window.setBackgroundBlurRadius(Kit.dp(this, 28))

        val root = FrameLayout(this)
        scrim = View(this).apply {
            setBackgroundColor(color(this@TaskInputActivity, R.color.pa_scrim))
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        sheet = Kit.vstack(this).apply {
            val r = dpf(this@TaskInputActivity, 28)
            background = GradientDrawable().apply {
                setColor(color(this@TaskInputActivity, if (blur) R.color.pa_glass else R.color.pa_card))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                if (blur) setStroke(dp(this@TaskInputActivity, 1), color(this@TaskInputActivity, R.color.pa_rim))
            }
            elevation = dpf(this@TaskInputActivity, 12)
            isClickable = true
        }
        buildSheet()
        root.addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            sheet.setPadding(dp(this, 16), dp(this, 10), dp(this, 16), dp(this, 16) + maxOf(bars.bottom, ime.bottom))
            insets
        }
        setContentView(root)

        // enter: the sheet rises, the scrim fades in
        root.post {
            sheet.translationY = sheet.height.toFloat().coerceAtLeast(dpf(this, 300))
            sheet.animate().translationY(0f).setDuration(420).setInterpolator(Kit.ease).start()
            scrim.animate().alpha(if (blur) 0.6f else 1f).setDuration(320).start()
        }
    }

    private fun buildSheet() {
        val c = this
        val q = question
        sheet.addView(
            View(c).apply { background = Kit.rounded(color(c, R.color.pa_line), dpf(c, 3)) },
            LinearLayout.LayoutParams(dp(c, 36), dp(c, 5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(c, 14) },
        )
        sheet.addView(
            Kit.text(c, q ?: "What should I do?", 20f, 600).apply { letterSpacing = -0.01f; setPadding(dp(c, 4), 0, dp(c, 4), 0) },
            Kit.lp(),
        )

        val input = Kit.input(c, if (q != null) "Your answer" else "Ask me anything on this screen", 17f).apply {
            minLines = 1
            maxLines = 5
            minimumHeight = dp(c, 44)
            setPadding(0, dp(c, 11), 0, dp(c, 11))
        }

        if (q == null) {
            val chips = Kit.hstack(c, 8).apply { setPadding(dp(c, 4), dp(c, 12), dp(c, 4), 0) }
            listOf("Take me to an address", "Read this screen", "Reply to this").forEach { label ->
                chips.addView(Kit.chip(c, label).apply {
                    setOnClickListener { input.setText(label); input.setSelection(input.text.length) }
                })
            }
            sheet.addView(Kit.hscroll(c, chips), Kit.lp())
        }

        composer = Kit.hstack(c, 6).apply {
            background = Kit.rounded(color(c, R.color.pa_glass_field), dpf(c, 26), color(c, R.color.pa_rim), dp(c, 1))
            setPadding(dp(c, 18), dp(c, 4), dp(c, 4), dp(c, 4))
            minimumHeight = dp(c, 52)
        }
        composer.addView(input, Kit.weight())

        val mic = Kit.icon(c, R.drawable.ic_mic, 22, R.color.pa_ink).apply {
            background = Kit.rounded(color(c, R.color.pa_fill), dpf(c, 22))
            setPadding(dp(c, 11), dp(c, 11), dp(c, 11), dp(c, 11))
            layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
        }
        val send = Kit.icon(c, R.drawable.ic_send, 20, R.color.pa_muted).apply {
            background = Kit.rounded(color(c, R.color.pa_line), dpf(c, 22))
            setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
        }

        // voice: tap to listen, the words appear in the box, nothing starts until the send button
        if (VoiceInput.isAvailable(c)) {
            val vi = VoiceInput(
                context = c,
                onPartial = { input.setText(it.take(MAX_TEXT)); input.setSelection(input.text.length) },
                onFinal = { input.setText(it.take(MAX_TEXT)); input.setSelection(input.text.length) },
                onError = { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() },
                onListening = { on ->
                    (mic.background as GradientDrawable).setColor(color(c, if (on) R.color.pa_accent else R.color.pa_fill))
                    mic.setColorFilter(color(c, if (on) R.color.pa_on_accent else R.color.pa_ink))
                    input.hint = if (on) "Listening…" else (if (q != null) "Your answer" else "Ask me anything on this screen")
                },
            )
            voice = vi
            Kit.pressable(mic, 0.9f) {
                // The permission is asked for in setup only; this screen closes when a system dialog appears.
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(c, R.string.mic_needs_setup, Toast.LENGTH_LONG).show()
                } else {
                    vi.toggle()
                }
            }
            composer.addView(mic)
        }
        composer.addView(send)

        // the send button wakes (grey to orange, with a pop) once there is something to send
        var armed = false
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val has = !s.isNullOrBlank()
                if (has == armed) return
                armed = has
                val from = color(c, if (has) R.color.pa_line else R.color.pa_accent)
                val to = color(c, if (has) R.color.pa_accent else R.color.pa_line)
                ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
                    duration = 220
                    addUpdateListener { (send.background as GradientDrawable).setColor(it.animatedValue as Int) }
                }.start()
                send.setColorFilter(color(c, if (has) R.color.pa_on_accent else R.color.pa_muted))
                if (has) send.animate().scaleX(1.14f).scaleY(1.14f).setDuration(110).withEndAction {
                    send.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
                }.start()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, a: Int, b: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, a: Int) {}
        })
        Kit.pressable(send, 0.9f) { go(input.text.toString().trim()) }
        sheet.addView(composer, Kit.lp().apply { topMargin = dp(c, 14) })

        if (q != null) {
            sheet.addView(
                Kit.text(c, "Skip", 16f, 500, R.color.pa_muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(c, 14), 0, dp(c, 6))
                    Kit.pressable(this, 1f) { dismiss() }
                },
                Kit.lp(),
            )
        }
        input.requestFocus()
    }

    /** Sends the text, or gives a small shake when there is nothing to send. */
    private fun go(text: String) {
        if (text.isEmpty()) {
            composer.animate().translationX(dpf(this, 8)).setDuration(60).withEndAction {
                composer.animate().translationX(-dpf(this, 7)).setDuration(90).withEndAction {
                    composer.animate().translationX(0f).setDuration(140).setInterpolator(OvershootInterpolator(3f)).start()
                }.start()
            }.start()
            return
        }
        handled = true
        if (question != null) TaskScreenHost.current?.completeAnswer(text) else TaskScreenHost.current?.startTask(text)
        closeSheet()
    }

    /** Drops the sheet down and away, then finishes. */
    private fun closeSheet() {
        if (closing) return
        closing = true
        voice?.destroy()
        scrim.animate().alpha(0f).setDuration(220).start()
        sheet.animate().translationY(sheet.height.toFloat() + dpf(this, 40)).setDuration(260).setInterpolator(Kit.ease)
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    @Deprecated("Back is treated as a dismissal.")
    override fun onBackPressed() {
        dismiss()
    }

    override fun onStop() {
        voice?.destroy()
        // Home or an app switch only stops this task-less screen and the user cannot return to it, so it counts as a dismissal.
        if (!isChangingConfigurations) dismiss(animate = false)
        super.onStop()
    }

    override fun onDestroy() {
        voice?.destroy()
        // An outside tap or a system close ends up here without going through a button.
        if (isFinishing) dismiss(animate = false, finishNow = false)
        if (answerInstance === this) answerInstance = null
        super.onDestroy()
    }

    private fun dismiss(animate: Boolean = true, finishNow: Boolean = true) {
        if (!handled) {
            handled = true
            if (question != null) {
                TaskScreenHost.current?.completeAnswer(null)
            } else {
                TaskScreenHost.current?.dropSavedScreen()
            }
        }
        if (finishNow) {
            if (animate && ::sheet.isInitialized) closeSheet() else finish()
        }
    }

    companion object {
        const val EXTRA_QUESTION = "question"
        const val MAX_TEXT = 2_000

        @Volatile
        private var answerInstance: TaskInputActivity? = null

        /** Closes the answer screen without reporting a dismissal (the session already has its answer or gave up). */
        fun closeAnswerScreen() {
            answerInstance?.let {
                it.handled = true
                it.closeSheet()
            }
        }
    }
}
