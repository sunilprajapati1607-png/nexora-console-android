package org.nexoraofficial.console

import org.json.JSONArray
import org.json.JSONObject

/* ======================================================================
   2.3.0 — made-up plans with validity, quotes, Invoice Settings and
   invoices, as the console service answers them. Every name here is
   invented; nothing reaches the live service.
   ====================================================================== */
internal object Fixtures230 {

    /** Sales & Costing's plans with their validity; Fabric Stock's Standard without one (read as a year). */
    fun plans(): JSONObject = JSONObject().put("software", JSONArray()
        .put(JSONObject("""{"id":"weight","ok":true,"supported":true,"features":[{"id":"quotation","label":"Quotation","group":"Sales"}],
            "plans":[{"code":"PRO","name":"Pro","note":"calculation, quotation and the cost tools","priceFirst":24000,"priceRenewal":18000,
                      "usersIncluded":5,"extraUserPrice":2500,"days":365,"features":{"quotation":true},"active":true,"sort":20,"customers":1,"changed":0,"builtIn":true},
                     {"code":"GOLD","name":"Gold","priceFirst":40000,"priceRenewal":30000,"usersIncluded":10,"extraUserPrice":2000,"days":730,
                      "features":{"quotation":true},"active":true,"sort":30,"customers":0,"changed":0,"builtIn":false}]}"""))
        .put(JSONObject("""{"id":"fabric","ok":true,"supported":true,"features":[{"id":"stock","label":"Fabric stock","group":"Stock & dispatch"}],
            "plans":[{"code":"STANDARD","name":"Standard","features":{"stock":true},"active":true,"sort":10,"customers":1,"changed":0,"builtIn":true}]}""")))

    /** The service's sum: Gold for 8 users is all included; 7 users on Pro is 2 extra. */
    fun quote(plan: String = "PRO", users: Int = 7, periods: Int = 1, kind: String = "RENEWAL", amount: Any? = 23000): JSONObject = JSONObject()
        .put("ok", true)
        .put("plan", JSONObject().put("code", plan).put("name", if (plan == "GOLD") "Gold" else "Pro").put("days", if (plan == "GOLD") 730 else 365)
            .put("priceFirst", 24000).put("priceRenewal", 18000).put("usersIncluded", 5).put("extraUserPrice", 2500))
        .put("kind", kind).put("users", users).put("periods", periods).put("extraUsers", (users - 5).coerceAtLeast(0))
        .put("unitPrice", 18000).put("extraUserPrice", 2500).put("extraAmount", 2500 * (users - 5).coerceAtLeast(0))
        .put("days", 365 * periods).put("amount", amount ?: JSONObject.NULL)
        .put("currentPlan", "PRO").put("currentSeats", 6).put("note", JSONObject.NULL)

    fun seller(gstin: String = "24ABCDE1234F1Z5"): JSONObject = JSONObject()
        .put("sellerName", "Lotus Softworks").put("legalName", "Lotus Softworks LLP").put("gstin", gstin)
        .put("address", "12 Riverfront Road\nAhmedabad 380009").put("stateCode", "24").put("stateName", "Gujarat")
        .put("phone", "+91 90000 00000").put("email", "accounts@lotus.example").put("website", "lotus.example")
        .put("pan", if (gstin.isEmpty()) "" else "ABCDE1234F").put("bankName", "Sample Co-operative Bank").put("accountName", "Lotus Softworks LLP")
        .put("accountNumber", "001122334455").put("ifsc", "SAMP0001234").put("upi", "lotus@upi").put("sac", "997331").put("gstRate", 18)
        .put("prefixGst", "NX").put("prefixPlain", "NXB").put("prefixReceipt", "NXR")
        .put("terms", "Payment within 7 days.\nSubject to Ahmedabad jurisdiction.").put("footer", "Thank you for your business.")
        .put("signatory", "Meera Joshi")

    fun settings(ready: Boolean = true): JSONObject = JSONObject().put("settings", seller()).put("ready", ready)

    private fun customer(state: String) = JSONObject().put("name", "Sagar Polysacks").put("gstin", if (state == "24") "24SAGAR1234P1Z5" else "27SAGAR1234P1Z9")
        .put("address", "Plot 7, GIDC\nVatva 382445").put("stateCode", state).put("stateName", if (state == "24") "Gujarat" else "Maharashtra")
        .put("email", "office@sagar.example").put("phone", "+91 90000 31111")

    /**
     * One invoice record. [kind] INVOICE / RECEIPT, [gst], [intra] (CGST + SGST, else IGST). The figures are the
     * service's — on purpose not what a phone would add up (the total 21,250 is not taxable + tax), so a screen that
     * did its own sums would show it.
     */
    fun invoice(
        id: Any = 101, number: String = "NX/2026-27/0007", kind: String = "INVOICE", gst: Boolean = true, intra: Boolean = true,
        cancelled: Boolean = false, sellerGstin: String = "24ABCDE1234F1Z5", paymentId: Int? = 41
    ): JSONObject {
        val o = JSONObject().put("id", id).put("number", number).put("series", number.substringBefore('/')).put("kind", kind).put("gst", gst)
            .put("date", "2026-11-07").put("fy", "2026-27").put("paymentId", paymentId ?: JSONObject.NULL).put("software", "weight")
            .put("companyId", 21).put("remoteId", JSONObject.NULL).put("seller", seller(sellerGstin)).put("customer", customer(if (intra) "24" else "27"))
            .put("lines", JSONArray().put(JSONObject().put("description", "Nexora Bag Weight Calculation — Pro plan, 1 year, 5 users")
                .put("sac", if (gst) "997331" else JSONObject.NULL).put("qty", 1).put("rate", 18000).put("amount", 18000))
                .put(JSONObject().put("description", "Extra users above the plan, 1 year").put("sac", if (gst) "997331" else JSONObject.NULL)
                    .put("qty", 1).put("rate", 2500).put("amount", 2500)))
            .put("inclusive", false).put("taxable", 20500)
            .put("roundOff", if (gst) 0.4 else 0).put("total", if (gst) 21250 else 20500)
            .put("totalWords", if (gst) "Rupees Twenty One Thousand Two Hundred Fifty Only" else "Rupees Twenty Thousand Five Hundred Only")
            .put("intraState", intra).put("placeOfSupply", if (intra) "24 Gujarat" else "27 Maharashtra")
            .put("notes", "Renewal for 2026-27.").put("createdAt", "2026-11-07T06:30:00.000Z")
            .put("cancelledAt", if (cancelled) "2026-11-08T05:00:00.000Z" else JSONObject.NULL)
            .put("cancelReason", if (cancelled) "wrong customer — issued again" else JSONObject.NULL)
        if (gst && intra) o.put("cgstRate", 9).put("cgst", 1845).put("sgstRate", 9).put("sgst", 1845).put("igstRate", 0).put("igst", 0)
        else if (gst) o.put("cgstRate", 0).put("cgst", 0).put("sgstRate", 0).put("sgst", 0).put("igstRate", 18).put("igst", 3690)
        else o.put("cgstRate", 0).put("cgst", 0).put("sgstRate", 0).put("sgst", 0).put("igstRate", 0).put("igst", 0)
        return o
    }

    /** One payment of Sagar Polysacks on Sales & Costing. */
    fun payments(): JSONObject = JSONObject().put("payments", JSONArray()
        .put(JSONObject().put("id", 41).put("software", "weight").put("companyId", 21).put("customer", "Sagar Polysacks").put("plan", "PRO")
            .put("planName", "Pro").put("kind", "RENEWAL").put("amount", 23600).put("paidOn", "2026-11-07").put("mode", "UPI")
            .put("reference", "UTR 6021 4410").put("validFrom", "2027-05-31").put("validTo", "2028-05-30").put("createdAt", "2026-11-07T06:00:00.000Z")))
        .put("totals", JSONObject().put("count", 1).put("amount", 23600))
}
