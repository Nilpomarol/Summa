package com.gestorfinances.app.ui.accounts

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.TemplateRepository
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
 * Smoke coverage for the account form's field-level validation (field-level validation) --
 * asserts `(errorRes, errorField)` for each validation branch this form can hit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
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
            viewModel.onAddClicked()

            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "  "))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.account_validation_name_required,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(AccountFormField.NAME, viewModel.state.value.form!!.errorField)
            assertTrue(store.accounts.listActive().isEmpty())
        }
    }

    @Test
    fun invalidLowBalanceThresholdIsRejectedOnThresholdField() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onAddClicked()

            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    name = "Compte corrent",
                    startingBalance = "100",
                    lowBalanceThreshold = "not-a-number",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.account_validation_low_balance_invalid,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(AccountFormField.LOW_BALANCE_THRESHOLD, viewModel.state.value.form!!.errorField)
        }
    }

    @Test
    fun turningAnAccountSharedMakesNobodyAMember() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    name = "Conjunt",
                    members = listOf(
                        AccountMemberFormState(null, "", true, "100,00", "100,00"),
                        AccountMemberFormState("alba", "Alba", false, "0,00", "0,00"),
                    ),
                ),
            )

            viewModel.onOwnershipChanged(AccountOwnershipKind.SHARED)

            assertEquals(listOf<String?>(null), viewModel.state.value.form!!.members.filter { it.enabled }.map { it.personId })
            viewModel.onSaveClicked()
            assertEquals(R.string.account_validation_shared_members, viewModel.state.value.form!!.errorRes)
        }
    }

    @Test
    fun aPersonCreatedFromTheAccountFormJoinsItAsAMember() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            viewModel.onOwnershipChanged(AccountOwnershipKind.SHARED)

            viewModel.onCreatePersonForAccount("  Alba  ")
            advanceUntilIdle()

            val alba = store.people.listActive().single()
            assertEquals("Alba", alba.name)
            assertTrue(viewModel.state.value.form!!.members.single { it.personId == alba.id }.enabled)
        }
    }

    private fun viewModel(store: TestStore): AccountsViewModel =
        AccountsViewModel(
            goalRepository = store.goals,
            accountRepository = store.accounts,
            movementRepository = store.movements,
            templateRepository = store.templates,
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
            goals = com.gestorfinances.app.data.repository.GoalRepository(database.goalsQueries, database.analysisQueries),
            accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            templates = TemplateRepository(database.templatesQueries),
            people = PersonRepository(database.peopleQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val goals: com.gestorfinances.app.data.repository.GoalRepository,
        val accounts: AccountRepository,
        val movements: MovementRepository,
        val templates: TemplateRepository,
        val people: PersonRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }
}
