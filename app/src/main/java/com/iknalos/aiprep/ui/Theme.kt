package com.iknalos.aiprep.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Indigo = Color(0xFF3D6BFF)
val IndigoLight = Color(0xFF7C9CFF)
val Cyan = Color(0xFF22D3EE)
val Ink = Color(0xFF0B1020)
val InkSoft = Color(0xFF141B33)
val InkCard = Color(0xFF1A2340)
val Good = Color(0xFF34D399)
val Warn = Color(0xFFFBBF24)
val Bad = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Ink,
    primaryContainer = Indigo,
    onPrimaryContainer = Color.White,
    secondary = Cyan,
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFE8ECF8),
    surface = InkSoft,
    onSurface = Color(0xFFE8ECF8),
    surfaceVariant = InkCard,
    onSurfaceVariant = Color(0xFFA9B4D0),
    outline = Color(0xFF33406B),
    error = Bad,
    onError = Ink
)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0A1F5C),
    secondary = Color(0xFF0E7490),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF14182B),
    surface = Color.White,
    onSurface = Color(0xFF14182B),
    surfaceVariant = Color(0xFFEDF0F9),
    onSurfaceVariant = Color(0xFF525C7A),
    outline = Color(0xFFC8D0E6),
    error = Color(0xFFB3261E)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun AIPrepTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
