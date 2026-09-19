package org.nexoraofficial.console

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.nexoraofficial.console.work.WatchWorker
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nexoraofficial.console.ui.AppScaffold
import org.nexoraofficial.console.ui.GateScreen
import org.nexoraofficial.console.ui.theme.LocalNexora
import org.nexoraofficial.console.ui.theme.NexoraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: ConsoleViewModel = viewModel()
            val systemDark = isSystemInDarkTheme()
            val askNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* granted or not, the console works the same */ }

            /* The phone's preference decides only where we START; after that
               the switch in the top bar decides, and it is remembered. */
            LaunchedEffect(Unit) {
                vm.startMode(systemDark)
                vm.resume()

                /* The quarter-hourly watch for new enquiries and new
                   registrations. Asking for the permission is the whole of
                   the setup; refusing it costs the notifications and nothing
                   else, so the answer is never insisted on. */
                WatchWorker.ensureChannel(this@MainActivity)
                WatchWorker.schedule(this@MainActivity)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            NexoraTheme(dark = vm.dark) {
                val c = LocalNexora.current

                SideEffect {
                    window.statusBarColor = c.bg.toArgb()
                    window.navigationBarColor = c.bg.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !c.isDark
                }

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
        }
    }
}
