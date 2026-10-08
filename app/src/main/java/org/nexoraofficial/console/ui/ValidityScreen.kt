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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.ValQuick
import org.nexoraofficial.console.data.Validity
import org.nexoraofficial.console.data.ValidityRow
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — VALIDITY & RENEWALS (as the web console's renderValidity).

   Every licence of every software, one row each, the one ending first at
   the top: which plan, its state, when it ends, the days left, and the
   last payment it had. A row opens the customer on that software's
   Payments — where the renewal is recorded.
   ====================================================================== */

@Composable
fun ValidityScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier) {
    val all = vm.validityAll
    val rows = vm.validityShown
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search") { Box(page) { SearchBox(vm.validityQuery, { vm.validityQuery = it }, "Customer, GSTIN…") } }
        item(key = "quick") {
            Box(page) {
                QuickRow(ValQuick.entries.map { q -> Quick(q.name, q.label, all.count { Validity.keep(it, q) }) }, vm.validityQuick.name) {
                    vm.validityQuick = ValQuick.valueOf(it)
                }
            }
        }
        item(key = "soft") {
            Box(page) {
                QuickRow(
                    listOf(Quick("ANY", "Any software"), Quick(Software.WEIGHT, Software.WEIGHT_NAME), Quick(Software.FABRIC, Software.FABRIC_NAME)),
                    vm.validitySoft ?: "ANY"
                ) { vm.validitySoft = it.takeIf { s -> s != "ANY" } }
            }
        }
        item(key = "figs") {
            Box(page) {
                val d30 = all.filter { Validity.keep(it, ValQuick.D30) }
                FigGrid(
                    listOf(
                        Fig("Ending in 7 days", all.count { Validity.keep(it, ValQuick.D7) }.toString(), "ring them now", 2) {
                            vm.validityQuick = ValQuick.D7
                        },
                        Fig("Ending in 30 days", d30.size.toString(),
                            "${d30.count { it.state == "LICENSED" }} paying · " + counted(d30.count { it.state == "DEMO" }, "demo"), 0) {
                            vm.validityQuick = ValQuick.D30
                        },
                        Fig("Ended, not renewed", all.count { it.state == "EXPIRED" }.toString(), "read-only until renewed", 3) {
                            vm.validityQuick = ValQuick.ENDED
                        },
                        Fig("Licences", all.size.toString(),
                            "${all.count { it.sw == Software.WEIGHT }} Sales & Costing · ${all.count { it.sw == Software.FABRIC }} Fabric Stock", 1)
                    )
                )
            }
        }
        if (vm.fabricProblem != null) {
            item(key = "fabric") { Box(page) { FabricNotConnectedCard(vm, vm.fabricProblem!!) } }
        }
        item(key = "heading") {
            Box(page) { ListHeading("${rows.size} licence" + (if (rows.size == 1) "" else "s"), "ending first · tap for the customer") }
        }
        if (rows.isEmpty()) item(key = "empty") { Box(page) { EmptyCard("Nothing in this view.") } }
        items(rows, key = { "${it.c.key}-${it.sw}" }) { r ->
            Box(page) { ValidityRowCard(r) { nav.open(Screen.CustomerWin(r.c.key, r.sw, "payments")) } }
        }
    }
}

/** One licence: the customer, its software and plan, its state, when it ends, the days left, and its last payment. */
@Composable
fun ValidityRowCard(r: ValidityRow, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val soon = r.live && r.left <= 30
    RecordRow(if (soon) c.warn else stateColour(r.state), onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(r.c.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                SwLine(r.sw, Software.name(r.sw) + " · " + r.plan)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Customers.stateWord(r.state), color = stateColour(r.state), fontSize = 12.5f.sp, fontWeight = FontWeight.Bold)
                    Text("  ·  started ${Fmt.day(r.start)}  ·  ${r.seats} seat" + (if (r.seats == 1) "" else "s"), color = c.muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(if (r.live) r.left.toString() else "—", color = if (soon) c.warn else c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (r.live) (if (r.left == 1) "day left" else "days left") else "", color = c.muted, fontSize = 10.5f.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ends ", color = c.muted, fontSize = 12.sp)
            Text(Fmt.day(r.ends), color = c.text, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val p = r.pay
            if (p != null) {
                Text(Money.rupees(p.amount), color = c.text, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold)
                Text(" · ${Fmt.day(p.paidOn)} · ${p.kindText}", color = c.muted, fontSize = 11.5f.sp)
            } else Text("no payment recorded", color = c.faint, fontSize = 11.5f.sp)
        }
    }
}
