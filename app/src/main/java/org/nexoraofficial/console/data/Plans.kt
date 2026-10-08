package org.nexoraofficial.console.data

import org.json.JSONArray
import org.json.JSONObject

/* ======================================================================
   2.0.0 — EVERY SOFTWARE ITS OWN PLANS (owner, 2026-10-08).

   "plan pan hu create kri saku darek software wise" — the owner makes the
   plans, per software; "price open rakho" — a price stays open (null) until
   the owner fills it; and per customer a feature can be added or taken off
   over its plan ("+ added / − off": yes).

   Each software has its OWN plans and its OWN features: a Sales & Costing
   feature means nothing in Fabric Stock, so the two are never mixed.
   Fabric Stock's own service has no plans yet — the service says so
   (supported:false), and the console shows its words.

   2.0.1 — Fabric Stock's own service (0.8.1) has plans and features now,
   in exactly the shape Sales & Costing's come in (supported:true, 16
   features in the groups Production, Stock & dispatch, Reports, Company;
   Standard built in with every feature on). Its groups are shown in the
   order they come, as the web console shows them; an older Fabric Stock
   service (supported:false) keeps the 2.0.0 behaviour — every company on
   Standard, no Features tab.

   GET /admin/api/plans → {"software":[{id, name, short, ok, supported,
     features:[{id,label,group}], plans:[{code, name, note, priceFirst,
     priceRenewal, usersIncluded, extraUserPrice, features, active, sort,
     createdAt, customers, changed, builtIn}], demo, message}]}
   Read with the same null-safe readers as Models.kt.
   ====================================================================== */

/** One feature of one software, in the group the plan and customer screens put it under. */
data class FeatureDef(val id: String, val label: String, val group: String)

/** The order the groups are shown in (the web console's GROUP_ORDER). */
val FEATURE_GROUP_ORDER = listOf("Calculation", "Sales", "Cost tools", "Output", "Company", "Other", "Features")

/** Where each Sales & Costing feature sits, for a service that sends no groups (server plans.js FEATURE_GROUP). */
val FEATURE_GROUP: Map<String, String> = mapOf(
    "bagView" to "Calculation", "ink" to "Calculation", "bomWorkflow" to "Calculation", "sectionSuggest" to "Calculation",
    "onlinePrices" to "Calculation", "priceHistory" to "Calculation",
    "quotation" to "Sales", "marketing" to "Sales", "sharing" to "Sales",
    "priceImpact" to "Cost tools", "compare" to "Cost tools", "targetCost" to "Cost tools",
    "exportExcel" to "Output", "exportPdf" to "Output", "numberSeries" to "Output", "tableSettings" to "Output",
    "chat" to "Company", "notes" to "Company", "activityLog" to "Company", "backup" to "Company", "mobile" to "Company"
)

/** The groups present in [features], in the order they are shown (Sales & Costing's order). */
fun groupsOf(features: List<FeatureDef>): List<String> =
    features.map { it.group }.distinct().sortedBy { g -> FEATURE_GROUP_ORDER.indexOf(g).let { if (it < 0) 99 else it } }

/**
 * 2.0.1 — the groups of [sw]'s [features]: Sales & Costing's in the console's own order, any other
 * software's in the order its service sends them (the web console's featBody does the same — Fabric
 * Stock's "Company" would otherwise jump ahead of "Production").
 */
fun groupsOf(features: List<FeatureDef>, sw: String): List<String> =
    if (sw == Software.WEIGHT) groupsOf(features) else features.map { it.group }.distinct()

/** The fallback catalogue (Models.kt PLAN_FEATURES), grouped as the service groups it. */
val FALLBACK_FEATURES: List<FeatureDef> = PLAN_FEATURES.map { FeatureDef(it.id, it.label, FEATURE_GROUP[it.id] ?: "Other") }

/** One plan the owner made (or one of the two every service started with, Standard and Pro). */
data class Plan(
    val code: String,
    val name: String,
    val note: String?,
    /** null = open: the owner has not set it yet. */
    val priceFirst: Double?,
    val priceRenewal: Double?,
    val usersIncluded: Int?,
    val extraUserPrice: Double?,
    val features: Map<String, Boolean>,
    val active: Boolean,
    val sort: Int,
    val createdAt: String?,
    /** Paying customers on it, and how many of them have a feature of their own added or off. */
    val customers: Int,
    val changed: Int,
    /** Standard and Pro: they can be retired, never deleted. */
    val builtIn: Boolean
) {
    /** A plan can be deleted only when it is not built in and nobody is on it (the service refuses otherwise). */
    val canDelete: Boolean get() = !builtIn && customers == 0

    /** How many of [catalogue] it gives. */
    fun onCount(catalogue: List<FeatureDef>): Int = catalogue.count { features[it.id] == true }

    fun gives(id: String): Boolean = features[id] == true

    companion object {
        fun from(o: JSONObject): Plan {
            val code = (o.str("code") ?: o.str("name") ?: "").uppercase()
            val f = o.optJSONObject("features")
            val features = LinkedHashMap<String, Boolean>()
            f?.keys()?.forEach { k -> (f.opt(k) as? Boolean)?.let { features[k] = it } }
            return Plan(
                code = code,
                name = o.str("name") ?: Plans.wordOf(code),
                note = o.str("note"),
                priceFirst = o.money("priceFirst"),
                priceRenewal = o.money("priceRenewal"),
                usersIncluded = o.whole("usersIncluded"),
                extraUserPrice = o.money("extraUserPrice"),
                features = features,
                active = o.optBoolean("active", true),
                sort = o.optInt("sort", 100),
                createdAt = o.str("createdAt"),
                customers = o.optInt("customers", 0).coerceAtLeast(0),
                changed = o.optInt("changed", 0).coerceAtLeast(0),
                builtIn = if (o.has("builtIn")) o.optBoolean("builtIn") else code in Plans.BUILT_IN
            )
        }
    }
}

/** One software's plans, as GET /admin/api/plans lists them. */
data class SoftwarePlans(
    val id: String,
    val name: String,
    val short: String,
    /** false when the service could not reach that software. */
    val ok: Boolean,
    /** false when that software has no plans of its own yet (Fabric Stock before its 0.8.1 service). */
    val supported: Boolean,
    val error: String?,
    val message: String?,
    val features: List<FeatureDef>,
    val plans: List<Plan>,
    val demo: String?
) {
    val groups: List<String> get() = groupsOf(features, id)

    /** The plans offered to a new customer. */
    val livePlans: List<Plan> get() = plans.filter { it.active }

    fun plan(code: String?): Plan? {
        val c = code?.trim()?.uppercase() ?: return null
        return plans.find { it.code == c }
    }

    /** What the owner is told when its plans cannot be shown — the service's own words first. */
    val problem: String
        get() = message ?: when {
            !ok -> "${Software.name(id)} could not be reached."
            else -> "${Software.name(id)} has no plans yet."
        }

    companion object {
        fun from(o: JSONObject): SoftwarePlans {
            val id = o.str("id") ?: ""
            val fa = o.optJSONArray("features")
            val features = if (fa == null) emptyList() else (0 until fa.length()).mapNotNull { i ->
                fa.optJSONObject(i)?.let { f ->
                    val fid = f.str("id") ?: return@let null
                    /* a feature sent without its group: Sales & Costing's own grouping; any other software's
                       ids mean something else, so they go under "Features" (the web console's fallback) */
                    val group = f.str("group") ?: if (id == Software.WEIGHT) FEATURE_GROUP[fid] ?: "Other" else "Features"
                    FeatureDef(fid, f.str("label") ?: fid, group)
                }
            }
            val pa: JSONArray? = o.optJSONArray("plans")
            val plans = if (pa == null) emptyList() else (0 until pa.length()).mapNotNull { i -> pa.optJSONObject(i)?.let { Plan.from(it) } }
                .filter { it.code.isNotEmpty() }
            return SoftwarePlans(
                id = id,
                name = o.str("name") ?: Software.name(id),
                short = Software.name(id),
                ok = o.optBoolean("ok", true),
                supported = o.optBoolean("supported", true),
                error = o.str("error"),
                message = o.str("message"),
                features = features,
                plans = plans.sortedWith(compareBy({ it.sort }, { it.name })),
                demo = o.str("demo")
            )
        }
    }
}

/** Everything GET /admin/api/plans answers with. */
data class PlansData(val software: List<SoftwarePlans> = emptyList()) {
    val weight: SoftwarePlans? get() = block(Software.WEIGHT)
    val fabric: SoftwarePlans? get() = block(Software.FABRIC)

    fun block(id: String): SoftwarePlans? = software.find { it.id == id }

    /** The plans of [sw] that can be shown (none for a software without plans of its own). */
    fun plansOf(sw: String): List<Plan> = block(sw)?.takeIf { it.supported }?.plans.orEmpty()

    fun plan(sw: String, code: String?): Plan? = block(sw)?.takeIf { it.supported }?.plan(code)

    /** Every plan of every software, each with its software's id. */
    val all: List<Pair<String, Plan>>
        get() = plansOf(Software.WEIGHT).map { Software.WEIGHT to it } + plansOf(Software.FABRIC).map { Software.FABRIC to it }

    companion object {
        fun from(o: JSONObject): PlansData {
            val a = o.optJSONArray("software") ?: return PlansData()
            return PlansData((0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { SoftwarePlans.from(it) } })
        }
    }
}

object Plans {
    /** The two plans every service started with. */
    val BUILT_IN = setOf("STANDARD", "PRO")

    /** "GOLD_PLUS" → "Gold plus" — a plan named by its code, when the plan itself is not in hand (planNameOf). */
    fun wordOf(code: String?): String {
        val c = code?.takeIf { it.isNotBlank() } ?: return "-"
        return c.first().uppercaseChar() + c.drop(1).lowercase().replace('_', ' ')
    }

    /**
     * A company's plan by its name: the plan when [data] has it, else the name the company itself came
     * with ([fallback] — Fabric Stock's companies carry "planName" since its 0.8.1 service), else its code
     * in words (Sales & Costing default Pro, Fabric Stock Standard).
     */
    fun nameOf(data: PlansData?, sw: String, code: String?, fallback: String? = null): String {
        val c = code?.takeIf { it.isNotBlank() } ?: if (sw == Software.FABRIC) "STANDARD" else "PRO"
        return data?.plan(sw, c)?.name ?: fallback?.takeIf { it.isNotBlank() } ?: wordOf(c.uppercase())
    }

    /**
     * The features of [sw] — the service's list when it gave one, else (Sales & Costing only) the
     * console's own catalogue.
     */
    fun catalogue(data: PlansData?, sw: String): List<FeatureDef> {
        val b = data?.block(sw)
        if (b != null && b.features.isNotEmpty()) return b.features
        return if (sw == Software.WEIGHT) FALLBACK_FEATURES else emptyList()
    }

    /**
     * What a Sales & Costing plan gives, by code: the plan's own ticks, or — from a service that gives
     * no plans — the older Standard / Pro matrix in the settings.
     */
    fun ticks(data: PlansData?, settings: ServiceSettings, code: String?): Map<String, Boolean> {
        val c = (code ?: "PRO").uppercase()
        data?.plan(Software.WEIGHT, c)?.let { return it.features }
        settings.planFeatures[c]?.let { return it }
        return if (c == "PRO") PLAN_FEATURES.associate { it.id to true } else emptyMap()
    }

    /**
     * 2.0.1 — what a plan of [sw] gives, by code: Sales & Costing's as [ticks] reads them; any other
     * software's from its own plans only (no plan in hand — an older service — gives nothing to show).
     */
    fun ticksOf(data: PlansData?, settings: ServiceSettings, sw: String, code: String?): Map<String, Boolean> =
        if (sw == Software.WEIGHT) ticks(data, settings, code)
        else data?.plan(sw, code?.takeIf { it.isNotBlank() } ?: "STANDARD")?.features.orEmpty()

    /** 2.0.1 — whether [sw]'s service sent plans of its own the console can show and change. */
    fun supported(data: PlansData?, sw: String): Boolean = data?.block(sw)?.supported == true
}

/* ---- money ---- */

/** A price or an amount, or null for "open" (null, missing, empty, or not a number). */
internal fun JSONObject.money(k: String): Double? {
    if (!has(k) || isNull(k)) return null
    return when (val v = opt(k)) {
        is Number -> v.toDouble()
        is String -> v.replace(Regex("[^\\d.]"), "").toDoubleOrNull()
        else -> null
    }?.takeIf { !it.isNaN() && it >= 0 }
}

internal fun JSONObject.whole(k: String): Int? {
    if (!has(k) || isNull(k)) return null
    return when (val v = opt(k)) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }
}

object Money {
    /** "₹1,66,000" (Indian grouping, at most two decimals), or a dash when there is none (the web console's rupees()). */
    fun rupees(n: Double?): String = if (n == null) "—" else "₹" + indian(n)

    /** A price on a plan: the figure, or "open". */
    fun price(n: Double?): String = if (n == null) "open" else rupees(n)

    /** 166000 → "1,66,000"; 1500.5 → "1,500.5". */
    fun indian(n: Double): String {
        val neg = n < 0
        val cents = Math.round(Math.abs(n) * 100)
        val whole = cents / 100
        val frac = (cents % 100).toInt()
        val digits = whole.toString()
        val grouped = if (digits.length <= 3) digits else {
            val head = digits.dropLast(3)
            val tail = digits.takeLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
        }
        val f = when {
            frac == 0 -> ""
            frac % 10 == 0 -> "." + (frac / 10)
            else -> "." + frac.toString().padStart(2, '0')
        }
        return (if (neg) "-" else "") + grouped + f
    }

    /** What the owner typed ("15,000", "₹ 15000.50") as a number; null when it is not one. */
    fun parse(s: String?): Double? {
        val t = s?.replace(Regex("[^\\d.]"), "")?.takeIf { it.isNotEmpty() } ?: return null
        return t.toDoubleOrNull()
    }
}
