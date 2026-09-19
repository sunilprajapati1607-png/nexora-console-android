package org.nexoraofficial.console.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** What every call comes back as: a body, and never a thrown surprise. */
class ApiError(message: String) : Exception(message)

/**
 * THE SAME FOUR ENDPOINTS THE WEB CONSOLE CALLS.
 *
 * One admin key, sent as x-admin-key, exactly as server/src/index.js expects
 * it. No library: the whole surface is one GET and four POSTs, and org.json
 * ships with Android, so the app carries no HTTP dependency at all.
 *
 * The timeouts are deliberately long. The service sleeps on its free tier and
 * a first call after a quiet night can take half a minute to answer; a short
 * timeout would report that as a failure the owner cannot act on.
 */
class Api(private val baseUrl: String, private val key: String) {

    suspend fun licences(): ConsoleData = withContext(Dispatchers.IO) {
        ConsoleData.from(request("GET", "/admin/api/licences", null))
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
    suspend fun inquiries(): InquiryData = withContext(Dispatchers.IO) {
        InquiryData.from(request("GET", "/admin/api/inquiries", null))
    }

    suspend fun inquiry(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/admin/api/inquiry", body)
    }

    /** 4.44.0 — what build of this application the owner has published. */
    suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
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

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("x-admin-key", key)
            setRequestProperty("content-type", "application/json")
            setRequestProperty("accept", "application/json")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            val text = (if (status in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (status == 401) throw ApiError("That admin key was not accepted.")

            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (_: Exception) {
                JSONObject()
            }
            if (status !in 200..299 && json.optString("error").isEmpty()) {
                throw ApiError("Request failed ($status)")
            }
            return json
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
