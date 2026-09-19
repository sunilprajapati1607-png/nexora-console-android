package org.nexoraofficial.console.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.Msg
import org.nexoraofficial.console.R
import org.nexoraofficial.console.ui.theme.LocalNexora
import org.nexoraofficial.console.ui.theme.MonoStyle

/* ======================================================================
   THE PARTS THE SCREENS ARE BUILT FROM — MATERIAL 3, NEXORA'S COLOURS.

   The names are the same ones the screens already call, so the whole
   application changed its clothes without changing a single call site.
   What is underneath is now Material's: Card, OutlinedTextField, Button,
   Switch, LinearProgressIndicator, the M3 colour roles and elevation.
   ====================================================================== */

/** The standard surface everything sits on. */
@Composable
fun ConsoleCard(
    modifier: Modifier = Modifier,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}

/** A status word, as an M3 tonal chip. */
@Composable
fun Pill(text: String, state: String, modifier: Modifier = Modifier) {
    val c = LocalNexora.current
    val (bg, fg) = when (state.uppercase()) {
        "TRIAL", "DEMO" -> c.okBg to c.ok
        "LICENSED", "SELF" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        "EXPIRED", "UNVERIFIED" -> c.warnBg to c.warn
        "REVOKED", "SUSPENDED", "FAILED" -> c.badBg to c.bad
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        contentColor = fg
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** A headline figure with its name under it. */
@Composable
fun Kpi(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.defaultMinSize(minWidth = 104.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One labelled figure inside a card. */
@Composable
fun Fact(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
fun FactValue(text: String, color: Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color ?: MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun Small(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The paragraph under a control that says what it is for. */
@Composable
fun Help(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The one-line reason beside an action. */
@Composable
fun Why(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Seats used, transactions against a limit. */
@Composable
fun Bar(fraction: Float, full: Boolean = false, modifier: Modifier = Modifier, color: Color? = null) {
    val c = LocalNexora.current
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = color ?: if (full) c.bad else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    )
}

/** A key, a device id — anything compared character by character. */
@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color? = null, size: Float = 12.5f) {
    Text(
        text,
        style = MonoStyle.copy(
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = size.sp
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** The small heading over a group of actions. */
@Composable
fun GroupHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

enum class ButtonKind { Default, Primary, Danger }

/** Filled for the one thing meant to be pressed, outlined for the rest. */
@Composable
fun ConsoleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Default,
    small: Boolean = false,
    enabled: Boolean = true,
    maxLines: Int = 1,
    center: Boolean = false
) {
    val c = LocalNexora.current
    val pad = if (small) {
        androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    } else {
        androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    }
    val label: @Composable () -> Unit = {
        Text(
            text,
            style = if (small) MaterialTheme.typography.labelMedium
            else MaterialTheme.typography.labelLarge,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (center) TextAlign.Center else null
        )
    }

    when (kind) {
        ButtonKind.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = pad,
            modifier = modifier.defaultMinSize(minHeight = if (small) 34.dp else 44.dp)
        ) { label() }

        ButtonKind.Danger -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = pad,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.bad),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.bad.copy(alpha = 0.6f)),
            modifier = modifier.defaultMinSize(minHeight = if (small) 34.dp else 44.dp)
        ) { label() }

        ButtonKind.Default -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = pad,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outline
            ),
            modifier = modifier.defaultMinSize(minHeight = if (small) 34.dp else 44.dp)
        ) { label() }
    }
}

/** A Material text field with a floating label. */
@Composable
fun ConsoleField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    password: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    supporting: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = if (placeholder.isEmpty()) null else { { Text(placeholder) } },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation =
            if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
            onSend = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
        )
    )
}

/** A setting you turn on or off — a real Material switch. */
@Composable
fun ConsoleCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            supporting?.let { Help(it) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/** A message: green, amber or red, in Material's container colours. */
@Composable
fun MessageStrip(msg: Msg, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalNexora.current
    val (bg, fg) = when (msg.kind) {
        Msg.Kind.OK -> c.okBg to c.ok
        Msg.Kind.WARN -> c.warnBg to c.warn
        Msg.Kind.ERR -> c.badBg to c.bad
    }
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onDismiss),
        shape = MaterialTheme.shapes.small,
        color = bg,
        contentColor = fg,
        shadowElevation = 3.dp
    ) {
        Text(
            msg.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

/** The mark, the name, the line under it. */
@Composable
fun Brand(subtitle: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(if (compact) 34 else 40)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                "NEXORA",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 4.44.0 — THE REAL MARK.
 *
 * The logo the calculation software wears, not a letter in a blue box. It is
 * drawn for white paper, so on a dark page it gets a soft halo of its own
 * blue underneath — the same thing the web console does for the same reason.
 */
@Composable
fun BrandMark(size: Int = 40) {
    val dark = LocalNexora.current.isDark
    Box(
        Modifier
            .size(size.dp)
            .then(
                if (dark) Modifier
                    .clip(RoundedCornerShape((size * 0.28f).dp))
                    .background(
                        Brush.radialGradient(
                            0.0f to Color(0x1AFFFFFF),
                            1.0f to Color(0x00FFFFFF)
                        )
                    )
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.nexora_mark),
            contentDescription = "Nexora",
            modifier = Modifier.size((size * 0.94f).dp)
        )
    }
}

/** Light and dark, as a Material switch with a sun and a moon on it. */
@Composable
fun ModeSwitch(dark: Boolean, onFlip: () -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = dark,
        onCheckedChange = { onFlip() },
        thumbContent = {
            Text(if (dark) "☾" else "☀", fontSize = 11.sp)
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = modifier
    )
}

@Composable
fun DashedRule(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

val CardTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
