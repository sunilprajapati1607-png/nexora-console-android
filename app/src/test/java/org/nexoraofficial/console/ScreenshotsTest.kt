package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
}
