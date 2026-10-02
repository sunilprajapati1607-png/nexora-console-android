package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 4.72.0 — audit #90 (console part): a paying plant whose licence ends within
 * 15 days is flagged on the dashboard, opens the company list on only those,
 * and is named on the company itself — so the call to renew is made before the
 * plant opens read-only one morning.
 *
 * Made-up companies; nothing reaches the live service (the address is a closed port).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class Ending4720Test {

    @get:Rule val rule = createComposeRule()

    private fun co(id: Int, name: String, state: String, days: Int, demo: Boolean = false, expired: Boolean = false, expires: String? = "2026-10-11T18:29:59Z") =
        JSONObject("""{"id": $id, "name": "$name", "licence_key": "NX-DEMO-000$id", "state": "$state", "seats": 2,
            "is_demo": $demo, "days_left": $days, "expired": $expired, "plan": "PRO"}""").apply {
            if (expires != null) put("expires_at", expires)
        }

    private val sample = JSONObject().put("settings", JSONObject()).put("licences", org.json.JSONArray())
        .put(
            "companies", org.json.JSONArray()
                .put(co(1, "Shree Demo Sacks", "LICENSED", 245, expires = "2027-06-01T18:29:59Z"))
                .put(co(2, "Om Poly Packs", "LICENSED", 9))
                .put(co(3, "Vijay Woven Bags", "LICENSED", 0, expires = "2026-10-02T18:29:59Z"))
                .put(co(4, "Kiran Demo Plant", "DEMO", 3, demo = true))
                .put(co(5, "Old Plant", "LICENSED", 0, expired = true, expires = "2026-09-01T18:29:59Z"))
                .put(co(6, "Paused Plant", "SUSPENDED", 5))
                .put(co(7, "Open Ended Plant", "LICENSED", 0, expires = null))
        )

    @Test
    fun whoIsEndingSoon() {
        val all = ConsoleData.from(sample).companies.associateBy { it.name }
        assertFalse("245 days is not soon", all.getValue("Shree Demo Sacks").endingSoon)
        assertTrue(all.getValue("Om Poly Packs").endingSoon)
        assertTrue("ending today, not yet expired", all.getValue("Vijay Woven Bags").endingSoon)
        assertFalse("a demo always ends within days — not flagged", all.getValue("Kiran Demo Plant").endingSoon)
        assertFalse("already ended", all.getValue("Old Plant").endingSoon)
        assertFalse("suspended", all.getValue("Paused Plant").endingSoon)
        assertFalse("no end date at all", all.getValue("Open Ended Plant").endingSoon)

        val edge = { d: Int -> Company.from(co(9, "Edge", "LICENSED", d)).endingSoon }
        assertTrue(edge(Company.ENDING_DAYS))
        assertFalse(edge(Company.ENDING_DAYS + 1))
        assertEquals("ends in 9 days", all.getValue("Om Poly Packs").endsText)
        assertEquals("ends today", all.getValue("Vijay Woven Bags").endsText)
    }

    private fun vm(): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        return ConsoleViewModel(app).apply {
            baseUrl = "http://127.0.0.1:9"
            signedIn = true
            data = ConsoleData.from(sample)
        }
    }

    @Test
    fun theViewModelListsThemSoonestFirstAndFiltersTheList() {
        val vm = vm()
        assertEquals(listOf("Vijay Woven Bags", "Om Poly Packs"), vm.endingSoon.map { it.name })
        assertEquals(7, vm.companies.size)
        vm.companyEnding = true
        assertEquals(setOf("Vijay Woven Bags", "Om Poly Packs"), vm.companies.map { it.name }.toSet())
        vm.companyEnding = false
        assertEquals(7, vm.companies.size)
    }

    @Test
    fun theDashboardFlagsThemAndOpensTheListOnThem() {
        val vm = vm()
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = false) { AppScaffold(vm) } }
        rule.waitForIdle()

        rule.onAllNodesWithText("2 Licences Ending Soon").assertCountEquals(1)
        rule.onAllNodesWithText("Vijay Woven Bags ends today · Om Poly Packs ends in 9 days — call to renew")
            .assertCountEquals(1)

        rule.onAllNodesWithText("2 Licences Ending Soon")[0].performClick()
        rule.waitForIdle()
        assertTrue(vm.companyEnding)
        rule.onAllNodesWithText("Licences ending within 15 days only").assertCountEquals(1)
        rule.onAllNodesWithText("Om Poly Packs").assertCountEquals(1)
        rule.onAllNodesWithText("Vijay Woven Bags").assertCountEquals(1)
        rule.onAllNodesWithText("Shree Demo Sacks").assertCountEquals(0)
        rule.onAllNodesWithText("Kiran Demo Plant").assertCountEquals(0)

        /* the company itself says it too */
        rule.onAllNodesWithText("Om Poly Packs")[0].performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(
            "The licence ends in 9 days (" + org.nexoraofficial.console.data.Fmt.day("2026-10-11T18:29:59Z") +
                "). After that it opens read-only — call them to renew."
        ).assertCountEquals(1)
    }

    @Test
    fun noCardWhenNothingIsEnding() {
        val vm = vm()
        vm.data = ConsoleData.from(
            JSONObject().put("companies", org.json.JSONArray().put(co(1, "Shree Demo Sacks", "LICENSED", 245, expires = "2027-06-01T18:29:59Z")))
        )
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = false) { AppScaffold(vm) } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Licence Ending Soon").assertCountEquals(0)
        assertTrue(vm.endingSoon.isEmpty())
    }
}
