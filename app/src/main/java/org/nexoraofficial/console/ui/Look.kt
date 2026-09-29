package org.nexoraofficial.console.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   1.6.0 — THE CONSOLE DRESSED LIKE NEXORA MOBILE.

   Owner, 2026-09-29: "androud app and console ui are still not good, make
   them more flexi like weight calcuation android app". The same parts
   Nexora Mobile is built from: the logo's sweep as a banner, each kind of
   thing in its own colour (customers blue, demos teal, machines orange,
   enquiries violet, money green, trouble red), round icons on a wash,
   cards that give under the finger and arrive one after another — and
   labels and headings in Proper Case ("in software and every where all
   fond will be in proper format like capital first then later as proper").
   ====================================================================== */

/** Each kind's strong colour (icon, figure) and its soft wash (behind). */
@Immutable
data class Accent(val fg: Color, val bg: Color)

object Kinds {
    private val blue = Accent(Color(0xFF0A66E0), Color(0xFFE3EEFF)) to Accent(Color(0xFF5AB0FF), Color(0xFF12294A))
    private val teal = Accent(Color(0xFF0B8F7E), Color(0xFFD9F4EF)) to Accent(Color(0xFF3FD6BE), Color(0xFF0D332E))
    private val orange = Accent(Color(0xFFD9730D), Color(0xFFFFEDDA)) to Accent(Color(0xFFFFB066), Color(0xFF3A2613))
    private val violet = Accent(Color(0xFF6D3FF5), Color(0xFFEDE6FF)) to Accent(Color(0xFFB39BFF), Color(0xFF281F48))
    private val green = Accent(Color(0xFF15803D), Color(0xFFDDF5E5)) to Accent(Color(0xFF4ADE80), Color(0xFF113222))
    private val red = Accent(Color(0xFFDC2626), Color(0xFFFDEAEA)) to Accent(Color(0xFFF87171), Color(0xFF2C1616))
    private val amber = Accent(Color(0xFFB45309), Color(0xFFFEF3C7)) to Accent(Color(0xFFFBBF24), Color(0xFF33290F))
    private val grey = Accent(Color(0xFF475467), Color(0xFFEEF1F6)) to Accent(Color(0xFFA8B2CA), Color(0xFF232A3B))

    fun of(kind: String, dark: Boolean): Accent = (when (kind) {
        "customer", "licensed", "blue" -> blue
        "demo", "teal" -> teal
        "machine", "orange" -> orange
        "enquiry", "violet", "ai" -> violet
        "money", "green", "won" -> green
        "bad", "red", "suspended", "revoked", "lost" -> red
        "warn", "amber", "expired" -> amber
        else -> grey
    }).let { if (dark) it.second else it.first }

    /** A company's or a machine's state, as its colour. */
    fun state(s: String, dark: Boolean): Accent = of(when (s.uppercase()) {
        "LICENSED" -> "licensed"
        "DEMO", "TRIAL" -> "demo"
        "EXPIRED" -> "expired"
        "SUSPENDED", "REVOKED" -> "suspended"
        else -> "grey"
    }, dark)
}

val BrandGradient = listOf(Color(0xFF002D86), Color(0xFF0A66E0), Color(0xFF1EA0FF))

@Composable
fun accent(kind: String): Accent = Kinds.of(kind, LocalNexora.current.isDark)

/**
 * A label or a heading in Proper Case — each word's first letter capital. A word that already has a capital
 * (GSTIN, PRO) and anything in brackets keep their letters; what people typed (names, keys, notes) never
 * passes through this.
 */
fun String.proper(): String {
    val out = StringBuilder(length)
    var depth = 0
    var start = true
    for (ch in this) {
        when {
            ch == '(' -> { depth++; out.append(ch); start = false }
            ch == ')' -> { if (depth > 0) depth--; out.append(ch); start = false }
            ch.isWhitespace() || ch == '-' || ch == '/' -> { out.append(ch); start = true }
            start && depth == 0 && ch.isLowerCase() -> { out.append(ch.uppercaseChar()); start = false }
            else -> { out.append(ch); start = false }
        }
    }
    return out.toString()
}

/** Arrives: fades in and rises a little, one after another down a list. */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(320, delayMillis = index.coerceAtMost(8) * 45, easing = FastOutSlowInEasing)) }
    graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 28f }
}

/** Gives under the finger, like a real button. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
}

/** The logo's sweep, as a banner: the dashboard's greeting, a company's head. */
@Composable
fun GradientBanner(modifier: Modifier = Modifier, colors: List<Color> = BrandGradient, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(colors))
            .drawBehind { drawCircle(Color.White.copy(alpha = 0.10f), radius = 70.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width - 20.dp.toPx(), 10.dp.toPx())) }
            .padding(18.dp)
    ) { Column(content = content) }
}

/** A round icon on its own colour wash. */
@Composable
fun IconDot(icon: ImageVector, a: Accent, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(a.bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = a.fg, modifier = Modifier.size((size * 0.58f).dp))
    }
}

/** A square on the dashboard: an icon, a count, and what it is — in that kind's colour. */
@Composable
fun BigTile(icon: ImageVector, count: String, label: String, kind: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val a = accent(kind)
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.medium).pressable(onClick),
        shape = MaterialTheme.shapes.medium,
        color = a.bg,
        border = BorderStroke(1.dp, a.fg.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(a.fg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(count, style = MaterialTheme.typography.headlineSmall, color = a.fg, fontWeight = FontWeight.SemiBold)
            Text(label.proper(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A wide card that leads somewhere: a round icon, a title and a line — in its kind's colour. */
@Composable
fun ActionCard(icon: ImageVector, title: String, line: String, kind: String, index: Int = 0, onClick: () -> Unit) {
    val a = accent(kind)
    Surface(
        modifier = Modifier.fillMaxWidth().appear(index).clip(MaterialTheme.shapes.medium).pressable(onClick),
        shape = MaterialTheme.shapes.medium,
        color = a.bg,
        border = BorderStroke(1.dp, a.fg.copy(alpha = 0.16f))
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(a.fg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title.proper(), style = MaterialTheme.typography.titleMedium, color = a.fg, fontWeight = FontWeight.SemiBold)
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = a.fg.copy(alpha = 0.7f))
        }
    }
}

/** A card with a coloured heading: an icon on its wash, and a title. */
@Composable
fun ColourCard(title: String, icon: ImageVector, kind: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val a = accent(kind)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, a.fg.copy(alpha = 0.22f))
    ) {
        Column {
            Row(Modifier.fillMaxWidth().background(a.bg).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = a.fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title.proper(), style = MaterialTheme.typography.titleSmall, color = a.fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
        }
    }
}

/** A row of tiles, each taking an equal share. */
@Composable
fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
