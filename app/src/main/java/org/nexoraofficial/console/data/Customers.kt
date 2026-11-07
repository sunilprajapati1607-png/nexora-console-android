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
    /** "w12" for a Sales & Costing company (with its other software, if any); "f7", "e3", "c2" for a customer on the
     *  other software alone — by the first of them it has (Fabric Stock, MSME ERP, Nexora Consult). */
    val key: String,
    val name: String,
    val gstin: String?,
    val email: String?,
    val phone: String?,
    val w: Company?,
    val f: FabricCompany?,
    val self: Boolean,
    val since: String?,
    /** 2.1.0 — its MSME ERP and Nexora Consult companies (any remote software but Fabric Stock), by software. */
    val others: Map<String, FabricCompany> = emptyMap()
) {
    /** 2.1.0 — every remote software it uses, in the table's order (Fabric Stock, MSME ERP, Nexora Consult). */
    val remotes: Map<String, FabricCompany> = LinkedHashMap<String, FabricCompany>().apply {
        Software.REMOTE_IDS.forEach { sw -> (if (sw == Software.FABRIC) f else others[sw])?.let { put(sw, it) } }
    }

    /** Its company on remote software [sw], or null. */
    fun remote(sw: String): FabricCompany? = if (sw == Software.FABRIC) f else others[sw]

    /** The software it uses, Sales & Costing first. */
    val softwares: List<String> get() = (if (w != null) listOf(Software.WEIGHT) else emptyList()) + remotes.keys

    /** The first remote company it has — the one a customer without Sales & Costing is known by. */
    val lead: FabricCompany? get() = remotes.values.firstOrNull()

    val subs: List<Sub> = listOfNotNull(w?.let { Sub(Software.WEIGHT, it.swState, it.expiresAt, it.daysLeft) }) +
        remotes.map { (sw, r) -> Sub(sw, r.shownState, r.expiresAt, r.daysLeft) }

    /** The licence that ends first (a suspended one is not counted). */
    val next: Sub? = subs.filter { it.state != "SUSPENDED" }.minByOrNull { moment(it.at) ?: Instant.MAX }

    /** A licence of theirs that runs and ends within 30 days ("Renew in 30 days"). */
    val soon: Boolean = subs.any { it.state != "SUSPENDED" && it.state != "EXPIRED" && it.left <= 30 }

    /** On two or more software (2.0: Sales & Costing and Fabric Stock both). */
    val both: Boolean get() = subs.size > 1

    fun has(sw: String): Boolean = if (sw == Software.WEIGHT) w != null else remote(sw) != null

    fun sub(sw: String): Sub? = subs.find { it.sw == sw }

    /** The people on its seats, on every software it uses. */
    val people: Int get() = (w?.usersTotal ?: 0) + remotes.values.sumOf { it.people }

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
        return (listOf(name, gstin, email, phone, w?.licenceKey, w?.loginId) +
            remotes.values.flatMap { listOf(it.licenceKey, it.loginId, it.name) })
            .any { it?.lowercase()?.contains(t) == true }
    }
}

/** The quick views of the Customers list (the web console's Q). DELETED is the archive (CompanyView). */
enum class CustQuick(val label: String) {
    ALL("All"), SOON("Renew in 30 days"), DEMO("On a demo"), BOTH("Two or more software"),
    SUSP("Suspended"), SELF("Self-registered")
}

object Customers {

    /** Every customer once: each Sales & Costing company with its Fabric Stock, then Fabric Stock alone. */
    fun of(weight: List<Company>, fabric: List<FabricCompany>): List<Customer> = of(weight, mapOf(Software.FABRIC to fabric))

    /**
     * 2.1.0 — every customer once, across every software. A customer is a Sales & Costing company with every
     * remote company whose companyId points at it (the older one when two of one software do); the remote
     * companies left over that share a GSTIN — one of each software — are one customer; any other is a customer
     * on its own. Every remote company appears exactly once.
     */
    fun of(weight: List<Company>, given: Map<String, List<FabricCompany>>): List<Customer> {
        val ids = weight.map { it.id }.toSet()
        /* each company carries the software it is listed under */
        val remotes = given.mapValues { (sw, l) -> l.map { if (it.sw == sw) it else it.copy(sw = sw) } }
        /* sw → (Sales & Costing id → its company on sw) */
        val linked = Software.REMOTE_IDS.associateWith { sw ->
            remotes[sw].orEmpty().filter { it.companyId != null && it.companyId in ids }
                .groupBy { it.companyId!! }.mapValues { (_, l) -> l.minBy { it.id } }
        }
        val shown = linked.mapValues { (_, m) -> m.values.map { it.id }.toSet() }
        val out = ArrayList<Customer>()
        weight.forEach { c ->
            val mine = Software.REMOTE_IDS.mapNotNull { sw -> linked[sw]?.get(c.id)?.let { sw to it } }.toMap()
            out += customerOf("w${c.id}", c, mine)
        }
        /* the rest, in the table's order and each software's own order (as 2.0 listed Fabric Stock alone): the first of a
           GSTIN starts its customer, and one company of each other software with that GSTIN joins it; any other is alone */
        val left = Software.REMOTE_IDS.flatMap { sw -> remotes[sw].orEmpty().filter { it.id !in shown[sw].orEmpty() } }
        val made = ArrayList<LinkedHashMap<String, FabricCompany>>()
        val byGstin = HashMap<String, LinkedHashMap<String, FabricCompany>>()
        left.forEach { r ->
            val g = r.gstin?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            val group = g?.let { byGstin[it] }
            if (group != null && !group.containsKey(r.sw)) {
                group[r.sw] = r
                return@forEach
            }
            val fresh = LinkedHashMap<String, FabricCompany>().apply { put(r.sw, r) }
            made += fresh
            if (g != null && group == null) byGstin[g] = fresh
        }
        made.forEach { mine ->
            val lead = mine.values.first()
            out += customerOf((Software.remote(lead.sw)?.prefix ?: 'f') + lead.id.toString(), null, mine)
        }
        return out
    }

    private fun customerOf(key: String, c: Company?, mine: Map<String, FabricCompany>): Customer {
        val rs = Software.REMOTE_IDS.mapNotNull { mine[it] }
        val lead = rs.firstOrNull()
        return Customer(
            key = key,
            name = c?.name ?: lead?.name ?: "-",
            gstin = c?.gstin ?: rs.firstNotNullOfOrNull { it.gstin },
            email = c?.email ?: rs.firstNotNullOfOrNull { it.email },
            phone = c?.phone ?: rs.firstNotNullOfOrNull { it.phone },
            w = c,
            f = mine[Software.FABRIC],
            self = c?.selfRegistered ?: lead?.selfRegistered ?: false,
            since = if (c != null) c.createdAt ?: c.registeredAt else lead?.createdAt,
            others = mine.filterKeys { it != Software.FABRIC }
        )
    }

    /** One customer by its key; a key that has changed (a Fabric Stock customer that now has Sales & Costing) is found by either licence. */
    fun find(list: List<Customer>, key: String): Customer? {
        list.find { it.key == key }?.let { return it }
        val id = key.drop(1).toIntOrNull() ?: return null
        val first = key.firstOrNull()
        if (first == 'w') return list.find { it.w?.id == id }
        val sw = Software.byPrefix(first)?.id ?: return null
        return list.find { it.remote(sw)?.id == id }
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
            val f = x.remote(sw) ?: return null
            Software.name(sw) + " · " + Plans.nameOf(plans, sw, f.plan, f.planName) + own(f.featureOverrides.size) + " · " +
                daysText(f.shownState, f.daysLeft, f.expiresAt)
        }
    }

    /** " ± 2" after a plan's name when a customer has features of its own over it; nothing when it has none. */
    fun own(n: Int): String = if (n > 0) " ± $n" else ""

    /** How many features of its own [x] has over its plan on [sw]. */
    fun ownCount(x: Customer, sw: String): Int =
        if (sw == Software.WEIGHT) x.w?.featureOverrides?.size ?: 0 else x.remote(sw)?.featureOverrides?.size ?: 0

    /** 2.1.0 — the plan code [x] is on in [sw] (Sales & Costing's, or the remote company's), or null. */
    fun planCode(x: Customer, sw: String): String? = if (sw == Software.WEIGHT) x.w?.plan else x.remote(sw)?.plan

    /** 2.1.0 — the plan's name as the remote company itself came with it (null for Sales & Costing). */
    fun planFallback(x: Customer, sw: String): String? = if (sw == Software.WEIGHT) null else x.remote(sw)?.planName

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
            x.remotes.forEach { (sw, f) ->
                out += ValidityRow(x, sw, Plans.nameOf(plans, sw, f.plan, f.planName), f.shownState,
                    f.periodStartedAt ?: f.createdAt, f.expiresAt, f.daysLeft, f.seats, payments?.last(x, sw))
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
            /* 2.1.0 — a remote software's: also on Sales & Costing; Sales & Costing's: also on any other */
            SwQuick.BOTH -> if (sw == Software.WEIGHT) x.both else x.w != null
        }
    }

    fun label(q: SwQuick, sw: String): String = when (q) {
        SwQuick.ALL -> "All"
        SwQuick.D30 -> "Ending in 30 days"
        SwQuick.DEMO -> "On a demo"
        SwQuick.ENDED -> "Ended"
        SwQuick.SUSP -> "Suspended"
        SwQuick.BOTH -> if (sw == Software.WEIGHT) "Also on other software" else "Also on " + Software.WEIGHT_NAME
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
        /* 2.1.0 — whichever remote software it is on, with that software's own catalogue and plans */
        sw = f.sw,
        name = f.name,
        planName = Plans.nameOf(data, f.sw, f.plan, f.planName),
        catalogue = Plans.catalogue(data, f.sw),
        plan = Plans.ticksOf(data, settings, f.sw, f.plan),
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
