package org.nexoraofficial.console

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.nexoraofficial.console.data.Company
import org.nexoraofficial.console.data.ConsoleData
import org.nexoraofficial.console.data.Feedback
import org.nexoraofficial.console.data.FeedbackData
import org.nexoraofficial.console.data.Inquiry
import org.nexoraofficial.console.data.InquiryData
import org.nexoraofficial.console.data.Prefs
import org.nexoraofficial.console.data.Release
import org.nexoraofficial.console.data.Sealer
import org.nexoraofficial.console.data.Summary
import org.nexoraofficial.console.data.WatchSource
import org.nexoraofficial.console.work.Watch
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 4.72.0 — audit #43 (contract C11): the quarter-hourly watch asks only for
 * the summary, only from 08:30 to 20:30 India time, fetches a short list only
 * when something new arrived, and never pulls the company listing.
 *
 * After review: a service without the summary (not updated yet) is told once
 * and watched the 1.7.1 way meanwhile — still only in the day, the company
 * listing no more than once a look — until the summary takes over; and a
 * refused admin key stops the watch until the next good sign-in.
 *
 * Made-up enquiries and plants; nothing reaches the live service (a stand-in
 * service, and for the route itself a one-page server on this computer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Watch4720Test {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val fake = object : Sealer {
        override fun seal(plain: String) = "sealed:$plain"
        override fun open(sealed: String) = sealed.removePrefix("sealed:")
    }
    private lateinit var prefs: Prefs

    @Before
    fun clean() {
        app.getSharedPreferences("nexora.console", 0).edit().clear().commit()
        prefs = Prefs(app, fake)
    }

    private fun ist(h: Int, m: Int): Instant =
        LocalDate.of(2026, 10, 2).atTime(h, m).atZone(Watch.IST).toInstant()

    private fun snap(at: String, enquiries: Int = 0, feedback: Int = 0, registrations: Int = 0) =
        Summary(at, null, 12, 30, enquiries, feedback, registrations)

    private fun inquiry(id: Int, name: String, company: String, at: String) = Inquiry.from(
        JSONObject().put("id", id).put("name", name).put("company", company)
            .put("product", "Bag Weight & Cost Forecasting").put("createdAt", at)
    )

    private fun report(id: Int, kind: String, plant: String, subject: String, at: String) = Feedback.from(
        JSONObject().put("id", id).put("kind", kind).put("coName", plant).put("subject", subject)
            .put("message", "words").put("createdAt", at)
    )

    private fun company(id: Int, name: String, selfRegistered: Boolean, at: String?) = Company.from(
        JSONObject().put("id", id).put("name", name).put("self_registered", selfRegistered)
            .apply { if (at != null) put("registered_at", at) }
    )

    /** A stand-in service that counts what it is asked. */
    private class FakeService : WatchSource {
        val sinces = mutableListOf<String?>()
        var next: Summary? = null
        var summaryFails = false
        /** What the summary throws instead of answering (a refused key, an older service …). */
        var summaryError: Exception? = null
        var inquiries = InquiryData()
        var inquiriesFail = false
        var inquiriesError: Exception? = null
        var feedback = FeedbackData()
        /** The company listing — the old way's only (a service without the summary). */
        var companies = emptyList<Company>()
        var licencesFail = false
        var release: Release? = null
        var releaseError: Exception? = null
        var inquiryCalls = 0
        var feedbackCalls = 0
        var licenceCalls = 0
        var releaseCalls = 0
        val calls get() = sinces.size + inquiryCalls + feedbackCalls + licenceCalls + releaseCalls

        override suspend fun summary(since: String?): Summary {
            sinces += since
            summaryError?.let { throw it }
            if (summaryFails) throw ApiError("Could not reach the service.")
            return next ?: throw ApiError("no summary")
        }

        override suspend fun inquiries(): InquiryData {
            inquiryCalls++
            inquiriesError?.let { throw it }
            if (inquiriesFail) throw ApiError("Could not reach the service.")
            return inquiries
        }

        override suspend fun feedback(): FeedbackData {
            feedbackCalls++
            return feedback
        }

        override suspend fun licences(): ConsoleData {
            licenceCalls++
            if (licencesFail) throw ApiError("Could not reach the service.")
            return ConsoleData(companies = companies)
        }

        override suspend fun latestRelease(): Release? {
            releaseCalls++
            releaseError?.let { throw it }
            return release
        }
    }

    @Test
    fun looksOnlyFromHalfPastEightToHalfPastEightIndiaTime() {
        assertFalse(Watch.awake(ist(8, 29)))
        assertTrue(Watch.awake(ist(8, 30)))
        assertTrue(Watch.awake(ist(14, 0)))
        assertTrue(Watch.awake(ist(20, 29)))
        assertFalse(Watch.awake(ist(20, 30)))
        assertFalse(Watch.awake(ist(23, 45)))
        assertFalse(Watch.awake(ist(3, 0)))
        /* the phone's own zone does not matter: 03:00 UTC is 08:30 in India */
        assertTrue(Watch.awake(Instant.parse("2026-10-02T03:00:00Z")))
        assertFalse(Watch.awake(Instant.parse("2026-10-02T15:00:00Z")))
    }

    @Test
    fun atNightNothingIsAskedOfTheService() = runBlocking {
        val s = FakeService().apply { next = snap("2026-10-02T17:30:00Z", enquiries = 4) }
        assertTrue(Watch.look(prefs, s, ist(23, 0)).isEmpty())
        assertTrue(Watch.look(prefs, s, ist(6, 0)).isEmpty())
        assertEquals("not one request at night", 0, s.calls)
        assertNull(prefs.lastSummaryAt)
    }

    @Test
    fun aServiceWithTheSummaryIsNeverAskedForTheCompanyListing() = runBlocking {
        val s = FakeService().apply { next = snap("2026-10-02T03:00:00Z") }
        Watch.look(prefs, s, ist(8, 30))
        s.next = snap("2026-10-02T03:15:00Z", enquiries = 1, feedback = 1, registrations = 2)
        val news = Watch.look(prefs, s, ist(8, 45))
        assertEquals(setOf(1001, 1004, 1002), news.map { it.id }.toSet())
        assertEquals("2 plants have registered and are on a demo. Open the console to see them.", news.first { it.id == 1002 }.text)
        assertEquals("no company listing in the background", 0, s.licenceCalls)
    }

    @Test
    fun theFirstLookLearnsQuietlyThenTellsWhatArrivedSince() = runBlocking {
        val s = FakeService()
        s.next = snap("2026-10-02T03:00:00Z", enquiries = 5, feedback = 2, registrations = 1)
        val first = Watch.look(prefs, s, ist(8, 30))
        assertTrue("the first look must not announce a whole day", first.isEmpty())
        assertEquals("2026-10-02T03:00:00Z", prefs.lastSummaryAt)
        assertEquals(listOf<String?>(null), s.sinces)
        assertEquals("no list fetched to learn", 0, s.inquiryCalls + s.feedbackCalls)

        s.next = snap("2026-10-02T03:15:00Z", enquiries = 2, feedback = 1, registrations = 1)
        s.inquiries = InquiryData(
            listOf(
                inquiry(41, "Ravi", "Ravi Poly", "2026-10-02T03:10:00Z"),
                inquiry(40, "Meena", "Meena Sacks", "2026-10-02T03:05:00Z"),
                inquiry(39, "Old Lead", "Told Yesterday", "2026-10-01T09:00:00Z")
            )
        )
        s.feedback = FeedbackData(listOf(report(7, "BUG", "Om Poly Packs", "BOM will not save", "2026-10-02T03:12:00Z")))
        val second = Watch.look(prefs, s, ist(8, 45))

        assertEquals("the next look asks from the service's own moment", listOf(null, "2026-10-02T03:00:00Z"), s.sinces)
        assertEquals("2026-10-02T03:15:00Z", prefs.lastSummaryAt)
        val byId = second.associateBy { it.id }
        assertEquals("2 new enquiries", byId[1001]?.title)
        assertTrue(byId[1001]!!.text.contains("Ravi (Ravi Poly)"))
        assertTrue(byId[1001]!!.text.contains("Meena (Meena Sacks)"))
        assertFalse("only the new ones are named", byId[1001]!!.text.contains("Old Lead"))
        assertEquals("New problem report", byId[1004]?.title)
        assertTrue(byId[1004]!!.text.contains("Om Poly Packs — BOM will not save"))
        assertEquals("New registration", byId[1002]?.title)
    }

    @Test
    fun aQuietLookFetchesNoList() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val s = FakeService().apply { next = snap("2026-10-02T03:15:00Z") }
        assertTrue(Watch.look(prefs, s, ist(9, 0)).isEmpty())
        assertEquals(1, s.sinces.size)
        assertEquals("nothing new: no enquiry or report list is pulled", 0, s.inquiryCalls + s.feedbackCalls)
    }

    @Test
    fun aFailedLookLearnsNothingSoNothingIsMissed() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val s = FakeService().apply { summaryFails = true }
        assertTrue(Watch.look(prefs, s, ist(9, 0)).isEmpty())
        assertEquals("2026-10-02T03:00:00Z", prefs.lastSummaryAt)
        s.summaryFails = false
        s.next = snap("2026-10-02T03:45:00Z", enquiries = 1)
        Watch.look(prefs, s, ist(9, 15))
        assertEquals("asked again from the same moment", "2026-10-02T03:00:00Z", s.sinces.last())
    }

    @Test
    fun aListThatWillNotComeStillTellsTheCount() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val s = FakeService().apply {
            next = snap("2026-10-02T03:15:00Z", enquiries = 1)
            inquiriesFail = true
        }
        val news = Watch.look(prefs, s, ist(9, 0))
        assertEquals(1, news.size)
        assertEquals("New enquiry", news[0].title)
        assertEquals("Open the console to see it.", news[0].text)
    }

    @Test
    fun theReleaseIsAskedEveryFewHoursAndToldOncePerVersion() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val s = FakeService().apply {
            next = snap("2026-10-02T03:15:00Z")
            release = Release(BuildConfig.VERSION_CODE + 1, "9.9.9", "https://example.invalid/c.apk", null, null, null, false, null)
        }
        val first = Watch.look(prefs, s, ist(9, 0))
        assertEquals("Console 9.9.9 is ready", first.single().title)
        Watch.look(prefs, s, ist(9, 15))
        Watch.look(prefs, s, ist(11, 0))
        assertEquals("asked once in three hours", 1, s.releaseCalls)
        val later = Watch.look(prefs, s, ist(12, 1))
        assertEquals(2, s.releaseCalls)
        assertTrue("told once per version", later.isEmpty())
    }

    /* ---- the route itself, on a one-page server on this computer ---- */

    private var server: TinyServer? = null

    @After
    fun stop() {
        server?.close()
    }

    @Test
    fun theSummaryIsOneGetWithTheKeyAndTheMoment() = runBlocking {
        val srv = TinyServer { target, _ ->
            if (target.substringBefore('?') == "/admin/api/summary")
                200 to """{"at":"2026-10-02T03:15:00.000Z","since":"2026-10-02T03:00:00.000Z","companies":12,"licences":30,"newEnquiries":2,"newFeedback":0,"newRegistrations":1}"""
            else 404 to """{"error":"NOT_FOUND"}"""
        }.also { server = it }
        val s = Api(srv.base, "NX-TEST-KEY").summary("2026-10-02T03:00:00.000Z")
        assertEquals("/admin/api/summary?since=2026-10-02T03%3A00%3A00.000Z", srv.lastTarget)
        assertEquals("NX-TEST-KEY", srv.lastHeaders["x-admin-key"])
        assertEquals("2026-10-02T03:15:00.000Z", s.at)
        assertEquals(2, s.newEnquiries)
        assertEquals(1, s.newRegistrations)
        assertEquals(12, s.companies)

        Api(srv.base, "NX-TEST-KEY").summary(null)
        assertEquals("no since on the first look", "/admin/api/summary", srv.lastTarget)
        assertEquals("one GET each, nothing else", 2, srv.hits.get())
    }

    @Test
    fun anOlderServiceWithoutTheSummaryIsAFailureNotZeroNews() = runBlocking {
        val srv = TinyServer { _, _ -> 404 to """{"error":"NOT_FOUND"}""" }.also { server = it }
        try {
            Api(srv.base, "NX-TEST-KEY").summary(null)
            fail("an answer without \"at\" must not read as a summary")
        } catch (e: ApiError) {
            assertEquals(404, e.status)
            assertEquals("NOT_FOUND", e.code)
            assertTrue("known as a service without the summary", Watch.noSummary(e))
            assertFalse(e.keyRefused)
        }
    }

    /* ---- 4.72.0, after review: the watch no longer fails in silence ---- */

    @Test
    fun aServiceWithoutTheSummaryIsToldOnceAndAskedAgainUntilItIsUpdated() = runBlocking {
        prefs.lastReleaseCheck = ist(8, 30).toEpochMilli()        // keep the release question out of this
        val s = FakeService().apply { summaryError = ApiError("NOT_FOUND", 404, "NOT_FOUND") }

        val first = Watch.look(prefs, s, ist(9, 0))
        assertEquals(Watch.TROUBLE_ID, first.single().id)
        assertEquals("Notifications need the service update", first.single().title)
        assertTrue(Watch.look(prefs, s, ist(9, 15)).isEmpty())
        assertTrue("told once, not every quarter of an hour", Watch.look(prefs, s, ist(9, 30)).isEmpty())
        assertEquals("asked at every look, so the news resumes by itself", 3, s.sinces.size)

        /* the service is updated: its first answer is learned quietly */
        s.summaryError = null
        s.next = snap("2026-10-02T04:15:00Z", enquiries = 3)
        assertTrue(Watch.look(prefs, s, ist(9, 45)).isEmpty())
        assertEquals("2026-10-02T04:15:00Z", prefs.lastSummaryAt)
        assertFalse(prefs.toldNoSummary)

        /* and a later break (an answer in another form) is told again */
        s.summaryError = ApiError("not a summary", 200, Summary.NOT_A_SUMMARY)
        assertEquals("Notifications need the service update", Watch.look(prefs, s, ist(10, 0)).single().title)
        assertEquals("2026-10-02T04:15:00Z", prefs.lastSummaryAt)
    }

    @Test
    fun aSleepingOrFailingServiceIsNotToldAsMissing() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        prefs.lastReleaseCheck = ist(8, 30).toEpochMilli()
        val s = FakeService()
        listOf(
            ApiError("Could not reach the service. timeout"),
            ApiError("Request failed (503)", 503),
            ApiError("Request failed (404)", 404),                // a 404 that is not the service's own NOT_FOUND
            ApiError("Something broke", 500, "SERVER_ERROR"),
            IllegalStateException("anything else")
        ).forEachIndexed { i, e ->
            s.summaryError = e
            assertTrue(e.toString(), Watch.look(prefs, s, ist(9, 15 + i)).isEmpty())
        }
        assertFalse(prefs.toldNoSummary)
        assertFalse(prefs.keyRejected)
        assertEquals("nothing learned, nothing missed", "2026-10-02T03:00:00Z", prefs.lastSummaryAt)
    }

    @Test
    fun aRefusedKeyIsToldOnceAndThenNothingIsAskedWithIt() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val s = FakeService().apply {
            summaryError = ApiError("That admin key was not accepted.", 401, "UNAUTHORISED")
            release = Release(BuildConfig.VERSION_CODE + 1, "9.9.9", "https://example.invalid/c.apk", null, null, null, false, null)
        }
        val told = Watch.look(prefs, s, ist(9, 0))
        assertEquals(Watch.TROUBLE_ID, told.single().id)
        assertEquals("Notifications stopped", told.single().title)
        assertTrue(prefs.keyRejected)
        assertEquals("the release question is not asked with a refused key", 0, s.releaseCalls)

        for (h in 9..20) for (m in listOf(15, 30, 45)) {
            if (h == 20 && m > 15) continue
            assertTrue(Watch.look(prefs, s, ist(h, m)).isEmpty())
        }
        assertEquals("one refused try, not one every quarter of an hour", 1, s.calls)

        /* the owner signs in with the current key (ConsoleViewModel.load clears the flag) */
        prefs.keyRejected = false
        s.summaryError = null
        s.next = snap("2026-10-02T09:00:00Z")
        Watch.look(prefs, s, ist(14, 30))
        assertEquals("the watch asks again", 2, s.sinces.size)
        assertEquals("2026-10-02T03:00:00Z", s.sinces.last())
    }

    @Test
    fun aKeyRefusedOnTheReleaseQuestionStopsTheWatchToo() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val s = FakeService().apply {
            summaryFails = true                                    // offline for the summary …
            releaseError = ApiError("That admin key was not accepted.", 401, "UNAUTHORISED")
        }
        assertEquals("Notifications stopped", Watch.look(prefs, s, ist(9, 0)).single().title)
        assertTrue(prefs.keyRejected)
        assertTrue(Watch.look(prefs, s, ist(12, 30)).isEmpty())
        assertEquals(2, s.calls)
    }

    @Test
    fun aShutAddressIsNotAskedAboutReleasesAsWell() = runBlocking {
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val s = FakeService().apply {
            summaryError = ApiError("Too many wrong admin keys from this address — try again in 12 minutes.", 429, "TOO_MANY")
        }
        assertTrue(Watch.look(prefs, s, ist(9, 0)).isEmpty())
        assertEquals("the release question would only be refused too", 0, s.releaseCalls)
        assertFalse("a shut address is not a refused key", prefs.keyRejected)
        assertFalse(prefs.toldNoSummary)
    }

    @Test
    fun aChangedKeyCostsTheServiceOneRefusalInADayNotOneEveryLook() = runBlocking {
        /* the real calls, to a service that refuses the key: what reaches it is what the lock counts */
        val srv = TinyServer { _, _ -> 401 to """{"error":"UNAUTHORISED"}""" }.also { server = it }
        prefs.lastSummaryAt = "2026-10-02T03:00:00Z"
        val api = Api(srv.base, "NX-OLD-KEY")
        assertEquals("Notifications stopped", Watch.look(prefs, api, ist(8, 30)).single().title)
        for (h in 8..20) for (m in listOf(0, 15, 30, 45)) {
            val t = ist(h, m)
            if (Watch.awake(t)) assertTrue(Watch.look(prefs, api, t).isEmpty())
        }
        assertEquals("one refused request all day (the service shuts an address after five)", 1, srv.hits.get())
        assertEquals("/admin/api/summary?since=2026-10-02T03%3A00%3A00Z", srv.lastTarget)
    }

    @Test
    fun aSummaryInAnotherFormIsNotReadAsNothingNew() = runBlocking {
        listOf(
            """{"at":"2026-10-02T03:15:00.000Z","enquiries":2,"feedback":0,"registrations":1}""",          // counts named otherwise
            """{"at":"2026-10-02T03:15:00.000Z","newEnquiries":2,"newFeedback":0}""",                      // one count missing
            """{"at":"2026-10-02T03:15:00.000Z","newEnquiries":"two","newFeedback":0,"newRegistrations":0}""", // not a number
            """{"at":1759374900000,"newEnquiries":2,"newFeedback":0,"newRegistrations":1}""",              // a number, not a moment
            """{"at":"2026-10-02 03:15:00","newEnquiries":2,"newFeedback":0,"newRegistrations":1}""",      // no zone
            """{"newEnquiries":2,"newFeedback":0,"newRegistrations":1}"""                                  // no at
        ).forEach { body ->
            val srv = TinyServer { _, _ -> 200 to body }
            try {
                Api(srv.base, "NX-TEST-KEY").summary("2026-10-02T03:00:00.000Z")
                fail("not a summary: $body")
            } catch (e: ApiError) {
                assertEquals(body, Summary.NOT_A_SUMMARY, e.code)
                assertTrue(body, Watch.noSummary(e))
            } finally {
                srv.close()
            }
        }

        /* the agreed form, with a zone offset and with counts sent as text (a Postgres bigint) */
        val ok = TinyServer { _, _ ->
            200 to """{"at":"2026-10-02T08:45:00.123+05:30","since":"2026-10-02T03:00:00.000Z","companies":"12","licences":30,"newEnquiries":"2","newFeedback":0,"newRegistrations":"1"}"""
        }.also { server = it }
        val s = Api(ok.base, "NX-TEST-KEY").summary("2026-10-02T03:00:00.000Z")
        assertEquals("2026-10-02T08:45:00.123+05:30", s.at)
        assertEquals(2, s.newEnquiries)
        assertEquals(0, s.newFeedback)
        assertEquals(1, s.newRegistrations)
    }

    @Test
    fun aRefusedKeyIsKnownAsSuch() = runBlocking {
        val srv = TinyServer { _, _ -> 401 to """{"error":"UNAUTHORISED"}""" }.also { server = it }
        try {
            Api(srv.base, "NX-OLD-KEY").summary(null)
            fail("a refused key must not read as a summary")
        } catch (e: ApiError) {
            assertTrue(e.keyRefused)
            assertEquals("That admin key was not accepted.", e.message)
            assertFalse(Watch.noSummary(e))
        }
    }

    /* ---- 4.72.0, after review: a service without the summary is watched the old (1.7.1) way ---- */

    private val notFound get() = ApiError("NOT_FOUND", 404, "NOT_FOUND")

    @Test
    fun anOlderServiceIsWatchedTheOldWayAndToldOnce() = runBlocking {
        prefs.lastReleaseCheck = ist(8, 30).toEpochMilli()        // keep the release question out of this
        val s = FakeService().apply {
            summaryError = notFound
            inquiries = InquiryData(listOf(inquiry(40, "Meena", "Meena Sacks", "2026-10-02T03:05:00Z")))
            feedback = FeedbackData(listOf(report(6, "FEEDBACK", "Ravi Poly", "Thanks", "2026-10-01T10:00:00Z")))
            companies = listOf(company(12, "Old Plant", true, "2026-09-20T10:00:00Z"))
        }

        /* the first look: why, told once; the lists learned quietly, as 1.7.1's first look learned them */
        val first = Watch.look(prefs, s, ist(9, 0))
        assertEquals(listOf("Notifications need the service update"), first.map { it.title })
        assertEquals(Watch.TROUBLE_ID, first.single().id)
        assertEquals(40, prefs.lastInquiryId)
        assertEquals(6, prefs.lastFeedbackId)
        assertEquals(12, prefs.lastCompanyId)

        /* then what is new is told by name — and the reason is not said again */
        s.inquiries = InquiryData(
            listOf(
                inquiry(41, "Ravi", "Ravi Poly", "2026-10-02T03:40:00Z"),
                inquiry(40, "Meena", "Meena Sacks", "2026-10-02T03:05:00Z")
            )
        )
        s.feedback = FeedbackData(
            listOf(
                report(7, "BUG", "Om Poly Packs", "BOM will not save", "2026-10-02T03:41:00Z"),
                report(6, "FEEDBACK", "Ravi Poly", "Thanks", "2026-10-01T10:00:00Z")
            )
        )
        s.companies = listOf(
            company(14, "Shree Packaging", true, "2026-10-02T03:44:00Z"),
            company(13, "Made In The Console", false, null),          // the owner's own: not a registration
            company(12, "Old Plant", true, "2026-09-20T10:00:00Z")
        )
        val second = Watch.look(prefs, s, ist(9, 15)).associateBy { it.id }
        assertEquals(setOf(1001, 1004, 1002), second.keys)
        assertEquals("New enquiry", second[1001]!!.title)
        assertEquals("Ravi (Ravi Poly) — Bag Weight & Cost Forecasting", second[1001]!!.text)
        assertEquals("New problem report", second[1004]!!.title)
        assertEquals("Om Poly Packs — BOM will not save", second[1004]!!.text)
        assertEquals("New registration", second[1002]!!.title)
        assertEquals("Shree Packaging has registered and is on a demo.", second[1002]!!.text)
        assertEquals(14, prefs.lastCompanyId)

        /* nothing new, nothing told */
        assertTrue(Watch.look(prefs, s, ist(9, 30)).isEmpty())
        assertEquals("the summary is asked at every look, so it can take over", 3, s.sinces.size)
        assertEquals("the company listing once a look, as 1.7.1 asked it, and no more", 3, s.licenceCalls)

        /* and never at night, the old way included */
        val asked = s.calls
        assertTrue(Watch.look(prefs, s, ist(21, 0)).isEmpty())
        assertTrue(Watch.look(prefs, s, ist(5, 0)).isEmpty())
        assertEquals("not one request at night", asked, s.calls)
    }

    @Test
    fun whenTheServiceIsUpdatedTheSummaryTakesOverWithNothingMissedOrToldTwice() = runBlocking {
        prefs.lastReleaseCheck = ist(8, 30).toEpochMilli()
        /* the old way has been keeping the news since the morning */
        prefs.lastInquiryId = 41
        prefs.lastFeedbackId = 7
        prefs.lastCompanyId = 14
        prefs.toldNoSummary = true
        val s = FakeService().apply {
            /* the updated service's first answer counts its default day: not news */
            next = snap("2026-10-02T05:00:00Z", enquiries = 6, feedback = 3, registrations = 2)
            inquiries = InquiryData(
                listOf(
                    inquiry(42, "Asha", "Asha Bags", "2026-10-02T04:50:00Z"),
                    inquiry(41, "Ravi", "Ravi Poly", "2026-10-02T03:40:00Z")
                )
            )
            feedback = FeedbackData(listOf(report(7, "BUG", "Om Poly Packs", "BOM will not save", "2026-10-02T03:41:00Z")))
            companies = listOf(company(14, "Shree Packaging", true, "2026-10-02T03:44:00Z"))
        }

        val told = Watch.look(prefs, s, ist(10, 30))
        assertEquals("only what came since the old way's last look", listOf("New enquiry"), told.map { it.title })
        assertEquals("Asha (Asha Bags) — Bag Weight & Cost Forecasting", told.single().text)
        assertEquals("the summary's moment is learned", "2026-10-02T05:00:00Z", prefs.lastSummaryAt)
        assertFalse(prefs.toldNoSummary)
        assertEquals("the old way's marks are forgotten", 0, prefs.lastInquiryId + prefs.lastFeedbackId + prefs.lastCompanyId)

        /* from now on the summary alone: no list, and no company listing, in the background */
        s.next = snap("2026-10-02T05:15:00Z")
        assertTrue(Watch.look(prefs, s, ist(10, 45)).isEmpty())
        assertEquals("2026-10-02T05:00:00Z", s.sinces.last())
        assertEquals(1, s.inquiryCalls)
        assertEquals(1, s.feedbackCalls)

        s.next = snap("2026-10-02T05:30:00Z", enquiries = 1)
        s.inquiries = InquiryData(listOf(inquiry(43, "Kiran", "Kiran Woven", "2026-10-02T05:20:00Z")) + s.inquiries.inquiries)
        assertEquals("Kiran (Kiran Woven) — Bag Weight & Cost Forecasting", Watch.look(prefs, s, ist(11, 0)).single().text)
        assertEquals("the company listing was asked once, on the look that handed over", 1, s.licenceCalls)
    }

    @Test
    fun aListThatDoesNotComeKeepsTheOldWayUntilItDoes() = runBlocking {
        prefs.lastReleaseCheck = ist(8, 30).toEpochMilli()
        prefs.lastInquiryId = 41
        prefs.lastFeedbackId = 7
        prefs.lastCompanyId = 14
        val s = FakeService().apply {
            next = snap("2026-10-02T05:00:00Z")
            inquiries = InquiryData(listOf(inquiry(42, "Asha", "Asha Bags", "2026-10-02T04:50:00Z")))
            feedback = FeedbackData(listOf(report(7, "BUG", "Om Poly Packs", "BOM will not save", "2026-10-02T03:41:00Z")))
            licencesFail = true
        }
        assertEquals(listOf("New enquiry"), Watch.look(prefs, s, ist(10, 30)).map { it.title })
        assertNull("the summary does not take over while a list is missing", prefs.lastSummaryAt)
        assertEquals(42, prefs.lastInquiryId)
        assertEquals(14, prefs.lastCompanyId)

        s.licencesFail = false
        s.next = snap("2026-10-02T05:15:00Z")
        s.companies = listOf(
            company(15, "Kiran Woven", true, "2026-10-02T05:05:00Z"),
            company(14, "Shree Packaging", true, "2026-10-02T03:44:00Z")
        )
        val next = Watch.look(prefs, s, ist(10, 45))
        assertEquals("the enquiry is not told twice", listOf("New registration"), next.map { it.title })
        assertEquals("Kiran Woven has registered and is on a demo.", next.single().text)
        assertEquals("2026-10-02T05:15:00Z", prefs.lastSummaryAt)
        assertEquals(0, prefs.lastInquiryId + prefs.lastFeedbackId + prefs.lastCompanyId)
    }

    @Test
    fun anEmptyAnswerIsNotTakenForTheListOrForAFloodOfNews() = runBlocking {
        prefs.lastReleaseCheck = ist(8, 30).toEpochMilli()
        /* an error answer from an older service reads as an empty list */
        val s = FakeService().apply { summaryError = notFound }
        assertEquals(listOf("Notifications need the service update"), Watch.look(prefs, s, ist(9, 0)).map { it.title })
        assertEquals("nothing learned from nothing", 0, prefs.lastInquiryId + prefs.lastFeedbackId + prefs.lastCompanyId)

        /* the real list: learned quietly, not 57 enquiries at once */
        val all = (57 downTo 1).map { inquiry(it, "Lead $it", "Plant $it", "2026-09-30T10:00:00Z") }
        s.inquiries = InquiryData(all)
        assertTrue(Watch.look(prefs, s, ist(9, 15)).isEmpty())
        assertEquals(57, prefs.lastInquiryId)

        /* an empty answer after it moves nothing back, so the next real one is no news either */
        s.inquiries = InquiryData()
        assertTrue(Watch.look(prefs, s, ist(9, 30)).isEmpty())
        assertEquals(57, prefs.lastInquiryId)
        s.inquiries = InquiryData(all)
        assertTrue(Watch.look(prefs, s, ist(9, 45)).isEmpty())

        /* one really new */
        s.inquiries = InquiryData(listOf(inquiry(58, "Asha", "Asha Bags", "2026-10-02T04:20:00Z")) + all)
        assertEquals("New enquiry", Watch.look(prefs, s, ist(10, 0)).single().title)
        assertEquals(58, prefs.lastInquiryId)
    }

    @Test
    fun aSummaryThatGoesAwayHandsOverToTheOldWayWithoutAGap() = runBlocking {
        prefs.lastReleaseCheck = ist(8, 30).toEpochMilli()
        /* the summary answered up to 08:30 India time (03:00 UTC) … */
        prefs.lastSummaryAt = "2026-10-02T03:00:00.000Z"
        /* … then the service went back to a version without it (or answers in another form) */
        val s = FakeService().apply {
            summaryError = ApiError("not a summary", 200, Summary.NOT_A_SUMMARY)
            inquiries = InquiryData(
                listOf(
                    inquiry(41, "Ravi", "Ravi Poly", "2026-10-02T03:10:00Z"),
                    inquiry(40, "Meena", "Meena Sacks", "2026-10-02T02:55:00Z")
                )
            )
            feedback = FeedbackData(listOf(report(7, "FEEDBACK", "Om Poly Packs", "Nice", "2026-10-02T02:00:00Z")))
            companies = listOf(
                company(15, "Kiran Woven", true, "2026-10-02T08:40:00+05:30"),     // 03:10 UTC: after it
                company(12, "Old Plant", true, "2026-09-20T10:00:00Z")
            )
        }
        val told = Watch.look(prefs, s, ist(8, 45)).associateBy { it.id }
        assertEquals(setOf(Watch.TROUBLE_ID, 1001, 1002), told.keys)
        assertEquals("what came after the summary's last moment is told",
            "Ravi (Ravi Poly) — Bag Weight & Cost Forecasting", told[1001]!!.text)
        assertEquals("Kiran Woven has registered and is on a demo.", told[1002]!!.text)
        assertEquals(41, prefs.lastInquiryId)
        assertEquals(7, prefs.lastFeedbackId)
        assertEquals(15, prefs.lastCompanyId)
        assertEquals("kept for when the summary comes back", "2026-10-02T03:00:00.000Z", prefs.lastSummaryAt)
    }

    @Test
    fun aKeyRefusedOnTheOldWayStopsTheWatchToo() = runBlocking {
        val s = FakeService().apply {
            summaryError = notFound
            inquiriesError = ApiError("That admin key was not accepted.", 401, "UNAUTHORISED")
            release = Release(BuildConfig.VERSION_CODE + 1, "9.9.9", "https://example.invalid/c.apk", null, null, null, false, null)
        }
        assertEquals("Notifications stopped", Watch.look(prefs, s, ist(9, 0)).single().title)
        assertTrue(prefs.keyRejected)
        assertEquals("nothing more asked with the refused key, the release included", 0, s.feedbackCalls + s.licenceCalls + s.releaseCalls)
        assertTrue(Watch.look(prefs, s, ist(9, 15)).isEmpty())
        assertEquals(2, s.calls)
    }

    @Test
    fun aShutAddressEndsTheOldWaysLookAsWell() = runBlocking {
        val s = FakeService().apply {
            summaryError = notFound
            inquiriesError = ApiError("Too many wrong admin keys from this address — try again in 12 minutes.", 429, "TOO_MANY")
        }
        assertTrue(Watch.look(prefs, s, ist(9, 0)).isEmpty())
        assertEquals("the other lists and the release would only be refused too", 0, s.feedbackCalls + s.licenceCalls + s.releaseCalls)
        assertFalse("a shut address is not a refused key", prefs.keyRejected)
        assertFalse("the reason is told at the next look that gets through", prefs.toldNoSummary)
    }

    @Test
    fun anOlderServiceAllDayCostsNoMoreThan171Did() = runBlocking {
        /* the real calls, to a service without the summary: what reaches it is what it pays */
        val enquiries = AtomicReference(
            """[{"id":40,"name":"Meena","company":"Meena Sacks","product":"Bag Weight & Cost Forecasting","createdAt":"2026-10-02T03:05:00.000Z"}]"""
        )
        val hits = ConcurrentHashMap<String, AtomicInteger>()
        val srv = TinyServer { target, _ ->
            val path = target.substringBefore('?')
            hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            when (path) {
                "/admin/api/inquiries" -> 200 to """{"inquiries":${enquiries.get()}}"""
                "/admin/api/feedback" -> 200 to """{"feedback":[]}"""
                "/admin/api/licences" -> 200 to """{"companies":[{"id":12,"name":"Old Plant","self_registered":true}],"licences":[]}"""
                "/admin/api/app/latest" -> 200 to """{"release":null}"""
                else -> 404 to """{"error":"NOT_FOUND"}"""             // the summary: not on this service yet
            }
        }.also { server = it }
        val api = Api(srv.base, "NX-TEST-KEY")

        /* a look every quarter of an hour, all day and all night */
        val told = mutableListOf<Watch.News>()
        var looks = 0
        var t = LocalDate.of(2026, 10, 2).atStartOfDay(Watch.IST).toInstant()
        val end = t.plus(Duration.ofDays(1))
        while (t.isBefore(end)) {
            if (t == ist(14, 0)) enquiries.set(
                """[{"id":41,"name":"Ravi","company":"Ravi Poly","product":"Bag Weight & Cost Forecasting","createdAt":"2026-10-02T08:25:00.000Z"},""" +
                    """{"id":40,"name":"Meena","company":"Meena Sacks","product":"Bag Weight & Cost Forecasting","createdAt":"2026-10-02T03:05:00.000Z"}]"""
            )
            told += Watch.look(prefs, api, t)
            looks++
            t = t.plus(Duration.ofMinutes(15))
        }

        val daytime = 48                                       // 08:30, 08:45 … 20:15
        assertEquals(96, looks)
        assertEquals("the summary asked at every daytime look", daytime, hits["/admin/api/summary"]?.get())
        assertEquals("the company listing once a daytime look — 1.7.1 pulled it at all 96",
            daytime, hits["/admin/api/licences"]?.get())
        assertEquals(daytime, hits["/admin/api/inquiries"]?.get())
        assertEquals(daytime, hits["/admin/api/feedback"]?.get())
        assertEquals(listOf("Notifications need the service update", "New enquiry"), told.map { it.title })
        assertEquals("Ravi (Ravi Poly) — Bag Weight & Cost Forecasting", told.last().text)
        assertEquals("NX-TEST-KEY", srv.lastHeaders["x-admin-key"])
    }
}
