package dev.pocketagent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import dev.pocketagent.R
import dev.pocketagent.agent.BUBBLE_RESULT_MS
import dev.pocketagent.agent.BubbleCommand
import dev.pocketagent.agent.BubbleLogic
import dev.pocketagent.agent.BubbleMode
import dev.pocketagent.ui.kit.Kit
import kotlin.math.cos
import kotlin.math.sin

/**
 * The floating bubble, like iOS's AssistiveTouch: a frosted disc that fades to a whisper while you use other apps and
 * wakes at a touch. While a task runs it is solid, so you always know the agent is acting. It never covers the screen
 * edge, so Android's back gesture still works. [BubbleLogic] decides what the gestures mean; this only draws and forwards.
 */
class BubbleOverlay(
    private val context: Context,
    private val logic: BubbleLogic,
    private val onCommand: (BubbleCommand) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val size = Overlays.dp(context, 52)
    private val edgeGap = Overlays.dp(context, 12)
    private val view = BubbleView(context)
    private val params = Overlays.params(
        size,
        size,
        Gravity.TOP or Gravity.START,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    ).also {
        val m = context.resources.displayMetrics
        it.x = m.widthPixels - size - edgeGap
        it.y = m.heightPixels / 3
    }
    private var shown = false
    private var visible = true
    private var touching = false

    private val returnToIdle = Runnable {
        logic.returnToIdle()
        render(logic.mode)
    }
    private val fadeToRest = Runnable { if (!touching) view.rest() }

    /** Screen rectangle of the bubble while it is shown and visible. */
    val bounds: Rect?
        get() = if (shown && visible) Rect(params.x, params.y, params.x + size, params.y + size) else null

    fun show() {
        if (shown) return
        shown = Overlays.add(context, view, params)
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        view.mode = logic.mode
        view.wake()
        scheduleRest()
    }

    fun hide() {
        handler.removeCallbacks(returnToIdle)
        handler.removeCallbacks(fadeToRest)
        view.stopSpinner()
        if (shown) Overlays.remove(context, view)
        shown = false
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun render(mode: BubbleMode) {
        view.mode = mode
        handler.removeCallbacks(returnToIdle)
        if (mode == BubbleMode.DONE || mode == BubbleMode.ERROR) handler.postDelayed(returnToIdle, BUBBLE_RESULT_MS)
        view.wake()
        scheduleRest()
    }

    fun close() = hide()

    /** After a few quiet seconds an idle bubble fades back to a whisper. */
    private fun scheduleRest() {
        handler.removeCallbacks(fadeToRest)
        if (view.mode == BubbleMode.IDLE) handler.postDelayed(fadeToRest, REST_AFTER_MS)
    }

    private fun snapToEdge() {
        val m = context.resources.displayMetrics
        val target = if (params.x + size / 2 < m.widthPixels / 2) edgeGap else m.widthPixels - size - edgeGap
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 320
            interpolator = Kit.ease
            addUpdateListener {
                params.x = it.animatedValue as Int
                Overlays.update(context, view, params)
            }
        }.start()
    }

    private inner class BubbleView(context: Context) : View(context) {
        var mode: BubbleMode = BubbleMode.IDLE
            set(value) {
                if (field == value) return
                field = value
                if (value == BubbleMode.RUNNING) startSpinner() else stopSpinner()
                invalidate()
            }

        private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = Overlays.dp(context, 1).toFloat()
        }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var spin = 0f
        private var spinner: ValueAnimator? = null

        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onCommand(logic.onLongPress())
        }

        init {
            contentDescription = context.getString(R.string.bubble_description)
            alpha = REST_ALPHA
        }

        private fun night() =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        fun wake() {
            animate().alpha(1f).scaleX(if (mode == BubbleMode.IDLE && touching) 1.08f else 1f)
                .scaleY(if (mode == BubbleMode.IDLE && touching) 1.08f else 1f).setDuration(220).setInterpolator(Kit.ease).start()
        }

        fun rest() {
            if (mode != BubbleMode.IDLE) return
            animate().alpha(REST_ALPHA).scaleX(1f).scaleY(1f).setDuration(600).setInterpolator(Kit.ease).start()
        }

        private fun startSpinner() {
            if (spinner != null) return
            spinner = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { spin = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        fun stopSpinner() {
            spinner?.cancel()
            spinner = null
        }

        override fun onDetachedFromWindow() {
            stopSpinner()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val c = w / 2f
            val u = w / 52f
            val dark = night()
            val r = c - 1.5f * u

            // The disc: frosted while idle, solid for every other state.
            val solidFill = when (mode) {
                BubbleMode.IDLE -> 0
                BubbleMode.RUNNING -> context.getColor(R.color.pa_tile)
                BubbleMode.WAITING -> context.getColor(R.color.pa_accent)
                BubbleMode.DONE -> context.getColor(R.color.pa_good)
                BubbleMode.ERROR -> context.getColor(R.color.pa_bad)
            }
            if (mode == BubbleMode.IDLE) {
                disc.color = if (dark) 0x2EFFFFFF else 0x66FFFFFF
                rim.color = if (dark) 0x47FFFFFF else 0xB3FFFFFF.toInt()
            } else {
                disc.color = solidFill
                rim.color = if (dark) 0x33FFFFFF else 0x00000000
            }
            canvas.drawCircle(c, c, r, disc)
            canvas.drawCircle(c, c, r, rim)

            val glyph = when {
                mode == BubbleMode.WAITING -> context.getColor(R.color.pa_on_accent)
                mode == BubbleMode.IDLE -> if (dark) 0xFFFFFFFF.toInt() else context.getColor(R.color.pa_tile)
                else -> 0xFFFFFFFF.toInt()
            }
            ink.color = glyph
            fillInk.color = glyph
            ink.strokeWidth = 2.6f * u

            when (mode) {
                BubbleMode.IDLE -> pocket(canvas, c, u, 1f)
                BubbleMode.RUNNING -> {
                    pocket(canvas, c, u, 0.8f)
                    for (i in 0 until DOTS) {
                        val a = Math.toRadians((spin - i * 360.0 / DOTS))
                        val trail = 1f - i / DOTS.toFloat()
                        fillInk.alpha = (255 * (0.15f + 0.85f * trail * trail)).toInt()
                        val rr = r - 4.2f * u
                        canvas.drawCircle(c + (rr * cos(a)).toFloat(), c + (rr * sin(a)).toFloat(), 1.7f * u, fillInk)
                    }
                    fillInk.alpha = 255
                }
                BubbleMode.WAITING -> {
                    canvas.drawRoundRect(c - 7.5f * u, c - 8.5f * u, c - 2.5f * u, c + 8.5f * u, 1.6f * u, 1.6f * u, fillInk)
                    canvas.drawRoundRect(c + 2.5f * u, c - 8.5f * u, c + 7.5f * u, c + 8.5f * u, 1.6f * u, 1.6f * u, fillInk)
                }
                BubbleMode.DONE -> {
                    val p = Path().apply {
                        moveTo(c - 9 * u, c + 0.5f * u)
                        lineTo(c - 2.5f * u, c + 7 * u)
                        lineTo(c + 9.5f * u, c - 7.5f * u)
                    }
                    ink.strokeWidth = 3.2f * u
                    canvas.drawPath(p, ink)
                }
                BubbleMode.ERROR -> {
                    ink.strokeWidth = 3.2f * u
                    canvas.drawLine(c - 7.5f * u, c - 7.5f * u, c + 7.5f * u, c + 7.5f * u, ink)
                    canvas.drawLine(c + 7.5f * u, c - 7.5f * u, c - 7.5f * u, c + 7.5f * u, ink)
                }
            }
        }

        /** The pocket from the logo: square top, rounded bottom, one stitch. */
        private fun pocket(canvas: Canvas, c: Float, u: Float, scale: Float) {
            val hw = 11f * u * scale
            val top = c - 8.5f * u * scale
            val bottom = c + 10f * u * scale
            val path = Path().apply {
                addRoundRect(
                    RectF(c - hw, top, c + hw, bottom),
                    floatArrayOf(0f, 0f, 0f, 0f, 7f * u * scale, 7f * u * scale, 7f * u * scale, 7f * u * scale),
                    Path.Direction.CW,
                )
            }
            ink.strokeWidth = 2.6f * u * scale
            canvas.drawPath(path, ink)
            canvas.drawLine(c - 5f * u * scale, c - 1f * u * scale, c + 5f * u * scale, c - 1f * u * scale, ink)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    handler.removeCallbacks(fadeToRest)
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    longPressed = false
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    animate().alpha(1f).scaleX(1.08f).scaleY(1.08f).setDuration(140).setInterpolator(Kit.ease).start()
                    handler.postDelayed(longPress, Overlays.LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && dx * dx + dy * dy >= Overlays.TOUCH_SLOP_PX * Overlays.TOUCH_SLOP_PX) {
                        moved = true
                        handler.removeCallbacks(longPress)
                    }
                    if (moved && !longPressed) {
                        val metrics = resources.displayMetrics
                        params.x = (startX + dx).toInt().coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
                        params.y = (startY + dy).toInt().coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
                        Overlays.update(context, this, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    handler.removeCallbacks(longPress)
                    animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(2.4f)).start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (moved) {
                            snapToEdge()
                        } else if (!longPressed) {
                            performClick()
                            onCommand(logic.onTap())
                        }
                    }
                    scheduleRest()
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private companion object {
        const val REST_AFTER_MS = 3_000L
        const val REST_ALPHA = 0.5f
        const val DOTS = 10
    }
}
