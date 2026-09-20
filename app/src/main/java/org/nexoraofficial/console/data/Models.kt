package org.nexoraofficial.console.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---- small readers, so a missing column is a blank and never a crash ---- */
private fun JSONObject.str(k: String): String? {
    if (!has(k) || isNull(k)) return null
    val v = optString(k, "")
    return if (v.isEmpty()) null else v
}

private fun JSONObject.int(k: String, dflt: Int = 0): Int = optInt(k, dflt)
private fun JSONObject.bool(k: String): Boolean = optBoolean(k, false)

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
    val txnLimit: Int,
    val txnUsed: Int,
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
    val userEmails: String?
) {
    /** The same reading the console shows on the pill. */
    val shownState: String
        get() = if (expired && state != "SUSPENDED") "EXPIRED" else state

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return listOf(name, licenceKey, email, gstin, loginId, phone)
            .any { it?.lowercase()?.contains(t) == true }
    }

    companion object {
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
            txnLimit = o.int("txn_limit"),
            txnUsed = o.int("txn_used"),
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
            userEmails = o.str("user_emails")
        )
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

/** Service settings — they apply to every installation from its next check. */
data class ServiceSettings(
    val trialDays: Int = 7,
    val demoGraceDays: Int = 0,
    val sessionMinutes: Int = 30,
    val expiredMode: String = "READONLY",
    val signupsOpen: Boolean = true,
    val demoSignup: Boolean = false
) {
    companion object {
        fun from(o: JSONObject?) = if (o == null) ServiceSettings() else ServiceSettings(
            trialDays = o.optInt("trialDays", 7),
            demoGraceDays = o.optInt("demoGraceDays", 0),
            sessionMinutes = o.optInt("sessionMinutes", 30),
            expiredMode = o.optString("expiredMode", "READONLY"),
            signupsOpen = o.optBoolean("signupsOpen", true),
            demoSignup = o.optBoolean("demoSignup", false)
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
    val updatedAt: String?
) {
    val isOpen: Boolean get() = state != "WON" && state != "LOST"

    fun matches(term: String): Boolean {
        if (term.isBlank()) return true
        val t = term.lowercase()
        return listOf(name, company, phone, email, product, message, notes, coName)
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
            updatedAt = o.str("updatedAt")
        )
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

/** Everything one screen needs, as /admin/api/licences returns it. */
data class ConsoleData(
    val licences: List<Licence> = emptyList(),
    val companies: List<Company> = emptyList(),
    val settings: ServiceSettings = ServiceSettings()
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
                settings = ServiceSettings.from(o.optJSONObject("settings"))
            )
        }
    }
}

/* ---- the console's own formatters ---- */
object Fmt {

    /** fmt() — "19 Sep 26", or a dash when there is no date at all. */
    fun day(iso: String?): String {
        val i = instant(iso) ?: return "-"
        return DateTimeFormatter.ofPattern("dd MMM yy", Locale.getDefault())
            .format(i.atZone(ZoneId.systemDefault()))
    }

    fun dateTime(iso: String?): String {
        val i = instant(iso) ?: return "never"
        return DateTimeFormatter.ofPattern("dd MMM yy, HH:mm", Locale.getDefault())
            .format(i.atZone(ZoneId.systemDefault()))
    }

    /** hoursText() — "3 h 20 m", or "20 m" under the hour. */
    fun hours(mins: Int): String {
        val m = if (mins < 0) 0 else mins
        val h = m / 60
        return if (h > 0) "$h h ${m % 60} m" else "${m % 60} m"
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
