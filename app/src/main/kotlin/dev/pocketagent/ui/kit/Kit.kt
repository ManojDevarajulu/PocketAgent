package dev.pocketagent.ui.kit

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R

/** Where a screen can send the user. The host (the app shell, or the preview) decides how to show it. */
class Nav(
    val tab: (Int) -> Unit = {},
    val push: (String) -> Unit = {},
    val back: () -> Unit = {},
)

/**
 * The building blocks of every screen. One type scale (Inter), one icon weight, one radius scale
 * (12 controls, 20 cards, 28 sheets), the 60/30/10 colours from `tokens.xml`, and one motion language:
 * a quick press that shrinks a little and springs back, and an iOS-style ease for everything that moves.
 */
object Kit {
    /** The curve iOS uses for most transitions: fast start, long soft landing. */
    val ease = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(c: Context, v: Int): Float = v * c.resources.displayMetrics.density
    fun color(c: Context, id: Int): Int = c.getColor(id)

    /** The whole type scale, one knob. Inter runs large for its point size, so everything sits a little under iOS's numbers. */
    private const val TYPE_SCALE = 0.86f

    private fun face(c: Context, weight: Int): Typeface =
        Typeface.create(c.resources.getFont(R.font.inter), weight, false)

    fun text(
        c: Context, s: CharSequence, sp: Float = 17f, weight: Int = 400, tint: Int = R.color.pa_ink,
    ): TextView = TextView(c).apply {
        text = s
        textSize = sp * TYPE_SCALE
        typeface = face(c, weight)
        setTextColor(color(c, tint))
        includeFontPadding = false
        setLineSpacing(dpf(c, 2), 1f)
    }

    fun rounded(fill: Int, radiusPx: Float, stroke: Int? = null, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusPx
            if (stroke != null) setStroke(strokePx, stroke)
        }

    fun ripple(content: GradientDrawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(0x22808080), content, null)

    fun vstack(c: Context, gap: Int = 0): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        if (gap > 0) {
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = GradientDrawable().apply { setSize(1, dp(c, gap)) }
        }
    }

    fun hstack(c: Context, gap: Int = 0): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (gap > 0) {
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = GradientDrawable().apply { setSize(dp(c, gap), 1) }
        }
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(w, h)

    fun weight(w: Float = 1f) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)

    // ------------------------------------------------------------------ motion

    /** Press feedback: a small shrink that springs back, like a tapped control on iOS. Row-sized things only dim. */
    fun pressable(v: View, scale: Float = 0.97f, onClick: (() -> Unit)? = null): View {
        v.isClickable = true
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(scale).scaleY(scale).alpha(if (scale == 1f) 0.55f else 0.9f)
                    .setDuration(90).setInterpolator(ease).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(280).setInterpolator(OvershootInterpolator(2.2f)).start()
            }
            false
        }
        if (onClick != null) v.setOnClickListener { onClick() }
        return v
    }

    /** Fades blocks up into place one after another. Used once, when a screen first appears. */
    fun reveal(views: List<View>, stepMs: Long = 45) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = v.resources.displayMetrics.density * 14
            v.animate().alpha(1f).translationY(0f).setStartDelay(60 + i * stepMs).setDuration(420)
                .setInterpolator(ease).start()
        }
    }

    // ------------------------------------------------------------------ pieces

    fun icon(c: Context, res: Int, sizeDp: Int = 20, tint: Int = R.color.pa_ink): ImageView =
        ImageView(c).apply {
            setImageResource(res)
            setColorFilter(color(c, tint))
            layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))
        }

    /** A coloured rounded square with an icon, the leading mark of a settings row. */
    fun tile(c: Context, res: Int, bg: Int, tintColor: Int, sizeDp: Int = 30): View =
        FrameLayout(c).apply {
            background = rounded(bg, dpf(c, sizeDp / 3))
            layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)).apply { marginEnd = dp(c, 14) }
            addView(
                ImageView(c).apply {
                    setImageResource(res)
                    setColorFilter(tintColor)
                },
                FrameLayout.LayoutParams(dp(c, sizeDp * 3 / 5), dp(c, sizeDp * 3 / 5), Gravity.CENTER),
            )
        }

    fun card(c: Context, radius: Int = 20, pad: Int = 16): LinearLayout = vstack(c).apply {
        background = rounded(color(c, R.color.pa_card), dpf(c, radius))
        setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad))
        elevation = dpf(c, 1)
    }

    /** An inset grouped list: rows with hairline separators, like iOS Settings. */
    fun group(c: Context, rows: List<View>, sepInsetDp: Int = 16): LinearLayout = vstack(c).apply {
        background = rounded(color(c, R.color.pa_card), dpf(c, 20))
        elevation = dpf(c, 1)
        clipToOutline = true
        rows.forEachIndexed { i, r ->
            if (i > 0) addView(View(c).apply { setBackgroundColor(color(c, R.color.pa_line)) },
                lp(h = 1).apply { marginStart = dp(c, sepInsetDp) })
            addView(r, lp())
        }
    }

    fun row(
        c: Context,
        title: CharSequence,
        subtitle: CharSequence? = null,
        leading: View? = null,
        trailing: View? = null,
        minHeight: Int = 56,
        titleTint: Int = R.color.pa_ink,
        onClick: (() -> Unit)? = null,
    ): LinearLayout = hstack(c).apply {
        minimumHeight = dp(c, minHeight)
        setPadding(dp(c, 16), 0, dp(c, 16), 0)
        if (onClick != null) pressable(this, 1f, onClick)
        if (leading != null) addView(leading)
        val col = vstack(c).apply {
            addView(text(c, title, 17f, 400, titleTint).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
            if (subtitle != null) addView(text(c, subtitle, 13f, 400, R.color.pa_muted).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(c, 2), 0, 0)
            })
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(col, weight())
        if (trailing != null) addView(trailing)
    }

    fun value(c: Context, s: CharSequence, withChevron: Boolean = true, tint: Int = R.color.pa_muted): View =
        hstack(c, 6).apply {
            addView(text(c, s, 17f, 400, tint))
            if (withChevron) addView(icon(c, R.drawable.ic_chevron, 14, R.color.pa_muted).apply { alpha = .7f })
        }

    fun chevron(c: Context): View = icon(c, R.drawable.ic_chevron, 14, R.color.pa_muted).apply { alpha = .7f }

    /** An iOS switch: the knob slides and the track fades between off and on. Tap it. */
    fun toggle(c: Context, on: Boolean, onChange: ((Boolean) -> Unit)? = null): View = object : View(c) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG)
        private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
        private var checked = on
        private var progress = if (on) 1f else 0f
        private val offColor = color(c, R.color.pa_fill)
        private val onColor = color(c, R.color.pa_toggle)

        init {
            layoutParams = LinearLayout.LayoutParams(dp(c, 51), dp(c, 31))
            knob.color = Color.WHITE
            knob.setShadowLayer(dpf(c, 2), 0f, dpf(c, 1), 0x40000000)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            pressable(this, 0.95f) {
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                checked = !checked
                ValueAnimator.ofFloat(progress, if (checked) 1f else 0f).apply {
                    duration = 260
                    interpolator = OvershootInterpolator(1.4f)
                    addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                }.start()
                onChange?.invoke(checked)
            }
        }

        override fun onDraw(canvas: Canvas) {
            val t = progress.coerceIn(0f, 1f)
            track.color = ArgbEvaluator().evaluate(t, offColor, onColor) as Int
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, track)
            val r = h / 2 - dpf(c, 2)
            val left = h / 2
            val right = w - h / 2
            canvas.drawCircle(left + (right - left) * progress, h / 2, r, knob)
        }
    }

    fun primary(c: Context, label: CharSequence, heightDp: Int = 54, onClick: (() -> Unit)? = null): TextView =
        button(c, label, color(c, R.color.pa_accent), color(c, R.color.pa_on_accent), heightDp, onClick)

    fun secondary(c: Context, label: CharSequence, heightDp: Int = 50, onClick: (() -> Unit)? = null): TextView =
        button(c, label, color(c, R.color.pa_fill), color(c, R.color.pa_ink), heightDp, onClick)

    private fun button(c: Context, label: CharSequence, fill: Int, textColor: Int, heightDp: Int, onClick: (() -> Unit)?) =
        TextView(c).apply {
            text = label
            textSize = 16f * TYPE_SCALE
            typeface = face(c, 600)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isFocusable = true
            background = rounded(fill, dpf(c, 16))
            minimumHeight = dp(c, heightDp)
            pressable(this, 0.97f, onClick)
        }

    fun chip(c: Context, label: CharSequence, selected: Boolean = false): TextView = TextView(c).apply {
        text = label
        textSize = 15f * TYPE_SCALE
        typeface = face(c, 500)
        setTextColor(color(c, if (selected) R.color.pa_on_blue else R.color.pa_ink))
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(c, 14), 0, dp(c, 14), 0)
        minimumHeight = dp(c, 36)
        background = rounded(color(c, if (selected) R.color.pa_blue else R.color.pa_fill), dpf(c, 18))
        pressable(this, 0.95f)
    }

    /** A segmented control whose white pill slides to the chosen segment. */
    fun segmented(c: Context, items: List<String>, selected: Int, onChange: ((Int) -> Unit)? = null): View {
        val pad = dp(c, 2)
        val frame = FrameLayout(c).apply {
            background = rounded(color(c, R.color.pa_fill), dpf(c, 10))
            setPadding(pad, pad, pad, pad)
        }
        val pill = View(c).apply {
            background = rounded(color(c, R.color.pa_card), dpf(c, 8))
            elevation = dpf(c, 1)
        }
        frame.addView(pill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        val labels = ArrayList<TextView>()
        val row = hstack(c)
        var current = selected
        fun paint() = labels.forEachIndexed { i, t ->
            t.typeface = face(c, if (i == current) 600 else 500)
            t.setTextColor(color(c, if (i == current) R.color.pa_ink else R.color.pa_muted))
        }
        items.forEachIndexed { i, s ->
            val t = text(c, s, 14f, 500, R.color.pa_muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(c, 8), 0, dp(c, 8))
                setOnClickListener {
                    if (i == current) return@setOnClickListener
                    current = i
                    paint()
                    pill.animate().translationX(pill.width * i.toFloat()).setDuration(280).setInterpolator(ease).start()
                    onChange?.invoke(i)
                }
            }
            labels.add(t)
            row.addView(t, weight())
        }
        paint()
        frame.addView(row, FrameLayout.LayoutParams(-1, -2))
        frame.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val w = (r - l - pad * 2) / items.size
            if (pill.layoutParams.width != w) {
                pill.layoutParams = (pill.layoutParams as FrameLayout.LayoutParams).apply { width = w }
                pill.translationX = w * current.toFloat()
            }
        }
        return frame
    }

    /** A borderless multi-line text input in the body type, for the composer. */
    fun input(c: Context, hint: CharSequence, sp: Float = 19f): android.widget.EditText = android.widget.EditText(c).apply {
        this.hint = hint
        textSize = sp * TYPE_SCALE
        typeface = face(c, 400)
        setTextColor(color(c, R.color.pa_ink))
        setHintTextColor(color(c, R.color.pa_muted))
        background = null
        setPadding(0, 0, 0, 0)
        includeFontPadding = false
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 2
        maxLines = 5
        gravity = Gravity.TOP or Gravity.START
        filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
    }

    fun largeTitle(c: Context, s: CharSequence): TextView = text(c, s, 32f, 600).apply { letterSpacing = -0.025f }

    fun section(c: Context, s: CharSequence): TextView = text(c, s, 13f, 600, R.color.pa_muted).apply {
        setPadding(dp(c, 20), 0, dp(c, 20), dp(c, 8))
    }

    fun footnote(c: Context, s: CharSequence): TextView = text(c, s, 13f, 400, R.color.pa_muted).apply {
        setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0)
        setLineSpacing(dpf(c, 3), 1f)
    }

    fun scroll(c: Context, content: View, bottomPadDp: Int = 0): android.widget.ScrollView =
        android.widget.ScrollView(c).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(color(c, R.color.pa_bg))
            addView(content)
            clipToPadding = false
            setPadding(0, 0, 0, dp(c, bottomPadDp))
        }

    fun hscroll(c: Context, content: View): HorizontalScrollView = HorizontalScrollView(c).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFocusable = false
        clipToPadding = false
        addView(content)
    }

    /** The three-tab bar. Selected: blue and a heavier label; the rest muted. Tapping an icon bounces it. */
    fun tabBar(c: Context, selected: Int, onTab: (Int) -> Unit = {}): View {
        val items = listOf(
            Triple("Home", R.drawable.ic_home, 0),
            Triple("Activity", R.drawable.ic_activity, 1),
            Triple("Settings", R.drawable.ic_settings, 2),
        )
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 6))
            items.forEach { (label, res, idx) ->
                val on = idx == selected
                val item = vstack(c).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    val ic = icon(c, res, 26, if (on) R.color.pa_blue else R.color.pa_muted).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)).apply { gravity = Gravity.CENTER_HORIZONTAL }
                    }
                    addView(ic)
                    addView(
                        text(c, label, 11f, if (on) 600 else 500, if (on) R.color.pa_blue else R.color.pa_muted).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(c, 4), 0, 0)
                        },
                        LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL },
                    )
                    pressable(this, 0.88f) {
                        if (!on) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            ic.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).withEndAction {
                                ic.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(3f)).start()
                            }.start()
                            onTab(idx)
                        }
                    }
                }
                addView(item, weight())
            }
        }
        return vstack(c).apply {
            setBackgroundColor(color(c, R.color.pa_bar))
            elevation = dpf(c, 8)
            addView(View(c).apply { setBackgroundColor(color(c, R.color.pa_line)) }, lp(h = 1))
            addView(row, lp())
        }
    }
}
