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
// (docs/design.md). Screens must consume semantic names (see FinanceColors)
// or MaterialTheme, never these raw values.
internal object TokenColor {
    // Neutrals — light
    val Neutral0 = Color(0xFFFFFDF9)
    val Neutral50 = Color(0xFFF6F0E5)
    val Neutral100 = Color(0xFFEEE6DA)
    val Neutral150 = Color(0xFFE7DDD1)
    val Neutral200 = Color(0xFFD8CCBF)
    val Neutral300 = Color(0xFFC7B9AA)
    val Neutral400 = Color(0xFFA59588)
    val Neutral500 = Color(0xFF85776D)
    val Neutral700 = Color(0xFF66584F)
    val Neutral900 = Color(0xFF331329)

    // Neutrals — dark
    val Dark0 = Color(0xFF191317)
    val Dark50 = Color(0xFF21191F)
    val Dark100 = Color(0xFF2B222A)
    val Dark150 = Color(0xFF393039)
    val Dark200 = Color(0xFF4A3D49)
    val Dark300 = Color(0xFF5B4E59)
    val Dark400 = Color(0xFF766874)
    val Dark500 = Color(0xFF958793)
    val Dark700 = Color(0xFFC9BAC5)
    val Dark900 = Color(0xFFF5EFEB)

    // Brand — indigo (action/interactive only)
    val Plum = Color(0xFF765077)
    val PlumHover = Color(0xFF674267)
    val PlumPressed = Color(0xFF542F53)
    val PlumTint = Color(0xFFF0E7EE)
    val PlumDark = Color(0xFFC9A5C7)

    // Functional — fixed meaning (light / dark)
    val IncomeLight = Color(0xFF5E7448)
    val IncomeDark = Color(0xFFB1C891)
    val ExpenseLight = Color(0xFF331329)
    val ExpenseDark = Color(0xFFF5EFEB)
    val TransferLight = Color(0xFF5F667A)
    val TransferDark = Color(0xFFAEB4C4)
    val SettlementLight = Color(0xFFA66F1C)
    val SettlementDark = Color(0xFFE0AE5B)
    val RefundLight = Color(0xFF277F7D)
    val RefundDark = Color(0xFF6BC2BE)
    val DebtLight = Color(0xFFB94E46)
    val DebtDark = Color(0xFFEF8A80)
    val SharedLight = Color(0xFF7A5A91)
    val SharedDark = Color(0xFFC5A7DC)
    val AlertLight = Color(0xFFA56B00)
    val AlertDark = Color(0xFFE5B75D)

    // Trip status â€” planned / active / finished (container / content, light / dark)
    val TripPlannedContainerLight = Color(0xFFF0E7EE)
    val TripPlannedContentLight = Color(0xFF59385A)
    val TripPlannedContainerDark = Color(0xFF4A3D49)
    val TripPlannedContentDark = Color(0xFFF5EFEB)
    val TripActiveContainerLight = Color(0xFFE8F0E3)
    val TripActiveContentLight = Color(0xFF405B32)
    val TripActiveContainerDark = Color(0xFF30412A)
    val TripActiveContentDark = Color(0xFFB1C891)
    val TripFinishedContainerLight = Color(0xFFEEE6DA)
    val TripFinishedContentLight = Color(0xFF66584F)
    val TripFinishedContainerDark = Color(0xFF2B222A)
    val TripFinishedContentDark = Color(0xFFC9BAC5)

    // Banner — light triples (background / border / text); Info + Alert + Error are in use.
    val BannerInfoBg = Color(0xFFF0E7EE)
    val BannerInfoBorder = Color(0xFFDFCFE0)
    val BannerInfoText = Color(0xFF59385A)
    val BannerAlertBg = Color(0xFFFBF0D9)
    val BannerAlertBorder = Color(0xFFEDD8AE)
    val BannerAlertText = Color(0xFF76510B)
    val BannerErrorBg = Color(0xFFFBE8E5)
    val BannerErrorBorder = Color(0xFFEFCAC4)
    val BannerErrorText = Color(0xFF883A35)

    // Category
    val CategoryUncategorized = Color(0xFF9E9187)

    // Toggle states: enabled-off remains visibly interactive; disabled-off is lower-emphasis.
    val ToggleOffTrackLight = Color(0xFFE7DDD1)
    val ToggleOffThumbLight = Color(0xFF66584F)
    val ToggleOffBorderLight = Color(0xFF66584F)
    val ToggleDisabledTrackLight = Color(0xFFEEE6DA)
    val ToggleDisabledThumbLight = Color(0xFFA59588)
    val ToggleDisabledBorderLight = Color(0xFFC7B9AA)
    val ToggleOffTrackDark = Color(0xFF4A3D49)
    val ToggleOffThumbDark = Color(0xFFC9BAC5)
    val ToggleOffBorderDark = Color(0xFFC9BAC5)
    val ToggleDisabledTrackDark = Color(0xFF2B222A)
    val ToggleDisabledThumbDark = Color(0xFF766874)
    val ToggleDisabledBorderDark = Color(0xFF5B4E59)
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

/** Render any style as a ledger figure: IBM Plex Mono + tabular numerals (design baseline). */
internal fun TextStyle.asFigures(): TextStyle =
    copy(fontFamily = LedgerMonoFontFamily, fontFeatureSettings = "tnum")

private val DisplayText = interfaceTextStyle(sizeSp = 28, weight = FontWeight.SemiBold)
private val TitleText = interfaceTextStyle(sizeSp = 21, weight = FontWeight.SemiBold)
private val HeadingText = interfaceTextStyle(sizeSp = 16, weight = FontWeight.SemiBold)
private val BodyText = interfaceTextStyle(sizeSp = 14, weight = FontWeight.Medium)
private val BodySmallText = interfaceTextStyle(sizeSp = 13, weight = FontWeight.Medium)
private val LabelText = interfaceTextStyle(sizeSp = 12, weight = FontWeight.Medium)
private val CaptionText = interfaceTextStyle(sizeSp = 11, weight = FontWeight.Medium)

internal val GestorLightColorScheme = lightColorScheme(
    primary = TokenColor.Plum,
    onPrimary = TokenColor.Neutral0,
    primaryContainer = TokenColor.PlumTint,
    onPrimaryContainer = TokenColor.PlumPressed,
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
    primary = TokenColor.PlumDark,
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
