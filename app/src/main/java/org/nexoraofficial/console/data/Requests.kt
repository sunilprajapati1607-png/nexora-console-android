package org.nexoraofficial.console.data

import org.json.JSONObject

/* ======================================================================
   2.0.0 — WHAT CONSOLE 2.0 SENDS, IN ONE PLACE.

   Every new request body is made here, from the forms the screens fill,
   so the tests can read exactly what goes to the service — and the web
   console's own rules (admin.js saveCustomer, payTool, planTool,
   newCustTool) are followed in one file rather than inside the screens.
   ====================================================================== */

/** A Sales & Costing licence while it is edited in the customer's window (text as typed). */
data class WeightEdit(
    val name: String,
    val gstin: String,
    val note: String,
    val plan: String,
    val seats: String,
    val graceDays: String,
    val aiDaily: String,
    val txnLimit: String,
    /** The feature changes being made: true added, false off, null back to the plan. */
    val features: Map<String, Boolean?> = emptyMap()
) {
    companion object {
        fun of(c: Company) = WeightEdit(
            name = c.name, gstin = c.gstin.orEmpty(), note = c.notes.orEmpty(), plan = c.plan.uppercase(),
            seats = c.seats.toString(), graceDays = c.graceDays.toString(), aiDaily = c.aiDailyLimit.toString(),
            txnLimit = c.txnLimit.toString()
        )
    }
}

/** A Fabric Stock licence while it is edited. */
data class FabricEdit(
    val name: String,
    val gstin: String,
    val email: String,
    val phone: String,
    val note: String,
    val seats: String,
    val graceDays: String,
    /** 2.0.1 — its plan's code (Standard when its service named none). */
    val plan: String = "STANDARD",
    /** 2.0.1 — the feature changes being made: true added, false off, null back to the plan. */
    val features: Map<String, Boolean?> = emptyMap()
) {
    companion object {
        fun of(f: FabricCompany) = FabricEdit(
            name = f.name, gstin = f.gstin.orEmpty(), email = f.email.orEmpty(), phone = f.phone.orEmpty(),
            note = f.notes.orEmpty(), seats = f.seats.toString(), graceDays = f.graceDays.toString(),
            plan = Requests.fabricPlan(f)
        )
    }
}

/** A plan while it is made or edited. Prices as typed; empty = open. */
data class PlanForm(
    val name: String = "",
    val note: String = "",
    val priceFirst: String = "",
    val priceRenewal: String = "",
    val usersIncluded: String = "",
    val extraUserPrice: String = "",
    val features: Map<String, Boolean> = emptyMap(),
    /** New plan: whose ticks it started from (sent as copyFrom). */
    val copyFrom: String = ""
) {
    companion object {
        private fun num(d: Double?): String = d?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()

        fun of(p: Plan) = PlanForm(
            name = p.name, note = p.note.orEmpty(), priceFirst = num(p.priceFirst), priceRenewal = num(p.priceRenewal),
            usersIncluded = p.usersIncluded?.toString().orEmpty(), extraUserPrice = num(p.extraUserPrice),
            features = p.features
        )

        /** Duplicate: the same, named "… copy", made from its ticks. */
        fun copyOf(p: Plan) = of(p).copy(name = p.name + " copy", copyFrom = p.code)
    }
}

/** New customer: one customer, any software — each gets its own licence key. */
data class NewCustomerForm(
    val name: String = "",
    val gstin: String = "",
    val email: String = "",
    val phone: String = "",
    val admin: String = "",
    val pin: String = "",
    val weight: Boolean = true,
    val weightPlan: String = "PRO",
    val weightDays: String = "365",
    val weightSeats: String = "1",
    val weightGrace: String = "3",
    val fabric: Boolean = false,
    /** null when Fabric Stock has no plans of its own (every company is on Standard). */
    val fabricPlan: String? = null,
    val fabricState: String = "DEMO",
    val fabricDays: String = "7",
    val fabricSeats: String = "3",
    val fabricGrace: String = "0"
)

object Requests {

    private fun int(s: String, dflt: Int): Int = s.trim().toIntOrNull() ?: dflt

    /* ---- a customer's features over its plan ---- */

    /** {id, action:"features", overrides:{feature: true | false | null}} — null takes a feature back to the plan. */
    fun features(id: Int, changes: Map<String, Boolean?>): JSONObject {
        val o = JSONObject()
        changes.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return JSONObject().put("id", id).put("action", "features").put("overrides", o)
    }

    /** "Back to the plan only", in one step. */
    fun featuresReset(id: Int): JSONObject = JSONObject().put("id", id).put("action", "features").put("reset", true)

    /** Any active plan the owner made. */
    fun plan(id: Int, code: String): JSONObject = JSONObject().put("id", id).put("action", "plan").put("plan", code.trim().uppercase())

    /**
     * Save on a Sales & Costing licence: one company action per thing that changed, in the web
     * console's order — rename, gstin, note, plan, seats, grace, ailimit, txnlimit, features.
     */
    fun weightSteps(c: Company, e: WeightEdit): List<JSONObject> {
        val id = c.id
        val out = ArrayList<JSONObject>()
        val name = e.name.trim()
        if (name.isNotEmpty() && name != c.name) out += JSONObject().put("id", id).put("action", "rename").put("name", name)
        val gstin = e.gstin.trim().uppercase()
        if (gstin != c.gstin.orEmpty()) out += JSONObject().put("id", id).put("action", "gstin").put("gstin", gstin)
        if (e.note != c.notes.orEmpty()) out += JSONObject().put("id", id).put("action", "note").put("notes", e.note)
        val plan = e.plan.trim().uppercase()
        if (plan.isNotEmpty() && plan != c.plan.uppercase()) out += plan(id, plan)
        val seats = int(e.seats, c.seats)
        if (seats != c.seats) out += JSONObject().put("id", id).put("action", "seats").put("seats", seats)
        val grace = int(e.graceDays, c.graceDays)
        if (grace != c.graceDays) out += JSONObject().put("id", id).put("action", "grace").put("graceDays", grace)
        val ai = int(e.aiDaily, c.aiDailyLimit)
        if (ai != c.aiDailyLimit) out += JSONObject().put("id", id).put("action", "ailimit").put("aiDailyLimit", ai)
        val txn = int(e.txnLimit, c.txnLimit)
        if (txn != c.txnLimit) out += JSONObject().put("id", id).put("action", "txnlimit").put("txnLimit", txn)
        val changes = Features.changes(c.featureOverrides, e.features)
        if (changes.isNotEmpty()) out += features(id, changes)
        return out
    }

    /** A Fabric Stock company's plan code, Standard when its service named none (the web console's f.plan||'STANDARD'). */
    fun fabricPlan(f: FabricCompany): String = f.plan?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "STANDARD"

    /**
     * Save on a Fabric Stock licence: one update with what changed, or null when nothing did.
     *
     * 2.0.1 — with the plan when it was changed, and the customer's own features as "featureOverrides":
     * only the keys that change its own list, null taking one back to the plan (Fabric Stock 0.8.1's
     * update; the web console's saveCustomer sends the same). A retired or unknown plan is refused by
     * the service in a sentence, which the window shows.
     */
    fun fabricUpdate(f: FabricCompany, e: FabricEdit): JSONObject? {
        val b = JSONObject().put("action", "update").put("id", f.id)
        val name = e.name.trim()
        if (name.isNotEmpty() && name != f.name) b.put("name", name)
        val gstin = e.gstin.trim().uppercase()
        if (gstin != f.gstin.orEmpty()) b.put("gstin", gstin)
        if (e.email.trim() != f.email.orEmpty()) b.put("email", e.email.trim())
        if (e.phone.trim() != f.phone.orEmpty()) b.put("phone", e.phone.trim())
        if (e.note != f.notes.orEmpty()) b.put("notes", e.note)
        val seats = int(e.seats, f.seats)
        if (seats != f.seats) b.put("seats", seats)
        val grace = int(e.graceDays, f.graceDays)
        if (grace != f.graceDays) b.put("graceDays", grace)
        val plan = e.plan.trim().uppercase()
        if (plan.isNotEmpty() && plan != fabricPlan(f)) b.put("plan", plan)
        val changes = Features.changes(f.featureOverrides, e.features)
        if (changes.isNotEmpty()) {
            val o = JSONObject()
            changes.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
            b.put("featureOverrides", o)
        }
        return if (b.length() > 2) b else null
    }

    /* ---- the plans ---- */

    /** Create (no [existing]) or update a plan of [sw]. A price sent as "" is open again. */
    fun planSave(sw: String, existing: Plan?, f: PlanForm): JSONObject {
        val features = JSONObject()
        f.features.forEach { (k, v) -> features.put(k, v) }
        val b = JSONObject()
            .put("software", sw)
            .put("action", if (existing == null) "create" else "update")
            .put("name", f.name.trim())
            .put("note", f.note.trim())
            .put("priceFirst", f.priceFirst.trim())
            .put("priceRenewal", f.priceRenewal.trim())
            .put("usersIncluded", f.usersIncluded.trim())
            .put("extraUserPrice", f.extraUserPrice.trim())
            .put("features", features)
        if (existing != null) b.put("code", existing.code)
        else if (f.copyFrom.isNotBlank()) b.put("copyFrom", f.copyFrom)
        return b
    }

    /** retire, restore or delete. */
    fun planAction(sw: String, action: String, code: String): JSONObject =
        JSONObject().put("software", sw).put("action", action).put("code", code)

    /* ---- the payments ---- */

    /** A typed amount as a number ("15,000" → 15000), or the text as it was when it is not one. */
    private fun amount(s: String): Any {
        val n = Money.parse(s) ?: return s.trim()
        return if (n % 1.0 == 0.0) n.toLong() else n
    }

    /**
     * Record payment. The customer's Sales & Costing company goes with it whenever it has one (as the web
     * console sends it); the Fabric Stock company only for a Fabric Stock payment. Renewing with it
     * (extendDays) renews the licence in the same step; without it, the validity typed by hand.
     */
    fun paymentAdd(f: PaymentForm, c: Customer): JSONObject {
        val b = JSONObject()
            .put("action", "add")
            .put("software", f.software)
            .put("amount", amount(f.amount))
            .put("mode", f.mode)
            .put("kind", f.kind)
        c.w?.let { b.put("companyId", it.id) }
        if (f.software == Software.FABRIC) c.f?.let { b.put("fabricId", it.id) }
        if (f.paidOn.isNotBlank()) b.put("paidOn", f.paidOn.trim())
        if (f.reference.isNotBlank()) b.put("reference", f.reference.trim())
        if (f.note.isNotBlank()) b.put("note", f.note.trim())
        if (f.extendDays > 0) b.put("extendDays", f.extendDays)
        else {
            if (f.validFrom.isNotBlank()) b.put("validFrom", f.validFrom.trim())
            if (f.validTo.isNotBlank()) b.put("validTo", f.validTo.trim())
        }
        return b
    }

    /** A payment's details changed. An emptied reference, note or validity is cleared. */
    fun paymentUpdate(f: PaymentForm): JSONObject = JSONObject()
        .put("action", "update")
        .put("id", f.id)
        .put("amount", amount(f.amount))
        .put("paidOn", f.paidOn.trim())
        .put("mode", f.mode)
        .put("kind", f.kind)
        .put("reference", f.reference.trim())
        .put("note", f.note.trim())
        .put("validFrom", f.validFrom.trim())
        .put("validTo", f.validTo.trim())

    /** Off the list (the service keeps it). */
    fun paymentDelete(id: Int): JSONObject = JSONObject().put("action", "delete").put("id", id)

    /* ---- a new customer ---- */

    /** The Sales & Costing company, made licensed on the chosen plan. */
    fun newWeight(f: NewCustomerForm): JSONObject = JSONObject()
        .put("action", "create")
        .put("name", f.name.trim())
        .put("gstin", f.gstin.trim().uppercase())
        .put("email", f.email.trim())
        .put("phone", f.phone.trim())
        .put("plan", f.weightPlan)
        .put("days", int(f.weightDays, 365).coerceAtLeast(1))
        .put("seats", int(f.weightSeats, 1).coerceAtLeast(1))
        .put("graceDays", int(f.weightGrace, 0).coerceAtLeast(0))

    /** The Fabric Stock company, in its own service — linked to the Sales & Costing one made with it ([linkTo]). */
    fun newFabric(f: NewCustomerForm, linkTo: Int?): JSONObject {
        val b = JSONObject()
            .put("action", "create")
            .put("name", f.name.trim())
            .put("state", f.fabricState)
            .put("days", int(f.fabricDays, 7).coerceAtLeast(1))
            .put("seats", int(f.fabricSeats, 3).coerceAtLeast(1))
            .put("graceDays", int(f.fabricGrace, 0).coerceAtLeast(0))
        f.gstin.trim().takeIf { it.isNotEmpty() }?.let { b.put("gstin", it.uppercase()) }
        f.email.trim().takeIf { it.isNotEmpty() }?.let { b.put("email", it) }
        f.phone.trim().takeIf { it.isNotEmpty() }?.let { b.put("phone", it) }
        linkTo?.let { b.put("linkTo", it) }
        f.fabricPlan?.let { b.put("plan", it) }
        if (f.admin.isNotBlank() && f.pin.isNotBlank()) b.put("adminName", f.admin.trim()).put("adminPin", f.pin)
        return b
    }

    /** The Sales & Costing administrator of a customer just made, when one was named. */
    fun newAdmin(companyId: Int, f: NewCustomerForm): JSONObject? =
        if (f.admin.isBlank() || f.pin.isBlank()) null
        else JSONObject().put("id", companyId).put("action", "adminuser").put("name", f.admin.trim()).put("pin", f.pin)
            .put("email", f.email.trim())

    /** Sales & Costing for a customer on Fabric Stock alone: a year's licence with its Fabric Stock company's details. */
    fun weightFor(f: FabricCompany): JSONObject = JSONObject()
        .put("action", "create")
        .put("name", f.name)
        .put("gstin", f.gstin.orEmpty())
        .put("email", f.email.orEmpty())
        .put("phone", f.phone.orEmpty())
        .put("seats", f.seats.coerceAtLeast(1))
        .put("days", 365)
        .put("plan", "PRO")
}
