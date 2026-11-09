package dev.pocketagent.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager

/** Adds and removes views as accessibility overlays; main thread only. */
internal object Overlays {
    const val TOUCH_SLOP_PX = 12
    const val LONG_PRESS_MS = 600L

    fun params(width: Int, height: Int, gravity: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).also { it.gravity = gravity }

    private fun manager(context: Context): WindowManager = context.getSystemService(WindowManager::class.java)

    /** Returns true if the view was added. */
    fun add(context: Context, view: View, params: WindowManager.LayoutParams): Boolean =
        try {
            manager(context).addView(view, params)
            true
        } catch (e: RuntimeException) {
            false
        }

    fun update(context: Context, view: View, params: WindowManager.LayoutParams) {
        try {
            manager(context).updateViewLayout(view, params)
        } catch (e: RuntimeException) {
            // The view is not attached, so there is nothing to update.
        }
    }

    fun remove(context: Context, view: View) {
        try {
            manager(context).removeViewImmediate(view)
        } catch (e: RuntimeException) {
            // Already removed.
        }
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
