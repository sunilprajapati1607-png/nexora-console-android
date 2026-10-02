package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * 1.8.0 — the page in front of the console until the owner has unlocked it
 * (see AppLock). It is drawn OVER the console, not instead of it: the console
 * underneath keeps its place, its open tab and a save-as still in flight —
 * it simply cannot be seen, touched or read aloud until the lock opens.
 *
 * Two faces. The usual one: the brand, a fingerprint and an Unlock button
 * (the prompt also comes up by itself). And the one for a phone with no
 * screen lock, which refuses to open and sends the owner to set one.
 */
@Composable
fun LockScreen(
    screenLockSet: Boolean,
    asking: Boolean,
    problem: String?,
    onUnlock: () -> Unit,
    onSetScreenLock: () -> Unit
) {
    val c = LocalNexora.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            /* Swallows every touch, so nothing reaches the console beneath. */
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = 420.dp)) {
            Spacer(Modifier.height(72.dp))
            Brand("Licence console")
            Spacer(Modifier.height(14.dp))

            ConsoleCard(padding = 22) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (screenLockSet) Icons.Outlined.Fingerprint else Icons.Outlined.LockOpen,
                        contentDescription = null,
                        tint = if (screenLockSet) MaterialTheme.colorScheme.primary else c.bad,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    if (screenLockSet) {
                        Text("Unlock the console", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Help(
                            "Your fingerprint, face or the phone's screen lock — the console holds " +
                                "the key to every company, so it asks every time it is opened.",
                            Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "This phone has no screen lock",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Help(
                            "The console holds the key to every company, so it opens only on a phone " +
                                "that is locked. Set a PIN, pattern, password or fingerprint, then come back.",
                            Modifier.fillMaxWidth()
                        )
                    }

                    problem?.let {
                        Spacer(Modifier.height(10.dp))
                        MessageStrip(Msg(it, Msg.Kind.ERR), onDismiss = {})
                    }

                    Spacer(Modifier.height(18.dp))
                    if (screenLockSet) {
                        ConsoleButton(
                            if (asking) "Waiting…" else "Unlock",
                            onUnlock,
                            kind = ButtonKind.Primary,
                            enabled = !asking,
                            modifier = Modifier.fillMaxWidth(),
                            center = true
                        )
                    } else {
                        ConsoleButton(
                            "Set a screen lock",
                            onSetScreenLock,
                            kind = ButtonKind.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            center = true
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Nothing of the console is shown until it is unlocked.",
                color = c.faint,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
