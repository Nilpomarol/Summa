package com.gestorfinances.app.ui.trips

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.BudgetDraft
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TripAnalysisRepository
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
class TripsViewModelTest {
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
    fun creatingATripWithoutANameShowsAValidationError() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "  "))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.trip_validation_name_required,
                viewModel.state.value.form!!.errorRes,
            )
            assertTrue(store.trips.listActive().isEmpty())
        }
    }

    @Test
    fun invalidStartOrEndDateShowsAValidationError() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Mallorca", startDate = "not-a-date"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.trip_validation_start_date_invalid,
                viewModel.state.value.form!!.errorRes,
            )
            assertTrue(store.trips.listActive().isEmpty())
        }
    }

    @Test
    fun endDateBeforeStartDateShowsAValidationError() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    name = "Mallorca",
                    startDate = "2026-08-10",
                    endDate = "2026-08-01",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.trip_validation_date_order,
                viewModel.state.value.form!!.errorRes,
            )
        }
    }

    @Test
    fun defaultAccountMustBeAnActiveAccount() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Mallorca", defaultAccountId = "missing"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.movement_validation_account_required,
                viewModel.state.value.form!!.errorRes,
            )
        }
    }

    @Test
    fun savingAValidFormCreatesTheTripAndClosesTheForm() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    name = "Mallorca",
                    type = TripType.TRIP,
                    status = TripStatus.PLANNED,
                    startDate = "2026-08-01",
                    endDate = "2026-08-10",
                    defaultAccountId = "checking",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val trip = viewModel.state.value.trips.single()
            assertEquals("Mallorca", trip.name)
            assertEquals(TripStatus.PLANNED, trip.status)
            assertEquals("checking", trip.defaultAccountId)
        }
    }

    @Test
    fun archivingATripConfirmsBeforeRemovingItFromTheActiveList() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onArchiveClicked(trip)
            assertEquals(trip, viewModel.state.value.archiveCandidate)

            viewModel.onArchiveConfirmed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.archiveCandidate)
            assertTrue(viewModel.state.value.trips.isEmpty())
            assertNull(store.trips.getActive("mallorca"))
        }
    }

    // Regression: `TripDetailScreen`'s own archive-confirm dialog used to call
    // `viewModel.onArchiveConfirmed()` and `onBack()` back-to-back, racing the archive
    // coroutine — a failure there was silently swallowed because it only ever set the
    // top-level `TripsUiState.errorMessage`, which `TripDetailScreen` (and whatever screen
    // `onBack()` had already navigated to) never reads. `onArchiveConfirmed` now only invokes
    // its `onSuccess` callback once the archive actually completes, and on failure attaches the
    // error to `detail` (which `TripDetailScreen` renders inline) whenever the archived trip is
    // the one currently open.
    @Test
    fun `archive failure while trip detail is open surfaces the error inline instead of navigating away`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onDetailClicked(trip)
            advanceUntilIdle()
            assertEquals("mallorca", viewModel.state.value.detail?.trip?.id)

            // Deterministically forces the archive UPDATE to fail without depending on any
            // particular exception type — mirrors RecurringViewModelTest's pattern.
            store.driver.execute(
                null,
                """
                CREATE TRIGGER reject_archive
                BEFORE UPDATE ON trips
                WHEN NEW.archived_at IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'simulated failure for regression test');
                END;
                """.trimIndent(),
                0,
            )

            viewModel.onArchiveClicked(trip)
            var onSuccessCalled = false
            viewModel.onArchiveConfirmed(onSuccess = { onSuccessCalled = true })
            advanceUntilIdle()

            assertTrue("onSuccess must not fire on a failed archive", !onSuccessCalled)
            assertNull(viewModel.state.value.archiveCandidate)
            assertNull(
                "the list-level errorMessage must stay clear; Trip Detail is where the error is shown",
                viewModel.state.value.errorMessage,
            )
            assertEquals(true, viewModel.state.value.detail?.errorMessage != null)
            assertEquals("mallorca", store.trips.getActive("mallorca")?.id)
        }
    }

    @Test
    fun archivingFromTripDetailInvokesOnSuccessOnlyAfterTheArchiveCompletes() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onDetailClicked(trip)
            advanceUntilIdle()

            viewModel.onArchiveClicked(trip)
            var onSuccessCalled = false
            viewModel.onArchiveConfirmed(onSuccess = { onSuccessCalled = true })

            // Not yet advanced: the coroutine hasn't completed, so onSuccess must not have fired
            // synchronously (that would reintroduce the original race with navigation).
            assertTrue(!onSuccessCalled)

            advanceUntilIdle()

            assertTrue(onSuccessCalled)
            assertNull(store.trips.getActive("mallorca"))
        }
    }

    @Test
    fun dismissingTheArchiveConfirmationLeavesTheTripUntouched() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onArchiveClicked(trip)
            viewModel.onArchiveDismissed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.archiveCandidate)
            assertEquals("mallorca", store.trips.getActive("mallorca")!!.id)
        }
    }

    @Test
    fun detailOpenedByIdLoadsATripThatIsNotYetInTheListedTrips() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            // Deliberately skip onScreenShown()/refresh() so `state.value.trips` is still empty,
            // mirroring a Dashboard quick-link opening detail before the Trips list has loaded.
            assertTrue(viewModel.state.value.trips.isEmpty())

            viewModel.onDetailOpened("mallorca")
            advanceUntilIdle()

            val detail = requireNotNull(viewModel.state.value.detail)
            assertEquals("mallorca", detail.trip.id)
            assertEquals(false, detail.excludeOneTime)
        }
    }

    @Test
    fun detailOpenedByIdReusesAnAlreadyLoadedSummaryWhenAvailable() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onDetailOpened("mallorca")
            advanceUntilIdle()

            assertEquals("mallorca", viewModel.state.value.detail?.trip?.id)
        }
    }

    @Test
    fun detailOpenedWithAnUnknownIdLeavesDetailNull() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onDetailOpened("does-not-exist")
            advanceUntilIdle()

            assertNull(viewModel.state.value.detail)
        }
    }

    @Test
    fun excludeOneTimeToggleReloadsTheDetailExcludingOneTimeMovements() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "flight", tripId = "mallorca", amountCents = 20_000, isOneTime = true),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(id = "dinner", tripId = "mallorca", amountCents = 3_000, isOneTime = false),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onDetailClicked(trip)
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.detail!!.excludeOneTime)
            assertEquals(23_000L, viewModel.state.value.detail!!.summary.actualCents)

            viewModel.onExcludeOneTimeToggled(true)
            advanceUntilIdle()

            assertEquals(true, viewModel.state.value.detail!!.excludeOneTime)
            assertEquals(3_000L, viewModel.state.value.detail!!.summary.actualCents)
        }
    }

    @Test
    fun detailLoadsOnlyMovementsBelongingToTheOpenedTrip() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.trips.create(tripDraft("andorra"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "mallorca-dinner", tripId = "mallorca", amountCents = 3_000, isOneTime = false),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(id = "andorra-dinner", tripId = "andorra", amountCents = 5_000, isOneTime = false),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single { it.id == "mallorca" }
            viewModel.onDetailClicked(trip)
            advanceUntilIdle()

            val movementIds = viewModel.state.value.detail!!.movements.map { it.id }
            assertEquals(listOf("mallorca-dinner"), movementIds)
        }
    }

    @Test
    fun detailSurfacesAnActiveTripScopeBudgetEvaluation() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.budgets.create(
                BudgetDraft(
                    id = "mallorca-budget",
                    categoryId = null,
                    limitAmountCents = 50_000,
                    alertThresholdPercent = 80,
                    startDate = todayMonth().atDay(1).toString(),
                    tripId = "mallorca",
                ),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(id = "dinner", tripId = "mallorca", amountCents = 3_000, isOneTime = false),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onDetailClicked(trip)
            advanceUntilIdle()

            val evaluation = requireNotNull(viewModel.state.value.detail!!.budgetEvaluation)
            assertEquals("mallorca", evaluation.budget.tripId)
            assertEquals(50_000L, evaluation.budget.limitAmountCents)
        }
    }

    @Test
    fun tripBudgetEvaluationCountsAdvanceBookingSpendOutsideTheCurrentCalendarMonth() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            // A planned trip whose own window is far from whatever the current calendar month
            // happens to be when this test runs (mirrors DataSeeder's Japan trip: planned,
            // future, with advance-booking spend recorded months before it even starts).
            store.trips.create(
                tripDraft("japan").copy(startDate = "2030-10-15", endDate = "2030-10-30"),
                createdAt = NOW,
            )
            store.budgets.create(
                BudgetDraft(
                    id = "japan-budget",
                    categoryId = null,
                    limitAmountCents = 200_000,
                    alertThresholdPercent = 80,
                    startDate = "2030-10-15",
                    tripId = "japan",
                ),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(id = "flights", tripId = "japan", amountCents = 89_000, isOneTime = false)
                    .copy(date = "2030-05-10"),
                createdAt = NOW,
            )

            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onDetailClicked(trip)
            advanceUntilIdle()

            val evaluation = requireNotNull(viewModel.state.value.detail!!.budgetEvaluation)
            assertEquals("japan", evaluation.budget.tripId)
            assertEquals(89_000L, evaluation.actualCents)
        }
    }

    @Test
    fun editingATripKeepsDetailOpenAndReloadsItWithTheSavedName() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val trip = viewModel.state.value.trips.single()
            viewModel.onDetailClicked(trip)
            advanceUntilIdle()
            assertEquals("mallorca", viewModel.state.value.detail!!.trip.name)

            viewModel.onEditClicked(trip)
            // Trip detail (a full page, not a dialog) stays visible underneath the edit form.
            assertEquals("mallorca", viewModel.state.value.detail?.trip?.id)

            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "Mallorca 2026"))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            assertEquals("Mallorca 2026", viewModel.state.value.detail!!.trip.name)
        }
    }

    @Test
    fun statusFilterNarrowsVisibleTrips() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca", status = TripStatus.ACTIVE), createdAt = NOW)
            store.trips.create(tripDraft("andorra", status = TripStatus.PLANNED), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.visibleTrips.size)

            viewModel.onStatusFilterChanged(TripStatus.ACTIVE)

            assertEquals(listOf("mallorca"), viewModel.state.value.visibleTrips.map { it.id })
        }
    }

    private fun viewModel(store: TestStore): TripsViewModel =
        TripsViewModel(
            tripRepository = store.trips,
            tripAnalysisRepository = store.tripAnalysis,
            movementRepository = store.movements,
            accountRepository = store.accounts,
            budgetRepository = store.budgets,
            tagRepository = store.tags,
            ioDispatcher = dispatcher,
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            trips = TripRepository(database.tripsQueries),
            tripAnalysis = TripAnalysisRepository(database.tripAnalysisQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            accounts = AccountRepository(database.accountsQueries),
            budgets = BudgetRepository(database.budgetsQueries),
            tags = TagRepository(database.tagsQueries),
        )
    }

    private class TestStore(
        val driver: JdbcSqliteDriver,
        val trips: TripRepository,
        val tripAnalysis: TripAnalysisRepository,
        val movements: MovementRepository,
        val accounts: AccountRepository,
        val budgets: BudgetRepository,
        val tags: TagRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private fun tripDraft(id: String, status: TripStatus = TripStatus.ACTIVE): TripDraft =
        TripDraft(
            id = id,
            name = id,
            type = TripType.TRIP,
            status = status,
            startDate = "2026-08-01",
            endDate = "2026-08-10",
            icon = null,
            color = null,
            notes = null,
            defaultAccountId = null,
        )

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

    private fun movementDraft(
        id: String,
        tripId: String,
        amountCents: Long,
        isOneTime: Boolean,
    ): MovementDraft =
        MovementDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = amountCents,
            date = "2026-08-02",
            accountId = "checking",
            destinationAccountId = null,
            categoryId = null,
            tripId = tripId,
            tagId = null,
            name = id,
            payee = null,
            notes = null,
            isOneTime = isOneTime,
        )

    private fun todayMonth(): java.time.YearMonth = java.time.YearMonth.from(java.time.LocalDate.now())

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
