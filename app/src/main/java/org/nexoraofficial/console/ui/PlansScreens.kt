package org.nexoraofficial.console.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.FeatureDef
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.Plan
import org.nexoraofficial.console.data.PlanForm
import org.nexoraofficial.console.data.Plans
import org.nexoraofficial.console.data.Requests
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.groupsOf
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — SOFTWARE & PLANS: EVERY SOFTWARE ITS OWN.

   "plan pan hu create kri saku darek software wise" — the owner makes the
   plans, per software, each with a name, what it gives, its prices (open
   until filled: "price open rakho"), the users it includes and its feature
   ticks. Standard and Pro can be retired, never deleted; a plan with
   customers on it cannot be deleted either. Fabric Stock has no plans of
   its own yet, and the service says so in its own words.

   2.0.1 — Fabric Stock's own service (0.8.1) has plans: they are listed,
   opened, made, edited, retired and deleted here exactly as Sales &
   Costing's (POST /admin/api/plans with "software":"fabric"); its
   features are grouped in the order its service sends them.
   ====================================================================== */

@Composable
fun PlansScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier) {
    val data = vm.plans
    val wb = data?.weight
    val fb = data?.fabric
    val t = vm.planQuery.lowercase().trim()
    val rows = data?.all.orEmpty().filter { (sw, p) ->
        (vm.planSoft == null || vm.planSoft == sw) &&
            (t.isEmpty() || listOf(p.name, p.code, p.note).any { it?.lowercase()?.contains(t) == true } ||
                Plans.catalogue(data, sw).any { p.gives(it.id) && it.label.lowercase().contains(t) })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search") { Box(page) { SearchBox(vm.planQuery, { vm.planQuery = it }, "Plan or feature…") } }
        item(key = "quick") {
            Box(page) {
                QuickRow(
                    listOf(
                        Quick("ANY", "Every software"),
                        Quick(Software.WEIGHT, Software.WEIGHT_NAME, data?.plansOf(Software.WEIGHT)?.size),
                        Quick(Software.FABRIC, Software.FABRIC_NAME, if (fb?.supported == true) fb.plans.size else null)
                    ),
                    vm.planSoft ?: "ANY"
                ) { vm.planSoft = it.takeIf { s -> s != "ANY" } }
            }
        }
        item(key = "new") {
            Box(page) {
                WrapRow {
                    ConsoleButton("+ New Sales & Costing plan", { nav.open(Screen.PlanWin(Software.WEIGHT, null)) }, small = true,
                        enabled = wb?.supported == true)
                    ConsoleButton("+ New Fabric Stock plan", { nav.open(Screen.PlanWin(Software.FABRIC, null)) }, small = true,
                        enabled = fb?.supported == true)
                }
            }
        }
        item(key = "figs") {
            Box(page) {
                val wPlans = data?.plansOf(Software.WEIGHT).orEmpty()
                val fPlans = data?.plansOf(Software.FABRIC).orEmpty()
                FigGrid(
                    listOf(
                        Fig(Software.WEIGHT_NAME, if (wb != null) "${wPlans.size} plans" else "—",
                            if (wb != null) "${wb.features.size} features · ${wPlans.sumOf { it.customers }} paying customers" else "reading…", 0) {
                            vm.planSoft = Software.WEIGHT
                        },
                        Fig(Software.FABRIC_NAME, if (fb?.supported == true) "${fb.plans.size} plans" else "—",
                            when {
                                fb == null -> if (vm.plansLoad == Load.FAILED) "not read" else "reading…"
                                fb.supported -> "${fb.features.size} features · ${fPlans.sumOf { it.customers }} paying customers"
                                fb.ok -> "no plans in its service yet"
                                else -> "not connected"
                            }, 1) { vm.planSoft = Software.FABRIC },
                        Fig(Software.JOBWORK_NAME, "—", "joins when it has a licence", 2),
                        /* 2.0.1 — every software's: Fabric Stock's customers have features of their own too */
                        Fig("Customer changes", (wPlans + fPlans).sumOf { it.changed }.toString(), "customers with a feature added or off", 3)
                    )
                )
            }
        }
        if (vm.plansLoad == Load.FAILED) {
            item(key = "failed") { Box(page) { EmptyCard(vm.plansError ?: "The plans could not be read.") } }
        } else if (data == null) {
            item(key = "reading") { Box(page) { EmptyCard("Reading the plans…") } }
        }
        if ((vm.planSoft == null || vm.planSoft == Software.FABRIC) && fb != null && !fb.supported) {
            item(key = "fabric-note") {
                Box(page) { MessageStrip(Msg("Fabric Stock: " + fb.problem, Msg.Kind.WARN), onDismiss = {}) }
            }
        }
        item(key = "heading") {
            Box(page) { ListHeading("${rows.size} plan" + (if (rows.size == 1) "" else "s"), "each software its own — never mixed") }
        }
        if (rows.isEmpty() && data != null) {
            item(key = "empty") { Box(page) { EmptyCard("No plan here yet. New plan makes one.") } }
        }
        items(rows, key = { (sw, p) -> "pl-$sw-${p.code}" }) { (sw, p) ->
            Box(page) { PlanRow(sw, p, Plans.catalogue(data, sw)) { nav.open(Screen.PlanWin(sw, p.code)) } }
        }
        item(key = "help") {
            Box(page) {
                Help(
                    "A price left empty is not set yet. Prices are for this console and the renewals; a plan's ticks decide what " +
                        "the application lets the plant do, at its next check. A demo has every feature, whatever its plan. A " +
                        "customer's own changes (on the customer, Features) lie over the plan.",
                    Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/** One plan in a list: its name and code, what it gives, its prices (or open), its users, its features and its customers. */
@Composable
fun PlanRow(sw: String, p: Plan, catalogue: List<FeatureDef>, onOpen: () -> Unit) {
    val c = LocalNexora.current
    RecordRow(if (p.active) SwColours.of(sw)[0] else c.faint, onOpen) {
        RowTitle(p.name, right = if (p.active) "in use" else "retired", rightColor = if (p.active) c.ok else c.faint)
        SwLine(sw, Software.name(sw) + " · " + p.code)
        p.note?.let { Small(it, color = c.text) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Mini("First year", Money.price(p.priceFirst))
            Mini("Renewal", Money.price(p.priceRenewal))
            Mini("Users", p.usersIncluded?.toString() ?: "open")
            Mini("Extra user", Money.price(p.extraUserPrice))
        }
        Spacer(Modifier.height(6.dp))
        Small("${p.onCount(catalogue)} of ${catalogue.size} features · ${p.customers} customer" + (if (p.customers == 1) "" else "s") +
            (if (p.changed > 0) " · ${p.changed} changed" else ""))
    }
}

/* ------------------------------------------------------------ one plan */

@Composable
fun PlanWindow(vm: ConsoleViewModel, nav: Navigator, screen: Screen.PlanWin, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    val sw = screen.sw
    val block = vm.plans?.block(sw)
    val p = screen.code?.let { block?.plan(it) }
    if (block == null || !block.supported || (screen.code != null && p == null)) {
        if (block != null && block.supported && vm.plansLoad != Load.LOADING) {
            /* deleted while it was open */
            LaunchedEffect(Unit) { nav.back() }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = gutter, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Box(page) { WindowHead("◫", SwColours.of(sw), Software.name(sw) + " · Plans", "each software its own plans", "DISPLAY") } }
            item {
                Box(page) {
                    EmptyCard(
                        when {
                            block != null -> Software.name(sw) + ": " + block.problem
                            vm.plansLoad == Load.FAILED -> vm.plansError ?: "The plans could not be read."
                            else -> "Reading the plans…"
                        }
                    )
                }
            }
        }
        return
    }
    PlanBody(vm, nav, screen, p, gutter, page, onAsk)
}

@Composable
private fun PlanBody(
    vm: ConsoleViewModel,
    nav: Navigator,
    screen: Screen.PlanWin,
    p: Plan?,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val c = LocalNexora.current
    val sw = screen.sw
    val isNew = p == null
    var editing by rememberSaveable(sw, screen.code, screen.copyFrom) { mutableStateOf(isNew) }
    var form by remember(sw, screen.code, screen.copyFrom) {
        mutableStateOf(
            when {
                p != null -> PlanForm.of(p)
                screen.copyFrom != null -> vm.plans?.plan(sw, screen.copyFrom)?.let { PlanForm.copyOf(it) } ?: PlanForm()
                else -> PlanForm()
            }
        )
    }
    var tab by rememberSaveable(sw, screen.code) { mutableStateOf("features") }
    val catalogue = Plans.catalogue(vm.plans, sw)
    val plans = vm.plans?.plansOf(sw).orEmpty()

    DisposableEffect(editing) {
        nav.guard = if (editing) ({
            onAsk(Ask.Confirm("Close without saving?", "What was changed here is not saved.", "Close", danger = true) {
                editing = false; nav.back()
            })
        }) else null
        onDispose { nav.guard = null }
    }
    BackHandler(enabled = editing) { nav.requestBack() }

    /* the paying customers on it — a demo has every feature whatever its plan, and neither service counts it */
    val onIt = if (p == null) emptyList() else vm.customers.filter { x ->
        if (sw == Software.WEIGHT) x.w != null && !x.w.isDemo && x.w.plan.uppercase() == p.code
        else x.f != null && !x.f.isDemo && Requests.fabricPlan(x.f) == p.code
    }

    val save = {
        vm.savePlan(sw, p, form) { saved ->
            if (saved != null) {
                editing = false
                if (p == null) nav.replace(Screen.PlanWin(sw, saved.code))
                else form = PlanForm.of(saved)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) {
                WindowHead(
                    p?.let { initials(it.name) } ?: "+", SwColours.of(sw), Software.name(sw) + " · " + (p?.name ?: "New Plan"),
                    if (p != null) "Plan · ${p.customers} customer" + (if (p.customers == 1) "" else "s") + " on it"
                    else "A name, a price (or leave it open), how many users, and which features it gives",
                    if (isNew) "NEW" else if (editing) "EDIT" else "DISPLAY"
                )
            }
        }
        item(key = "tools") {
            Box(page) {
                val tools = ArrayList<Tool>()
                if (editing) {
                    tools += Tool("Save", Icons.Outlined.Save, ToolColours.green, !vm.busy) { save() }
                    tools += Tool("Cancel", Icons.Outlined.Close, ToolColours.back) {
                        if (p == null) nav.back() else { form = PlanForm.of(p); editing = false }
                    }
                } else {
                    tools += Tool("Edit", Icons.Outlined.Edit, ToolColours.blue) { editing = true }
                }
                if (p != null) {
                    tools += Tool("Duplicate", Icons.Outlined.ContentCopy, ToolColours.blue, !editing) {
                        nav.open(Screen.PlanWin(sw, null, copyFrom = p.code))
                    }
                    tools += if (!p.active) Tool("Restore", Icons.Outlined.PlayCircle, ToolColours.green, !editing) { vm.planDo(sw, "restore", p) }
                    else Tool("Retire", Icons.Outlined.Block, ToolColours.red, !editing) {
                        onAsk(Ask.Confirm("Retire ${p.name}?",
                            "It is no longer offered to a new customer; the ${p.customers} on it keep it. Restore offers it again.",
                            "Retire", danger = true) { vm.planDo(sw, "retire", p) })
                    }
                    tools += Tool("Delete", Icons.Outlined.Delete, ToolColours.red, p.canDelete && !editing) {
                        onAsk(Ask.Confirm("Delete the plan ${p.name}?", "Nobody is on it. It cannot be undone; retiring keeps it.",
                            "Delete", danger = true) { vm.planDo(sw, "delete", p) { ok -> if (ok) nav.back() } })
                    }
                }
                ToolStrip(tools)
            }
        }
        item(key = "fields") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    if (editing) {
                        ConsoleField("Plan name", form.name, { form = form.copy(name = it.take(40)) })
                        Spacer(Modifier.height(8.dp))
                        ConsoleField("Gives (a few words)", form.note, { form = form.copy(note = it.take(120)) },
                            placeholder = if (sw == Software.FABRIC) "e.g. loom reading, stock and dispatch"
                                          else "e.g. calculation, quotation and the cost tools")
                        Spacer(Modifier.height(8.dp))
                        val money = { s: String -> s.filter { ch -> ch.isDigit() || ch == '.' } }
                        FieldPair(
                            { ConsoleField("First year (₹)", form.priceFirst, { v -> form = form.copy(priceFirst = money(v)) }, it, placeholder = "open", numeric = true) },
                            { ConsoleField("Renewal / year (₹)", form.priceRenewal, { v -> form = form.copy(priceRenewal = money(v)) }, it, placeholder = "open", numeric = true) }
                        )
                        Spacer(Modifier.height(8.dp))
                        FieldPair(
                            { ConsoleField("Users included", form.usersIncluded, { v -> form = form.copy(usersIncluded = v.filter { ch -> ch.isDigit() }) }, it, placeholder = "open", numeric = true) },
                            { ConsoleField("Extra user / year (₹)", form.extraUserPrice, { v -> form = form.copy(extraUserPrice = money(v)) }, it, placeholder = "open", numeric = true) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Small("Prices are before GST. A price left empty stays open — not set — until you fill it.")
                        Spacer(Modifier.height(10.dp))
                        if (isNew) {
                            ChoiceField(
                                "Start from the ticks of",
                                listOf("" to "nothing ticked") + plans.map { it.code to it.name },
                                form.copyFrom
                            ) { code ->
                                val from = plans.find { it.code == code }
                                form = form.copy(copyFrom = code, features = from?.features ?: emptyMap())
                            }
                        } else FieldPair({ ReadField("Code", p!!.code, it) }, { ReadField("Software", Software.name(sw), it) })
                    } else {
                        p!!
                        FieldPair({ ReadField("Plan name", p.name, it) }, { ReadField("Gives", p.note ?: "—", it) })
                        Spacer(Modifier.height(8.dp))
                        FieldPair(
                            { ReadField("First year", p.priceFirst?.let { Money.rupees(it) + " + GST" } ?: "open — not set", it) },
                            { ReadField("Renewal / year", p.priceRenewal?.let { Money.rupees(it) + " + GST" } ?: "open — not set", it) }
                        )
                        Spacer(Modifier.height(8.dp))
                        FieldPair(
                            { ReadField("Users included", p.usersIncluded?.toString() ?: "open", it) },
                            { ReadField("Each extra user / year", p.extraUserPrice?.let { Money.rupees(it) } ?: "open", it) }
                        )
                        Spacer(Modifier.height(8.dp))
                        FieldPair(
                            { ReadField("Customers on it", "${p.customers}" + (if (p.changed > 0) " · ${p.changed} with their own changes" else ""), it) },
                            { ReadField("State", if (p.active) "in use" else "retired — not offered to new customers", it,
                                valueColor = if (p.active) c.ok else c.muted) }
                        )
                        Spacer(Modifier.height(8.dp))
                        FieldPair({ ReadField("Code", p.code, it) }, { ReadField("Software", Software.name(sw), it) })
                    }
                }
            }
        }
        if (p != null) {
            item(key = "tabs") {
                Box(page) {
                    SubTabs(
                        listOf("features" to "Features", "customers" to "Customers"), tab,
                        mapOf("features" to catalogue.count { form.features[it.id] == true }.toString(), "customers" to onIt.size.toString())
                    ) { tab = it }
                }
            }
        }
        if (p == null || tab == "features") {
            item(key = "features") {
                Box(page) {
                    ConsoleCard(padding = 14) {
                        val on = catalogue.count { form.features[it.id] == true }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$on of ${catalogue.size}", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(" features", color = c.text, fontSize = 14.sp)
                        }
                        Small((if (editing) "Tap to tick or untick. " else "") +
                            "A tick changes every customer on this plan at their next check; a customer's own additions and removals stay.")
                        Spacer(Modifier.height(10.dp))
                        groupsOf(catalogue, sw).forEachIndexed { gi, g ->
                            if (gi > 0) Spacer(Modifier.height(12.dp))
                            Text(g.uppercase(), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                            Spacer(Modifier.height(6.dp))
                            catalogue.filter { it.group == g }.forEach { fe ->
                                FeatureRow(fe.label, null, form.features[fe.id] == true, editing) {
                                    form = form.copy(features = form.features + (fe.id to (form.features[fe.id] != true)))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "customers") {
                Box(page) {
                    ConsoleCard(padding = 14) {
                        if (onIt.isEmpty()) Help("No paying customer is on this plan.")
                        onIt.forEachIndexed { i, x ->
                            if (i > 0) DashedRule(Modifier.padding(vertical = 8.dp))
                            val st = if (sw == Software.WEIGHT) x.w!!.swState else x.f!!.shownState
                            val ends = if (sw == Software.WEIGHT) x.w!!.expiresAt else x.f!!.expiresAt
                            val own = Customers.ownCount(x, sw)
                            Row(Modifier.fillMaxWidth().pressable { nav.open(Screen.CustomerWin(x.key, sw, "licence")) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                    Text(x.name, color = c.text, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold)
                                    Small("ends " + Fmt.day(ends) + (if (own > 0) " · ± $own of its own" else ""))
                                }
                                Text(Customers.stateWord(st), color = stateColour(st), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (editing) {
            item(key = "buttons") {
                Box(page) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConsoleButton(if (isNew) "Make the plan" else "Save", save, kind = ButtonKind.Primary,
                            enabled = !vm.busy && form.name.isNotBlank(), modifier = Modifier.weight(1f), center = true)
                        ConsoleButton("Cancel", { if (p == null) nav.back() else { form = PlanForm.of(p); editing = false } },
                            modifier = Modifier.weight(1f), center = true)
                    }
                }
            }
        }
    }
}
