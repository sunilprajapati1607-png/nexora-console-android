package org.nexoraofficial.console.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.data.Billing
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.PayQuote
import org.nexoraofficial.console.data.PaymentForm
import org.nexoraofficial.console.data.PaymentKinds
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.3.0 — RECORD PAYMENT BY PLAN (the desktop console 1.4's payments-ui.js).

   By plan (the default): the plan (the one it is on first), what it is for,
   the users (its seats now) and the periods. The service works the amount
   out as they change (quote: included users, extra users × price, the days
   one period runs, the days it adds, a plan or seat change, the total) and
   on Save sets that plan and the seats and adds plan days × periods. The
   amount can be typed over. "Without plan" is the 2.0 form with a renewal.
   After a save the console offers to make its invoice.
   ====================================================================== */

@Composable
fun RecordPaymentScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current
    val f = vm.payForm
    val all = vm.customers
    val x = vm.customer(f.customerKey)
    val sws = x?.softwares.orEmpty()
    val save = {
        vm.recordPayment(vm.payForm) { p ->
            if (p != null) {
                nav.back()
                /* right after it is kept: its invoice or receipt now? */
                onAsk(Ask.Confirm("Make invoice", "The payment of ${Money.rupees(p.amount)} is recorded. Make its invoice or receipt now?", "Make invoice") {
                    startPaymentInvoice(vm, nav, onAsk, p)
                })
            }
        }
    }
    /* the quote follows the plan, kind, users and periods — a moment after the last change */
    val key = Billing.quoteKey(f)
    LaunchedEffect(key, f.byPlan) {
        if (f.byPlan) { delay(250); vm.requote(vm.payForm) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) { WindowHead("₹", PayColours, "Record Payment", "What came in, for which software and plan — and the days it buys", "NEW") }
        }
        item(key = "tools") {
            Box(page) {
                ToolStrip(listOf(
                    Tool("Save", Icons.Outlined.Save, ToolColours.green, !vm.busy && x != null) { save() },
                    Tool("Cancel", Icons.Outlined.Close, ToolColours.back) { nav.back() }
                ))
            }
        }
        item(key = "who") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    Text("Customer", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                    Row(
                        Modifier.fillMaxWidth().background(c.surface, MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .pressable {
                                onAsk(Ask.Pick(
                                    title = "Which customer paid?",
                                    body = "Each software has its own licence; the payment is recorded against the one you choose next.",
                                    options = all.mapIndexed { i, cu -> Ask.Pick.Option(i, cu.name, (listOfNotNull(cu.gstin) + cu.softwares.map { Software.name(it) }).joinToString(" · ")) },
                                    confirmText = "Choose", empty = "No customers yet.",
                                    onPick = { i ->
                                        all.getOrNull(i)?.let { cu ->
                                            val sw = if (cu.has(vm.payForm.software)) vm.payForm.software else cu.softwares.firstOrNull() ?: Software.WEIGHT
                                            vm.payForm = vm.presetPlan(vm.payForm.copy(customerKey = cu.key, software = sw))
                                        }
                                    }
                                ))
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(x?.name ?: "Choose the customer", color = if (x == null) c.faint else c.text, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Change", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (sws.isNotEmpty()) {
                        ChoiceField("Software", sws.map { it to Software.name(it) }, f.software) { vm.payForm = vm.presetPlan(f.copy(software = it)) }
                        Spacer(Modifier.height(8.dp))
                    }
                    val lic = x?.sub(f.software)
                    val seats = if (f.software == Software.WEIGHT) x?.w?.seats else x?.remote(f.software)?.seats
                    ReadField(
                        "Now",
                        if (x == null || lic == null) "—"
                        else vm.planName(f.software, Customers.planCode(x, f.software), Customers.planFallback(x, f.software)) + " · " + Customers.stateWord(lic.state),
                        sub = if (lic == null) null else "${seats ?: 1} seats · valid till " + Fmt.day(lic.at)
                    )
                }
            }
        }
        item(key = "mode") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    OptionChips(listOf("plan" to "By plan — adds the days by itself", "free" to "Without plan"), if (f.byPlan) "plan" else "free") { m ->
                        val on = m == "plan"
                        vm.payForm = if (on) vm.presetPlan(f.copy(byPlan = true)) else f.copy(byPlan = false, typed = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (f.byPlan) PlanPart(vm, f) else {
                        val demo = x?.sub(f.software)?.state == "DEMO"
                        ChoiceField("For", PaymentKinds.ALL.map { it to PaymentKinds.word(it) }, f.kind) { vm.payForm = f.copy(kind = it) }
                        Spacer(Modifier.height(10.dp))
                        ChoiceField("Renew the licence with it", PaymentForm.RENEWALS.map { it.toString() to PaymentForm.renewalText(it, demo) },
                            f.extendDays.toString()) { vm.payForm = f.copy(extendDays = it.toInt()) }
                    }
                }
            }
        }
        if (f.byPlan) item(key = "quote") { Box(page) { QuoteCard(vm, f) } }
        item(key = "form") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    PaymentFields(f, { vm.payForm = it }, onAsk, renewing = f.byPlan || f.extendDays > 0, byPlan = f.byPlan)
                }
            }
        }
        item(key = "help") {
            Box(page) {
                Help(
                    if (f.byPlan) "On Save the plan and the users are set on ${Software.name(f.software)}, the days are added to the licence " +
                        "(Sales & Costing adds them after what is left), and the payment keeps the validity it bought."
                    else "With a renewal chosen, the licence is renewed in the same step. Without one, give the validity by hand if you know it.",
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

/** The plan, what it is for, the users and the periods. */
@Composable
private fun PlanPart(vm: ConsoleViewModel, f: PaymentForm) {
    val x = vm.customer(f.customerKey)
    val now = x?.let { Customers.planCode(it, f.software)?.uppercase() }
    val block = vm.plans?.block(f.software)
    val list = vm.plans?.plansOf(f.software).orEmpty().filter { it.active || it.code == now }
    when {
        vm.plans == null -> Help("Reading the plans…")
        block == null || !block.supported || list.isEmpty() ->
            Why(Software.name(f.software) + ": " + (block?.message ?: "it has no plans yet — make them under Software & plans, or record it Without plan."))
        else -> {
            ChoiceField("Plan", list.map { it.code to it.name + (if (it.code == now) " (now)" else "") + (if (!it.active) " (retired)" else "") }, f.plan) {
                vm.payForm = f.copy(plan = it)
            }
            Spacer(Modifier.height(10.dp))
            ChoiceField("For", PaymentForm.PLAN_KINDS, f.kind) { vm.payForm = f.copy(kind = it) }
            Spacer(Modifier.height(10.dp))
            FieldPair(
                { Stepper("Users", f.users, 1, 500, it) { n -> vm.payForm = f.copy(users = n) } },
                { Stepper("Periods", f.periods, 1, 10, it) { n -> vm.payForm = f.copy(periods = n) } }
            )
        }
    }
}

/** A whole number changed with − and + (no keyboard). */
@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val c = LocalNexora.current
    Column(modifier) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(c.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepKey("−", value > min) { onChange((value - 1).coerceAtLeast(min)) }
            Text(value.toString(), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            StepKey("+", value < max) { onChange((value + 1).coerceAtMost(max)) }
        }
    }
}

@Composable
private fun StepKey(sign: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalNexora.current
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (enabled) c.accent.copy(alpha = 0.12f) else c.border.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.pressable(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(sign, color = if (enabled) c.accent else c.faint, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

/** What the service worked out: the price, the users, the periods, the days it adds, what else it sets, the total. */
@Composable
private fun QuoteCard(vm: ConsoleViewModel, f: PaymentForm) {
    val c = LocalNexora.current
    val q = vm.quote?.takeIf { vm.quoteFor == Billing.quoteKey(f) }
    val err = vm.quoteError?.takeIf { vm.quoteFor == Billing.quoteKey(f) }
    ConsoleCard(padding = 14) {
        Text("What it comes to", style = CardTitleStyle)
        Spacer(Modifier.height(8.dp))
        when {
            err != null -> Text("⚠ $err", color = c.bad, fontSize = 13.5f.sp, fontWeight = FontWeight.SemiBold)
            q == null -> Help("Working out the amount…")
            else -> QuoteRows(vm, f, q)
        }
    }
}

@Composable
private fun QuoteRows(vm: ConsoleViewModel, f: PaymentForm, q: PayQuote) {
    val c = LocalNexora.current
    val extra = q.extraOnly
    QRow("Plan", q.planName, "one period ${q.planDays} days")
    if (!extra) QRow(if (q.kind == "NEW") "Price — first period" else "Price — renewal", Money.rupees(q.unitPrice))
    QRow(
        "Users",
        "${q.users} — " + if (extra) "licence has ${q.currentSeats ?: "—"} now" else q.usersIncluded?.let { "$it included" } ?: "all included",
        if (q.extraUsers > 0) "${q.extraUsers} extra × ${Money.rupees(q.extraUserPrice)} = ${Money.rupees(q.extraAmount)}" else null,
        subColor = c.accent
    )
    if (!extra) QRow("Periods", "${q.periods} × ${q.planDays} days")
    QRow("Adds", if (q.days > 0) "${q.days} days to the licence" else "no days — extra users on the period already paid",
        valueColor = if (q.days > 0) c.ok else null)
    val changes = ArrayList<String>()
    if (q.planCode.isNotEmpty() && q.planCode != q.currentPlan) changes += "plan " + vm.planName(f.software, q.currentPlan) + " → " + q.planName
    if (q.currentSeats != null && q.users != q.currentSeats) changes += "seats ${q.currentSeats} → ${q.users}"
    if (changes.isNotEmpty()) QRow("Also sets", changes.joinToString(" · "))
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Total", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(if (q.amount != null) Money.rupees(q.amount) else "not set — type the amount", color = if (q.amount != null) c.text else c.warn,
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
    if (f.typed && f.amount.isNotBlank() && Money.parse(f.amount) != q.amount) {
        Spacer(Modifier.height(6.dp))
        Small("You typed ${Money.rupees(Money.parse(f.amount))} — that is what is recorded.", color = c.warn)
    }
    q.note?.let { Spacer(Modifier.height(6.dp)); Small(it) }
}

@Composable
private fun QRow(label: String, value: String, sub: String? = null, valueColor: androidx.compose.ui.graphics.Color? = null,
                 subColor: androidx.compose.ui.graphics.Color? = null) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = c.muted, fontSize = 12.5f.sp, modifier = Modifier.width(112.dp))
        Column(Modifier.weight(1f).widthIn(min = 0.dp)) {
            Text(value, color = valueColor ?: c.text, fontSize = 13.5f.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            sub?.let { Text(it, color = subColor ?: c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) }
        }
    }
}
