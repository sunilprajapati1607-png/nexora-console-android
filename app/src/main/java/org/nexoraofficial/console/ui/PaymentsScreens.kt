package org.nexoraofficial.console.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.PayFilter
import org.nexoraofficial.console.data.PayQuick
import org.nexoraofficial.console.data.Payment
import org.nexoraofficial.console.data.PaymentForm
import org.nexoraofficial.console.data.PaymentKinds
import org.nexoraofficial.console.data.PaymentModes
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — PAYMENTS: WHAT CAME IN, AND THE VALIDITY IT BOUGHT.

   "customer ni validity payment kyare aavyu kya plan nu kayo plan expire
   thay che aena record" (owner, 2026-10-08). The ledger as a Records list
   (quick views Today, Last 30 days, This month, This financial year from
   1 April), a payment as a window (DISPLAY, Edit, Delete), and Record
   payment — which can renew the licence in the same step.
   ====================================================================== */

/** The payment window's mark: green, as the web console's ₹. */
val PayColours = listOf(Color(0xFF15803D), Color(0xFF34D399))

@Composable
fun PaymentsScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier) {
    val rows = vm.paymentsShown
    val (from, to) = PayFilter.range(vm.payQuick, vm.today())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search") { Box(page) { SearchBox(vm.payQuery, { vm.payQuery = it }, "Customer, reference, note, plan…") } }
        item(key = "quick") {
            Box(page) {
                QuickRow(PayQuick.entries.map { Quick(it.name, it.label) }, vm.payQuick.name) { vm.payQuick = PayQuick.valueOf(it) }
            }
        }
        item(key = "filters") {
            Box(page) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickRow(
                        listOf(Quick("ANY", "Any software")) + Software.LICENSED.map { Quick(it, Software.name(it)) },
                        vm.paySoft ?: "ANY"
                    ) { vm.paySoft = it.takeIf { s -> s != "ANY" } }
                    QuickRow(listOf(Quick("ANY", "Any kind")) + PaymentKinds.ALL.map { Quick(it, PaymentKinds.word(it)) }, vm.payKind ?: "ANY") {
                        vm.payKind = it.takeIf { k -> k != "ANY" }
                    }
                }
            }
        }
        if (vm.paymentsLoad == Load.FAILED) {
            item(key = "failed") { Box(page) { EmptyCard(vm.paymentsError ?: "The payments could not be read.") } }
        } else if (vm.payments == null && vm.paymentsLoad == Load.LOADING) {
            item(key = "reading") { Box(page) { EmptyCard("Reading the payments…") } }
        }
        item(key = "figs") {
            Box(page) {
                val ren = rows.filter { it.kind == "RENEWAL" }
                val new = rows.filter { it.kind == "NEW" }
                /* 2.1.0 — every software's own takings: Sales & Costing and Fabric Stock always, the others once they have any */
                val shown = Software.LICENSED.filter { sw -> sw == Software.WEIGHT || sw == Software.FABRIC || rows.any { it.software == sw } }
                FigGrid(
                    shown.mapIndexed { i, sw ->
                        val l = rows.filter { it.software == sw }
                        Fig(Software.name(sw), Money.rupees(PayFilter.sum(l)), "${l.size} payment" + (if (l.size == 1) "" else "s"), i % 2)
                    } + listOf(
                        Fig("Renewals", Money.rupees(PayFilter.sum(ren)), "${ren.size} renewal" + (if (ren.size == 1) "" else "s"), 2),
                        Fig("New customers", Money.rupees(PayFilter.sum(new)), "${new.size} first payment" + (if (new.size == 1) "" else "s"), 3)
                    )
                )
            }
        }
        item(key = "heading") {
            Box(page) {
                ListHeading(
                    "${rows.size} payment" + (if (rows.size == 1) "" else "s") + " · " + Money.rupees(PayFilter.sum(rows)),
                    if (from != null || to != null) Fmt.day(from) + " → " + Fmt.day(to) else "tap a row to open it"
                )
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") { Box(page) { EmptyCard("No payment in this view. Record payment keeps one.") } }
        }
        items(rows, key = { "p-${it.id}" }) { p -> Box(page) { PaymentRow(p, showCustomer = true) { nav.open(Screen.PaymentWin(p.id)) } } }
        if (rows.isNotEmpty()) {
            item(key = "total") {
                Box(page) {
                    ConsoleCard(padding = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", style = CardTitleStyle, modifier = Modifier.weight(1f))
                            Text(Money.rupees(PayFilter.sum(rows)), style = CardTitleStyle)
                        }
                    }
                }
            }
        }
    }
}

/** One payment in a list: who (or what it was for), how much, which software and plan, when, how, and the validity it bought. */
@Composable
fun PaymentRow(p: Payment, showCustomer: Boolean, onOpen: () -> Unit) {
    val c = LocalNexora.current
    RecordRow(SwColours.of(p.software)[0], onOpen) {
        RowTitle(if (showCustomer) p.customer else p.kindText, right = Money.rupees(p.amount))
        SwLine(p.software, listOfNotNull(p.softwareName, p.planName ?: p.plan, if (showCustomer) p.kindText else null).joinToString(" · "))
        Spacer(Modifier.height(3.dp))
        Small(listOfNotNull(Fmt.day(p.paidOn), p.modeText, p.reference).joinToString(" · "), color = c.text)
        if (p.validTo != null) Small("validity " + p.validityText)
    }
}

/* -------------------------------------------------------- one payment */

@Composable
fun PaymentWindow(vm: ConsoleViewModel, nav: Navigator, id: Int, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    val p = vm.paymentById(id)
    if (p == null) {
        if (vm.paymentsLoad == Load.LOADING) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = gutter, horizontalAlignment = Alignment.CenterHorizontally) {
                item { Box(page) { EmptyCard("Reading the payments…") } }
            }
        } else LaunchedEffect(Unit) { nav.back() }
        return
    }
    var editing by rememberSaveable(id) { mutableStateOf(false) }
    var form by remember(id) { mutableStateOf(PaymentForm.of(p)) }

    DisposableEffect(editing) {
        nav.guard = if (editing) ({
            onAsk(Ask.Confirm("Close without saving?", "What was changed here is not saved.", "Close", danger = true) {
                editing = false; nav.back()
            })
        }) else null
        onDispose { nav.guard = null }
    }
    BackHandler(enabled = editing) { nav.requestBack() }

    val owner = vm.customers.find { p.of(it) }
    LaunchedEffect(Unit) { if (vm.invoicesLoad == Load.IDLE) vm.loadInvoices() }
    val made = vm.invoicesFor(p.id)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) {
                WindowHead("₹", PayColours, "${p.customer} · ${Money.rupees(p.amount)}", "${Fmt.day(p.paidOn)} · ${p.softwareName}",
                    if (editing) "EDIT" else "DISPLAY")
            }
        }
        item(key = "tools") {
            Box(page) {
                val tools = ArrayList<Tool>()
                if (editing) {
                    tools += Tool("Save", Icons.Outlined.Save, ToolColours.green, !vm.busy) {
                        vm.updatePayment(form) { ok -> if (ok) editing = false }
                    }
                    tools += Tool("Cancel", Icons.Outlined.Close, ToolColours.back) { form = PaymentForm.of(p); editing = false }
                } else {
                    tools += Tool("Edit", Icons.Outlined.Edit, ToolColours.blue) { form = PaymentForm.of(p); editing = true }
                }
                tools += Tool("Customer", Icons.Outlined.Person, ToolColours.teal, owner != null) {
                    owner?.let { nav.open(Screen.CustomerWin(it.key, p.software, "payments")) }
                }
                /* 2.3.0 — its invoice or receipt: made here, or opened when it has one */
                tools += Tool("Invoice", Icons.AutoMirrored.Outlined.ReceiptLong, ToolColours.violet, !editing) { startPaymentInvoice(vm, nav, onAsk, p) }
                tools += Tool("Delete", Icons.Outlined.Delete, ToolColours.red) {
                    onAsk(
                        Ask.Confirm(
                            "Delete this payment of ${Money.rupees(p.amount)} from ${p.customer}?",
                            "It leaves the list; the service keeps it, and its Activity list that it was deleted.",
                            "Delete", danger = true
                        ) { vm.deletePayment(p) { ok -> if (ok) nav.back() } }
                    )
                }
                ToolStrip(tools)
            }
        }
        item(key = "fields") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    FieldPair({ ReadField("Customer", p.customer, it) }, { ReadField("Software", p.softwareName, it) })
                    Spacer(Modifier.height(8.dp))
                    if (editing) {
                        ReadField("Plan", p.planName ?: p.plan ?: "—")
                        Spacer(Modifier.height(10.dp))
                        PaymentFields(form, { form = it }, onAsk, renewing = false)
                    } else {
                        FieldPair({ ReadField("Plan", p.planName ?: p.plan ?: "—", it) }, { ReadField("For", p.kindText, it) })
                        Spacer(Modifier.height(8.dp))
                        FieldPair({ ReadField("Amount received", Money.rupees(p.amount), it) }, { ReadField("Paid on", Fmt.day(p.paidOn), it) })
                        Spacer(Modifier.height(8.dp))
                        FieldPair({ ReadField("How it came", p.modeText, it) }, { ReadField("Reference", p.reference ?: "—", it) })
                        Spacer(Modifier.height(8.dp))
                        FieldPair(
                            { ReadField("Valid from", if (p.validFrom != null) Fmt.day(p.validFrom) else "—", it) },
                            { ReadField("Valid to", if (p.validTo != null) Fmt.day(p.validTo) else "—", it) }
                        )
                        Spacer(Modifier.height(8.dp))
                        ReadField("Note", p.note ?: "—")
                        Spacer(Modifier.height(8.dp))
                        ReadField("Recorded", Fmt.dateTime(p.createdAt) + (p.viaText?.let { " · $it" } ?: ""))
                    }
                }
            }
        }
        /* 2.3.0 — the invoices and receipts made for it */
        if (!editing && made.isNotEmpty()) {
            item(key = "inv-head") { Box(page) { ListHeading(counted(made.size, "invoice") + " for this payment", "tap one to open it") } }
            made.forEach { inv -> item(key = "inv-${inv.id}") { Box(page) { InvoiceRow(inv) { nav.open(Screen.InvoiceWin(inv.id)) } } } }
        }
    }
}

/** The fields a payment is typed in: what it was for, how much, when, how, a reference, the validity, a note. */
/* 2.3.0 — internal, for Record payment's own file; [byPlan] leaves out What for and the validity (the plan part asks
   them) and the amount says it was worked out — typing over it marks the form typed (then it is sent and overrides). */
@Composable
internal fun PaymentFields(f: PaymentForm, onChange: (PaymentForm) -> Unit, onAsk: (Ask) -> Unit, renewing: Boolean, byPlan: Boolean = false) {
    if (!byPlan) {
        ChoiceField("For", PaymentKinds.ALL.map { it to PaymentKinds.word(it) }, f.kind) { onChange(f.copy(kind = it)) }
        Spacer(Modifier.height(10.dp))
    }
    ConsoleField(
        "Amount received (₹)", f.amount,
        { v -> val a = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }; onChange(f.copy(amount = a, typed = byPlan && a.isNotBlank())) },
        placeholder = "15,000", numeric = true,
        supporting = if (byPlan) "worked out from the plan; type over it if it differs" else null
    )
    Spacer(Modifier.height(8.dp))
    DateField("Paid on", f.paidOn, Modifier.fillMaxWidth(), onAsk = onAsk) { d -> onChange(f.copy(paidOn = d)) }
    Spacer(Modifier.height(10.dp))
    ChoiceField("How it came", PaymentModes.ALL.map { it to PaymentModes.word(it) }, f.mode) { onChange(f.copy(mode = it)) }
    Spacer(Modifier.height(8.dp))
    ConsoleField("Reference (UTR, cheque, invoice)", f.reference, { onChange(f.copy(reference = it)) })
    if (!renewing && !byPlan) {
        Spacer(Modifier.height(10.dp))
        FieldPair(
            { DateField(if (f.id == 0) "Valid from (if not renewing)" else "Valid from", f.validFrom, it, clearable = true, onAsk = onAsk) { d -> onChange(f.copy(validFrom = d)) } },
            { DateField(if (f.id == 0) "Valid to (if not renewing)" else "Valid to", f.validTo, it, clearable = true, onAsk = onAsk) { d -> onChange(f.copy(validTo = d)) } }
        )
    }
    Spacer(Modifier.height(8.dp))
    ConsoleField("Note", f.note, { onChange(f.copy(note = it)) }, singleLine = false,
        placeholder = if (byPlan) "left empty: the plan, users and periods" else "")
}

