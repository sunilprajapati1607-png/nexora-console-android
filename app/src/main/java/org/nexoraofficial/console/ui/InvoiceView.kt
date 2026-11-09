package org.nexoraofficial.console.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Load
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.R
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.InvFmt
import org.nexoraofficial.console.data.Invoice
import org.nexoraofficial.console.data.InvoicePdf
import org.nexoraofficial.console.data.Software
import java.io.File

/* ======================================================================
   2.3.0 — ONE INVOICE (the desktop console 1.4's invoice-view.js): the
   sheet as the service keeps it — every figure the service's — with Share
   PDF (the phone's share sheet), Save PDF (the phone's own save-as, named by
   its number with "/" made "-") and Cancel invoice (asks first; a reason is
   required and printed; the number stays used). Never edited or deleted.
   ====================================================================== */

@Composable
fun InvoiceWindow(vm: ConsoleViewModel, nav: Navigator, id: String, gutter: PaddingValues, page: Modifier, onAsk: (Ask) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inv = vm.invoiceById(id)
    if (inv == null) {
        LaunchedEffect(Unit) { if (vm.invoicesLoad == Load.IDLE) vm.loadInvoices() }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = gutter, horizontalAlignment = Alignment.CenterHorizontally) {
            item { Box(page) { EmptyCard(if (vm.invoicesLoad == Load.LOADING) "Reading invoices…" else "That invoice is no longer in the list.") } }
        }
        return
    }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val bytes = pending
        pending = null
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                vm.say("Saved ${InvFmt.fileName(inv)} to the phone.", Msg.Kind.OK)
            } catch (e: Exception) {
                vm.say("Could not save it: ${e.message ?: "unknown"}", Msg.Kind.ERR)
            }
        }
    }
    /* the PDF is drawn off the main thread, then handed on */
    val withPdf = { then: (ByteArray) -> Unit ->
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { InvoicePdf.bytes(inv, logoOf(context)) }
                then(bytes)
            } catch (e: Exception) {
                vm.say("Could not make the PDF: ${e.message ?: "unknown"}", Msg.Kind.ERR)
            }
        }
    }
    val owner = vm.customers.find { x ->
        (inv.companyId != null && x.w?.id == inv.companyId && (inv.software == null || inv.software == Software.WEIGHT)) ||
            (inv.remoteId != null && inv.software != null && x.remote(inv.software)?.id == inv.remoteId)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "head") {
            Box(page) {
                WindowHead("▤", if (inv.cancelled) listOf(ToolColours.back, ToolColours.back) else listOf(ToolColours.blue, ToolColours.violet),
                    inv.title + " " + inv.number, inv.customer.name.ifBlank { "—" } + " · " + InvFmt.date(inv.date) + " · ₹ " + InvFmt.f2(inv.total),
                    if (inv.cancelled) "CANCELLED" else "DISPLAY")
            }
        }
        item(key = "tools") {
            Box(page) {
                val t = ArrayList<Tool>()
                t += Tool("Share PDF", Icons.Outlined.Share, ToolColours.blue) { withPdf { b -> sharePdf(context, inv, b, vm) } }
                t += Tool("Save PDF", Icons.Outlined.SaveAlt, ToolColours.green) { withPdf { b -> pending = b; saveAs.launch(InvFmt.fileName(inv)) } }
                if (!inv.cancelled) t += Tool("Cancel invoice", Icons.Outlined.Block, ToolColours.red, !vm.busy) { onAsk(cancelQuestion(vm, inv)) }
                inv.paymentId?.let { pid -> t += Tool("Payment", Icons.Outlined.Payments, ToolColours.teal, vm.paymentById(pid) != null) { nav.open(Screen.PaymentWin(pid)) } }
                t += Tool("Customer", Icons.Outlined.Person, ToolColours.orange, owner != null) {
                    owner?.let { nav.open(Screen.CustomerWin(it.key, inv.software)) }
                }
                ToolStrip(t)
            }
        }
        item(key = "meta") {
            Box(page) {
                ConsoleCard(padding = 12) {
                    InvoiceTags(inv)
                    Spacer(Modifier.height(6.dp))
                    Small(inv.kindWord + " · made " + Fmt.dateTime(inv.createdAt) +
                        (if (inv.cancelled) " · cancelled " + Fmt.dateTime(inv.cancelledAt) + (inv.cancelReason?.let { " — $it" } ?: "") else ""))
                }
            }
        }
        item(key = "sheet") { Box(page) { InvoiceSheetCard(inv) } }
    }
}

/** Cancel: asks first, and a reason is required (it is printed on the invoice). */
private fun cancelQuestion(vm: ConsoleViewModel, inv: Invoice) = Ask.Input(
    title = "Cancel ${inv.title} ${inv.number}?",
    body = "For ${inv.customer.name} (₹ ${InvFmt.f2(inv.total)}). It is not deleted: the number stays used, the invoice stays in the list " +
        "and prints with a CANCELLED mark. This cannot be undone.",
    label = "Reason (printed on the invoice)",
    confirmText = "Cancel invoice",
    validate = { if (it.trim().length < 3) "Write the reason for cancelling." else null },
    onOk = { reason -> vm.cancelInvoice(inv, reason) }
)

private fun logoOf(context: Context) = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.nexora_mark) }.getOrNull()

/** The PDF into the console's own cache, handed to the phone's share sheet through the FileProvider. */
private fun sharePdf(context: Context, inv: Invoice, bytes: ByteArray, vm: ConsoleViewModel) {
    try {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, InvFmt.fileName(inv)).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, inv.title + " " + inv.number)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share ${inv.number}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        vm.say("Could not share it: ${e.message ?: "unknown"}", Msg.Kind.ERR)
    }
}
