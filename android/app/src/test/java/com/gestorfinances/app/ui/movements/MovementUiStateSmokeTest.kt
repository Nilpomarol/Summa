package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.MovementAmountRole
import com.gestorfinances.app.ui.common.primaryAmountRole
import com.gestorfinances.app.ui.common.secondaryAmountRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementUiStateSmokeTest {
    @Test
    fun activeFilterCountMatchesNamedLedgerFilters() {
        assertEquals(
            5,
            MovementFilters(
                accountId = "checking",
                categoryId = "food",
                tripId = "trip",
                tagId = "tag",
                dateFrom = "2026-01-01",
                dateTo = "2026-01-31",
            ).activeFilterCount,
        )
        assertEquals(
            3,
            MovementFilters(
                categoryId = "food",
                sourceMode = MovementSourceMode.ACTUAL,
                oneTimeMode = MovementOneTimeMode.EXCLUDE,
            ).activeFilterCount,
        )
        assertEquals(0, MovementFilters().activeFilterCount)
    }

    @Test
    fun visibleMovementsRemainInContinuousLedgerOrder() {
        val state = MovementsUiState(
            movements = listOf(
                movement("newer", MovementType.EXPENSE, date = "2026-01-05", accountId = "a", accountName = "A"),
                movement("same-day", MovementType.INCOME, date = "2026-01-05", accountId = "a", accountName = "A"),
                movement("older", MovementType.EXPENSE, date = "2026-01-04", accountId = "a", accountName = "A"),
            ),
        )

        assertEquals(listOf("newer", "same-day", "older"), state.visibleMovements.map { it.id })
    }

    @Test
    fun sharedAmountRolesNameMyShareAndKeepTotalSecondary() {
        val shared = movement(
            id = "shared",
            type = MovementType.EXPENSE,
            accountId = "checking",
            accountName = "Compte",
            isShared = true,
            userShareCents = 1_500,
        )
        val external = movement(
            id = "external",
            type = MovementType.EXTERNAL_EXPENSE,
            accountId = null,
            accountName = null,
            userShareCents = 0,
        )
        val personal = movement(
            id = "personal",
            type = MovementType.EXPENSE,
            accountId = "checking",
            accountName = "Compte",
        )

        assertEquals(MovementAmountRole.YOUR_SHARE, shared.primaryAmountRole())
        assertEquals(MovementAmountRole.TOTAL, shared.secondaryAmountRole())
        assertEquals(MovementAmountRole.YOUR_SHARE, external.primaryAmountRole())
        assertEquals(MovementAmountRole.TOTAL, external.secondaryAmountRole())
        assertEquals(MovementAmountRole.MOVEMENT, personal.primaryAmountRole())
        assertEquals(null, personal.secondaryAmountRole())
    }

    @Test
    fun accountLedgerLeadsWithTheAccountMovementAndCaptionsMyShare() {
        val shared = movement(
            id = "shared",
            type = MovementType.EXPENSE,
            accountId = "joint",
            accountName = "Conjunt",
            isShared = true,
            userShareCents = 1_500,
        )
        val transfer = movement(
            id = "transfer",
            type = MovementType.TRANSFER,
            accountId = "joint",
            accountName = "Conjunt",
        )

        assertEquals(MovementAmountRole.MOVEMENT, shared.primaryAmountRole(inAccount = true))
        assertEquals(MovementAmountRole.YOUR_SHARE, shared.secondaryAmountRole(inAccount = true))
        assertEquals(MovementAmountRole.MOVEMENT, transfer.primaryAmountRole(inAccount = true))
        assertEquals(null, transfer.secondaryAmountRole(inAccount = true))
    }

    @Test
    fun anAllocatedIncomeLeadsWithMyPartLikeASharedExpense() {
        val income = movement(
            id = "income",
            type = MovementType.INCOME,
            accountId = "joint",
            accountName = "Conjunt",
            isShared = true,
            userShareCents = 1_200,
        )

        assertEquals(MovementAmountRole.YOUR_SHARE, income.primaryAmountRole())
        assertEquals(MovementAmountRole.TOTAL, income.secondaryAmountRole())
        assertEquals(MovementAmountRole.MOVEMENT, income.primaryAmountRole(inAccount = true))
        assertEquals(MovementAmountRole.YOUR_SHARE, income.secondaryAmountRole(inAccount = true))
    }

    @Test
    fun visibleMovementsApplyMainFiltersTogether() {
        val state = MovementsUiState(
            movements = listOf(
                movement(
                    id = "groceries",
                    type = MovementType.EXPENSE,
                    date = "2026-01-05",
                    accountId = "checking",
                    accountName = "Compte",
                    categoryId = "food",
                    categoryName = "Supermercat",
                    name = "Compra setmanal",
                ),
                movement(
                    id = "salary",
                    type = MovementType.INCOME,
                    date = "2026-01-02",
                    accountId = "checking",
                    accountName = "Compte",
                    categoryId = "salary",
                    categoryName = "Sou",
                    name = "Nomina",
                ),
                movement(
                    id = "old-groceries",
                    type = MovementType.EXPENSE,
                    date = "2025-12-30",
                    accountId = "checking",
                    accountName = "Compte",
                    categoryId = "food",
                    categoryName = "Supermercat",
                    name = "Compra antiga",
                ),
            ),
            filters = MovementFilters(
                query = "super",
                type = MovementType.EXPENSE,
                accountId = "checking",
                categoryId = "food",
                dateFrom = "2026-01-01",
                dateTo = "2026-01-31",
            ),
        )

        assertEquals(listOf("groceries"), state.visibleMovements.map { it.id })
    }

    @Test
    fun accountFilterIncludesTransferDestinationAccount() {
        val state = MovementsUiState(
            movements = listOf(
                movement(
                    id = "transfer",
                    type = MovementType.TRANSFER,
                    accountId = "checking",
                    accountName = "Compte",
                    destinationAccountId = "savings",
                    destinationAccountName = "Estalvi",
                ),
            ),
            filters = MovementFilters(accountId = "savings"),
        )

        assertEquals(listOf("transfer"), state.visibleMovements.map { it.id })
    }

    @Test
    fun actualSourceFilterExcludesFlowOnlyMovementTypes() {
        val state = MovementsUiState(
            movements = listOf(
                movement(
                    id = "expense",
                    type = MovementType.EXPENSE,
                    accountId = "checking",
                    accountName = "Compte",
                ),
                movement(
                    id = "transfer",
                    type = MovementType.TRANSFER,
                    accountId = "checking",
                    accountName = "Compte",
                ),
                movement(
                    id = "settlement",
                    type = MovementType.SETTLEMENT,
                    accountId = "checking",
                    accountName = "Compte",
                ),
                movement(
                    id = "refund",
                    type = MovementType.REFUND,
                    accountId = "checking",
                    accountName = "Compte",
                ),
            ),
            filters = MovementFilters(sourceMode = MovementSourceMode.ACTUAL),
        )

        assertEquals(listOf("expense", "refund"), state.visibleMovements.map { it.id })
    }

    @Test
    fun categoryNatureAndOneTimeFiltersApplyTogether() {
        val state = MovementsUiState(
            movements = listOf(
                movement(
                    id = "matched",
                    type = MovementType.EXPENSE,
                    accountId = "checking",
                    accountName = "Compte",
                    categoryNature = CategoryNature.VARIABLE,
                    isOneTime = true,
                ),
                movement(
                    id = "recurring-variable",
                    type = MovementType.EXPENSE,
                    accountId = "checking",
                    accountName = "Compte",
                    categoryNature = CategoryNature.VARIABLE,
                    isOneTime = false,
                ),
                movement(
                    id = "one-time-fixed",
                    type = MovementType.EXPENSE,
                    accountId = "checking",
                    accountName = "Compte",
                    categoryNature = CategoryNature.FIXED,
                    isOneTime = true,
                ),
            ),
            filters = MovementFilters(
                categoryNature = CategoryNature.VARIABLE,
                oneTimeMode = MovementOneTimeMode.ONLY,
            ),
        )

        assertEquals(listOf("matched"), state.visibleMovements.map { it.id })
    }

    @Test
    fun uncategorizedFilterMatchesOnlyMissingCategory() {
        val state = MovementsUiState(
            movements = listOf(
                movement(
                    id = "uncategorized",
                    type = MovementType.EXPENSE,
                    accountId = "checking",
                    accountName = "Compte",
                    categoryId = null,
                ),
                movement(
                    id = "categorized",
                    type = MovementType.EXPENSE,
                    accountId = "checking",
                    accountName = "Compte",
                    categoryId = "food",
                ),
            ),
            filters = MovementFilters(uncategorizedOnly = true),
        )

        assertEquals(listOf("uncategorized"), state.visibleMovements.map { it.id })
    }

    @Test
    fun expenseFilterIncludesExternalExpense() {
        val state = MovementsUiState(
            movements = listOf(
                movement(
                    id = "expense",
                    type = MovementType.EXPENSE,
                    accountId = "checking",
                    accountName = "Compte",
                ),
                movement(
                    id = "external",
                    type = MovementType.EXTERNAL_EXPENSE,
                    accountId = "",
                    accountName = "",
                ),
            ),
            filters = MovementFilters(type = MovementType.EXPENSE),
        )

        assertEquals(listOf("expense", "external"), state.visibleMovements.map { it.id })
    }

    private fun movement(
        id: String,
        type: MovementType,
        date: String = "2026-01-01",
        accountId: String?,
        accountName: String?,
        destinationAccountId: String? = null,
        destinationAccountName: String? = null,
        categoryId: String? = null,
        categoryName: String? = null,
        categoryNature: CategoryNature? = null,
        name: String? = null,
        isOneTime: Boolean = false,
        isShared: Boolean = false,
        userShareCents: Long = -1L,
    ): MovementSummary =
        MovementSummary(
            id = id,
            type = type,
            amountCents = 1_000,
            date = date,
            accountId = accountId,
            accountName = accountName,
            destinationAccountId = destinationAccountId,
            destinationAccountName = destinationAccountName,
            categoryId = categoryId,
            categoryName = categoryName,
            categoryNature = categoryNature,
            categoryIcon = null,
            categoryColor = null,
            name = name,
            payee = null,
            notes = null,
            isOneTime = isOneTime,
            isShared = isShared,
            userShareCents = userShareCents,
            isRecurring = false,
            paidByPersonName = null,
            payerId = null,
            settlementDirection = null,
            settlementPersonName = null,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            archivedAt = null,
        )
}
