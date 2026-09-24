package com.gestorfinances.app.ui.accounts

import org.junit.Assert.assertNull
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AccountMemberDraft
import com.gestorfinances.app.data.repository.AccountDraft
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

    @Test
    fun aWithdrawalFromTheAccountPageLeavesTheAccountAndStaysOutwardWhenCorrected() = runTest(dispatcher) {
        freshStore().use { store ->
            val at = "2026-01-01T00:00:00Z"
            store.people.create(PersonDraft("alba", "Alba", null, null, null), at)
            store.accounts.create(accountDraft("personal", displayOrder = 0), createdAt = at)
            store.accounts.create(
                accountDraft("shared", displayOrder = 1).copy(
                    startingBalanceCents = 10_000,
                    ownershipKind = AccountOwnershipKind.SHARED,
                    members = listOf(
                        AccountMemberDraft(SplitParticipantKind.USER, null, 5_000L, 5_000L),
                        AccountMemberDraft(SplitParticipantKind.PERSON, "alba", 5_000L, 5_000L),
                    ),
                ),
                createdAt = at,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onWithdrawalClicked(viewModel.state.value.accounts.single { it.id == "shared" })
            val form = viewModel.state.value.contributionForm!!
            assertEquals(ContributionDirection.OUT, form.direction)
            viewModel.onContributionFormChanged(form.copy(amount = "20", sourceAccountId = "personal"))
            viewModel.onContributionSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.contributionForm)
            assertEquals(2_000L, store.accounts.getActive("personal")!!.currentBalanceCents)
            assertEquals(8_000L, store.accounts.getActive("shared")!!.currentBalanceCents)

            // Correcting it reopens the form outward, and saving keeps it outward.
            val withdrawal = store.movements.listActive().single { it.type == MovementType.CONTRIBUTION }
            viewModel.editContribution(withdrawal.id)
            advanceUntilIdle()
            val correction = viewModel.state.value.contributionForm!!
            assertEquals(ContributionDirection.OUT, correction.direction)
            viewModel.onContributionFormChanged(correction.copy(amount = "25"))
            viewModel.onContributionSaveClicked()
            advanceUntilIdle()

            assertEquals(7_500L, store.accounts.getActive("shared")!!.currentBalanceCents)
            assertEquals(ContributionDirection.OUT, store.accounts.getContribution(withdrawal.id)!!.direction)
        }
    }

    private fun accountDraft(id: String, displayOrder: Long): AccountDraft =
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

    @Test
    fun aRequestedAddFormOpensAfterTheFirstLoadWithRealDefaults() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("personal", displayOrder = 0), createdAt = "2026-01-01T00:00:00Z")
            val viewModel = viewModel(store)

            viewModel.onAddRequested()
            assertNull(viewModel.state.value.form)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val form = viewModel.state.value.form!!
            assertEquals(1L, form.displayOrder)
            assertEquals(false, form.isDefault)

            // The request is consumed: later reloads leave a dismissed form closed.
            viewModel.onFormDismissed()
            viewModel.onScreenShown()
            advanceUntilIdle()
            assertNull(viewModel.state.value.form)
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
