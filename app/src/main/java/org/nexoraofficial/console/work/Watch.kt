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
import org.nexoraofficial.console.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * "new enquiry ni ke user creation ni notification app ma madvi joiye"
 *
 * THE PHONE CHECKS, RATHER THAN BEING TOLD.
 *
 * A pushed notification would mean Firebase: another account, another key in
 * the service, a google-services.json in this repo and a second thing to keep
 * alive. For an owner's own phone watching their own service, asking every
 * quarter of an hour is the same answer for none of that — and it works with
 * the service exactly as it is, with no server change at all.
 *
 * What counts as news is deliberately narrow: an enquiry whose id is higher
 * than the highest seen, or a company that did not exist last time. Anything
 * else — a state changed, a seat taken — is not worth a sound at night.
 */
class WatchWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val key = prefs.adminKey
        if (key.isBlank()) return Result.success()   // signed out; nothing to watch

        val api = Api(prefs.baseUrl, key)

        /* Enquiries. */
        try {
            val fresh = api.inquiries().inquiries
            val highest = fresh.maxOfOrNull { it.id } ?: 0
            val seen = prefs.lastInquiryId
            if (seen == 0) {
                prefs.lastInquiryId = highest          // first run: learn, do not shout
            } else if (highest > seen) {
                val fresh_ = fresh.filter { it.id > seen }
                prefs.lastInquiryId = highest
                notify(
                    applicationContext,
                    id = 1001,
                    title = if (fresh_.size == 1) "New enquiry" else "${fresh_.size} new enquiries",
                    text = fresh_.take(3).joinToString(" · ") {
                        it.name + (if (!it.company.isNullOrBlank()) " (${it.company})" else "") +
                            " — " + it.product
                    }
                )
            }
        } catch (_: Exception) {
            /* Asleep, offline, or an older service: try again in fifteen minutes. */
        }

        /* 1.4.0 — feedback and problem reports from inside the application.
           A problem report is the one thing here that may mean a plant is
           stuck, so it is named as such. */
        try {
            val fresh = api.feedback().feedback
            val highest = fresh.maxOfOrNull { it.id } ?: 0
            val seen = prefs.lastFeedbackId
            if (seen == 0) {
                prefs.lastFeedbackId = highest          // first run: learn, do not shout
            } else if (highest > seen) {
                val fresh_ = fresh.filter { it.id > seen }
                prefs.lastFeedbackId = highest
                val bugs = fresh_.count { it.isBug }
                notify(
                    applicationContext,
                    id = 1004,
                    title = when {
                        fresh_.size == 1 && bugs == 1 -> "New problem report"
                        fresh_.size == 1 -> "New feedback"
                        bugs > 0 -> "${fresh_.size} new reports, ${bugs} problem" + (if (bugs == 1) "" else "s")
                        else -> "${fresh_.size} new feedback"
                    },
                    text = fresh_.take(3).joinToString(" · ") {
                        it.plant + " — " + (it.subject ?: it.message.take(60))
                    }
                )
            }
        } catch (_: Exception) {
        }

        /* Companies — somebody registered themselves from the application. */
        try {
            val companies = api.licences().companies
            val count = companies.size
            val seen = prefs.lastCompanyCount
            if (seen < 0) {
                prefs.lastCompanyCount = count
            } else if (count > seen) {
                prefs.lastCompanyCount = count
                val newest = companies.firstOrNull { it.selfRegistered } ?: companies.firstOrNull()
                notify(
                    applicationContext,
                    id = 1002,
                    title = if (count - seen == 1) "New registration" else "${count - seen} new registrations",
                    text = (newest?.name ?: "A plant") + " has registered and is on a demo."
                )
            } else if (count < seen) {
                prefs.lastCompanyCount = count         // one was deleted; just keep up
            }
        } catch (_: Exception) {
        }

        /* 4.44.0 — and whether a newer build of this very application has
           been published. Told once per version: a notice that repeats every
           quarter of an hour until you give in is not a notice, it is
           nagging. */
        try {
            val release = api.latestRelease()
            if (release != null &&
                release.versionCode > BuildConfig.VERSION_CODE &&
                release.versionCode > prefs.lastOfferedVersion
            ) {
                prefs.lastOfferedVersion = release.versionCode
                notify(
                    applicationContext,
                    id = 1003,
                    title = "Console ${release.versionName} is ready",
                    text = (release.notes?.takeIf { it.isNotBlank() }
                        ?: "Open the console to download and install it.")
                )
            }
        } catch (_: Exception) {
        }

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
