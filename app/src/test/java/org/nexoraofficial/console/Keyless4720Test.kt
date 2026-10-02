package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Keyless
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 4.72.0 — audit #97 (console part): the listing counts the computers and
 * phones in use that hold no device key yet (`keyless {devices, required}`),
 * and the Machines screen says it in one line — so the owner sees when the
 * rollout is complete and NEXORA_DEVICE_KEY_REQUIRED=1 can be set on Render.
 *
 * Made-up figures; nothing reaches the live service (the address is a closed port).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class Keyless4720Test {

    @get:Rule val rule = createComposeRule()

    @Test
    fun theNoteSaysWhatIsLeft() {
        assertEquals(
            "Device keys: 3 computers and phones in use hold none yet — each hands its key over at its next check " +
                "on Nexora 4.71.0 / Mobile 1.0.0 or later.",
            Keyless(3, required = false).note
        )
        assertEquals(
            "Device keys: 1 computer or phone in use holds none yet — each hands its key over at its next check " +
                "on Nexora 4.71.0 / Mobile 1.0.0 or later.",
            Keyless(1, required = false).note
        )
        assertEquals(
            "Device keys: 2 computers and phones in use hold none yet, and NEXORA_DEVICE_KEY_REQUIRED is set — one " +
                "that has also lost its token must join again.",
            Keyless(2, required = true).note
        )
        assertEquals(
            "Device keys: every computer and phone in use holds its own — set NEXORA_DEVICE_KEY_REQUIRED=1 on Render " +
                "to stop a device id alone re-joining.",
            Keyless(0, required = false).note
        )
        assertEquals(
            "Device keys: every computer and phone in use holds its own, and NEXORA_DEVICE_KEY_REQUIRED is set.",
            Keyless(0, required = true).note
        )
    }

    @Test
    fun readFromTheListing() {
        val d = ConsoleData.from(JSONObject("""{"companies":[],"licences":[],"keyless":{"devices":4,"required":true}}"""))
        assertEquals(Keyless(4, true), d.keyless)
        assertEquals(Keyless(0, false), ConsoleData.from(JSONObject("""{"keyless":{"devices":-2}}""")).keyless)
        assertNull("an older service does not count them", ConsoleData.from(JSONObject("""{"companies":[],"licences":[]}""")).keyless)
    }

    private fun machines(listing: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        val vm = ConsoleViewModel(app).apply {
            baseUrl = "http://127.0.0.1:9"
            signedIn = true
            data = ConsoleData.from(JSONObject(listing))
        }
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = false) { AppScaffold(vm) } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Running")[0].performClick()      // the dashboard tile opens the machines
        rule.waitForIdle()
        rule.onAllNodesWithText("Installations").assertCountEquals(1)
    }

    @Test
    fun theMachinesScreenShowsIt() {
        machines("""{"companies":[],"licences":[],"keyless":{"devices":2,"required":false}}""")
        rule.onAllNodesWithText(Keyless(2, false).note).assertCountEquals(1)
    }

    @Test
    fun anOlderServiceShowsNoNote() {
        machines("""{"companies":[],"licences":[]}""")
        rule.onAllNodesWithText("Device keys:", substring = true).assertCountEquals(0)
    }
}
