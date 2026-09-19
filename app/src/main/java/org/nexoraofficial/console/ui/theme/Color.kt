package org.nexoraofficial.console.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * THE CONSOLE'S OWN TOKENS, VALUE FOR VALUE.
 *
 * Every colour here is lifted from the licence console's `:root` block in
 * server/src/admin.js — which itself wears the desktop application's theme.
 * Nothing is "close enough": a number that differs from the web console is a
 * bug, because somebody who spends the day in Nexora and then opens this on
 * their phone should not feel they have left the product.
 */
@Immutable
data class NexoraColors(
    val bg: Color,
    val bgElevated: Color,
    val bgSunken: Color,
    val surface: Color,
    val surfaceHover: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentBg: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val okBg: Color,
    val warnBg: Color,
    val badBg: Color,
    val isDark: Boolean
)

val LightColors = NexoraColors(
    bg = Color(0xFFF4F6FB),
    bgElevated = Color(0xFFFFFFFF),
    bgSunken = Color(0xFFECEFF5),
    surface = Color(0xFFFFFFFF),
    surfaceHover = Color(0xFFF1F4FA),
    border = Color(0xFFE1E5EE),
    borderStrong = Color(0xFFCBD2E1),
    text = Color(0xFF1A2233),
    muted = Color(0xFF667085),
    faint = Color(0xFF98A2B3),
    accent = Color(0xFF4F7CFF),
    accentBg = Color(0xFFEAF1FE),
    ok = Color(0xFF16A34A),
    warn = Color(0xFFD97706),
    bad = Color(0xFFDC2626),
    okBg = Color(0xFFE8F7EE),
    warnBg = Color(0xFFFEF3E2),
    badBg = Color(0xFFFDEAEA),
    isDark = false
)

/* 4.39.0's dark mode: the quiet writing lifted to 4.82:1 against a card,
   and depth carried by a hairline of light on the top edge rather than by
   a black shadow nobody can see on a near-black page. */
val DarkColors = NexoraColors(
    bg = Color(0xFF12141C),
    bgElevated = Color(0xFF1B1E29),
    bgSunken = Color(0xFF0C0E14),
    surface = Color(0xFF1B1E29),
    surfaceHover = Color(0xFF232735),
    border = Color(0xFF333A4F),
    borderStrong = Color(0xFF464F6A),
    text = Color(0xFFEEF0F6),
    muted = Color(0xFFA8B2CA),
    faint = Color(0xFF7F8AA6),
    accent = Color(0xFF4F7CFF),
    accentBg = Color(0xFF1C2740),
    ok = Color(0xFF34D399),
    warn = Color(0xFFFBBF24),
    bad = Color(0xFFF87171),
    okBg = Color(0xFF12291F),
    warnBg = Color(0xFF2C2410),
    badBg = Color(0xFF2C1616),
    isDark = true
)

val LocalNexora = staticCompositionLocalOf { LightColors }
