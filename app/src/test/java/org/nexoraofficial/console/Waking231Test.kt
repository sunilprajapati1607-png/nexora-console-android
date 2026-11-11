package org.nexoraofficial.console

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * 2.3.1 — servers asleep (the free plan). While Render wakes a service its own front door answers a plain
 * 429 / 502 / 503 / 504 — the request never reached the service. The phone waits quietly and asks again;
 * the service's own answers (JSON, e.g. too many wrong keys) are never repeated. Nothing live is reached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Waking231Test {

    private var server: TinyServer? = null

    @After
    fun stop() {
        server?.close()
    }

    private val listing = """{"companies":[],"licences":[],"settings":{},"archived":[]}"""

    /** The first [asleep] answers are Render's own [code] in plain text; then the service answers. */
    private fun sleepy(asleep: Int, code: Int = 429): TinyServer {
        val left = AtomicInteger(asleep)
        lateinit var s: TinyServer
        s = TinyServer { _, _ ->
            if (left.getAndDecrement() > 0) {
                s.contentType = "text/plain"
                code to "Too Many Requests"
            } else {
                s.contentType = "application/json"
                200 to listing
            }
        }
        server = s
        return s
    }

    @Test
    fun aSleepingServiceIsWaitedForThenTheListComes() = runBlocking {
        val srv = sleepy(3)
        Api(srv.base, "NX-TEST-KEY", anyHost = true, wakeWaitMs = 15_000).licences()
        assertEquals("asked 3 times while asleep, then once more", 4, srv.hits.get())
    }

    @Test
    fun aServiceStillAsleepAfterTheWaitIsToldAsWakingNotAs429() = runBlocking {
        val srv = sleepy(1000, 503)
        try {
            Api(srv.base, "NX-TEST-KEY", anyHost = true, wakeWaitMs = 2_500).licences()
            fail("a service that never woke was read")
        } catch (e: ApiError) {
            assertEquals("WAKING", e.code)
            assertEquals("never the 429 of an address shut out", 0, e.status)
            assertTrue(e.message!!.contains("waking up"))
        }
    }

    @Test
    fun theServicesOwn429IsNeverAskedAgain() = runBlocking {
        val srv = TinyServer { _, _ -> 429 to """{"error":"TOO_MANY","message":"Too many wrong keys from this address."}""" }
        server = srv
        try {
            Api(srv.base, "NX-TEST-KEY", anyHost = true, wakeWaitMs = 15_000)
                .invoiceAction(JSONObject().put("action", "cancel").put("id", 4).put("reason", "Test"))
            fail("refused answer was read")
        } catch (e: ApiError) {
            assertEquals(429, e.status)
            assertEquals("TOO_MANY", e.code)
        }
        assertEquals("asked once only", 1, srv.hits.get())
    }

    @Test
    fun anActionThatMetTheFrontDoorIsSentAgainAndDone() = runBlocking {
        val srv = sleepy(2, 503)
        val r = Api(srv.base, "NX-TEST-KEY", anyHost = true, wakeWaitMs = 15_000)
            .invoiceAction(JSONObject().put("action", "cancel").put("id", 4).put("reason", "Test"))
        assertEquals(3, srv.hits.get())
        assertTrue("the action itself went out", srv.lastBody!!.contains("\"cancel\""))
        assertTrue(r.length() >= 0)
    }
}
