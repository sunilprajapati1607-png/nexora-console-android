package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.BySoftware
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.CustQuick
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.FabricCompany
import org.nexoraofficial.console.data.FabricEdit
import org.nexoraofficial.console.data.FeatureDef
import org.nexoraofficial.console.data.FeatureTag
import org.nexoraofficial.console.data.Features
import org.nexoraofficial.console.data.Money
import org.nexoraofficial.console.data.NewCustomerForm
import org.nexoraofficial.console.data.PayFilter
import org.nexoraofficial.console.data.PayQuick
import org.nexoraofficial.console.data.PaymentForm
import org.nexoraofficial.console.data.PaymentsData
import org.nexoraofficial.console.data.PlanForm
import org.nexoraofficial.console.data.Plans
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.Requests
import org.nexoraofficial.console.data.ServiceSettings
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.SwQuick
import org.nexoraofficial.console.data.ValQuick
import org.nexoraofficial.console.data.Validity
import org.nexoraofficial.console.data.WatchSource
import org.nexoraofficial.console.data.WeightEdit
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.0.0 — CONSOLE 2.0: what the new screens read and send.
 *
 * The plans of every software (null prices open, Standard and Pro built in,
 * Fabric Stock not supported yet), the payments ledger, a customer's own
 * features over its plan (plan × override × demo), the quick views of the
 * validity and payments lists (the financial year from 1 April), and the
 * exact bodies sent for features, plan, payments and plans.
 *
 * Made-up plants; nothing reaches the live service (a one-page server on this
 * computer, or no server at all).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Console200Test {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var server: TinyServer? = null

    @Before
    fun clean() {
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
    }

    @After
    fun stop() {
        server?.close()
    }

    /* ---------------------------------------------------------------- the plans */

    private val plansJson = JSONObject("""{"software":[
      {"id":"weight","name":"Nexora Bag Weight Calculation","short":"Sales & Costing","ok":true,"supported":true,
       "features":[{"id":"quotation","label":"Quotation","group":"Sales"},{"id":"exportPdf","label":"Export to PDF","group":"Output"},
                   {"id":"bagView","label":"3D bag view","group":"Calculation"},{"id":"chat","label":"Company conversation (chat)"}],
       "plans":[
         {"code":"PRO","name":"Pro","note":"everything","priceFirst":35000,"priceRenewal":18000,"usersIncluded":3,"extraUserPrice":2000,
          "features":{"quotation":true,"exportPdf":true,"bagView":true,"chat":true},"active":true,"sort":20,"customers":2,"changed":0,"builtIn":true},
         {"code":"STANDARD","name":"Standard","note":"calculation and costing","priceFirst":null,"priceRenewal":null,"usersIncluded":null,"extraUserPrice":null,
          "features":{"quotation":false,"exportPdf":true,"bagView":false,"chat":false},"active":true,"sort":10,"createdAt":"2026-10-08T05:00:00Z",
          "customers":4,"changed":1,"builtIn":true},
         {"code":"GOLD","name":"Gold","priceFirst":"25,000","priceRenewal":12000.5,"usersIncluded":2,
          "features":{"quotation":true,"exportPdf":true},"active":false,"sort":30,"customers":0,"changed":0,"builtIn":false}],
       "demo":"A demo has every feature, whatever its plan."},
      {"id":"fabric","short":"Fabric Stock","ok":true,"supported":false,
       "message":"Fabric Stock’s own service has no plans yet …","features":[],"plans":[]}]}""")

    @Test
    fun thePlansAreReadWithOpenPricesAndBuiltInPlans() {
        val p = PlansData.from(plansJson)
        val w = p.weight!!
        assertTrue(w.supported)
        assertEquals("sorted as the service sorts them", listOf("STANDARD", "PRO", "GOLD"), w.plans.map { it.code })
        val std = w.plan("standard")!!
        assertNull("a null price is open", std.priceFirst)
        assertNull(std.priceRenewal)
        assertNull(std.usersIncluded)
        assertNull(std.extraUserPrice)
        assertTrue(std.builtIn)
        assertFalse("built in: retired, never deleted", std.canDelete)
        assertEquals(4, std.customers)
        assertEquals(1, std.changed)
        assertEquals(1, std.onCount(w.features))
        val gold = w.plan("GOLD")!!
        assertEquals("a price written with a comma is still the price", 25000.0, gold.priceFirst!!, 0.0)
        assertEquals(12000.5, gold.priceRenewal!!, 0.0)
        assertFalse(gold.active)
        assertTrue("not built in and nobody on it", gold.canDelete)
        assertEquals("the plans offered to a new customer", listOf("STANDARD", "PRO"), w.livePlans.map { it.code })
        assertEquals("a feature with no group falls back to the console's own", "Company", w.features.first { it.id == "chat" }.group)
        assertEquals(listOf("Calculation", "Sales", "Output", "Company"), w.groups)

        val f = p.fabric!!
        assertFalse(f.supported)
        assertTrue(f.plans.isEmpty())
        assertEquals("Fabric Stock’s own service has no plans yet …", f.problem)
        assertTrue("an unsupported software has no plans to list", p.plansOf(Software.FABRIC).isEmpty())
        assertEquals(listOf("STANDARD", "PRO", "GOLD"), p.all.map { it.second.code })
    }

    @Test
    fun aPlanIsNamedByItsCodeWhenItIsNotInHand() {
        val p = PlansData.from(plansJson)
        assertEquals("Standard", Plans.nameOf(p, Software.WEIGHT, "STANDARD"))
        assertEquals("Gold plus", Plans.nameOf(p, Software.WEIGHT, "GOLD_PLUS"))
        assertEquals("Sales & Costing's default is Pro", "Pro", Plans.nameOf(null, Software.WEIGHT, null))
        assertEquals("Fabric Stock's default is Standard", "Standard", Plans.nameOf(p, Software.FABRIC, null))
        /* an older service: Standard / Pro ticks from the settings */
        val older = ServiceSettings.from(JSONObject("""{"planFeatures":{"STANDARD":{"exportPdf":true},"PRO":{"exportPdf":true,"quotation":true}}}"""))
        assertEquals(true, Plans.ticks(null, older, "PRO")["quotation"])
        assertEquals(false, Plans.ticks(null, older, "STANDARD")["quotation"])
        assertEquals("the console's own catalogue without the service's", 21, Plans.catalogue(null, Software.WEIGHT).size)
    }

    @Test
    fun moneyIsWrittenTheIndianWay() {
        assertEquals("₹1,66,000", Money.rupees(166000.0))
        assertEquals("₹15,000", Money.rupees(15000.0))
        assertEquals("₹999", Money.rupees(999.0))
        assertEquals("₹1,00,00,000", Money.rupees(10000000.0))
        assertEquals("₹1,500.5", Money.rupees(1500.5))
        assertEquals("₹12,000.25", Money.rupees(12000.25))
        assertEquals("—", Money.rupees(null))
        assertEquals("open", Money.price(null))
        assertEquals(15000.0, Money.parse("15,000")!!, 0.0)
        assertEquals(15000.5, Money.parse("₹ 15000.50")!!, 0.0)
        assertNull(Money.parse("  "))
    }

    /* ---------------------------------------------------------------- the payments */

    private val paymentsJson = JSONObject("""{"payments":[
      {"id":3,"software":"fabric","companyId":"7","fabricId":"2","customer":"Vijay Woven Bags","plan":"STANDARD","planName":"Standard","kind":"RENEWAL",
       "amount":25000,"paidOn":"2026-10-08","mode":"UPI","reference":"UTR 7788","validFrom":"2026-10-08","validTo":"2027-10-29","note":null,
       "createdAt":"2026-10-08T06:00:00Z","via":"android"},
      {"id":2,"software":"weight","companyId":"7","fabricId":null,"customer":"Vijay Woven Bags","plan":"PRO","planName":"Pro","kind":"NEW",
       "amount":35000,"paidOn":"2026-04-01","mode":"CHEQUE","reference":null,"validFrom":null,"validTo":"2027-04-01","note":"first year","via":"web"},
      {"id":1,"software":"weight","companyId":"3","fabricId":null,"customer":"Riverside Sacks Pvt Ltd","plan":"STANDARD","planName":"Standard",
       "kind":"EXTRA_USERS","amount":3000,"paidOn":"2026-03-31","mode":"BANK","reference":"NEFT 1102","validFrom":null,"validTo":null,"via":null}],
     "totals":{"count":3,"amount":63000,"bySoftware":{"weight":38000,"fabric":25000}}}""")

    @Test
    fun thePaymentsAreRead() {
        val d = PaymentsData.from(paymentsJson)
        assertEquals(3, d.payments.size)
        val p = d.byId(3)!!
        assertEquals(Software.FABRIC, p.software)
        assertEquals(7, p.companyId)
        assertEquals(2, p.fabricId)
        assertEquals(25000.0, p.amount!!, 0.0)
        assertEquals("2026-10-08", p.paidOn)
        assertEquals("Renewal", p.kindText)
        assertEquals("UPI", p.modeText)
        assertEquals("phone console", p.viaText)
        assertEquals("Fabric Stock", p.softwareName)
        assertTrue(p.validityText.contains("→"))
        assertEquals("Extra users", d.byId(1)!!.kindText)
        assertEquals("Bank transfer", d.byId(1)!!.modeText)
        assertEquals("—", d.byId(1)!!.validityText)
        assertNull(d.byId(1)!!.viaText)
        assertTrue(d.byId(2)!!.validityText.startsWith("to "))
        assertEquals(63000.0, d.totals.amount, 0.0)
        assertEquals(25000.0, d.totals.bySoftware.getValue("fabric"), 0.0)
        assertEquals(3, d.totals.count)
    }

    @Test
    fun theFinancialYearStartsOnTheFirstOfApril() {
        assertEquals(LocalDate.of(2026, 4, 1), PayFilter.fyStart(LocalDate.of(2026, 10, 8)))
        assertEquals(LocalDate.of(2026, 4, 1), PayFilter.fyStart(LocalDate.of(2026, 4, 1)))
        assertEquals("before April it is last year's", LocalDate.of(2025, 4, 1), PayFilter.fyStart(LocalDate.of(2026, 3, 31)))
        assertEquals(LocalDate.of(2025, 4, 1), PayFilter.fyStart(LocalDate.of(2026, 1, 15)))

        val today = LocalDate.of(2026, 10, 8)
        assertEquals("2026-04-01" to "2026-10-08", PayFilter.range(PayQuick.FY, today))
        assertEquals("2026-10-01" to "2026-10-08", PayFilter.range(PayQuick.MONTH, today))
        assertEquals("2026-09-08" to "2026-10-08", PayFilter.range(PayQuick.LAST30, today))
        assertEquals("2026-10-08" to "2026-10-08", PayFilter.range(PayQuick.TODAY, today))
        assertEquals(null to null, PayFilter.range(PayQuick.ALL, today))

        val all = PaymentsData.from(paymentsJson).payments
        assertEquals("31 March is last year's", listOf(3, 2), PayFilter.apply(all, PayQuick.FY, today).map { it.id })
        assertEquals(listOf(3), PayFilter.apply(all, PayQuick.TODAY, today).map { it.id })
        assertEquals(listOf(3, 2, 1), PayFilter.apply(all, PayQuick.ALL, today).map { it.id })
        assertEquals(listOf(2, 1), PayFilter.apply(all, PayQuick.ALL, today, software = Software.WEIGHT).map { it.id })
        assertEquals(listOf(2), PayFilter.apply(all, PayQuick.ALL, today, kind = "NEW").map { it.id })
        assertEquals(listOf(1), PayFilter.apply(all, PayQuick.ALL, today, term = "neft").map { it.id })
        assertEquals(63000.0, PayFilter.sum(all), 0.0)
    }

    /* ---------------------------------------------------------------- features over the plan */

    @Test
    fun aFeatureIsThePlansTickUnlessTheCustomerHasItsOwnAndADemoHasEverything() {
        /* plan × override × demo */
        for (plan in listOf(true, false)) for (own in listOf<Boolean?>(null, true, false)) {
            assertTrue("a demo has every feature (plan $plan, own $own)", Features.effective(plan, own, demo = true))
            assertEquals("plan $plan, own $own", own ?: plan, Features.effective(plan, own, demo = false))
        }
        assertEquals(FeatureTag.PLAN, Features.tag(true, null))
        assertEquals(FeatureTag.NONE, Features.tag(false, null))
        assertEquals(FeatureTag.ADDED, Features.tag(false, true))
        assertEquals(FeatureTag.OFF, Features.tag(true, false))
        assertEquals("an override that agrees with the plan reads as the plan", FeatureTag.PLAN, Features.tag(true, true))
        assertEquals(FeatureTag.NONE, Features.tag(false, false))
        assertEquals("+ added", FeatureTag.ADDED.word)
        assertEquals("− off", FeatureTag.OFF.word)
    }

    private val catalogue = listOf(
        FeatureDef("quotation", "Quotation", "Sales"), FeatureDef("exportPdf", "Export to PDF", "Output"),
        FeatureDef("bagView", "3D bag view", "Calculation"), FeatureDef("chat", "Chat", "Company")
    )
    private val standard = mapOf("quotation" to false, "exportPdf" to true, "bagView" to true, "chat" to false)

    @Test
    fun theSummaryCountsFromThePlanAddedOffAndOn() {
        val own = mapOf("quotation" to true, "exportPdf" to false)
        val s = Features.summary(catalogue, standard, own)
        assertEquals(2, s.fromPlan)
        assertEquals(1, s.added)
        assertEquals(1, s.off)
        assertEquals("bagView from the plan, quotation added", 2, s.on)
        assertEquals(4, s.total)
    }

    @Test
    fun aTapTurnsAFeatureAndTheDraftKeepsOnlyWhatDiffersFromThePlan() {
        val own = mapOf("quotation" to true)
        var draft: Map<String, Boolean?> = emptyMap()
        draft = Features.toggle("chat", standard, own, draft)
        assertEquals(mapOf<String, Boolean?>("chat" to true), draft)
        draft = Features.toggle("exportPdf", standard, own, draft)
        assertEquals(false, draft["exportPdf"])
        /* taking the added quotation off again goes back to the plan: null */
        draft = Features.toggle("quotation", standard, own, draft)
        assertTrue(draft.containsKey("quotation"))
        assertNull(draft["quotation"])
        assertEquals(mapOf("chat" to true, "exportPdf" to false), Features.merge(own, draft))
        /* what is sent: only what changes the customer's own list */
        assertEquals(mapOf<String, Boolean?>("chat" to true, "exportPdf" to false, "quotation" to null), Features.changes(own, draft))
        assertEquals("a null that removes nothing is not sent", emptyMap<String, Boolean?>(), Features.changes(emptyMap(), mapOf("chat" to null)))
        assertEquals(catalogue.map { it.id }.toSet(), Features.resetAll(catalogue).keys)
        assertTrue(Features.resetAll(catalogue).values.all { it == null })
    }

    @Test
    fun aCompanysOwnFeaturesAreRead() {
        val c = Company.from(JSONObject("""{"id":3,"name":"Riverside","feature_overrides":{"quotation":true,"exportPdf":false,"junk":"x"},"plan":"gold"}"""))
        assertEquals(mapOf("quotation" to true, "exportPdf" to false), c.featureOverrides)
        assertEquals("GOLD", c.plan)
        assertEquals(mapOf("chat" to true), Company.from(JSONObject("""{"id":4,"name":"x","feature_overrides":"{\"chat\":true}"}""")).featureOverrides)
        assertTrue(Company.from(JSONObject("""{"id":5,"name":"y","feature_overrides":null}""")).featureOverrides.isEmpty())
        assertEquals("DEMO", Company.from(JSONObject("""{"id":6,"name":"d","state":"DEMO","is_demo":true}""")).swState)
        assertEquals("EXPIRED", Company.from(JSONObject("""{"id":7,"name":"e","state":"LICENSED","expired":true}""")).swState)
        assertEquals("SUSPENDED", Company.from(JSONObject("""{"id":8,"name":"s","state":"SUSPENDED","expired":true}""")).swState)
    }

    /* ---------------------------------------------------------------- customers, validity, by software */

    private fun co(id: Int, name: String, state: String, days: Int, demo: Boolean = false, gstin: String? = null, self: Boolean = false,
                   expires: String = "2027-01-01T18:29:59Z", plan: String = "PRO") =
        JSONObject().put("id", id).put("name", name).put("licence_key", "NX-$id").put("state", state).put("days_left", days)
            .put("is_demo", demo).put("expires_at", expires).put("gstin", gstin ?: JSONObject.NULL).put("self_registered", self)
            .put("plan", plan).put("seats", 3)

    private fun fab(id: Int, name: String, state: String, days: Int, companyId: Int?, expires: String) = JSONObject()
        .put("id", id).put("name", name).put("licenceKey", "NFS-$id").put("state", state).put("shownState", state)
        .put("isDemo", state == "DEMO").put("daysLeft", days).put("expiresAt", expires).put("expired", false)
        .put("companyId", companyId?.toString() ?: JSONObject.NULL).put("linkedBy", if (companyId != null) "gstin" else JSONObject.NULL)
        .put("seats", 3).put("plan", "STANDARD")

    private val weight = ConsoleData.from(JSONObject().put("companies", JSONArray()
        .put(co(3, "Riverside Sacks", "LICENSED", 245, gstin = "24ABCDE1234F1Z5", expires = "2027-06-10T18:29:59Z", plan = "STANDARD"))
        .put(co(7, "Vijay Woven Bags", "LICENSED", 21, expires = "2026-10-29T18:29:59Z"))
        .put(co(12, "Kaveri Weaves", "SUSPENDED", 90, expires = "2027-01-06T18:29:59Z"))
        .put(co(14, "Om Poly Packs", "DEMO", 5, demo = true, self = true, expires = "2026-10-13T18:29:59Z")))).companies
    private val fabric = listOf(
        FabricCompany.from(fab(1, "Riverside Sacks", "DEMO", 5, 3, "2026-10-13T18:29:59Z")),
        FabricCompany.from(fab(4, "Shree Loom Works", "LICENSED", 120, null, "2027-02-05T18:29:59Z")),
        FabricCompany.from(fab(5, "Mahalaxmi Tex", "EXPIRED", 0, null, "2026-09-30T18:29:59Z"))
    )

    @Test
    fun eachCustomerOnceWithEverySoftware() {
        val all = Customers.of(weight, fabric)
        assertEquals(listOf("w3", "w7", "w12", "w14", "f4", "f5"), all.map { it.key })
        val riverside = all.first { it.key == "w3" }
        assertTrue(riverside.both)
        assertEquals("the Fabric Stock demo ends first", Software.FABRIC, riverside.next!!.sw)
        assertTrue(riverside.soon)
        assertFalse("a suspended licence is not 'next'", all.first { it.key == "w12" }.next != null)
        assertEquals(1, Customers.count(all, CustQuick.BOTH))
        assertEquals(1, Customers.count(all, CustQuick.SUSP))
        assertEquals(1, Customers.count(all, CustQuick.SELF))
        assertEquals("a demo on either software", 2, Customers.count(all, CustQuick.DEMO))
        assertEquals("Vijay (21 days), Riverside (Fabric demo), Om Poly Packs (demo)", 3, Customers.count(all, CustQuick.SOON))
        /* f5 ended 30 Sep; Om Poly Packs and Riverside (its Fabric Stock demo) both end 13 Oct — by name; Kaveri (suspended) last */
        assertEquals("the one renewing first at the top", listOf("f5", "w14", "w3", "w7", "f4", "w12"),
            Customers.filter(all, CustQuick.ALL, "").map { it.key })
        assertEquals(listOf("w3"), Customers.filter(all, CustQuick.ALL, "nfs-1").map { it.key })
        /* a Fabric Stock customer that has been given Sales & Costing keeps being found by either key */
        assertEquals("w3", Customers.find(all, "f1")!!.key)
        assertEquals("w7", Customers.find(all, "w7")!!.key)
        assertNull(Customers.find(all, "w99"))
        assertEquals("Sales & Costing · Standard · 245 days", Customers.line(riverside, Software.WEIGHT, PlansData.from(plansJson)))
        assertEquals("Fabric Stock · Standard · demo · 5 days", Customers.line(riverside, Software.FABRIC, null))
    }

    @Test
    fun theValidityListsEveryLicenceEndingFirst() {
        val all = Customers.of(weight, fabric)
        val pays = PaymentsData.from(paymentsJson)
        val rows = Validity.rows(all, PlansData.from(plansJson), pays)
        assertEquals("one row per software per customer", 7, rows.size)
        assertEquals("Mahalaxmi Tex", rows.first().c.name)
        assertEquals("the last payment of that software", 35000.0, rows.first { it.c.key == "w7" && it.sw == Software.WEIGHT }.pay!!.amount!!, 0.0)
        assertEquals(3000.0, rows.first { it.c.key == "w3" && it.sw == Software.WEIGHT }.pay!!.amount!!, 0.0)
        assertNull("none on its Fabric Stock licence", rows.first { it.c.key == "w3" && it.sw == Software.FABRIC }.pay)
        assertNull(rows.first { it.c.key == "f4" }.pay)
        assertEquals(3, Validity.filter(rows, ValQuick.D30).size)
        assertEquals("the two demos (5 days)", 2, Validity.filter(rows, ValQuick.D7).size)
        assertEquals(1, Validity.filter(rows, ValQuick.ENDED).size)
        assertEquals(2, Validity.filter(rows, ValQuick.DEMO).size)
        assertEquals(1, Validity.filter(rows, ValQuick.SUSP).size)
        assertEquals(3, Validity.filter(rows, ValQuick.ALL, sw = Software.FABRIC).size)
        assertEquals(2, Validity.filter(rows, ValQuick.ALL, term = "riverside").size)
    }

    @Test
    fun oneSoftwareOnItsOwn() {
        val all = Customers.of(weight, fabric)
        val f = BySoftware.customers(all, Software.FABRIC)
        assertEquals(listOf("f5", "w3", "f4"), f.map { it.key })
        assertEquals(listOf("w3"), BySoftware.filter(f, Software.FABRIC, SwQuick.BOTH, "").map { it.key })
        assertEquals(listOf("f5"), BySoftware.filter(f, Software.FABRIC, SwQuick.ENDED, "").map { it.key })
        assertEquals("Also on Sales & Costing", BySoftware.label(SwQuick.BOTH, Software.FABRIC))
        assertEquals(4, BySoftware.customers(all, Software.WEIGHT).size)
    }

    /* ---------------------------------------------------------------- what is sent */

    @Test
    fun theFeaturesAndPlanBodies() {
        val b = Requests.features(3, mapOf("quotation" to true, "exportPdf" to false, "bagView" to null))
        assertEquals(3, b.getInt("id"))
        assertEquals("features", b.getString("action"))
        val o = b.getJSONObject("overrides")
        assertEquals(true, o.getBoolean("quotation"))
        assertEquals(false, o.getBoolean("exportPdf"))
        assertTrue("null takes it back to the plan", o.isNull("bagView") && o.has("bagView"))
        assertEquals(JSONObject().put("id", 3).put("action", "features").put("reset", true).toString(), Requests.featuresReset(3).toString())
        assertEquals(JSONObject().put("id", 3).put("action", "plan").put("plan", "GOLD").toString(), Requests.plan(3, "gold").toString())
    }

    @Test
    fun saveSendsOneActionPerChangeInTheWebConsolesOrder() {
        val c = Company.from(JSONObject("""{"id":3,"name":"Riverside","plan":"STANDARD","seats":5,"grace_days":3,"ai_daily_limit":40,"txn_limit":0,
            "gstin":"24ABCDE1234F1Z5","notes":"old","feature_overrides":{"quotation":true}}"""))
        assertTrue("nothing changed, nothing sent", Requests.weightSteps(c, WeightEdit.of(c)).isEmpty())
        val e = WeightEdit.of(c).copy(name = "Riverside Sacks", plan = "GOLD", seats = "7", graceDays = "3", aiDaily = "0", txnLimit = "500",
            features = mapOf("quotation" to null, "chat" to true))
        val steps = Requests.weightSteps(c, e)
        assertEquals(listOf("rename", "plan", "seats", "ailimit", "txnlimit", "features"), steps.map { it.getString("action") })
        assertEquals("Riverside Sacks", steps[0].getString("name"))
        assertEquals("GOLD", steps[1].getString("plan"))
        assertEquals(7, steps[2].getInt("seats"))
        assertEquals(0, steps[3].getInt("aiDailyLimit"))
        assertEquals(500, steps[4].getInt("txnLimit"))
        val ov = steps[5].getJSONObject("overrides")
        assertTrue(ov.isNull("quotation"))
        assertTrue(ov.getBoolean("chat"))
        assertTrue(steps.all { it.getInt("id") == 3 })
    }

    @Test
    fun aFabricStockSaveIsOneUpdate() {
        val f = FabricCompany.from(fab(4, "Shree Loom Works", "LICENSED", 120, null, "2027-02-05T18:29:59Z"))
        assertNull("nothing changed", Requests.fabricUpdate(f, FabricEdit.of(f)))
        val b = Requests.fabricUpdate(f, FabricEdit.of(f).copy(seats = "6", graceDays = "5", email = " office@shree.example ", gstin = "24shree1111l1z3"))!!
        assertEquals("update", b.getString("action"))
        assertEquals(4, b.getInt("id"))
        assertEquals(6, b.getInt("seats"))
        assertEquals(5, b.getInt("graceDays"))
        assertEquals("office@shree.example", b.getString("email"))
        assertEquals("24SHREE1111L1Z3", b.getString("gstin"))
        assertFalse(b.has("name"))
    }

    @Test
    fun thePlanBodies() {
        val made = Requests.planSave(Software.WEIGHT, null, PlanForm(name = " Gold ", note = "the cost tools", priceFirst = "25000", priceRenewal = "",
            usersIncluded = "2", features = mapOf("quotation" to true, "chat" to false), copyFrom = "STANDARD"))
        assertEquals("weight", made.getString("software"))
        assertEquals("create", made.getString("action"))
        assertEquals("Gold", made.getString("name"))
        assertEquals("25000", made.getString("priceFirst"))
        assertEquals("an empty price is sent empty: open", "", made.getString("priceRenewal"))
        assertEquals("", made.getString("extraUserPrice"))
        assertEquals("STANDARD", made.getString("copyFrom"))
        assertFalse(made.has("code"))
        assertTrue(made.getJSONObject("features").getBoolean("quotation"))

        val gold = PlansData.from(plansJson).weight!!.plan("GOLD")!!
        val upd = Requests.planSave(Software.WEIGHT, gold, PlanForm.of(gold))
        assertEquals("update", upd.getString("action"))
        assertEquals("GOLD", upd.getString("code"))
        assertEquals("25000", upd.getString("priceFirst"))
        assertEquals("12000.5", upd.getString("priceRenewal"))
        assertFalse(upd.has("copyFrom"))
        assertEquals("Gold copy", PlanForm.copyOf(gold).name)
        assertEquals("GOLD", PlanForm.copyOf(gold).copyFrom)

        assertEquals(JSONObject().put("software", "fabric").put("action", "retire").put("code", "STANDARD").toString(),
            Requests.planAction(Software.FABRIC, "retire", "STANDARD").toString())
    }

    @Test
    fun thePaymentBodies() {
        val all = Customers.of(weight, fabric)
        val riverside = all.first { it.key == "w3" }
        /* renewing with it: no validity by hand */
        val renew = Requests.paymentAdd(PaymentForm(customerKey = "w3", software = Software.FABRIC, amount = "15,000", paidOn = "2026-10-08",
            mode = "UPI", reference = " UTR 1 ", extendDays = 365, validFrom = "2026-01-01", validTo = "2026-12-31"), riverside)
        assertEquals("add", renew.getString("action"))
        assertEquals("fabric", renew.getString("software"))
        assertEquals("the Sales & Costing company goes with a Fabric Stock payment too", 3, renew.getInt("companyId"))
        assertEquals(1, renew.getInt("fabricId"))
        assertEquals(15000L, renew.getLong("amount"))
        assertEquals("15000", renew.get("amount").toString())
        assertEquals(365, renew.getInt("extendDays"))
        assertEquals("UTR 1", renew.getString("reference"))
        assertFalse(renew.has("validFrom"))
        assertFalse(renew.has("validTo"))
        assertEquals("RENEWAL", renew.getString("kind"))

        /* only recording it: the validity typed by hand, no fabricId on a Sales & Costing payment */
        val plain = Requests.paymentAdd(PaymentForm(customerKey = "w3", software = Software.WEIGHT, amount = "3000", extendDays = 0,
            validTo = "2027-06-10", kind = "EXTRA_USERS"), riverside)
        assertFalse(plain.has("extendDays"))
        assertFalse(plain.has("fabricId"))
        assertFalse(plain.has("validFrom"))
        assertEquals("2027-06-10", plain.getString("validTo"))
        assertFalse("no date: the service takes today", plain.has("paidOn"))
        assertFalse(plain.has("reference"))

        /* a Fabric Stock customer alone: no Sales & Costing company to send */
        val shree = all.first { it.key == "f4" }
        val f = Requests.paymentAdd(PaymentForm(customerKey = "f4", software = Software.FABRIC, amount = "25000"), shree)
        assertFalse(f.has("companyId"))
        assertEquals(4, f.getInt("fabricId"))

        val upd = Requests.paymentUpdate(PaymentForm(id = 9, amount = "26,500.50", paidOn = "2026-10-01", mode = "BANK", kind = "RENEWAL",
            reference = "", validFrom = "", validTo = "2027-10-01", note = "corrected"))
        assertEquals("update", upd.getString("action"))
        assertEquals(9, upd.getInt("id"))
        assertEquals(26500.5, upd.getDouble("amount"), 0.0)
        assertEquals("an emptied field is cleared", "", upd.getString("reference"))
        assertEquals("", upd.getString("validFrom"))
        assertEquals(JSONObject().put("action", "delete").put("id", 9).toString(), Requests.paymentDelete(9).toString())
    }

    @Test
    fun theNewCustomerBodies() {
        val f = NewCustomerForm(name = " Sunrise Polysacks ", gstin = "24sunrs4455q1z9", email = "a@sunrise.example", phone = "+91 90000 22222",
            admin = "Tasmi", pin = "4821", weightPlan = "GOLD", weightDays = "365", weightSeats = "2", weightGrace = "3",
            fabric = true, fabricState = "DEMO", fabricDays = "7", fabricSeats = "3", fabricGrace = "0")
        val w = Requests.newWeight(f)
        assertEquals("create", w.getString("action"))
        assertEquals("Sunrise Polysacks", w.getString("name"))
        assertEquals("24SUNRS4455Q1Z9", w.getString("gstin"))
        assertEquals("GOLD", w.getString("plan"))
        assertEquals(2, w.getInt("seats"))
        assertEquals("+91 90000 22222", w.getString("phone"))
        val fb = Requests.newFabric(f, 31)
        assertEquals(31, fb.getInt("linkTo"))
        assertEquals("DEMO", fb.getString("state"))
        assertEquals("Tasmi", fb.getString("adminName"))
        assertEquals("4821", fb.getString("adminPin"))
        assertFalse("Fabric Stock has no plans of its own yet", fb.has("plan"))
        assertFalse("no Sales & Costing made with it, nothing to link", Requests.newFabric(f, null).has("linkTo"))
        val a = Requests.newAdmin(31, f)!!
        assertEquals("adminuser", a.getString("action"))
        assertNull("no administrator named, none made", Requests.newAdmin(31, f.copy(admin = "")))
    }

    @Test
    fun theWatchNeverAsksForPlansOrPayments() {
        /* audit 43: the quarter-hourly watch fetches no whole lists — WatchSource has no way to ask for them */
        val names = WatchSource::class.java.methods.map { it.name }.toSet()
        assertFalse(names.any { it.startsWith("plans") || it.startsWith("payments") || it.startsWith("paymentAction") || it.startsWith("planAction") })
    }

    /* ---------------------------------------------------------------- the view model, against a one-page server */

    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (ok()) return
            if (System.currentTimeMillis() > end) fail("timed out: $what")
            Thread.sleep(10)
        }
    }

    private val listing = JSONObject().put("settings", JSONObject()).put("licences", JSONArray())
        .put("companies", JSONArray().put(co(3, "Riverside Sacks", "LICENSED", 245, gstin = "24ABCDE1234F1Z5", plan = "STANDARD")))

    @Test
    fun aServiceWithoutPlansOrPaymentsIsQuietAndSaysItNeedsTheUpdate() {
        val srv = TinyServer { target, _ ->
            when (target.substringBefore('?')) {
                "/admin/api/licences" -> 200 to listing.toString()
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply { baseUrl = srv.base; key = "NX-TEST-KEY" }
        vm.load()
        until("read") { vm.signedIn && vm.plansLoad == Load.FAILED && vm.paymentsLoad == Load.FAILED }
        assertEquals("This service has no plans per software yet — it needs the service update.", vm.plansError)
        assertEquals("This service keeps no payments yet — it needs the service update.", vm.paymentsError)
        assertNull("quiet: no red strip", vm.lastSaid)
        assertEquals("the customers are there regardless", 1, vm.customers.size)
        assertEquals("Pro", vm.planName(Software.WEIGHT, "PRO"))
    }

    @Test
    fun recordingAPaymentSendsItAndReadsThePaymentsAgain() {
        val asked = CopyOnWriteArrayList<String>()
        var sent: JSONObject? = null
        val srv = TinyServer { target, headers ->
            val path = target.substringBefore('?')
            asked += path
            when (path) {
                "/admin/api/payments" ->
                    if ((headers["content-length"]?.toIntOrNull() ?: 0) > 0) {
                        sent = JSONObject(server?.lastBody ?: "{}")
                        200 to """{"ok":true,"payment":{"id":8,"software":"weight","companyId":"3","customer":"Riverside Sacks","amount":9000,
                            "paidOn":"2026-10-08","kind":"RENEWAL","mode":"UPI","validTo":"2027-06-10"}}"""
                    } else 200 to paymentsJson.toString()
                "/admin/api/licences" -> 200 to listing.toString()
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base
            key = "NX-TEST-KEY"
            signedIn = true
            data = ConsoleData.from(listing)
            today = { LocalDate.of(2026, 10, 8) }
        }
        val form = vm.newPaymentForm("w3", null)
        assertEquals("w3", form.customerKey)
        assertEquals(Software.WEIGHT, form.software)
        assertEquals("2026-10-08", form.paidOn)
        assertEquals("renews a year unless told", 365, form.extendDays)

        /* refused before anything is sent: no amount */
        vm.recordPayment(form.copy(amount = ""))
        assertEquals("Enter the amount received.", vm.lastSaid?.text)
        assertTrue(asked.isEmpty())

        var done: org.nexoraofficial.console.data.Payment? = null
        vm.recordPayment(form.copy(amount = "9,000", extendDays = 0, validTo = "2027-06-10")) { done = it }
        until("recorded and read again") { done != null && vm.paymentsLoad == Load.READY }
        val s = sent!!
        assertEquals("add", s.getString("action"))
        assertEquals(3, s.getInt("companyId"))
        assertEquals(9000L, s.getLong("amount"))
        assertFalse(s.has("extendDays"))
        assertEquals("2027-06-10", s.getString("validTo"))
        assertEquals(Msg.Kind.OK, vm.lastSaid?.kind)
        assertTrue(vm.lastSaid!!.text.startsWith("Recorded ₹9,000 from Riverside Sacks"))
        assertEquals(3, vm.payments!!.payments.size)
        assertFalse("not renewing: the licences are not read again", asked.contains("/admin/api/licences"))
    }

    @Test
    fun saveSendsEachChangeThenReadsTheListAgain() {
        val sent = CopyOnWriteArrayList<JSONObject>()
        val srv = TinyServer { target, headers ->
            when (target.substringBefore('?')) {
                "/admin/api/company" -> {
                    val b = JSONObject(server?.lastBody ?: "{}")
                    sent += b
                    if (b.optString("action") == "plan") 200 to """{"error":"There is no plan GONE, or it has been retired. Choose one under Software & plans."}"""
                    else 200 to """{"ok":true}"""
                }
                "/admin/api/licences" -> 200 to listing.toString()
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply { baseUrl = srv.base; key = "NX-TEST-KEY"; signedIn = true; data = ConsoleData.from(listing) }
        val c = vm.data.companies.first()
        var ok: Boolean? = null
        vm.saveWeight(c, WeightEdit.of(c).copy(seats = "9", plan = "GONE", features = mapOf("chat" to true))) { ok = it }
        until("saved") { ok != null }
        assertEquals(listOf("plan", "seats", "features"), sent.map { it.getString("action") })
        assertEquals("a refused step keeps the window in Edit", false, ok)
        assertEquals(Msg.Kind.ERR, vm.lastSaid?.kind)
        assertTrue(vm.lastSaid!!.text.startsWith("There is no plan GONE"))
    }

    @Test
    fun aFabricStockPlanIsRefusedInItsServicesWords() {
        val srv = TinyServer { target, _ ->
            if (target.substringBefore('?') == "/admin/api/plans")
                501 to """{"error":"NOT_YET","message":"Fabric Stock’s own service has no plans yet."}"""
            else 404 to """{"error":"NOT_FOUND"}"""
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply { baseUrl = srv.base; key = "NX-TEST-KEY"; signedIn = true }
        var done = false
        var plan: org.nexoraofficial.console.data.Plan? = null
        vm.savePlan(Software.FABRIC, null, PlanForm(name = "Basic")) { plan = it; done = true }
        until("refused") { done }
        assertNull(plan)
        assertEquals("Fabric Stock’s own service has no plans yet.", vm.lastSaid?.text)
        assertEquals(Msg.Kind.ERR, vm.lastSaid?.kind)
        /* and a plan with no name is refused before anything is sent */
        vm.savePlan(Software.WEIGHT, null, PlanForm(name = " "))
        assertEquals("Give the plan a name.", vm.lastSaid?.text)
        assertNotNull(srv)
    }

    @Test
    fun theProductsAreStillReadAsBefore() {
        /* the Fabric Stock companies the customers are made of come from the same products listing as 1.9.0 */
        val p = ProductsData.from(JSONObject().put("products", JSONArray().put(JSONObject().put("id", "fabric").put("ok", true)
            .put("companies", JSONArray().put(fab(4, "Shree Loom Works", "LICENSED", 120, null, "2027-02-05T18:29:59Z"))))))
        assertEquals(1, p.fabric!!.companies.size)
        assertEquals("Fabric Stock", Software.name(Software.FABRIC))
        assertEquals("Sales & Costing", Software.WEIGHT_NAME)
        assertEquals("Jobwork", Software.name(Software.JOBWORK))
    }
}
