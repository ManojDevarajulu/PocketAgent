package dev.pocketagent.llm

import java.io.IOException
import kotlinx.coroutines.delay

sealed interface HttpOutcome {
    data class Success(val body: String) : HttpOutcome
    data class Failure(val kind: FailureKind, val message: String, val status: Int?) : HttpOutcome
}

/**
 * Posts once, retrying transient failures twice (after 1 s, then 3 s). Failure messages are fixed phrases plus
 * the status code: the provider's body and all headers are never copied, because they can echo the API key.
 * Only [IOException] is caught, so cancellation always propagates.
 */
class HttpCaller(private val transport: HttpTransport) {
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpOutcome {
        var attempt = 0
        while (true) {
            var retryAfterMs: Long? = null
            val failure: HttpOutcome.Failure = try {
                val response = transport.post(url, headers, body)
                if (response.status in 200..299) return HttpOutcome.Success(response.body)
                retryAfterMs = parseRetryAfterMs(response.headers["retry-after"])
                mapStatus(response.status)
            } catch (e: BodyTooLargeException) {
                return HttpOutcome.Failure(FailureKind.SERVER, "The provider's reply was too large", null)
            } catch (e: IOException) {
                HttpOutcome.Failure(FailureKind.NETWORK, "Could not reach the provider", null)
            }

            if (!isRetryable(failure.kind) || attempt >= BACKOFF_MS.size) return failure
            val wait = retryAfterMs ?: BACKOFF_MS[attempt]
            if (wait > MAX_RETRY_AFTER_MS) return failure
            delay(wait)
            attempt++
        }
    }

    private fun isRetryable(kind: FailureKind) =
        kind == FailureKind.NETWORK || kind == FailureKind.RATE_LIMIT || kind == FailureKind.SERVER

    private fun mapStatus(status: Int): HttpOutcome.Failure = when {
        status == 401 || status == 403 ->
            HttpOutcome.Failure(FailureKind.AUTH, "The provider rejected the API key ($status)", status)
        status == 429 ->
            HttpOutcome.Failure(FailureKind.RATE_LIMIT, "The provider is rate limiting requests ($status)", status)
        status >= 500 ->
            HttpOutcome.Failure(FailureKind.SERVER, "The provider had a server error ($status)", status)
        else ->
            HttpOutcome.Failure(FailureKind.OTHER, "The provider returned an unexpected response ($status)", status)
    }

    /** Whole non-negative seconds only; anything else is null. Absurdly large values count as over the cap. */
    private fun parseRetryAfterMs(value: String?): Long? {
        val v = value?.trim() ?: return null
        if (v.isEmpty() || !v.all { it in '0'..'9' }) return null
        val seconds = v.toLongOrNull() ?: return Long.MAX_VALUE
        return if (seconds > MAX_RETRY_AFTER_MS / 1000) Long.MAX_VALUE else seconds * 1000
    }

    private companion object {
        val BACKOFF_MS = longArrayOf(1_000, 3_000)
        const val MAX_RETRY_AFTER_MS = 10_000L
    }
}
