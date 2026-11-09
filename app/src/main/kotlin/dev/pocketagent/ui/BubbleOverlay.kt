package dev.pocketagent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import dev.pocketagent.R
import dev.pocketagent.agent.BUBBLE_RESULT_MS
import dev.pocketagent.agent.BubbleCommand
import dev.pocketagent.agent.BubbleLogic
import dev.pocketagent.agent.BubbleMode

/** The floating bubble. It draws the mode and forwards gestures; [BubbleLogic] decides what they mean. */
class BubbleOverlay(
    private val context: Context,
    private val logic: BubbleLogic,
    private val onCommand: (BubbleCommand) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val size = Overlays.dp(context, 52)
    private val view = BubbleView(context)
    private val params = Overlays.params(
        size,
        size,
        Gravity.TOP or Gravity.START,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    ).also {
        it.x = 0
        it.y = context.resources.displayMetrics.heightPixels / 3
    }
    private var shown = false
    private var visible = true
    private val returnToIdle = Runnable {
        logic.returnToIdle()
        view.mode = logic.mode
    }

    /** Screen rectangle of the bubble while it is shown and visible. */
    val bounds: Rect?
        get() = if (shown && visible) Rect(params.x, params.y, params.x + size, params.y + size) else null

    fun show() {
        if (shown) return
        shown = Overlays.add(context, view, params)
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        view.mode = logic.mode
    }

    fun hide() {
        handler.removeCallbacks(returnToIdle)
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
    }

    fun close() = hide()

    private inner class BubbleView(context: Context) : View(context) {
        var mode: BubbleMode = BubbleMode.IDLE
            set(value) {
                field = value
                invalidate()
            }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeWidth = Overlays.dp(context, 3).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
        }

        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            onCommand(logic.onLongPress())
        }

        init {
            contentDescription = context.getString(R.string.bubble_description)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val c = w / 2f
            fill.color = context.getColor(
                when (mode) {
                    BubbleMode.IDLE -> R.color.bubble_idle
                    BubbleMode.RUNNING -> R.color.bubble_running
                    BubbleMode.WAITING -> R.color.bubble_waiting
                    BubbleMode.DONE -> R.color.bubble_done
                    BubbleMode.ERROR -> R.color.bubble_error
                },
            )
            canvas.drawCircle(c, c, c, fill)
            val u = w / 52f
            when (mode) {
                BubbleMode.IDLE -> sparkle(canvas, c, c, 14 * u)
                BubbleMode.RUNNING -> canvas.drawCircle(c, c, 12 * u, stroke)
                BubbleMode.WAITING -> hand(canvas, c, c, u)
                BubbleMode.DONE -> {
                    val p = Path().apply {
                        moveTo(c - 11 * u, c)
                        lineTo(c - 3 * u, c + 8 * u)
                        lineTo(c + 12 * u, c - 9 * u)
                    }
                    canvas.drawPath(p, stroke)
                }
                BubbleMode.ERROR -> {
                    canvas.drawLine(c - 10 * u, c - 10 * u, c + 10 * u, c + 10 * u, stroke)
                    canvas.drawLine(c + 10 * u, c - 10 * u, c - 10 * u, c + 10 * u, stroke)
                }
            }
        }

        // Four-pointed star.
        private fun sparkle(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val k = r * 0.25f
            val p = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + k, cy - k)
                lineTo(cx + r, cy)
                lineTo(cx + k, cy + k)
                lineTo(cx, cy + r)
                lineTo(cx - k, cy + k)
                lineTo(cx - r, cy)
                lineTo(cx - k, cy - k)
                close()
            }
            canvas.drawPath(p, solid)
        }

        // A simple open hand: a palm and four fingers.
        private fun hand(canvas: Canvas, cx: Float, cy: Float, u: Float) {
            canvas.drawRoundRect(cx - 10 * u, cy - 2 * u, cx + 10 * u, cy + 12 * u, 4 * u, 4 * u, solid)
            for (i in 0..3) {
                val x = cx - 10 * u + i * 6.5f * u
                canvas.drawRoundRect(x, cy - 13 * u, x + 3.5f * u, cy + 2 * u, 1.75f * u, 1.75f * u, solid)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    longPressed = false
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
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (!moved && !longPressed) {
                        performClick()
                        onCommand(logic.onTap())
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
