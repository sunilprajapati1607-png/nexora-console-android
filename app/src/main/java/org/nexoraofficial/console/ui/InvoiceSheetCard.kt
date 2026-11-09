package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.data.InvFmt
import org.nexoraofficial.console.data.InvSheet
import org.nexoraofficial.console.data.Invoice

/* ======================================================================
   2.3.0 — THE INVOICE AS A SHEET ON THE PHONE: the desktop's A4 design
   (invoice.css) laid out for a narrow screen — seller, title and number,
   Bill To, the details, the lines, the totals and the words, the tax table
   (only with GST), bank & UPI, terms, signatory, and CANCELLED across it.
   White paper and one dark ink in light and dark alike, as it will print.
   Every figure comes from InvSheet: the service's own, only formatted.
   ====================================================================== */

private val INK = Color(0xFF1D2939)
private val INK2 = Color(0xFF475467)
private val LINE = Color(0xFFD0D5DD)
private val TINT = Color(0xFFF2F4F7)
private val TEXT = Color(0xFF101828)
private val BODY = Color(0xFF344054)

@Composable
private fun T(text: String, size: TextUnit = 12.sp, color: Color = TEXT, bold: Boolean = false, mono: Boolean = false,
              align: TextAlign? = null, modifier: Modifier = Modifier, spacing: TextUnit = TextUnit.Unspecified) =
    Text(text, color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = (size.value * 1.35f).sp,
        fontFamily = if (mono) FontFamily.Monospace else null, textAlign = align, modifier = modifier, letterSpacing = spacing)

@Composable
private fun Cap(text: String) = T(text.uppercase(), 9.5f.sp, INK2, bold = true, spacing = 1.sp)

@Composable
private fun Kv(rows: List<Pair<String, String>>, leftValues: Boolean = false, mono: Boolean = false) {
    rows.forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 1.5f.dp)) {
            T(k, 11.5f.sp, INK2, modifier = Modifier.width(104.dp))
            T(v, 11.5f.sp, TEXT, bold = true, mono = mono, align = if (leftValues) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Boxed(tint: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (tint) TINT else Color.White)
        .border(1.dp, LINE, RoundedCornerShape(6.dp)).padding(10.dp), content = content)

@Composable
fun InvoiceSheetCard(inv: Invoice) {
    val s = inv.seller
    val g = inv.gst
    Box(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            /* seller */
            T(s.shownName.ifBlank { "Your company" }, 17.sp, INK, bold = true)
            if (s.legalName.isNotBlank() && s.legalName != s.sellerName) T(s.legalName, 11.sp, INK2)
            if (s.address.isNotBlank()) T(s.address, 11.5f.sp, BODY)
            InvSheet.sellerIds(inv).takeIf { it.isNotEmpty() }?.let { T(it, 11.sp, BODY) }
            InvSheet.sellerReach(inv).takeIf { it.isNotEmpty() }?.let { T(it, 11.sp, BODY) }
            Spacer(Modifier.height(10.dp))
            /* title, number, date */
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    T(inv.title.uppercase(), 18.sp, INK, bold = true, spacing = 1.2f.sp)
                    if (inv.copyLine.isNotEmpty()) T(inv.copyLine.uppercase(), 9.5f.sp, INK2, spacing = 0.6f.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    T(inv.number, 14.sp, INK, bold = true, mono = true)
                    T(InvFmt.date(inv.date), 11.5f.sp, BODY)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).background(INK))
            Spacer(Modifier.height(12.dp))
            /* Bill To, then the details */
            Boxed {
                Cap("Bill To")
                T(inv.customer.name.ifBlank { "—" }, 14.sp, INK, bold = true)
                if (inv.customer.address.isNotBlank()) T(inv.customer.address, 11.5f.sp, BODY)
                InvSheet.buyerRows(inv).takeIf { it.isNotEmpty() }?.let { Spacer(Modifier.height(4.dp)); Kv(it, leftValues = true) }
            }
            Spacer(Modifier.height(8.dp))
            Boxed(tint = true) {
                Cap((if (inv.receipt) "Receipt" else "Invoice") + " Details")
                Kv(InvSheet.detailRows(inv))
            }
            if (inv.receipt) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().background(TINT)) {
                    Box(Modifier.width(4.dp).height(64.dp).background(INK))
                    T(InvSheet.receivedLine(inv), 12.5f.sp, TEXT, modifier = Modifier.padding(10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            /* the lines */
            Row(Modifier.fillMaxWidth().background(INK).padding(horizontal = 8.dp, vertical = 6.dp)) {
                T("#  DESCRIPTION", 9.5f.sp, Color.White, bold = true, spacing = 0.5f.sp, modifier = Modifier.weight(1f))
                T(if (g) "TAXABLE VALUE (₹)" else "AMOUNT (₹)", 9.5f.sp, Color.White, bold = true, spacing = 0.5f.sp)
            }
            if (inv.lines.isEmpty()) T("—", 12.sp, INK2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
            inv.lines.forEachIndexed { i, l ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        T("${i + 1}.  " + l.description, 12.5f.sp, TEXT, modifier = Modifier.weight(1f))
                        T(InvFmt.f2(l.amount), 12.5f.sp, TEXT, bold = true, modifier = Modifier.padding(start = 8.dp))
                    }
                    T(listOfNotNull(if (g && !l.sac.isNullOrBlank()) "HSN/SAC ${l.sac}" else null, "Qty ${InvFmt.qty(l.qty)} × ₹ ${InvFmt.f2(l.rate)}").joinToString("  ·  "),
                        11.sp, INK2, modifier = Modifier.padding(start = 18.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
            }
            Spacer(Modifier.height(12.dp))
            /* totals, then the words */
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(1.dp, LINE, RoundedCornerShape(6.dp))) {
                InvSheet.totalRows(inv).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                        T(k, 12.sp, INK2, modifier = Modifier.weight(1f)); T(v, 12.sp, TEXT, bold = true)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                }
                val (gk, gv) = InvSheet.grand(inv)
                Row(Modifier.fillMaxWidth().background(INK).padding(horizontal = 10.dp, vertical = 9.dp)) {
                    T(gk, 15.sp, Color.White, bold = true, modifier = Modifier.weight(1f)); T(gv, 15.sp, Color.White, bold = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            Boxed { Cap("Amount in Words"); T(inv.totalWords ?: "—", 13.sp, INK, bold = true) }
            /* the tax table — only with GST */
            InvSheet.taxTable(inv)?.let { (heads, row) ->
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().border(1.dp, INK)) {
                    heads.zip(row).forEachIndexed { i, (h, v) ->
                        Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) TINT else Color.White).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            T(h, 11.5f.sp, INK2, modifier = Modifier.weight(1f)); T(v, 11.5f.sp, TEXT, bold = true)
                        }
                    }
                }
            }
            if (!inv.notes.isNullOrBlank()) { Spacer(Modifier.height(10.dp)); T("Notes: " + inv.notes, 11.5f.sp, BODY) }
            if (inv.cancelled) {
                Spacer(Modifier.height(10.dp))
                T(InvSheet.cancelLine(inv), 12.sp, TEXT, bold = true,
                    modifier = Modifier.fillMaxWidth().border(2.dp, TEXT, RoundedCornerShape(6.dp)).padding(10.dp))
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
            Spacer(Modifier.height(10.dp))
            /* bank & UPI, terms, signatory */
            val bank = InvSheet.bankRows(s)
            if (bank.isNotEmpty()) { Cap("Bank & UPI"); Kv(bank, leftValues = true, mono = true); Spacer(Modifier.height(8.dp)) }
            if (s.terms.isNotBlank()) { Cap("Terms"); T(s.terms, 11.sp, BODY); Spacer(Modifier.height(8.dp)) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                T("For " + s.shownName, 12.sp, INK, bold = true)
                Spacer(Modifier.height(36.dp))
                Box(Modifier.widthIn(min = 160.dp).height(1.dp).background(INK))
                T(s.signatory.ifBlank { "Authorised Signatory" }, 11.sp, INK2)
                T("Authorised Signatory", 11.sp, INK2)
            }
            Spacer(Modifier.height(14.dp))
            if (s.footer.isNotBlank()) T(s.footer, 10.5f.sp, INK2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            T(InvSheet.closing(inv), 10.5f.sp, INK2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (inv.cancelled) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text("CANCELLED", color = INK.copy(alpha = 0.10f), fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                    modifier = Modifier.rotate(-28f).border(5.dp, INK.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp))
            }
        }
    }
}

