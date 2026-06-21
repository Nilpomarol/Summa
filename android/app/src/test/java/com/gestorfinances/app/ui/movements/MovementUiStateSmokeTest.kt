package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementUiStateSmokeTest {
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

    private fun movement(
        id: String,
        type: MovementType,
        date: String = "2026-01-01",
        accountId: String,
        accountName: String,
        destinationAccountId: String? = null,
        destinationAccountName: String? = null,
        categoryId: String? = null,
        categoryName: String? = null,
        categoryNature: CategoryNature? = null,
        name: String? = null,
        isOneTime: Boolean = false,
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
            name = name,
            payee = null,
            notes = null,
            isOneTime = isOneTime,
            isShared = false,
            paidByPersonName = null,
            settlementDirection = null,
            settlementPersonName = null,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            archivedAt = null,
        )
}
