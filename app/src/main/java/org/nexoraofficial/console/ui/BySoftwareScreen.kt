package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.data.BySoftware
import org.nexoraofficial.console.data.Customer
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.PayFilter
import org.nexoraofficial.console.data.Plans
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.SwQuick
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — BY SOFTWARE (owner: "by software wise pan joi sakay").

   Sales & Costing alone, Fabric Stock alone: that software's customers,
   its plans and its payments, each a tab. A customer row opens the
   customer's window on that software's tab. Fabric Stock not connected:
   its message, as everywhere. Jobwork joins when it has a licence.
   ====================================================================== */

@Composable
fun BySoftwareScreen(vm: ConsoleViewModel, nav: Navigator, sw: String, gutter: PaddingValues, page: Modifier) {
    val c = LocalNexora.current
    val view = vm.swView(sw)
    val all = BySoftware.customers(vm.customers, sw)
    val off = sw == Software.FABRIC && vm.fabricProblem != null
    val pays = vm.payments?.payments.orEmpty().filter { it.software == sw }
    val fy = PayFilter.fyStart(vm.today()).toString()
    val fyPays = pays.filter { (it.paidOn ?: "") >= fy }
    val block = vm.plans?.block(sw)
    val plans = vm.plans?.plansOf(sw).orEmpty()
    val st = { x: Customer -> x.sub(sw)?.state.orEmpty() }
    val live = { x: Customer -> x.sub(sw)?.live == true }
    val left = { x: Customer -> x.sub(sw)?.left ?: 0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "banner") {
            Box(page) {
                GradientBanner(colors = SwColours.of(sw)) {
                    Text(Software.name(sw), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (sw == Software.FABRIC) "Nexora Loom & Fabric Stock" else "Nexora Bag Weight Calculation") +
                            " — its customers, its plans and its payments",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
        item(key = "figs") {
            Box(page) {
                val d30 = all.filter { live(it) && left(it) <= 30 }
                FigGrid(
                    listOf(
                        Fig("Customers", if (off) "—" else all.size.toString(),
                            if (off) "not connected" else "${all.count { st(it) == "LICENSED" }} licensed · ${all.count { st(it) == "DEMO" }} demo · ${all.count { st(it) == "SUSPENDED" }} suspended",
                            if (sw == Software.FABRIC) 1 else 0),
                        Fig("Ending in 30 days", d30.size.toString(),
                            "${d30.count { st(it) == "LICENSED" }} paying · " + counted(d30.count { st(it) == "DEMO" }, "demo"), 2) {
                            vm.setSwView(sw, view.copy(tab = "customers", quick = SwQuick.D30))
                        },
                        Fig("Received this financial year", Money.rupees(PayFilter.sum(fyPays)),
                            "${fyPays.size} payment" + (if (fyPays.size == 1) "" else "s") + " since ${Fmt.day(fy)}", if (sw == Software.FABRIC) 0 else 1) {
                            vm.setSwView(sw, view.copy(tab = "payments"))
                        },
                        Fig("Plans", if (block?.supported == true) plans.size.toString() else "—",
                            when {
                                block == null -> "reading…"
                                block.supported -> "${plans.count { it.active }} in use"
                                else -> "no plans in its service yet"
                            }, 3) { vm.setSwView(sw, view.copy(tab = "plans")) }
                    )
                )
            }
        }
        item(key = "tabs") {
            Box(page) {
                SubTabs(
                    listOf("customers" to "Customers", "plans" to "Plans", "payments" to "Payments"), view.tab,
                    mapOf("customers" to (if (off) "–" else all.size.toString()), "plans" to plans.size.toString(), "payments" to pays.size.toString())
                ) { vm.setSwView(sw, view.copy(tab = it)) }
            }
        }

        when (view.tab) {
            "plans" -> {
                if (block == null || !block.supported) {
                    item(key = "no-plans") {
                        Box(page) { EmptyCard(block?.let { Software.name(sw) + ": " + it.problem } ?: vm.plansError ?: "Reading the plans…") }
                    }
                } else {
                    items(plans, key = { "pl-${it.code}" }) { p ->
                        Box(page) { PlanRow(sw, p, Plans.catalogue(vm.plans, sw)) { nav.open(Screen.PlanWin(sw, p.code)) } }
                    }
                    item(key = "new-plan") {
                        Box(page) {
                            ConsoleButton("+ New ${Software.name(sw)} plan", { nav.open(Screen.PlanWin(sw, null)) }, kind = ButtonKind.Primary, small = true)
                        }
                    }
                }
            }
            "payments" -> {
                val t = view.query
                val rows = pays.filter { it.matches(t) }
                item(key = "pay-search") { Box(page) { SearchBox(view.query, { vm.setSwView(sw, view.copy(query = it)) }, "Customer, reference, note…") } }
                item(key = "pay-head") {
                    Box(page) { ListHeading("${rows.size} payment" + (if (rows.size == 1) "" else "s") + " · " + Money.rupees(PayFilter.sum(rows))) }
                }
                if (rows.isEmpty()) item(key = "pay-empty") { Box(page) { EmptyCard("No ${Software.name(sw)} payment in this view.") } }
                items(rows, key = { "p-${it.id}" }) { p -> Box(page) { PaymentRow(p, showCustomer = true) { nav.open(Screen.PaymentWin(p.id)) } } }
            }
            else -> {
                if (off) {
                    item(key = "off") { Box(page) { FabricNotConnectedCard(vm, vm.fabricProblem!!) } }
                } else {
                    item(key = "search") { Box(page) { SearchBox(view.query, { vm.setSwView(sw, view.copy(query = it)) }, "Customer, GSTIN, key…") } }
                    item(key = "quick") {
                        Box(page) {
                            QuickRow(SwQuick.entries.map { q -> Quick(q.name, BySoftware.label(q, sw), all.count { BySoftware.keep(it, sw, q) }) }, view.quick.name) {
                                vm.setSwView(sw, view.copy(quick = SwQuick.valueOf(it)))
                            }
                        }
                    }
                    val rows = BySoftware.filter(all, sw, view.quick, view.query)
                    item(key = "heading") {
                        Box(page) {
                            ListHeading(
                                "${rows.size} customer" + (if (rows.size == 1) "" else "s"),
                                if (sw == Software.WEIGHT) "${vm.data.licences.size} computers and phones"
                                else "${all.sumOf { it.f?.devices ?: 0 }} computers and phones"
                            )
                        }
                    }
                    if (rows.isEmpty()) item(key = "empty") { Box(page) { EmptyCard("No ${Software.name(sw)} customer in this view.") } }
                    items(rows, key = { it.key }) { x ->
                        Box(page) { SwCustomerRow(vm, x, sw) { nav.open(Screen.CustomerWin(x.key, sw, "licence")) } }
                    }
                }
            }
        }
    }
}

/** A customer on one software: plan, state, the dates, seats and computers, the last payment, and the other software it uses. */
@Composable
private fun SwCustomerRow(vm: ConsoleViewModel, x: Customer, sw: String, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val s = x.sub(sw) ?: return
    val soon = s.live && s.left <= 30
    val w = x.w
    val f = x.f
    /* 2.0.1 — Fabric Stock's own changes over its plan show as "± n" too */
    val plan = (if (sw == Software.WEIGHT) vm.planName(sw, w?.plan) else vm.planName(sw, f?.plan, f?.planName)) +
        Customers.own(Customers.ownCount(x, sw))
    RecordRow(if (soon) c.warn else stateColour(s.state), onOpen) {
        RowTitle(x.name, right = if (s.live) "${s.left} d" else null, rightColor = if (soon) c.warn else c.text)
        x.gstin?.let { Small(it) }
        Spacer(Modifier.height(2.dp))
        SwLine(sw, plan + " · " + Customers.stateWord(s.state), color = stateColour(s.state).takeIf { s.state != "LICENSED" && s.state != "DEMO" })
        val started = if (sw == Software.WEIGHT) w?.periodStartedAt else f?.periodStartedAt ?: f?.createdAt
        val seats = if (sw == Software.WEIGHT) "${w?.usersTotal ?: 0} of ${w?.seats ?: 0}" else "${f?.people ?: 0} of ${f?.seats ?: 0}"
        val devices = if (sw == Software.WEIGHT) w?.machinesUsed ?: 0 else f?.devices ?: 0
        Small("started ${Fmt.day(started)} · ends ${Fmt.day(s.at)} · $seats seats · $devices computers")
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val p = vm.payments?.last(x, sw)
            Text(
                if (p != null) "last payment ${Money.rupees(p.amount)} · ${Fmt.day(p.paidOn)}" else "no payment recorded",
                color = if (p != null) c.text else c.faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
            )
            val other = Software.other(sw)
            if (x.has(other)) {
                SwDot(other)
                Text(" also " + Software.name(other), color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}
