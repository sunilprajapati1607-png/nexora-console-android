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
