package org.nexoraofficial.console.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * UPDATING THE APPLICATION FROM INSIDE THE APPLICATION.
 *
 *   "add features in app that everytime we can update app via app,
 *    use github or server to push update directly to apk file"
 *
 * This application is not on Play, so nothing tells it a new build exists.
 * The service does: the owner publishes a version code, a name and a URL in
 * the console, the phone asks for it, and if the code is higher than the one
 * it is running it offers to fetch and install it.
 *
 * The service holds only the description. The APK is fetched from wherever
 * the URL points — a GitHub release asset, a file on the site, anywhere over
 * https — which is what keeps a sixteen-megabyte binary out of the service
 * and lets the hosting change without a new build of this.
 *
 * Two things are checked before anything is installed. The address must be
 * https, because an APK is executable code. And if the publisher gave a
 * SHA-256, the downloaded bytes must match it, so a file swapped in transit
 * or a half-finished download is refused rather than handed to the installer.
 */
data class Release(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String?,
    val sha256: String?,
    val sizeBytes: Long?,
    val mandatory: Boolean,
    val publishedAt: String?
) {
    companion object {
        fun from(o: JSONObject?): Release? {
            if (o == null) return null
            val code = o.optInt("versionCode", 0)
            val url = o.optString("url", "")
            if (code <= 0 || url.isEmpty()) return null
            return Release(
                versionCode = code,
                versionName = o.optString("versionName", code.toString()),
                url = url,
                notes = o.optString("notes", "").ifEmpty { null },
                sha256 = o.optString("sha256", "").ifEmpty { null },
                sizeBytes = o.optLong("sizeBytes", 0L).takeIf { it > 0 },
                mandatory = o.optBoolean("mandatory", false),
                publishedAt = o.optString("publishedAt", "").ifEmpty { null }
            )
        }
    }
}

/** What the screen shows while a build is being fetched. */
sealed interface Download {
    data object Idle : Download
    data class Running(val readBytes: Long, val totalBytes: Long) : Download {
        val fraction: Float
            get() = if (totalBytes <= 0) 0f else (readBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }
    data class Ready(val file: File) : Download
    data class Failed(val why: String) : Download
}

class Updates(private val context: Context) {

    /** Asks the service what it should be running. Null means nothing published. */
    suspend fun latest(api: Api): Release? = api.latestRelease()

    /**
     * Fetches the APK, reporting progress, and verifies it. Everything lands
     * in the cache directory, which Android empties for us when it needs the
     * room — an abandoned download must not cost the phone a permanent
     * sixteen megabytes.
     */
    suspend fun download(
        release: Release,
        onProgress: (read: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!release.url.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                ApiError("That build is published over plain http, which is refused for an app file.")
            )
        }

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        /* One file per version, so a re-download replaces rather than piles up. */
        val file = File(dir, "nexora-console-${release.versionCode}.apk")
        val partial = File(dir, file.name + ".part")

        try {
            dir.listFiles()?.forEach { f ->
                if (f.name != file.name && f.name != partial.name) f.delete()
            }

            var url = URL(release.url)
            var redirects = 0
            var connection: HttpURLConnection

            /* GitHub release assets answer with a redirect to a CDN, and
               HttpURLConnection will not follow one that changes scheme or
               host on its own, so it is followed here — a handful of times,
               never in a circle. */
            while (true) {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = false
                    setRequestProperty("accept", "application/vnd.android.package-archive, */*")
                }
                val code = connection.responseCode
                if (code in 301..308 && code != 304) {
                    val location = connection.getHeaderField("location")
                    connection.disconnect()
                    if (location.isNullOrBlank() || ++redirects > 5) {
                        return@withContext Result.failure(ApiError("That download address keeps redirecting."))
                    }
                    url = URL(url, location)
                    continue
                }
                if (code !in 200..299) {
                    connection.disconnect()
                    return@withContext Result.failure(
                        ApiError("The build could not be fetched (HTTP $code). Check the address in the console.")
                    )
                }
                break
            }

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: (release.sizeBytes ?: -1L)
            var read = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            connection.disconnect()

            if (read <= 0) {
                partial.delete()
                return@withContext Result.failure(ApiError("The download came back empty."))
            }

            /* If a checksum was published, the bytes must match it exactly. */
            release.sha256?.let { want ->
                val got = sha256Of(partial)
                if (!got.equals(want.trim(), ignoreCase = true)) {
                    partial.delete()
                    return@withContext Result.failure(
                        ApiError("The downloaded file does not match the checksum that was published. It has not been installed.")
                    )
                }
            }

            if (file.exists()) file.delete()
            if (!partial.renameTo(file)) {
                partial.delete()
                return@withContext Result.failure(ApiError("The download could not be saved."))
            }
            Result.success(file)
        } catch (e: Exception) {
            partial.delete()
            Result.failure(ApiError("The build could not be fetched. ${e.message ?: "No connection."}"))
        }
    }

    /**
     * Hands the file to Android's own installer. From Android 8 the phone
     * asks once whether this application may install others; there is no way
     * around that and there should not be.
     */
    fun install(file: File): Result<Unit> = try {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".updates",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(ApiError("The installer could not be opened. ${e.message ?: ""}"))
    }

    /** Whether the phone will let this application install another at all. */
    fun mayInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** The settings page where that permission is granted. */
    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(android.net.Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            /* Some builds hide the per-app page; the general one will do. */
            try {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
