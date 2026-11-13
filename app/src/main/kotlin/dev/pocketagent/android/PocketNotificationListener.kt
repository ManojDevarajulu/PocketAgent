package dev.pocketagent.android

import android.service.notification.NotificationListenerService

/**
 * Lifecycle only: publishes itself while the system has it connected, so [AndroidNotifications] can read the shade
 * on demand. It never handles posted notifications and stores no notification content.
 */
class PocketNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        clear()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        clear()
        super.onDestroy()
    }

    /** Clears only our own reference, so a late callback from an old instance cannot wipe a newer one. */
    private fun clear() {
        if (instance === this) instance = null
    }

    companion object {
        @Volatile
        var instance: PocketNotificationListener? = null
            private set
    }
}
