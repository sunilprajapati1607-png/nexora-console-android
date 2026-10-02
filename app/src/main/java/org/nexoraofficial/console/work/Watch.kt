package org.nexoraofficial.console.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.nexoraofficial.console.BuildConfig
import org.nexoraofficial.console.MainActivity
import org.nexoraofficial.console.R
import org.nexoraofficial.console.data.Api
import org.nexoraofficial.console.data.ApiError
import org.nexoraofficial.console.data.Feedback
import org.nexoraofficial.console.data.Inquiry
import org.nexoraofficial.console.data.Prefs
import org.nexoraofficial.console.data.Summary
import org.nexoraofficial.console.data.WatchSource
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * "new enquiry ni ke user creation ni notification app ma madvi joiye"
 *
 * THE PHONE CHECKS, RATHER THAN BEING TOLD.
 *
 * A pushed notification would mean Firebase: another account, another key in
 * the service, a google-services.json in this repo and a second thing to keep
 * alive. For an owner's own phone watching their own service, asking every
 * quarter of an hour is the same answer for none of that.
 *
 * What counts as news is deliberately narrow: a new enquiry, a new feedback
 * or problem report, a plant that registered itself. Anything else — a state
 * changed, a seat taken — is not worth a sound.
 *
 * 4.72.0 — audit #43: every look used to pull the enquiries, the reports and
 * the full company listing (every licence key and staff e-mail, the
 * service's heaviest query) day and night, waking the free service at 3 a.m.
 * Now a look asks only GET /admin/api/summary — a few counts since the last
 * look — and fetches a short list only when that says something new arrived,
 * for the names on the notification. And it looks only between 08:30 and
 * 20:30 India time; whatever arrives overnight is told at the first look of
 * the morning.
 *
 * 4.72.0, after review — the watch no longer fails in silence. A service
 * without the summary (not updated yet), or one answering in another form,
 * is told once ("Notifications need the service update") and watched the
 * 1.7.1 way meanwhile (Watch.OldWay) — the lists, compared with the highest
 * ids already told, still only from 08:30 to 20:30 — so the news does not
 * stop; the summary is asked again at every look and takes over by itself
 * once the service is updated. A refused admin key (changed on the service)
 * is told once ("Notifications stopped") and then nothing is asked with it at
 * all until the owner signs in with the current key: every refused try counts
 * towards the service's lock on this address, which would also shut out the
 * web console on the same Wi-Fi.
 */
class WatchWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now()
        /* Night: nothing is asked of the service, not even the key read. */
        if (!Watch.awake(now)) return Result.success()

        val prefs = Prefs(applicationContext)
        /* 4.72.0 (review) — a key the service refused is not offered again
           until the owner signs in with the current one (Watch.look). */
        if (prefs.keyRejected) return Result.success()
        val key = prefs.adminKey
        if (key.isBlank()) return Result.success()   // signed out; nothing to watch

        val api = Api(prefs.baseUrl, key)
        Watch.look(prefs, api, now).forEach { notify(applicationContext, it.id, it.title, it.text) }
        return Result.success()
    }

    companion object {
        const val CHANNEL = "nexora.console.news"
        private const val UNIQUE = "nexora-console-watch"

        /** Called at every start: idempotent, so it cannot stack up. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL,
                "Enquiries, reports and registrations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A new enquiry from the website, a feedback or problem report from a plant, or a plant that has just registered."
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        private fun notify(context: Context, id: Int, title: String, text: String) {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                /* Nexora's own mark, as the owner asked — the same N the
                   launcher wears, drawn as the silhouette a status bar needs. */
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, n)
            } catch (_: SecurityException) {
                /* Android 13 and up, notifications not granted. Nothing to do:
                   the console still shows everything when it is opened. */
            }
        }
    }
}

/**
 * 4.72.0 — ONE LOOK, apart from the worker so it can be tried on the computer
 * with a stand-in service and a stand-in clock.
 */
object Watch {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    val OPENS: LocalTime = LocalTime.of(8, 30)
    val CLOSES: LocalTime = LocalTime.of(20, 30)

    /** Whether a newer console was published is asked at most this often. */
    const val RELEASE_EVERY_MS = 3L * 60 * 60 * 1000

    /** One notification: the id keeps one of each kind in the status bar. */
    data class News(val id: Int, val title: String, val text: String)

    /** 08:30 up to (not including) 20:30, India time, whatever zone the phone is set to. */
    fun awake(now: Instant): Boolean {
        val t = now.atZone(IST).toLocalTime()
        return !t.isBefore(OPENS) && t.isBefore(CLOSES)
    }

    /** The status-bar id of the one notice about the watch itself (it replaces itself). */
    const val TROUBLE_ID = 1005

    /**
     * The service answered, but with no summary this console can read: an
     * older service without the route (its own 404 NOT_FOUND), or an answer
     * not in the agreed form. Not a passing failure like a sleeping service.
     */
    fun noSummary(e: ApiError): Boolean =
        (e.status == 404 && e.code == "NOT_FOUND") || e.code == Summary.NOT_A_SUMMARY

    /** Told once while the service gives no summary; the news itself goes on the old way. */
    val NEEDS_UPDATE = News(
        TROUBLE_ID,
        "Notifications need the service update",
        "The service does not answer this console's quick check yet, so new enquiries, reports and " +
            "registrations are looked for the older, heavier way meanwhile (still only from 08:30 to 20:30). " +
            "The quick check takes over by itself once the service is updated."
    )

    /** What to tell the owner now; what was learned is written to [prefs]. */
    suspend fun look(prefs: Prefs, src: WatchSource, now: Instant): List<News> {
        /* Night: nothing is asked of the service — the old way included. */
        if (!awake(now)) return emptyList()
        /* 4.72.0 (review) — the service refused this key: nothing is asked
           with it again (each refusal counts towards the lock on this
           address) until the owner signs in with the current key. */
        if (prefs.keyRejected) return emptyList()
        val out = mutableListOf<News>()
        var askRelease = true

        try {
            out += news(prefs, src)
        } catch (e: ApiError) {
            /* Told once; and the release question is not asked with it either. */
            if (e.keyRefused) return listOf(keyRefused(prefs))
            /* This address is shut out for a while (too many wrong admin
               keys from it): the release question would only be refused too. */
            if (e.status == 429) askRelease = false
            /* Otherwise asleep or offline: nothing learned, so the next look
               asks again from the same moment and misses nothing. */
        } catch (_: Exception) {
        }

        /* 4.44.0 — and whether a newer build of this very application has
           been published. Told once per version; asked every few hours. */
        val last = prefs.lastReleaseCheck
        val t = now.toEpochMilli()
        if (askRelease && (last <= 0L || t < last || t - last >= RELEASE_EVERY_MS)) {
            try {
                val release = src.latestRelease()
                prefs.lastReleaseCheck = t
                if (release != null &&
                    release.versionCode > BuildConfig.VERSION_CODE &&
                    release.versionCode > prefs.lastOfferedVersion
                ) {
                    prefs.lastOfferedVersion = release.versionCode
                    out += News(
                        1003,
                        "Console ${release.versionName} is ready",
                        release.notes?.takeIf { it.isNotBlank() } ?: "Open the console to download and install it."
                    )
                }
            } catch (e: ApiError) {
                if (e.keyRefused) out += keyRefused(prefs)
            } catch (_: Exception) {
            }
        }
        return out
    }

    /* The summary's news — or, from a service that gives none, the old way's. */
    private suspend fun news(prefs: Prefs, src: WatchSource): List<News> {
        val since = prefs.lastSummaryAt
        val s = try {
            src.summary(since)
        } catch (e: ApiError) {
            if (!noSummary(e)) throw e
            /* No summary to be had: the news comes the old way, and the owner
               is told why once — not every quarter of an hour. The summary is
               asked again at every look, so it takes over by itself. */
            val old = OldWay.look(prefs, src)
            if (prefs.toldNoSummary) return old.news
            prefs.toldNoSummary = true
            return listOf(NEEDS_UPDATE) + old.news
        }
        val out = when {
            /* The summary is back (the service was updated) after the old way
               kept the news. The old way's marks know exactly what it told, so
               they tell what came since its last look, this one last time:
               the summary's counts would count some of that again. If a list
               does not come, the marks stay and the next look tries again. */
            OldWay.running(prefs) -> {
                val old = OldWay.look(prefs, src)
                if (!old.complete) return old.news
                prefs.forgetOldWay()
                old.news
            }
            /* first look: learn the moment, do not shout */
            since == null -> emptyList()
            else -> counted(s, src)
        }
        prefs.lastSummaryAt = s.at
        if (prefs.toldNoSummary) prefs.toldNoSummary = false
        return out
    }

    private suspend fun counted(s: Summary, src: WatchSource): List<News> {
        val out = mutableListOf<News>()
        if (s.newEnquiries > 0) out += enquiries(src, s.newEnquiries)
        if (s.newFeedback > 0) out += reports(src, s.newFeedback)
        if (s.newRegistrations > 0) out += registrationNews(s.newRegistrations, emptyList())
        return out
    }

    /* Told once: the flag stops every later look until the owner signs in. */
    private fun keyRefused(prefs: Prefs): News {
        prefs.keyRejected = true
        return News(
            TROUBLE_ID,
            "Notifications stopped",
            "The service did not accept the admin key kept on this phone (it may have been changed). " +
                "Open the console and sign in with the current key to start them again."
        )
    }

    /* The names come from the short enquiry list, fetched only now that the
       summary says there is news; if that list will not come, the count alone. */
    private suspend fun enquiries(src: WatchSource, n: Int): News {
        val fresh = try {
            src.inquiries().inquiries.sortedByDescending { it.createdAt ?: "" }.take(n)
        } catch (_: Exception) {
            emptyList()
        }
        return enquiryNews(n, fresh)
    }

    /* 1.4.0 — a problem report is the one thing here that may mean a plant is
       stuck, so it is named as such. */
    private suspend fun reports(src: WatchSource, n: Int): News {
        val fresh = try {
            src.feedback().feedback.sortedByDescending { it.createdAt ?: "" }.take(n)
        } catch (_: Exception) {
            emptyList()
        }
        return reportNews(n, fresh)
    }

    /* [fresh]: the new ones known by name, newest first — none when their list would not come. */
    private fun enquiryNews(n: Int, fresh: List<Inquiry>): News {
        val text = fresh.take(3).joinToString(" · ") {
            it.name + (if (!it.company.isNullOrBlank()) " (${it.company})" else "") + " — " + it.product
        }
        return News(
            1001,
            if (n == 1) "New enquiry" else "$n new enquiries",
            text.ifBlank { "Open the console to see " + (if (n == 1) "it." else "them.") }
        )
    }

    private fun reportNews(n: Int, fresh: List<Feedback>): News {
        val bugs = fresh.count { it.isBug }
        val title = when {
            n == 1 && bugs == 1 -> "New problem report"
            n == 1 && fresh.isNotEmpty() -> "New feedback"
            n == 1 -> "New report"
            bugs > 0 -> "$n new reports, $bugs problem" + (if (bugs == 1) "" else "s")
            fresh.isNotEmpty() -> "$n new feedback"
            else -> "$n new reports"
        }
        val text = fresh.take(3).joinToString(" · ") { it.plant + " — " + (it.subject ?: it.message.take(60)) }
        return News(1004, title, text.ifBlank { "Open the console to read " + (if (n == 1) "it." else "them.") })
    }

    /* From the summary a registration is told without a name: the company
       listing carries every licence key and is not pulled in the background.
       The old way has that listing anyway, so it names the plants, as 1.7.1 did. */
    private fun registrationNews(n: Int, names: List<String>) = News(
        1002,
        if (n == 1) "New registration" else "$n new registrations",
        when {
            names.isEmpty() && n == 1 -> "A plant has registered and is on a demo. Open the console to see it."
            names.isEmpty() -> "$n plants have registered and are on a demo. Open the console to see them."
            n == 1 -> names[0] + " has registered and is on a demo."
            else -> names.take(3).joinToString(" · ") + (if (n > 3) " and ${n - 3} more" else "") +
                " have registered and are on a demo."
        }
    )

    /**
     * 4.72.0 (review) — THE OLD WAY, while the service gives no summary (not
     * updated yet, or answering in another form): 1.7.1's own look. The
     * enquiry and report lists and the company listing are compared with the
     * highest ids already told (Prefs.lastInquiryId, lastFeedbackId,
     * lastCompanyId), so an older service does not silence the notifications.
     *
     * It asks no more than 1.7.1 did — each list once a look, the company
     * listing (every licence key; 1.7.1 pulled it every quarter of an hour,
     * day and night) included — and less often, because every look, this one
     * too, is made only from 08:30 to 20:30. Each list stands alone: one that
     * will not come costs only its own news this time and is asked again at
     * the next look. A refused key or a shut address ends the whole look.
     */
    private object OldWay {

        /** What the old way told, and whether every list came. */
        class Told(val news: List<News>, val complete: Boolean)

        /** Whether the old way has learned anything the summary must take over from. */
        fun running(prefs: Prefs): Boolean =
            prefs.lastInquiryId > 0 || prefs.lastFeedbackId > 0 || prefs.lastCompanyId > 0

        suspend fun look(prefs: Prefs, src: WatchSource): Told {
            val out = mutableListOf<News>()
            var complete = true
            /* The summary's last moment, when it ever answered: a list met for
               the first time then tells what came after it, so nothing falls
               between the summary's last look and the old way's first. */
            val since = moment(prefs.lastSummaryAt)

            val inquiries = fetch { src.inquiries().inquiries }
            if (inquiries == null) complete = false else {
                val (fresh, mark) = unseen(inquiries, prefs.lastInquiryId, since, { it.id }, { it.createdAt })
                if (mark != prefs.lastInquiryId) prefs.lastInquiryId = mark
                if (fresh.isNotEmpty()) out += enquiryNews(fresh.size, fresh)
            }

            val reports = fetch { src.feedback().feedback }
            if (reports == null) complete = false else {
                val (fresh, mark) = unseen(reports, prefs.lastFeedbackId, since, { it.id }, { it.createdAt })
                if (mark != prefs.lastFeedbackId) prefs.lastFeedbackId = mark
                if (fresh.isNotEmpty()) out += reportNews(fresh.size, fresh)
            }

            /* Every company moves the mark; only a plant that registered
               itself is news (one the owner made in the console is not). */
            val companies = fetch { src.licences().companies }
            if (companies == null) complete = false else {
                val (fresh, mark) = unseen(companies, prefs.lastCompanyId, since, { it.id }, { it.registeredAt })
                if (mark != prefs.lastCompanyId) prefs.lastCompanyId = mark
                val plants = fresh.filter { it.selfRegistered }
                if (plants.isNotEmpty()) out += registrationNews(plants.size, plants.map { it.name })
            }
            return Told(out, complete)
        }

        /* A list, or null when it would not come this time. A refused key or
           a shut address ends the whole look (thrown on to Watch.look). */
        private suspend fun <T> fetch(get: suspend () -> T): T? = try {
            get()
        } catch (e: ApiError) {
            if (e.keyRefused || e.status == 429) throw e
            null
        } catch (_: Exception) {
            null
        }

        /*
         * The ones not told yet (newest first) and the new mark. An empty list
         * — none yet, or an answer that was not a list (a service error reads
         * as none) — neither learns nor moves the mark, so a real list after
         * it is not taken for a flood of news. A list met for the first time
         * is learned quietly, as 1.7.1's first look was — unless the summary
         * had answered before, when what came after its last moment is told.
         * A mark never goes down (a deleted enquiry is no reason to tell the
         * next one twice).
         */
        private fun <T> unseen(
            list: List<T>, mark: Int, since: Instant?, id: (T) -> Int, at: (T) -> String?
        ): Pair<List<T>, Int> {
            if (list.isEmpty()) return emptyList<T>() to mark
            val fresh = when {
                mark > 0 -> list.filter { id(it) > mark }
                since != null -> list.filter { moment(at(it))?.isAfter(since) == true }
                else -> emptyList()
            }
            return fresh.sortedByDescending(id) to maxOf(mark, list.maxOf(id))
        }
    }

    /* An ISO moment with its zone ("…Z" or "…+05:30"), or null. */
    private fun moment(s: String?): Instant? {
        if (s.isNullOrBlank()) return null
        return runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
    }
}
