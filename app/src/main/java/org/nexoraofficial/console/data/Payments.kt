package org.nexoraofficial.console.data

import org.json.JSONObject
import java.time.LocalDate

/* ======================================================================
   2.0.0 — WHAT EACH CUSTOMER PAID, AND THE VALIDITY IT BOUGHT.

   Owner, 2026-10-08: "customer ni validity payment kyare aavyu kya plan nu
   kayo plan expire thay che aena record". The owner's own ledger, kept by
   the service (payments.js): which customer, which software and plan, how
   much, when it came and how, a reference, and the validity it covers.
   Recording one can renew the licence in the same step (extendDays). A
   payment is never erased: Delete takes it off the list and the service
   keeps it.

   GET  /admin/api/payments → {payments:[…], totals:{count, amount, bySoftware}}
   POST /admin/api/payments {action: add | update | delete, …}
   ====================================================================== */

/** One payment, as the service lists it. Dates are plain days ("2026-10-08"). */
data class Payment(
    val id: Int,
    val software: String,
    /** The Sales & Costing company it belongs to (also set on a Fabric Stock payment of a customer who has both). */
    val companyId: Int?,
    val fabricId: Int?,
    val customer: String,
    val plan: String?,
    val planName: String?,
    val kind: String,
    val amount: Double?,
    val paidOn: String?,
    val mode: String?,
    val reference: String?,
    val validFrom: String?,
    val validTo: String?,
    val note: String?,
    val createdAt: String?,
    /** "web" or "android": which console recorded it; null from before the service kept it. */
    val via: String?
) {
    val softwareName: String get() = Software.name(software)
    val kindText: String get() = PaymentKinds.word(kind)
    val modeText: String get() = PaymentModes.word(mode)

    /** "10 Feb 26 → 10 Jun 27", "to 13 Sep 27", or a dash — the validity it bought. */
    val validityText: String
        get() = when {
            validTo == null -> "—"
            validFrom != null -> Fmt.day(validFrom) + " → " + Fmt.day(validTo)
            else -> "to " + Fmt.day(validTo)
        }

    /** Who recorded it, in words. */
    val viaText: String?
        get() = when (via) {
            "android" -> "phone console"
            null, "" -> null
            else -> "web console"
        }

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase().trim()
        return listOf(customer, reference, note, planName).any { it?.lowercase()?.contains(t) == true }
    }

    /** Whether it is this customer's (by its Sales & Costing or its Fabric Stock licence). */
    fun of(c: Customer): Boolean =
        (c.w != null && companyId == c.w.id) || (c.f != null && fabricId == c.f.id)

    companion object {
        fun from(o: JSONObject) = Payment(
            id = o.optInt("id"),
            software = o.str("software") ?: Software.WEIGHT,
            companyId = o.str("companyId")?.trim()?.toIntOrNull(),
            fabricId = o.str("fabricId")?.trim()?.toIntOrNull(),
            customer = o.str("customer") ?: "-",
            plan = o.str("plan"),
            planName = o.str("planName"),
            kind = o.str("kind") ?: "OTHER",
            amount = o.money("amount"),
            paidOn = o.str("paidOn")?.take(10),
            mode = o.str("mode"),
            reference = o.str("reference"),
            validFrom = o.str("validFrom")?.take(10),
            validTo = o.str("validTo")?.take(10),
            note = o.str("note"),
            createdAt = o.str("createdAt"),
            via = o.str("via")
        )
    }
}

/** What a payment was for (the web console's KIND_WORDS). */
object PaymentKinds {
    val ALL = listOf("NEW", "RENEWAL", "EXTRA_USERS", "UPGRADE", "OTHER")
    fun word(k: String?): String = when (k) {
        "NEW" -> "New customer"
        "RENEWAL" -> "Renewal"
        "EXTRA_USERS" -> "Extra users"
        "UPGRADE" -> "Plan upgrade"
        "OTHER" -> "Other"
        else -> k ?: "-"
    }
}

/** How it came. */
object PaymentModes {
    val ALL = listOf("UPI", "BANK", "CASH", "CHEQUE", "CARD", "OTHER")
    fun word(m: String?): String = when (m) {
        "UPI" -> "UPI"
        "BANK" -> "Bank transfer"
        "CASH" -> "Cash"
        "CHEQUE" -> "Cheque"
        "CARD" -> "Card"
        "OTHER" -> "Other"
        null, "" -> "—"
        else -> m
    }
}

data class PaymentTotals(val count: Int = 0, val amount: Double = 0.0, val bySoftware: Map<String, Double> = emptyMap())

/** Everything GET /admin/api/payments answers with, newest first. */
data class PaymentsData(val payments: List<Payment> = emptyList(), val totals: PaymentTotals = PaymentTotals()) {
    fun byId(id: Int): Payment? = payments.find { it.id == id }

    /** A customer's payments, newest first; [sw] narrows them to one software. */
    fun of(c: Customer, sw: String? = null): List<Payment> = payments.filter { it.of(c) && (sw == null || it.software == sw) }

    /** The newest payment of a customer on one software (the validity list's "last payment"). */
    fun last(c: Customer, sw: String): Payment? = of(c, sw).firstOrNull()

    companion object {
        fun from(o: JSONObject): PaymentsData {
            val a = o.optJSONArray("payments")
            val list = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Payment.from(it) } }
            val t = o.optJSONObject("totals")
            val by = LinkedHashMap<String, Double>()
            t?.optJSONObject("bySoftware")?.let { b -> b.keys().forEach { k -> b.money(k)?.let { by[k] = it } } }
            return PaymentsData(
                payments = list,
                totals = PaymentTotals(
                    count = t?.optInt("count", list.size) ?: list.size,
                    amount = t?.money("amount") ?: list.sumOf { it.amount ?: 0.0 },
                    bySoftware = by
                )
            )
        }
    }
}

/* ---- the Payments list's quick views ---- */

enum class PayQuick(val label: String) {
    ALL("All"), TODAY("Today"), LAST30("Last 30 days"), MONTH("This month"), FY("This financial year")
}

object PayFilter {
    /** The financial year's first day: 1 April of this year from April on, of last year before it. */
    fun fyStart(today: LocalDate): LocalDate =
        LocalDate.of(if (today.monthValue >= 4) today.year else today.year - 1, 4, 1)

    /** The days a quick view covers, as "YYYY-MM-DD" (from, to); nulls for All. */
    fun range(q: PayQuick, today: LocalDate): Pair<String?, String?> = when (q) {
        PayQuick.ALL -> null to null
        PayQuick.TODAY -> today.toString() to today.toString()
        PayQuick.LAST30 -> today.minusDays(30).toString() to today.toString()
        PayQuick.MONTH -> today.withDayOfMonth(1).toString() to today.toString()
        PayQuick.FY -> fyStart(today).toString() to today.toString()
    }

    /** What the Payments list shows: the quick view's days, the search, the software and the kind. */
    fun apply(
        list: List<Payment>, q: PayQuick, today: LocalDate,
        term: String = "", software: String? = null, kind: String? = null
    ): List<Payment> {
        val (from, to) = range(q, today)
        return list.filter { p ->
            val d = p.paidOn.orEmpty()
            p.matches(term) &&
                (software == null || p.software == software) &&
                (kind == null || p.kind == kind) &&
                (from == null || d >= from) && (to == null || d <= to)
        }
    }

    fun sum(list: List<Payment>): Double = list.sumOf { it.amount ?: 0.0 }
}

/* ---- the Record payment form ---- */

/** The Record payment form, and the one a payment is edited in. Dates "YYYY-MM-DD" or blank. */
data class PaymentForm(
    /** 0 for a new payment. */
    val id: Int = 0,
    val customerKey: String = "",
    val software: String = Software.WEIGHT,
    val kind: String = "RENEWAL",
    val amount: String = "",
    val paidOn: String = "",
    val mode: String = "UPI",
    val reference: String = "",
    /** Renew the licence with it: 0 = no, else days (365, 730, 180, 30). */
    val extendDays: Int = 365,
    val validFrom: String = "",
    val validTo: String = "",
    val note: String = ""
) {
    companion object {
        /** The renewals offered, in the order the web console offers them. */
        val RENEWALS = listOf(0, 365, 730, 180, 30)

        fun renewalText(days: Int, demo: Boolean): String = when (days) {
            0 -> "No — only record the payment"
            365 -> "Yes — 1 year" + if (demo) " (makes it licensed)" else " added"
            730 -> "Yes — 2 years"
            180 -> "Yes — 6 months"
            30 -> "Yes — 30 days"
            else -> "Yes — $days days"
        }

        /** A payment as the form shows it for editing. */
        fun of(p: Payment) = PaymentForm(
            id = p.id,
            software = p.software,
            kind = p.kind,
            amount = p.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty(),
            paidOn = p.paidOn.orEmpty(),
            mode = p.mode ?: "OTHER",
            reference = p.reference.orEmpty(),
            extendDays = 0,
            validFrom = p.validFrom.orEmpty(),
            validTo = p.validTo.orEmpty(),
            note = p.note.orEmpty()
        )
    }
}
