package org.nexoraofficial.console

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.Billing
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Invoice
import org.nexoraofficial.console.data.InvoiceSettingsData
import org.nexoraofficial.console.data.PayQuote
import org.nexoraofficial.console.data.PaymentsData
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.SoftwareRulesData
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.theme.NexoraTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/* ======================================================================
   2.3.0 — EVERY NEW SCREEN, PHONE-SIZED, FROM MADE-UP DATA: Record payment
   by plan with its quote, a plan's validity, Invoice Settings, Make invoice
   (from a payment, and a service's lines), the invoice — with GST in the
   same state and in another, without GST, a receipt, cancelled — the
   Invoices list, and one dark. Nothing is typed (Robolectric never goes idle
   after typing into a field): the state is set directly.
   Run:  gradle testDebugUnitTest      → D:\nexora-console-android\screenshots\2-3-0-*.png
   ====================================================================== */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class Console230ScreensTest {

    @get:Rule val rule = createComposeRule()

    private val out = File(System.getProperty("user.dir")!!).let { if (it.name == "app") it.parentFile!! else it }
        .resolve("screenshots").apply { mkdirs() }

    private fun shot(name: String) = rule.onRoot().captureRoboImage(out.resolve("$name.png").path)

    private fun invoices(): List<Invoice> = listOf(
        Fixtures230.invoice(id = 101, number = "NX/2026-27/0007"),
        Fixtures230.invoice(id = 102, number = "NX/2026-27/0008", intra = false, paymentId = null),
        Fixtures230.invoice(id = 103, number = "NXB/2026-27/0003", gst = false, paymentId = null),
        Fixtures230.invoice(id = 104, number = "NXR/2026-27/0002", kind = "RECEIPT"),
        Fixtures230.invoice(id = 105, number = "NX/2026-27/0005", cancelled = true, paymentId = null)
    ).map { Invoice.from(it) }

    private fun signedIn(dark: Boolean = false, withInvoices: Boolean = true): ConsoleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        val vm = ConsoleViewModel(app)
        vm.baseUrl = "http://127.0.0.1:9"
        vm.dark = dark
        vm.today = { LocalDate.of(2026, 11, 7) }
        vm.signedIn = true
        vm.data = ConsoleData.from(Fixtures210.weight())
        vm.products = ProductsData.from(Fixtures210.products()); vm.productsLoad = Load.READY
        vm.plans = PlansData.from(Fixtures230.plans()); vm.plansLoad = Load.READY
        vm.rules = SoftwareRulesData.from(Fixtures210.rules()); vm.rulesLoad = Load.READY
        vm.payments = PaymentsData.from(Fixtures230.payments()); vm.paymentsLoad = Load.READY
        vm.invSettings = InvoiceSettingsData.from(Fixtures230.settings()); vm.invSettingsLoad = Load.READY
        vm.invoices = if (withInvoices) invoices() else emptyList(); vm.invoicesLoad = Load.READY
        rule.runOnIdle { vm.lock.unlocked() }
        rule.setContent { NexoraTheme(dark = vm.dark) { AppScaffold(vm) } }
        rule.waitForIdle()
        return vm
    }

    private fun click(n: androidx.compose.ui.test.SemanticsNodeInteraction) {
        if (n.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
            n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        else n.performClick()
        rule.waitForIdle()
    }

    private fun tap(text: String) {
        if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            rule.waitForIdle()
        }
        click(rule.onAllNodesWithText(text)[0])
    }

    private fun tapInPage(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        rule.waitForIdle()
        click(rule.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollToNodeAction()))[0])
    }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
        rule.waitForIdle()
    }

    /** From the side menu. */
    private fun menu(item: String) {
        click(rule.onNodeWithContentDescription("Menu"))
        rule.onNodeWithText(item).performScrollTo()
        click(rule.onNodeWithText(item))
    }

    @Test
    fun recordPaymentByPlanWithItsQuote() {
        val vm = signedIn()
        tap("Payments"); tap("Record payment")
        rule.runOnIdle {
            val f = vm.newPaymentForm("w21", Software.WEIGHT).copy(users = 7, reference = "UTR 6021 4410")
            vm.quote = PayQuote.from(Fixtures230.quote(users = 7))
            vm.quoteFor = Billing.quoteKey(f)
            vm.payForm = f.copy(amount = "23000")
        }
        rule.waitForIdle()
        shot("2-3-0-01-record-payment-by-plan")
        scrollTo("What it comes to"); shot("2-3-0-01-record-payment-quote")
    }

    @Test
    fun aPlanWithItsValidity() {
        signedIn()
        tap("Plans"); tapInPage("Gold"); shot("2-3-0-02-plan-validity")
        tapInPage("Edit"); scrollTo("Validity"); shot("2-3-0-02-plan-edit-days")
    }

    @Test
    fun invoiceSettings() {
        signedIn()
        menu("Invoice settings"); shot("2-3-0-03-invoice-settings")
        scrollTo("Bank & UPI"); shot("2-3-0-03-invoice-settings-bank")
    }

    @Test
    fun makeAnInvoiceFromAPayment() {
        signedIn(withInvoices = false)
        tap("Payments"); tapInPage("Sagar Polysacks"); tapInPage("Invoice")
        shot("2-3-0-04-make-invoice-from-payment")
        scrollTo("Customer"); shot("2-3-0-04-make-invoice-customer")
    }

    @Test
    fun aServiceInvoicesLines() {
        signedIn()
        tap("Customers"); tapInPage("Sagar Polysacks"); tapInPage("Make invoice")
        scrollTo("Lines"); shot("2-3-0-05-service-invoice-lines")
    }

    private fun openInvoice(number: String, name: String, lower: String) {
        menu("Invoices"); tapInPage(number)
        shot(name)
        scrollTo(lower); shot("$name-totals")
    }

    @Test
    fun invoiceWithGstSameState() {
        signedIn(); openInvoice("NX/2026-27/0007", "2-3-0-06-invoice-gst-same-state", "Taxable Value")
    }

    @Test
    fun invoiceWithGstOtherState() {
        signedIn(); openInvoice("NX/2026-27/0008", "2-3-0-07-invoice-gst-other-state", "Taxable Value")
    }

    @Test
    fun invoiceWithoutGst() {
        signedIn(); openInvoice("NXB/2026-27/0003", "2-3-0-08-invoice-without-gst", "Sub Total")
    }

    @Test
    fun aReceipt() {
        signedIn(); openInvoice("NXR/2026-27/0002", "2-3-0-09-receipt", "Amount Received")
    }

    @Test
    fun aCancelledInvoice() {
        signedIn(); openInvoice("NX/2026-27/0005", "2-3-0-10-invoice-cancelled", "CANCELLED on")
    }

    @Test
    fun theInvoicesList() {
        signedIn()
        menu("Invoices"); shot("2-3-0-11-invoices")
        scrollTo("NX/2026-27/0005"); shot("2-3-0-11-invoices-rows")
    }

    @Test
    fun anInvoiceDark() {
        signedIn(dark = true); openInvoice("NX/2026-27/0007", "2-3-0-12-invoice-dark", "Taxable Value")
    }

}
