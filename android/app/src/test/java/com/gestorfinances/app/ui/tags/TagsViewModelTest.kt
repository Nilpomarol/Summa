package com.gestorfinances.app.ui.tags

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.TripAnalysisRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.TagDraft
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TripDraft
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun creatingATagWithoutANameShowsAValidationError() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "  "))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.tag_validation_name_required,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(TagFormField.NAME, viewModel.state.value.form!!.errorField)
            assertTrue(store.tags.listActive().isEmpty())
        }
    }

    @Test
    fun tripIdMustBeAnActiveTrip() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Restaurants", tripId = "does-not-exist"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.tag_validation_trip_required,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(TagFormField.TRIP, viewModel.state.value.form!!.errorField)
        }
    }

    // Mirrors the schema CHECK (trip_id IS NULL OR trip_type IS NULL): a tag can be global,
    // event-type-scoped, or trip-specific, but never both type-scoped and trip-specific at once.
    @Test
    fun aTagCannotBeBothTripLocalAndEventTypeScoped() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    name = "Restaurants",
                    tripId = "mallorca",
                    tripType = TripType.TRIP,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.tag_validation_scope_exclusive,
                viewModel.state.value.form!!.errorRes,
            )
            assertTrue(store.tags.listActive().isEmpty())
        }
    }

    @Test
    fun anEventTypeScopedTagWithNoTripIdSavesSuccessfully() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Regals", tripType = TripType.CELEBRATION),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val tag = viewModel.state.value.tags.single()
            assertEquals(TripType.CELEBRATION, tag.tripType)
            assertNull(tag.tripId)
        }
    }

    @Test
    fun aTripSpecificTagSavesWithTheChosenTripAndOptionalCategory() = runTest(dispatcher) {
        freshStore().use { store ->
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
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    name = "Restaurants",
                    tripId = "mallorca",
                    categoryId = "food",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val tag = viewModel.state.value.tags.single()
            assertEquals("mallorca", tag.tripId)
            assertEquals("food", tag.categoryId)
            assertNull(tag.tripType)
        }
    }

    @Test
    fun onScreenShownWithAContextTripIdPrefillsTheAddFormWithThatTrip() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = "mallorca")
            advanceUntilIdle()

            viewModel.onAddClicked()

            assertEquals("mallorca", viewModel.state.value.form!!.tripId)
        }
    }

    @Test
    fun visibleTagsWithAContextTripIdShowsGlobalAndThatTripsLocalTagsOnly() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.trips.create(tripDraft("andorra"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = "mallorca")
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "Global"))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Mallorca local", tripId = "mallorca"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Andorra local", tripId = "andorra"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val visibleNames = viewModel.state.value.visibleTags.map { it.name }.toSet()
            assertEquals(setOf("Global", "Mallorca local"), visibleNames)
        }
    }

    @Test
    fun archivingATagConfirmsBeforeRemovingItFromTheActiveList() = runTest(dispatcher) {
        freshStore().use { store ->
            store.tags.create(tagDraft("food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            val tag = viewModel.state.value.tags.single()
            viewModel.onArchiveClicked(tag)
            assertEquals(tag, viewModel.state.value.archiveCandidate)

            viewModel.onArchiveConfirmed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.archiveCandidate)
            assertTrue(viewModel.state.value.tags.isEmpty())
            assertNull(store.tags.getActive("food"))
        }
    }

    @Test
    fun dismissingTheArchiveConfirmationLeavesTheTagUntouched() = runTest(dispatcher) {
        freshStore().use { store ->
            store.tags.create(tagDraft("food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            val tag = viewModel.state.value.tags.single()
            viewModel.onArchiveClicked(tag)
            viewModel.onArchiveDismissed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.archiveCandidate)
            assertEquals("food", store.tags.getActive("food")!!.id)
        }
    }

    @Test
    fun editingATagLoadsItsScopeAndCategoryIntoTheForm() = runTest(dispatcher) {
        freshStore().use { store ->
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
            store.tags.create(
                tagDraft("food-tag").copy(categoryId = "food", tripType = TripType.OTHER),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown(contextTripId = null)
            advanceUntilIdle()

            val tag = viewModel.state.value.tags.single()
            viewModel.onEditClicked(tag)

            val form = requireNotNull(viewModel.state.value.form)
            assertEquals("food", form.categoryId)
            assertEquals(TripType.OTHER, form.tripType)
        }
    }

    @Test
    fun openingATagLoadsItsTotalItsTripsAndItsMovements() = runTest(dispatcher) {
        freshStore().use { store ->
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
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.trips.create(tripDraft("roma"), createdAt = NOW)
            store.tags.create(tagDraft("dinar"), createdAt = NOW)
            store.movements.create(expense("paella", 3_000L, tripId = "mallorca", tagId = "dinar"), createdAt = NOW)
            store.movements.create(expense("menu", 1_000L, tripId = "roma", tagId = "dinar"), createdAt = NOW)
            store.movements.create(expense("coffee", 500L, tripId = "mallorca", tagId = null), createdAt = NOW)
            val viewModel = viewModel(store)

            viewModel.onTagDetailOpened("dinar")
            advanceUntilIdle()

            val detail = requireNotNull(viewModel.state.value.detail)
            assertEquals(4_000L, detail.totalCents)
            assertEquals(listOf("mallorca" to 3_000L, "roma" to 1_000L), detail.byTrip.map { it.tripId to it.actualCents })
            assertEquals(setOf("paella", "menu"), detail.movements.map { it.id }.toSet())
        }
    }

    private fun expense(id: String, amountCents: Long, tripId: String?, tagId: String?) = MovementDraft(
        id = id,
        type = MovementType.EXPENSE,
        amountCents = amountCents,
        date = "2026-03-10",
        accountId = "checking",
        destinationAccountId = null,
        categoryId = null,
        tripId = tripId,
        tagId = tagId,
        name = id,
        payee = null,
        notes = null,
        isOneTime = false,
    )

    private fun viewModel(store: TestStore): TagsViewModel =
        TagsViewModel(
            tagRepository = store.tags,
            tripRepository = store.trips,
            categoryRepository = store.categories,
            movementRepository = store.movements,
            tripAnalysisRepository = store.tripAnalysis,
            ioDispatcher = dispatcher,
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            tags = TagRepository(database.tagsQueries),
            trips = TripRepository(database.tripsQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            tripAnalysis = TripAnalysisRepository(database.tripAnalysisQueries),
            accounts = AccountRepository(database.accountsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val tags: TagRepository,
        val trips: TripRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val tripAnalysis: TripAnalysisRepository,
        val accounts: AccountRepository,
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

    private fun tagDraft(id: String): TagDraft =
        TagDraft(
            id = id,
            name = id,
            icon = null,
            color = null,
            tripId = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
