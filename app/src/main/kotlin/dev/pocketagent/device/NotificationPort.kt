package dev.pocketagent.device

/** The phone's notifications, read and answered without opening any app. */
interface NotificationPort {
    fun hasAccess(): Boolean
    suspend fun recent(): List<PhoneNotification>
    suspend fun reply(key: String, text: String): ActionResult
}
