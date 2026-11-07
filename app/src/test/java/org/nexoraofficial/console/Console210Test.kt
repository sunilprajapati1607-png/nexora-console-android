package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.FabricCompany
import org.nexoraofficial.console.data.FabricEdit
import org.nexoraofficial.console.data.NewCustomerForm
import org.nexoraofficial.console.data.PaymentForm
import org.nexoraofficial.console.data.Payment
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.Requests
import org.nexoraofficial.console.data.RulesForm
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.SoftwareRulesData
import org.nexoraofficial.console.data.Validity
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/* ======================================================================
   2.1.0 — MSME ERP AND NEXORA CONSULT IN THE PHONE CONSOLE, AND EACH
   SOFTWARE'S OWN DEMO RULES.

   The console service serves MSME ERP ("erp") and Nexora Consult
   ("consult") exactly the way it serves Fabric Stock: the same company
   shape in GET /admin/api/products, the same actions on POST
   /admin/api/<sw>, and every software's demo rules on GET/POST
   /admin/api/software-settings. Made-up plants throughout; nothing reaches
   the live service (a one-page server on this computer, or a closed port).
   ====================================================================== */

/** Made-up products, as the console service answers them: Sales & Costing, Fabric Stock, MSME ERP and Nexora Consult. */
internal object Fixtures210 {

    fun co(
        id: Int, name: String, state: String = "LICENSED", days: Int = 200, companyId: Int? = null, linkedBy: String? = null,
        gstin: String? = null, people: Int = 2, seats: Int = 5, devices: Int = 1, plan: String = "STANDARD", planName: String? = "Standard"
    ): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("licenceKey", "NX%02d-%04d-T6WD".format(id / 10, id))
        .put("loginId", JSONObject.NULL).put("email", JSONObject.NULL).put("phone", JSONObject.NULL)
        .put("gstin", gstin ?: JSONObject.NULL).put("state", state).put("isDemo", state == "DEMO").put("seats", seats)
        .put("graceDays", if (state == "DEMO") 0 else 3).put("plan", plan).put("planName", planName ?: JSONObject.NULL)
        .put("expiresAt", java.time.Instant.parse("2026-11-07T18:29:59Z").plus(java.time.Duration.ofDays(days.toLong())).toString())
        .put("periodStartedAt", "2026-04-01T00:00:00.000Z").put("selfRegistered", false).put("notes", JSONObject.NULL)
        .put("createdAt", "2026-04-01T05:00:00.000Z").put("deletedAt", JSONObject.NULL)
        .put("people", people).put("devices", devices).put("daysLeft", days).put("expired", false)
        .put("periodDays", if (state == "DEMO") 14 else 365).put("shownState", state).put("endingSoon", days in 1..30 && state == "LICENSED")
        .put("companyId", companyId ?: JSONObject.NULL).put("linkedBy", linkedBy ?: JSONObject.NULL)
        .put("featureOverrides", JSONObject.NULL)

    /** Sales & Costing: two made-up plants. */
    fun weight(): JSONObject = JSONObject()
        .put("settings", JSONObject("""{"trialDays": 7, "demoGraceDays": 0, "sessionMinutes": 30, "expiredMode": "READONLY", "signupsOpen": true}"""))
        .put("archived", JSONArray())
        .put("companies", JSONArray()
            .put(JSONObject().put("id", 21).put("name", "Sagar Polysacks").put("licence_key", "NX-0021-K7Q2").put("state", "LICENSED")
                .put("plan", "PRO").put("seats", 6).put("users_total", 4).put("machines_used", 3).put("is_demo", false)
                .put("expires_at", "2027-05-30T18:29:59Z").put("days_left", 204).put("period_started_at", "2026-05-30T00:00:00Z")
                .put("period_days", 365).put("grace_days", 3).put("gstin", "24SAGAR1234P1Z5").put("gst_status", "VERIFIED")
                .put("created_at", "2026-05-30T05:00:00Z").put("email", "office@sagar.example").put("phone", "+91 90000 31111"))
            .put(JSONObject().put("id", 22).put("name", "Neel Raffia").put("licence_key", "NX-0022-K7Q2").put("state", "DEMO")
                .put("plan", "PRO").put("seats", 1).put("users_total", 1).put("is_demo", true)
                .put("expires_at", "2026-11-12T18:29:59Z").put("days_left", 5).put("created_at", "2026-11-01T05:00:00Z")))
        .put("licences", JSONArray())
        .put("keyless", JSONObject().put("devices", 0).put("required", false))

    /* Fabric Stock: Sagar's own (31), and an older second one pointing at Sagar too (32 — listed on its own) */
    private fun fabric() = JSONObject().put("id", "fabric").put("name", "Nexora Loom & Fabric Stock").put("short", "Fabric Stock").put("ok", true)
        .put("companies", JSONArray()
            .put(co(31, "Sagar Polysacks", companyId = 21, linkedBy = "gstin", gstin = "24SAGAR1234P1Z5"))
            .put(co(32, "Sagar Looms (old)", state = "SUSPENDED", companyId = 21, linkedBy = "hand")))

    /* MSME ERP: Sagar's by hand (41); Kiran with no Sales & Costing but a GSTIN (42); one alone with no GSTIN (43) */
    private fun erp() = JSONObject().put("id", "erp").put("name", "Nexora MSME ERP").put("short", "MSME ERP").put("ok", true)
        .put("companies", JSONArray()
            .put(co(41, "Sagar Polysacks", companyId = 21, linkedBy = "hand", gstin = "24SAGAR1234P1Z5", people = 9, seats = 10, devices = 4,
                plan = "GROWTH", planName = "Growth"))
            .put(co(42, "Kiran Tarpaulins", state = "DEMO", days = 9, gstin = "24KIRAN5555T1Z1", people = 1, seats = 3))
            .put(co(43, "Lakshmi Fabricators", days = 61, people = 3)))

    /* Nexora Consult: Sagar's (51); Kiran's, its GSTIN written another way (52 — one customer with 42); a second Kiran (53 — alone) */
    private fun consult() = JSONObject().put("id", "consult").put("name", "Nexora Consult").put("short", "Nexora Consult").put("ok", true)
        .put("companies", JSONArray()
            .put(co(51, "Sagar Polysacks", companyId = 21, linkedBy = "gstin", gstin = "24SAGAR1234P1Z5", people = 2, seats = 2, plan = "PRACTICE",
                planName = "Practice"))
            .put(co(52, "Kiran Tarpaulins", days = 300, gstin = " 24kiran5555t1z1 ", people = 1, seats = 1))
            .put(co(53, "Kiran Tarpaulins Unit 2", days = 40, gstin = "24KIRAN5555T1Z1", people = 1, seats = 1)))

    const val CONSULT_MESSAGE = "Nexora Consult's key is not set on the console service — add NEXORA_CONSULT_ADMIN_KEY on nexora-api."

    fun products(consultOk: Boolean = true): JSONObject = JSONObject().put("products", JSONArray()
        .put(JSONObject("""{"id":"weight","name":"Nexora Bag Weight Calculation","short":"Sales & Costing","ok":true}"""))
        .put(fabric())
        .put(erp())
        .put(if (consultOk) consult() else JSONObject().put("id", "consult").put("name", "Nexora Consult").put("short", "Nexora Consult")
            .put("ok", false).put("error", "CONSULT_KEY").put("message", CONSULT_MESSAGE)))

    /** Every software's demo rules, each its own (Consult's could not be read when [consultOk] is false). */
    fun rules(consultOk: Boolean = true): JSONObject = JSONObject().put("software", JSONArray()
        .put(JSONObject("""{"id":"weight","ok":true,"settings":{"trialDays":7,"demoGraceDays":0,"signupsOpen":true,"expiredMode":"READONLY","demoSeats":9}}"""))
        .put(JSONObject("""{"id":"fabric","ok":true,"settings":{"trialDays":14,"demoGraceDays":2,"demoSeats":3,"signupsOpen":true,"expiredMode":"READONLY"}}"""))
        .put(JSONObject("""{"id":"erp","ok":true,"settings":{"trialDays":30,"demoGraceDays":5,"demoSeats":8,"signupsOpen":false,"expiredMode":"HARDSTOP"}}"""))
        .put(if (consultOk) JSONObject("""{"id":"consult","ok":true,"settings":{"trialDays":21,"demoGraceDays":1,"demoSeats":2,"signupsOpen":true,"expiredMode":"READONLY"}}""")
             else JSONObject().put("id", "consult").put("ok", false).put("error", "CONSULT_KEY").put("message", CONSULT_MESSAGE)))

    /** The plans of every software: MSME ERP and Nexora Consult with plans of their own. */
    fun plans(): JSONObject = JSONObject().put("software", JSONArray()
        .put(JSONObject("""{"id":"weight","ok":true,"supported":true,"features":[{"id":"quotation","label":"Quotation","group":"Sales"}],
            "plans":[{"code":"PRO","name":"Pro","features":{"quotation":true},"active":true,"sort":20,"customers":1,"changed":0,"builtIn":true}]}"""))
        .put(JSONObject("""{"id":"fabric","ok":true,"supported":true,"features":[{"id":"stock","label":"Fabric stock","group":"Stock & dispatch"}],
            "plans":[{"code":"STANDARD","name":"Standard","features":{"stock":true},"active":true,"sort":10,"customers":1,"changed":0,"builtIn":true}]}"""))
        .put(JSONObject("""{"id":"erp","ok":true,"supported":true,"features":[{"id":"sales","label":"Sales","group":"Sales"},
            {"id":"production","label":"Production","group":"Production"}],
            "plans":[{"code":"STANDARD","name":"Standard","features":{"sales":true,"production":false},"active":true,"sort":10,"customers":0,"changed":0,"builtIn":true},
                     {"code":"GROWTH","name":"Growth","note":"sales, purchase, stock and production","priceFirst":60000,"priceRenewal":30000,
                      "features":{"sales":true,"production":true},"active":true,"sort":20,"customers":1,"changed":0,"builtIn":false}]}"""))
        .put(JSONObject("""{"id":"consult","ok":true,"supported":true,"features":[{"id":"clients","label":"Clients","group":"Practice"}],
            "plans":[{"code":"PRACTICE","name":"Practice","features":{"clients":true},"active":true,"sort":10,"customers":1,"changed":0,"builtIn":false}]}""")))

    /** Every remote software's companies, as the console reads them. */
    fun remotes(p: ProductsData): Map<String, List<FabricCompany>> =
        Software.REMOTE_IDS.associateWith { sw -> p.of(sw)?.takeIf { it.ok }?.companies.orEmpty() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Console210Test {

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

    /* ---------------------------------------------------------------- the products */

    @Test
    fun erpAndConsultAreReadFromTheProductsEachWithItsOwnCompanies() {
        val p = ProductsData.from(Fixtures210.products())
        assertEquals(listOf("weight", "fabric", "erp", "consult"), p.products.map { it.id })
        val erp = p.erp!!
        assertTrue(erp.ok)
        assertEquals("MSME ERP", erp.short)
        assertEquals(listOf(41, 42, 43), erp.companies.map { it.id })
        assertTrue("every company carries its software", erp.companies.all { it.sw == Software.ERP })
        assertEquals("GROWTH", erp.companies.first().plan)
        assertEquals("Growth", erp.companies.first().planName)
        assertEquals(21, erp.companies.first().companyId)
        assertEquals("hand", erp.companies.first().linkedBy)
        assertEquals(9, erp.companies.first().people)
        val consult = p.consult!!
        assertTrue(consult.companies.all { it.sw == Software.CONSULT })
        assertTrue("Fabric Stock's are still Fabric Stock's", p.fabric!!.companies.all { it.sw == Software.FABRIC })
        assertEquals(p.erp, p.of("erp"))

        /* the names, the table and the keys' letters */
        assertEquals("MSME ERP", Software.name(Software.ERP))
        assertEquals("Nexora Consult", Software.name(Software.CONSULT))
        assertEquals(listOf("fabric", "erp", "consult"), Software.REMOTE_IDS)
        assertEquals(listOf("weight", "fabric", "erp", "consult"), Software.LICENSED)
        assertEquals(Software.ERP, Software.byPrefix('e')?.id)
        assertEquals(Software.CONSULT, Software.byPrefix('c')?.id)
        assertFalse(Software.isRemote(Software.WEIGHT))
        assertFalse("Jobwork stays coming", Software.isRemote(Software.JOBWORK))
    }

    @Test
    fun aSoftwareTheServiceCouldNotReadSaysWhyNeverZeroCustomers() {
        val p = ProductsData.from(Fixtures210.products(consultOk = false))
        val c = p.consult!!
        assertFalse(c.ok)
        assertTrue(c.keyProblem)
        assertEquals(Fixtures210.CONSULT_MESSAGE, c.problem)
        /* without a message, the code is still told in words */
        val bare = ProductsData.from(JSONObject("""{"products":[{"id":"erp","short":"MSME ERP","ok":false,"error":"ERP_DOWN"}]}""")).erp!!
        assertTrue(bare.problem.contains("did not answer"))
        assertFalse(bare.keyProblem)

        val vm = ConsoleViewModel(app).apply {
            data = ConsoleData.from(Fixtures210.weight())
            products = p
            productsLoad = Load.READY
        }
        assertEquals(Fixtures210.CONSULT_MESSAGE, vm.remoteProblem(Software.CONSULT))
        assertTrue(vm.remoteKeyProblem(Software.CONSULT))
        assertFalse(vm.remoteReady(Software.CONSULT))
        assertTrue("no Consult company is shown while it cannot be read", vm.remoteCompanies(Software.CONSULT).isEmpty())
        assertNull(vm.remoteProblem(Software.ERP))
        assertNull("Fabric Stock is not touched by it", vm.fabricProblem)
        assertTrue(vm.customers.none { it.has(Software.CONSULT) })
        /* a service that does not list a software at all says so */
        vm.products = ProductsData.from(JSONObject("""{"products":[{"id":"weight","ok":true}]}"""))
        assertEquals("This service does not list MSME ERP.", vm.remoteProblem(Software.ERP))
    }

    /* ---------------------------------------------------------------- one customer, every software */

    @Test
    fun aCustomerIsJoinedAcrossEverySoftwareByCompanyIdAndByGstin() {
        val weight = ConsoleData.from(Fixtures210.weight()).companies
        val all = Customers.of(weight, Fixtures210.remotes(ProductsData.from(Fixtures210.products())))

        /* Sagar: Sales & Costing with Fabric Stock, MSME ERP and Nexora Consult, each pointing at it by companyId */
        val sagar = all.single { it.key == "w21" }
        assertEquals(31, sagar.f?.id)
        assertEquals(41, sagar.remote(Software.ERP)?.id)
        assertEquals(51, sagar.remote(Software.CONSULT)?.id)
        assertEquals(listOf("weight", "fabric", "erp", "consult"), sagar.softwares)
        assertEquals(4, sagar.subs.size)
        assertTrue(sagar.both)
        assertEquals("Customer · 4 software · since 30 May 26", sagar.subtitle)
        assertEquals("people on every software's seats", 4 + 2 + 9 + 2, sagar.people)
        assertTrue("found by its MSME ERP key", sagar.matches("NX04-0041"))

        /* Kiran: no Sales & Costing — its MSME ERP and Nexora Consult are one customer by the same GSTIN */
        val kiran = all.single { it.key == "e42" }
        assertNull(kiran.w)
        assertNull(kiran.f)
        assertEquals(42, kiran.remote(Software.ERP)?.id)
        assertEquals("the GSTIN written another way still joins them", 52, kiran.remote(Software.CONSULT)?.id)
        assertEquals("24KIRAN5555T1Z1", kiran.gstin)
        assertEquals(listOf("erp", "consult"), kiran.softwares)
        assertEquals("its demo ends first", "erp", kiran.next?.sw)

        /* each alone: a second Fabric Stock for Sagar, an MSME ERP with no GSTIN, a second Kiran on Consult */
        assertEquals(listOf(32), all.single { it.key == "f32" }.remotes.values.map { it.id })
        assertEquals(listOf(43), all.single { it.key == "e43" }.remotes.values.map { it.id })
        assertEquals(listOf(53), all.single { it.key == "c53" }.remotes.values.map { it.id })
        assertTrue(all.single { it.key == "w22" }.remotes.isEmpty())
        assertEquals(listOf("w21", "w22", "f32", "e42", "e43", "c53"), all.map { it.key })

        /* found by its key, and by any of its licences when the key has changed */
        assertEquals(kiran, Customers.find(all, "e42"))
        assertEquals("by its Consult company", kiran, Customers.find(all, "c52"))
        assertEquals(sagar, Customers.find(all, "e41"))

        /* lines, validity and by software */
        assertEquals("MSME ERP · Growth · 200 days", Customers.line(sagar, Software.ERP, null))
        assertEquals("Nexora Consult · Standard · 300 days", Customers.line(kiran, Software.CONSULT, null))
        assertEquals("Nexora Consult · Practice · 200 days", Customers.line(sagar, Software.CONSULT, null))
        val rows = Validity.rows(all, null, null)
        assertEquals("one row per licence", 2 + 2 + 3 + 3, rows.size)
        assertEquals(3, rows.count { it.sw == Software.ERP })
        assertEquals(listOf("e42", "e43", "w21").sorted(), org.nexoraofficial.console.data.BySoftware.customers(all, Software.ERP).map { it.key }.sorted())
    }

    @Test
    fun everyRemoteCompanyAppearsExactlyOnce() {
        val weight = ConsoleData.from(Fixtures210.weight()).companies
        val remotes = Fixtures210.remotes(ProductsData.from(Fixtures210.products()))
        val all = Customers.of(weight, remotes)
        val shown = all.flatMap { x -> x.remotes.map { (sw, r) -> "$sw:${r.id}" } }
        val given = remotes.flatMap { (sw, l) -> l.map { "$sw:${it.id}" } }
        assertEquals("nothing lost, nothing twice", given.sorted(), shown.sorted())
        assertEquals(shown.size, shown.toSet().size)
        assertEquals("every Sales & Costing company once", weight.map { it.id }.sorted(), all.mapNotNull { it.w?.id }.sorted())

        /* a company that came without its software (a hand-made list) takes the one it is listed under */
        val x = Customers.of(emptyList(), mapOf(Software.ERP to listOf(FabricCompany.from(Fixtures210.co(7, "Om Fab"))))).single()
        assertEquals("e7", x.key)
        assertEquals(Software.ERP, x.remote(Software.ERP)?.sw)

        /* 2.0's two-software lists are read exactly as before */
        val only = Customers.of(weight, remotes[Software.FABRIC]!!)
        assertEquals(listOf("w21", "w22", "f32"), only.map { it.key })
    }

    /* ---------------------------------------------------------------- the payments of each software */

    @Test
    fun aPaymentBelongsToItsSoftwaresCompanyAndIsSentWithRemoteId() {
        val weight = ConsoleData.from(Fixtures210.weight()).companies
        val all = Customers.of(weight, Fixtures210.remotes(ProductsData.from(Fixtures210.products())))
        val kiran = all.single { it.key == "e42" }
        val b = Requests.paymentAdd(PaymentForm(customerKey = "e42", software = Software.CONSULT, amount = "12,000", paidOn = "2026-11-07"), kiran)
        assertEquals("consult", b.getString("software"))
        assertEquals(52, b.getInt("remoteId"))
        assertFalse("no Sales & Costing company", b.has("companyId"))
        assertFalse("fabricId only for Fabric Stock", b.has("fabricId"))
        val sagar = all.single { it.key == "w21" }
        val fb = Requests.paymentAdd(PaymentForm(customerKey = "w21", software = Software.FABRIC, amount = "1"), sagar)
        assertEquals(31, fb.getInt("remoteId"))
        assertEquals(31, fb.getInt("fabricId"))
        assertEquals(21, fb.getInt("companyId"))

        val p = Payment.from(JSONObject().put("id", 1).put("software", "consult").put("remoteId", "52").put("amount", 12000))
        assertTrue(p.of(kiran))
        assertFalse(p.of(all.single { it.key == "c53" }))
        val old = Payment.from(JSONObject().put("id", 2).put("software", "fabric").put("fabricId", "31"))
        assertTrue("the older name still reads", old.of(sagar))
    }

    /* ---------------------------------------------------------------- what is sent, and where */

    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (ok()) return
            if (System.currentTimeMillis() > end) fail("timed out: $what")
            Thread.sleep(10)
        }
    }

    private fun service(sent: MutableList<Pair<String, JSONObject>>, answer: (String, JSONObject) -> Pair<Int, String>? = { _, _ -> null }) =
        TinyServer { target, headers ->
            val path = target.substringBefore('?')
            /* a POST carries a body; a GET does not (the server keeps the last body it read, so it is not asked) */
            val post = (headers["content-length"]?.toIntOrNull() ?: 0) > 0
            val body = if (post) runCatching { JSONObject(server?.lastBody ?: "{}") }.getOrDefault(JSONObject()) else JSONObject()
            answer(path, body) ?: when (path) {
                "/admin/api/fabric", "/admin/api/erp", "/admin/api/consult" -> {
                    sent += path to body
                    200 to """{"ok":true,"company":{"id":${body.optInt("id", 99)},"name":"x","licenceKey":"NEW-KEY-0001"}}"""
                }
                "/admin/api/software-settings" -> {
                    if (post) {
                        sent += path to body
                        200 to JSONObject().put("ok", true).put("id", body.getString("software")).put("settings", JSONObject(body.toString()).apply { remove("software") }).toString()
                    } else 200 to Fixtures210.rules().toString()
                }
                "/admin/api/products" -> 200 to Fixtures210.products().toString()
                "/admin/api/plans" -> 200 to Fixtures210.plans().toString()
                "/admin/api/licences" -> 200 to Fixtures210.weight().toString()
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }

    @Test
    fun remoteActionPostsToEachSoftwaresOwnPath() {
        val sent = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        val srv = service(sent)
        runBlocking {
            val api = Api(srv.base, "NX-TEST-KEY")
            api.remoteAction(Software.ERP, JSONObject().put("action", "suspend").put("id", 42))
            api.remoteAction(Software.CONSULT, JSONObject().put("action", "resume").put("id", 52))
            api.fabric(JSONObject().put("action", "unlink").put("id", 31))
            val before = srv.hits.get()
            try {
                api.remoteAction(Software.WEIGHT, JSONObject().put("action", "suspend"))
                fail("Sales & Costing is not reached through the console service")
            } catch (e: ApiError) {
                assertEquals("refused before anything was sent", before, srv.hits.get())
            }
        }
        assertEquals(listOf("/admin/api/erp", "/admin/api/consult", "/admin/api/fabric"), sent.map { it.first })
        assertEquals("suspend", sent[0].second.getString("action"))
        assertEquals(52, sent[1].second.getInt("id"))
    }

    @Test
    fun theViewModelSendsEachChangeToTheCompanysOwnSoftware() {
        val sent = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        val srv = service(sent)
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base; key = "NX-TEST-KEY"; signedIn = true
            data = ConsoleData.from(Fixtures210.weight())
            products = ProductsData.from(Fixtures210.products()); productsLoad = Load.READY
        }
        val kiranErp = vm.remoteById(Software.ERP, 42)!!
        val kiranConsult = vm.remoteById(Software.CONSULT, 52)!!
        vm.fabricSuspend(kiranErp)
        until("erp suspend") { sent.size >= 1 }
        vm.fabricAddDays(kiranConsult, 30)
        until("consult add days") { sent.size >= 2 }
        vm.fabricSeats(42, 4, sw = Software.ERP)
        until("erp seats") { sent.size >= 3 }
        var saved: Boolean? = null
        vm.saveFabric(kiranConsult, FabricEdit.of(kiranConsult).copy(seats = "3")) { saved = it }
        until("consult save") { saved != null }
        vm.startRemote(Software.CONSULT, vm.data.companies.single { it.id == 22 }, demo = true)
        until("consult start") { sent.size >= 5 && vm.lastSaid?.text?.startsWith("Nexora Consult started") == true }

        assertEquals(listOf("/admin/api/erp", "/admin/api/consult", "/admin/api/erp", "/admin/api/consult", "/admin/api/consult"), sent.map { it.first })
        assertEquals("suspend", sent[0].second.getString("action"))
        assertEquals("days from today: what is left and 30 more", kiranConsult.daysLeft + 30, sent[1].second.getInt("days"))
        assertEquals(4, sent[2].second.getInt("seats"))
        assertEquals(3, sent[3].second.getInt("seats"))
        assertEquals(true, saved)
        val start = sent[4].second
        assertEquals("create", start.getString("action"))
        assertEquals("linked to its Sales & Costing company", 22, start.getInt("linkTo"))
        assertEquals("DEMO", start.getString("state"))
        assertEquals("7 days while the demo rules are not in hand", 7, start.getInt("days"))
        assertEquals("Nexora Consult started for Neel Raffia. Nexora Consult licence key NEW-KEY-0001 — give this to the customer; " +
            "it is not the Sales & Costing key.", vm.lastSaid?.text)
    }

    @Test
    fun aNewCustomerStartsEachTickedSoftwareInItsOwnService() {
        val f = NewCustomerForm(name = "Om Poly", gstin = "24ompol1234q1z9", weight = false)
            .let { it.withRemote(Software.ERP, it.remote(Software.ERP).copy(on = true, plan = "GROWTH", days = "30")) }
            .let { it.withRemote(Software.CONSULT, it.remote(Software.CONSULT).copy(on = true, state = "LICENSED", days = "365", seats = "2")) }
        assertEquals(listOf("erp", "consult"), f.remotesOn)
        assertFalse(f.fabric)
        val e = Requests.newRemote(f, Software.ERP, 21)
        assertEquals("GROWTH", e.getString("plan"))
        assertEquals(30, e.getInt("days"))
        assertEquals(21, e.getInt("linkTo"))
        assertEquals("24OMPOL1234Q1Z9", e.getString("gstin"))
        val c = Requests.newRemote(f, Software.CONSULT, null)
        assertEquals("LICENSED", c.getString("state"))
        assertEquals(2, c.getInt("seats"))
        assertFalse(c.has("linkTo"))
        /* Fabric Stock's own fields are the same as ever */
        val fab = NewCustomerForm(name = "x", fabric = true, fabricDays = "9")
        assertEquals(listOf("fabric"), fab.remotesOn)
        assertEquals(9, Requests.newFabric(fab, null).getInt("days"))
    }

    /* ---------------------------------------------------------------- each software's own demo rules */

    @Test
    fun theDemoRulesAreReadPerSoftwareNeverMixed() {
        val r = SoftwareRulesData.from(Fixtures210.rules(consultOk = false))
        val w = r.of(Software.WEIGHT)!!
        val f = r.of(Software.FABRIC)!!
        val e = r.of(Software.ERP)!!
        val c = r.of(Software.CONSULT)!!
        assertTrue(w.ok)
        assertEquals(7, w.trialDays)
        assertNull("Sales & Costing has no demo seats, whatever was sent", w.demoSeats)
        assertEquals(listOf(14, 2, 3), listOf(f.trialDays, f.demoGraceDays, f.demoSeats))
        assertEquals(listOf(30, 5, 8), listOf(e.trialDays, e.demoGraceDays, e.demoSeats))
        assertEquals(true, f.signupsOpen)
        assertEquals(false, e.signupsOpen)
        assertEquals("READONLY", f.expiredMode)
        assertEquals("HARDSTOP", e.expiredMode)
        assertFalse(c.ok)
        assertNull(c.trialDays)
        assertEquals(Fixtures210.CONSULT_MESSAGE, c.problem)
        assertNull(r.of(Software.JOBWORK))
        assertTrue(SoftwareRulesData.from(JSONObject("{}")).software.isEmpty())

        val vm = ConsoleViewModel(app).apply { rules = r; rulesLoad = Load.READY }
        assertEquals("a demo started from here runs as its own software says", 14, vm.demoDays(Software.FABRIC))
        assertEquals(30, vm.demoDays(Software.ERP))
        assertEquals("rules not read: 7", 7, vm.demoDays(Software.CONSULT))
    }

    @Test
    fun theChangeIsRefusedOutOfRangeBeforeTheServiceIsAsked() {
        val ok = RulesForm("30", "5", "8", true, "READONLY")
        assertNull(ok.problem(Software.ERP))
        assertEquals("Demo days must be 1 to 60.", ok.copy(trialDays = "61").problem(Software.ERP))
        assertEquals("Demo days must be 1 to 60.", ok.copy(trialDays = "0").problem(Software.CONSULT))
        assertEquals("Demo days must be 1 to 60.", ok.copy(trialDays = "").problem(Software.FABRIC))
        assertEquals("Offline days for a demo must be 0 to 30.", ok.copy(demoGraceDays = "31").problem(Software.ERP))
        assertEquals("Demo seats must be 1 to 50.", ok.copy(demoSeats = "51").problem(Software.CONSULT))
        assertEquals("Demo seats must be 1 to 50.", ok.copy(demoSeats = "0").problem(Software.FABRIC))
        assertEquals("Choose what happens when a licence ends.", ok.copy(expiredMode = "SOMETIMES").problem(Software.ERP))
        /* Sales & Costing's own limits: a year of demo, no demo seats */
        assertNull(ok.copy(trialDays = "365", demoGraceDays = "365", demoSeats = "0").problem(Software.WEIGHT))
        assertEquals("Demo days must be 1 to 365.", ok.copy(trialDays = "366").problem(Software.WEIGHT))
        assertEquals("Offline days for a demo must be 0 to 365.", ok.copy(demoGraceDays = "366").problem(Software.WEIGHT))

        /* the body: that one software, demo seats only for a remote one */
        val b = ok.body(Software.ERP)
        assertEquals("erp", b.getString("software"))
        assertEquals(30, b.getInt("trialDays"))
        assertEquals(8, b.getInt("demoSeats"))
        assertTrue(b.getBoolean("signupsOpen"))
        assertEquals("READONLY", b.getString("expiredMode"))
        assertFalse(ok.body(Software.WEIGHT).has("demoSeats"))

        /* through the view model: out of range, nothing reaches the service */
        val sent = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        val srv = service(sent)
        val vm = ConsoleViewModel(app).apply { baseUrl = srv.base; key = "NX-TEST-KEY"; signedIn = true }
        var done: Boolean? = null
        vm.saveRules(Software.ERP, ok.copy(trialDays = "90")) { done = it }
        assertEquals(false, done)
        assertEquals(0, srv.hits.get())
        assertEquals(Msg.Kind.ERR, vm.lastSaid?.kind)
        assertEquals("Demo days must be 1 to 60.", vm.lastSaid?.text)

        /* in range: one POST for that one software, and the rules are read again */
        done = null
        vm.saveRules(Software.ERP, ok.copy(trialDays = "45")) { done = it }
        until("saved") { done != null && vm.rulesLoad == Load.READY }
        assertEquals(true, done)
        assertEquals(listOf("/admin/api/software-settings"), sent.map { it.first })
        assertEquals("erp", sent.single().second.getString("software"))
        assertEquals(45, sent.single().second.getInt("trialDays"))
        assertEquals("MSME ERP's demo rules are saved on its own server.", vm.lastSaid?.text)
        assertNotNull(vm.rulesOf(Software.FABRIC))
    }

    @Test
    fun aRefusalIsShownInTheServicesOwnWords() {
        val sent = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        val srv = service(sent) { path, body ->
            if (path == "/admin/api/software-settings" && body.has("software")) 400 to """{"error":"Demo seats must be 1 to 50."}""" else null
        }
        val vm = ConsoleViewModel(app).apply { baseUrl = srv.base; key = "NX-TEST-KEY"; signedIn = true }
        var done: Boolean? = null
        vm.saveRules(Software.CONSULT, RulesForm("21", "1", "2", true, "READONLY")) { done = it }
        until("refused") { done != null }
        assertEquals(false, done)
        assertEquals(Msg.Kind.ERR, vm.lastSaid?.kind)
        assertEquals("Nexora Consult: Demo seats must be 1 to 50.", vm.lastSaid?.text)
    }

    @Test
    fun theRulesAndThePlansOfEverySoftwareAreReadWhenTheConsoleOpens() {
        val sent = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        val srv = service(sent)
        val vm = ConsoleViewModel(app).apply { baseUrl = srv.base; key = "NX-TEST-KEY" }
        vm.load()
        until("everything read") { vm.signedIn && vm.rulesLoad == Load.READY && vm.productsLoad == Load.READY && vm.plansLoad == Load.READY }
        assertEquals(4, vm.rules!!.software.size)
        assertEquals("HARDSTOP", vm.rulesOf(Software.ERP)?.expiredMode)
        assertEquals(listOf("weight", "fabric", "erp", "consult"), vm.plans!!.all.map { it.first }.distinct())
        assertEquals("Growth", vm.planName(Software.ERP, "GROWTH"))
        assertEquals("Standard", vm.planName(Software.CONSULT, null))
        assertEquals(6, vm.customers.size)
    }
}

/* ======================================================================
   2.1.0 — THE NEW SCREENS, PHOTOGRAPHED (393 × 851 dp, made-up plants).
   Run:  gradle testDebugUnitTest      → D:\nexora-console-android\screenshots\2-1-0-*.png
   ====================================================================== */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class Console210ScreensTest {

    @get:Rule val rule = createComposeRule()

    private val out = File(System.getProperty("user.dir")!!).let { if (it.name == "app") it.parentFile!! else it }
        .resolve("screenshots").apply { mkdirs() }

    private fun shot(name: String) = rule.onRoot().captureRoboImage(out.resolve("$name.png").path)

    private fun signedIn(dark: Boolean = false, consultOk: Boolean = true): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        val vm = ConsoleViewModel(app)
        vm.baseUrl = "http://127.0.0.1:9"
        vm.dark = dark
        vm.today = { LocalDate.of(2026, 11, 7) }
        vm.signedIn = true
        vm.data = ConsoleData.from(Fixtures210.weight())
        vm.products = ProductsData.from(Fixtures210.products(consultOk)); vm.productsLoad = Load.READY
        vm.plans = PlansData.from(Fixtures210.plans()); vm.plansLoad = Load.READY
        vm.rules = SoftwareRulesData.from(Fixtures210.rules(consultOk)); vm.rulesLoad = Load.READY
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = vm.dark) { AppScaffold(vm) } }
        rule.waitForIdle()
        return vm
    }

    private fun click(n: androidx.compose.ui.test.SemanticsNodeInteraction) {
        if (n.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
            n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        else n.performClick()
        rule.waitForIdle()
    }

    private fun tap(text: String) {
        if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            rule.waitForIdle()
        }
        click(rule.onAllNodesWithText(text)[0])
    }

    private fun tapInPage(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        rule.waitForIdle()
        click(rule.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollToNodeAction()))[0])
    }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
        rule.waitForIdle()
    }

    private fun afterRead(busy: () -> Boolean, then: () -> Unit) {
        val end = System.currentTimeMillis() + 15_000
        while (busy()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (System.currentTimeMillis() > end) error("the read never ended")
            Thread.sleep(10)
        }
        rule.runOnIdle(then)
        rule.waitForIdle()
    }

    @Test
    fun theDashboardAndCustomersWithEverySoftware() {
        signedIn()
        scrollTo("By Software"); shot("2-1-0-01-dashboard-every-software")
        tap("Customers"); scrollTo("Sagar Polysacks"); shot("2-1-0-02-customers-every-software")
    }

    @Test
    fun aCustomerWithErpAndConsultTabs() {
        val vm = signedIn()
        tap("Customers"); tap("Sagar Polysacks")
        tapInPage("MSME ERP")
        afterRead({ vm.fabricDetailBusy }) { vm.fabricDetailError = null }
        shot("2-1-0-03-customer-msme-erp-tab")
        tapInPage("Nexora Consult")
        afterRead({ vm.fabricDetailBusy }) { vm.fabricDetailError = null }
        shot("2-1-0-04-customer-nexora-consult-tab")
    }

    @Test
    fun aCustomerOnErpAndConsultAloneJoinedByGstin() {
        val vm = signedIn()
        tap("Customers"); scrollTo("Kiran Tarpaulins"); tap("Kiran Tarpaulins")
        afterRead({ vm.fabricDetailBusy }) { vm.fabricDetailError = null }
        shot("2-1-0-05-customer-erp-and-consult-by-gstin")
    }

    @Test
    fun theMsmeErpScreen() {
        signedIn()
        tap("MSME ERP"); shot("2-1-0-06-by-software-msme-erp")
        scrollTo("Lakshmi Fabricators"); shot("2-1-0-06-by-software-msme-erp-rows")
    }

    @Test
    fun theNexoraConsultScreenWithItsKeyNotSet() {
        signedIn(consultOk = false)
        tap("Nexora Consult"); scrollTo("Retry"); shot("2-1-0-07-by-software-consult-key-not-set")
    }

    @Test
    fun theDemoRulesCards() {
        signedIn(consultOk = false)
        tap("Plans"); tap("Demo rules"); shot("2-1-0-08-demo-rules")
        scrollTo("Nexora Consult — Demo Rules"); shot("2-1-0-08-demo-rules-rows")
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test
    fun theDemoRulesChangeDialog() {
        val vm = signedIn()
        tap("Plans"); tap("Demo rules")
        rule.runOnIdle { vm.planSoft = Software.ERP }
        rule.waitForIdle()
        /* the clock is moved by hand from here and nothing waits for idle once the dialog is open: under Robolectric its
           first field's blinking cursor never lets the idle check end. Typing in it and pressing Save is therefore not
           driven here — the refusal before the service is asked is tested on RulesForm and saveRules above */
        rule.mainClock.autoAdvance = false
        tapInPage("Change")
        rule.mainClock.advanceTimeBy(600)
        com.github.takahirom.roborazzi.captureScreenRoboImage(out.resolve("2-1-0-09-demo-rules-change.png").path)
    }

    @Test
    fun theDemoRulesDark() {
        signedIn(dark = true)
        tap("Plans"); tap("Demo rules"); scrollTo("MSME ERP — Demo Rules"); shot("2-1-0-10-demo-rules-dark")
    }
}
