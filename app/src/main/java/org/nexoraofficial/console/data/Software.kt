package org.nexoraofficial.console.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/* ======================================================================
   1.9.0 — EVERY NEXORA SOFTWARE IN THE ONE CONSOLE.

   Owner, 2026-10-07: "nexora console page single rahese badhi service tya
   thij update chalu bandh thase" — one console for every Nexora software;
   each one's licences are renewed, started and stopped from here.

   Each software keeps its OWN licence — its own key, plan, days, seats,
   people and rights. Nothing is merged: a Weight Calc company and a Fabric
   Stock company are two licences that may belong to one customer. The
   service decides that they do when the GSTIN is the same; otherwise the
   owner links them by hand. People and rights are set by the company's own
   administrator inside each application, never from here.

   What the service answers (GET /admin/api/products) is read with the same
   null-safe readers as Models.kt, so a missing field is a blank, not a crash.
   ====================================================================== */

/**
 * The software the console sells, by the service's own ids and the names the screens use.
 *
 * 2.0.0 — the weight-calculation software is called "Sales & Costing" on the console (owner,
 * 2026-10-08, as the web console); its id stays "weight". Jobwork has no licence yet: it is
 * shown as "coming" and nothing can be done to it.
 */
object Software {
    const val WEIGHT = "weight"
    const val FABRIC = "fabric"
    const val ERP = "erp"
    const val CONSULT = "consult"
    const val JOBWORK = "jobwork"
    const val WEIGHT_NAME = "Sales & Costing"
    const val FABRIC_NAME = "Fabric Stock"
    const val ERP_NAME = "MSME ERP"
    const val CONSULT_NAME = "Nexora Consult"
    const val JOBWORK_NAME = "Jobwork"
    const val WEIGHT_FULL = "Nexora Bag Weight Calculation"

    /**
     * 2.1.0 — every software the console reaches through its service, as the service's products.js lists
     * them: each keeps its own licences on its own server, and the console reads and acts on each the same
     * way (GET /admin/api/products, POST /admin/api/<id>). Fabric Stock first, as it always was.
     */
    val REMOTES: List<RemoteSoftware> = listOf(
        RemoteSoftware(FABRIC, FABRIC_NAME, "Nexora Loom & Fabric Stock", 'f', "F"),
        RemoteSoftware(ERP, ERP_NAME, "Nexora MSME ERP", 'e', "E"),
        RemoteSoftware(CONSULT, CONSULT_NAME, "Nexora Consult", 'c', "C")
    )

    val REMOTE_IDS: List<String> = REMOTES.map { it.id }

    /** Every software that has licences, Sales & Costing first. */
    val LICENSED: List<String> = listOf(WEIGHT) + REMOTE_IDS

    fun isRemote(id: String?): Boolean = id in REMOTE_IDS

    fun remote(id: String?): RemoteSoftware? = REMOTES.find { it.id == id }

    /** The remote software whose customers' keys start with [c] ("f7", "e3", "c2"). */
    fun byPrefix(c: Char?): RemoteSoftware? = REMOTES.find { it.prefix == c }

    /** The name a screen shows for a software id. */
    fun name(id: String?): String = when (id) {
        WEIGHT -> WEIGHT_NAME
        JOBWORK -> JOBWORK_NAME
        else -> remote(id)?.name ?: id ?: "-"
    }

    /** "Nexora Bag Weight Calculation", "Nexora Loom & Fabric Stock", "Nexora MSME ERP", "Nexora Consult". */
    fun fullName(id: String?): String = when (id) {
        WEIGHT -> WEIGHT_FULL
        else -> remote(id)?.fullName ?: name(id)
    }

    /** The other of the two that have licences. */
    fun other(id: String): String = if (id == FABRIC) WEIGHT else FABRIC
}

/** 2.1.0 — one software reached through the console service: its id, its names, and the letter its customers' keys start with. */
data class RemoteSoftware(val id: String, val name: String, val fullName: String, val prefix: Char, val letter: String)

/** 2.1.0 — a company of any software reached through the console service: they all come in Fabric Stock's shape. */
typealias RemoteCompany = FabricCompany

/**
 * A company on Nexora Loom & Fabric Stock — its own licence, apart from any
 * Weight Calc licence the same customer holds.
 */
data class FabricCompany(
    val id: Int,
    val name: String,
    val licenceKey: String,
    val loginId: String?,
    val email: String?,
    val phone: String?,
    val gstin: String?,
    val state: String,
    val isDemo: Boolean,
    val seats: Int,
    val graceDays: Int,
    val plan: String?,
    val expiresAt: String?,
    val periodStartedAt: String?,
    val selfRegistered: Boolean,
    val notes: String?,
    val createdAt: String?,
    val deletedAt: String?,
    /** How many people may sign in, and how many computers and phones it has (the listing's counts). */
    val people: Int,
    val devices: Int,
    val daysLeft: Int,
    val expired: Boolean,
    val periodDays: Int,
    /** LICENSED, DEMO, EXPIRED or SUSPENDED — the service's own reading. */
    val shownState: String,
    val endingSoon: Boolean,
    /** The Weight Calc company it belongs to, or null: Fabric Stock only. */
    val companyId: Int?,
    /** "gstin" (the service matched the GSTIN), "hand" (the owner linked them), or null. */
    val linkedBy: String?,
    /* 2.0.1 — Fabric Stock 0.8.1 has plans and features: the plan's name as its service names it, and this
       company's own changes over its plan ("+ added" true / "− off" false; a feature not named follows the
       plan). Null and empty from an older Fabric Stock service. */
    val planName: String? = null,
    val featureOverrides: Map<String, Boolean> = emptyMap(),
    /** 2.1.0 — the software it is on (fabric, erp, consult): every one of them comes in this shape. */
    val sw: String = Software.FABRIC
) {
    val linked: Boolean get() = companyId != null

    /** A demo, however the service says it (the state, or the flag). */
    val onDemo: Boolean get() = state == "DEMO" || (isDemo && state != "LICENSED")

    val ended: Boolean get() = shownState == "EXPIRED" || shownState == "SUSPENDED"

    /** "Demo · 5 days", "Licensed · ends today", "Expired", "Suspended" — after the software's name on a pill. */
    val stateText: String
        get() = when (shownState) {
            "EXPIRED" -> "expired"
            "SUSPENDED" -> "suspended"
            else -> shownState.lowercase() + " · " + when (daysLeft) {
                0 -> "ends today"
                1 -> "1 day"
                else -> "$daysLeft days"
            }
        }

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return listOf(name, licenceKey, gstin, email, phone, loginId).any { it?.lowercase()?.contains(t) == true }
    }

    /**
     * The days to send for [add] more. Fabric Stock's update starts a new
     * period of N days from today; to ADD days, as Weight Calc's "Add days"
     * and "+1 year" do, the days still left go with them. An ended licence
     * has none left: it runs [add] days from today.
     */
    fun renewDays(add: Int): Int = (if (expired || shownState == "EXPIRED") 0 else daysLeft) + add

    /** When it ends after [add] more days. */
    fun renewEnd(add: Int, now: Instant = Instant.now()): Instant = now.plus(Duration.ofDays(renewDays(add).toLong()))

    companion object {
        fun from(o: JSONObject, sw: String = Software.FABRIC): FabricCompany {
            val state = o.str("state") ?: "DEMO"
            val expiresAt = o.str("expiresAt")
            val end = moment(expiresAt)
            val now = Instant.now()
            /* The listing computes these; a company read on its own (the detail) does not carry
               them, so they are worked out here the same way rather than shown as zeros. */
            val expired = if (o.has("expired")) o.bool("expired") else end != null && !end.isAfter(now)
            val daysLeft = if (o.has("daysLeft")) o.int("daysLeft").coerceAtLeast(0)
            else if (end == null || expired) 0 else ceilDays(Duration.between(now, end))
            val started = o.str("periodStartedAt")
            val periodDays = if (o.has("periodDays")) o.int("periodDays")
            else moment(started)?.let { s -> end?.let { e -> ceilDays(Duration.between(s, e)) } } ?: 0
            return FabricCompany(
                id = o.int("id"),
                name = o.str("name") ?: "-",
                licenceKey = o.str("licenceKey") ?: "",
                loginId = o.str("loginId"),
                email = o.str("email"),
                phone = o.str("phone"),
                gstin = o.str("gstin"),
                state = state,
                isDemo = o.bool("isDemo"),
                seats = o.int("seats", 1),
                graceDays = o.int("graceDays"),
                plan = o.str("plan")?.uppercase(),
                expiresAt = expiresAt,
                periodStartedAt = started,
                selfRegistered = o.bool("selfRegistered"),
                notes = o.str("notes"),
                createdAt = o.str("createdAt"),
                deletedAt = o.str("deletedAt"),
                people = count(o.opt("people")).takeIf { it >= 0 } ?: count(o.opt("users")).coerceAtLeast(0),
                devices = count(o.opt("devices")).coerceAtLeast(0),
                daysLeft = daysLeft,
                expired = expired,
                periodDays = periodDays,
                shownState = o.str("shownState") ?: if (expired && state != "SUSPENDED") "EXPIRED" else state,
                endingSoon = o.bool("endingSoon"),
                companyId = o.str("companyId")?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                linkedBy = o.str("linkedBy")?.lowercase(),
                planName = o.str("planName"),
                /* an object of on/off, or that object as text; null or anything else is none */
                featureOverrides = Company.overridesOf(o.opt("featureOverrides") ?: o.opt("feature_overrides")),
                sw = sw
            )
        }

        /* "devices" is a count in the listing and the list itself in the detail: either way, how many. */
        private fun count(v: Any?): Int = when (v) {
            is Number -> v.toInt()
            is JSONArray -> v.length()
            is String -> v.trim().toIntOrNull() ?: -1
            else -> -1
        }

        private fun ceilDays(d: Duration): Int {
            val s = d.seconds
            return if (s <= 0) 0 else ((s + 86_399) / 86_400).toInt()
        }
    }
}

/** A person on a Fabric Stock company. Their rights are the company administrator's to set, inside Fabric Stock. */
data class FabricUser(
    /** The service's own id, kept as it came (a number or a text) so it goes back unchanged. */
    val id: Any,
    val name: String,
    val email: String?,
    val role: String,
    val scope: String,
    val rights: List<String>,
    val active: Boolean,
    val sessionDevice: String?,
    val sessionAt: String?,
    val lastLoginAt: String?,
    val lastSeenAt: String?,
    val createdAt: String?
) {
    val isAdmin get() = role == "ADMIN"
    val signedIn get() = !sessionDevice.isNullOrBlank()

    companion object {
        fun from(o: JSONObject) = FabricUser(
            id = o.opt("id")?.takeIf { it != JSONObject.NULL } ?: 0,
            name = o.str("name") ?: "-",
            email = o.str("email"),
            role = o.str("role") ?: "USER",
            scope = o.str("scope") ?: "OWN",
            rights = rightsOf(o.opt("permissions")),
            active = o.optBoolean("active", true),
            sessionDevice = o.str("sessionDevice"),
            sessionAt = o.str("sessionAt"),
            lastLoginAt = o.str("lastLoginAt"),
            lastSeenAt = o.str("lastSeenAt"),
            createdAt = o.str("createdAt")
        )

        /** The rights that are on, however they were written: a list, a map of on/off, or words with commas. */
        internal fun rightsOf(v: Any?): List<String> = when (v) {
            is JSONArray -> (0 until v.length()).mapNotNull { i -> v.opt(i)?.takeIf { it is String }?.let { (it as String).trim() } }
                .filter { it.isNotEmpty() }
            is JSONObject -> v.keys().asSequence().filter { k ->
                when (val x = v.opt(k)) {
                    is Boolean -> x
                    is Number -> x.toInt() != 0
                    is String -> x.isNotBlank() && x.lowercase() !in setOf("none", "false", "no", "0")
                    is JSONObject, is JSONArray -> true
                    else -> false
                }
            }.toList()
            is String -> {
                val s = v.trim()
                when {
                    s.startsWith("[") -> runCatching { rightsOf(JSONArray(s)) }.getOrDefault(emptyList())
                    s.startsWith("{") -> runCatching { rightsOf(JSONObject(s)) }.getOrDefault(emptyList())
                    else -> s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
            else -> emptyList()
        }
    }
}

/** A computer or a phone on a Fabric Stock company. */
data class FabricDevice(
    val id: Any,
    val name: String?,
    /** "desktop" or "mobile". */
    val platform: String,
    val state: String?,
    val approved: Boolean,
    val approvedBy: String?,
    val pending: Boolean,
    /** Who withdrew it: "NEXORA" (from this console — Give back undoes it), "COMPANY", or null. */
    val revokedBy: String?,
    val lastSeen: String?,
    val computerNo: Int?,
    val appVersion: String?,
    val signedInName: String?
) {
    val isPhone: Boolean get() = platform.equals("mobile", ignoreCase = true)
    val withdrawn: Boolean get() = revokedBy != null
    /** Only one Nexora withdrew can be given back from here; the company's own is the company's to undo. */
    val nexoraWithdrew: Boolean get() = revokedBy == "NEXORA"

    companion object {
        fun from(o: JSONObject) = FabricDevice(
            id = o.opt("id")?.takeIf { it != JSONObject.NULL } ?: 0,
            name = o.str("name"),
            platform = o.str("platform") ?: "desktop",
            state = o.str("state"),
            approved = o.bool("approved"),
            approvedBy = o.str("approvedBy"),
            pending = o.bool("pending"),
            revokedBy = o.str("revokedBy")?.uppercase(),
            lastSeen = o.str("lastSeen"),
            computerNo = o.optInt("computerNo", 0).takeIf { it > 0 },
            appVersion = o.str("appVersion"),
            signedInName = o.optJSONObject("signedIn")?.str("name")
        )
    }
}

/** One Fabric Stock company read on its own: its facts, its people and its computers and phones. */
data class FabricDetail(
    val company: FabricCompany,
    val users: List<FabricUser>,
    val devices: List<FabricDevice>
) {
    companion object {
        fun from(o: JSONObject, sw: String = Software.FABRIC): FabricDetail {
            /* the company's own fields at the top, as the service sends them (or, from another
               build of it, under "company"); the people and the devices beside them */
            val co = o.optJSONObject("company") ?: o
            fun <T> list(k: String, f: (JSONObject) -> T): List<T> {
                val a = o.optJSONArray(k) ?: co.optJSONArray(k) ?: return emptyList()
                return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(f) }
            }
            return FabricDetail(FabricCompany.from(co, sw), list("users") { FabricUser.from(it) }, list("devices") { FabricDevice.from(it) })
        }
    }
}

/** One software, as GET /admin/api/products lists it. */
data class Product(
    val id: String,
    val name: String,
    val short: String,
    /** false when the service could not reach that software (Fabric Stock asleep, or its key refused). */
    val ok: Boolean,
    /** FABRIC_KEY or FABRIC_DOWN when not ok. */
    val error: String?,
    /** The sentence to show when not ok. */
    val message: String?,
    val companies: List<FabricCompany>
) {
    /** What the owner is told when it is not reachable — the service's own words first.
     *  2.1.0 — any software's FABRIC_KEY / ERP_DOWN / CONSULT_KEY …, by the word after its name. */
    val problem: String
        get() = message ?: when {
            error?.endsWith("_KEY") == true -> "The $short service did not accept the console's key."
            error?.endsWith("_DOWN") == true -> "The $short service did not answer — it may still be waking up. Try again in a minute."
            else -> "$short could not be reached."
        }

    /** 2.1.0 — the console service has no key for it yet (FABRIC_KEY, CONSULT_KEY …): waiting will not help. */
    val keyProblem: Boolean get() = error?.endsWith("_KEY") == true

    companion object {
        fun from(o: JSONObject): Product {
            val id = o.str("id") ?: ""
            val a = o.optJSONArray("companies")
            /* 2.1.0 — every company carries the software it came from */
            val sw = if (Software.isRemote(id)) id else Software.FABRIC
            return Product(
                id = id,
                name = o.str("name") ?: id,
                short = o.str("short") ?: o.str("name") ?: Software.name(id),
                ok = o.optBoolean("ok", true),
                error = o.str("error"),
                message = o.str("message"),
                companies = if (a == null) emptyList()
                else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { FabricCompany.from(it, sw) } }
            )
        }
    }
}

/** Everything GET /admin/api/products answers with. */
data class ProductsData(val products: List<Product> = emptyList()) {
    val weight: Product? get() = products.find { it.id == Software.WEIGHT }
    val fabric: Product? get() = products.find { it.id == Software.FABRIC }
    val erp: Product? get() = products.find { it.id == Software.ERP }
    val consult: Product? get() = products.find { it.id == Software.CONSULT }

    /** 2.1.0 — any one software by its id. */
    fun of(sw: String): Product? = products.find { it.id == sw }

    companion object {
        fun from(o: JSONObject): ProductsData {
            val a = o.optJSONArray("products") ?: return ProductsData()
            return ProductsData((0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Product.from(it) } })
        }
    }
}

/* ---- 2.1.0: each software's own demo rules, kept on its own server ---- */

/**
 * One software's demo rules, as GET /admin/api/software-settings lists them: how long a demo runs, the
 * offline days a demo has, its seats (a remote software only), whether a plant may sign itself up, and
 * what happens when a licence ends. Each software keeps its own on its own server — never mixed.
 */
data class SoftwareRules(
    val id: String,
    val ok: Boolean,
    val error: String? = null,
    val message: String? = null,
    val trialDays: Int? = null,
    val demoGraceDays: Int? = null,
    val demoSeats: Int? = null,
    val signupsOpen: Boolean? = null,
    /** READONLY or HARDSTOP. */
    val expiredMode: String? = null
) {
    val name: String get() = Software.name(id)

    /** Why they could not be read — the service's own words first. */
    val problem: String
        get() = message ?: when {
            error?.endsWith("_KEY") == true -> "The $name service did not accept the console's key."
            error?.endsWith("_DOWN") == true -> "The $name service did not answer — it may still be waking up. Try again in a minute."
            error == "NOT_YET" -> "$name's own service has no demo rules yet."
            else -> "$name's demo rules could not be read."
        }

    companion object {
        fun from(o: JSONObject): SoftwareRules {
            val s = o.optJSONObject("settings")
            fun int(k: String): Int? = s?.takeIf { it.has(k) && !it.isNull(k) }?.let { x ->
                when (val v = x.opt(k)) {
                    is Number -> v.toInt()
                    is String -> v.trim().toIntOrNull()
                    else -> null
                }
            }
            fun flag(k: String): Boolean? = s?.takeIf { it.has(k) && !it.isNull(k) }?.let { x ->
                when (val v = x.opt(k)) {
                    is Boolean -> v
                    is Number -> v.toInt() != 0
                    is String -> v.trim().lowercase() in setOf("true", "1", "yes", "open")
                    else -> null
                }
            }
            val err = o.str("error")
            return SoftwareRules(
                id = o.str("id") ?: "",
                ok = o.optBoolean("ok", s != null && err == null) && s != null,
                error = err,
                message = o.str("message"),
                trialDays = int("trialDays"),
                demoGraceDays = int("demoGraceDays"),
                demoSeats = if (o.str("id") == Software.WEIGHT) null else int("demoSeats"),
                signupsOpen = flag("signupsOpen"),
                expiredMode = s?.str("expiredMode")?.uppercase()
            )
        }
    }
}

/** Everything GET /admin/api/software-settings answers with. */
data class SoftwareRulesData(val software: List<SoftwareRules> = emptyList()) {
    fun of(sw: String): SoftwareRules? = software.find { it.id == sw }

    companion object {
        fun from(o: JSONObject): SoftwareRulesData {
            val a = o.optJSONArray("software") ?: return SoftwareRulesData()
            return SoftwareRulesData((0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { SoftwareRules.from(it) } })
        }
    }
}

/** 2.1.0 — one software's demo rules while they are changed (text as typed). */
data class RulesForm(
    val trialDays: String,
    val demoGraceDays: String,
    val demoSeats: String,
    val signupsOpen: Boolean,
    val expiredMode: String
) {
    /** What is wrong with it for [sw], in the service's own words — null when it will do. */
    fun problem(sw: String): String? {
        val remote = Software.isRemote(sw)
        val trial = trialDays.trim().toIntOrNull()
        val grace = demoGraceDays.trim().toIntOrNull()
        val trialMax = if (remote) 60 else 365
        val graceMax = if (remote) 30 else 365
        return when {
            trial == null || trial < 1 || trial > trialMax -> "Demo days must be 1 to $trialMax."
            grace == null || grace < 0 || grace > graceMax -> "Offline days for a demo must be 0 to $graceMax."
            remote && demoSeats.trim().toIntOrNull().let { it == null || it < 1 || it > 50 } -> "Demo seats must be 1 to 50."
            expiredMode !in setOf("READONLY", "HARDSTOP") -> "Choose what happens when a licence ends."
            else -> null
        }
    }

    /** POST /admin/api/software-settings, for this one software (demo seats only for a remote software). */
    fun body(sw: String): JSONObject = JSONObject()
        .put("software", sw)
        .put("trialDays", trialDays.trim().toInt())
        .put("demoGraceDays", demoGraceDays.trim().toInt())
        .put("signupsOpen", signupsOpen)
        .put("expiredMode", expiredMode)
        .apply { if (Software.isRemote(sw)) put("demoSeats", demoSeats.trim().toInt()) }

    companion object {
        fun of(r: SoftwareRules) = RulesForm(
            trialDays = (r.trialDays ?: 7).toString(),
            demoGraceDays = (r.demoGraceDays ?: 0).toString(),
            demoSeats = (r.demoSeats ?: 3).toString(),
            signupsOpen = r.signupsOpen ?: true,
            expiredMode = r.expiredMode?.takeIf { it == "READONLY" || it == "HARDSTOP" } ?: "READONLY"
        )
    }
}

/** Fabric Stock's figures for the dashboard: paying, on demo, and stopped. */
data class FabricCounts(val licensed: Int, val demo: Int, val suspended: Int) {
    val total: Int get() = licensed + demo + suspended

    companion object {
        fun of(list: List<FabricCompany>): FabricCounts {
            val suspended = list.count { it.state == "SUSPENDED" }
            val demo = list.count { it.state != "SUSPENDED" && it.onDemo }
            return FabricCounts(list.size - suspended - demo, demo, suspended)
        }
    }
}

/* ---- the Companies list, across the software ---- */

/** Which software the Companies list shows. */
enum class SoftwareFilter { ALL, WEIGHT, FABRIC }

/** One row of the Companies list. */
sealed interface CompanyEntry {
    val key: String

    /** A Weight Calc company, and the Fabric Stock company that belongs to it, if there is one. */
    data class Weight(val company: Company, val fabric: FabricCompany?) : CompanyEntry {
        override val key: String get() = "w-${company.id}"
    }

    /** A Fabric Stock company that belongs to no Weight Calc company in the list. */
    data class FabricOnly(val company: FabricCompany) : CompanyEntry {
        override val key: String get() = "f-${company.id}"
    }
}

/** How many each filter chip stands for: every customer once, each software's own companies. */
data class SoftwareCounts(val all: Int, val weight: Int, val fabric: Int)

object CompanyList {

    /**
     * The Fabric Stock company that belongs to each Weight Calc company, by the
     * Weight Calc company's id. Should two ever point at the same company, the
     * older (lower id) is the one shown with it; the other is listed on its own
     * rather than lost.
     */
    fun linked(weight: List<Company>, fabric: List<FabricCompany>): Map<Int, FabricCompany> {
        val ids = weight.map { it.id }.toSet()
        return fabric.filter { it.companyId != null && it.companyId in ids }
            .groupBy { it.companyId!! }
            .mapValues { (_, list) -> list.minBy { it.id } }
    }

    /** The Fabric Stock companies with no Weight Calc company beside them in the list. */
    fun fabricOnly(weight: List<Company>, fabric: List<FabricCompany>): List<FabricCompany> {
        val shown = linked(weight, fabric).values.map { it.id }.toSet()
        return fabric.filter { it.id !in shown }
    }

    fun counts(weight: List<Company>, fabric: List<FabricCompany>) =
        SoftwareCounts(weight.size + fabricOnly(weight, fabric).size, weight.size, fabric.size)

    /**
     * What the Companies screen lists, after the software chip, the search and
     * the "ending soon" view (the dashboard's card — Weight Calc licences, as it
     * counts them). A Weight Calc company is found by its Fabric Stock twin's
     * name, key or GSTIN too. Under Fabric Stock: the Weight Calc companies that
     * use it, then the companies on Fabric Stock alone.
     */
    fun entries(
        weight: List<Company>,
        fabric: List<FabricCompany>,
        filter: SoftwareFilter,
        query: String,
        endingOnly: Boolean = false
    ): List<CompanyEntry> {
        val link = linked(weight, fabric)
        fun found(c: Company) = c.matches(query) || link[c.id]?.matches(query) == true
        val weightRows = { keep: (Company) -> Boolean ->
            weight.filter { keep(it) && found(it) }.map { CompanyEntry.Weight(it, link[it.id]) }
        }
        val fabricRows = { fabricOnly(weight, fabric).filter { it.matches(query) }.map { CompanyEntry.FabricOnly(it) } }

        if (endingOnly) return weightRows { it.endingSoon }
        return when (filter) {
            SoftwareFilter.ALL -> weightRows { true } + fabricRows()
            SoftwareFilter.WEIGHT -> weightRows { true }
            SoftwareFilter.FABRIC -> weightRows { link[it.id] != null } + fabricRows()
        }
    }
}

/* An ISO moment with its zone, or a plain date-time read as UTC; null when there is none.
   2.0.0 — internal, so the customer lists sort by the same reading. A plain date (2026-10-08) is its midnight UTC. */
internal fun moment(s: String?): Instant? {
    if (!s.isNullOrBlank() && s.length == 10) {
        runCatching { return java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() }
    }
    return momentOf(s)
}

private fun momentOf(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(s.take(19)).atZone(java.time.ZoneOffset.UTC).toInstant() }.getOrNull()
}
