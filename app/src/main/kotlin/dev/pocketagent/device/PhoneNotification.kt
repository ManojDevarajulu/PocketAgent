package dev.pocketagent.device

/** One notification from the phone's shade, as the listener reports it. No Android types so it can be tested on the JVM. */
data class PhoneNotification(
    val key: String,
    val packageName: String,
    val appLabel: String?,
    val title: String?,
    val conversation: String?,
    val lines: List<String>,
    val postedAtMs: Long,
    val canReply: Boolean,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
)
