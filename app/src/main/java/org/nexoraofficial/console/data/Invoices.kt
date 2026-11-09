package org.nexoraofficial.console.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ======================================================================
   2.3.0 — INVOICES WITH AND WITHOUT GST, AS THE DESKTOP CONSOLE 1.4 MAKES
   THEM (invoice-doc.js, invoice-make.js, invoice-settings.js).

   The console service keeps Invoice Settings (your company on paper), gives
   the numbers (one series each for with GST, without GST and receipts, from
   0001 every financial year) and does ALL the money maths: line amounts,
   taxable value, CGST + SGST or IGST, round-off, total and the words. The
   phone reads those figures and shows them; it adds, multiplies and rounds
   nothing (InvFmt only formats).

   GET  /admin/api/invoice-settings → {settings:{…}, ready}
   POST /admin/api/invoice-settings  {the same fields}
   GET  /admin/api/invoices?…       → {invoices:[…]}
   POST /admin/api/invoices         {action: create | cancel, …}
   ====================================================================== */

/** Your company as it is printed on every invoice — and the seller block an invoice keeps. Text as typed. */
data class InvoiceSettings(
    val sellerName: String = "",
    val legalName: String = "",
    val gstin: String = "",
    val address: String = "",
    val stateCode: String = "",
    val stateName: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val pan: String = "",
    val bankName: String = "",
    val accountName: String = "",
    val accountNumber: String = "",
    val ifsc: String = "",
    val upi: String = "",
    val sac: String = "997331",
    val gstRate: String = "18",
    val prefixGst: String = "NX",
    val prefixPlain: String = "NXB",
    val prefixReceipt: String = "NXR",
    val terms: String = "",
    val footer: String = "",
    val signatory: String = ""
) {
    /** The name at the top of the sheet. */
    val shownName: String get() = sellerName.ifBlank { legalName }

    companion object {
        private fun num(o: JSONObject, k: String, dflt: String): String {
            if (!o.has(k) || o.isNull(k)) return dflt
            return when (val v = o.opt(k)) {
                is Number -> v.toDouble().let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                else -> v.toString()
            }
        }

        fun from(o: JSONObject?): InvoiceSettings {
            if (o == null) return InvoiceSettings()
            val d = InvoiceSettings()
            fun s(k: String, dflt: String = "") = o.str(k) ?: dflt
            return InvoiceSettings(
                sellerName = s("sellerName"), legalName = s("legalName"), gstin = s("gstin"), address = s("address"),
                stateCode = num(o, "stateCode", ""), stateName = s("stateName"), phone = s("phone"), email = s("email"),
                website = s("website"), pan = s("pan"), bankName = s("bankName"), accountName = s("accountName"),
                accountNumber = num(o, "accountNumber", ""), ifsc = s("ifsc"), upi = s("upi"), sac = num(o, "sac", d.sac),
                gstRate = num(o, "gstRate", d.gstRate), prefixGst = s("prefixGst", d.prefixGst), prefixPlain = s("prefixPlain", d.prefixPlain),
                prefixReceipt = s("prefixReceipt", d.prefixReceipt), terms = s("terms"), footer = s("footer"), signatory = s("signatory")
            )
        }
    }
}

/** What GET /admin/api/invoice-settings answers: the settings, and whether an invoice can be made with them. */
data class InvoiceSettingsData(val settings: InvoiceSettings, val ready: Boolean) {
    companion object {
        fun from(o: JSONObject) = InvoiceSettingsData(InvoiceSettings.from(o.optJSONObject("settings")), o.optBoolean("ready", false))
    }
}

/** The customer on an invoice ("Bill To"). */
data class InvParty(
    val name: String = "",
    val gstin: String = "",
    val address: String = "",
    val stateCode: String = "",
    val stateName: String = "",
    val email: String = "",
    val phone: String = ""
) {
    companion object {
        fun from(o: JSONObject?): InvParty {
            if (o == null) return InvParty()
            return InvParty(
                name = o.str("name").orEmpty(), gstin = o.str("gstin").orEmpty(), address = o.str("address").orEmpty(),
                stateCode = o.opt("stateCode")?.takeIf { it != JSONObject.NULL }?.toString().orEmpty(),
                stateName = o.str("stateName").orEmpty(), email = o.str("email").orEmpty(), phone = o.str("phone").orEmpty()
            )
        }
    }
}

/** One line of an invoice, every figure the service's. */
data class InvLine(val description: String, val sac: String?, val qty: Double?, val rate: Double?, val amount: Double?)

/** One invoice or receipt, exactly as the service keeps it. */
data class Invoice(
    /** The service's own id, as it came (sent back as it came when the invoice is cancelled). */
    val idRaw: Any,
    val number: String,
    val series: String?,
    /** INVOICE or RECEIPT. */
    val kind: String,
    val gst: Boolean,
    val date: String?,
    val fy: String?,
    val paymentId: Int?,
    val software: String?,
    val companyId: Int?,
    val remoteId: Int?,
    val seller: InvoiceSettings,
    val customer: InvParty,
    val lines: List<InvLine>,
    val inclusive: Boolean,
    val taxable: Double?,
    val cgstRate: Double?,
    val cgst: Double?,
    val sgstRate: Double?,
    val sgst: Double?,
    val igstRate: Double?,
    val igst: Double?,
    val roundOff: Double?,
    val total: Double?,
    val totalWords: String?,
    val intraState: Boolean,
    val placeOfSupply: String?,
    val notes: String?,
    val cancelledAt: String?,
    val cancelReason: String?,
    val createdAt: String?,
    val paymentRef: String? = null,
    val paymentMode: String? = null
) {
    val id: String get() = idRaw.toString()
    val cancelled: Boolean get() = !cancelledAt.isNullOrBlank()
    val receipt: Boolean get() = kind == "RECEIPT"

    /** What the document is called: Tax Invoice, Bill of Supply (no GST, the seller has a GSTIN), Invoice, Payment Receipt. */
    val title: String
        get() = when {
            receipt -> "Payment Receipt"
            gst -> "Tax Invoice"
            seller.gstin.isNotBlank() -> "Bill of Supply"
            else -> "Invoice"
        }

    /** The word in lists: Receipt, Tax Invoice, Bill of Supply, Invoice. */
    val kindWord: String get() = if (receipt) "Receipt" else title

    /** The line under the title on the sheet. */
    val copyLine: String
        get() = when {
            receipt -> if (gst) "With GST break-up" else ""
            gst -> "Original for Recipient"
            seller.gstin.isNotBlank() -> "GST not charged"
            else -> ""
        }

    /** CGST + SGST, IGST, or nothing (no tax lines at all without GST). */
    val taxSplit: String? get() = if (!gst) null else if (intraState) "CGST + SGST" else "IGST"

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase().trim()
        return listOf(number, customer.name, customer.gstin).any { it.lowercase().contains(t) }
    }

    companion object {
        private fun JSONObject.intOrNull(k: String): Int? = opt(k)?.takeIf { it != JSONObject.NULL }?.toString()?.trim()?.toIntOrNull()

        /** A figure as the service sent it (a negative round-off kept), or null. */
        private fun JSONObject.fig(k: String): Double? {
            if (!has(k) || isNull(k)) return null
            return when (val v = opt(k)) {
                is Number -> v.toDouble()
                is String -> v.trim().replace(",", "").toDoubleOrNull()
                else -> null
            }?.takeIf { !it.isNaN() }
        }

        fun from(o: JSONObject): Invoice {
            val la: JSONArray? = o.optJSONArray("lines")
            val lines = if (la == null) emptyList() else (0 until la.length()).mapNotNull { i ->
                la.optJSONObject(i)?.let { l -> InvLine(l.str("description").orEmpty(), l.opt("sac")?.takeIf { it != JSONObject.NULL }?.toString(), l.fig("qty"), l.fig("rate"), l.fig("amount")) }
            }
            return Invoice(
                idRaw = o.opt("id")?.takeIf { it != JSONObject.NULL } ?: "",
                number = o.str("number") ?: "—", series = o.str("series"), kind = o.str("kind") ?: "INVOICE",
                gst = o.optBoolean("gst", false), date = o.str("date")?.take(10), fy = o.str("fy"),
                paymentId = o.intOrNull("paymentId"), software = o.str("software"), companyId = o.intOrNull("companyId"), remoteId = o.intOrNull("remoteId"),
                seller = InvoiceSettings.from(o.optJSONObject("seller")), customer = InvParty.from(o.optJSONObject("customer")), lines = lines,
                inclusive = o.optBoolean("inclusive", false), taxable = o.fig("taxable"),
                cgstRate = o.fig("cgstRate"), cgst = o.fig("cgst"), sgstRate = o.fig("sgstRate"), sgst = o.fig("sgst"),
                igstRate = o.fig("igstRate"), igst = o.fig("igst"), roundOff = o.fig("roundOff"), total = o.fig("total"),
                totalWords = o.str("totalWords"), intraState = o.optBoolean("intraState", false), placeOfSupply = o.str("placeOfSupply"),
                notes = o.str("notes"), cancelledAt = o.str("cancelledAt"), cancelReason = o.str("cancelReason"), createdAt = o.str("createdAt"),
                paymentRef = o.str("paymentRef"), paymentMode = o.str("paymentMode")
            )
        }

        fun listFrom(o: JSONObject): List<Invoice> {
            val a = o.optJSONArray("invoices") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { from(it) } }
        }
    }
}

/** Formatting only — never arithmetic on the service's figures. */
object InvFmt {
    /** 53100 → "53,100.00"; null → "—" (Indian grouping, always two decimals). */
    fun f2(n: Double?): String {
        if (n == null || n.isNaN()) return "—"
        val neg = n < 0
        val cents = Math.round(Math.abs(n) * 100)
        val whole = Money.indian((cents / 100).toDouble())
        return (if (neg) "-" else "") + whole + "." + (cents % 100).toString().padStart(2, '0')
    }

    /** 9.0 → "9%", 2.5 → "2.5%". */
    fun pct(r: Double?): String = if (r == null) "" else (if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()) + "%"

    /** A quantity, up to three decimals. */
    fun qty(q: Double?): String = if (q == null) "" else if (q % 1.0 == 0.0) Money.indian(q) else
        String.format(Locale.US, "%.3f", q).trimEnd('0').trimEnd('.')

    /** "2026-11-08" → "08 Nov 2026". */
    fun date(iso: String?): String {
        val d = iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return iso?.takeIf { it.isNotBlank() } ?: "—"
        return DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).format(d)
    }

    /** A round-off as printed: "+0.40", "-0.30". */
    fun signed(n: Double?): String = if (n == null) "" else (if (n > 0) "+" else "") + f2(n)

    /** The PDF's file name: the number with "/" made "-" ("NX/2026-27/0001" → "NX-2026-27-0001.pdf"). */
    fun fileName(inv: Invoice): String = inv.number.replace('/', '-').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "invoice" } + ".pdf"

    /** The financial year a day falls in: "2026-27" (April to March). */
    fun fy(day: LocalDate): String {
        val a = if (day.monthValue >= 4) day.year else day.year - 1
        return "$a-" + (a + 1).toString().takeLast(2)
    }
}

/** The states, by their GST code (the desktop console's data.js STATES). */
object GstStates {
    val ALL: Map<String, String> = linkedMapOf(
        "01" to "Jammu & Kashmir", "02" to "Himachal Pradesh", "03" to "Punjab", "04" to "Chandigarh", "05" to "Uttarakhand",
        "06" to "Haryana", "07" to "Delhi", "08" to "Rajasthan", "09" to "Uttar Pradesh", "10" to "Bihar", "11" to "Sikkim",
        "12" to "Arunachal Pradesh", "13" to "Nagaland", "14" to "Manipur", "15" to "Mizoram", "16" to "Tripura", "17" to "Meghalaya",
        "18" to "Assam", "19" to "West Bengal", "20" to "Jharkhand", "21" to "Odisha", "22" to "Chhattisgarh", "23" to "Madhya Pradesh",
        "24" to "Gujarat", "26" to "Dadra & Nagar Haveli and Daman & Diu", "27" to "Maharashtra", "29" to "Karnataka", "30" to "Goa",
        "32" to "Kerala", "33" to "Tamil Nadu", "34" to "Puducherry", "36" to "Telangana", "37" to "Andhra Pradesh"
    )

    val GSTIN = Regex("^[0-9]{2}[A-Z0-9]{13}$")
    val PAN = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
    val IFSC = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    val PREFIX = Regex("^[A-Z0-9-]{1,8}$")

    fun name(code: String?): String = ALL[code?.trim()].orEmpty()

    /** The state a GSTIN is registered in, when its first two digits name one. */
    fun ofGstin(g: String?): String? = g?.trim()?.uppercase()?.takeIf { GSTIN.matches(it) }?.take(2)?.takeIf { ALL.containsKey(it) }
}
