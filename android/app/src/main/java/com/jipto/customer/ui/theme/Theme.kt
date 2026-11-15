package com.jipto.customer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

// Official JIPTO Brand Color Palette
val JiptoGreenPrimary = Color(0xFF16A34A)    // Primary Brand Color: Fresh Green (#16A34A)
val JiptoGreenDark = Color(0xFF166534)       // Dark Primary / Price Highlight: Deep Emerald (#166534)
val JiptoGreenLight = Color(0xFFDCFCE7)      // Light Green (#DCFCE7)
val JiptoCreamBackground = Color(0xFFFAFAF5) // App Background: Soft Cream (#FAFAF5)
val JiptoSurfaceWhite = Color(0xFFFFFFFF)    // Card Background: White (#FFFFFF)
val JiptoTextCharcoal = Color(0xFF1F2937)    // Main Text: Dark Charcoal (#1F2937)
val JiptoTextGrey = Color(0xFF6B7280)        // Secondary Text: Grey (#6B7280)
val JiptoPriceEmerald = Color(0xFF166534)    // Price Highlight: Deep Emerald (#166534)
val JiptoDiscountOrange = Color(0xFFF97316)  // Discount Badge: Attractive Orange (#F97316)
val JiptoRedError = Color(0xFFDC2626)        // Error: Red (#DC2626)

private val JiptoLightColorScheme = lightColorScheme(
    primary = JiptoGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = JiptoGreenLight,
    onPrimaryContainer = JiptoGreenDark,
    secondary = JiptoGreenDark,
    onSecondary = Color.White,
    tertiary = JiptoDiscountOrange,
    onTertiary = Color.White,
    background = JiptoCreamBackground,
    onBackground = JiptoTextCharcoal,
    surface = JiptoSurfaceWhite,
    onSurface = JiptoTextCharcoal,
    surfaceVariant = Color(0xFFF3F4EE),
    onSurfaceVariant = JiptoTextGrey,
    error = JiptoRedError,
    onError = Color.White
)

private val JiptoDarkColorScheme = darkColorScheme(
    primary = JiptoGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = JiptoGreenDark,
    onPrimaryContainer = JiptoGreenLight,
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    tertiary = JiptoDiscountOrange,
    onTertiary = Color.White,
    background = Color(0xFF0B111E),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF9CA3AF),
    error = JiptoRedError,
    onError = Color.White
)

val JiptoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

val JiptoTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun JiptoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) JiptoDarkColorScheme else JiptoLightColorScheme,
        shapes = JiptoShapes,
        typography = JiptoTypography,
        content = content
    )
}
