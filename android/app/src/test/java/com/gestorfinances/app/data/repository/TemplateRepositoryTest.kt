package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRepositoryTest {
    @Test
    fun createsAndReadsBackAFixedMonthlyExpenseTemplate() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.templates.create(monthlyRentDraft(), createdAt = NOW)

            val template = store.templates.listActive().single()
            assertEquals("rent", template.id)
            assertEquals(MovementType.EXPENSE, template.type)
            assertEquals(80_000L, template.amountCents)
            assertEquals("checking", template.accountId)
            assertEquals("Compte", template.accountName)
            assertEquals("food", template.categoryId)
            assertEquals(RecurrenceFrequency.MONTHLY, template.frequency)
            assertEquals(1L, template.dayOfMonth)
            assertEquals("2026-02-01", template.nextDueDate)
            assertEquals(3L, template.leadNotificationDays)
            assertEquals(TemplateStatus.ACTIVE, template.status)
            assertNull(template.splitConfig)
            assertEquals(false, template.amountIsVariable)
        }
    }

    @Test
    fun persistsTripAndTagAttributionOnCreateAndUpdate() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.trips.create(
                TripDraft("trip", "Viatge", TripType.TRIP, TripStatus.ACTIVE, null, null, null, null, null, "checking"),
                createdAt = NOW,
            )
            store.tags.create(TagDraft("tag", "Platja", null, null, "trip"), createdAt = NOW)

            store.templates.create(monthlyRentDraft().copy(tripId = "trip", tagId = "tag"), createdAt = NOW)
            assertEquals("trip", store.templates.getActive("rent")!!.tripId)
            assertEquals("tag", store.templates.getActive("rent")!!.tagId)

            store.templates.update(monthlyRentDraft().copy(tripId = "trip", tagId = null), updatedAt = LATER)
            assertEquals("trip", store.templates.getActive("rent")!!.tripId)
            assertNull(store.templates.getActive("rent")!!.tagId)
        }
    }

    @Test
    fun roundTripsSplitConfigJsonForSharedRecurring() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            val splitConfig = TemplateSplitConfig(
                entryMethod = "equal",
                payer = "user",
                lines = listOf(
                    TemplateSplitConfigLine(party = "user", owedAmountCents = 2_500),
                    TemplateSplitConfigLine(party = "laura", owedAmountCents = 2_500),
                ),
            )
            store.templates.create(
                monthlyRentDraft().copy(id = "shared-dinner", amountCents = 5_000, splitConfig = splitConfig),
                createdAt = NOW,
            )

            val stored = store.templates.getActive("shared-dinner")!!
            assertEquals(splitConfig, stored.splitConfig)
        }
    }

    @Test
    fun persistsCustomScheduleAndVariableAmountTemplates() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.templates.create(
                monthlyRentDraft().copy(
                    id = "custom",
                    amountCents = null,
                    amountIsVariable = true,
                    frequency = RecurrenceFrequency.CUSTOM,
                    intervalCount = 2,
                    customUnit = CustomRecurrenceUnit.WEEKS,
                    dayOfMonth = null,
                    amountFlexCents = 500,
                    dateFlexDays = 2,
                ),
                createdAt = NOW,
            )

            val stored = store.templates.getActive("custom")!!
            assertNull(stored.amountCents)
            assertTrue(stored.amountIsVariable)
            assertEquals(RecurrenceFrequency.CUSTOM, stored.frequency)
            assertEquals(2L, stored.intervalCount)
            assertEquals(CustomRecurrenceUnit.WEEKS, stored.customUnit)
            assertEquals(500L, stored.amountFlexCents)
            assertEquals(2L, stored.dateFlexDays)
        }
    }

    @Test
    fun updatesPausesAndSoftArchivesTemplates() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.templates.create(monthlyRentDraft(), createdAt = NOW)

            store.templates.update(
                monthlyRentDraft().copy(amountCents = 82_000, name = "Lloguer nou"),
                updatedAt = LATER,
            )
            val updated = store.templates.getActive("rent")!!
            assertEquals(82_000L, updated.amountCents)
            assertEquals("Lloguer nou", updated.name)
            assertEquals(TemplateStatus.ACTIVE, updated.status)

            store.templates.setStatus("rent", TemplateStatus.PAUSED, updatedAt = LATER)
            assertEquals(TemplateStatus.PAUSED, store.templates.getActive("rent")!!.status)

            store.templates.archive("rent", archivedAt = LATER)
            assertNull(store.templates.getActive("rent"))
            assertTrue(store.templates.listActive().isEmpty())
        }
    }

    @Test
    fun updateAppliesAStatusChangeInTheSameDraft() {
        // Regression: `updateTemplate`'s SET clause omitted `status`, so editing a template's
        // status through the add/edit form (or a "Actualitza" detection candidate) silently had
        // no effect -- only the dedicated pause/resume/end actions (`setStatus`) worked.
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.templates.create(monthlyRentDraft(), createdAt = NOW)

            store.templates.update(monthlyRentDraft().copy(status = TemplateStatus.ENDED), updatedAt = LATER)

            assertEquals(TemplateStatus.ENDED, store.templates.getActive("rent")!!.status)
        }
    }

    @Test
    fun rejectsSchemaInvalidDrafts() {
        freshStore().use { store ->
            seedAccountAndCategory(store)

            // Custom frequency without interval/unit.
            assertThrows(IllegalArgumentException::class.java) {
                store.templates.create(
                    monthlyRentDraft().copy(id = "bad1", frequency = RecurrenceFrequency.CUSTOM),
                    createdAt = NOW,
                )
            }
            // Fixed amount template with no amount.
            assertThrows(IllegalArgumentException::class.java) {
                store.templates.create(
                    monthlyRentDraft().copy(id = "bad2", amountCents = null, amountIsVariable = false),
                    createdAt = NOW,
                )
            }
            // Transfer without a destination account.
            assertThrows(IllegalArgumentException::class.java) {
                store.templates.create(
                    monthlyRentDraft().copy(id = "bad3", type = MovementType.TRANSFER, categoryId = null),
                    createdAt = NOW,
                )
            }
            assertTrue(store.templates.listActive().isEmpty())
        }
    }

    private fun monthlyRentDraft(): TemplateDraft =
        TemplateDraft(
            id = "rent",
            type = MovementType.EXPENSE,
            amountCents = 80_000,
            accountId = "checking",
            destAccountId = null,
            categoryId = "food",
            name = "Lloguer",
            payee = null,
            notes = null,
            splitConfig = null,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalCount = null,
            customUnit = null,
            dayOfMonth = 1,
            weekday = null,
            nextDueDate = "2026-02-01",
            amountIsVariable = false,
            amountFlexCents = null,
            dateFlexDays = null,
            leadNotificationDays = 3,
            status = TemplateStatus.ACTIVE,
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
                name = "Llar",
                kind = CategoryKind.EXPENSE,
                nature = CategoryNature.FIXED,
                parentId = null,
                icon = null,
                color = null,
                displayOrder = 0,
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
            trips = TripRepository(database.tripsQueries),
            tags = TagRepository(database.tagsQueries),
            templates = TemplateRepository(database.templatesQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val trips: TripRepository,
        val tags: TagRepository,
        val templates: TemplateRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val LATER = "2026-01-02T00:00:00Z"
    }
}
