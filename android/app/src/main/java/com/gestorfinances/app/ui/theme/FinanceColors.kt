package com.gestorfinances.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.gestorfinances.app.data.repository.MovementType

/**
 * Semantic finance colors (design baseline). Screens consume these names instead of
 * raw token hex, so money meaning and identity stay consistent across light/dark.
 */
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val settlement: Color,
    val refund: Color,
    val debt: Color,
    val shared: Color,
    val alert: Color,
    val tripPlannedContainer: Color,
    val tripPlannedContent: Color,
    val tripActiveContainer: Color,
    val tripActiveContent: Color,
    val tripFinishedContainer: Color,
    val tripFinishedContent: Color,
    val mutedText: Color,
    val subtleText: Color,
    val disabledText: Color,
    val switchOffTrack: Color,
    val switchOffThumb: Color,
    val switchOffBorder: Color,
    val switchDisabledTrack: Color,
    val switchDisabledThumb: Color,
    val switchDisabledBorder: Color,
    val cardBorder: Color,
    val cardShadow: Color,
    val cardRim: Color,
    val cardSheen: Color,
    val accent: Color,
    val accentTint: Color,
    val heroInkTop: Color,
    val heroInkBottom: Color,
    val heroGlow: Color,
    val heroSurface: Color,
    val heroOnSurface: Color,
    val heroOnSurfaceMuted: Color,
    val heroIncome: Color,
    val heroDebt: Color,
    val bottomBarSurface: Color,
    val bottomBarContent: Color,
    val bottomBarActive: Color,
    val bottomBarDivider: Color,
)

/** Color of an amount by movement type — expense is plain ink, never a hue. */
fun FinanceColors.amountColor(type: MovementType): Color =
    when (type) {
        MovementType.INCOME -> income
        MovementType.EXPENSE -> expense
        MovementType.TRANSFER -> transfer
        MovementType.SETTLEMENT -> settlement
        MovementType.REFUND -> refund
        MovementType.EXTERNAL_EXPENSE -> debt
    }

/** Parse a stored category color hex (`#RRGGBB`); falls back to the neutral category color. */
fun categoryColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return TokenColor.CategoryUncategorized
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: IllegalArgumentException) {
        TokenColor.CategoryUncategorized
    }
}

/** Soft tint background for a category icon chip, derived from its color. */
fun categoryTint(color: Color): Color = color.copy(alpha = 0.16f)

/** Keeps saved identity hues legible on dark surfaces without changing stored values. */
@Composable
fun themedIdentityColor(color: Color): Color {
    if (!FinanceTheme.isDark || color.luminance() >= 0.28f) return color
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = maxOf(hsl[2], 0.62f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * The glyph that sits on a solid identity colour. A filled tile is the colour's own object rather
 * than part of the page, so its contents take paper or ink by the fill's luminance instead of
 * following the theme's text colour.
 */
fun onIdentityColor(fill: Color): Color =
    if (fill.luminance() > 0.45f) TokenColor.Neutral900 else TokenColor.Neutral0

/**
 * Identity color adjusted for use as a solid data mark — a share rule, a chart segment. A saved
 * color can sit close enough to the page ink that a filled bar reads as a divider, or close
 * enough to the surface that it disappears; either way the mark stops looking like data. Only
 * lightness is clamped, never hue or saturation: the color is the user's own choice, so a grey
 * category still renders grey, just a grey that cannot be mistaken for a rule.
 *
 * Icons and text keep [themedIdentityColor] instead — a near-ink glyph on a pale tint stays
 * perfectly legible, so only filled marks need this.
 */
@Composable
fun dataMarkColor(color: Color): Color {
    val minLightness = if (FinanceTheme.isDark) 0.48f else 0.38f
    val maxLightness = if (FinanceTheme.isDark) 0.82f else 0.68f
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    val clamped = hsl[2].coerceIn(minLightness, maxLightness)
    if (clamped == hsl[2]) return color
    hsl[2] = clamped
    return Color(ColorUtils.HSLToColor(hsl))
}

internal val LightFinanceColors = FinanceColors(
    income = TokenColor.IncomeLight,
    expense = TokenColor.ExpenseLight,
    transfer = TokenColor.TransferLight,
    settlement = TokenColor.SettlementLight,
    refund = TokenColor.RefundLight,
    debt = TokenColor.DebtLight,
    shared = TokenColor.SharedLight,
    alert = TokenColor.AlertLight,
    tripPlannedContainer = TokenColor.TripPlannedContainerLight,
    tripPlannedContent = TokenColor.TripPlannedContentLight,
    tripActiveContainer = TokenColor.TripActiveContainerLight,
    tripActiveContent = TokenColor.TripActiveContentLight,
    tripFinishedContainer = TokenColor.TripFinishedContainerLight,
    tripFinishedContent = TokenColor.TripFinishedContentLight,
    mutedText = TokenColor.Neutral700,
    subtleText = TokenColor.Neutral600,
    disabledText = TokenColor.Neutral400,
    switchOffTrack = TokenColor.ToggleOffTrackLight,
    switchOffThumb = TokenColor.ToggleOffThumbLight,
    switchOffBorder = TokenColor.ToggleOffBorderLight,
    switchDisabledTrack = TokenColor.ToggleDisabledTrackLight,
    switchDisabledThumb = TokenColor.ToggleDisabledThumbLight,
    switchDisabledBorder = TokenColor.ToggleDisabledBorderLight,
    cardBorder = TokenColor.Neutral150,
    cardShadow = TokenColor.ShadowLight,
    cardRim = TokenColor.CardRimLight,
    cardSheen = TokenColor.CardSheenLight,
    accent = TokenColor.AccentLight,
    accentTint = TokenColor.AccentTintLight,
    heroInkTop = TokenColor.HeroInkTop,
    heroInkBottom = TokenColor.HeroInkBottom,
    heroGlow = TokenColor.HeroGlow,
    heroSurface = TokenColor.Neutral900,
    heroOnSurface = TokenColor.Neutral0,
    heroOnSurfaceMuted = TokenColor.Neutral400,
    // Hero surfaces are always dark ink regardless of the app's light/dark theme, so content on
    // them always needs the brighter dark-mode functional colors for contrast (design baseline).
    heroIncome = TokenColor.IncomeDark,
    heroDebt = TokenColor.DebtDark,
    bottomBarSurface = TokenColor.Neutral0,
    bottomBarContent = TokenColor.Neutral700,
    bottomBarActive = TokenColor.Plum,
    bottomBarDivider = TokenColor.Neutral150,
)

internal val DarkFinanceColors = FinanceColors(
    income = TokenColor.IncomeDark,
    expense = TokenColor.ExpenseDark,
    transfer = TokenColor.TransferDark,
    settlement = TokenColor.SettlementDark,
    refund = TokenColor.RefundDark,
    debt = TokenColor.DebtDark,
    shared = TokenColor.SharedDark,
    alert = TokenColor.AlertDark,
    tripPlannedContainer = TokenColor.TripPlannedContainerDark,
    tripPlannedContent = TokenColor.TripPlannedContentDark,
    tripActiveContainer = TokenColor.TripActiveContainerDark,
    tripActiveContent = TokenColor.TripActiveContentDark,
    tripFinishedContainer = TokenColor.TripFinishedContainerDark,
    tripFinishedContent = TokenColor.TripFinishedContentDark,
    mutedText = TokenColor.Dark700,
    subtleText = TokenColor.Dark500,
    disabledText = TokenColor.Dark400,
    switchOffTrack = TokenColor.ToggleOffTrackDark,
    switchOffThumb = TokenColor.ToggleOffThumbDark,
    switchOffBorder = TokenColor.ToggleOffBorderDark,
    switchDisabledTrack = TokenColor.ToggleDisabledTrackDark,
    switchDisabledThumb = TokenColor.ToggleDisabledThumbDark,
    switchDisabledBorder = TokenColor.ToggleDisabledBorderDark,
    cardBorder = TokenColor.Dark150,
    cardShadow = TokenColor.ShadowDark,
    cardRim = TokenColor.CardRimDark,
    cardSheen = TokenColor.CardSheenDark,
    accent = TokenColor.AccentDark,
    accentTint = TokenColor.AccentTintDark,
    heroInkTop = TokenColor.HeroInkTopDark,
    heroInkBottom = TokenColor.HeroInkBottomDark,
    heroGlow = TokenColor.HeroGlow,
    heroSurface = TokenColor.Dark100,
    heroOnSurface = TokenColor.Dark900,
    heroOnSurfaceMuted = TokenColor.Dark700,
    heroIncome = TokenColor.IncomeDark,
    heroDebt = TokenColor.DebtDark,
    bottomBarSurface = TokenColor.Dark50,
    bottomBarContent = TokenColor.Dark700,
    bottomBarActive = TokenColor.PlumDark,
    bottomBarDivider = TokenColor.Dark300,
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }
