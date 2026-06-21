package com.gestorfinances.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.MovementType

/**
 * Maps the Material Symbols names stored on categories (design tokens §2.5) to the
 * outlined Compose vectors. Unknown names fall back to the neutral "uncategorized" glyph.
 */
fun categoryIcon(name: String?): ImageVector =
    when (name) {
        "home" -> Icons.Outlined.Home
        "shopping_cart" -> Icons.Outlined.ShoppingCart
        "restaurant" -> Icons.Outlined.Restaurant
        "directions_car" -> Icons.Outlined.DirectionsCar
        "sports_esports" -> Icons.Outlined.SportsEsports
        "credit_card" -> Icons.Outlined.CreditCard
        "payments" -> Icons.Outlined.Payments
        "savings" -> Icons.Outlined.Savings
        else -> Icons.Outlined.MoreHoriz
    }

fun accountTypeIcon(type: AccountType): ImageVector =
    when (type) {
        AccountType.BANK -> Icons.Outlined.AccountBalance
        AccountType.CASH -> Icons.Outlined.Payments
        AccountType.SAVINGS -> Icons.Outlined.Savings
        AccountType.INVESTMENT -> Icons.AutoMirrored.Outlined.TrendingUp
        AccountType.OTHER -> Icons.Outlined.AccountBalanceWallet
    }

/** Icon for a movement chip when no category icon applies (income/transfer/settlement/refund). */
fun movementTypeIcon(type: MovementType): ImageVector =
    when (type) {
        MovementType.INCOME -> Icons.Outlined.SouthWest
        MovementType.EXPENSE -> Icons.Outlined.MoreHoriz
        MovementType.TRANSFER -> Icons.Outlined.SwapHoriz
        MovementType.SETTLEMENT -> Icons.Outlined.Handshake
        MovementType.REFUND -> Icons.AutoMirrored.Outlined.AssignmentReturn
    }
