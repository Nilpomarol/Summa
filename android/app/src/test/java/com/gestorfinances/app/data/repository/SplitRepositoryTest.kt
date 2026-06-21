package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitRepositoryTest {
    @Test
    fun externalPaidByPersonSplitAffectsDebtAndActualExpenseButNotAccountFlow() {
        freshStore().use { store ->
            store.people.create(
                PersonDraft(
                    id = "laura",
                    name = "Laura",
                    avatar = null,
                    color = null,
                    notes = null,
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

            store.splits.createExternalPaidByPerson(
                ExternalSplitDraft(
                    id = "split-laura-paid",
                    payerPersonId = "laura",
                    totalAmountCents = 1_000,
                    userShareCents = 400,
                    date = "2026-01-01",
                    description = "Sopar",
                    categoryId = "food",
                ),
                createdAt = NOW,
            )

            val totals = store.analysis.periodTotals(
                fromDate = "2026-01-01",
                toDate = "2026-01-02",
            )
            val categories = store.analysis.actualByCategory(
                fromDate = "2026-01-01",
                toDate = "2026-01-02",
            ).associateBy { it.categoryId }

            assertTrue(store.movements.listActive().isEmpty())
            assertEquals(-400L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(400L, totals.actualExpenseCents)
            assertEquals(0L, totals.accountFlowCents)
            assertEquals(400L, categories.getValue("food").expenseCents)
        }
    }

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            analysis = AnalysisRepository(database.analysisQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries),
            people = PersonRepository(database.peopleQueries),
            splits = SplitRepository(database.splitsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val analysis: AnalysisRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val splits: SplitRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
