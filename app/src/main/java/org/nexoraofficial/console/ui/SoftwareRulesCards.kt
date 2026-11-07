package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.data.Customer
import org.nexoraofficial.console.data.RulesForm
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.1.0 — EVERY SOFTWARE ITS OWN DEMO RULES, AND EVERY SOFTWARE SIDE BY SIDE.

   The owner sells several separate Nexora softwares to the same plants.
   Each keeps its own licence, plan, validity, seats and demo rules on its
   own server; the console only reads them and shows them side by side.
   A card per software: how long its demo runs, the offline days a demo
   has, its demo seats (a software reached through the console service),
   whether a plant may sign itself up, and what happens when a licence
   ends — changed for that ONE software, on its own server.
   ====================================================================== */

/** "Read-only" / "Stops" — what happens when a licence ends, in words. */
fun expiredWord(mode: String?): String = when (mode?.uppercase()) {
    "READONLY" -> "Read-only"
    "HARDSTOP" -> "Stops"
    null, "" -> "—"
    else -> mode
}

/** Where a software's demo rules are kept, in words. */
fun rulesWhere(sw: String): String {
    val n = Software.name(sw)
    return if (Software.isRemote(sw)) "$n's own rules, kept on $n's own server — they apply to $n only"
    else "Sales & Costing's own rules, kept on the console service — they apply to Sales & Costing only"
}

/** One software's demo rules, with Change. A software whose rules could not be read shows the service's words. */
@Composable
fun DemoRulesCard(vm: ConsoleViewModel, sw: String, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current
    val n = Software.name(sw)
    val r = vm.rulesOf(sw)
    val reading = vm.rulesLoad == Load.LOADING
    ColourCard(
        "$n — demo rules", SwColours.icon(sw), SwColours.kind(sw),
        trailing = if (r?.ok == true) ({
            ConsoleButton("Change", {
                onAsk(Ask.DemoRules(sw, RulesForm.of(r)) { form -> vm.saveRules(sw, form) })
            }, small = true, enabled = !vm.busy)
        }) else null
    ) {
        Small(rulesWhere(sw))
        Spacer(Modifier.height(10.dp))
        when {
            r == null -> {
                Help(
                    when {
                        vm.rulesLoad == Load.FAILED -> vm.rulesError ?: "The demo rules could not be read."
                        reading || vm.rulesLoad == Load.IDLE -> "Reading $n's demo rules…"
                        else -> "This service does not list $n's demo rules."
                    }
                )
                if (vm.rulesLoad == Load.FAILED || vm.rulesLoad == Load.READY) {
                    Spacer(Modifier.height(8.dp))
                    ConsoleButton(if (reading) "Trying…" else "Retry", { vm.loadRules() }, small = true, enabled = !reading)
                }
            }
            !r.ok -> {
                Text(r.problem, style = MaterialTheme.typography.bodyMedium, color = c.text)
                Spacer(Modifier.height(8.dp))
                ConsoleButton(if (reading) "Trying…" else "Retry", { vm.loadRules() }, kind = ButtonKind.Primary, small = true, enabled = !reading)
            }
            else -> {
                FieldPair(
                    { ReadField("Demo length", r.trialDays?.let { if (it == 1) "1 day" else "$it days" } ?: "—", it) },
                    { ReadField("Offline days for a demo", r.demoGraceDays?.let { if (it == 0) "none" else if (it == 1) "1 day" else "$it days" } ?: "—", it) }
                )
                Spacer(Modifier.height(8.dp))
                FieldPair(
                    {
                        ReadField("New sign-ups", when (r.signupsOpen) { true -> "Open"; false -> "Closed"; null -> "—" }, it,
                            valueColor = if (r.signupsOpen == false) c.warn else null,
                            sub = if (r.signupsOpen == false) "a plant cannot start a demo itself" else "a plant may start a demo itself")
                    },
                    {
                        ReadField("When a licence ends", expiredWord(r.expiredMode), it,
                            sub = if (r.expiredMode == "HARDSTOP") "the software stops" else "opens read-only until renewed")
                    }
                )
                if (Software.isRemote(sw)) {
                    Spacer(Modifier.height(8.dp))
                    ReadField("Demo seats", r.demoSeats?.let { if (it == 1) "1 person" else "$it people" } ?: "—", sub = "people a demo may have")
                }
            }
        }
    }
}

/** What the demo rules are, said once under the cards. */
@Composable
fun DemoRulesHelp() {
    Help(
        "Each software keeps its own demo rules on its own server — a change here is sent to that one software only, " +
            "and the others keep theirs. A demo started from this console runs as long as that software's own rule says."
    )
}

/**
 * The software shown side by side on the dashboard and the customers list: Sales & Costing and Fabric Stock always
 * (as 2.0 showed them), MSME ERP and Nexora Consult once the service lists them (read, or not reachable with a reason).
 */
fun sideBySide(vm: ConsoleViewModel): List<String> =
    Software.LICENSED.filter { it == Software.WEIGHT || it == Software.FABRIC || vm.products?.of(it) != null }

/**
 * The figure a software is shown with on the dashboard and the customers list: how many customers, and how many of
 * them are licensed, on a demo or suspended — or, for a software the service could not read, "not connected", never 0.
 */
fun softwareFig(vm: ConsoleViewModel, all: List<Customer>, sw: String, slot: Int, onClick: () -> Unit): Fig {
    val states = all.mapNotNull { it.sub(sw)?.state }
    val n = { s: String -> states.count { it == s } }
    val counts = "${n("LICENSED")} licensed · ${n("DEMO")} demo · ${n("SUSPENDED")} suspended"
    if (!Software.isRemote(sw)) return Fig(Software.name(sw), states.size.toString(), counts, slot, onClick)
    val ready = vm.remoteReady(sw)
    return Fig(
        Software.name(sw), if (ready) states.size.toString() else "—",
        when {
            vm.remoteProblem(sw) != null -> "not connected"
            !ready -> "reading…"
            else -> counts
        }, slot, onClick
    )
}
