package org.nexoraofficial.console.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/* ======================================================================
   2.3.0 — THE INVOICE ON PAPER: ONE A4 PAGE, AS THE DESKTOP'S SHEET.

   The desktop console's invoice-doc.js + invoice.css, drawn with Android's
   Canvas: seller and title, Bill To and the details, the lines, the words and
   the totals, the tax table (only with GST), bank & UPI, terms, signatory,
   and a CANCELLED watermark. One dark ink and greys only as tints, so it
   prints in black and white. Every figure is the service's (InvFmt only
   formats). On the phone the page goes through PdfDocument (text stays
   text); where PdfDocument cannot run (the unit tests' Robolectric) the same
   drawing is made into a picture and wrapped in a one-page PDF by hand.
   ====================================================================== */
object InvoicePdf {
    const val W = 595f
    const val H = 842f
    private const val L = 34f
    private const val R = W - 34f

    private val INK = Color.rgb(0x1D, 0x29, 0x39)
    private val INK2 = Color.rgb(0x47, 0x54, 0x67)
    private val LINE = Color.rgb(0xD0, 0xD5, 0xDD)
    private val TINT = Color.rgb(0xF2, 0xF4, 0xF7)
    private val TEXT = Color.rgb(0x10, 0x18, 0x28)
    private val BODY = Color.rgb(0x34, 0x40, 0x54)

    /** The PDF of [inv]; [logo] (Nexora's mark) is drawn beside the seller when given. */
    fun bytes(inv: Invoice, logo: Bitmap? = null): ByteArray {
        try {
            val doc = PdfDocument()
            try {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
                draw(page.canvas, inv, logo)
                doc.finishPage(page)
                val out = ByteArrayOutputStream()
                doc.writeTo(out)
                val b = out.toByteArray()
                if (b.size > 8 && String(b, 0, 4, Charsets.ISO_8859_1) == "%PDF") return b
            } finally {
                doc.close()
            }
        } catch (_: Throwable) {
            /* no PdfDocument here: the picture way below */
        }
        return pictured(inv, logo)
    }

    /** The same page drawn into a picture ([scale] × 72 dpi) and wrapped in a one-page PDF. */
    internal fun pictured(inv: Invoice, logo: Bitmap?, scale: Float = 2f): ByteArray {
        val w = (W * scale).toInt()
        val h = (H * scale).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale, scale)
        draw(c, inv, logo)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        val rgb = ByteArray(w * h * 3)
        var j = 0
        for (p in px) {
            /* a pixel never drawn on (0) is the paper */
            val v = if (p == 0) -1 else p
            rgb[j++] = (v shr 16 and 0xFF).toByte(); rgb[j++] = (v shr 8 and 0xFF).toByte(); rgb[j++] = (v and 0xFF).toByte()
        }
        val z = Deflater(Deflater.BEST_COMPRESSION)
        z.setInput(rgb); z.finish()
        val packed = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (!z.finished()) packed.write(buf, 0, z.deflate(buf))
        z.end()
        return onePage(packed.toByteArray(), w, h)
    }

    /** A one-page PDF showing one Flate-packed RGB picture over the whole A4 page. */
    private fun onePage(image: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val at = ArrayList<Int>()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        ascii("%PDF-1.4\n%âãÏÓ\n")
        val content = "q ${W.toInt()} 0 0 ${H.toInt()} 0 0 cm /Im0 Do Q\n"
        val objs = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${W.toInt()} ${H.toInt()}] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>",
            "<< /Length ${content.length} >>\nstream\n${content}endstream"
        )
        objs.forEachIndexed { i, o -> at += out.size(); ascii("${i + 1} 0 obj\n$o\nendobj\n") }
        at += out.size()
        ascii("5 0 obj\n<< /Type /XObject /Subtype /Image /Width $w /Height $h /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length ${image.size} >>\nstream\n")
        out.write(image)
        ascii("\nendstream\nendobj\n")
        val xref = out.size()
        ascii("xref\n0 6\n0000000000 65535 f \n")
        at.forEach { ascii(String.format("%010d 00000 n \n", it)) }
        ascii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    /* ------------------------------------------------------------ the drawing */

    private fun paint(size: Float, color: Int = TEXT, bold: Boolean = false, mono: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = when { mono -> Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL); bold -> Typeface.DEFAULT_BOLD; else -> Typeface.DEFAULT }
    }

    private fun fill(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }

    /** [text] broken into lines no wider than [width] (its own line breaks kept). */
    internal fun wrap(text: String, p: Paint, width: Float): List<String> {
        val out = ArrayList<String>()
        text.split('\n').forEach { para ->
            var line = ""
            para.split(' ').forEach { word ->
                val t = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(t) <= width || line.isEmpty()) line = t else { out += line; line = word }
            }
            out += line
        }
        return out
    }

    /** Writes [lines] from [y] (the first baseline); returns the next free y. */
    private fun Canvas.lines(lines: List<String>, x: Float, y: Float, p: Paint, gap: Float = 1.35f, right: Boolean = false): Float {
        var b = y
        lines.forEach { s -> drawText(s, if (right) x - p.measureText(s) else x, b, p); b += p.textSize * gap }
        return b
    }

    private fun has(s: String?) = !s.isNullOrBlank()

    /** Key-value rows in a box column: keys in grey, values bold (right-aligned unless [leftValues]). */
    private fun Canvas.kv(rows: List<Pair<String, String>>, x: Float, w: Float, y0: Float, leftValues: Boolean = false, mono: Boolean = false): Float {
        val k = paint(8f, INK2)
        val v = paint(8f, TEXT, bold = true, mono = mono)
        val kw = (rows.maxOfOrNull { k.measureText(it.first) } ?: 0f) + 10f
        var y = y0
        rows.forEach { (a, b) ->
            drawText(a, x, y, k)
            val ls = wrap(b, v, w - kw)
            ls.forEachIndexed { i, s -> drawText(s, if (leftValues) x + kw else x + w - v.measureText(s), y + i * 10.5f, v) }
            y += 10.5f * ls.size + 1.5f
        }
        return y
    }

    private fun cap(c: Canvas, text: String, x: Float, y: Float) {
        val p = paint(6.6f, INK2, bold = true).apply { letterSpacing = 0.1f }
        c.drawText(text.uppercase(), x, y, p)
    }

    fun draw(c: Canvas, inv: Invoice, logo: Bitmap?) {
        val s = inv.seller
        val cu = inv.customer
        var y = 34f

        /* ---- head: logo, seller, the document's title and number ---- */
        var sx = L
        if (logo != null) { c.drawBitmap(logo, null, RectF(L, y - 2f, L + 44f, y + 42f), Paint(Paint.FILTER_BITMAP_FLAG)); sx = L + 56f }
        val titleP = paint(16f, INK, bold = true).apply { letterSpacing = 0.07f }
        val title = inv.title.uppercase()
        val docW = maxOf(titleP.measureText(title), 120f)
        val sellerW = R - docW - 18f - sx
        var ly = y + 13f
        ly = c.lines(wrap(s.shownName.ifBlank { "Your company" }, paint(15f, INK, bold = true), sellerW), sx, ly, paint(15f, INK, bold = true), 1.2f)
        if (has(s.legalName) && s.legalName != s.sellerName) ly = c.lines(listOf(s.legalName), sx, ly - 3f, paint(8.2f, INK2))
        if (has(s.address)) ly = c.lines(wrap(s.address, paint(8.6f, BODY), sellerW), sx, ly + 1f, paint(8.6f, BODY))
        val ids = InvSheet.sellerIds(inv)
        if (ids.isNotEmpty()) ly = c.lines(wrap(ids, paint(8.2f, BODY), sellerW), sx, ly + 1f, paint(8.2f, BODY))
        val reach = InvSheet.sellerReach(inv)
        if (reach.isNotEmpty()) ly = c.lines(wrap(reach, paint(8.2f, BODY), sellerW), sx, ly, paint(8.2f, BODY))
        var ry = y + 14f
        c.drawText(title, R - titleP.measureText(title), ry, titleP)
        if (inv.copyLine.isNotEmpty()) { ry += 10f; val p = paint(7f, INK2).apply { letterSpacing = 0.06f }; c.drawText(inv.copyLine.uppercase(), R - p.measureText(inv.copyLine.uppercase()), ry, p) }
        ry += 16f
        val noP = paint(11f, INK, bold = true, mono = true)
        c.drawText(inv.number, R - noP.measureText(inv.number), ry, noP)
        ry += 12f
        val dtP = paint(8.6f, BODY)
        InvFmt.date(inv.date).let { c.drawText(it, R - dtP.measureText(it), ry, dtP) }
        y = maxOf(ly, ry, if (logo != null) y + 46f else 0f) + 6f
        c.drawRect(L, y, R, y + 2.2f, fill(INK))
        y += 12f

        /* ---- Bill To | details ---- */
        val split = L + (R - L) * 0.555f
        val buyer = InvSheet.buyerRows(inv)
        val det = InvSheet.detailRows(inv)
        /* measured first on a scratch pass, so the box is drawn to the taller side */
        val boxTop = y
        val nameP = paint(10.5f, INK, bold = true)
        val addrP = paint(8.4f, BODY)
        fun leftSide(cv: Canvas): Float {
            var b = boxTop + 14f
            cap(cv, "Bill To", L + 10f, b); b += 12f
            b = cv.lines(wrap(cu.name.ifBlank { "—" }, nameP, split - L - 20f), L + 10f, b, nameP, 1.2f)
            if (has(cu.address)) b = cv.lines(wrap(cu.address, addrP, split - L - 20f), L + 10f, b, addrP)
            if (buyer.isNotEmpty()) b = cv.kv(buyer, L + 10f, split - L - 20f, b + 3f, leftValues = true)
            return b
        }
        fun rightSide(cv: Canvas): Float {
            var b = boxTop + 14f
            cap(cv, (if (inv.receipt) "Receipt" else "Invoice") + " Details", split + 10f, b); b += 12f
            return cv.kv(det, split + 10f, R - split - 20f, b)
        }
        val scratch = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val boxBottom = maxOf(leftSide(scratch), rightSide(scratch)) + 4f
        c.drawRect(split, boxTop, R, boxBottom, fill(TINT))
        c.drawRoundRect(RectF(L, boxTop, R, boxBottom), 5f, 5f, stroke(LINE, 0.8f))
        c.drawLine(split, boxTop, split, boxBottom, stroke(LINE, 0.8f))
        leftSide(c); rightSide(c)
        y = boxBottom + 12f

        /* ---- a receipt says what was received ---- */
        if (inv.receipt) {
            val p = paint(9f, TEXT)
            val ls = wrap(InvSheet.receivedLine(inv), p, R - L - 24f)
            val h = ls.size * 12.2f + 12f
            c.drawRect(L, y, R, y + h, fill(TINT)); c.drawRect(L, y, L + 3f, y + h, fill(INK))
            c.lines(ls, L + 12f, y + 15f, p)
            y += h + 10f
        }

        /* ---- the lines ---- */
        val g = inv.gst
        val cols = if (g) floatArrayOf(22f, 0f, 58f, 40f, 72f, 84f) else floatArrayOf(22f, 0f, 0f, 44f, 80f, 90f)
        cols[1] = (R - L) - cols.sum()
        val xs = FloatArray(cols.size + 1).also { a -> a[0] = L; for (i in cols.indices) a[i + 1] = a[i] + cols[i] }
        val headP = paint(6.8f, Color.WHITE, bold = true).apply { letterSpacing = 0.05f }
        c.drawRect(L, y, R, y + 18f, fill(INK))
        val heads = listOf("#", "Description", if (g) "HSN/SAC" else "", "Qty", "Rate (₹)", if (g) "Taxable Value (₹)" else "Amount (₹)")
        heads.forEachIndexed { i, t ->
            if (t.isEmpty()) return@forEachIndexed
            val tx = t.uppercase()
            val x = when (i) { 0, 2 -> (xs[i] + xs[i + 1] - headP.measureText(tx)) / 2; 1 -> xs[i] + 6f; else -> xs[i + 1] - 6f - headP.measureText(tx) }
            c.drawText(tx, x, y + 12f, headP)
        }
        y += 18f
        val cell = paint(8.6f, TEXT)
        if (inv.lines.isEmpty()) { c.drawText("—", L + (R - L) / 2, y + 13f, paint(8.6f, INK2)); y += 20f; c.drawLine(L, y, R, y, stroke(LINE, 0.7f)) }
        inv.lines.forEachIndexed { n, l ->
            val ds = wrap(l.description, cell, cols[1] - 12f)
            val h = ds.size * 11.6f + 9f
            val b = y + 13f
            (n + 1).toString().let { c.drawText(it, (xs[0] + xs[1] - cell.measureText(it)) / 2, b, cell) }
            c.lines(ds, xs[1] + 6f, b, cell, 1.35f)
            if (g) l.sac.orEmpty().let { c.drawText(it, (xs[2] + xs[3] - cell.measureText(it)) / 2, b, cell) }
            listOf(InvFmt.qty(l.qty) to 4, InvFmt.f2(l.rate) to 5, InvFmt.f2(l.amount) to 6).forEach { (t, i) -> c.drawText(t, xs[i] - 6f - cell.measureText(t), b, cell) }
            y += h
            c.drawLine(L, y, R, y, stroke(LINE, 0.7f))
        }
        y += 12f

        /* ---- words | totals ---- */
        val totW = 210f
        val tx = R - totW
        val rows = InvSheet.totalRows(inv)
        val rowP = paint(8.6f, INK2)
        val valP = paint(8.6f, TEXT, bold = true)
        val totTop = y
        rows.forEach { (k, v) ->
            c.drawText(k, tx + 9f, y + 12f, rowP); c.drawText(v, R - 9f - valP.measureText(v), y + 12f, valP)
            y += 17f; c.drawLine(tx, y, R, y, stroke(LINE, 0.7f))
        }
        c.drawRect(tx, y, R, y + 22f, fill(INK))
        val grandP = paint(11f, Color.WHITE, bold = true)
        val (gk, gv) = InvSheet.grand(inv)
        c.drawText(gk, tx + 9f, y + 15f, grandP)
        c.drawText(gv, R - 9f - grandP.measureText(gv), y + 15f, grandP)
        y += 22f
        c.drawRoundRect(RectF(tx, totTop, R, y), 5f, 5f, stroke(LINE, 0.8f))
        val wordsP = paint(9.4f, INK, bold = true)
        val ws = wrap(inv.totalWords ?: "—", wordsP, tx - L - 34f)
        val wordsBottom = totTop + 26f + ws.size * 12f
        c.drawRoundRect(RectF(L, totTop, tx - 14f, wordsBottom), 5f, 5f, stroke(LINE, 0.8f))
        cap(c, "Amount in Words", L + 9f, totTop + 13f)
        c.lines(ws, L + 9f, totTop + 26f, wordsP, 1.25f)
        y = maxOf(y, wordsBottom) + 12f

        /* ---- the tax table: only with GST ---- */
        InvSheet.taxTable(inv)?.let { (th, tv) ->
            val cw = (R - L) / th.size
            c.drawRect(L, y, R, y + 17f, fill(TINT))
            c.drawLine(L, y, R, y, stroke(INK, 0.8f)); c.drawLine(L, y + 17f, R, y + 17f, stroke(INK, 0.8f))
            val hp = paint(6.8f, INK, bold = true).apply { letterSpacing = 0.05f }
            th.forEachIndexed { i, t -> val u = t.uppercase(); c.drawText(u, if (i == 0) L + 6f else L + cw * (i + 1) - 6f - hp.measureText(u), y + 11.5f, hp) }
            y += 17f
            tv.forEachIndexed { i, t -> c.drawText(t, if (i == 0) L + 6f else L + cw * (i + 1) - 6f - cell.measureText(t), y + 13f, cell) }
            y += 19f; c.drawLine(L, y, R, y, stroke(LINE, 0.7f)); y += 10f
        }

        if (has(inv.notes)) {
            val np = paint(8.4f, BODY)
            y = c.lines(wrap("Notes: " + inv.notes, np, R - L), L, y + 4f, np) + 4f
        }
        if (inv.cancelled) {
            val p = paint(9f, TEXT, bold = true)
            val ls = wrap(InvSheet.cancelLine(inv), p, R - L - 20f)
            val h = ls.size * 12f + 12f
            c.drawRoundRect(RectF(L, y, R, y + h), 5f, 5f, stroke(TEXT, 1.6f))
            c.lines(ls, L + 10f, y + 15f, p)
            y += h + 10f
        }

        /* ---- foot: bank & UPI | terms | signatory, at the bottom of the page ---- */
        val footTop = maxOf(y + 6f, H - 168f)
        c.drawLine(L, footTop, R, footTop, stroke(LINE, 0.8f))
        val colW = (R - L - 152f - 24f) / 2
        val bank = InvSheet.bankRows(s)
        if (bank.isNotEmpty()) { cap(c, "Bank & UPI", L, footTop + 14f); c.kv(bank, L, colW, footTop + 26f, leftValues = true, mono = true) }
        if (has(s.terms)) {
            val x2 = L + colW + 12f
            cap(c, "Terms", x2, footTop + 14f)
            c.lines(wrap(s.terms, paint(8f, BODY), colW), x2, footTop + 26f, paint(8f, BODY))
        }
        val forP = paint(9f, INK, bold = true)
        val forT = "For " + s.shownName
        wrap(forT, forP, 150f).let { c.lines(it, R, footTop + 16f, forP, right = true) }
        c.drawLine(R - 150f, footTop + 82f, R, footTop + 82f, stroke(INK, 0.8f))
        val byP = paint(8f, INK2)
        c.lines(listOf(s.signatory.ifBlank { "Authorised Signatory" }, "Authorised Signatory"), R, footTop + 93f, byP, right = true)
        val cgP = paint(7.6f, INK2)
        var fy = H - 40f
        if (has(s.footer)) wrap(s.footer, cgP, R - L).forEach { t -> c.drawText(t, (W - cgP.measureText(t)) / 2, fy, cgP); fy += 10f }
        "This is a computer-generated ${if (inv.receipt) "receipt" else "invoice"}.".let { c.drawText(it, (W - cgP.measureText(it)) / 2, fy, cgP) }

        /* ---- CANCELLED across the page ---- */
        if (inv.cancelled) {
            c.save()
            c.rotate(-28f, W / 2, H * 0.44f)
            val wm = paint(84f, Color.argb(30, 16, 24, 40), bold = true).apply { letterSpacing = 0.12f }
            val t = "CANCELLED"
            val tw = wm.measureText(t)
            c.drawText(t, (W - tw) / 2, H * 0.44f + 30f, wm)
            c.drawRoundRect(RectF((W - tw) / 2 - 22f, H * 0.44f - 50f, (W + tw) / 2 + 22f, H * 0.44f + 54f), 14f, 14f,
                stroke(Color.argb(30, 16, 24, 40), 7f))
            c.restore()
        }
    }
}
