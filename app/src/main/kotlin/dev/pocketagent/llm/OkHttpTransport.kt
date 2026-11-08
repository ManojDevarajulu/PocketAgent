package dev.pocketagent.llm

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * The only class that touches OkHttp. Redirects are never followed (so a key is never sent to a
 * second host), nothing is logged, and error messages never include request data.
 *
 * Read and write timeouts are disabled on purpose: a non-streaming model reply can take most of
 * a minute to start, so the call timeout is the single time budget for the whole call.
 */
class OkHttpTransport(
    callTimeoutMs: Long = 90_000,
    connectTimeoutMs: Long = 15_000,
    private val maxBodyBytes: Long = 2_000_000,
) : HttpTransport {
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val request = buildRequest(url, headers, body)
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(response.use { readResponse(it) })
                    } catch (e: IOException) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    private fun buildRequest(url: String, headers: Map<String, String>, body: String): Request {
        // OkHttp's own IllegalArgumentException messages can echo header values, so replace them.
        try {
            val builder = Request.Builder().url(url).post(body.toRequestBody(null))
            headers.forEach { (name, value) -> builder.header(name, value) }
            return builder.build()
        } catch (_: IllegalArgumentException) {
            throw IOException("Invalid request URL or headers")
        }
    }

    private fun readResponse(response: Response): HttpResponse {
        val responseHeaders = HashMap<String, String>()
        for (i in 0 until response.headers.size) {
            val name = response.headers.name(i).lowercase()
            val value = response.headers.value(i)
            responseHeaders[name] = responseHeaders[name]?.let { "$it, $value" } ?: value
        }
        return HttpResponse(response.code, responseHeaders, readCapped(response))
    }

    private fun readCapped(response: Response): String {
        val source = response.body.source()
        val buffer = Buffer()
        while (true) {
            val read = source.read(buffer, 8_192)
            if (read == -1L) break
            if (buffer.size > maxBodyBytes) throw BodyTooLargeException()
        }
        return buffer.readUtf8()
    }
}
