package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.CompanyEntry
import org.nexoraofficial.console.data.CompanyList
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.FabricCompany
import org.nexoraofficial.console.data.FabricCounts
import org.nexoraofficial.console.data.FabricDetail
import org.nexoraofficial.console.data.FabricUser
import org.nexoraofficial.console.data.FeedbackData
import org.nexoraofficial.console.data.InquiryData
import org.nexoraofficial.console.data.Prefs
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.Release
import org.nexoraofficial.console.data.Sealer
import org.nexoraofficial.console.data.SoftwareFilter
import org.nexoraofficial.console.data.Summary
import org.nexoraofficial.console.data.WatchSource
import org.nexoraofficial.console.work.Watch
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1.9.0 — EVERY NEXORA SOFTWARE IN THE ONE CONSOLE: what GET /admin/api/products
 * answers is read null-safely (Fabric Stock reachable or not, linked by the
 * GSTIN, by hand, or not at all), the Companies list puts each customer in its
 * place under All / Weight Calc / Fabric Stock, Fabric Stock's changes go to
 * POST /admin/api/fabric with its own failures told in its own words, and the
 * watch tells a plant that registered itself on Fabric Stock.
 *
 * Made-up companies; nothing reaches the live service (a one-page server on
 * this computer, or a stand-in).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Software190Test {

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

    /* ---- the products, as the service sends them ---- */

    private fun fabricCo(
        id: Int, name: String, state: String = "LICENSED", companyId: Any? = null, linkedBy: String? = null,
        gstin: String? = null, selfRegistered: Boolean = false, daysLeft: Int = 100, extra: JSONObject.() -> Unit = {}
    ) = JSONObject()
        .put("id", id).put("name", name).put("licenceKey", "NFS-TEST-000$id").put("loginId", JSONObject.NULL)
        .put("email", JSONObject.NULL).put("phone", JSONObject.NULL).put("gstin", gstin ?: JSONObject.NULL)
        .put("state", state).put("isDemo", state == "DEMO").put("seats", 3).put("graceDays", 3).put("plan", "STANDARD")
        .put("expiresAt", "2027-01-15T18:29:59.000Z").put("periodStartedAt", "2026-10-07T00:00:00.000Z")
        .put("selfRegistered", selfRegistered).put("notes", JSONObject.NULL).put("createdAt", "2026-10-07T05:00:00.000Z")
        .put("deletedAt", JSONObject.NULL).put("people", 2).put("devices", 1).put("product", "fabric")
        .put("daysLeft", daysLeft).put("expired", false).put("periodDays", 365)
        .put("shownState", state).put("endingSoon", false)
        .put("companyId", companyId ?: JSONObject.NULL).put("linkedBy", linkedBy ?: JSONObject.NULL)
        .apply(extra)

    private fun products(vararg companies: JSONObject) = JSONObject().put(
        "products", JSONArray()
            .put(JSONObject("""{"id":"weight","name":"Nexora Bag Weight Calculation","short":"Weight Calc","ok":true}"""))
            .put(
                JSONObject().put("id", "fabric").put("name", "Nexora Loom & Fabric Stock").put("short", "Fabric Stock")
                    .put("ok", true).put("companies", JSONArray().apply { companies.forEach { put(it) } })
            )
    )

    private fun weightCo(id: Int, name: String, gstin: String? = null, key: String = "NX-TEST-000$id", ending: Boolean = false) =
        Company.from(
            JSONObject().put("id", id).put("name", name).put("licence_key", key).put("state", "LICENSED")
                .put("seats", 2).put("days_left", if (ending) 9 else 200)
                .put("expires_at", if (ending) "2026-10-16T18:29:59Z" else "2027-04-25T18:29:59Z")
                .apply { if (gstin != null) put("gstin", gstin) }
        )

    @Test
    fun productsAreReadWithEveryFabricStockCompany() {
        val p = ProductsData.from(
            products(
                fabricCo(1, "Riverside Sacks", "DEMO", companyId = "12", linkedBy = "gstin", gstin = "24ABCDE1234F1Z5", daysLeft = 5),
                fabricCo(2, "Loom House", companyId = 7, linkedBy = "hand"),
                fabricCo(3, "Fabric Only Mills", "SUSPENDED", selfRegistered = true)
            )
        )
        assertEquals(listOf("weight", "fabric"), p.products.map { it.id })
        assertEquals("Weight Calc", p.weight?.short)
        assertTrue(p.weight!!.companies.isEmpty())

        val f = p.fabric!!
        assertTrue(f.ok)
        assertNull(f.error)
        assertEquals("Fabric Stock", f.short)
        val (a, b, c) = f.companies
        assertEquals("the Weight Calc id comes as text", 12, a.companyId)
        assertEquals("gstin", a.linkedBy)
        assertEquals("NFS-TEST-0001", a.licenceKey)
        assertEquals("24ABCDE1234F1Z5", a.gstin)
        assertEquals("DEMO", a.shownState)
        assertTrue(a.onDemo)
        assertEquals(5, a.daysLeft)
        assertEquals(365, a.periodDays)
        assertEquals(2, a.people)
        assertEquals(1, a.devices)
        assertEquals("demo · 5 days", a.stateText)
        assertEquals("…or as a number", 7, b.companyId)
        assertEquals("hand", b.linkedBy)
        assertNull("Fabric Stock only", c.companyId)
        assertNull(c.linkedBy)
        assertFalse(c.linked)
        assertTrue(c.selfRegistered)
        assertEquals("suspended", c.stateText)
        assertNull("null fields are blanks, not the word null", c.email)
        assertNull(c.loginId)
    }

    @Test
    fun fabricStockAsleepIsNotConnectedWithTheServicesOwnWords() {
        val p = ProductsData.from(JSONObject("""{"products":[
            {"id":"weight","name":"Nexora Bag Weight Calculation","short":"Weight Calc","ok":true},
            {"id":"fabric","name":"Nexora Loom & Fabric Stock","short":"Fabric Stock","ok":false,
             "error":"FABRIC_DOWN","message":"Fabric Stock did not answer in time — it may be waking up."}]}"""))
        val f = p.fabric!!
        assertFalse(f.ok)
        assertEquals("FABRIC_DOWN", f.error)
        assertEquals("Fabric Stock did not answer in time — it may be waking up.", f.problem)
        assertTrue(f.companies.isEmpty())

        /* no message: the console's own words for the code */
        val key = ProductsData.from(JSONObject("""{"products":[{"id":"fabric","short":"Fabric Stock","ok":false,"error":"FABRIC_KEY"}]}"""))
        assertEquals("The Fabric Stock service did not accept the console's key.", key.fabric!!.problem)
        /* an answer with no products at all is an empty list, not a crash */
        assertTrue(ProductsData.from(JSONObject("{}")).products.isEmpty())
        assertNull(ProductsData.from(JSONObject("{}")).fabric)
    }

    @Test
    fun theDetailCarriesItsPeopleAndItsComputersAndPhones() {
        val o = fabricCo(1, "Riverside Sacks", companyId = "12", linkedBy = "gstin").apply {
            /* the detail is the company without the computed fields, and with the lists */
            listOf("daysLeft", "expired", "periodDays", "shownState", "endingSoon", "people", "devices").forEach { remove(it) }
            put("users", JSONArray()
                .put(JSONObject("""{"id":11,"name":"Asha","email":"asha@example.com","role":"ADMIN","scope":"ALL","permissions":null,
                    "active":true,"sessionDevice":"d-0001-aaaa-bbbb","sessionAt":"2026-10-07T04:00:00Z","lastLoginAt":"2026-10-07T04:00:00Z",
                    "lastSeenAt":"2026-10-07T05:00:00Z","createdAt":"2026-10-01T00:00:00Z"}"""))
                .put(JSONObject("""{"id":12,"name":"Ravi","role":"USER","scope":"OWN","permissions":{"stock":true,"reports":false,"looms":"edit"},
                    "active":false,"sessionDevice":null}""")))
            put("devices", JSONArray()
                .put(JSONObject("""{"id":"dev-1","name":"STORE-PC","platform":"desktop","state":"ACTIVE","approved":true,"approvedBy":"Asha",
                    "pending":false,"revokedBy":null,"lastSeen":"2026-10-07T05:00:00Z","computerNo":1,"appVersion":"0.6.0","signedIn":{"id":11,"name":"Asha"}}"""))
                .put(JSONObject("""{"id":"dev-2","name":null,"platform":"mobile","approved":false,"pending":false,"revokedBy":"NEXORA","signedIn":null}"""))
                .put(JSONObject("""{"id":"dev-3","platform":"mobile","approved":false,"pending":true,"revokedBy":"COMPANY"}""")))
        }
        val d = FabricDetail.from(o)
        assertEquals(1, d.company.id)
        assertEquals(12, d.company.companyId)
        assertEquals("the count, from the list itself", 3, d.company.devices)
        assertEquals("the state worked out when not sent", "LICENSED", d.company.shownState)
        assertTrue(d.company.daysLeft > 0)

        val (asha, ravi) = d.users
        assertEquals(11, asha.id)
        assertTrue(asha.isAdmin)
        assertTrue(asha.signedIn)
        assertTrue(asha.rights.isEmpty())
        assertFalse(ravi.isAdmin)
        assertFalse(ravi.active)
        assertFalse(ravi.signedIn)
        assertEquals("the rights that are on", listOf("stock", "looms"), ravi.rights)

        val (pc, phone, theirs) = d.devices
        assertEquals("dev-1", pc.id)
        assertFalse(pc.isPhone)
        assertEquals("Asha", pc.signedInName)
        assertEquals(1, pc.computerNo)
        assertFalse(pc.withdrawn)
        assertTrue(phone.isPhone)
        assertTrue(phone.nexoraWithdrew)
        assertNull(phone.name)
        assertTrue(theirs.withdrawn)
        assertFalse("only the company gives back what it withdrew", theirs.nexoraWithdrew)
        assertTrue(theirs.pending)

        /* the same detail sent under "company" reads the same */
        val wrapped = JSONObject().put("company", fabricCo(1, "Riverside Sacks")).put("users", JSONArray()).put("devices", JSONArray())
        assertEquals("Riverside Sacks", FabricDetail.from(wrapped).company.name)
    }

    @Test
    fun rightsAreReadHoweverTheyAreWritten() {
        assertEquals(listOf("a", "b"), FabricUser.rightsOf(JSONArray().put("a").put("b")))
        assertEquals(listOf("a", "b"), FabricUser.rightsOf("a, b"))
        assertEquals(listOf("x"), FabricUser.rightsOf("""["x"]"""))
        assertEquals(listOf("on"), FabricUser.rightsOf(JSONObject().put("on", true).put("off", false).put("none", "none")))
        assertTrue(FabricUser.rightsOf(null).isEmpty())
        assertTrue(FabricUser.rightsOf(JSONObject.NULL).isEmpty())
    }

    /* ---- the Companies list across the software ---- */

    private val weight = listOf(
        weightCo(3, "Shree Demo Sacks", gstin = "24AAACS1429B1ZQ"),
        weightCo(7, "Om Poly Packs", ending = true),
        weightCo(9, "Vijay Woven Bags")
    )

    private val fabric = listOf(
        FabricCompany.from(fabricCo(1, "Shree Demo Sacks", "DEMO", companyId = "3", linkedBy = "gstin", gstin = "24AAACS1429B1ZQ")),
        FabricCompany.from(fabricCo(2, "Riverside Looms", selfRegistered = true)),
        FabricCompany.from(fabricCo(4, "Om Fabrics", "SUSPENDED", companyId = 7, linkedBy = "hand")),
        /* linked to a Weight Calc company that is not in the list (deleted): shown on its own, never lost */
        FabricCompany.from(fabricCo(5, "Ghost Weaves", companyId = "99", linkedBy = "hand"))
    )

    @Test
    fun everyCustomerIsListedOnceUnderAll() {
        val all = CompanyList.entries(weight, fabric, SoftwareFilter.ALL, "")
        assertEquals(listOf("w-3", "w-7", "w-9", "f-2", "f-5"), all.map { it.key })
        val shree = all[0] as CompanyEntry.Weight
        assertEquals("its Fabric Stock licence rides with it", 1, shree.fabric?.id)
        assertEquals(4, (all[1] as CompanyEntry.Weight).fabric?.id)
        assertNull((all[2] as CompanyEntry.Weight).fabric)

        val n = CompanyList.counts(weight, fabric)
        assertEquals(5, n.all)
        assertEquals(3, n.weight)
        assertEquals(4, n.fabric)
    }

    @Test
    fun eachSoftwareChipShowsItsOwnCompanies() {
        val w = CompanyList.entries(weight, fabric, SoftwareFilter.WEIGHT, "")
        assertEquals(listOf("w-3", "w-7", "w-9"), w.map { it.key })

        val f = CompanyList.entries(weight, fabric, SoftwareFilter.FABRIC, "")
        assertEquals("the Weight Calc companies on Fabric Stock, then Fabric Stock alone", listOf("w-3", "w-7", "f-2", "f-5"), f.map { it.key })
    }

    @Test
    fun theSearchFindsFabricStockCompaniesByNameGstinAndKey() {
        assertEquals(listOf("f-2"), CompanyList.entries(weight, fabric, SoftwareFilter.ALL, "riverside").map { it.key })
        assertEquals("a Weight Calc company found by its Fabric Stock key", listOf("w-7"),
            CompanyList.entries(weight, fabric, SoftwareFilter.ALL, "NFS-TEST-0004").map { it.key })
        assertEquals(listOf("w-3"), CompanyList.entries(weight, fabric, SoftwareFilter.FABRIC, "24AAACS").map { it.key })
        assertEquals(listOf("f-5"), CompanyList.entries(weight, fabric, SoftwareFilter.FABRIC, "nfs-test-0005").map { it.key })
        assertTrue(CompanyList.entries(weight, fabric, SoftwareFilter.WEIGHT, "Riverside").isEmpty())
    }

    @Test
    fun endingSoonIsTheDashboardsWeightCalcList() {
        assertEquals(listOf("w-7"), CompanyList.entries(weight, fabric, SoftwareFilter.FABRIC, "", endingOnly = true).map { it.key })
    }

    @Test
    fun withFabricStockNotConnectedTheListIsWeightCalcAsBefore() {
        val all = CompanyList.entries(weight, emptyList(), SoftwareFilter.ALL, "")
        assertEquals(listOf("w-3", "w-7", "w-9"), all.map { it.key })
        assertEquals(3, CompanyList.counts(weight, emptyList()).all)
    }

    @Test
    fun twoPointingAtOneCompanyLeaveNoneOut() {
        val twins = fabric + FabricCompany.from(fabricCo(6, "Shree Twin", companyId = 3, linkedBy = "hand"))
        assertEquals("the older stays with it", 1, CompanyList.linked(weight, twins)[3]?.id)
        assertTrue(CompanyList.fabricOnly(weight, twins).any { it.id == 6 })
    }

    @Test
    fun fabricStocksFiguresForTheDashboard() {
        val n = FabricCounts.of(fabric)
        assertEquals(2, n.licensed)
        assertEquals(1, n.demo)
        assertEquals(1, n.suspended)
        assertEquals(4, n.total)
    }

    @Test
    fun addingDaysKeepsWhatIsLeft() {
        val f = FabricCompany.from(fabricCo(2, "Riverside Looms", daysLeft = 100))
        assertEquals("Fabric Stock renews from today: the 100 left go with the 30", 130, f.renewDays(30))
        val now = Instant.parse("2026-10-07T00:00:00Z")
        assertEquals(Instant.parse("2027-11-11T00:00:00Z"), f.renewEnd(300, now))
        val ended = FabricCompany.from(fabricCo(8, "Ended Mills", "LICENSED", daysLeft = 0) {
            put("expired", true).put("shownState", "EXPIRED")
        })
        assertEquals("an ended licence runs from today", 365, ended.renewDays(365))
    }

    /* ---- the calls ---- */

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
    fun fabricStocksFailuresAreToldInItsOwnWords() = runBlocking {
        val heard = CopyOnWriteArrayList<Pair<String, String?>>()
        val srv = TinyServer { target, headers ->
            heard += target to headers["x-console"]
            502 to """{"error":"FABRIC_DOWN","message":"Fabric Stock is asleep — try again in a minute."}"""
        }.also { server = it }
        try {
            Api(srv.base, "NX-TEST-KEY").fabric(JSONObject().put("action", "suspend").put("id", 2))
            fail("a 502 is a failure")
        } catch (e: ApiError) {
            assertEquals(502, e.status)
            assertEquals("FABRIC_DOWN", e.code)
            assertEquals("Fabric Stock is asleep — try again in a minute.", e.message)
        }
        assertEquals(JSONObject().put("action", "suspend").put("id", 2).toString(), srv.lastBody)
        assertEquals(listOf("/admin/api/fabric" to "android"), heard)
        assertEquals("NX-TEST-KEY", srv.lastHeaders["x-admin-key"])
    }

    @Test
    fun theConsoleReadsTheSoftwareApartAndSaysWhenFabricStockIsAsleep() {
        val asleep = """{"products":[{"id":"weight","short":"Weight Calc","ok":true},
            {"id":"fabric","short":"Fabric Stock","ok":false,"error":"FABRIC_DOWN","message":"Fabric Stock is waking up."}]}"""
        val srv = TinyServer { target, _ ->
            when (target.substringBefore('?')) {
                "/admin/api/licences" -> 200 to """{"companies":[{"id":3,"name":"Shree Demo Sacks","licence_key":"NX-1"}],"licences":[],"settings":{}}"""
                "/admin/api/products" -> 200 to asleep
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-TEST-KEY"
        }
        vm.load()
        until("signed in and the software read") { vm.signedIn && vm.productsLoad == Load.READY }
        assertEquals("Fabric Stock is waking up.", vm.fabricProblem)
        assertTrue(vm.fabricCompanies.isEmpty())
        assertFalse(vm.fabricReady)
        assertEquals("the Weight Calc companies are there regardless", 1, vm.companyEntries.size)
        assertNull("quiet: no red strip", vm.lastSaid)
    }

    @Test
    fun aServiceWithoutTheSoftwareListIsToldItNeedsTheUpdate() {
        val srv = TinyServer { _, _ -> 404 to """{"error":"NOT_FOUND"}""" }.also { server = it }
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-TEST-KEY"
            signedIn = true
        }
        vm.loadProducts()
        until("answered") { vm.productsLoad == Load.FAILED }
        assertEquals("This service does not list the other software yet — it needs the service update.", vm.fabricProblem)
    }

    @Test
    fun aFabricStockChangeIsSentThenTheListIsReadAgain() {
        val asked = CopyOnWriteArrayList<String>()
        var sent: JSONObject? = null
        val listing = products(fabricCo(2, "Riverside Looms")).toString()
        val srv = TinyServer { target, _ ->
            val path = target.substringBefore('?')
            asked += path
            when (path) {
                "/admin/api/fabric" -> {
                    sent = JSONObject(server?.lastBody ?: "{}")
                    if (sent!!.optString("action") == "resume")
                        502 to """{"error":"FABRIC_KEY","message":"Fabric Stock refused the console's key."}"""
                    else 200 to """{"ok":true,"company":{"id":2,"name":"Riverside Looms","state":"SUSPENDED"}}"""
                }
                "/admin/api/products" -> 200 to listing
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-TEST-KEY"
            signedIn = true
        }
        val f = FabricCompany.from(fabricCo(2, "Riverside Looms"))

        /* (what was said is read from lastSaid: under Robolectric the clock can leap past the strip's
           six seconds between two looks) */
        vm.fabricSuspend(f)
        until("suspended and read again") { !vm.busy && asked.contains("/admin/api/products") && vm.productsLoad == Load.READY }
        assertEquals("suspend", sent!!.getString("action"))
        assertEquals(2, sent!!.getInt("id"))
        assertEquals("Riverside Looms is suspended on Fabric Stock.", vm.lastSaid?.text)
        assertEquals(Msg.Kind.OK, vm.lastSaid?.kind)
        assertEquals(1, vm.fabricCompanies.size)

        vm.fabricResume(f)
        until("refused") { !vm.busy && sent!!.optString("action") == "resume" && vm.lastSaid?.kind == Msg.Kind.ERR }
        assertEquals("Fabric Stock refused the console's key.", vm.lastSaid?.text)

        /* Add days carries the days left; a hand link sends the Weight Calc id */
        vm.fabricAddDays(f, 30)
        until("renewed") { !vm.busy && sent!!.optString("action") == "update" }
        assertEquals(130, sent!!.getInt("days"))
        vm.fabricLink(2, 9, "Vijay Woven Bags")
        until("linked") { !vm.busy && sent!!.optString("action") == "link" }
        assertEquals(9, sent!!.getInt("companyId"))
        assertEquals("Linked to Vijay Woven Bags.", vm.lastSaid?.text)
    }

    @Test
    fun startingFabricStockForAWeightCalcCompanyLinksItAtOnce() {
        var sent: JSONObject? = null
        val srv = TinyServer { target, _ ->
            if (target.substringBefore('?') == "/admin/api/fabric") {
                sent = JSONObject(server?.lastBody ?: "{}")
                200 to """{"ok":true,"company":{"id":20,"name":"Shree Demo Sacks","licenceKey":"NFS-NEW-0020"}}"""
            } else 404 to """{"error":"NOT_FOUND"}"""
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-TEST-KEY"
            signedIn = true
        }
        val co = Company.from(
            JSONObject().put("id", 3).put("name", "Shree Demo Sacks").put("licence_key", "NX-1").put("seats", 5)
                .put("grace_days", 2).put("gstin", "24AAACS1429B1ZQ").put("email", "office@example.com")
        )
        vm.startFabric(co, demo = true)
        until("started") { !vm.busy && sent != null && vm.lastSaid != null }
        val s = sent!!
        assertEquals("create", s.getString("action"))
        assertEquals("DEMO", s.getString("state"))
        assertEquals(7, s.getInt("days"))
        assertEquals(5, s.getInt("seats"))
        assertEquals(2, s.getInt("graceDays"))
        assertEquals("24AAACS1429B1ZQ", s.getString("gstin"))
        assertEquals("office@example.com", s.getString("email"))
        assertEquals("Shree Demo Sacks", s.getString("name"))
        assertEquals("linked to the Weight Calc company at once", 3, s.getInt("linkTo"))
        assertFalse("no phone to copy, so none is sent", s.has("phone"))
        assertTrue(vm.lastSaid!!.text.contains("NFS-NEW-0020"))
    }
}
