package dev.pocketagent.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R
import dev.pocketagent.setup.SetupStep
import dev.pocketagent.setup.StepStatus

/**
 * View helpers for the setup screen. One scale (4 dp steps), one corner radius for controls (14 dp), one for
 * panels (18 dp), and touch targets of at least 48 dp, so every card is built from the same few pieces.
 */
object SetupViews {
    private const val CONTROL_RADIUS = 14
    private const val PANEL_RADIUS = 18
    private const val TARGET_HEIGHT = 52

    private fun medium() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun rounded(context: Context, fill: Int, stroke: Int? = null, strokeDp: Int = 1, radiusDp: Int = CONTROL_RADIUS) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = Overlays.dp(context, radiusDp).toFloat()
            if (stroke != null) setStroke(Overlays.dp(context, strokeDp), stroke)
        }

    private fun ripple(context: Context, content: GradientDrawable) = RippleDrawable(
        ColorStateList.valueOf(context.getColor(R.color.brand_text) and 0x00FFFFFF or 0x26000000),
        content,
        null,
    )

    fun panel(context: Context) = rounded(
        context, context.getColor(R.color.brand_surface), context.getColor(R.color.brand_border), radiusDp = PANEL_RADIUS,
    )

    fun blockParams(context: Context, topDp: Int = 12) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Overlays.dp(context, topDp) }

    /** Running text. Sentence case, comfortable line height. */
    fun body(context: Context, text: CharSequence, muted: Boolean = false): TextView = TextView(context).apply {
        this.text = text
        textSize = 15f
        setLineSpacing(Overlays.dp(context, 3).toFloat(), 1f)
        setTextColor(context.getColor(if (muted) R.color.brand_muted else R.color.brand_text))
        layoutParams = blockParams(context, 8)
    }

    fun gap(context: Context, dp: Int = 8): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, Overlays.dp(context, dp))
    }

    /** A small section heading inside a card. */
    fun heading(context: Context, text: CharSequence): TextView = TextView(context).apply {
        this.text = text
        textSize = 16f
        typeface = medium()
        setTextColor(context.getColor(R.color.brand_text))
        layoutParams = blockParams(context, 20)
    }

    /** The one primary action of a card: dark text on orange, full width. */
    fun primaryButton(context: Context, label: CharSequence, onClick: () -> Unit): TextView =
        button(context, label, context.getColor(R.color.brand_orange), null, context.getColor(R.color.brand_background), onClick)

    /** Secondary actions: an outlined button that keeps the primary one the loudest thing on screen. */
    fun secondaryButton(context: Context, label: CharSequence, onClick: () -> Unit): TextView =
        button(context, label, context.getColor(R.color.brand_surface), context.getColor(R.color.brand_border), context.getColor(R.color.brand_text), onClick)

    private fun button(context: Context, label: CharSequence, fill: Int, stroke: Int?, textColor: Int, onClick: () -> Unit) =
        object : TextView(context) {
            override fun setEnabled(enabled: Boolean) {
                super.setEnabled(enabled)
                alpha = if (enabled) 1f else 0.4f
            }
        }.apply {
            text = label
            textSize = 16f
            typeface = medium()
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextColor(textColor)
            background = ripple(context, rounded(context, fill, stroke))
            minHeight = Overlays.dp(context, TARGET_HEIGHT)
            setPadding(Overlays.dp(context, 20), 0, Overlays.dp(context, 20), 0)
            layoutParams = blockParams(context, 12)
            setOnClickListener { onClick() }
        }

    /** A text field: dark well, hairline border, 52 dp tall for single lines. */
    fun field(context: Context, view: EditText, singleLine: Boolean = true): EditText = view.apply {
        textSize = 16f
        setTextColor(context.getColor(R.color.brand_text))
        setHintTextColor(context.getColor(R.color.brand_muted))
        background = rounded(context, context.getColor(R.color.brand_background), context.getColor(R.color.brand_border))
        val h = Overlays.dp(context, 16)
        val v = Overlays.dp(context, 14)
        setPadding(h, v, h, v)
        if (singleLine) {
            setSingleLine(true)
            minHeight = Overlays.dp(context, TARGET_HEIGHT)
        } else {
            minHeight = Overlays.dp(context, TARGET_HEIGHT * 2)
            gravity = Gravity.TOP or Gravity.START
        }
        layoutParams = blockParams(context, 12)
        setOnFocusChangeListener { _, focused ->
            background = rounded(
                context,
                context.getColor(R.color.brand_background),
                context.getColor(if (focused) R.color.brand_blue else R.color.brand_border),
                strokeDp = if (focused) 2 else 1,
            )
        }
    }

    /** One selectable option. Selected: blue outline on a subdued blue well, bold text. */
    fun chip(context: Context, label: CharSequence, selected: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        typeface = if (selected) Typeface.create("sans-serif-medium", Typeface.BOLD) else medium()
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        isClickable = true
        isFocusable = true
        setTextColor(context.getColor(R.color.brand_text))
        background = ripple(
            context,
            if (selected) {
                rounded(context, context.getColor(R.color.brand_blue_subdued), context.getColor(R.color.brand_blue), strokeDp = 2)
            } else {
                rounded(context, context.getColor(R.color.brand_surface), context.getColor(R.color.brand_border))
            },
        )
        minHeight = Overlays.dp(context, 48)
        setPadding(Overlays.dp(context, 16), 0, Overlays.dp(context, 16), 0)
        setOnClickListener { onClick() }
    }

    /** Lays chips out two to a row. */
    fun chipGrid(context: Context, chips: List<View>): LinearLayout {
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = blockParams(context, 12) }
        chips.chunked(2).forEachIndexed { i, pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { j, chip ->
                chip.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (j == 0 && pair.size == 2) marginEnd = Overlays.dp(context, 8)
                    if (i > 0) topMargin = Overlays.dp(context, 8)
                }
                row.addView(chip)
            }
            if (pair.size == 1) row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(row)
        }
        return grid
    }

    /** One bar of segments, one per required step; filled blue when done. */
    fun progress(context: Context, done: Int, total: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = blockParams(context, 16)
        repeat(total) { i ->
            addView(
                View(context).apply {
                    background = rounded(
                        context,
                        context.getColor(if (i < done) R.color.brand_blue else R.color.brand_border),
                        radiusDp = 2,
                    )
                },
                LinearLayout.LayoutParams(0, Overlays.dp(context, 4), 1f).apply {
                    if (i < total - 1) marginEnd = Overlays.dp(context, 4)
                },
            )
        }
    }
}

/**
 * One step of the checklist, drawn on a vertical rail: a numbered node joined to the next by a line.
 * Only the current step sits in a panel; finished steps are quiet one-line rows and later steps are dimmed.
 */
class StepCard(context: Context, val step: SetupStep, title: String, private val number: Int, isLast: Boolean) :
    LinearLayout(context) {
    val body = LinearLayout(context).apply { orientation = VERTICAL }
    var onEdit: (() -> Unit)? = null

    private val node = TextView(context).apply {
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
    }
    private val line = View(context).apply {
        setBackgroundColor(context.getColor(R.color.brand_border))
        visibility = if (isLast) GONE else VISIBLE
    }
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val titleView = TextView(context).apply {
        text = title
        textSize = 17f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(context.getColor(R.color.brand_text))
    }
    private val summaryView = TextView(context).apply {
        textSize = 14f
        setTextColor(context.getColor(R.color.brand_muted))
        visibility = GONE
    }
    private val editView = TextView(context).apply {
        text = context.getString(R.string.setup_edit)
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(context.getColor(R.color.brand_text))
        gravity = Gravity.CENTER
        minHeight = Overlays.dp(context, 48)
        minWidth = Overlays.dp(context, 48)
        visibility = GONE
        isClickable = true
        isFocusable = true
        setOnClickListener { onEdit?.invoke() }
    }

    init {
        orientation = HORIZONTAL
        val nodeSize = Overlays.dp(context, 28)
        val rail = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(node, LayoutParams(nodeSize, nodeSize))
            addView(line, LayoutParams(Overlays.dp(context, 2), 0, 1f).apply { topMargin = Overlays.dp(context, 4); bottomMargin = Overlays.dp(context, 4) })
        }
        addView(rail, LayoutParams(nodeSize, LayoutParams.MATCH_PARENT).apply { marginEnd = Overlays.dp(context, 14) })

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(titleView)
            addView(summaryView)
        }
        header.addView(titles, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(editView)
        content.addView(header, LayoutParams(LayoutParams.MATCH_PARENT, Overlays.dp(context, 28)).apply { height = LayoutParams.WRAP_CONTENT })
        content.addView(body)
        addView(content, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { bottomMargin = Overlays.dp(context, 20) })
    }

    fun setStatus(status: StepStatus) {
        val c = context
        when (status) {
            StepStatus.DONE -> {
                node.text = "✓"
                node.setTextColor(c.getColor(R.color.brand_text))
                node.background = SetupViews.rounded(c, c.getColor(R.color.brand_blue), radiusDp = 14)
            }
            StepStatus.CURRENT -> {
                node.text = number.toString()
                node.setTextColor(c.getColor(R.color.brand_text))
                node.background = SetupViews.rounded(c, c.getColor(R.color.brand_blue_subdued), c.getColor(R.color.brand_blue), 2, 14)
            }
            StepStatus.TODO -> {
                node.text = number.toString()
                node.setTextColor(c.getColor(R.color.brand_muted))
                node.background = SetupViews.rounded(c, c.getColor(R.color.brand_background), c.getColor(R.color.brand_border), 1, 14)
            }
        }
        titleView.setTextColor(c.getColor(if (status == StepStatus.TODO) R.color.brand_muted else R.color.brand_text))
    }

    fun setSummary(text: String?) {
        summaryView.text = text
        summaryView.visibility = if (text.isNullOrBlank()) GONE else VISIBLE
    }

    /** The open step sits in a panel; collapsed steps are plain rows. */
    fun setExpanded(expanded: Boolean) {
        body.visibility = if (expanded) VISIBLE else GONE
        val pad = Overlays.dp(context, 16)
        if (expanded) {
            content.background = SetupViews.panel(context)
            content.setPadding(pad, pad, pad, pad + Overlays.dp(context, 4))
        } else {
            content.background = null
            content.setPadding(0, Overlays.dp(context, 2), 0, 0)
        }
    }

    fun setEditable(editable: Boolean) {
        editView.visibility = if (editable) VISIBLE else GONE
    }
}
