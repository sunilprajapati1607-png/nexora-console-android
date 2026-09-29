package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.GateScreen
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
}
