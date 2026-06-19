package com.gestorfinances.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object TokenColor {
    val Neutral0 = Color(0xFFFFFFFF)
    val Neutral50 = Color(0xFFF7F8FA)
    val Neutral100 = Color(0xFFF0F1F4)
    val Neutral200 = Color(0xFFE2E5EA)
    val Neutral400 = Color(0xFFA4ABB7)
    val Neutral700 = Color(0xFF4A5160)
    val Neutral900 = Color(0xFF0B0D12)

    val Indigo = Color(0xFF3344E0)
    val IndigoPressed = Color(0xFF2230AE)
    val IndigoTint = Color(0xFFECEEFD)
    val IncomePositive = Color(0xFF1F8F5F)
    val Refund = Color(0xFF128A93)
    val DebtDanger = Color(0xFFCC4B4B)
}

private fun tokenTextStyle(
    sizeSp: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    letterSpacing = 0.sp,
)

private val DisplayText = tokenTextStyle(sizeSp = 28, weight = FontWeight.SemiBold)
private val TitleText = tokenTextStyle(sizeSp = 21, weight = FontWeight.SemiBold)
private val HeadingText = tokenTextStyle(sizeSp = 16, weight = FontWeight.SemiBold)
private val BodyText = tokenTextStyle(sizeSp = 14, weight = FontWeight.Medium)
private val BodySmallText = tokenTextStyle(sizeSp = 13, weight = FontWeight.Medium)
private val LabelText = tokenTextStyle(sizeSp = 12, weight = FontWeight.Medium)
private val CaptionText = tokenTextStyle(sizeSp = 11, weight = FontWeight.Medium)

internal val GestorLightColorScheme = lightColorScheme(
    primary = TokenColor.Indigo,
    onPrimary = TokenColor.Neutral0,
    primaryContainer = TokenColor.IndigoTint,
    onPrimaryContainer = TokenColor.IndigoPressed,
    secondary = TokenColor.IncomePositive,
    onSecondary = TokenColor.Neutral0,
    tertiary = TokenColor.Refund,
    onTertiary = TokenColor.Neutral0,
    error = TokenColor.DebtDanger,
    onError = TokenColor.Neutral0,
    background = TokenColor.Neutral50,
    onBackground = TokenColor.Neutral900,
    surface = TokenColor.Neutral0,
    onSurface = TokenColor.Neutral900,
    surfaceVariant = TokenColor.Neutral100,
    onSurfaceVariant = TokenColor.Neutral700,
    outline = TokenColor.Neutral200,
    outlineVariant = TokenColor.Neutral400,
)

internal val GestorTypography = Typography(
    displayLarge = DisplayText,
    displayMedium = DisplayText,
    displaySmall = DisplayText,
    headlineLarge = TitleText,
    headlineMedium = DisplayText,
    headlineSmall = TitleText,
    titleLarge = TitleText,
    titleMedium = HeadingText,
    titleSmall = HeadingText,
    bodyLarge = BodyText,
    bodyMedium = BodySmallText,
    bodySmall = BodySmallText,
    labelLarge = LabelText,
    labelMedium = LabelText,
    labelSmall = CaptionText,
)

internal val GestorShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)
