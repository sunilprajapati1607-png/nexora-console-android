package org.nexoraofficial.console.data

import java.time.Instant

/* ======================================================================
   2.0.0 — ONE CUSTOMER, EVERY SOFTWARE (owner, 2026-10-08).

   "by customer pan joi sakay ane by software wise pan joi sakay" — the
   console shows each customer once, with every software it uses side by
   side (Sales & Costing, Fabric Stock; Jobwork when it has a licence), and
   also each software on its own. Each software keeps its own licence: a
   customer is a Sales & Costing company and the Fabric Stock company that
   belongs to it (the same GSTIN, or linked by hand), or either one alone.

   Everything here is read from what the console already holds — the
   licences listing, Fabric Stock's companies (products), the plans and the
   payments — exactly as the web console's customers(), validityRows() and
   renderSoftware() read it.
   ====================================================================== */

/** One software licence of a customer, as the lists sort and colour it. */
data class Sub(val sw: String, val state: String, val at: String?, val left: Int) {
    val live: Boolean get() = state == "LICENSED" || state == "DEMO"
}

data class Customer(
    /** "w12" for a Sales & Costing company (with its Fabric Stock, if any), "f7" for a Fabric Stock company alone. */
    val key: String,
    val name: String,
    val gstin: String?,
    val email: String?,
    val phone: String?,
    val w: Company?,
    val f: FabricCompany?,
    val self: Boolean,
    val since: String?
) {
    val subs: List<Sub> = listOfNotNull(
        w?.let { Sub(Software.WEIGHT, it.swState, it.expiresAt, it.daysLeft) },
        f?.let { Sub(Software.FABRIC, it.shownState, it.expiresAt, it.daysLeft) }
    )

    /** The licence that ends first (a suspended one is not counted). */
    val next: Sub? = subs.filter { it.state != "SUSPENDED" }.minByOrNull { moment(it.at) ?: Instant.MAX }

    /** A licence of theirs that runs and ends within 30 days ("Renew in 30 days"). */
    val soon: Boolean = subs.any { it.state != "SUSPENDED" && it.state != "EXPIRED" && it.left <= 30 }

    val both: Boolean get() = w != null && f != null

    fun has(sw: String): Boolean = if (sw == Software.FABRIC) f != null else w != null

    fun sub(sw: String): Sub? = subs.find { it.sw == sw }

    /** The people on its seats: Sales & Costing's and Fabric Stock's. */
    val people: Int get() = (w?.usersTotal ?: 0) + (f?.people ?: 0)

    /** "Customer · 2 software · since 08 Oct 26 · registered by the plant itself" */
    val subtitle: String
        get() = "Customer · ${subs.size} software" + (since?.let { " · since " + Fmt.day(it) } ?: "") +
            (if (self) " · registered by the plant itself" else "")

    /** Whether the next renewal is the same day on every software it uses. */
    val nextOnBoth: Boolean
        get() = subs.size > 1 && next != null && subs.all { it.at?.take(10) == next.at?.take(10) }

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase().trim()
        return listOf(name, gstin, email, phone, w?.licenceKey, w?.loginId, f?.licenceKey, f?.loginId, f?.name)
            .any { it?.lowercase()?.contains(t) == true }
    }
}

/** The quick views of the Customers list (the web console's Q). DELETED is the archive (CompanyView). */
enum class CustQuick(val label: String) {
    ALL("All"), SOON("Renew in 30 days"), DEMO("On a demo"), BOTH("Both software"),
    SUSP("Suspended"), SELF("Self-registered")
}

object Customers {

    /** Every customer once: each Sales & Costing company with its Fabric Stock, then Fabric Stock alone. */
    fun of(weight: List<Company>, fabric: List<FabricCompany>): List<Customer> {
        val link = CompanyList.linked(weight, fabric)
        val out = ArrayList<Customer>()
        weight.forEach { c ->
            val f = link[c.id]
            out += Customer(
                key = "w${c.id}", name = c.name, gstin = c.gstin ?: f?.gstin, email = c.email ?: f?.email, phone = c.phone ?: f?.phone,
                w = c, f = f, self = c.selfRegistered, since = c.createdAt ?: c.registeredAt
            )
        }
        CompanyList.fabricOnly(weight, fabric).forEach { f ->
            out += Customer(
                key = "f${f.id}", name = f.name, gstin = f.gstin, email = f.email, phone = f.phone,
                w = null, f = f, self = f.selfRegistered, since = f.createdAt
            )
        }
        return out
    }

    /** One customer by its key; a key that has changed (a Fabric Stock customer that now has Sales & Costing) is found by either licence. */
    fun find(list: List<Customer>, key: String): Customer? {
        list.find { it.key == key }?.let { return it }
        val id = key.drop(1).toIntOrNull() ?: return null
        return when (key.firstOrNull()) {
            'f' -> list.find { it.f?.id == id }
            'w' -> list.find { it.w?.id == id }
            else -> null
        }
    }

    fun count(list: List<Customer>, q: CustQuick): Int = list.count { keep(it, q) }

    private fun keep(x: Customer, q: CustQuick): Boolean = when (q) {
        CustQuick.ALL -> true
        CustQuick.SOON -> x.soon
        CustQuick.DEMO -> x.subs.any { it.state == "DEMO" }
        CustQuick.BOTH -> x.both
        CustQuick.SUSP -> x.subs.any { it.state == "SUSPENDED" }
        CustQuick.SELF -> x.self
    }

    /**
     * What the Customers list shows: the quick view, the search — and, opened from the dashboard's
     * "licences ending soon", only paying Sales & Costing licences ending within 15 days ([endingOnly]).
     * The one that renews first comes first.
     */
    fun filter(list: List<Customer>, q: CustQuick, term: String, endingOnly: Boolean = false): List<Customer> =
        list.filter { x -> x.matches(term) && (if (endingOnly) x.w?.endingSoon == true else keep(x, q)) }
            .sortedWith(compareBy<Customer> { moment(it.next?.at) ?: Instant.MAX }.thenBy { it.name.lowercase() })

    /** licensed, demo, ended, suspended — the web console's stWord. */
    fun stateWord(s: String): String = when (s) {
        "LICENSED" -> "licensed"
        "DEMO" -> "demo"
        "EXPIRED" -> "ended"
        "SUSPENDED" -> "suspended"
        else -> s.lowercase()
    }

    /** "suspended", "ended 01 Sep 26", "demo · 5 days", "245 days", "ends today" — the web console's daysText. */
    fun daysText(state: String, left: Int, at: String?): String = when (state) {
        "SUSPENDED" -> "suspended"
        "EXPIRED" -> "ended " + Fmt.day(at)
        else -> (if (state == "DEMO") "demo · " else "") + when (left) {
            0 -> "ends today"
            1 -> "1 day"
            else -> "$left days"
        }
    }

    /**
     * "Sales & Costing · Standard ± 2 · 245 days" — one software of a customer, on one line.
     * 2.0.1 — Fabric Stock's own changes over its plan show as "± n" too (its 0.8.1 service).
     */
    fun line(x: Customer, sw: String, plans: PlansData?): String? {
        return if (sw == Software.WEIGHT) {
            val c = x.w ?: return null
            Software.WEIGHT_NAME + " · " + Plans.nameOf(plans, sw, c.plan) + own(c.featureOverrides.size) + " · " +
                daysText(c.swState, c.daysLeft, c.expiresAt)
        } else {
            val f = x.f ?: return null
            Software.FABRIC_NAME + " · " + Plans.nameOf(plans, sw, f.plan, f.planName) + own(f.featureOverrides.size) + " · " +
                daysText(f.shownState, f.daysLeft, f.expiresAt)
        }
    }

    /** " ± 2" after a plan's name when a customer has features of its own over it; nothing when it has none. */
    fun own(n: Int): String = if (n > 0) " ± $n" else ""

    /** How many features of its own [x] has over its plan on [sw]. */
    fun ownCount(x: Customer, sw: String): Int =
        if (sw == Software.WEIGHT) x.w?.featureOverrides?.size ?: 0 else x.f?.featureOverrides?.size ?: 0

    /** "Standard ± 3 · 245 days" — the same without the software's name (the customer's software tabs). */
    fun tabLine(x: Customer, sw: String, plans: PlansData?): String? =
        line(x, sw, plans)?.substringAfter(" · ")
}

/* ---- the validity list ---- */

/** One licence of one customer, as the Validity & renewals list shows it. */
data class ValidityRow(
    val c: Customer,
    val sw: String,
    val plan: String,
    val state: String,
    val start: String?,
    val ends: String?,
    val left: Int,
    val seats: Int,
    val pay: Payment?
) {
    val live: Boolean get() = state == "LICENSED" || state == "DEMO"
}

enum class ValQuick(val label: String) {
    ALL("All"), D30("Ending in 30 days"), D7("In 7 days"), ENDED("Ended"), DEMO("Demos"), SUSP("Suspended")
}

object Validity {
    /** One row per software per customer, the one ending first at the top. */
    fun rows(customers: List<Customer>, plans: PlansData?, payments: PaymentsData?): List<ValidityRow> {
        val out = ArrayList<ValidityRow>()
        customers.forEach { x ->
            x.w?.let { c ->
                out += ValidityRow(x, Software.WEIGHT, Plans.nameOf(plans, Software.WEIGHT, c.plan), c.swState,
                    c.periodStartedAt, c.expiresAt, c.daysLeft, c.seats, payments?.last(x, Software.WEIGHT))
            }
            x.f?.let { f ->
                out += ValidityRow(x, Software.FABRIC, Plans.nameOf(plans, Software.FABRIC, f.plan, f.planName), f.shownState,
                    f.periodStartedAt ?: f.createdAt, f.expiresAt, f.daysLeft, f.seats, payments?.last(x, Software.FABRIC))
            }
        }
        return out.sortedWith(compareBy<ValidityRow> { moment(it.ends) ?: Instant.MAX }.thenBy { it.c.name.lowercase() })
    }

    fun keep(r: ValidityRow, q: ValQuick): Boolean = when (q) {
        ValQuick.ALL -> true
        ValQuick.D30 -> r.live && r.left <= 30
        ValQuick.D7 -> r.live && r.left <= 7
        ValQuick.ENDED -> r.state == "EXPIRED"
        ValQuick.DEMO -> r.state == "DEMO"
        ValQuick.SUSP -> r.state == "SUSPENDED"
    }

    fun filter(rows: List<ValidityRow>, q: ValQuick, term: String = "", sw: String? = null): List<ValidityRow> {
        val t = term.lowercase().trim()
        return rows.filter { r ->
            keep(r, q) && (sw == null || r.sw == sw) &&
                (t.isEmpty() || listOf(r.c.name, r.c.gstin).any { it?.lowercase()?.contains(t) == true })
        }
    }
}

/* ---- one software on its own ---- */

enum class SwQuick { ALL, D30, DEMO, ENDED, SUSP, BOTH }

object BySoftware {
    /** That software's customers, the one ending first at the top. */
    fun customers(all: List<Customer>, sw: String): List<Customer> =
        all.filter { it.has(sw) }.sortedWith(compareBy<Customer> { moment(it.sub(sw)?.at) ?: Instant.MAX }.thenBy { it.name.lowercase() })

    fun keep(x: Customer, sw: String, q: SwQuick): Boolean {
        val s = x.sub(sw) ?: return false
        return when (q) {
            SwQuick.ALL -> true
            SwQuick.D30 -> s.live && s.left <= 30
            SwQuick.DEMO -> s.state == "DEMO"
            SwQuick.ENDED -> s.state == "EXPIRED"
            SwQuick.SUSP -> s.state == "SUSPENDED"
            SwQuick.BOTH -> x.both
        }
    }

    fun label(q: SwQuick, sw: String): String = when (q) {
        SwQuick.ALL -> "All"
        SwQuick.D30 -> "Ending in 30 days"
        SwQuick.DEMO -> "On a demo"
        SwQuick.ENDED -> "Ended"
        SwQuick.SUSP -> "Suspended"
        SwQuick.BOTH -> "Also on " + Software.name(Software.other(sw))
    }

    fun filter(list: List<Customer>, sw: String, q: SwQuick, term: String): List<Customer> =
        list.filter { keep(it, sw, q) && it.matches(term) }
}

/* ---- a customer's features over its plan ---- */

/** How one feature stands for one customer: from its plan, added for it, taken off for it, or not in its plan. */
enum class FeatureTag(val word: String) {
    PLAN("from plan"), ADDED("+ added"), OFF("− off"), NONE("not in plan")
}

object Features {
    /** On or off: a demo has every feature; else the customer's own change if there is one, else its plan's tick. */
    fun effective(planOn: Boolean, override: Boolean?, demo: Boolean = false): Boolean = demo || (override ?: planOn)

    fun tag(planOn: Boolean, override: Boolean?): FeatureTag = when {
        override == true && !planOn -> FeatureTag.ADDED
        override == false && planOn -> FeatureTag.OFF
        planOn -> FeatureTag.PLAN
        else -> FeatureTag.NONE
    }

    /** The customer's own changes with the ones being edited laid over them ([draft]: null = back to the plan). */
    fun merge(own: Map<String, Boolean>, draft: Map<String, Boolean?>): Map<String, Boolean> {
        val out = LinkedHashMap(own)
        draft.forEach { (k, v) -> if (v == null) out.remove(k) else out[k] = v }
        return out
    }

    /**
     * One tap on a feature while editing: it turns to the other reading, and the draft keeps only what
     * differs from the plan (the web console: draft[id] = next === plan ? null : next).
     */
    fun toggle(id: String, plan: Map<String, Boolean>, own: Map<String, Boolean>, draft: Map<String, Boolean?>): Map<String, Boolean?> {
        val p = plan[id] == true
        val eff = merge(own, draft)[id] ?: p
        val next = !eff
        return draft + (id to (if (next == p) null else next))
    }

    /** "Back to the plan only": every feature back to its plan. */
    fun resetAll(catalogue: List<FeatureDef>): Map<String, Boolean?> = catalogue.associate { it.id to null }

    /** "Standard gives 12 of 21 · +2 added · −1 off → 13 on" — without the demo (a demo's own line says it has everything). */
    data class Summary(val fromPlan: Int, val added: Int, val off: Int, val on: Int, val total: Int)

    fun summary(catalogue: List<FeatureDef>, plan: Map<String, Boolean>, own: Map<String, Boolean>): Summary {
        var fromPlan = 0; var added = 0; var off = 0; var on = 0
        catalogue.forEach { f ->
            val p = plan[f.id] == true
            val o = own[f.id]
            if (p) fromPlan++
            when (tag(p, o)) {
                FeatureTag.ADDED -> added++
                FeatureTag.OFF -> off++
                else -> Unit
            }
            if (effective(p, o)) on++
        }
        return Summary(fromPlan, added, off, on, catalogue.size)
    }

    /** The draft as it is sent: only what changes the customer's own list (a null that removes nothing is dropped). */
    fun changes(own: Map<String, Boolean>, draft: Map<String, Boolean?>): Map<String, Boolean?> =
        draft.filter { (k, v) -> if (v == null) own.containsKey(k) else own[k] != v }

    /** 2.0.1 — a Sales & Costing licence's features over its plan. */
    fun viewOf(data: PlansData?, settings: ServiceSettings, c: Company) = FeatureView(
        sw = Software.WEIGHT,
        name = c.name,
        planName = Plans.nameOf(data, Software.WEIGHT, c.plan),
        catalogue = Plans.catalogue(data, Software.WEIGHT),
        plan = Plans.ticksOf(data, settings, Software.WEIGHT, c.plan),
        own = c.featureOverrides,
        demo = c.isDemo
    )

    /** 2.0.1 — a Fabric Stock licence's features over its plan (its own catalogue and plans, from its own service). */
    fun viewOf(data: PlansData?, settings: ServiceSettings, f: FabricCompany) = FeatureView(
        sw = Software.FABRIC,
        name = f.name,
        planName = Plans.nameOf(data, Software.FABRIC, f.plan, f.planName),
        catalogue = Plans.catalogue(data, Software.FABRIC),
        plan = Plans.ticksOf(data, settings, Software.FABRIC, f.plan),
        own = f.featureOverrides,
        /* Fabric Stock's own rule: is_demo, whatever the state says (its service's resolveFeatures) */
        demo = f.isDemo
    )
}

/**
 * 2.0.1 — ONE LICENCE'S FEATURES OVER ITS PLAN, WHICHEVER SOFTWARE IT IS.
 *
 * What the customer window's Features tab draws, for Sales & Costing and for Fabric Stock alike (the web
 * console's featBody(sw, l)): each comes with its own catalogue and its own plan's ticks, so the two are
 * never mixed. The rules are the ones above — a demo has every feature; else the customer's own change,
 * else the plan's tick — and a tap while editing keeps only what differs from the plan.
 */
data class FeatureView(
    val sw: String,
    /** The customer's name ("for … only"). */
    val name: String,
    val planName: String,
    val catalogue: List<FeatureDef>,
    /** What its plan gives. */
    val plan: Map<String, Boolean>,
    /** Its own changes as saved: true added, false off. */
    val own: Map<String, Boolean>,
    val demo: Boolean
) {
    /** The groups, in the order the tab shows them (Sales & Costing's own order; any other software's as it sent them). */
    val groups: List<String> get() = groupsOf(catalogue, sw)

    /** Its own changes with [draft] (the changes being made in Edit) laid over them. */
    fun ownWith(draft: Map<String, Boolean?>?): Map<String, Boolean> = if (draft == null) own else Features.merge(own, draft)

    /** "Standard gives 16 of 16 · +0 added · −1 off → 15 on". */
    fun summary(draft: Map<String, Boolean?>? = null): Features.Summary = Features.summary(catalogue, plan, ownWith(draft))

    /** Whether [id] is on for the plant: a demo has every feature; else its own change, else its plan's tick. */
    fun on(id: String): Boolean = Features.effective(plan[id] == true, own[id], demo)

    /** Every feature of its catalogue, on or off, as the application gets it. */
    val effective: Map<String, Boolean> get() = catalogue.associate { it.id to on(it.id) }

    /** One tap on [id] while editing. */
    fun toggle(id: String, draft: Map<String, Boolean?>): Map<String, Boolean?> = Features.toggle(id, plan, own, draft)

    /** "Back to the plan only". */
    fun reset(): Map<String, Boolean?> = Features.resetAll(catalogue)
}
