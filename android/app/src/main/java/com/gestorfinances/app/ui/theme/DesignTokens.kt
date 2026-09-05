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
    // Neutrals — light. Warm sand ramp; the background is deeper than the card surface so
    // cards read as raised paper instead of near-white on near-white.
    val Neutral0 = Color(0xFFFFFCF6)
    val Neutral50 = Color(0xFFF1E9DB)
    val Neutral100 = Color(0xFFE8DDCB)
    val Neutral150 = Color(0xFFDFD2BE)
    val Neutral200 = Color(0xFFD1C2AB)
    val Neutral300 = Color(0xFFBCA98F)
    val Neutral400 = Color(0xFF9C8A73)
    val Neutral500 = Color(0xFF7C6B59)
    val Neutral600 = Color(0xFF6B5A53)
    val Neutral700 = Color(0xFF5A4A4E)
    val Neutral900 = Color(0xFF2B1024)

    // Neutrals — dark
    val Dark0 = Color(0xFF150F17)
    val Dark50 = Color(0xFF1E1621)
    val Dark100 = Color(0xFF29202C)
    val Dark150 = Color(0xFF382D3B)
    val Dark200 = Color(0xFF493C4C)
    val Dark300 = Color(0xFF5B4C5E)
    val Dark400 = Color(0xFF77677A)
    val Dark500 = Color(0xFF968799)
    val Dark700 = Color(0xFFCBBACB)
    val Dark900 = Color(0xFFF6EFF2)

    // Brand — plum (action/interactive only)
    val Plum = Color(0xFF7C3B6E)
    val PlumHover = Color(0xFF6A3260)
    val PlumPressed = Color(0xFF57284E)
    val PlumTint = Color(0xFFF5E6F0)
    val PlumDark = Color(0xFFE3A8D2)

    // Accent — warm ochre; the counterweight to plum on ink surfaces and highlights.
    val AccentLight = Color(0xFFC97B2E)
    val AccentDark = Color(0xFFE8B071)
    val AccentTintLight = Color(0xFFF9EBD7)
    val AccentTintDark = Color(0xFF3B2A18)

    // Ink hero — a deep plum panel used as the single high-contrast anchor of a page.
    val HeroInkTop = Color(0xFF5B2A50)
    val HeroInkBottom = Color(0xFF24101F)
    val HeroInkTopDark = Color(0xFF3E1D38)
    val HeroInkBottomDark = Color(0xFF190B16)
    val HeroGlow = Color(0xFFB55A9C)

    // Depth — a warm cast shadow, a lit top rim, and a sheen down the face of the card.
    // Together they replace the flat hairline outline the surfaces used to rely on.
    val ShadowLight = Color(0x4D2B1024)
    val ShadowDark = Color(0x99000000)
    val CardRimLight = Color(0xFFFFFFFF)
    val CardRimDark = Color(0xFF4E4152)
    val CardSheenLight = Color(0x59FFFFFF)
    val CardSheenDark = Color(0x0DFFFFFF)

    // Functional — fixed meaning (light / dark)
    val IncomeLight = Color(0xFF55763F)
    val IncomeDark = Color(0xFFB1C891)
    val ExpenseLight = Color(0xFF2B1024)
    val ExpenseDark = Color(0xFFF6EFF2)
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

    // Trip status — planned / active / finished (container / content, light / dark)
    val TripPlannedContainerLight = Color(0xFFF5E6F0)
    val TripPlannedContentLight = Color(0xFF57284E)
    val TripPlannedContainerDark = Color(0xFF493C4C)
    val TripPlannedContentDark = Color(0xFFF6EFF2)
    val TripActiveContainerLight = Color(0xFFE7F0E0)
    val TripActiveContentLight = Color(0xFF3E5B2E)
    val TripActiveContainerDark = Color(0xFF30412A)
    val TripActiveContentDark = Color(0xFFB1C891)
    val TripFinishedContainerLight = Color(0xFFE8DDCB)
    val TripFinishedContentLight = Color(0xFF5A4A4E)
    val TripFinishedContainerDark = Color(0xFF29202C)
    val TripFinishedContentDark = Color(0xFFCBBACB)

    // Banner — light triples (background / border / text); Info + Alert + Error are in use.
    val BannerInfoBg = Color(0xFFF5E6F0)
    val BannerInfoBorder = Color(0xFFE4CBDD)
    val BannerInfoText = Color(0xFF57284E)
    val BannerAlertBg = Color(0xFFFAEDD6)
    val BannerAlertBorder = Color(0xFFEBD4A6)
    val BannerAlertText = Color(0xFF76510B)
    val BannerErrorBg = Color(0xFFFBE8E5)
    val BannerErrorBorder = Color(0xFFEFCAC4)
    val BannerErrorText = Color(0xFF883A35)

    // Category
    val CategoryUncategorized = Color(0xFF9E9187)

    // Toggle states: enabled-off remains visibly interactive; disabled-off is lower-emphasis.
    val ToggleOffTrackLight = Color(0xFFDFD2BE)
    val ToggleOffThumbLight = Color(0xFF5A4A4E)
    val ToggleOffBorderLight = Color(0xFF5A4A4E)
    val ToggleDisabledTrackLight = Color(0xFFE8DDCB)
    val ToggleDisabledThumbLight = Color(0xFF9C8A73)
    val ToggleDisabledBorderLight = Color(0xFFBCA98F)
    val ToggleOffTrackDark = Color(0xFF493C4C)
    val ToggleOffThumbDark = Color(0xFFCBBACB)
    val ToggleOffBorderDark = Color(0xFFCBBACB)
    val ToggleDisabledTrackDark = Color(0xFF29202C)
    val ToggleDisabledThumbDark = Color(0xFF77677A)
    val ToggleDisabledBorderDark = Color(0xFF5B4C5E)
}

private fun interfaceTextStyle(
    sizeSp: Int,
    weight: FontWeight,
    trackingSp: Float = 0f,
) = TextStyle(
    fontFamily = InterfaceFontFamily,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    letterSpacing = trackingSp.sp,
)

/** Render any style as a ledger figure: IBM Plex Mono + tabular numerals (design baseline). */
internal fun TextStyle.asFigures(): TextStyle =
    copy(fontFamily = LedgerMonoFontFamily, fontFeatureSettings = "tnum")

/**
 * Editorial eyebrow: the small, wide-tracked label that sits above a figure or a section.
 * Callers uppercase the text themselves so the string resource stays natural.
 */
internal fun TextStyle.asEyebrow(): TextStyle =
    copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)

// Schibsted Grotesk is a grotesque sans with fairly tight native spacing at large sizes, so
// display and title text take a firmer negative tracking than a serif face would want.
private val DisplayText = interfaceTextStyle(sizeSp = 32, weight = FontWeight.SemiBold, trackingSp = -0.5f)
private val TitleText = interfaceTextStyle(sizeSp = 22, weight = FontWeight.SemiBold, trackingSp = -0.25f)
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)
