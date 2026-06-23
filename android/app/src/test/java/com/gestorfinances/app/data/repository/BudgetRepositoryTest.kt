package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BudgetRepositoryTest {
    @Test
    fun evaluationReflectsActualSpendAndStatusBands() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft(
                    id = "b1",
                    categoryId = "food",
                    limitAmountCents = 10_000,
                    alertThresholdPercent = 80,
                    startDate = null,
                ),
                createdAt = NOW,
            )

            // No spend yet → OK, full limit remaining.
            var evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(0L, evaluation.actualCents)
            assertEquals(10_000L, evaluation.remainingCents)
            assertEquals(BudgetStatus.OK, evaluation.status)

            // 90% of the limit → WARN (>= 80% threshold).
            store.movements.create(expense("e1", 9_000), createdAt = NOW)
            evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(9_000L, evaluation.actualCents)
            assertEquals(BudgetStatus.WARN, evaluation.status)

            // Over the limit → OVER, negative remaining.
            store.movements.create(expense("e2", 2_000), createdAt = NOW)
            evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(11_000L, evaluation.actualCents)
            assertEquals(-1_000L, evaluation.remainingCents)
            assertEquals(BudgetStatus.OVER, evaluation.status)
        }
    }

    @Test
    fun refundNetsDownBudgetActual() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null, null),
                createdAt = NOW,
            )
            store.movements.create(expense("e1", 9_000), createdAt = NOW)
            store.movements.createRefund(
                RefundDraft(
                    id = "r1",
                    refundsExpenseId = "e1",
                    amountCents = 3_000,
                    accountId = "checking",
                    categoryId = "food",
                    date = "2026-03-10",
                    name = null,
                    payee = null,
                    notes = null,
                    actualRefundCents = null,
                ),
                createdAt = NOW,
            )

            assertEquals(6_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun partialActualRefundNetsOnlyUserShareFromBudgetActual() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null, null),
                createdAt = NOW,
            )
            store.movements.create(expense("e1", 9_000), createdAt = NOW)
            store.movements.createRefund(
                RefundDraft(
                    id = "r1",
                    refundsExpenseId = "e1",
                    amountCents = 5_000,
                    accountId = "checking",
                    categoryId = "food",
                    date = "2026-03-10",
                    name = null,
                    payee = null,
                    notes = null,
                    actualRefundCents = 2_000,
                ),
                createdAt = NOW,
            )

            assertEquals(7_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun defaultThresholdWarnsAtEightyPercent() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null, null),
                createdAt = NOW,
            )

            store.movements.create(expense("e1", 7_999), createdAt = NOW)
            assertEquals(BudgetStatus.OK, store.budgets.evaluateAll(FROM, TO).single().status)

            store.movements.create(expense("e2", 1), createdAt = NOW)
            assertEquals(BudgetStatus.WARN, store.budgets.evaluateAll(FROM, TO).single().status)
        }
    }

    @Test
    fun evaluationHonorsBudgetStartDateInsidePeriod() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null, "2026-03-10"),
                createdAt = NOW,
            )
            store.movements.create(expense("before", 4_000).copy(date = "2026-03-05"), createdAt = NOW)
            store.movements.create(expense("after", 3_000).copy(date = "2026-03-10"), createdAt = NOW)

            assertEquals(3_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun tripBudgetEvaluatesActualSpendForTripOnly() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedTrip(store, "mallorca")
            store.budgets.create(
                BudgetDraft(
                    id = "trip-budget",
                    categoryId = null,
                    limitAmountCents = 10_000,
                    alertThresholdPercent = null,
                    startDate = "2026-03-01",
                    tripId = "mallorca",
                    scope = BudgetScope.TRIP,
                    period = BudgetPeriod.ONE_OFF,
                ),
                createdAt = NOW,
            )

            store.movements.create(expense("trip-expense", 7_000).copy(tripId = "mallorca"), createdAt = NOW)
            store.movements.create(expense("regular-expense", 5_000), createdAt = NOW)

            val evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(BudgetScope.TRIP, evaluation.budget.scope)
            assertEquals(7_000L, evaluation.actualCents)
            assertEquals(3_000L, evaluation.remainingCents)
        }
    }

    @Test
    fun rejectsInvalidBudgets() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            assertThrows(IllegalArgumentException::class.java) {
                store.budgets.create(BudgetDraft("b1", "food", 0, null, null), createdAt = NOW)
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.budgets.create(BudgetDraft("b2", "food", 10_000, 150, null), createdAt = NOW)
            }
        }
    }

    private fun expense(id: String, amountCents: Long): MovementDraft =
        MovementDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = amountCents,
            date = "2026-03-05",
            accountId = "checking",
            destinationAccountId = null,
            categoryId = "food",
            name = id,
            payee = null,
            notes = null,
            isOneTime = false,
        )

    private fun seedAccountAndCategory(store: TestStore) {
        store.accounts.create(
            AccountDraft(
                id = "checking",
                name = "Compte",
                startingBalanceCents = 0,
                type = AccountType.BANK,
                icon = null,
                color = null,
                isDefault = true,
                displayOrder = 0,
                lowBalanceThresholdCents = null,
            ),
            createdAt = NOW,
        )
        store.categories.create(
            CategoryDraft(
                id = "food",
                name = "Menjar",
                kind = CategoryKind.EXPENSE,
                nature = CategoryNature.VARIABLE,
                parentId = null,
                icon = null,
                color = null,
                displayOrder = 0,
            ),
            createdAt = NOW,
        )
    }

    private fun seedTrip(store: TestStore, id: String) {
        store.trips.create(
            TripDraft(
                id = id,
                name = id,
                type = TripType.TRIP,
                status = TripStatus.ACTIVE,
                startDate = "2026-03-01",
                endDate = "2026-03-31",
                icon = null,
                color = null,
                notes = null,
                defaultAccountId = null,
            ),
            createdAt = NOW,
        )
    }

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            trips = TripRepository(database.tripsQueries),
            budgets = BudgetRepository(database.budgetsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val trips: TripRepository,
        val budgets: BudgetRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val FROM = "2026-03-01"
        const val TO = "2026-03-31"
    }
}
