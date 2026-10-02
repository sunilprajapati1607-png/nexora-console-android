package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Prefs
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 4.72.0, after review — A KEY THE SERVICE REFUSED IS NOT TRIED OVER AND OVER.
 *
 * The service shuts an address out for fifteen minutes after five wrong
 * admin keys, and the web console on the same Wi-Fi with it. So once the key
 * is refused (it was changed on the service), the background watch stops
 * (Watch4720Test) and so does the once-a-minute read of an open company's
 * people; a good sign-in starts the watch again.
 *
 * Nothing reaches the live service: the one server here is on this computer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyRefused4720Test {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var server: TinyServer? = null

    @Before
    fun clean() {
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
    }

    @After
    fun stop() {
        server?.close()
    }

    /* The view model's work comes back to the main thread; run it until [ok]. */
    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (ok()) return
            if (System.currentTimeMillis() > end) fail("timed out: $what")
            Thread.sleep(10)
        }
    }

    @Test
    fun aGoodSignInStartsTheWatchAgain() {
        val srv = TinyServer { target, _ ->
            if (target.substringBefore('?') == "/admin/api/licences")
                200 to """{"companies":[],"licences":[],"settings":{}}"""
            else 404 to """{"error":"NOT_FOUND"}"""
        }.also { server = it }

        Prefs(app).keyRejected = true                 // the watch stopped on the old key
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-NEW-KEY"
        }
        vm.load()
        until("signed in") { vm.signedIn && !vm.busy }
        assertFalse("the watch may ask again", Prefs(app).keyRejected)
    }

    @Test
    fun aRefusedSignInLeavesTheWatchStopped() {
        val srv = TinyServer { _, _ -> 401 to """{"error":"UNAUTHORISED"}""" }.also { server = it }
        Prefs(app).keyRejected = true
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-OLD-KEY"
        }
        vm.load()
        until("answered") { srv.hits.get() == 1 && !vm.busy }
        assertFalse(vm.signedIn)
        assertEquals("That admin key was not accepted.", vm.gateError)
        assertTrue(Prefs(app).keyRejected)
    }

    @Test
    fun theMinuteReadOfPeopleStopsAfterARefusedKey() {
        val srv = TinyServer { _, _ -> 401 to """{"error":"UNAUTHORISED"}""" }.also { server = it }
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-OLD-KEY"
            signedIn = true
        }

        vm.loadPeople(3, quiet = true)
        until("the first quiet read is answered") { srv.hits.get() == 1 && !vm.peopleBusy }

        repeat(5) { vm.loadPeople(3, quiet = true) }   // five more minutes on the company page
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("no more quiet reads with a refused key", 1, srv.hits.get())
        assertNull("a quiet read says nothing", vm.peopleError)

        /* a press of Refresh still asks, and says why it failed */
        vm.loadPeople(3)
        until("the refresh is answered") { srv.hits.get() == 2 && !vm.peopleBusy }
        assertEquals("That admin key was not accepted.", vm.peopleError)
    }
}
