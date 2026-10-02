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
    private val anyHost: Boolean = BuildConfig.DEBUG
) : WatchSource {

    companion object {
        /** What every call says it is, in the `x-console` header (the service reads 'android' or 'web'). */
        const val CONSOLE_NAME = "android"
    }

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

    private fun request(method: String, path: String, body: JSONObject?): JSONObject =
        exchange(method, path, body).second

    /** One call: the HTTP status and the JSON that came back. */
    private fun exchange(method: String, path: String, body: JSONObject?): Pair<Int, JSONObject> {
        /* 4.72.0 — audit #36: refused before a connection is even opened, so
           the key never leaves the phone for an address that is not Nexora's. */
        ServiceHost.problem(baseUrl, anyHost)?.let { throw ApiError(it) }
        val url = URL(baseUrl.trim().trimEnd('/') + path)
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
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
