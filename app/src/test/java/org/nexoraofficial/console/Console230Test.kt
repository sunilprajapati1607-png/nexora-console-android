package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Billing
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.InvFmt
import org.nexoraofficial.console.data.InvSheet
import org.nexoraofficial.console.data.Invoice
import org.nexoraofficial.console.data.InvoiceDraft
import org.nexoraofficial.console.data.InvoicePdf
import org.nexoraofficial.console.data.InvoiceSettings
import org.nexoraofficial.console.data.InvParty
import org.nexoraofficial.console.data.LineDraft
import org.nexoraofficial.console.data.PayQuote
import org.nexoraofficial.console.data.PaymentsData
import org.nexoraofficial.console.data.PlanForm
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.Refused
import org.nexoraofficial.console.data.Requests
import org.nexoraofficial.console.data.Software
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/* ======================================================================
   2.3.0 — A PLAN'S VALIDITY, PAYMENT BY PLAN, AND INVOICES WITH AND
   WITHOUT GST, as the desktop console 1.4 has them. What goes to the
   service is read from the bodies; every figure on an invoice is the
   service's (the phone formats, never sums); without GST there is no tax
   line; a cancel needs a reason; the PDF is a PDF. Made-up names only.
   ====================================================================== */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class Console230Test {

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

    private fun refused(what: () -> Unit): String {
        try { what() } catch (e: Refused) { return e.message.orEmpty() }
        fail("expected a refusal"); return ""
    }

    private fun vm(base: String = "http://127.0.0.1:9") = ConsoleViewModel(app).apply {
        baseUrl = base; key = "NX-TEST-KEY"; signedIn = true
        data = ConsoleData.from(Fixtures210.weight())
        products = ProductsData.from(Fixtures210.products())
        plans = PlansData.from(Fixtures230.plans())
        today = { LocalDate.of(2026, 11, 7) }
    }

    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (ok()) return
            if (System.currentTimeMillis() > end) fail("timed out: $what")
            Thread.sleep(10)
        }
    }

    /* ---------------------------------------------------------------- a plan's validity */

    @Test
    fun aPlansValidityIsReadShownAndSent() {
        val p = PlansData.from(Fixtures230.plans())
        assertEquals(730, p.plan(Software.WEIGHT, "GOLD")!!.days)
        val std = p.plan(Software.FABRIC, "STANDARD")!!
        assertNull("no days from the service", std.days)
        assertEquals("read as a year", 365, std.periodDays)

        val form = PlanForm.of(p.plan(Software.WEIGHT, "GOLD")!!)
        assertEquals("730", form.days)
        val b = Requests.planSave(Software.WEIGHT, p.plan(Software.WEIGHT, "GOLD"), form.copy(days = "180"))
        assertEquals(180, b.getInt("days"))
        assertEquals("a new plan starts at a year", "365", PlanForm().days)
        assertNull(PlanForm(days = "0").daysValue)
        assertNull(PlanForm(days = "3661").daysValue)
        assertFalse("a wrong validity is not sent", Requests.planSave(Software.WEIGHT, null, PlanForm(name = "X", days = "")).has("days"))

        val vm = vm()
        vm.savePlan(Software.WEIGHT, null, PlanForm(name = "Silver", days = "0"))
        assertTrue(vm.lastSaid!!.text.startsWith("Validity is the days in one period"))
    }

    /* ---------------------------------------------------------------- payment by plan */

    @Test
    fun theRecordPaymentFormOpensByPlanOnTheCustomersOwnPlan() {
        val vm = vm()
        val f = vm.newPaymentForm("w21", Software.WEIGHT)
        assertTrue(f.byPlan)
        assertEquals("PRO", f.plan)
        assertEquals("its seats now", 6, f.users)
        assertEquals(1, f.periods)
        assertEquals("RENEWAL", f.kind)
        assertEquals("a demo pays its first period", "NEW", vm.newPaymentForm("w22", Software.WEIGHT).kind)
    }

    @Test
    fun theQuoteAndTheAddByPlanBodies() {
        val vm = vm()
        val x = vm.customer("w21")!!
        val f = vm.newPaymentForm("w21", Software.WEIGHT).copy(users = 7, periods = 2, kind = "RENEWAL")
        val q = Billing.quote(f, x)
        assertEquals("quote", q.getString("action"))
        assertEquals("weight", q.getString("software"))
        assertEquals(21, q.getInt("companyId"))
        assertFalse(q.has("remoteId"))
        assertEquals("PRO", q.getString("plan"))
        assertEquals(7, q.getInt("users"))
        assertEquals(2, q.getInt("periods"))

        /* a remote software's licence goes as remoteId */
        val r = Billing.quote(f.copy(software = Software.ERP, plan = "growth"), x)
        assertEquals(41, r.getInt("remoteId"))
        assertEquals("GROWTH", r.getString("plan"))

        val quote = PayQuote.from(Fixtures230.quote(users = 7, periods = 2, amount = 41000))
        assertEquals(2, quote.extraUsers)
        assertEquals(730, quote.days)
        assertEquals(41000.0, quote.amount!!, 0.0)

        /* the quote's own amount is not sent back: the service works it out again */
        val a = Billing.addByPlan(f.copy(amount = "41000", paidOn = "2026-11-07", reference = "UTR 1"), x, quote)
        assertEquals("add", a.getString("action"))
        assertTrue(a.getBoolean("byPlan"))
        assertEquals(21, a.getInt("companyId"))
        assertFalse(a.has("amount"))
        assertFalse("days are the service's: plan days × periods", a.has("extendDays"))
        assertEquals("UTR 1", a.getString("reference"))

        /* typed over: sent, and it overrides */
        val t = Billing.addByPlan(f.copy(amount = "40,000", typed = true), x, quote)
        assertEquals(40000L, t.getLong("amount"))

        /* no price on the plan: the amount must be typed */
        val open = PayQuote.from(Fixtures230.quote(amount = null))
        assertEquals("This plan has no price set for that — type the amount.", refused { Billing.addByPlan(f.copy(amount = ""), x, open) })
        assertEquals(15000L, Billing.addByPlan(f.copy(amount = "15000"), x, open).getLong("amount"))
        assertEquals("The amount is still being worked out.", refused { Billing.addByPlan(f, x, null) })
    }

    @Test
    fun recordingByPlanAsksTheQuoteThenAddsAndReadsTheLicencesAgain() {
        val sent = CopyOnWriteArrayList<JSONObject>()
        val asked = CopyOnWriteArrayList<String>()
        val srv = TinyServer { target, headers ->
            val path = target.substringBefore('?')
            asked += path
            val post = (headers["content-length"]?.toIntOrNull() ?: 0) > 0
            val body = if (post) JSONObject(server?.lastBody ?: "{}") else JSONObject()
            when (path) {
                "/admin/api/payments" -> when {
                    !post -> 200 to Fixtures230.payments().toString()
                    body.getString("action") == "quote" -> { sent += body; 200 to Fixtures230.quote(users = body.getInt("users")).toString() }
                    else -> { sent += body; 200 to JSONObject().put("ok", true).put("payment", Fixtures230.payments().getJSONArray("payments").getJSONObject(0)).toString() }
                }
                "/admin/api/licences" -> 200 to Fixtures210.weight().toString()
                "/admin/api/products" -> 200 to Fixtures210.products().toString()
                "/admin/api/plans" -> 200 to Fixtures230.plans().toString()
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = vm(srv.base)
        vm.payForm = vm.newPaymentForm("w21", Software.WEIGHT).copy(users = 7)
        vm.requote(vm.payForm)
        until("quoted") { vm.quote != null }
        assertEquals("quote", sent[0].getString("action"))
        assertEquals("the amount follows the quote", "23000", vm.payForm.amount)

        var done: org.nexoraofficial.console.data.Payment? = null
        vm.recordPayment(vm.payForm) { done = it }
        until("recorded") { done != null }
        val add = sent.last()
        assertEquals("add", add.getString("action"))
        assertTrue(add.getBoolean("byPlan"))
        assertEquals(7, add.getInt("users"))
        until("the licences read again") { asked.contains("/admin/api/licences") }
    }

    /* ---------------------------------------------------------------- Invoice Settings */

    @Test
    fun invoiceSettingsAreCheckedBeforeTheyAreSent() {
        val good = InvoiceSettings.from(Fixtures230.seller())
        assertEquals("18", good.gstRate)
        val b = Billing.settingsBody(good.copy(gstin = " 24abcde1234f1z5 ", ifsc = "samp0001234"))
        assertEquals("24ABCDE1234F1Z5", b.getString("gstin"))
        assertEquals("SAMP0001234", b.getString("ifsc"))
        assertEquals(18L, b.getLong("gstRate"))
        assertEquals("Gujarat", b.getString("stateName"))

        assertEquals("Type the name that goes on the invoice.", refused { Billing.settingsBody(good.copy(sellerName = " ")) })
        assertEquals("The GSTIN must be 15 letters and digits.", refused { Billing.settingsBody(good.copy(gstin = "24ABC")) })
        assertEquals("The IFSC must look like ABCD0123456.", refused { Billing.settingsBody(good.copy(ifsc = "SAMP1001234")) })
        assertEquals("The PAN must look like AAAAA0000A.", refused { Billing.settingsBody(good.copy(pan = "12345")) })
        assertEquals("The GST rate must be 0 to 28.", refused { Billing.settingsBody(good.copy(gstRate = "30")) })
        assertEquals("The three number prefixes must be different.", refused { Billing.settingsBody(good.copy(prefixPlain = "NX")) })
        assertEquals("A number prefix is 1 to 8 letters or digits (e.g. NX).", refused { Billing.settingsBody(good.copy(prefixReceipt = "NX/R")) })

        /* a whole GSTIN typed brings its state and the PAN inside it */
        val typed = org.nexoraofficial.console.ui.withGstin(InvoiceSettings(), "27abcde1234f1z5")
        assertEquals("27", typed.stateCode)
        assertEquals("ABCDE1234F", typed.pan)
    }

    /* ---------------------------------------------------------------- making an invoice */

    @Test
    fun anInvoiceFromAPaymentSendsNoLinesAndAServiceInvoiceSendsItsOwn() {
        val vm = vm()
        vm.payments = PaymentsData.from(Fixtures230.payments())
        val d = vm.draftForPayment(vm.paymentById(41)!!)
        assertEquals("pay", d.mode)
        assertTrue("from a payment the amount includes GST", d.inclusive)
        assertEquals("2026-11-07", d.date)
        assertEquals("24", d.customer.stateCode)
        val b = Billing.createBody(d.copy(kind = "RECEIPT"))
        assertEquals("create", b.getString("action"))
        assertEquals("RECEIPT", b.getString("kind"))
        assertEquals(41, b.getInt("paymentId"))
        assertEquals(21, b.getInt("companyId"))
        assertFalse("the service makes the line from the payment", b.has("lines"))
        assertTrue(b.getBoolean("inclusive"))
        assertEquals("Sagar Polysacks", b.getJSONObject("customer").getString("name"))

        val s = vm.draftForService(vm.customer("w21")!!, Software.WEIGHT)
        assertEquals("svc", s.mode)
        assertFalse("a service's rates are before GST", s.inclusive)
        assertEquals(2, s.lines.size)
        assertTrue(s.lines[0].description.contains("Pro plan, 1 year, 5 users"))
        assertEquals("18000", s.lines[0].rate)
        assertEquals("1", s.lines[1].qty)
        val sb = Billing.createBody(s)
        assertEquals("INVOICE", sb.getString("kind"))
        assertEquals(2, sb.getJSONArray("lines").length())
        assertEquals("997331", sb.getJSONArray("lines").getJSONObject(0).getString("sac"))
        assertFalse(sb.has("paymentId"))
        val plain = Billing.createBody(s.copy(gst = false, inclusive = true))
        assertFalse("no SAC without GST", plain.getJSONArray("lines").getJSONObject(0).has("sac"))
        assertFalse("nothing is inclusive without GST", plain.getBoolean("inclusive"))

        assertEquals("Line 1: type the rate.", refused { Billing.createBody(s.copy(lines = listOf(LineDraft("Support", rate = "")))) })
        assertEquals("Line 1: the quantity must be more than 0.", refused { Billing.createBody(s.copy(lines = listOf(LineDraft("Support", qty = "0", rate = "5")))) })
        assertEquals("Choose the customer’s state — it is the place of supply.",
            refused { Billing.createBody(InvoiceDraft(paymentId = 1, customer = InvParty(name = "A"))) })
        assertEquals("The GSTIN must be 15 letters and digits (or leave it empty).",
            refused { Billing.createBody(InvoiceDraft(paymentId = 1, customer = InvParty(name = "A", gstin = "24X", stateCode = "24"))) })
    }

    /* ---------------------------------------------------------------- the invoice record */

    @Test
    fun theInvoiceRecordIsReadWhole() {
        val inv = Invoice.from(Fixtures230.invoice())
        assertEquals("101", inv.id)
        assertEquals("NX/2026-27/0007", inv.number)
        assertEquals("Tax Invoice", inv.title)
        assertEquals(41, inv.paymentId)
        assertEquals(21, inv.companyId)
        assertEquals("Lotus Softworks", inv.seller.sellerName)
        assertEquals("Maharashtra", Invoice.from(Fixtures230.invoice(intra = false)).customer.stateName)
        assertEquals(2, inv.lines.size)
        assertEquals(18000.0, inv.lines[0].amount!!, 0.0)
        assertEquals(1845.0, inv.cgst!!, 0.0)
        assertEquals(0.4, inv.roundOff!!, 0.0)
        assertTrue(inv.intraState)
        assertFalse(inv.cancelled)
        assertEquals("NX-2026-27-0007.pdf", InvFmt.fileName(inv))

        assertEquals("Bill of Supply", Invoice.from(Fixtures230.invoice(gst = false)).title)
        assertEquals("Invoice", Invoice.from(Fixtures230.invoice(gst = false, sellerGstin = "")).title)
        assertEquals("Payment Receipt", Invoice.from(Fixtures230.invoice(kind = "RECEIPT")).title)
        assertTrue(Invoice.from(Fixtures230.invoice(cancelled = true)).cancelled)
    }

    @Test
    fun theSheetShowsTheServicesFiguresNotThePhonesSums() {
        val inv = Invoice.from(Fixtures230.invoice())
        val rows = InvSheet.totalRows(inv)
        assertEquals(listOf("Taxable Value" to "20,500.00", "CGST @ 9%" to "1,845.00", "SGST @ 9%" to "1,845.00", "Round Off" to "+0.40"), rows)
        /* 20,500 + 1,845 + 1,845 + 0.40 would be 24,190.40 — the sheet says what the service said */
        assertEquals("Total" to "₹ 21,250.00", InvSheet.grand(inv))
        assertEquals("Rupees Twenty One Thousand Two Hundred Fifty Only", inv.totalWords)
        val other = Invoice.from(Fixtures230.invoice(intra = false))
        assertEquals(listOf("Taxable Value" to "20,500.00", "IGST @ 18%" to "3,690.00", "Round Off" to "+0.40"), InvSheet.totalRows(other))
        assertEquals("IGST", InvSheet.taxTable(other)!!.first[3])
        assertEquals("Amount Received" to "₹ 21,250.00", InvSheet.grand(Invoice.from(Fixtures230.invoice(kind = "RECEIPT"))))
    }

    @Test
    fun withoutGstThereIsNoTaxLineAnywhere() {
        val inv = Invoice.from(Fixtures230.invoice(gst = false))
        val rows = InvSheet.totalRows(inv)
        assertEquals(listOf("Sub Total" to "20,500.00"), rows)
        assertTrue(rows.none { it.first.contains("GST") })
        assertNull(InvSheet.taxTable(inv))
        assertNull(inv.taxSplit)
        assertTrue(InvSheet.detailRows(inv).none { it.first == "Place of Supply" || it.first == "Reverse Charge" })
        assertTrue(InvSheet.buyerRows(inv).none { it.first == "State Code" })
    }

    @Test
    fun cancellingNeedsAReasonAndKeepsTheServicesId() {
        val inv = Invoice.from(Fixtures230.invoice(id = 101))
        assertEquals("Write the reason for cancelling.", refused { Billing.cancelBody(inv, "  ") })
        assertEquals("Write the reason for cancelling.", refused { Billing.cancelBody(inv, "no") })
        val b = Billing.cancelBody(inv, " wrong customer ")
        assertEquals("cancel", b.getString("action"))
        assertEquals(101, b.getInt("id"))
        assertEquals("wrong customer", b.getString("reason"))
        assertEquals("This invoice is already cancelled.", refused { Billing.cancelBody(Invoice.from(Fixtures230.invoice(cancelled = true)), "again please") })
        assertEquals("ab-12", Billing.cancelBody(Invoice.from(Fixtures230.invoice(id = "ab-12")), "typo").getString("id"))
    }

    @Test
    fun theViewModelMakesAndCancelsThroughTheService() {
        val sent = CopyOnWriteArrayList<JSONObject>()
        var notSetUp = true
        val srv = TinyServer { target, headers ->
            val post = (headers["content-length"]?.toIntOrNull() ?: 0) > 0
            val body = if (post) JSONObject(server?.lastBody ?: "{}") else JSONObject()
            when (target.substringBefore('?')) {
                "/admin/api/invoices" -> when {
                    !post -> 200 to JSONObject().put("invoices", org.json.JSONArray().put(Fixtures230.invoice())).toString()
                    notSetUp -> 409 to """{"error":"NOT_SET_UP","message":"Fill Invoice Settings first."}"""
                    body.getString("action") == "cancel" -> { sent += body; 200 to JSONObject().put("ok", true).put("invoice", Fixtures230.invoice(cancelled = true)).toString() }
                    else -> { sent += body; 200 to JSONObject().put("ok", true).put("invoice", Fixtures230.invoice(id = 102, number = "NX/2026-27/0008")).toString() }
                }
                "/admin/api/invoice-settings" -> 200 to Fixtures230.settings(ready = !notSetUp).toString()
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = vm(srv.base)
        vm.payments = PaymentsData.from(Fixtures230.payments())
        vm.loadInvoiceSettings()
        until("settings read") { vm.invSettingsLoad == Load.READY }
        assertFalse(vm.invSettings!!.ready)

        var made: Invoice? = Invoice.from(Fixtures230.invoice())
        vm.createInvoice(vm.draftForPayment(vm.paymentById(41)!!)) { made = it }
        until("refused") { made == null }
        assertEquals(Msg.Kind.WARN, vm.lastSaid?.kind)
        assertEquals("Fill Invoice Settings first.", vm.lastSaid?.text)

        notSetUp = false
        vm.createInvoice(vm.draftForPayment(vm.paymentById(41)!!)) { made = it }
        until("made") { made != null }
        assertEquals("NX/2026-27/0008", made!!.number)
        assertEquals(41, sent[0].getInt("paymentId"))
        assertEquals("the new one is in the list", made, vm.invoiceById("102"))

        var ok = false
        /* ASCII only: TinyServer reads the body by characters, not bytes */
        vm.cancelInvoice(made!!, "wrong customer, issued again") { ok = it }
        until("cancelled") { ok }
        assertEquals("cancel", sent.last().getString("action"))
        assertTrue(vm.invoices!!.any { it.cancelled })
    }

    /* ---------------------------------------------------------------- the PDF */

    @Test
    fun thePdfIsAPdf() {
        listOf(Fixtures230.invoice(), Fixtures230.invoice(gst = false), Fixtures230.invoice(kind = "RECEIPT", cancelled = true)).forEach { o ->
            val b = InvoicePdf.bytes(Invoice.from(o))
            assertTrue("big enough to hold a page", b.size > 1000)
            assertEquals("%PDF", String(b, 0, 4, Charsets.ISO_8859_1))
            assertTrue(String(b, b.size - 8, 8, Charsets.ISO_8859_1).contains("%%EOF"))
        }
    }
}
