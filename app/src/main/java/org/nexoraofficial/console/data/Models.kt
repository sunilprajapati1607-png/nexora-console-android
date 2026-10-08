package org.nexoraofficial.console.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---- small readers, so a missing column is a blank and never a crash ----
   1.9.0 — internal (were private), so Fabric Stock's models (Software.kt) read the same way */
internal fun JSONObject.str(k: String): String? {
    if (!has(k) || isNull(k)) return null
    val v = optString(k, "")
    return if (v.isEmpty()) null else v
}

internal fun JSONObject.int(k: String, dflt: Int = 0): Int = optInt(k, dflt)
internal fun JSONObject.bool(k: String): Boolean = optBoolean(k, false)

/** A customer — the company IS the licence: one key, N seats, one clock. */
data class Company(
    val id: Int,
    val name: String,
    val licenceKey: String,
    val email: String?,
    val phone: String?,
    val state: String,
    val seats: Int,
    /* 4.42.0 — A SEAT IS A PERSON. seatsUsed counts the people who may sign
       in; machinesUsed counts the computers, which are not rationed, because
       a computer with nobody signed in can only read. */
    val seatsUsed: Int,
    val machinesUsed: Int,
    val gstin: String?,
    val graceDays: Int,
    val isDemo: Boolean,
    val expiresAt: String?,
    /* 4.57.0 - when THIS stretch began, and how many days it runs for.
       expiresAt alone gave a days-left count with no scale: three of
       seven is a demo lapsing this week, three of 365 is next year. */
    val periodStartedAt: String?,
    val periodDays: Int,
    val txnLimit: Int,
    val txnUsed: Int,
    /* 1.6.0 — "want to limit ai call as per company per day from console and from console android app":
       Nexora AI questions a day set for this company (0 = the service's own number), and how many today */
    val aiDailyLimit: Int,
    val aiUsedToday: Int,
    val usageMinutes: Int,
    val loginId: String?,
    val selfRegistered: Boolean,
    val registeredIp: String?,
    val registeredAt: String?,
    val gstStatus: String?,
    val gstCheckedAt: String?,
    val gstNote: String?,
    val daysLeft: Int,
    val expired: Boolean,
    val usersCount: Int,
    val usersTotal: Int,
    val adminNames: String?,
    /* 4.42.0 — the addresses a circular would actually reach at this
       company: its people, not only its one registered inbox. */
    val userEmails: String?,
    /* 1.5.0 — STANDARD or PRO; a demo reads as everything whatever this says */
    val plan: String = "PRO",
    /* 4.72.0 — audit #90 (service part): the service's own `ending_soon`, a
       paying licence that ends within 30 days (IST calendar days) and has not
       ended yet. Marked "renew soon" in the list and on the company, as the
       web console marks it; false from a service that does not send it. The
       dashboard's warning stays at ENDING_DAYS (endingSoon, below). */
    val renewSoon: Boolean = false,
    /* 2.0.0 — the owner's note, when the company was made, and this company's own changes over its plan
       ("+ added" true / "− off" false; a feature not named follows the plan). Empty from an older service. */
    val notes: String? = null,
    val createdAt: String? = null,
    val featureOverrides: Map<String, Boolean> = emptyMap()
) {
    /** 2.0.0 — the web console's reading (wState): suspended, ended, on a demo, or licensed. */
    val swState: String
        get() = when {
            state == "SUSPENDED" -> "SUSPENDED"
            expired -> "EXPIRED"
            isDemo -> "DEMO"
            else -> "LICENSED"
        }

    /** The same reading the console shows on the pill. */
    val shownState: String
        get() = if (expired && state != "SUSPENDED") "EXPIRED" else state

    /**
     * 4.72.0 — audit #90: a paying plant whose licence ends within
     * [ENDING_DAYS] days. Until now it worked normally to the last evening
     * and opened read-only the next morning, and renewing hung on the owner
     * remembering. Demos are left out (every demo ends within days, by
     * design), and so is a licence with no end date — its days_left reads 0.
     */
    val endingSoon: Boolean
        get() = state == "LICENSED" && !isDemo && !expired && expiresAt != null && daysLeft <= ENDING_DAYS

    /** "ends today", "ends tomorrow", "ends in 9 days". */
    val endsText: String
        get() = when (daysLeft) {
            0 -> "ends today"
            1 -> "ends tomorrow"
            else -> "ends in $daysLeft days"
        }

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return listOf(name, licenceKey, email, gstin, loginId, phone)
            .any { it?.lowercase()?.contains(t) == true }
    }

    companion object {
        /** 4.72.0 — how far ahead the dashboard warns of a licence ending. */
        const val ENDING_DAYS = 15

        fun from(o: JSONObject) = Company(
            id = o.int("id"),
            name = o.str("name") ?: "-",
            licenceKey = o.str("licence_key") ?: "",
            email = o.str("email"),
            phone = o.str("phone"),
            state = o.str("state") ?: "DEMO",
            seats = o.int("seats", 1),
            seatsUsed = o.int("seats_used"),
            machinesUsed = o.int("machines_used"),
            gstin = o.str("gstin"),
            graceDays = o.int("grace_days"),
            isDemo = o.bool("is_demo"),
            expiresAt = o.str("expires_at"),
            periodStartedAt = o.str("period_started_at"),
            periodDays = o.int("period_days"),
            txnLimit = o.int("txn_limit"),
            txnUsed = o.int("txn_used"),
            aiDailyLimit = o.int("ai_daily_limit"),
            aiUsedToday = o.int("ai_used_today"),
            usageMinutes = o.int("usage_minutes"),
            loginId = o.str("login_id"),
            selfRegistered = o.bool("self_registered"),
            registeredIp = o.str("registered_ip"),
            registeredAt = o.str("registered_at"),
            gstStatus = o.str("gst_status"),
            gstCheckedAt = o.str("gst_checked_at"),
            gstNote = o.str("gst_note"),
            daysLeft = o.int("days_left"),
            expired = o.bool("expired"),
            usersCount = o.int("users_count"),
            usersTotal = o.int("users_total"),
            adminNames = o.str("admin_names"),
            userEmails = o.str("user_emails"),
            plan = o.str("plan")?.uppercase() ?: "PRO",
            renewSoon = o.bool("ending_soon"),
            notes = o.str("notes"),
            createdAt = o.str("created_at"),
            featureOverrides = overridesOf(o.opt("feature_overrides"))
        )

        /** {featureId: true|false} however the service kept it (an object, or that object as text); anything else is none. */
        internal fun overridesOf(v: Any?): Map<String, Boolean> {
            val o: JSONObject = when (v) {
                is JSONObject -> v
                is String -> runCatching { JSONObject(v) }.getOrNull() ?: return emptyMap()
                else -> return emptyMap()
            }
            val out = LinkedHashMap<String, Boolean>()
            o.keys().forEach { k -> (o.opt(k) as? Boolean)?.let { out[k] = it } }
            return out
        }
    }
}

/**
 * 4.72.0 — audit #40: A COMPANY THAT DELETE HAS ARCHIVED.
 *
 * Delete no longer erases a company at once. The service keeps it
 * [KEEP_DAYS] days with everything it had — its computers and phones stopped,
 * nobody able to sign in — lists it apart from the live companies (`archived`
 * in GET /admin/api/licences), and erases it for good after that. Until then
 * Restore (the company action `undelete`) puts it back exactly as it was.
 * Nothing else can be done to it: the service refuses every other action.
 */
data class DeletedCompany(
    val id: Int,
    val name: String,
    val email: String?,
    val phone: String?,
    val gstin: String?,
    val loginId: String?,
    val isDemo: Boolean,
    val deletedAt: String?,
    /** What it was when it was deleted (licensed, demo, suspended) — what Restore puts back. */
    val deletedState: String?,
    /** When it is erased for good. */
    val purgeAt: String?,
    /** Whole days left to restore it, as the service counts them. */
    val daysToPurge: Int,
    val machines: Int,
    val people: Int,
    val records: Int
) {
    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return listOf(name, email, gstin, loginId, phone).any { it?.lowercase()?.contains(t) == true }
    }

    companion object {
        /** The service's DELETED_KEEP_DAYS (server/src/licence.js). */
        const val KEEP_DAYS = 30

        fun from(o: JSONObject) = DeletedCompany(
            id = o.int("id"),
            name = o.str("name") ?: "-",
            email = o.str("email"),
            phone = o.str("phone"),
            gstin = o.str("gstin"),
            loginId = o.str("login_id"),
            isDemo = o.bool("is_demo"),
            deletedAt = o.str("deleted_at"),
            deletedState = o.str("deleted_state"),
            purgeAt = o.str("purge_at"),
            daysToPurge = o.int("days_to_purge").coerceAtLeast(0),
            machines = o.int("machines"),
            people = o.int("people"),
            records = o.int("records")
        )
    }
}

/**
 * 4.72.0 — audit #97: the computers and phones in use whose row holds no
 * device key yet. While there are any, a device id alone still re-joins as
 * that device; once [devices] is 0, NEXORA_DEVICE_KEY_REQUIRED=1 on Render
 * closes that for good. [required] — whether it is set already.
 */
data class Keyless(val devices: Int, val required: Boolean) {

    /** The one line the Machines screen shows. */
    val note: String
        get() {
            val who = if (devices == 1) "1 computer or phone in use holds" else "$devices computers and phones in use hold"
            return when {
                devices > 0 && !required ->
                    "Device keys: $who none yet — each hands its key over at its next check on Nexora 4.71.0 / Mobile 1.0.0 or later."
                devices > 0 ->
                    "Device keys: $who none yet, and NEXORA_DEVICE_KEY_REQUIRED is set — one that has also lost its token must join again."
                !required ->
                    "Device keys: every computer and phone in use holds its own — set NEXORA_DEVICE_KEY_REQUIRED=1 on Render to stop a device id alone re-joining."
                else ->
                    "Device keys: every computer and phone in use holds its own, and NEXORA_DEVICE_KEY_REQUIRED is set."
            }
        }

    companion object {
        /** null from a service that does not count them. */
        fun from(o: JSONObject?): Keyless? =
            if (o == null) null else Keyless(o.optInt("devices", 0).coerceAtLeast(0), o.optBoolean("required", false))
    }
}

/** One machine on a licence. */
data class Licence(
    val deviceId: String,
    val deviceName: String?,
    val company: String?,
    val email: String?,
    val state: String,
    val trialStartedAt: String?,
    val lastSeenAt: String?,
    val appVersion: String?,
    val companyId: Int,
    val seatNo: Int,
    val txnCount: Int,
    val usageMinutes: Int,
    val usageResetAt: String?,
    val coName: String?,
    val coKey: String?,
    val coState: String?,
    val coSeats: Int,
    /* 4.43.0 — who is signed in on this machine right now, and since when. */
    val onUser: String?,
    val onSince: String?,
    val daysLeft: Int,
    val expired: Boolean
) {
    /** A TRIAL that has run out reads EXPIRED; a suspended company wins. */
    val shownState: String
        get() {
            var s = if (state == "TRIAL" && expired) "EXPIRED" else state
            if (coState == "SUSPENDED" && s != "REVOKED") s = "SUSPENDED"
            return s
        }

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return listOf(company, coName, coKey, email, deviceId, deviceName)
            .any { it?.lowercase()?.contains(t) == true }
    }

    companion object {
        fun from(o: JSONObject) = Licence(
            deviceId = o.str("device_id") ?: "",
            deviceName = o.str("device_name"),
            company = o.str("company"),
            email = o.str("email"),
            state = o.str("state") ?: "TRIAL",
            trialStartedAt = o.str("trial_started_at"),
            lastSeenAt = o.str("last_seen_at"),
            appVersion = o.str("app_version"),
            companyId = o.int("company_id"),
            seatNo = o.int("seat_no"),
            txnCount = o.int("txn_count"),
            usageMinutes = o.int("usage_minutes"),
            usageResetAt = o.str("usage_reset_at"),
            coName = o.str("co_name"),
            coKey = o.str("co_key"),
            coState = o.str("co_state"),
            coSeats = o.int("co_seats", 1),
            onUser = o.str("on_user"),
            onSince = o.str("on_since"),
            daysLeft = o.int("days_left"),
            expired = o.bool("expired")
        )
    }
}

/* ---- the plans (1.5.0) ---------------------------------------------- */

/** One feature a plan may carry. The ids are the application's own.
 *  2.0.0 — this list is what the console falls back on when the service gives no plans
 *  (GET /admin/api/plans, an older service); the service's own list is what is shown otherwise. */
data class PlanFeature(val id: String, val label: String)

/** The catalogue, in the order the web console lists it. */
val PLAN_FEATURES: List<PlanFeature> = listOf(
    PlanFeature("quotation", "Quotation"),
    PlanFeature("chat", "Company conversation (chat)"),
    PlanFeature("notes", "Notes pad"),
    PlanFeature("bomWorkflow", "BOM workflow automation"),
    PlanFeature("onlinePrices", "Prices from the producer’s list"),
    PlanFeature("bagView", "3D bag view"),
    PlanFeature("ink", "Ink assumption"),
    PlanFeature("sharing", "Email & WhatsApp sharing"),
    PlanFeature("exportExcel", "Export to Excel"),
    PlanFeature("exportPdf", "Export to PDF"),
    PlanFeature("priceHistory", "RM price history (price versions)"),
    PlanFeature("activityLog", "Activity log"),
    PlanFeature("backup", "Backup & restore"),
    PlanFeature("numberSeries", "Document number series"),
    PlanFeature("tableSettings", "Table Settings (own column names)"),
    /* 4.51.0 — the BOM fills a section nobody has saved from what this
       plant itself usually does on that process. Sold per plan like the
       rest; with it off a plant works the old way and presses Suggest.

       4.55.0 — RENAMED. It read "BOM sections learned from the plant",
       which describes one thing it does. It now also matches a saved
       workflow to a calculation and says why, and gives a stage its shape
       from the process when nothing has been learned at all. The owner
       sells it as the learning itself, beside workflow automation, so the
       console says what it is. The id is unchanged: a licence in the
       field still gates on sectionSuggest. */
    PlanFeature("sectionSuggest", "BOM learning (MLM)"),
    /* 4.65.0 — "in console under plan add these all feature": the three
       cost tools of the desktop app, each its own line on the plan. The
       ids are the app's and the service's own. */
    PlanFeature("priceImpact", "Price Impact"),
    PlanFeature("compare", "Compare calculations"),
    PlanFeature("targetCost", "Target Cost"),
    /* 2026-09-28 — Nexora Mobile (licence A: one phone per person, PRO only). Listed here as well as
       in the service's plans.js and the web console, or saving the plan matrix from this app would
       switch it off. */
    PlanFeature("mobile", "Nexora Mobile (Android app)"),
    /* 2026-09-29 — Marketing in the desktop app 4.68.0 (PRO only: "pro ma j"). Listed here too, or saving the
       plan matrix from this app would switch it off. */
    PlanFeature("marketing", "Marketing (enquiries, customers, follow-ups)")
)

fun planMatrixFrom(o: JSONObject?): Map<String, Map<String, Boolean>> {
    if (o == null) return emptyMap()
    val out = HashMap<String, Map<String, Boolean>>()
    for (plan in listOf("STANDARD", "PRO")) {
        val row = o.optJSONObject(plan) ?: continue
        val m = HashMap<String, Boolean>()
        PLAN_FEATURES.forEach { f -> m[f.id] = row.optBoolean(f.id, false) }
        out[plan] = m
    }
    return out
}

/** A message Nexora put into every plant's conversation (1.5.0). */
data class Broadcast(val body: String, val at: String?, val rooms: Int) {
    val atText: String get() = Fmt.dateTime(at)
    companion object {
        fun from(o: JSONObject) = Broadcast(
            body = o.str("body") ?: "",
            at = o.str("at"),
            rooms = o.int("rooms")
        )
    }
}

/** Service settings — they apply to every installation from its next check. */
data class ServiceSettings(
    val trialDays: Int = 7,
    val demoGraceDays: Int = 0,
    val sessionMinutes: Int = 30,
    val expiredMode: String = "READONLY",
    val signupsOpen: Boolean = true,
    val demoSignup: Boolean = false,
    /* 1.5.0 — which features each plan carries: plan -> feature id -> on */
    val planFeatures: Map<String, Map<String, Boolean>> = emptyMap()
) {
    companion object {
        fun from(o: JSONObject?) = if (o == null) ServiceSettings() else ServiceSettings(
            trialDays = o.optInt("trialDays", 7),
            demoGraceDays = o.optInt("demoGraceDays", 0),
            sessionMinutes = o.optInt("sessionMinutes", 30),
            expiredMode = o.optString("expiredMode", "READONLY"),
            signupsOpen = o.optBoolean("signupsOpen", true),
            demoSignup = o.optBoolean("demoSignup", false),
            planFeatures = planMatrixFrom(o.optJSONObject("planFeatures"))
        )
    }
}

/** A person who may sign in on one of the company's seats. */
data class Person(
    val id: Int,
    val name: String,
    /* 4.42.0 — their own address. Optional: an operator who has none is not
       broken, they simply do not receive the circulars. */
    val email: String?,
    val role: String,
    val scope: String,
    val active: Boolean,
    val lastLoginAt: String?,
    /* 4.58.1 — the last time their software spoke to the service. A
       remembered session never types the PIN again, so "signed in" alone
       stood still while the person worked every day. */
    val lastSeenAt: String?,
    /* 4.43.0 — one person is signed in at one place at a time. This is that
       place, and when they took it; it is the first thing asked when somebody
       rings to say they cannot get in. */
    val sessionDevice: String?,
    val sessionAt: String?
) {
    val isAdmin get() = role == "ADMIN"
    val signedIn get() = !sessionDevice.isNullOrBlank()

    companion object {
        fun from(o: JSONObject) = Person(
            id = o.optInt("id"),
            name = o.optString("name", "-"),
            email = o.str("email"),
            role = o.optString("role", "USER"),
            scope = o.optString("scope", "OWN"),
            active = o.optBoolean("active", true),
            lastLoginAt = o.str("lastLoginAt"),
            lastSeenAt = o.str("lastSeenAt"),
            sessionDevice = o.str("sessionDevice"),
            sessionAt = o.str("sessionAt")
        )
    }
}

data class People(val users: List<Person>, val capMax: Int, val capCount: Int)

/**
 * A lead — somebody who has asked about the software but has not bought it.
 * They arrive from the website's forms, or the owner types in the one that
 * came by phone. Same table either way.
 */
data class Inquiry(
    val id: Int,
    val name: String,
    val company: String?,
    val phone: String?,
    val email: String?,
    val product: String,
    val message: String?,
    val state: String,
    val source: String,
    val sourcePage: String?,
    val channel: String?,
    val notes: String?,
    val followUp: String?,
    val companyId: Int?,
    val coName: String?,
    val createdAt: String?,
    val updatedAt: String?,
    /* 1.8.1 (Nexora 4.73.0, C17) — the website's enquiry form 2: where the plant manufactures, its website,
       and its product range (the ticks, with the words written beside "Other"). Null / empty on an enquiry
       from before it and on one typed in by hand. Plain text from a public form: shown, never opened. */
    val location: String? = null,
    val website: String? = null,
    val products: List<String> = emptyList(),
    val productOther: String? = null
) {
    val isOpen: Boolean get() = state != "WON" && state != "LOST"

    /**
     * The product range as the card lists it: each tick in the form's order, "Other" carrying what was
     * written beside it ("Other: Jumbo bags"), and words for Other with no Other tick still shown.
     */
    val productLines: List<String>
        get() {
            val other = productOther?.takeIf { it.isNotBlank() }
            val out = products.map { p -> if (p.equals("Other", ignoreCase = true) && other != null) "Other: $other" else p }
            return if (other != null && products.none { it.equals("Other", ignoreCase = true) }) out + "Other: $other" else out
        }

    /** Whether the card has any of the form-2 facts to show. */
    val hasPlantFacts: Boolean get() = !location.isNullOrBlank() || !website.isNullOrBlank() || productLines.isNotEmpty()

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return (listOf(name, company, phone, email, product, message, notes, coName, location, website, productOther) + products)
            .any { it?.lowercase()?.contains(t) == true }
    }

    companion object {
        fun from(o: JSONObject) = Inquiry(
            id = o.optInt("id"),
            name = o.str("name") ?: "-",
            company = o.str("company"),
            phone = o.str("phone"),
            email = o.str("email"),
            product = o.str("product") ?: "Other",
            message = o.str("message"),
            state = o.str("state") ?: "NEW",
            source = o.str("source") ?: "MANUAL",
            sourcePage = o.str("sourcePage"),
            channel = o.str("channel"),
            notes = o.str("notes"),
            followUp = o.str("followUp"),
            companyId = o.optInt("companyId").takeIf { it > 0 },
            coName = o.str("coName"),
            createdAt = o.str("createdAt"),
            updatedAt = o.str("updatedAt"),
            location = o.str("location")?.oneLine(),
            website = o.str("website")?.oneLine(),
            products = productsOf(o.opt("products")),
            productOther = (o.str("productOther") ?: o.str("product_other"))?.oneLine()
        )

        /* One line each, whatever the service kept: a control character is a space (the service makes them so
           too, since 4.72.0), and runs of spaces are one. */
        private fun String.oneLine(): String? =
            replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ").trim().replace(Regex("\\s+"), " ").ifEmpty { null }

        /** The ticks — a JSON array (as the service sends them), or that array written as text; anything else is none. */
        internal fun productsOf(v: Any?): List<String> {
            val arr: JSONArray = when (v) {
                is JSONArray -> v
                is String -> {
                    val s = v.trim()
                    if (!s.startsWith("[")) return listOfNotNull(v.oneLine())
                    runCatching { JSONArray(s) }.getOrNull() ?: return emptyList()
                }
                else -> return emptyList()
            }
            return (0 until arr.length())
                .mapNotNull { i -> arr.opt(i)?.takeIf { it is String }?.let { (it as String).oneLine() } }
                .distinct()
        }
    }
}

/**
 * A report from inside the application — Help → Nexora Contact. FEEDBACK
 * is words; a BUG usually carries a picture of the screen, which is not in
 * this object: it is fetched on its own when the report is opened.
 */
data class Feedback(
    val id: Int,
    val kind: String,
    val subject: String?,
    val message: String,
    val name: String?,
    val phone: String?,
    val email: String?,
    val company: String?,
    val companyId: Int?,
    val coName: String?,
    val licenceKey: String?,
    val userName: String?,
    val deviceId: String?,
    val deviceName: String?,
    val appVersion: String?,
    val edition: String?,
    val view: String?,
    val hasShot: Boolean,
    val state: String,
    val reply: String?,
    val createdAt: String?,
    val updatedAt: String?
) {
    val isBug: Boolean get() = kind == "BUG"
    val isOpen: Boolean get() = state == "NEW" || state == "SEEN"
    val plant: String get() = coName ?: company ?: "Unknown plant"
    val who: String? get() = name?.takeIf { it.isNotBlank() } ?: userName?.takeIf { it.isNotBlank() }

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return listOf(subject, message, name, userName, company, coName, deviceName, view, reply, appVersion)
            .any { it?.lowercase()?.contains(t) == true }
    }

    companion object {
        fun from(o: JSONObject) = Feedback(
            id = o.optInt("id"),
            kind = o.str("kind") ?: "FEEDBACK",
            subject = o.str("subject"),
            message = o.str("message") ?: "",
            name = o.str("name"),
            phone = o.str("phone"),
            email = o.str("email"),
            company = o.str("company"),
            companyId = o.optInt("companyId").takeIf { it > 0 },
            coName = o.str("coName"),
            licenceKey = o.str("licenceKey"),
            userName = o.str("userName"),
            deviceId = o.str("deviceId"),
            deviceName = o.str("deviceName"),
            appVersion = o.str("appVersion"),
            edition = o.str("edition"),
            view = o.str("view"),
            hasShot = o.optBoolean("hasShot", false),
            state = o.str("state") ?: "NEW",
            reply = o.str("reply"),
            createdAt = o.str("createdAt"),
            updatedAt = o.str("updatedAt")
        )
    }
}

/** Everything /admin/api/feedback answers with. */
data class FeedbackData(
    val feedback: List<Feedback> = emptyList(),
    val kinds: List<String> = listOf("FEEDBACK", "BUG"),
    val states: List<String> = listOf("NEW", "SEEN", "FIXED", "CLOSED")
) {
    companion object {
        fun from(o: JSONObject): FeedbackData {
            fun strings(k: String, dflt: List<String>): List<String> {
                val a = o.optJSONArray(k) ?: return dflt
                val out = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
                return out.ifEmpty { dflt }
            }
            val a = o.optJSONArray("feedback")
            return FeedbackData(
                feedback = if (a == null) emptyList()
                else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Feedback.from(it) } },
                kinds = strings("kinds", listOf("FEEDBACK", "BUG")),
                states = strings("states", listOf("NEW", "SEEN", "FIXED", "CLOSED"))
            )
        }
    }
}

/** Everything /admin/api/inquiries answers with, lists included. */
data class InquiryData(
    val inquiries: List<Inquiry> = emptyList(),
    val products: List<String> = DEFAULT_PRODUCTS,
    val states: List<String> = DEFAULT_STATES,
    val sources: List<String> = DEFAULT_SOURCES
) {
    companion object {
        fun from(o: JSONObject): InquiryData {
            fun strings(k: String, dflt: List<String>): List<String> {
                val a = o.optJSONArray(k) ?: return dflt
                val out = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
                return out.ifEmpty { dflt }
            }
            val a = o.optJSONArray("inquiries")
            return InquiryData(
                inquiries = if (a == null) emptyList()
                else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Inquiry.from(it) } },
                products = strings("products", DEFAULT_PRODUCTS),
                states = strings("states", DEFAULT_STATES),
                sources = strings("sources", DEFAULT_SOURCES)
            )
        }
    }
}

/* The service sends its own lists; these are what the screen draws before
   the first answer arrives, and if an older service sends none. */
val DEFAULT_PRODUCTS = listOf(
    "Bag Weight & Cost Forecasting", "Nexora ERP", "Inventory & Roll Traceability",
    "HR & Payroll", "Maintenance", "Staff Tracking", "ERP Implementation",
    "Website Development", "Custom Software", "AMC & Support", "Jobwork Module", "Other"
)
val DEFAULT_STATES = listOf("NEW", "CONTACTED", "DEMO", "QUOTED", "WON", "LOST")
val DEFAULT_SOURCES =
    listOf("WEBSITE", "MANUAL", "PHONE", "WHATSAPP", "REFERRAL", "VISIT", "EXHIBITION")

/**
 * 4.72.0 — audit #43: what GET /admin/api/summary answers — a handful of
 * counts, cheap for the service, in place of the full listings the
 * quarter-hourly watch used to pull. [at] is the service's own clock; the
 * next look sends it back as ?since= so nothing falls between two looks
 * whatever the phone's clock says.
 */
data class Summary(
    val at: String,
    val since: String?,
    val companies: Int,
    val licences: Int,
    val newEnquiries: Int,
    val newFeedback: Int,
    val newRegistrations: Int
) {
    companion object {
        /** ApiError.code when an answer came back but not in the agreed form. */
        const val NOT_A_SUMMARY = "NOT_A_SUMMARY"

        /* The three counts the watch acts on (contract C11), by these exact names. */
        private val COUNTS = listOf("newEnquiries", "newFeedback", "newRegistrations")

        /**
         * null when the answer is not a summary (an older service, or an error).
         *
         * 4.72.0 (review) — strict about what the watch relies on: [at] must be
         * an ISO-8601 moment with its zone (it goes back as ?since=; a number,
         * or a date without a zone, could make the service count a whole day
         * again on every look), and each count must be there by its contract
         * name — a count named otherwise would read as "nothing new" for ever.
         */
        fun from(o: JSONObject): Summary? {
            if (o.str("error") != null) return null
            val at = o.str("at") ?: return null
            if (!isoMoment(at)) return null
            if (COUNTS.any { k -> !o.has(k) || o.isNull(k) || o.optInt(k, Int.MIN_VALUE) == Int.MIN_VALUE }) return null
            return Summary(
                at = at,
                since = o.str("since"),
                companies = o.int("companies"),
                licences = o.int("licences"),
                newEnquiries = o.int("newEnquiries").coerceAtLeast(0),
                newFeedback = o.int("newFeedback").coerceAtLeast(0),
                newRegistrations = o.int("newRegistrations").coerceAtLeast(0)
            )
        }

        /* "2026-10-02T03:15:00.000Z" or "…+05:30": a moment with its zone. */
        private fun isoMoment(s: String): Boolean =
            runCatching { Instant.parse(s) }.isSuccess ||
                runCatching { java.time.OffsetDateTime.parse(s) }.isSuccess
    }
}

/** Everything one screen needs, as /admin/api/licences returns it. */
data class ConsoleData(
    val licences: List<Licence> = emptyList(),
    val companies: List<Company> = emptyList(),
    val settings: ServiceSettings = ServiceSettings(),
    /* 4.72.0 — audit #40: what Delete has archived, newest first (restorable for 30 days) */
    val archived: List<DeletedCompany> = emptyList(),
    /** True when the service keeps a deleted company for 30 days (its listing
     *  carries `archived`, even empty). An older service erases one at once,
     *  and the console must not promise a Restore that cannot be given. */
    val keepsDeleted: Boolean = false,
    /* 4.72.0 — audit #97: null from a service that does not count them */
    val keyless: Keyless? = null
) {
    companion object {
        fun from(o: JSONObject): ConsoleData {
            fun <T> arr(k: String, f: (JSONObject) -> T): List<T> {
                val a: JSONArray = o.optJSONArray(k) ?: return emptyList()
                return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(f) }
            }
            return ConsoleData(
                licences = arr("licences") { Licence.from(it) },
                companies = arr("companies") { Company.from(it) },
                settings = ServiceSettings.from(o.optJSONObject("settings")),
                archived = arr("archived") { DeletedCompany.from(it) },
                keepsDeleted = o.optJSONArray("archived") != null,
                keyless = Keyless.from(o.optJSONObject("keyless"))
            )
        }
    }
}

/* ---- the console's own formatters ---- */
object Fmt {

    /** fmt() — "19 Sep 26", or a dash when there is no date at all.
     *  2.0.0 — a plain date ("2026-10-08", as a payment's paid-on) is that day, never moved by a time zone. */
    fun day(iso: String?): String {
        plainDate(iso)?.let { return DateTimeFormatter.ofPattern("dd MMM yy", Locale.getDefault()).format(it) }
        val i = instant(iso) ?: return "-"
        return DateTimeFormatter.ofPattern("dd MMM yy", Locale.getDefault())
            .format(i.atZone(ZoneId.systemDefault()))
    }

    fun dateTime(iso: String?): String {
        val i = instant(iso) ?: return "never"
        return DateTimeFormatter.ofPattern("dd MMM yy, HH:mm", Locale.getDefault())
            .format(i.atZone(ZoneId.systemDefault()))
    }

    /** 4.58.1 — "just now", "12 min ago", "3 h ago", "4 days ago". */
    fun ago(iso: String?): String {
        val i = instant(iso) ?: return ""
        val s = java.time.Duration.between(i, Instant.now()).seconds.coerceAtLeast(0)
        if (s < 60) return "just now"
        val m = (s + 30) / 60
        if (m < 60) return "$m min ago"
        val h = (m + 30) / 60
        if (h < 48) return "$h h ago"
        return "${(h + 12) / 24} days ago"
    }

    /** 4.58.1 — the clock time alone, for "up to date — 16:42:05". */
    fun clock(): String = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
        .format(Instant.now().atZone(ZoneId.systemDefault()))

    /** hoursText() — "3 h 20 m", or "20 m" under the hour. */
    fun hours(mins: Int): String {
        val m = if (mins < 0) 0 else mins
        val h = m / 60
        return if (h > 0) "$h h ${m % 60} m" else "${m % 60} m"
    }

    /** "2026-10-08" as a date; null for anything else (a moment with a time is read by [day] as before). */
    fun plainDate(iso: String?): java.time.LocalDate? {
        if (iso == null || iso.length != 10) return null
        return runCatching { java.time.LocalDate.parse(iso) }.getOrNull()
    }

    private fun instant(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso)
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(iso).toInstant()
            } catch (_: Exception) {
                try {
                    java.time.LocalDateTime.parse(iso.take(19))
                        .atZone(ZoneId.of("UTC")).toInstant()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
