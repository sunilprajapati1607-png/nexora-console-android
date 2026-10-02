package org.nexoraofficial.console.data

import android.content.Intent
import android.net.Uri
import org.nexoraofficial.console.BuildConfig
import java.net.URI

/* ======================================================================
   1.8.0 — THE AUDIT, SECOND PART (Nexora 4.72.0, 2026-10-02).
   Where the admin key may be sent, and which e-mail addresses a mail may
   be started to.
   ====================================================================== */

/**
 * WHERE THE ADMIN KEY MAY GO (audit #36).
 *
 * The admin key opens every company on the service. Until now the gate's
 * "Service…" field took any https address and the key went there with the
 * first request — so anybody who talked the owner into typing another
 * address would have collected it. A release build now talks only to
 * Nexora's own service: the address it was built with
 * (BuildConfig.API_BASE, nexora-api-55jv.onrender.com) — https only, no user
 * name in the address.
 *
 * 4.72.0, after review — that one host and nothing else. nexoraofficial.org
 * (and www) is the website, a static site on Vercel that never serves the
 * admin API, so the key has no business there; were the service ever given
 * a name of its own, API_BASE changes with it. And Api never follows a
 * redirect, so the key cannot be carried on from an allowed host either.
 *
 * A debug build (the one tried on this computer, never published) still
 * takes a staging copy: any https address, and plain http to this computer
 * itself (127.0.0.1, localhost, the emulator's 10.0.2.2) for the tests.
 */
object ServiceHost {

    /** The one host the build was made for. */
    val defaultHost: String = runCatching { URI(BuildConfig.API_BASE).host.lowercase() }
        .getOrDefault("nexora-api-55jv.onrender.com")

    private val LOOPBACK = setOf("127.0.0.1", "localhost", "10.0.2.2", "[::1]", "::1")

    /** null when the admin key may be sent to [url]; else why not, in the owner's words. */
    fun problem(url: String?, anyHost: Boolean = BuildConfig.DEBUG): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return "Type the service address."
        val uri = runCatching { URI(u) }.getOrNull() ?: return "That is not a web address."
        val scheme = uri.scheme?.lowercase() ?: return "That is not a web address."
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return "That is not a web address."
        if (uri.userInfo != null) return "That address is not Nexora's service."
        if (anyHost) {
            if (scheme == "https") return null
            if (scheme == "http" && host in LOOPBACK) return null
            return "Only a secure address (https://) is accepted."
        }
        if (scheme != "https") return "Only a secure address (https://) is accepted."
        return if (ours(host)) null
        else "This console talks only to Nexora's own service ($defaultHost)."
    }

    fun ok(url: String?, anyHost: Boolean = BuildConfig.DEBUG): Boolean = problem(url, anyHost) == null

    /** A saved address that may no longer be used reads as the built-in service. */
    fun orDefault(url: String?, anyHost: Boolean = BuildConfig.DEBUG): String =
        if (url != null && ok(url, anyHost)) url else BuildConfig.API_BASE

    private fun ours(host: String): Boolean = host == defaultHost
}

/**
 * ONE PLAIN E-MAIL ADDRESS, OR NO MAIL AT ALL (audit #41).
 *
 * The e-mail on an enquiry or a feedback report is typed by whoever filled
 * in the website's form or the application's Nexora Contact. Built into a
 * mailto: link as it stood, "a@b.com?bcc=spy@x.com" put a hidden BCC (or a
 * ready-written body) into the owner's reply, and a line break or a second
 * address did much the same. So the Email button starts a mail only to an
 * address that is exactly one plain address — no line breaks, spaces,
 * commas, semicolons, ?, &, % or angle brackets — and says so otherwise.
 */
object MailAddress {

    private val PLAIN = Regex(
        "^[A-Za-z0-9._+'-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$"
    )

    const val REFUSED =
        "That e-mail is not one plain address (it has a line break, a space or more than one address in it), " +
            "so no mail was started. Check it with them by phone."

    /** The address itself when [raw] is exactly one plain address; null otherwise. */
    fun plain(raw: String?): String? {
        if (raw == null) return null
        if (raw.any { it == '\r' || it == '\n' }) return null
        val a = raw.trim()
        if (a.isEmpty() || a.length > 254) return null
        if (a.count { it == '@' } != 1) return null
        if (a.startsWith('.') || a.contains("..") || a.substringBefore('@').endsWith('.')) return null
        return if (PLAIN.matches(a)) a else null
    }

    /**
     * The mail app, opened on a new mail to [raw] with [subject] — or null when
     * [raw] is not one plain address, and then nothing is started. The address
     * goes in as the link's own part, encoded, never pasted into a link string.
     */
    fun compose(raw: String?, subject: String): Intent? {
        val to = plain(raw) ?: return null
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", to, null))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
    }
}
