package org.nexoraofficial.console

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 4.72.0 — a one-page HTTP server on this computer (127.0.0.1, a free port),
 * for the tests that must see what the console actually sends: the path, the
 * query and the admin-key header. (The JDK's own HttpServer is not on the
 * Android unit-test classpath.) [answer] gets the request target and the
 * headers and returns the status and the JSON body.
 */
class TinyServer(private val answer: (target: String, headers: Map<String, String>) -> Pair<Int, String>) : AutoCloseable {

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val hits = AtomicInteger()
    @Volatile var lastTarget: String? = null
    @Volatile var lastHeaders: Map<String, String> = emptyMap()
    @Volatile var lastBody: String? = null
    /** Extra headers on every answer (a redirect's Location, say). */
    @Volatile var replyHeaders: Map<String, String> = emptyMap()

    val base: String get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true, name = "tiny-server") {
            while (!socket.isClosed) {
                val s = try { socket.accept() } catch (_: Exception) { break }
                s.use { c ->
                    val r = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
                    val line = r.readLine() ?: return@use
                    val headers = HashMap<String, String>()
                    while (true) {
                        val h = r.readLine() ?: break
                        if (h.isEmpty()) break
                        val i = h.indexOf(':')
                        if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
                    }
                    /* a POST's body is read off before answering, so closing
                       the socket never resets it under the sender */
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    if (length > 0) {
                        val buf = CharArray(length)
                        var got = 0
                        while (got < length) {
                            val n = r.read(buf, got, length - got)
                            if (n < 0) break
                            got += n
                        }
                        lastBody = String(buf, 0, got)
                    }
                    val target = line.split(" ").getOrElse(1) { "/" }
                    hits.incrementAndGet()
                    lastTarget = target
                    lastHeaders = headers
                    val (status, body) = answer(target, headers)
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val out = c.getOutputStream()
                    val extra = replyHeaders.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
                    out.write(
                        ("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n" +
                            extra + "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                    )
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    override fun close() {
        try { socket.close() } catch (_: Exception) { }
    }
}
