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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Person
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * ONE CUSTOMER, AS A SCREEN OF ITS OWN.
 *
 * The web console opens a panel inside a long page. A phone opens a screen:
 * the facts at the top, then the people, then everything that can be done to
 * them grouped the way somebody actually thinks about it — the licence, the
 * machines, the people, GST, usage, and stopping them.
 */
@Composable
fun CompanyScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    companyId: Int,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val company = vm.data.companies.find { it.id == companyId }

    /* Opening the screen is what asks for the people. */
    LaunchedEffect(companyId) { vm.loadPeople(companyId) }

    if (company == null) {
        /* Deleted while it was open, or the list has been reloaded without it. */
        LaunchedEffect(Unit) { nav.backToRoot() }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Box(page) { IdentityCard(company, vm) } }
        item { Box(page) { FactsCard(company) } }
        item { Box(page) { PeopleCard(company, vm, onAsk) } }
        item { Box(page) { ActionsCard(company, vm, nav, onAsk) } }
    }
}

/* ---- who they are ---- */

@Composable
private fun IdentityCard(company: Company, vm: ConsoleViewModel) {

    val clipboard = LocalClipboardManager.current
    val state = company.shownState

    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                company.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Pill(if (state == "DEMO") "demo" else state.lowercase(), state)
        }

        Spacer(Modifier.height(8.dp))
        WrapRow {
            if (company.selfRegistered) Pill("self-registered", "SELF")
            if (company.gstin != null) {
                val g = company.gstStatus ?: "UNVERIFIED"
                Pill(
                    when (g) {
                        "VERIFIED" -> "GST verified"
                        "FAILED" -> "GST failed"
                        else -> "GST not yet verified"
                    },
                    if (g == "VERIFIED") "LICENSED" else g
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Fact("Licence key", Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(
                    company.licenceKey,
                    color = MaterialTheme.colorScheme.onSurface,
                    size = 14f,
                    modifier = Modifier.weight(1f)
                )
                ConsoleButton("Copy", {
                    clipboard.setText(AnnotatedString(company.licenceKey))
                    vm.say("Copied ${company.licenceKey}", Msg.Kind.OK)
                }, small = true)
            }
        }

        val lines = buildList {
            company.gstin?.let { add("GSTIN" to it) }
            company.email?.let { add("Email" to it) }
            company.phone?.let { add("Mobile" to it) }
            company.loginId?.let { add("Login id" to it) }
            company.registeredIp?.let { add("Registered from" to it) }
            company.registeredAt?.let { add("Registered on" to Fmt.day(it)) }
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
    }
}

/* ---- what the figures say ---- */

@Composable
private fun FactsCard(company: Company) {

    val state = company.shownState

    ConsoleCard {
        Text("Where they stand", style = CardTitleStyle)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            /* A seat is a PERSON; machines are counted but not rationed. */
            Fact("Seats (people)", Modifier.weight(1f)) {
                FactValue("${company.seatsUsed} of ${company.seats}")
                val left = company.seats - company.seatsUsed
                Small(if (left > 0) "$left available" else "none available")
                Spacer(Modifier.height(6.dp))
                Bar(
                    company.seatsUsed / company.seats.coerceAtLeast(1).toFloat(),
                    full = company.seatsUsed >= company.seats
                )
            }
            Fact("Computers", Modifier.weight(1f)) {
                FactValue(company.machinesUsed.toString())
                Small("not counted against seats")
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact(
                when (state) {
                    "EXPIRED" -> "Ended"
                    "SUSPENDED" -> "Suspended · ends"
                    else -> "Days left"
                },
                Modifier.weight(1f)
            ) {
                if (state == "EXPIRED" || state == "SUSPENDED") {
                    FactValue(Fmt.day(company.expiresAt))
                } else {
                    FactValue(if (company.daysLeft == 0) "today" else company.daysLeft.toString())
                    Small(Fmt.day(company.expiresAt))
                }
            }
            Fact("Offline allowed", Modifier.weight(1f)) {
                FactValue(if (company.graceDays > 0) "${company.graceDays} days" else "none")
                if (company.graceDays == 0) Small("stops when it cannot reach the service")
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact("Hours in use", Modifier.weight(1f)) {
                FactValue(Fmt.hours(company.usageMinutes))
            }
            Fact("Transactions", Modifier.weight(1f)) {
                TransactionsFigure(company.txnUsed, company.txnLimit)
            }
        }
    }
}

@Composable
private fun TransactionsFigure(used: Int, limit: Int) {
    val c = LocalNexora.current
    if (limit <= 0) {
        FactValue(used.toString())
        Small("no limit")
        return
    }
    val pct = (used / limit.toFloat()).coerceIn(0f, 1f)
    val colour = when {
        used >= limit -> c.bad
        used >= limit * 0.9 -> c.warn
        else -> MaterialTheme.colorScheme.primary
    }
    FactValue(used.toString())
    Small("of $limit")
    Spacer(Modifier.height(6.dp))
    Bar(pct, full = used >= limit, color = colour)
    if (used >= limit) Small("limit reached — read-only", color = c.bad)
}

/* ---- who may sign in ---- */

@Composable
private fun PeopleCard(company: Company, vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {

    val people = vm.people

    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("People", style = CardTitleStyle, modifier = Modifier.weight(1f))
            ConsoleButton("Refresh", { vm.loadPeople(company.id) }, small = true)
        }

        Spacer(Modifier.height(10.dp))

        when {
            vm.peopleError != null ->
                MessageStrip(Msg(vm.peopleError!!, Msg.Kind.ERR), onDismiss = {})

            people == null -> Help("Reading…")

            else -> {
                Help(
                    "${people.capCount} of ${people.capMax} seat(s) taken. A PIN cannot be shown " +
                        "here or anywhere else — it is stored scrambled, which is what stops " +
                        "anyone who gets the database signing in as your customers. When somebody " +
                        "forgets theirs, set a new one and tell them."
                )
                Spacer(Modifier.height(12.dp))

                if (people.users.isEmpty()) {
                    Help("Nobody has been added to this company yet.")
                } else {
                    people.users.forEachIndexed { i, u ->
                        if (i > 0) {
                            Spacer(Modifier.height(10.dp))
                            DashedRule()
                            Spacer(Modifier.height(10.dp))
                        }
                        PersonRow(company, u, vm, onAsk)
                    }
                }

                Spacer(Modifier.height(14.dp))
                ConsoleButton("Add a person", {
                    onAsk(
                        Ask.AddPerson(company.name) { name, pin, email, admin ->
                            vm.addPerson(company.id, name, pin, email, admin)
                        }
                    )
                }, kind = ButtonKind.Primary, small = true)
            }
        }
    }
}

@Composable
private fun PersonRow(company: Company, u: Person, vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {
    val c = LocalNexora.current

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                u.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (u.active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (u.isAdmin) Pill("admin", "LICENSED")
            if (!u.active) {
                Spacer(Modifier.width(6.dp))
                Pill("off", "REVOKED")
            }
        }
        Small(
            (if (u.scope == "ALL") "sees everyone's work" else "sees own work") + " · " +
                (if (u.lastLoginAt != null) "last signed in ${Fmt.day(u.lastLoginAt)}"
                else "never signed in")
        )
        if (u.email != null) {
            Mono(u.email, color = MaterialTheme.colorScheme.primary)
        } else {
            Small("no address — will not get the circulars", color = c.warn)
        }
        /* 4.43.0 — one person, one place at a time. Where they are is the
           first thing asked when somebody rings to say they cannot get in. */
        if (u.signedIn) {
            Small(
                "signed in on ${u.sessionDevice?.take(12)}…" +
                    (if (u.sessionAt != null) " since ${Fmt.dateTime(u.sessionAt)}" else ""),
                color = c.ok
            )
        }

        Spacer(Modifier.height(8.dp))
        WrapRow {
            if (u.signedIn) {
                ConsoleButton("Sign out", {
                    onAsk(
                        Ask.Confirm(
                            title = "Sign ${u.name} out?",
                            body = "This releases the machine their name is bound to — for the one " +
                                "that is lost, wiped or switched off in a shed and will never " +
                                "close tidily. Their PIN is unchanged and nothing is removed: " +
                                "their next sign-in, anywhere, simply works.",
                            confirmText = "Sign out",
                            onYes = { vm.signOutPerson(company.id, u.id) }
                        )
                    )
                }, small = true)
            }
            ConsoleButton(
                if (u.isAdmin) "Make ordinary" else "Make admin",
                {
                    val to = if (u.isAdmin) "USER" else "ADMIN"
                    onAsk(
                        Ask.Confirm(
                            title = "Make ${u.name} " +
                                (if (to == "ADMIN") "an administrator?" else "an ordinary user?"),
                            body = if (to == "ADMIN")
                                "They will be able to add and remove people from inside the " +
                                    "application, and see everyone's work."
                            else "They will no longer be able to add or remove anybody.",
                            confirmText = "Change",
                            onYes = { vm.setPersonRole(company.id, u.id, to) }
                        )
                    )
                },
                small = true
            )
            ConsoleButton("Email", {
                onAsk(
                    Ask.Input(
                        title = "Email for ${u.name}",
                        body = "Notices about new versions are sent here. Leave it blank to take " +
                            "the address off.",
                        label = "Email",
                        initial = u.email.orEmpty(),
                        validate = {
                            val v = it.trim()
                            if (v.isEmpty() || Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(v)) null
                            else "That does not look like an email address."
                        },
                        onOk = { mail -> vm.setPersonEmail(company.id, u.id, mail) }
                    )
                )
            }, small = true)
            ConsoleButton("Set PIN", {
                onAsk(
                    Ask.Input(
                        title = "New PIN for ${u.name}",
                        body = "The old one cannot be read back. Tell them this one directly.",
                        label = "PIN (at least 4 characters)",
                        password = true,
                        validate = {
                            if (it.length < 4) "A PIN of at least 4 characters is required." else null
                        },
                        onOk = { pin -> vm.setPersonPin(company.id, u.id, pin) }
                    )
                )
            }, small = true)
            ConsoleButton("Remove", {
                onAsk(
                    Ask.Confirm(
                        title = "Remove ${u.name} from ${company.name}?",
                        body = "Their seat is freed. Everything they saved stays with the company.",
                        confirmText = "Remove",
                        danger = true,
                        onYes = { vm.removePerson(company.id, u.id) }
                    )
                )
            }, kind = ButtonKind.Danger, small = true)
        }
    }
}

/* ---- everything the owner may do ---- */

@Composable
private fun ActionsCard(
    company: Company,
    vm: ConsoleViewModel,
    nav: Navigator,
    onAsk: (Ask) -> Unit
) {
    val id = company.id

    ConsoleCard {
        Text("What you can do", style = CardTitleStyle)

        Spacer(Modifier.height(14.dp))
        GroupHeading("Their details")
        WrapRow {
            ConsoleButton("Rename", {
                onAsk(
                    Ask.Input(
                        title = "Rename this company",
                        body = "What the customer is called everywhere — here, on their machines, " +
                            "and on anything printed.",
                        label = "Company name",
                        initial = company.name,
                        validate = { if (it.isBlank()) "A name is required." else null },
                        onOk = { name -> vm.renameCompany(id, name) }
                    )
                )
            }, small = true)
            ConsoleButton(if (company.gstin == null) "Add GSTIN" else "Change GSTIN", {
                onAsk(
                    Ask.Input(
                        title = "GSTIN for ${company.name}",
                        body = "Fifteen characters. Leave it blank to take it off.",
                        label = "GSTIN",
                        initial = company.gstin.orEmpty(),
                        onOk = { g -> vm.setGstin(id, g) }
                    )
                )
            }, small = true)
            ConsoleButton("Note", {
                onAsk(
                    Ask.Input(
                        title = "A note about ${company.name}",
                        body = "For you, not for them. Anything worth remembering next time they ring.",
                        label = "Note",
                        initial = "",
                        onOk = { n -> vm.setNote(id, n) }
                    )
                )
            }, small = true)
        }

        Spacer(Modifier.height(16.dp))
        GroupHeading("Licence")
        WrapRow {
            if (company.isDemo) {
                ConsoleButton(
                    "Make licensed for a year",
                    { vm.act(id, "licence", 365) },
                    kind = ButtonKind.Primary,
                    small = true
                )
            }
            ConsoleButton("Add days", {
                onAsk(
                    Ask.Input(
                        title = "Add how many days to this licence?",
                        body = "The company's clock moves; every seat follows.",
                        label = "Days",
                        initial = "30",
                        numeric = true,
                        confirmText = "Add",
                        validate = { if ((it.toIntOrNull() ?: 0) > 0) null else "Enter a number of days." },
                        onOk = { v -> vm.act(id, "extend", v.toInt()) }
                    )
                )
            }, small = true)
            ConsoleButton("+1 year", { vm.act(id, "extend", 365) }, small = true)
            /* 1.5.0 — the plan; seats are set separately */
            val onStd = company.plan == "STANDARD"
            ConsoleButton(
                if (onStd) "Plan: Standard" else "Plan: Pro",
                {
                    onAsk(
                        Ask.Confirm(
                            title = if (onStd) "Change to Pro?" else "Change to Standard?",
                            body = if (onStd) "Pro carries everything ticked under Plans." else "Standard is calculation and costing; the rest goes at the next check. Seats are not affected.",
                            confirmText = "Change"
                        ) { vm.setPlan(id, if (onStd) "PRO" else "STANDARD") }
                    )
                },
                small = true
            )
        }
        if (company.isDemo) Why("turns this demo into a paying customer — a demo has every feature whatever its plan")

        Spacer(Modifier.height(16.dp))
        GroupHeading("Machines and seats")
        WrapRow {
            ConsoleButton("Seats", {
                onAsk(
                    Ask.Input(
                        title = "How many people may sign in on this licence?",
                        body = "A seat is a person. Computers are not rationed.",
                        label = "Seats",
                        initial = company.seats.toString(),
                        numeric = true,
                        validate = { if ((it.toIntOrNull() ?: 0) > 0) null else "Enter a number of seats." },
                        onOk = { v -> vm.setSeats(id, v.toInt()) }
                    )
                )
            }, small = true)
            ConsoleButton("Offline days", {
                onAsk(
                    Ask.Input(
                        title = "How many days may this customer work with no contact?",
                        body = "0 = none: it stops as soon as it cannot reach the service.",
                        label = "Offline days",
                        initial = company.graceDays.toString(),
                        numeric = true,
                        validate = { if (it.toIntOrNull() != null) null else "Enter a number of days." },
                        onOk = { v -> vm.setGrace(id, v.toInt()) }
                    )
                )
            }, small = true)
            ConsoleButton("Show its machines", {
                vm.companyFilter = id
                nav.switchTo(Screen.Machines)
            }, small = true)
        }

        Spacer(Modifier.height(16.dp))
        GroupHeading("Sign-in")
        WrapRow {
            ConsoleButton("Set administrator", {
                onAsk(
                    Ask.TwoInputs(
                        title = "Administrator for ${company.name}",
                        body = "Name the person who will manage users and see every calculation. " +
                            "If a user of that name exists, they become the administrator and get " +
                            "the new PIN.",
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
                        onOk = { n, p -> vm.setAdministrator(id, n, p) }
                    )
                )
            }, small = true)
            ConsoleButton("New company passcode", {
                onAsk(
                    Ask.TwoInputs(
                        title = "New company passcode for ${company.name}",
                        body = "The login id is the first half of their login. Nobody can read the " +
                            "old passcode — it is stored scrambled. Tell them the new one directly.",
                        labelA = "Company login id",
                        initialA = company.loginId ?: "",
                        labelB = "New passcode (at least 6 characters)",
                        validate = { _, p ->
                            if (p.length < 6) "A passcode of at least 6 characters is required." else null
                        },
                        onOk = { l, p -> vm.setPasscode(id, l.trim(), p) }
                    )
                )
            }, small = true)
        }

        if (company.gstin != null) {
            Spacer(Modifier.height(16.dp))
            GroupHeading("GST")
            WrapRow {
                ConsoleButton("Verify online", { vm.gstVerify(id) }, small = true)
                if (company.gstStatus != "VERIFIED") {
                    ConsoleButton("Mark checked by hand", {
                        onAsk(
                            Ask.Input(
                                title = "How was it checked?",
                                body = "A note for the record.",
                                label = "Note",
                                initial = "Checked on the GST portal by hand",
                                onOk = { note -> vm.gstMark(id, "VERIFIED", note) }
                            )
                        )
                    }, small = true)
                } else {
                    ConsoleButton(
                        "Take the mark off",
                        { vm.gstMark(id, "UNVERIFIED", "") },
                        small = true
                    )
                }
            }
            company.gstCheckedAt?.let { Why("last checked ${Fmt.dateTime(it)}") }
            company.gstNote?.let { Why(it) }
        }

        Spacer(Modifier.height(16.dp))
        GroupHeading("Usage")
        WrapRow {
            ConsoleButton("Transaction limit", {
                onAsk(
                    Ask.Input(
                        title = "How many transactions may this licence commit?",
                        body = "0 = no limit. Reaching the limit makes the machines READ-ONLY: " +
                            "everything saved still opens and prints.",
                        label = "Transaction limit",
                        initial = company.txnLimit.toString(),
                        numeric = true,
                        validate = { if (it.toIntOrNull() != null) null else "Enter a number." },
                        onOk = { v -> vm.setTxnLimit(id, v.toInt()) }
                    )
                )
            }, small = true)
            ConsoleButton("Reset usage", {
                onAsk(
                    Ask.Confirm(
                        title = "Start ${company.name}'s count and hours from zero?",
                        body = "On every machine. Nothing saved is touched. A limit that was " +
                            "reached is no longer reached.",
                        confirmText = "Reset",
                        onYes = { vm.resetUsage(id, company.name) }
                    )
                )
            }, small = true)
        }

        Spacer(Modifier.height(16.dp))
        GroupHeading("Stop them")
        WrapRow {
            if (company.state == "SUSPENDED") {
                ConsoleButton(
                    "Restore",
                    { vm.act(id, "restore") },
                    kind = ButtonKind.Primary,
                    small = true
                )
            } else {
                ConsoleButton("Suspend", {
                    onAsk(
                        Ask.Confirm(
                            title = "Suspend this company?",
                            body = "EVERY machine on this licence stops calculating at its next " +
                                "check. Nothing is deleted; Restore puts it back.",
                            confirmText = "Suspend",
                            danger = true,
                            onYes = { vm.act(id, "suspend") }
                        )
                    )
                }, kind = ButtonKind.Danger, small = true)
            }
            ConsoleButton("Delete", {
                onAsk(
                    Ask.TypeToConfirm(
                        title = "Delete ${company.name}?",
                        body = "This removes the company, its machines, its people and everything " +
                            "they synced. It cannot be undone from here.",
                        label = "Type the company name exactly",
                        expected = company.name,
                        onOk = { typed ->
                            vm.deleteCompany(id, typed)
                            nav.backToRoot()
                        }
                    )
                )
            }, kind = ButtonKind.Danger, small = true)
        }
        Why(
            if (company.state == "SUSPENDED") "every machine runs again"
            else "every machine stops at its next check; nothing is deleted"
        )
    }
}
