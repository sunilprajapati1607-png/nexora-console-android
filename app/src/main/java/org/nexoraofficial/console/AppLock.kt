package org.nexoraofficial.console

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "reliease pahela audit fix kriye"
 *
 * 1.8.0 — THE CONSOLE OPENS ONLY FOR THE PHONE'S OWNER.
 *
 * The console carries the master admin key: anyone holding the phone, already
 * unlocked, could license, suspend or delete any company on the service. So
 * the console asks for the phone's own fingerprint, face or screen lock every
 * time it is opened, and again when it comes back after two minutes away —
 * long enough to answer a call or save an Excel file without being asked
 * twice, short enough that a phone left on a desk does not stay open.
 *
 * A phone with no screen lock at all has nothing to ask, and the console will
 * not open on it: it says so and offers the phone's own screen-lock settings.
 *
 * This is only the state; MainActivity shows the prompt (it needs the
 * activity) and LockScreen draws the page. It lives in the view model, so a
 * turned screen does not lock again, and a new process always starts locked.
 */
class AppLock(private val now: () -> Long = { SystemClock.elapsedRealtime() }) {

    /** True until the owner has proved themselves; nothing of the console is shown meanwhile. */
    var locked by mutableStateOf(true)
        private set

    /** Whether the phone has a PIN, pattern, password or biometric at all. */
    var screenLockSet by mutableStateOf(true)

    /** True while the prompt is up, so a second tap does not stack another on it. */
    var asking by mutableStateOf(false)
        private set

    /** Why the last attempt did not unlock — null when the owner simply cancelled. */
    var problem by mutableStateOf<String?>(null)
        private set

    /* Counts each time the lock closes. The prompt comes up by itself once
       per closing; after a Cancel it waits for the Unlock button, or the
       prompt and the Cancel would chase each other round for ever. */
    var closings by mutableStateOf(1)
        private set

    /* When the console last left the screen. Elapsed real time, which keeps
       counting while the phone sleeps and cannot be wound back by changing
       the clock in Settings. */
    private var awayAt: Long? = null

    fun wentAway() {
        if (!locked) awayAt = now()
    }

    fun cameBack() {
        val since = awayAt
        awayAt = null
        if (!locked && since != null && now() - since >= AWAY_MS) {
            locked = true
            problem = null
            closings++
        }
    }

    /** False when a prompt is already up — the caller must not show another. */
    fun startAsking(): Boolean {
        if (asking || !locked) return false
        asking = true
        problem = null
        return true
    }

    fun unlocked() {
        locked = false
        asking = false
        problem = null
        awayAt = null
    }

    fun failed(why: String?) {
        asking = false
        problem = why
    }

    companion object {
        /** "returns after 2 minutes in the background" */
        const val AWAY_MS = 2 * 60 * 1000L
    }
}

/**
 * 4.72.0 — audit #36: THE PICTURE IN RECENT APPS.
 *
 * Android keeps a picture of an application's last screen for the recent-apps
 * list. The lock covers the console when it is opened again, but that picture
 * was taken before — the companies, licence keys and e-mails, for anyone who
 * swiped up on a phone left unlocked. On Android 13 and later the picture is
 * simply not taken (setRecentsScreenshotEnabled), and the owner's own
 * screenshots of the console still work. Older Android has only FLAG_SECURE,
 * which keeps the picture out and stops screenshots of the console as well.
 */
object Recents {
    fun keepOut(activity: Activity, sdk: Int = Build.VERSION.SDK_INT) {
        if (sdk >= Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                activity.setRecentsScreenshotEnabled(false)
                return
            } catch (_: Exception) {
                /* a trimmed build without it: the older way below */
            }
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
