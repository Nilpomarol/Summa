package com.gestorfinances.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.gestorfinances.app.data.repository.MovementType

/**
 * Semantic finance colors (design-system §2.4). Screens consume these names instead of
 * raw token hex, so money meaning and identity stay consistent across light/dark.
 */
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val settlement: Color,
    val refund: Color,
    val debt: Color,
    val alert: Color,
    val mutedText: Color,
    val cardBorder: Color,
    val heroSurface: Color,
    val heroOnSurface: Color,
    val heroOnSurfaceMuted: Color,
)

/** Color of an amount by movement type — expense is plain ink, never a hue (§2.4). */
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

/** Soft tint background for a category icon chip (§2.5), derived from its color. */
fun categoryTint(color: Color): Color = color.copy(alpha = 0.16f)

internal val LightFinanceColors = FinanceColors(
    income = TokenColor.IncomeLight,
    expense = TokenColor.ExpenseLight,
    transfer = TokenColor.TransferLight,
    settlement = TokenColor.SettlementLight,
    refund = TokenColor.RefundLight,
    debt = TokenColor.DebtLight,
    alert = TokenColor.AlertLight,
    mutedText = TokenColor.Neutral500,
    cardBorder = TokenColor.Neutral150,
    heroSurface = TokenColor.Neutral900,
    heroOnSurface = TokenColor.Neutral0,
    heroOnSurfaceMuted = TokenColor.Neutral400,
)

internal val DarkFinanceColors = FinanceColors(
    income = TokenColor.IncomeDark,
    expense = TokenColor.ExpenseDark,
    transfer = TokenColor.TransferDark,
    settlement = TokenColor.SettlementDark,
    refund = TokenColor.RefundDark,
    debt = TokenColor.DebtDark,
    alert = TokenColor.AlertDark,
    mutedText = TokenColor.Dark500,
    cardBorder = TokenColor.Dark150,
    heroSurface = TokenColor.Dark100,
    heroOnSurface = TokenColor.Dark900,
    heroOnSurfaceMuted = TokenColor.Dark500,
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }
