package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.CustQuick
import org.nexoraofficial.console.data.FALLBACK_FEATURES
import org.nexoraofficial.console.data.FabricDetail
import org.nexoraofficial.console.data.FabricDevice
import org.nexoraofficial.console.data.FabricUser
import org.nexoraofficial.console.data.InquiryData
import org.nexoraofficial.console.data.PaymentsData
import org.nexoraofficial.console.data.People
import org.nexoraofficial.console.data.Person
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.GateScreen
import org.nexoraofficial.console.ui.LockScreen
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * EVERY SCREEN OF THE CONSOLE, PHOTOGRAPHED (1.6.0, as Nexora Mobile's are).
 *
 * 2.0.0 — Console 2.0: by customer and by software, the customer's window
 * (DISPLAY, Edit, the features over the plan), Validity & renewals, the
 * payments, Software & plans, New customer and the side menu.
 *
 * Drawn on the computer at a phone's size (393 × 851 dp), from made-up plants
 * and people (Sunil, Tasmi, Mosam) — no customer's real figures are in this
 * file. Nothing here reaches the live service (the address is a closed port).
 *
 * Run:  gradle testDebugUnitTest      → D:\nexora-console-android\screenshots\
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreenshotsTest {

    @get:Rule val rule = createComposeRule()

    private val out = File(System.getProperty("user.dir")!!).let { if (it.name == "app") it.parentFile!! else it }
        .resolve("screenshots").apply { mkdirs() }

    private fun shot(name: String) = rule.onRoot().captureRoboImage(out.resolve("$name.png").path)

    /* ---------------------------------------------------------------- made-up data */

    private fun sc(
        id: Int, name: String, state: String, plan: String, days: Int, expires: String,
        seats: Int = 5, users: Int = 1, machines: Int = 0, demo: Boolean = false, gstin: String? = null,
        endingSoon: Boolean = false, overrides: JSONObject? = null, self: Boolean = false, started: String = "2026-02-10T00:00:00Z",
        periodDays: Int = 485, email: String? = null, phone: String? = null, login: String? = null
    ) = JSONObject()
        .put("id", id).put("name", name).put("licence_key", "NX-%04d-K7Q2".format(id)).put("state", state).put("plan", plan)
        .put("seats", seats).put("seats_used", users).put("users_total", users).put("users_count", users).put("machines_used", machines)
        .put("is_demo", demo).put("expires_at", expires).put("days_left", days).put("expired", state == "EXPIRED")
        .put("period_started_at", started).put("period_days", periodDays).put("grace_days", if (demo) 0 else 3)
        .put("txn_limit", if (demo) 50 else 0).put("txn_used", if (demo) 12 else 412).put("ai_daily_limit", if (demo) 0 else 40)
        .put("ai_used_today", 7).put("usage_minutes", 5230).put("self_registered", self).put("ending_soon", endingSoon)
        .put("created_at", "2026-10-08T05:00:00Z").put("gstin", gstin ?: JSONObject.NULL).put("gst_status", if (gstin != null) "VERIFIED" else JSONObject.NULL)
        .put("email", email ?: JSONObject.NULL).put("phone", phone ?: JSONObject.NULL).put("login_id", login ?: JSONObject.NULL)
        .put("feature_overrides", overrides ?: JSONObject.NULL)
        .apply { if (self) put("registered_at", "2026-10-03T09:00:00Z").put("registered_ip", "103.21.44.10") }

    private val sample = JSONObject()
        .put("settings", JSONObject("""{"trialDays": 7, "demoGraceDays": 0, "sessionMinutes": 30, "expiredMode": "READONLY", "signupsOpen": true}"""))
        .put("archived", JSONArray())
        .put("companies", JSONArray()
            .put(sc(3, "Riverside Sacks Pvt Ltd", "LICENSED", "STANDARD", 245, "2027-06-10T18:29:59Z", users = 3, machines = 2,
                gstin = "24ABCDE1234F1Z5", overrides = JSONObject().put("quotation", true).put("priceImpact", true).put("exportPdf", false),
                email = "office@riverside.example", phone = "+91 98250 11111", login = "riverside"))
            .put(sc(7, "Vijay Woven Bags", "LICENSED", "PRO", 21, "2026-10-29T18:29:59Z", seats = 6, gstin = "24VIJAY5678K1Z2",
                endingSoon = true, started = "2025-10-29T00:00:00Z", periodDays = 365))
            .put(sc(9, "Ganesh Polyfab", "LICENSED", "PRO", 188, "2027-04-14T18:29:59Z", gstin = "24GANES2020P1Z8", started = "2026-04-14T00:00:00Z", periodDays = 365))
            .put(sc(11, "Hillside Bags", "LICENSED", "GOLD", 340, "2027-09-13T18:29:59Z", seats = 4, started = "2026-10-02T00:00:00Z", periodDays = 346))
            .put(sc(12, "Kaveri Weaves", "SUSPENDED", "STANDARD", 90, "2027-01-06T18:29:59Z", seats = 2, gstin = "24KAVER9090W1Z1"))
            .put(sc(14, "Om Poly Packs", "DEMO", "PRO", 5, "2026-10-13T18:29:59Z", seats = 1, demo = true, self = true,
                started = "2026-10-06T00:00:00Z", periodDays = 7)))
        .put("licences", JSONArray()
            .put(JSONObject("""{"device_id": "c0ffee01", "device_name": "OFFICE-PC", "state": "LICENSED", "company_id": 3, "seat_no": 1, "app_version": "4.76.6",
                "last_seen_at": "2026-10-08T05:40:00Z", "co_name": "Riverside Sacks Pvt Ltd", "co_state": "LICENSED", "co_seats": 5, "on_user": "Sunil", "txn_count": 300, "usage_minutes": 4000}"""))
            .put(JSONObject("""{"device_id": "c0ffee02", "device_name": "PLANT-PC", "state": "LICENSED", "company_id": 3, "seat_no": 2, "app_version": "4.76.5",
                "last_seen_at": "2026-10-07T12:10:00Z", "co_name": "Riverside Sacks Pvt Ltd", "co_state": "LICENSED", "co_seats": 5, "txn_count": 112, "usage_minutes": 1230}""")))
        .put("keyless", JSONObject().put("devices", 0).put("required", false))

    private fun fabricCo(
        id: Int, name: String, state: String, days: Int, companyId: String?, linkedBy: String?,
        people: Int, seats: Int, devices: Int, self: Boolean = false, gstin: String? = null
    ) = JSONObject()
        .put("id", id).put("name", name).put("licenceKey", "NFS-%04d-T6WD".format(id)).put("loginId", if (id == 1) "riverside-fs" else JSONObject.NULL)
        .put("email", JSONObject.NULL).put("phone", JSONObject.NULL)
        .put("gstin", gstin ?: JSONObject.NULL).put("state", state).put("isDemo", state == "DEMO").put("seats", seats)
        .put("graceDays", if (state == "DEMO") 0 else 3).put("plan", "STANDARD")
        .put("expiresAt", java.time.Instant.parse("2026-10-08T18:29:59Z").plus(java.time.Duration.ofDays(days.toLong())).toString())
        .put("periodStartedAt", if (state == "DEMO") "2026-10-06T00:00:00.000Z" else "2026-02-05T00:00:00.000Z")
        .put("selfRegistered", self).put("notes", JSONObject.NULL).put("createdAt", "2026-10-06T05:00:00.000Z")
        .put("deletedAt", JSONObject.NULL).put("people", people).put("devices", devices).put("product", "fabric")
        .put("daysLeft", days).put("expired", false)
        .put("periodDays", if (state == "DEMO") 7 else 365).put("shownState", state).put("endingSoon", days in 1..30 && state == "LICENSED")
        .put("companyId", companyId ?: JSONObject.NULL).put("linkedBy", linkedBy ?: JSONObject.NULL)

    private val software = JSONObject().put("products", JSONArray()
        .put(JSONObject("""{"id":"weight","name":"Nexora Bag Weight Calculation","short":"Sales & Costing","ok":true}"""))
        .put(JSONObject().put("id", "fabric").put("name", "Nexora Loom & Fabric Stock").put("short", "Fabric Stock").put("ok", true)
            .put("companies", JSONArray()
                .put(fabricCo(1, "Riverside Sacks Pvt Ltd", "DEMO", 5, "3", "gstin", 3, 3, 3, gstin = "24ABCDE1234F1Z5"))
                .put(fabricCo(2, "Vijay Woven Bags", "LICENSED", 21, "7", "gstin", 4, 4, 2, gstin = "24VIJAY5678K1Z2"))
                .put(fabricCo(4, "Shree Loom Works", "LICENSED", 120, null, null, 6, 6, 4, gstin = "24SHREE1111L1Z3"))
                .put(fabricCo(5, "Mahalaxmi Tex", "DEMO", 2, null, null, 1, 3, 1, self = true)))))

    private val fabricAsleep = JSONObject("""{"products":[
        {"id":"weight","name":"Nexora Bag Weight Calculation","short":"Sales & Costing","ok":true},
        {"id":"fabric","name":"Nexora Loom & Fabric Stock","short":"Fabric Stock","ok":false,"error":"FABRIC_DOWN",
         "message":"Fabric Stock's service did not answer in time — it may still be waking up."}]}""")

    private fun ticks(vararg on: String) = JSONObject().apply { FALLBACK_FEATURES.forEach { put(it.id, it.id in on) } }

    private val plans = JSONObject().put("software", JSONArray()
        .put(JSONObject().put("id", "weight").put("name", "Nexora Bag Weight Calculation").put("short", "Sales & Costing")
            .put("ok", true).put("supported", true).put("demo", "A demo has every feature, whatever its plan.")
            .put("features", JSONArray().apply { FALLBACK_FEATURES.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("group", it.group)) } })
            .put("plans", JSONArray()
                .put(JSONObject().put("code", "STANDARD").put("name", "Standard").put("note", "calculation and costing")
                    .put("priceFirst", 18000).put("priceRenewal", 9000).put("usersIncluded", 1).put("extraUserPrice", 1500)
                    .put("features", ticks("exportPdf", "backup")).put("active", true).put("sort", 10).put("customers", 2).put("changed", 1).put("builtIn", true))
                .put(JSONObject().put("code", "PRO").put("name", "Pro").put("note", "everything")
                    .put("priceFirst", 35000).put("priceRenewal", 18000).put("usersIncluded", 3).put("extraUserPrice", 2000)
                    .put("features", ticks(*FALLBACK_FEATURES.map { it.id }.toTypedArray())).put("active", true).put("sort", 20).put("customers", 2).put("changed", 0).put("builtIn", true))
                .put(JSONObject().put("code", "GOLD").put("name", "Gold").put("note", "calculation, quotation and the cost tools")
                    .put("priceFirst", 25000).put("priceRenewal", 12000).put("usersIncluded", 2).put("extraUserPrice", JSONObject.NULL)
                    .put("features", ticks("quotation", "backup", "bagView", "ink", "exportExcel", "exportPdf", "priceImpact", "compare", "targetCost"))
                    .put("active", true).put("sort", 30).put("customers", 1).put("changed", 0).put("builtIn", false))
                .put(JSONObject().put("code", "STARTER").put("name", "Starter").put("note", "a first year at a lower price")
                    .put("priceFirst", JSONObject.NULL).put("priceRenewal", JSONObject.NULL).put("usersIncluded", JSONObject.NULL).put("extraUserPrice", JSONObject.NULL)
                    .put("features", ticks("exportPdf")).put("active", false).put("sort", 40).put("customers", 0).put("changed", 0).put("builtIn", false))))
        .put(JSONObject().put("id", "fabric").put("name", "Nexora Loom & Fabric Stock").put("short", "Fabric Stock").put("ok", true)
            .put("supported", false).put("features", JSONArray()).put("plans", JSONArray())
            .put("message", "Fabric Stock’s own service has no plans yet. They are made there first (in its own window); until then every Fabric Stock company is on Standard.")))

    private fun pay(id: Int, sw: String, co: Int?, fab: Int?, customer: String, plan: String, planName: String, kind: String,
                    amount: Int, paid: String, mode: String, ref: String, from: String?, to: String?) = JSONObject()
        .put("id", id).put("software", sw).put("companyId", co?.toString() ?: JSONObject.NULL).put("fabricId", fab?.toString() ?: JSONObject.NULL)
        .put("customer", customer).put("plan", plan).put("planName", planName).put("kind", kind).put("amount", amount).put("paidOn", paid)
        .put("mode", mode).put("reference", ref).put("validFrom", from ?: JSONObject.NULL).put("validTo", to ?: JSONObject.NULL)
        .put("note", JSONObject.NULL).put("createdAt", paid + "T06:30:00.000Z").put("via", if (id % 2 == 0) "android" else "web")

    private val payments = JSONObject().put("payments", JSONArray()
        .put(pay(7, "weight", 11, null, "Hillside Bags", "GOLD", "Gold", "NEW", 25000, "2026-10-02", "UPI", "UTR 9911", null, "2027-09-13"))
        .put(pay(6, "weight", 3, null, "Riverside Sacks Pvt Ltd", "STANDARD", "Standard", "EXTRA_USERS", 3000, "2026-09-01", "UPI", "UTR 4455", null, null))
        .put(pay(5, "weight", 9, null, "Ganesh Polyfab", "PRO", "Pro", "NEW", 35000, "2026-04-14", "BANK", "RTGS 5544", null, "2027-04-14"))
        .put(pay(4, "weight", 3, null, "Riverside Sacks Pvt Ltd", "STANDARD", "Standard", "NEW", 18000, "2026-02-10", "BANK", "NEFT 1102", "2026-02-10", "2027-06-10"))
        .put(pay(3, "fabric", null, 4, "Shree Loom Works", "STANDARD", "Standard", "NEW", 25000, "2026-02-05", "BANK", "NEFT 2201", null, "2027-02-05"))
        .put(pay(2, "fabric", 7, 2, "Vijay Woven Bags", "STANDARD", "Standard", "NEW", 25000, "2025-10-29", "UPI", "UTR 7788", null, "2026-10-29"))
        .put(pay(1, "weight", 7, null, "Vijay Woven Bags", "PRO", "Pro", "NEW", 35000, "2025-10-29", "CHEQUE", "CHQ 000981", "2025-10-29", "2026-10-29")))
        .put("totals", JSONObject().put("count", 7).put("amount", 166000).put("bySoftware", JSONObject().put("weight", 116000).put("fabric", 50000)))

    /* the people on Riverside's Sales & Costing licence */
    private val riversidePeople = People(
        users = listOf(
            Person(1, "Sunil", "sunil@riverside.example", "ADMIN", "ALL", true, "2026-10-08T04:10:00Z", "2026-10-08T05:40:00Z", "c0ffee01-9a77", "2026-10-08T04:10:00Z"),
            Person(2, "Tasmi", "tasmi@riverside.example", "USER", "OWN", true, "2026-10-07T09:00:00Z", "2026-10-07T12:00:00Z", null, null),
            Person(3, "Mosam", null, "USER", "OWN", true, null, null, null, null)
        ),
        capMax = 5, capCount = 3
    )

    /* Riverside's Fabric Stock company read on its own: its people and its computers and phones */
    private fun riversideFabric() = FabricDetail(
        ProductsData.from(software).fabric!!.companies.first { it.id == 1 },
        listOf(
            FabricUser.from(JSONObject("""{"id":11,"name":"Sunil","email":"sunil@riverside.example","role":"ADMIN","scope":"ALL","active":true,
                "sessionDevice":"7f3c9a21-55de-4c1b","sessionAt":"2026-10-08T03:40:00Z","lastLoginAt":"2026-10-08T03:40:00Z","lastSeenAt":"2026-10-08T05:55:00Z"}""")),
            FabricUser.from(JSONObject("""{"id":12,"name":"Tasmi","role":"USER","scope":"OWN","permissions":{"stock":true,"looms":true,"reports":false},
                "active":true,"lastLoginAt":"2026-10-07T10:00:00Z"}""")),
            FabricUser.from(JSONObject("""{"id":13,"name":"Mosam","role":"USER","scope":"OWN","permissions":["stock"],"active":true}"""))
        ),
        listOf(
            FabricDevice.from(JSONObject("""{"id":"d1","name":"STORE-PC","platform":"desktop","approved":true,"approvedBy":"Sunil","pending":false,
                "revokedBy":null,"lastSeen":"2026-10-08T05:55:00Z","computerNo":1,"appVersion":"0.8.0","signedIn":{"id":11,"name":"Sunil"}}""")),
            FabricDevice.from(JSONObject("""{"id":"d2","name":"Tasmi's phone","platform":"mobile","approved":false,"pending":true,"revokedBy":null,
                "lastSeen":"2026-10-08T04:10:00Z","appVersion":"0.8.0"}""")),
            FabricDevice.from(JSONObject("""{"id":"d3","name":"OLD-LAPTOP","platform":"desktop","approved":true,"pending":false,"revokedBy":"NEXORA",
                "lastSeen":"2026-09-20T09:00:00Z","computerNo":2,"appVersion":"0.7.2"}"""))
        )
    )

    /* ---------------------------------------------------------------- helpers */

    private fun vm(dark: Boolean = false): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        val vm = ConsoleViewModel(app)
        vm.baseUrl = "http://127.0.0.1:9"
        vm.dark = dark
        vm.today = { LocalDate.of(2026, 10, 8) }
        return vm
    }

    /** A signed-in console drawn from the made-up listing, with — unless told otherwise — the other software, the plans and the payments. */
    private fun signedIn(
        data: JSONObject = sample, dark: Boolean = false, products: JSONObject? = software,
        withPlans: Boolean = true, withPayments: Boolean = true
    ): ConsoleViewModel {
        val vm = vm(dark)
        vm.signedIn = true
        vm.data = ConsoleData.from(data)
        products?.let { vm.products = ProductsData.from(it); vm.productsLoad = Load.READY }
        if (withPlans) { vm.plans = PlansData.from(plans); vm.plansLoad = Load.READY }
        if (withPayments) { vm.payments = PaymentsData.from(payments); vm.paymentsLoad = Load.READY }
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = vm.dark) { AppScaffold(vm) } }
        rule.waitForIdle()
        return vm
    }

    /**
     * A tap. The page is scrolled back to it first when the list has let it go (a window's tools and tabs are
     * at its top), and it is pressed by its click action — a tab past the edge of the tab row cannot be touched.
     */
    private fun tap(text: String) {
        if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            rule.waitForIdle()
        }
        click(rule.onAllNodesWithText(text)[0])
    }

    /** A tap on the one inside the page (not the bottom bar's or the side menu's of the same name). */
    private fun tapInPage(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        rule.waitForIdle()
        click(rule.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollToNodeAction()))[0])
    }

    private fun click(n: androidx.compose.ui.test.SemanticsNodeInteraction) {
        if (n.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
            n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        else n.performClick()
        rule.waitForIdle()
    }

    /** Scrolls the page to the first item with [text] in it (a part of a longer text will do). */
    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
        rule.waitForIdle()
    }

    /** The side menu, from its ☰. */
    private fun openMenu() {
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.waitForIdle()
    }

    /** Waits for a read the screen started (a closed port answers at once) to end, then shows [then]. */
    private fun afterRead(busy: () -> Boolean, then: () -> Unit) {
        val end = System.currentTimeMillis() + 15_000
        while (busy()) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (System.currentTimeMillis() > end) error("the read never ended")
            Thread.sleep(10)
        }
        rule.runOnIdle(then)
        rule.waitForIdle()
    }

    private fun openRiverside(vm: ConsoleViewModel) {
        tap("Customers")
        tap("Riverside Sacks Pvt Ltd")
    }

    /* ---------------------------------------------------------------- the lock and the gate */

    /* 1.8.0 — the app lock, both faces: the usual one, and a phone with no screen lock */
    @Test
    fun theLock() {
        rule.setContent {
            NexoraTheme(dark = false) {
                LockScreen(screenLockSet = true, asking = false, problem = null, onUnlock = {}, onSetScreenLock = {})
            }
        }
        rule.waitForIdle()
        shot("00-lock")
    }

    @Test
    fun theLockWithoutAScreenLock() {
        rule.setContent {
            NexoraTheme(dark = true) {
                LockScreen(screenLockSet = false, asking = false, problem = null, onUnlock = {}, onSetScreenLock = {})
            }
        }
        rule.waitForIdle()
        shot("00-lock-no-screen-lock-dark")
    }

    @Test
    fun theGate() {
        val vm = vm()
        rule.setContent { NexoraTheme(dark = vm.dark) { GateScreen(vm) } }
        rule.waitForIdle()
        shot("01-gate")
    }

    /* ---------------------------------------------------------------- dashboard and side menu */

    @Test
    fun theDashboard() {
        signedIn()
        shot("02-dashboard")
        scrollTo("Ending Within 30 Days"); shot("02-dashboard-ending-and-payments")
        scrollTo("Latest Payments"); shot("02-dashboard-latest-payments")
    }

    @Test
    fun theDashboardDark() {
        signedIn(dark = true)
        scrollTo("By Software"); shot("02-dashboard-dark")
    }

    @Test
    fun theSideMenu() {
        val vm = signedIn()
        rule.runOnIdle { vm.inquiryData = InquiryData.from(enquiries) }
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.waitForIdle()
        shot("03-side-menu")
    }

    /* ---------------------------------------------------------------- customers, by customer */

    @Test
    fun theCustomers() {
        val vm = signedIn()
        tap("Customers"); shot("10-customers")
        scrollTo("Kaveri Weaves"); shot("10-customers-rows")
        rule.runOnIdle { vm.customerQuick = CustQuick.SOON }
        rule.waitForIdle()
        shot("11-customers-renew-in-30-days")
    }

    @Test
    fun theCustomersDark() {
        signedIn(dark = true)
        tap("Customers"); scrollTo("Riverside Sacks Pvt Ltd"); shot("10-customers-dark")
    }

    @Test
    fun theCustomersWithFabricStockAsleep() {
        signedIn(products = fabricAsleep)
        tap("Customers"); shot("13-customers-fabric-not-connected")
    }

    /* 4.72.0 — the service of server step 3: two companies Delete has archived (kept 30 days) — made-up, like the rest */
    private val step3 = JSONObject(sample.toString()).apply {
        put("archived", JSONArray()
            .put(JSONObject("""{"id": 21, "name": "Ganesh Tarpaulins", "email": "accounts@ganesh.example", "gstin": "24AAACG5678C1Z2",
                "is_demo": false, "plan": "PRO", "seats": 3, "deleted_at": "2026-10-02T05:40:00Z", "deleted_state": "LICENSED",
                "purge_at": "2026-11-01T05:40:00Z", "days_to_purge": 24, "machines": 2, "people": 3, "records": 418}"""))
            .put(JSONObject("""{"id": 22, "name": "Laxmi Raffia", "is_demo": true, "plan": "PRO", "seats": 1,
                "deleted_at": "2026-09-14T10:15:00Z", "deleted_state": "DEMO", "purge_at": "2026-10-14T10:15:00Z",
                "days_to_purge": 6, "machines": 1, "people": 1, "records": 12}""")))
        put("keyless", JSONObject().put("devices", 2).put("required", false))
    }

    @Test
    fun deletedCustomers() {
        signedIn(step3)
        tap("Customers"); tap("Deleted 2"); shot("12-customers-deleted")
    }

    @Test
    fun deletedCustomersDark() {
        signedIn(step3, dark = true)
        tap("Customers"); tap("Deleted 2"); shot("12-customers-deleted-dark")
    }

    /* 4.72.0 — audit #90: a paying licence ending within 15 days, from the dashboard's card to the list and the customer */
    @Test
    fun aLicenceEndingSoon() {
        val soon = JSONObject(sample.toString())
        soon.getJSONArray("companies").getJSONObject(1).put("days_left", 9).put("expires_at", "2026-10-17T18:29:59Z")
        signedIn(soon)
        tap("Licence Ending Soon"); shot("14-ending-soon-customers")
        tap("Vijay Woven Bags"); shot("14-ending-soon-customer")
    }

    /* ---------------------------------------------------------------- one customer's window */

    @Test
    fun aCustomersWindow() {
        val vm = signedIn()
        openRiverside(vm); shot("20-customer-licence")
        scrollTo("Hours in use"); shot("20-customer-licence-figures")
        tapInPage("Edit"); scrollTo("Save sends each change"); shot("21-customer-licence-edit")
    }

    @Test
    fun aCustomersWindowDark() {
        val vm = signedIn(dark = true)
        openRiverside(vm); shot("20-customer-licence-dark")
    }

    @Test
    fun aCustomersFeatures() {
        val vm = signedIn()
        openRiverside(vm)
        tap("Features"); shot("22-customer-features")
        scrollTo("Backup & restore"); shot("22-customer-features-list")
        tapInPage("Edit")
        scrollTo("Compare calculations"); tap("Compare calculations"); tap("Activity log")
        shot("23-customer-features-edit")
        scrollTo("Back to the plan only"); shot("23-customer-features-edit-summary")
    }

    @Test
    fun aCustomersFeaturesDark() {
        val vm = signedIn(dark = true)
        openRiverside(vm)
        tap("Features"); scrollTo("Export to PDF"); shot("22-customer-features-dark")
    }

    @Test
    fun aCustomersPeopleComputersPaymentsAndMore() {
        val vm = signedIn()
        openRiverside(vm)
        tap("People")
        afterRead({ vm.peopleBusy }) { vm.people = riversidePeople; vm.peopleError = null }
        scrollTo("Sunil"); shot("24-customer-people")
        tap("Computers & phones"); scrollTo("OFFICE-PC"); shot("25-customer-computers")
        tapInPage("Payments"); shot("26-customer-payments")
        tap("More"); scrollTo("Stop Them"); shot("27-customer-more")
    }

    @Test
    fun aCustomersFabricStock() {
        val vm = signedIn()
        openRiverside(vm)
        tapInPage("Fabric Stock")
        afterRead({ vm.fabricDetailBusy }) { vm.fabricDetail = riversideFabric(); vm.fabricDetailError = null }
        shot("28-customer-fabric-licence")
        tap("People"); scrollTo("Tasmi"); shot("29-customer-fabric-people")
        tap("Computers & phones"); scrollTo("OLD-LAPTOP"); shot("29-customer-fabric-computers")
        tap("Company"); shot("29-customer-fabric-company")
    }

    @Test
    fun aCustomerOnFabricStockAlone() {
        signedIn()
        tap("Customers"); scrollTo("Shree Loom Works"); tap("Shree Loom Works")
        tapInPage("+ Add — not taken"); shot("30-customer-fabric-only-no-sales-costing")
    }

    @Test
    fun aCustomerNotOnFabricStock() {
        signedIn()
        tap("Customers"); scrollTo("Ganesh Polyfab"); tap("Ganesh Polyfab")
        tapInPage("+ Add — not taken"); shot("31-customer-not-on-fabric-stock")
    }

    /* the question that links a Sales & Costing company to the Fabric Stock company it already is — the whole screen,
       the dialog being a window of its own (few to choose from, so no search field takes focus) */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test
    fun linkingAnExistingFabricStockCompany() {
        signedIn()
        tap("Customers"); scrollTo("Ganesh Polyfab"); tap("Ganesh Polyfab")
        tapInPage("+ Add — not taken")
        tap("Link An Existing Fabric Stock Company")
        tap("Mahalaxmi Tex")
        com.github.takahirom.roborazzi.captureScreenRoboImage(out.resolve("32-link-existing-fabric-company.png").path)
    }

    /* ---------------------------------------------------------------- validity, payments */

    @Test
    fun theValidity() {
        val vm = signedIn()
        rule.runOnIdle { vm.validityQuery = "" }
        openMenu(); tapDrawer("Validity & renewals"); shot("40-validity")
        scrollTo("Hillside Bags"); shot("40-validity-rows")
    }

    @Test
    fun theValidityDark() {
        signedIn(dark = true)
        openMenu(); tapDrawer("Validity & renewals"); scrollTo("Mahalaxmi Tex"); shot("40-validity-dark")
    }

    @Test
    fun thePayments() {
        signedIn()
        tap("Payments"); shot("41-payments")
        scrollTo("Ganesh Polyfab"); shot("41-payments-rows")
        tap("Ganesh Polyfab"); shot("42-payment")
        tapInPage("Edit"); shot("43-payment-edit")
    }

    @Test
    fun recordingAPayment() {
        val vm = signedIn()
        openRiverside(vm)
        tapInPage("Record payment")
        rule.runOnIdle { vm.payForm = vm.payForm.copy(amount = "9,000", reference = "UTR 6021 4410", kind = "RENEWAL") }
        rule.waitForIdle()
        shot("44-record-payment")
        scrollTo("Record the payment"); shot("44-record-payment-lower")
    }

    /* ---------------------------------------------------------------- software and plans */

    @Test
    fun thePlans() {
        signedIn()
        tap("Plans"); shot("50-plans")
        scrollTo("Starter"); shot("50-plans-rows")
        tap("Gold"); shot("51-plan")
        scrollTo("Target Cost"); shot("51-plan-features")
    }

    @Test
    fun aPlansCustomersAndEdit() {
        signedIn()
        tap("Plans"); tap("Standard")
        tapInPage("Customers"); shot("52-plan-customers")
        tapInPage("Features"); tapInPage("Edit"); shot("53-plan-edit")
    }

    @Test
    fun aNewPlan() {
        signedIn()
        tap("Plans"); tap("+ New Sales & Costing plan")
        tap("Gold")      /* start from Gold's ticks */
        shot("54-new-plan")
    }

    /* ---------------------------------------------------------------- by software */

    @Test
    fun bySoftwareSalesAndCosting() {
        val vm = signedIn()
        rule.runOnIdle { vm.lock.unlocked() }
        openMenu(); tapDrawer("Sales & Costing"); shot("60-by-software-sales-costing")
        scrollTo("Hillside Bags"); shot("60-by-software-sales-costing-rows")
        rule.runOnIdle { vm.setSwView(Software.WEIGHT, vm.swView(Software.WEIGHT).copy(tab = "plans")) }
        rule.waitForIdle(); scrollTo("Gold"); shot("61-by-software-sales-costing-plans")
        rule.runOnIdle { vm.setSwView(Software.WEIGHT, vm.swView(Software.WEIGHT).copy(tab = "payments")) }
        rule.waitForIdle(); scrollTo("Hillside Bags"); shot("62-by-software-sales-costing-payments")
    }

    @Test
    fun bySoftwareFabricStock() {
        signedIn()
        openMenu(); tapDrawer("Fabric Stock"); shot("63-by-software-fabric-stock")
        scrollTo("Mahalaxmi Tex"); shot("63-by-software-fabric-stock-rows")
    }

    @Test
    fun bySoftwareFabricStockNotConnectedDark() {
        signedIn(dark = true, products = fabricAsleep)
        openMenu(); tapDrawer("Fabric Stock"); scrollTo("Retry"); shot("64-by-software-fabric-not-connected-dark")
    }

    /** A row of the side menu (it is drawn whether open or not; the page's own of the same name comes first). */
    private fun tapDrawer(text: String) {
        click(rule.onAllNodesWithText(text).let { it[it.fetchSemanticsNodes().size - 1] })
        rule.waitForIdle()
    }

    /* ---------------------------------------------------------------- new customer */

    @Test
    fun aNewCustomer() {
        val vm = signedIn()
        tap("Customers"); tap("New customer")
        rule.runOnIdle {
            vm.newCustomer = vm.newCustomer.copy(name = "Sunrise Polysacks", gstin = "24SUNRS4455Q1Z9", email = "accounts@sunrise.example",
                phone = "+91 90000 22222", admin = "Tasmi", weightPlan = "GOLD", fabric = true)
        }
        rule.waitForIdle()
        shot("70-new-customer")
        scrollTo("Create the customer"); shot("70-new-customer-software")
    }

    /* ---------------------------------------------------------------- what stayed as it was */

    /* 1.8.1 (Nexora 4.73.0, C17) — an enquiry from the website's form: manufacturing location, website and product range */
    private val enquiries = JSONObject("""{"inquiries": [
      {"id": 41, "name": "Ramesh Patel", "company": "Northpoint Polymers", "phone": "+91 90000 00001", "email": "purchase@example.com",
       "product": "Nexora Sales & Costing", "message": "We run 24 circular looms and want to forecast RM cost per order.",
       "state": "NEW", "source": "WEBSITE", "channel": "whatsapp", "createdAt": "2026-10-02T06:10:00Z",
       "location": "Vapi, Gujarat", "website": "www.example.in",
       "products": ["BOPP bags", "Block bottom bags", "Pinch bottom bags", "Other"], "productOther": "Jumbo bags (FIBC)"},
      {"id": 40, "name": "Kiran Mehta", "company": "Blue River Packaging", "phone": "+91 90000 00002", "product": "Nexora ERP",
       "state": "CONTACTED", "source": "PHONE", "createdAt": "2026-09-30T09:00:00Z", "followUp": "2026-10-05",
       "location": null, "website": null, "products": null, "productOther": null}
    ]}""")

    @Test
    fun enquiriesFeedbackMachinesAndMore() {
        val vm = signedIn(step3)
        rule.runOnIdle { vm.inquiryData = InquiryData.from(enquiries) }
        openMenu(); tapDrawer("Enquiries"); shot("80-enquiries")
        openMenu(); tapDrawer("Feedback"); shot("81-feedback")
        openMenu(); tapDrawer("More tools"); shot("82-more-tools")
        openMenu(); tapDrawer("Machines"); shot("83-machines-keyless")
    }

    @Test
    fun enquiriesDark() {
        val vm = signedIn(dark = true)
        rule.runOnIdle { vm.inquiryData = InquiryData.from(enquiries) }
        openMenu(); tapDrawer("Enquiries"); shot("80-enquiries-dark")
    }
}
