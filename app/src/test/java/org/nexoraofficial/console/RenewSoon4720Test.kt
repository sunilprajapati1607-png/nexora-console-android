package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 4.72.0 — audit #90 (service part): the service marks each paying licence
 * that ends within 30 days (`ending_soon`), and the console marks it "renew
 * soon" in the list and on the company, as the web console does. The
 * dashboard's own warning stays at 15 days (Ending4720Test).
 *
 * Made-up companies; nothing reaches the live service (the address is a closed port).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class RenewSoon4720Test {

    @get:Rule val rule = createComposeRule()

    private val sample = JSONObject("""{
      "settings": {}, "licences": [], "archived": [],
      "companies": [
        {"id": 1, "name": "Shree Demo Sacks", "licence_key": "NX-DEMO-0001", "state": "LICENSED", "seats": 2, "is_demo": false,
         "expires_at": "2027-06-01T18:29:59Z", "days_left": 245, "ending_soon": false, "plan": "PRO"},
        {"id": 2, "name": "Om Poly Packs", "licence_key": "NX-DEMO-0002", "state": "LICENSED", "seats": 2, "is_demo": false,
         "expires_at": "2026-10-26T18:29:59Z", "days_left": 24, "ending_soon": true, "plan": "PRO"},
        {"id": 3, "name": "Kiran Demo Plant", "licence_key": "NX-DEMO-0003", "state": "DEMO", "seats": 1, "is_demo": true,
         "expires_at": "2026-10-05T18:29:59Z", "days_left": 3, "ending_soon": false, "plan": "PRO"}
      ]
    }""")

    @Test
    fun theServicesFlagIsRead() {
        val all = ConsoleData.from(sample).companies.associateBy { it.name }
        assertTrue(all.getValue("Om Poly Packs").renewSoon)
        assertFalse(all.getValue("Shree Demo Sacks").renewSoon)
        assertFalse("a demo runs out by design", all.getValue("Kiran Demo Plant").renewSoon)
        assertFalse("24 days is not within the dashboard's 15", all.getValue("Om Poly Packs").endingSoon)
        assertFalse("an older service sends no flag", Company.from(JSONObject("""{"id": 9, "name": "Old Plant"}""")).renewSoon)
    }

    private fun tap(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
        rule.waitForIdle()
    }

    @Test
    fun theListAndTheCompanySayRenewSoon() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        val vm = ConsoleViewModel(app).apply {
            baseUrl = "http://127.0.0.1:9"
            signedIn = true
            data = ConsoleData.from(sample)
        }
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = false) { AppScaffold(vm) } }
        rule.waitForIdle()

        /* the dashboard's warning stays at 15 days: no card for 24 */
        rule.onAllNodesWithText("Ending Soon", substring = true).assertCountEquals(0)

        tap("Companies")
        rule.onAllNodesWithText("renew soon").assertCountEquals(1)

        tap("Om Poly Packs")
        rule.onAllNodesWithText(Fmt.day("2026-10-26T18:29:59Z") + " · renew soon").assertCountEquals(1)
    }
}
