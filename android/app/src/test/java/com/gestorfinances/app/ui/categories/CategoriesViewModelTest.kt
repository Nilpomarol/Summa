package com.gestorfinances.app.ui.categories

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Smoke coverage for the category form's field-level validation (field-level validation) --
 * asserts `(errorRes, errorField)` for the two validation branches this form can hit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {
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
    fun blankNameIsRejectedOnNameField() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "  "))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.category_validation_name_required,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(CategoryFormField.NAME, viewModel.state.value.form!!.errorField)
            assertTrue(store.categories.listActive().isEmpty())
        }
    }

    @Test
    fun aParentWithActiveChildrenIsRejectedOnParentField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("groceries"), createdAt = NOW)
            store.categories.create(
                categoryDraft("supermarket").copy(parentId = "groceries"),
                createdAt = NOW,
            )
            store.categories.create(categoryDraft("other-parent", displayOrder = 1), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            // "groceries" already has an active child ("supermarket") -- assigning it a parent of
            // its own (moving it under "other-parent") must be rejected: a category with active
            // children can't itself become a child (one level of nesting only).
            val parent = viewModel.state.value.categories.single { it.id == "groceries" }
            viewModel.onEditClicked(parent)
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Alimentació", parentId = "other-parent"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.category_validation_parent_has_children,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(CategoryFormField.PARENT, viewModel.state.value.form!!.errorField)
        }
    }

    @Test
    fun parentDrillThroughIncludesChildrensMovements() = runTest(dispatcher) {
        freshStore().use { store ->
            store.categories.create(categoryDraft("food"), createdAt = NOW)
            store.categories.create(
                categoryDraft("restaurants").copy(parentId = "food"),
                createdAt = NOW,
            )
            store.exec(
                """
                INSERT INTO accounts (id, name, starting_balance_cents, type, display_order, created_at, updated_at)
                VALUES ('checking', 'Checking', 0, 'bank', 0, '$NOW', '$NOW');
                """.trimIndent(),
            )
            store.exec(expenseInsert("m-own", category = "food", cents = 1000))
            store.exec(expenseInsert("m-child", category = "restaurants", cents = 3000))

            // Tapping the container 'food' rolls up: its drill-through returns the parent's own
            // movement plus every child's movement.
            val parentEntries = store.movements.listActiveForCategory("food")
            assertEquals(setOf("m-own", "m-child"), parentEntries.map { it.id }.toSet())

            // Tapping the leaf child stays scoped to itself.
            val leafEntries = store.movements.listActiveForCategory("restaurants")
            assertEquals(setOf("m-child"), leafEntries.map { it.id }.toSet())
        }
    }

    private fun expenseInsert(id: String, category: String, cents: Long): String =
        """
        INSERT INTO movements
            (id, type, amount_cents, date, account_id, name, is_one_time, category_id, created_at, updated_at)
        VALUES ('$id', 'expense', $cents, '2026-06-05', 'checking', '$id', 0, '$category', '$NOW', '$NOW');
        """.trimIndent()

    private fun viewModel(store: TestStore): CategoriesViewModel =
        CategoriesViewModel(
            categoryRepository = store.categories,
            analysisRepository = store.analysis,
            movementRepository = store.movements,
            budgetRepository = store.budgets,
            ioDispatcher = dispatcher,
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            categories = CategoryRepository(database.categoriesQueries),
            analysis = AnalysisRepository(database.analysisQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            budgets = BudgetRepository(database.budgetsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val categories: CategoryRepository,
        val analysis: AnalysisRepository,
        val movements: MovementRepository,
        val budgets: BudgetRepository,
    ) : AutoCloseable {
        fun exec(sql: String) {
            driver.execute(null, sql, 0)
        }

        override fun close() {
            driver.close()
        }
    }

    private fun categoryDraft(id: String, displayOrder: Long = 0): CategoryDraft =
        CategoryDraft(
            id = id,
            name = id,
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            parentId = null,
            icon = null,
            color = null,
            displayOrder = displayOrder,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
