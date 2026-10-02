package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.Deleted4720Test.Companion.GONE
import org.nexoraofficial.console.Deleted4720Test.Companion.LIVE
import org.nexoraofficial.console.Deleted4720Test.Companion.OM_BACK
import org.nexoraofficial.console.Deleted4720Test.Companion.RESTORED_WARNING
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.DeletedCompany
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 4.72.0 — audit #40 (console part), the screens: Companies shows the deleted
 * companies apart (All / Deleted), each with Restore as its only action — the
 * row is no way into the company — and Restore asks, sends
 * {id, action:'undelete'} and reads the list again. The company says what
 * Delete now does; a service not updated yet gets neither.
 *
 * (The Delete question itself is checked in Deleted4720Test: its type-the-name
 * field takes focus when the dialog opens under Robolectric, and the blinking
 * cursor never lets Compose go idle.)
 *
 * Made-up companies; nothing reaches the live service (a closed port, or a
 * one-page server on this computer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class DeletedScreens4720Test {

    @get:Rule val rule = createComposeRule()

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

    private fun listing(companies: List<String>, archived: List<String>?): JSONObject {
        val o = JSONObject().put("settings", JSONObject()).put("licences", JSONArray())
            .put("companies", JSONArray().apply { companies.forEach { put(JSONObject(it)) } })
        if (archived != null) o.put("archived", JSONArray().apply { archived.forEach { put(JSONObject(it)) } })
        return o
    }

    private fun screen(base: String, data: JSONObject): ConsoleViewModel {
        val vm = ConsoleViewModel(app).apply {
            baseUrl = base
            key = "NX-TEST-KEY"
            signedIn = true
            this.data = ConsoleData.from(data)
        }
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = false) { AppScaffold(vm) } }
        rule.waitForIdle()
        return vm
    }

    private fun tap(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
        rule.waitForIdle()
    }

    private fun waitFor(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (ok()) return
            if (System.currentTimeMillis() > end) fail("timed out: $what")
            Thread.sleep(10)
        }
    }

    private val whyDelete = "delete stops it now and keeps it 30 days (Restore under Companies → Deleted); " +
        "then it and everything that belongs to it are erased"

    @Test
    fun theCompanySaysWhatDeleteNowDoes() {
        screen("http://127.0.0.1:9", listing(listOf(LIVE), emptyList()))
        tap("Companies")
        tap("Shree Demo Sacks")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Delete"))
        rule.onAllNodesWithText(whyDelete).assertCountEquals(1)
    }

    @Test
    fun anOlderServiceIsNotPromisedARestore() {
        screen("http://127.0.0.1:9", listing(listOf(LIVE), null))
        tap("Companies")
        rule.onAllNodesWithText("Deleted", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("All 1").assertCountEquals(0)
        tap("Shree Demo Sacks")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Delete"))
        rule.onAllNodesWithText(whyDelete).assertCountEquals(0)
    }

    @Test
    fun anEmptyDeletedListSaysHowItWorks() {
        screen("http://127.0.0.1:9", listing(listOf(LIVE), emptyList()))
        tap("Companies")
        tap("Deleted 0")
        rule.onAllNodesWithText(
            "No deleted companies. A company you delete is kept here for ${DeletedCompany.KEEP_DAYS} days and can be " +
                "restored until then; after that it is erased for good."
        ).assertCountEquals(1)
    }

    @Test
    fun theDeletedListOffersOnlyRestore() {
        val asked = CopyOnWriteArrayList<String>()
        var sent: JSONObject? = null
        var restored = false
        val srv = TinyServer { target, _ ->
            when (target.substringBefore('?')) {
                "/admin/api/company" -> {
                    sent = JSONObject(server?.lastBody ?: "{}")
                    asked += "company:" + sent!!.optString("action")
                    restored = true
                    200 to JSONObject().put("ok", true).put("name", "Om Poly Packs").put("state", "LICENSED")
                        .put("warning", RESTORED_WARNING).toString()
                }
                "/admin/api/licences" -> {
                    asked += "licences"
                    200 to (if (restored) listing(listOf(LIVE, OM_BACK), emptyList()) else listing(listOf(LIVE), listOf(GONE))).toString()
                }
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = screen(srv.base, listing(listOf(LIVE), listOf(GONE)))

        tap("Companies")
        rule.onAllNodesWithText("All 1").assertCountEquals(1)
        rule.onAllNodesWithText("Om Poly Packs").assertCountEquals(0)

        tap("Deleted 1")
        assertEquals(CompanyView.DELETED, vm.companyView)
        rule.onAllNodesWithText("Om Poly Packs").assertCountEquals(1)
        rule.onAllNodesWithText("Shree Demo Sacks").assertCountEquals(0)
        rule.onAllNodesWithText("Deleted").assertCountEquals(1)          // its pill
        rule.onAllNodesWithText("Erased On").assertCountEquals(1)
        rule.onAllNodesWithText(Fmt.day("2026-11-01T05:40:00.000Z")).assertCountEquals(1)
        rule.onAllNodesWithText("46 synced record(s), kept until then").assertCountEquals(1)
        rule.onAllNodesWithText("Restore").assertCountEquals(1)
        /* nothing else is offered on a deleted company */
        listOf("Suspend", "Delete", "Add days", "+1 year", "Rename", "Seats", "Set PIN", "Show its machines")
            .forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }

        /* the row is not a way into the company */
        tap("Om Poly Packs")
        rule.onAllNodesWithText("What You Can Do").assertCountEquals(0)
        assertEquals("nothing asked of the service yet", 0, asked.size)

        /* Restore asks first, then sends {id, action:'undelete'} and reads the list again */
        tap("Restore")
        rule.onAllNodesWithText("Restore Om Poly Packs?").assertCountEquals(1)
        rule.onNode(hasText("Restore") and hasAnyAncestor(isDialog())).performClick()
        waitFor("restored and read again") { asked.contains("licences") && vm.companies.size == 2 }

        assertEquals(listOf("company:undelete", "licences"), asked.take(2))
        assertEquals(7, sent!!.getInt("id"))
        assertEquals("undelete", sent!!.getString("action"))
        assertEquals(CompanyView.ALL, vm.companyView)
        rule.onAllNodesWithText("Om Poly Packs").assertCountEquals(1)     // among the live ones again
        rule.onAllNodesWithText("All 2").assertCountEquals(1)
        rule.onAllNodesWithText("Deleted 0").assertCountEquals(1)
        rule.onAllNodesWithText(RESTORED_WARNING).assertCountEquals(1)
    }
}
