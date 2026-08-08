package com.gestorfinances.app.ui.budgets

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.BudgetDraft
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
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
import org.junit.Assert.assertTrue
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

    @Test
    fun categoryBudgetWithoutASelectedCategoryIsRejectedOnCategoryField() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    scope = BudgetScope.CATEGORY,
                    categoryId = null,
                    limit = "100",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val form = requireNotNull(viewModel.state.value.form)
            assertEquals(
                com.gestorfinances.app.R.string.budget_validation_category_required,
                form.errorRes,
            )
            assertEquals(BudgetFormField.CATEGORY, form.errorField)
        }
    }

    @Test
    fun deletingABudgetSetsTheArchiveCandidateWithoutArchivingIt() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("food"), createdAt = NOW)
            store.budgets.create(budgetDraft("food-budget", categoryId = "food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val evaluation = viewModel.state.value.evaluations.single()
            viewModel.onDeleteClicked(evaluation.budget)

            assertEquals(evaluation.budget, viewModel.state.value.archiveCandidate)
            assertEquals(1, store.budgets.evaluateAll("2026-08-01", "2026-08-31").size)
        }
    }

    @Test
    fun confirmingTheArchiveCandidateActuallyArchivesTheBudget() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("food"), createdAt = NOW)
            store.budgets.create(budgetDraft("food-budget", categoryId = "food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val evaluation = viewModel.state.value.evaluations.single()
            viewModel.onDeleteClicked(evaluation.budget)
            viewModel.onArchiveConfirmed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.archiveCandidate)
            assertTrue(viewModel.state.value.evaluations.isEmpty())
            assertTrue(store.budgets.evaluateAll("2026-08-01", "2026-08-31").isEmpty())
        }
    }

    @Test
    fun dismissingTheArchiveCandidateLeavesTheBudgetUntouched() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("food"), createdAt = NOW)
            store.budgets.create(budgetDraft("food-budget", categoryId = "food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val evaluation = viewModel.state.value.evaluations.single()
            viewModel.onDeleteClicked(evaluation.budget)
            viewModel.onArchiveDismissed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.archiveCandidate)
            assertEquals(1, store.budgets.evaluateAll("2026-08-01", "2026-08-31").size)
        }
    }

    @Test
    fun onAddClickedWithACategoryIdSeedsACategoryScopedForm() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked(categoryId = "food")

            val form = requireNotNull(viewModel.state.value.form)
            assertEquals(BudgetScope.CATEGORY, form.scope)
            assertEquals("food", form.categoryId)
            assertNull(form.id)
        }
    }

    @Test
    fun deletingFromTheEditSheetClosesItBeforeOpeningTheArchiveConfirmation() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("food"), createdAt = NOW)
            store.budgets.create(budgetDraft("food-budget", categoryId = "food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onEditClicked(viewModel.state.value.evaluations.single().budget)
            viewModel.onDeleteEditingBudgetClicked()

            assertNull(viewModel.state.value.form)
            assertEquals("food-budget", viewModel.state.value.archiveCandidate?.id)
        }
    }

    @Test
    fun addingAnAlreadyConfiguredMonthlyCategoryStillStartsANewForm() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("food"), createdAt = NOW)
            store.budgets.create(budgetDraft("food-monthly", categoryId = "food"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked(categoryId = "food")

            val form = requireNotNull(viewModel.state.value.form)
            assertNull(form.id)
            assertEquals(BudgetScope.CATEGORY, form.scope)
        }
    }

    @Test
    fun addingABudgetNeverOpensTheExistingOverallMonthlyBudget() = runTest(dispatcher) {
        freshStore().use { store ->
            store.budgets.create(
                BudgetDraft(
                    id = "overall-monthly",
                    categoryId = null,
                    limitAmountCents = 100_000L,
                    alertThresholdPercent = null,
                    scope = BudgetScope.OVERALL_MONTH,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()

            val form = requireNotNull(viewModel.state.value.form)
            assertNull(form.id)
            assertEquals(BudgetScope.CATEGORY, form.scope)
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

    private fun categoryDraft(id: String): CategoryDraft =
        CategoryDraft(
            id = id,
            name = id,
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            parentId = null,
            icon = null,
            color = null,
            displayOrder = 0,
        )

    private fun budgetDraft(id: String, categoryId: String): BudgetDraft =
        BudgetDraft(
            id = id,
            categoryId = categoryId,
            limitAmountCents = 25_000L,
            alertThresholdPercent = 80L,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
