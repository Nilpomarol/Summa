package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisWidgetsRepositoryTest {
    @Test
    fun widgetQueriesReturnPeriodScopedAggregates() {
        freshStore().use { store ->
            seedFixture(store)

            val largest = store.analysis.largestExpenses(fromDate = FROM, toDate = TO)
            assertEquals(
                listOf(
                    Triple("laptop", "Amazon", 5_000L),
                    Triple("groceries-2", "Mercadona", 3_000L),
                    Triple("groceries-1", "Mercadona", 2_000L),
                ),
                largest.map { Triple(it.sourceId, it.label, it.amountCents) },
            )

            val withoutOneTime = store.analysis.largestExpenses(
                fromDate = FROM,
                toDate = TO,
                oneTimeMode = AnalysisOneTimeMode.EXCLUDE,
            )
            assertEquals(listOf("groceries-2", "groceries-1"), withoutOneTime.map { it.sourceId })

            val merchants = store.analysis.topMerchants(fromDate = FROM, toDate = TO)
            assertEquals(
                listOf(
                    Triple("Mercadona", 5_000L, 2L),
                    Triple("Amazon", 5_000L, 1L),
                ),
                merchants.map { Triple(it.merchantLabel, it.totalCents, it.movementCount) },
            )

            val trends = store.analysis
                .categoryTrends(fromDate = FROM, toDate = TO, bucket = AnalysisBucket.MONTH)
                .associate { (it.categoryId to it.bucket) to it.expenseCents }
            assertEquals(5_000L, trends.getValue("electronics" to "2026-06"))
            assertEquals(5_000L, trends.getValue("groceries" to "2026-06"))

            val netWorth = store.analysis
                .netWorthOverTime(fromDate = FROM, toDate = TO, bucket = AnalysisBucket.MONTH)
            assertEquals(
                listOf("2026-06" to 250_000L),
                netWorth.map { it.bucket to it.netWorthCents },
            )
        }
    }

    private fun seedFixture(store: TestStore) {
        store.accounts.create(
            accountDraft(id = "checking", startingBalanceCents = 10_000),
            createdAt = NOW,
        )
        store.categories.create(
            categoryDraft(id = "salary", kind = CategoryKind.INCOME, nature = CategoryNature.FIXED),
            createdAt = NOW,
        )
        store.categories.create(categoryDraft(id = "groceries", displayOrder = 1), createdAt = NOW)
        store.categories.create(categoryDraft(id = "electronics", displayOrder = 2), createdAt = NOW)

        store.movements.create(
            movementDraft(
                id = "salary",
                type = MovementType.INCOME,
                amountCents = 250_000,
                date = "2026-06-01",
                categoryId = "salary",
                payee = "Empresa",
            ),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft(
                id = "groceries-1",
                type = MovementType.EXPENSE,
                amountCents = 2_000,
                date = "2026-06-05",
                categoryId = "groceries",
                payee = "Mercadona",
            ),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft(
                id = "groceries-2",
                type = MovementType.EXPENSE,
                amountCents = 3_000,
                date = "2026-06-08",
                categoryId = "groceries",
                payee = "Mercadona",
            ),
            createdAt = NOW,
        )
        store.movements.create(
            movementDraft(
                id = "laptop",
                type = MovementType.EXPENSE,
                amountCents = 5_000,
                date = "2026-06-10",
                categoryId = "electronics",
                payee = "Amazon",
                isOneTime = true,
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

    private fun accountDraft(
        id: String,
        startingBalanceCents: Long = 0,
        displayOrder: Long = 0,
    ): AccountDraft =
        AccountDraft(
            id = id,
            name = id,
            startingBalanceCents = startingBalanceCents,
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
        categoryId: String? = null,
        payee: String? = null,
        isOneTime: Boolean = false,
    ): MovementDraft =
        MovementDraft(
            id = id,
            type = type,
            amountCents = amountCents,
            date = date,
            accountId = "checking",
            destinationAccountId = null,
            categoryId = categoryId,
            name = id,
            payee = payee,
            notes = null,
            isOneTime = isOneTime,
        )

    private companion object {
        const val NOW = "2026-06-01T00:00:00Z"
        const val FROM = "2026-06-01"
        const val TO = "2026-07-01"
    }
}
