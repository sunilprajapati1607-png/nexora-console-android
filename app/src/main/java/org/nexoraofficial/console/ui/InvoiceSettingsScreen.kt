package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Billing
import org.nexoraofficial.console.data.GstStates
import org.nexoraofficial.console.data.InvFmt
import org.nexoraofficial.console.data.InvoiceSettings
import org.nexoraofficial.console.data.Refused
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.3.0 — INVOICE SETTINGS (the desktop console 1.4's invoice-settings.js):
   your company as it is printed on every invoice — name, address, GSTIN,
   PAN, state, SAC and GST rate, bank & UPI, numbering, terms, footer,
   signatory — filled once. It is your legal identity on paper, so Save asks
   first (the console has no second lock of its own for one action; the
   phone's lock already stands in front of the whole console). Invoices
   already made keep the details they were made with.
   ====================================================================== */

@Composable
fun InvoiceSettingsScreen(vm: ConsoleViewModel, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    LaunchedEffect(Unit) { if (vm.invSettings == null && vm.invSettingsLoad != Load.LOADING) vm.loadInvoiceSettings() }
    val s = vm.invSettingsForm ?: vm.invSettings?.settings ?: InvoiceSettings()
    val set = { n: InvoiceSettings -> vm.invSettingsForm = n }
    val fy = InvFmt.fy(vm.today())
    val save = {
        try {
            Billing.cleanSettings(s)
            onAsk(Ask.Confirm(
                "Save Invoice Settings?",
                "This is your legal identity on paper — every new invoice and receipt carries it. Invoices already made keep the details they were made with.",
                "Save"
            ) { vm.saveInvoiceSettings(s) })
        } catch (e: Refused) {
            vm.say(e.message ?: "Check the settings.", Msg.Kind.ERR)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) { WindowHead("✍", listOf(ToolColours.violet, ToolColours.blue), "Invoice Settings", "Your company as it is printed on every invoice — fill it once", "EDIT") }
        }
        item(key = "tools") {
            Box(page) {
                ToolStrip(listOf(
                    Tool("Save", Icons.Outlined.Save, ToolColours.green, !vm.busy && vm.invSettings != null) { save() },
                    Tool("Read again", Icons.Outlined.Refresh, ToolColours.back) { vm.invSettingsForm = null; vm.loadInvoiceSettings() }
                ))
            }
        }
        when {
            vm.invSettingsLoad == Load.FAILED && vm.invSettings == null ->
                item(key = "failed") { Box(page) { EmptyCard("Invoice Settings: " + (vm.invSettingsError ?: "they could not be read.")) } }
            vm.invSettings == null -> item(key = "reading") { Box(page) { EmptyCard("Reading Invoice Settings…") } }
            else -> {
                if (vm.invSettings?.ready != true) item(key = "not-ready") {
                    Box(page) {
                        Why("Not filled yet. Invoices can be made once your company name, address and (for GST invoices) GSTIN are saved here.")
                    }
                }
                item(key = "company") {
                    Box(page) {
                        Group("Your company") {
                            ConsoleField("Name on the invoice", s.sellerName, { set(s.copy(sellerName = it)) }, placeholder = "Trade name")
                            Gap(); ConsoleField("Legal name", s.legalName, { set(s.copy(legalName = it)) }, supporting = "if different")
                            Gap(); ConsoleField("Address", s.address, { set(s.copy(address = it)) }, singleLine = false)
                            Gap(); FieldPair({ ConsoleField("Phone", s.phone, { v -> set(s.copy(phone = v)) }, it) }, { ConsoleField("E-mail", s.email, { v -> set(s.copy(email = v)) }, it) })
                            Gap(); ConsoleField("Website", s.website, { set(s.copy(website = it)) })
                            Gap(); ConsoleField("Signatory", s.signatory, { set(s.copy(signatory = it)) }, supporting = "the name under the signature")
                        }
                    }
                }
                item(key = "gst") {
                    Box(page) {
                        Group("GST") {
                            ConsoleField("GSTIN", s.gstin, { v -> set(withGstin(s, v)) }, placeholder = "15 letters and digits")
                            Gap(); ConsoleField("PAN", s.pan, { set(s.copy(pan = it.uppercase())) })
                            Gap(); StateField("State", s.stateCode, onAsk) { code -> set(s.copy(stateCode = code, stateName = GstStates.name(code))) }
                            Gap(); FieldPair(
                                { ConsoleField("SAC (services)", s.sac, { v -> set(s.copy(sac = v.filter { ch -> ch.isDigit() })) }, it, numeric = true, supporting = "997331 = software licence") },
                                { ConsoleField("GST rate %", s.gstRate, { v -> set(s.copy(gstRate = v.filter { ch -> ch.isDigit() || ch == '.' })) }, it, numeric = true) }
                            )
                            Gap(); ReadField("Tax split", "Same state → CGST + SGST · other state → IGST")
                        }
                    }
                }
                item(key = "bank") {
                    Box(page) {
                        Group("Bank & UPI") {
                            ConsoleField("Bank", s.bankName, { set(s.copy(bankName = it)) })
                            Gap(); ConsoleField("Account name", s.accountName, { set(s.copy(accountName = it)) })
                            Gap(); FieldPair(
                                { ConsoleField("Account number", s.accountNumber, { v -> set(s.copy(accountNumber = v)) }, it, numeric = true) },
                                { ConsoleField("IFSC", s.ifsc, { v -> set(s.copy(ifsc = v.uppercase())) }, it) }
                            )
                            Gap(); ConsoleField("UPI ID", s.upi, { set(s.copy(upi = it)) })
                        }
                    }
                }
                item(key = "numbers") {
                    Box(page) {
                        Group("Numbering") {
                            ConsoleField("With GST", s.prefixGst, { set(s.copy(prefixGst = it.uppercase())) }, supporting = "${s.prefixGst}/$fy/0001")
                            Gap(); ConsoleField("Without GST", s.prefixPlain, { set(s.copy(prefixPlain = it.uppercase())) }, supporting = "${s.prefixPlain}/$fy/0001")
                            Gap(); ConsoleField("Receipts", s.prefixReceipt, { set(s.copy(prefixReceipt = it.uppercase())) }, supporting = "${s.prefixReceipt}/$fy/0001")
                            Gap(); Help("Each series counts from 0001 again every financial year (April). The service gives the numbers; a cancelled invoice keeps its number.")
                        }
                    }
                }
                item(key = "terms") {
                    Box(page) {
                        Group("Terms & footer") {
                            ConsoleField("Terms", s.terms, { set(s.copy(terms = it)) }, singleLine = false)
                            Gap(); ConsoleField("Footer", s.footer, { set(s.copy(footer = it)) }, singleLine = false, supporting = "a line at the bottom of every page")
                        }
                    }
                }
                item(key = "save") {
                    Box(page) {
                        Column {
                            Help("Saving asks first: this is your legal identity on paper.", Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            Spacer(Modifier.height(8.dp))
                            ConsoleButton("Save Invoice Settings", save, kind = ButtonKind.Primary, enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), center = true)
                        }
                    }
                }
            }
        }
    }
}

/** A GSTIN typed: upper case, and — when it is a whole one — its state and (if empty) the PAN inside it. */
internal fun withGstin(s: InvoiceSettings, typed: String): InvoiceSettings {
    val g = typed.uppercase().filter { it.isLetterOrDigit() }.take(15)
    val st = GstStates.ofGstin(g) ?: return s.copy(gstin = g)
    return s.copy(gstin = g, stateCode = st, stateName = GstStates.name(st), pan = s.pan.ifBlank { g.substring(2, 12) })
}

@Composable
private fun Gap() = Spacer(Modifier.height(8.dp))

/** A card of fields under a heading. */
@Composable
internal fun Group(title: String, content: @Composable () -> Unit) {
    ConsoleCard(padding = 14) {
        Text(title, style = CardTitleStyle)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** The state, chosen from the list (by its GST code). */
@Composable
internal fun StateField(label: String, code: String, onAsk: (Ask) -> Unit, onPick: (String) -> Unit) {
    val c = LocalNexora.current
    val codes = GstStates.ALL.keys.toList()
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        Row(
            Modifier.fillMaxWidth().background(c.surface, MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .pressable {
                    onAsk(Ask.Pick(
                        title = label, body = "The state, by its GST code.",
                        options = codes.mapIndexed { i, k -> Ask.Pick.Option(i, GstStates.name(k), k) },
                        confirmText = "Choose", onPick = { i -> codes.getOrNull(i)?.let(onPick) }
                    ))
                }
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(if (code.isBlank()) "— State —" else code + " " + GstStates.name(code), color = if (code.isBlank()) c.faint else c.text,
                fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("Change", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
