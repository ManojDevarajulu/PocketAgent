package dev.pocketagent.ui.kit

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ScrollView

/**
 * Shows one screen at a time with iOS-style motion: tabs cross-fade, a pushed screen slides in over the old one
 * (which drifts left behind it), and going back slides it out. Used by the app and by the debug preview.
 */
class Shell(
    private val context: Context,
    private val content: FrameLayout,
    /**
     * Where Back goes from a screen when nothing is stacked beneath it, for example after the activity was recreated
     * (null means there is nowhere: Back leaves the app). Settings and Activity go to Home, as in most apps.
     */
    private val parentOf: (String) -> String? = { null },
    private val build: (String, Nav) -> View,
) {
    enum class Transition { NONE, FADE, PUSH }

    private companion object {
        const val SETTLE_TOTAL_MS = 420L
    }

    private val tabs = listOf("home", "activity", "settings")
    private val stack = ArrayList<Pair<String, View>>()
    val nav = Nav(tab = { show(tabs[it], Transition.FADE, replace = true) }, push = { show(it, Transition.PUSH) }, back = { pop() })

    val depth: Int get() = stack.size
    val current: String? get() = stack.lastOrNull()?.first

    /** [settle] is false when the same screen is being put back (after a theme change), so it does not animate in again. */
    fun reset(name: String, kind: Transition = Transition.NONE, settle: Boolean = true) {
        stack.clear()
        content.removeAllViews()
        show(name, kind, replace = true, settle = settle)
    }

    /** How far the visible page is scrolled, to be put back with [scrollTo] after the activity is recreated. */
    fun scrollY(): Int = stack.lastOrNull()?.second?.let { scrollerIn(it)?.scrollY } ?: 0

    fun scrollTo(y: Int) {
        val view = stack.lastOrNull()?.second ?: return
        if (y > 0) {
            pendingScroll = y
            restoreScroll(view, y)
        }
    }

    /** A position waiting for the first frame. A [refresh] before then must carry it over instead of reading 0 from the unscrolled page. */
    private var pendingScroll: Int? = null

    /** Rebuilds the visible screen in place, for when the data behind it changed. */
    fun refresh() {
        val top = stack.lastOrNull() ?: return
        // A rebuilt page starts at the top. Without this, flipping a switch halfway down Settings threw the user back up.
        val scrolledTo = pendingScroll ?: scrollerIn(top.second)?.scrollY ?: 0
        val view = build(top.first, nav)
        content.removeView(top.second)
        stack[stack.lastIndex] = top.first to view
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
        if (scrolledTo > 0) restoreScroll(view, scrolledTo)
    }

    /**
     * The page's blocks fade up a few dp, one after another, like a screen settling into place. It runs before the
     * first frame so nothing flashes, and only when a screen is shown (never on [refresh], which would replay it on
     * every switch). Every block takes part, so nothing pops in while its neighbours fade; the gap between blocks
     * shrinks on a long page so the whole thing still takes about half a second.
     */
    private fun settle(view: View) {
        val body = scrollerIn(view)?.getChildAt(0) as? ViewGroup ?: return
        val blocks = (0 until body.childCount).map { body.getChildAt(it) }
        if (blocks.isEmpty()) return
        Kit.reveal(blocks, stepMs = (SETTLE_TOTAL_MS / blocks.size).coerceIn(8L, 28L), riseDp = 8, durationMs = 380)
    }

    /** Puts the new page back where the old one was, before its first frame so nothing visibly jumps. */
    private fun restoreScroll(view: View, y: Int) {
        val scroller = scrollerIn(view) ?: return
        scroller.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                scroller.viewTreeObserver.removeOnPreDrawListener(this)
                scroller.scrollTo(0, y)
                if (pendingScroll == y) pendingScroll = null
                return true
            }
        })
    }

    /** The first scrolling container in [view], which is the page body for every screen the shell builds. */
    private fun scrollerIn(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) scrollerIn(view.getChildAt(i))?.let { return it }
        return null
    }

    fun show(name: String, kind: Transition, replace: Boolean = false, settle: Boolean = true) {
        val view = build(name, nav)
        val old = stack.lastOrNull()?.second
        if (replace && stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(name to view)
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
        // Home runs its own, livelier reveal. Every other screen rises in quietly, so the whole app moves alike.
        if (settle && name != "home") settle(view)
        val w = content.width.toFloat().coerceAtLeast(1f)
        when (kind) {
            Transition.NONE -> old?.let { content.removeView(it) }
            Transition.FADE -> {
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(220).setInterpolator(Kit.ease).withEndAction {
                    old?.let { content.removeView(it) }
                }.start()
            }
            Transition.PUSH -> {
                view.elevation = Kit.dpf(context, 24)
                view.translationX = w
                view.animate().translationX(0f).setDuration(420).setInterpolator(Kit.ease).start()
                old?.animate()?.translationX(-w * 0.28f)?.setDuration(420)?.setInterpolator(Kit.ease)?.withEndAction {
                    old.visibility = View.INVISIBLE
                }?.start()
            }
        }
    }

    /** Returns false when there is nothing to go back to. */
    fun pop(): Boolean {
        if (stack.size < 2) {
            // Nothing is stacked beneath, so go to the screen this one belongs under, if it has one.
            val parent = stack.lastOrNull()?.first?.let(parentOf) ?: return false
            reset(parent, Transition.FADE)
            return true
        }
        val top = stack.removeAt(stack.lastIndex).second
        val below = stack.last().second
        val w = content.width.toFloat()
        below.visibility = View.VISIBLE
        below.animate().translationX(0f).setDuration(380).setInterpolator(Kit.ease).start()
        top.animate().translationX(w).setDuration(380).setInterpolator(Kit.ease).withEndAction {
            content.removeView(top)
        }.start()
        return true
    }
}
