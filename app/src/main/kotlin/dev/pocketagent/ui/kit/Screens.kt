package dev.pocketagent.ui.kit

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit.color
import dev.pocketagent.ui.kit.Kit.dp
import dev.pocketagent.ui.kit.Kit.dpf

data class RecentItem(val ok: Boolean, val title: String, val sub: String, val time: String, val id: Long = 0)

data class ActivityRow(val id: Long, val ok: Boolean, val title: String, val sub: String, val time: String, val day: String)

class ActivityDetail(
    val ok: Boolean, val title: String, val whenText: String, val result: String,
    val duration: String, val tokens: String,
    val onRun: () -> Unit = {}, val onDelete: () -> Unit = {},
)

/** One thing that needs fixing, shown on Home as a single card with one button. */
class Attention(val title: String, val body: String, val action: String, val onAction: () -> Unit)

data class HomeState(
    val statusDetail: String,
    val attention: Attention?,
    val recent: List<RecentItem>,
    val usage: String?,
    val onRun: (String) -> Unit = {},
    val shortcuts: List<String> = DefaultShortcuts,
    val editingShortcuts: Boolean = false,
    val onEditShortcuts: () -> Unit = {},
    val onRemoveShortcut: (String) -> Unit = {},
    val onAddShortcut: () -> Unit = {},
)

val DefaultShortcuts = listOf("Take me to an address", "Reply to the latest message", "Summarise this screen")

data class SettingsState(
    val version: String,
    val provider: String,
    val keySaved: Boolean,
    val accessibilityOn: Boolean,
    val micOn: Boolean,
    val bubbleOn: Boolean,
    val onBubble: (Boolean) -> Unit = {},
    val onProvider: () -> Unit = {},
    val onAccessibility: () -> Unit = {},
    val onMic: () -> Unit = {},
    val showHistory: Boolean = true,
    val theme: String = "system",
    val onTheme: (String) -> Unit = {},
    val historyOn: Boolean = true,
    val onHistory: (Boolean) -> Unit = {},
    val onClearHistory: () -> Unit = {},
    val onRiskyInfo: () -> Unit = {},
    val onStar: () -> Unit = {},
    val onReport: () -> Unit = {},
)

object Samples {
    val recent = listOf(
        RecentItem(true, "Take me to the address in this message", "Maps · 41 s", "2 min"),
        RecentItem(true, "Summarise this screen", "Chrome · 12 s", "1 h"),
        RecentItem(false, "Order my usual from the app", "Stopped by you", "Mon"),
    )
    val home = HomeState("Model works · Accessibility on", null, recent, "Today: 3 tasks, about 18k tokens used.")
    val fix = home.copy(
        attention = Attention("Accessibility is off", "Pocket Agent can't tap or read the screen until you turn it back on.", "Turn it on") {},
    )
    val activity = listOf(
        ActivityRow(1, true, "Take me to the address in this message", "Maps", "2 min", "Today"),
        ActivityRow(2, true, "Summarise this screen", "Chrome", "1 h", "Today"),
        ActivityRow(3, false, "Order my usual from the app", "Stopped by you", "Mon", "Yesterday"),
    )
    val settings = SettingsState("Version 0.1.0 · Ready", "OpenRouter", true, true, false, true)
}

/** The app's screens, drawn from the kit. The app and the debug preview feed them real or sample state. */
object Screens {
    private const val GUTTER = 16

    /** Wraps a scrolling screen with the tab bar pinned to the bottom, and an optional top bar that gains a hairline once content scrolls under it. */
    private fun withTabs(c: Context, content: View, selected: Int, nav: Nav, topBar: View? = null): View = FrameLayout(c).apply {
        setBackgroundColor(color(c, R.color.pa_bg))
        val scroll = Kit.scroll(c, content, bottomPadDp = 96)
        if (topBar == null) {
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
        } else {
            val column = Kit.vstack(c)
            val line = View(c).apply { setBackgroundColor(color(c, R.color.pa_line)); alpha = 0f }
            column.addView(topBar, Kit.lp())
            column.addView(line, Kit.lp(h = 1))
            column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            scroll.setOnScrollChangeListener { _, _, y, _, _ ->
                line.animate().alpha(if (y > dp(c, 6)) 1f else 0f).setDuration(160).start()
            }
            addView(column, FrameLayout.LayoutParams(-1, -1))
        }
        addView(Kit.tabBar(c, selected, nav.tab), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    /** A screen with a fixed bottom action area (welcome, notice): content scrolls, buttons never move. */
    private fun withActions(c: Context, content: View, actions: View): View = FrameLayout(c).apply {
        setBackgroundColor(color(c, R.color.pa_bg))
        addView(Kit.scroll(c, content, bottomPadDp = 150), FrameLayout.LayoutParams(-1, -1))
        addView(
            Kit.vstack(c).apply {
                setBackgroundColor(color(c, R.color.pa_bg))
                setPadding(dp(c, 24), dp(c, 14), dp(c, 24), dp(c, 20))
                addView(actions, Kit.lp())
            },
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM),
        )
    }

    private fun page(c: Context, topDp: Int = 28): LinearLayout = Kit.vstack(c).apply {
        setPadding(dp(c, GUTTER), dp(c, topDp), dp(c, GUTTER), dp(c, 24))
    }

    private fun gap(c: Context, v: Int) = View(c).apply { layoutParams = Kit.lp(h = dp(c, v)) }

    private fun okTile(c: Context) =
        Kit.tile(c, R.drawable.ic_check, color(c, R.color.pa_good) and 0x00FFFFFF or 0x24000000, color(c, R.color.pa_good), 32)

    private fun failTile(c: Context) =
        Kit.tile(c, R.drawable.ic_close, color(c, R.color.pa_bad) and 0x00FFFFFF or 0x24000000, color(c, R.color.pa_bad), 32)

    private fun blueTile(c: Context, icon: Int) =
        Kit.tile(c, icon, color(c, R.color.pa_tile), color(c, R.color.pa_tile_icon), 30)

    private fun sectionTitle(c: Context, title: String, action: String? = null, onAction: (() -> Unit)? = null): View {
        val h = Kit.hstack(c).apply { setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 10)) }
        h.addView(Kit.text(c, title, 19f, 600).apply { letterSpacing = -0.01f }, Kit.weight())
        if (action != null) {
            h.addView(Kit.text(c, action, 16f, 500, R.color.pa_blue).apply {
                setPadding(dp(c, 14), dp(c, 8), 0, dp(c, 8))
                if (onAction != null) Kit.pressable(this, 1f, onClick = onAction)
            })
        }
        return h
    }

    private fun shortcutIcon(title: String): Int {
        val t = title.lowercase()
        return when {
            "address" in t || "map" in t || "direction" in t -> R.drawable.ic_pin
            "reply" in t || "message" in t || "text" in t -> R.drawable.ic_chat
            else -> R.drawable.ic_doc
        }
    }

    // ---------------------------------------------------------------- Home

    /** The app bar: the mark and name on the left, a live status pill on the right. */
    private fun appBar(c: Context, ok: Boolean, nav: Nav): View {
        val bar = Kit.hstack(c).apply {
            setPadding(dp(c, 18), dp(c, 8), dp(c, 14), dp(c, 8))
            minimumHeight = dp(c, 56)
            setBackgroundColor(color(c, R.color.pa_bg))
        }
        bar.addView(ImageView(c).apply {
            setImageResource(R.drawable.logo_mark)
            layoutParams = LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)).apply { marginEnd = dp(c, 10) }
        })
        bar.addView(Kit.text(c, "Pocket Agent", 18f, 600).apply { letterSpacing = -0.01f }, Kit.weight())
        val pill = Kit.hstack(c, 7).apply {
            background = Kit.rounded(color(c, R.color.pa_fill), dpf(c, 16))
            setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7))
        }
        pill.addView(View(c).apply {
            background = Kit.rounded(color(c, if (ok) R.color.pa_good else R.color.pa_bad), dpf(c, 4))
            layoutParams = LinearLayout.LayoutParams(dp(c, 8), dp(c, 8))
        })
        pill.addView(Kit.text(c, if (ok) "Ready" else "Needs setup", 14f, 600))
        Kit.pressable(pill, 0.94f) { nav.tab(2) }
        bar.addView(pill)
        return bar
    }

    fun home(c: Context, state: HomeState = Samples.home, nav: Nav = Nav()): View {
        val needsAttention = state.attention != null
        val p = page(c, 12)
        val blocks = ArrayList<View>()
        fun add(v: View, lp: LinearLayout.LayoutParams = Kit.lp()) { p.addView(v, lp); blocks.add(v) }

        // one calm banner when something needs fixing: tinted, with a small pill button
        state.attention?.let { at ->
            val banner = Kit.hstack(c, 12).apply {
                background = Kit.rounded(color(c, R.color.pa_bad) and 0x00FFFFFF or 0x1A000000, dpf(c, 20))
                setPadding(dp(c, 14), dp(c, 14), dp(c, 12), dp(c, 14))
            }
            banner.addView(Kit.icon(c, R.drawable.ic_alert, 22, R.color.pa_bad).apply {
                background = Kit.rounded(color(c, R.color.pa_bad) and 0x00FFFFFF or 0x26000000, dpf(c, 18))
                setPadding(dp(c, 7), dp(c, 7), dp(c, 7), dp(c, 7))
                layoutParams = LinearLayout.LayoutParams(dp(c, 36), dp(c, 36))
            })
            val col = Kit.vstack(c)
            col.addView(Kit.text(c, at.title, 15f, 600))
            col.addView(Kit.text(c, at.body, 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 2), 0, 0) })
            banner.addView(col, Kit.weight())
            banner.addView(
                Kit.text(c, at.action, 14f, 600, R.color.pa_on_accent).apply {
                    gravity = Gravity.CENTER
                    background = Kit.rounded(color(c, R.color.pa_accent), dpf(c, 18))
                    setPadding(dp(c, 14), 0, dp(c, 14), 0)
                    minimumHeight = dp(c, 36)
                    Kit.pressable(this, 0.94f, onClick = at.onAction)
                },
            )
            add(banner, Kit.lp().apply { bottomMargin = dp(c, 14) })
        }

        // composer: the hero. The send button wakes up (grey to orange, with a pop) once there is something to send.
        val composer = Kit.vstack(c).apply {
            background = Kit.rounded(color(c, R.color.pa_hero), dpf(c, 28))
            setPadding(dp(c, 18), dp(c, 16), dp(c, 12), dp(c, 12))
            elevation = dpf(c, 1)
            alpha = if (needsAttention) .5f else 1f
        }
        val input = Kit.input(
            c, if (needsAttention) "Fix the banner above first" else "What should I do?",
            textTint = R.color.pa_on_hero, hintTint = R.color.pa_hero_muted,
        ).apply {
            isEnabled = !needsAttention
            minimumHeight = dp(c, 44)
        }
        composer.addView(input, Kit.lp())
        val controls = Kit.hstack(c).apply { setPadding(0, dp(c, 6), 0, 0) }
        controls.addView(
            Kit.icon(c, R.drawable.ic_mic, 22, R.color.pa_on_hero).apply {
                background = Kit.rounded(color(c, R.color.pa_hero_fill), dpf(c, 22))
                setPadding(dp(c, 11), dp(c, 11), dp(c, 11), dp(c, 11))
                layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
                Kit.pressable(this, 0.9f)
            },
        )
        controls.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        val send = Kit.icon(c, R.drawable.ic_send, 20, R.color.pa_hero_muted).apply {
            val bg = Kit.rounded(color(c, R.color.pa_hero_fill), dpf(c, 22))
            background = bg
            setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
            Kit.pressable(this, 0.9f) {
                val t = input.text.toString().trim()
                if (t.isNotEmpty() && !needsAttention) { state.onRun(t); input.setText("") }
            }
        }
        controls.addView(send)
        var armed = false
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val has = !s.isNullOrBlank()
                if (has == armed) return
                armed = has
                val from = if (has) color(c, R.color.pa_hero_fill) else color(c, R.color.pa_accent)
                val to = if (has) color(c, R.color.pa_accent) else color(c, R.color.pa_hero_fill)
                android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, to).apply {
                    duration = 220
                    addUpdateListener { (send.background as GradientDrawable).setColor(it.animatedValue as Int) }
                }.start()
                send.setColorFilter(color(c, if (has) R.color.pa_on_accent else R.color.pa_hero_muted))
                if (has) send.animate().scaleX(1.14f).scaleY(1.14f).setDuration(110).withEndAction {
                    send.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
                }.start()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c1: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c2: Int) {}
        })
        input.setOnFocusChangeListener { _, focused ->
            composer.animate().translationZ(if (focused) dpf(c, 6) else 0f).setDuration(220).setInterpolator(Kit.ease).start()
        }
        composer.addView(controls, Kit.lp())
        add(composer)

        // shortcuts: compact pills in one scrolling row. Edit turns them into removable pills and adds nothing else.
        val editing = state.editingShortcuts
        add(
            sectionTitle(c, "Shortcuts", if (editing) "Done" else "Edit", state.onEditShortcuts),
            Kit.lp().apply { topMargin = dp(c, 28) },
        )
        val pills = Kit.hstack(c, 8).apply { setPadding(0, dp(c, 4), dp(c, 16), dp(c, 4)) }
        state.shortcuts.forEachIndexed { i, title ->
            val pill = Kit.hstack(c, 8).apply {
                background = Kit.rounded(color(c, R.color.pa_tile), dpf(c, 22))
                setPadding(dp(c, 14), 0, dp(c, if (editing) 8 else 16), 0)
                minimumHeight = dp(c, 44)
            }
            pill.addView(Kit.icon(c, shortcutIcon(title), 18, R.color.pa_tile_icon))
            pill.addView(Kit.text(c, title, 14f, 600, R.color.pa_on_tile).apply { maxLines = 1 })
            if (editing) {
                pill.addView(
                    Kit.icon(c, R.drawable.ic_close, 14, R.color.pa_tile_icon).apply {
                        background = Kit.rounded(0x33FFFFFF, dpf(c, 11))
                        setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4))
                        layoutParams = LinearLayout.LayoutParams(dp(c, 22), dp(c, 22))
                    },
                )
                // a gentle wiggle says "these can be changed"
                pill.rotation = if (i % 2 == 0) -1.6f else 1.6f
                pill.animate().rotation(if (i % 2 == 0) 1.6f else -1.6f).setDuration(120).withEndAction(object : Runnable {
                    var flip = false
                    override fun run() {
                        if (!pill.isAttachedToWindow) return
                        flip = !flip
                        val target = if ((i % 2 == 0) == flip) -1.6f else 1.6f
                        pill.animate().rotation(target).setDuration(120).withEndAction(this).start()
                    }
                }).start()
                Kit.pressable(pill, 0.94f) {
                    pill.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).withEndAction { state.onRemoveShortcut(title) }.start()
                }
            } else {
                Kit.pressable(pill, 0.95f) { if (!needsAttention) state.onRun(title) }
            }
            pills.addView(pill)
        }
        if (!editing) {
            val add = Kit.hstack(c, 6).apply {
                background = GradientDrawable().apply {
                    setColor(0); cornerRadius = dpf(c, 22); setStroke(dp(c, 2), color(c, R.color.pa_line), dpf(c, 6), dpf(c, 5))
                }
                setPadding(dp(c, 14), 0, dp(c, 16), 0)
                minimumHeight = dp(c, 44)
            }
            add.addView(Kit.icon(c, R.drawable.ic_plus, 16, R.color.pa_muted))
            add.addView(Kit.text(c, "New", 14f, 500, R.color.pa_muted))
            Kit.pressable(add, 0.95f, onClick = state.onAddShortcut)
            pills.addView(add)
        }
        val scroller = Kit.hscroll(c, pills).apply { clipChildren = false }
        add(scroller)

        // recent: a plain list, no card
        add(sectionTitle(c, "Recent", if (state.recent.isEmpty()) null else "See all"), Kit.lp().apply { topMargin = dp(c, 32) })
        if (state.recent.isEmpty()) {
            add(Kit.text(c, "Nothing yet. Tasks you run will show up here, on this phone only.", 15f, 400, R.color.pa_muted).apply {
                setPadding(dp(c, 4), 0, dp(c, 4), 0); setLineSpacing(dpf(c, 3), 1f)
            })
        } else {
            val list = Kit.vstack(c)
            state.recent.forEachIndexed { i, it ->
                val r = Kit.hstack(c, 14).apply { minimumHeight = dp(c, 68); setPadding(dp(c, 4), 0, dp(c, 4), 0) }
                r.addView(Kit.icon(c, if (it.ok) R.drawable.ic_check else R.drawable.ic_close, 22, if (it.ok) R.color.pa_good else R.color.pa_bad))
                val col = Kit.vstack(c)
                col.addView(Kit.text(c, it.title, 17f, 500).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                col.addView(Kit.text(c, it.sub, 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 2), 0, 0) })
                r.addView(col, Kit.weight())
                r.addView(Kit.text(c, it.time, 13f, 400, R.color.pa_muted))
                Kit.pressable(r, 1f) { nav.push(if (it.id == 0L) "detail" else "detail:${it.id}") }
                list.addView(r, Kit.lp())
                if (i < state.recent.lastIndex) list.addView(View(c).apply { setBackgroundColor(color(c, R.color.pa_line)) },
                    Kit.lp(h = 1).apply { marginStart = dp(c, 40) })
            }
            add(list)
        }
        state.usage?.let { u -> add(Kit.footnote(c, u).apply { setPadding(dp(c, 4), dp(c, 16), 0, 0) }) }

        p.post { Kit.reveal(blocks) }
        return withTabs(c, p, 0, nav, topBar = appBar(c, !needsAttention, nav))
    }

    // ------------------------------------------------------------ Activity

    fun activity(c: Context, nav: Nav = Nav(), empty: Boolean = false, rows: List<ActivityRow>? = null): View {
        val all = if (empty) emptyList() else rows ?: Samples.activity
        val p = page(c)
        p.addView(Kit.largeTitle(c, "Activity").apply { setPadding(dp(c, 4), 0, 0, 0) })
        if (all.isEmpty()) {
            val e = Kit.vstack(c).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(c, 24), dp(c, 120), dp(c, 24), 0) }
            e.addView(Kit.icon(c, R.drawable.ic_activity, 44, R.color.pa_muted).apply {
                layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            })
            e.addView(Kit.text(c, "No activity yet", 19f, 600).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 16), 0, 0) }, Kit.lp())
            e.addView(Kit.text(c, "Tasks you run will show up here, with how they went. Kept on this phone only.", 15f, 400, R.color.pa_muted).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(c, 8), 0, 0); setLineSpacing(dpf(c, 3), 1f)
            }, Kit.lp())
            p.addView(e, Kit.lp())
            return withTabs(c, p, 1, nav)
        }
        p.addView(gap(c, 14))
        val search = Kit.hstack(c, 8).apply {
            background = Kit.rounded(color(c, R.color.pa_fill), dpf(c, 12))
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            minimumHeight = dp(c, 40)
        }
        search.addView(Kit.icon(c, R.drawable.ic_search, 18, R.color.pa_muted))
        val query = Kit.input(c, "Search tasks", 16f).apply { minLines = 1; maxLines = 1; setSingleLine(true) }
        search.addView(query, Kit.weight())
        p.addView(search, Kit.lp())
        p.addView(gap(c, 12))
        val list = Kit.vstack(c)
        var filter = 0
        fun render() {
            list.removeAllViews()
            val q = query.text.toString().trim()
            val shown = all.filter {
                (filter == 0 || (filter == 1 && it.ok) || (filter == 2 && !it.ok)) && (q.isEmpty() || it.title.contains(q, ignoreCase = true))
            }
            if (shown.isEmpty()) {
                list.addView(Kit.text(c, "Nothing matches.", 15f, 400, R.color.pa_muted).apply { setPadding(dp(c, 20), dp(c, 24), 0, 0) })
                return
            }
            shown.groupBy { it.day }.forEach { (day, items) ->
                list.addView(Kit.section(c, day), Kit.lp().apply { topMargin = dp(c, 22) })
                list.addView(
                    Kit.group(
                        c,
                        items.map { r ->
                            Kit.row(c, r.title, r.sub, if (r.ok) okTile(c) else failTile(c), Kit.text(c, r.time, 13f, 400, R.color.pa_muted), 64) {
                                nav.push(if (r.id <= 5) "detail" else "detail:${r.id}")
                            }
                        },
                        sepInsetDp = 62,
                    ),
                    Kit.lp(),
                )
            }
        }
        p.addView(Kit.segmented(c, listOf("All", "Done", "Stopped"), 0) { filter = it; render() }, Kit.lp())
        query.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = render()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c1: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c2: Int) {}
        })
        render()
        p.addView(list, Kit.lp())
        p.addView(Kit.footnote(c, "Kept only on this phone: the task you typed and how it went. Never what was on your screen. Turn this off or clear it in Settings."), Kit.lp().apply { topMargin = dp(c, 12) })
        return withTabs(c, p, 1, nav)
    }

    fun activityDetail(c: Context, nav: Nav = Nav(), d: ActivityDetail? = null): View {
        val detail = d ?: ActivityDetail(true, "Take me to the address in this message", "Today, 6:42 pm", "Maps is showing directions to 14 Park Street.", "41 s", "6.2k")
        val p = page(c, 20)
        val back = Kit.hstack(c, 2).apply { setPadding(0, 0, 0, dp(c, 14)) }
        back.addView(Kit.icon(c, R.drawable.ic_back, 22, R.color.pa_blue))
        back.addView(Kit.text(c, "Activity", 17f, 400, R.color.pa_blue))
        Kit.pressable(back, 1f) { nav.back() }
        p.addView(back, Kit.lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        p.addView(Kit.text(c, detail.title, 24f, 600).apply { letterSpacing = -0.02f; setLineSpacing(dpf(c, 2), 1f) })
        p.addView(Kit.text(c, detail.whenText, 15f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 6), 0, dp(c, 18)) })

        val res = Kit.card(c, 22, 16)
        val head = Kit.hstack(c)
        head.addView(if (detail.ok) okTile(c) else failTile(c))
        head.addView(Kit.text(c, if (detail.ok) "Done" else "Stopped", 17f, 600))
        res.addView(head)
        if (detail.result.isNotBlank()) {
            res.addView(Kit.text(c, detail.result, 15f, 400).apply { setPadding(0, dp(c, 10), 0, dp(c, 14)); setLineSpacing(dpf(c, 3), 1f) })
        } else {
            res.addView(View(c), Kit.lp(h = dp(c, 12)))
        }
        val stats = Kit.hstack(c)
        listOf(detail.duration to "time", detail.tokens to "tokens").forEach { (v, l) ->
            val col = Kit.vstack(c).apply { gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(Kit.text(c, v, 20f, 600).apply { gravity = Gravity.CENTER })
            col.addView(Kit.text(c, l, 13f, 400, R.color.pa_muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 2), 0, 0) })
            stats.addView(col, Kit.weight())
        }
        res.addView(stats)
        p.addView(res, Kit.lp())
        p.addView(gap(c, 22))
        p.addView(Kit.primary(c, "Run again", onClick = detail.onRun), Kit.lp())
        p.addView(Kit.text(c, "Delete from Activity", 17f, 400, R.color.pa_bad).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(c, 16), 0, dp(c, 8))
            Kit.pressable(this, 1f, onClick = detail.onDelete)
        }, Kit.lp())
        return FrameLayout(c).apply {
            setBackgroundColor(color(c, R.color.pa_bg))
            addView(Kit.scroll(c, p), FrameLayout.LayoutParams(-1, -1))
        }
    }

    /** A pushed screen's back link and large title. */
    private fun pushed(c: Context, nav: Nav, back: String, title: String): LinearLayout {
        val p = page(c, 20)
        val row = Kit.hstack(c, 2).apply { setPadding(0, 0, 0, dp(c, 10)) }
        row.addView(Kit.icon(c, R.drawable.ic_back, 22, R.color.pa_blue))
        row.addView(Kit.text(c, back, 17f, 400, R.color.pa_blue))
        Kit.pressable(row, 1f) { nav.back() }
        p.addView(row, Kit.lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        p.addView(Kit.largeTitle(c, title).apply { setPadding(dp(c, 4), 0, 0, dp(c, 8)) })
        return p
    }

    private fun plain(c: Context, p: View): View = FrameLayout(c).apply {
        setBackgroundColor(color(c, R.color.pa_bg))
        addView(Kit.scroll(c, p), FrameLayout.LayoutParams(-1, -1))
    }

    /** The apps the agent will never open or act in. Mirrors the rules in [dev.pocketagent.safety.BlockedApps]. */
    fun blockedApps(c: Context, nav: Nav = Nav()): View {
        val p = pushed(c, nav, "Settings", "Apps it never touches")
        p.addView(Kit.text(c, "Pocket Agent will not open these, read them or tap in them, even if you ask. It is a safety rule that cannot be turned off.", 15f, 400, R.color.pa_muted).apply {
            setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 8)); setLineSpacing(dpf(c, 3), 1f)
        }, Kit.lp())
        fun block(title: String, icon: Int, vararg names: String) {
            p.addView(Kit.section(c, title), Kit.lp().apply { topMargin = dp(c, 18) })
            p.addView(Kit.group(c, names.map { n -> Kit.row(c, n, leading = blueTile(c, icon), minHeight = 52) }, 60), Kit.lp())
        }
        block("Banking and payments", R.drawable.ic_lock, "Google Pay", "PhonePe", "Paytm", "BHIM and other UPI apps", "Any bank or wallet app")
        block("Passwords and sign-in codes", R.drawable.ic_key, "Bitwarden", "1Password", "LastPass", "Google Authenticator")
        block("Phone controls", R.drawable.ic_settings, "Settings", "System controls and permission screens", "App installer")
        p.addView(Kit.footnote(c, "It also skips any app whose name contains bank, pay, wallet, UPI or password."))
        return plain(c, p)
    }

    /** Open-source components bundled in the app. */
    fun licences(c: Context, nav: Nav = Nav()): View {
        val p = pushed(c, nav, "Settings", "Open-source licences")
        p.addView(Kit.text(c, "Pocket Agent is free and open source. It is built on these projects.", 15f, 400, R.color.pa_muted).apply {
            setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 8)); setLineSpacing(dpf(c, 3), 1f)
        }, Kit.lp())
        p.addView(Kit.group(c, listOf(
            Kit.row(c, "Inter typeface", "SIL Open Font Licence 1.1", blueTile(c, R.drawable.ic_doc), null, 64),
            Kit.row(c, "OkHttp", "Apache Licence 2.0", blueTile(c, R.drawable.ic_doc), null, 64),
            Kit.row(c, "Kotlin and kotlinx", "Apache Licence 2.0", blueTile(c, R.drawable.ic_doc), null, 64),
            Kit.row(c, "Android Open Source Project", "Apache Licence 2.0", blueTile(c, R.drawable.ic_doc), null, 64),
        ), 60), Kit.lp().apply { topMargin = dp(c, 14) })
        p.addView(Kit.footnote(c, "The full licence texts are in the project repository on GitHub."))
        return plain(c, p)
    }

    // ------------------------------------------------------------ Settings

    private fun settingsGroup(c: Context, vararg rows: View) = Kit.group(c, rows.toList(), 60)

    fun settings(c: Context, state: SettingsState = Samples.settings, nav: Nav = Nav()): View {
        val p = page(c)
        p.addView(Kit.largeTitle(c, "Settings").apply { setPadding(dp(c, 4), 0, 0, 0) })
        p.addView(gap(c, 22))

        val about = Kit.hstack(c, 14).apply {
            background = Kit.rounded(color(c, R.color.pa_hero), dpf(c, 22))
            setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18))
        }
        about.addView(ImageView(c).apply {
            setImageResource(R.drawable.logo_mark)
            layoutParams = LinearLayout.LayoutParams(dp(c, 52), dp(c, 52))
        })
        val aboutText = Kit.vstack(c)
        aboutText.addView(Kit.text(c, "Pocket Agent", 19f, 600, R.color.pa_on_hero))
        aboutText.addView(Kit.text(c, state.version, 14f, 400, R.color.pa_hero_muted).apply { setPadding(0, dp(c, 3), 0, 0) })
        about.addView(aboutText, Kit.weight())
        p.addView(about, Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "AI"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Provider and model", leading = blueTile(c, R.drawable.ic_globe), trailing = Kit.value(c, state.provider), onClick = state.onProvider),
            Kit.row(c, "API key", leading = blueTile(c, R.drawable.ic_key), trailing = Kit.value(c, if (state.keySaved) "Saved" else "Not set"), onClick = state.onProvider),
        ), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Permissions"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Accessibility", leading = blueTile(c, R.drawable.ic_access),
                trailing = Kit.value(c, if (state.accessibilityOn) "On" else "Off", tint = if (state.accessibilityOn) R.color.pa_good else R.color.pa_bad),
                onClick = state.onAccessibility),
            Kit.row(c, "Microphone", leading = blueTile(c, R.drawable.ic_mic), trailing = Kit.value(c, if (state.micOn) "On" else "Off"), onClick = state.onMic),
        ), Kit.lp())
        p.addView(Kit.footnote(c, "Accessibility is how Pocket Agent reads and taps. It only listens while a task runs."))
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Bubble"))
        p.addView(Kit.group(c, listOf(
            Kit.row(c, "Show the bubble", trailing = Kit.toggle(c, state.bubbleOn, state.onBubble)),
        ), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Appearance"))
        val modes = listOf("system", "light", "dark")
        val appearance = Kit.vstack(c).apply { setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14)) }
        appearance.addView(
            Kit.segmented(c, listOf("System", "Light", "Dark"), modes.indexOf(state.theme).coerceAtLeast(0)) { i -> state.onTheme(modes[i]) },
            Kit.lp(),
        )
        appearance.addView(
            Kit.text(c, "System follows your phone's light or dark setting.", 13f, 400, R.color.pa_muted).apply { setPadding(dp(c, 2), dp(c, 10), 0, 0) },
            Kit.lp(),
        )
        p.addView(Kit.group(c, listOf(appearance), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Safety and privacy"))
        val safety = ArrayList<View>()
        safety.add(Kit.row(c, "Apps it never touches", "Banking, payments, passwords", blueTile(c, R.drawable.ic_shield), Kit.chevron(c), 64) { nav.push("blocked") })
        safety.add(Kit.row(c, "Ask before risky actions", leading = blueTile(c, R.drawable.ic_check), trailing = Kit.value(c, "Always"), onClick = state.onRiskyInfo))
        if (state.showHistory) {
            safety.add(Kit.row(c, "Keep Activity history", leading = blueTile(c, R.drawable.ic_activity), trailing = Kit.toggle(c, state.historyOn, state.onHistory)))
            safety.add(Kit.row(c, "Clear history",
                leading = Kit.tile(c, R.drawable.ic_trash, color(c, R.color.pa_bad) and 0x00FFFFFF or 0x24000000, color(c, R.color.pa_bad), 30),
                titleTint = R.color.pa_bad, onClick = state.onClearHistory))
        }
        p.addView(Kit.group(c, safety, 60), Kit.lp())
        p.addView(Kit.footnote(c, "What is sent: the text on your screen, to your provider only. Nothing from your screen is stored."))
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "About"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Star us on GitHub", leading = blueTile(c, R.drawable.ic_star), trailing = Kit.chevron(c), onClick = state.onStar),
            Kit.row(c, "Report a problem", leading = blueTile(c, R.drawable.ic_alert), trailing = Kit.chevron(c), onClick = state.onReport),
            Kit.row(c, "Open-source licences", leading = blueTile(c, R.drawable.ic_doc), trailing = Kit.chevron(c)) { nav.push("licences") },
        ), Kit.lp())
        p.addView(Kit.footnote(c, "Free and open source. No account, no tracking.").apply { gravity = Gravity.CENTER })
        return withTabs(c, p, 2, nav)
    }

    // ----------------------------------------------------- Welcome and notice

    fun welcome(c: Context, nav: Nav = Nav()): View {
        val p = Kit.vstack(c).apply { setPadding(dp(c, 28), dp(c, 56), dp(c, 28), dp(c, 24)) }
        val blocks = ArrayList<View>()
        val logo = ImageView(c).apply {
            setImageResource(R.drawable.logo_mark)
            elevation = dpf(c, 10)
            background = Kit.rounded(0x00000000, dpf(c, 26))
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        p.addView(logo, Kit.lp(dp(c, 104), dp(c, 104)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        blocks.add(logo)
        val title = Kit.text(c, "Welcome to\nPocket Agent", 30f, 600).apply {
            gravity = Gravity.CENTER; letterSpacing = -0.025f; setLineSpacing(dpf(c, 2), 1f)
        }
        p.addView(title, Kit.lp().apply { topMargin = dp(c, 28) })
        blocks.add(title)

        val feats = Kit.vstack(c, 26).apply { setPadding(0, dp(c, 40), 0, 0) }
        listOf(
            Triple(R.drawable.ic_mic, "Just say it", "Type or speak a task. Pocket Agent opens the apps and does the tapping for you."),
            Triple(R.drawable.ic_shield, "You stay in charge", "It asks before anything that sends, pays, posts or deletes. Hold the bubble to stop it."),
            Triple(R.drawable.ic_key, "Your AI, your key", "Use the service you already pay for. There is no account and no Pocket Agent server."),
        ).forEach { (ic, t, s) ->
            val r = Kit.hstack(c, 18).apply { gravity = Gravity.TOP }
            r.addView(Kit.icon(c, ic, 32, R.color.pa_icon).apply { setPadding(0, dp(c, 2), 0, 0) })
            val col = Kit.vstack(c)
            col.addView(Kit.text(c, t, 17f, 600))
            col.addView(Kit.text(c, s, 15f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 3), 0, 0); setLineSpacing(dpf(c, 3), 1f) })
            r.addView(col, Kit.weight())
            feats.addView(r, Kit.lp())
            blocks.add(r)
        }
        p.addView(feats, Kit.lp())
        p.post { Kit.reveal(blocks, 70) }

        val actions = Kit.vstack(c)
        actions.addView(Kit.text(c, "How your data is handled", 15f, 500, R.color.pa_blue).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(c, 4), 0, dp(c, 14))
        }, Kit.lp())
        actions.addView(Kit.primary(c, "Continue", 56) { nav.push("notice") }, Kit.lp())
        return withActions(c, p, actions)
    }

    fun notice(c: Context, nav: Nav = Nav(), onAccept: () -> Unit = {}): View {
        val p = Kit.vstack(c).apply { setPadding(dp(c, 28), dp(c, 48), dp(c, 28), dp(c, 24)) }
        val badge = FrameLayout(c).apply {
            background = Kit.rounded(color(c, R.color.pa_tile), dpf(c, 20))
            addView(Kit.icon(c, R.drawable.ic_shield, 32, R.color.pa_tile_icon), FrameLayout.LayoutParams(dp(c, 32), dp(c, 32), Gravity.CENTER))
        }
        p.addView(badge, Kit.lp(dp(c, 64), dp(c, 64)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        p.addView(Kit.text(c, "Before you start", 28f, 600).apply { gravity = Gravity.CENTER; letterSpacing = -0.02f },
            Kit.lp().apply { topMargin = dp(c, 22) })
        p.addView(Kit.text(c, "Four things worth knowing, in plain words.", 16f, 400, R.color.pa_muted).apply { gravity = Gravity.CENTER },
            Kit.lp().apply { topMargin = dp(c, 10) })

        val card = Kit.card(c, 22, 18)
        listOf(
            "Sent straight to your AI service." to "The text on your screen, and a screenshot when needed. Nowhere else.",
            "Nothing from your screen is stored." to "Once a task ends, only the task you typed and how it went are kept.",
            "It asks first." to "Anything that sends, pays, posts or deletes needs your yes. It skips banking and password apps.",
            "You can stop it any time." to "Long-press the bubble and it stops at once.",
        ).forEachIndexed { i, (b, rest) ->
            val r = Kit.hstack(c, 14).apply { gravity = Gravity.TOP; setPadding(0, dp(c, 14), 0, dp(c, 14)) }
            r.addView(Kit.text(c, "${i + 1}", 14f, 600, R.color.pa_on_tile).apply {
                gravity = Gravity.CENTER
                background = Kit.rounded(color(c, R.color.pa_tile), dpf(c, 14))
                layoutParams = LinearLayout.LayoutParams(dp(c, 28), dp(c, 28))
            })
            val col = Kit.vstack(c)
            col.addView(Kit.text(c, b, 15f, 600))
            col.addView(Kit.text(c, rest, 15f, 400, R.color.pa_muted).apply { setLineSpacing(dpf(c, 3), 1f); setPadding(0, dp(c, 3), 0, 0) })
            r.addView(col, Kit.weight())
            card.addView(r, Kit.lp())
            if (i < 3) card.addView(View(c).apply { setBackgroundColor(color(c, R.color.pa_line)) }, Kit.lp(h = 1))
        }
        p.addView(card, Kit.lp().apply { topMargin = dp(c, 28) })

        val actions = Kit.vstack(c)
        actions.addView(Kit.primary(c, "I understand", 56, onAccept), Kit.lp())
        actions.addView(Kit.text(c, "Back", 16f, 500, R.color.pa_blue).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(c, 14), 0, dp(c, 4))
            Kit.pressable(this, 1f) { nav.back() }
        }, Kit.lp())
        return withActions(c, p, actions)
    }
}
