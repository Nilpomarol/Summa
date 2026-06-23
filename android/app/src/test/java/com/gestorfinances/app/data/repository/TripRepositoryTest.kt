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

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            trips = TripRepository(database.tripsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
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
