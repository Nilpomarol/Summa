package com.gestorfinances.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Raw design tokens (docs/future/redesign-plan.md). Screens must consume semantic names
// (see FinanceColors) or MaterialTheme, never these raw values.
object TokenColor {
    // Neutrals — light. Near-white ramp; white cards sit on a barely warm page, separated by a
    // hairline rather than a shadow.
    val Ivory = Color(0xFFF9F2E7)
    val Card = Color(0xFFFFFFFF)
    val Page = Color(0xFFFAFAF8)
    val Stone = Color(0xFFF2F2EE)
    val Sand = Color(0xFFE4E4DE)
    val Border = Color(0xFFE8E8E4)
    val BorderStrong = Color(0xFFD4D4CE)
    val TextMuted = Color(0xFF8C948F)
    val TextSecondary = Color(0xFF5F6A63)
    val Ink = Color(0xFF18201C)

    // Neutrals — dark. Neutral black ramp, so forest and sage are the only greens.
    val Dark0 = Color(0xFF101010)
    val Dark50 = Color(0xFF191919)
    val Dark100 = Color(0xFF222222)
    val Dark150 = Color(0xFF2A2A2A)
    val Dark200 = Color(0xFF3A3A3A)
    val Dark400 = Color(0xFF5E5E5E)
    val Dark500 = Color(0xFF808080)
    val Dark700 = Color(0xFFA8A8A8)
    val Dark900 = Color(0xFFEDEDED)

    // Brand — forest, from the logo. Used where attention should go, not as decoration.
    val Forest = Color(0xFF063E29)
    val ForestSoft = Color(0xFF3F6550)
    val Sage = Color(0xFF90A988)
    val SageLight = Color(0xFFB7CEAE)
    val SageSurface = Color(0xFFE0EBDD)
    val ForestContainerDark = Color(0xFF24382C)
    val SageSurfaceDark = Color(0xFFDCE8D6)

    // Functional — fixed meaning (light / dark). Expense is plain ink: the sign carries it.
    val IncomeLight = Color(0xFF3A7054)
    val IncomeDark = Color(0xFF8FC4A0)
    val ExpenseLight = Ink
    val ExpenseDark = Dark900
    val TransferLight = Color(0xFF5C6670)
    val TransferDark = Color(0xFFA9B4BC)
    val SettlementLight = Color(0xFF7A5A1E)
    val SettlementDark = Color(0xFFD9B26E)
    val RefundLight = Color(0xFF2F7370)
    val RefundDark = Color(0xFF7CC4BF)
    val DebtLight = Color(0xFF96524A)
    val DebtDark = Color(0xFFE0A095)
    val SharedLight = Color(0xFF735A7A)
    val SharedDark = Color(0xFFC4A9CF)
    val AlertLight = Color(0xFF8A6428)
    val AlertDark = Color(0xFFE3B964)

    // Figures on the forest hero panel, which is forest in both themes.
    val HeroIncome = Color(0xFFA8DDB6)
    val HeroDebt = Color(0xFFF0B2A8)

    // Trip status — planned / active / finished (container / content, light / dark)
    val TripPlannedContainerLight = SageSurface
    val TripPlannedContentLight = Forest
    val TripPlannedContainerDark = ForestContainerDark
    val TripPlannedContentDark = SageSurfaceDark
    val TripActiveContainerLight = Color(0xFFDDEBDF)
    val TripActiveContentLight = Color(0xFF2C5A41)
    val TripActiveContainerDark = Color(0xFF1E3A2A)
    val TripActiveContentDark = IncomeDark
    val TripFinishedContainerLight = Stone
    val TripFinishedContentLight = TextSecondary
    val TripFinishedContainerDark = Dark100
    val TripFinishedContentDark = Dark700

    // Banner — light triples (background / border / text); Info + Alert + Error are in use.
    val BannerInfoBg = SageSurface
    val BannerInfoBorder = Color(0xFFC9DBC4)
    val BannerInfoText = Forest
    val BannerAlertBg = Color(0xFFF6ECD9)
    val BannerAlertBorder = Color(0xFFE6D2A8)
    val BannerAlertText = Color(0xFF6E4E1C)
    val BannerErrorBg = Color(0xFFF6E3E0)
    val BannerErrorBorder = Color(0xFFEBC7C1)
    val BannerErrorText = Color(0xFF8A3F39)

    // Category
    val CategoryUncategorized = Color(0xFF9A968C)

    // Toggle states: enabled-off remains visibly interactive; disabled-off is lower-emphasis.
    val ToggleOffTrackLight = Sand
    val ToggleOffThumbLight = TextSecondary
    val ToggleOffBorderLight = TextSecondary
    val ToggleDisabledTrackLight = Stone
    val ToggleDisabledThumbLight = TextMuted
    val ToggleDisabledBorderLight = BorderStrong
    val ToggleOffTrackDark = Dark150
    val ToggleOffThumbDark = Dark700
    val ToggleOffBorderDark = Dark700
    val ToggleDisabledTrackDark = Dark100
    val ToggleDisabledThumbDark = Dark400
    val ToggleDisabledBorderDark = Dark200
}

private fun textStyle(
    family: FontFamily,
    sizeSp: Int,
    weight: FontWeight,
    lineHeightSp: Int,
    trackingSp: Float = 0f,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = trackingSp.sp,
)

/**
 * Render any style as a ledger figure: tabular numerals in the style's own face, so list amounts
 * stay in the sans and hero amounts in the serif.
 */
fun TextStyle.asFigures(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Editorial eyebrow: the small, wide-tracked label that sits above a figure or a section.
 * Callers uppercase the text themselves so the string resource stays natural.
 */
fun TextStyle.asEyebrow(): TextStyle =
    copy(fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp)

// Newsreader carries titles and hero amounts; Inter carries everything else. Personality comes
// from the hierarchy, not from weight, so nothing goes past Medium.
private val HeroText = textStyle(DisplayFontFamily, 34, FontWeight.Medium, lineHeightSp = 40, trackingSp = -0.3f)
private val ScreenTitleText = textStyle(DisplayFontFamily, 28, FontWeight.Medium, lineHeightSp = 34, trackingSp = -0.2f)
private val SectionTitleText = textStyle(DisplayFontFamily, 22, FontWeight.Medium, lineHeightSp = 28)
private val CardTitleText = textStyle(InterfaceFontFamily, 16, FontWeight.Medium, lineHeightSp = 22)
private val CardTitleSmallText = textStyle(InterfaceFontFamily, 15, FontWeight.Medium, lineHeightSp = 20)
private val BodyText = textStyle(InterfaceFontFamily, 15, FontWeight.Normal, lineHeightSp = 22)
private val BodyMediumText = textStyle(InterfaceFontFamily, 14, FontWeight.Normal, lineHeightSp = 20)
private val SecondaryText = textStyle(InterfaceFontFamily, 13, FontWeight.Normal, lineHeightSp = 18)
private val LabelText = textStyle(InterfaceFontFamily, 13, FontWeight.Medium, lineHeightSp = 18)
private val LabelSmallText = textStyle(InterfaceFontFamily, 12, FontWeight.Medium, lineHeightSp = 16)
private val MetadataText = textStyle(InterfaceFontFamily, 11, FontWeight.Medium, lineHeightSp = 14)

val GestorLightColorScheme = lightColorScheme(
    primary = TokenColor.Forest,
    onPrimary = TokenColor.Ivory,
    primaryContainer = TokenColor.SageSurface,
    onPrimaryContainer = TokenColor.Forest,
    secondary = TokenColor.ForestSoft,
    onSecondary = TokenColor.Ivory,
    secondaryContainer = TokenColor.SageSurface,
    onSecondaryContainer = TokenColor.Forest,
    tertiary = TokenColor.RefundLight,
    onTertiary = TokenColor.Ivory,
    error = TokenColor.DebtLight,
    onError = TokenColor.Ivory,
    background = TokenColor.Page,
    onBackground = TokenColor.Ink,
    surface = TokenColor.Card,
    onSurface = TokenColor.Ink,
    surfaceVariant = TokenColor.Stone,
    onSurfaceVariant = TokenColor.TextSecondary,
    surfaceContainerLowest = TokenColor.Card,
    surfaceContainerLow = TokenColor.Card,
    surfaceContainer = TokenColor.Card,
    surfaceContainerHigh = TokenColor.Card,
    surfaceContainerHighest = TokenColor.Stone,
    outline = TokenColor.BorderStrong,
    outlineVariant = TokenColor.Border,
    inverseSurface = TokenColor.Ink,
    inverseOnSurface = TokenColor.Ivory,
    inversePrimary = TokenColor.SageLight,
    scrim = TokenColor.Ink,
)

val GestorDarkColorScheme = darkColorScheme(
    primary = TokenColor.SageLight,
    onPrimary = TokenColor.Forest,
    primaryContainer = TokenColor.ForestContainerDark,
    onPrimaryContainer = TokenColor.SageSurfaceDark,
    secondary = TokenColor.Sage,
    onSecondary = TokenColor.Dark0,
    secondaryContainer = TokenColor.ForestContainerDark,
    onSecondaryContainer = TokenColor.SageSurfaceDark,
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
    surfaceContainerLowest = TokenColor.Dark0,
    surfaceContainerLow = TokenColor.Dark50,
    surfaceContainer = TokenColor.Dark50,
    surfaceContainerHigh = TokenColor.Dark100,
    surfaceContainerHighest = TokenColor.Dark150,
    outline = TokenColor.Dark200,
    outlineVariant = TokenColor.Dark150,
    inverseSurface = TokenColor.Dark900,
    inverseOnSurface = TokenColor.Dark0,
    inversePrimary = TokenColor.Forest,
    scrim = TokenColor.Ink,
)

val GestorTypography = Typography(
    displayLarge = HeroText,
    displayMedium = HeroText,
    displaySmall = HeroText,
    headlineLarge = HeroText.copy(fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = ScreenTitleText,
    headlineSmall = SectionTitleText.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = SectionTitleText,
    titleMedium = CardTitleText,
    titleSmall = CardTitleSmallText,
    bodyLarge = BodyText,
    bodyMedium = BodyMediumText,
    bodySmall = SecondaryText,
    labelLarge = LabelText,
    labelMedium = LabelSmallText,
    labelSmall = MetadataText,
)

val GestorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
