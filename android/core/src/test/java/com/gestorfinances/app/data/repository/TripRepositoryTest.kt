package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRepositoryTest {
    @Test
    fun tripRepositoryCreatesUpdatesAndSoftArchivesTrips() {
        freshStore().use { store ->
            store.accounts.create(accountDraft("cash"), createdAt = NOW)
            store.trips.create(
                TripDraft(
                    id = "mallorca",
                    name = "Mallorca",
                    type = TripType.TRIP,
                    status = TripStatus.PLANNED,
                    startDate = "2026-08-01",
                    endDate = "2026-08-10",
                    icon = "flight",
                    color = "#2563EB",
                    notes = "Estiu",
                    defaultAccountId = "cash",
                ),
                createdAt = NOW,
            )

            val created = store.trips.getActive("mallorca")!!
            assertEquals("Mallorca", created.name)
            assertEquals("cash", created.defaultAccountId)
            assertEquals("cash", created.defaultAccountName)

            store.trips.update(
                TripDraft(
                    id = "mallorca",
                    name = "Mallorca 2026",
                    type = TripType.CELEBRATION,
                    status = TripStatus.ACTIVE,
                    startDate = "2026-08-02",
                    endDate = null,
                    icon = null,
                    color = null,
                    notes = "Aniversari",
                    defaultAccountId = null,
                ),
                updatedAt = LATER,
            )

            val updated = store.trips.getActive("mallorca")!!
            assertEquals("Mallorca 2026", updated.name)
            assertEquals(TripType.CELEBRATION, updated.type)
            assertEquals(TripStatus.ACTIVE, updated.status)
            assertNull(updated.defaultAccountId)

            store.trips.archive("mallorca", archivedAt = LATER)

            assertNull(store.trips.getActive("mallorca"))
            assertEquals(emptyList<TripSummary>(), store.trips.listActive())
        }
    }

    @Test
    fun activeTodayReturnsTheTripCoveringTodayAndNullOtherwise() {
        freshStore().use { store ->
            store.trips.create(
                TripDraft(
                    id = "mallorca",
                    name = "Mallorca",
                    type = TripType.TRIP,
                    status = TripStatus.ACTIVE,
                    startDate = "2026-08-01",
                    endDate = "2026-08-10",
                    icon = null,
                    color = null,
                    notes = null,
                    defaultAccountId = null,
                ),
                createdAt = NOW,
            )
            store.trips.create(
                TripDraft(
                    id = "andorra",
                    name = "Andorra",
                    type = TripType.TRIP,
                    status = TripStatus.PLANNED,
                    startDate = "2026-12-01",
                    endDate = "2026-12-05",
                    icon = null,
                    color = null,
                    notes = null,
                    defaultAccountId = null,
                ),
                createdAt = NOW,
            )

            assertEquals("mallorca", store.trips.activeToday("2026-08-05")!!.id)
            assertEquals("mallorca", store.trips.activeToday("2026-08-01")!!.id)
            assertEquals("mallorca", store.trips.activeToday("2026-08-10")!!.id)
            assertNull(store.trips.activeToday("2026-08-11"))
            // "andorra" is planned (not active), so it never matches regardless of date.
            assertNull(store.trips.activeToday("2026-12-02"))
        }
    }

    @Test
    fun activeTripsAndTripByIdExposeTotalActualCentsFromVActualExpense() {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(
                TripDraft(
                    id = "mallorca",
                    name = "Mallorca",
                    type = TripType.TRIP,
                    status = TripStatus.ACTIVE,
                    startDate = "2026-08-01",
                    endDate = "2026-08-10",
                    icon = null,
                    color = null,
                    notes = null,
                    defaultAccountId = null,
                ),
                createdAt = NOW,
            )

            // No spend yet: total defaults to 0, not null.
            assertEquals(0L, store.trips.getActive("mallorca")!!.totalActualCents)
            assertEquals(0L, store.trips.listActive().single().totalActualCents)

            store.movements.create(
                MovementDraft(
                    id = "dinner",
                    type = MovementType.EXPENSE,
                    amountCents = 1_500,
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
            store.movements.create(
                MovementDraft(
                    id = "hotel",
                    type = MovementType.EXPENSE,
                    amountCents = 2_500,
                    date = "2026-08-03",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = null,
                    tripId = "mallorca",
                    tagId = null,
                    name = "Hotel",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                ),
                createdAt = NOW,
            )

            assertEquals(4_000L, store.trips.getActive("mallorca")!!.totalActualCents)
            assertEquals(4_000L, store.trips.listActive().single().totalActualCents)
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
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            trips = TripRepository(database.tripsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val movements: MovementRepository,
        val trips: TripRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private fun accountDraft(id: String): AccountDraft =
        AccountDraft(
            id = id,
            name = id,
            startingBalanceCents = 0,
            type = AccountType.CASH,
            icon = null,
            color = null,
            isDefault = true,
            displayOrder = 0,
            lowBalanceThresholdCents = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val LATER = "2026-01-02T00:00:00Z"
    }
}
