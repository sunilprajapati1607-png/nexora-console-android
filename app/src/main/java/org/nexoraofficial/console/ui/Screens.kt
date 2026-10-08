package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.InquiryForm
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.PayQuick
import org.nexoraofficial.console.data.Payment
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.ValQuick
import org.nexoraofficial.console.data.ValidityRow
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   ONE SCREEN PER SECTION.

   Each is a list with its own search, its own filters and nothing from any
   other section on it. What used to be an inline panel four screens down a
   single page is now a screen you open, deal with, and come back from.

   2.0.0 — the customers, the validity, the payments, the plans and each
   software on its own are Records screens of their own files
   (CustomersScreen, ValidityScreen, PaymentsScreens, PlansScreens,
   BySoftwareScreen); this file keeps the dashboard and the smaller ones.
   ====================================================================== */

/* ---------------------------------------------------------------- Dashboard */

@Composable
fun DashboardScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier
) {
    val all = vm.customers
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

        /* 1.6.0 — the greeting on the logo's sweep, each kind of thing in its own colour, every tile leads somewhere */
        item {
            Box(page.appear(0)) {
                /* 1.7.0 — the morning briefing's gradient, by the hour, as Nexora Mobile's dashboard */
                val hr = java.time.LocalTime.now().hour
                GradientBanner(colors = if (hr >= 17) listOf(Color(0xFF312E81), Color(0xFF6D28D9), Color(0xFFDB2777)) else if (hr >= 12) listOf(Color(0xFF0EA5E9), Color(0xFF6366F1), Color(0xFFDB2777)) else listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF7C3AED))) {
                    Text("Namaste", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Nexora Console · every software at a glance", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(10.dp))
                    Text("${all.size} customers · ${vm.customerCount} paying on Sales & Costing · ${vm.runningCount} computers running",
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f))
                }
            }
        }
        item {
            Column(page.appear(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TileRow {
                    BigTile(Icons.Outlined.Business, vm.customerCount.toString(), "Customers", "customer", { vm.companyEnding = false; nav.switchTo(Screen.Customers) }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.HourglassTop, vm.demoCount.toString(), "Demos", "demo", {
                        vm.companyEnding = false; vm.customerQuick = org.nexoraofficial.console.data.CustQuick.DEMO; nav.switchTo(Screen.Customers)
                    }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.Computer, vm.runningCount.toString(), "Running", "machine", { nav.open(Screen.Machines) }, Modifier.weight(1f))
                }
                TileRow {
                    BigTile(Icons.Outlined.QuestionAnswer, vm.openInquiries.toString(), "Open enquiries", "enquiry", { nav.switchTo(Screen.Enquiries) }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.EmojiEvents, vm.wonInquiries.toString(), "Won", "money", { nav.switchTo(Screen.Enquiries) }, Modifier.weight(1f))
                    BigTile(Icons.Outlined.Feedback, vm.openFeedback.toString(), "Reports", if (vm.openBugs > 0) "bad" else "grey", { nav.switchTo(Screen.Feedback) }, Modifier.weight(1f))
                }
            }
        }

        /* 2.0.0 — by software, the money this month and the plans: the web console's dashboard figures */
        item {
            Column(page.appear(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("By Software", style = MaterialTheme.typography.titleSmall, color = LocalNexora.current.muted, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp))
                val ws = all.mapNotNull { it.w?.swState }
                val fs = all.mapNotNull { it.f?.shownState }
                val n = { l: List<String>, s: String -> l.count { it == s } }
                val month = vm.today().withDayOfMonth(1).toString()
                val pays = vm.payments?.payments.orEmpty().filter { (it.paidOn ?: "") >= month }
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
                        Fig("Received this month", Money.rupees(pays.sumOf { it.amount ?: 0.0 }),
                            "${pays.size} payment" + (if (pays.size == 1) "" else "s"), 2) {
                            vm.payQuick = PayQuick.MONTH; nav.switchTo(Screen.Payments)
                        },
                        Fig("Plans", (vm.plans?.all?.size ?: 0).toString(),
                            "${vm.plans?.plansOf(Software.WEIGHT)?.size ?: 0} Sales & Costing · ${vm.plans?.plansOf(Software.FABRIC)?.size ?: 0} Fabric Stock", 3) {
                            nav.switchTo(Screen.Plans)
                        }
                    )
                )
            }
        }

        /* 4.72.0 — audit #90: a paying plant's licence ending within 15 days, first of all,
           so the call to renew is made before it opens read-only one morning */
        val ending = vm.endingSoon
        if (ending.isNotEmpty()) {
            item {
                Box(page) {
                    ActionCard(
                        Icons.Outlined.EventBusy,
                        if (ending.size == 1) "Licence ending soon" else "${ending.size} licences ending soon",
                        ending.take(3).joinToString(" · ") { it.name + " " + it.endsText } +
                            (if (ending.size > 3) " · and ${ending.size - 3} more" else "") + " — call to renew",
                        "amber", 2
                    ) {
                        vm.companyQuery = ""
                        vm.companyEnding = true
                        nav.switchTo(Screen.Customers)
                    }
                }
            }
        }

        /* 2.0.0 — every licence of every software that ends within 30 days, soonest first */
        item {
            Box(page) {
                val end = vm.validityAll.filter { it.live && it.left <= 30 }.take(8)
                ColourCard("Ending Within 30 Days", Icons.Outlined.Event, "amber", trailing = {
                    ConsoleButton("All validity", { vm.validityQuick = ValQuick.D30; nav.switchTo(Screen.Validity) }, small = true)
                }) {
                    if (end.isEmpty()) Help("Nothing ends within 30 days.")
                    end.forEachIndexed { i, r ->
                        if (i > 0) DashedRule(Modifier.padding(vertical = 6.dp))
                        DashEndingRow(r) { nav.open(Screen.CustomerWin(r.c.key, r.sw, "licence")) }
                    }
                }
            }
        }

        /* 2.0.0 — the latest payments */
        item {
            Box(page) {
                val lp = vm.payments?.payments.orEmpty().take(6)
                ColourCard("Latest Payments", Icons.Outlined.Payments, "money", trailing = {
                    ConsoleButton("All payments", { vm.payQuick = PayQuick.ALL; nav.switchTo(Screen.Payments) }, small = true)
                }) {
                    if (lp.isEmpty()) Help(vm.paymentsError ?: "No payment recorded yet. Record payment keeps one.")
                    lp.forEachIndexed { i, p ->
                        if (i > 0) DashedRule(Modifier.padding(vertical = 6.dp))
                        DashPaymentRow(p) { nav.open(Screen.PaymentWin(p.id)) }
                    }
                }
            }
        }

        item {
            Column(page, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard(Icons.Outlined.QuestionAnswer, "The enquiries", "${vm.openInquiries} still open · ${vm.newInquiryCount} new", "enquiry", 2) {
                    nav.switchTo(Screen.Enquiries)
                }
                ActionCard(Icons.Outlined.Feedback, "Feedback and problem reports",
                    if (vm.openFeedback == 0) "nothing waiting"
                    else "${vm.openFeedback} open" + (if (vm.openBugs > 0) " · ${vm.openBugs} problem" + (if (vm.openBugs == 1) "" else "s") else ""),
                    if (vm.openBugs > 0) "bad" else "amber", 3) { nav.switchTo(Screen.Feedback) }
                ActionCard(Icons.Outlined.Computer, "The machines", "${vm.runningCount} running · ${vm.data.licences.size} installed", "machine", 4) {
                    nav.open(Screen.Machines)
                }
            }
        }

        item { Box(page) { DashboardCard(vm) } }
    }
}

/** A licence ending soon, on the dashboard: who, which software and plan, its state, and the days left. */
@Composable
private fun DashEndingRow(r: ValidityRow, onOpen: () -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).pressable(onOpen).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.c.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SwDot(r.sw, 8)
                Text(" " + Software.name(r.sw) + " · " + r.plan + " · ", color = c.muted, fontSize = 12.sp)
                Text(Customers.stateWord(r.state), color = stateColour(r.state), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.left.toString(), color = c.warn, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(Fmt.day(r.ends), color = c.muted, fontSize = 11.sp)
        }
    }
}

/** A payment on the dashboard: who, which software and plan, when, and how much. */
@Composable
private fun DashPaymentRow(p: Payment, onOpen: () -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).pressable(onOpen).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.customer, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SwDot(p.software, 8)
                Text(" " + listOfNotNull(p.softwareName, p.planName, Fmt.day(p.paidOn)).joinToString(" · "), color = c.muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.rupees(p.amount), color = c.text, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold)
            Text(if (p.validTo != null) "to " + Fmt.day(p.validTo) else "—", color = c.muted, fontSize = 11.sp)
        }
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

@Composable
internal fun Mini(label: String, value: String, color: Color? = null, note: String? = null) {
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
                    MenuRow(Icons.Outlined.Campaign, "Tell the customers", "one message to everybody who should hear it") { nav.open(Screen.Announce) }
                    MenuRow(Icons.Outlined.Forum, "Message every plant", "as Nexora, in each company's conversation") { nav.open(Screen.Broadcast) }
                    MenuRow(Icons.Outlined.TableChart, "Export to Excel", "every company and machine, saved on this phone") { onExport() }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("See", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    MenuRow(Icons.Outlined.Event, "Validity and renewals", "every licence of every software, ending first") { nav.switchTo(Screen.Validity) }
                    MenuRow(Icons.Outlined.Computer, "The machines", "every installation · ${vm.runningCount} running") { nav.open(Screen.Machines) }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    Text("Set up", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    MenuRow(Icons.Outlined.Tune, "Service settings", "demo length, offline days, registrations") { nav.open(Screen.Settings) }
                    MenuRow(Icons.Outlined.WorkspacePremium, "Software and plans", "each software its own plans, prices and features") { nav.switchTo(Screen.Plans) }
                    MenuRow(
                        Icons.Outlined.SystemUpdate,
                        if (vm.updateAvailable) "Update to ${vm.release?.versionName}" else "Check for updates",
                        if (vm.updateAvailable) "a newer build is waiting" else "you are on ${org.nexoraofficial.console.BuildConfig.VERSION_NAME}"
                    ) {
                        if (vm.updateAvailable) nav.open(Screen.About) else vm.checkForUpdate(loud = true)
                    }
                    MenuRow(Icons.Outlined.Info, "About this console", "version, service, notifications") { nav.open(Screen.About) }
                }
            }
        }

        item {
            Box(page) {
                ConsoleCard {
                    MenuRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", "forget the admin key on this phone", danger = true) { vm.signOut() }
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
