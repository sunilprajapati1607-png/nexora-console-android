package org.nexoraofficial.console.data

import android.content.Context
import org.nexoraofficial.console.BuildConfig

/**
 * What the phone remembers between visits: the admin key, the service
 * address, and which mode the owner last chose.
 *
 * The web console keeps the key in sessionStorage — gone when the tab closes.
 * A phone has no tabs, so the key is kept but never backed up off the device
 * (see data_extraction_rules.xml), and Sign out erases it.
 */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("nexora.console", Context.MODE_PRIVATE)

    var adminKey: String
        get() = p.getString(KEY, "") ?: ""
        set(v) = p.edit().putString(KEY, v).apply()

    var baseUrl: String
        get() = p.getString(URL, BuildConfig.API_BASE) ?: BuildConfig.API_BASE
        set(v) = p.edit().putString(URL, v.trim().trimEnd('/')).apply()

    /** null until the owner has chosen — then the phone's preference is only where we started. */
    var mode: String?
        get() = p.getString(MODE, null)
        set(v) = p.edit().putString(MODE, v).apply()

    var rememberKey: Boolean
        get() = p.getBoolean(REMEMBER, true)
        set(v) = p.edit().putBoolean(REMEMBER, v).apply()

    /* What the background watch has already told the owner about. The enquiry
       id starts at 0 and the company count at -1, because "none yet" and
       "learned nothing yet" have to be different: the first run must learn
       the current state quietly instead of announcing everything at once. */
    var lastInquiryId: Int
        get() = p.getInt(LAST_INQUIRY, 0)
        set(v) = p.edit().putInt(LAST_INQUIRY, v).apply()

    var lastCompanyCount: Int
        get() = p.getInt(LAST_COMPANIES, -1)
        set(v) = p.edit().putInt(LAST_COMPANIES, v).apply()

    /* The newest build this phone has already been told about, so a notice
       is given once per version and not every quarter of an hour. */
    var lastOfferedVersion: Int
        get() = p.getInt(LAST_OFFERED, 0)
        set(v) = p.edit().putInt(LAST_OFFERED, v).apply()

    var watching: Boolean
        get() = p.getBoolean(WATCHING, true)
        set(v) = p.edit().putBoolean(WATCHING, v).apply()

    fun signOut() = p.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "adminKey"
        const val URL = "baseUrl"
        const val MODE = "mode"
        const val REMEMBER = "rememberKey"
        const val LAST_INQUIRY = "lastInquiryId"
        const val LAST_COMPANIES = "lastCompanyCount"
        const val WATCHING = "watching"
        const val LAST_OFFERED = "lastOfferedVersion"
    }
}
