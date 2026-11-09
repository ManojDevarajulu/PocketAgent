package dev.pocketagent.ui.kit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R

/**
 * The building blocks of every screen. One type scale (Inter), one icon weight, one radius scale
 * (12 controls, 20 cards, 28 sheets) and the 60/30/10 colours from `tokens.xml`, so light and dark share the code.
 */
object Kit {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(c: Context, v: Int): Float = v * c.resources.displayMetrics.density
    fun color(c: Context, id: Int): Int = c.getColor(id)

    private fun face(c: Context, weight: Int): Typeface =
        Typeface.create(c.resources.getFont(R.font.inter), weight, false)

    fun text(
        c: Context, s: CharSequence, sp: Float = 17f, weight: Int = 400, tint: Int = R.color.pa_ink,
    ): TextView = TextView(c).apply {
        text = s
        textSize = sp
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

    fun ripple(c: Context, content: GradientDrawable): RippleDrawable =
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
    ): LinearLayout = hstack(c).apply {
        minimumHeight = dp(c, minHeight)
        setPadding(dp(c, 16), 0, dp(c, 16), 0)
        isClickable = true
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

    fun toggle(c: Context, on: Boolean): View = object : View(c) {
        private val track = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val knob = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        init {
            layoutParams = LinearLayout.LayoutParams(dp(c, 51), dp(c, 31))
            knob.color = Color.WHITE
            knob.setShadowLayer(dpf(c, 2), 0f, dpf(c, 1), 0x40000000)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            track.color = if (on) color(c, R.color.pa_toggle) else color(c, R.color.pa_fill)
            val w = width.toFloat(); val h = height.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, track)
            val r = h / 2 - dpf(c, 2)
            val cx = if (on) w - h / 2 else h / 2
            canvas.drawCircle(cx, h / 2, r, knob)
        }
    }

    fun primary(c: Context, label: CharSequence, heightDp: Int = 54): TextView =
        button(c, label, color(c, R.color.pa_accent), color(c, R.color.pa_on_accent), heightDp, glow = true)

    fun secondary(c: Context, label: CharSequence, heightDp: Int = 50): TextView =
        button(c, label, color(c, R.color.pa_fill), color(c, R.color.pa_ink), heightDp, glow = false)

    private fun button(c: Context, label: CharSequence, fill: Int, textColor: Int, heightDp: Int, glow: Boolean) =
        TextView(c).apply {
            text = label
            textSize = 17f
            typeface = face(c, 600)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            background = ripple(c, rounded(fill, dpf(c, 16)))
            minimumHeight = dp(c, heightDp)
        }

    fun chip(c: Context, label: CharSequence, selected: Boolean = false): TextView = TextView(c).apply {
        text = label
        textSize = 15f
        typeface = face(c, 500)
        setTextColor(color(c, if (selected) R.color.pa_on_navy else R.color.pa_ink))
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(c, 14), 0, dp(c, 14), 0)
        minimumHeight = dp(c, 36)
        background = rounded(color(c, if (selected) R.color.pa_navy else R.color.pa_fill), dpf(c, 18))
    }

    fun segmented(c: Context, items: List<String>, selected: Int): View = hstack(c).apply {
        background = rounded(color(c, R.color.pa_fill), dpf(c, 10))
        setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2))
        items.forEachIndexed { i, s ->
            addView(text(c, s, 14f, if (i == selected) 600 else 500, if (i == selected) R.color.pa_ink else R.color.pa_muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(c, 8), 0, dp(c, 8))
                if (i == selected) {
                    background = rounded(color(c, R.color.pa_card), dpf(c, 8))
                    elevation = dpf(c, 1)
                }
            }, weight())
        }
    }

    fun largeTitle(c: Context, s: CharSequence): TextView = text(c, s, 34f, 700).apply { letterSpacing = -0.02f }

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

    /** The three-tab bar. Selected: ink and a heavier stroke; the rest muted. Hairline on top, near-opaque so lists never show through. */
    fun tabBar(c: Context, selected: Int): View {
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
                addView(
                    vstack(c).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(icon(c, res, 26, if (on) R.color.pa_ink else R.color.pa_muted).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)).apply { gravity = Gravity.CENTER_HORIZONTAL }
                        })
                        addView(
                            text(c, label, 11f, if (on) 600 else 500, if (on) R.color.pa_ink else R.color.pa_muted).apply {
                                gravity = Gravity.CENTER
                                setPadding(0, dp(c, 4), 0, 0)
                            },
                            LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL },
                        )
                    },
                    weight(),
                )
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
