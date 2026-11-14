package dev.pocketagent.ui

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
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
import dev.pocketagent.agent.OnPhone
import dev.pocketagent.agent.OtpQuickAction
import dev.pocketagent.agent.OtpResult
import dev.pocketagent.agent.QuickTasks
import dev.pocketagent.android.AndroidNotifications
import dev.pocketagent.ui.kit.Kit
import dev.pocketagent.ui.kit.Kit.color
import dev.pocketagent.ui.kit.Kit.dp
import dev.pocketagent.ui.kit.Kit.dpf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** What the task screen needs from the session. The session sets [current] while the service is on. */
interface TaskScreenHost {
    /** [spoken] is true when the text came from the mic, so the session may run the task by voice. */
    fun startTask(text: String, spoken: Boolean = false)
    fun dropSavedScreen()
    fun completeAnswer(answer: String?)
    fun startSetupTask(text: String)

    /** Starts recording the user's own taps as a playbook draft; the bubble becomes the stop button. */
    fun startRecording()

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
class TaskInputActivity : ThemedActivity() {
    // Plain Activity, so no lifecycleScope: the Latest OTP lookup runs here and is cancelled in onDestroy.
    private val otpScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var otpLine: LinearLayout? = null

    private var question: String? = null
    private var handled = false
    private var closing = false
    private var voice: VoiceInput? = null
    /** True once the mic filled the box; cleared when the box is emptied. */
    private var spoken = false
    private lateinit var sheet: LinearLayout
    private lateinit var scrim: View
    private lateinit var composer: LinearLayout
    private var scrimPeak = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        question = intent.getStringExtra(EXTRA_QUESTION)
        if (question != null) answerInstance = this
        // Android 13 and later send the back gesture here and never call onBackPressed.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { dismiss() }
        }
        window.setDecorFitsSystemWindows(false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        // Real blur of the app behind, where the device allows it; otherwise a plain, slightly stronger scrim.
        // Cross-window blur arrived in Android 12; calling it on Android 11 crashes, so 11 gets the scrim only.
        val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled
        if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 17 no longer creates the decor view inside setBackgroundBlurRadius, so it threw a NullPointerException
            // here and took the whole app, and the accessibility service with it, down. Touching decorView creates it.
            window.decorView
            window.setBackgroundBlurRadius(dp(this, 28))
        }
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
                // Solid, like an iOS sheet: white in light mode, elevated grey in dark. The blur is behind it, not through it.
                setColor(color(this@TaskInputActivity, R.color.pa_card))
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
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
                background = Kit.rounded(color(c, R.color.pa_fill), dpf(c, 20))
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
            // One icon and hue per tile, in QuickTasks.all order: Catch me up, Reply to this, Take me to an address,
            // What's this?, Latest OTP, Record my routine.
            val icons = listOf(R.drawable.ic_chat, R.drawable.ic_send, R.drawable.ic_pin, R.drawable.ic_search, R.drawable.ic_key, R.drawable.ic_doc)
            val hues = listOf(R.color.pa_t_green, R.color.pa_t_indigo, R.color.pa_t_blue, R.color.pa_t_orange, R.color.pa_t_gray, R.color.pa_t_indigo)
            QuickTasks.all.forEachIndexed { index, quick ->
                // Defensive only: a tile added without an icon gets a plain chat icon rather than another tile's.
                val icon = icons.getOrElse(index) { R.drawable.ic_chat }
                val title = quick.title
                val sub = quick.subtitle
                val hue = hues.getOrElse(index) { R.color.pa_t_gray }
                val row = Kit.hstack(c, 12).apply { minimumHeight = dp(c, 56); setPadding(dp(c, 4), 0, dp(c, 4), 0) }
                row.addView(Kit.tile(c, icon, color(c, hue), 0xFFFFFFFF.toInt(), 32).apply {
                    (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
                })
                val col = Kit.vstack(c)
                col.addView(Kit.text(c, title, 16f, 500))
                col.addView(Kit.text(c, sub, 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 1), 0, 0) })
                row.addView(col, Kit.weight())
                row.addView(Kit.icon(c, R.drawable.ic_chevron, 14, R.color.pa_muted).apply { alpha = .6f })
                Kit.pressable(row, 1f) {
                    row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (quick.onPhone == OnPhone.LATEST_OTP) {
                        showLatestOtp()
                    } else if (quick.onPhone == OnPhone.RECORD_ROUTINE) {
                        handled = true
                        TaskScreenHost.current?.startRecording()
                        closeSheet()
                    } else {
                        input.setText(quick.task)
                        input.setSelection(input.text.length)
                    }
                }
                list.addView(row, Kit.lp())
            }
            // The Latest OTP answer: one line under the tiles, hidden until the tile is tapped.
            val line = Kit.hstack(c, 10).apply { visibility = View.GONE; setPadding(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 4)) }
            otpLine = line
            list.addView(line, Kit.lp())
            suggestions = list
            sheet.addView(list, Kit.lp())
            blocks.add(list)
        }

        composer = Kit.hstack(c, 6).apply {
            background = Kit.rounded(color(c, R.color.pa_fill), dpf(c, 26))
            setPadding(dp(c, 18), dp(c, 4), dp(c, 4), dp(c, 4))
            minimumHeight = dp(c, 52)
        }
        composer.addView(input, Kit.weight())
        val mic = Kit.icon(c, R.drawable.ic_mic, 22, R.color.pa_ink).apply {
            // a bare icon until it is listening, as in iOS Messages
            background = Kit.rounded(0x00000000, dpf(c, 22))
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
                onFinal = {
                    // Set first: an empty result clears the box, and clearing it resets the flag.
                    spoken = true
                    input.setText(it.take(MAX_TEXT))
                    input.setSelection(input.text.length)
                },
                onError = { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() },
                onListening = { on ->
                    (mic.background as GradientDrawable).setColor(if (on) color(c, R.color.pa_accent) else 0x00000000)
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
                if (n == 0) spoken = false
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

    /** Reads the code on the phone with no AI, history or logging; the answer is only ever held in this view. */
    private fun showLatestOtp() {
        otpScope.launch {
            val result = OtpQuickAction(AndroidNotifications, System::currentTimeMillis).run()
            val line = otpLine ?: return@launch
            val c = this@TaskInputActivity
            line.removeAllViews()
            when (result) {
                is OtpResult.Found -> {
                    line.addView(Kit.text(c, "OTP ${result.code}, from ${result.from}, ${result.minutesAgo} min ago", 15f, 500), Kit.weight())
                    line.addView(Kit.text(c, "Copy", 16f, 600, R.color.pa_blue).apply {
                        setPadding(dp(c, 10), dp(c, 8), dp(c, 4), dp(c, 8))
                        Kit.pressable(this, 1f) { copySensitive(result.code) }
                    })
                }
                OtpResult.NoneRecent ->
                    line.addView(Kit.text(c, "No recent code in your notifications.", 15f, 400, R.color.pa_muted), Kit.weight())
                OtpResult.NoAccess -> line.addView(
                    Kit.text(c, "Turn on notification access in setup to use this.", 15f, 500, R.color.pa_blue).apply {
                        Kit.pressable(this, 1f) { startActivity(Intent(c, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    Kit.weight(),
                )
            }
            line.visibility = View.VISIBLE
        }
    }

    private fun copySensitive(code: String) {
        val clip = ClipData.newPlainText("OTP", code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
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
        if (question != null) TaskScreenHost.current?.completeAnswer(text) else TaskScreenHost.current?.startTask(text, spoken)
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

    @SuppressLint("GestureBackNavigation") // Android 13+ goes through the callback registered in onCreate; this serves older phones.
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
        otpScope.cancel()
        otpLine?.removeAllViews()
        otpLine = null
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
