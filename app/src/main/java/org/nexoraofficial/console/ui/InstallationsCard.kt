package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Licence
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * The installations card — the console's table, read down instead of across.
 *
 * A ten-column table is unreadable on a phone, so each machine is a row of
 * its own with the same ten facts stacked: who it is, what state it is in,
 * its clock, what it has used, and the three things that can be done to it.
 */
@Composable
fun InstallationsCard(vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current
    val rows = vm.installations
    val filtered = vm.companyFilter?.let { id -> vm.data.companies.find { it.id == id } }

    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Installations", style = CardTitleStyle, modifier = Modifier.weight(1f))
            Small(
                if (filtered != null) "— ${filtered.name} only"
                else "— ${rows.size} of ${vm.data.licences.size}"
            )
        }

        Spacer(Modifier.height(10.dp))
        ConsoleField(
            label = null,
            value = vm.installQuery,
            onValueChange = { vm.installQuery = it },
            placeholder = "Search company, key, email, device…"
        )

        if (filtered != null) {
            Spacer(Modifier.height(8.dp))
            ConsoleButton("Show all companies", { vm.companyFilter = null }, small = true)
        }

        Spacer(Modifier.height(12.dp))

        if (rows.isEmpty()) {
            Help("Nothing here yet.")
        } else {
            rows.forEachIndexed { i, l ->
                if (i > 0) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    Spacer(Modifier.height(12.dp))
                }
                InstallationRow(l, vm, onAsk)
            }
        }

        Spacer(Modifier.height(12.dp))
        Help(
            "The clock belongs to the company, not the machine. Machines take no seat — revoke " +
                "one to stop that computer, suspend the company to stop all of them. Seats are " +
                "the people, under a company's People."
        )
    }
}

@Composable
private fun InstallationRow(l: Licence, vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current
    val state = l.shownState

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    l.coName ?: l.company ?: "—",
                    color = c.text,
                    fontSize = 14.5f.sp,
                    fontWeight = FontWeight.Bold
                )
                if (l.seatNo > 0) {
                    Mono("seat ${l.seatNo} of ${l.coSeats}")
                }
                Spacer(Modifier.height(3.dp))
                Mono(l.deviceId.take(12) + "…")
                l.deviceName?.let { Mono(it) }
                /* 4.43.0 — who is on it. A machine with nobody signed in
                   can only read, which is why this is worth a line. */
                if (!l.onUser.isNullOrBlank()) {
                    Small(
                        "signed in: ${l.onUser}" +
                            (if (l.onSince != null) " · since ${Fmt.dateTime(l.onSince)}" else ""),
                        color = c.ok
                    )
                } else {
                    Small("nobody signed in — read-only")
                }
            }
            Pill(state.lowercase(), state)
        }

        Spacer(Modifier.height(8.dp))

        /* The table's remaining columns, as label-and-figure pairs. */
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell("Days left", Modifier.weight(1f)) {
                Text(
                    if (state == "EXPIRED" || state == "REVOKED") "—"
                    else if (l.daysLeft == 0) "today" else l.daysLeft.toString(),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Cell("Transactions", Modifier.weight(1f)) {
                Text(
                    l.txnCount.toString(),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (l.usageResetAt != null) Mono("reset ${Fmt.day(l.usageResetAt)}")
            }
            Cell("Hours", Modifier.weight(1f)) {
                Text(
                    Fmt.hours(l.usageMinutes),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell("Started", Modifier.weight(1f)) { Small(Fmt.day(l.trialStartedAt), color = c.text) }
            Cell("Last seen", Modifier.weight(1f)) { Small(Fmt.day(l.lastSeenAt), color = c.text) }
            Cell("Version", Modifier.weight(1f)) { Small(l.appVersion ?: "—", color = c.text) }
        }

        if (l.email != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Small("Email ")
                Mono(l.email)
            }
        }

        Spacer(Modifier.height(10.dp))
        WrapRow {
            ConsoleButton("Reset usage", {
                onAsk(
                    Ask.Confirm(
                        title = "Start this machine's count and hours again from zero?",
                        body = "Nothing saved is touched.",
                        confirmText = "Reset",
                        onYes = { vm.licenceAction(l.deviceId, "resetusage", "Usage reset.") }
                    )
                )
            }, small = true)

            if (l.state == "REVOKED") {
                ConsoleButton(
                    "Restore",
                    { vm.licenceAction(l.deviceId, "restore", "Restored.") },
                    small = true
                )
            } else {
                ConsoleButton("Revoke", {
                    onAsk(
                        Ask.Confirm(
                            title = "Revoke this installation?",
                            body = "It stops calculating at its next check. It frees no seat — " +
                                "seats are people, and a machine never held one. The company " +
                                "keeps running.",
                            confirmText = "Revoke",
                            danger = true,
                            onYes = { vm.licenceAction(l.deviceId, "revoke", "Revoked.") }
                        )
                    )
                }, kind = ButtonKind.Danger, small = true)
            }

            ConsoleButton("Delete", {
                onAsk(
                    Ask.Confirm(
                        title = "Delete the installation \"${l.coName ?: l.company ?: l.deviceName ?: l.deviceId}\"?",
                        body = "The row is removed altogether. If the machine is still in use it " +
                            "can activate again — use Revoke to stop a machine, " +
                            "and this to tidy away one that is finished with.",
                        confirmText = "Delete",
                        danger = true,
                        onYes = { vm.licenceAction(l.deviceId, "delete", "Installation deleted.") }
                    )
                )
            }, kind = ButtonKind.Danger, small = true)
        }
    }
}

/** th + td, stacked: the column's name over its figure. */
@Composable
private fun Cell(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = LocalNexora.current
    Column(modifier) {
        Text(
            label.uppercase(),
            color = c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.44.sp
        )
        Spacer(Modifier.height(2.dp))
        content()
    }
}
