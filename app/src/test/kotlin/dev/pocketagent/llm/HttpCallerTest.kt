package dev.pocketagent.llm

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HttpCallerTest {
    /** Each scripted step is an [HttpResponse] or a [Throwable]. The last step repeats if the script runs out. */
    private class FakeTransport(private val script: List<Any>) : HttpTransport {
        val calls = mutableListOf<Triple<String, Map<String, String>, String>>()

        override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
            calls += Triple(url, headers, body)
            val step = script[minOf(calls.size - 1, script.size - 1)]
            if (step is Throwable) throw step
            return step as HttpResponse
        }
    }

    private fun resp(status: Int, body: String = "", headers: Map<String, String> = emptyMap()) =
        HttpResponse(status, headers, body)

    private fun failure(outcome: HttpOutcome) = assertIs<HttpOutcome.Failure>(outcome)

    @Test
    fun status200IsSuccess() = runTest {
        val t = FakeTransport(listOf(resp(200, "hello")))
        val out = HttpCaller(t).post("https://x/y", mapOf("a" to "b"), "{}")
        assertEquals(HttpOutcome.Success("hello"), out)
        assertEquals(1, t.calls.size)
        assertEquals(Triple("https://x/y", mapOf("a" to "b"), "{}"), t.calls[0])
    }

    @Test
    fun statusMapping() = runTest {
        val expected = mapOf(
            401 to FailureKind.AUTH, 403 to FailureKind.AUTH,
            429 to FailureKind.RATE_LIMIT,
            500 to FailureKind.SERVER, 502 to FailureKind.SERVER, 529 to FailureKind.SERVER,
            400 to FailureKind.OTHER, 404 to FailureKind.OTHER, 413 to FailureKind.OTHER,
            422 to FailureKind.OTHER, 302 to FailureKind.OTHER,
        )
        for ((status, kind) in expected) {
            val f = failure(HttpCaller(FakeTransport(listOf(resp(status)))).post("u", emptyMap(), "b"))
            assertEquals(kind, f.kind, "status $status")
            assertEquals(status, f.status, "status $status")
            assertTrue(f.message.contains(status.toString()), "message for $status: ${f.message}")
        }
    }

    @Test
    fun retriesTwiceWithBackoffThenFails() = runTest {
        val t = FakeTransport(listOf(resp(500)))
        val f = failure(HttpCaller(t).post("u", emptyMap(), "b"))
        assertEquals(FailureKind.SERVER, f.kind)
        assertEquals(3, t.calls.size)
        assertEquals(4_000L, testScheduler.currentTime)
    }

    @Test
    fun succeedsOnTheSecondTry() = runTest {
        val t = FakeTransport(listOf(resp(503), resp(200, "ok")))
        assertEquals(HttpOutcome.Success("ok"), HttpCaller(t).post("u", emptyMap(), "b"))
        assertEquals(2, t.calls.size)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun doesNotRetryAuthOrOtherClientErrors() = runTest {
        for (status in listOf(401, 403, 400, 404, 413, 422, 302)) {
            val t = FakeTransport(listOf(resp(status)))
            HttpCaller(t).post("u", emptyMap(), "b")
            assertEquals(1, t.calls.size, "status $status")
        }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun retryAfterIsHonouredUpTo10Seconds() = runTest {
        val t = FakeTransport(listOf(resp(429, headers = mapOf("retry-after" to "5")), resp(200, "ok")))
        assertEquals(HttpOutcome.Success("ok"), HttpCaller(t).post("u", emptyMap(), "b"))
        assertEquals(5_000L, testScheduler.currentTime)

        val t2 = FakeTransport(listOf(resp(429, headers = mapOf("retry-after" to "10")), resp(200, "ok")))
        val start = testScheduler.currentTime
        HttpCaller(t2).post("u", emptyMap(), "b")
        assertEquals(10_000L, testScheduler.currentTime - start)
    }

    @Test
    fun retryAfterThatIsNotWholeSecondsIsIgnored() = runTest {
        for (value in listOf("Wed, 21 Oct 2026 07:28:00 GMT", "abc", "-3", "1.5", "")) {
            val t = FakeTransport(listOf(resp(429, headers = mapOf("retry-after" to value)), resp(200, "ok")))
            val start = testScheduler.currentTime
            assertEquals(HttpOutcome.Success("ok"), HttpCaller(t).post("u", emptyMap(), "b"))
            assertEquals(1_000L, testScheduler.currentTime - start, "value [$value]")
        }
    }

    @Test
    fun retryAfterOver10SecondsStopsWithoutWaiting() = runTest {
        for (value in listOf("11", "99999999999999999999")) {
            val t = FakeTransport(listOf(resp(429, headers = mapOf("retry-after" to value)), resp(200, "ok")))
            val f = failure(HttpCaller(t).post("u", emptyMap(), "b"))
            assertEquals(FailureKind.RATE_LIMIT, f.kind)
            assertEquals(1, t.calls.size)
            assertEquals(0L, testScheduler.currentTime)
        }
    }

    @Test
    fun ioErrorIsNetworkAndRetried() = runTest {
        val t = FakeTransport(listOf(IOException("boom")))
        val f = failure(HttpCaller(t).post("u", emptyMap(), "b"))
        assertEquals(FailureKind.NETWORK, f.kind)
        assertEquals(null, f.status)
        assertEquals(3, t.calls.size)
        assertEquals(4_000L, testScheduler.currentTime)

        val t2 = FakeTransport(listOf(IOException("boom"), resp(200, "ok")))
        assertEquals(HttpOutcome.Success("ok"), HttpCaller(t2).post("u", emptyMap(), "b"))
    }

    @Test
    fun bodyTooLargeIsServerAndNotRetried() = runTest {
        val t = FakeTransport(listOf(BodyTooLargeException(), resp(200, "ok")))
        val f = failure(HttpCaller(t).post("u", emptyMap(), "b"))
        assertEquals(FailureKind.SERVER, f.kind)
        assertEquals(1, t.calls.size)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun cancellationDuringBackoffStopsAtOnce() = runTest {
        val t = FakeTransport(listOf(resp(500), resp(200, "ok")))
        var cancelled = false
        val job = launch {
            try {
                HttpCaller(t).post("u", emptyMap(), "b")
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        runCurrent()
        advanceTimeBy(500)
        assertEquals(1, t.calls.size)
        job.cancel()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(cancelled)
        assertTrue(job.isCancelled)
        assertEquals(1, t.calls.size)
    }

    @Test
    fun messagesNeverContainTheKeyOrTheProviderBody() = runTest {
        val key = "sk-secret-key-123"
        val echo = "provider echoed $key and Bearer $key"
        val scripts = listOf(
            listOf<Any>(resp(401, echo, mapOf("x-echo" to key))),
            listOf<Any>(resp(429, echo, mapOf("retry-after" to key))),
            listOf<Any>(resp(500, echo)),
            listOf<Any>(resp(400, echo)),
            listOf<Any>(IOException("connect to $key failed")),
            listOf<Any>(BodyTooLargeException()),
        )
        for (script in scripts) {
            val f = failure(HttpCaller(FakeTransport(script)).post("u", mapOf("Authorization" to "Bearer $key"), "b"))
            assertFalse(f.message.contains(key), f.message)
            assertFalse(f.message.contains("echoed"), f.message)
            assertTrue(f.message.isNotBlank())
        }
    }
}
