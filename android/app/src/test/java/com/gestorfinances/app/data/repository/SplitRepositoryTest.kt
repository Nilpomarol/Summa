package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

            // An external (paid-by-someone-else) split surfaces in the ledger as one EXTERNAL_EXPENSE
            // row: the user's actual share, attributed to the payer, with no owning account.
            val ledger = store.movements.listActive()
            assertEquals(1, ledger.size)
            val external = ledger.single()
            assertEquals("split-laura-paid", external.id)
            assertEquals(MovementType.EXTERNAL_EXPENSE, external.type)
            assertEquals("laura", external.payerId)
            assertEquals("Laura", external.paidByPersonName)
            assertEquals(400L, external.userShareCents)
            assertNull(external.accountId)

            assertEquals(-400L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(400L, totals.actualExpenseCents)
            assertEquals(0L, totals.accountFlowCents)
            assertEquals(400L, categories.getValue("food").expenseCents)
        }
    }

    @Test
    fun `replaceExternalSplit rolls back when insert fails inside transaction`() {
        freshStore().use { store ->
            store.people.create(
                PersonDraft(id = "marc", name = "Marc", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.splits.createExternalPaidByPerson(
                ExternalSplitDraft(
                    id = "split-1",
                    payerPersonId = "marc",
                    totalAmountCents = 500,
                    userShareCents = 500,
                    date = "2026-01-01",
                    description = "Cafè",
                    categoryId = null,
                ),
                createdAt = NOW,
            )
            assertEquals(1, store.movements.listActive().size)

            // Draft with non-existent payer → FK violation fires on insertExternalSplit,
            // AFTER archiveSplitLines + archiveMovementSplit have already run inside TX.
            val thrown = runCatching {
                store.splits.replaceExternalSplit(
                    id = "split-1",
                    draft = ExternalSplitDraft(
                        id = "split-2",
                        payerPersonId = "ghost-does-not-exist",
                        totalAmountCents = 600,
                        userShareCents = 600,
                        date = "2026-01-01",
                        description = "Cafè",
                        categoryId = null,
                    ),
                    now = NOW2,
                )
            }
            assertTrue("expected FK violation to surface", thrown.isFailure)

            // Atomicity: original split must still be active after rollback.
            val after = store.movements.listActive()
            assertEquals(1, after.size)
            assertEquals("split-1", after.single().id)
        }
    }

    @Test
    fun `createExternalPaidByPerson carries tripId and tagId`() {
        freshStore().use { store ->
            store.people.create(
                PersonDraft(id = "anna", name = "Anna", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.trips.create(
                TripDraft(
                    id = "trip-bcn",
                    name = "Barcelona",
                    type = TripType.TRIP,
                    status = TripStatus.ACTIVE,
                    startDate = null,
                    endDate = null,
                    icon = null,
                    color = null,
                    notes = null,
                    defaultAccountId = null,
                ),
                createdAt = NOW,
            )
            store.tags.create(
                TagDraft(id = "tag-1", name = "Transport", icon = null, color = null, tripId = "trip-bcn"),
                createdAt = NOW,
            )

            store.splits.createExternalPaidByPerson(
                ExternalSplitDraft(
                    id = "split-1",
                    payerPersonId = "anna",
                    totalAmountCents = 1200,
                    userShareCents = 1200,
                    date = "2026-06-01",
                    description = "Taxi aeroport",
                    categoryId = null,
                    tripId = "trip-bcn",
                    tagId = "tag-1",
                ),
                createdAt = NOW,
            )

            val external = store.movements.listActive().single()
            assertEquals("trip-bcn", external.tripId)
            assertEquals("tag-1", external.tagId)
            assertEquals(-1200L, store.people.getActive("anna")!!.balanceCents)
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
            trips = TripRepository(database.tripsQueries),
            tags = TagRepository(database.tagsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val analysis: AnalysisRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val splits: SplitRepository,
        val trips: TripRepository,
        val tags: TagRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val NOW2 = "2026-01-02T00:00:00Z"
    }
}
