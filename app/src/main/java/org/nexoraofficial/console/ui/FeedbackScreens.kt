package org.nexoraofficial.console.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.ConsoleViewModel
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.data.Feedback
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   FEEDBACK AND PROBLEM REPORTS — what the plants say from inside Nexora.

   Help → Nexora Contact in the application sends a report to the
   service; this is where it is read. A problem report carries a picture
   of the screen as it was when the person opened the menu, which is
   fetched only when the report is opened: a list of reports must not
   weigh a list of screenshots.

   A report moves NEW → SEEN → FIXED or CLOSED, one tap each, and the
   owner's note stays here and on the web console — it is never sent
   back to the plant. Calling back is one tap, because a plant that has
   sent a problem report is a plant that is waiting.
   ====================================================================== */

/* ---------------------------------------------------------------- list */

@Composable
fun FeedbackScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    gutter: PaddingValues,
    page: Modifier
) {
    val c = LocalNexora.current
    val rows = vm.feedback

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(page) {
                Column(Modifier.fillMaxWidth()) {
                    ConsoleField(
                        label = null,
                        value = vm.feedbackQuery,
                        onValueChange = { vm.feedbackQuery = it },
                        placeholder = "Find a plant, person, word…"
                    )

                    /* Kind, then state — both as filters that also count. */
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val all = vm.feedbackData.feedback
                        ConsoleButton(
                            "All ${all.size}",
                            { vm.feedbackKind = null; vm.feedbackState = null },
                            kind = if (vm.feedbackKind == null && vm.feedbackState == null) ButtonKind.Primary else ButtonKind.Default,
                            small = true
                        )
                        vm.feedbackData.kinds.forEach { k ->
                            ConsoleButton(
                                (if (k == "BUG") "problems" else "feedback") + " " + all.count { it.kind == k },
                                { vm.feedbackKind = if (vm.feedbackKind == k) null else k },
                                kind = if (vm.feedbackKind == k) ButtonKind.Primary else ButtonKind.Default,
                                small = true
                            )
                        }
                        vm.feedbackData.states.forEach { s ->
                            ConsoleButton(
                                s.lowercase() + " " + all.count { it.state == s },
                                { vm.feedbackState = if (vm.feedbackState == s) null else s },
                                kind = if (vm.feedbackState == s) ButtonKind.Primary else ButtonKind.Default,
                                small = true
                            )
                        }
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                Box(page) {
                    ConsoleCard {
                        Help(
                            if (vm.feedbackData.feedback.isEmpty())
                                "Nothing yet. A report arrives when somebody in a plant uses " +
                                    "Help → Nexora Contact inside the application — feedback, or a " +
                                    "problem with a picture of their screen."
                            else "Nothing matches that."
                        )
                    }
                }
            }
        }

        items(rows, key = { it.id }) { f ->
            Box(page) { FeedbackRow(f) { nav.open(Screen.FeedbackDetail(f.id)) } }
        }
    }
}

/** One report in the list: who, what, how old, and where it has got to. */
@Composable
private fun FeedbackRow(f: Feedback, onOpen: () -> Unit) {
    val c = LocalNexora.current
    val edge = if (f.isBug) c.bad else c.accent

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, if (f.state == "NEW") edge else c.border, RoundedCornerShape(12.dp))
            .drawAccentEdge(edge)
            .clickable(onClick = onOpen)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    f.subject ?: (if (f.isBug) "Problem" else "Feedback"),
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(3.dp))
                Small(f.plant + (f.who?.let { " · $it" } ?: ""), color = c.text)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Pill(if (f.isBug) "problem" else "feedback", if (f.isBug) "REVOKED" else "LICENSED")
                Spacer(Modifier.height(4.dp))
                Pill(f.state.lowercase(), feedbackPillState(f.state))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            f.message,
            color = c.muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 3
        )
        Spacer(Modifier.height(8.dp))
        Small(
            Fmt.day(f.createdAt) +
                (f.view?.let { " · $it" } ?: "") +
                (f.appVersion?.let { " · v$it" } ?: "") +
                (if (f.hasShot) " · has a picture" else "")
        )
    }
}

fun feedbackPillState(state: String): String = when (state) {
    "NEW" -> "TRIAL"
    "SEEN" -> "SELF"
    "FIXED" -> "LICENSED"
    "CLOSED" -> "REVOKED"
    else -> "SELF"
}

/* -------------------------------------------------------------- detail */

@Composable
fun FeedbackDetailScreen(
    vm: ConsoleViewModel,
    nav: Navigator,
    id: Int,
    gutter: PaddingValues,
    page: Modifier,
    onAsk: (Ask) -> Unit
) {
    val c = LocalNexora.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val f = vm.feedbackById(id)

    /* The picture, fetched once per report and kept for the session. */
    LaunchedEffect(id, f?.hasShot) { if (f != null && f.hasShot) vm.loadShot(id) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = gutter,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (f == null) {
            item { Box(page) { ConsoleCard { Help("That report is no longer here.") } } }
            return@LazyColumn
        }

        /* ---- what they said ---- */
        item {
            Box(page) {
                ConsoleCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.subject ?: (if (f.isBug) "Problem" else "Feedback"),
                                color = c.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Small(Fmt.day(f.createdAt) + " · " + (if (f.isBug) "problem report" else "feedback"))
                        }
                        Pill(f.state.lowercase(), feedbackPillState(f.state))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(f.message, color = c.text, fontSize = 14.sp, lineHeight = 20.sp)

                    if (!f.reply.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Fact("Your note", Modifier.fillMaxWidth()) {
                            Small(f.reply, color = c.text)
                        }
                    }

                    /* ---- where it has got to: one tap ---- */
                    Spacer(Modifier.height(14.dp))
                    GroupHeading("Where it has got to")
                    Spacer(Modifier.height(6.dp))
                    WrapRow {
                        vm.feedbackData.states.forEach { s ->
                            Chip(s.lowercase(), f.state == s) {
                                if (f.state != s) vm.setFeedbackState(f.id, s)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    WrapRow {
                        ConsoleButton(if (f.reply.isNullOrBlank()) "Add a note" else "Edit the note", {
                            onAsk(
                                Ask.Input(
                                    title = "Your note",
                                    body = "Kept here and on the web console. Never sent to the plant.",
                                    label = "Note",
                                    initial = f.reply ?: "",
                                    confirmText = "Save",
                                    onOk = { vm.replyFeedback(f.id, it) }
                                )
                            )
                        }, small = true)
                        ConsoleButton("Remove", {
                            onAsk(
                                Ask.Confirm(
                                    title = "Remove this report?",
                                    body = "The report and its picture are deleted. The plant is not told.",
                                    confirmText = "Remove",
                                    danger = true,
                                    onYes = { vm.deleteFeedback(f.id) { nav.backToRoot() } }
                                )
                            )
                        }, small = true, kind = ButtonKind.Danger)
                    }
                }
            }
        }

        /* ---- who, and how to reach them ---- */
        item {
            Box(page) {
                ConsoleCard {
                    Text("Who sent it", style = CardTitleStyle)
                    Spacer(Modifier.height(10.dp))
                    Fact("Plant", Modifier.fillMaxWidth()) { FactValue(f.plant) }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Fact("Person", Modifier.weight(1f)) { FactValue(f.who ?: "—") }
                        Fact("Machine", Modifier.weight(1f)) { FactValue(f.deviceName ?: "—") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Fact("Window", Modifier.weight(1f)) { FactValue(f.view ?: "—") }
                        Fact("Nexora", Modifier.weight(1f)) {
                            FactValue((f.appVersion ?: "—") + (f.edition?.let { " " + it.lowercase() } ?: ""))
                        }
                    }
                    if (!f.phone.isNullOrBlank() || !f.email.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!f.phone.isNullOrBlank()) Mono(f.phone)
                            if (!f.phone.isNullOrBlank() && !f.email.isNullOrBlank()) Small("  ·  ")
                            if (!f.email.isNullOrBlank()) Mono(f.email)
                        }
                        Spacer(Modifier.height(10.dp))
                        WrapRow {
                            if (!f.phone.isNullOrBlank()) {
                                ConsoleButton("Call back", {
                                    launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${f.phone}")), vm)
                                }, small = true, kind = ButtonKind.Primary)
                                ConsoleButton("WhatsApp", {
                                    val digits = f.phone.filter { ch -> ch.isDigit() }
                                    val number = if (digits.length == 10) "91$digits" else digits
                                    val text = Uri.encode("Hello, this is Nexora about your report \"${f.subject ?: ""}\".")
                                    launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$text")), vm)
                                }, small = true)
                            }
                            if (!f.email.isNullOrBlank()) {
                                ConsoleButton("Email", {
                                    launch(
                                        context,
                                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${f.email}"))
                                            .putExtra(Intent.EXTRA_SUBJECT, "Re: ${f.subject ?: "your report to Nexora"}"),
                                        vm
                                    )
                                }, small = true)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Small("No call-back number or email was given.")
                    }
                }
            }
        }

        /* ---- the screen as it was ---- */
        if (f.hasShot) {
            item {
                Box(page) {
                    ConsoleCard(padding = 10) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Their screen", style = CardTitleStyle, modifier = Modifier.weight(1f))
                            if (vm.shotBusy == id) Small("loading…")
                        }
                        Spacer(Modifier.height(6.dp))
                        val bmp = vm.shots[id]
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "The screen when the report was made",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, c.border, RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        } else if (vm.shots.containsKey(id)) {
                            Help("The picture could not be read.")
                        } else {
                            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                                Small("Fetching the picture…")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Small("Taken by the application the moment the menu was used, before its own dialog opened.")
                    }
                }
            }
        }
    }
}

private fun launch(context: Context, intent: Intent, vm: ConsoleViewModel) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        vm.say("No application on this phone can open that.", Msg.Kind.ERR)
    }
}
