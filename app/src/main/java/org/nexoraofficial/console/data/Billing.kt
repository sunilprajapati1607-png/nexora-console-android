package org.nexoraofficial.console.data

import org.json.JSONArray
import org.json.JSONObject

/* ======================================================================
   2.3.0 — WHAT THE PHONE SENDS FOR A PAYMENT BY PLAN AND FOR AN INVOICE.

   Every body is made here, from the forms the screens fill, so the tests
   read exactly what goes to the service — and the desktop console's own
   rules (payments-ui.js pw-save, invoice-make.js body(), invoice-settings.js
   clean(), invoice-view.js inv-cancel) are followed in one place. A body the
   service would refuse is refused here first, in the desktop's words.
   ====================================================================== */

/** A form the owner must correct before anything is sent; [message] says what. */
class Refused(message: String) : Exception(message)

/** What POST /admin/api/payments {action:'quote'} answers: the service's sum for a plan, users and periods. */
data class PayQuote(
    val planCode: String,
    val planName: String,
    /** One period's days (365 when the plan has none). */
    val planDays: Int,
    val usersIncluded: Int?,
    val kind: String,
    val users: Int,
    val periods: Int,
    val extraUsers: Int,
    val unitPrice: Double?,
    val extraUserPrice: Double?,
    val extraAmount: Double?,
    /** The days it adds to the licence (0 for extra users on the period already paid). */
    val days: Int,
    /** null = the plan has no price for it: the amount must be typed. */
    val amount: Double?,
    val currentPlan: String?,
    val currentSeats: Int?,
    val note: String?
) {
    val extraOnly: Boolean get() = kind == "EXTRA_USERS"

    companion object {
        fun from(o: JSONObject): PayQuote {
            val p = o.optJSONObject("plan") ?: JSONObject()
            return PayQuote(
                planCode = (p.str("code") ?: "").uppercase(), planName = p.str("name") ?: Plans.wordOf(p.str("code")),
                planDays = p.whole("days")?.takeIf { it > 0 } ?: 365, usersIncluded = p.whole("usersIncluded"),
                kind = o.str("kind") ?: "RENEWAL", users = o.optInt("users", 1), periods = o.optInt("periods", 1),
                extraUsers = o.optInt("extraUsers", 0), unitPrice = o.money("unitPrice"), extraUserPrice = o.money("extraUserPrice"),
                extraAmount = o.money("extraAmount"), days = o.optInt("days", 0), amount = o.money("amount"),
                currentPlan = o.str("currentPlan")?.uppercase(), currentSeats = o.whole("currentSeats"), note = o.str("note")
            )
        }

        /** The amount as the form holds it ("15000", "15000.5"). */
        fun typed(n: Double?): String = n?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()
    }
}

/** One line of a service invoice while it is typed. */
data class LineDraft(val description: String = "", val sac: String = "997331", val qty: String = "1", val rate: String = "")

/** The Make invoice form: from a payment ("pay") or for a service ("svc"). */
data class InvoiceDraft(
    val mode: String = "pay",
    val paymentId: Int? = null,
    val software: String = Software.WEIGHT,
    val companyId: Int? = null,
    val remoteId: Int? = null,
    /** INVOICE or RECEIPT (a receipt only from a payment). */
    val kind: String = "INVOICE",
    val gst: Boolean = true,
    /** The amount (or the rates) include GST: on by default from a payment, off for a service. */
    val inclusive: Boolean = true,
    val date: String = "",
    val customer: InvParty = InvParty(),
    val notes: String = "",
    val lines: List<LineDraft> = emptyList(),
    /** The customer it was made from, to go back to. */
    val customerKey: String? = null
) {
    val fromPayment: Boolean get() = mode == "pay"
}

object Billing {

    /** A typed amount as a number ("15,000" → 15000), or null. */
    private fun number(s: String): Any? {
        val n = Money.parse(s.replace(",", "")) ?: return null
        return if (n % 1.0 == 0.0) n.toLong() else n
    }

    /** The ids the service knows a licence by: Sales & Costing's companyId, any other software's remoteId (desktop idsOf). */
    fun ids(b: JSONObject, c: Customer, sw: String): JSONObject {
        if (sw == Software.WEIGHT) c.w?.let { b.put("companyId", it.id) } else c.remote(sw)?.let { b.put("remoteId", it.id) }
        return b
    }

    /** What a quote is asked for — the same key means the same answer, so it is not asked twice. */
    fun quoteKey(f: PaymentForm): String = listOf(f.customerKey, f.software, f.plan, f.kind, f.users, f.periods).joinToString("|")

    /** POST /admin/api/payments {action:'quote'} — changes nothing on the service. */
    fun quote(f: PaymentForm, c: Customer): JSONObject = ids(
        JSONObject().put("action", "quote").put("software", f.software).put("plan", f.plan.trim().uppercase())
            .put("kind", f.kind).put("users", f.users.coerceAtLeast(1)).put("periods", f.periods.coerceAtLeast(1)),
        c, f.software
    )

    /**
     * Save a payment BY PLAN: the plan and seats are set and plan days × periods added by the service. The amount
     * goes only when it was typed over the quote (or the quote has none) — then it overrides the quote.
     */
    fun addByPlan(f: PaymentForm, c: Customer, q: PayQuote?): JSONObject {
        if (f.plan.isBlank()) throw Refused("Choose the plan.")
        if (q == null) throw Refused("The amount is still being worked out.")
        val typed = f.amount.trim().replace(",", "")
        val b = ids(
            JSONObject().put("action", "add").put("byPlan", true).put("software", f.software).put("plan", f.plan.trim().uppercase())
                .put("kind", f.kind).put("users", f.users.coerceAtLeast(1)).put("periods", f.periods.coerceAtLeast(1)),
            c, f.software
        )
        val n = if (typed.isEmpty()) null else number(typed) ?: throw Refused("The amount is not a number.")
        if (n != null && (f.typed || q.amount == null || (n as Number).toDouble() != q.amount)) b.put("amount", n)
        if (!b.has("amount") && q.amount == null) throw Refused("This plan has no price set for that — type the amount.")
        if (f.paidOn.isNotBlank()) b.put("paidOn", f.paidOn.trim())
        b.put("mode", f.mode)
        if (f.reference.isNotBlank()) b.put("reference", f.reference.trim())
        if (f.note.isNotBlank()) b.put("note", f.note.trim())
        return b
    }

    /* ---- Invoice Settings ---- */

    /** The settings cleaned and checked as the desktop's clean() checks them; a wrong one is refused in its words. */
    fun cleanSettings(s0: InvoiceSettings): InvoiceSettings {
        val up = { v: String -> v.trim().uppercase() }
        val s = s0.copy(
            sellerName = s0.sellerName.trim(), legalName = s0.legalName.trim(), gstin = up(s0.gstin), address = s0.address.trimEnd(),
            stateCode = s0.stateCode.trim(), phone = s0.phone.trim(), email = s0.email.trim(), website = s0.website.trim(), pan = up(s0.pan),
            bankName = s0.bankName.trim(), accountName = s0.accountName.trim(), accountNumber = s0.accountNumber.trim(), ifsc = up(s0.ifsc),
            upi = s0.upi.trim(), sac = s0.sac.trim(), gstRate = s0.gstRate.trim(), prefixGst = up(s0.prefixGst), prefixPlain = up(s0.prefixPlain),
            prefixReceipt = up(s0.prefixReceipt), terms = s0.terms.trimEnd(), footer = s0.footer.trimEnd(), signatory = s0.signatory.trim()
        )
        if (s.sellerName.isEmpty()) throw Refused("Type the name that goes on the invoice.")
        if (s.address.isBlank()) throw Refused("Type the address — it is printed on every invoice.")
        if (s.gstin.isNotEmpty() && !GstStates.GSTIN.matches(s.gstin)) throw Refused("The GSTIN must be 15 letters and digits.")
        if (s.pan.isNotEmpty() && !GstStates.PAN.matches(s.pan)) throw Refused("The PAN must look like AAAAA0000A.")
        if (s.ifsc.isNotEmpty() && !GstStates.IFSC.matches(s.ifsc)) throw Refused("The IFSC must look like ABCD0123456.")
        if (s.gstin.isNotEmpty() && s.stateCode.isEmpty()) throw Refused("Choose the state.")
        val rate = s.gstRate.toDoubleOrNull()
        if (rate == null || rate < 0 || rate > 28) throw Refused("The GST rate must be 0 to 28.")
        listOf(s.prefixGst, s.prefixPlain, s.prefixReceipt).forEach {
            if (!GstStates.PREFIX.matches(it)) throw Refused("A number prefix is 1 to 8 letters or digits (e.g. NX).")
        }
        if (setOf(s.prefixGst, s.prefixPlain, s.prefixReceipt).size < 3) throw Refused("The three number prefixes must be different.")
        return s.copy(stateName = GstStates.name(s.stateCode).ifEmpty { s.stateName })
    }

    /** POST /admin/api/invoice-settings — the cleaned settings, the rate as a number. */
    fun settingsBody(s0: InvoiceSettings): JSONObject {
        val s = cleanSettings(s0)
        val rate = s.gstRate.toDouble()
        return JSONObject()
            .put("sellerName", s.sellerName).put("legalName", s.legalName).put("gstin", s.gstin).put("address", s.address)
            .put("stateCode", s.stateCode).put("stateName", s.stateName).put("phone", s.phone).put("email", s.email)
            .put("website", s.website).put("pan", s.pan).put("bankName", s.bankName).put("accountName", s.accountName)
            .put("accountNumber", s.accountNumber).put("ifsc", s.ifsc).put("upi", s.upi).put("sac", s.sac)
            .put("gstRate", if (rate % 1.0 == 0.0) rate.toLong() else rate)
            .put("prefixGst", s.prefixGst).put("prefixPlain", s.prefixPlain).put("prefixReceipt", s.prefixReceipt)
            .put("terms", s.terms).put("footer", s.footer).put("signatory", s.signatory)
    }

    /* ---- making an invoice ---- */

    /**
     * POST /admin/api/invoices {action:'create'}. From a payment no lines are sent (the service makes the line from
     * the payment); for a service every line is sent as typed. The service does every sum.
     */
    fun createBody(d: InvoiceDraft): JSONObject {
        val cu = d.customer
        val gstin = cu.gstin.trim().uppercase()
        if (cu.name.isBlank()) throw Refused("Type the customer’s name.")
        if (gstin.isNotEmpty() && !GstStates.GSTIN.matches(gstin)) throw Refused("The GSTIN must be 15 letters and digits (or leave it empty).")
        if (d.gst && cu.stateCode.isBlank()) throw Refused("Choose the customer’s state — it is the place of supply.")
        val customer = JSONObject().put("name", cu.name.trim()).put("gstin", gstin).put("address", cu.address.trim())
            .put("email", cu.email.trim()).put("phone", cu.phone.trim())
        if (cu.stateCode.isNotBlank()) customer.put("stateCode", cu.stateCode.trim())
        val b = JSONObject().put("action", "create").put("kind", if (d.fromPayment) d.kind else "INVOICE").put("gst", d.gst)
            .put("software", d.software).put("customer", customer).put("inclusive", d.gst && d.inclusive)
        d.companyId?.let { b.put("companyId", it) }
        d.remoteId?.let { b.put("remoteId", it) }
        if (d.date.isNotBlank()) b.put("date", d.date.trim())
        if (d.notes.isNotBlank()) b.put("notes", d.notes.trim())
        if (d.fromPayment) {
            b.put("paymentId", d.paymentId ?: throw Refused("That payment is no longer there."))
        } else {
            if (d.lines.isEmpty()) throw Refused("Add a line.")
            val lines = JSONArray()
            d.lines.forEachIndexed { i, l ->
                val n = i + 1
                val qty = l.qty.trim().replace(",", "").toDoubleOrNull()
                val rate = l.rate.trim().replace(",", "").toDoubleOrNull()
                if (l.description.isBlank()) throw Refused("Line $n: type what it is for.")
                if (qty == null || qty <= 0) throw Refused("Line $n: the quantity must be more than 0.")
                if (l.rate.isBlank() || rate == null || rate < 0) throw Refused("Line $n: type the rate.")
                val o = JSONObject().put("description", l.description.trim())
                    .put("qty", if (qty % 1.0 == 0.0) qty.toLong() else qty)
                    .put("rate", if (rate % 1.0 == 0.0) rate.toLong() else rate)
                if (d.gst && l.sac.isNotBlank()) o.put("sac", l.sac.trim())
                lines.put(o)
            }
            b.put("lines", lines)
        }
        return b
    }

    /** POST /admin/api/invoices {action:'cancel'} — never edited or deleted; a reason is required and printed. */
    fun cancelBody(inv: Invoice, reason: String): JSONObject {
        if (inv.cancelled) throw Refused("This invoice is already cancelled.")
        if (reason.trim().length < 3) throw Refused("Write the reason for cancelling.")
        return JSONObject().put("action", "cancel").put("id", inv.idRaw).put("reason", reason.trim())
    }

    /** "1 year", "2 years", "180 days" — one period as the service line says it. */
    fun per(days: Int): String = when {
        days % 365 == 0 && days / 365 == 1 -> "1 year"
        days % 365 == 0 -> "${days / 365} years"
        else -> "$days days"
    }

    /** A service invoice's lines, from the customer's plan on [sw] (desktop planLines): the plan, and extra users above it. */
    fun planLines(x: Customer, sw: String, plan: Plan?, sac: String): List<LineDraft> {
        val seats = (if (sw == Software.WEIGHT) x.w?.seats else x.remote(sw)?.seats)?.takeIf { it > 0 } ?: 1
        val per = per(plan?.periodDays ?: 365)
        val incl = plan?.usersIncluded?.takeIf { it > 0 }?.let { minOf(it, seats) } ?: seats
        val demo = x.sub(sw)?.state == "DEMO"
        val price = plan?.let { if (demo) it.priceFirst else it.priceRenewal }
        val planName = plan?.name ?: Plans.wordOf(Customers.planCode(x, sw))
        val out = mutableListOf(
            LineDraft(Software.fullName(sw) + " — " + planName + " plan, " + per + ", " + incl + (if (incl == 1) " user" else " users"),
                sac, "1", PayQuote.typed(price))
        )
        val extra = plan?.extraUserPrice
        if (extra != null && extra > 0 && seats > incl) out += LineDraft("Extra users above the plan, $per", sac, (seats - incl).toString(), PayQuote.typed(extra))
        return out
    }

    /** The customer block, from the customer — with the address their last invoice carried. */
    fun partyOf(x: Customer, sw: String, past: List<Invoice>, settings: InvoiceSettings?): InvParty {
        val g = x.gstin?.trim()?.uppercase().orEmpty()
        val last = past.firstOrNull { (g.isNotEmpty() && it.customer.gstin.equals(g, true)) || it.customer.name == x.name }
        val r = if (sw == Software.WEIGHT) null else x.remote(sw)
        return InvParty(
            name = x.name, gstin = g, address = last?.customer?.address.orEmpty(),
            stateCode = GstStates.ofGstin(g) ?: last?.customer?.stateCode?.takeIf { it.isNotBlank() } ?: settings?.stateCode.orEmpty(),
            email = x.email ?: r?.email.orEmpty(), phone = x.phone ?: r?.phone.orEmpty()
        )
    }
}

/** The Invoices list's filters. */
object InvFilter {
    val DOCS = listOf("all" to "Any", "gst" to "With GST", "plain" to "Without GST", "receipt" to "Receipts")
    val STATES = listOf("all" to "Any status", "ok" to "Issued", "x" to "Cancelled")

    fun apply(list: List<Invoice>, sw: String?, doc: String, status: String, month: String?, term: String): List<Invoice> =
        list.filter { i ->
            (sw == null || i.software == sw) &&
                when (doc) {
                    "receipt" -> i.receipt
                    "gst" -> i.gst && !i.receipt
                    "plain" -> !i.gst && !i.receipt
                    else -> true
                } &&
                (month == null || i.date.orEmpty().take(7) == month) &&
                when (status) { "x" -> i.cancelled; "ok" -> !i.cancelled; else -> true } &&
                i.matches(term)
        }.sortedWith(compareByDescending<Invoice> { it.date.orEmpty() }.thenByDescending { it.number })

    /** The months that have an invoice, newest first ("2026-11"). */
    fun months(list: List<Invoice>): List<String> = list.mapNotNull { it.date?.take(7)?.takeIf { m -> m.length == 7 } }.distinct().sortedDescending()
}
