package org.nexoraofficial.console

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Customers
import org.nexoraofficial.console.data.FabricCompany
import org.nexoraofficial.console.data.FabricEdit
import org.nexoraofficial.console.data.FeatureTag
import org.nexoraofficial.console.data.Features
import org.nexoraofficial.console.data.PlansData
import org.nexoraofficial.console.data.Plans
import org.nexoraofficial.console.data.ProductsData
import org.nexoraofficial.console.data.Requests
import org.nexoraofficial.console.data.ServiceSettings
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.data.groupsOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.0.1 — FABRIC STOCK'S OWN PLANS AND FEATURES.
 *
 * Fabric Stock's service (0.8.1) answers plans and features in exactly the shape Sales & Costing's
 * come in (supported:true, 16 features in four groups, Standard built in with everything on), and its
 * companies carry plan, planName and featureOverrides. What the console reads from that, the features a
 * Fabric Stock company ends up with (plan × its own × demo), and the one /fabric update Save sends —
 * plan and featureOverrides with the rest, null taking a feature back to the plan.
 *
 * Made-up plants; nothing reaches the live service (a one-page server on this computer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Console201Test {

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

    /* ---------------------------------------------------------------- what Fabric Stock 0.8.1 answers */

    /** Fabric Stock's own 16 features, as its rules.js lists them. */
    private val fabricFeatures = listOf(
        "loomReading" to "Production", "rollCutting" to "Production", "efficiency" to "Production",
        "stock" to "Stock & dispatch", "packing" to "Stock & dispatch", "dispatch" to "Stock & dispatch", "labels" to "Stock & dispatch",
        "reports" to "Reports", "operatorPerf" to "Reports",
        "ai" to "Company", "mobile" to "Company", "chatNotes" to "Company", "exportExcel" to "Company", "printPdf" to "Company",
        "activityLog" to "Company", "backup" to "Company"
    )

    private fun ticks(vararg off: String) = JSONObject().apply { fabricFeatures.forEach { (id, _) -> put(id, id !in off) } }

    private val plansJson = JSONObject().put("software", JSONArray()
        .put(JSONObject("""{"id":"weight","name":"Nexora Bag Weight Calculation","short":"Sales & Costing","ok":true,"supported":true,
            "features":[{"id":"quotation","label":"Quotation","group":"Sales"}],
            "plans":[{"code":"PRO","name":"Pro","features":{"quotation":true},"active":true,"sort":20,"builtIn":true}]}"""))
        .put(JSONObject().put("id", "fabric").put("name", "Nexora Loom & Fabric Stock").put("short", "Fabric Stock").put("ok", true)
            .put("supported", true)
            .put("features", JSONArray().apply {
                fabricFeatures.forEach { (id, g) -> put(JSONObject().put("id", id).put("label", id.replaceFirstChar { it.uppercase() }).put("group", g)) }
            })
            .put("plans", JSONArray()
                .put(JSONObject().put("code", "STANDARD").put("name", "Standard").put("note", "everything in Fabric Stock")
                    .put("priceFirst", JSONObject.NULL).put("priceRenewal", JSONObject.NULL).put("usersIncluded", JSONObject.NULL)
                    .put("extraUserPrice", JSONObject.NULL).put("features", ticks()).put("active", true).put("sort", 10)
                    .put("createdAt", "2026-10-08T05:00:00Z").put("customers", 2).put("changed", 1).put("builtIn", true))
                .put(JSONObject().put("code", "BASIC").put("name", "Basic").put("note", "loom reading and stock")
                    .put("priceFirst", 15000).put("priceRenewal", 8000).put("usersIncluded", 3).put("extraUserPrice", 1000)
                    .put("features", ticks("ai", "mobile", "efficiency", "operatorPerf", "labels")).put("active", true).put("sort", 20)
                    .put("customers", 1).put("changed", 0).put("builtIn", false))
                .put(JSONObject().put("code", "OLD").put("name", "Old").put("features", ticks("ai")).put("active", false).put("sort", 30)
                    .put("customers", 0).put("changed", 0).put("builtIn", false)))))

    private val olderFabric = JSONObject("""{"software":[
        {"id":"weight","ok":true,"supported":true,"features":[],"plans":[]},
        {"id":"fabric","ok":true,"supported":false,"message":"Fabric Stock’s own service has no plans yet …","features":[],"plans":[]}]}""")

    private fun fab(id: Int, name: String, plan: String?, overrides: Any?, demo: Boolean = false, planName: String? = null) = JSONObject()
        .put("id", id).put("name", name).put("licenceKey", "NFS-$id").put("state", if (demo) "DEMO" else "LICENSED")
        .put("shownState", if (demo) "DEMO" else "LICENSED").put("isDemo", demo).put("daysLeft", 120)
        .put("expiresAt", "2027-02-05T18:29:59Z").put("expired", false).put("seats", 3).put("graceDays", 3)
        .put("companyId", JSONObject.NULL).put("plan", plan ?: JSONObject.NULL).put("planName", planName ?: JSONObject.NULL)
        .put("featureOverrides", overrides ?: JSONObject.NULL)

    @Test
    fun fabricStocksPlansAreReadWhenItsServiceSupportsThem() {
        val p = PlansData.from(plansJson)
        val f = p.fabric!!
        assertTrue(f.supported)
        assertTrue(Plans.supported(p, Software.FABRIC))
        assertEquals(16, f.features.size)
        assertEquals("its groups in the order they come — Company is not pulled ahead of Production",
            listOf("Production", "Stock & dispatch", "Reports", "Company"), f.groups)
        assertEquals(listOf("Production", "Stock & dispatch", "Reports", "Company"), groupsOf(f.features, Software.FABRIC))
        assertEquals(listOf("STANDARD", "BASIC", "OLD"), f.plans.map { it.code })
        val std = f.plan("standard")!!
        assertTrue("Standard is built in", std.builtIn)
        assertFalse(std.canDelete)
        assertEquals("every feature on", 16, std.onCount(f.features))
        assertNull("a price left open", std.priceFirst)
        assertEquals(2, std.customers)
        assertEquals(1, std.changed)
        val basic = f.plan("BASIC")!!
        assertEquals(11, basic.onCount(f.features))
        assertEquals(15000.0, basic.priceFirst!!, 0.0)
        assertEquals("the plans a new company may be put on", listOf("STANDARD", "BASIC"), f.livePlans.map { it.code })
        /* they are listed with Sales & Costing's, each with its software */
        assertEquals(listOf("STANDARD", "BASIC", "OLD"), p.plansOf(Software.FABRIC).map { it.code })
        assertEquals(listOf(Software.WEIGHT, Software.FABRIC, Software.FABRIC, Software.FABRIC), p.all.map { it.first })
        assertEquals("Basic", Plans.nameOf(p, Software.FABRIC, "basic"))
        assertEquals(16, Plans.catalogue(p, Software.FABRIC).size)
        assertEquals(basic.features, Plans.ticksOf(p, ServiceSettings(), Software.FABRIC, "BASIC"))
        assertEquals("no plan named: Standard", std.features, Plans.ticksOf(p, ServiceSettings(), Software.FABRIC, null))

        /* an older Fabric Stock service: no plans, every company on Standard, nothing to tick */
        val older = PlansData.from(olderFabric)
        assertFalse(Plans.supported(older, Software.FABRIC))
        assertTrue(older.plansOf(Software.FABRIC).isEmpty())
        assertTrue(Plans.ticksOf(older, ServiceSettings(), Software.FABRIC, "STANDARD").isEmpty())
        assertEquals("Standard", Plans.nameOf(older, Software.FABRIC, null))

        /* a Fabric Stock feature sent without its group goes under "Features", never under a Sales & Costing group */
        val bare = PlansData.from(JSONObject("""{"software":[{"id":"fabric","supported":true,
            "features":[{"id":"backup","label":"Backup"},{"id":"stock","label":"Stock","group":"Stock & dispatch"}],"plans":[]}]}"""))
        assertEquals("Features", bare.fabric!!.features.first { it.id == "backup" }.group)
        assertEquals(listOf("Features", "Stock & dispatch"), bare.fabric!!.groups)
    }

    @Test
    fun aFabricStockCompanyCarriesItsPlanAndItsOwnFeatures() {
        val f = FabricCompany.from(fab(4, "Shree Loom Works", "basic", JSONObject().put("labels", true).put("stock", false).put("junk", "x"),
            planName = "Basic"))
        assertEquals("BASIC", f.plan)
        assertEquals("Basic", f.planName)
        assertEquals(mapOf("labels" to true, "stock" to false), f.featureOverrides)
        /* none: null, missing, or written as text */
        assertTrue(FabricCompany.from(fab(5, "a", "STANDARD", null)).featureOverrides.isEmpty())
        assertTrue(FabricCompany.from(fab(6, "b", null, null).apply { remove("featureOverrides"); remove("planName") }).featureOverrides.isEmpty())
        assertNull(FabricCompany.from(fab(6, "b", null, null)).planName)
        assertEquals(mapOf("ai" to true), FabricCompany.from(fab(7, "c", "STANDARD", "{\"ai\":true}")).featureOverrides)
        /* its plan's name: from the plans list; else the name it came with; else its code in words */
        assertEquals("Basic", Plans.nameOf(PlansData.from(plansJson), Software.FABRIC, f.plan, f.planName))
        assertEquals("Gold weave", Plans.nameOf(null, Software.FABRIC, "GOLDW", "Gold weave"))
        assertEquals("Goldw", Plans.nameOf(null, Software.FABRIC, "GOLDW", null))
        /* read through the products listing, exactly as the customers are made */
        val p = ProductsData.from(JSONObject().put("products", JSONArray().put(JSONObject().put("id", "fabric").put("ok", true)
            .put("companies", JSONArray().put(fab(4, "Shree Loom Works", "BASIC", JSONObject().put("labels", true)))))))
        assertEquals(mapOf("labels" to true), p.fabric!!.companies.single().featureOverrides)
    }

    @Test
    fun aFabricStockCompanysFeaturesArePlanTimesOwnTimesDemo() {
        val plans = PlansData.from(plansJson)
        /* Basic: ai, mobile, efficiency, operatorPerf and labels are not in it */
        val shree = FabricCompany.from(fab(4, "Shree Loom Works", "BASIC", JSONObject().put("labels", true).put("stock", false)))
        val v = Features.viewOf(plans, ServiceSettings(), shree)
        assertEquals(Software.FABRIC, v.sw)
        assertEquals("Basic", v.planName)
        assertEquals(16, v.catalogue.size)
        assertEquals(listOf("Production", "Stock & dispatch", "Reports", "Company"), v.groups)
        assertTrue("in the plan, nothing of its own", v.on("loomReading"))
        assertFalse("in the plan, taken off for it", v.on("stock"))
        assertTrue("not in the plan, added for it", v.on("labels"))
        assertFalse("not in the plan, nothing of its own", v.on("ai"))
        assertEquals(FeatureTag.OFF, Features.tag(v.plan["stock"] == true, v.own["stock"]))
        assertEquals(FeatureTag.ADDED, Features.tag(v.plan["labels"] == true, v.own["labels"]))
        assertEquals(FeatureTag.NONE, Features.tag(v.plan["ai"] == true, v.own["ai"]))
        assertEquals(11, v.effective.count { it.value })
        val s = v.summary()
        assertEquals(11, s.fromPlan)
        assertEquals(1, s.added)
        assertEquals(1, s.off)
        assertEquals(11, s.on)
        assertEquals(16, s.total)

        /* every combination of plan × its own × demo, feature by feature */
        for (planOn in listOf(true, false)) for (own in listOf<Boolean?>(null, true, false)) for (demo in listOf(true, false)) {
            val id = if (planOn) "stock" else "ai"
            val co = FabricCompany.from(fab(9, "x", "BASIC", own?.let { JSONObject().put(id, it) }, demo = demo))
            val view = Features.viewOf(plans, ServiceSettings(), co)
            assertEquals("plan $planOn, own $own, demo $demo", demo || (own ?: planOn), view.on(id))
        }

        /* a demo has every feature, whatever its plan and its own */
        val demo = FabricCompany.from(fab(1, "Riverside", "BASIC", JSONObject().put("stock", false), demo = true))
        assertTrue(Features.viewOf(plans, ServiceSettings(), demo).effective.values.all { it })

        /* Standard (every feature on) with one off: "Standard gives 16 of 16 · +0 added · −1 off → 15 on" */
        val std = Features.viewOf(plans, ServiceSettings(), FabricCompany.from(fab(2, "Vijay", "STANDARD", JSONObject().put("backup", false))))
        val ss = std.summary()
        assertEquals(listOf(16, 0, 1, 15, 16), listOf(ss.fromPlan, ss.added, ss.off, ss.on, ss.total))

        /* editing: a tap keeps only what differs from the plan, "Back to the plan only" takes every one back */
        var draft: Map<String, Boolean?> = emptyMap()
        draft = v.toggle("ai", draft)
        assertEquals(true, draft["ai"])
        draft = v.toggle("stock", draft)
        assertTrue("stock goes back to the plan: null", draft.containsKey("stock") && draft["stock"] == null)
        assertEquals(mapOf("labels" to true, "ai" to true), v.ownWith(draft))
        assertEquals("the plan's 11 (stock back on) + labels + ai", 13, v.summary(draft).on)
        assertTrue(v.reset().values.all { it == null })
        assertEquals(16, v.reset().size)
        assertTrue(v.ownWith(v.reset()).isEmpty())

        /* the line the lists show: its own changes as "± n" after the plan's name */
        val x = Customers.of(emptyList(), listOf(shree)).single()
        assertEquals("Fabric Stock · Basic ± 2 · 120 days", Customers.line(x, Software.FABRIC, plans))
        assertEquals("Basic ± 2 · 120 days", Customers.tabLine(x, Software.FABRIC, plans))
        assertEquals(2, Customers.ownCount(x, Software.FABRIC))
        assertEquals(0, Customers.ownCount(x, Software.WEIGHT))
    }

    @Test
    fun aFabricStockSaveSendsPlanAndFeaturesInItsOneUpdate() {
        val f = FabricCompany.from(fab(4, "Shree Loom Works", "BASIC", JSONObject().put("labels", true).put("stock", false)))
        val e0 = FabricEdit.of(f)
        assertEquals("BASIC", e0.plan)
        assertNull("nothing changed, nothing sent", Requests.fabricUpdate(f, e0))
        assertNull("the same plan is not sent", Requests.fabricUpdate(f, e0.copy(plan = "basic")))

        val b = Requests.fabricUpdate(f, e0.copy(plan = "standard", seats = "5",
            features = mapOf("ai" to true, "stock" to null, "labels" to true, "mobile" to null)))!!
        assertEquals("update", b.getString("action"))
        assertEquals(4, b.getInt("id"))
        assertEquals("STANDARD", b.getString("plan"))
        assertEquals(5, b.getInt("seats"))
        val ov = b.getJSONObject("featureOverrides")
        assertEquals("only the keys that change its own list", setOf("ai", "stock"), ov.keys().asSequence().toSet())
        assertTrue(ov.getBoolean("ai"))
        assertTrue("null: back to the plan", ov.has("stock") && ov.isNull("stock"))
        assertFalse("not sent: Fabric Stock's update takes featureOverrides", b.has("resetFeatures"))

        /* only the features */
        val onlyFeatures = Requests.fabricUpdate(f, e0.copy(features = mapOf("labels" to null)))!!
        assertEquals(setOf("action", "id", "featureOverrides"), onlyFeatures.keys().asSequence().toSet())
        assertTrue(onlyFeatures.getJSONObject("featureOverrides").isNull("labels"))

        /* "Back to the plan only": every one it had goes back, in the one update */
        val v = Features.viewOf(PlansData.from(plansJson), ServiceSettings(), f)
        val reset = Requests.fabricUpdate(f, e0.copy(features = v.reset()))!!.getJSONObject("featureOverrides")
        assertEquals(setOf("labels", "stock"), reset.keys().asSequence().toSet())
        assertTrue(reset.isNull("labels") && reset.isNull("stock"))

        /* a company whose service named no plan is on Standard: choosing Standard sends nothing */
        val bare = FabricCompany.from(fab(5, "Mahalaxmi Tex", null, null))
        assertEquals("STANDARD", FabricEdit.of(bare).plan)
        assertNull(Requests.fabricUpdate(bare, FabricEdit.of(bare).copy(plan = "STANDARD")))
        assertEquals("BASIC", Requests.fabricUpdate(bare, FabricEdit.of(bare).copy(plan = "BASIC"))!!.getString("plan"))
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

    @Test
    fun aRetiredPlanIsRefusedInFabricStocksSentenceAndAGoodSaveReadsThePlansAgain() {
        val sent = CopyOnWriteArrayList<JSONObject>()
        val asked = CopyOnWriteArrayList<String>()
        val srv = TinyServer { target, _ ->
            val path = target.substringBefore('?')
            asked += path
            when (path) {
                "/admin/api/fabric" -> {
                    val b = JSONObject(server?.lastBody ?: "{}")
                    sent += b
                    if (b.optString("plan") == "OLD") 400 to """{"error":"The plan “Old” is retired — restore it first, or choose another."}"""
                    else 200 to """{"ok":true,"company":{"id":4,"name":"Shree Loom Works"}}"""
                }
                "/admin/api/plans" -> 200 to plansJson.toString()
                "/admin/api/products" -> 200 to """{"products":[]}"""
                else -> 404 to """{"error":"NOT_FOUND"}"""
            }
        }.also { server = it }
        val vm = ConsoleViewModel(app).apply {
            baseUrl = srv.base; key = "NX-TEST-KEY"; signedIn = true
            data = ConsoleData.from(JSONObject().put("companies", JSONArray()))
        }
        val f = FabricCompany.from(fab(4, "Shree Loom Works", "BASIC", null))

        var ok: Boolean? = null
        vm.saveFabric(f, FabricEdit.of(f).copy(plan = "OLD")) { ok = it }
        until("refused") { ok != null }
        assertEquals("a refused plan keeps the window in Edit", false, ok)
        assertEquals(Msg.Kind.ERR, vm.lastSaid?.kind)
        assertEquals("Fabric Stock: The plan “Old” is retired — restore it first, or choose another.", vm.lastSaid?.text)
        assertEquals("OLD", sent.single().getString("plan"))
        assertFalse("refused: the plans are not read again", asked.contains("/admin/api/plans"))

        ok = null
        vm.saveFabric(f, FabricEdit.of(f).copy(plan = "STANDARD", features = mapOf("ai" to true))) { ok = it }
        until("saved and the plans read again") { ok != null && vm.plansLoad == Load.READY }
        assertEquals(true, ok)
        assertEquals("one update", 2, sent.size)
        assertEquals("STANDARD", sent[1].getString("plan"))
        assertTrue(sent[1].getJSONObject("featureOverrides").getBoolean("ai"))
        assertEquals("Saved.", vm.lastSaid?.text)
        assertTrue(vm.plans!!.fabric!!.supported)
    }
}
