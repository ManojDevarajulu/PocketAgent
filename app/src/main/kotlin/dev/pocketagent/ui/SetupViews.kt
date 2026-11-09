package dev.pocketagent.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R
import dev.pocketagent.setup.SetupStep
import dev.pocketagent.setup.StepStatus

/** Small view helpers for the setup screen, so every card looks the same. */
object SetupViews {
    fun body(context: Context, text: CharSequence, muted: Boolean = false): TextView = TextView(context).apply {
        this.text = text
        textSize = 15f
        setTextColor(context.getColor(if (muted) R.color.brand_muted else R.color.brand_text))
        setPadding(0, Overlays.dp(context, 6), 0, Overlays.dp(context, 6))
    }

    /** The one primary action of a card: dark text on orange. */
    fun primaryButton(context: Context, label: CharSequence, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        setTextColor(context.getColor(R.color.brand_background))
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundColor(context.getColor(R.color.brand_orange))
        setOnClickListener { onClick() }
    }

    fun secondaryButton(context: Context, label: CharSequence, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        setTextColor(context.getColor(R.color.brand_text))
        setBackgroundColor(context.getColor(R.color.brand_blue_subdued))
        setOnClickListener { onClick() }
    }

    fun gap(context: Context, dp: Int = 8): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, Overlays.dp(context, dp))
    }
}

/** One step of the checklist: a title, a status mark, an optional one-line summary and a body that can collapse. */
class StepCard(context: Context, val step: SetupStep, title: String) : LinearLayout(context) {
    val body = LinearLayout(context).apply { orientation = VERTICAL }
    var onEdit: (() -> Unit)? = null

    private val titleView = TextView(context).apply {
        text = title
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(context.getColor(R.color.brand_text))
    }
    private val statusView = TextView(context).apply {
        textSize = 14f
        gravity = Gravity.END
    }
    private val summaryView = TextView(context).apply {
        textSize = 14f
        setTextColor(context.getColor(R.color.brand_muted))
        visibility = GONE
    }
    private val editButton = SetupViews.secondaryButton(context, context.getString(R.string.setup_edit)) { onEdit?.invoke() }
        .apply { visibility = GONE }

    init {
        orientation = VERTICAL
        val pad = Overlays.dp(context, 16)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(context.getColor(R.color.brand_surface))
            setStroke(Overlays.dp(context, 1), context.getColor(R.color.brand_border))
            cornerRadius = Overlays.dp(context, 12).toFloat()
        }
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(titleView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(statusView)
        addView(header)
        addView(summaryView)
        addView(body)
        addView(editButton)
    }

    fun setStatus(status: StepStatus) {
        val c = context
        statusView.text = when (status) {
            StepStatus.DONE -> c.getString(R.string.setup_status_done)
            StepStatus.CURRENT -> c.getString(R.string.setup_status_current)
            StepStatus.TODO -> ""
        }
        statusView.setTextColor(c.getColor(if (status == StepStatus.DONE) R.color.bubble_done else R.color.brand_muted))
        alpha = if (status == StepStatus.TODO) 0.6f else 1f
    }

    fun setSummary(text: String?) {
        summaryView.text = text
        summaryView.visibility = if (text.isNullOrBlank()) GONE else VISIBLE
    }

    fun setExpanded(expanded: Boolean) {
        body.visibility = if (expanded) VISIBLE else GONE
    }

    fun setEditable(editable: Boolean) {
        editButton.visibility = if (editable) VISIBLE else GONE
    }
}
