package com.gestorfinances.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.MovementType

/** Shared Catalan labels for the domain enums, used across multiple screens. */
@Composable
internal fun MovementType.label(): String =
    when (this) {
        MovementType.EXPENSE -> stringResource(R.string.movement_type_expense)
        MovementType.INCOME -> stringResource(R.string.movement_type_income)
        MovementType.TRANSFER -> stringResource(R.string.movement_type_transfer)
        MovementType.SETTLEMENT -> stringResource(R.string.movement_type_settlement)
        MovementType.REFUND -> stringResource(R.string.movement_type_refund)
        MovementType.CONTRIBUTION -> stringResource(R.string.movement_type_contribution)
    }

@Composable
internal fun AccountType.label(): String =
    when (this) {
        AccountType.BANK -> stringResource(R.string.account_type_bank)
        AccountType.CASH -> stringResource(R.string.account_type_cash)
        AccountType.SAVINGS -> stringResource(R.string.account_type_savings)
        AccountType.INVESTMENT -> stringResource(R.string.account_type_investment)
        AccountType.OTHER -> stringResource(R.string.account_type_other)
    }
