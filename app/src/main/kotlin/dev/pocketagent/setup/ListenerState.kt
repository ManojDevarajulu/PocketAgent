package dev.pocketagent.setup

/**
 * True when [flattenedComponent] (package/class) is one entry of the system's colon-separated
 * `enabled_notification_listeners` value. Whole entries are compared, never substrings.
 */
internal fun isListenerEnabled(settingValue: String?, flattenedComponent: String): Boolean =
    !settingValue.isNullOrEmpty() && settingValue.split(':').any { it == flattenedComponent }
