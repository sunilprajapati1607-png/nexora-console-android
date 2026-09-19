package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.BuildConfig
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.data.Download
import org.nexoraofficial.console.data.Fmt

/**
 * UPDATING THE CONSOLE FROM INSIDE THE CONSOLE.
 *
 * Three states and no more: there is a newer build, it is coming down, it is
 * ready to install. The phone still asks once whether this application may
 * install another — that is Android's question, not ours, and the card says
 * so plainly rather than failing silently at the last tap.
 */
@Composable
fun UpdateCard(vm: ConsoleViewModel) {
    val r = vm.release ?: return
    if (!vm.updateAvailable) return

    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Version ${r.versionName} is ready", style = CardTitleStyle)
                Small(
                    "you are running ${BuildConfig.VERSION_NAME}" +
                        (if (r.publishedAt != null) " · published ${Fmt.day(r.publishedAt)}" else "")
                )
            }
            if (r.mandatory) Pill("must install", "EXPIRED") else Pill("new", "TRIAL")
        }

        if (!r.notes.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Help(r.notes)
        }

        Spacer(Modifier.height(14.dp))

        when (val d = vm.download) {
            is Download.Running -> {
                val mb = { b: Long -> String.format("%.1f MB", b / 1_048_576.0) }
                Text(
                    if (d.totalBytes > 0)
                        "Fetching — ${mb(d.readBytes)} of ${mb(d.totalBytes)}"
                    else "Fetching — ${mb(d.readBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Bar(d.fraction)
            }

            is Download.Ready -> {
                MessageStrip(
                    org.nexoraofficial.console.Msg(
                        "Downloaded and checked. Install replaces this application; nothing you " +
                            "have signed into is lost.",
                        org.nexoraofficial.console.Msg.Kind.OK
                    ),
                    onDismiss = {}
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConsoleButton("Install now", { vm.installUpdate() }, kind = ButtonKind.Primary)
                    ConsoleButton("Not yet", { vm.clearDownload() })
                }
            }

            is Download.Failed -> {
                MessageStrip(
                    org.nexoraofficial.console.Msg(d.why, org.nexoraofficial.console.Msg.Kind.ERR),
                    onDismiss = { vm.clearDownload() }
                )
                Spacer(Modifier.height(12.dp))
                ConsoleButton("Try again", { vm.downloadUpdate() }, kind = ButtonKind.Primary)
            }

            Download.Idle -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConsoleButton("Download and install", { vm.downloadUpdate() }, kind = ButtonKind.Primary)
                    ConsoleButton("Check again", { vm.checkForUpdate(loud = true) })
                }
                if (r.sizeBytes != null) {
                    Spacer(Modifier.height(8.dp))
                    Small(String.format("about %.1f MB over your connection", r.sizeBytes / 1_048_576.0))
                }
            }
        }
    }
}
