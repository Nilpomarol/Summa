package com.gestorfinances.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Raw design tokens, mirrored verbatim from shared/design/tokens/design-tokens.json
// (docs/08-design-system.md). Screens must consume semantic names (see FinanceColors)
// or MaterialTheme, never these raw values.
internal object TokenColor {
    // Neutrals — light
    val Neutral0 = Color(0xFFFFFFFF)
    val Neutral50 = Color(0xFFF7F8FA)
    val Neutral100 = Color(0xFFF0F1F4)
    val Neutral150 = Color(0xFFEAECEF)
    val Neutral200 = Color(0xFFE2E5EA)
    val Neutral300 = Color(0xFFCDD2DA)
    val Neutral400 = Color(0xFFA4ABB7)
    val Neutral500 = Color(0xFF8A92A0)
    val Neutral700 = Color(0xFF4A5160)
    val Neutral900 = Color(0xFF0B0D12)

    // Neutrals — dark
    val Dark0 = Color(0xFF0E1014)
    val Dark50 = Color(0xFF16181E)
    val Dark100 = Color(0xFF1E2128)
    val Dark150 = Color(0xFF23262E)
    val Dark200 = Color(0xFF2C303A)
    val Dark300 = Color(0xFF3A3F4B)
    val Dark400 = Color(0xFF565E6C)
    val Dark500 = Color(0xFF7C8494)
    val Dark700 = Color(0xFFAEB6C4)
    val Dark900 = Color(0xFFF2F4F8)

    // Brand — indigo (action/interactive only)
    val Indigo = Color(0xFF3344E0)
    val IndigoHover = Color(0xFF2A3BCB)
    val IndigoPressed = Color(0xFF2230AE)
    val IndigoTint = Color(0xFFECEEFD)
    val IndigoDark = Color(0xFF6E7BFF)

    // Functional — fixed meaning (light / dark)
    val IncomeLight = Color(0xFF1F8F5F)
    val IncomeDark = Color(0xFF43C28A)
    val ExpenseLight = Color(0xFF20242E)
    val ExpenseDark = Color(0xFFF2F4F8)
    val TransferLight = Color(0xFF5B6B86)
    val TransferDark = Color(0xFF8A99B5)
    val SettlementLight = Color(0xFFB9772A)
    val SettlementDark = Color(0xFFD9A152)
    val RefundLight = Color(0xFF128A93)
    val RefundDark = Color(0xFF3FB6BE)
    val DebtLight = Color(0xFFCC4B4B)
    val DebtDark = Color(0xFFE8736F)
    val SharedLight = Color(0xFF6D5DD3)
    val SharedDark = Color(0xFFA99BFF)
    val AlertLight = Color(0xFFC98A14)
    val AlertDark = Color(0xFFE0A93C)

    // Banner — light triples (background / border / text); Info + Alert + Error are in use.
    val BannerInfoBg = Color(0xFFECEEFD)
    val BannerInfoBorder = Color(0xFFD5D9FA)
    val BannerInfoText = Color(0xFF28308C)
    val BannerAlertBg = Color(0xFFFBF1DD)
    val BannerAlertBorder = Color(0xFFF1E2BE)
    val BannerAlertText = Color(0xFF7A5A12)
    val BannerErrorBg = Color(0xFFFBEAEA)
    val BannerErrorBorder = Color(0xFFF0CDCD)
    val BannerErrorText = Color(0xFF8E2F2F)

    // Category
    val CategoryUncategorized = Color(0xFF9097A3)
}

private fun interfaceTextStyle(
    sizeSp: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = GeistFontFamily,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    letterSpacing = 0.sp,
)

/** Render any style as a ledger figure: Geist Mono + tabular numerals (design §3). */
internal fun TextStyle.asFigures(): TextStyle =
    copy(fontFamily = GeistMonoFontFamily, fontFeatureSettings = "tnum")

private val DisplayText = interfaceTextStyle(sizeSp = 28, weight = FontWeight.SemiBold)
private val TitleText = interfaceTextStyle(sizeSp = 21, weight = FontWeight.SemiBold)
private val HeadingText = interfaceTextStyle(sizeSp = 16, weight = FontWeight.SemiBold)
private val BodyText = interfaceTextStyle(sizeSp = 14, weight = FontWeight.Medium)
private val BodySmallText = interfaceTextStyle(sizeSp = 13, weight = FontWeight.Medium)
private val LabelText = interfaceTextStyle(sizeSp = 12, weight = FontWeight.Medium)
private val CaptionText = interfaceTextStyle(sizeSp = 11, weight = FontWeight.Medium)

internal val GestorLightColorScheme = lightColorScheme(
    primary = TokenColor.Indigo,
    onPrimary = TokenColor.Neutral0,
    primaryContainer = TokenColor.IndigoTint,
    onPrimaryContainer = TokenColor.IndigoPressed,
    secondary = TokenColor.IncomeLight,
    onSecondary = TokenColor.Neutral0,
    tertiary = TokenColor.RefundLight,
    onTertiary = TokenColor.Neutral0,
    error = TokenColor.DebtLight,
    onError = TokenColor.Neutral0,
    background = TokenColor.Neutral50,
    onBackground = TokenColor.Neutral900,
    surface = TokenColor.Neutral0,
    onSurface = TokenColor.Neutral900,
    surfaceVariant = TokenColor.Neutral100,
    onSurfaceVariant = TokenColor.Neutral700,
    outline = TokenColor.Neutral200,
    outlineVariant = TokenColor.Neutral150,
    inverseSurface = TokenColor.Neutral900,
    inverseOnSurface = TokenColor.Neutral0,
    scrim = TokenColor.Neutral900,
)

internal val GestorDarkColorScheme = darkColorScheme(
    primary = TokenColor.IndigoDark,
    onPrimary = TokenColor.Dark0,
    primaryContainer = TokenColor.Dark200,
    onPrimaryContainer = TokenColor.Dark900,
    secondary = TokenColor.IncomeDark,
    onSecondary = TokenColor.Dark0,
    tertiary = TokenColor.RefundDark,
    onTertiary = TokenColor.Dark0,
    error = TokenColor.DebtDark,
    onError = TokenColor.Dark0,
    background = TokenColor.Dark0,
    onBackground = TokenColor.Dark900,
    surface = TokenColor.Dark50,
    onSurface = TokenColor.Dark900,
    surfaceVariant = TokenColor.Dark100,
    onSurfaceVariant = TokenColor.Dark700,
    outline = TokenColor.Dark150,
    outlineVariant = TokenColor.Dark300,
    inverseSurface = TokenColor.Dark900,
    inverseOnSurface = TokenColor.Dark0,
    scrim = TokenColor.Neutral900,
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
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)
