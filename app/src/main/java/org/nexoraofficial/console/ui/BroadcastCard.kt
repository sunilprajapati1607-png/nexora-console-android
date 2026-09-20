package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel

/**
 * A MESSAGE FROM NEXORA INTO EVERY PLANT'S CONVERSATION  (1.5.0)
 *
 *   "pushed directly in chat so every user get and sender will be nexora
 *    with like update information"
 *
 * The same room the plant talks in, with Nexora as the speaker: every
 * user sees the small popup and the unread count, a version named here is
 * a tag that opens their update window, and the application looks for the
 * update the moment the message arrives. Withdraw takes it back from every
 * room; it stays there as "message removed".
 */
@Composable
fun BroadcastCard(vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {
    ConsoleCard {
        Text("Message every plant", style = CardTitleStyle)
        Small("— as “Nexora”, in each company’s conversation")

        Spacer(Modifier.height(12.dp))
        ConsoleField(
            "Message", vm.broadcastText,
            { vm.broadcastText = it },
            Modifier.fillMaxWidth(),
            placeholder = "Nexora 4.49.0 is published — Settings → Help → Updates installs it. What changed: …",
            singleLine = false,
            imeAction = ImeAction.Default
        )
        Spacer(Modifier.height(8.dp))
        ConsoleField(
            "Version it announces (optional)", vm.broadcastVersion,
            { vm.broadcastVersion = it },
            Modifier.fillMaxWidth(),
            placeholder = "e.g. 4.49.0",
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(10.dp))
        ConsoleButton(
            "Send to every plant",
            {
                onAsk(
                    Ask.Confirm(
                        title = "Send this to every plant?",
                        body = "It appears in every company's conversation as Nexora.",
                        confirmText = "Send"
                    ) { vm.sendBroadcast() }
                )
            },
            kind = ButtonKind.Primary,
            modifier = Modifier.fillMaxWidth(),
            enabled = vm.broadcastText.isNotBlank() && !vm.busy
        )
        Spacer(Modifier.height(8.dp))
        Why("Every user sees it with the small popup and the unread count. A version named here becomes a tag that opens the plant's update window, and the application looks for the update at once. Publishing a build sends one of these by itself.")
    }
}

@Composable
fun BroadcastsSentCard(vm: ConsoleViewModel, onAsk: (Ask) -> Unit) {
    ConsoleCard {
        Text("Sent", style = CardTitleStyle)
        if (vm.broadcasts.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Small("Nothing sent yet.")
        }
        for (b in vm.broadcasts) {
            Spacer(Modifier.height(12.dp))
            Text(b.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Small(b.atText + " · " + b.rooms + (if (b.rooms == 1) " room" else " rooms"), modifier = Modifier.weight(1f))
                ConsoleButton(
                    "Withdraw",
                    {
                        onAsk(
                            Ask.Confirm(
                                title = "Take this message back from every room?",
                                body = "It stays in each conversation as “message removed”.",
                                confirmText = "Withdraw",
                                danger = true
                            ) { vm.withdrawBroadcast(b.body) }
                        )
                    },
                    small = true
                )
            }
        }
    }
}
