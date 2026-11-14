package dev.pocketagent.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest

/**
 * A tiny HTTP server for one endpoint, POST /mcp, that hands JSON-RPC bodies to [handler].
 *
 * It is strict on purpose, because it listens on the local network:
 *  - only loopback, private (192.168.x.x, 10.x.x.x, 172.16-31.x.x) and link-local callers are served;
 *  - every request needs the pairing token as a Bearer token, compared in constant time;
 *  - five wrong tokens lock everyone out for a minute;
 *  - a request with an Origin header is refused, so a web page in a browser can never drive the phone;
 *  - bodies are at most [MAX_BODY] bytes and headers at most [MAX_HEADER] bytes, with read timeouts.
 * It makes no outgoing connection.
 */
class McpHttpServer(
    private val port: Int,
    private val token: () -> String,
    private val handler: McpHandler,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val runLimitMs: Long = RUN_LIMIT_MS,
) {
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null

    private val active = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var failures = 0

    @Volatile
    private var lockedUntil = 0L

    /** The port actually bound; differs from the requested one only when that was 0. */
    val boundPort: Int get() = server?.localPort ?: -1

    /** True once listening. False when the port is taken or the network refuses. */
    fun start(): Boolean {
        if (server != null) return true
        val socket = try {
            ServerSocket(port, BACKLOG).apply { reuseAddress = true }
        } catch (e: IOException) {
            return false
        }
        server = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                if (active.incrementAndGet() > MAX_CONNECTIONS) {
                    active.decrementAndGet()
                    try { client.close() } catch (e: IOException) { /* already gone */ }
                    continue
                }
                scope.launch(Dispatchers.IO) {
                    try { serve(client) } finally { active.decrementAndGet() }
                }
            }
        }
        return true
    }

    fun stop() {
        try {
            server?.close()
        } catch (e: IOException) {
            // Already closed.
        }
        server = null
        acceptJob?.cancel()
        acceptJob = null
    }

    private suspend fun serve(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                val out = socket.getOutputStream()
                if (!allowedCaller(socket.inetAddress)) return
                val input = BufferedInputStream(socket.getInputStream())
                val request = readRequest(input) ?: return respond(out, 400, "Bad Request", null)
                val now = nowMs()
                if (now < lockedUntil) return respond(out, 429, "Too Many Requests", null)
                if (request.headers.containsKey("origin")) return respond(out, 403, "Forbidden", null)
                if (request.path != PATH) return respond(out, 404, "Not Found", null)
                if (request.method != "POST") return respond(out, 405, "Method Not Allowed", null, "Allow: POST\r\n")
                if (!authorised(request.headers["authorization"])) {
                    if (++failures >= MAX_FAILURES) {
                        lockedUntil = now + LOCKOUT_MS
                        failures = 0
                    }
                    return respond(out, 401, "Unauthorized", null, "WWW-Authenticate: Bearer\r\n")
                }
                failures = 0
                if (request.body == null) return respond(out, 400, "Bad Request", null)
                // A task can take minutes; the socket is not read again, so only the run limit applies.
                val text = request.body.toString(Charsets.UTF_8)
                // A notification has no reply: handle returns null, which is answered with an empty 202.
                val reply = withTimeoutOrNull(runLimitMs) { handler.handle(text) ?: "" }
                    ?: return respond(out, 504, "Gateway Timeout", null)
                respond(out, if (reply.isEmpty()) 202 else 200, if (reply.isEmpty()) "Accepted" else "OK", reply.ifEmpty { null })
            } catch (e: IOException) {
                // The caller went away or was too slow; nothing to report.
            }
        }
    }

    private fun authorised(header: String?): Boolean {
        val expected = token()
        if (expected.isEmpty() || header == null || !header.startsWith("Bearer ")) return false
        return MessageDigest.isEqual(header.removePrefix("Bearer ").trim().toByteArray(), expected.toByteArray())
    }

    private fun allowedCaller(address: InetAddress): Boolean =
        address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray?)

    private fun readRequest(input: InputStream): Request? {
        val headerBytes = StringBuilder()
        var last4 = 0
        // Each read has its own timeout, so a sender dripping one byte at a time needs an overall limit too.
        val deadline = System.nanoTime() + READ_DEADLINE_MS * 1_000_000L
        while (true) {
            if (System.nanoTime() > deadline) return null
            val b = try {
                input.read()
            } catch (e: SocketTimeoutException) {
                return null
            }
            if (b < 0) return null
            headerBytes.append(b.toChar())
            if (headerBytes.length > MAX_HEADER) return null
            last4 = (last4 shl 8) or b
            if (last4 == END_OF_HEADERS) break
        }
        val lines = headerBytes.toString().split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != 3) return null
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) return null
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            if (System.nanoTime() > deadline) return null
            val n = try {
                input.read(body, read, length - read)
            } catch (e: SocketTimeoutException) {
                return null
            }
            if (n < 0) return null
            read += n
        }
        return Request(parts[0], parts[1].substringBefore('?'), headers, body)
    }

    private fun respond(out: OutputStream, code: Int, reason: String, body: String?, extra: String = "") {
        val bytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val head = "HTTP/1.1 $code $reason\r\n" + extra +
            (if (body != null) "Content-Type: application/json\r\n" else "") +
            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        const val PATH = "/mcp"
        const val DEFAULT_PORT = 8765
        const val MAX_BODY = 16 * 1024
        const val MAX_HEADER = 8 * 1024
        const val MAX_FAILURES = 5
        const val LOCKOUT_MS = 60_000L
        const val RUN_LIMIT_MS = 5 * 60_000L
        private const val READ_TIMEOUT_MS = 10_000
        private const val READ_DEADLINE_MS = 15_000L
        private const val MAX_CONNECTIONS = 8
        private const val BACKLOG = 8
        private const val END_OF_HEADERS = 0x0D0A0D0A
    }
}
