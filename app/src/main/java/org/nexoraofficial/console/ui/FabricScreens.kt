package org.nexoraofficial.console.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.FabricCompany
import org.nexoraofficial.console.data.FabricDevice
import org.nexoraofficial.console.data.FabricUser
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   1.9.0 — FABRIC STOCK, IN THE SAME CONSOLE.

   Owner, 2026-10-07: "nexora console page single rahese badhi service tya
   thij update chalu bandh thase" — one console for every Nexora software,
   and each software's licences renewed, started and stopped from it.

   Nexora Loom & Fabric Stock keeps its OWN licence beside Sales & Costing's:
   its own key, plan, days, seats, people and rights. People and their
   rights are only read here — the company's administrator sets them inside
   Fabric Stock. A Fabric Stock company with a Sales & Costing company's
   GSTIN is that company (the service matches them); otherwise the owner
   links them here.

   2.0.0 — these are the parts of a customer's window on its Fabric Stock
   tab (CustomerWindow.kt): its people, its computers and phones, which
   customer it is, and stopping it. The licence is the Licence tab.
   ====================================================================== */

/** A small software label: "Fabric Stock · Demo · 5 Days" — in that software's colour. */
@Composable
fun SoftwareTag(text: String, kind: String, modifier: Modifier = Modifier) {
    val a = accent(kind)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = a.bg,
        contentColor = a.fg,
        border = BorderStroke(1.dp, a.fg.copy(alpha = 0.28f))
    ) {
        Text(
            text.proper(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/* ------------------------------------------------- when it cannot be shown */

/**
 * Fabric Stock is not reachable — the list of software would not come, or
 * Fabric Stock's own service is asleep or refused the key. Said once, with
 * Retry; Sales & Costing stays exactly where it is.
 */
@Composable
fun FabricNotConnectedCard(vm: ConsoleViewModel, problem: String) {
    val c = LocalNexora.current
    val trying = vm.productsLoad == Load.LOADING
    ColourCard("Fabric Stock is not connected", Icons.Outlined.CloudOff, "amber") {
        Text(problem, style = MaterialTheme.typography.bodyMedium, color = c.text)
        Spacer(Modifier.height(6.dp))
        Help(
            "Sales & Costing is not affected. Fabric Stock's own service sleeps when nobody has used it for a " +
                "while, and waking it can take up to a minute."
        )
        Spacer(Modifier.height(10.dp))
        ConsoleButton(if (trying) "Trying…" else "Retry", { vm.loadProducts() }, kind = ButtonKind.Primary, small = true, enabled = !trying)
    }
}

/** Fabric Stock's companies are on their way (its service may be waking). No spinner: a still line. */
@Composable
fun FabricReadingCard() {
    ConsoleCard {
        Text("Reading Fabric Stock…", style = MaterialTheme.typography.titleSmall, color = LocalNexora.current.text)
        Spacer(Modifier.height(4.dp))
        Help("Its service may take up to a minute to wake. Sales & Costing does not wait for it.")
    }
}

/* ---- the questions the tool strip and the tabs share ---- */

internal fun fabricSuspendQuestion(vm: ConsoleViewModel, f: FabricCompany) = Ask.Confirm(
    title = "Suspend ${f.name} on Fabric Stock?",
    body = "EVERY Fabric Stock computer and phone of theirs stops at its next check. Sales & Costing " +
        "is not touched. Nothing is deleted; Restore puts it back.",
    confirmText = "Suspend",
    danger = true,
    onYes = { vm.fabricSuspend(f) }
)

internal fun fabricRestoreQuestion(vm: ConsoleViewModel, f: FabricCompany) = Ask.Confirm(
    title = "Restore ${f.name} on Fabric Stock?",
    body = "Their Fabric Stock computers and phones work again at their next check.",
    confirmText = "Restore",
    onYes = { vm.fabricResume(f) }
)

internal fun fabricAddDaysQuestion(vm: ConsoleViewModel, f: FabricCompany) = Ask.Input(
    title = "Add how many days to Fabric Stock?",
    body = if (f.renewDays(0) > 0)
        "Fabric Stock renews from today, so the ${f.renewDays(0)} days still left are carried into the new period."
    else "It has ended: it runs this many days from today.",
    label = "Days",
    initial = "30",
    numeric = true,
    confirmText = "Add",
    validate = { if ((it.toIntOrNull() ?: 0) > 0) null else "Enter a number of days." },
    onOk = { v -> vm.fabricAddDays(f, v.toInt()) }
)

/* ---- the people: read here, their rights set inside Fabric Stock ---- */

/** People tab, top: the administrator and the company passcode, set up by Nexora. */
@Composable
internal fun FabricSignInCard(vm: ConsoleViewModel, f: FabricCompany, onAsk: (Ask) -> Unit) {
    val id = f.id
    ConsoleCard {
        CardHeading("Sign-In", "the administrator adds everyone else and gives their rights inside Fabric Stock, apart from Sales & Costing")
        WrapRow {
            ConsoleButton("Set administrator…", {
                onAsk(
                    Ask.TwoInputs(
                        title = "Fabric Stock administrator for ${f.name}",
                        body = "The person who adds everybody else and gives them their rights, inside Fabric Stock. " +
                            "If a user of that name exists, they become the administrator and get the new PIN.",
                        labelA = "Name",
                        initialA = "Administrator",
                        labelB = "PIN (at least 4 characters)",
                        validate = { n, p ->
                            when {
                                n.isBlank() -> "A name is required."
                                p.length < 4 -> "A PIN of at least 4 characters is required."
                                else -> null
                            }
                        },
                        onOk = { n, p -> vm.fabricAdministrator(id, n, p) }
                    )
                )
            }, small = true)
            ConsoleButton("New company passcode…", {
                onAsk(
                    Ask.TwoInputs(
                        title = "New Fabric Stock passcode for ${f.name}",
                        body = "The login id is the first half of their login. Nobody can read the old passcode. " +
                            "Tell them the new one directly.",
                        labelA = "Company login id",
                        initialA = f.loginId ?: "",
                        labelB = "New passcode (at least 6 characters)",
                        validate = { _, p -> if (p.length < 6) "A passcode of at least 6 characters is required." else null },
                        onOk = { l, p -> vm.fabricPasscode(id, l, p) }
                    )
                )
            }, small = true)
        }
    }
}

@Composable
internal fun FabricPeopleCard(vm: ConsoleViewModel, f: FabricCompany, onAsk: (Ask) -> Unit) {
    val d = vm.fabricDetail?.takeIf { it.company.id == f.id }
    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("People", style = CardTitleStyle)
                Small("their rights are set by the company's administrator, inside Fabric Stock")
            }
            ConsoleButton(if (vm.fabricDetailBusy) "Reading…" else "Refresh", { vm.loadFabricDetail(f.id) }, small = true, enabled = !vm.fabricDetailBusy)
        }
        Spacer(Modifier.height(10.dp))
        vm.fabricDetailError?.let {
            MessageStrip(Msg(it, Msg.Kind.ERR), onDismiss = {})
            Spacer(Modifier.height(10.dp))
        }
        when {
            d == null -> if (vm.fabricDetailError == null) Help("Reading…")
            d.users.isEmpty() -> Help("Nobody has been added yet. Set the administrator above; they add everybody else.")
            else -> d.users.forEachIndexed { i, u ->
                if (i > 0) {
                    Spacer(Modifier.height(10.dp))
                    DashedRule()
                    Spacer(Modifier.height(10.dp))
                }
                FabricPersonRow(vm, f, u, onAsk)
            }
        }
    }
}

@Composable
private fun FabricPersonRow(vm: ConsoleViewModel, f: FabricCompany, u: FabricUser, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                u.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (u.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Pill(if (u.isAdmin) "admin" else "user", if (u.isAdmin) "LICENSED" else "OTHER")
            if (!u.active) {
                Spacer(Modifier.width(6.dp))
                Pill("off", "REVOKED")
            }
        }
        Small(
            (if (u.scope == "ALL") "sees everyone's work" else "sees own work") + " · " +
                (if (u.lastLoginAt != null) "last signed in ${Fmt.dateTime(u.lastLoginAt)}" else "never signed in")
        )
        if (!u.isAdmin && u.rights.isNotEmpty()) {
            Small("${u.rights.size} " + (if (u.rights.size == 1) "right" else "rights") + " given by their administrator")
        }
        if (u.lastSeenAt != null) Small("active ${Fmt.ago(u.lastSeenAt)} (${Fmt.dateTime(u.lastSeenAt)})", color = c.ok)
        u.email?.let { Mono(it, color = MaterialTheme.colorScheme.primary) }
        if (u.signedIn) {
            Small(
                "signed in on ${u.sessionDevice?.take(12)}…" + (if (u.sessionAt != null) " since ${Fmt.dateTime(u.sessionAt)}" else ""),
                color = c.ok
            )
            Spacer(Modifier.height(8.dp))
            ConsoleButton("Sign out", {
                onAsk(
                    Ask.Confirm(
                        title = "Sign ${u.name} out of Fabric Stock?",
                        body = "This releases the computer or phone their name is bound to — for the one that is lost, " +
                            "wiped or switched off and will never close tidily. Their PIN is unchanged and nothing is " +
                            "removed: their next sign-in simply works.",
                        confirmText = "Sign out",
                        onYes = { vm.fabricSignOut(f.id, u.id) }
                    )
                )
            }, small = true)
        }
    }
}

/* ---- the computers and phones ---- */

@Composable
internal fun FabricDevicesCard(vm: ConsoleViewModel, f: FabricCompany, onAsk: (Ask) -> Unit) {
    val d = vm.fabricDetail?.takeIf { it.company.id == f.id }
    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Computers And Phones", style = CardTitleStyle, modifier = Modifier.weight(1f))
            ConsoleButton(if (vm.fabricDetailBusy) "Reading…" else "Refresh", { vm.loadFabricDetail(f.id) }, small = true, enabled = !vm.fabricDetailBusy)
        }
        Small("each one is approved by the company; Nexora can withdraw one, and give back what it withdrew")
        Spacer(Modifier.height(10.dp))
        when {
            d == null -> Help(if (vm.fabricDetailError == null) "Reading…" else "Not read — Refresh above.")
            d.devices.isEmpty() -> Help("No computer or phone has joined yet.")
            else -> d.devices.forEachIndexed { i, dev ->
                if (i > 0) {
                    Spacer(Modifier.height(10.dp))
                    DashedRule()
                    Spacer(Modifier.height(10.dp))
                }
                FabricDeviceRow(vm, dev, onAsk)
            }
        }
    }
}

@Composable
private fun FabricDeviceRow(vm: ConsoleViewModel, dev: FabricDevice, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current
    val what = if (dev.isPhone) "phone" else "computer"
    val name = dev.name ?: (if (dev.isPhone) "Phone" else "Computer") + (dev.computerNo?.let { " $it" } ?: "")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconDot(if (dev.isPhone) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer, accent(if (dev.withdrawn) "bad" else "machine"), size = 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = c.text, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                when {
                    dev.withdrawn -> Pill("withdrawn", "REVOKED")
                    dev.pending -> Pill("waiting", "EXPIRED")
                    dev.approved -> Pill("approved", "LICENSED")
                }
            }
            Small(
                listOfNotNull(
                    what,
                    dev.computerNo?.let { "no. $it" },
                    dev.appVersion?.let { "version $it" }
                ).joinToString(" · ")
            )
            if (dev.lastSeen != null) Small("last seen ${Fmt.ago(dev.lastSeen)} (${Fmt.dateTime(dev.lastSeen)})")
            else Small("not seen yet")
            dev.signedInName?.let { Small("signed in: $it", color = c.ok) }
            when {
                dev.revokedBy == "NEXORA" -> Small("withdrawn by Nexora — Give back undoes it", color = c.bad)
                dev.revokedBy != null -> Small("withdrawn by the company — only they can give it back", color = c.bad)
                dev.pending -> Small("waiting for the company's administrator to approve it", color = c.warn)
                dev.approvedBy != null -> Small("approved by ${dev.approvedBy}")
            }
            Spacer(Modifier.height(8.dp))
            if (!dev.withdrawn) {
                ConsoleButton("Withdraw", {
                    onAsk(
                        Ask.Confirm(
                            title = "Withdraw $name?",
                            body = "This $what stops working on Fabric Stock at its next check. Nothing saved is lost, " +
                                "and Give back puts it back. Sales & Costing is not touched.",
                            confirmText = "Withdraw",
                            danger = true,
                            onYes = { vm.fabricRevoke(dev.id) }
                        )
                    )
                }, kind = ButtonKind.Danger, small = true)
            } else if (dev.nexoraWithdrew) {
                ConsoleButton("Give back", {
                    onAsk(
                        Ask.Confirm(
                            title = "Give $name back?",
                            body = "It works on Fabric Stock again from its next check.",
                            confirmText = "Give back",
                            onYes = { vm.fabricGiveBack(dev.id) }
                        )
                    )
                }, kind = ButtonKind.Primary, small = true)
            }
        }
    }
}

/* ---- Company tab: which customer it is, and stopping it ---- */

@Composable
internal fun FabricCompanyCard(vm: ConsoleViewModel, f: FabricCompany, weight: Company?, onAsk: (Ask) -> Unit) {
    val id = f.id
    ConsoleCard {
        CardHeading("Which Customer", "on Fabric Stock only — Sales & Costing's licence is not touched")
        Small(
            when {
                weight != null && f.linkedBy == "gstin" -> "Shown with ${weight.name} because the GSTIN is the same."
                weight != null -> "Linked to ${weight.name} by hand."
                f.linked -> "Linked to Sales & Costing company #${f.companyId}."
                else -> "Fabric Stock only — not with a Sales & Costing company."
            },
            color = LocalNexora.current.text
        )
        Spacer(Modifier.height(8.dp))
        WrapRow {
            when {
                f.linked && f.linkedBy == "hand" -> ConsoleButton("Unlink", {
                    onAsk(
                        Ask.Confirm(
                            title = "Unlink ${f.name}?",
                            body = "The hand link to " + (weight?.name ?: "its Sales & Costing company") + " is forgotten. If " +
                                "the two have the same GSTIN the service joins them again by it; otherwise " +
                                "${f.name} is listed on its own. Neither licence changes.",
                            confirmText = "Unlink",
                            onYes = { vm.fabricUnlink(id) }
                        )
                    )
                }, small = true)
                f.linked -> ConsoleButton("Not The Same Company", {
                    onAsk(
                        Ask.Confirm(
                            title = "Not the same company?",
                            body = "${f.name} on Fabric Stock and " + (weight?.name ?: "the Sales & Costing company") +
                                " have the same GSTIN, so the service took them for one customer. Kept apart, they " +
                                "are listed as two customers and the GSTIN no longer joins them. Neither licence changes.",
                            confirmText = "Keep apart",
                            danger = true,
                            onYes = { vm.fabricApart(id) }
                        )
                    )
                }, kind = ButtonKind.Danger, small = true)
                else -> ConsoleButton("Link To A Sales & Costing Company", {
                    val taken = vm.fabricCompanies.mapNotNull { it.companyId }.toSet()
                    onAsk(
                        Ask.Pick(
                            title = "Link ${f.name} to a Sales & Costing company",
                            body = "Each keeps its own licence — key, plan, days, seats and people. Linked, they are " +
                                "shown as one customer.",
                            options = vm.data.companies.filter { it.id !in taken }.map {
                                Ask.Pick.Option(it.id, it.name, listOfNotNull(it.gstin, it.licenceKey).joinToString(" · "))
                            },
                            empty = "Every Sales & Costing company already has its Fabric Stock company.",
                            onPick = { cid -> vm.fabricLink(id, cid, vm.data.companies.find { it.id == cid }?.name ?: "it") }
                        )
                    )
                }, kind = ButtonKind.Primary, small = true, maxLines = 2)
            }
        }

        Spacer(Modifier.height(16.dp))
        GroupHeading("Stop them")
        WrapRow {
            if (f.state == "SUSPENDED") {
                ConsoleButton("Restore", { onAsk(fabricRestoreQuestion(vm, f)) }, kind = ButtonKind.Primary, small = true)
            } else {
                ConsoleButton("Suspend", { onAsk(fabricSuspendQuestion(vm, f)) }, kind = ButtonKind.Danger, small = true)
            }
        }
        Why(
            if (f.state == "SUSPENDED") "every Fabric Stock computer and phone runs again"
            else "every Fabric Stock computer and phone stops at its next check; nothing is deleted, and Sales & Costing is not touched"
        )
    }
}

/* ---- a Sales & Costing customer with no Fabric Stock ---- */

/**
 * The Fabric Stock tab of a customer that has none: start a demo, make it
 * licensed, or link the Fabric Stock company it already is (one whose
 * GSTIN did not match).
 */
@Composable
fun NotUsingFabricCard(co: Company, vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {
    val start = { demo: Boolean ->
        onAsk(
            Ask.Confirm(
                title = if (demo) "Start a 7-day Fabric Stock demo for ${co.name}?" else "Licence ${co.name} on Fabric Stock for a year?",
                body = "A Fabric Stock licence of its own — its own key, with ${co.seats} " +
                    (if (co.seats == 1) "seat" else "seats") + " and ${co.graceDays} offline " +
                    (if (co.graceDays == 1) "day" else "days") + " to begin with (change them after) — made with this " +
                    "company's name, GSTIN, email and mobile, and linked to it at once. Its Sales & Costing licence is not touched.",
                confirmText = if (demo) "Start the demo" else "Make licensed",
                onYes = { vm.startFabric(co, demo) }
            )
        )
    }
    ColourCard("Not using Fabric Stock", Icons.Outlined.Inventory2, "fabric") {
        Help(
            "${co.name} has no Fabric Stock (Nexora Loom & Fabric Stock) licence. Start one here, or link the " +
                "Fabric Stock company it already has if its GSTIN did not match."
        )
        Spacer(Modifier.height(12.dp))
        WrapRow {
            ConsoleButton("Start A 7-Day Demo", { start(true) }, kind = ButtonKind.Primary, small = true)
            ConsoleButton("Make Licensed (1 Year)", { start(false) }, small = true)
            ConsoleButton("Link An Existing Fabric Stock Company", {
                onAsk(
                    Ask.Pick(
                        title = "Which Fabric Stock company is ${co.name}?",
                        body = "Each keeps its own licence — key, plan, days, seats and people. Linked, they are shown as one customer.",
                        options = vm.fabricOnly.filter { !it.linked }.map {
                            Ask.Pick.Option(it.id, it.name, listOfNotNull(it.gstin, it.licenceKey, it.stateText.proper()).joinToString(" · "))
                        },
                        empty = "There is no Fabric Stock company on its own to link.",
                        onPick = { fid -> vm.fabricLink(fid, co.id, co.name) }
                    )
                )
            }, small = true, maxLines = 2)
        }
    }
}
