package org.nexoraofficial.console.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MATERIAL 3, IN NEXORA'S COLOURS.
 *
 *   "app ui can be different then consol — make ui as per android app"
 *
 * The console is a web page and looks like one. This is an Android
 * application, so it is built out of Android's own parts: Material 3
 * components, Material's shapes and elevation, Material's type scale.
 *
 * What it keeps from the console is the only thing that has to travel — the
 * brand. The same #4F7CFF, the same N, the same green for good news and red
 * for bad. Everything else is what an Android user already knows.
 *
 * This is a full M3 scheme rather than a handful of tokens, which means any
 * Material component we never style at all still lands on Nexora's colours,
 * in light and in dark, because the scheme tells it to.
 */
val NexoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4F7CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE7FF),
    onPrimaryContainer = Color(0xFF12275E),

    secondary = Color(0xFF5A6478),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8F3),
    onSecondaryContainer = Color(0xFF1A2233),

    tertiary = Color(0xFF16A34A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7F3E1),
    onTertiaryContainer = Color(0xFF07331A),

    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFDE0E0),
    onErrorContainer = Color(0xFF5A0F0F),

    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF1A2233),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2233),
    surfaceVariant = Color(0xFFECEFF5),
    onSurfaceVariant = Color(0xFF5A6478),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFE),
    surfaceContainer = Color(0xFFF7F9FD),
    surfaceContainerHigh = Color(0xFFEFF2F8),
    surfaceContainerHighest = Color(0xFFE8ECF4),

    outline = Color(0xFFCBD2E1),
    outlineVariant = Color(0xFFE1E5EE),
    inverseSurface = Color(0xFF232B3D),
    inverseOnSurface = Color(0xFFF1F3F9),
    scrim = Color(0xFF000000)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9CB6FF),
    onPrimary = Color(0xFF0E1F4D),
    primaryContainer = Color(0xFF2B4590),
    onPrimaryContainer = Color(0xFFDDE7FF),

    secondary = Color(0xFFBFC7DA),
    onSecondary = Color(0xFF29303F),
    secondaryContainer = Color(0xFF3A4256),
    onSecondaryContainer = Color(0xFFE3E8F3),

    tertiary = Color(0xFF6EE7A8),
    onTertiary = Color(0xFF05301A),
    tertiaryContainer = Color(0xFF12512F),
    onTertiaryContainer = Color(0xFFCFF5E0),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5F1412),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF12141C),
    onBackground = Color(0xFFEEF0F6),
    surface = Color(0xFF1B1E29),
    onSurface = Color(0xFFEEF0F6),
    surfaceVariant = Color(0xFF2A3042),
    onSurfaceVariant = Color(0xFFA8B2CA),
    surfaceContainerLowest = Color(0xFF0C0E14),
    surfaceContainerLow = Color(0xFF181B25),
    surfaceContainer = Color(0xFF1F2330),
    surfaceContainerHigh = Color(0xFF262B3A),
    surfaceContainerHighest = Color(0xFF2E3446),

    outline = Color(0xFF464F6A),
    outlineVariant = Color(0xFF333A4F),
    inverseSurface = Color(0xFFEEF0F6),
    inverseOnSurface = Color(0xFF1B1E29),
    scrim = Color(0xFF000000)
)

/**
 * Themed by hand and remembered, exactly as the console and the desktop
 * application are: the phone's preference decides only where you START.
 */
@Composable
fun NexoraTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalNexora provides c) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = NexoraTypography,
            shapes = NexoraShapes,
            content = content
        )
    }
}
