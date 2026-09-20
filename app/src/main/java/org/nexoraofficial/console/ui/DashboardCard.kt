package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * THE DASHBOARD — the whole business in one screen.
 *
 * Above the line, the money that is already in: customers, demos, machines
 * running. Below it, the money that might be: enquiries by state, which
 * software people are actually asking about, and who is due a call today.
 *
 * The product bars are the part worth having. "Eleven asked about ERP and two
 * about payroll" is a decision about what to build next; a list of names is
 * not.
 */
@Composable
fun DashboardCard(vm: ConsoleViewModel) {
    val c = LocalNexora.current
    val byProduct = vm.byProduct()
    val due = vm.dueFollowUps()
    val total = vm.inquiryData.inquiries.size

    ConsoleCard {
        Text("Dashboard", style = CardTitleStyle)
        Small("— where the business stands today")

        /* ---- what is already earned ---- */
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Kpi(vm.customerCount.toString(), "Customers")
            Kpi(vm.demoCount.toString(), "Demos")
            Kpi(vm.runningCount.toString(), "Running")
            Kpi(vm.data.licences.size.toString(), "Installations")
        }

        /* ---- what might still be ---- */
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Kpi(total.toString(), "Enquiries")
            Kpi(vm.newInquiryCount.toString(), "New")
            Kpi(vm.openInquiries.toString(), "Open")
            Kpi(vm.wonInquiries.toString(), "Won")
            Kpi(due.size.toString(), "Due today")
            Kpi(vm.openFeedback.toString(), "Reports")
        }

        if (total == 0) {
            Spacer(Modifier.height(12.dp))
            Help(
                "The enquiry figures fill in as leads arrive — from the website's forms on their " +
                    "own, or by hand under Enquiries."
            )
            return@ConsoleCard
        }

        /* ---- who is waiting to hear back ---- */
        if (due.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            GroupHeading("Due a call")
            due.take(6).forEach { q ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        q.name + (if (!q.company.isNullOrBlank()) " · ${q.company}" else ""),
                        color = c.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Small(q.followUp ?: "", color = c.warn)
                }
            }
            if (due.size > 6) Small("and ${due.size - 6} more")
        }

        /* ---- how far the leads have got ---- */
        Spacer(Modifier.height(14.dp))
        GroupHeading("Where the enquiries have got to")
        vm.byState().forEach { (state, n) ->
            CountBar(state.lowercase(), n, total, if (state == "WON") c.ok else if (state == "LOST") c.bad else c.accent)
        }

        /* ---- which software they ask about ---- */
        Spacer(Modifier.height(14.dp))
        GroupHeading("Which software they ask about")
        val top = byProduct.firstOrNull()?.second ?: 1
        byProduct.forEach { (product, n) ->
            CountBar(product, n, top, c.accent) { vm.inquiryProduct = product }
        }
        Spacer(Modifier.height(8.dp))
        Help(
            "Tap a product to see only those enquiries. The list is the same one the website " +
                "offers under \"I am interested in\"."
        )

        /* ---- the newest thing that happened ---- */
        val newest = vm.inquiryData.inquiries.firstOrNull()
        if (newest != null) {
            Spacer(Modifier.height(14.dp))
            GroupHeading("Latest enquiry")
            Text(
                newest.name + (if (!newest.company.isNullOrBlank()) " · ${newest.company}" else ""),
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Small("${newest.product} · ${newest.source.lowercase()} · ${Fmt.dateTime(newest.createdAt)}")
        }
    }
}

/** One labelled bar: the name, the figure, and how it compares. */
@Composable
private fun CountBar(
    label: String,
    value: Int,
    outOf: Int,
    colour: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null
) {
    val c = LocalNexora.current
    val fraction = if (outOf <= 0) 0f else value / outOf.toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(value.toString(), color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Bar(fraction, color = colour)
    }
}
