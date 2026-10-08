package org.nexoraofficial.console.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.Customer
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.FabricCompany
import org.nexoraofficial.console.data.FabricEdit
import org.nexoraofficial.console.data.FeatureTag
import org.nexoraofficial.console.data.FeatureView
import org.nexoraofficial.console.data.Features
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.PayFilter
import org.nexoraofficial.console.data.Plans
import org.nexoraofficial.console.data.Requests
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.WeightEdit
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — ONE CUSTOMER, AS A WINDOW (as the web console's renderWin).

   Owner, 2026-10-08: "row type details click and open window". The head
   says who it is and DISPLAY or EDIT; the tools act on the software shown;
   who they are (name, GSTIN, email, mobile, note); a tab for each software
   — Sales & Costing, Fabric Stock, Jobwork (coming) — and under the one
   shown its own tabs: Licence · Features · People · Computers & phones ·
   Payments · More (Company on Fabric Stock).

   2.0.1 — Fabric Stock has its own plans and features (its 0.8.1 service):
   a Features tab like Sales & Costing's (the same cards, its own catalogue),
   a plan chooser on its Licence in Edit and a Plan tool; Save sends plan,
   features and the rest in its one update. An older Fabric Stock service
   (no plans) keeps 2.0.0's tabs.

   Read-only first. Edit opens the licence, the identity and the features
   for change; Save sends each change the web console sends (plan, seats,
   offline days, AI a day, transaction limit, the customer's own features —
   on Fabric Stock one update); Cancel leaves everything as it was.
   ====================================================================== */

@Composable
fun CustomerWindow(
    vm: ConsoleViewModel,
    nav: Navigator,
    screen: Screen.CustomerWin,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val x = vm.customer(screen.key)
    if (x == null) {
        /* a Fabric Stock customer while Fabric Stock is still being read, or not reachable */
        val problem = vm.fabricProblem
        if (screen.key.startsWith("f") && (problem != null || !vm.fabricReady)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = gutter, horizontalAlignment = Alignment.CenterHorizontally) {
                item { Box(page) { if (problem != null) FabricNotConnectedCard(vm, problem) else FabricReadingCard() } }
            }
            return
        }
        /* deleted while it was open, or the list has been read again without it */
        LaunchedEffect(Unit) { nav.backToRoot() }
        return
    }
    CustomerBody(vm, nav, x, screen, gutter, page, onAsk)
}

/** Where the software's own tabs sit in the window's list: after "head", "tools", "identity" and "software". */
private const val SUBS_ITEM = 4

@Composable
private fun CustomerBody(
    vm: ConsoleViewModel,
    nav: Navigator,
    x: Customer,
    screen: Screen.CustomerWin,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val key = x.key
    var sw by rememberSaveable(key) { mutableStateOf(screen.sw ?: if (x.w != null) Software.WEIGHT else Software.FABRIC) }
    var subPicked by rememberSaveable(key) { mutableStateOf(screen.sub ?: "licence") }
    var editing by rememberSaveable(key) { mutableStateOf(false) }
    var wEdit by remember(key) { mutableStateOf<WeightEdit?>(null) }
    var fEdit by remember(key) { mutableStateOf<FabricEdit?>(null) }

    val weightTab = sw == Software.WEIGHT
    val c = x.w
    val f = x.f
    /* 2.0.1 — Fabric Stock has a Features tab once its own service has plans (0.8.1); an older one keeps 2.0.0's tabs */
    val fabricPlans = Plans.supported(vm.plans, Software.FABRIC)
    val subs = if (weightTab)
        listOf("licence" to "Licence", "features" to "Features", "people" to "People", "computers" to "Computers & phones",
            "payments" to "Payments", "more" to "More")
    else listOfNotNull("licence" to "Licence", if (fabricPlans) "features" to "Features" else null, "people" to "People",
        "computers" to "Computers & phones", "payments" to "Payments", "company" to "Company")
    val sub = if (subs.any { it.first == subPicked }) subPicked else "licence"
    val licence = if (weightTab) c != null else f != null

    fun stopEditing() {
        editing = false
        wEdit = null
        fEdit = null
    }

    /* what changing place does while something is being edited: ask first, as the software does */
    fun leaveEdit(then: () -> Unit) {
        if (!editing) return then()
        onAsk(Ask.Confirm("Leave Edit without saving?", "What was changed here is not saved.", "Leave", danger = true) {
            stopEditing(); then()
        })
    }

    fun startEditing(onSub: String? = null) {
        if (weightTab) wEdit = c?.let { WeightEdit.of(it) } ?: return
        else fEdit = f?.let { FabricEdit.of(it) } ?: return
        editing = true
        subPicked = onSub ?: if (sub == "licence" || sub == "features") sub else "licence"
    }

    /* Back while editing asks first (the bar's arrow and the phone's both come here) */
    DisposableEffect(editing) {
        nav.guard = if (editing) ({
            onAsk(Ask.Confirm("Close without saving the changes?", "What was changed here is not saved.", "Close", danger = true) {
                stopEditing(); nav.back()
            })
        }) else null
        onDispose { nav.guard = null }
    }
    BackHandler(enabled = editing) { nav.requestBack() }

    /* the Fabric Stock company on show is read with its people and devices, and again after every change */
    val fid = f?.id
    DisposableEffect(weightTab, fid) {
        vm.fabricOpen = if (!weightTab) fid else null
        onDispose { if (vm.fabricOpen == fid) vm.fabricOpen = null }
    }
    LaunchedEffect(weightTab, fid) {
        if (weightTab) return@LaunchedEffect
        if (fid != null) vm.loadFabricDetail(fid)
        else if (vm.productsLoad == Load.IDLE) vm.loadProducts()
    }
    /* the Sales & Costing people are asked when their tab is opened */
    LaunchedEffect(key, weightTab, sub) {
        if (weightTab && c != null && sub == "people") vm.loadPeople(c.id)
    }

    val order = vm.customerRows.map { it.key }
    val at = order.indexOf(key)

    /* 2.0.1 — Plan and Seats open the Licence in Edit and bring it into view (the web console focuses the field):
       the window is scrolled to its tabs — the fifth item, after the head, the tools, who they are and the software */
    val list = rememberLazyListState()
    var bringLicence by remember(key) { mutableStateOf(0) }
    LaunchedEffect(bringLicence) { if (bringLicence > 0) list.animateScrollToItem(SUBS_ITEM) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) {
                WindowHead(initials(x.name), SwColours.of(if (licence) sw else Software.WEIGHT), x.name, x.subtitle,
                    if (editing) "EDIT" else "DISPLAY")
            }
        }

        item(key = "tools") {
            Box(page) {
                ToolStrip(
                    customerTools(
                        vm, nav, x, sw, editing, onAsk,
                        edit = { startEditing() },
                        editOn = { s -> startEditing(s); if (editing) bringLicence++ },
                        save = {
                            if (weightTab && c != null) wEdit?.let { e -> vm.saveWeight(c, e) { ok -> if (ok) stopEditing() } }
                            else if (f != null) fEdit?.let { e -> vm.saveFabric(f, e) { ok -> if (ok) stopEditing() } }
                        },
                        cancel = { stopEditing() },
                        prev = if (at > 0) ({ leaveEdit { nav.replace(Screen.CustomerWin(order[at - 1], null, sub)) } }) else null,
                        next = if (at >= 0 && at < order.size - 1) ({ leaveEdit { nav.replace(Screen.CustomerWin(order[at + 1], null, sub)) } }) else null
                    )
                )
            }
        }

        item(key = "identity") {
            Box(page) {
                IdentityCard(x, weightTab, editing, wEdit, fEdit, { wEdit = it }, { fEdit = it })
            }
        }

        item(key = "software") {
            Box(page) {
                SwTabs(x, sw, vm) { pick -> if (pick != sw) leaveEdit { sw = pick; subPicked = "licence" } }
            }
        }

        if (licence) {
            item(key = "subs") {
                Box(page) {
                    val counts = buildMap {
                        val pays = vm.payments?.of(x, sw)?.size ?: 0
                        if (pays > 0) put("payments", pays.toString())
                        val n = Customers.ownCount(x, sw)
                        if (n > 0) put("features", "± $n")
                    }
                    SubTabs(subs, sub, counts) { pick -> if (pick != sub) leaveEdit { subPicked = pick } }
                }
            }
        }

        when {
            weightTab && c != null -> weightItems(vm, nav, x, c, sub, wEdit, { wEdit = it }, page, onAsk)
            weightTab && f != null -> item(key = "no-weight") { Box(page) { NotUsingWeightCard(vm, nav, f, onAsk) } }
            !weightTab && f != null -> fabricItems(vm, nav, x, f, sub, fEdit, { fEdit = it }, page, onAsk)
            else -> {
                val problem = vm.fabricProblem
                item(key = "no-fabric") {
                    Box(page) {
                        when {
                            problem != null -> FabricNotConnectedCard(vm, problem)
                            !vm.fabricReady -> FabricReadingCard()
                            c != null -> NotUsingFabricCard(c, vm, onAsk)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- tools */

private fun customerTools(
    vm: ConsoleViewModel,
    nav: Navigator,
    x: Customer,
    sw: String,
    editing: Boolean,
    onAsk: (Ask) -> Unit,
    edit: () -> Unit,
    editOn: (String) -> Unit,
    save: () -> Unit,
    cancel: () -> Unit,
    prev: (() -> Unit)?,
    next: (() -> Unit)?
): List<Tool> {
    val t = ArrayList<Tool>()
    val c = x.w
    val f = x.f
    val licence = if (sw == Software.WEIGHT) c != null else f != null
    if (editing) {
        t += Tool("Save", Icons.Outlined.Save, ToolColours.green, !vm.busy, save)
        t += Tool("Cancel", Icons.Outlined.Close, ToolColours.back, true, cancel)
    } else {
        t += Tool("Edit", Icons.Outlined.Edit, ToolColours.blue, licence, edit)
    }
    t += Tool("Previous", Icons.Outlined.ChevronLeft, ToolColours.back, prev != null) { prev?.invoke() }
    t += Tool("Next", Icons.Outlined.ChevronRight, ToolColours.back, next != null) { next?.invoke() }

    if (sw == Software.WEIGHT && c != null) {
        val s = c.swState
        val id = c.id
        /* 2.0.0 — Add days for a demo too (owner 2026-10-08: "koi na demo ma days vadharva hoy"): a demo gets
           more days and stays a demo; Make licensed sits beside it for any demo, ended or not */
        t += Tool("Add days", Icons.Outlined.Add, ToolColours.orange) {
            onAsk(
                Ask.Input(
                    title = if (c.isDemo) "Add how many days to this demo?" else "Add how many days to this licence?",
                    body = if (c.isDemo) "It stays a demo; the days are added to what is left (from today if it has ended)."
                           else "The company's clock moves; every seat follows.",
                    label = "Days",
                    initial = if (c.isDemo) "7" else "30",
                    numeric = true,
                    confirmText = "Add",
                    validate = { if ((it.toIntOrNull() ?: 0) > 0) null else "Enter a number of days." },
                    onOk = { v -> vm.act(id, "extend", v.toInt()) }
                )
            )
        }
        t += if (c.isDemo) Tool("Make licensed", Icons.Outlined.CheckCircle, ToolColours.green) {
            onAsk(Ask.Confirm("Make ${c.name} a licensed Sales & Costing customer for 1 year?",
                "A demo has every feature whatever its plan; licensed, it has its plan's. Fabric Stock is not touched.",
                "Make licensed") { vm.act(id, "licence", 365) })
        } else Tool("+1 year", Icons.Outlined.Update, ToolColours.amber) {
            onAsk(Ask.Confirm("Add a year to Sales & Costing for ${c.name}?", "It ends on " +
                Fmt.day(java.time.Instant.now().plus(java.time.Duration.ofDays((c.daysLeft + 365).toLong())).toString()) +
                ". Every seat follows.", "Add a year") { vm.act(id, "extend", 365) })
        }
        t += Tool("Plan", Icons.Outlined.WorkspacePremium, ToolColours.violet, !editing) { editOn("licence") }
        t += Tool("Seats", Icons.Outlined.Group, ToolColours.teal, !editing) { editOn("licence") }
        t += if (c.state == "SUSPENDED") Tool("Restore", Icons.Outlined.PlayCircle, ToolColours.green) { vm.act(id, "restore") }
        else Tool("Suspend", Icons.Outlined.PauseCircle, ToolColours.red) { onAsk(suspendQuestion(vm, id)) }
        t += Tool("New key", Icons.Outlined.VpnKey, ToolColours.violet) {
            onAsk(Ask.Confirm("Issue a new licence key for ${c.name}?",
                "The old key stops adding computers and phones at once. Every computer and phone already on the company " +
                    "keeps working — nothing to type there. Use it when somebody who knew the key has left.",
                "New key", danger = true) { vm.rekey(c) })
        }
    }
    if (sw == Software.FABRIC && f != null) {
        t += Tool("Add days", Icons.Outlined.Add, ToolColours.orange) { onAsk(fabricAddDaysQuestion(vm, f)) }
        t += if (f.isDemo) Tool("Make licensed", Icons.Outlined.CheckCircle, ToolColours.green) {
            onAsk(Ask.Confirm("Make ${f.name} a licensed Fabric Stock customer for 1 year?",
                "A year from today. Sales & Costing is not touched.", "Make licensed") { vm.fabricMakeLicensed(f) })
        } else Tool("+1 year", Icons.Outlined.Update, ToolColours.amber) {
            onAsk(Ask.Confirm("Add a year to Fabric Stock for ${f.name}?", "Fabric Stock now ends on " +
                Fmt.day(f.renewEnd(365).toString()) + ".", "Add a year") { vm.fabricAddDays(f, 365) })
        }
        /* 2.0.1 — its own plans (Fabric Stock 0.8.1): Plan opens the Licence in Edit, as Sales & Costing's does */
        if (Plans.supported(vm.plans, Software.FABRIC)) {
            t += Tool("Plan", Icons.Outlined.WorkspacePremium, ToolColours.violet, !editing) { editOn("licence") }
        }
        t += Tool("Seats", Icons.Outlined.Group, ToolColours.teal, !editing) { editOn("licence") }
        t += if (f.state == "SUSPENDED") Tool("Restore", Icons.Outlined.PlayCircle, ToolColours.green) { onAsk(fabricRestoreQuestion(vm, f)) }
        else Tool("Suspend", Icons.Outlined.PauseCircle, ToolColours.red) { onAsk(fabricSuspendQuestion(vm, f)) }
    }
    if (licence) {
        t += Tool("Record payment", Icons.Outlined.Payments, ToolColours.teal) {
            vm.payForm = vm.newPaymentForm(x.key, sw)
            nav.open(Screen.RecordPayment)
        }
    }
    return t
}

/* ------------------------------------------------------------- identity */

@Composable
private fun IdentityCard(
    x: Customer,
    weightTab: Boolean,
    editing: Boolean,
    wEdit: WeightEdit?,
    fEdit: FabricEdit?,
    onWeight: (WeightEdit) -> Unit,
    onFabric: (FabricEdit) -> Unit
) {
    ConsoleCard(padding = 14) {
        val gst = x.gstin?.let { it + if (x.w?.gstStatus == "VERIFIED") " · verified" else "" }.orEmpty()
        when {
            editing && weightTab && wEdit != null -> {
                ConsoleField("Name", wEdit.name, { onWeight(wEdit.copy(name = it)) })
                Spacer(Modifier.height(8.dp))
                ConsoleField("GSTIN", wEdit.gstin, { onWeight(wEdit.copy(gstin = it.uppercase().take(15))) }, placeholder = "15 characters")
                Spacer(Modifier.height(8.dp))
                FieldPair({ ReadField("Email", x.email.orEmpty(), it) }, { ReadField("Mobile", x.phone.orEmpty(), it) })
                Spacer(Modifier.height(8.dp))
                ConsoleField("Note", wEdit.note, { onWeight(wEdit.copy(note = it)) }, singleLine = false)
            }
            editing && !weightTab && fEdit != null -> {
                ConsoleField("Name", fEdit.name, { onFabric(fEdit.copy(name = it)) })
                Spacer(Modifier.height(8.dp))
                ConsoleField("GSTIN", fEdit.gstin, { onFabric(fEdit.copy(gstin = it.uppercase().take(15))) }, placeholder = "15 characters")
                Spacer(Modifier.height(8.dp))
                ConsoleField("Email", fEdit.email, { onFabric(fEdit.copy(email = it)) })
                Spacer(Modifier.height(8.dp))
                ConsoleField("Mobile", fEdit.phone, { onFabric(fEdit.copy(phone = it)) })
                Spacer(Modifier.height(8.dp))
                ConsoleField("Note", fEdit.note, { onFabric(fEdit.copy(note = it)) }, singleLine = false)
            }
            else -> {
                FieldPair({ ReadField("Name", x.name, it) }, { ReadField("GSTIN", gst, it) })
                Spacer(Modifier.height(8.dp))
                FieldPair({ ReadField("Email", x.email.orEmpty(), it) }, { ReadField("Mobile", x.phone.orEmpty(), it) })
                Spacer(Modifier.height(8.dp))
                ReadField("Note", (if (weightTab) x.w?.notes else x.f?.notes) ?: "—")
            }
        }
    }
}

/* -------------------------------------------------------- software tabs */

/** Sales & Costing, Fabric Stock and Jobwork (coming) side by side; the one shown wears its software's gradient. */
@Composable
private fun SwTabs(x: Customer, sw: String, vm: ConsoleViewModel, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        /* a software it does not use keeps its name, and says "+ Add" under it (the web console's "+ Add …") */
        SwTab(
            Software.WEIGHT, Icons.Outlined.Scale, Software.WEIGHT_NAME,
            Customers.tabLine(x, Software.WEIGHT, vm.plans) ?: "+ Add — not taken",
            sw == Software.WEIGHT, add = x.w == null
        ) { onPick(Software.WEIGHT) }
        SwTab(
            Software.FABRIC, Icons.Outlined.Inventory2, Software.FABRIC_NAME,
            Customers.tabLine(x, Software.FABRIC, vm.plans) ?: when {
                vm.fabricProblem != null -> "not connected"
                !vm.fabricReady -> "reading…"
                else -> "+ Add — not taken"
            },
            sw == Software.FABRIC, add = x.f == null
        ) { onPick(Software.FABRIC) }
        SwTab(Software.JOBWORK, Icons.Outlined.Build, Software.JOBWORK_NAME, "coming — no licence yet", false, add = false, enabled = false) {}
    }
}

@Composable
private fun SwTab(
    sw: String,
    icon: ImageVector,
    title: String,
    line: String,
    on: Boolean,
    add: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = LocalNexora.current
    val g = SwColours.of(sw)
    Row(
        Modifier
            .width(172.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Brush.horizontalGradient(g) else SolidColor(c.surface))
            .border(1.dp, if (on) Color.Transparent else if (add) g[0].copy(alpha = 0.45f) else c.border, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (on) SolidColor(Color.White.copy(alpha = 0.22f)) else Brush.linearGradient(g)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (on) Color.White else if (add) SwColours.ink(sw, c.isDark) else c.text, fontSize = 13.5f.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = if (on) Color.White.copy(alpha = 0.9f) else c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ------------------------------------------------------ Sales & Costing */

private fun LazyListScope.weightItems(
    vm: ConsoleViewModel,
    nav: Navigator,
    x: Customer,
    c: Company,
    sub: String,
    edit: WeightEdit?,
    onEdit: (WeightEdit) -> Unit,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    when (sub) {
        "licence" -> {
            item(key = "w-licence") { Box(page) { WeightLicenceCard(vm, c, edit, onEdit) } }
            item(key = "w-figs") {
                Box(page) {
                    FigGrid(
                        listOf(
                            Fig("Computers & phones", c.machinesUsed.toString(), "not counted against seats", 0),
                            Fig("Nexora AI today", c.aiUsedToday.toString(), if (c.aiDailyLimit > 0) "of ${c.aiDailyLimit} a day" else "questions asked today", 1),
                            Fig("Transactions", c.txnUsed.toString(), if (c.txnLimit > 0) "of ${c.txnLimit}" + (if (c.txnUsed >= c.txnLimit) " — limit reached, read-only" else "") else "no limit", 2),
                            Fig("Hours in use", Fmt.hours(c.usageMinutes), "summed over its machines", 3)
                        )
                    )
                }
            }
        }
        "features" -> featureItems(
            "w", Features.viewOf(vm.plans, vm.data.settings, c), edit?.features, page
        ) { d -> edit?.let { onEdit(it.copy(features = d)) } }
        "people" -> {
            item(key = "w-signin") { Box(page) { WeightSignInCard(c, vm, onAsk) } }
            item(key = "w-people") { Box(page) { PeopleCard(c, vm, onAsk) } }
        }
        "computers" -> item(key = "w-computers") { Box(page) { WeightComputersCard(vm, nav, c, onAsk) } }
        "payments" -> paymentItems(vm, nav, x, Software.WEIGHT, page)
        "more" -> item(key = "w-more") { Box(page) { WeightMoreCard(c, vm, nav, onAsk) } }
    }
}

@Composable
private fun WeightLicenceCard(vm: ConsoleViewModel, c: Company, edit: WeightEdit?, onEdit: (WeightEdit) -> Unit) {
    val cl = LocalNexora.current
    val clipboard = LocalClipboardManager.current
    val s = c.swState
    ConsoleCard(padding = 14) {
        /* 4.72.0 — audit #90: said where the renewal is done, as well as on the dashboard */
        if (c.endingSoon) {
            MessageStrip(
                Msg(
                    "The licence ${c.endsText} (${Fmt.day(c.expiresAt)}). " +
                        "After that it opens read-only — call them to renew.",
                    Msg.Kind.WARN
                ),
                onDismiss = {}
            )
            Spacer(Modifier.height(12.dp))
        }
        if (edit != null) {
            val current = c.plan.uppercase()
            val all = vm.plans?.plansOf(Software.WEIGHT).orEmpty()
            val offered = all.filter { it.active || it.code == current }
            val options = if (offered.isNotEmpty()) offered.map { it.code to (it.name + if (!it.active) " (retired)" else "") }
            else listOf("STANDARD" to "Standard", "PRO" to "Pro")
            val chosen = all.find { it.code == edit.plan }
            ChoiceField(
                "Plan", options, edit.plan,
                help = listOfNotNull(
                    chosen?.note,
                    chosen?.priceRenewal?.let { "renewal ${Money.rupees(it)} a year" },
                    if (c.isDemo) "a demo has every feature whatever its plan" else null
                ).joinToString(" · ").ifEmpty { null }
            ) { onEdit(edit.copy(plan = it)) }
            Spacer(Modifier.height(10.dp))
        } else {
            FieldPair(
                { ReadField("Plan", vm.planName(Software.WEIGHT, c.plan) + if (c.isDemo) " — a demo has every feature" else "", it) },
                { ReadField("State", Customers.stateWord(s), it, valueColor = stateColour(s)) }
            )
            Spacer(Modifier.height(8.dp))
        }
        ReadField("Licence key", c.licenceKey, mono = true) {
            ConsoleButton("Copy", {
                clipboard.setText(AnnotatedString(c.licenceKey))
                vm.say("Copied ${c.licenceKey}", Msg.Kind.OK)
            }, small = true)
        }
        Spacer(Modifier.height(8.dp))
        FieldPair(
            { ReadField("Company id (sign-in)", c.loginId ?: "—", it) },
            {
                ReadField(if (c.isDemo) "Demo started" else "Licence started", Fmt.day(c.periodStartedAt), it,
                    sub = if (c.periodDays > 0) "${c.periodDays}-day " + (if (c.isDemo) "demo" else "licence") else null)
            }
        )
        Spacer(Modifier.height(8.dp))
        val warn = if (c.renewSoon || c.endingSoon) cl.warn else null
        val ends: @Composable (Modifier) -> Unit = {
            ReadField(
                if (s == "EXPIRED") "Ended" else "Ends",
                Fmt.day(c.expiresAt) + if (c.renewSoon) " · renew soon" else "", it, valueColor = warn,
                sub = if (s == "LICENSED" || s == "DEMO") (if (c.daysLeft == 0) "ends today" else "${c.daysLeft} days left") else Customers.stateWord(s),
                subColor = warn
            )
        }
        if (edit != null) {
            ends(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            FieldPair(
                { ConsoleField("Seats (people)", edit.seats, { v -> onEdit(edit.copy(seats = v.filter { ch -> ch.isDigit() })) }, it, numeric = true) },
                { ConsoleField("Offline days", edit.graceDays, { v -> onEdit(edit.copy(graceDays = v.filter { ch -> ch.isDigit() })) }, it, numeric = true) }
            )
            Spacer(Modifier.height(8.dp))
            ConsoleField("Nexora AI a day (0 = the service's)", edit.aiDaily, { v -> onEdit(edit.copy(aiDaily = v.filter { ch -> ch.isDigit() })) }, numeric = true)
            Spacer(Modifier.height(8.dp))
            ConsoleField("Transaction limit (0 = none)", edit.txnLimit, { v -> onEdit(edit.copy(txnLimit = v.filter { ch -> ch.isDigit() })) }, numeric = true)
            Spacer(Modifier.height(8.dp))
            Help("Save sends each change on its own; the machines hear them at their next check. A seat is a person — computers are not rationed.")
        } else {
            FieldPair(ends) {
                val free = c.seats - c.usersTotal
                ReadField("Seats (people)", "${c.usersTotal} of ${c.seats}", it, sub = if (free > 0) "$free free" else "full")
            }
            Spacer(Modifier.height(8.dp))
            FieldPair(
                { ReadField("Offline days", if (c.graceDays > 0) "${c.graceDays} days" else "none", it) },
                { ReadField("Nexora AI a day", if (c.aiDailyLimit > 0) c.aiDailyLimit.toString() else "the service's own", it) }
            )
            Spacer(Modifier.height(8.dp))
            FieldPair(
                { ReadField("Transaction limit", if (c.txnLimit > 0) c.txnLimit.toString() else "none", it) },
                {
                    ReadField("GST check", when {
                        c.gstin == null -> "no GSTIN"
                        c.gstStatus == "VERIFIED" -> "verified"
                        c.gstStatus == "FAILED" -> "failed"
                        else -> "not yet verified"
                    }, it)
                }
            )
            Spacer(Modifier.height(8.dp))
            ReadField("Self-registered",
                if (c.selfRegistered) "yes · " + Fmt.day(c.registeredAt) + (c.registeredIp?.let { " · $it" } ?: "") else "no")
        }
    }
}

/* ---- the features over the plan (2.0.1: any software — Sales & Costing's and Fabric Stock's alike) ---- */

/**
 * A licence's Features tab: the summary, then every feature by group. [draft] is the changes being made
 * in Edit (null in DISPLAY: the rows cannot be tapped); [onDraft] gets the draft after a tap or "Back to
 * the plan only". [prefix] keeps each software's list items apart.
 */
private fun LazyListScope.featureItems(
    prefix: String,
    view: FeatureView,
    draft: Map<String, Boolean?>?,
    page: Modifier,
    onDraft: (Map<String, Boolean?>) -> Unit
) {
    item(key = "$prefix-features-sum") { Box(page) { FeatureSummaryCard(view, draft, onDraft) } }
    item(key = "$prefix-features") { Box(page) { FeatureListCard(view, draft, onDraft) } }
}

@Composable
private fun FeatureSummaryCard(view: FeatureView, draft: Map<String, Boolean?>?, onDraft: (Map<String, Boolean?>) -> Unit) {
    val cl = LocalNexora.current
    val s = view.summary(draft)
    ConsoleCard(padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(view.planName, color = cl.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(" gives ${s.fromPlan} of ${s.total}", color = cl.text, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("+${s.added} added", color = cl.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("−${s.off} off", color = cl.bad, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("→ ${s.on} on", color = cl.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Small(
            when {
                view.demo -> "A demo has every feature; these apply once it is licensed."
                draft != null -> "Tap a feature to add it or take it off for ${view.name} only. The plan itself stays as it is."
                else -> "Grey until Edit. The plan itself stays as it is."
            }
        )
        if (draft != null) {
            Spacer(Modifier.height(8.dp))
            ConsoleButton("Back to the plan only", { onDraft(view.reset()) }, small = true)
        }
    }
}

@Composable
private fun FeatureListCard(view: FeatureView, draft: Map<String, Boolean?>?, onDraft: (Map<String, Boolean?>) -> Unit) {
    val own = view.ownWith(draft)
    ConsoleCard(padding = 14) {
        if (view.catalogue.isEmpty()) Help("${Software.name(view.sw)} sent no features to show.")
        view.groups.forEachIndexed { gi, g ->
            if (gi > 0) Spacer(Modifier.height(12.dp))
            Text(g.uppercase(), color = LocalNexora.current.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(6.dp))
            view.catalogue.filter { it.group == g }.forEach { fe ->
                val p = view.plan[fe.id] == true
                val o = own[fe.id]
                FeatureRow(fe.label, Features.tag(p, o), Features.effective(p, o), draft != null) {
                    if (draft != null) onDraft(view.toggle(fe.id, draft))
                }
            }
        }
    }
}

/** One feature: its tick, its name, and how it stands (from plan, + added, − off, not in plan). */
@Composable
internal fun FeatureRow(label: String, tag: FeatureTag?, on: Boolean, enabled: Boolean, onTap: () -> Unit) {
    val c = LocalNexora.current
    val (bg, line, tagColour) = when (tag) {
        FeatureTag.ADDED -> Triple(c.accentBg, c.accent.copy(alpha = 0.5f), c.accent)
        FeatureTag.OFF -> Triple(c.badBg, c.bad.copy(alpha = 0.45f), c.bad)
        else -> Triple(Color.Transparent, c.border, c.muted)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .border(1.dp, line, RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.pressable(onTap) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                .background(if (on) c.accent else Color.Transparent)
                .border(1.5.dp, if (on) c.accent else c.borderStrong, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) { if (on) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(
            label, color = if (tag == FeatureTag.OFF) c.bad else c.text, fontSize = 13.5f.sp, modifier = Modifier.weight(1f),
            textDecoration = if (tag == FeatureTag.OFF) TextDecoration.LineThrough else null
        )
        tag?.let { Text(it.word, color = tagColour, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp)) }
    }
}

/* ---- computers and phones ---- */

@Composable
private fun WeightComputersCard(vm: ConsoleViewModel, nav: Navigator, c: Company, onAsk: (Ask) -> Unit) {
    val cl = LocalNexora.current
    val mine = vm.data.licences.filter { it.companyId == c.id }
    ConsoleCard(padding = 14) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${mine.size} " + (if (mine.size == 1) "Computer Or Phone" else "Computers And Phones"), style = CardTitleStyle, modifier = Modifier.weight(1f))
            ConsoleButton("Show in Machines", { vm.companyFilter = c.id; nav.switchTo(Screen.Machines) }, small = true)
        }
        Small("computers and phones take no seat; revoke one to stop that machine")
        Spacer(Modifier.height(10.dp))
        if (mine.isEmpty()) Help("No computer or phone has joined yet.")
        mine.forEachIndexed { i, l ->
            if (i > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(cl.border))
                Spacer(Modifier.height(10.dp))
            }
            InstallationRow(l, vm, onAsk)
        }
    }
}

/* ---- a Fabric Stock customer with no Sales & Costing ---- */

@Composable
private fun NotUsingWeightCard(vm: ConsoleViewModel, nav: Navigator, f: FabricCompany, onAsk: (Ask) -> Unit) {
    ColourCard("Not using Sales & Costing", Icons.Outlined.Scale, "weight") {
        Help("${f.name} does not use Sales & Costing yet. It gets a licence of its own — its own key — made with this company's " +
            "name, GSTIN, email and mobile. Fabric Stock is not touched.")
        Spacer(Modifier.height(12.dp))
        ConsoleButton("Make licensed for 1 year", {
            onAsk(
                Ask.Confirm(
                    "Make ${f.name} a licensed Sales & Costing customer for 1 year?",
                    "It gets a Sales & Costing licence key of its own, on Pro; Fabric Stock is not touched.",
                    "Make licensed"
                ) { vm.startWeightFor(f) { k -> if (k != null) nav.replace(Screen.CustomerWin(k, Software.WEIGHT)) } }
            )
        }, kind = ButtonKind.Primary, small = true)
    }
}

/* --------------------------------------------------------- Fabric Stock */

private fun LazyListScope.fabricItems(
    vm: ConsoleViewModel,
    nav: Navigator,
    x: Customer,
    f: FabricCompany,
    sub: String,
    edit: FabricEdit?,
    onEdit: (FabricEdit) -> Unit,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    when (sub) {
        "licence" -> {
            item(key = "f-licence") { Box(page) { FabricLicenceCard(vm, f, edit, onEdit) } }
            item(key = "f-figs") {
                Box(page) {
                    val block = vm.plans?.fabric
                    FigGrid(
                        listOf(
                            Fig("Computers & phones", f.devices.toString(), "not counted against seats", 0),
                            Fig("People", f.people.toString(), "of ${f.seats} seats", 1),
                            Fig("Linked", if (x.w != null) (if (f.linkedBy == "gstin") "same GSTIN" else "by hand") else "Fabric Stock only",
                                if (x.w != null) "shown with its Sales & Costing company" else "not with a Sales & Costing company", 2),
                            Fig("Plans", if (block?.supported == true) "its own" else "Standard",
                                if (block?.supported == true) "made under Software & plans" else "Fabric Stock has no plans yet", 3)
                        )
                    )
                }
            }
        }
        /* 2.0.1 — its own features over its own plan (the tab is there only when Fabric Stock's service has plans) */
        "features" -> featureItems(
            "f", Features.viewOf(vm.plans, vm.data.settings, f), edit?.features, page
        ) { d -> edit?.let { onEdit(it.copy(features = d)) } }
        "people" -> {
            item(key = "f-signin") { Box(page) { FabricSignInCard(vm, f, onAsk) } }
            item(key = "f-people") { Box(page) { FabricPeopleCard(vm, f, onAsk) } }
        }
        "computers" -> item(key = "f-devices") { Box(page) { FabricDevicesCard(vm, f, onAsk) } }
        "payments" -> paymentItems(vm, nav, x, Software.FABRIC, page)
        "company" -> item(key = "f-company") { Box(page) { FabricCompanyCard(vm, f, x.w, onAsk) } }
    }
}

@Composable
private fun FabricLicenceCard(vm: ConsoleViewModel, f: FabricCompany, edit: FabricEdit?, onEdit: (FabricEdit) -> Unit) {
    val cl = LocalNexora.current
    val clipboard = LocalClipboardManager.current
    val s = f.shownState
    ConsoleCard(padding = 14) {
        if (f.endingSoon) {
            MessageStrip(Msg("The Fabric Stock licence ends ${Fmt.day(f.expiresAt)} — call them to renew.", Msg.Kind.WARN), onDismiss = {})
            Spacer(Modifier.height(12.dp))
        }
        val block = vm.plans?.fabric?.takeIf { it.supported }
        if (edit != null && block != null) {
            /* 2.0.1 — its own plans (Fabric Stock 0.8.1): the ones in use, and the one it is on even if retired */
            val current = Requests.fabricPlan(f)
            val offered = block.plans.filter { it.active || it.code == current }
            val options = offered.map { it.code to (it.name + if (!it.active) " (retired)" else "") }
                .ifEmpty { listOf(current to vm.planName(Software.FABRIC, current, f.planName)) }
            val chosen = block.plan(edit.plan)
            ChoiceField(
                "Plan", options, edit.plan,
                help = listOfNotNull(
                    chosen?.note,
                    chosen?.priceRenewal?.let { "renewal ${Money.rupees(it)} a year" },
                    if (f.isDemo) "a demo has every feature whatever its plan" else null
                ).joinToString(" · ").ifEmpty { null }
            ) { onEdit(edit.copy(plan = it)) }
            Spacer(Modifier.height(10.dp))
        } else {
            FieldPair(
                {
                    ReadField("Plan", vm.planName(Software.FABRIC, f.plan, f.planName) + Customers.own(f.featureOverrides.size) +
                        if (f.onDemo) " — a demo has everything" else "", it)
                },
                { ReadField("State", Customers.stateWord(s), it, valueColor = stateColour(s)) }
            )
            Spacer(Modifier.height(8.dp))
        }
        ReadField("Licence key", f.licenceKey, mono = true) {
            ConsoleButton("Copy", {
                clipboard.setText(AnnotatedString(f.licenceKey))
                vm.say("Copied ${f.licenceKey}", Msg.Kind.OK)
            }, small = true)
        }
        Spacer(Modifier.height(8.dp))
        FieldPair(
            { ReadField("Company id (sign-in)", f.loginId ?: "—", it) },
            {
                ReadField(if (f.onDemo) "Demo started" else "Licence started", Fmt.day(f.periodStartedAt ?: f.createdAt), it,
                    sub = if (f.periodDays > 0) "${f.periodDays}-day " + (if (f.onDemo) "demo" else "licence") else null)
            }
        )
        Spacer(Modifier.height(8.dp))
        val warn = if (f.endingSoon) cl.warn else null
        val ends: @Composable (Modifier) -> Unit = {
            ReadField(
                if (s == "EXPIRED") "Ended" else "Ends", Fmt.day(f.expiresAt), it, valueColor = warn,
                sub = if (s == "LICENSED" || s == "DEMO") (if (f.daysLeft == 0) "ends today" else "${f.daysLeft} days left") else Customers.stateWord(s),
                subColor = warn
            )
        }
        if (edit != null) {
            ends(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            FieldPair(
                { ConsoleField("Seats (people)", edit.seats, { v -> onEdit(edit.copy(seats = v.filter { ch -> ch.isDigit() })) }, it, numeric = true) },
                { ConsoleField("Offline days (0–30)", edit.graceDays, { v -> onEdit(edit.copy(graceDays = v.filter { ch -> ch.isDigit() })) }, it, numeric = true) }
            )
            Spacer(Modifier.height(6.dp))
            Help(
                "Fabric Stock's own " + (if (block != null) "plan, seats, offline days and features" else "seats and offline days") +
                    " — Sales & Costing's are counted apart. Save sends them to Fabric Stock in one update."
            )
        } else {
            FieldPair(ends) { ReadField("Seats (people)", "${f.people} of ${f.seats}", it) }
            Spacer(Modifier.height(8.dp))
            ReadField("Offline days", if (f.graceDays > 0) "${f.graceDays} days" else "none")
        }
    }
}

/* ------------------------------------------------------------ payments */

/** A customer's payments on one software, with Record payment. */
private fun LazyListScope.paymentItems(vm: ConsoleViewModel, nav: Navigator, x: Customer, sw: String, page: Modifier) {
    val list = vm.payments?.of(x, sw).orEmpty()
    item(key = "pay-head-$sw") {
        Box(page) {
            ConsoleCard(padding = 14) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Software.name(sw) + " Payments", style = CardTitleStyle)
                        Small("${list.size} · ${Money.rupees(PayFilter.sum(list))}")
                    }
                    ConsoleButton("+ Record payment", {
                        vm.payForm = vm.newPaymentForm(x.key, sw)
                        nav.open(Screen.RecordPayment)
                    }, kind = ButtonKind.Primary, small = true)
                }
                if (vm.paymentsLoad == Load.FAILED) {
                    Spacer(Modifier.height(8.dp))
                    Help(vm.paymentsError ?: "The payments could not be read.")
                } else if (list.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Help("No payment recorded for this software yet. Record payment keeps when it came, how much, for which plan, and can renew the licence in the same step.")
                }
            }
        }
    }
    list.forEach { p ->
        item(key = "pay-${p.id}") { Box(page) { PaymentRow(p, showCustomer = false) { nav.open(Screen.PaymentWin(p.id)) } } }
    }
}
