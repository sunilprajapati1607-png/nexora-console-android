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
                        listOf(Quick("ANY", "Any software"), Quick(Software.WEIGHT, Software.WEIGHT_NAME), Quick(Software.FABRIC, Software.FABRIC_NAME)),
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
                val w = rows.filter { it.software == Software.WEIGHT }
                val f = rows.filter { it.software == Software.FABRIC }
                val ren = rows.filter { it.kind == "RENEWAL" }
                val new = rows.filter { it.kind == "NEW" }
                FigGrid(
                    listOf(
                        Fig(Software.WEIGHT_NAME, Money.rupees(PayFilter.sum(w)), "${w.size} payment" + (if (w.size == 1) "" else "s"), 0),
                        Fig(Software.FABRIC_NAME, Money.rupees(PayFilter.sum(f)), "${f.size} payment" + (if (f.size == 1) "" else "s"), 1),
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
    }
}

/** The fields a payment is typed in: what it was for, how much, when, how, a reference, the validity, a note. */
@Composable
private fun PaymentFields(f: PaymentForm, onChange: (PaymentForm) -> Unit, onAsk: (Ask) -> Unit, renewing: Boolean) {
    ChoiceField("For", PaymentKinds.ALL.map { it to PaymentKinds.word(it) }, f.kind) { onChange(f.copy(kind = it)) }
    Spacer(Modifier.height(10.dp))
    ConsoleField("Amount received (₹)", f.amount, { v -> onChange(f.copy(amount = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' })) },
        placeholder = "15,000", numeric = true)
    Spacer(Modifier.height(8.dp))
    DateField("Paid on", f.paidOn, Modifier.fillMaxWidth(), onAsk = onAsk) { d -> onChange(f.copy(paidOn = d)) }
    Spacer(Modifier.height(10.dp))
    ChoiceField("How it came", PaymentModes.ALL.map { it to PaymentModes.word(it) }, f.mode) { onChange(f.copy(mode = it)) }
    Spacer(Modifier.height(8.dp))
    ConsoleField("Reference (UTR, cheque, invoice)", f.reference, { onChange(f.copy(reference = it)) })
    if (!renewing) {
        Spacer(Modifier.height(10.dp))
        FieldPair(
            { DateField(if (f.id == 0) "Valid from (if not renewing)" else "Valid from", f.validFrom, it, clearable = true, onAsk = onAsk) { d -> onChange(f.copy(validFrom = d)) } },
            { DateField(if (f.id == 0) "Valid to (if not renewing)" else "Valid to", f.validTo, it, clearable = true, onAsk = onAsk) { d -> onChange(f.copy(validTo = d)) } }
        )
    }
    Spacer(Modifier.height(8.dp))
    ConsoleField("Note", f.note, { onChange(f.copy(note = it)) }, singleLine = false)
}

/* ------------------------------------------------------- Record payment */

@Composable
fun RecordPaymentScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current
    val f = vm.payForm
    val all = vm.customers
    val x = vm.customer(f.customerKey)
    val sws = listOfNotNull(if (x?.w != null) Software.WEIGHT else null, if (x?.f != null) Software.FABRIC else null)
    val save = { vm.recordPayment(vm.payForm) { p -> if (p != null) nav.back() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) {
                WindowHead("₹", PayColours, "Record Payment",
                    "What came in, for which software and plan — and, if you like, the renewal it pays for", "NEW")
            }
        }
        item(key = "tools") {
            Box(page) {
                ToolStrip(
                    listOf(
                        Tool("Save", Icons.Outlined.Save, ToolColours.green, !vm.busy && x != null) { save() },
                        Tool("Cancel", Icons.Outlined.Close, ToolColours.back) { nav.back() }
                    )
                )
            }
        }
        item(key = "form") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    /* the customer: a tap opens the list to choose from */
                    Text("Customer", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                    Row(
                        Modifier.fillMaxWidth().background(c.surface, MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .pressable {
                                onAsk(
                                    Ask.Pick(
                                        title = "Which customer paid?",
                                        body = "Each software has its own licence; the payment is recorded against the one you choose next.",
                                        options = all.mapIndexed { i, cu ->
                                            Ask.Pick.Option(i, cu.name, listOfNotNull(cu.gstin, if (cu.w != null) Software.WEIGHT_NAME else null,
                                                if (cu.f != null) Software.FABRIC_NAME else null).joinToString(" · "))
                                        },
                                        confirmText = "Choose",
                                        empty = "No customers yet.",
                                        onPick = { i ->
                                            all.getOrNull(i)?.let { cu ->
                                                val sw = if (cu.has(vm.payForm.software)) vm.payForm.software else if (cu.w != null) Software.WEIGHT else Software.FABRIC
                                                vm.payForm = vm.payForm.copy(customerKey = cu.key, software = sw)
                                            }
                                        }
                                    )
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(x?.name ?: "Choose the customer", color = if (x == null) c.faint else c.text, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Change", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (sws.isNotEmpty()) {
                        ChoiceField("Software", sws.map { it to Software.name(it) }, f.software) { vm.payForm = f.copy(software = it) }
                        Spacer(Modifier.height(8.dp))
                    }
                    val lic = x?.sub(f.software)
                    val planCode = if (f.software == Software.FABRIC) x?.f?.plan else x?.w?.plan
                    ReadField(
                        "Plan",
                        if (x == null || lic == null) "—"
                        else vm.planName(f.software, planCode, if (f.software == Software.FABRIC) x.f?.planName else null) + " · " +
                            Customers.stateWord(lic.state) + " · ends " + Fmt.day(lic.at)
                    )
                    Spacer(Modifier.height(10.dp))
                    val demo = lic?.state == "DEMO"
                    ChoiceField(
                        "Renew the licence with it",
                        PaymentForm.RENEWALS.map { it.toString() to PaymentForm.renewalText(it, demo) },
                        f.extendDays.toString()
                    ) { vm.payForm = f.copy(extendDays = it.toInt()) }
                    Spacer(Modifier.height(10.dp))
                    PaymentFields(f, { vm.payForm = it }, onAsk, renewing = f.extendDays > 0)
                }
            }
        }
        item(key = "help") {
            Box(page) {
                Help(
                    "With a renewal chosen, the licence is renewed in the same step and the payment keeps the validity it bought: " +
                        "Sales & Costing adds the time after what is left; Fabric Stock carries the days left into the new period. " +
                        "Without one, give the validity by hand if you know it.",
                    Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        item(key = "buttons") {
            Box(page) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConsoleButton("Record the payment", save, kind = ButtonKind.Primary, enabled = !vm.busy && x != null,
                        modifier = Modifier.weight(1f), center = true)
                    ConsoleButton("Cancel", { nav.back() }, modifier = Modifier.weight(1f), center = true)
                }
            }
        }
    }
}
