package org.nexoraofficial.console

import android.Manifest
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.nexoraofficial.console.work.WatchWorker
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.view.WindowCompat
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.GateScreen
import org.nexoraofficial.console.ui.LockScreen
import org.nexoraofficial.console.ui.theme.LocalNexora
import org.nexoraofficial.console.ui.theme.NexoraTheme

/**
 * 1.8.0 — a FragmentActivity (not a plain ComponentActivity) because the
 * fingerprint prompt is a fragment of its own and needs one to sit in, as in
 * Nexora Mobile.
 */
class MainActivity : FragmentActivity() {

    /* The same view model the screens get from viewModel(): one per activity. */
    private val vm: ConsoleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* 4.72.0 — audit #36: no picture of the console in recent apps */
        Recents.keepOut(this)

        setContent {
            val lock = vm.lock
            val systemDark = isSystemInDarkTheme()
            val askNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* granted or not, the console works the same */ }

            /* The phone's preference decides only where we START; after that
               the switch in the top bar decides, and it is remembered. */
            LaunchedEffect(Unit) {
                vm.startMode(systemDark)

                /* The quarter-hourly watch for new enquiries and new
                   registrations. It reads the sealed key itself and shows
                   nothing on the screen, so it is set up behind the lock. */
                WatchWorker.ensureChannel(this@MainActivity)
                WatchWorker.schedule(this@MainActivity)
            }

            /* 1.8.0 — nothing is fetched until the owner has unlocked: the
               remembered key opens the console only after the phone's own
               lock has said who is holding it. */
            var askedNotifications by remember { mutableStateOf(false) }
            LaunchedEffect(lock.locked) {
                if (lock.locked) return@LaunchedEffect
                vm.resume()

                /* Asking for the permission is the whole of the setup;
                   refusing it costs the notifications and nothing else, so
                   the answer is never insisted on. Asked after the unlock, so
                   the two system dialogs do not fight over the screen. */
                if (!askedNotifications &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    askedNotifications = true
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            /* The prompt comes up by itself once each time the lock closes
               (and again once a screen lock has been set); after a Cancel it
               waits for the Unlock button. */
            LaunchedEffect(lock.closings, lock.screenLockSet) {
                if (lock.locked && lock.screenLockSet) askToUnlock()
            }

            NexoraTheme(dark = vm.dark) {
                val c = LocalNexora.current

                SideEffect {
                    window.statusBarColor = c.bg.toArgb()
                    window.navigationBarColor = c.bg.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !c.isDark
                }

                /* A field left focused must not bring the keyboard up over the lock. */
                val focus = LocalFocusManager.current
                LaunchedEffect(lock.locked) { if (lock.locked) focus.clearFocus(force = true) }

                Box(Modifier.fillMaxSize()) {
                    /* While locked the console is still composed underneath —
                       so its open tab and a save-as in flight survive — but it
                       says nothing to a screen reader, and LockScreen covers it
                       and takes every touch. */
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (lock.locked) Modifier.clearAndSetSemantics { } else Modifier)
                    ) {
                        /* The console proper is a Scaffold and handles its own
                           insets, top bar to navigation bar. The gate is one card on
                           an empty page, so it is the one that needs them applied. */
                        if (vm.signedIn) {
                            AppScaffold(vm)
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(c.bg)
                                    .safeDrawingPadding()
                            ) {
                                GateScreen(vm)
                            }
                        }
                    }

                    if (lock.locked) {
                        /* Back on the lock leaves the console; it never reaches the screens beneath. */
                        BackHandler { moveTaskToBack(true) }
                        LockScreen(
                            screenLockSet = lock.screenLockSet,
                            asking = lock.asking,
                            problem = lock.problem,
                            onUnlock = ::askToUnlock,
                            onSetScreenLock = ::openScreenLockSettings
                        )
                    }
                }
            }
        }
    }

    /* "returns after 2 minutes in the background" — the clock starts when the
       console leaves the screen and is read when it comes back. */
    override fun onStart() {
        super.onStart()
        vm.lock.cameBack()
    }

    override fun onStop() {
        super.onStop()
        vm.lock.wentAway()
    }

    /* Read again every time the console comes to the front, so a screen lock
       set (or removed) in Settings is noticed the moment the owner returns. */
    override fun onResume() {
        super.onResume()
        vm.lock.screenLockSet = hasScreenLock()
    }

    private fun hasScreenLock(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * The phone's own prompt: fingerprint or face where there is one, and
     * always the PIN, pattern or password as the way round it — a cut finger
     * must not lock the owner out of their own console.
     */
    private fun askToUnlock() {
        val lock = vm.lock
        if (!hasScreenLock()) {
            lock.screenLockSet = false
            return
        }
        if (!lock.startAsking()) return
        try {
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                        lock.unlocked()

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            /* The owner said no, or the prompt was put away with
                               the console: stay locked, say nothing. */
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> lock.failed(null)
                            /* The screen lock was taken off while the prompt was up. */
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                                lock.failed(null)
                                lock.screenLockSet = hasScreenLock()
                            }
                            else -> lock.failed(errString.toString())
                        }
                    }
                    /* onAuthenticationFailed — a finger not recognised: the
                       prompt stays up and says so itself. */
                }
            ).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Open Nexora Console")
                    .setSubtitle("The licence console")
                    .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            )
        } catch (e: Exception) {
            lock.failed("The phone's lock could not be shown. ${e.message ?: ""}".trim())
        }
    }

    /* The phone's own "choose a screen lock" page; older or trimmed builds
       without it get the security settings, and failing that, Settings. */
    private fun openScreenLockSettings() {
        val tries = listOf(
            Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in tries) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }
}
