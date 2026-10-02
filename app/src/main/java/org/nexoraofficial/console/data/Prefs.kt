package org.nexoraofficial.console.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.nexoraofficial.console.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What the phone remembers between visits: the admin key, the service
 * address, and which mode the owner last chose.
 *
 * The web console keeps the key in sessionStorage — gone when the tab closes.
 * A phone has no tabs, so the key is kept but never backed up off the device
 * (see data_extraction_rules.xml), and Sign out erases it.
 *
 * 1.8.0 — the audit before the release: the master admin key opens every
 * company on the service, and until now it sat in this file as plain text,
 * readable by anything that could read the application's storage (a rooted
 * phone, a debug build's run-as). It is now sealed with AES-GCM under a key
 * that lives in the phone's own keystore and cannot be taken off it, so the
 * file holds only ciphertext. The plain copy an earlier build left behind is
 * sealed the first time it is read and then deleted — once, quietly; the
 * owner does not have to type the key again.
 */
class Prefs(
    context: Context,
    private val sealer: Sealer = KeystoreSealer,
    /* 4.72.0 — audit #36: a release build keeps only Nexora's own service (ServiceHost) */
    private val anyHost: Boolean = BuildConfig.DEBUG
) {
    private val p = context.getSharedPreferences("nexora.console", Context.MODE_PRIVATE)

    var adminKey: String
        get() = synchronized(LOCK) { readKey() }
        set(v) = synchronized(LOCK) { writeKey(v) }

    /* The sealed copy wins; a plain copy is an earlier build's, moved over once.
       A seal that will not open (the keystore was wiped — a factory restore, or
       the screen lock removed on some phones) reads as "no key": the gate asks
       for it again, the right answer for a key the phone can no longer vouch for. */
    private fun readKey(): String {
        p.getString(SEALED, null)?.let { sealed ->
            return try { sealer.open(sealed) } catch (_: Exception) { "" }
        }
        val plain = p.getString(KEY, null) ?: return ""
        writeKey(plain)
        return plain
    }

    /* The plain copy is removed in the same write the sealed one lands in —
       and also when sealing fails: a key that cannot be sealed is not kept at
       all (the owner types it at the gate next time), never kept in the clear. */
    private fun writeKey(v: String) {
        val e = p.edit().remove(KEY)
        if (v.isEmpty()) e.remove(SEALED)
        else try { e.putString(SEALED, sealer.seal(v)) } catch (_: Exception) { e.remove(SEALED) }
        e.commit()
    }

    /* 4.72.0 — audit #36: an address that is not Nexora's own service (one an
       earlier build let the owner type, before the gate refused it) is not
       used: it reads as the built-in service, and the admin key never goes there. */
    var baseUrl: String
        get() = ServiceHost.orDefault(p.getString(URL, null), anyHost)
        set(v) {
            val u = v.trim().trimEnd('/')
            if (ServiceHost.ok(u, anyHost)) p.edit().putString(URL, u).apply()
            else p.edit().remove(URL).apply()
        }

    /** null until the owner has chosen — then the phone's preference is only where we started. */
    var mode: String?
        get() = p.getString(MODE, null)
        set(v) = p.edit().putString(MODE, v).apply()

    var rememberKey: Boolean
        get() = p.getBoolean(REMEMBER, true)
        set(v) = p.edit().putBoolean(REMEMBER, v).apply()

    /* 4.72.0 — audit #43: what the background watch has already told the
       owner about is now one moment — the service's own clock ("at") on the
       last summary it answered; the next look asks what arrived since then.
       null means "learned nothing yet": the first look learns the moment
       quietly instead of announcing a whole day at once. */
    var lastSummaryAt: String?
        get() = p.getString(LAST_SUMMARY, null)
        set(v) = p.edit().putString(LAST_SUMMARY, v).apply()

    /* 4.72.0 (review) — THE OLD WAY'S MARKS, kept only while the service
       gives no summary (not updated yet) and the watch looks the 1.7.1 way
       (Watch.kt, OldWay): the highest enquiry and report ids already told
       (1.7.1's own keys) and the highest company id seen. 0 = not learned
       yet. Forgotten once the summary answers again and has taken over. */
    var lastInquiryId: Int
        get() = p.getInt(LAST_INQUIRY, 0)
        set(v) = p.edit().putInt(LAST_INQUIRY, v).apply()

    var lastFeedbackId: Int
        get() = p.getInt(LAST_FEEDBACK, 0)
        set(v) = p.edit().putInt(LAST_FEEDBACK, v).apply()

    var lastCompanyId: Int
        get() = p.getInt(LAST_COMPANY, 0)
        set(v) = p.edit().putInt(LAST_COMPANY, v).apply()

    /* 1.7.1's company count is a different measure (a deletion moved it, and
       an error answer read as none at all), so it goes with the others. */
    fun forgetOldWay() {
        p.edit().remove(LAST_INQUIRY).remove(LAST_FEEDBACK).remove(LAST_COMPANY).remove(LAST_COMPANIES_171).apply()
    }

    /* When the watch last asked whether a newer console was published (ms). */
    var lastReleaseCheck: Long
        get() = p.getLong(LAST_RELEASE_CHECK, 0L)
        set(v) = p.edit().putLong(LAST_RELEASE_CHECK, v).apply()

    /* The newest build this phone has already been told about, so a notice
       is given once per version and not every quarter of an hour. */
    var lastOfferedVersion: Int
        get() = p.getInt(LAST_OFFERED, 0)
        set(v) = p.edit().putInt(LAST_OFFERED, v).apply()

    var watching: Boolean
        get() = p.getBoolean(WATCHING, true)
        set(v) = p.edit().putBoolean(WATCHING, v).apply()

    /* 4.72.0 (review) — the service refused the kept admin key (it was
       changed on the service). The watch asks nothing more with it: every
       refused try counts towards the service's lock on this address (five
       = fifteen minutes), which would also shut out the web console on the
       same Wi-Fi. Cleared by the next good sign-in (ConsoleViewModel.load). */
    var keyRejected: Boolean
        get() = p.getBoolean(KEY_REJECTED, false)
        set(v) = p.edit().putBoolean(KEY_REJECTED, v).apply()

    /* 4.72.0 (review) — the owner has been told once that the service gives
       the watch no summary (not updated yet, or a different form); cleared
       as soon as one comes, so a later break is told again. */
    var toldNoSummary: Boolean
        get() = p.getBoolean(TOLD_NO_SUMMARY, false)
        set(v) = p.edit().putBoolean(TOLD_NO_SUMMARY, v).apply()

    fun signOut() = synchronized(LOCK) { p.edit().remove(KEY).remove(SEALED).remove(KEY_REJECTED).commit(); Unit }

    private companion object {
        /* One lock for the whole process: the screen and the quarter-hourly
           watch both read the key, and two first reads at once must not both
           migrate it (or both make a keystore key, the second orphaning the first). */
        val LOCK = Any()
        /** The plain key earlier builds wrote — read once, sealed, deleted. */
        const val KEY = "adminKey"
        const val SEALED = "adminKeySealed"
        const val URL = "baseUrl"
        const val MODE = "mode"
        const val REMEMBER = "rememberKey"
        const val LAST_SUMMARY = "lastSummaryAt"
        const val LAST_RELEASE_CHECK = "lastReleaseCheck"
        const val WATCHING = "watching"
        const val LAST_OFFERED = "lastOfferedVersion"
        const val KEY_REJECTED = "watchKeyRejected"
        const val TOLD_NO_SUMMARY = "watchToldNoSummary"
        const val LAST_INQUIRY = "lastInquiryId"
        const val LAST_FEEDBACK = "lastFeedbackId"
        const val LAST_COMPANY = "watchLastCompanyId"
        const val LAST_COMPANIES_171 = "lastCompanyCount"
    }
}

/** What seals the admin key: the phone's keystore in the application, a stand-in in the tests. */
interface Sealer {
    fun seal(plain: String): String
    fun open(sealed: String): String
}

/**
 * AES-256-GCM under a key made inside the Android keystore. The key never
 * exists outside it: this process may ask the keystore to encrypt and
 * decrypt, but cannot read the key itself, and neither can a copy of the
 * application's files.
 *
 * The keystore key does not demand a fingerprint of its own
 * (setUserAuthenticationRequired): the quarter-hourly watch reads the admin
 * key with the phone in a pocket. The person is checked by the lock in front
 * of the whole application instead (AppLock).
 *
 * Stored as "iv:ciphertext", both Base64. GCM's tag rides on the ciphertext,
 * so a changed byte fails to open rather than opening to a wrong key.
 */
object KeystoreSealer : Sealer {
    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "nexora.console.adminKey"
    private const val CIPHER = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    override fun seal(plain: String): String {
        val c = Cipher.getInstance(CIPHER)
        c.init(Cipher.ENCRYPT_MODE, key())        // the keystore chooses a fresh IV every time
        val body = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(body, Base64.NO_WRAP)
    }

    override fun open(sealed: String): String {
        val parts = sealed.split(":", limit = 2)
        require(parts.size == 2) { "not a sealed value" }
        val c = Cipher.getInstance(CIPHER)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
}
