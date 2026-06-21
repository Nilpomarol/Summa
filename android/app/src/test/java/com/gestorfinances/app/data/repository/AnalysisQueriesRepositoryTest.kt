package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Correctness of the core analysis queries over a fixed multi-month/multi-year dataset:
 * month and year bucketing, savings-rate basis points, and the two-leg netting of a transfer
 * in account-flow-over-time. Single-month daily aggregates and the P2-8 widgets are covered by
 * the other analysis tests.
 */
class AnalysisQueriesRepositoryTest {
    @Test
    fun incomeVsExpenseBucketsByMonthWithSavingsRate() {
        freshStore().use { store ->
            seedTwoMonths(store)

            val byMonth = store.analysis.incomeVsExpense(
                fromDate = "2026-01-01",
                toDate = "2027-01-01",
                bucket = AnalysisBucket.MONTH,
            ).associateBy { it.bucket }

            assertEquals(listOf("2026-05", "2026-06"), byMonth.keys.toList())

            val may = byMonth.getValue("2026-05")
            assertEquals(100_000L, may.incomeCents)
            assertEquals(25_000L, may.expenseCents)
            assertEquals(75_000L, may.netCents)
            assertEquals(7_500L, may.savingsRateBasisPoints) // 75000 / 100000

            val june = byMonth.getValue("2026-06")
            assertEquals(200_000L, june.incomeCents)
            assertEquals(50_000L, june.expenseCents)
            assertEquals(150_000L, june.netCents)
            assertEquals(7_500L, june.savingsRateBasisPoints) // 150000 / 200000
        }
    }

    @Test
    fun incomeVsExpenseBucketsByYear() {
        freshStore().use { store ->
            seedTwoMonths(store)

            val byYear = store.analysis.incomeVsExpense(
                fromDate = "2025-01-01",
                toDate = "2027-01-01",
                bucket = AnalysisBucket.YEAR,
            ).associateBy { it.bucket }

            assertEquals(listOf("2025", "2026"), byYear.keys.toList())
            assertEquals(50_000L, byYear.getValue("2025").incomeCents)
            assertEquals(300_000L, byYear.getValue("2026").incomeCents) // 100000 + 200000
            assertEquals(75_000L, byYear.getValue("2026").expenseCents) // 25000 + 50000
        }
    }

    @Test
    fun accountFlowOverTimeSplitsTransferLegsAndNetsTheBucket() {
        freshStore().use { store ->
            seedTwoMonths(store)

            val transferDay = store.analysis.accountFlowOverTime(
                fromDate = "2026-06-20",
                toDate = "2026-06-21",
                bucket = AnalysisBucket.DAY,
            )

            val byAccount = transferDay.associate { it.accountId to it.deltaCents }
            assertEquals(-10_000L, byAccount["checking"])
            assertEquals(10_000L, byAccount["savings"])
            // Both legs share the same bucket, which nets to zero.
            assertEquals(setOf(0L), transferDay.map { it.bucketDeltaCents }.toSet())
        }
    }

    private fun seedTwoMonths(store: TestStore) {
        store.accounts.create(accountDraft(id = "checking"), createdAt = NOW)
        store.accounts.create(accountDraft(id = "savings", displayOrder = 1), createdAt = NOW)
        store.categories.create(
            categoryDraft(id = "salary", kind = CategoryKind.INCOME, nature = CategoryNature.FIXED),
            createdAt = NOW,
        )
        store.categories.create(categoryDraft(id = "groceries", displayOrder = 1), createdAt = NOW)

        store.movements.create(
            movementDraft("salary-2025", MovementType.INCOME, 50_000, "2025-11-10", categoryId = "salary"),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft("salary-may", MovementType.INCOME, 100_000, "2026-05-10", categoryId = "salary"),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft("groceries-may", MovementType.EXPENSE, 25_000, "2026-05-15", categoryId = "groceries"),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft("salary-jun", MovementType.INCOME, 200_000, "2026-06-10", categoryId = "salary"),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft("groceries-jun", MovementType.EXPENSE, 50_000, "2026-06-15", categoryId = "groceries"),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft(
                id = "to-savings",
                type = MovementType.TRANSFER,
                amountCents = 10_000,
                date = "2026-06-20",
                destinationAccountId = "savings",
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
            movements = MovementRepository(database.movementsQueries),
            analysis = AnalysisRepository(database.analysisQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val analysis: AnalysisRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private fun accountDraft(id: String, displayOrder: Long = 0): AccountDraft =
        AccountDraft(
            id = id,
            name = id,
            startingBalanceCents = 0,
            type = AccountType.BANK,
            icon = null,
            color = null,
            isDefault = false,
            displayOrder = displayOrder,
            lowBalanceThresholdCents = null,
        )

    private fun categoryDraft(
        id: String,
        kind: CategoryKind = CategoryKind.EXPENSE,
        nature: CategoryNature = CategoryNature.VARIABLE,
        displayOrder: Long = 0,
    ): CategoryDraft =
        CategoryDraft(
            id = id,
            name = id,
            kind = kind,
            nature = nature,
            parentId = null,
            icon = null,
            color = null,
            displayOrder = displayOrder,
        )

    private fun movementDraft(
        id: String,
        type: MovementType,
        amountCents: Long,
        date: String,
        destinationAccountId: String? = null,
        categoryId: String? = null,
    ): MovementDraft =
        MovementDraft(
            id = id,
            type = type,
            amountCents = amountCents,
            date = date,
            accountId = "checking",
            destinationAccountId = destinationAccountId,
            categoryId = categoryId,
            name = id,
            payee = null,
            notes = null,
            isOneTime = false,
        )

    private companion object {
        const val NOW = "2026-05-01T00:00:00Z"
    }
}
