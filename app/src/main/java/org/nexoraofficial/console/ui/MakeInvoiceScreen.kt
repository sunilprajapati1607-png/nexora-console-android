package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.data.Customer
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.GstStates
import org.nexoraofficial.console.data.InvParty
import org.nexoraofficial.console.data.InvoiceDraft
import org.nexoraofficial.console.data.LineDraft
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.Payment
import org.nexoraofficial.console.data.PaymentKinds
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.3.0 — MAKE AN INVOICE (the desktop console 1.4's invoice-make.js).

   · From a payment (the payment's window, after Record payment): Tax
     invoice or Receipt, with or without GST, the amount includes GST (on by
     default), the date, the customer (filled from the customer, editable),
     notes. The service makes the line from the payment.
   · For a service (customer window → a software's tab → Make invoice): lines
     from the plan — description, SAC, qty, rate — added and removed; with or
     without GST, rates including GST off by default.
   The phone sends what was chosen; the service numbers it and does all the
   money maths.
   ====================================================================== */

/** Invoice Settings are not filled: say so, and offer to open them. */
private fun fillFirst(nav: Navigator) = Ask.Confirm(
    "Fill Invoice Settings first",
    "Your company’s name, address, GSTIN and bank go on every invoice. Fill Invoice Settings once, then make the invoice.",
    "Open Invoice Settings"
) { nav.open(Screen.InvoiceSettings) }

/** Make the invoice or receipt of [p] — or open the one it has, or make another. */
fun startPaymentInvoice(vm: ConsoleViewModel, nav: Navigator, onAsk: (Ask) -> Unit, p: Payment) {
    if (vm.invoicesLoad == Load.IDLE) vm.loadInvoices()
    vm.whenInvoiceReady { ready ->
        if (!ready) return@whenInvoiceReady onAsk(fillFirst(nav))
        val had = vm.invoicesFor(p.id).filter { !it.cancelled }
        val make = { vm.invDraft = vm.draftForPayment(p); nav.open(Screen.MakeInvoice) }
        if (had.isEmpty()) make()
        else onAsk(Ask.Pick(
            title = "Already invoiced",
            body = "This payment already has ${had[0].kindWord} ${had[0].number}. Open it, or make another (for example a receipt)?",
            options = listOf(
                Ask.Pick.Option(0, "Open ${had[0].number}", had[0].kindWord + " · ₹ " + org.nexoraofficial.console.data.InvFmt.f2(had[0].total)),
                Ask.Pick.Option(1, "Make another", "a receipt, or an invoice without GST")
            ),
            confirmText = "Go",
            onPick = { i -> if (i == 0) nav.open(Screen.InvoiceWin(had[0].id)) else make() }
        ))
    }
}

/** Make a service invoice for [x]'s licence on [sw], its lines from the plan. */
fun startServiceInvoice(vm: ConsoleViewModel, nav: Navigator, onAsk: (Ask) -> Unit, x: Customer, sw: String) {
    if (vm.invoicesLoad == Load.IDLE) vm.loadInvoices()
    vm.whenInvoiceReady { ready ->
        if (!ready) onAsk(fillFirst(nav)) else { vm.invDraft = vm.draftForService(x, sw); nav.open(Screen.MakeInvoice) }
    }
}

@Composable
fun MakeInvoiceScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    val d = vm.invDraft
    val set = { n: InvoiceDraft -> vm.invDraft = n }
    val receipt = d.fromPayment && d.kind == "RECEIPT"
    val s = vm.invSettings?.settings
    val create = { vm.createInvoice(vm.invDraft) { inv -> if (inv != null) nav.replace(Screen.InvoiceWin(inv.id)) } }
    val prefix = when {
        receipt -> s?.prefixReceipt ?: "NXR"
        d.gst -> s?.prefixGst ?: "NX"
        else -> s?.prefixPlain ?: "NXB"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) {
                WindowHead("▤", listOf(ToolColours.blue, ToolColours.violet),
                    if (d.fromPayment) "Make Invoice for a Payment" else "Make Service Invoice",
                    d.customer.name.ifBlank { "—" } + " · " + Software.name(d.software), "NEW")
            }
        }
        item(key = "tools") {
            Box(page) {
                ToolStrip(listOf(
                    Tool(if (receipt) "Create receipt" else "Create invoice", Icons.AutoMirrored.Outlined.ReceiptLong, ToolColours.green, !vm.busy) { create() },
                    Tool("Cancel", Icons.Outlined.Close, ToolColours.back) { nav.back() }
                ))
            }
        }
        item(key = "doc") {
            Box(page) {
                Group("Document") {
                    if (d.fromPayment) {
                        ChoiceField("Document", listOf("INVOICE" to "Tax invoice / bill", "RECEIPT" to "Payment receipt"), d.kind) { set(d.copy(kind = it)) }
                        Spacer(Modifier.height(10.dp))
                    }
                    ChoiceField("GST", listOf("1" to "With GST", "0" to "Without GST"), if (d.gst) "1" else "0") { set(d.copy(gst = it == "1")) }
                    Spacer(Modifier.height(10.dp))
                    DateField((if (receipt) "Receipt" else "Invoice") + " date", d.date, Modifier.fillMaxWidth(), onAsk = onAsk) { set(d.copy(date = it)) }
                    Spacer(Modifier.height(6.dp))
                    if (d.gst) ConsoleCheck(d.inclusive, { set(d.copy(inclusive = it)) },
                        if (d.fromPayment) "The amount received includes GST" else "The rates include GST",
                        supporting = if (d.fromPayment) "the service takes the tax out of it" else "off: GST is added on top of the rates")
                    else Help(if (receipt) "A receipt without a GST break-up."
                        else "No GST is charged: the sheet is a " + (if (s?.gstin.isNullOrBlank()) "plain Invoice" else "Bill of Supply") + " with no tax lines.")
                }
            }
        }
        item(key = "mid") {
            Box(page) {
                if (d.fromPayment) PaymentFacts(vm.paymentById(d.paymentId ?: -1)) else LineEditor(d, set, s?.sac ?: "997331")
            }
        }
        item(key = "customer") {
            Box(page) {
                Group("Customer") { PartyFields(d.customer, d.gst, onAsk) { set(d.copy(customer = it)) } }
            }
        }
        item(key = "notes") {
            Box(page) {
                Group("Notes") {
                    ConsoleField("Notes (printed on the ${if (receipt) "receipt" else "invoice"})", d.notes, { set(d.copy(notes = it)) }, singleLine = false)
                }
            }
        }
        item(key = "go") {
            Box(page) {
                Column {
                    Help("The service gives the number ($prefix/…) — an invoice can later be cancelled, never changed.", Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConsoleButton(if (receipt) "Create receipt" else "Create invoice", create, kind = ButtonKind.Primary, enabled = !vm.busy,
                            modifier = Modifier.weight(1f), center = true)
                        ConsoleButton("Cancel", { nav.back() }, modifier = Modifier.weight(1f), center = true)
                    }
                }
            }
        }
    }
}

/** What the payment was: the line is made from it by the service. */
@Composable
private fun PaymentFacts(p: Payment?) {
    Group("The payment") {
        if (p == null) { Help("That payment is no longer in the list."); return@Group }
        FieldPair({ ReadField("Amount received", Money.rupees(p.amount), it) }, { ReadField("Paid on", Fmt.day(p.paidOn), it) })
        Spacer(Modifier.height(8.dp))
        FieldPair({ ReadField("Software", p.softwareName, it) }, { ReadField("Plan · for", (p.planName ?: p.plan ?: "—") + " · " + PaymentKinds.word(p.kind), it) })
        Spacer(Modifier.height(8.dp))
        Help("The line is made from this payment by the service" + (if (p.validTo != null) " (validity ${p.validityText})" else "") + ".")
    }
}

/** A service invoice's lines: what, SAC (with GST), quantity, rate — added and removed. */
@Composable
private fun LineEditor(d: InvoiceDraft, set: (InvoiceDraft) -> Unit, sac: String) {
    val c = LocalNexora.current
    Group("Lines") {
        d.lines.forEachIndexed { i, l ->
            val put = { n: LineDraft -> set(d.copy(lines = d.lines.toMutableList().also { it[i] = n })) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Line ${i + 1}", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (d.lines.size > 1) ConsoleButton("Remove", { set(d.copy(lines = d.lines.filterIndexed { n, _ -> n != i })) }, kind = ButtonKind.Danger, small = true)
            }
            Spacer(Modifier.height(4.dp))
            ConsoleField("Description", l.description, { put(l.copy(description = it)) }, singleLine = false)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.gst) ConsoleField("SAC", l.sac, { put(l.copy(sac = it.filter { ch -> ch.isDigit() })) }, Modifier.weight(1f), numeric = true)
                ConsoleField("Qty", l.qty, { put(l.copy(qty = it.filter { ch -> ch.isDigit() || ch == '.' })) }, Modifier.weight(0.8f), numeric = true)
                ConsoleField("Rate (₹)", l.rate, { put(l.copy(rate = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' })) }, Modifier.weight(1.3f), numeric = true)
            }
            Spacer(Modifier.height(12.dp))
        }
        ConsoleButton("+ Add line", { set(d.copy(lines = d.lines + LineDraft(sac = sac))) }, small = true)
        Spacer(Modifier.height(6.dp))
        Help("Rates are filled from the plan’s price — type over them. The tax and the total are worked out by the service on Create.")
    }
}

/** The customer on the invoice: name, GSTIN (its state follows), address, state (the place of supply with GST), e-mail, phone. */
@Composable
internal fun PartyFields(cu: InvParty, gst: Boolean, onAsk: (Ask) -> Unit, onChange: (InvParty) -> Unit) {
    ConsoleField("Name", cu.name, { onChange(cu.copy(name = it)) })
    Spacer(Modifier.height(8.dp))
    ConsoleField("GSTIN", cu.gstin, { v ->
        val g = v.uppercase().filter { it.isLetterOrDigit() }.take(15)
        onChange(cu.copy(gstin = g, stateCode = GstStates.ofGstin(g) ?: cu.stateCode))
    }, placeholder = "leave empty for none")
    Spacer(Modifier.height(8.dp))
    ConsoleField("Address", cu.address, { onChange(cu.copy(address = it)) }, singleLine = false)
    Spacer(Modifier.height(8.dp))
    StateField(if (gst) "State (place of supply)" else "State", cu.stateCode, onAsk) { onChange(cu.copy(stateCode = it, stateName = GstStates.name(it))) }
    Spacer(Modifier.height(8.dp))
    FieldPair({ ConsoleField("E-mail", cu.email, { v -> onChange(cu.copy(email = v)) }, it) }, { ConsoleField("Phone", cu.phone, { v -> onChange(cu.copy(phone = v)) }, it) })
}
