package org.nexoraofficial.console.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Audience
import org.nexoraofficial.console.data.Reach
import org.nexoraofficial.console.data.circularFor

/**
 * TELLING EVERY CUSTOMER SOMETHING AT ONCE.
 *
 * A new version, a new product, a service window: one message, every company
 * that should hear it. The app does not send the mail itself and holds no
 * password — it hands the finished message to the phone's own mail app with
 * every address already in BCC, and the owner reads it once more and presses
 * Send. Nothing leaves the phone without that press.
 *
 * BCC, not To, so no customer learns who the other customers are.
 */
@Composable
fun AnnounceCard(vm: ConsoleViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val circular = circularFor(vm.data.companies, vm.audience, vm.reach)

    ConsoleCard {
        Text("Tell the customers", style = CardTitleStyle)
        Small("— one message to every company that should hear it")

        Spacer(Modifier.height(12.dp))
        Text("Who hears it", color = LocalNexoraMuted(), style = MaterialBodySmall())
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Audience.entries.forEach { a ->
                val n = circularFor(vm.data.companies, a, vm.reach).addresses.size
                ConsoleButton(
                    (if (vm.audience == a) "● " else "○ ") + "${a.label} — $n",
                    { vm.audience = a },
                    kind = if (vm.audience == a) ButtonKind.Primary else ButtonKind.Default,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    center = true
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Help(vm.audience.why)

        /* 4.42.0 — and WHICH address at each of them. */
        Spacer(Modifier.height(12.dp))
        Text("Which address", color = LocalNexoraMuted(), style = MaterialBodySmall())
        Spacer(Modifier.height(6.dp))
        WrapRow {
            Reach.entries.forEach { r ->
                ConsoleButton(
                    r.label,
                    { vm.reach = r },
                    kind = if (vm.reach == r) ButtonKind.Primary else ButtonKind.Default,
                    small = true
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Help(vm.reach.why)

        Spacer(Modifier.height(12.dp))
        ConsoleField(
            "Subject",
            vm.announceSubject,
            { vm.announceSubject = it },
            Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        ConsoleField(
            "Message",
            vm.announceBody,
            { vm.announceBody = it },
            Modifier.fillMaxWidth().heightIn(min = 140.dp),
            singleLine = false,
            imeAction = ImeAction.Default
        )

        Spacer(Modifier.height(12.dp))
        MessageStrip(
            Msg(
                if (circular.addresses.isEmpty())
                    "No company in this group has an email address on record."
                else
                    "${circular.addresses.size} address(es) — ${circular.reached} compan" +
                        (if (circular.reached == 1) "y" else "ies") + " will be reached." +
                        if (circular.withoutEmail.isEmpty()) ""
                        else " ${circular.withoutEmail.size} have no email: " +
                            circular.withoutEmail.take(6).joinToString(", ") +
                            if (circular.withoutEmail.size > 6) "…" else "",
                if (circular.addresses.isEmpty()) Msg.Kind.WARN
                else if (circular.withoutEmail.isEmpty()) Msg.Kind.OK else Msg.Kind.WARN
            ),
            onDismiss = {}
        )

        Spacer(Modifier.height(12.dp))
        WrapRow {
            ConsoleButton(
                "Open in the mail app",
                {
                    if (circular.addresses.isEmpty()) {
                        vm.say("Nobody to send to.", Msg.Kind.ERR)
                    } else if (vm.announceSubject.isBlank() && vm.announceBody.isBlank()) {
                        vm.say("Write something to send first.", Msg.Kind.ERR)
                    } else {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_BCC, circular.addresses.toTypedArray())
                            putExtra(Intent.EXTRA_SUBJECT, vm.announceSubject)
                            putExtra(Intent.EXTRA_TEXT, vm.announceBody)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "Send with"))
                        } catch (_: ActivityNotFoundException) {
                            vm.say("No mail app on this phone. Copy the addresses instead.", Msg.Kind.ERR)
                        }
                    }
                },
                kind = ButtonKind.Primary,
                enabled = circular.addresses.isNotEmpty()
            )
            ConsoleButton("Copy the addresses", {
                if (circular.addresses.isEmpty()) {
                    vm.say("Nothing to copy.", Msg.Kind.ERR)
                } else {
                    clipboard.setText(AnnotatedString(circular.addresses.joinToString(", ")))
                    vm.say("${circular.addresses.size} address(es) copied.", Msg.Kind.OK)
                }
            })
            ConsoleButton("Clear", {
                vm.announceSubject = ""
                vm.announceBody = ""
            })
        }

        Spacer(Modifier.height(12.dp))
        Help(
            "The app sends nothing by itself and keeps no mail password. It fills your own mail " +
                "app in — every address in BCC, so no customer sees another customer — and you " +
                "read it once more and press Send there. Addresses come from two places: the one " +
                "each company registered with, and the people on its seats. Somebody with no " +
                "address set simply does not receive it; set one under Manage · People."
        )
    }
}

/* Two tiny helpers so this file does not import the theme twice over. */
@Composable
private fun LocalNexoraMuted() = org.nexoraofficial.console.ui.theme.LocalNexora.current.muted

@Composable
private fun MaterialBodySmall() = androidx.compose.material3.MaterialTheme.typography.bodySmall
