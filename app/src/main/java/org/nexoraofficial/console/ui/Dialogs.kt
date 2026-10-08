package org.nexoraofficial.console.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.nexoraofficial.console.ui.theme.LocalNexora

/**
 * WHAT prompt() AND confirm() BECOME ON A PHONE.
 *
 * The web console asks its questions with the browser's own boxes. A phone
 * has none, so they are rebuilt here — same words, same order, same warnings,
 * in the console's own surface rather than in Material's.
 */
sealed interface Ask {

    data class Confirm(
        val title: String,
        val body: String,
        val confirmText: String = "Yes",
        val danger: Boolean = false,
        val onYes: () -> Unit
    ) : Ask

    data class Input(
        val title: String,
        val body: String = "",
        val label: String,
        val initial: String = "",
        val numeric: Boolean = false,
        val password: Boolean = false,
        val confirmText: String = "Save",
        /** returns a complaint, or null when the value will do */
        val validate: (String) -> String? = { null },
        val onOk: (String) -> Unit
    ) : Ask

    data class TwoInputs(
        val title: String,
        val body: String = "",
        val labelA: String,
        val initialA: String = "",
        val labelB: String,
        val passwordB: Boolean = true,
        val confirmText: String = "Save",
        val validate: (String, String) -> String? = { _, _ -> null },
        val onOk: (String, String) -> Unit
    ) : Ask

    data class AddPerson(
        val companyName: String,
        val onOk: (name: String, pin: String, email: String, admin: Boolean) -> Unit
    ) : Ask

    /** Delete — the name must be typed exactly, as it must in the console. */
    data class TypeToConfirm(
        val title: String,
        val body: String,
        val label: String,
        val expected: String,
        val onOk: (String) -> Unit
    ) : Ask

    /**
     * 1.9.0 — one of a list: the company a Fabric Stock company belongs to,
     * or the other way round. Tap one, then [confirmText]; a search narrows
     * a long list.
     */
    data class Pick(
        val title: String,
        val body: String,
        val options: List<Option>,
        val confirmText: String = "Link",
        val empty: String = "Nothing to choose from.",
        val onPick: (Int) -> Unit
    ) : Ask {
        /** [id] goes back to [onPick]; [line] is what tells two of the same name apart. */
        data class Option(val id: Int, val title: String, val line: String)
    }

    /**
     * 2.0.0 — a day, from the phone's own calendar ("paid on", "valid to"). [initial] and the answer are
     * "YYYY-MM-DD"; [clearable] offers to leave it empty (the answer is then "").
     */
    data class Date(
        val title: String,
        val initial: String,
        val clearable: Boolean = false,
        val onPick: (String) -> Unit
    ) : Ask
}

/**
 * Material's own dialog, so a question in this application looks like a
 * question anywhere else on the phone — and picks up the system's scrim,
 * its insets, its back handling and its animation for nothing.
 */
@Composable
private fun DialogShell(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (body.isNotEmpty()) Help(body)
                content()
            }
        },
        confirmButton = { actions() }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AskHost(ask: Ask?, onClose: () -> Unit) {
    when (ask) {
        null -> Unit

        is Ask.Confirm -> DialogShell(
            title = ask.title,
            body = ask.body,
            onDismiss = onClose,
            actions = {
                ConsoleButton("Cancel", onClose)
                ConsoleButton(
                    ask.confirmText,
                    { ask.onYes(); onClose() },
                    kind = if (ask.danger) ButtonKind.Danger else ButtonKind.Primary
                )
            }
        )

        is Ask.Input -> {
            var v by remember(ask) { mutableStateOf(ask.initial) }
            var err by remember(ask) { mutableStateOf<String?>(null) }
            DialogShell(
                title = ask.title,
                body = ask.body,
                onDismiss = onClose,
                actions = {
                    ConsoleButton("Cancel", onClose)
                    ConsoleButton(
                        ask.confirmText,
                        {
                            val complaint = ask.validate(v)
                            if (complaint != null) err = complaint
                            else { ask.onOk(v); onClose() }
                        },
                        kind = ButtonKind.Primary
                    )
                }
            ) {
                Spacer(Modifier.height(12.dp))
                ConsoleField(
                    label = ask.label,
                    value = v,
                    onValueChange = { v = it; err = null },
                    numeric = ask.numeric,
                    password = ask.password,
                    imeAction = ImeAction.Done
                )
                err?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = LocalNexora.current.bad, fontSize = 12.5f.sp)
                }
            }
        }

        is Ask.TwoInputs -> {
            var a by remember(ask) { mutableStateOf(ask.initialA) }
            var b by remember(ask) { mutableStateOf("") }
            var err by remember(ask) { mutableStateOf<String?>(null) }
            DialogShell(
                title = ask.title,
                body = ask.body,
                onDismiss = onClose,
                actions = {
                    ConsoleButton("Cancel", onClose)
                    ConsoleButton(
                        ask.confirmText,
                        {
                            val complaint = ask.validate(a, b)
                            if (complaint != null) err = complaint
                            else { ask.onOk(a, b); onClose() }
                        },
                        kind = ButtonKind.Primary
                    )
                }
            ) {
                Spacer(Modifier.height(12.dp))
                ConsoleField(ask.labelA, a, { a = it; err = null })
                Spacer(Modifier.height(10.dp))
                ConsoleField(
                    ask.labelB, b, { b = it; err = null },
                    password = ask.passwordB, imeAction = ImeAction.Done
                )
                err?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = LocalNexora.current.bad, fontSize = 12.5f.sp)
                }
            }
        }

        is Ask.AddPerson -> {
            var name by remember(ask) { mutableStateOf("") }
            var pin by remember(ask) { mutableStateOf("") }
            var email by remember(ask) { mutableStateOf("") }
            var admin by remember(ask) { mutableStateOf(false) }
            var err by remember(ask) { mutableStateOf<String?>(null) }
            DialogShell(
                title = "Add a person to ${ask.companyName}",
                body = "They sign in with this name and a PIN. Tell it to them directly; " +
                    "it is not shown again.",
                onDismiss = onClose,
                actions = {
                    ConsoleButton("Cancel", onClose)
                    ConsoleButton(
                        "Add",
                        {
                            val mail = email.trim()
                            err = when {
                                name.isBlank() -> "A name is required."
                                pin.length < 4 -> "A PIN of at least 4 characters is required."
                                mail.isNotEmpty() &&
                                    !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(mail) ->
                                    "That does not look like an email address."
                                else -> null
                            }
                            if (err == null) { ask.onOk(name, pin, mail, admin); onClose() }
                        },
                        kind = ButtonKind.Primary
                    )
                }
            ) {
                Spacer(Modifier.height(12.dp))
                ConsoleField("Name", name, { name = it; err = null })
                Spacer(Modifier.height(10.dp))
                ConsoleField("PIN (at least 4 characters)", pin, { pin = it; err = null }, password = true)
                Spacer(Modifier.height(10.dp))
                ConsoleField(
                    "Email (optional)", email, { email = it; err = null },
                    placeholder = "where notices about new versions go"
                )
                Spacer(Modifier.height(6.dp))
                ConsoleCheck(
                    admin, { admin = it },
                    "Make them an administrator — they can add and remove people " +
                        "from inside the application, and see everyone's work"
                )
                err?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = LocalNexora.current.bad, fontSize = 12.5f.sp)
                }
            }
        }

        is Ask.Pick -> {
            var chosen by remember(ask) { mutableStateOf<Int?>(null) }
            var find by remember(ask) { mutableStateOf("") }
            val c = LocalNexora.current
            DialogShell(
                title = ask.title,
                body = ask.body,
                onDismiss = onClose,
                actions = {
                    ConsoleButton("Cancel", onClose)
                    ConsoleButton(
                        ask.confirmText,
                        { chosen?.let { ask.onPick(it); onClose() } },
                        kind = ButtonKind.Primary,
                        enabled = chosen != null
                    )
                }
            ) {
                Spacer(Modifier.height(12.dp))
                if (ask.options.isEmpty()) {
                    Help(ask.empty)
                    return@DialogShell
                }
                if (ask.options.size > 6) {
                    ConsoleField(null, find, { find = it }, placeholder = "Find…", imeAction = ImeAction.Done)
                    Spacer(Modifier.height(8.dp))
                }
                val t = find.trim().lowercase()
                ask.options.filter { t.isEmpty() || it.title.lowercase().contains(t) || it.line.lowercase().contains(t) }
                    .forEach { o ->
                        val on = chosen == o.id
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) c.accentBg else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(12.dp))
                                .pressable { chosen = o.id }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(o.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (o.line.isNotBlank()) Small(o.line)
                        }
                    }
            }
        }

        is Ask.Date -> {
            val start = remember(ask) {
                runCatching { java.time.LocalDate.parse(ask.initial) }.getOrNull() ?: java.time.LocalDate.now()
            }
            val state = androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest = onClose,
                confirmButton = {
                    ConsoleButton("OK", {
                        state.selectedDateMillis?.let { ms ->
                            ask.onPick(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
                        }
                        onClose()
                    }, kind = ButtonKind.Primary)
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ask.clearable) ConsoleButton("Clear", { ask.onPick(""); onClose() })
                        ConsoleButton("Cancel", onClose)
                    }
                }
            ) {
                androidx.compose.material3.DatePicker(state = state, title = {
                    Text(ask.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, top = 16.dp))
                })
            }
        }

        is Ask.TypeToConfirm -> {
            var typed by remember(ask) { mutableStateOf("") }
            var err by remember(ask) { mutableStateOf<String?>(null) }
            DialogShell(
                title = ask.title,
                body = ask.body,
                onDismiss = onClose,
                actions = {
                    ConsoleButton("Cancel", onClose)
                    ConsoleButton(
                        "Delete",
                        {
                            if (typed.trim() != ask.expected) {
                                err = "Type the name exactly to confirm."
                            } else { ask.onOk(typed.trim()); onClose() }
                        },
                        kind = ButtonKind.Danger
                    )
                }
            ) {
                Spacer(Modifier.height(12.dp))
                ConsoleField(ask.label, typed, { typed = it; err = null }, imeAction = ImeAction.Done)
                err?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = LocalNexora.current.bad, fontSize = 12.5f.sp)
                }
            }
        }
    }
}
