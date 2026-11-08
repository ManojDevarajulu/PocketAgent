package dev.pocketagent.llm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OkHttpTransportTest {
    private val servers = mutableListOf<HttpServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop(0) }
    }

    private fun serve(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/") { ex ->
            try {
                handler(ex)
            } finally {
                ex.close()
            }
        }
        server.executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        server.start()
        servers += server
        return server
    }

    private fun HttpServer.url(path: String = "/v1") = "http://127.0.0.1:${address.port}$path"

    private fun HttpExchange.reply(status: Int, body: String, vararg headers: Pair<String, String>) {
        val bytes = body.toByteArray()
        headers.forEach { (k, v) -> responseHeaders.add(k, v) }
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.write(bytes)
    }

    @Test
    fun sendsPostWithHeadersAndBody() = runBlocking {
        var method = ""
        var auth: String? = null
        var custom: String? = null
        var received = ""
        val server = serve { ex ->
            method = ex.requestMethod
            auth = ex.requestHeaders.getFirst("Authorization")
            custom = ex.requestHeaders.getFirst("X-Custom")
            received = ex.requestBody.readBytes().decodeToString()
            ex.reply(200, "ok", "Content-Type" to "application/json")
        }
        val response = OkHttpTransport().post(
            server.url(),
            mapOf("Authorization" to "Bearer k", "X-Custom" to "v", "Content-Type" to "application/json"),
            """{"a":1}""",
        )
        assertEquals("POST", method)
        assertEquals("Bearer k", auth)
        assertEquals("v", custom)
        assertEquals("""{"a":1}""", received)
        assertEquals(200, response.status)
        assertEquals("ok", response.body)
    }

    @Test
    fun returnsStatusHeadersAndBodyOnNon2xx() = runBlocking {
        val server = serve { ex -> ex.reply(500, "boom", "Retry-After" to "7", "X-Mixed-Case" to "yes") }
        val response = OkHttpTransport().post(server.url(), emptyMap(), "{}")
        assertEquals(500, response.status)
        assertEquals("boom", response.body)
        assertEquals("7", response.headers["retry-after"])
        assertEquals("yes", response.headers["x-mixed-case"])
        assertTrue(response.headers.keys.all { it == it.lowercase() })
    }

    @Test
    fun redirectIsNotFollowedAndSecondHostNeverSeesTheKey() = runBlocking {
        val seenByB = CopyOnWriteArrayList<String?>()
        val b = serve { ex ->
            seenByB += ex.requestHeaders.getFirst("Authorization")
            ex.reply(200, "b")
        }
        val a = serve { ex -> ex.reply(302, "", "Location" to b.url("/stolen")) }
        val response = OkHttpTransport().post(a.url(), mapOf("Authorization" to "Bearer secret"), "{}")
        assertEquals(302, response.status)
        assertEquals(b.url("/stolen"), response.headers["location"])
        assertTrue(seenByB.isEmpty())
    }

    @Test
    fun bodyOverTheCapFailsWithBodyTooLarge() = runBlocking {
        val server = serve { ex -> ex.reply(200, "x".repeat(5_000)) }
        assertFailsWith<BodyTooLargeException> {
            OkHttpTransport(maxBodyBytes = 1_000).post(server.url(), emptyMap(), "{}")
        }
        Unit
    }

    @Test
    fun stalledServerTimesOutAsIOException() = runBlocking {
        val release = CountDownLatch(1)
        val server = serve { release.await(30, TimeUnit.SECONDS) }
        try {
            val start = System.nanoTime()
            assertFailsWith<InterruptedIOException> {
                OkHttpTransport(callTimeoutMs = 300).post(server.url(), emptyMap(), "{}")
            }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue(elapsedMs < 5_000, "took $elapsedMs ms")
        } finally {
            release.countDown()
        }
        Unit
    }

    @Test
    fun slowReplyWithinTheCallTimeoutSucceeds() = runBlocking {
        val server = serve { ex ->
            Thread.sleep(1_500)
            ex.reply(200, "slow")
        }
        val response = OkHttpTransport(callTimeoutMs = 10_000).post(server.url(), emptyMap(), "{}")
        assertEquals(200, response.status)
        assertEquals("slow", response.body)
    }

    @Test
    fun cancellingTheCoroutineEndsThePostPromptly() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = serve {
            arrived.countDown()
            release.await(30, TimeUnit.SECONDS)
        }
        try {
            var elapsedMs = -1L
            runBlocking {
                val job = launch(Dispatchers.Default) {
                    OkHttpTransport().post(server.url(), emptyMap(), "{}")
                }
                assertTrue(arrived.await(5, TimeUnit.SECONDS))
                val start = System.nanoTime()
                job.cancel()
                job.join()
                elapsedMs = (System.nanoTime() - start) / 1_000_000
                assertTrue(job.isCancelled)
            }
            assertTrue(elapsedMs < 1_000, "took $elapsedMs ms")
        } finally {
            release.countDown()
        }
    }

    @Test
    fun noLoggingOrKeyInExceptionMessages() = runBlocking {
        val secret = "sk-SUPERSECRET-123"
        val release = CountDownLatch(1)
        val big = serve { ex -> ex.reply(200, "x".repeat(5_000)) }
        val stalled = serve { release.await(10, TimeUnit.SECONDS) }
        val dead = serve { ex -> ex.reply(200, "") }
        val deadUrl = dead.url()
        dead.stop(0)
        val headers = mapOf("Authorization" to "Bearer $secret")
        val failures = mutableListOf<Throwable>()
        val calls = listOf<suspend () -> Unit>(
            { OkHttpTransport(maxBodyBytes = 100).post(big.url(), headers, "{}") },
            { OkHttpTransport(callTimeoutMs = 300).post(stalled.url(), headers, "{}") },
            { OkHttpTransport().post(deadUrl, headers, "{}") },
        )
        try {
            for (call in calls) {
                try {
                    call()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    failures += e
                }
            }
        } finally {
            release.countDown()
        }
        assertEquals(3, failures.size)
        failures.forEach { e ->
            var t: Throwable? = e
            while (t != null) {
                val msg = t.message.orEmpty()
                assertFalse(msg.contains(secret), "leaked in ${t::class}")
                assertFalse(msg.contains("Authorization", ignoreCase = true))
                t = t.cause
            }
        }
    }
}
