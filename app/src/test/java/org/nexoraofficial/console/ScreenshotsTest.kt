package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.GateScreen
import org.nexoraofficial.console.ui.LockScreen
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 1.6.0 — EVERY SCREEN OF THE CONSOLE, PHOTOGRAPHED (as Nexora Mobile's are).
 *
 * Drawn on the computer at a phone's size (393 × 851 dp), from made-up companies — no customer's real figures are
 * in this file. Nothing here reaches the live service (the address is a closed port).
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

    private val sample = JSONObject("""{
      "settings": {"trialDays": 7, "demoGraceDays": 0, "sessionMinutes": 30, "expiredMode": "READONLY", "signupsOpen": true},
      "aiDefaultDaily": 100000,
      "companies": [
        {"id": 3, "name": "Shree Demo Sacks", "licence_key": "NX-DEMO-0003", "state": "LICENSED", "seats": 5, "seats_used": 2, "machines_used": 3,
         "is_demo": false, "expires_at": "2027-06-01T18:29:59Z", "period_started_at": "2026-06-01T00:00:00Z", "period_days": 365, "days_left": 245,
         "txn_limit": 0, "txn_used": 412, "ai_daily_limit": 40, "ai_used_today": 12, "usage_minutes": 5230, "users_count": 2, "users_total": 2,
         "admin_names": "Priya", "plan": "PRO", "gstin": "24AAACS1429B1ZQ", "gst_status": "VERIFIED", "grace_days": 2},
        {"id": 7, "name": "Om Poly Packs", "licence_key": "NX-DEMO-0007", "state": "DEMO", "seats": 1, "seats_used": 1, "machines_used": 1,
         "is_demo": true, "expires_at": "2026-10-02T18:29:59Z", "period_started_at": "2026-09-25T00:00:00Z", "period_days": 7, "days_left": 3,
         "txn_limit": 50, "txn_used": 46, "ai_daily_limit": 0, "ai_used_today": 3, "usage_minutes": 310, "users_count": 1, "users_total": 1,
         "admin_names": "Kiran", "plan": "STANDARD", "self_registered": true},
        {"id": 9, "name": "Vijay Woven Bags", "licence_key": "NX-DEMO-0009", "state": "EXPIRED", "seats": 2, "seats_used": 0, "machines_used": 2,
         "is_demo": false, "expires_at": "2026-09-01T18:29:59Z", "period_started_at": "2025-09-01T00:00:00Z", "period_days": 365, "days_left": 0,
         "expired": true, "txn_limit": 0, "txn_used": 1880, "usage_minutes": 22110, "users_count": 2, "users_total": 3, "admin_names": "Vijay", "plan": "PRO"}
      ],
      "licences": [
        {"device_id": "c0ffee01", "device_name": "OFFICE-PC", "state": "LICENSED", "company_id": 3, "seat_no": 1, "app_version": "4.67.21",
         "last_seen_at": "2026-09-29T05:40:00Z", "co_name": "Shree Demo Sacks", "co_state": "LICENSED", "co_seats": 5, "on_user": "Priya"},
        {"device_id": "c0ffee02", "device_name": "PLANT-PC", "state": "LICENSED", "company_id": 3, "seat_no": 2, "app_version": "4.67.20",
         "last_seen_at": "2026-09-28T12:10:00Z", "co_name": "Shree Demo Sacks", "co_state": "LICENSED", "co_seats": 5},
        {"device_id": "c0ffee03", "device_name": "KIRAN-LAPTOP", "state": "DEMO", "company_id": 7, "seat_no": 1, "app_version": "4.67.19",
         "last_seen_at": "2026-09-29T04:00:00Z", "co_name": "Om Poly Packs", "co_state": "DEMO", "co_seats": 1, "on_user": "Kiran"}
      ]
    }""")

    private fun vm(dark: Boolean = false): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        val vm = ConsoleViewModel(app)
        vm.baseUrl = "http://127.0.0.1:9"
        vm.dark = dark
        return vm
    }

    private fun tap(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
        rule.waitForIdle()
    }

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

    @Test
    fun everyScreen() {
        val vm = vm()
        vm.signedIn = true
        vm.data = ConsoleData.from(sample)
        rule.setContent { NexoraTheme(dark = vm.dark) { AppScaffold(vm) } }
        rule.waitForIdle()
        shot("02-dashboard")
        tap("Companies"); shot("03-companies")
        tap("Shree Demo Sacks"); shot("04-company")
        rule.onRoot()
        tap("Enquiries"); shot("05-enquiries")
        tap("Feedback"); shot("06-feedback")
        tap("More"); shot("07-more")
    }

    @Test
    fun darkDashboard() {
        val vm = vm(dark = true)
        vm.signedIn = true
        vm.data = ConsoleData.from(sample)
        rule.setContent { NexoraTheme(dark = vm.dark) { AppScaffold(vm) } }
        rule.waitForIdle()
        shot("02-dashboard-dark")
    }

    /* 4.72.0 — audit #90: a paying licence ending within 15 days, on the dashboard, the list and the company */
    @Test
    fun aLicenceEndingSoon() {
        val vm = vm()
        vm.signedIn = true
        val soon = JSONObject(sample.toString())
        soon.getJSONArray("companies").getJSONObject(0).put("days_left", 9).put("expires_at", "2026-10-11T18:29:59Z")
        vm.data = ConsoleData.from(soon)
        rule.setContent { NexoraTheme(dark = vm.dark) { AppScaffold(vm) } }
        rule.waitForIdle()
        shot("08-ending-soon-dashboard")
        tap("Licence Ending Soon"); shot("09-ending-soon-companies")
        tap("Shree Demo Sacks"); shot("10-ending-soon-company")
    }

    /* 4.72.0 — the service of server step 3: two companies Delete has archived (kept 30 days), and the
       device-key count — made-up, like the rest */
    private val step3 = JSONObject(sample.toString()).apply {
        put("archived", JSONArray()
            .put(JSONObject("""{"id": 11, "name": "Ganesh Tarpaulins", "email": "accounts@ganesh.example", "gstin": "24AAACG5678C1Z2",
                "is_demo": false, "plan": "PRO", "seats": 3, "deleted_at": "2026-10-02T05:40:00Z", "deleted_state": "LICENSED",
                "purge_at": "2026-11-01T05:40:00Z", "days_to_purge": 30, "machines": 2, "people": 3, "records": 418}"""))
            .put(JSONObject("""{"id": 12, "name": "Laxmi Raffia", "is_demo": true, "plan": "PRO", "seats": 1,
                "deleted_at": "2026-09-08T10:15:00Z", "deleted_state": "DEMO", "purge_at": "2026-10-08T10:15:00Z",
                "days_to_purge": 6, "machines": 1, "people": 1, "records": 12}""")))
        put("keyless", JSONObject().put("devices", 2).put("required", false))
    }

    private fun signedIn(data: JSONObject, dark: Boolean = false): ConsoleViewModel {
        val vm = vm(dark)
        vm.signedIn = true
        vm.data = ConsoleData.from(data)
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = vm.dark) { AppScaffold(vm) } }
        rule.waitForIdle()
        return vm
    }

    /* 4.72.0 — audit #40: the companies with the Deleted filter, and the Deleted list with Restore */
    @Test
    fun deletedCompanies() {
        signedIn(step3)
        tap("Companies"); shot("11-companies-with-deleted")
        tap("Deleted 2"); shot("12-companies-deleted")
    }

    @Test
    fun deletedCompaniesDark() {
        signedIn(step3, dark = true)
        tap("Companies"); tap("Deleted 2"); shot("12-companies-deleted-dark")
    }

    /* (No picture of the Delete question: its type-the-name field takes focus when the dialog opens under
       Robolectric, and the blinking cursor never lets Compose go idle. Its words: Deleted4720Test.) */

    /* 4.72.0 — audit #90 (service part): "renew soon" on a licence the service counts as ending within 30 days */
    @Test
    fun renewSoon() {
        val soon = JSONObject(step3.toString())
        soon.getJSONArray("companies").getJSONObject(0)
            .put("days_left", 24).put("expires_at", "2026-10-26T18:29:59Z").put("ending_soon", true)
        signedIn(soon)
        tap("Companies"); shot("14-renew-soon-companies")
        tap("Shree Demo Sacks"); shot("15-renew-soon-company")
    }

    /* 4.72.0 — audit #97: the device-key count on the machines */
    @Test
    fun machinesKeyless() {
        signedIn(step3)
        tap("Running"); shot("16-machines-keyless")
    }

    /* 1.8.1 (Nexora 4.73.0, C17) — an enquiry from the website's new form: manufacturing location, website and
       product range (with the words written beside Other), above one from before it — made-up, like the rest */
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
    fun enquiryWithWebsiteFields() {
        val vm = signedIn(sample)
        rule.runOnIdle { vm.inquiryData = org.nexoraofficial.console.data.InquiryData.from(enquiries) }
        tap("Enquiries"); shot("17-enquiry-website-fields")
    }

    @Test
    fun enquiryWithWebsiteFieldsDark() {
        val vm = signedIn(sample, dark = true)
        rule.runOnIdle { vm.inquiryData = org.nexoraofficial.console.data.InquiryData.from(enquiries) }
        tap("Enquiries"); shot("17-enquiry-website-fields-dark")
    }

    /* ---- 1.9.0: every Nexora software in the one console — Fabric Stock beside Weight Calc, each its own
       licence. Made-up Fabric Stock companies: one with Shree's GSTIN (linked by the service), one linked by
       hand to Om Poly Packs, and two on Fabric Stock alone. ---- */

    private fun fabricCo(
        id: Int, name: String, key: String, state: String, days: Int, companyId: String?, linkedBy: String?,
        people: Int, seats: Int, devices: Int, self: Boolean = false, gstin: String? = null, plan: String = "STANDARD"
    ) = JSONObject()
        .put("id", id).put("name", name).put("licenceKey", key).put("loginId", if (id == 2) "riverside" else JSONObject.NULL)
        .put("email", if (id == 2) "office@riverside.example" else JSONObject.NULL).put("phone", JSONObject.NULL)
        .put("gstin", gstin ?: JSONObject.NULL).put("state", state).put("isDemo", state == "DEMO").put("seats", seats)
        .put("graceDays", 3).put("plan", plan)
        .put("expiresAt", java.time.Instant.parse("2026-10-07T18:29:59Z").plus(java.time.Duration.ofDays(days.toLong())).toString())
        .put("periodStartedAt", if (state == "DEMO") "2026-10-04T00:00:00.000Z" else "2026-07-01T00:00:00.000Z")
        .put("selfRegistered", self).put("notes", JSONObject.NULL).put("createdAt", "2026-10-04T05:00:00.000Z")
        .put("deletedAt", JSONObject.NULL).put("people", people).put("devices", devices).put("product", "fabric")
        .put("daysLeft", if (state == "SUSPENDED") 140 else days).put("expired", false)
        .put("periodDays", if (state == "DEMO") 7 else 365).put("shownState", state).put("endingSoon", false)
        .put("companyId", companyId ?: JSONObject.NULL).put("linkedBy", linkedBy ?: JSONObject.NULL)

    private val software = JSONObject().put("products", JSONArray()
        .put(JSONObject("""{"id":"weight","name":"Nexora Bag Weight Calculation","short":"Weight Calc","ok":true}"""))
        .put(JSONObject().put("id", "fabric").put("name", "Nexora Loom & Fabric Stock").put("short", "Fabric Stock").put("ok", true)
            .put("companies", JSONArray()
                .put(fabricCo(1, "Shree Demo Sacks", "NFS-7K2M-Q9TB-0001", "DEMO", 5, "3", "gstin", 2, 3, 2, gstin = "24AAACS1429B1ZQ"))
                .put(fabricCo(2, "Riverside Looms", "NFS-R4VX-8HPL-0002", "LICENSED", 100, null, null, 3, 5, 3, self = true, gstin = "24ABCDE1234F1Z5", plan = "PRO"))
                .put(fabricCo(4, "Om Fabrics", "NFS-OM22-T6WD-0004", "SUSPENDED", 140, "7", "hand", 1, 2, 1))
                .put(fabricCo(5, "Kaveri Weaves", "NFS-KV55-J3NC-0005", "DEMO", 4, null, null, 1, 1, 1, self = true)))))

    private val fabricAsleep = JSONObject("""{"products":[
        {"id":"weight","name":"Nexora Bag Weight Calculation","short":"Weight Calc","ok":true},
        {"id":"fabric","name":"Nexora Loom & Fabric Stock","short":"Fabric Stock","ok":false,"error":"FABRIC_DOWN",
         "message":"Fabric Stock's service did not answer in time — it may still be waking up."}]}""")

    /* one Fabric Stock company read on its own: its people and its computers and phones */
    private fun detail(id: Int, name: String) = org.nexoraofficial.console.data.FabricDetail(
        org.nexoraofficial.console.data.ProductsData.from(software).fabric!!.companies.first { it.id == id },
        listOf(
            org.nexoraofficial.console.data.FabricUser.from(JSONObject("""{"id":11,"name":"Asha Patel","email":"asha@example.com","role":"ADMIN",
                "scope":"ALL","active":true,"sessionDevice":"7f3c9a21-55de-4c1b","sessionAt":"2026-10-07T03:40:00Z",
                "lastLoginAt":"2026-10-07T03:40:00Z","lastSeenAt":"2026-10-07T05:55:00Z"}""")),
            org.nexoraofficial.console.data.FabricUser.from(JSONObject("""{"id":12,"name":"Ravi Shah","role":"USER","scope":"OWN",
                "permissions":{"stock":true,"looms":true,"reports":false},"active":true,"lastLoginAt":"2026-10-06T10:00:00Z"}"""))
        ),
        listOf(
            org.nexoraofficial.console.data.FabricDevice.from(JSONObject("""{"id":"d1","name":"STORE-PC","platform":"desktop","approved":true,
                "approvedBy":"Asha Patel","pending":false,"revokedBy":null,"lastSeen":"2026-10-07T05:55:00Z","computerNo":1,"appVersion":"0.6.0",
                "signedIn":{"id":11,"name":"Asha Patel"}}""")),
            org.nexoraofficial.console.data.FabricDevice.from(JSONObject("""{"id":"d2","name":"Ravi's phone","platform":"mobile","approved":false,
                "pending":true,"revokedBy":null,"lastSeen":"2026-10-07T04:10:00Z","appVersion":"0.6.0"}""")),
            org.nexoraofficial.console.data.FabricDevice.from(JSONObject("""{"id":"d3","name":"OLD-LAPTOP","platform":"desktop","approved":true,
                "pending":false,"revokedBy":"NEXORA","lastSeen":"2026-09-20T09:00:00Z","computerNo":2,"appVersion":"0.5.2"}"""))
        )
    ).also { check(it.company.name == name) }

    private fun withSoftware(vm: ConsoleViewModel, json: JSONObject = software) {
        rule.runOnIdle {
            vm.products = org.nexoraofficial.console.data.ProductsData.from(json)
            vm.productsLoad = Load.READY
        }
        rule.waitForIdle()
    }

    /* The company screen asks for the detail (a closed port here, so it fails at once); the made-up one is
       put in its place once that answer is in. */
    private fun showDetail(vm: ConsoleViewModel, d: org.nexoraofficial.console.data.FabricDetail) {
        val end = System.currentTimeMillis() + 15_000
        while (vm.fabricDetailBusy) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (System.currentTimeMillis() > end) error("the detail never answered")
            Thread.sleep(10)
        }
        rule.runOnIdle {
            vm.fabricDetail = d
            vm.fabricDetailError = null
        }
        rule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText(text))
        rule.waitForIdle()
    }

    @Test
    fun companiesAcrossTheSoftware() {
        val vm = signedIn(sample)
        withSoftware(vm)
        tap("Companies"); shot("18-companies-all-software")
        tap("Fabric Stock 4"); shot("19-companies-fabric-stock")
    }

    @Test
    fun companiesAcrossTheSoftwareDark() {
        val vm = signedIn(sample, dark = true)
        withSoftware(vm)
        tap("Companies"); shot("18-companies-all-software-dark")
    }

    @Test
    fun fabricStockNotConnected() {
        val vm = signedIn(sample)
        withSoftware(vm, fabricAsleep)
        tap("Companies"); shot("20-companies-fabric-not-connected")
    }

    @Test
    fun aWeightCalcCompanysFabricStockTab() {
        val vm = signedIn(sample)
        withSoftware(vm)
        tap("Companies"); tap("Shree Demo Sacks"); tap("Fabric Stock")
        showDetail(vm, detail(1, "Shree Demo Sacks"))
        shot("21-company-fabric-tab-linked")
        scrollTo("Asha Patel"); shot("21-company-fabric-tab-linked-people")
        scrollTo("OLD-LAPTOP"); shot("21-company-fabric-tab-linked-devices")
        scrollTo("What You Can Do"); shot("21-company-fabric-tab-linked-actions")
    }

    @Test
    fun aWeightCalcCompanysFabricStockTabDark() {
        val vm = signedIn(sample, dark = true)
        withSoftware(vm)
        tap("Companies"); tap("Shree Demo Sacks"); tap("Fabric Stock")
        showDetail(vm, detail(1, "Shree Demo Sacks"))
        shot("21-company-fabric-tab-linked-dark")
    }

    @Test
    fun aWeightCalcCompanyNotOnFabricStock() {
        val vm = signedIn(sample)
        withSoftware(vm)
        tap("Companies"); tap("Vijay Woven Bags"); tap("Fabric Stock")
        shot("22-company-fabric-tab-not-linked")
    }

    /* the question that links a Weight Calc company to the Fabric Stock company it already is (no search field
       with this few to choose from, so nothing takes focus) — the whole screen, the dialog being a window of its own */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test
    fun linkingAnExistingFabricStockCompany() {
        val vm = signedIn(sample)
        withSoftware(vm)
        tap("Companies"); tap("Vijay Woven Bags"); tap("Fabric Stock")
        tap("Link An Existing Fabric Stock Company")
        tap("Kaveri Weaves")
        com.github.takahirom.roborazzi.captureScreenRoboImage(out.resolve("26-link-existing-fabric-company.png").path)
    }

    @Test
    fun aFabricStockOnlyCompany() {
        val vm = signedIn(sample)
        withSoftware(vm)
        tap("Companies"); tap("Riverside Looms")
        showDetail(vm, detail(2, "Riverside Looms"))
        shot("23-fabric-only-company")
        scrollTo("What You Can Do"); shot("23-fabric-only-company-actions")
    }

    @Test
    fun theDashboardBySoftware() {
        val vm = signedIn(sample)
        withSoftware(vm)
        scrollTo("By Software"); shot("24-dashboard-by-software")
    }

    @Test
    fun theDashboardBySoftwareNotConnectedDark() {
        val vm = signedIn(sample, dark = true)
        withSoftware(vm, fabricAsleep)
        scrollTo("By Software"); shot("24-dashboard-by-software-not-connected-dark")
    }

    @Test
    fun aNewCompanyOnWhichSoftware() {
        val vm = signedIn(sample)
        withSoftware(vm)
        tap("Companies"); tap("New company")
        rule.runOnIdle { vm.newCompany = vm.newCompany.copy(software = NewSoftware.FABRIC, fabricState = "DEMO", days = "7") }
        rule.waitForIdle()
        shot("25-new-company-software")
    }
}
