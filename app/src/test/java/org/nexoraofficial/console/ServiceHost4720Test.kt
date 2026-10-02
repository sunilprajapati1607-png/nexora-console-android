package org.nexoraofficial.console

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.nexoraofficial.console.data.Prefs
import org.nexoraofficial.console.data.Sealer
import org.nexoraofficial.console.data.ServiceHost
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 4.72.0 — audit #36: the admin key goes only to Nexora's own service in a
 * release build (a test build also to a staging copy), a saved address that
 * is not Nexora's is not used, and the console leaves no picture of itself in
 * the phone's recent apps.
 *
 * Nothing reaches the live service: the one server here is on this computer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceHost4720Test {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val file get() = app.getSharedPreferences("nexora.console", 0)
    private val fake = object : Sealer {
        override fun seal(plain: String) = "sealed:$plain"
        override fun open(sealed: String) = sealed.removePrefix("sealed:")
    }

    @Before
    fun clean() {
        file.edit().clear().commit()
    }

    @Test
    fun aReleaseBuildTakesOnlyNexorasOwnService() {
        listOf(
            BuildConfig.API_BASE,
            "https://nexora-api-55jv.onrender.com/",
            "https://NEXORA-API-55JV.onrender.com",
            "  https://nexora-api-55jv.onrender.com  "
        ).forEach { assertNull(it, ServiceHost.problem(it, anyHost = false)) }

        listOf(
            /* 4.72.0 (review) — the website, a static site that never serves the admin API */
            "https://nexoraofficial.org",
            "https://www.nexoraofficial.org",
            "https://api.nexoraofficial.org",
            "http://nexora-api-55jv.onrender.com",            // not https
            "https://evil.example.com",
            "https://another-api.onrender.com",               // Render, but not Nexora's service
            "https://nexora-api-55jv.onrender.com.evil.com",
            "https://nexoraofficial.org.evil.com",
            "https://evilnexoraofficial.org",
            "https://nexora-api-55jv.onrender.com@evil.com",  // the host is evil.com
            "https://user:pw@nexora-api-55jv.onrender.com",
            "http://127.0.0.1:9",
            "ftp://nexoraofficial.org",
            "not an address",
            ""
        ).forEach { assertNotNull(it, ServiceHost.problem(it, anyHost = false)) }
    }

    @Test
    fun aTestBuildAlsoTakesAStagingCopy() {
        assertNull(ServiceHost.problem("https://staging.example.com", anyHost = true))
        assertNull(ServiceHost.problem("http://127.0.0.1:9", anyHost = true))
        assertNull(ServiceHost.problem("http://10.0.2.2:8080", anyHost = true))
        assertNotNull("plain http only to this computer", ServiceHost.problem("http://staging.example.com", anyHost = true))
        assertNotNull(ServiceHost.problem("https://user@staging.example.com", anyHost = true))
    }

    @Test
    fun aSavedAddressThatIsNotOursReadsAsTheBuiltInService() {
        file.edit().putString("baseUrl", "https://evil.example.com").commit()
        val p = Prefs(app, fake, anyHost = false)
        assertEquals(BuildConfig.API_BASE, p.baseUrl)

        p.baseUrl = "https://evil.example.com"
        assertFalse("never saved", file.contains("baseUrl"))

        p.baseUrl = "https://www.nexoraofficial.org"
        assertFalse("the website is not the service", file.contains("baseUrl"))

        p.baseUrl = "https://nexora-api-55jv.onrender.com/"
        assertEquals("https://nexora-api-55jv.onrender.com", p.baseUrl)
    }

    private var server: TinyServer? = null

    @After
    fun stop() {
        server?.close()
    }

    @Test
    fun theKeyIsNotSentToAnAddressThatIsNotOurs() = runBlocking {
        val srv = TinyServer { _, _ -> 200 to """{"companies":[],"licences":[]}""" }.also { server = it }
        val hits = srv.hits
        val base = srv.base

        try {
            Api(base, "NX-TEST-KEY", anyHost = false).licences()
            fail("a release build must refuse that address")
        } catch (e: ApiError) {
            assertTrue(e.message!!.contains("secure"))
        }
        assertEquals("refused before a connection was opened", 0, hits.get())

        /* the same address in a test build is reached — so the 0 above is the refusal, not a dead server */
        Api(base, "NX-TEST-KEY", anyHost = true).licences()
        assertEquals(1, hits.get())
    }

    /* ---- 4.72.0 (review): a redirect is never followed with the key ---- */

    private var elsewhere: TinyServer? = null

    @After
    fun stopElsewhere() {
        elsewhere?.close()
    }

    @Test
    fun aRedirectDoesNotCarryTheKeyToAnotherAddress() = runBlocking {
        val other = TinyServer { _, _ -> 200 to """{"companies":[],"licences":[]}""" }.also { elsewhere = it }
        val first = TinyServer { _, _ -> 302 to "" }.also { server = it }
        first.replyHeaders = mapOf("Location" to other.base + "/admin/api/licences")

        /* What the danger was: a plain connection follows the redirect by
           itself and sends the custom header on to the other address. */
        val raw = java.net.URL(first.base + "/admin/api/licences").openConnection() as java.net.HttpURLConnection
        raw.setRequestProperty("x-admin-key", "NX-PLAIN-KEY")
        raw.responseCode
        raw.disconnect()
        assertEquals("a plain connection did follow", 1, other.hits.get())
        assertEquals("NX-PLAIN-KEY", other.lastHeaders["x-admin-key"])

        /* The console's own calls stop at the first answer. */
        for (call in listOf<suspend () -> Unit>(
            { Api(first.base, "NX-TEST-KEY", anyHost = true).licences() },
            { Api(first.base, "NX-TEST-KEY", anyHost = true).summary(null) },
            { Api(first.base, "NX-TEST-KEY", anyHost = true).latestRelease() }
        )) {
            try {
                call()
                fail("a redirect must be refused, not followed")
            } catch (e: ApiError) {
                assertEquals(302, e.status)
                assertEquals("The service moved; check the address.", e.message)
            }
        }
        assertEquals("the console's key never reached the other address", 1, other.hits.get())
        assertEquals(4, first.hits.get())
    }

    @Test
    fun aRedirectedChangeIsNotSentOnEither() = runBlocking {
        val other = TinyServer { _, _ -> 200 to """{"ok":true}""" }.also { elsewhere = it }
        for (code in listOf(301, 307, 308)) {
            val first = TinyServer { _, _ -> code to "" }
            first.replyHeaders = mapOf("Location" to other.base + "/admin/api/company")
            try {
                Api(first.base, "NX-TEST-KEY", anyHost = true)
                    .company(org.json.JSONObject().put("id", 3).put("action", "suspend"))
                fail("$code must be refused")
            } catch (e: ApiError) {
                assertEquals(code, e.status)
                assertEquals("The service moved; check the address.", e.message)
            } finally {
                first.close()
            }
        }
        assertEquals("no change and no key reached the other address", 0, other.hits.get())
    }

    @Test
    fun noPictureOfTheConsoleInRecentApps() {
        /* Android 12 and older: FLAG_SECURE, the only way there is */
        val old = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        Recents.keepOut(old, sdk = 32)
        assertTrue(old.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

        /* Android 13 and later: the recent-apps picture is switched off instead, and the owner's own screenshots still work */
        val now = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        Recents.keepOut(now)
        assertEquals(0, now.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }
}
