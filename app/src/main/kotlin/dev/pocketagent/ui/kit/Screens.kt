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

/** The app's screens, drawn from the kit with sample data. The same builders will be fed real data by the app. */
object Screens {
    private const val GUTTER = 16

    /** Wraps a scrolling screen with the tab bar pinned to the bottom. */
    private fun withTabs(c: Context, content: View, selected: Int, nav: Nav): View = FrameLayout(c).apply {
        setBackgroundColor(color(c, R.color.pa_bg))
        addView(Kit.scroll(c, content, bottomPadDp = 96), FrameLayout.LayoutParams(-1, -1))
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
        Kit.tile(c, icon, color(c, R.color.pa_blue), color(c, R.color.pa_on_blue), 30)

    private fun sectionTitle(c: Context, title: String, action: String? = null): View {
        val h = Kit.hstack(c).apply { setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 10)) }
        h.addView(Kit.text(c, title, 22f, 700).apply { letterSpacing = -0.01f }, Kit.weight())
        if (action != null) h.addView(Kit.text(c, action, 16f, 500, R.color.pa_blue))
        return h
    }

    // ---------------------------------------------------------------- Home

    fun home(c: Context, needsAttention: Boolean = false, nav: Nav = Nav()): View {
        val p = page(c, 24)
        val blocks = ArrayList<View>()
        fun add(v: View, lp: LinearLayout.LayoutParams = Kit.lp()) { p.addView(v, lp); blocks.add(v) }

        // greeting
        val head = Kit.vstack(c).apply { setPadding(dp(c, 4), 0, 0, 0) }
        head.addView(Kit.text(c, "Monday 9 November", 15f, 400, R.color.pa_muted))
        head.addView(Kit.largeTitle(c, "Good evening").apply { setPadding(0, dp(c, 2), 0, 0) })
        add(head)

        // status: one quiet line, or one loud card
        if (!needsAttention) {
            val line = Kit.hstack(c, 8).apply { setPadding(dp(c, 4), dp(c, 12), 0, 0) }
            line.addView(View(c).apply {
                background = Kit.rounded(color(c, R.color.pa_good), dpf(c, 4))
                layoutParams = LinearLayout.LayoutParams(dp(c, 8), dp(c, 8))
            })
            line.addView(Kit.text(c, "Ready", 15f, 600))
            line.addView(Kit.text(c, "Model works · Accessibility on", 15f, 400, R.color.pa_muted))
            Kit.pressable(line, 1f) { nav.tab(2) }
            add(line)
        } else {
            val card = Kit.vstack(c).apply {
                background = Kit.rounded(color(c, R.color.pa_card), dpf(c, 22), color(c, R.color.pa_bad), dp(c, 2))
                setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 18))
            }
            card.addView(Kit.text(c, "Accessibility is off", 19f, 600))
            card.addView(Kit.text(c, "Pocket Agent can't tap or read the screen until you turn it back on.", 15f, 400, R.color.pa_muted).apply {
                setPadding(0, dp(c, 4), 0, 0); setLineSpacing(dpf(c, 3), 1f)
            })
            card.addView(Kit.primary(c, "Turn it on"), Kit.lp().apply { topMargin = dp(c, 16) })
            add(card, Kit.lp().apply { topMargin = dp(c, 14) })
        }

        // composer: the hero. One card, no inner box.
        val composer = Kit.vstack(c).apply {
            background = Kit.rounded(color(c, R.color.pa_card), dpf(c, 28))
            setPadding(dp(c, 20), dp(c, 18), dp(c, 14), dp(c, 14))
            elevation = dpf(c, 2)
            alpha = if (needsAttention) .5f else 1f
        }
        composer.addView(
            Kit.text(c, if (needsAttention) "Turn on accessibility first" else "What should I do?", 22f, 500, R.color.pa_muted).apply {
                minimumHeight = dp(c, 56)
            },
        )
        val controls = Kit.hstack(c).apply { setPadding(0, dp(c, 6), 0, 0) }
        controls.addView(
            Kit.icon(c, R.drawable.ic_mic, 22, R.color.pa_ink).apply {
                background = Kit.rounded(color(c, R.color.pa_fill), dpf(c, 22))
                setPadding(dp(c, 11), dp(c, 11), dp(c, 11), dp(c, 11))
                layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
                Kit.pressable(this, 0.9f)
            },
        )
        controls.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(
            Kit.icon(c, R.drawable.ic_send, 20, R.color.pa_on_accent).apply {
                background = Kit.rounded(color(c, if (needsAttention) R.color.pa_line else R.color.pa_accent), dpf(c, 22))
                setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
                layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
                Kit.pressable(this, 0.9f)
            },
        )
        composer.addView(controls, Kit.lp())
        add(composer, Kit.lp().apply { topMargin = dp(c, 20) })

        // shortcuts: solid blue tiles
        add(sectionTitle(c, "Shortcuts", "Edit"), Kit.lp().apply { topMargin = dp(c, 32) })
        fun tile(icon: Int, title: String): View = Kit.vstack(c).apply {
            background = Kit.rounded(color(c, R.color.pa_blue), dpf(c, 20))
            setPadding(dp(c, 16), dp(c, 16), dp(c, 14), dp(c, 16))
            minimumHeight = dp(c, 112)
            addView(Kit.icon(c, icon, 26, R.color.pa_on_blue))
            addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
            addView(Kit.text(c, title, 16f, 600, R.color.pa_on_blue).apply { setLineSpacing(dpf(c, 1), 1f) })
            Kit.pressable(this, 0.96f)
        }
        val ghost = Kit.vstack(c).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0); cornerRadius = dpf(c, 20); setStroke(dp(c, 2), color(c, R.color.pa_line), dpf(c, 6), dpf(c, 5))
            }
            minimumHeight = dp(c, 112)
            addView(Kit.icon(c, R.drawable.ic_plus, 24, R.color.pa_muted).apply {
                layoutParams = LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            })
            addView(Kit.text(c, "New shortcut", 15f, 500, R.color.pa_muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 8), 0, 0) })
            Kit.pressable(this, 0.96f)
        }
        val grid = Kit.vstack(c, 12)
        grid.addView(Kit.hstack(c, 12).apply {
            addView(tile(R.drawable.ic_pin, "Take me to an address"), Kit.weight())
            addView(tile(R.drawable.ic_chat, "Reply to the latest message"), Kit.weight())
        }, Kit.lp())
        grid.addView(Kit.hstack(c, 12).apply {
            addView(tile(R.drawable.ic_doc, "Summarise this screen"), Kit.weight())
            addView(ghost, Kit.weight())
        }, Kit.lp())
        add(grid)

        // recent: a plain list, no card
        add(sectionTitle(c, "Recent", "See all"), Kit.lp().apply { topMargin = dp(c, 32) })
        val list = Kit.vstack(c)
        fun item(ok: Boolean, title: String, sub: String, time: String, last: Boolean) {
            val r = Kit.hstack(c, 14).apply { minimumHeight = dp(c, 68); setPadding(dp(c, 4), 0, dp(c, 4), 0) }
            r.addView(Kit.icon(c, if (ok) R.drawable.ic_check else R.drawable.ic_close, 22, if (ok) R.color.pa_good else R.color.pa_bad))
            val col = Kit.vstack(c)
            col.addView(Kit.text(c, title, 17f, 500).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            col.addView(Kit.text(c, sub, 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 2), 0, 0) })
            r.addView(col, Kit.weight())
            r.addView(Kit.text(c, time, 13f, 400, R.color.pa_muted))
            Kit.pressable(r, 1f) { nav.push("detail") }
            list.addView(r, Kit.lp())
            if (!last) list.addView(View(c).apply { setBackgroundColor(color(c, R.color.pa_line)) },
                Kit.lp(h = 1).apply { marginStart = dp(c, 40) })
        }
        item(true, "Take me to the address in this message", "Maps · 41 s", "2 min", false)
        item(true, "Summarise this screen", "Chrome · 12 s", "1 h", false)
        item(false, "Order my usual from the app", "Stopped by you", "Mon", true)
        add(list)
        add(Kit.footnote(c, "Today: 3 tasks, about 18k tokens used.").apply { setPadding(dp(c, 4), dp(c, 16), 0, 0) })

        p.post { Kit.reveal(blocks) }
        return withTabs(c, p, 0, nav)
    }

    // ------------------------------------------------------------ Activity

    fun activity(c: Context, nav: Nav = Nav()): View {
        val p = page(c)
        p.addView(Kit.largeTitle(c, "Activity").apply { setPadding(dp(c, 4), 0, 0, 0) })
        p.addView(gap(c, 14))
        val search = Kit.hstack(c, 8).apply {
            background = Kit.rounded(color(c, R.color.pa_fill), dpf(c, 12))
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            minimumHeight = dp(c, 40)
        }
        search.addView(Kit.icon(c, R.drawable.ic_search, 18, R.color.pa_muted))
        search.addView(Kit.text(c, "Search tasks", 16f, 400, R.color.pa_muted))
        p.addView(search, Kit.lp())
        p.addView(gap(c, 12))
        p.addView(Kit.segmented(c, listOf("All", "Done", "Stopped"), 0), Kit.lp())
        p.addView(gap(c, 22))

        fun r(ok: Boolean, t: String, s: String, time: String) =
            Kit.row(c, t, s, if (ok) okTile(c) else failTile(c), Kit.text(c, time, 13f, 400, R.color.pa_muted), 64) { nav.push("detail") }

        p.addView(Kit.section(c, "Today"))
        p.addView(
            Kit.group(
                c,
                listOf(
                    r(true, "Take me to the address in this message", "Maps · 9 steps · 41 s", "2 min"),
                    r(true, "Summarise this screen", "Chrome · 3 steps · 12 s", "1 h"),
                    r(true, "Reply to Priya: running late", "WhatsApp · 6 steps · 33 s", "3 h"),
                ),
                sepInsetDp = 62,
            ),
            Kit.lp(),
        )
        p.addView(gap(c, 22))
        p.addView(Kit.section(c, "Yesterday"))
        p.addView(
            Kit.group(
                c,
                listOf(
                    r(false, "Order my usual from the app", "Stopped by you · 4 steps", "Mon"),
                    r(true, "Turn on battery saver", "Blocked app · nothing was changed", "Mon"),
                ),
                sepInsetDp = 62,
            ),
            Kit.lp(),
        )
        p.addView(
            Kit.footnote(
                c,
                "Kept only on this phone: the task you typed and how it went. Never what was on your screen. Turn this off or clear it in Settings.",
            ),
        )
        return withTabs(c, p, 1, nav)
    }

    fun activityDetail(c: Context, nav: Nav = Nav()): View {
        val p = page(c, 20)
        val back = Kit.hstack(c, 2).apply { setPadding(0, 0, 0, dp(c, 14)) }
        back.addView(Kit.icon(c, R.drawable.ic_back, 22, R.color.pa_blue))
        back.addView(Kit.text(c, "Activity", 17f, 400, R.color.pa_blue))
        Kit.pressable(back, 1f) { nav.back() }
        p.addView(back, Kit.lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        p.addView(Kit.text(c, "Take me to the address in this message", 26f, 700).apply { letterSpacing = -0.02f; setLineSpacing(dpf(c, 2), 1f) })
        p.addView(Kit.text(c, "Today, 6:42 pm", 15f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 6), 0, dp(c, 18)) })

        val res = Kit.card(c, 22, 16)
        val head = Kit.hstack(c)
        head.addView(okTile(c))
        head.addView(Kit.text(c, "Done", 17f, 600))
        res.addView(head)
        res.addView(Kit.text(c, "Maps is showing directions to 14 Park Street.", 15f, 400).apply { setPadding(0, dp(c, 10), 0, dp(c, 14)) })
        val stats = Kit.hstack(c)
        listOf("9" to "steps", "41 s" to "time", "6.2k" to "tokens").forEach { (v, l) ->
            val col = Kit.vstack(c).apply { gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(Kit.text(c, v, 20f, 600).apply { gravity = Gravity.CENTER })
            col.addView(Kit.text(c, l, 13f, 400, R.color.pa_muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 2), 0, 0) })
            stats.addView(col, Kit.weight())
        }
        res.addView(stats)
        p.addView(res, Kit.lp())
        p.addView(gap(c, 22))
        p.addView(Kit.section(c, "What it did"))
        val steps = Kit.card(c, 20, 16)
        val items = listOf("Read the message" to "WhatsApp", "Copied the address" to "14 Park Street", "Opened Google Maps" to "Searched the address", "Started directions" to "Finished")
        items.forEachIndexed { i, (t, st) ->
            val last = i == items.lastIndex
            val r = Kit.hstack(c, 14).apply { gravity = Gravity.TOP }
            val rail = Kit.vstack(c).apply { gravity = Gravity.CENTER_HORIZONTAL }
            rail.addView(View(c).apply {
                background = Kit.rounded(color(c, if (last) R.color.pa_good else R.color.pa_blue), dpf(c, 5))
            }, LinearLayout.LayoutParams(dp(c, 10), dp(c, 10)).apply { topMargin = dp(c, 6) })
            if (!last) rail.addView(View(c).apply { setBackgroundColor(color(c, R.color.pa_line)) },
                LinearLayout.LayoutParams(dp(c, 2), 0, 1f).apply { topMargin = dp(c, 4); bottomMargin = dp(c, 2) })
            r.addView(rail, LinearLayout.LayoutParams(dp(c, 10), ViewGroup.LayoutParams.MATCH_PARENT))
            val col = Kit.vstack(c).apply { setPadding(0, 0, 0, dp(c, if (last) 0 else 16)) }
            col.addView(Kit.text(c, t, 15f, 500))
            col.addView(Kit.text(c, st, 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 2), 0, 0) })
            r.addView(col, Kit.weight())
            steps.addView(r, Kit.lp())
        }
        p.addView(steps, Kit.lp())
        p.addView(gap(c, 20))
        p.addView(Kit.primary(c, "Run again"), Kit.lp())
        p.addView(Kit.text(c, "Delete from Activity", 17f, 400, R.color.pa_bad).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(c, 16), 0, dp(c, 8))
        }, Kit.lp())
        return FrameLayout(c).apply {
            setBackgroundColor(color(c, R.color.pa_bg))
            addView(Kit.scroll(c, p), FrameLayout.LayoutParams(-1, -1))
        }
    }

    // ------------------------------------------------------------ Settings

    private fun settingsGroup(c: Context, vararg rows: View) = Kit.group(c, rows.toList(), 60)

    fun settings(c: Context, nav: Nav = Nav()): View {
        val p = page(c)
        p.addView(Kit.largeTitle(c, "Settings").apply { setPadding(dp(c, 4), 0, 0, 0) })
        p.addView(gap(c, 22))

        val about = Kit.row(
            c, "Pocket Agent", "Version 0.1.0 · Ready",
            leading = ImageView(c).apply {
                setImageResource(R.drawable.logo_mark)
                layoutParams = LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)).apply { marginEnd = dp(c, 14) }
            },
            minHeight = 76,
        )
        p.addView(Kit.group(c, listOf(about), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "AI"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Provider and model", leading = blueTile(c, R.drawable.ic_globe), trailing = Kit.value(c, "OpenRouter"), onClick = {}),
            Kit.row(c, "API key", leading = blueTile(c, R.drawable.ic_key), trailing = Kit.value(c, "Saved"), onClick = {}),
        ), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Permissions"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Accessibility", leading = blueTile(c, R.drawable.ic_access), trailing = Kit.value(c, "On", tint = R.color.pa_good), onClick = {}),
            Kit.row(c, "Microphone", leading = blueTile(c, R.drawable.ic_mic), trailing = Kit.value(c, "Off"), onClick = {}),
        ), Kit.lp())
        p.addView(Kit.footnote(c, "Accessibility is how Pocket Agent reads and taps. It only listens while a task runs."))
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Bubble"))
        p.addView(Kit.group(c, listOf(
            Kit.row(c, "Show the bubble", trailing = Kit.toggle(c, true)),
            Kit.row(c, "Resting look", trailing = Kit.value(c, "Fades"), onClick = {}),
        ), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Safety and privacy"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Apps it never touches", "Banking, payments, passwords", blueTile(c, R.drawable.ic_shield), Kit.chevron(c), 64, onClick = {}),
            Kit.row(c, "Ask before risky actions", leading = blueTile(c, R.drawable.ic_check), trailing = Kit.value(c, "Always", false)),
            Kit.row(c, "Keep Activity history", leading = blueTile(c, R.drawable.ic_activity), trailing = Kit.toggle(c, true)),
            Kit.row(c, "Clear history",
                leading = Kit.tile(c, R.drawable.ic_trash, color(c, R.color.pa_bad) and 0x00FFFFFF or 0x24000000, color(c, R.color.pa_bad), 30),
                titleTint = R.color.pa_bad, onClick = {}),
        ), Kit.lp())
        p.addView(Kit.footnote(c, "What is sent: the text on your screen, to your provider only. What is kept: your task and how it went, on this phone only."))
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Appearance"))
        p.addView(Kit.group(c, listOf(Kit.row(c, "Theme", trailing = Kit.value(c, "System"), onClick = {})), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "About"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Star us on GitHub", leading = blueTile(c, R.drawable.ic_star), trailing = Kit.chevron(c), onClick = {}),
            Kit.row(c, "Report a problem", leading = blueTile(c, R.drawable.ic_alert), trailing = Kit.chevron(c), onClick = {}),
            Kit.row(c, "Open-source licences", leading = blueTile(c, R.drawable.ic_doc), trailing = Kit.chevron(c), onClick = {}),
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
        val title = Kit.text(c, "Welcome to\nPocket Agent", 34f, 700).apply {
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

    fun notice(c: Context, nav: Nav = Nav()): View {
        val p = Kit.vstack(c).apply { setPadding(dp(c, 28), dp(c, 48), dp(c, 28), dp(c, 24)) }
        val badge = FrameLayout(c).apply {
            background = Kit.rounded(color(c, R.color.pa_blue), dpf(c, 20))
            addView(Kit.icon(c, R.drawable.ic_shield, 32, R.color.pa_on_blue), FrameLayout.LayoutParams(dp(c, 32), dp(c, 32), Gravity.CENTER))
        }
        p.addView(badge, Kit.lp(dp(c, 64), dp(c, 64)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        p.addView(Kit.text(c, "Before you start", 30f, 700).apply { gravity = Gravity.CENTER; letterSpacing = -0.02f },
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
            r.addView(Kit.text(c, "${i + 1}", 14f, 600, R.color.pa_on_blue).apply {
                gravity = Gravity.CENTER
                background = Kit.rounded(color(c, R.color.pa_blue), dpf(c, 14))
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
        actions.addView(Kit.primary(c, "I understand", 56), Kit.lp())
        actions.addView(Kit.text(c, "Back", 16f, 500, R.color.pa_blue).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(c, 14), 0, dp(c, 4))
            Kit.pressable(this, 1f) { nav.back() }
        }, Kit.lp())
        return withActions(c, p, actions)
    }
}
