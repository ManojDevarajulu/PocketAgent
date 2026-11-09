package dev.pocketagent.ui.kit

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * Shows one screen at a time with iOS-style motion: tabs cross-fade, a pushed screen slides in over the old one
 * (which drifts left behind it), and going back slides it out. Used by the app and by the debug preview.
 */
class Shell(private val context: Context, private val content: FrameLayout, private val build: (String, Nav) -> View) {
    enum class Transition { NONE, FADE, PUSH }

    private val tabs = listOf("home", "activity", "settings")
    private val stack = ArrayList<Pair<String, View>>()
    val nav = Nav(tab = { show(tabs[it], Transition.FADE, replace = true) }, push = { show(it, Transition.PUSH) }, back = { pop() })

    val depth: Int get() = stack.size
    val current: String? get() = stack.lastOrNull()?.first

    fun reset(name: String, kind: Transition = Transition.NONE) {
        stack.clear()
        content.removeAllViews()
        show(name, kind, replace = true)
    }

    /** Rebuilds the visible screen in place, for when the data behind it changed. */
    fun refresh() {
        val top = stack.lastOrNull() ?: return
        val view = build(top.first, nav)
        content.removeView(top.second)
        stack[stack.lastIndex] = top.first to view
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    fun show(name: String, kind: Transition, replace: Boolean = false) {
        val view = build(name, nav)
        val old = stack.lastOrNull()?.second
        if (replace && stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(name to view)
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
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
        if (stack.size < 2) return false
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
