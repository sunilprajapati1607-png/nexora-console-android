package org.nexoraofficial.console

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.nexoraofficial.console.data.Audience
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Inquiry
import org.nexoraofficial.console.data.InquiryData
import org.nexoraofficial.console.data.Licence
import org.nexoraofficial.console.data.People
import org.nexoraofficial.console.data.Prefs
import org.nexoraofficial.console.data.Reach
import org.nexoraofficial.console.data.Download
import org.nexoraofficial.console.data.Release
import org.nexoraofficial.console.data.Updates
import org.nexoraofficial.console.data.ServiceSettings

/** The console's own .msg strip: one line, three readings, gone in six seconds. */
data class Msg(val text: String, val kind: Kind) {
    enum class Kind { OK, WARN, ERR }
}

/** The editable copy of the service settings, while the owner is changing them. */
data class SettingsForm(
    val trialDays: String = "7",
    val demoGraceDays: String = "0",
    val sessionMinutes: String = "30",
    val expiredMode: String = "READONLY",
    val signupsOpen: Boolean = true,
    val demoSignup: Boolean = false
) {
    companion object {
        fun of(s: ServiceSettings) = SettingsForm(
            trialDays = s.trialDays.toString(),
            demoGraceDays = s.demoGraceDays.toString(),
            sessionMinutes = s.sessionMinutes.toString(),
            expiredMode = s.expiredMode,
            signupsOpen = s.signupsOpen,
            demoSignup = s.demoSignup
        )
    }
}

/** The New enquiry form, and the one an enquiry is edited in. */
data class InquiryForm(
    val id: Int = 0,
    val name: String = "",
    val company: String = "",
    val phone: String = "",
    val email: String = "",
    val product: String = "Bag Weight & Cost Forecasting",
    val source: String = "PHONE",
    val state: String = "NEW",
    val message: String = "",
    val notes: String = "",
    val followUp: String = ""
) {
    companion object {
        fun of(i: Inquiry) = InquiryForm(
            id = i.id,
            name = i.name,
            company = i.company.orEmpty(),
            phone = i.phone.orEmpty(),
            email = i.email.orEmpty(),
            product = i.product,
            source = i.source,
            state = i.state,
            message = i.message.orEmpty(),
            notes = i.notes.orEmpty(),
            followUp = i.followUp.orEmpty()
        )
    }
}

/** The New company form. */
data class NewCompanyForm(
    val name: String = "",
    val seats: String = "1",
    val days: String = "365",
    val graceDays: String = "0",
    val gstin: String = "",
    val email: String = ""
)

class ConsoleViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    /* ---- the gate ---- */
    var key by mutableStateOf(prefs.adminKey)
    var baseUrl by mutableStateOf(prefs.baseUrl)
    var rememberKey by mutableStateOf(prefs.rememberKey)
    var signedIn by mutableStateOf(false)
        private set
    var gateError by mutableStateOf<String?>(null)
        private set

    /* ---- the page ---- */
    var data by mutableStateOf(ConsoleData())
        private set
    var busy by mutableStateOf(false)
        private set
    var msg by mutableStateOf<Msg?>(null)
        private set

    /* ---- what is open, filtered, searched ---- */
    var companyQuery by mutableStateOf("")
    var installQuery by mutableStateOf("")
    var openCompany by mutableStateOf<Int?>(null)
        private set
    var companyFilter by mutableStateOf<Int?>(null)
    var showSettings by mutableStateOf(false)
    var showNewCompany by mutableStateOf(false)

    var settingsForm by mutableStateOf(SettingsForm())
    var newCompany by mutableStateOf(NewCompanyForm())

    /* ---- the people on the open company ---- */
    var people by mutableStateOf<People?>(null)
        private set
    var peopleBusy by mutableStateOf(false)
        private set
    var peopleError by mutableStateOf<String?>(null)
        private set

    /* ---- the enquiries: leads, before they are customers ---- */
    var inquiryData by mutableStateOf(InquiryData())
        private set
    var inquiryQuery by mutableStateOf("")
    var inquiryState by mutableStateOf<String?>(null)
    var inquiryProduct by mutableStateOf<String?>(null)
    var showInquiries by mutableStateOf(true)
    var showNewInquiry by mutableStateOf(false)
    var newInquiry by mutableStateOf(InquiryForm())

    /* ---- telling every customer something at once ---- */
    var showAnnounce by mutableStateOf(false)
    var audience by mutableStateOf(Audience.ACTIVE)
    var reach by mutableStateOf(Reach.EVERYONE)
    var announceSubject by mutableStateOf("Nexora — update")
    var announceBody by mutableStateOf("")

    /* ---- updating this application from inside it ---- */
    private val updates = Updates(app)
    var release by mutableStateOf<Release?>(null)
        private set
    var download by mutableStateOf<Download>(Download.Idle)
        private set
    var checkedForUpdate by mutableStateOf(false)
        private set

    /** True when the service is offering a build newer than this one. */
    val updateAvailable: Boolean
        get() = (release?.versionCode ?: 0) > BuildConfig.VERSION_CODE

    /* ---- light and dark, remembered exactly as the console remembers it ---- */
    var dark by mutableStateOf(false)
        private set

    fun startMode(systemDark: Boolean) {
        dark = when (prefs.mode) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }
    }

    fun flipMode() {
        dark = !dark
        prefs.mode = if (dark) "dark" else "light"
    }

    private val api get() = Api(baseUrl, key.trim())

    /* ---- the companies and installations actually shown ---- */
    val companies: List<Company> get() = data.companies.filter { it.matches(companyQuery) }

    val installations: List<Licence>
        get() = data.licences.filter {
            (companyFilter == null || it.companyId == companyFilter) && it.matches(installQuery)
        }

    val customerCount get() = data.companies.count { !it.isDemo }
    val demoCount get() = data.companies.count { it.isDemo }
    val runningCount get() = data.licences.count { !it.expired && it.state != "REVOKED" }

    /** Opened once at startup when a key was remembered. */
    fun resume() {
        if (!signedIn && key.isNotBlank()) load()
    }

    fun load(onDone: (() -> Unit)? = null) {
        if (key.isBlank()) {
            gateError = "Enter the admin key to open the console."
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val fresh = api.licences()
                data = fresh
                settingsForm = SettingsForm.of(fresh.settings)
                gateError = null
                if (!signedIn) {
                    signedIn = true
                    prefs.baseUrl = baseUrl
                    prefs.rememberKey = rememberKey
                    if (rememberKey) prefs.adminKey = key.trim() else prefs.signOut()
                }
                /* A company left open keeps its people in step with the reload. */
                openCompany?.let { loadPeople(it) }
                /* The leads come with everything else. Quietly: a service that
                   has not been deployed with enquiries yet should still open. */
                loadInquiries(quiet = true)
                /* And whether a newer build of this application exists. */
                checkForUpdate()
            } catch (e: Exception) {
                val text = (e as? ApiError)?.message ?: "Something went wrong."
                if (signedIn) say(text, Msg.Kind.ERR) else gateError = text
            } finally {
                busy = false
                onDone?.invoke()
            }
        }
    }

    fun signOut() {
        prefs.signOut()
        key = ""
        signedIn = false
        data = ConsoleData()
        openCompany = null
        companyFilter = null
        people = null
        msg = null
    }

    fun say(text: String, kind: Msg.Kind) {
        val m = Msg(text, kind)
        msg = m
        viewModelScope.launch {
            delay(6_000)
            if (msg === m) msg = null
        }
    }

    fun dismissMessage() {
        msg = null
    }

    /* ---------- companies ---------- */

    fun toggleManage(id: Int) {
        openCompany = if (openCompany == id) null else id
        people = null
        peopleError = null
        openCompany?.let { loadPeople(it) }
    }

    /** Every company action goes through here, so error, warning and reload
     *  are handled in one place exactly as the web console handles them. */
    fun companyAction(body: JSONObject, okText: String? = null, then: (JSONObject) -> Unit = {}) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.company(body)
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(err, Msg.Kind.ERR)
                    return@launch
                }
                val warn = r.optString("warning")
                when {
                    warn.isNotEmpty() -> say(warn, Msg.Kind.WARN)
                    okText != null -> say(okText, Msg.Kind.OK)
                }
                then(r)
                load()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    fun act(id: Int, action: String, days: Int = 0) =
        companyAction(JSONObject().put("id", id).put("action", action).put("days", days))

    fun renameCompany(id: Int, name: String) =
        companyAction(
            JSONObject().put("id", id).put("action", "rename").put("name", name.trim()),
            okText = "Renamed."
        )

    fun setGstin(id: Int, gstin: String) =
        companyAction(
            JSONObject().put("id", id).put("action", "gstin").put("gstin", gstin.trim().uppercase()),
            okText = "GSTIN saved."
        )

    fun setNote(id: Int, notes: String) =
        companyAction(
            JSONObject().put("id", id).put("action", "note").put("notes", notes.trim()),
            okText = "Note saved."
        )

    fun setSeats(id: Int, seats: Int) =
        companyAction(JSONObject().put("id", id).put("action", "seats").put("seats", seats))

    fun setGrace(id: Int, graceDays: Int) =
        companyAction(JSONObject().put("id", id).put("action", "grace").put("graceDays", graceDays))

    fun setTxnLimit(id: Int, limit: Int) =
        companyAction(JSONObject().put("id", id).put("action", "txnlimit").put("txnLimit", limit))

    fun resetUsage(id: Int, name: String) =
        companyAction(
            JSONObject().put("id", id).put("action", "resetusage"),
            okText = "Usage reset for $name."
        )

    fun setPasscode(id: Int, loginId: String, passcode: String) =
        companyAction(
            JSONObject().put("id", id).put("action", "passcode")
                .put("loginId", loginId).put("passcode", passcode),
            okText = "Done."
        )

    fun setAdministrator(id: Int, name: String, pin: String, email: String = "") =
        companyAction(
            JSONObject().put("id", id).put("action", "adminuser")
                .put("name", name).put("pin", pin).put("email", email.trim()),
            okText = "Done."
        )

    fun deleteCompany(id: Int, typedName: String) {
        companyAction(
            JSONObject().put("id", id).put("action", "delete").put("confirmName", typedName)
        ) { r ->
            val x = r.optJSONObject("removed")
            openCompany = null
            companyFilter = null
            say(
                "Deleted ${r.optString("name")} — " +
                    "${x?.optInt("installations") ?: 0} installation(s), " +
                    "${x?.optInt("users") ?: 0} user(s), " +
                    "${x?.optInt("records") ?: 0} synced record(s), " +
                    "${x?.optInt("inkModels") ?: 0} ink model(s).",
                Msg.Kind.OK
            )
        }
    }

    fun createCompany() {
        val f = newCompany
        if (f.name.isBlank()) {
            say("A company name is required.", Msg.Kind.ERR)
            return
        }
        companyAction(
            JSONObject()
                .put("action", "create")
                .put("name", f.name.trim())
                .put("seats", f.seats.toIntOrNull() ?: 1)
                .put("days", f.days.toIntOrNull() ?: 365)
                .put("graceDays", f.graceDays.toIntOrNull() ?: 0)
                .put("gstin", f.gstin.trim().uppercase())
                .put("email", f.email.trim())
        ) { r ->
            val co = r.optJSONObject("company")
            showNewCompany = false
            newCompany = NewCompanyForm()
            if (co != null) {
                say(
                    "${co.optString("name")} created. Licence key ${co.optString("licence_key")} — " +
                        "give this to the customer; every machine types it at activation.",
                    Msg.Kind.OK
                )
            }
        }
    }

    /* ---------- the people on a company ---------- */

    fun loadPeople(companyId: Int) {
        viewModelScope.launch {
            peopleBusy = true
            peopleError = null
            try {
                people = api.people(companyId)
            } catch (e: Exception) {
                people = null
                peopleError = (e as? ApiError)?.message ?: "Something went wrong."
            } finally {
                peopleBusy = false
            }
        }
    }

    private fun personAction(companyId: Int, body: JSONObject, fallback: String, reload: Boolean) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.company(body)
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(err, Msg.Kind.ERR)
                    return@launch
                }
                say(r.optString("warning").ifEmpty { fallback }, Msg.Kind.OK)
                loadPeople(companyId)
                if (reload) load()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    fun addPerson(companyId: Int, name: String, pin: String, email: String, admin: Boolean) =
        personAction(
            companyId,
            JSONObject().put("id", companyId).put("action", "useradd")
                .put("name", name.trim()).put("pin", pin).put("email", email.trim())
                .put("role", if (admin) "ADMIN" else "USER"),
            "Added.", reload = true
        )

    /** 4.42.0 — a person's own address, set or taken off. Blank clears it. */
    fun setPersonEmail(companyId: Int, userId: Int, email: String) = personAction(
        companyId,
        JSONObject().put("id", companyId).put("action", "useremail")
            .put("userId", userId).put("email", email.trim()),
        "Done.", reload = true
    )

    fun setPersonRole(companyId: Int, userId: Int, role: String) = personAction(
        companyId,
        JSONObject().put("id", companyId).put("action", "userrole")
            .put("userId", userId).put("role", role),
        "Done.", reload = false
    )

    fun setPersonPin(companyId: Int, userId: Int, pin: String) = personAction(
        companyId,
        JSONObject().put("id", companyId).put("action", "userpin")
            .put("userId", userId).put("pin", pin),
        "Done.", reload = false
    )

    /** 4.43.0 — release a name that is bound to a machine nobody can reach. */
    fun signOutPerson(companyId: Int, userId: Int) = personAction(
        companyId,
        JSONObject().put("id", companyId).put("action", "usersignout").put("userId", userId),
        "Signed out.", reload = true
    )

    fun removePerson(companyId: Int, userId: Int) = personAction(
        companyId,
        JSONObject().put("id", companyId).put("action", "userdel").put("userId", userId),
        "Removed.", reload = true
    )

    /* ---------- GST ---------- */

    fun gstVerify(id: Int) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.gst(JSONObject().put("action", "gstverify").put("id", id))
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(r.optString("message").ifEmpty { err }, Msg.Kind.ERR)
                    return@launch
                }
                val g = r.optJSONObject("gst")
                val status = g?.optString("status").orEmpty()
                val why = g?.optString("reason").orEmpty().ifEmpty { g?.optString("legalName").orEmpty() }
                say(
                    "GST ${status.lowercase()}" + if (why.isNotEmpty()) " — $why" else "",
                    when (status) {
                        "VERIFIED" -> Msg.Kind.OK
                        "FAILED" -> Msg.Kind.ERR
                        else -> Msg.Kind.WARN
                    }
                )
                load()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    fun gstMark(id: Int, status: String, note: String) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.gst(
                    JSONObject().put("action", "gstmark").put("id", id)
                        .put("status", status).put("note", note)
                )
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(r.optString("message").ifEmpty { err }, Msg.Kind.ERR)
                } else {
                    load()
                }
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    /* ---------- installations ---------- */

    fun licenceAction(deviceId: String, action: String, okText: String? = null) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.licence(
                    JSONObject().put("deviceId", deviceId).put("action", action).put("days", 0)
                )
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(err, Msg.Kind.ERR)
                    return@launch
                }
                val warn = r.optString("warning")
                when {
                    warn.isNotEmpty() -> say(warn, Msg.Kind.WARN)
                    okText != null -> say(
                        if (action == "delete" && r.optBoolean("orphan"))
                            "Installation deleted — it belonged to no company."
                        else okText,
                        Msg.Kind.OK
                    )
                }
                load()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    /* ---------- enquiries ---------- */

    /** What the screen actually lists, after the search and the two filters. */
    val inquiries: List<Inquiry>
        get() = inquiryData.inquiries.filter {
            it.matches(inquiryQuery) &&
                (inquiryState == null || it.state == inquiryState) &&
                (inquiryProduct == null || it.product == inquiryProduct)
        }

    val openInquiries get() = inquiryData.inquiries.count { it.isOpen }
    val newInquiryCount get() = inquiryData.inquiries.count { it.state == "NEW" }
    val wonInquiries get() = inquiryData.inquiries.count { it.state == "WON" }

    /** How many enquiries sit in each state, in the order the service lists them. */
    fun byState(): List<Pair<String, Int>> =
        inquiryData.states.map { s -> s to inquiryData.inquiries.count { it.state == s } }

    /** Which software people are actually asking about, commonest first. */
    fun byProduct(): List<Pair<String, Int>> =
        inquiryData.inquiries.groupBy { it.product }
            .map { (p, list) -> p to list.size }
            .sortedByDescending { it.second }

    /** Those whose follow-up date has arrived or passed, and are still open. */
    fun dueFollowUps(): List<Inquiry> {
        val today = java.time.LocalDate.now().toString()
        return inquiryData.inquiries.filter {
            it.isOpen && !it.followUp.isNullOrBlank() && it.followUp.take(10) <= today
        }
    }

    fun loadInquiries(quiet: Boolean = false) {
        if (key.isBlank()) return
        viewModelScope.launch {
            try {
                inquiryData = api.inquiries()
            } catch (e: Exception) {
                /* An older service has no such route; that is not worth a red
                   strip across the page every time the console loads. */
                if (!quiet) {
                    say((e as? ApiError)?.message ?: "Could not read the enquiries.", Msg.Kind.ERR)
                }
            }
        }
    }

    private fun inquiryCall(body: JSONObject, okText: String?, then: () -> Unit = {}) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.inquiry(body)
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(err, Msg.Kind.ERR)
                    return@launch
                }
                val warn = r.optString("warning")
                when {
                    warn.isNotEmpty() -> say(warn, Msg.Kind.OK)
                    okText != null -> say(okText, Msg.Kind.OK)
                }
                then()
                loadInquiries()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    fun saveInquiry() {
        val f = newInquiry
        if (f.name.isBlank()) {
            say("A name is required.", Msg.Kind.ERR)
            return
        }
        val body = JSONObject()
            .put("action", if (f.id > 0) "update" else "create")
            .put("name", f.name.trim())
            .put("company", f.company.trim())
            .put("phone", f.phone.trim())
            .put("email", f.email.trim())
            .put("product", f.product)
            .put("source", f.source)
            .put("state", f.state)
            .put("message", f.message.trim())
            .put("notes", f.notes.trim())
            .put("followUp", f.followUp.trim())
        if (f.id > 0) body.put("id", f.id)
        inquiryCall(body, if (f.id > 0) "Saved." else "Enquiry added.") {
            showNewInquiry = false
            newInquiry = InquiryForm()
        }
    }

    fun setInquiryState(id: Int, state: String) =
        inquiryCall(JSONObject().put("action", "state").put("id", id).put("state", state), null)

    fun deleteInquiry(id: Int, name: String) =
        inquiryCall(
            JSONObject().put("action", "delete").put("id", id),
            "$name removed from the enquiries."
        )

    fun editInquiry(i: Inquiry) {
        newInquiry = InquiryForm.of(i)
        showNewInquiry = true
    }

    /* ---------- updating this application ---------- */

    /** Quiet on the way in — a service without releases must not shout. */
    fun checkForUpdate(loud: Boolean = false) {
        if (key.isBlank()) return
        viewModelScope.launch {
            try {
                release = updates.latest(api)
                checkedForUpdate = true
                if (loud) {
                    say(
                        if (updateAvailable) "Version ${release?.versionName} is ready to install."
                        else "This is the newest build there is.",
                        if (updateAvailable) Msg.Kind.OK else Msg.Kind.WARN
                    )
                }
            } catch (e: Exception) {
                checkedForUpdate = true
                if (loud) say((e as? ApiError)?.message ?: "Could not ask about updates.", Msg.Kind.ERR)
            }
        }
    }

    fun downloadUpdate() {
        val r = release ?: return
        if (download is Download.Running) return
        viewModelScope.launch {
            download = Download.Running(0, r.sizeBytes ?: -1L)
            val outcome = updates.download(r) { read, total ->
                download = Download.Running(read, total)
            }
            download = outcome.fold(
                onSuccess = { Download.Ready(it) },
                onFailure = { Download.Failed(it.message ?: "The download failed.") }
            )
            (download as? Download.Failed)?.let { say(it.why, Msg.Kind.ERR) }
        }
    }

    fun installUpdate() {
        val ready = download as? Download.Ready ?: return
        if (!updates.mayInstall()) {
            say(
                "This phone has not been told that the console may install applications. " +
                    "Turn it on, then press Install again.",
                Msg.Kind.WARN
            )
            updates.openInstallPermission()
            return
        }
        updates.install(ready.file).onFailure { say(it.message ?: "Could not open the installer.", Msg.Kind.ERR) }
    }

    fun clearDownload() {
        download = Download.Idle
    }

    /* ---------- service settings ---------- */

    fun saveSettings() {
        val f = settingsForm
        viewModelScope.launch {
            busy = true
            try {
                api.settings(
                    JSONObject()
                        .put("trialDays", f.trialDays.toIntOrNull() ?: 7)
                        .put("demoGraceDays", f.demoGraceDays.toIntOrNull() ?: 0)
                        .put("sessionMinutes", f.sessionMinutes.toIntOrNull() ?: 30)
                        .put("expiredMode", f.expiredMode)
                        .put("signupsOpen", f.signupsOpen)
                        .put("demoSignup", f.demoSignup)
                )
                say("Settings saved.", Msg.Kind.OK)
                load()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }
}
