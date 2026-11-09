package dev.pocketagent.ui

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
 * The task sheet. It rises from the bottom over whatever app you were in, frosted where the phone allows blur, with a
 * rounded composer above the keyboard. Content settles in one piece at a time, suggestions collapse once you type, and
 * the sheet follows your finger when you drag it down. With [EXTRA_QUESTION] it answers a question from the agent.
 */
class TaskInputActivity : Activity() {
    private var question: String? = null
    private var handled = false
    private var closing = false
    private var voice: VoiceInput? = null
    private lateinit var sheet: LinearLayout
    private lateinit var scrim: View
    private lateinit var composer: LinearLayout
    private var scrimPeak = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        question = intent.getStringExtra(EXTRA_QUESTION)
        if (question != null) answerInstance = this
        window.setDecorFitsSystemWindows(false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        // Real blur of the app behind, where the device allows it; otherwise a plain, slightly stronger scrim.
        val blur = windowManager.isCrossWindowBlurEnabled
        if (blur) window.setBackgroundBlurRadius(dp(this, 28))
        scrimPeak = if (blur) 0.6f else 1f

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
            // Things that appear or disappear (the suggestions) fade and slide instead of snapping.
            layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
                setDuration(240)
            }
        }
        val blocks = buildSheet()
        root.addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            sheet.setPadding(dp(this, 16), dp(this, 10), dp(this, 16), dp(this, 16) + maxOf(bars.bottom, ime.bottom))
            insets
        }
        setContentView(root)

        // enter: the sheet rises, the scrim fades in, then the content settles one block at a time
        root.post {
            sheet.translationY = sheet.height.toFloat().coerceAtLeast(dpf(this, 300))
            sheet.animate().translationY(0f).setDuration(460).setInterpolator(Kit.ease).start()
            scrim.animate().alpha(scrimPeak).setDuration(320).start()
            Kit.reveal(blocks, 60)
        }
    }

    /** Builds the sheet and returns the blocks that should fade up in turn. */
    private fun buildSheet(): List<View> {
        val c = this
        val q = question
        val blocks = ArrayList<View>()

        // The grabber and header double as the drag handle.
        val header = Kit.vstack(c)
        header.addView(
            View(c).apply { background = Kit.rounded(color(c, R.color.pa_line), dpf(c, 3)) },
            LinearLayout.LayoutParams(dp(c, 36), dp(c, 5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(c, 14) },
        )
        if (q == null) {
            val titleRow = Kit.hstack(c, 10).apply { setPadding(dp(c, 4), 0, dp(c, 4), 0) }
            titleRow.addView(
                ImageView(c).apply {
                    setImageResource(R.drawable.logo_mark)
                    layoutParams = LinearLayout.LayoutParams(dp(c, 28), dp(c, 28))
                },
            )
            val titles = Kit.vstack(c)
            titles.addView(Kit.text(c, "What should I do?", 20f, 600).apply { letterSpacing = -0.01f })
            titles.addView(Kit.text(c, "I'll look at what's on your screen.", 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 2), 0, 0) })
            titleRow.addView(titles, Kit.weight())
            header.addView(titleRow, Kit.lp())
        } else {
            // The agent's question appears as a message from the agent.
            val msg = Kit.hstack(c, 10).apply { gravity = Gravity.TOP; setPadding(dp(c, 4), 0, dp(c, 4), 0) }
            msg.addView(
                ImageView(c).apply {
                    setImageResource(R.drawable.logo_mark)
                    layoutParams = LinearLayout.LayoutParams(dp(c, 30), dp(c, 30))
                },
            )
            val bubble = Kit.text(c, q, 16f, 400).apply {
                background = Kit.rounded(color(c, R.color.pa_glass_field), dpf(c, 20), color(c, R.color.pa_rim), dp(c, 1))
                setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 12))
                setLineSpacing(dpf(c, 3), 1f)
            }
            msg.addView(bubble, Kit.weight())
            header.addView(msg, Kit.lp())
        }
        attachDragToDismiss(header)
        sheet.addView(header, Kit.lp())
        blocks.add(header)

        val input = Kit.input(c, if (q != null) "Your answer" else "Ask me anything on this screen", 17f).apply {
            minLines = 1
            maxLines = 5
            minimumHeight = dp(c, 44)
            setPadding(0, dp(c, 11), 0, dp(c, 11))
        }

        // Suggestions as quiet rows. They tuck away once there is text.
        var suggestions: View? = null
        if (q == null) {
            val list = Kit.vstack(c).apply { setPadding(0, dp(c, 10), 0, 0) }
            listOf(
                Triple(R.drawable.ic_pin, "Take me to an address", "From a message or a map link"),
                Triple(R.drawable.ic_doc, "Read this screen out", "A short summary of what is here"),
                Triple(R.drawable.ic_chat, "Reply to this", "Drafts it, asks before sending"),
            ).forEach { (icon, title, sub) ->
                val row = Kit.hstack(c, 12).apply { minimumHeight = dp(c, 56); setPadding(dp(c, 4), 0, dp(c, 4), 0) }
                row.addView(Kit.tile(c, icon, color(c, R.color.pa_tile), color(c, R.color.pa_tile_icon), 32).apply {
                    (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
                })
                val col = Kit.vstack(c)
                col.addView(Kit.text(c, title, 16f, 500))
                col.addView(Kit.text(c, sub, 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 1), 0, 0) })
                row.addView(col, Kit.weight())
                row.addView(Kit.icon(c, R.drawable.ic_chevron, 14, R.color.pa_muted).apply { alpha = .6f })
                Kit.pressable(row, 1f) {
                    row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    input.setText(title)
                    input.setSelection(input.text.length)
                }
                list.addView(row, Kit.lp())
            }
            suggestions = list
            sheet.addView(list, Kit.lp())
            blocks.add(list)
        }

        composer = Kit.hstack(c, 6).apply {
            val idle = Kit.rounded(color(c, R.color.pa_glass_field), dpf(c, 26), color(c, R.color.pa_rim), dp(c, 1))
            background = idle
            setPadding(dp(c, 18), dp(c, 4), dp(c, 4), dp(c, 4))
            minimumHeight = dp(c, 52)
        }
        composer.addView(input, Kit.weight())
        // a ring that fades in while you are typing
        input.setOnFocusChangeListener { _, focused ->
            composer.background = if (focused) {
                Kit.rounded(color(c, R.color.pa_glass_field), dpf(c, 26), color(c, R.color.pa_blue), dp(c, 2))
            } else {
                Kit.rounded(color(c, R.color.pa_glass_field), dpf(c, 26), color(c, R.color.pa_rim), dp(c, 1))
            }
        }

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
            val dots = Kit.listeningDots(c).apply { visibility = View.GONE }
            composer.addView(dots, composer.indexOfChild(input) + 1)
            val vi = VoiceInput(
                context = c,
                onPartial = { input.setText(it.take(MAX_TEXT)); input.setSelection(input.text.length) },
                onFinal = { input.setText(it.take(MAX_TEXT)); input.setSelection(input.text.length) },
                onError = { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() },
                onListening = { on ->
                    (mic.background as GradientDrawable).setColor(color(c, if (on) R.color.pa_accent else R.color.pa_fill))
                    mic.setColorFilter(color(c, if (on) R.color.pa_on_accent else R.color.pa_ink))
                    dots.visibility = if (on) View.VISIBLE else View.GONE
                    input.hint = if (on) "Listening" else (if (q != null) "Your answer" else "Ask me anything on this screen")
                    if (on) mic.animate().scaleX(1.12f).scaleY(1.12f).setDuration(160).start() else mic.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                },
            )
            voice = vi
            Kit.pressable(mic, 0.9f) {
                mic.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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

        // The send button wakes (grey to orange, with a pop) once there is something to send; suggestions tuck away.
        var armed = false
        val counter = Kit.text(c, "", 12f, 400, R.color.pa_muted).apply { gravity = Gravity.END; visibility = View.GONE }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val has = !s.isNullOrBlank()
                val n = s?.length ?: 0
                counter.visibility = if (n > MAX_TEXT - 300) View.VISIBLE else View.GONE
                counter.text = "$n / $MAX_TEXT"
                suggestions?.visibility = if (n == 0) View.VISIBLE else View.GONE
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
        blocks.add(composer)
        sheet.addView(counter, Kit.lp().apply { topMargin = dp(c, 6); marginEnd = dp(c, 10) })

        if (q != null) {
            val skip = Kit.text(c, "Skip", 16f, 500, R.color.pa_muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(c, 14), 0, dp(c, 6))
                Kit.pressable(this, 1f) { dismiss() }
            }
            sheet.addView(skip, Kit.lp())
            blocks.add(skip)
        }
        input.requestFocus()
        return blocks
    }

    /** Dragging the grabber or header pulls the sheet down with your finger; past a point it closes, otherwise it springs back. */
    private fun attachDragToDismiss(handle: View) {
        var startY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - startY).coerceAtLeast(0f)
                    // a little resistance, like pulling a real sheet
                    sheet.translationY = dy * 0.9f
                    scrim.alpha = scrimPeak * (1f - (dy / (sheet.height.coerceAtLeast(1) * 1.3f)).coerceIn(0f, 1f))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = (e.rawY - startY)
                    if (dy > dpf(this, 110)) {
                        dismiss()
                    } else {
                        sheet.animate().translationY(0f).setDuration(360).setInterpolator(OvershootInterpolator(1.6f)).start()
                        scrim.animate().alpha(scrimPeak).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Sends the text, or gives a small shake when there is nothing to send. */
    private fun go(text: String) {
        if (text.isEmpty()) {
            composer.performHapticFeedback(HapticFeedbackConstants.REJECT)
            composer.animate().translationX(dpf(this, 8)).setDuration(60).withEndAction {
                composer.animate().translationX(-dpf(this, 7)).setDuration(90).withEndAction {
                    composer.animate().translationX(0f).setDuration(140).setInterpolator(OvershootInterpolator(3f)).start()
                }.start()
            }.start()
            return
        }
        composer.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
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
