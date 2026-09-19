package org.nexoraofficial.console.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Inquiry
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * THE ENQUIRIES — everybody who has asked, before they are anybody who pays.
 *
 * The website's contact and demo forms land here on their own; the ones that
 * come by phone or at an exhibition are added with the + on this screen. Same
 * row either way, and the source says which it was.
 *
 * A lead moves NEW → CONTACTED → DEMO → QUOTED → WON or LOST, and the state
 * is one tap, because a follow-up that takes four taps to record does not get
 * recorded.
 */
@Composable
fun InquiriesCard(
    vm: ConsoleViewModel,
    onAsk: (Ask) -> Unit,
    onEdit: (Inquiry?) -> Unit
) {

    val rows = vm.inquiries

    Column(Modifier.fillMaxWidth()) {

        ConsoleField(
            label = null,
            value = vm.inquiryQuery,
            onValueChange = { vm.inquiryQuery = it },
            placeholder = "Find a name, plant, number, product…"
        )

        /* The states, as filters that also say how many are in each. */
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConsoleButton(
                "All ${vm.inquiryData.inquiries.size}",
                { vm.inquiryState = null },
                kind = if (vm.inquiryState == null) ButtonKind.Primary else ButtonKind.Default,
                small = true
            )
            vm.byState().forEach { (state, n) ->
                ConsoleButton(
                    "${state.lowercase()} $n",
                    { vm.inquiryState = if (vm.inquiryState == state) null else state },
                    kind = if (vm.inquiryState == state) ButtonKind.Primary else ButtonKind.Default,
                    small = true
                )
            }
        }

        if (vm.inquiryProduct != null) {
            Spacer(Modifier.height(8.dp))
            ConsoleButton(
                "Showing ${vm.inquiryProduct} only — tap to show every product",
                { vm.inquiryProduct = null },
                small = true,
                maxLines = 2
            )
        }

        Spacer(Modifier.height(14.dp))

        if (rows.isEmpty()) {
            ConsoleCard {
                Help(
                    if (vm.inquiryData.inquiries.isEmpty())
                        "No enquiries yet. One arrives on its own when somebody fills the form on " +
                            "nexoraofficial.org; add the ones that come by phone with the + below."
                    else "Nothing matches that."
                )
            }
        } else {
            rows.forEachIndexed { i, q ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                InquiryCard(q, vm, onAsk, onEdit)
            }
        }
    }
}

/** One lead, as its own card: who, what they want, and what to do next. */
@Composable
private fun InquiryCard(
    q: Inquiry,
    vm: ConsoleViewModel,
    onAsk: (Ask) -> Unit,
    onEdit: (Inquiry?) -> Unit
) {
    val c = LocalNexora.current
    val context = LocalContext.current

    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(q.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (!q.company.isNullOrBlank()) Small(q.company, color = c.text)
            }
            Pill(q.state.lowercase(), inquiryPillState(q.state))
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(q.product, "LICENSED")
        }

        Spacer(Modifier.height(6.dp))
        Small(
            "${q.source.lowercase()} · ${Fmt.day(q.createdAt)}" +
                (if (!q.followUp.isNullOrBlank()) " · follow up ${q.followUp}" else "")
        )

        if (!q.phone.isNullOrBlank() || !q.email.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!q.phone.isNullOrBlank()) Mono(q.phone)
                if (!q.phone.isNullOrBlank() && !q.email.isNullOrBlank()) Small("  ·  ")
                if (!q.email.isNullOrBlank()) Mono(q.email)
            }
        }

        if (!q.message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Help(q.message)
        }
        if (!q.notes.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Small("Note: ${q.notes}", color = c.muted)
        }

        /* Reaching them: the three things anybody actually does next. */
        Spacer(Modifier.height(12.dp))
        WrapRow {
            if (!q.phone.isNullOrBlank()) {
                ConsoleButton("Call", {
                    open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${q.phone}")), vm)
                }, small = true)
                ConsoleButton("WhatsApp", {
                    val digits = q.phone.filter { ch -> ch.isDigit() }
                    val number = if (digits.length == 10) "91$digits" else digits
                    open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")), vm)
                }, small = true)
            }
            if (!q.email.isNullOrBlank()) {
                ConsoleButton("Email", {
                    open(
                        context,
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${q.email}")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "Nexora — ${q.product}")
                        },
                        vm
                    )
                }, small = true)
            }
            ConsoleButton("Edit", { onEdit(q) }, small = true)
            ConsoleButton("Remove", {
                onAsk(
                    Ask.Confirm(
                        title = "Remove ${q.name} from the enquiries?",
                        body = "The row is deleted. Nothing else is touched — if they became a " +
                            "customer, their company stays exactly as it is.",
                        confirmText = "Remove",
                        danger = true,
                        onYes = { vm.deleteInquiry(q.id, q.name) }
                    )
                )
            }, kind = ButtonKind.Danger, small = true)
        }

        /* Moving it along. */
        Spacer(Modifier.height(10.dp))
        Small("Move it to")
        Spacer(Modifier.height(6.dp))
        WrapRow {
            vm.inquiryData.states.filter { it != q.state }.forEach { s ->
                ConsoleButton(
                    s.lowercase(),
                    { vm.setInquiryState(q.id, s) },
                    small = true,
                    kind = if (s == "WON") ButtonKind.Primary else ButtonKind.Default
                )
            }
        }
    }
}

internal fun inquiryPillState(state: String) = when (state) {
    "NEW" -> "TRIAL"
    "WON" -> "LICENSED"
    "LOST" -> "REVOKED"
    "QUOTED", "DEMO" -> "EXPIRED"
    else -> "SELF"
}

internal fun open(context: Context, intent: Intent, vm: ConsoleViewModel) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        vm.say("Nothing on this phone can open that.", Msg.Kind.ERR)
    }
}
