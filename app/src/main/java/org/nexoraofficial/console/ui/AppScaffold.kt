package org.nexoraofficial.console.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.CustQuick
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.NewCustomerForm
import org.nexoraofficial.console.data.PayFilter
import org.nexoraofficial.console.data.Reports
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.ValQuick
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * THE APPLICATION, AS AN APPLICATION.
 *
 *   "perfect menu and ui like other best android app,
 *    menu section wise form entry view all function"
 *
 * The furniture is the system's: a top bar that says where you are, a
 * navigation bar that says what else there is, a + where a + belongs, and
 * Back that goes back. The colours and the type stay the console's own.
 *
 * 2.0.0 — Console 2.0 (owner, 2026-10-08: "console ne software jevu banavanu
 * che row type details click and open window"): Dashboard, Customers,
 * Payments and Plans along the bottom; everything else — Validity, each
 * software on its own, Enquiries, Feedback, the machines, the settings — in
 * the side menu, whose ☰ wears a red dot while an enquiry or a report is new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(vm: ConsoleViewModel) {
    val c = LocalNexora.current
    val context = LocalContext.current
    val nav = remember { Navigator() }
    var ask by remember { mutableStateOf<Ask?>(null) }

    val wide = LocalConfiguration.current.screenWidthDp >= 600

    /* Back goes back through what is open (asking first while a window is in Edit); on a root it leaves the app. */
    BackHandler(enabled = nav.canGoBack) { nav.requestBack() }

    /* Export: build the file, then let the phone's own save-as place it. No
       storage permission is needed at any Android version this way. */
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val saveAs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        val bytes = pending
        pending = null
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                vm.say("Saved to the phone.", Msg.Kind.OK)
            } catch (e: Exception) {
                vm.say("Could not save it: ${e.message ?: "unknown"}", Msg.Kind.ERR)
            }
        }
    }
    val exportAll: () -> Unit = {
        try {
            pending = Reports.workbook(vm.data)
            saveAs.launch(Reports.fileName())
        } catch (e: Exception) {
            pending = null
            vm.say("Could not build the file: ${e.message ?: "unknown"}", Msg.Kind.ERR)
        }
    }

    val screen = nav.current
    /* 1.7.0 — the side menu */
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !nav.canGoBack || drawer.isOpen,
        drawerContent = { ConsoleDrawer(vm, nav, exportAll) { act -> scope.launch { drawer.close() }; act() } }
    ) {
        Scaffold(
            containerColor = c.bg,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (nav.canGoBack) {
                            IconTap(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { nav.requestBack() }
                        } else {
                            /* a red dot on ☰ while an enquiry or a report is new: they live in the side menu now */
                            val news = vm.newInquiryCount + vm.newFeedbackCount
                            BadgedBox(badge = { if (news > 0) Badge(containerColor = c.bad, modifier = Modifier.padding(top = 10.dp, end = 10.dp)) }) {
                                IconTap(Icons.Outlined.Menu, "Menu") { scope.launch { drawer.open() } }
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(titleOf(screen, vm), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            subtitleOf(screen, vm)?.let {
                                Text(it, color = c.muted, fontSize = 11.5f.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    actions = {
                        if (vm.busy) {
                            Text("…", color = c.muted, fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
                        } else {
                            IconTap(Icons.Outlined.Refresh, "Refresh") { vm.refreshNow() }
                        }
                        ModeSwitch(vm.dark, vm::flipMode, Modifier.padding(end = 12.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = c.bgElevated,
                        titleContentColor = c.text
                    )
                )
            },
            bottomBar = {
                if (!nav.canGoBack) GradientTabBar(vm, nav)
            },
            floatingActionButton = { ScreenFab(vm, nav, screen) }
        ) { padding ->

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(c.bg)
            ) {
                val page = Modifier.widthIn(max = 1180.dp)
                val gutter = PaddingValues(
                    start = if (wide) 24.dp else 14.dp,
                    end = if (wide) 24.dp else 14.dp,
                    top = 14.dp,
                    bottom = 96.dp
                )

                when (screen) {
                    Screen.Dashboard -> DashboardScreen(vm, nav, gutter, page)
                    Screen.Customers -> CustomersScreen(vm, nav, gutter, page) { ask = it }
                    Screen.Validity -> ValidityScreen(vm, nav, gutter, page)
                    Screen.Payments -> PaymentsScreen(vm, nav, gutter, page)
                    Screen.Plans -> PlansScreen(vm, nav, gutter, page)
                    is Screen.BySoftware -> BySoftwareScreen(vm, nav, screen.sw, gutter, page)
                    Screen.Enquiries -> EnquiriesScreen(vm, nav, gutter, page) { ask = it }
                    Screen.Machines -> MachinesScreen(vm, gutter, page) { ask = it }
                    Screen.Feedback -> FeedbackScreen(vm, nav, gutter, page)
                    Screen.More -> MoreScreen(vm, nav, gutter, page, exportAll)

                    is Screen.CustomerWin -> CustomerWindow(vm, nav, screen, gutter, page) { ask = it }
                    is Screen.PaymentWin -> PaymentWindow(vm, nav, screen.id, gutter, page) { ask = it }
                    Screen.RecordPayment -> RecordPaymentScreen(vm, nav, gutter, page) { ask = it }
                    is Screen.PlanWin -> PlanWindow(vm, nav, screen, gutter, page) { ask = it }
                    is Screen.FeedbackDetail -> FeedbackDetailScreen(vm, nav, screen.id, gutter, page) { ask = it }
                    is Screen.EnquiryForm -> EnquiryFormScreen(vm, nav, screen.id, gutter, page, wide)
                    Screen.NewCustomer -> NewCustomerScreen(vm, nav, gutter, page)
                    Screen.Announce -> AnnounceScreen(vm, gutter, page)
                    Screen.Settings -> SettingsScreen(vm, gutter, page)
                    Screen.Broadcast -> BroadcastScreen(vm, gutter, page) { ask = it }
                    Screen.About -> AboutScreen(vm, gutter, page)
                }

                /* One message strip for the whole application, above everything. */
                vm.msg?.let { m ->
                    MessageStrip(
                        m,
                        onDismiss = { vm.dismissMessage() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 720.dp)
                            .padding(14.dp)
                    )
                }
            }
        }

        /* 1.8.0 — a question is a window of its own (Material's AlertDialog),
           ABOVE the activity, so the lock page drawn over the console cannot
           cover it or take its touches: a Suspend or a Set PIN left open when the
           phone was put down would still be on the screen, its company in it and
           its Yes still working, the moment the lock closed. So no question is
           shown while locked. `ask` itself is kept, and the question comes back
           as it was asked once the owner has unlocked — only what had been typed
           into it is gone, which for a half-typed PIN is as it should be. The
           same goes for any Dialog, Popup or DropdownMenu added here later: none
           of them may be composed while vm.lock.locked. (2.0.0 — the date picker
           is one of these questions, Ask.Date, for the same reason.) */
        if (!vm.lock.locked) AskHost(ask) { ask = null }
    }
}

/* ---- the bits of furniture the scaffold needs ---- */

/** A + only where there is something to add — on a By software screen, what its tab adds. */
@Composable
private fun ScreenFab(vm: ConsoleViewModel, nav: Navigator, screen: Screen) {
    val newCustomer = { only: String? ->
        vm.newCustomer = NewCustomerForm(
            weight = only != Software.FABRIC,
            fabric = only == Software.FABRIC
        )
        nav.open(Screen.NewCustomer)
    }
    val recordPayment = { sw: String? ->
        vm.payForm = vm.newPaymentForm(null, sw)
        nav.open(Screen.RecordPayment)
    }
    when (screen) {
        Screen.Enquiries -> Fab("New enquiry") { nav.open(Screen.EnquiryForm(null)) }
        Screen.Customers -> Fab("New customer") { newCustomer(null) }
        Screen.Payments, Screen.Validity -> Fab("Record payment") { recordPayment(vm.paySoft) }
        Screen.Plans -> Fab("New plan") {
            val sw = vm.planSoft ?: Software.WEIGHT
            if (vm.plans?.block(sw)?.supported == true) nav.open(Screen.PlanWin(sw, null))
            else vm.say(Software.name(sw) + ": " + (vm.plans?.block(sw)?.problem ?: "its plans could not be read."), Msg.Kind.WARN)
        }
        is Screen.BySoftware -> when (vm.swView(screen.sw).tab) {
            "plans" -> if (vm.plans?.block(screen.sw)?.supported == true) Fab("New plan") { nav.open(Screen.PlanWin(screen.sw, null)) }
            "payments" -> Fab("Record payment") { recordPayment(screen.sw) }
            else -> Fab("New customer") { newCustomer(screen.sw) }
        }
        else -> Unit
    }
}

@Composable
private fun Fab(label: String, onClick: () -> Unit) {
    val c = LocalNexora.current
    FloatingActionButton(
        onClick = onClick,
        containerColor = c.accent,
        contentColor = Color.White
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Add, label, Modifier.size(20.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun IconTap(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val c = LocalNexora.current
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = c.muted, modifier = Modifier.size(22.dp))
    }
}

/** What the top bar says, per screen. 1.6.0 — headings in Proper Case (a company's own name is shown as it was typed). */
private fun titleOf(screen: Screen, vm: ConsoleViewModel): String = when (screen) {
    Screen.Plans -> "Software & Plans"
    Screen.Validity -> "Validity & Renewals"
    is Screen.Root -> screen.title
    is Screen.CustomerWin -> vm.customer(screen.key)?.name ?: "Customer"
    is Screen.PaymentWin -> "Payment"
    Screen.RecordPayment -> "Record Payment"
    is Screen.PlanWin -> if (screen.code == null) "New Plan" else (vm.plans?.plan(screen.sw, screen.code)?.name ?: "Plan")
    is Screen.EnquiryForm -> if (screen.id == null) "New Enquiry" else "Edit Enquiry"
    is Screen.FeedbackDetail -> vm.feedbackById(screen.id)?.let { if (it.isBug) "Problem Report" else "Feedback" } ?: "Report"
    Screen.NewCustomer -> "New Customer"
    Screen.Announce -> "Tell The Customers"
    Screen.Settings -> "Service Settings"
    Screen.Broadcast -> "Message Every Plant"
    Screen.About -> "About"
}

private fun subtitleOf(screen: Screen, vm: ConsoleViewModel): String? = when (screen) {
    Screen.Dashboard -> "Every Nexora software at a glance"
    Screen.Customers -> vm.customers.let { all -> "${all.size} customers · ${all.count { it.both }} on both software" }
    Screen.Validity -> "${vm.validityAll.count { it.live && it.left <= 30 }} ending within 30 days · ending first"
    Screen.Payments -> vm.payments?.payments.orEmpty().let { "${it.size} payments · ${Money.rupees(PayFilter.sum(it))}" }
    Screen.Plans -> "${vm.plans?.all?.size ?: 0} plans · each software its own"
    is Screen.BySoftware -> "${vm.customers.count { it.has(screen.sw) }} customers · " +
        (if (screen.sw == Software.FABRIC) "Nexora Loom & Fabric Stock" else "Nexora Bag Weight Calculation")
    Screen.Enquiries -> "${vm.inquiryData.inquiries.size} in all · ${vm.openInquiries} open"
    Screen.Machines -> "${vm.data.licences.size} installed · ${vm.runningCount} running"
    Screen.Feedback -> "${vm.feedbackData.feedback.size} in all · ${vm.openFeedback} open · ${vm.openBugs} problems"
    is Screen.FeedbackDetail -> vm.feedbackById(screen.id)?.plant
    is Screen.CustomerWin -> vm.customer(screen.key)?.let { x ->
        listOfNotNull(x.w?.let { Software.WEIGHT_NAME }, x.f?.let { Software.FABRIC_NAME }).joinToString(" + ")
    }
    is Screen.PaymentWin -> vm.paymentById(screen.id)?.let { it.customer + " · " + it.softwareName }
    is Screen.PlanWin -> Software.name(screen.sw)
    else -> null
}

/** The red dot: enquiries and reports nobody has answered yet. */
private fun badgeFor(dest: Screen.Root, vm: ConsoleViewModel): Int = when (dest) {
    Screen.Enquiries -> vm.newInquiryCount
    Screen.Feedback -> vm.newFeedbackCount
    Screen.Validity -> vm.validityAll.count { it.live && it.left <= 7 }
    else -> 0
}

/* 1.7.0 — each tab in its own gradient, a pill that grows in under the one you are on, and a press felt */
private fun tabGradient(dest: Screen.Root): List<Color> = when (dest) {
    Screen.Dashboard -> listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
    Screen.Customers -> listOf(Color(0xFFF97316), Color(0xFFF59E0B))
    Screen.Payments -> listOf(Color(0xFF15803D), Color(0xFF34D399))
    Screen.Plans -> listOf(Color(0xFF7C3AED), Color(0xFFA855F7))
    Screen.Validity -> listOf(Color(0xFFE11D48), Color(0xFFF472B6))
    Screen.Enquiries -> listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
    Screen.Feedback -> listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
    is Screen.BySoftware -> SwColours.of(dest.sw)
    else -> listOf(Color(0xFF0A66E0), Color(0xFF06B6D4))
}

@Composable
private fun GradientTabBar(vm: ConsoleViewModel, nav: Navigator) {
    val c = LocalNexora.current
    Surface(color = c.bgElevated, shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ROOTS.forEach { dest ->
                val sel = nav.root == dest && !nav.canGoBack
                val g = tabGradient(dest)
                val on by androidx.compose.animation.core.animateFloatAsState(if (sel) 1f else 0f, androidx.compose.animation.core.tween(280), label = "tab")
                val count = badgeFor(dest, vm)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .pressable { nav.switchTo(dest) }.padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(width = 58.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = (34 + 24 * on).dp, height = 32.dp).graphicsLayer { alpha = on }
                            .clip(CircleShape).background(Brush.horizontalGradient(g)))
                        BadgedBox(badge = {
                            if (count > 0) Badge(containerColor = c.bad, contentColor = Color.White) { Text(if (count > 99) "99+" else "$count", fontSize = 10.sp) }
                        }) { Icon(dest.icon, dest.title, Modifier.size(22.dp), tint = if (sel) Color.White else g[0].copy(alpha = 0.8f)) }
                    }
                    Text(dest.title, fontSize = 11.sp, maxLines = 1, color = if (sel) g[0] else c.muted,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/** The side menu: every place of the console — the records, each software, the leads and reports, then do, see, set up, and sign out. */
@Composable
private fun ConsoleDrawer(vm: ConsoleViewModel, nav: Navigator, onExport: () -> Unit, go: (() -> Unit) -> Unit) {
    val c = LocalNexora.current
    val at = { d: Screen.Root -> nav.root == d && !nav.canGoBack }
    ModalDrawerSheet(drawerContainerColor = c.bgElevated, modifier = Modifier.padding(end = 56.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF7C3AED)))).padding(20.dp)) {
                Column {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { BrandMark(36) }
                    Spacer(Modifier.height(10.dp))
                    Text("Nexora Console", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("every Nexora software, in your pocket", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            listOf(Screen.Dashboard, Screen.Customers, Screen.Validity, Screen.Payments).forEach { d ->
                DrawerRow(d.icon, if (d == Screen.Validity) "Validity & renewals" else d.title, tabGradient(d)[0], at(d), badgeFor(d, vm)) {
                    go { if (d == Screen.Customers) { vm.companyEnding = false }; nav.switchTo(d) }
                }
            }
            DrawerSection("By software")
            listOf(Software.WEIGHT, Software.FABRIC).forEach { sw ->
                val d = Screen.BySoftware(sw)
                DrawerRow(d.icon, d.title, SwColours.of(sw)[0], at(d), note = vm.customers.count { it.has(sw) }.toString()) { go { nav.switchTo(d) } }
            }
            DrawerRow(Icons.Outlined.Build, Software.JOBWORK_NAME, SwColours.JOBWORK[0], note = "soon", enabled = false) {}
            DrawerRow(Screen.Plans.icon, "Software & plans", tabGradient(Screen.Plans)[0], at(Screen.Plans), note = (vm.plans?.all?.size ?: 0).toString()) {
                go { nav.switchTo(Screen.Plans) }
            }
            DrawerSection("Leads and reports")
            listOf(Screen.Enquiries, Screen.Feedback).forEach { d ->
                DrawerRow(d.icon, d.title, tabGradient(d)[0], at(d), badgeFor(d, vm)) { go { nav.switchTo(d) } }
            }
            DrawerSection("Do")
            DrawerRow(Icons.Outlined.Campaign, "Tell the customers", Color(0xFFEC4899)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Announce) } }
            DrawerRow(Icons.Outlined.Forum, "Message every plant", Color(0xFF7C3AED)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Broadcast) } }
            DrawerRow(Icons.Outlined.TableChart, "Export to Excel", Color(0xFF15803D)) { go { onExport() } }
            DrawerSection("See")
            DrawerRow(Icons.Outlined.Computer, "Machines", Color(0xFF0A66E0), at(Screen.Machines)) { go { nav.switchTo(Screen.Machines) } }
            DrawerSection("Set up")
            DrawerRow(Icons.Outlined.Tune, "Service settings", Color(0xFF0D9488)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Settings) } }
            DrawerRow(Icons.Outlined.SystemUpdate, if (vm.updateAvailable) "Update to ${vm.release?.versionName}" else "Check for updates", Color(0xFF2563EB)) {
                go { if (vm.updateAvailable) { nav.switchTo(Screen.Dashboard); nav.open(Screen.About) } else vm.checkForUpdate(loud = true) }
            }
            DrawerRow(Icons.Outlined.MoreHoriz, "More tools", Color(0xFF475569), at(Screen.More)) { go { nav.switchTo(Screen.More) } }
            DrawerRow(Icons.Outlined.Info, "About this console", Color(0xFF475569)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.About) } }
            DrawerSection("")
            DrawerRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", c.bad) { go { vm.signOut() } }
            Spacer(Modifier.height(16.dp))
            Text("Console " + org.nexoraofficial.console.BuildConfig.VERSION_NAME, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun DrawerSection(text: String) {
    val c = LocalNexora.current
    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = c.border)
    if (text.isNotBlank()) Text(text.uppercase(), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp))
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    selected: Boolean = false,
    count: Int = 0,
    note: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShapeDrawer)
        .background(if (selected) tint.copy(alpha = 0.12f) else Color.Transparent)
        .then(if (enabled) Modifier.pressable(onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = c.text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (count > 0) Badge(containerColor = c.bad, contentColor = Color.White) { Text(if (count > 99) "99+" else "$count", fontSize = 10.sp) }
        else if (note != null) Text(note, color = c.muted, fontSize = 11.5f.sp, fontWeight = FontWeight.SemiBold)
    }
}
private val RoundedCornerShapeDrawer = RoundedCornerShape(28.dp)
