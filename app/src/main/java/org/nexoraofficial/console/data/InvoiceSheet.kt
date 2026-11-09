package org.nexoraofficial.console.data

/* ======================================================================
   2.3.0 — WHAT THE SHEET SAYS, ROW BY ROW: shared by the invoice's window
   (Compose) and its PDF (Canvas), so both show the same thing — and every
   figure in it is the service's own, only formatted (invoice-doc.js).
   Without GST there is no tax line anywhere: no CGST, SGST or IGST row and
   no tax table.
   ====================================================================== */
object InvSheet {

    /** The seller's GSTIN, PAN and (with GST) state, joined. */
    fun sellerIds(inv: Invoice): String {
        val s = inv.seller
        return listOfNotNull(
            s.gstin.takeIf { it.isNotBlank() }?.let { "GSTIN $it" },
            s.pan.takeIf { it.isNotBlank() }?.let { "PAN $it" },
            if (inv.gst && s.stateName.isNotBlank()) "State " + (if (s.stateCode.isNotBlank()) s.stateCode + " " else "") + s.stateName else null
        ).joinToString("  ·  ")
    }

    fun sellerReach(inv: Invoice): String = listOf(inv.seller.phone, inv.seller.email, inv.seller.website).filter { it.isNotBlank() }.joinToString("  ·  ")

    /** Bill To's rows under the name and address. */
    fun buyerRows(inv: Invoice): List<Pair<String, String>> {
        val c = inv.customer
        val r = ArrayList<Pair<String, String>>()
        if (c.gstin.isNotBlank()) r += "GSTIN" to c.gstin
        if (inv.gst && c.stateCode.isNotBlank()) r += "State Code" to (c.stateCode + if (c.stateName.isNotBlank()) " " + c.stateName else "")
        if (c.phone.isNotBlank()) r += "Phone" to c.phone
        if (c.email.isNotBlank()) r += "E-mail" to c.email
        return r
    }

    /** Invoice (or Receipt) Details. */
    fun detailRows(inv: Invoice): List<Pair<String, String>> {
        val r = ArrayList<Pair<String, String>>()
        r += "Number" to inv.number
        r += "Date" to InvFmt.date(inv.date)
        if (inv.gst && !inv.placeOfSupply.isNullOrBlank()) r += "Place of Supply" to inv.placeOfSupply
        if (inv.gst && !inv.receipt) r += "Reverse Charge" to "No"
        if (!inv.software.isNullOrBlank()) r += "Software" to Software.fullName(inv.software)
        if (!inv.paymentRef.isNullOrBlank()) r += "Payment Ref." to inv.paymentRef
        inv.paymentId?.let { r += "Against Payment" to "#$it" }
        if (!inv.fy.isNullOrBlank()) r += "Financial Year" to inv.fy
        return r
    }

    /** The totals box above the grand total: the service's figures, never summed here. */
    fun totalRows(inv: Invoice): List<Pair<String, String>> {
        val r = ArrayList<Pair<String, String>>()
        r += (if (inv.gst) "Taxable Value" else "Sub Total") to InvFmt.f2(inv.taxable)
        if (inv.gst && inv.intraState) {
            r += "CGST @ ${InvFmt.pct(inv.cgstRate)}" to InvFmt.f2(inv.cgst)
            r += "SGST @ ${InvFmt.pct(inv.sgstRate)}" to InvFmt.f2(inv.sgst)
        } else if (inv.gst) r += "IGST @ ${InvFmt.pct(inv.igstRate)}" to InvFmt.f2(inv.igst)
        if (inv.roundOff != null && inv.roundOff != 0.0) r += "Round Off" to InvFmt.signed(inv.roundOff)
        return r
    }

    /** The grand row: Total (Amount Received on a receipt) and the service's total. */
    fun grand(inv: Invoice): Pair<String, String> = (if (inv.receipt) "Amount Received" else "Total") to "₹ " + InvFmt.f2(inv.total)

    /** The tax table's heads and its one row — null without GST. */
    fun taxTable(inv: Invoice): Pair<List<String>, List<String>>? {
        if (!inv.gst) return null
        val sacs = inv.lines.mapNotNull { it.sac?.takeIf { s -> s.isNotBlank() } }.distinct().joinToString(", ").ifEmpty { inv.seller.sac }
        return if (inv.intraState) listOf("HSN/SAC", "Taxable Value", "CGST Rate", "CGST", "SGST Rate", "SGST") to
            listOf(sacs, InvFmt.f2(inv.taxable), InvFmt.pct(inv.cgstRate), InvFmt.f2(inv.cgst), InvFmt.pct(inv.sgstRate), InvFmt.f2(inv.sgst))
        else listOf("HSN/SAC", "Taxable Value", "IGST Rate", "IGST") to listOf(sacs, InvFmt.f2(inv.taxable), InvFmt.pct(inv.igstRate), InvFmt.f2(inv.igst))
    }

    /** Bank & UPI, only what is filled. */
    fun bankRows(s: InvoiceSettings): List<Pair<String, String>> =
        listOf("Bank" to s.bankName, "A/c Name" to s.accountName, "A/c No." to s.accountNumber, "IFSC" to s.ifsc, "UPI" to s.upi).filter { it.second.isNotBlank() }

    /** A receipt's sentence: received with thanks from … the sum of … for the following. */
    fun receivedLine(inv: Invoice): String =
        "Received with thanks from ${inv.customer.name.ifBlank { "—" }} the sum of ₹ ${InvFmt.f2(inv.total)} (${inv.totalWords.orEmpty()})" +
            (if (!inv.paymentMode.isNullOrBlank()) " by ${inv.paymentMode}" else "") + ", for the following:"

    fun cancelLine(inv: Invoice): String =
        "CANCELLED on " + InvFmt.date(inv.cancelledAt) + (if (!inv.cancelReason.isNullOrBlank()) " — " + inv.cancelReason else "") +
            ". This document is not valid for payment or tax credit."

    fun closing(inv: Invoice): String = "This is a computer-generated ${if (inv.receipt) "receipt" else "invoice"}."
}
