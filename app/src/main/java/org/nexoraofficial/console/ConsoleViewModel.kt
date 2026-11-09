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
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.nexoraofficial.console.data.Audience
import org.nexoraofficial.console.data.Broadcast
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.DeletedCompany
import org.nexoraofficial.console.data.Inquiry
import org.nexoraofficial.console.data.InquiryData
import org.nexoraofficial.console.data.Feedback
import org.nexoraofficial.console.data.FeedbackData
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.nexoraofficial.console.data.Licence
import org.nexoraofficial.console.data.People
import org.nexoraofficial.console.data.Prefs
import org.nexoraofficial.console.data.Reach
import org.nexoraofficial.console.data.Download
import org.nexoraofficial.console.data.Release
import org.nexoraofficial.console.data.MailAddress
import org.nexoraofficial.console.data.ServiceHost
import org.nexoraofficial.console.data.Updates
import org.nexoraofficial.console.data.ServiceSettings
import org.nexoraofficial.console.data.CompanyEntry
import org.nexoraofficial.console.data.CompanyList
import org.nexoraofficial.console.data.FabricCompany
import org.nexoraofficial.console.data.FabricCounts
import org.nexoraofficial.console.data.FabricDetail
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.RulesForm
import org.nexoraofficial.console.data.SoftwareRules
import org.nexoraofficial.console.data.SoftwareRulesData
import org.nexoraofficial.console.data.SoftwareCounts
import org.nexoraofficial.console.data.SoftwareFilter
import org.nexoraofficial.console.data.CustQuick
import org.nexoraofficial.console.data.Customer
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.FabricEdit
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.NewCustomerForm
import org.nexoraofficial.console.data.PayFilter
import org.nexoraofficial.console.data.PayQuick
import org.nexoraofficial.console.data.Payment
import org.nexoraofficial.console.data.PaymentForm
import org.nexoraofficial.console.data.PaymentsData
import org.nexoraofficial.console.data.Plan
import org.nexoraofficial.console.data.PlanForm
import org.nexoraofficial.console.data.Plans
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.Requests
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.SwQuick
import org.nexoraofficial.console.data.ValQuick
import org.nexoraofficial.console.data.Validity
import org.nexoraofficial.console.data.ValidityRow
import org.nexoraofficial.console.data.WeightEdit
import org.nexoraofficial.console.data.Billing
import org.nexoraofficial.console.data.InvFilter
import org.nexoraofficial.console.data.Invoice
import org.nexoraofficial.console.data.InvoiceDraft
import org.nexoraofficial.console.data.InvoiceSettings
import org.nexoraofficial.console.data.InvoiceSettingsData
import org.nexoraofficial.console.data.PayQuote
import org.nexoraofficial.console.data.Refused
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.time.LocalDate

/** The console's own .msg strip: one line, three readings, gone in six seconds. */
data class Msg(val text: String, val kind: Kind) {
    enum class Kind { OK, WARN, ERR }
}

/**
 * 4.72.0 — which companies the Companies screen lists: every live one, only
 * the licences ending soon (the dashboard's card, audit #90), or the ones
 * Delete has archived, which can be restored for 30 days (audit #40).
 */
enum class CompanyView { ALL, ENDING, DELETED }

/**
 * 1.9.0 — where a read that may take a while has got to: not asked yet,
 * on its way, in, or failed (Fabric Stock's companies — its service sleeps).
 */
enum class Load { IDLE, LOADING, READY, FAILED }

/** 2.0.0 — one software's own list (By software): its quick view, its tab (customers, plans, payments) and its search. */
data class SwView(val quick: SwQuick = SwQuick.ALL, val tab: String = "customers", val query: String = "")

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

class ConsoleViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    /* 1.8.0 — the phone's own lock in front of everything (AppLock, MainActivity) */
    val lock = AppLock()

    /* ---- the gate ---- */
    var key by mutableStateOf(prefs.adminKey)
    var baseUrl by mutableStateOf(prefs.baseUrl)
    var rememberKey by mutableStateOf(prefs.rememberKey)
    /* 1.6.0 — internal (not private) so the screenshot suite can draw a signed-in console from sample companies */
    var signedIn by mutableStateOf(false)
        internal set
    var gateError by mutableStateOf<String?>(null)
        private set

    /* ---- the page ---- */
    var data by mutableStateOf(ConsoleData())
        internal set
    var busy by mutableStateOf(false)
        private set
    var msg by mutableStateOf<Msg?>(null)
        private set
    /* 1.9.0 — the last thing said, kept after its strip has cleared itself: the tests read it (under
       Robolectric the clock can leap past the strip's six seconds between two looks) */
    internal var lastSaid: Msg? = null
        private set

    /* ---- what is open, filtered, searched ---- */
    var companyQuery by mutableStateOf("")
    var installQuery by mutableStateOf("")
    var openCompany by mutableStateOf<Int?>(null)
        private set
    var companyFilter by mutableStateOf<Int?>(null)
    var showSettings by mutableStateOf(false)

    var settingsForm by mutableStateOf(SettingsForm())
    /* 1.5.0 — the messages sent to every room. (2.0.0 — the Standard / Pro matrix edited here is gone:
       plans are made per software under Software & plans.) */
    var broadcasts by mutableStateOf<List<Broadcast>>(emptyList())
    var broadcastText by mutableStateOf("")
    var broadcastVersion by mutableStateOf("")
    /* 2.0.0 — New customer: any software, each its own licence */
    var newCustomer by mutableStateOf(NewCustomerForm())

    /* ---- the people on the open company ---- */
    var people by mutableStateOf<People?>(null)
        internal set   // 2.0.0 — internal so the screenshot suite can show made-up people
    var peopleBusy by mutableStateOf(false)
        private set
    var peopleError by mutableStateOf<String?>(null)
        internal set

    /* ---- the enquiries: leads, before they are customers ---- */
    /* 4.72.0 — internal (not private) so the tests can draw enquiries from made-up ones, as `data` */
    var inquiryData by mutableStateOf(InquiryData())
        internal set
    var inquiryQuery by mutableStateOf("")
    var inquiryState by mutableStateOf<String?>(null)
    var inquiryProduct by mutableStateOf<String?>(null)
    var showInquiries by mutableStateOf(true)
    var showNewInquiry by mutableStateOf(false)
    var newInquiry by mutableStateOf(InquiryForm())

    /* ---- 1.4.0: feedback and problem reports from inside the application ---- */
    var feedbackData by mutableStateOf(FeedbackData())
        private set
    var feedbackQuery by mutableStateOf("")
    var feedbackKind by mutableStateOf<String?>(null)
    var feedbackState by mutableStateOf<String?>(null)
    /** The pictures already fetched this session; null means fetched and unreadable. */
    val shots = mutableStateMapOf<Int, ImageBitmap?>()
    var shotBusy by mutableStateOf<Int?>(null)
        private set

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
        internal set

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
    /* 4.72.0 — audit #90: the dashboard's "Licences ending soon" opens the
       list on only those; Show all puts it back.
       4.72.0 — audit #40: or the Deleted list. One state, so the list is
       never "ending soon" and "deleted" at once. */
    var companyView by mutableStateOf(CompanyView.ALL)

    var companyEnding: Boolean
        get() = companyView == CompanyView.ENDING
        set(on) { companyView = if (on) CompanyView.ENDING else CompanyView.ALL }

    val companies: List<Company>
        get() = data.companies.filter { it.matches(companyQuery) && (!companyEnding || it.endingSoon) }

    /** 4.72.0 — audit #40: what Delete has archived, newest first, after the search. */
    val deletedCompanies: List<DeletedCompany>
        get() = data.archived.filter { it.matches(companyQuery) }

    /** 4.72.0 — paying plants whose licence ends within 15 days, soonest first. */
    val endingSoon: List<Company>
        get() = data.companies.filter { it.endingSoon }.sortedWith(compareBy({ it.daysLeft }, { it.name }))

    val installations: List<Licence>
        get() = data.licences.filter {
            (companyFilter == null || it.companyId == companyFilter) && it.matches(installQuery)
        }

    val customerCount get() = data.companies.count { !it.isDemo }
    val demoCount get() = data.companies.count { it.isDemo }
    val runningCount get() = data.licences.count { !it.expired && it.state != "REVOKED" }

    /* ---- 1.9.0: every Nexora software in the one console ----

       "nexora console page single rahese badhi service tya thij update chalu
       bandh thase" (owner, 2026-10-07). Weight Calc is `data`, as it always
       was; the other software come from GET /admin/api/products, read apart
       and never in the way: Fabric Stock's free service sleeps, and its
       companies can take most of a minute to arrive. Each software keeps its
       own licence — nothing here merges two. */

    /* internal (not private) so the tests can draw the console from made-up software, as `data` */
    var products by mutableStateOf<ProductsData?>(null)
        internal set
    var productsLoad by mutableStateOf(Load.IDLE)
        internal set
    var productsError by mutableStateOf<String?>(null)
        internal set
    private var productsJob: Job? = null

    /** Which software the Companies list shows. */
    var software by mutableStateOf(SoftwareFilter.ALL)

    /* The Fabric Stock company open on screen, with its people and its computers and phones. */
    var fabricDetail by mutableStateOf<FabricDetail?>(null)
        internal set
    var fabricDetailBusy by mutableStateOf(false)
        private set
    var fabricDetailError by mutableStateOf<String?>(null)
        internal set
    /** The Fabric Stock company whose screen (or tab) is showing — read again after a refresh or a change. */
    var fabricOpen by mutableStateOf<Int?>(null)
    /** 2.1.0 — the software that company is on (fabric, erp, consult). */
    var fabricOpenSw by mutableStateOf(Software.FABRIC)
    private var fabricDetailJob: Job? = null

    /**
     * Why Fabric Stock cannot be shown, in words for the owner — null while
     * it can (or before it has been asked). Either the list of software would
     * not come at all, or it came and Fabric Stock's own service was not
     * reachable (asleep, or refusing the key).
     */
    val fabricProblem: String? get() = remoteProblem(Software.FABRIC)

    /** Fabric Stock's live companies — none while it is not connected. */
    val fabricCompanies: List<FabricCompany> get() = remoteCompanies(Software.FABRIC)

    /** Whether Fabric Stock's companies are in hand (so "none linked" really means none). */
    val fabricReady: Boolean get() = remoteReady(Software.FABRIC)

    /**
     * 2.1.0 — the same for any software the console service reaches (Fabric Stock, MSME ERP, Nexora Consult):
     * why it cannot be shown (the service's own words — "add NEXORA_CONSULT_ADMIN_KEY on nexora-api" — never
     * "0 customers"), null while it can or before it has been asked.
     */
    fun remoteProblem(sw: String): String? {
        if (productsLoad == Load.FAILED) return productsError ?: "The software list could not be read."
        val p = products ?: return null
        val r = p.of(sw) ?: return "This service does not list ${Software.name(sw)}."
        return if (r.ok) null else r.problem
    }

    /** 2.1.0 — whether the console service could not read [sw] because it has no key for it (waiting will not help). */
    fun remoteKeyProblem(sw: String): Boolean = products?.of(sw)?.let { !it.ok && it.keyProblem } == true

    /** 2.1.0 — [sw]'s live companies, each marked with its software — none while it is not connected. */
    fun remoteCompanies(sw: String): List<FabricCompany> =
        if (remoteProblem(sw) != null) emptyList()
        else products?.of(sw)?.companies.orEmpty().filter { it.deletedAt == null }.map { if (it.sw == sw) it else it.copy(sw = sw) }

    /** 2.1.0 — whether [sw]'s companies are in hand. */
    fun remoteReady(sw: String): Boolean = products?.of(sw) != null && remoteProblem(sw) == null

    /** 2.1.0 — one company on [sw] by its id. */
    fun remoteById(sw: String, id: Int): FabricCompany? = remoteCompanies(sw).find { it.id == id }

    /** 2.1.0 — the companies on [sw] with no Sales & Costing company beside them — what may be linked to one. */
    fun remoteOnly(sw: String): List<FabricCompany> = CompanyList.fabricOnly(data.companies, remoteCompanies(sw))

    fun fabricById(id: Int): FabricCompany? = fabricCompanies.find { it.id == id }

    /** The Fabric Stock company that belongs to this Weight Calc company, if any. */
    fun fabricFor(companyId: Int): FabricCompany? = CompanyList.linked(data.companies, fabricCompanies)[companyId]

    /** The Fabric Stock companies on their own — what may be linked to a Weight Calc company. */
    val fabricOnly: List<FabricCompany> get() = CompanyList.fabricOnly(data.companies, fabricCompanies)

    /** The Companies list across the software, after the chip, the search and the ending-soon view. */
    val companyEntries: List<CompanyEntry>
        get() = CompanyList.entries(data.companies, fabricCompanies, software, companyQuery, companyEnding)

    val softwareCounts: SoftwareCounts get() = CompanyList.counts(data.companies, fabricCompanies)
    val fabricCounts: FabricCounts get() = FabricCounts.of(fabricCompanies)

    /* ---- 2.0.0: by customer, by software, the plans and the payments ----

       "console ne software jevu banavanu che row type details click and open
       window" (owner, 2026-10-08): lists of rows, a row opens its record in a
       screen of its own, read-only first, Edit to change it. The plans and the
       payments are read like the other software — apart, quietly, after the
       licences — and never by the quarter-hourly watch (audit 43). */

    /* internal (not private) so the tests can draw the screens from made-up plans and payments, as `data` */
    var plans by mutableStateOf<PlansData?>(null)
        internal set
    var plansLoad by mutableStateOf(Load.IDLE)
        internal set
    var plansError by mutableStateOf<String?>(null)
        internal set
    var payments by mutableStateOf<PaymentsData?>(null)
        internal set
    var paymentsLoad by mutableStateOf(Load.IDLE)
        internal set
    var paymentsError by mutableStateOf<String?>(null)
        internal set
    private var plansJob: Job? = null
    private var paymentsJob: Job? = null
    private var plansGen = 0
    private var paymentsGen = 0

    /** Today, as the payment quick views and the Record payment form read it (the tests may fix it). */
    internal var today: () -> LocalDate = { LocalDate.now() }

    /** Every customer once, with every software it uses. */
    val customers: List<Customer> get() = Customers.of(data.companies, Software.REMOTE_IDS.associateWith { remoteCompanies(it) })

    /* ---- 2.1.0: each software's own demo rules, kept on its own server ---- */

    /* internal (not private) so the tests can draw them from made-up rules, as `data` */
    var rules by mutableStateOf<SoftwareRulesData?>(null)
        internal set
    var rulesLoad by mutableStateOf(Load.IDLE)
        internal set
    var rulesError by mutableStateOf<String?>(null)
        internal set
    private var rulesJob: Job? = null
    private var rulesGen = 0

    /** [sw]'s demo rules, or null while they are on their way (or the list could not be read). */
    fun rulesOf(sw: String): SoftwareRules? = rules?.of(sw)

    /** How many days a new demo on [sw] runs: its own rule when read, else 7. */
    fun demoDays(sw: String): Int = rulesOf(sw)?.takeIf { it.ok }?.trialDays?.takeIf { it > 0 } ?: 7

    fun customer(key: String): Customer? = Customers.find(customers, key)

    /** The Customers list's quick view (Deleted is companyView). */
    var customerQuick by mutableStateOf(CustQuick.ALL)

    /** What the Customers list shows, after the quick view, the search and the dashboard's "ending soon". */
    val customerRows: List<Customer> get() = Customers.filter(customers, customerQuick, companyQuery, companyEnding)

    /* Validity & renewals */
    var validityQuick by mutableStateOf(ValQuick.ALL)
    var validityQuery by mutableStateOf("")
    var validitySoft by mutableStateOf<String?>(null)
    val validityAll: List<ValidityRow> get() = Validity.rows(customers, plans, payments)
    val validityShown: List<ValidityRow> get() = Validity.filter(validityAll, validityQuick, validityQuery, validitySoft)

    /* Payments */
    var payQuick by mutableStateOf(PayQuick.ALL)
    var payQuery by mutableStateOf("")
    var paySoft by mutableStateOf<String?>(null)
    var payKind by mutableStateOf<String?>(null)
    val paymentsShown: List<Payment>
        get() = PayFilter.apply(payments?.payments.orEmpty(), payQuick, today(), payQuery, paySoft, payKind)

    fun paymentById(id: Int): Payment? = payments?.byId(id)

    /** The Record payment form while it is open (newPaymentForm fills it). */
    var payForm by mutableStateOf(PaymentForm())

    /* By software: each its own quick view, tab and search */
    val swViews = mutableStateMapOf<String, SwView>()
    fun swView(sw: String): SwView = swViews[sw] ?: SwView()
    fun setSwView(sw: String, v: SwView) { swViews[sw] = v }

    /* Software & plans */
    var planSoft by mutableStateOf<String?>(null)
    var planQuery by mutableStateOf("")
    /** 2.1.0 — "plans", or "rules": each software's own demo rules. */
    var planTab by mutableStateOf("plans")

    /** A plan's name by its code ("GOLD" → "Gold"), on [sw]; [fallback] is the name the company itself came with. */
    fun planName(sw: String, code: String?, fallback: String? = null): String = Plans.nameOf(plans, sw, code, fallback)

    /** Why [sw]'s plans cannot be shown — null while they can (or before they have been asked). */
    fun plansProblem(sw: String): String? {
        if (plansLoad == Load.FAILED) return plansError ?: "The plans could not be read."
        val p = plans ?: return null
        val b = p.block(sw) ?: return "This service does not list ${Software.name(sw)}'s plans."
        return if (b.supported) null else b.problem
    }

    /** Opened once at startup when a key was remembered. */
    fun resume() {
        if (!signedIn && key.isNotBlank()) load()
    }

    /* 4.58.1 — when the last good read came in, shown beside People */
    var refreshedAt by mutableStateOf<String?>(null)

    /** 4.58.1 — "refresh is not working proper": it worked, but said
     *  nothing, so a press that succeeded looked like one that did not.
     *  The icon's press now says so either way. */
    /* load() already fetches the enquiries, the feedback and the broadcasts
       once the companies have arrived; asking for them here as well sent every
       one of those requests twice per tap. */
    fun refreshNow() {
        load(announce = true)
    }

    fun load(onDone: (() -> Unit)? = null, announce: Boolean = false) {
        if (key.isBlank()) {
            gateError = "Enter the admin key to open the console."
            return
        }
        /* 4.72.0 — audit #36: the key goes only to Nexora's own service (a
           test build also to a staging copy) — said here, before it is sent. */
        ServiceHost.problem(baseUrl)?.let {
            if (signedIn) say(it, Msg.Kind.ERR) else gateError = it
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val fresh = api.licences()
                data = fresh
                refreshedAt = Fmt.clock()
                if (announce) say("Up to date \u2014 " + refreshedAt, Msg.Kind.OK)
                settingsForm = SettingsForm.of(fresh.settings)
                gateError = null
                if (!signedIn) {
                    signedIn = true
                    prefs.baseUrl = baseUrl
                    prefs.rememberKey = rememberKey
                    if (rememberKey) prefs.adminKey = key.trim() else prefs.signOut()
                }
                /* 4.72.0 (review) — the service took this key: the background
                   watch, stopped by a refused one, may ask again. */
                keyRefused = false
                if (prefs.keyRejected) prefs.keyRejected = false
                /* A company left open keeps its people in step with the reload. */
                openCompany?.let { loadPeople(it) }
                /* The leads come with everything else. Quietly: a service that
                   has not been deployed with enquiries yet should still open. */
                loadInquiries(quiet = true)
                loadFeedback(quiet = true)
                loadBroadcasts()
                /* 1.9.0 — and the other software, last and apart: Fabric Stock may take a minute to wake */
                loadProducts()
                fabricOpen?.let { loadFabricDetail(it, quiet = true, sw = fabricOpenSw) }
                /* 2.0.0 — the plans of every software and the payments, quietly, apart from the rest */
                loadPlans()
                loadPayments()
                /* 2.1.0 — and each software's own demo rules */
                loadRules()
                /* And whether a newer build of this application exists. */
                checkForUpdate()
            } catch (e: Exception) {
                if ((e as? ApiError)?.keyRefused == true) keyRefused = true
                val text = (e as? ApiError)?.message ?: "Something went wrong."
                if (signedIn) say(text, Msg.Kind.ERR) else gateError = text
            } finally {
                busy = false
                onDone?.invoke()
            }
        }
    }

    /* 4.72.0 (review) — the service refused this key. The once-a-minute
       read of an open company's people stops asking with it: each refusal
       counts towards the service's lock on this address (five = fifteen
       minutes, the web console on the same Wi-Fi shut out too). A press of
       Refresh still asks; a good load starts it again. */
    private var keyRefused = false

    fun signOut() {
        prefs.signOut()
        key = ""
        signedIn = false
        data = ConsoleData()
        openCompany = null
        companyFilter = null
        companyView = CompanyView.ALL
        people = null
        msg = null
        productsJob?.cancel()
        products = null
        productsLoad = Load.IDLE
        productsError = null
        software = SoftwareFilter.ALL
        fabricDetail = null
        fabricOpen = null
        fabricOpenSw = Software.FABRIC
        rulesJob?.cancel()
        rules = null
        rulesLoad = Load.IDLE
        rulesError = null
        plansJob?.cancel()
        paymentsJob?.cancel()
        plans = null
        plansLoad = Load.IDLE
        plansError = null
        payments = null
        paymentsLoad = Load.IDLE
        paymentsError = null
        /* 2.3.0 — the invoices and Invoice Settings go with the key */
        invoicesJob?.cancel()
        invoices = null
        invoicesLoad = Load.IDLE
        invSettings = null
        invSettingsLoad = Load.IDLE
        invSettingsForm = null
        quote = null
        quoteFor = null
        customerQuick = CustQuick.ALL
        newCustomer = NewCustomerForm()
    }

    /* 4.72.0 — [holdMs]: a long answer that must be read (Delete's) stays longer than six seconds */
    fun say(text: String, kind: Msg.Kind, holdMs: Long = 6_000) {
        val m = Msg(text, kind)
        msg = m
        lastSaid = m
        viewModelScope.launch {
            delay(holdMs)
            if (msg === m) msg = null
        }
    }

    fun dismissMessage() {
        msg = null
    }

    /* ---------- feedback & problem reports (1.4.0) ---------- */

    val feedback: List<Feedback>
        get() = feedbackData.feedback.filter {
            it.matches(feedbackQuery) &&
                (feedbackKind == null || it.kind == feedbackKind) &&
                (feedbackState == null || it.state == feedbackState)
        }

    val openFeedback get() = feedbackData.feedback.count { it.isOpen }
    val newFeedbackCount get() = feedbackData.feedback.count { it.state == "NEW" }
    val openBugs get() = feedbackData.feedback.count { it.isOpen && it.isBug }

    fun feedbackById(id: Int): Feedback? = feedbackData.feedback.find { it.id == id }

    fun loadFeedback(quiet: Boolean = false) {
        if (key.isBlank()) return
        viewModelScope.launch {
            try {
                feedbackData = api.feedback()
            } catch (e: Exception) {
                /* An older service has no such route; not worth a red strip. */
                if (!quiet) say((e as? ApiError)?.message ?: "Could not read the reports.", Msg.Kind.ERR)
            }
        }
    }

    private fun feedbackCall(body: JSONObject, okText: String?, then: () -> Unit = {}) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.feedbackAction(body)
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
                loadFeedback()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    fun setFeedbackState(id: Int, state: String) =
        feedbackCall(JSONObject().put("action", "state").put("id", id).put("state", state), null)

    fun replyFeedback(id: Int, note: String) =
        feedbackCall(JSONObject().put("action", "reply").put("id", id).put("reply", note), "Note saved.")

    fun deleteFeedback(id: Int, then: () -> Unit = {}) =
        feedbackCall(JSONObject().put("action", "delete").put("id", id), "Removed.") {
            shots.remove(id)
            then()
        }

    /** The picture on a report, fetched once and kept for the session. */
    fun loadShot(id: Int) {
        if (shots.containsKey(id) || shotBusy == id) return
        viewModelScope.launch {
            shotBusy = id
            try {
                val s = api.feedbackShot(id)
                shots[id] = s?.let { decodeShot(it) }
            } catch (e: Exception) {
                shots[id] = null
                say((e as? ApiError)?.message ?: "Could not fetch the picture.", Msg.Kind.ERR)
            } finally {
                if (shotBusy == id) shotBusy = null
            }
        }
    }

    private fun decodeShot(dataUrl: String): ImageBitmap? = try {
        val bytes = Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
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

    /** 1.6.0 — Nexora AI questions a day for this company (0 = the service's own number). */
    fun setAiLimit(id: Int, limit: Int) =
        companyAction(JSONObject().put("id", id).put("action", "ailimit").put("aiDailyLimit", limit),
            okText = if (limit > 0) "Nexora AI: $limit questions a day." else "Nexora AI: the service's own number a day.")

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

    /* 4.72.0 — audit #40: Delete now ARCHIVES. A service that keeps a deleted
       company answers archived:true with deletedAt, purgeAt, restoreDays and a
       warning saying it is kept 30 days, can be restored until then and is
       erased after; that warning is the message (in the console's own words
       when a service sends none), held long enough to be read. An older
       service erased the company at once, and is told exactly as before. */
    fun deleteCompany(id: Int, typedName: String) {
        companyAction(
            JSONObject().put("id", id).put("action", "delete").put("confirmName", typedName)
        ) { r ->
            val x = r.optJSONObject("removed")
            openCompany = null
            companyFilter = null
            val name = r.optString("name")
            val counts = "${x?.optInt("installations") ?: 0} installation(s), " +
                "${x?.optInt("users") ?: 0} user(s), " +
                "${x?.optInt("records") ?: 0} synced record(s), " +
                "${x?.optInt("inkModels") ?: 0} ink model(s)"
            if (r.optBoolean("archived")) {
                val days = r.optInt("restoreDays", DeletedCompany.KEEP_DAYS)
                val until = r.optString("purgeAt").takeIf { it.isNotBlank() }?.let { " until " + Fmt.day(it) }.orEmpty()
                val said = r.optString("warning").ifEmpty {
                    "$name is deleted and kept for $days days: Restore (Customers → Deleted) puts it back " +
                        "exactly as it was$until. After that it is erased for good."
                }
                say("$said ($counts are kept until then.)", Msg.Kind.OK, holdMs = 20_000)
            } else {
                say("Deleted $name — $counts.", Msg.Kind.OK)
            }
        }
    }

    /**
     * 4.72.0 — audit #40: puts a deleted company back exactly as it was (the
     * service's `undelete` — the one action a deleted company takes), then
     * the list shows the live companies again, where it now is.
     */
    fun undeleteCompany(id: Int) =
        companyAction(JSONObject().put("id", id).put("action", "undelete")) { r ->
            companyView = CompanyView.ALL
            say(r.optString("warning").ifEmpty { "${r.optString("name")} is restored." }, Msg.Kind.OK)
        }

    /**
     * 2.0.0 — New customer: each software ticked gets its own licence key, made in its own service
     * (Sales & Costing first, then Fabric Stock linked to it), and the administrator when one was named.
     * A Fabric Stock that is asleep or refuses leaves the Sales & Costing company made, and says so.
     * [done] gets the new customer's key (to open its window), or null when nothing was made.
     */
    fun createCustomer(done: (String?) -> Unit = {}) {
        /* each remote software on its first plan when it has plans of its own and none was chosen (the one the screen shows) */
        val f = Software.REMOTE_IDS.fold(newCustomer) { n, sw ->
            val r = n.remote(sw)
            if (r.on && r.plan == null) n.withRemote(sw, r.copy(plan = plans?.block(sw)?.takeIf { it.supported }?.livePlans?.firstOrNull()?.code))
            else n
        }
        val name = f.name.trim()
        if (name.isEmpty()) {
            say("A company name is required.", Msg.Kind.ERR)
            return
        }
        if (!f.weight && f.remotesOn.isEmpty()) {
            say("Tick at least one software.", Msg.Kind.ERR)
            return
        }
        viewModelScope.launch {
            busy = true
            val notes = ArrayList<String>()
            var weightId: Int? = null
            var weightKey: String? = null
            /* 2.1.0 — each remote software made: its id and its key, in the table's order */
            val made = LinkedHashMap<String, Pair<Int?, String?>>()
            try {
                if (f.weight) {
                    val r = api.company(Requests.newWeight(f))
                    val err = r.optString("error")
                    if (err.isNotEmpty()) {
                        say(err, Msg.Kind.ERR)
                        done(null)
                        return@launch
                    }
                    val co = r.optJSONObject("company")
                    weightId = co?.optInt("id")?.takeIf { it > 0 }
                    weightKey = co?.optString("licence_key")?.takeIf { it.isNotEmpty() }
                    if (weightId != null) {
                        Requests.newAdmin(weightId, f)?.let { body ->
                            val a = runCatching { api.company(body) }.getOrNull()
                            a?.optString("error")?.takeIf { it.isNotEmpty() }?.let { notes += "Sales & Costing administrator: $it" }
                        }
                    }
                }
                for (sw in f.remotesOn) {
                    try {
                        val r = api.remoteAction(sw, Requests.newRemote(f, sw, weightId))
                        val co = r.optJSONObject("company")
                        made[sw] = co?.optInt("id")?.takeIf { it > 0 } to co?.optString("licenceKey")?.takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        notes += Software.name(sw) + ": " + ((e as? ApiError)?.message ?: "something went wrong.")
                    }
                }
                newCustomer = NewCustomerForm()
                val keys = listOfNotNull(weightKey) + made.values.mapNotNull { it.second }
                val said = "$name made." +
                    (weightKey?.let { " Sales & Costing key $it." } ?: "") +
                    made.entries.joinToString("") { (sw, m) -> m.second?.let { " ${Software.name(sw)} key $it." } ?: "" } +
                    (when (keys.size) {
                        0, 1 -> ""
                        2 -> " Two keys — give both to the customer."
                        else -> " ${keys.size} keys — give each to the customer."
                    }) +
                    (if (notes.isEmpty()) "" else " " + notes.joinToString(" "))
                say(said, if (notes.isEmpty()) Msg.Kind.OK else Msg.Kind.WARN, holdMs = 20_000)
                /* the window opens once the new customer is in the list (load reads the other software again too) */
                val first = made.entries.firstOrNull { it.value.first != null }
                val key = weightId?.let { "w$it" } ?: first?.let { (sw, m) -> "${Software.remote(sw)?.prefix ?: 'f'}${m.first}" }
                if (weightId != null) load(onDone = { done(key) })
                else if (first != null) loadProducts(onDone = { done(key) })
                else done(null)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(null)
            } finally {
                busy = false
            }
        }
    }

    /** 2.0.0 — a new licence key for a Sales & Costing company; the old one stops adding computers and phones. */
    fun rekey(c: Company) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.company(JSONObject().put("id", c.id).put("action", "rekey"))
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(err, Msg.Kind.ERR)
                    return@launch
                }
                say(
                    "New licence key for ${r.optString("name").ifEmpty { c.name }}: ${r.optString("key")} — give it only to whoever " +
                        "adds the next computer or phone. It is also on the customer's Licence tab.",
                    Msg.Kind.OK, holdMs = 20_000
                )
                load()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    /**
     * 2.0.0 — Save on a Sales & Costing licence edited in its window: one company action per thing that
     * changed (Requests.weightSteps), every refusal and warning said together, and the list read again.
     */
    fun saveWeight(c: Company, e: WeightEdit, done: (Boolean) -> Unit = {}) {
        val steps = Requests.weightSteps(c, e)
        if (steps.isEmpty()) {
            say("Nothing was changed.", Msg.Kind.OK)
            done(true)
            return
        }
        viewModelScope.launch {
            busy = true
            val said = ArrayList<String>()
            var refused = false
            try {
                for (s in steps) {
                    val r = api.company(s)
                    r.optString("error").takeIf { it.isNotEmpty() }?.let { said += it; refused = true }
                    r.optString("warning").takeIf { it.isNotEmpty() }?.let { said += it }
                }
            } catch (ex: Exception) {
                said += (ex as? ApiError)?.message ?: "Something went wrong."
                refused = true
            } finally {
                busy = false
            }
            if (said.isEmpty()) say("Saved.", Msg.Kind.OK)
            else say(said.joinToString(" "), if (refused) Msg.Kind.ERR else Msg.Kind.WARN, holdMs = 12_000)
            load()
            done(!refused)
        }
    }

    /**
     * 2.0.0 — Save on a Fabric Stock licence edited in its window: one update with what changed.
     * 2.0.1 — the plan and the customer's own features go in the same update; a retired or unknown plan is
     * refused in Fabric Stock's own sentence (shown, the window stays in Edit), and after a plan or features
     * change the plans are read again so their customer counts follow.
     */
    fun saveFabric(f: FabricCompany, e: FabricEdit, done: (Boolean) -> Unit = {}) {
        val body = Requests.fabricUpdate(f, e)
        if (body == null) {
            say("Nothing was changed.", Msg.Kind.OK)
            done(true)
            return
        }
        val planOrFeatures = body.has("plan") || body.has("featureOverrides")
        viewModelScope.launch {
            busy = true
            var ok = true
            try {
                /* a refusal (400 with its sentence) is thrown in the service's own words;
                   2.1.0 — sent to the company's own software (fabric, erp, consult) */
                val r = api.remoteAction(f.sw, body)
                val warn = r.optString("warning")
                say(warn.ifEmpty { "Saved." }, if (warn.isNotEmpty()) Msg.Kind.WARN else Msg.Kind.OK)
            } catch (ex: Exception) {
                ok = false
                say(Software.name(f.sw) + ": " + ((ex as? ApiError)?.message ?: "something went wrong."), Msg.Kind.ERR, holdMs = 12_000)
            } finally {
                busy = false
            }
            loadProducts()
            fabricOpen?.let { loadFabricDetail(it, quiet = true, sw = fabricOpenSw) }
            if (ok && planOrFeatures) loadPlans()
            done(ok)
        }
    }

    /**
     * 2.0.0 — Sales & Costing for a customer on Fabric Stock alone: a year's licence of its own (its own
     * key), made with the Fabric Stock company's details, then linked to it. [done] gets the customer's
     * new key ("w…").
     */
    /* 2.1.0 — [also]: the customer's companies on its other remote software, linked to the new one too */
    fun startWeightFor(f: FabricCompany, also: List<FabricCompany> = emptyList(), done: (String?) -> Unit = {}) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.company(Requests.weightFor(f))
                val err = r.optString("error")
                if (err.isNotEmpty()) {
                    say(err, Msg.Kind.ERR)
                    done(null)
                    return@launch
                }
                val co = r.optJSONObject("company")
                val id = co?.optInt("id") ?: 0
                val key = co?.optString("licence_key").orEmpty()
                val linked = runCatching { api.remoteAction(f.sw, JSONObject().put("action", "link").put("id", f.id).put("companyId", id)) }.isSuccess
                also.filter { it.sw != f.sw || it.id != f.id }.forEach { o ->
                    runCatching { api.remoteAction(o.sw, JSONObject().put("action", "link").put("id", o.id).put("companyId", id)) }
                }
                say(
                    "${f.name} now has Sales & Costing. Its Sales & Costing licence key is $key." +
                        (if (linked) "" else " It could not be linked to its ${Software.name(f.sw)} company yet — link it from the Company tab."),
                    if (linked) Msg.Kind.OK else Msg.Kind.WARN, holdMs = 20_000
                )
                load(onDone = { done(if (id > 0) "w$id" else null) })
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(null)
            } finally {
                busy = false
            }
        }
    }

    /* ---------- the people on a company ---------- */

    /** quiet = the once-a-minute read while a company is open: what is on
     *  screen stays until the answer is in, and a failed read leaves it. */
    fun loadPeople(companyId: Int, quiet: Boolean = false) {
        if (quiet && keyRefused) return
        viewModelScope.launch {
            peopleBusy = true
            if (!quiet) peopleError = null
            try {
                people = api.people(companyId)
                peopleError = null
                refreshedAt = Fmt.clock()
                keyRefused = false
            } catch (e: Exception) {
                if ((e as? ApiError)?.keyRefused == true) keyRefused = true
                if (quiet) return@launch
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

    /* ---------- 1.9.0: Fabric Stock — its own licences, run from here ---------- */

    /* A newer read replaces an older one still on its way (a Refresh, then a change). */
    private var productsGen = 0
    private var detailGen = 0

    /**
     * The software and Fabric Stock's companies. Quiet: what went wrong is
     * said where it matters — a card with Retry on the Companies list and on
     * the company — never as a red strip, and the Sales & Costing screens never
     * wait for it. 2.0.0 — [onDone], once this read is in (or failed): a new
     * Fabric Stock customer's window opens when its company is in the list.
     */
    fun loadProducts(onDone: (() -> Unit)? = null) {
        if (key.isBlank()) return
        val gen = ++productsGen
        productsJob?.cancel()
        productsLoad = Load.LOADING
        productsJob = viewModelScope.launch {
            try {
                val p = api.products()
                if (gen != productsGen) return@launch
                products = p
                productsError = null
                productsLoad = Load.READY
                onDone?.invoke()
            } catch (e: Exception) {
                if (gen != productsGen || e is CancellationException) return@launch
                products = null
                productsError = productsProblem(e)
                productsLoad = Load.FAILED
                onDone?.invoke()
            }
        }
    }

    private fun productsProblem(e: Exception): String {
        val a = e as? ApiError ?: return "The software list could not be read."
        return if (a.status == 404) "This service does not list the other software yet — it needs the service update."
        else a.message ?: "The software list could not be read."
    }

    /**
     * One Fabric Stock company's people and computers and phones. [quiet]: a re-read; a failure leaves what is shown.
     * 2.1.0 — [sw]: any software the console service reaches (fabric, erp, consult).
     */
    fun loadFabricDetail(id: Int, quiet: Boolean = false, sw: String = Software.FABRIC) {
        if (key.isBlank()) return
        val gen = ++detailGen
        fabricDetailJob?.cancel()
        if (fabricDetail?.company?.id != id || fabricDetail?.company?.sw != sw) fabricDetail = null
        if (!quiet) fabricDetailError = null
        fabricDetailBusy = true
        fabricDetailJob = viewModelScope.launch {
            try {
                val d = api.remoteDetail(sw, id)
                if (gen != detailGen) return@launch
                fabricDetail = d
                fabricDetailError = null
            } catch (e: Exception) {
                if (gen != detailGen || e is CancellationException) return@launch
                fabricDetailError = (e as? ApiError)?.message ?: "Could not read this company."
            } finally {
                if (gen == detailGen) fabricDetailBusy = false
            }
        }
    }

    /**
     * Every Fabric Stock change goes through here, as every Weight Calc one
     * goes through companyAction: one busy mark, the service's warning or
     * [okText] on success, its own words on failure (502 FABRIC_DOWN when
     * Fabric Stock is asleep, FABRIC_KEY when it refuses the key) — and then
     * Fabric Stock's companies, and the one open, are read again.
     */
    fun fabricAction(body: JSONObject, okText: String? = null, then: (JSONObject) -> Unit = {}) =
        remoteAction(Software.FABRIC, body, okText, then)

    /**
     * 2.1.0 — every change to a company on any software the console service reaches goes through here:
     * POST /admin/api/<sw> (fabric, erp, consult), the same actions for each, told the same way.
     */
    fun remoteAction(sw: String, body: JSONObject, okText: String? = null, then: (JSONObject) -> Unit = {}) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.remoteAction(sw, body)
                val warn = r.optString("warning")
                when {
                    warn.isNotEmpty() -> say(warn, Msg.Kind.WARN)
                    okText != null -> say(okText, Msg.Kind.OK)
                }
                then(r)
                loadProducts()
                fabricOpen?.let { loadFabricDetail(it, quiet = true, sw = fabricOpenSw) }
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    private fun fabricUpdate(id: Int, okText: String, sw: String = Software.FABRIC, fill: JSONObject.() -> Unit) =
        remoteAction(sw, JSONObject().put("action", "update").put("id", id).apply(fill), okText)

    /** A demo made a paying licence: a year from today. */
    fun fabricMakeLicensed(f: FabricCompany) =
        fabricUpdate(f.id, "${f.name} is licensed on ${Software.name(f.sw)} for a year.", f.sw) { put("state", "LICENSED").put("days", 365) }

    /**
     * [add] days more. Fabric Stock renews by starting a new period from
     * today, so the days still left are sent with them (FabricCompany.renewDays)
     * and nothing already paid for is lost. 2.1.0 — every remote software renews the same way.
     */
    fun fabricAddDays(f: FabricCompany, add: Int) =
        fabricUpdate(f.id, "${Software.name(f.sw)} now ends on ${Fmt.day(f.renewEnd(add).toString())}.", f.sw) { put("days", f.renewDays(add)) }

    fun fabricSeats(id: Int, seats: Int, sw: String = Software.FABRIC) = fabricUpdate(id, "Seats saved.", sw) { put("seats", seats) }
    fun fabricGrace(id: Int, days: Int, sw: String = Software.FABRIC) = fabricUpdate(id, "Offline days saved.", sw) { put("graceDays", days) }
    fun fabricRename(id: Int, name: String, sw: String = Software.FABRIC) = fabricUpdate(id, "Renamed.", sw) { put("name", name.trim()) }
    fun fabricGstin(id: Int, gstin: String, sw: String = Software.FABRIC) = fabricUpdate(id, "GSTIN saved.", sw) { put("gstin", gstin.trim().uppercase()) }
    fun fabricEmail(id: Int, email: String, sw: String = Software.FABRIC) = fabricUpdate(id, "Email saved.", sw) { put("email", email.trim()) }
    fun fabricPhone(id: Int, phone: String, sw: String = Software.FABRIC) = fabricUpdate(id, "Mobile saved.", sw) { put("phone", phone.trim()) }
    fun fabricNote(id: Int, notes: String, sw: String = Software.FABRIC) = fabricUpdate(id, "Note saved.", sw) { put("notes", notes.trim()) }

    fun fabricSuspend(f: FabricCompany) =
        remoteAction(f.sw, JSONObject().put("action", "suspend").put("id", f.id), "${f.name} is suspended on ${Software.name(f.sw)}.")

    fun fabricResume(f: FabricCompany) =
        remoteAction(f.sw, JSONObject().put("action", "resume").put("id", f.id), "${f.name} runs on ${Software.name(f.sw)} again.")

    fun fabricAdministrator(id: Int, name: String, pin: String, email: String = "", sw: String = Software.FABRIC) =
        remoteAction(
            sw,
            JSONObject().put("action", "adminuser").put("id", id).put("name", name.trim()).put("pin", pin)
                .apply { if (email.isNotBlank()) put("email", email.trim()) },
            "Done."
        )

    fun fabricPasscode(id: Int, loginId: String, passcode: String, sw: String = Software.FABRIC) =
        remoteAction(
            sw,
            JSONObject().put("action", "passcode").put("id", id).put("passcode", passcode)
                .apply { if (loginId.isNotBlank()) put("loginId", loginId.trim()) },
            "Done."
        )

    fun fabricSignOut(id: Int, userId: Any, sw: String = Software.FABRIC) =
        remoteAction(sw, JSONObject().put("action", "usersignout").put("id", id).put("userId", userId), "Signed out.")

    /** Withdraw a Fabric Stock computer or phone; Give back undoes only what Nexora withdrew. */
    fun fabricRevoke(deviceId: Any, sw: String = Software.FABRIC) =
        remoteAction(sw, JSONObject().put("action", "revoke").put("deviceId", deviceId), "Withdrawn.")

    fun fabricGiveBack(deviceId: Any, sw: String = Software.FABRIC) =
        remoteAction(sw, JSONObject().put("action", "restore").put("deviceId", deviceId), "Given back.")

    fun fabricLink(id: Int, companyId: Int, name: String, sw: String = Software.FABRIC) =
        remoteAction(sw, JSONObject().put("action", "link").put("id", id).put("companyId", companyId), "Linked to $name.")

    /** Two different companies, even though the GSTIN is the same. */
    fun fabricApart(id: Int, sw: String = Software.FABRIC) =
        remoteAction(sw, JSONObject().put("action", "apart").put("id", id), "Kept apart — two different companies.")

    /** Forget the hand link; the GSTIN rule decides again. */
    fun fabricUnlink(id: Int, sw: String = Software.FABRIC) =
        remoteAction(sw, JSONObject().put("action", "unlink").put("id", id), "Unlinked.")

    /**
     * Fabric Stock for a Weight Calc company that has none: a 7-day demo, or a
     * year's licence, made on its own licence but with the company's name,
     * GSTIN, email, mobile, seats and offline days to start from — and linked
     * to it at once (linkTo).
     */
    fun startFabric(co: Company, demo: Boolean) = startRemote(Software.FABRIC, co, demo)

    /**
     * 2.1.0 — "Start <software> for this customer": the same for any remote software — a demo as long as that
     * software's own demo rules say (7 days when they could not be read), or a year's licence; created with linkTo.
     */
    fun startRemote(sw: String, co: Company, demo: Boolean) {
        val n = Software.name(sw)
        remoteAction(
            sw,
            fabricCreateBody(co.name, if (demo) "DEMO" else "LICENSED", if (demo) demoDays(sw) else 365, co.seats, co.graceDays,
                co.gstin, co.email, co.phone).put("linkTo", co.id)
        ) { r -> say(fabricCreated(r, "$n started for ${co.name}", n), Msg.Kind.OK, holdMs = 15_000) }
    }

    /**
     * 2.1.0 — a remote software for a customer with no Sales & Costing: made with its details and its GSTIN, so
     * it is shown with the customer's other software (one customer per GSTIN).
     */
    fun startRemoteFor(sw: String, x: Customer, demo: Boolean) {
        val lead = x.lead ?: return
        val n = Software.name(sw)
        remoteAction(
            sw,
            fabricCreateBody(x.name, if (demo) "DEMO" else "LICENSED", if (demo) demoDays(sw) else 365, lead.seats, lead.graceDays,
                x.gstin, x.email, x.phone)
        ) { r -> say(fabricCreated(r, "$n started for ${x.name}", n), Msg.Kind.OK, holdMs = 15_000) }
    }

    private fun fabricCreateBody(
        name: String, state: String, days: Int, seats: Int, graceDays: Int,
        gstin: String?, email: String?, phone: String?
    ): JSONObject = JSONObject()
        .put("action", "create")
        .put("name", name.trim())
        .put("state", state)
        .put("days", days)
        .put("seats", seats.coerceAtLeast(1))
        .put("graceDays", graceDays.coerceAtLeast(0))
        .apply {
            gstin?.trim()?.takeIf { it.isNotEmpty() }?.let { put("gstin", it.uppercase()) }
            email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
            phone?.trim()?.takeIf { it.isNotEmpty() }?.let { put("phone", it) }
        }

    /* "… Fabric Stock licence key NFS-… — give this to the customer." */
    private fun fabricCreated(r: JSONObject, lead: String, name: String = Software.FABRIC_NAME): String {
        val key = r.optJSONObject("company")?.optString("licenceKey").orEmpty()
        return if (key.isEmpty()) "$lead." else "$lead. $name licence key $key — give this to the customer; it is not the Sales & Costing key."
    }

    /* ---------- 2.1.0: each software's own demo rules ---------- */

    /** Every software's demo rules. Quiet: what went wrong is said where they are shown, never as a red strip. */
    fun loadRules(onDone: (() -> Unit)? = null) {
        if (key.isBlank()) return
        val gen = ++rulesGen
        rulesJob?.cancel()
        rulesLoad = Load.LOADING
        rulesJob = viewModelScope.launch {
            try {
                val r = api.softwareSettings()
                if (gen != rulesGen) return@launch
                rules = r
                rulesError = null
                rulesLoad = Load.READY
            } catch (e: Exception) {
                if (gen != rulesGen || e is CancellationException) return@launch
                rulesError = (e as? ApiError)?.let {
                    if (it.status == 404) "This service keeps no demo rules per software yet — it needs the service update." else it.message
                } ?: "The demo rules could not be read."
                rulesLoad = Load.FAILED
            } finally {
                if (gen == rulesGen) onDone?.invoke()
            }
        }
    }

    /**
     * 2.1.0 — one software's demo rules changed, on that software's own server (POST /admin/api/software-settings
     * for that ONE software). Values out of range are refused here, before anything is sent; a refusal from the
     * service is shown in its own words. [done] is told whether it was saved.
     */
    fun saveRules(sw: String, form: RulesForm, done: (Boolean) -> Unit = {}) {
        form.problem(sw)?.let {
            say(it, Msg.Kind.ERR)
            done(false)
            return
        }
        viewModelScope.launch {
            busy = true
            var ok = true
            try {
                val r = api.saveSoftwareSettings(form.body(sw))
                val saved = r.optJSONObject("settings")?.let { s ->
                    SoftwareRules.from(JSONObject().put("id", sw).put("ok", true).put("settings", s))
                }
                if (saved != null) {
                    val all = rules?.software.orEmpty()
                    rules = SoftwareRulesData(if (all.any { it.id == sw }) all.map { if (it.id == sw) saved else it } else all + saved)
                }
                say("${Software.name(sw)}'s demo rules are saved on its own server.", Msg.Kind.OK)
            } catch (e: Exception) {
                ok = false
                say(Software.name(sw) + ": " + ((e as? ApiError)?.message ?: "something went wrong."), Msg.Kind.ERR, holdMs = 12_000)
            } finally {
                busy = false
            }
            if (ok) loadRules()
            done(ok)
        }
    }

    /* ---------- 2.0.0: the plans and the payments ---------- */

    /** Every software's plans. Quiet: what went wrong is said where the plans are shown, never as a red strip. */
    fun loadPlans() {
        if (key.isBlank()) return
        val gen = ++plansGen
        plansJob?.cancel()
        plansLoad = Load.LOADING
        plansJob = viewModelScope.launch {
            try {
                val p = api.plans()
                if (gen != plansGen) return@launch
                plans = p
                plansError = null
                plansLoad = Load.READY
            } catch (e: Exception) {
                if (gen != plansGen || e is CancellationException) return@launch
                plansError = (e as? ApiError)?.let {
                    if (it.status == 404) "This service has no plans per software yet — it needs the service update." else it.message
                } ?: "The plans could not be read."
                plansLoad = Load.FAILED
            }
        }
    }

    /** The payments ledger. Quiet, like the plans. */
    fun loadPayments() {
        if (key.isBlank()) return
        val gen = ++paymentsGen
        paymentsJob?.cancel()
        paymentsLoad = Load.LOADING
        paymentsJob = viewModelScope.launch {
            try {
                val p = api.payments()
                if (gen != paymentsGen) return@launch
                payments = p
                paymentsError = null
                paymentsLoad = Load.READY
            } catch (e: Exception) {
                if (gen != paymentsGen || e is CancellationException) return@launch
                paymentsError = (e as? ApiError)?.let {
                    if (it.status == 404) "This service keeps no payments yet — it needs the service update." else it.message
                } ?: "The payments could not be read."
                paymentsLoad = Load.FAILED
            }
        }
    }

    /**
     * Create (no [existing]) or update a plan. [done] gets the plan as the service saved it (null when
     * refused — Fabric Stock answers 501 NOT_YET until it has plans of its own).
     */
    fun savePlan(sw: String, existing: Plan?, form: PlanForm, done: (Plan?) -> Unit = {}) {
        if (form.name.isBlank()) {
            say("Give the plan a name.", Msg.Kind.ERR)
            return
        }
        /* 2.3.0 — the validity of one period, as the desktop console refuses it */
        if (form.daysValue == null) {
            say("Validity is the days in one period — 1 to 3660 (365 for a year).", Msg.Kind.ERR)
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val r = api.planAction(Requests.planSave(sw, existing, form))
                val p = r.optJSONObject("plan")?.let { Plan.from(it) }
                say(
                    (if (existing != null) "Saved: " else "Made: ") + (p?.name ?: form.name.trim()) +
                        ". Every customer on it hears it at their next check.",
                    Msg.Kind.OK
                )
                loadPlans()
                done(p)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(null)
            } finally {
                busy = false
            }
        }
    }

    /** retire, restore or delete a plan; [done] is told whether it was done. */
    fun planDo(sw: String, action: String, p: Plan, done: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            busy = true
            try {
                api.planAction(Requests.planAction(sw, action, p.code))
                say(
                    when (action) {
                        "retire" -> "${p.name} is retired — no longer offered to a new customer; the ${p.customers} on it keep it."
                        "restore" -> "${p.name} is in use again."
                        else -> "${p.name} is deleted."
                    },
                    Msg.Kind.OK
                )
                loadPlans()
                done(true)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(false)
            } finally {
                busy = false
            }
        }
    }

    /**
     * The Record payment form, for [presetKey]'s customer (else the first one with [presetSw], else the
     * first) and [presetSw] when that customer has it — paid today, renewing a year.
     */
    fun newPaymentForm(presetKey: String? = null, presetSw: String? = null): PaymentForm {
        val all = customers
        val c = presetKey?.let { customer(it) }
            ?: presetSw?.let { sw -> all.firstOrNull { it.has(sw) } }
            ?: all.firstOrNull()
        val sw = when {
            c == null -> presetSw ?: Software.WEIGHT
            presetSw != null && c.has(presetSw) -> presetSw
            c.w != null -> Software.WEIGHT
            else -> c.softwares.firstOrNull() ?: Software.FABRIC
        }
        /* 2.3.0 — by plan unless told otherwise: the customer's own plan, its seats, one period */
        return presetPlan(PaymentForm(customerKey = c?.key.orEmpty(), software = sw, paidOn = today().toString(), byPlan = true))
    }

    /**
     * 2.3.0 — the plan part of the form for its customer and software (the desktop's preset()): the plan it is on
     * now, its seats, one period, and New for a demo else Renewal. The amount is left to the quote.
     */
    fun presetPlan(f: PaymentForm): PaymentForm {
        val x = customer(f.customerKey) ?: return f
        val sw = f.software
        val code = Customers.planCode(x, sw)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: plans?.plansOf(sw)?.firstOrNull { it.active }?.code ?: if (Software.isRemote(sw)) "STANDARD" else "PRO"
        val seats = (if (sw == Software.WEIGHT) x.w?.seats else x.remote(sw)?.seats)?.takeIf { it > 0 } ?: 1
        return f.copy(plan = code, users = seats, periods = 1, kind = if (x.sub(sw)?.state == "DEMO") "NEW" else "RENEWAL",
            typed = false, amount = if (f.byPlan) "" else f.amount)
    }

    /* 2.3.0 — the service's sum for the plan, users and periods on the form (POST payments {action:'quote'}) */
    var quote by mutableStateOf<PayQuote?>(null)
        internal set
    /** Which form the quote (or its error) is for — Billing.quoteKey. */
    var quoteFor by mutableStateOf<String?>(null)
        internal set
    var quoteError by mutableStateOf<String?>(null)
        internal set
    private var quoteJob: Job? = null

    /** Ask the service what [f] comes to — once per plan, kind, users and periods; changes nothing there. */
    fun requote(f: PaymentForm) {
        if (!f.byPlan || f.plan.isBlank()) return
        val x = customer(f.customerKey) ?: return
        if (!x.has(f.software)) return
        val k = Billing.quoteKey(f)
        if (k == quoteFor && (quote != null || quoteError != null)) return
        quoteJob?.cancel()
        quoteFor = k; quote = null; quoteError = null
        quoteJob = viewModelScope.launch {
            try {
                val q = PayQuote.from(api.paymentAction(Billing.quote(f, x)))
                if (quoteFor != k) return@launch
                quote = q
                /* the amount follows the quote until the owner types over it */
                if (!payForm.typed && Billing.quoteKey(payForm) == k) payForm = payForm.copy(amount = PayQuote.typed(q.amount))
            } catch (e: Exception) {
                if (e is CancellationException || quoteFor != k) return@launch
                quoteError = (e as? ApiError)?.message ?: "The amount could not be worked out."
            }
        }
    }

    /** Record payment; with a renewal chosen the licence is renewed in the same step. [done] gets the payment. */
    fun recordPayment(form: PaymentForm, done: (Payment?) -> Unit = {}) {
        val c = customer(form.customerKey)
        when {
            c == null -> return say("Choose the customer.", Msg.Kind.ERR)
            !c.has(form.software) -> return say("This customer has no ${Software.name(form.software)} licence.", Msg.Kind.ERR)
            !form.byPlan && (Money.parse(form.amount) ?: 0.0) <= 0.0 -> return say("Enter the amount received.", Msg.Kind.ERR)
        }
        /* 2.3.0 — by plan: the service sets the plan and the seats and adds plan days × periods */
        val body = try {
            if (form.byPlan) Billing.addByPlan(form, c!!, quote?.takeIf { quoteFor == Billing.quoteKey(form) }) else Requests.paymentAdd(form, c!!)
        } catch (e: Refused) {
            return say(e.message ?: "Check the form.", Msg.Kind.ERR)
        }
        viewModelScope.launch {
            busy = true
            try {
                val r = api.paymentAction(body)
                val p = r.optJSONObject("payment")?.let { Payment.from(it) }
                val warn = r.optString("warning")
                say(
                    warn.ifEmpty {
                        "Recorded ${Money.rupees(p?.amount ?: Money.parse(form.amount))} from ${p?.customer ?: c.name}" +
                            (p?.validTo?.let { " — valid to " + Fmt.day(it) } ?: "") + "."
                    },
                    if (warn.isNotEmpty()) Msg.Kind.WARN else Msg.Kind.OK, holdMs = if (warn.isNotEmpty()) 15_000 else 6_000
                )
                loadPayments()
                if (form.byPlan || form.extendDays > 0) load()
                done(p)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(null)
            } finally {
                busy = false
            }
        }
    }

    fun updatePayment(form: PaymentForm, done: (Boolean) -> Unit = {}) {
        if ((Money.parse(form.amount) ?: 0.0) <= 0.0) return say("Enter the amount received.", Msg.Kind.ERR)
        viewModelScope.launch {
            busy = true
            try {
                api.paymentAction(Requests.paymentUpdate(form))
                say("Saved.", Msg.Kind.OK)
                loadPayments()
                done(true)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(false)
            } finally {
                busy = false
            }
        }
    }

    /** Off the list; the service keeps it, and its Activity list that it was deleted. */
    fun deletePayment(p: Payment, done: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            busy = true
            try {
                api.paymentAction(Requests.paymentDelete(p.id))
                say("Deleted the payment of ${Money.rupees(p.amount)} from ${p.customer}.", Msg.Kind.OK)
                loadPayments()
                done(true)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(false)
            } finally {
                busy = false
            }
        }
    }

    /* ---------- 2.3.0: invoices with and without GST ---------- */

    /* internal (not private) so the tests can draw the screens from made-up invoices, as `payments` */
    var invoices by mutableStateOf<List<Invoice>?>(null)
        internal set
    var invoicesLoad by mutableStateOf(Load.IDLE)
        internal set
    var invoicesError by mutableStateOf<String?>(null)
        internal set
    var invSettings by mutableStateOf<InvoiceSettingsData?>(null)
        internal set
    var invSettingsLoad by mutableStateOf(Load.IDLE)
        internal set
    var invSettingsError by mutableStateOf<String?>(null)
        internal set
    /** Invoice Settings while they are typed (null until the screen opens on what the service has). */
    var invSettingsForm by mutableStateOf<InvoiceSettings?>(null)
    /** The Make invoice form while it is open. */
    var invDraft by mutableStateOf(InvoiceDraft())
    /* the Invoices list's filters */
    var invSoft by mutableStateOf<String?>(null)
    var invDoc by mutableStateOf("all")
    var invStatus by mutableStateOf("all")
    var invMonth by mutableStateOf<String?>(null)
    var invQuery by mutableStateOf("")
    val invoicesShown: List<Invoice> get() = InvFilter.apply(invoices.orEmpty(), invSoft, invDoc, invStatus, invMonth, invQuery)
    private var invoicesJob: Job? = null

    fun invoiceById(id: String): Invoice? = invoices?.find { it.id == id }

    /** A payment's invoices, the issued ones first. */
    fun invoicesFor(paymentId: Int): List<Invoice> = invoices.orEmpty().filter { it.paymentId == paymentId }.sortedBy { it.cancelled }

    /** Every invoice, newest first. Quiet, like the payments: what went wrong is said on the Invoices screen. */
    fun loadInvoices() {
        if (key.isBlank()) return
        invoicesJob?.cancel()
        invoicesLoad = Load.LOADING
        invoicesJob = viewModelScope.launch {
            try {
                invoices = api.invoices()
                invoicesError = null
                invoicesLoad = Load.READY
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                invoicesError = (e as? ApiError)?.let {
                    if (it.status == 404) "This service makes no invoices yet — it needs the service update." else it.message
                } ?: "The invoices could not be read."
                invoicesLoad = Load.FAILED
            }
        }
    }

    /** Invoice Settings; [then] is told whether an invoice can be made with them (null when they could not be read). */
    fun loadInvoiceSettings(then: ((Boolean?) -> Unit)? = null) {
        if (key.isBlank()) return
        invSettingsLoad = Load.LOADING
        viewModelScope.launch {
            try {
                val s = api.invoiceSettings()
                invSettings = s
                invSettingsError = null
                invSettingsLoad = Load.READY
                then?.invoke(s.ready)
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                invSettingsError = (e as? ApiError)?.message ?: "Invoice Settings could not be read."
                invSettingsLoad = Load.FAILED
                then?.invoke(null)
            }
        }
    }

    /** Whether an invoice can be made — from the settings in hand, else read first. A failure is said in red. */
    fun whenInvoiceReady(then: (Boolean) -> Unit) {
        val s = invSettings
        if (s != null) return then(s.ready)
        loadInvoiceSettings { ok ->
            if (ok == null) say("Invoice Settings: " + (invSettingsError ?: "could not be read."), Msg.Kind.ERR) else then(ok)
        }
    }

    /** Save Invoice Settings (checked here first, in the desktop console's words). [done] is told whether they were kept. */
    fun saveInvoiceSettings(s: InvoiceSettings, done: (Boolean) -> Unit = {}) {
        val body = try { Billing.settingsBody(s) } catch (e: Refused) { say(e.message ?: "Check the settings.", Msg.Kind.ERR); return done(false) }
        viewModelScope.launch {
            busy = true
            try {
                val r = api.saveInvoiceSettings(body)
                val kept = r.optJSONObject("settings")?.let { InvoiceSettings.from(it) } ?: Billing.cleanSettings(s)
                invSettings = InvoiceSettingsData(kept, if (r.has("ready")) r.optBoolean("ready") else true)
                invSettingsLoad = Load.READY
                invSettingsForm = null
                say("Invoice Settings saved — new invoices carry them.", Msg.Kind.OK)
                done(true)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(false)
            } finally {
                busy = false
            }
        }
    }

    /** The Make invoice form for a payment: Tax Invoice with GST, the amount including GST, dated the day it was paid. */
    fun draftForPayment(p: Payment): InvoiceDraft {
        val x = customers.find { p.of(it) }
        val party = x?.let { Billing.partyOf(it, p.software, invoices.orEmpty(), invSettings?.settings) }
            ?: org.nexoraofficial.console.data.InvParty(name = p.customer, stateCode = invSettings?.settings?.stateCode.orEmpty())
        val remote = Software.isRemote(p.software)
        return InvoiceDraft(
            mode = "pay", paymentId = p.id, software = p.software,
            companyId = if (remote) null else p.companyId, remoteId = if (remote) p.fabricId else null,
            kind = "INVOICE", gst = true, inclusive = true, date = p.paidOn ?: today().toString(), customer = party, customerKey = x?.key
        )
    }

    /** The Make invoice form for a service with no payment: lines from the customer's plan, amounts before GST. */
    fun draftForService(x: Customer, sw: String): InvoiceDraft {
        val plan = plans?.plan(sw, Customers.planCode(x, sw) ?: if (Software.isRemote(sw)) "STANDARD" else "PRO")
        val sac = invSettings?.settings?.sac?.takeIf { it.isNotBlank() } ?: "997331"
        return InvoiceDraft(
            mode = "svc", software = sw,
            companyId = if (sw == Software.WEIGHT) x.w?.id else null, remoteId = if (sw == Software.WEIGHT) null else x.remote(sw)?.id,
            kind = "INVOICE", gst = true, inclusive = false, date = today().toString(),
            customer = Billing.partyOf(x, sw, invoices.orEmpty(), invSettings?.settings), lines = Billing.planLines(x, sw, plan, sac),
            customerKey = x.key
        )
    }

    private fun putInvoice(inv: Invoice) {
        val list = invoices.orEmpty()
        invoices = if (list.any { it.id == inv.id }) list.map { if (it.id == inv.id) inv else it } else listOf(inv) + list
    }

    /** Make the invoice; the service numbers it and works every figure out. [done] gets it as the service made it. */
    fun createInvoice(d: InvoiceDraft, done: (Invoice?) -> Unit = {}) {
        val body = try { Billing.createBody(d) } catch (e: Refused) { say(e.message ?: "Check the form.", Msg.Kind.ERR); return done(null) }
        viewModelScope.launch {
            busy = true
            try {
                val r = api.invoiceAction(body)
                val inv = r.optJSONObject("invoice")?.let { Invoice.from(it) }
                    ?: throw ApiError("The service made it but sent no invoice back — open Invoices.")
                putInvoice(inv)
                say("Made ${inv.kindWord} ${inv.number} — ₹ ${org.nexoraofficial.console.data.InvFmt.f2(inv.total)}.", Msg.Kind.OK)
                done(inv)
            } catch (e: Exception) {
                val a = e as? ApiError
                /* 409 NOT_SET_UP / NO_GSTIN: Invoice Settings are missing what this invoice needs */
                if (a != null && a.status == 409 && a.code == "NOT_SET_UP") invSettings = invSettings?.copy(ready = false)
                say(a?.message ?: "Something went wrong.", if (a?.status == 409) Msg.Kind.WARN else Msg.Kind.ERR, holdMs = 10_000)
                done(null)
            } finally {
                busy = false
            }
        }
    }

    /** Cancel — never edit or delete: the number stays used and the sheet prints CANCELLED. A reason is required. */
    fun cancelInvoice(inv: Invoice, reason: String, done: (Boolean) -> Unit = {}) {
        val body = try { Billing.cancelBody(inv, reason) } catch (e: Refused) { say(e.message ?: "Write the reason.", Msg.Kind.ERR); return done(false) }
        viewModelScope.launch {
            busy = true
            try {
                val r = api.invoiceAction(body)
                putInvoice(r.optJSONObject("invoice")?.let { Invoice.from(it) } ?: inv.copy(cancelledAt = java.time.Instant.now().toString(), cancelReason = reason.trim()))
                say("${inv.number} cancelled — its number stays used.", Msg.Kind.OK)
                done(true)
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
                done(false)
            } finally {
                busy = false
            }
        }
    }

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
        /* 4.72.0 — audit #41: one plain address or none, as the Email button needs it */
        if (f.email.isNotBlank() && MailAddress.plain(f.email) == null) {
            say("The e-mail must be one plain address, like name@company.com — correct it or leave it empty.", Msg.Kind.ERR)
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

    /* ---- the plan of one company (2.0.0: any active plan the owner made) ---- */

    fun setPlan(id: Int, plan: String) =
        companyAction(Requests.plan(id, plan), okText = "Now on ${planName(Software.WEIGHT, plan)}.")

    /* ---- a message from Nexora into every room (1.5.0) ---- */

    fun loadBroadcasts() {
        viewModelScope.launch {
            try { broadcasts = api.broadcasts() } catch (e: Exception) { /* a service without the route: nothing to list */ }
        }
    }

    fun sendBroadcast() {
        val text = broadcastText.trim()
        val v = broadcastVersion.trim()
        if (text.isEmpty()) return
        val body = if (v.isNotEmpty() && !text.contains(v)) "$text ($v)" else text
        val tags = org.json.JSONArray()
        if (v.isNotEmpty()) tags.put(JSONObject().put("kind", "UPDATE").put("ref", v))
        viewModelScope.launch {
            busy = true
            try {
                val r = api.broadcast(JSONObject().put("action", "send").put("body", body).put("tags", tags))
                val err = r.optString("error")
                if (err.isNotEmpty()) { say(r.optString("message", err), Msg.Kind.ERR); return@launch }
                val rooms = r.optInt("rooms", 0)
                say("Sent to $rooms " + (if (rooms == 1) "room." else "rooms."), Msg.Kind.OK)
                broadcastText = ""
                broadcastVersion = ""
                loadBroadcasts()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

    fun withdrawBroadcast(body: String) {
        viewModelScope.launch {
            busy = true
            try {
                val r = api.broadcast(JSONObject().put("action", "withdraw").put("body", body))
                val rooms = r.optInt("rooms", 0)
                say("Withdrawn from $rooms " + (if (rooms == 1) "room." else "rooms."), Msg.Kind.OK)
                loadBroadcasts()
            } catch (e: Exception) {
                say((e as? ApiError)?.message ?: "Something went wrong.", Msg.Kind.ERR)
            } finally {
                busy = false
            }
        }
    }

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
