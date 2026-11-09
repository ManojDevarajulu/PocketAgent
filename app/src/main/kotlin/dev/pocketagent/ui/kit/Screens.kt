package dev.pocketagent.ui.kit

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.pocketagent.R
import dev.pocketagent.ui.kit.Kit.color
import dev.pocketagent.ui.kit.Kit.dp
import dev.pocketagent.ui.kit.Kit.dpf

/** The app's screens, drawn from the kit with sample data. The same builders will be fed real data by the app. */
object Screens {
    private const val GUTTER = 16

    /** Wraps a scrolling screen with the tab bar pinned to the bottom. */
    private fun withTabs(c: Context, content: View, selected: Int): View = FrameLayout(c).apply {
        setBackgroundColor(color(c, R.color.pa_bg))
        addView(Kit.scroll(c, content, bottomPadDp = 96), FrameLayout.LayoutParams(-1, -1))
        addView(Kit.tabBar(c, selected), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun page(c: Context, topDp: Int = 28): LinearLayout = Kit.vstack(c).apply {
        setPadding(dp(c, GUTTER), dp(c, topDp), dp(c, GUTTER), dp(c, 24))
    }

    private fun gap(c: Context, v: Int) = View(c).apply { layoutParams = Kit.lp(h = dp(c, v)) }

    private fun okTile(c: Context) =
        Kit.tile(c, R.drawable.ic_check, 0x1F1F9D55, color(c, R.color.pa_good), 32)

    private fun failTile(c: Context) =
        Kit.tile(c, R.drawable.ic_close, 0x1FD92D20, color(c, R.color.pa_bad), 32)

    private fun navyTile(c: Context, icon: Int) =
        Kit.tile(c, icon, color(c, R.color.pa_navy), color(c, R.color.pa_on_navy), 30)

    // ---------------------------------------------------------------- Home

    fun home(c: Context, needsAttention: Boolean = false): View {
        val p = page(c)
        p.addView(Kit.text(c, "Monday 9 November", 15f, 400, R.color.pa_muted).apply { setPadding(dp(c, 4), 0, 0, 0) })
        p.addView(Kit.largeTitle(c, "Good evening").apply { setPadding(dp(c, 4), dp(c, 2), 0, 0) })
        p.addView(gap(c, 22))

        // readiness: the navy 30%, or the one red warning
        if (!needsAttention) {
            val card = Kit.vstack(c).apply {
                background = Kit.rounded(color(c, R.color.pa_navy), dpf(c, 24))
                setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 16))
            }
            val top = Kit.hstack(c)
            top.addView(
                Kit.tile(c, R.drawable.ic_check, 0x24FFFFFF, color(c, R.color.pa_on_navy), 44).apply {
                    background = Kit.rounded(0x24FFFFFF, dpf(c, 22))
                },
            )
            val t = Kit.vstack(c)
            t.addView(Kit.text(c, "Ready to help", 19f, 600, R.color.pa_on_navy))
            t.addView(Kit.text(c, "Everything is set up", 14f, 400, R.color.pa_on_navy).apply { alpha = .7f; setPadding(0, dp(c, 2), 0, 0) })
            top.addView(t, Kit.weight())
            top.addView(Kit.icon(c, R.drawable.ic_chevron, 16, R.color.pa_on_navy).apply { alpha = .6f })
            card.addView(top)
            val chips = Kit.hstack(c, 8).apply { setPadding(0, dp(c, 14), 0, 0) }
            listOf("Model works", "Accessibility on", "Bubble on").forEach {
                chips.addView(Kit.text(c, it, 13f, 500, R.color.pa_on_navy).apply {
                    background = Kit.rounded(0x1FFFFFFF, dpf(c, 14))
                    setPadding(dp(c, 11), dp(c, 6), dp(c, 11), dp(c, 6))
                })
            }
            card.addView(Kit.hscroll(c, chips))
            p.addView(card, Kit.lp())
        } else {
            val card = Kit.vstack(c).apply {
                background = Kit.rounded(color(c, R.color.pa_card), dpf(c, 24), color(c, R.color.pa_bad), dp(c, 2))
                setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18))
                elevation = dpf(c, 2)
            }
            val top = Kit.hstack(c)
            top.addView(Kit.tile(c, R.drawable.ic_alert, 0x1FD92D20, color(c, R.color.pa_bad), 44).apply {
                background = Kit.rounded(0x1FD92D20, dpf(c, 22))
            })
            val t = Kit.vstack(c)
            t.addView(Kit.text(c, "Accessibility is off", 19f, 600))
            t.addView(Kit.text(c, "Pocket Agent can't tap or read the screen until you turn it back on.", 14f, 400, R.color.pa_muted).apply {
                setPadding(0, dp(c, 3), 0, 0)
            })
            top.addView(t, Kit.weight())
            card.addView(top)
            card.addView(Kit.primary(c, "Turn it on"), Kit.lp().apply { topMargin = dp(c, 16) })
            p.addView(card, Kit.lp())
        }
        p.addView(gap(c, 18))

        // new task
        val task = Kit.card(c, 24, 16)
        task.addView(Kit.text(c, "New task", 15f, 600))
        val field = Kit.hstack(c).apply {
            background = Kit.rounded(color(c, R.color.pa_bg), dpf(c, 26))
            setPadding(dp(c, 18), dp(c, 4), dp(c, 4), dp(c, 4))
            minimumHeight = dp(c, 52)
        }
        field.addView(Kit.text(c, if (needsAttention) "Turn on accessibility first" else "What should I do?", 17f, 400, R.color.pa_muted), Kit.weight())
        field.addView(Kit.icon(c, R.drawable.ic_mic, 22).apply { setPadding(dp(c, 11), dp(c, 11), dp(c, 11), dp(c, 11)) }.also {
            it.layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
        })
        val send = Kit.icon(c, R.drawable.ic_send, 20, R.color.pa_on_accent).apply {
            background = Kit.rounded(color(c, if (needsAttention) R.color.pa_line else R.color.pa_accent), dpf(c, 22))
            setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
        }
        field.addView(send)
        task.addView(field, Kit.lp().apply { topMargin = dp(c, 12) })
        task.alpha = if (needsAttention) .5f else 1f
        p.addView(task, Kit.lp())
        p.addView(gap(c, 24))

        // shortcuts
        val head = Kit.hstack(c).apply { setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 8)) }
        head.addView(Kit.text(c, "Shortcuts", 13f, 600, R.color.pa_muted), Kit.weight())
        head.addView(Kit.text(c, "Edit", 15f, 500))
        p.addView(head, Kit.lp())
        val row = Kit.hstack(c, 12).apply { gravity = Gravity.TOP }
        listOf(
            Triple(R.drawable.ic_pin, "Take me to an address", "From a message"),
            Triple(R.drawable.ic_chat, "Reply to the latest message", "Asks before sending"),
        ).forEach { (ic, t, s) ->
            val card = Kit.card(c, 20, 14).apply { minimumHeight = dp(c, 140) }
            card.addView(Kit.tile(c, ic, color(c, R.color.pa_bg), color(c, R.color.pa_icon), 32).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
            })
            card.addView(Kit.text(c, t, 16f, 600).apply { setPadding(0, dp(c, 14), 0, 0) })
            card.addView(Kit.text(c, s, 13f, 400, R.color.pa_muted).apply { setPadding(0, dp(c, 4), 0, 0) })
            row.addView(card, LinearLayout.LayoutParams(dp(c, 148), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val add = Kit.vstack(c).apply {
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0); cornerRadius = dpf(c, 20); setStroke(dp(c, 2), color(c, R.color.pa_line), dpf(c, 6), dpf(c, 5))
            }
            minimumHeight = dp(c, 140)
            addView(Kit.icon(c, R.drawable.ic_plus, 26, R.color.pa_muted).apply {
                layoutParams = LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            })
            addView(Kit.text(c, "New shortcut", 14f, 500, R.color.pa_muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 8), 0, 0) })
        }
        row.addView(add, LinearLayout.LayoutParams(dp(c, 132), ViewGroup.LayoutParams.WRAP_CONTENT))
        p.addView(Kit.hscroll(c, row).apply { clipChildren = false })
        p.addView(gap(c, 24))

        // recent
        val rh = Kit.hstack(c).apply { setPadding(dp(c, 4), 0, dp(c, 4), dp(c, 8)) }
        rh.addView(Kit.text(c, "Recent", 13f, 600, R.color.pa_muted), Kit.weight())
        rh.addView(Kit.text(c, "See all", 15f, 500))
        p.addView(rh, Kit.lp())
        p.addView(
            Kit.group(
                c,
                listOf(
                    Kit.row(c, "Take me to the address in this message", "Maps · 41 s · 2 min ago", okTile(c), Kit.chevron(c), 64),
                    Kit.row(c, "Summarise this screen", "Chrome · 12 s · 1 h ago", okTile(c), Kit.chevron(c), 64),
                    Kit.row(c, "Order my usual from the app", "Stopped by you · yesterday", failTile(c), Kit.chevron(c), 64),
                ),
                sepInsetDp = 62,
            ),
            Kit.lp(),
        )
        p.addView(Kit.footnote(c, "Today: 3 tasks, about 18k tokens used."))
        return withTabs(c, p, 0)
    }

    // ------------------------------------------------------------ Activity

    fun activity(c: Context): View {
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

        p.addView(Kit.section(c, "Today"))
        p.addView(
            Kit.group(
                c,
                listOf(
                    Kit.row(c, "Take me to the address in this message", "Maps · 9 steps · 41 s", okTile(c), Kit.text(c, "2 min", 13f, 400, R.color.pa_muted), 64),
                    Kit.row(c, "Summarise this screen", "Chrome · 3 steps · 12 s", okTile(c), Kit.text(c, "1 h", 13f, 400, R.color.pa_muted), 64),
                    Kit.row(c, "Reply to Priya: running late", "WhatsApp · 6 steps · 33 s", okTile(c), Kit.text(c, "3 h", 13f, 400, R.color.pa_muted), 64),
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
                    Kit.row(c, "Order my usual from the app", "Stopped by you · 4 steps", failTile(c), Kit.text(c, "Mon", 13f, 400, R.color.pa_muted), 64),
                    Kit.row(c, "Turn on battery saver", "Blocked app · nothing was changed", okTile(c), Kit.text(c, "Mon", 13f, 400, R.color.pa_muted), 64),
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
        return withTabs(c, p, 1)
    }

    fun activityDetail(c: Context): View {
        val p = page(c, 20)
        val back = Kit.hstack(c, 2).apply { setPadding(0, 0, 0, dp(c, 14)) }
        back.addView(Kit.icon(c, R.drawable.ic_back, 22))
        back.addView(Kit.text(c, "Activity", 17f, 400))
        p.addView(back)
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
                background = Kit.rounded(color(c, if (last) R.color.pa_good else R.color.pa_navy), dpf(c, 5))
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

    fun settings(c: Context): View {
        val p = page(c)
        p.addView(Kit.largeTitle(c, "Settings").apply { setPadding(dp(c, 4), 0, 0, 0) })
        p.addView(gap(c, 22))

        val about = Kit.row(
            c, "Pocket Agent", "Version 0.1.0 · Ready",
            leading = android.widget.ImageView(c).apply {
                setImageResource(R.drawable.logo_mark)
                layoutParams = LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)).apply { marginEnd = dp(c, 14) }
            },
            minHeight = 76,
        )
        p.addView(Kit.group(c, listOf(about), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "AI"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Provider and model", leading = navyTile(c, R.drawable.ic_globe), trailing = Kit.value(c, "OpenRouter")),
            Kit.row(c, "API key", leading = navyTile(c, R.drawable.ic_key), trailing = Kit.value(c, "Saved")),
        ), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Permissions"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Accessibility", leading = navyTile(c, R.drawable.ic_access), trailing = Kit.value(c, "On", tint = R.color.pa_good)),
            Kit.row(c, "Microphone", leading = navyTile(c, R.drawable.ic_mic), trailing = Kit.value(c, "Off")),
        ), Kit.lp())
        p.addView(Kit.footnote(c, "Accessibility is how Pocket Agent reads and taps. It only listens while a task runs."))
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Bubble"))
        p.addView(Kit.group(c, listOf(
            Kit.row(c, "Show the bubble", trailing = Kit.toggle(c, true)),
            Kit.row(c, "Resting look", trailing = Kit.value(c, "Fades")),
        ), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Safety and privacy"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Apps it never touches", "Banking, payments, passwords", navyTile(c, R.drawable.ic_shield), Kit.chevron(c), 64),
            Kit.row(c, "Ask before risky actions", leading = navyTile(c, R.drawable.ic_check), trailing = Kit.value(c, "Always", false)),
            Kit.row(c, "Keep Activity history", leading = navyTile(c, R.drawable.ic_activity), trailing = Kit.toggle(c, true)),
            Kit.row(c, "Clear history",
                leading = Kit.tile(c, R.drawable.ic_trash, 0x1FD92D20, color(c, R.color.pa_bad), 30),
                titleTint = R.color.pa_bad),
        ), Kit.lp())
        p.addView(Kit.footnote(c, "What is sent: the text on your screen, to your provider only. What is kept: your task and how it went, on this phone only."))
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "Appearance"))
        p.addView(Kit.group(c, listOf(Kit.row(c, "Theme", trailing = Kit.value(c, "System"))), 16), Kit.lp())
        p.addView(gap(c, 26))

        p.addView(Kit.section(c, "About"))
        p.addView(settingsGroup(c,
            Kit.row(c, "Star us on GitHub", leading = navyTile(c, R.drawable.ic_star), trailing = Kit.chevron(c)),
            Kit.row(c, "Report a problem", leading = navyTile(c, R.drawable.ic_alert), trailing = Kit.chevron(c)),
            Kit.row(c, "Open-source licences", leading = navyTile(c, R.drawable.ic_doc), trailing = Kit.chevron(c)),
        ), Kit.lp())
        p.addView(Kit.footnote(c, "Free and open source. No account, no tracking.").apply { gravity = Gravity.CENTER })
        return withTabs(c, p, 2)
    }

    // ----------------------------------------------------- Welcome and notice

    fun welcome(c: Context): View {
        val p = Kit.vstack(c).apply { setPadding(dp(c, 28), dp(c, 72), dp(c, 28), dp(c, 24)) }
        val logo = android.widget.ImageView(c).apply {
            setImageResource(R.drawable.logo_mark)
            elevation = dpf(c, 10)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = Kit.rounded(0x00000000, dpf(c, 26))
        }
        p.addView(logo, Kit.lp(dp(c, 104), dp(c, 104)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        p.addView(Kit.text(c, "Welcome to\nPocket Agent", 34f, 700).apply {
            gravity = Gravity.CENTER; letterSpacing = -0.025f; setLineSpacing(dpf(c, 2), 1f)
        }, Kit.lp().apply { topMargin = dp(c, 28) })

        val feats = Kit.vstack(c, 26).apply { setPadding(0, dp(c, 44), 0, 0) }
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
        }
        p.addView(feats, Kit.lp())
        p.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        p.addView(Kit.text(c, "How your data is handled", 15f, 500).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(c, 14), 0, dp(c, 14))
        }, Kit.lp())
        p.addView(Kit.primary(c, "Continue", 56), Kit.lp())
        return FrameLayout(c).apply {
            setBackgroundColor(color(c, R.color.pa_bg))
            addView(p, FrameLayout.LayoutParams(-1, -1))
        }
    }

    fun notice(c: Context): View {
        val p = Kit.vstack(c).apply { setPadding(dp(c, 28), dp(c, 64), dp(c, 28), dp(c, 24)) }
        val badge = FrameLayout(c).apply {
            background = Kit.rounded(color(c, R.color.pa_navy), dpf(c, 20))
            addView(Kit.icon(c, R.drawable.ic_shield, 32, R.color.pa_on_navy), FrameLayout.LayoutParams(dp(c, 32), dp(c, 32), Gravity.CENTER))
        }
        p.addView(badge, Kit.lp(dp(c, 64), dp(c, 64)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        p.addView(Kit.text(c, "Before you start", 30f, 700).apply { gravity = Gravity.CENTER; letterSpacing = -0.02f },
            Kit.lp().apply { topMargin = dp(c, 22) })
        p.addView(Kit.text(c, "Four things worth knowing, in plain words.", 16f, 400, R.color.pa_muted).apply { gravity = Gravity.CENTER },
            Kit.lp().apply { topMargin = dp(c, 10) })

        val card = Kit.card(c, 22, 18)
        listOf(
            "Sent straight to your AI service." to " The text on your screen, and a screenshot when needed. Nowhere else.",
            "Nothing from your screen is stored." to " Once a task ends, only the task you typed and how it went are kept.",
            "It asks first." to " Anything that sends, pays, posts or deletes needs your yes. It skips banking and password apps.",
            "You can stop it any time." to " Long-press the bubble and it stops at once.",
        ).forEachIndexed { i, (b, rest) ->
            val r = Kit.hstack(c, 14).apply { gravity = Gravity.TOP; setPadding(0, dp(c, 14), 0, dp(c, 14)) }
            r.addView(Kit.text(c, "${i + 1}", 14f, 600, R.color.pa_on_navy).apply {
                gravity = Gravity.CENTER
                background = Kit.rounded(color(c, R.color.pa_navy), dpf(c, 14))
                layoutParams = LinearLayout.LayoutParams(dp(c, 28), dp(c, 28))
            })
            val col = Kit.vstack(c)
            col.addView(Kit.text(c, b.trim(), 15f, 600))
            col.addView(Kit.text(c, rest.trim(), 15f, 400, R.color.pa_muted).apply {
                setLineSpacing(dpf(c, 3), 1f); setPadding(0, dp(c, 3), 0, 0)
            })
            r.addView(col, Kit.weight())
            card.addView(r, Kit.lp())
            if (i < 3) card.addView(View(c).apply { setBackgroundColor(color(c, R.color.pa_line)) }, Kit.lp(h = 1))
        }
        p.addView(card, Kit.lp().apply { topMargin = dp(c, 30) })
        p.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
        p.addView(Kit.primary(c, "I understand", 56), Kit.lp().apply { topMargin = dp(c, 20) })
        p.addView(Kit.text(c, "Back", 16f, 400, R.color.pa_muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 14), 0, 0) }, Kit.lp())
        return FrameLayout(c).apply {
            setBackgroundColor(color(c, R.color.pa_bg))
            addView(p, FrameLayout.LayoutParams(-1, -1))
        }
    }
}
