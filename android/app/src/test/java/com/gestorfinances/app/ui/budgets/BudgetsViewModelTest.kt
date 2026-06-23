package com.gestorfinances.app.ui.budgets

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.TripDraft
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripType
import java.time.LocalDate
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {
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
    fun tripContextOpensPrefilledTripBudgetFormAndSaves() = runTest(dispatcher) {
        freshStore().use { store ->
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)

            viewModel.onScreenShown(contextTripId = "mallorca")
            advanceUntilIdle()

            val form = requireNotNull(viewModel.state.value.form)
            assertEquals(BudgetScope.TRIP, form.scope)
            assertEquals("mallorca", form.tripId)
            assertEquals("2026-08-01", form.startDate)

            viewModel.onFormChanged(form.copy(limit = "250", threshold = "80"))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val evaluation = store.budgets.evaluateAll("2026-08-01", "2026-08-31").single()
            assertEquals(BudgetScope.TRIP, evaluation.budget.scope)
            assertEquals("mallorca", evaluation.budget.tripId)
            assertEquals(25_000L, evaluation.budget.limitAmountCents)
        }
    }

    private fun viewModel(store: TestStore): BudgetsViewModel =
        BudgetsViewModel(
            budgetRepository = store.budgets,
            categoryRepository = store.categories,
            tripRepository = store.trips,
            ioDispatcher = dispatcher,
            today = { LocalDate.parse("2026-08-15") },
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            budgets = BudgetRepository(database.budgetsQueries),
            categories = CategoryRepository(database.categoriesQueries),
            trips = TripRepository(database.tripsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val budgets: BudgetRepository,
        val categories: CategoryRepository,
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
            startDate = "2026-08-01",
            endDate = "2026-08-10",
            icon = null,
            color = null,
            notes = null,
            defaultAccountId = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
