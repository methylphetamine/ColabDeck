package dev.colabdeck.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColabColors = darkColorScheme(
    primary = Cyan,
    onPrimary = ColorOnCyan,
    primaryContainer = CyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = Violet,
    onSecondary = ColorOnViolet,
    secondaryContainer = Color(0xFF2E2350),
    onSecondaryContainer = TextPrimary,
    tertiary = Amber,
    onTertiary = Color(0xFF1C1200),
    background = Ink,
    onBackground = TextPrimary,
    surface = InkElevated,
    onSurface = TextPrimary,
    surfaceVariant = InkRaised,
    onSurfaceVariant = TextSecondary,
    outline = InkBorder,
    outlineVariant = Color(0xFF1B2735),
    error = Rose,
    onError = Color(0xFF2A0710),
    errorContainer = Color(0xFF3B0D1A),
    onErrorContainer = Color(0xFFFFD9DF),
    scrim = Color(0xCC000000)
)

private val ColabShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ColabDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColabColors,
        typography = ColabTypography,
        shapes = ColabShapes,
        content = content
    )
}
