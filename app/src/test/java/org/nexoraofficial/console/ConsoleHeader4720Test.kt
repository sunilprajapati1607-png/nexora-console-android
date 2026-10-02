package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Api
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 4.72.0 — audit #39 (console part): every call the console makes says
 * `x-console: android`, so each event the service logs during it carries
 * app 'android' (admin.js consoleCaller) — the owner's audit trail says which
 * console acted, not a guess from the user-agent.
 *
 * Nothing reaches the live service: the one server here is on this computer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsoleHeader4720Test {

    private var server: TinyServer? = null

    @After
    fun stop() {
        server?.close()
    }

    /** Every request the stand-in service heard: its path and its x-console header. */
    private fun service(heard: MutableList<Pair<String, String?>>) = TinyServer { target, headers ->
        val path = target.substringBefore('?')
        heard += path to headers["x-console"]
        when (path) {
            "/admin/api/summary" -> 200 to """{"at":"2026-10-02T03:15:00.000Z","companies":1,"licences":1,"newEnquiries":0,"newFeedback":0,"newRegistrations":0}"""
            "/admin/api/licences" -> 200 to """{"companies":[],"licences":[],"settings":{},"archived":[]}"""
            else -> 200 to """{"ok":true}"""
        }
    }.also { server = it }

    @Test
    fun everyCallSaysItIsTheAndroidConsole() = runBlocking {
        val heard = CopyOnWriteArrayList<Pair<String, String?>>()
        val srv = service(heard)
        val api = Api(srv.base, "NX-TEST-KEY")

        api.licences()
        api.summary(null)
        api.inquiries()
        api.feedback()
        api.latestRelease()
        api.broadcasts()
        api.feedbackShot(1)
        api.people(3)
        api.company(JSONObject().put("id", 7).put("action", "undelete"))
        api.licence(JSONObject().put("deviceId", "c0ffee01").put("action", "resetusage"))
        api.settings(JSONObject().put("trialDays", 7))
        api.gst(JSONObject().put("action", "gstverify").put("id", 3))
        api.inquiry(JSONObject().put("action", "state").put("id", 1).put("state", "CONTACTED"))
        api.feedbackAction(JSONObject().put("action", "state").put("id", 1).put("state", "SEEN"))
        api.broadcast(JSONObject().put("action", "send").put("body", "Hello"))

        assertEquals(15, heard.size)
        heard.forEach { (path, said) -> assertEquals("x-console on $path", "android", said) }
        assertEquals("the key is still sent as before", "NX-TEST-KEY", srv.lastHeaders["x-admin-key"])
        assertEquals("android", Api.CONSOLE_NAME)
    }

    @Test
    fun theViewModelsOwnCallsSayItToo() {
        val heard = CopyOnWriteArrayList<Pair<String, String?>>()
        val srv = service(heard)
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-TEST-KEY"
        }

        /* a sign-in reads the listing, then the enquiries, the reports, the messages and the update */
        vm.load()
        val end = System.currentTimeMillis() + 15_000
        while (!(vm.signedIn && !vm.busy && heard.size >= 5)) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > end) fail("timed out: signed in")
            Thread.sleep(10)
        }
        assertTrue(heard.any { it.first == "/admin/api/licences" })
        heard.forEach { (path, said) -> assertEquals("x-console on $path", "android", said) }
    }
}
