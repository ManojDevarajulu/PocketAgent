package dev.pocketagent.device

/** The phone can no longer be read or driven (locked, service turned off). [reason] is shown to the user. */
class DeviceUnavailable(val reason: String) : Exception(reason)
