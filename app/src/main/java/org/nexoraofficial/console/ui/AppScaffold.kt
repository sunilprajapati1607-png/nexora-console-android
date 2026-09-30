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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Menu
import kotlinx.coroutines.launch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Reports
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * THE APPLICATION, AS AN APPLICATION.
 *
 *   "perfect menu and ui like other best android app,
 *    menu section wise form entry view all function"
 *
 * One page that scrolled for ever has become five sections along the bottom,
 * each with its own screen, its own search and its own list — and forms that
 * open as screens of their own with a Save button, the way every Android
 * application anybody already knows works.
 *
 * The furniture is the system's: a top bar that says where you are, a
 * navigation bar that says what else there is, a + where a + belongs, and
 * Back that goes back. The colours and the type stay the console's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(vm: ConsoleViewModel) {
    val c = LocalNexora.current
    val context = LocalContext.current
    val nav = remember { Navigator() }
    var ask by remember { mutableStateOf<Ask?>(null) }

    val wide = LocalConfiguration.current.screenWidthDp >= 600

    /* Back goes back through what is open; on a root it leaves the app. */
    BackHandler(enabled = nav.canGoBack) { nav.back() }

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
    val drawer = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    androidx.compose.material3.ModalNavigationDrawer(
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
                        IconTap(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { nav.back() }
                    } else {
                        IconTap(Icons.Outlined.Menu, "Menu") { scope.launch { drawer.open() } }
                    }
                },
                title = {
                    Column {
                        Text(
                            titleOf(screen, vm),
                            color = c.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        subtitleOf(screen, vm)?.let {
                            Text(it, color = c.muted, fontSize = 11.5f.sp)
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
            if (!nav.canGoBack && nav.root in ROOTS) GradientTabBar(vm, nav)
        },
        floatingActionButton = {
            /* A + only where there is something to add. */
            when (screen) {
                Screen.Enquiries -> Fab("New enquiry") { nav.open(Screen.EnquiryForm(null)) }
                Screen.Companies -> Fab("New company") { nav.open(Screen.NewCompany) }
                else -> Unit
            }
        }
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
                Screen.Enquiries -> EnquiriesScreen(vm, nav, gutter, page) { ask = it }
                Screen.Companies -> CompaniesScreen(vm, nav, gutter, page)
                Screen.Machines -> MachinesScreen(vm, gutter, page) { ask = it }
                Screen.Feedback -> FeedbackScreen(vm, nav, gutter, page)
                Screen.More -> MoreScreen(vm, nav, gutter, page, exportAll)

                is Screen.Company -> CompanyScreen(vm, nav, screen.id, gutter, page) { ask = it }
                is Screen.FeedbackDetail -> FeedbackDetailScreen(vm, nav, screen.id, gutter, page) { ask = it }
                is Screen.EnquiryForm -> EnquiryFormScreen(vm, nav, screen.id, gutter, page, wide)
                Screen.NewCompany -> NewCompanyScreen(vm, nav, gutter, page, wide)
                Screen.Announce -> AnnounceScreen(vm, gutter, page)
                Screen.Settings -> SettingsScreen(vm, gutter, page)
                Screen.Plans -> PlansScreen(vm, gutter, page)
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

    AskHost(ask) { ask = null }
    }
}

/* ---- the bits of furniture the scaffold needs ---- */

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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

/** What the top bar says, per screen. */
private fun titleOf(screen: Screen, vm: ConsoleViewModel): String = when (screen) {
    is Screen.Root -> screen.title
    is Screen.Company -> vm.data.companies.find { it.id == screen.id }?.name ?: "Company"
    /* 1.6.0 — headings in Proper Case (a company's own name is shown as it was typed) */
    is Screen.EnquiryForm -> if (screen.id == null) "New Enquiry" else "Edit Enquiry"
    is Screen.FeedbackDetail -> vm.feedbackById(screen.id)?.let { if (it.isBug) "Problem Report" else "Feedback" } ?: "Report"
    Screen.NewCompany -> "New Company"
    Screen.Announce -> "Tell The Customers"
    Screen.Settings -> "Service Settings"
    Screen.Plans -> "Plans"
    Screen.Broadcast -> "Message Every Plant"
    Screen.About -> "About"
}

@Composable
private fun subtitleOf(screen: Screen, vm: ConsoleViewModel): String? = when (screen) {
    Screen.Dashboard -> "Nexora Licence Console"
    Screen.Enquiries -> "${vm.inquiryData.inquiries.size} in all · ${vm.openInquiries} open"
    Screen.Companies -> "${vm.customerCount} customers · ${vm.demoCount} demos"
    Screen.Machines -> "${vm.data.licences.size} installed · ${vm.runningCount} running"
    Screen.Feedback -> "${vm.feedbackData.feedback.size} in all · ${vm.openFeedback} open · ${vm.openBugs} problems"
    is Screen.FeedbackDetail -> vm.feedbackById(screen.id)?.plant
    is Screen.Company -> vm.data.companies.find { it.id == screen.id }?.licenceKey
    else -> null
}

/** The red dot: enquiries nobody has answered yet. */
private fun badgeFor(dest: Screen.Root, vm: ConsoleViewModel): Int = when (dest) {
    Screen.Enquiries -> vm.newInquiryCount
    Screen.Feedback -> vm.newFeedbackCount
    else -> 0
}

/* 1.7.0 — each tab in its own gradient, a pill that grows in under the one you are on, and a press felt */
private fun tabGradient(dest: Screen.Root): List<Color> = when (dest) {
    Screen.Dashboard -> listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
    Screen.Enquiries -> listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
    Screen.Feedback -> listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
    Screen.Companies -> listOf(Color(0xFF0D9488), Color(0xFF22C55E))
    else -> listOf(Color(0xFF0A66E0), Color(0xFF06B6D4))
}

@Composable
private fun GradientTabBar(vm: ConsoleViewModel, nav: Navigator) {
    val c = LocalNexora.current
    androidx.compose.material3.Surface(color = c.bgElevated, shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(androidx.compose.material3.NavigationBarDefaults.windowInsets)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ROOTS.forEach { dest ->
                val sel = nav.root == dest && !nav.canGoBack
                val g = tabGradient(dest)
                val on by androidx.compose.animation.core.animateFloatAsState(if (sel) 1f else 0f, androidx.compose.animation.core.tween(280), label = "tab")
                val count = badgeFor(dest, vm)
                Column(
                    Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .pressable { nav.switchTo(dest) }.padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(width = 58.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = (34 + 24 * on).dp, height = 32.dp).graphicsLayer { alpha = on }
                            .clip(CircleShape).background(androidx.compose.ui.graphics.Brush.horizontalGradient(g)))
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

/** 1.7.0 — the side menu: the four places, then what More had — do, see, set up — and sign out. */
@Composable
private fun ConsoleDrawer(vm: ConsoleViewModel, nav: Navigator, onExport: () -> Unit, go: (() -> Unit) -> Unit) {
    val c = LocalNexora.current
    androidx.compose.material3.ModalDrawerSheet(drawerContainerColor = c.bgElevated, modifier = Modifier.padding(end = 56.dp)) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF7C3AED)))).padding(20.dp)) {
                Column {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { BrandMark(36) }
                    Spacer(Modifier.height(10.dp))
                    Text("Nexora Console", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("the licence service, in your pocket", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            ROOTS.forEach { d ->
                DrawerRow(d.icon, d.title, tabGradient(d)[0], nav.root == d && !nav.canGoBack, badgeFor(d, vm)) { go { nav.switchTo(d) } }
            }
            DrawerSection("Do")
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.Campaign, "Tell the customers", Color(0xFFEC4899)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Announce) } }
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.Forum, "Message every plant", Color(0xFF7C3AED)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Broadcast) } }
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.TableChart, "Export to Excel", Color(0xFF15803D)) { go { onExport() } }
            DrawerSection("See")
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.Computer, "The machines", Color(0xFF0A66E0)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Machines) } }
            DrawerSection("Set up")
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.Tune, "Service settings", Color(0xFF0D9488)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Settings) } }
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.WorkspacePremium, "Plans", Color(0xFFF59E0B)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.Plans) } }
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.MoreHoriz, "More", Color(0xFF475569), nav.root == Screen.More && !nav.canGoBack) { go { nav.switchTo(Screen.More) } }
            DrawerRow(androidx.compose.material.icons.Icons.Outlined.Info, "About this console", Color(0xFF475569)) { go { nav.switchTo(Screen.Dashboard); nav.open(Screen.About) } }
            DrawerSection("")
            DrawerRow(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.Logout, "Sign out", c.bad) { go { vm.signOut() } }
            Spacer(Modifier.height(16.dp))
            Text("Console " + org.nexoraofficial.console.BuildConfig.VERSION_NAME, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun DrawerSection(text: String) {
    val c = LocalNexora.current
    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = c.border)
    if (text.isNotBlank()) Text(text.uppercase(), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp))
}

@Composable
private fun DrawerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, selected: Boolean = false, count: Int = 0, onClick: () -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShapeDrawer)
        .background(if (selected) tint.copy(alpha = 0.12f) else Color.Transparent).pressable(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = c.text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (count > 0) Badge(containerColor = c.bad, contentColor = Color.White) { Text(if (count > 99) "99+" else "$count", fontSize = 10.sp) }
    }
}
private val RoundedCornerShapeDrawer = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
