package dev.pocketagent.agent

import dev.pocketagent.device.NotificationPort
import dev.pocketagent.safety.CodeFinder
import kotlinx.coroutines.CancellationException

sealed interface OtpResult {
    data class Found(val code: String, val from: String, val minutesAgo: Int) : OtpResult
    data object NoneRecent : OtpResult
    data object NoAccess : OtpResult
}

/**
 * "Latest OTP": finds the newest one-time code in the notification shade. Runs on the phone with no AI, so it
 * deliberately looks at unfiltered notifications (bank SMS and blocked apps included): nothing leaves the phone.
 * A port that fails gives [OtpResult.NoneRecent] rather than crashing the task screen.
 */
class OtpQuickAction(
    private val notifications: NotificationPort,
    private val wallMs: () -> Long,
) {
    suspend fun run(): OtpResult {
        if (!notifications.hasAccess()) return OtpResult.NoAccess
        val items = try {
            notifications.recent()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OtpResult.NoneRecent
        }
        val now = wallMs()
        var best: Pair<Long, OtpResult.Found>? = null
        for (n in items) {
            val age = now - n.postedAtMs
            if (age > WINDOW_MS) continue
            val code = (listOfNotNull(n.title) + n.lines).firstNotNullOfOrNull { CodeFinder.find(it) } ?: continue
            if (best == null || n.postedAtMs > best.first) {
                val minutes = (maxOf(age, 0L) / 60_000L).toInt()
                best = n.postedAtMs to OtpResult.Found(code, n.title ?: n.appLabel ?: n.packageName, minutes)
            }
        }
        return best?.second ?: OtpResult.NoneRecent
    }

    private companion object {
        const val WINDOW_MS = 10 * 60_000L
    }
}
