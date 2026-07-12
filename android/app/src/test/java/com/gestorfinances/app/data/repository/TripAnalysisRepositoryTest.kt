package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class TripAnalysisRepositoryTest {
    @Test
    fun tripAnalysisUsesCanonicalActualAndAccountFlowViews() {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking", displayOrder = 0), createdAt = NOW)
            store.accounts.create(accountDraft("cash", displayOrder = 1), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
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
            store.tags.create(TagDraft("restaurants", "Restaurants", null, null, "mallorca"), createdAt = NOW)

            store.movements.create(
                MovementDraft(
                    id = "dinner",
                    type = MovementType.EXPENSE,
                    amountCents = 1_000,
                    date = "2026-08-01",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = "food",
                    tripId = "mallorca",
                    tagId = "restaurants",
                    name = "Sopar",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                ),
                createdAt = NOW,
            )
            store.movements.create(
                MovementDraft(
                    id = "cash-transfer",
                    type = MovementType.TRANSFER,
                    amountCents = 500,
                    date = "2026-08-02",
                    accountId = "checking",
                    destinationAccountId = "cash",
                    categoryId = null,
                    tripId = "mallorca",
                    tagId = null,
                    name = "Efectiu",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                ),
                createdAt = NOW,
            )

            val summary = store.tripAnalysis.summary("mallorca")
            val daily = store.tripAnalysis.actualByDay("mallorca")
            val categories = store.tripAnalysis.actualByCategory("mallorca")
            val tags = store.tripAnalysis.actualByTag("mallorca")

            assertEquals(1_000L, summary.actualCents)
            assertEquals(1_500L, summary.accountOutflowCents)
            assertEquals(listOf("2026-08-01" to 1_000L), daily.map { it.date to it.actualCents })
            assertEquals(listOf("food" to 1_000L), categories.map { it.categoryId to it.actualCents })
            assertEquals(listOf("restaurants" to 1_000L), tags.map { it.tagId to it.actualCents })
        }
    }

    @Test
    fun externalSplitWithItsOwnTagSurfacesInActualByTag() {
        // Regression test for the bug fixed alongside Slice A: tripActualByTag used to re-join
        // movements on e.source_id, which silently dropped the tag for external splits because
        // their source_id is a splits.id, not a movements.id. v_actual_expense now exposes
        // tag_id directly for external splits, so this must surface under its own tag bucket
        // rather than falling into the untagged (tag_id IS NULL) group.
        freshStore().use { store ->
            store.people.create(
                PersonDraft(id = "anna", name = "Anna", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.tags.create(TagDraft("transport", "Transport", null, null, "mallorca"), createdAt = NOW)

            store.splits.createExternalPaidByPerson(
                ExternalSplitDraft(
                    id = "split-taxi",
                    payerPersonId = "anna",
                    totalAmountCents = 1_200,
                    userShareCents = 1_200,
                    date = "2026-08-01",
                    description = "Taxi aeroport",
                    categoryId = null,
                    tripId = "mallorca",
                    tagId = "transport",
                ),
                createdAt = NOW,
            )

            val tags = store.tripAnalysis.actualByTag("mallorca")

            assertEquals(listOf("transport" to 1_200L), tags.map { it.tagId to it.actualCents })
        }
    }

    @Test
    fun excludeOneTimeFiltersOutFixedCostsFromTripActualByDay() {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking", displayOrder = 0), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)

            store.movements.create(
                MovementDraft(
                    id = "flight",
                    type = MovementType.EXPENSE,
                    amountCents = 20_000,
                    date = "2026-08-01",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = null,
                    tripId = "mallorca",
                    tagId = null,
                    name = "Vol",
                    payee = null,
                    notes = null,
                    isOneTime = true,
                ),
                createdAt = NOW,
            )
            store.movements.create(
                MovementDraft(
                    id = "dinner",
                    type = MovementType.EXPENSE,
                    amountCents = 3_000,
                    date = "2026-08-02",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = null,
                    tripId = "mallorca",
                    tagId = null,
                    name = "Sopar",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                ),
                createdAt = NOW,
            )

            val includingOneTime = store.tripAnalysis.actualByDay("mallorca", excludeOneTime = false)
            val excludingOneTime = store.tripAnalysis.actualByDay("mallorca", excludeOneTime = true)

            assertEquals(
                listOf("2026-08-01" to 20_000L, "2026-08-02" to 3_000L),
                includingOneTime.map { it.date to it.actualCents },
            )
            assertEquals(
                listOf("2026-08-02" to 3_000L),
                excludingOneTime.map { it.date to it.actualCents },
            )
        }
    }

    @Test
    fun actualByDayByCategoryGroupsPerDayAndThreadsExcludeOneTime() {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking", displayOrder = 0), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
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

            store.movements.create(
                MovementDraft(
                    id = "dinner-day1",
                    type = MovementType.EXPENSE,
                    amountCents = 1_000,
                    date = "2026-08-01",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = "food",
                    tripId = "mallorca",
                    tagId = null,
                    name = "Sopar",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                ),
                createdAt = NOW,
            )
            store.movements.create(
                MovementDraft(
                    id = "taxi-day1",
                    type = MovementType.EXPENSE,
                    amountCents = 500,
                    date = "2026-08-01",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = null,
                    tripId = "mallorca",
                    tagId = null,
                    name = "Taxi",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                ),
                createdAt = NOW,
            )
            store.movements.create(
                MovementDraft(
                    id = "dinner-day2",
                    type = MovementType.EXPENSE,
                    amountCents = 2_000,
                    date = "2026-08-02",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = "food",
                    tripId = "mallorca",
                    tagId = null,
                    name = "Sopar estrella",
                    payee = null,
                    notes = null,
                    isOneTime = true,
                ),
                createdAt = NOW,
            )

            val all = store.tripAnalysis.actualByDayByCategory("mallorca")
            val excludingOneTime = store.tripAnalysis.actualByDayByCategory("mallorca", excludeOneTime = true)

            // Days ascending; within a day, largest absolute amount first; null = "no category".
            assertEquals(
                listOf(
                    Triple("2026-08-01", "food", 1_000L),
                    Triple("2026-08-01", null, 500L),
                    Triple("2026-08-02", "food", 2_000L),
                ),
                all.map { Triple(it.date, it.categoryId, it.actualCents) },
            )
            assertEquals(
                listOf(
                    Triple("2026-08-01", "food", 1_000L),
                    Triple("2026-08-01", null, 500L),
                ),
                excludingOneTime.map { Triple(it.date, it.categoryId, it.actualCents) },
            )
        }
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
            people = PersonRepository(database.peopleQueries),
            splits = SplitRepository(database.splitsQueries),
            tags = TagRepository(database.tagsQueries),
            trips = TripRepository(database.tripsQueries),
            tripAnalysis = TripAnalysisRepository(database.tripAnalysisQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val splits: SplitRepository,
        val tags: TagRepository,
        val trips: TripRepository,
        val tripAnalysis: TripAnalysisRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private fun accountDraft(id: String, displayOrder: Long): AccountDraft =
        AccountDraft(
            id = id,
            name = id,
            startingBalanceCents = 0,
            type = AccountType.BANK,
            icon = null,
            color = null,
            isDefault = displayOrder == 0L,
            displayOrder = displayOrder,
            lowBalanceThresholdCents = null,
        )

    private fun tripDraft(id: String): TripDraft =
        TripDraft(
            id = id,
            name = id,
            type = TripType.TRIP,
            status = TripStatus.ACTIVE,
            startDate = "2026-08-01",
            endDate = "2026-08-03",
            icon = null,
            color = null,
            notes = null,
            defaultAccountId = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
