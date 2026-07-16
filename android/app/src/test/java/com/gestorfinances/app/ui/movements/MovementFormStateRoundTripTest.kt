package com.gestorfinances.app.ui.movements

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementFormStateRoundTripTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun personalExpenseRoundTrip() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val vm = viewModel(store)
            vm.onAddClicked()
            advanceUntilIdle()

            vm.onFormChanged(vm.form().copy(
                amount = "15",
                date = "2026-01-01",
                accountId = "checking",
                name = "Dinar",
                expenseKind = ExpenseKind.PERSONAL,
            ))
            vm.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            vm.onEditClicked(movement)
            advanceUntilIdle()

            val reloaded = vm.form()
            assertEquals(ExpenseKind.PERSONAL, reloaded.expenseKind)
            assertEquals("checking", reloaded.accountId)
            assertNull(reloaded.splitEditor)
            assertNull(reloaded.forOtherPersonId)
        }
    }

    @Test
    fun sharedExpenseRoundTrip() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(PersonDraft("anna", "Anna", null, null, null), createdAt = NOW)
            val vm = viewModel(store)
            vm.onAddClicked()
            advanceUntilIdle()

            vm.onFormChanged(vm.form().copy(
                amount = "20",
                date = "2026-01-01",
                accountId = "checking",
                name = "Sopar",
                expenseKind = ExpenseKind.SHARED,
                splitEditor = SplitEditorState().withPersonToggled("anna"),
            ))
            vm.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            vm.onEditClicked(movement)
            advanceUntilIdle()

            val reloaded = vm.form()
            assertEquals(ExpenseKind.SHARED, reloaded.expenseKind)
            assertEquals("checking", reloaded.accountId)
            assertNotNull(reloaded.splitEditor)
            assertNull(reloaded.forOtherPersonId)
        }
    }

    @Test
    fun forOtherExpenseRoundTrip() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(PersonDraft("anna", "Anna", null, null, null), createdAt = NOW)
            val vm = viewModel(store)
            vm.onAddClicked()
            advanceUntilIdle()

            vm.onFormChanged(vm.form().copy(
                amount = "30",
                date = "2026-01-01",
                accountId = "checking",
                name = "Entrades",
                expenseKind = ExpenseKind.FOR_OTHER,
                forOtherPersonId = "anna",
            ))
            vm.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            vm.onEditClicked(movement)
            advanceUntilIdle()

            val reloaded = vm.form()
            assertEquals(ExpenseKind.FOR_OTHER, reloaded.expenseKind)
            assertEquals("checking", reloaded.accountId)
            assertEquals("anna", reloaded.forOtherPersonId)
            assertNull(reloaded.splitEditor)
        }
    }

    @Test
    fun debtExpenseRoundTrip() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(PersonDraft("marc", "Marc", null, null, null), createdAt = NOW)
            val vm = viewModel(store)
            vm.onAddClicked()
            advanceUntilIdle()

            vm.onFormChanged(vm.form().copy(
                amount = "40",
                date = "2026-01-01",
                name = "Taxi",
                expenseKind = ExpenseKind.DEBT,
                forOtherPersonId = "marc",
            ))
            vm.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            vm.onEditClicked(movement)
            advanceUntilIdle()

            val reloaded = vm.form()
            assertEquals(ExpenseKind.DEBT, reloaded.expenseKind)
            assertEquals("marc", reloaded.forOtherPersonId)
            assertNull(reloaded.accountId)
        }
    }

    @Test
    fun incomeRoundTripKeepsTypeAndAccount() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val vm = viewModel(store)
            vm.onAddClicked()
            advanceUntilIdle()

            vm.onFormChanged(vm.form().copy(
                type = MovementType.INCOME,
                amount = "100",
                date = "2026-01-01",
                accountId = "checking",
                name = "Nòmina",
            ))
            vm.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertEquals(MovementType.INCOME, movement.type)
            vm.onEditClicked(movement)
            advanceUntilIdle()

            assertEquals(MovementType.INCOME, vm.form().type)
            assertEquals("checking", vm.form().accountId)
        }
    }

    @Test
    fun transferRoundTripKeepsBothAccounts() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(
                accountDraft("savings").copy(isDefault = false, displayOrder = 1),
                createdAt = NOW,
            )
            val vm = viewModel(store)
            vm.onAddClicked()
            advanceUntilIdle()

            vm.onFormChanged(vm.form().copy(
                type = MovementType.TRANSFER,
                amount = "50",
                date = "2026-01-01",
                accountId = "checking",
                destinationAccountId = "savings",
                name = "Estalvi",
            ))
            vm.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertEquals(MovementType.TRANSFER, movement.type)
            assertEquals("checking", movement.accountId)
            assertEquals("savings", movement.destinationAccountId)
            vm.onEditClicked(movement)
            advanceUntilIdle()

            assertEquals(MovementType.TRANSFER, vm.form().type)
            assertEquals("savings", vm.form().destinationAccountId)
        }
    }

    @Test
    fun optionalDisclosureStartsCollapsedAndCanBeToggledWithoutChangingDraft() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val vm = viewModel(store)
            vm.onAddClicked()
            advanceUntilIdle()
            val before = vm.form()

            assertEquals(false, before.showOptional)
            vm.onOptionalToggled()
            assertEquals(true, vm.form().showOptional)
            assertEquals(before.copy(showOptional = true), vm.form())

            vm.onOptionalToggled()
            assertEquals(false, vm.form().showOptional)
        }
    }

    private fun MovementsViewModel.form(): MovementFormState = state.value.form!!

    private fun viewModel(store: TestStore): MovementsViewModel =
        MovementsViewModel(
            movementRepository = store.movements,
            accountRepository = store.accounts,
            categoryRepository = store.categories,
            personRepository = store.people,
            tripRepository = store.trips,
            tagRepository = store.tags,
            splitRepository = store.splits,
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
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            people = PersonRepository(database.peopleQueries),
            splits = SplitRepository(database.splitsQueries),
            tags = TagRepository(database.tagsQueries),
            trips = TripRepository(database.tripsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val splits: SplitRepository,
        val tags: TagRepository,
        val trips: TripRepository,
    ) : AutoCloseable {
        override fun close() = driver.close()
    }

    private fun accountDraft(id: String): AccountDraft =
        AccountDraft(
            id = id,
            name = id,
            startingBalanceCents = 0,
            type = AccountType.BANK,
            icon = null,
            color = null,
            isDefault = true,
            displayOrder = 0,
            lowBalanceThresholdCents = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
