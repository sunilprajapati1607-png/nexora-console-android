package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * #gate — one card, the brand, and the admin key.
 *
 * The phone adds two things the browser did not need: the service address,
 * because a phone cannot simply be pointed at a different host by typing in
 * the bar, and a choice about whether the key is kept on the device.
 */
@Composable
fun GateScreen(vm: ConsoleViewModel) {
    val c = LocalNexora.current
    var showService by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = 420.dp)) {
            Spacer(Modifier.height(72.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Brand("Licence console")
                ModeSwitch(vm.dark, vm::flipMode)
            }

            Spacer(Modifier.height(14.dp))

            ConsoleCard {
                Help("Enter the admin key (NEXORA_ADMIN_KEY on the service).")

                vm.gateError?.let {
                    Spacer(Modifier.height(10.dp))
                    MessageStrip(Msg(it, Msg.Kind.ERR), onDismiss = {})
                }

                Spacer(Modifier.height(12.dp))
                ConsoleField(
                    label = null,
                    value = vm.key,
                    onValueChange = { vm.key = it },
                    placeholder = "Admin key",
                    password = true,
                    imeAction = ImeAction.Go,
                    onImeAction = { vm.load() }
                )

                Spacer(Modifier.height(8.dp))
                ConsoleCheck(
                    vm.rememberKey,
                    { vm.rememberKey = it },
                    "Keep the key on this phone"
                )

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConsoleButton(
                        if (showService) "Hide service" else "Service…",
                        { showService = !showService },
                        small = true
                    )
                    Spacer(Modifier.weight(1f))
                    ConsoleButton(
                        if (vm.busy) "Opening…" else "Open",
                        { vm.load() },
                        kind = ButtonKind.Primary,
                        enabled = !vm.busy
                    )
                }

                if (showService) {
                    Spacer(Modifier.height(12.dp))
                    ConsoleField(
                        label = "Service address",
                        value = vm.baseUrl,
                        onValueChange = { vm.baseUrl = it },
                        placeholder = "https://…",
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(6.dp))
                    Help(
                        "The same service the application talks to. Leave it alone unless " +
                            "you are pointing this at a staging copy."
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "The key is never backed up off this phone. Sign out erases it.",
                color = c.faint,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
