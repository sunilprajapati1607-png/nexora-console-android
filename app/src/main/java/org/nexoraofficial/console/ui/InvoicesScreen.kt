package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.nexoraofficial.console.data.InvFilter
import org.nexoraofficial.console.data.InvFmt
import org.nexoraofficial.console.data.Invoice
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ======================================================================
   2.3.0 — INVOICES: every tax invoice, bill and receipt the console service
   has made (the desktop console's invoices-list.js), newest first — by
   software, with / without GST / receipts, month and status, and a search.
   A row opens the invoice. Invoices are made from a payment or from a
   customer's software tab (Make invoice).
   ====================================================================== */

@Composable
fun InvoicesScreen(vm: ConsoleViewModel, nav: Navigator, gutter: PaddingValues, page: Modifier) {
    LaunchedEffect(Unit) { if (vm.invoicesLoad == Load.IDLE) vm.loadInvoices() }
    val all = vm.invoices.orEmpty()
    val rows = vm.invoicesShown
    val live = rows.filter { !it.cancelled }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search") { Box(page) { SearchBox(vm.invQuery, { vm.invQuery = it }, "Number, customer, GSTIN…") } }
        item(key = "filters") {
            Box(page) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickRow(listOf(Quick("ANY", "Any software")) + Software.LICENSED.map { Quick(it, Software.name(it)) }, vm.invSoft ?: "ANY") {
                        vm.invSoft = it.takeIf { s -> s != "ANY" }
                    }
                    QuickRow(InvFilter.DOCS.map { Quick(it.first, it.second) }, vm.invDoc) { vm.invDoc = it }
                    QuickRow(InvFilter.STATES.map { Quick(it.first, it.second) }, vm.invStatus) { vm.invStatus = it }
                    val months = InvFilter.months(all)
                    if (months.isNotEmpty()) {
                        QuickRow(listOf(Quick("ALL", "Every month")) + months.map { Quick(it, monthName(it)) }, vm.invMonth ?: "ALL") {
                            vm.invMonth = it.takeIf { m -> m != "ALL" }
                        }
                    }
                }
            }
        }
        if (vm.invoicesLoad == Load.FAILED) {
            item(key = "failed") { Box(page) { EmptyCard("Invoices: " + (vm.invoicesError ?: "they could not be read.")) } }
        } else if (vm.invoices == null && vm.invoicesLoad == Load.LOADING) {
            item(key = "reading") { Box(page) { EmptyCard("Reading invoices…") } }
        }
        item(key = "figs") {
            Box(page) {
                FigGrid(listOf(
                    Fig("In this view", rows.size.toString(), counted(rows.size, "document"), 0),
                    Fig("Issued", "₹ " + InvFmt.f2(live.sumOf { it.total ?: 0.0 }), "not cancelled", 1),
                    Fig("With GST", rows.count { it.gst }.toString(), "tax invoices and receipts", 2),
                    Fig("Cancelled", (rows.size - live.size).toString(), "numbers kept", 3)
                ))
            }
        }
        item(key = "heading") { Box(page) { ListHeading(counted(rows.size, "invoice"), "tap a row to open it") } }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Box(page) {
                    EmptyCard("No invoice in this view. Make one from a payment (open the payment → Invoice) or from a customer’s software tab (Make invoice).")
                }
            }
        }
        items(rows, key = { "i-${it.id}" }) { inv -> Box(page) { InvoiceRow(inv) { nav.open(Screen.InvoiceWin(inv.id)) } } }
    }
}

private fun monthName(ym: String): String =
    runCatching { DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).format(YearMonth.parse(ym)) }.getOrDefault(ym)

/** One invoice in a list: its number and total, the customer and software, the date, what it is, GST or not, and its state. */
@Composable
fun InvoiceRow(inv: Invoice, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val sw = inv.software ?: Software.WEIGHT
    RecordRow(if (inv.cancelled) c.faint else SwColours.of(sw)[0], onOpen) {
        RowTitle(inv.number, right = "₹ " + InvFmt.f2(inv.total), rightColor = if (inv.cancelled) c.faint else null)
        SwLine(sw, inv.customer.name.ifBlank { "—" } + " · " + Software.name(sw), faint = inv.cancelled)
        Spacer(Modifier.height(3.dp))
        Small(listOfNotNull(InvFmt.date(inv.date), inv.kindWord, if (inv.paymentId != null) "from a payment" else "service").joinToString(" · "), color = c.text)
        Spacer(Modifier.height(5.dp))
        InvoiceTags(inv)
    }
}

/** Issued / Cancelled, With GST (and its split) / Without GST. */
@Composable
fun InvoiceTags(inv: Invoice) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(if (inv.cancelled) "Cancelled" else "Issued", if (inv.cancelled) "REVOKED" else "LICENSED")
        Pill(if (inv.gst) "With GST" else "Without GST", if (inv.gst) "DEMO" else "")
        inv.taxSplit?.let { Text(it, color = LocalNexora.current.muted, fontSize = 11.5f.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** "₹1,000" when a figure is shown as a price; kept here so the rows and the sheet agree. */
internal fun rupees2(n: Double?): String = if (n == null) "—" else "₹ " + InvFmt.f2(n)

