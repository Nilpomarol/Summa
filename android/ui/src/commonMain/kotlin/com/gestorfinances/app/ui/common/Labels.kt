package com.gestorfinances.app.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.account_type_bank
import com.gestorfinances.ui.resources.account_type_cash
import com.gestorfinances.ui.resources.account_type_investment
import com.gestorfinances.ui.resources.account_type_other
import com.gestorfinances.ui.resources.account_type_savings
import com.gestorfinances.ui.resources.movement_type_contribution
import com.gestorfinances.ui.resources.movement_type_expense
import com.gestorfinances.ui.resources.movement_type_income
import com.gestorfinances.ui.resources.movement_type_refund
import com.gestorfinances.ui.resources.movement_type_settlement
import com.gestorfinances.ui.resources.movement_type_transfer
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.MovementType

/** Shared Catalan labels for the domain enums, used across multiple screens. */
@Composable
fun MovementType.label(): String =
    when (this) {
        MovementType.EXPENSE -> stringResource(Res.string.movement_type_expense)
        MovementType.INCOME -> stringResource(Res.string.movement_type_income)
        MovementType.TRANSFER -> stringResource(Res.string.movement_type_transfer)
        MovementType.SETTLEMENT -> stringResource(Res.string.movement_type_settlement)
        MovementType.REFUND -> stringResource(Res.string.movement_type_refund)
        MovementType.CONTRIBUTION -> stringResource(Res.string.movement_type_contribution)
    }

@Composable
fun AccountType.label(): String =
    when (this) {
        AccountType.BANK -> stringResource(Res.string.account_type_bank)
        AccountType.CASH -> stringResource(Res.string.account_type_cash)
        AccountType.SAVINGS -> stringResource(Res.string.account_type_savings)
        AccountType.INVESTMENT -> stringResource(Res.string.account_type_investment)
        AccountType.OTHER -> stringResource(Res.string.account_type_other)
    }
