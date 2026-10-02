package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.ui.deleteQuestionBody
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 4.72.0 — audit #40 (console part): DELETE NOW ARCHIVES FOR 30 DAYS.
 *
 * The service (server step 3) keeps a deleted company 30 days, lists it apart
 * from the live ones (`archived` in GET /admin/api/licences), puts it back on
 * {id, action:'undelete'} and refuses every other action on it. So the console
 * says so before (the Delete question) and after (the answer's warning), and
 * Restore sends exactly {id, action:'undelete'} and reads the list again. A
 * service not updated yet, which still erases at once, is promised nothing.
 * (What the screens show: DeletedScreens4720Test.)
 *
 * Made-up companies; nothing reaches the live service (a one-page server on
 * this computer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Deleted4720Test {

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

    /** A listing; [archived] null is a service not updated yet (no `archived` at all). */
    private fun listing(companies: List<String>, archived: List<String>?): JSONObject {
        val o = JSONObject().put("settings", JSONObject()).put("licences", JSONArray())
            .put("companies", JSONArray().apply { companies.forEach { put(JSONObject(it)) } })
        if (archived != null) o.put("archived", JSONArray().apply { archived.forEach { put(JSONObject(it)) } })
        return o
    }

    private fun vm(base: String, data: JSONObject) = ConsoleViewModel(app).apply {
        baseUrl = base
        key = "NX-TEST-KEY"
        signedIn = true
        this.data = ConsoleData.from(data)
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

    /** A stand-in service: what it was asked, in order ("licences", "company:delete" …), and the last change sent. */
    private class Heard {
        val asked = CopyOnWriteArrayList<String>()
        @Volatile var sent: JSONObject? = null
        fun after(first: String, then: String): Boolean {
            val i = asked.indexOf(first)
            return i >= 0 && asked.subList(i + 1, asked.size).contains(then)
        }
    }

    private fun service(heard: Heard, onAction: (JSONObject) -> String, listingNow: () -> JSONObject) =
        TinyServer { target, _ ->
            when (target.substringBefore('?')) {
                "/admin/api/company" -> {
                    val body = JSONObject(server?.lastBody ?: "{}")
                    heard.asked += "company:" + body.optString("action")
                    heard.sent = body
                    200 to onAction(body)
                }
                "/admin/api/licences" -> {
                    heard.asked += "licences"
                    200 to listingNow().toString()
                }
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }

    private fun msgOf(vm: ConsoleViewModel): Msg {
        val m = vm.msg
        assertNotNull("a message was said", m)
        return m!!
    }

    /* ---- the listing ---- */

    @Test
    fun theListingCarriesTheDeletedApart() {
        val d = ConsoleData.from(listing(listOf(LIVE), listOf(GONE)))
        assertTrue(d.keepsDeleted)
        assertEquals(listOf("Shree Demo Sacks"), d.companies.map { it.name })
        val x = d.archived.single()
        assertEquals(7, x.id)
        assertEquals("Om Poly Packs", x.name)
        assertEquals("2026-10-02T05:40:00.000Z", x.deletedAt)
        assertEquals("LICENSED", x.deletedState)
        assertEquals("2026-11-01T05:40:00.000Z", x.purgeAt)
        assertEquals(30, x.daysToPurge)
        assertEquals(2, x.machines)
        assertEquals(1, x.people)
        assertEquals(46, x.records)
        assertTrue("found by GSTIN", x.matches("24aaaco"))
        assertTrue("found by login id", x.matches("OMPOLY"))
        assertFalse(x.matches("shree"))

        val empty = ConsoleData.from(listing(listOf(LIVE), emptyList()))
        assertTrue("an empty archived list still says the service keeps them", empty.keepsDeleted)
        assertTrue(empty.archived.isEmpty())

        val older = ConsoleData.from(listing(listOf(LIVE), null))
        assertFalse("a service not updated yet erases at once", older.keepsDeleted)
        assertTrue(older.archived.isEmpty())
    }

    @Test
    fun theViewModelListsTheDeletedApartAndSearchesThem() {
        val vm = vm("http://127.0.0.1:9", listing(listOf(LIVE), listOf(GONE)))
        assertEquals(listOf("Shree Demo Sacks"), vm.companies.map { it.name })
        assertEquals(listOf("Om Poly Packs"), vm.deletedCompanies.map { it.name })
        vm.companyQuery = "shree"
        assertTrue(vm.deletedCompanies.isEmpty())
        vm.companyQuery = ""

        /* one view at a time: the dashboard's "ending soon" and the Deleted list never at once */
        vm.companyView = CompanyView.DELETED
        assertFalse(vm.companyEnding)
        vm.companyEnding = true
        assertEquals(CompanyView.ENDING, vm.companyView)
        vm.companyEnding = false
        assertEquals(CompanyView.ALL, vm.companyView)
    }

    /* ---- the Delete question ---- */

    @Test
    fun theDeleteQuestionSaysItIsKeptThirtyDays() {
        assertEquals(
            "Its computers and phones stop at their next check and nobody can sign in. It is kept for 30 days: " +
                "Restore (Companies → Deleted) puts it back exactly as it was. After 30 days the company, its " +
                "machines, its people, everything they synced, its chat and its problem reports are erased for good.",
            deleteQuestionBody(keepsDeleted = true)
        )
        assertEquals(
            "a service not updated yet erases at once, and the question says so",
            "This removes the company, its machines, its people and everything they synced. It cannot be undone from here.",
            deleteQuestionBody(keepsDeleted = false)
        )
    }

    /* ---- Delete: what it answers ---- */

    @Test
    fun deleteSaysItIsKeptThirtyDaysAndShowsTheWarning() {
        val heard = Heard()
        var deleted = false
        val srv = service(heard, { deleted = true; keptAnswer().toString() }) {
            if (deleted) listing(emptyList(), listOf(SHREE_GONE)) else listing(listOf(LIVE), emptyList())
        }
        val vm = vm(srv.base, listing(listOf(LIVE), emptyList()))

        vm.deleteCompany(3, "Shree Demo Sacks")
        until("deleted and read again") { heard.after("company:delete", "licences") && vm.deletedCompanies.isNotEmpty() }

        val sent = heard.sent!!
        assertEquals(3, sent.getInt("id"))
        assertEquals("delete", sent.getString("action"))
        assertEquals("Shree Demo Sacks", sent.getString("confirmName"))

        val m = msgOf(vm)
        assertEquals(Msg.Kind.OK, m.kind)
        assertEquals(
            KEPT_WARNING + " (3 installation(s), 2 user(s), 46 synced record(s), 1 ink model(s) are kept until then.)",
            m.text
        )
        /* the list read again: it is now under Deleted, not among the live ones */
        assertTrue(vm.companies.isEmpty())
        assertEquals(listOf("Shree Demo Sacks"), vm.deletedCompanies.map { it.name })
    }

    @Test
    fun aKeptDeleteWithNoWarningIsSaidInTheConsolesOwnWords() {
        val heard = Heard()
        val answer = JSONObject()
            .put("ok", true).put("removed", JSONObject().put("installations", 1))
            .put("name", "Shree Demo Sacks").put("archived", true)
            .put("purgeAt", "2026-11-01T05:40:00.000Z").put("restoreDays", 30)
        val srv = service(heard, { answer.toString() }) { listing(emptyList(), emptyList()) }
        val vm = vm(srv.base, listing(listOf(LIVE), emptyList()))

        vm.deleteCompany(3, "Shree Demo Sacks")
        until("answered") { heard.after("company:delete", "licences") && vm.msg != null }

        assertEquals(
            "Shree Demo Sacks is deleted and kept for 30 days: Restore (Companies → Deleted) puts it back exactly " +
                "as it was until " + Fmt.day("2026-11-01T05:40:00.000Z") + ". After that it is erased for good. " +
                "(1 installation(s), 0 user(s), 0 synced record(s), 0 ink model(s) are kept until then.)",
            msgOf(vm).text
        )
    }

    @Test
    fun deleteOnAnOlderServiceSaysWhatItDid() {
        val heard = Heard()
        val answer = JSONObject().put("ok", true)
            .put("removed", JSONObject().put("installations", 3).put("users", 2).put("records", 46).put("inkModels", 1))
            .put("name", "Shree Demo Sacks")
        val srv = service(heard, { answer.toString() }) { listing(emptyList(), null) }
        val vm = vm(srv.base, listing(listOf(LIVE), null))

        vm.deleteCompany(3, "Shree Demo Sacks")
        until("answered") { heard.after("company:delete", "licences") && vm.msg != null }

        val m = msgOf(vm)
        assertEquals("Deleted Shree Demo Sacks — 3 installation(s), 2 user(s), 46 synced record(s), 1 ink model(s).", m.text)
        assertFalse("no promise of a Restore", m.text.contains("Restore"))
    }

    /* ---- Restore ---- */

    @Test
    fun restoreSendsUndeleteAndReadsTheListAgain() {
        val heard = Heard()
        var restored = false
        val srv = service(heard, {
            restored = true
            JSONObject().put("ok", true).put("name", "Om Poly Packs").put("state", "LICENSED")
                .put("warning", RESTORED_WARNING).toString()
        }) { if (restored) listing(listOf(LIVE, OM_BACK), emptyList()) else listing(listOf(LIVE), listOf(GONE)) }
        val vm = vm(srv.base, listing(listOf(LIVE), listOf(GONE)))
        vm.companyView = CompanyView.DELETED

        vm.undeleteCompany(7)
        until("restored and read again") { heard.after("company:undelete", "licences") && vm.companies.size == 2 }

        val sent = heard.sent!!
        assertEquals("exactly {id, action:'undelete'}", setOf("id", "action"), sent.keys().asSequence().toSet())
        assertEquals(7, sent.getInt("id"))
        assertEquals("undelete", sent.getString("action"))

        val m = msgOf(vm)
        assertEquals(Msg.Kind.OK, m.kind)
        assertEquals(RESTORED_WARNING, m.text)
        assertEquals("back to the live companies, where it now is", CompanyView.ALL, vm.companyView)
        assertEquals(setOf("Shree Demo Sacks", "Om Poly Packs"), vm.companies.map { it.name }.toSet())
        assertTrue(vm.data.archived.isEmpty())
    }

    @Test
    fun aRestoreTheServiceRefusesSaysWhy() {
        val heard = Heard()
        val srv = service(heard, {
            """{"error":"No such company — a deleted company is erased 30 days after it was deleted."}"""
        }) { listing(listOf(LIVE), listOf(GONE)) }
        val vm = vm(srv.base, listing(listOf(LIVE), listOf(GONE)))
        vm.companyView = CompanyView.DELETED

        vm.undeleteCompany(7)
        until("refused") { heard.asked.contains("company:undelete") && !vm.busy && vm.msg != null }

        val m = msgOf(vm)
        assertEquals(Msg.Kind.ERR, m.kind)
        assertEquals("No such company — a deleted company is erased 30 days after it was deleted.", m.text)
        assertEquals(CompanyView.DELETED, vm.companyView)
        assertFalse("nothing to read again", heard.asked.contains("licences"))
    }

    private fun keptAnswer() = JSONObject()
        .put("ok", true)
        .put("removed", JSONObject().put("installations", 3).put("users", 2).put("records", 46).put("inkModels", 1)
            .put("chats", 4).put("reports", 0))
        .put("name", "Shree Demo Sacks").put("archived", true)
        .put("deletedAt", "2026-10-02T05:40:00.000Z").put("purgeAt", "2026-11-01T05:40:00.000Z")
        .put("restoreDays", 30).put("warning", KEPT_WARNING)

    companion object {
        const val LIVE = """{"id": 3, "name": "Shree Demo Sacks", "licence_key": "NX-DEMO-0003", "state": "LICENSED", "seats": 5,
            "seats_used": 2, "machines_used": 3, "is_demo": false, "expires_at": "2027-06-01T18:29:59Z", "days_left": 245, "plan": "PRO"}"""

        const val GONE = """{"id": 7, "name": "Om Poly Packs", "email": "accounts@ompoly.example", "phone": "9800000007",
            "gstin": "24AAACO1234B1Z5", "login_id": "ompoly", "is_demo": false, "plan": "PRO", "seats": 2,
            "deleted_at": "2026-10-02T05:40:00.000Z", "deleted_state": "LICENSED", "purge_at": "2026-11-01T05:40:00.000Z",
            "days_to_purge": 30, "machines": 2, "people": 1, "records": 46}"""

        /** Shree Demo Sacks, deleted. */
        val SHREE_GONE = GONE.replace("\"id\": 7", "\"id\": 3").replace("Om Poly Packs", "Shree Demo Sacks")

        /** Om Poly Packs as a live company again, after Restore. */
        val OM_BACK = GONE.replace("\"is_demo\"", "\"licence_key\": \"NX-DEMO-0007\", \"state\": \"LICENSED\", \"is_demo\"")

        const val KEPT_WARNING = "Shree Demo Sacks is deleted: its computers and phones stop at their next check and nobody " +
            "can sign in. It is kept for 30 days — Restore (Companies → Deleted) puts it back exactly as it was until " +
            "1 Nov 2026. After that the company and everything it synced are erased for good."

        const val RESTORED_WARNING =
            "Om Poly Packs is back as it was (licensed). Its computers and phones work again at their next check."
    }
}
