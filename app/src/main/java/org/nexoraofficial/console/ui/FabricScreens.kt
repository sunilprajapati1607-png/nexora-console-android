package org.nexoraofficial.console.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import org.nexoraofficial.console.data.SoftwareFilter
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   1.9.0 — FABRIC STOCK, IN THE SAME CONSOLE.

   Owner, 2026-10-07: "nexora console page single rahese badhi service tya
   thij update chalu bandh thase" — one console for every Nexora software,
   and each software's licences renewed, started and stopped from it.

   Nexora Loom & Fabric Stock keeps its OWN licence beside Weight Calc's:
   its own key, plan, days, seats, people and rights. These screens show it
   in its own colour (emerald, where Weight Calc is Nexora's blue) so the two
   are never taken for one. People and their rights are only read here —
   the company's administrator sets them inside Fabric Stock, as in Weight
   Calc. A Fabric Stock company with a Weight Calc company's GSTIN is that
   company (the service matches them); otherwise the owner links them here.
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

/** A Weight Calc company's state the way a software tag says it: "licensed · 245 days", "expired". */
fun Company.stateText(): String {
    val s = shownState
    return when (s) {
        "EXPIRED" -> "expired"
        "SUSPENDED" -> "suspended"
        else -> (if (s == "DEMO") "demo" else s.lowercase()) + " · " + when (daysLeft) {
            0 -> "ends today"
            1 -> "1 day"
            else -> "$daysLeft days"
        }
    }
}

/* ------------------------------------------------- when it cannot be shown */

/**
 * Fabric Stock is not reachable — the list of software would not come, or
 * Fabric Stock's own service is asleep or refused the key. Said once, at the
 * top, with Retry; Weight Calc's companies stay exactly where they are.
 */
@Composable
fun FabricNotConnectedCard(vm: ConsoleViewModel, problem: String) {
    val c = LocalNexora.current
    val trying = vm.productsLoad == Load.LOADING
    ColourCard("Fabric Stock is not connected", Icons.Outlined.CloudOff, "amber") {
        Text(problem, style = MaterialTheme.typography.bodyMedium, color = c.text)
        Spacer(Modifier.height(6.dp))
        Help(
            "Weight Calc is not affected. Fabric Stock's own service sleeps when nobody has used it for a " +
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
        Help("Its service may take up to a minute to wake. Weight Calc does not wait for it.")
    }
}

/* ------------------------------------------------------------ the list row */

/**
 * A Fabric Stock company in the Companies list — the same row as a Weight
 * Calc company's (a colour edge for its state), with Fabric Stock's figures
 * and a tag saying which software, and whether it belongs to a Weight Calc
 * company too.
 */
@Composable
fun FabricRow(f: FabricCompany, weight: Company?, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val state = f.shownState
    val a = Kinds.state(state, c.isDark)

    Column(
        Modifier
            .fillMaxWidth()
            .appear()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, a.fg.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .drawAccentEdge(a.fg, alpha = 1f)
            .pressable(onOpen)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.name, color = c.text, fontSize = 15.5f.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Mono(f.licenceKey)
            }
            Pill(if (state == "DEMO") "demo" else state.lowercase(), state)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.ChevronRight, "Open", tint = c.faint, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Mini("People", "${f.people}/${f.seats}")
            Mini("Devices", f.devices.toString())
            Mini(
                when (state) {
                    "EXPIRED" -> "Ended"
                    "SUSPENDED" -> "Ends"
                    else -> "Days left"
                },
                if (f.ended) Fmt.day(f.expiresAt) else if (f.daysLeft == 0) "today" else f.daysLeft.toString(),
                color = if (f.endingSoon) c.warn else null
            )
            Mini("Plan", f.plan?.lowercase()?.proper() ?: "-")
        }

        Spacer(Modifier.height(10.dp))
        WrapRow {
            if (weight != null) {
                SoftwareTag("Fabric Stock", "fabric")
                SoftwareTag("Weight Calc · " + weight.stateText(), "weight")
            } else {
                SoftwareTag("Fabric Stock only", "fabric")
                if (f.selfRegistered) Pill("self-registered", "SELF")
            }
        }
    }
}

/* ---------------------------------------------------- the software switch */

/**
 * The two software at the top of a company: Weight Calc (everything the
 * screen always showed) and Fabric Stock (its own licence, or how to give
 * it one). The one chosen wears its software's gradient.
 */
@Composable
fun SoftwareSwitch(fabricTab: Boolean, weightLine: String, fabricLine: String, onPick: (Boolean) -> Unit) {
    val c = LocalNexora.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgSunken)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Segment(Modifier.weight(1f), Icons.Outlined.Scale, "Weight Calc", weightLine, !fabricTab, BrandGradient) { onPick(false) }
        Segment(Modifier.weight(1f), Icons.Outlined.Inventory2, "Fabric Stock", fabricLine, fabricTab, FabricGradient) { onPick(true) }
    }
}

@Composable
private fun Segment(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    line: String,
    on: Boolean,
    colours: List<Color>,
    onClick: () -> Unit
) {
    val c = LocalNexora.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Brush.horizontalGradient(colours) else SolidColor(Color.Transparent))
            .pressable(onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) Color.White else colours[1], modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (on) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                line.proper(),
                color = if (on) Color.White.copy(alpha = 0.88f) else c.muted,
                fontSize = 11.5f.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** What the Fabric Stock half of the switch says under its name. */
fun fabricSwitchLine(vm: ConsoleViewModel, f: FabricCompany?): String = when {
    f != null -> f.stateText
    vm.fabricProblem != null -> "not connected"
    !vm.fabricReady -> "reading…"
    else -> "not used"
}

/* ------------------------------------------------- one Fabric Stock company */

/**
 * Everything about one Fabric Stock licence, as the items of a company's
 * list: who it is, where it stands, its people, its computers and phones,
 * and what can be done to it. The same items on a Weight Calc company's
 * Fabric Stock tab and on a Fabric-Stock-only company's own screen.
 */
fun LazyListScope.fabricItems(
    vm: ConsoleViewModel,
    nav: Navigator,
    f: FabricCompany,
    weight: Company?,
    page: Modifier,
    onAsk: (Ask) -> Unit,
    openWeight: Boolean
) {
    item(key = "fabric-head-${f.id}") { Column(page) { FabricIdentity(f, weight, vm, nav, openWeight) } }
    item(key = "fabric-facts-${f.id}") { Box(page) { FabricFacts(f) } }
    item(key = "fabric-people-${f.id}") { Box(page) { FabricPeopleCard(vm, f, onAsk) } }
    item(key = "fabric-devices-${f.id}") { Box(page) { FabricDevicesCard(vm, f, onAsk) } }
    item(key = "fabric-acts-${f.id}") { Box(page) { FabricActionsCard(vm, f, weight, onAsk) } }
}

@Composable
private fun FabricIdentity(f: FabricCompany, weight: Company?, vm: ConsoleViewModel, nav: Navigator, openWeight: Boolean) {
    val clipboard = LocalClipboardManager.current
    val c = LocalNexora.current
    val state = f.shownState

    /* the head on Fabric Stock's own sweep, so it is never taken for the Weight Calc licence */
    GradientBanner(Modifier.appear(0), colors = FabricGradient) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(f.name, style = MaterialTheme.typography.headlineSmall, color = Color.White,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                Text((if (state == "DEMO") "demo" else state.lowercase()).proper(), color = Color.White,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Fabric Stock · " + (f.plan?.let { "$it plan · " } ?: "") +
                (if (f.ended) "ended " + Fmt.day(f.expiresAt) else if (f.daysLeft == 0) "ends today" else "${f.daysLeft} days left"),
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f)
        )
        Text(
            "${f.people} of ${f.seats} seats · ${f.devices} " + (if (f.devices == 1) "computer or phone" else "computers and phones"),
            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f)
        )
    }
    Spacer(Modifier.height(12.dp))
    ConsoleCard {
        if (f.endingSoon) {
            MessageStrip(
                Msg("The Fabric Stock licence ends ${Fmt.day(f.expiresAt)} — call them to renew.", Msg.Kind.WARN),
                onDismiss = {}
            )
            Spacer(Modifier.height(12.dp))
        }

        /* which Weight Calc company it belongs to, and how the service knows */
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(Icons.Outlined.Link, accent(if (f.linked) "weight" else "grey"), size = 34)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        weight != null -> "Weight Calc: ${weight.name}"
                        f.linked -> "Weight Calc company #${f.companyId}"
                        else -> "Fabric Stock only"
                    },
                    color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Small(
                    when {
                        !f.linked -> "not linked to a Weight Calc company"
                        f.linkedBy == "hand" -> "Linked by hand"
                        f.linkedBy == "gstin" -> "Linked by the same GSTIN"
                        else -> "Linked"
                    }
                )
            }
            if (openWeight && weight != null) {
                ConsoleButton("Open", { nav.open(Screen.Company(weight.id)) }, small = true)
            }
        }

        Spacer(Modifier.height(12.dp))
        Fact("Fabric Stock licence key", Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(f.licenceKey, color = MaterialTheme.colorScheme.onSurface, size = 14f, modifier = Modifier.weight(1f))
                ConsoleButton("Copy", {
                    clipboard.setText(AnnotatedString(f.licenceKey))
                    vm.say("Copied ${f.licenceKey}", Msg.Kind.OK)
                }, small = true)
            }
        }

        val lines = buildList {
            f.gstin?.let { add("GSTIN" to it) }
            f.email?.let { add("Email" to it) }
            f.phone?.let { add("Mobile" to it) }
            f.loginId?.let { add("Login id" to it) }
            f.createdAt?.let { add("Created on" to Fmt.day(it)) }
            f.notes?.let { add("Note" to it) }
        }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            lines.forEach { (label, value) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Small(label, modifier = Modifier.width(120.dp))
                    Mono(value, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (f.selfRegistered) {
            Spacer(Modifier.height(8.dp))
            Pill("self-registered", "SELF")
        }
    }
}

@Composable
private fun FabricFacts(f: FabricCompany) {
    val c = LocalNexora.current
    val state = f.shownState
    ConsoleCard {
        Text("Where They Stand", style = CardTitleStyle)
        Small("on Fabric Stock — its own licence, apart from Weight Calc's")
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact("Seats (people)", Modifier.weight(1f)) {
                FactValue("${f.people} of ${f.seats}")
                val left = f.seats - f.people
                Small(if (left > 0) "$left available" else "none available")
                Spacer(Modifier.height(6.dp))
                Bar(f.people / f.seats.coerceAtLeast(1).toFloat(), full = f.people >= f.seats)
            }
            Fact("Computers and phones", Modifier.weight(1f)) {
                FactValue(f.devices.toString())
                Small("each one approved by the company")
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact(if (f.onDemo) "Demo started" else "Licence started", Modifier.weight(1f)) {
                FactValue(Fmt.day(f.periodStartedAt))
                if (f.periodDays > 0) Small("${f.periodDays}-day " + (if (f.onDemo) "demo" else "licence"))
            }
            Fact(
                when (state) {
                    "EXPIRED" -> "Ended"
                    "SUSPENDED" -> "Suspended · ends"
                    else -> "Days left"
                },
                Modifier.weight(1f)
            ) {
                if (f.ended) {
                    FactValue(Fmt.day(f.expiresAt))
                } else {
                    val warn = if (f.endingSoon) c.warn else null
                    FactValue(if (f.daysLeft == 0) "today" else f.daysLeft.toString(), color = warn)
                    Small(Fmt.day(f.expiresAt) + (if (f.endingSoon) " · renew soon" else ""), color = warn)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact("Offline allowed", Modifier.weight(1f)) {
                FactValue(if (f.graceDays > 0) "${f.graceDays} days" else "none")
                if (f.graceDays == 0) Small("stops when it cannot reach the service")
            }
            Fact("Plan", Modifier.weight(1f)) {
                FactValue(f.plan?.lowercase()?.proper() ?: "-")
                if (f.onDemo) Small("a demo has everything")
            }
        }
    }
}

/* ---- the people: read here, their rights set inside Fabric Stock ---- */

@Composable
private fun FabricPeopleCard(vm: ConsoleViewModel, f: FabricCompany, onAsk: (Ask) -> Unit) {
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
            d.users.isEmpty() -> Help("Nobody has been added yet. Set the administrator below; they add everybody else.")
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
private fun FabricDevicesCard(vm: ConsoleViewModel, f: FabricCompany, onAsk: (Ask) -> Unit) {
    val d = vm.fabricDetail?.takeIf { it.company.id == f.id }
    ConsoleCard {
        Text("Computers And Phones", style = CardTitleStyle)
        Small("each one is approved by the company; Nexora can withdraw one, and give back what it withdrew")
        Spacer(Modifier.height(10.dp))
        when {
            d == null -> Help(if (vm.fabricDetailError == null) "Reading…" else "Not read — Refresh under People.")
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
                                "and Give back puts it back. Weight Calc is not touched.",
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

/* ---- everything the owner may do, grouped as on a Weight Calc company ---- */

@Composable
private fun FabricActionsCard(vm: ConsoleViewModel, f: FabricCompany, weight: Company?, onAsk: (Ask) -> Unit) {
    val id = f.id

    ConsoleCard {
        Text("What You Can Do", style = CardTitleStyle)
        Small("on Fabric Stock only — Weight Calc's licence is not touched")

        Spacer(Modifier.height(14.dp))
        GroupHeading("Their details")
        WrapRow {
            ConsoleButton("Rename", {
                onAsk(Ask.Input(title = "Rename on Fabric Stock", body = "What this company is called in Fabric Stock.",
                    label = "Company name", initial = f.name,
                    validate = { if (it.isBlank()) "A name is required." else null },
                    onOk = { vm.fabricRename(id, it) }))
            }, small = true)
            ConsoleButton(if (f.gstin == null) "Add GSTIN" else "Change GSTIN", {
                onAsk(Ask.Input(title = "GSTIN for ${f.name}",
                    body = "Fifteen characters. A Weight Calc company with the same GSTIN is taken for the same customer.",
                    label = "GSTIN", initial = f.gstin.orEmpty(), onOk = { vm.fabricGstin(id, it) }))
            }, small = true)
            ConsoleButton("Email", {
                onAsk(Ask.Input(title = "Email for ${f.name}", label = "Email", initial = f.email.orEmpty(),
                    validate = {
                        val v = it.trim()
                        if (v.isEmpty() || Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(v)) null
                        else "That does not look like an email address."
                    },
                    onOk = { vm.fabricEmail(id, it) }))
            }, small = true)
            ConsoleButton("Mobile", {
                onAsk(Ask.Input(title = "Mobile for ${f.name}", label = "Mobile", initial = f.phone.orEmpty(),
                    onOk = { vm.fabricPhone(id, it) }))
            }, small = true)
            ConsoleButton("Note", {
                onAsk(Ask.Input(title = "A note about ${f.name}", body = "For you, not for them.", label = "Note",
                    initial = f.notes.orEmpty(), onOk = { vm.fabricNote(id, it) }))
            }, small = true)
        }

        Spacer(Modifier.height(16.dp))
        GroupHeading("Licence")
        WrapRow {
            if (f.onDemo) {
                ConsoleButton("Make licensed for a year", { vm.fabricMakeLicensed(f) }, kind = ButtonKind.Primary, small = true)
            }
            ConsoleButton("Add days", {
                onAsk(
                    Ask.Input(
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
                )
            }, small = true)
            ConsoleButton("+1 year", { vm.fabricAddDays(f, 365) }, small = true)
        }
        Why(
            if (f.onDemo) "turns this demo into a paying customer for a year from today"
            else "days are added to what is left — Fabric Stock starts the new period today"
        )

        Spacer(Modifier.height(16.dp))
        GroupHeading("Seats and offline days")
        WrapRow {
            ConsoleButton("Seats", {
                onAsk(Ask.Input(title = "How many people may sign in on Fabric Stock?",
                    body = "Fabric Stock's own seats — Weight Calc's are counted apart.",
                    label = "Seats", initial = f.seats.toString(), numeric = true,
                    validate = { if ((it.toIntOrNull() ?: 0) > 0) null else "Enter a number of seats." },
                    onOk = { v -> vm.fabricSeats(id, v.toInt()) }))
            }, small = true)
            ConsoleButton("Offline days", {
                onAsk(Ask.Input(title = "How many days may Fabric Stock work with no contact?",
                    body = "0 = none: it stops as soon as it cannot reach the service.",
                    label = "Offline days", initial = f.graceDays.toString(), numeric = true,
                    validate = { if ((it.toIntOrNull() ?: -1) >= 0) null else "Enter a number of days." },
                    onOk = { v -> vm.fabricGrace(id, v.toInt()) }))
            }, small = true)
        }

        Spacer(Modifier.height(16.dp))
        GroupHeading("Sign-in")
        WrapRow {
            ConsoleButton("Set administrator", {
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
            ConsoleButton("New company passcode", {
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

        Spacer(Modifier.height(16.dp))
        GroupHeading("Stop them")
        WrapRow {
            if (f.state == "SUSPENDED") {
                ConsoleButton("Restore", {
                    onAsk(
                        Ask.Confirm(
                            title = "Restore ${f.name} on Fabric Stock?",
                            body = "Their Fabric Stock computers and phones work again at their next check.",
                            confirmText = "Restore",
                            onYes = { vm.fabricResume(f) }
                        )
                    )
                }, kind = ButtonKind.Primary, small = true)
            } else {
                ConsoleButton("Suspend", {
                    onAsk(
                        Ask.Confirm(
                            title = "Suspend ${f.name} on Fabric Stock?",
                            body = "EVERY Fabric Stock computer and phone of theirs stops at its next check. Weight Calc " +
                                "is not touched. Nothing is deleted; Restore puts it back.",
                            confirmText = "Suspend",
                            danger = true,
                            onYes = { vm.fabricSuspend(f) }
                        )
                    )
                }, kind = ButtonKind.Danger, small = true)
            }
        }
        Why(
            if (f.state == "SUSPENDED") "every Fabric Stock computer and phone runs again"
            else "every Fabric Stock computer and phone stops at its next check; nothing is deleted"
        )

        Spacer(Modifier.height(16.dp))
        GroupHeading("Link")
        WrapRow {
            when {
                f.linked && f.linkedBy == "hand" -> ConsoleButton("Unlink", {
                    onAsk(
                        Ask.Confirm(
                            title = "Unlink ${f.name}?",
                            body = "The hand link to " + (weight?.name ?: "its Weight Calc company") + " is forgotten. If " +
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
                            body = "${f.name} on Fabric Stock and " + (weight?.name ?: "the Weight Calc company") +
                                " have the same GSTIN, so the service took them for one customer. Kept apart, they " +
                                "are listed as two companies and the GSTIN no longer joins them. Neither licence changes.",
                            confirmText = "Keep apart",
                            danger = true,
                            onYes = { vm.fabricApart(id) }
                        )
                    )
                }, kind = ButtonKind.Danger, small = true)
                else -> ConsoleButton("Link To A Weight Calc Company", {
                    val taken = vm.fabricCompanies.mapNotNull { it.companyId }.toSet()
                    onAsk(
                        Ask.Pick(
                            title = "Link ${f.name} to a Weight Calc company",
                            body = "Each keeps its own licence — key, plan, days, seats and people. Linked, they are " +
                                "shown as one customer.",
                            options = vm.data.companies.filter { it.id !in taken }.map {
                                Ask.Pick.Option(it.id, it.name, listOfNotNull(it.gstin, it.licenceKey).joinToString(" · "))
                            },
                            empty = "Every Weight Calc company already has its Fabric Stock company.",
                            onPick = { cid -> vm.fabricLink(id, cid, vm.data.companies.find { it.id == cid }?.name ?: "it") }
                        )
                    )
                }, kind = ButtonKind.Primary, small = true)
            }
        }
        Why(
            when {
                !f.linked -> "for a customer the GSTIN did not match — say which Weight Calc company it is"
                f.linkedBy == "hand" -> "you linked these two by hand"
                else -> "the service joined these two because the GSTIN is the same"
            }
        )
    }
}

/* ---- a Weight Calc company with no Fabric Stock ---- */

/**
 * The Fabric Stock tab of a company that has none: start a demo, make it
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
                    "company's name, GSTIN, email and mobile, and linked to it at once. Its Weight Calc licence is not touched.",
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

/* ---- a Fabric-Stock-only company, as a screen of its own ---- */

@Composable
fun FabricCompanyScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    fabricId: Int,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val f = vm.fabricById(fabricId)

    /* opening it reads its people and its computers and phones, and keeps them in step with every change */
    DisposableEffect(fabricId) {
        vm.fabricOpen = fabricId
        onDispose { if (vm.fabricOpen == fabricId) vm.fabricOpen = null }
    }
    LaunchedEffect(fabricId) { vm.loadFabricDetail(fabricId) }

    if (f == null) {
        val problem = vm.fabricProblem
        if (problem == null && vm.fabricReady) {
            /* gone from Fabric Stock's list while it was open */
            LaunchedEffect(Unit) { nav.backToRoot() }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = gutter, horizontalAlignment = Alignment.CenterHorizontally) {
            item { Box(page) { if (problem != null) FabricNotConnectedCard(vm, problem) else FabricReadingCard() } }
        }
        return
    }

    val weight = f.companyId?.let { cid -> vm.data.companies.find { it.id == cid } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        fabricItems(vm, nav, f, weight, page, onAsk, openWeight = true)
    }
}

/* ---- the dashboard: one tile per software ---- */

/**
 * "By Software": Weight Calc's licensed and demo counts, Fabric Stock's
 * licensed, demo and suspended — or that it is not connected. Each opens the
 * Companies list on that software.
 */
@Composable
fun SoftwareTiles(vm: ConsoleViewModel, nav: Navigator, modifier: Modifier = Modifier) {
    val c = LocalNexora.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("By Software", style = MaterialTheme.typography.titleSmall, color = c.muted, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp))
        /* the two tiles the same height, whichever says more */
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftwareTile(
                Icons.Outlined.Scale, vm.data.companies.size.toString(), "Weight Calc",
                "${vm.customerCount} licensed · ${vm.demoCount} demo", "weight", false, Modifier.weight(1f).fillMaxHeight()
            ) {
                vm.companyEnding = false
                vm.software = SoftwareFilter.WEIGHT
                nav.switchTo(Screen.Companies)
            }
            val problem = vm.fabricProblem
            val n = vm.fabricCounts
            SoftwareTile(
                Icons.Outlined.Inventory2,
                if (vm.fabricReady) n.total.toString() else "–",
                "Fabric Stock",
                when {
                    problem != null -> "Not connected"
                    !vm.fabricReady -> "Reading…"
                    else -> "${n.licensed} licensed · ${n.demo} demo\n${n.suspended} suspended"
                },
                "fabric", problem != null, Modifier.weight(1f).fillMaxHeight()
            ) {
                vm.companyEnding = false
                vm.software = SoftwareFilter.FABRIC
                nav.switchTo(Screen.Companies)
            }
        }
    }
}

/** A dashboard tile for one software: its icon, how many companies, its name, and its line — in its colour. */
@Composable
private fun SoftwareTile(
    icon: ImageVector,
    count: String,
    name: String,
    line: String,
    kind: String,
    trouble: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val a = accent(kind)
    val c = LocalNexora.current
    val c2 = androidx.compose.ui.graphics.lerp(a.fg, Color(0xFF7C3AED), 0.25f)
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.medium).pressable(onClick),
        shape = MaterialTheme.shapes.medium,
        color = a.bg,
        border = BorderStroke(1.dp, a.fg.copy(alpha = if (c.isDark) 0.40f else 0.24f))
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(a.fg.copy(alpha = if (c.isDark) 0.20f else 0.10f), a.bg)))) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(a.fg, c2))), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(count, style = MaterialTheme.typography.headlineSmall, color = a.fg, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    line,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (trouble) c.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.matchParentSize()) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(a.fg, c2))))
            }
        }
    }
}
