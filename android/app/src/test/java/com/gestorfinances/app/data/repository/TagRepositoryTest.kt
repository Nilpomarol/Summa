package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagRepositoryTest {
    @Test
    fun tagRepositoryCreatesUpdatesAndSoftArchivesTags() {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.tags.create(
                TagDraft(
                    id = "food",
                    name = "Menjar",
                    icon = "fork",
                    color = "#16A34A",
                    tripId = null,
                ),
                createdAt = NOW,
            )

            val created = store.tags.getActive("food")!!
            assertEquals("Menjar", created.name)
            assertNull(created.tripId)

            store.tags.update(
                TagDraft(
                    id = "food",
                    name = "Restaurants",
                    icon = null,
                    color = null,
                    tripId = "mallorca",
                ),
                updatedAt = LATER,
            )

            val updated = store.tags.getActive("food")!!
            assertEquals("Restaurants", updated.name)
            assertEquals("mallorca", updated.tripId)
            assertEquals("mallorca", updated.tripName)

            store.tags.archive("food", archivedAt = LATER)

            assertNull(store.tags.getActive("food"))
            assertEquals(emptyList<TagSummary>(), store.tags.listActive())
        }
    }

    @Test
    fun activeTagsExcludesLocalTagsForArchivedTrips() {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.tags.create(
                TagDraft(
                    id = "global",
                    name = "Global",
                    icon = null,
                    color = null,
                    tripId = null,
                ),
                createdAt = NOW,
            )
            store.tags.create(
                TagDraft(
                    id = "local",
                    name = "Local",
                    icon = null,
                    color = null,
                    tripId = "mallorca",
                ),
                createdAt = NOW,
            )

            store.trips.archive("mallorca", archivedAt = LATER)

            assertNull(store.tags.getActive("local"))
            assertEquals(listOf("global"), store.tags.listActive().map { it.id })
        }
    }

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            tags = TagRepository(database.tagsQueries),
            trips = TripRepository(database.tripsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val tags: TagRepository,
        val trips: TripRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private fun tripDraft(id: String): TripDraft =
        TripDraft(
            id = id,
            name = id,
            type = TripType.TRIP,
            status = TripStatus.ACTIVE,
            startDate = null,
            endDate = null,
            icon = null,
            color = null,
            notes = null,
            defaultAccountId = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val LATER = "2026-01-02T00:00:00Z"
    }
}
