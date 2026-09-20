package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.data.PLAN_FEATURES
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * THE PLANS  (1.5.0)
 *
 *   "give this plan wise access things in console so i can control app
 *    feature from console as per plan … for demo everything is available"
 *
 * What Standard and Pro carry, feature by feature: the same matrix the web
 * console edits, saved to the same service setting, read by every
 * installation at its next check. Calculation and costing are not in the
 * list because they are the product; a demo has everything whatever its
 * plan says; seats are set per company and have nothing to do with it.
 */
@Composable
fun PlansCard(vm: ConsoleViewModel) {
    val c = LocalNexora.current
    val m = vm.planMatrix

    ConsoleCard {
        Text("Plans", style = CardTitleStyle)
        Small("— what Standard and Pro carry; every installation reads this at its next check")

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Feature", modifier = Modifier.weight(1f), color = c.muted, style = MaterialTheme.typography.bodySmall)
            Text("Std", modifier = Modifier.width(52.dp), color = c.muted, style = MaterialTheme.typography.bodySmall)
            Text("Pro", modifier = Modifier.width(52.dp), color = c.muted, style = MaterialTheme.typography.bodySmall)
            Text("Demo", modifier = Modifier.width(52.dp), color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        PLAN_FEATURES.forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Checkbox(
                    checked = m["STANDARD"]?.get(f.id) == true,
                    onCheckedChange = { vm.setPlanFeature("STANDARD", f.id, it) },
                    modifier = Modifier.width(52.dp)
                )
                Checkbox(
                    checked = m["PRO"]?.get(f.id) == true,
                    onCheckedChange = { vm.setPlanFeature("PRO", f.id, it) },
                    modifier = Modifier.width(52.dp)
                )
                Checkbox(
                    checked = true,
                    onCheckedChange = null,
                    enabled = false,
                    modifier = Modifier.width(52.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        ConsoleButton(
            "Save plans",
            { vm.savePlans() },
            kind = ButtonKind.Primary,
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.busy
        )
        Spacer(Modifier.height(8.dp))
        Why("Calculation and costing are always on. A feature unticked for a plan disappears from every installation on that plan, and the application says which plan it belongs to when somebody asks for it. Seats are separate: you set them per company, on either plan. A demo always has everything.")
    }
}
