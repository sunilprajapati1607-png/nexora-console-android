package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * Service settings — they apply to every installation from its next check.
 * The same six controls, in the same order, with the same paragraph under
 * them explaining what registration and anonymous demos actually mean.
 */
@Composable
fun SettingsCard(vm: ConsoleViewModel) {
    val f = vm.settingsForm

    ConsoleCard {
        Text("Service settings", style = CardTitleStyle)
        Small("— apply to every installation from its next check")

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConsoleField(
                "Demo length, days", f.trialDays,
                { vm.settingsForm = f.copy(trialDays = it) },
                Modifier.weight(1f), numeric = true
            )
            ConsoleField(
                "Offline, days", f.demoGraceDays,
                { vm.settingsForm = f.copy(demoGraceDays = it) },
                Modifier.weight(1f), numeric = true
            )
        }

        Spacer(Modifier.height(10.dp))
        ConsoleField(
            "Working window, minutes", f.sessionMinutes,
            { vm.settingsForm = f.copy(sessionMinutes = it) },
            Modifier.fillMaxWidth(), numeric = true
        )

        Spacer(Modifier.height(12.dp))
        Text("When a licence ends", color = LocalNexora.current.muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice(
                selected = f.expiredMode == "READONLY",
                label = "Read-only — saved work still opens and prints",
                onClick = { vm.settingsForm = f.copy(expiredMode = "READONLY") }
            )
            Choice(
                selected = f.expiredMode == "HARDSTOP",
                label = "Hard stop",
                onClick = { vm.settingsForm = f.copy(expiredMode = "HARDSTOP") }
            )
        }

        Spacer(Modifier.height(8.dp))
        ConsoleCheck(
            f.signupsOpen,
            { vm.settingsForm = f.copy(signupsOpen = it) },
            "Accept new registrations"
        )
        ConsoleCheck(
            f.demoSignup,
            { vm.settingsForm = f.copy(demoSignup = it) },
            "Also allow anonymous demos"
        )

        Spacer(Modifier.height(10.dp))
        ConsoleButton(
            if (vm.busy) "Saving…" else "Save settings",
            { vm.saveSettings() },
            kind = ButtonKind.Primary,
            enabled = !vm.busy
        )

        Spacer(Modifier.height(12.dp))
        Help(
            "Accept new registrations is how a plant that downloads Nexora starts: company, " +
                "GSTIN, email, mobile, a company id and passcode. Anonymous demos is the old way — " +
                "a licence key left blank creates a company from whatever name is typed, with " +
                "nothing to tell a real plant from a made-up one; leave it off unless you are " +
                "demonstrating on a prospect's machine yourself. A demo with 0 offline days stops " +
                "the moment it cannot reach this service. The working window is only how long a " +
                "good answer is reused before the application asks again. Offline days for a " +
                "paying customer are set on the company."
        )
    }
}

/** The select, as a phone can actually use one: two lines, one chosen. */
@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    ConsoleButton(
        (if (selected) "● " else "○ ") + label,
        onClick,
        kind = if (selected) ButtonKind.Primary else ButtonKind.Default,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 2,
        center = true
    )
}
