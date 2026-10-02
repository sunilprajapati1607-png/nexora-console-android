package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.CompanyView
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.InquiryForm
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.DeletedCompany
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   ONE SCREEN PER SECTION.

   Each is a list with its own search, its own filters and nothing from any
   other section on it. What used to be an inline panel four screens down a
   single page is now a screen you open, deal with, and come back from.
   ====================================================================== */

/* ---------------------------------------------------------------- Dashboard */

@Composable
fun DashboardScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        /* A new build is the one thing that should be seen before anything
           else, because everything else can wait and this cannot. */
        if (vm.updateAvailable) {
            item { Box(page) { UpdateCard(vm) } }
        }

        /* 1.6.0 — "make them more flexi like weight calcuation android app": the greeting on the logo's sweep,
           each kind of thing in its own colour, and every tile and card leads somewhere */
        item {
            Box(page.appear(0)) {
                /* 1.7.0 — the morning briefing's gradient, by the hour, as Nexora Mobile's dashboard */
                val hr = java.time.LocalTime.now().hour
                GradientBanner(colors = if (hr >= 17) listOf(Color(0xFF312E81), Color(0xFF6D28D9), Color(0xFFDB2777)) else if (hr >= 12) listOf(Color(0xFF0EA5E9), Color(0xFF6366F1), Color(0xFFDB2777)) else listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF7C3AED))) {
                    Text("Namaste", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Nexora Licence Console", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(10.dp))
                    Text("${vm.customerCount} paying · ${vm.demoCount} on demo · ${vm.runningCount} computers running",
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f))
                }
            }
        }
        item {
            Column(page.appear(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TileRow {
                    BigTile(Icons.Outlined.Business, vm.customerCount.toString(), "Customers", "customer", { vm.companyEnding = false; nav.switchTo(Screen.Companies) }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.HourglassTop, vm.demoCount.toString(), "Demos", "demo", { vm.companyEnding = false; nav.switchTo(Screen.Companies) }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.Computer, vm.runningCount.toString(), "Running", "machine", { nav.open(Screen.Machines) }, Modifier.weight(1f))
                }
                TileRow {
                    BigTile(Icons.Outlined.QuestionAnswer, vm.openInquiries.toString(), "Open enquiries", "enquiry", { nav.switchTo(Screen.Enquiries) }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.EmojiEvents, vm.wonInquiries.toString(), "Won", "money", { nav.switchTo(Screen.Enquiries) }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.Feedback, vm.openFeedback.toString(), "Reports", if (vm.openBugs > 0) "bad" else "grey", { nav.switchTo(Screen.Feedback) }, Modifier.weight(1f))
                }
            }
        }
        item {
            Column(page, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                /* 4.72.0 — audit #90: a paying plant's licence ending within 15 days, first of all,
                   so the call to renew is made before it opens read-only one morning */
                val ending = vm.endingSoon
                if (ending.isNotEmpty()) {
                    ActionCard(
                        Icons.Outlined.EventBusy,
                        if (ending.size == 1) "Licence ending soon" else "${ending.size} licences ending soon",
                        ending.take(3).joinToString(" · ") { it.name + " " + it.endsText } +
                            (if (ending.size > 3) " · and ${ending.size - 3} more" else "") + " — call to renew",
                        "amber", 2
                    ) {
                        vm.companyQuery = ""
                        vm.companyEnding = true
                        nav.switchTo(Screen.Companies)
                    }
                }
                ActionCard(Icons.Outlined.QuestionAnswer, "The enquiries", "${vm.openInquiries} still open · ${vm.newInquiryCount} new", "enquiry", 2) {
                    nav.switchTo(Screen.Enquiries)
                }
                ActionCard(Icons.Outlined.Feedback, "Feedback and problem reports",
                    if (vm.openFeedback == 0) "nothing waiting"
                    else "${vm.openFeedback} open" + (if (vm.openBugs > 0) " · ${vm.openBugs} problem" + (if (vm.openBugs == 1) "" else "s") else ""),
                    if (vm.openBugs > 0) "bad" else "amber", 3) { nav.switchTo(Screen.Feedback) }
                ActionCard(Icons.Outlined.Business, "The customers", "${vm.customerCount} paying · ${vm.demoCount} on demo", "customer", 4) {
                    vm.companyEnding = false; nav.switchTo(Screen.Companies)
                }
                ActionCard(Icons.Outlined.Computer, "The machines", "${vm.runningCount} running · ${vm.data.licences.size} installed", "machine", 5) {
                    nav.open(Screen.Machines)
                }
            }
        }

        item { Box(page) { DashboardCard(vm) } }
    }
}

/* -------------------------------------------------------------- Enquiries */

@Composable
fun EnquiriesScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(page) {
                InquiriesCard(vm, onAsk) { inquiry ->
                    nav.open(Screen.EnquiryForm(inquiry?.id))
                    if (inquiry != null) vm.newInquiry = InquiryForm.of(inquiry)
                    else vm.newInquiry = InquiryForm()
                }
            }
        }
    }
}

/* -------------------------------------------------------------- Companies */

@Composable
fun CompaniesScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val showDeleted = vm.companyView == CompanyView.DELETED && vm.data.keepsDeleted

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(page) {
                ConsoleField(
                    label = null,
                    value = vm.companyQuery,
                    onValueChange = { vm.companyQuery = it },
                    placeholder = "Find a company, key, email, GSTIN…"
                )
            }
        }

        /* 4.72.0 — audit #40: the live companies, or the ones Delete has archived,
           as filters that say how many — only from a service that keeps them */
        if (vm.data.keepsDeleted) {
            item {
                Box(page) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConsoleButton(
                            "All ${vm.data.companies.size}",
                            { vm.companyView = CompanyView.ALL },
                            kind = if (vm.companyView == CompanyView.ALL) ButtonKind.Primary else ButtonKind.Default,
                            small = true
                        )
                        ConsoleButton(
                            "Deleted ${vm.data.archived.size}",
                            { vm.companyView = CompanyView.DELETED },
                            kind = if (showDeleted) ButtonKind.Primary else ButtonKind.Default,
                            small = true
                        )
                    }
                }
            }
        }

        if (showDeleted) {
            item {
                Box(page) {
                    ConsoleCard {
                        Help(
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

        /* 4.72.0 — audit #90: opened from the dashboard's "Licences ending soon" */
        if (vm.companyEnding) {
            item {
                Box(page) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Small(
                            "Licences ending within ${Company.ENDING_DAYS} days only",
                            color = LocalNexora.current.warn,
                            modifier = Modifier.weight(1f)
                        )
                        ConsoleButton("Show all companies", { vm.companyEnding = false }, small = true)
                    }
                }
            }
        }

        if (vm.companies.isEmpty()) {
            item {
                Box(page) {
                    ConsoleCard {
                        Help(
                            if (vm.data.companies.isEmpty())
                                "No companies yet. A plant that registers itself from the " +
                                    "application appears here as a demo; a customer you set up " +
                                    "yourself is created with the + below."
                            else "Nothing matches that."
                        )
                    }
                }
            }
        }

        items(vm.companies, key = { it.id }) { co ->
            Box(page) { CompanyRow(co) { nav.open(Screen.Company(co.id)) } }
        }
    }
}

/** A customer in the list: enough to recognise and choose, not everything. 1.6.0 — a colour edge for its state
 *  (licensed blue, demo teal, expired amber, suspended red), as Nexora Mobile marks each kind of record. */
@Composable
private fun CompanyRow(company: Company, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val state = company.shownState
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
                Text(company.name, color = c.text, fontSize = 15.5f.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Mono(company.licenceKey)
            }
            Pill(if (state == "DEMO") "demo" else state.lowercase(), state)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.ChevronRight, "Open", tint = c.faint, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Mini("Seats", "${company.seatsUsed}/${company.seats}")
            Mini("Computers", company.machinesUsed.toString())
            Mini(
                if (state == "EXPIRED" || state == "SUSPENDED") "Ended" else "Days left",
                if (state == "EXPIRED" || state == "SUSPENDED") Fmt.day(company.expiresAt)
                else if (company.daysLeft == 0) "today" else company.daysLeft.toString(),
                /* 4.72.0 — audit #90: a paying licence ending within 15 days reads amber;
                   one the service counts as ending within 30 (ending_soon) too, marked "renew soon" */
                color = if (company.endingSoon || company.renewSoon) c.warn else null,
                note = if (company.renewSoon) "renew soon" else null
            )
            Mini("Txns", company.txnUsed.toString())
        }
    }
}

/**
 * 4.72.0 — audit #40: a company Delete has archived — what it was, when it
 * goes for good, and Restore: the one thing that can be done to it (the
 * service refuses every other action on a deleted company), so the row is
 * not a way into the company's screen.
 */
@Composable
private fun DeletedRow(co: DeletedCompany, onRestore: () -> Unit) {
    val c = LocalNexora.current
    val a = Kinds.state("SUSPENDED", c.isDark)

    Column(
        Modifier
            .fillMaxWidth()
            .appear()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, a.fg.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .drawAccentEdge(a.fg, alpha = 1f)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
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
        ConsoleButton("Restore", onRestore, kind = ButtonKind.Primary, small = true)
    }
}

@Composable
private fun Mini(label: String, value: String, color: Color? = null, note: String? = null) {
    val c = LocalNexora.current
    Column {
        Text(
            label.proper(),
            color = c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(value, color = color ?: c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        /* 4.72.0 — a word under the figure, in its colour ("renew soon") */
        if (note != null) Text(note, color = color ?: c.muted, fontSize = 10.5f.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* --------------------------------------------------------------- Machines */

@Composable
fun MachinesScreen(
    vm: ConsoleViewModel,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(page) { InstallationsCard(vm) { onAsk(it) } } }
    }
}

/* ------------------------------------------------------------------- More */

@Composable
fun MoreScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier,
    onExport: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(page) {
                ConsoleCard {
                    Text("Do", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    MenuRow(
                        Icons.Outlined.Campaign,
                        "Tell the customers",
                        "one message to everybody who should hear it"
                    ) { nav.open(Screen.Announce) }
                    MenuRow(
                        Icons.Outlined.Forum,
                        "Message every plant",
                        "as Nexora, in each company's conversation"
                    ) { nav.open(Screen.Broadcast) }
                    MenuRow(
                        Icons.Outlined.TableChart,
                        "Export to Excel",
                        "every company and machine, saved on this phone"
                    ) { onExport() }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("See", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    MenuRow(
                        Icons.Outlined.Computer,
                        "The machines",
                        "every installation · ${vm.runningCount} running"
                    ) { nav.open(Screen.Machines) }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("Set up", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    MenuRow(
                        Icons.Outlined.Tune,
                        "Service settings",
                        "demo length, offline days, registrations"
                    ) { nav.open(Screen.Settings) }
                    MenuRow(
                        Icons.Outlined.WorkspacePremium,
                        "Plans",
                        "what Standard and Pro carry"
                    ) { nav.open(Screen.Plans) }
                    MenuRow(
                        Icons.Outlined.SystemUpdate,
                        if (vm.updateAvailable) "Update to ${vm.release?.versionName}"
                        else "Check for updates",
                        if (vm.updateAvailable) "a newer build is waiting"
                        else "you are on ${org.nexoraofficial.console.BuildConfig.VERSION_NAME}"
                    ) {
                        if (vm.updateAvailable) nav.open(Screen.About) else vm.checkForUpdate(loud = true)
                    }
                    MenuRow(
                        Icons.Outlined.Info,
                        "About this console",
                        "version, service, notifications"
                    ) { nav.open(Screen.About) }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    MenuRow(
                        Icons.AutoMirrored.Outlined.Logout,
                        "Sign out",
                        "forget the admin key on this phone",
                        danger = true
                    ) { vm.signOut() }
                }
            }
        }
    }
}

/** A row of a menu: icon, what it is, what it does, and a chevron. */
@Composable
fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalNexora.current
    val tint = if (danger) c.bad else c.accent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .pressable(onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (danger) c.badBg else c.accentBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.proper(),
                color = if (danger) c.bad else c.text,
                fontSize = 14.5f.sp,
                fontWeight = FontWeight.Bold
            )
            Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = c.faint, modifier = Modifier.size(20.dp))
    }
}

/* --------------------------------------------------- the screens on top */

@Composable
fun AnnounceScreen(vm: ConsoleViewModel, gutter: PaddingValues, page: Modifier) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(page) { AnnounceCard(vm) } }
    }
}

@Composable
fun SettingsScreen(vm: ConsoleViewModel, gutter: PaddingValues, page: Modifier) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(page) { SettingsCard(vm) } }
    }
}

@Composable
fun PlansScreen(vm: ConsoleViewModel, gutter: PaddingValues, page: Modifier) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(page) { PlansCard(vm) } }
    }
}

@Composable
fun BroadcastScreen(vm: ConsoleViewModel, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Box(page) { BroadcastCard(vm, onAsk) } }
        item { Box(page) { BroadcastsSentCard(vm, onAsk) } }
    }
}

@Composable
fun AboutScreen(vm: ConsoleViewModel, gutter: PaddingValues, page: Modifier) {
    val c = LocalNexora.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (vm.updateAvailable) {
            item { Box(page) { UpdateCard(vm) } }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(44)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Nexora Console", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Small("version ${org.nexoraofficial.console.BuildConfig.VERSION_NAME}")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Fact("Service", Modifier.fillMaxWidth()) { Mono(vm.baseUrl, color = c.text) }
                    Spacer(Modifier.height(10.dp))
                    Fact("Notifications", Modifier.fillMaxWidth()) {
                        Small(
                            "Between 08:30 and 20:30 (India time) this phone asks the service " +
                                "every fifteen minutes whether a new enquiry, a new feedback or " +
                                "problem report, or a new registration has arrived, and says so in " +
                                "the status bar. What arrives at night is told at the first look of " +
                                "the morning. Nothing is pushed; nothing is sent anywhere else.",
                            color = c.text
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Fact("Updates", Modifier.fillMaxWidth()) {
                        Small(
                            if (vm.updateAvailable)
                                "Version ${vm.release?.versionName} is waiting above."
                            else if (vm.release != null)
                                "This is the newest build published (${vm.release?.versionName})."
                            else if (vm.checkedForUpdate)
                                "Nothing has been published to update to yet."
                            else "Not checked yet.",
                            color = c.text
                        )
                        Spacer(Modifier.height(8.dp))
                        ConsoleButton("Check now", { vm.checkForUpdate(loud = true) }, small = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    Help(
                        "The admin key is kept sealed by the phone's own keystore, is never backed " +
                            "up off the device, is sent only to Nexora's own service, and Sign out " +
                            "erases it. The console asks for your " +
                            "fingerprint or screen lock each time it is opened."
                    )
                }
            }
        }
    }
}
