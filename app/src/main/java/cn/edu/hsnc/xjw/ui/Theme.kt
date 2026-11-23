package cn.edu.hsnc.xjw.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0E0E11)
val CardBg = Color(0xFF17171C)
val CardBg2 = Color(0xFF1F1F26)
val Line = Color(0xFF2A2A32)
val TextMain = Color(0xFFECECEE)
val TextSub = Color(0xFF9B9BA3)
val TextDim = Color(0xFF6B6B75)
val Accent = Color(0xFF4F7CFF)
val AccentSoft = Color(0xFF1B2A4A)
val Good = Color(0xFF35C79B)
val Warn = Color(0xFFE0A94B)
val Bad = Color(0xFFE06C6C)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFFC9D8FF),
    secondary = Good,
    background = Bg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = CardBg2,
    onSurfaceVariant = TextSub,
    outline = Line,
    outlineVariant = Line,
    error = Bad,
    onError = Color.White
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp)
)

@Composable
fun XjwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
