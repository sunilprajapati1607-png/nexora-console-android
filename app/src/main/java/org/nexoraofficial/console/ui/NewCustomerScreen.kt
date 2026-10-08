package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — NEW CUSTOMER: ONE CUSTOMER, ANY SOFTWARE (renderNewCust).

   Who it is (name, GSTIN, email, mobile) and, if wanted, its
   administrator and PIN; then each software on a row of its own with a
   tick — Sales & Costing on a plan the owner made, Fabric Stock as a demo
   or licensed, Jobwork coming. Save makes each one ticked in its own
   service, with its own licence key, and opens the customer's window.
   ====================================================================== */

@Composable
fun NewCustomerScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier) {
    val f = vm.newCustomer
    val wPlans = vm.plans?.weight?.livePlans.orEmpty()
    val fb = vm.plans?.fabric
    val fOk = vm.fabricReady
    val save = { vm.createCustomer { key -> if (key != null) nav.replace(Screen.CustomerWin(key)) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) {
                WindowHead("+", listOf(Color(0xFF10B981), Color(0xFF34D399)), "New Customer",
                    "One customer, any software — each gets its own licence key", "NEW")
            }
        }
        item(key = "tools") {
            Box(page) {
                ToolStrip(
                    listOf(
                        Tool("Save", Icons.Outlined.Save, ToolColours.green, !vm.busy && f.name.isNotBlank()) { save() },
                        Tool("Cancel", Icons.Outlined.Close, ToolColours.back) { nav.back() }
                    )
                )
            }
        }
        item(key = "who") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    CardHeading("The Customer")
                    ConsoleField("Company name", f.name, { vm.newCustomer = f.copy(name = it) })
                    Spacer(Modifier.height(8.dp))
                    ConsoleField("GSTIN", f.gstin, { vm.newCustomer = f.copy(gstin = it.uppercase().take(15)) }, placeholder = "15 characters")
                    Spacer(Modifier.height(8.dp))
                    FieldPair(
                        { ConsoleField("Email", f.email, { v -> vm.newCustomer = f.copy(email = v) }, it) },
                        { ConsoleField("Mobile", f.phone, { v -> vm.newCustomer = f.copy(phone = v) }, it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    FieldPair(
                        { ConsoleField("Administrator (optional)", f.admin, { v -> vm.newCustomer = f.copy(admin = v) }, it,
                            placeholder = "adds everyone else") },
                        { ConsoleField("Administrator PIN", f.pin, { v -> vm.newCustomer = f.copy(pin = v) }, it,
                            placeholder = "4 digits or more", password = true) }
                    )
                }
            }
        }
        item(key = "software") {
            Box(page) {
                ConsoleCard(padding = 14) {
                    CardHeading("Which Software, On Which Plan", "each one ticked gets its own licence key, made in its own service")

                    SoftwareChoice(Software.WEIGHT, Software.WEIGHT_NAME, f.weight, true, { vm.newCustomer = f.copy(weight = it) }) {
                        val options = if (wPlans.isNotEmpty()) wPlans.map { it.code to it.name } else listOf("STANDARD" to "Standard", "PRO" to "Pro")
                        val chosen = wPlans.find { it.code == f.weightPlan }
                        ChoiceField(
                            "Plan", options, f.weightPlan,
                            help = listOfNotNull(chosen?.note, chosen?.priceFirst?.let { Money.rupees(it) + " first year" } ?: "price open")
                                .joinToString(" · ")
                        ) { vm.newCustomer = f.copy(weightPlan = it) }
                        Spacer(Modifier.height(6.dp))
                        Small("Starts licensed")
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConsoleField("Days", f.weightDays, { vm.newCustomer = f.copy(weightDays = it.filter(Char::isDigit)) }, Modifier.weight(1f), numeric = true)
                            ConsoleField("Seats", f.weightSeats, { vm.newCustomer = f.copy(weightSeats = it.filter(Char::isDigit)) }, Modifier.weight(1f), numeric = true)
                            ConsoleField("Offline", f.weightGrace, { vm.newCustomer = f.copy(weightGrace = it.filter(Char::isDigit)) }, Modifier.weight(1f), numeric = true)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    SoftwareChoice(Software.FABRIC, Software.FABRIC_NAME, f.fabric && fOk, fOk, { vm.newCustomer = f.copy(fabric = it) }) {
                        if (fb?.supported == true && fb.livePlans.isNotEmpty()) {
                            ChoiceField("Plan", fb.livePlans.map { it.code to it.name }, f.fabricPlan ?: fb.livePlans.first().code) {
                                vm.newCustomer = f.copy(fabricPlan = it)
                            }
                        } else ReadField("Plan", "Standard", sub = "Fabric Stock has no plans of its own yet")
                        Spacer(Modifier.height(8.dp))
                        ChoiceField("Start as", listOf("DEMO" to "Demo", "LICENSED" to "Licensed"), f.fabricState) { s ->
                            vm.newCustomer = f.copy(
                                fabricState = s,
                                fabricDays = when {
                                    s == "LICENSED" && f.fabricDays == "7" -> "365"
                                    s == "DEMO" && f.fabricDays == "365" -> "7"
                                    else -> f.fabricDays
                                }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConsoleField("Days", f.fabricDays, { vm.newCustomer = f.copy(fabricDays = it.filter(Char::isDigit)) }, Modifier.weight(1f), numeric = true)
                            ConsoleField("Users", f.fabricSeats, { vm.newCustomer = f.copy(fabricSeats = it.filter(Char::isDigit)) }, Modifier.weight(1f), numeric = true)
                            ConsoleField("Offline", f.fabricGrace, { vm.newCustomer = f.copy(fabricGrace = it.filter(Char::isDigit)) }, Modifier.weight(1f), numeric = true)
                        }
                    }
                    if (!fOk) {
                        Spacer(Modifier.height(6.dp))
                        MessageStrip(Msg("Fabric Stock is not connected" + (vm.fabricProblem?.let { ": $it" } ?: " yet."), Msg.Kind.WARN), onDismiss = {})
                    }

                    Spacer(Modifier.height(10.dp))
                    SoftwareChoice(Software.JOBWORK, Software.JOBWORK_NAME + " — coming, no licence yet", false, false, {}) {}

                    Spacer(Modifier.height(10.dp))
                    Help(
                        "On Save each software ticked gets its own licence key, made in its own service; they are shown with " +
                            "each other as one customer. A plant that registers itself from the application appears here on its own, as a demo."
                    )
                }
            }
        }
        item(key = "buttons") {
            Box(page) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConsoleButton("Create the customer", save, kind = ButtonKind.Primary, enabled = !vm.busy && f.name.isNotBlank(),
                        modifier = Modifier.weight(1f), center = true)
                    ConsoleButton("Cancel", { nav.back() }, modifier = Modifier.weight(1f), center = true)
                }
            }
        }
    }
}

/** One software of a new customer: its tick and name, and — ticked — what it starts on. */
@Composable
private fun SoftwareChoice(
    sw: String,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheck: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalNexora.current
    val g = SwColours.of(sw)
    Column(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).clip(RoundedCornerShape(14.dp))
            .background(if (checked) g[0].copy(alpha = if (c.isDark) 0.14f else 0.06f) else c.surface)
            .border(1.dp, if (checked) g[0].copy(alpha = 0.5f) else c.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = if (enabled) onCheck else null, enabled = enabled)
            SwDot(sw, 10)
            Spacer(Modifier.width(8.dp))
            Text(title, color = c.text, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold)
        }
        if (checked) {
            Column(Modifier.padding(start = 6.dp, end = 4.dp, bottom = 8.dp), content = content)
        }
    }
}
