package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Inquiry
import org.nexoraofficial.console.data.InquiryData
import org.nexoraofficial.console.ui.InquiriesCard
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 1.8.1 (Nexora 4.73.0, C17) — the website's enquiry form now asks where the plant manufactures, its
 * website and its product range (ticks, and words beside "Other"); the enquiry card shows the three.
 *
 * Made-up leads; nothing reaches the live service (the address is a closed port).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class EnquiryFields4730Test {

    @get:Rule val rule = createComposeRule()

    /* as the service's describe() sends a form-2 enquiry */
    private val form2 = JSONObject()
        .put("id", 41).put("name", "Ramesh Patel").put("company", "Northpoint Polymers").put("phone", "+91 90000 00001")
        .put("email", "purchase@example.com").put("product", "Nexora Sales & Costing").put("state", "NEW").put("source", "WEBSITE")
        .put("location", "Vapi, Gujarat").put("website", "www.example.in")
        .put("products", JSONArray().put("BOPP bags").put("Block bottom bags").put("Other"))
        .put("productOther", "Jumbo bags (FIBC)")

    /* one from before form 2 (or typed in by hand): the service sends null for the three */
    private val older = JSONObject()
        .put("id", 40).put("name", "Kiran Mehta").put("company", "Blue River Packaging").put("phone", "+91 90000 00002")
        .put("product", "Nexora ERP").put("state", "CONTACTED").put("source", "PHONE")
        .put("location", JSONObject.NULL).put("website", JSONObject.NULL).put("products", JSONObject.NULL).put("productOther", JSONObject.NULL)

    @Test
    fun theServiceAnswerIsRead() {
        val q = Inquiry.from(form2)
        assertEquals("Vapi, Gujarat", q.location)
        assertEquals("www.example.in", q.website)
        assertEquals(listOf("BOPP bags", "Block bottom bags", "Other"), q.products)
        assertEquals("Jumbo bags (FIBC)", q.productOther)
        assertEquals(listOf("BOPP bags", "Block bottom bags", "Other: Jumbo bags (FIBC)"), q.productLines)
        assertTrue(q.hasPlantFacts)
    }

    @Test
    fun anOlderEnquiryHasNoneAndShowsNone() {
        listOf(older, JSONObject().put("id", 7).put("name", "No Fields")).forEach { o ->
            val q = Inquiry.from(o)
            assertNull(q.location)
            assertNull(q.website)
            assertEquals(emptyList<String>(), q.products)
            assertNull(q.productOther)
            assertEquals(emptyList<String>(), q.productLines)
            assertFalse(q.hasPlantFacts)
        }
    }

    @Test
    fun theTicksAreReadWhateverShapeTheyCameIn() {
        /* the array written as text (a jsonb read back as a string) */
        assertEquals(listOf("Tape", "Fabric"), Inquiry.productsOf("[\"Tape\",\"Fabric\"]"))
        /* one plain word */
        assertEquals(listOf("Tape"), Inquiry.productsOf("Tape"))
        /* broken text, a number, an object: none */
        assertEquals(emptyList<String>(), Inquiry.productsOf("[\"Tape\""))
        assertEquals(emptyList<String>(), Inquiry.productsOf(12))
        assertEquals(emptyList<String>(), Inquiry.productsOf(JSONObject().put("a", 1)))
        /* a line break is a space, a blank or a non-text item is dropped, a repeat is shown once */
        assertEquals(
            listOf("BOPP printing", "Pinch bottom bags"),
            Inquiry.productsOf(JSONArray().put("BOPP\nprinting").put("  ").put(5).put("Pinch bottom bags").put("Pinch bottom bags"))
        )
        /* the location and website are one line each too */
        val q = Inquiry.from(JSONObject(form2.toString()).put("location", "Vapi,\r\nGujarat").put("website", "  www.example.in\t"))
        assertEquals("Vapi, Gujarat", q.location)
        assertEquals("www.example.in", q.website)
    }

    @Test
    fun otherIsShownWithItsWords() {
        fun lines(products: JSONArray?, other: String?) = Inquiry.from(
            JSONObject().put("id", 1).put("name", "X").put("products", products ?: JSONObject.NULL).put("productOther", other ?: JSONObject.NULL)
        ).productLines
        assertEquals(listOf("Tape", "Other: Leno bags"), lines(JSONArray().put("Tape").put("Other"), "Leno bags"))
        assertEquals(listOf("Tape", "Other: Leno bags"), lines(JSONArray().put("Tape"), "Leno bags"))
        assertEquals(listOf("Other"), lines(JSONArray().put("Other"), null))
        assertEquals(listOf("Other"), lines(JSONArray().put("Other"), "   "))
        assertEquals(listOf("Other: Tarpaulin"), lines(null, "Tarpaulin"))
    }

    @Test
    fun theSearchFindsThem() {
        val q = Inquiry.from(form2)
        listOf("vapi", "GUJARAT", "example.in", "block bottom", "jumbo").forEach { assertTrue(it, q.matches(it)) }
        assertFalse(q.matches("tarpaulin"))
        assertFalse(Inquiry.from(older).matches("vapi"))
    }

    private fun vm(): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        return ConsoleViewModel(app).apply {
            baseUrl = "http://127.0.0.1:9"
            signedIn = true
            inquiryData = InquiryData.from(JSONObject().put("inquiries", JSONArray().put(form2).put(older)))
        }
    }

    @Test
    fun theCardShowsTheThree() {
        val vm = vm()
        rule.setContent { NexoraTheme(dark = false) { InquiriesCard(vm, onAsk = {}, onEdit = {}) } }
        rule.waitForIdle()
        /* once each: only the form-2 enquiry has them, the older card shows no empty rows */
        listOf(
            "Location", "Vapi, Gujarat", "Website", "www.example.in", "Product range",
            "BOPP bags", "Block bottom bags", "Other: Jumbo bags (FIBC)"
        ).forEach { rule.onAllNodesWithText(it).assertCountEquals(1) }
        /* the plain "Other" tick is never shown on its own beside its words */
        rule.onAllNodesWithText("Other").assertCountEquals(0)
        /* both cards are there */
        rule.onAllNodesWithText("Ramesh Patel").assertCountEquals(1)
        rule.onAllNodesWithText("Kiran Mehta").assertCountEquals(1)
    }

    @Test
    fun theWebsiteIsShownNeverOpened() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vm()
        rule.setContent { NexoraTheme(dark = true) { InquiriesCard(vm, onAsk = {}, onEdit = {}) } }
        rule.waitForIdle()
        while (shadowOf(app).nextStartedActivity != null) Unit
        rule.onAllNodesWithText("www.example.in")[0].performClick()
        rule.waitForIdle()
        assertNull("a website from a public form starts nothing", shadowOf(app).nextStartedActivity)
    }
}
