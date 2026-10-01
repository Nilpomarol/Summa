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
    fun tagCategoryJoinSurfacesCategoryFieldsAndEffectiveFallbacksApply() {
        freshStore().use { store ->
            store.categories.create(
                CategoryDraft(
                    id = "food",
                    name = "Menjar",
                    kind = CategoryKind.EXPENSE,
                    nature = CategoryNature.VARIABLE,
                    parentId = null,
                    icon = "restaurant",
                    color = "#F97316",
                    displayOrder = 0,
                ),
                createdAt = NOW,
            )

            // Tag with no icon/color of its own: falls back to the category's.
            store.tags.create(
                TagDraft(
                    id = "restaurants",
                    name = "Restaurants",
                    icon = null,
                    color = null,
                    tripId = null,
                    categoryId = "food",
                ),
                createdAt = NOW,
            )
            // Tag with its own icon/color: keeps them, ignoring the category's.
            store.tags.create(
                TagDraft(
                    id = "fine-dining",
                    name = "Sopars especials",
                    icon = "star",
                    color = "#DC2626",
                    tripId = null,
                    categoryId = "food",
                ),
                createdAt = NOW,
            )

            val restaurants = store.tags.getActive("restaurants")!!
            assertEquals("food", restaurants.categoryId)
            assertEquals("Menjar", restaurants.categoryName)
            assertEquals("restaurant", restaurants.categoryIcon)
            assertEquals("#F97316", restaurants.categoryColor)
            assertEquals("restaurant", restaurants.effectiveIcon())
            assertEquals("#F97316", restaurants.effectiveColor())

            val fineDining = store.tags.getActive("fine-dining")!!
            assertEquals("star", fineDining.effectiveIcon())
            assertEquals("#DC2626", fineDining.effectiveColor())

            // A tag with neither its own nor a category's icon/color falls back to null.
            store.tags.create(
                TagDraft(id = "bare", name = "Bare", icon = null, color = null, tripId = null),
                createdAt = NOW,
            )
            val bare = store.tags.getActive("bare")!!
            assertNull(bare.categoryId)
            assertNull(bare.effectiveIcon())
            assertNull(bare.effectiveColor())
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
            categories = CategoryRepository(database.categoriesQueries),
            tags = TagRepository(database.tagsQueries),
            trips = TripRepository(database.tripsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val categories: CategoryRepository,
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
