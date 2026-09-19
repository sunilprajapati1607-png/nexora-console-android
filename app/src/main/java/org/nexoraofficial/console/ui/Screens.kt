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
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.InquiryForm
import org.nexoraofficial.console.data.Company
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

        item { Box(page) { DashboardCard(vm) } }

        /* The dashboard is a summary, so everything on it leads somewhere. */
        item {
            Box(page) {
                ConsoleCard {
                    Text("Go to", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    MenuRow(Icons.Outlined.ChevronRight, "The enquiries", "${vm.openInquiries} still open") {
                        nav.switchTo(Screen.Enquiries)
                    }
                    MenuRow(Icons.Outlined.ChevronRight, "The customers", "${vm.customerCount} paying · ${vm.demoCount} on demo") {
                        nav.switchTo(Screen.Companies)
                    }
                    MenuRow(Icons.Outlined.ChevronRight, "The machines", "${vm.runningCount} running") {
                        nav.switchTo(Screen.Machines)
                    }
                }
            }
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

/* -------------------------------------------------------------- Companies */

@Composable
fun CompaniesScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier
) {


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

/** A customer in the list: enough to recognise and choose, not everything. */
@Composable
private fun CompanyRow(company: Company, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val state = company.shownState

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(
                1.dp,
                if (company.state == "SUSPENDED") c.bad else c.border,
                RoundedCornerShape(12.dp)
            )
            .drawAccentEdge(c.accent)
            .clickable(onClick = onOpen)
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
                else if (company.daysLeft == 0) "today" else company.daysLeft.toString()
            )
            Mini("Txns", company.txnUsed.toString())
        }
    }
}

@Composable
private fun Mini(label: String, value: String) {
    val c = LocalNexora.current
    Column {
        Text(
            label.uppercase(),
            color = c.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp
        )
        Text(value, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                    Text("Set up", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    MenuRow(
                        Icons.Outlined.Tune,
                        "Service settings",
                        "demo length, offline days, registrations"
                    ) { nav.open(Screen.Settings) }
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
            .clickable(onClick = onClick)
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
                title,
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
                            "This phone asks the service every fifteen minutes whether a new " +
                                "enquiry has arrived or a plant has registered, and says so in the " +
                                "status bar. Nothing is pushed; nothing is sent anywhere else.",
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
                        "The admin key is kept in this application's own storage, is never backed " +
                            "up off the device, and Sign out erases it."
                    )
                }
            }
        }
    }
}
