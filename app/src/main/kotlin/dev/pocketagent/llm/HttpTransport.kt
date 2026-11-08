package dev.pocketagent.llm

import java.io.IOException

/** A completed HTTP response. Header names are lower-case. */
data class HttpResponse(val status: Int, val headers: Map<String, String>, val body: String)

interface HttpTransport {
    /**
     * Sends a POST. Throws [IOException] for network failures and [BodyTooLargeException] for an
     * oversized body. Non-2xx statuses are returned, not thrown.
     */
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse
}

class BodyTooLargeException : IOException("Response body too large")
