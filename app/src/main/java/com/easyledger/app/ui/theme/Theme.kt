package com.easyledger.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val CorporateColors = lightColorScheme(
    primary = Color(0xFF176D69),
    onPrimary = Color.White,
    background = Color(0xFFFFFDF7),
    onBackground = Color(0xFF142F35),
    surface = Color.White,
    onSurface = Color(0xFF142F35),
    onSurfaceVariant = Color(0xFF52666A),
    outline = Color(0xFFE2EBE5),
    secondaryContainer = Color(0xFFE7F4EF),
    onSecondaryContainer = Color(0xFF176D69),
)

private val AccessibleTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
)

@Composable
fun EasyLedgerTheme(content: @Composable () -> Unit) {
    // Fixed product colors: One UI wallpaper colors must not override readability.
    MaterialTheme(colorScheme = CorporateColors, typography = AccessibleTypography, content = content)
}
