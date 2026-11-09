package dev.pocketagent.android

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Lifecycle only (S3 spec §2 and §7): creates and closes [AgentSession], forwards events to its settler, and
 * switches event subscription on and off. Service lifetime equals agent lifetime.
 */
class PocketAccessibilityService : AccessibilityService() {
    private var session: AgentSession? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        session?.close()
        subscribeToTaskEvents(false)
        session = AgentSession(this, SetupStores.repository(this), SetupStores.settings(this), SetupStores.history(this)).also { it.showBubble() }
    }

    /** Events arrive only while a task runs; each one only restarts the settle timer. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        session?.onEvent()
    }

    override fun onInterrupt() {
        // No spoken or haptic feedback to stop.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        closeSession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        closeSession()
        super.onDestroy()
    }

    /** The four task-time event types while [on], none otherwise (spec §3). Main thread. */
    internal fun subscribeToTaskEvents(on: Boolean) {
        val info = serviceInfo ?: return
        val types = if (on) TASK_EVENT_TYPES else 0
        if (info.eventTypes == types) return
        info.eventTypes = types
        serviceInfo = info
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private companion object {
        const val TASK_EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
    }
}
