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
        running = this
        session = AgentSession(this, SetupStores.repository(this), SetupStores.settings(this), SetupStores.history(this)).also { it.showBubble() }
    }

    /** Events arrive only while a task runs; each one only restarts the settle timer. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = session ?: return
        current.onEvent()
        if (event != null) current.onRecordingEvent(event)
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

    private var taskEvents = false
    private var recordingEvents = false

    /** The four task-time event types while [on], none otherwise (spec §3). Main thread. */
    internal fun subscribeToTaskEvents(on: Boolean) {
        taskEvents = on
        applyEventTypes()
    }

    /** Only while a routine is recorded: clicks and screen changes, nothing else, and none once recording stops. */
    internal fun subscribeToRecordingEvents(on: Boolean) {
        recordingEvents = on
        applyEventTypes()
    }

    /** Both users of events share one subscription, so one switching off never cancels the other. */
    private fun applyEventTypes() {
        val info = serviceInfo ?: return
        var types = 0
        if (taskEvents) types = types or TASK_EVENT_TYPES
        if (recordingEvents) types = types or RECORDING_EVENT_TYPES
        if (info.eventTypes == types) return
        info.eventTypes = types
        serviceInfo = info
    }

    /** Quick Settings tile entry: the same as a bubble tap, after the shade is out of the way. */
    internal fun startFromTile() {
        session?.startFromTile()
    }

    private fun closeSession() {
        if (running === this) running = null
        session?.close()
        session = null
    }

    companion object {
        /** The connected service, for the Quick Settings tile. Main thread only. */
        @Volatile
        internal var running: PocketAccessibilityService? = null

        private const val RECORDING_EVENT_TYPES = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        private const val TASK_EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
    }
}
