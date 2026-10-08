package org.nexoraofficial.console.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nexoraofficial.console.data.Fmt
import org.nexoraofficial.console.data.Software
import org.nexoraofficial.console.ui.theme.LocalNexora

/* ======================================================================
   2.0.0 — ROWS AND WINDOWS, AS THE SOFTWARE HAS THEM.

   Owner, 2026-10-08: "console ne software jevu banavanu che row type
   details click and open window". Every list is a Records screen — a
   search, quick views with their counts, four figure tiles and a list of
   rows — and a row opens its record in a screen of its own: a head that
   says DISPLAY, EDIT or NEW, a strip of tools, read-only boxes until Edit,
   and Save / Cancel. The parts below are what every one of them is built
   from, in the console's own Material look, sized for a phone.
   ====================================================================== */

/** Each software in its own colours: Sales & Costing blue, Fabric Stock a two-colour green, Jobwork orange. */
object SwColours {
    val SALES = listOf(Color(0xFF3366FF), Color(0xFF6A8CFF))
    val FABRIC = listOf(Color(0xFF10A37F), Color(0xFF2CC6B0))
    val JOBWORK = listOf(Color(0xFFF08A0C), Color(0xFFF7B538))

    fun of(sw: String): List<Color> = when (sw) {
        Software.FABRIC -> FABRIC
        Software.JOBWORK -> JOBWORK
        else -> SALES
    }

    /** The software's colour as writing on the page: deeper on a light page, lighter on a dark one. */
    fun ink(sw: String, dark: Boolean): Color {
        val c = of(sw)[0]
        return if (dark) lerp(c, Color.White, 0.35f) else lerp(c, Color.Black, 0.12f)
    }
}

/** A small round mark in a software's gradient. */
@Composable
fun SwDot(sw: String, size: Int = 9) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(SwColours.of(sw))))
}

/** A licence state in its colour — licensed green, demo teal, ended amber, suspended red (the web console's c-ok … c-bad). */
@Composable
fun stateColour(s: String): Color {
    val c = LocalNexora.current
    return when (s) {
        "LICENSED" -> c.ok
        "DEMO" -> Kinds.of("demo", c.isDark).fg
        "EXPIRED" -> c.warn
        "SUSPENDED", "REVOKED" -> c.bad
        else -> c.muted
    }
}

/* ------------------------------------------------------------------ lists */

/** The search box at the top of a list. */
@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String) {
    ConsoleField(label = null, value = value, onValueChange = onChange, placeholder = placeholder)
}

/** One quick view: its name and, after it, how many it holds ("Renew in 30 days 3"). */
data class Quick(val id: String, val label: String, val count: Int? = null)

/** The quick views of a list, in one line that scrolls sideways; the one chosen wears the console's gradient. */
@Composable
fun QuickRow(items: List<Quick>, selected: String?, onPick: (String) -> Unit) {
    val c = LocalNexora.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { q ->
            val on = q.id == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) Brush.horizontalGradient(listOf(Color(0xFF0A66E0), Color(0xFF6D3FF5))) else SolidColor(c.surface))
                    .border(1.dp, if (on) Color.Transparent else c.borderStrong, CircleShape)
                    .pressable { onPick(q.id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    q.label + (q.count?.let { " $it" } ?: ""),
                    color = if (on) Color.White else c.text,
                    fontSize = 12.5f.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/** A figure tile: its name, the figure, and a line under it — each tile its own colour along the top (the web console's .fig). */
data class Fig(val label: String, val value: String, val line: String, val slot: Int, val onClick: (() -> Unit)? = null)

private val FigPalette = listOf(SwColours.SALES, SwColours.FABRIC, SwColours.JOBWORK, listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)))

/** Two figure tiles a row, each row's tiles the same height. */
@Composable
fun FigGrid(figs: List<Fig>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        figs.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { FigTile(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun FigTile(f: Fig, modifier: Modifier = Modifier) {
    val c = LocalNexora.current
    val g = FigPalette[f.slot % FigPalette.size]
    val ink = if (c.isDark) lerp(g[0], Color.White, 0.35f) else lerp(g[0], Color.Black, 0.15f)
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.medium).then(if (f.onClick != null) Modifier.pressable(f.onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = c.surface,
        border = BorderStroke(1.dp, g[0].copy(alpha = if (c.isDark) 0.40f else 0.22f))
    ) {
        Box(Modifier.background(Brush.verticalGradient(listOf(g[0].copy(alpha = if (c.isDark) 0.16f else 0.07f), c.surface)))) {
            Column(Modifier.fillMaxWidth().padding(start = 13.dp, end = 10.dp, top = 12.dp, bottom = 11.dp)) {
                Text(f.label, color = ink, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(f.value, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.line, color = c.muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.matchParentSize()) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(g)))
            }
        }
    }
}

/** The line over a list: how many, and a note on the right. */
@Composable
fun ListHeading(title: String, note: String? = null) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.text, fontSize = 14.5f.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        note?.let { Text(it, color = c.muted, fontSize = 11.5f.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 210.dp)) }
    }
}

/** One row of a list: a card with its colour down the left edge, that opens its record. */
@Composable
fun RecordRow(accent: Color, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalNexora.current
    Column(
        Modifier
            .fillMaxWidth()
            .appear()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .drawAccentEdge(accent, alpha = 1f)
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        content = content
    )
}

/** A row's title line: the name, an optional right-hand text, and the chevron that says it opens. */
@Composable
fun RowTitle(title: String, right: String? = null, rightColor: Color? = null, chevron: Boolean = true) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        right?.let { Text(it, color = rightColor ?: c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)) }
        if (chevron) Icon(Icons.Outlined.ChevronRight, "Open", tint = c.faint, modifier = Modifier.size(20.dp))
    }
}

/** A software's line inside a row: its mark, its words, and an optional note in colour at the end. */
@Composable
fun SwLine(sw: String, text: String, color: Color? = null, note: String? = null, noteColor: Color? = null, faint: Boolean = false) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        SwDot(sw)
        Spacer(Modifier.width(7.dp))
        Text(text, color = if (faint) c.faint else color ?: c.text, fontSize = 12.5f.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        note?.let {
            Spacer(Modifier.width(6.dp))
            Text(it, color = noteColor ?: c.warn, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** When a list has nothing to show, it says so — and what to do. */
@Composable
fun EmptyCard(text: String) {
    ConsoleCard { Help(text) }
}

/* ---------------------------------------------------------------- windows */

/** DISPLAY, EDIT or NEW — what the window is doing, as the software says it. */
@Composable
fun ModeBadge(mode: String) {
    val c = LocalNexora.current
    val (bg, fg) = when (mode) {
        "EDIT" -> c.warnBg to c.warn
        "NEW" -> c.okBg to c.ok
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to c.muted
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg, contentColor = fg) {
        Text(mode, fontSize = 10.5f.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

/** A window's head: its round mark, its name with DISPLAY / EDIT / NEW beside it, and the line under. */
@Composable
fun WindowHead(mark: String, colours: List<Color>, title: String, subtitle: String, mode: String) {
    val c = LocalNexora.current
    ConsoleCard(padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colours)), contentAlignment = Alignment.Center) {
                Text(mark, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    ModeBadge(mode)
                }
                Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/** "Riverside Sacks Pvt Ltd" → "RS" — the window's mark. */
fun initials(name: String): String =
    name.split(Regex("[ .]+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "N" }

/** One tool of a window's strip: a coloured square with its sign, and its name under it. */
data class Tool(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/** The window's tools, wrapping onto a second line on a phone — never hidden off the edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolStrip(tools: List<Tool>) {
    ConsoleCard(padding = 8) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tools.forEach { ToolButton(it) }
        }
    }
}

@Composable
private fun ToolButton(t: Tool) {
    val c = LocalNexora.current
    Column(
        Modifier
            .width(57.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (t.enabled) Modifier.pressable(t.onClick) else Modifier)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (t.enabled) Brush.linearGradient(listOf(t.color, lerp(t.color, Color.White, 0.18f))) else SolidColor(c.border)),
            contentAlignment = Alignment.Center
        ) {
            Icon(t.icon, null, tint = if (t.enabled) Color.White else c.faint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            t.label, color = if (t.enabled) c.text else c.faint, fontSize = 10.5f.sp, lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2
        )
    }
}

/** Tool colours, as the web console's toolbar has them. */
object ToolColours {
    val blue = Color(0xFF2563EB)
    val green = Color(0xFF16A34A)
    val back = Color(0xFF64748B)
    val orange = Color(0xFFEA580C)
    val amber = Color(0xFFD97706)
    val violet = Color(0xFF7C3AED)
    val teal = Color(0xFF0D9488)
    val red = Color(0xFFDC2626)
}

/** A read-only field: its name, and its value in a grey box — the window's DISPLAY. */
@Composable
fun ReadField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    mono: Boolean = false,
    sub: String? = null,
    subColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = LocalNexora.current
    Column(modifier) {
        Text(label, color = c.muted, fontSize = 11.5f.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (mono) Mono(value.ifEmpty { "—" }, color = valueColor ?: c.text, size = 13.5f)
                else Text(value.ifEmpty { "—" }, color = valueColor ?: c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                sub?.let { Text(it, color = subColor ?: c.muted, fontSize = 11.5f.sp) }
            }
            trailing?.invoke()
        }
    }
}

/** Two fields side by side. */
@Composable
fun FieldPair(first: @Composable (Modifier) -> Unit, second: (@Composable (Modifier) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        first(Modifier.weight(1f))
        if (second != null) second(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

/** A window's tabs: one line that scrolls sideways, a small count after a name where there is one. */
@Composable
fun SubTabs(tabs: List<Pair<String, String>>, selected: String, counts: Map<String, String> = emptyMap(), onPick: (String) -> Unit) {
    val c = LocalNexora.current
    val i = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = i,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        contentColor = c.text,
        divider = { Box(Modifier.fillMaxWidth().height(1.dp).background(c.border)) },
        indicator = { pos ->
            if (i < pos.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[i]), color = MaterialTheme.colorScheme.primary)
        }
    ) {
        tabs.forEachIndexed { n, (id, label) ->
            Tab(selected = n == i, onClick = { onPick(id) }, selectedContentColor = c.text, unselectedContentColor = c.muted) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.5f.sp, fontWeight = if (n == i) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    counts[id]?.let {
                        Spacer(Modifier.width(4.dp))
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One of a few choices, as Material's chips — the window's dropdown on a phone. */
@Composable
fun OptionChips(options: List<Pair<String, String>>, selected: String?, enabled: Boolean = true, onPick: (String) -> Unit) {
    WrapRow {
        options.forEach { (id, label) -> Chip(label, id == selected) { if (enabled) onPick(id) } }
    }
}

/** A labelled group of chips inside a form. */
@Composable
fun ChoiceField(label: String, options: List<Pair<String, String>>, selected: String?, help: String? = null, onPick: (String) -> Unit) {
    val c = LocalNexora.current
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        OptionChips(options, selected, onPick = onPick)
        help?.let { Small(it) }
    }
}

/**
 * A date the owner picks: the field shows it, a tap asks the phone's own calendar (a question of the
 * console's, so it is never shown over the lock). [clearable] adds a way to leave it empty.
 */
@Composable
fun DateField(label: String, value: String, modifier: Modifier = Modifier, clearable: Boolean = false, onAsk: (Ask) -> Unit, onPick: (String) -> Unit) {
    val c = LocalNexora.current
    Column(modifier) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(c.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .pressable { onAsk(Ask.Date(label, value, clearable, onPick)) }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (value.isBlank()) "dd mmm yy" else Fmt.day(value), color = if (value.isBlank()) c.faint else c.text, fontSize = 15.sp,
                modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.Event, "Pick a date", tint = c.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** A small heading inside a card. */
@Composable
fun CardHeading(text: String, line: String? = null) {
    Text(text, style = CardTitleStyle)
    line?.let { Small(it) }
    Spacer(Modifier.height(10.dp))
}

/** "1 demo", "3 demos". */
fun counted(n: Int, word: String): String = "$n $word" + if (n == 1) "" else "s"
