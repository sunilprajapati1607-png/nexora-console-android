package org.nexoraofficial.console.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.nexoraofficial.console.BuildConfig
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * What every call comes back as: a body, and never a thrown surprise.
 *
 * 4.72.0 (review) — with the HTTP [status] and the service's own error
 * [code] where there was one, so the background watch can tell "the key was
 * refused" or "the service has no summary yet" from "the service is asleep".
 */
class ApiError(message: String, val status: Int = 0, val code: String = "") : Exception(message) {
    /** The service did not accept the admin key (401). */
    val keyRefused: Boolean get() = status == 401

    companion object {
        /** What any redirect (3xx) is told as: it is never followed with the key. */
        const val MOVED = "The service moved; check the address."
    }
}

/**
 * 4.72.0 — what the quarter-hourly watch may ask the service (audit #43):
 * the summary, and the two short lists only when the summary says something
 * new arrived.
 *
 * 4.72.0 (review) — and [licences], the full company listing (every licence
 * key and every staff e-mail), for one case only: a service that gives no
 * summary (not updated yet), where the watch keeps the news coming the 1.7.1
 * way (Watch.kt, OldWay) — asked at most once a look, as 1.7.1 asked it, and
 * only from 08:30 to 20:30 like every look. A service with the summary is
 * never asked for it in the background.
 */
interface WatchSource {
    suspend fun summary(since: String?): Summary
    suspend fun inquiries(): InquiryData
    suspend fun feedback(): FeedbackData
    suspend fun licences(): ConsoleData
    suspend fun latestRelease(): Release?

    /**
     * 1.9.0 — every Nexora software, Fabric Stock's companies with it. Read by
     * the console when it is open; the watch never asks for it — a whole company
     * list is not fetched in the background (4.72.0, audit 43). null from a
     * source that cannot say (the tests' stand-ins).
     */
    suspend fun products(): ProductsData? = null
}

/**
 * THE SAME FOUR ENDPOINTS THE WEB CONSOLE CALLS.
 *
 * One admin key, sent as x-admin-key, exactly as server/src/index.js expects
 * it — and, since 4.72.0, `x-console: android` on every call, so the service's
 * audit trail says which console acted. No library: the whole surface is one
 * GET and four POSTs, and org.json ships with Android, so the app carries no
 * HTTP dependency at all.
 *
 * The timeouts are deliberately long. The service sleeps on its free tier and
 * a first call after a quiet night can take half a minute to answer; a short
 * timeout would report that as a failure the owner cannot act on.
 */
class Api(
    private val baseUrl: String,
    private val key: String,
    /* 4.72.0 — audit #36: a release build sends the key only to Nexora's own service */
    private val anyHost: Boolean = BuildConfig.DEBUG,
    /* 2.3.1 — how long a sleeping service is waited for, quietly, before "waking" is said (tests shorten it) */
    private val wakeWaitMs: Long = WAKE_WAIT_MS
) : WatchSource {

    companion object {
        /** What every call says it is, in the `x-console` header (the service reads 'android' or 'web'). */
        const val CONSOLE_NAME = "android"

        /** 1.9.0 — how long a Fabric Stock call may take to answer: its service can take ~55 s to wake. */
        const val SLOW_READ_MS = 90_000

        /** 2.3.1 — a free service asleep takes 15–60 s to wake; up to 2 minutes is waited for. */
        const val WAKE_WAIT_MS = 120_000L

        /** 2.3.1 — what is said when the servers are still waking after the wait. */
        const val WAKING = "The servers are waking up (they sleep when nobody uses them). Try again in a minute."
    }

    /* 2.3.1 — Render's own front door answers 429 / 502 / 503 / 504 (not JSON) while a sleeping service wakes.
       The request never reached the service, so it is asked again — a POST too — every few seconds. */
    private class FrontDoor(val status: Int) : Exception()

    override suspend fun licences(): ConsoleData = withContext(Dispatchers.IO) {
        ConsoleData.from(request("GET", "/admin/api/licences", null))
    }

    /**
     * 4.72.0 — audit #43 (contract C11): a few counts since [since] (the
     * service's own "at" from the last look; the service's default when null).
     */
    /* 4.72.0 (review) — a failure says what it was: the service's own error
       code with its status (404 NOT_FOUND is a service without the route), or
       Summary.NOT_A_SUMMARY when an answer came but not in the agreed form
       (a misnamed count reads as that, never as "nothing new"). */
    override suspend fun summary(since: String?): Summary = withContext(Dispatchers.IO) {
        val q = if (since.isNullOrBlank()) "" else "?since=" + URLEncoder.encode(since, "UTF-8")
        val (status, r) = exchange("GET", "/admin/api/summary$q", null)
        val err = r.optString("error")
        if (err.isNotEmpty()) throw ApiError(r.optString("message").ifEmpty { err }, status, err)
        Summary.from(r) ?: throw ApiError("The service's summary is not in the form this console reads.", status, Summary.NOT_A_SUMMARY)
    }

    suspend fun company(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/company", body)
    }

    suspend fun licence(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/licence", body)
    }

    suspend fun settings(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/settings", body)
    }

    suspend fun gst(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/gst", body)
    }

    /** The leads, newest first, with the lists the screen offers. */
    override suspend fun inquiries(): InquiryData = withContext(Dispatchers.IO) {
        InquiryData.from(request("GET", "/admin/api/inquiries", null))
    }

    suspend fun inquiry(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/inquiry", body)
    }

    /** 1.4.0 — feedback and problem reports sent from inside the application. */
    override suspend fun feedback(): FeedbackData = withContext(Dispatchers.IO) {
        FeedbackData.from(request("GET", "/admin/api/feedback", null))
    }

    suspend fun feedbackAction(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/feedback", body)
    }

    /** The picture on one report, as a data URL — or null when there is none. */
    suspend fun feedbackShot(id: Int): String? = withContext(Dispatchers.IO) {
        request("GET", "/admin/api/feedback/shot?id=$id", null)
            .optString("shot").takeIf { it.startsWith("data:image") }
    }

    /** 4.44.0 — what build of this application the owner has published. */
    /* 1.5.0 — what Nexora said in every room, and saying more */
    suspend fun broadcasts(): List<Broadcast> = withContext(Dispatchers.IO) {
        val a = request("GET", "/admin/api/broadcast", null).optJSONArray("broadcasts") ?: return@withContext emptyList()
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Broadcast.from(it) } }
    }

    suspend fun broadcast(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/broadcast", body)
    }

    override suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
        Release.from(request("GET", "/admin/api/app/latest", null).optJSONObject("release"))
    }

    /** The people on a company, as the console's `users` action returns them. */
    suspend fun people(companyId: Int): People {
        val r = company(JSONObject().put("id", companyId).put("action", "users"))
        r.optString("error").takeIf { it.isNotEmpty() }?.let { throw ApiError(it) }
        val cap = r.optJSONObject("cap")
        val a = r.optJSONArray("users")
        val users = if (a == null) emptyList() else
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Person.from(it) } }
        return People(
            users = users,
            capMax = cap?.optInt("max", 0) ?: 0,
            capCount = cap?.optInt("count", users.size) ?: users.size
        )
    }

    /* ---------- 1.9.0: every Nexora software in the one console ---------- */

    /**
     * Every software the service sells, and Fabric Stock's companies. The
     * service asks Fabric Stock's own service for them, which sleeps on its
     * free tier: a first call after a quiet hour can take most of a minute,
     * so this is read apart from the licences and never holds a screen up.
     */
    override suspend fun products(): ProductsData = withContext(Dispatchers.IO) {
        ProductsData.from(checked("GET", "/admin/api/products", null))
    }

    /** One change to a Fabric Stock company ({action, ...}); every failure is thrown in the service's own words. */
    suspend fun fabric(body: JSONObject): JSONObject = remoteAction(Software.FABRIC, body)

    /**
     * 2.1.0 — one change to a company on any software the console service reaches (fabric, erp, consult):
     * POST /admin/api/<sw>, the same actions for each. A software that is not one of them is refused here,
     * before anything is sent.
     */
    suspend fun remoteAction(sw: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        if (!Software.isRemote(sw)) throw ApiError("${Software.name(sw)} is not reached through the console service.")
        checked("POST", "/admin/api/$sw", body)
    }

    /** One Fabric Stock company with its people and its computers and phones. */
    suspend fun fabricDetail(id: Int): FabricDetail = remoteDetail(Software.FABRIC, id)

    /** 2.1.0 — one company on remote software [sw], with its people and its computers and phones. */
    suspend fun remoteDetail(sw: String, id: Int): FabricDetail =
        FabricDetail.from(remoteAction(sw, JSONObject().put("action", "detail").put("id", id)), sw)

    /* ---------- 2.1.0: each software's own demo rules, kept on its own server ---------- */

    /** Every software's demo rules (read when the console is open — never by the background watch). */
    suspend fun softwareSettings(): SoftwareRulesData = withContext(Dispatchers.IO) {
        SoftwareRulesData.from(checked("GET", "/admin/api/software-settings", null))
    }

    /** One software's demo rules changed ({software, trialDays, …}); a refusal is thrown in the service's own words. */
    suspend fun saveSoftwareSettings(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        checked("POST", "/admin/api/software-settings", body)
    }

    /* ---------- 2.0.0: plans per software, and the payments ledger ---------- */

    /**
     * Every software's plans and features. Read when the console is open — never by the quarter-hourly
     * watch (audit 43). Fabric Stock's block is asked of its own service, so this may take a while.
     */
    suspend fun plans(): PlansData = withContext(Dispatchers.IO) {
        PlansData.from(checked("GET", "/admin/api/plans", null))
    }

    /** create | update | retire | restore | delete a plan; a refusal is thrown in the service's own words (501 NOT_YET for Fabric Stock). */
    suspend fun planAction(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        checked("POST", "/admin/api/plans", body)
    }

    /** The payments, newest first, with their totals. Read when the console is open — never in the background. */
    suspend fun payments(): PaymentsData = withContext(Dispatchers.IO) {
        PaymentsData.from(checked("GET", "/admin/api/payments", null))
    }

    /** add | update | delete a payment (the service says which console recorded it, from x-console). */
    suspend fun paymentAction(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        checked("POST", "/admin/api/payments", body)
    }

    /* ---- 2.3.0: invoices (the console service numbers them and does every sum) ---- */

    /** Your company as printed on every invoice, and whether an invoice can be made with it. */
    suspend fun invoiceSettings(): InvoiceSettingsData = withContext(Dispatchers.IO) {
        InvoiceSettingsData.from(checked("GET", "/admin/api/invoice-settings", null))
    }

    /** Save Invoice Settings; a wrong GSTIN, IFSC, PAN, rate or prefix comes back 400 in the service's words. */
    suspend fun saveInvoiceSettings(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        checked("POST", "/admin/api/invoice-settings", body)
    }

    /** The invoices, narrowed by [query] (paymentId, software, companyId, remoteId, from, to, gst=yes|no). */
    suspend fun invoices(query: Map<String, String> = emptyMap()): List<Invoice> = withContext(Dispatchers.IO) {
        val q = query.filterValues { it.isNotBlank() }.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        Invoice.listFrom(checked("GET", "/admin/api/invoices" + (if (q.isEmpty()) "" else "?$q"), null))
    }

    /** create | cancel an invoice; 409 NOT_SET_UP / NO_GSTIN when Invoice Settings are missing. */
    suspend fun invoiceAction(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        checked("POST", "/admin/api/invoices", body)
    }

    /* 200 is done; any other answer is a failure carrying {error, message} — 502 FABRIC_DOWN or
       FABRIC_KEY when Fabric Stock is asleep or refuses the key — told in the message's words.
       Read for longer than the rest, for Fabric Stock waking. */
    private fun checked(method: String, path: String, body: JSONObject?): JSONObject {
        val (status, r) = exchange(method, path, body, SLOW_READ_MS)
        val err = r.optString("error")
        if (status !in 200..299 || err.isNotEmpty()) {
            throw ApiError(r.optString("message").ifEmpty { err.ifEmpty { "Request failed ($status)" } }, status, err)
        }
        return r
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject =
        exchange(method, path, body).second

    /** One call: the HTTP status and the JSON that came back — waiting quietly while a sleeping service wakes. */
    private fun exchange(method: String, path: String, body: JSONObject?, readMs: Int = 60_000): Pair<Int, JSONObject> {
        val until = System.currentTimeMillis() + wakeWaitMs
        var n = 0
        while (true) {
            try {
                return exchangeOnce(method, path, body, readMs)
            } catch (e: FrontDoor) {
                val pause = minOf(5_000L, 1_000L * (n + 1))
                n++
                /* status 0: "asleep", never the 429 of an address shut out for wrong keys */
                if (System.currentTimeMillis() + pause > until) throw ApiError(WAKING, 0, "WAKING")
                Thread.sleep(pause)
            }
        }
    }

    private fun exchangeOnce(method: String, path: String, body: JSONObject?, readMs: Int): Pair<Int, JSONObject> {
        /* 4.72.0 — audit #36: refused before a connection is even opened, so
           the key never leaves the phone for an address that is not Nexora's. */
        ServiceHost.problem(baseUrl, anyHost)?.let { throw ApiError(it) }
        val url = URL(baseUrl.trim().trimEnd('/') + path)
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = readMs
            /* 4.72.0 (review of audit #36) — a redirect is never followed.
               Android's HttpURLConnection follows one by itself and drops only
               an Authorization header when the host changes, so x-admin-key
               would have gone on to wherever the service (or a website in
               front of it) pointed. The service itself never redirects. */
            instanceFollowRedirects = false
            setRequestProperty("x-admin-key", key)
            /* 4.72.0 — audit #39: says which console acted, so every event the
               service logs during this call reads app 'android' (admin.js
               consoleCaller) rather than a guess from the user-agent. */
            setRequestProperty("x-console", CONSOLE_NAME)
            setRequestProperty("content-type", "application/json")
            setRequestProperty("accept", "application/json")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            /* Any 3xx at all: nothing is sent on, and the owner is told where to look. */
            if (status in 300..399) throw ApiError(ApiError.MOVED, status, "MOVED")
            if (status in setOf(429, 502, 503, 504) && !(c.contentType ?: "").contains("json", ignoreCase = true)) throw FrontDoor(status)
            val text = (if (status in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (status == 401) throw ApiError("That admin key was not accepted.", status, "UNAUTHORISED")

            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (_: Exception) {
                JSONObject()
            }
            if (status !in 200..299 && json.optString("error").isEmpty()) {
                throw ApiError("Request failed ($status)", status)
            }
            return status to json
        } catch (e: ApiError) {
            throw e
        } catch (e: FrontDoor) {
            throw e
        } catch (e: Exception) {
            /* A name that will not resolve, a phone with no signal, a service
               still waking: all the same thing to the owner — it could not be
               reached — so it is said in those words rather than in Java's. */
            throw ApiError("Could not reach the service. ${e.message ?: "No connection."}")
        } finally {
            c.disconnect()
        }
    }
}
