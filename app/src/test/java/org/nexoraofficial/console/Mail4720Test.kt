package org.nexoraofficial.console

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.InquiryData
import org.nexoraofficial.console.data.MailAddress
import org.nexoraofficial.console.ui.InquiriesCard
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 4.72.0 — audit #41: an e-mail typed into a public form ("a@b.com?bcc=…",
 * a line break, a second address) never becomes the owner's reply to
 * somebody else. The Email button starts a mail only to one plain address.
 *
 * Made-up leads; nothing reaches the live service (the address is a closed port).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class Mail4720Test {

    @get:Rule val rule = createComposeRule()

    @Test
    fun onlyOnePlainAddressIsTaken() {
        listOf("priya@shreedemo.in", "  priya.shah+quotes@shree-demo.co.in ", "a_b@x.org", "O'Neil@plant.com")
            .forEach { assertNotNull(it, MailAddress.plain(it)) }
        assertEquals("priya@shreedemo.in", MailAddress.plain(" priya@shreedemo.in "))

        listOf(
            "a@b.com?bcc=spy@x.com",
            "a@b.com&bcc=spy@x.com",
            "a@b.com\nbcc: spy@x.com",
            "a@b.com\r\n",
            "a@b.com, spy@x.com",
            "a@b.com;spy@x.com",
            "a@b.com spy@x.com",
            "Priya <a@b.com>",
            "a%40b.com",
            "a@b.com%0Abcc=spy@x.com",
            "a@@b.com",
            "a@b",
            "a..b@x.com",
            ".a@x.com",
            "a.@x.com",
            "",
            null
        ).forEach { assertNull(it.toString(), MailAddress.plain(it)) }
    }

    @Test
    fun theMailIsAddressedAsOnePartNeverAsALinkString() {
        val i = MailAddress.compose("priya@shreedemo.in", "Nexora — Bag Weight")!!
        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("mailto", i.data!!.scheme)
        assertEquals("priya@shreedemo.in", i.data!!.schemeSpecificPart)
        assertNull("no query on the link", i.data!!.query)
        assertEquals(listOf("priya@shreedemo.in"), i.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals("Nexora — Bag Weight", i.getStringExtra(Intent.EXTRA_SUBJECT))
        assertNull(MailAddress.compose("a@b.com?bcc=spy@x.com", "x"))
    }

    private fun vm(email: String): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        return ConsoleViewModel(app).apply {
            baseUrl = "http://127.0.0.1:9"
            signedIn = true
            inquiryData = InquiryData.from(
                JSONObject().put(
                    "inquiries", org.json.JSONArray().put(
                        JSONObject().put("id", 5).put("name", "Website Visitor").put("company", "Some Plant")
                            .put("email", email).put("product", "Nexora ERP").put("state", "NEW").put("source", "WEBSITE")
                    )
                )
            )
        }
    }

    /* The enquiries list itself, as the Enquiries tab draws it, and its Email pressed. */
    private fun openEnquiriesAndPressEmail(vm: ConsoleViewModel) {
        rule.setContent { NexoraTheme(dark = false) { InquiriesCard(vm, onAsk = {}, onEdit = {}) } }
        rule.waitForIdle()
        /* whatever the test harness itself started is not what is being checked */
        val app = ApplicationProvider.getApplicationContext<Application>()
        while (shadowOf(app).nextStartedActivity != null) Unit
        rule.onAllNodesWithText("Email").assertCountEquals(1)
        rule.onAllNodesWithText("Email")[0].performClick()
        rule.waitForIdle()
    }

    @Test
    fun aHiddenBccIsRefusedAndNoMailIsStarted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vm("visitor@example.com?bcc=spy@example.net&body=send%20prices")
        openEnquiriesAndPressEmail(vm)
        assertNull("nothing may be started", shadowOf(app).nextStartedActivity)
        assertEquals(MailAddress.REFUSED, vm.msg?.text)
        assertEquals(Msg.Kind.ERR, vm.msg?.kind)
    }

    @Test
    fun aPlainAddressOpensTheMailApp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vm("visitor@example.com")
        openEnquiriesAndPressEmail(vm)
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_SENDTO, started.action)
        assertEquals("visitor@example.com", started.data!!.schemeSpecificPart)
        assertFalse(started.data.toString().contains("?"))
    }

    @Test
    fun theEnquiryFormRefusesAnAddressThatIsNotPlain() {
        val vm = vm("visitor@example.com")
        vm.newInquiry = InquiryForm(name = "Phone Lead", email = "lead@example.com\nbcc: spy@example.net")
        vm.saveInquiry()
        assertTrue(vm.msg!!.text.startsWith("The e-mail must be one plain address"))
        assertTrue("the form stays open with what was typed", vm.newInquiry.name == "Phone Lead")
    }
}
