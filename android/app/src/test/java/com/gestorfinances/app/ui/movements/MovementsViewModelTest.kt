package com.gestorfinances.app.ui.movements

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.SplitEntryMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementsViewModelTest {
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
    fun amountMustBePositive() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(amount = "0", date = "2026-01-01", accountId = "checking"),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_amount_positive, viewModel.form().errorRes)
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    @Test
    fun transferToSameAccountIsRejected() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(accountDraft("savings", displayOrder = 1), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    destinationAccountId = "checking",
                ),
            )
            viewModel.onSaveClicked()

            assertEquals(
                R.string.movement_validation_transfer_same_account,
                viewModel.form().errorRes,
            )
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    @Test
    fun duplicateWarnsThenOverrideSaves() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "existing", amountCents = 250, name = "Cafè"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "2,50",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Cafè",
                ),
            )
            viewModel.onSaveClicked()

            assertTrue(viewModel.form().duplicateWarning)
            assertEquals(1, store.movements.listActive().size)

            viewModel.onDuplicateOverrideClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            assertEquals(2, store.movements.listActive().size)
        }
    }

    @Test
    fun nonDuplicateSavesWithoutWarning() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "existing", amountCents = 250, name = "Cafè"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "9,99",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Llibre",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            assertFalse(store.movements.listActive().none { it.name == "Llibre" })
            assertEquals(2, store.movements.listActive().size)
        }
    }

    @Test
    fun sharedSplitMustReconcileBeforeSaving() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            val splitEditor = SplitEditorState()
                .withPersonToggled("laura")
                .withMethod(SplitEntryMethod.EXACT)
                .withExactAmount(USER_PARTICIPANT_ID, "3")
                .withExactAmount("laura", "4")
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    splitEditor = splitEditor,
                ),
            )

            viewModel.onSaveClicked()

            assertEquals(R.string.split_validation_reconcile, viewModel.form().errorRes)
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    @Test
    fun sharedEqualSplitSavesMovementAndDerivedPersonBalance() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Sopar",
                    splitEditor = SplitEditorState().withPersonToggled("laura"),
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertTrue(movement.isShared)
            assertEquals(500L, store.people.getActive("laura")!!.balanceCents)
            assertNull(viewModel.state.value.form)
        }
    }

    @Test
    fun paidByOtherPresetSavesZeroActualExpenseAndFullAccountFlow() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Entrades",
                    splitEditor = SplitEditorState().withPaidByOther("laura"),
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            val totals = store.analysis.periodTotals(
                fromDate = "2026-01-01",
                toDate = "2026-01-02",
            )

            assertTrue(movement.isShared)
            assertEquals("laura", movement.paidByPersonName)
            assertEquals(1_000L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(0L, totals.actualExpenseCents)
            assertEquals(-1_000L, totals.accountFlowCents)
            assertNull(viewModel.state.value.form)
        }
    }

    @Test
    fun overRefundIsWarnedButNotBlocked() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 5_000, name = "Sabates"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val expense = store.movements.listActive().single { it.id == "exp" }
            viewModel.onDetailClicked(expense)
            advanceUntilIdle()
            viewModel.onAddRefundClicked(expense)

            val form = viewModel.state.value.refundForm!!
            assertEquals(5_000L, form.remainingCents)
            // Refund more than the remaining amount — must still save (warn, never block).
            viewModel.onRefundFormChanged(form.copy(amount = "60", date = "2026-01-02"))
            viewModel.onRefundSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.refundForm)
            assertEquals(1, store.movements.refundsForExpense("exp").size)
        }
    }

    private fun MovementsViewModel.form(): MovementFormState = state.value.form!!

    private fun viewModel(store: TestStore): MovementsViewModel =
        MovementsViewModel(
            movementRepository = store.movements,
            accountRepository = store.accounts,
            categoryRepository = store.categories,
            personRepository = store.people,
            ioDispatcher = dispatcher,
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            analysis = AnalysisRepository(database.analysisQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            people = PersonRepository(database.peopleQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val analysis: AnalysisRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private fun accountDraft(id: String, displayOrder: Long = 0): AccountDraft =
        AccountDraft(
            id = id,
            name = id,
            startingBalanceCents = 0,
            type = AccountType.BANK,
            icon = null,
            color = null,
            isDefault = displayOrder == 0L,
            displayOrder = displayOrder,
            lowBalanceThresholdCents = null,
        )

    private fun personDraft(id: String): PersonDraft =
        PersonDraft(
            id = id,
            name = id,
            avatar = null,
            color = null,
            notes = null,
        )

    private fun movementDraft(
        id: String,
        amountCents: Long,
        name: String,
    ): MovementDraft =
        MovementDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = amountCents,
            date = "2026-01-01",
            accountId = "checking",
            destinationAccountId = null,
            categoryId = null,
            name = name,
            payee = null,
            notes = null,
            isOneTime = false,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
