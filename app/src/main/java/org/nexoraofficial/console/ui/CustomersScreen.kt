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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.CompanyView
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.CustQuick
import org.nexoraofficial.console.data.Customer
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.DeletedCompany
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — CUSTOMERS, BY CUSTOMER.

   "by customer pan joi sakay" — every customer once, every software it
   uses on its own line ("Sales & Costing · Standard ± 2 · 245 days",
   "Fabric Stock · Standard · demo · 5 days"), and when it next renews.
   Quick views with their counts, four figures, a search; a tap opens the
   customer's window. Deleted (kept 30 days, then erased) is one of the
   quick views, from a service that keeps them.
   ====================================================================== */

@Composable
fun CustomersScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val c = LocalNexora.current
    val showDeleted = vm.companyView == CompanyView.DELETED && vm.data.keepsDeleted
    val all = vm.customers
    val plans = vm.plans

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search") {
            Box(page) { SearchBox(vm.companyQuery, { vm.companyQuery = it }, "Customer, GSTIN, key, phone, email…") }
        }

        item(key = "quick") {
            Box(page) {
                val q = CustQuick.entries.map { Quick(it.name, it.label, Customers.count(all, it)) } +
                    (if (vm.data.keepsDeleted) listOf(Quick("DELETED", "Deleted", vm.data.archived.size)) else emptyList())
                QuickRow(
                    q,
                    selected = when {
                        showDeleted -> "DELETED"
                        vm.companyEnding -> null
                        else -> vm.customerQuick.name
                    }
                ) { id ->
                    if (id == "DELETED") vm.companyView = CompanyView.DELETED
                    else {
                        vm.companyView = CompanyView.ALL
                        vm.customerQuick = CustQuick.valueOf(id)
                    }
                }
            }
        }

        if (showDeleted) {
            item(key = "deleted-help") {
                Box(page) {
                    EmptyCard(
                        when {
                            vm.data.archived.isEmpty() ->
                                "No deleted companies. A company you delete is kept here for " +
                                    "${DeletedCompany.KEEP_DAYS} days and can be restored until then; " +
                                    "after that it is erased for good."
                            vm.deletedCompanies.isEmpty() -> "Nothing matches that."
                            else ->
                                "A deleted company is kept for ${DeletedCompany.KEEP_DAYS} days with everything " +
                                    "it had, but its computers and phones are stopped and nobody can sign in. " +
                                    "Restore puts it back exactly as it was. After ${DeletedCompany.KEEP_DAYS} days " +
                                    "it is erased for good — its machines, people, synced records, chat and " +
                                    "problem reports."
                        }
                    )
                }
            }
            items(vm.deletedCompanies, key = { "deleted-${it.id}" }) { co ->
                Box(page) {
                    DeletedRow(co) {
                        onAsk(
                            Ask.Confirm(
                                title = "Restore ${co.name}?",
                                body = "It comes back exactly as it was when it was deleted: its computers " +
                                    "and phones work again at their next check, and nobody has to join or " +
                                    "sign in again.",
                                confirmText = "Restore",
                                onYes = { vm.undeleteCompany(co.id) }
                            )
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "figs") {
            Box(page) {
                val ws = all.mapNotNull { it.w?.swState }
                val fs = all.mapNotNull { it.f?.shownState }
                val n = { l: List<String>, s: String -> l.count { it == s } }
                FigGrid(
                    listOf(
                        Fig(Software.WEIGHT_NAME, ws.size.toString(),
                            "${n(ws, "LICENSED")} licensed · ${n(ws, "DEMO")} demo · ${n(ws, "SUSPENDED")} suspended", 0) {
                            nav.switchTo(Screen.BySoftware(Software.WEIGHT))
                        },
                        Fig(Software.FABRIC_NAME, if (vm.fabricReady) fs.size.toString() else "—",
                            when {
                                vm.fabricProblem != null -> "not connected"
                                !vm.fabricReady -> "reading…"
                                else -> "${n(fs, "LICENSED")} licensed · ${n(fs, "DEMO")} demo · ${n(fs, "SUSPENDED")} suspended"
                            }, 1) { nav.switchTo(Screen.BySoftware(Software.FABRIC)) },
                        Fig("Renew in 30 days", Customers.count(all, CustQuick.SOON).toString(), "a licence of theirs ends within 30 days", 2) {
                            vm.companyView = CompanyView.ALL; vm.customerQuick = CustQuick.SOON
                        },
                        Fig("Using both", Customers.count(all, CustQuick.BOTH).toString(), "Sales & Costing + Fabric Stock", 3) {
                            vm.companyView = CompanyView.ALL; vm.customerQuick = CustQuick.BOTH
                        }
                    )
                )
            }
        }

        /* 4.72.0 — audit #90: opened from the dashboard's "Licences ending soon" */
        if (vm.companyEnding) {
            item(key = "ending") {
                Box(page) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Small(
                            "Licences ending within ${Company.ENDING_DAYS} days only",
                            color = c.warn,
                            modifier = Modifier.weight(1f)
                        )
                        ConsoleButton("Show all customers", { vm.companyEnding = false }, small = true)
                    }
                }
            }
        }

        /* Fabric Stock not reachable (asleep, or refusing the key): said once, with Retry — the Sales & Costing
           customers are listed below as ever */
        val problem = vm.fabricProblem
        if (!vm.companyEnding && problem != null) {
            item(key = "fabric-not-connected") { Box(page) { FabricNotConnectedCard(vm, problem) } }
        } else if (!vm.companyEnding && vm.productsLoad == Load.LOADING && vm.products == null) {
            item(key = "fabric-reading") { Box(page) { FabricReadingCard() } }
        }

        val rows = vm.customerRows
        item(key = "heading") {
            Box(page) {
                ListHeading(
                    "${rows.size} customer" + (if (rows.size == 1) "" else "s"),
                    "${rows.sumOf { it.people }} people on seats · tap a row to open it"
                )
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Box(page) {
                    EmptyCard(
                        if (all.isEmpty())
                            "No customers yet. A plant that registers itself from the application appears here as a demo; " +
                                "a customer you set up yourself is made with the + below."
                        else "No customer matches. Clear the search or choose All, or make one with the + below."
                    )
                }
            }
        }
        items(rows, key = { it.key }) { x ->
            Box(page) { CustomerRow(x, plans, vm.fabricProblem != null) { nav.open(Screen.CustomerWin(x.key)) } }
        }
    }
}

/**
 * A customer in the list: its name and GSTIN, each software on its own line, and when it next renews.
 * The edge wears the colour of the licence that ends first.
 */
@Composable
fun CustomerRow(x: Customer, plans: PlansData?, fabricDown: Boolean, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val edge = x.next?.state ?: x.subs.firstOrNull()?.state ?: "OTHER"
    RecordRow(if (x.soon) c.warn else Kinds.state(edge, c.isDark).fg, onOpen) {
        RowTitle(x.name)
        val tags = listOfNotNull(
            x.gstin,
            if (x.self) "self-registered" else null,
            if (x.both) (if (x.f?.linkedBy == "gstin") "linked by GSTIN" else "linked by hand") else null
        )
        if (tags.isNotEmpty()) Small(tags.joinToString(" · "))
        Spacer(Modifier.height(4.dp))
        SoftwareLines(x, plans, fabricDown)
        x.next?.let { n ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Next renewal ", color = c.muted, fontSize = 11.5f.sp)
                Text(Fmt.day(n.at), color = if (x.soon) c.warn else c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(" · " + (if (x.nextOnBoth) "both software" else Software.name(n.sw)), color = c.muted, fontSize = 11.5f.sp,
                    modifier = Modifier.weight(1f))
                Text("People ${x.w?.usersTotal ?: "—"} + ${x.f?.people ?: "—"}", color = c.muted, fontSize = 11.5f.sp)
            }
        }
    }
}

/** Each software of a customer on its own line; one it does not use says so, faintly. */
@Composable
fun SoftwareLines(x: Customer, plans: PlansData?, fabricDown: Boolean) {
    val c = LocalNexora.current
    val w = x.w
    if (w != null) {
        val s = w.swState
        SwLine(
            Software.WEIGHT, Customers.line(x, Software.WEIGHT, plans).orEmpty(),
            color = if (s == "SUSPENDED") c.bad else if (s == "EXPIRED") c.warn else null,
            /* 4.72.0 — audit #90: a paying licence the service counts as ending within 30 days */
            note = if (w.renewSoon || w.endingSoon) "renew soon" else null
        )
    } else SwLine(Software.WEIGHT, Software.WEIGHT_NAME + " — not taken", faint = true)
    val f = x.f
    if (f != null) {
        val s = f.shownState
        SwLine(
            Software.FABRIC, Customers.line(x, Software.FABRIC, plans).orEmpty(),
            color = if (s == "SUSPENDED") c.bad else if (s == "EXPIRED") c.warn else null,
            note = if (f.endingSoon) "renew soon" else null
        )
    } else SwLine(Software.FABRIC, Software.FABRIC_NAME + if (fabricDown) " — not connected" else " — not taken", faint = true)
}

/**
 * 4.72.0 — audit #40: a company Delete has archived — what it was, when it
 * goes for good, and Restore: the one thing that can be done to it (the
 * service refuses every other action on a deleted company), so the row is
 * not a way into the company's window.
 */
@Composable
internal fun DeletedRow(co: DeletedCompany, onRestore: () -> Unit) {
    val c = LocalNexora.current
    val a = Kinds.state("SUSPENDED", c.isDark)
    RecordRow(a.fg, null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(co.name, color = c.text, fontSize = 15.5f.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Small(
                    "deleted ${Fmt.dateTime(co.deletedAt)}" +
                        (co.deletedState?.let { " · was " + if (it == "DEMO") "a demo" else it.lowercase() } ?: "")
                )
            }
            Pill("deleted", "SUSPENDED")
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Mini("Erased on", Fmt.day(co.purgeAt))
            Mini("Days to restore", co.daysToPurge.toString(), color = if (co.daysToPurge <= 7) c.warn else null)
            Mini("Machines", co.machines.toString())
            Mini("People", co.people.toString())
        }

        Spacer(Modifier.height(8.dp))
        Small("${co.records} synced record(s), kept until then")
        co.gstin?.let { Mono(it) }
        co.email?.let { Mono(it) }

        Spacer(Modifier.height(10.dp))
        ConsoleButton("Restore", onRestore, kind = ButtonKind.Primary, small = true, modifier = Modifier.padding(top = 0.dp))
    }
}
