package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.DeletedCompany
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Person
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   SALES & COSTING, INSIDE A CUSTOMER'S WINDOW.

   2.0.0 — what the company screen held, laid out as the window's tabs
   (CustomerWindow.kt): the people (who may sign in, their PINs, their
   addresses), the sign-in set up by Nexora (administrator, company
   passcode), and More — GST, usage, and stopping or deleting the company.
   The licence itself (plan, days, seats, limits) is the Licence tab, and
   is changed with Edit.
   ====================================================================== */

/** People tab, top: what Nexora sets up for the company's sign-in — the rest is its administrator's. */
@Composable
internal fun WeightSignInCard(company: Company, vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {
    val id = company.id
    ConsoleCard {
        CardHeading("Sign-In", "the administrator adds everyone else and gives their rights inside Sales & Costing")
        WrapRow {
            ConsoleButton("Set administrator…", {
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
            ConsoleButton("New company passcode…", {
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
    }
}

/* ---- who may sign in ---- */

@Composable
internal fun PeopleCard(company: Company, vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {

    val people = vm.people

    /* 4.58.1 — who is signed in, and when they were last active, keep
       themselves current while this company is open: once a minute, quietly */
    LaunchedEffect(company.id) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            vm.loadPeople(company.id, quiet = true)
        }
    }

    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("People", style = CardTitleStyle)
                vm.refreshedAt?.let { Small("updated $it") }
            }
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
                (if (u.lastLoginAt != null) "last signed in ${Fmt.dateTime(u.lastLoginAt)}"
                else "never signed in")
        )
        /* 4.58.1 — when their software last spoke to the service */
        if (u.lastSeenAt != null) {
            Small("active ${Fmt.ago(u.lastSeenAt)} (${Fmt.dateTime(u.lastSeenAt)})", color = c.ok)
        }
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

/* ---- More: GST, usage, and stopping them ---- */

/** The Suspend question, the same from the tool strip and from More. */
internal fun suspendQuestion(vm: ConsoleViewModel, id: Int) = Ask.Confirm(
    title = "Suspend this company?",
    body = "EVERY machine on this licence stops calculating at its next " +
        "check. Nothing is deleted; Restore puts it back.",
    confirmText = "Suspend",
    danger = true,
    onYes = { vm.act(id, "suspend") }
)

@Composable
internal fun WeightMoreCard(
    company: Company,
    vm: ConsoleViewModel,
    nav: Navigator,
    onAsk: (Ask) -> Unit
) {
    val id = company.id

    ConsoleCard {
        CardHeading("More", "on Sales & Costing only — Fabric Stock's licence is not touched")

        if (company.gstin != null) {
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
            Spacer(Modifier.height(16.dp))
        }

        GroupHeading("Usage")
        WrapRow {
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
        Why("count and hours from zero; nothing saved is touched")

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
                ConsoleButton("Suspend", { onAsk(suspendQuestion(vm, id)) }, kind = ButtonKind.Danger, small = true)
            }
            ConsoleButton("Delete", {
                onAsk(
                    Ask.TypeToConfirm(
                        title = "Delete ${company.name}?",
                        /* 4.72.0 — audit #40: a service that keeps a deleted company 30 days
                           says so in its listing; an older one still erases it at once */
                        body = deleteQuestionBody(vm.data.keepsDeleted),
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
        if (vm.data.keepsDeleted) {
            Spacer(Modifier.height(4.dp))
            Why(
                "delete stops it now and keeps it ${DeletedCompany.KEEP_DAYS} days (Restore under " +
                    "Customers → Deleted); then it and everything that belongs to it are erased"
            )
        }
    }
}

/**
 * 4.72.0 — audit #40: what the Delete question says. On a service that keeps a
 * deleted company (the web console's words): kept 30 days, restorable until
 * then, erased after. On one not updated yet, which still erases at once: so.
 */
internal fun deleteQuestionBody(keepsDeleted: Boolean): String =
    if (keepsDeleted)
        "Its computers and phones stop at their next check and nobody can sign in. It is kept for " +
            "${DeletedCompany.KEEP_DAYS} days: Restore (Customers → Deleted) puts it back exactly as it was. " +
            "After ${DeletedCompany.KEEP_DAYS} days the company, its machines, its people, everything they " +
            "synced, its chat and its problem reports are erased for good."
    else "This removes the company, its machines, its people and everything they synced. " +
        "It cannot be undone from here."
