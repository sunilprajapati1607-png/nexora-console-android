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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
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

    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (nav.canGoBack) {
                        IconTap(Icons.AutoMirrored.Outlined.ArrowBack, "Back") { nav.back() }
                    } else {
                        Box(Modifier.padding(start = 12.dp)) { BrandMark(30) }
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
                        IconTap(Icons.Outlined.Refresh, "Refresh") {
                            vm.load()
                            vm.loadInquiries(quiet = true)
                            vm.loadFeedback(quiet = true)
                        }
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
            NavigationBar(containerColor = c.bgElevated, tonalElevation = 0.dp) {
                ROOTS.forEach { dest ->
                    val selected = nav.root == dest && !nav.canGoBack
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.switchTo(dest) },
                        icon = {
                            val count = badgeFor(dest, vm)
                            if (count > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = c.bad, contentColor = Color.White) {
                                        Text(if (count > 99) "99+" else "$count", fontSize = 10.sp)
                                    }
                                }) { Icon(dest.icon, dest.title, Modifier.size(22.dp)) }
                            } else {
                                Icon(dest.icon, dest.title, Modifier.size(22.dp))
                            }
                        },
                        label = { Text(dest.title, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accent,
                            selectedTextColor = c.accent,
                            indicatorColor = c.accentBg,
                            unselectedIconColor = c.muted,
                            unselectedTextColor = c.muted
                        )
                    )
                }
            }
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
    is Screen.EnquiryForm -> if (screen.id == null) "New enquiry" else "Edit enquiry"
    is Screen.FeedbackDetail -> vm.feedbackById(screen.id)?.let { if (it.isBug) "Problem report" else "Feedback" } ?: "Report"
    Screen.NewCompany -> "New company"
    Screen.Announce -> "Tell the customers"
    Screen.Settings -> "Service settings"
    Screen.Plans -> "Plans"
    Screen.Broadcast -> "Message every plant"
    Screen.About -> "About"
}

@Composable
private fun subtitleOf(screen: Screen, vm: ConsoleViewModel): String? = when (screen) {
    Screen.Dashboard -> "Nexora licence console"
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
