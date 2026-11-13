package dev.pocketagent.android

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.StatusBarNotification
import dev.pocketagent.device.ActionResult
import dev.pocketagent.device.NotificationPort
import dev.pocketagent.device.PhoneNotification
import dev.pocketagent.device.messageLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the shade through [PocketNotificationListener] only when asked, and answers through a notification's own
 * reply action. Nothing is cached: every call reads the live notifications, and no call ever throws.
 */
object AndroidNotifications : NotificationPort {
    override fun hasAccess(): Boolean = PocketNotificationListener.instance != null

    override suspend fun recent(): List<PhoneNotification> = withContext(Dispatchers.Default) {
        try {
            val listener = PocketNotificationListener.instance ?: return@withContext emptyList()
            val labels = HashMap<String, String?>()
            live(listener).map { sbn ->
                toPhoneNotification(sbn, labels.getOrPut(sbn.packageName) { appLabel(listener.packageManager, sbn.packageName) })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // SecurityException when the listener is not bound yet, or a binder or parcel failure: nothing to read.
            emptyList()
        }
    }

    override suspend fun reply(key: String, text: String): ActionResult = withContext(Dispatchers.Default) {
        try {
            val listener = PocketNotificationListener.instance ?: return@withContext ActionResult.Failed(GONE)
            val sbn = live(listener).firstOrNull { it.key == key } ?: return@withContext ActionResult.Failed(GONE)
            val action = replyAction(sbn.notification) ?: return@withContext ActionResult.Failed(NOT_REPLYABLE)
            val inputs = action.remoteInputs
            val target = action.actionIntent
            if (inputs.isNullOrEmpty() || target == null) return@withContext ActionResult.Failed(NOT_REPLYABLE)
            val intent = Intent()
            RemoteInput.addResultsToIntent(inputs, intent, Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } })
            try {
                target.send(listener, 0, intent)
            } catch (e: PendingIntent.CanceledException) {
                return@withContext ActionResult.Failed(NOT_ACCEPTED)
            } catch (e: RuntimeException) {
                // Failed inside the send itself, so the reply may or may not have reached the app.
                return@withContext ActionResult.Failed(UNCONFIRMED)
            }
            ActionResult.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Before anything was sent: the listener was unbound or a binder or parcel call failed.
            ActionResult.Failed(NOT_SENT)
        }
    }

    /** May be null when the binder call fails inside the platform; throws SecurityException when not connected. */
    private fun live(listener: PocketNotificationListener): List<StatusBarNotification> =
        listener.activeNotifications?.filterNotNull().orEmpty()

    /** The one rule for both [PhoneNotification.canReply] and [reply]: the first action that takes typed input. */
    private fun replyAction(n: Notification): Notification.Action? =
        n.actions?.firstOrNull { it != null && !it.remoteInputs.isNullOrEmpty() }

    private fun toPhoneNotification(sbn: StatusBarNotification, appLabel: String?): PhoneNotification {
        val n = sbn.notification
        val extras = n.extras ?: Bundle.EMPTY
        return PhoneNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            lines = lines(extras),
            postedAtMs = sbn.postTime,
            canReply = replyAction(n) != null,
            ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    /**
     * MessagingStyle messages as "sender: text", else the text. The platform gives the phone owner's own messages no
     * sender, and some apps give them the owner's Person from EXTRA_MESSAGING_PERSON; both become "Me: text".
     */
    private fun lines(extras: Bundle): List<String> {
        @Suppress("DEPRECATION") // the typed overload needs API 33; minSdk is 30
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        @Suppress("DEPRECATION") // the typed overload needs API 33; minSdk is 30
        val me = extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
            .mapNotNull { m ->
                val text = m.text?.toString() ?: return@mapNotNull null
                val person = m.senderPerson
                messageLine(person?.name?.toString(), fromOwner = person == null || isSamePerson(person, me), text = text)
            }
        if (messages.isNotEmpty()) return messages
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val inbox = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().mapNotNull { it?.toString() }
        return (listOfNotNull(body) + inbox).filter { it.isNotBlank() }
    }

    /** Same key when both have one, else the same name when neither has a key. */
    private fun isSamePerson(a: Person, me: Person?): Boolean {
        if (me == null) return false
        val (ka, km) = a.key to me.key
        if (ka != null && km != null) return ka == km
        if (ka != null || km != null) return false
        val name = me.name?.toString()
        return !name.isNullOrBlank() && name == a.name?.toString()
    }

    private fun appLabel(pm: PackageManager, packageName: String): String? =
        try {
            @Suppress("DEPRECATION") // the flags-object overload needs API 33; minSdk is 30
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Not visible to this app under package visibility rules, or uninstalled since posting.
            null
        }

    private const val GONE = "that notification is gone; open the app to reply"
    private const val NOT_REPLYABLE = "this notification can't be replied to"
    private const val NOT_ACCEPTED = "the app did not accept the reply"
    private const val NOT_SENT = "could not send the reply"
    private const val UNCONFIRMED = "the app did not confirm the reply (it may or may not have been sent); tell the user instead of retrying"
}
