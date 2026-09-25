package com.gestorfinances.app.ui.people

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.personPaidExpense
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonBalanceItemType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.SplitRepository
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
class PeopleViewModelTest {
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
    fun personNameIsRequired() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onAddClicked()

            viewModel.onSaveClicked()

            assertEquals(R.string.person_validation_name_required, viewModel.state.value.form!!.errorRes)
            assertEquals(PersonFormField.NAME, viewModel.state.value.form!!.errorField)
            assertTrue(store.people.listActive().isEmpty())
        }
    }

    @Test
    fun createAndUpdatePersonRefreshesList() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "Laura", notes = "Sopars"))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val created = viewModel.state.value.people.single()
            assertEquals("Laura", created.name)
            assertEquals("Sopars", created.notes)

            viewModel.onEditClicked(created)
            viewModel.onFormChanged(viewModel.state.value.form!!.copy(name = "Laura M.", notes = ""))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val updated = viewModel.state.value.people.single()
            assertEquals("Laura M.", updated.name)
            assertNull(updated.notes)
        }
    }

    @Test
    fun nonZeroArchiveShowsWarningCandidateAndOverrideArchives() = runTest(dispatcher) {
        freshStore().use { store ->
            seedUserFrontedSplit(store)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val person = viewModel.state.value.people.single()
            viewModel.onArchiveClicked(person)

            assertEquals(600L, viewModel.state.value.archiveCandidate!!.balanceCents)

            viewModel.onArchiveConfirmed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.archiveCandidate)
            assertTrue(viewModel.state.value.people.isEmpty())
        }
    }

    @Test
    fun personDetailLoadsBalanceBreakdown() = runTest(dispatcher) {
        freshStore().use { store ->
            seedUserFrontedSplit(store)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onPersonDetailClicked(viewModel.state.value.people.single())
            advanceUntilIdle()

            val detail = viewModel.state.value.detail!!
            assertEquals("Laura", detail.person.name)
            assertEquals(600L, detail.person.balanceCents)
            assertEquals(false, detail.isLoading)
            assertNull(detail.errorMessage)
            assertEquals(listOf(PersonBalanceItemType.USER_PAID), detail.items.map { it.type })
            assertEquals(listOf(600L), detail.items.map { it.effectCents })
            assertEquals(1, detail.history.size)
            assertEquals("dinner", detail.history.single().movement.id)
        }
    }

    @Test
    fun personDetailHistoryResolvesExternalSplitAsSyntheticExternalExpense() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.movements.create(
                personPaidExpense(
                    id = "ext",
                    payerPersonId = "laura",
                    amountCents = 600,
                    date = "2026-01-01",
                    name = "Taxi",
                    categoryId = null,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onPersonDetailClicked(viewModel.state.value.people.single())
            advanceUntilIdle()

            val detail = viewModel.state.value.detail!!
            assertEquals(listOf(PersonBalanceItemType.PERSON_PAID), detail.items.map { it.type })
            assertEquals(listOf(-600L), detail.items.map { it.effectCents })
            assertEquals(1, detail.history.size)
            val entry = detail.history.single()
            assertEquals("ext", entry.movement.id)
            assertEquals(MovementType.EXPENSE, entry.movement.type)
            assertTrue(entry.movement.paidByPerson)
            assertEquals(-600L, entry.item.effectCents)
        }
    }

    @Test
    fun settleUpInfersDirectionPrefillsFullOutstandingAndReducesBalance() = runTest(dispatcher) {
        freshStore().use { store ->
            seedUserFrontedSplit(store)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val person = viewModel.state.value.people.single()
            assertEquals(600L, person.balanceCents)

            viewModel.onSettleUpClicked(person)
            val form = viewModel.state.value.settlementForm!!
            assertEquals(SettlementDirection.PERSON_TO_USER, form.direction)
            assertEquals(600L, form.outstandingCents)
            assertEquals("6,00", form.amount)
            assertEquals("checking", form.accountId)

            viewModel.onSettlementSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.settlementForm)
            assertEquals(0L, viewModel.state.value.people.single().balanceCents)
        }
    }

    @Test
    fun partialSettlementLeavesRemainingBalance() = runTest(dispatcher) {
        freshStore().use { store ->
            seedUserFrontedSplit(store)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onSettleUpClicked(viewModel.state.value.people.single())
            viewModel.onSettlementFormChanged(
                viewModel.state.value.settlementForm!!.copy(amount = "2"),
            )
            viewModel.onSettlementSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.settlementForm)
            assertEquals(400L, viewModel.state.value.people.single().balanceCents)
        }
    }

    @Test
    fun copyMessageClickedPopulatesDetailMessageForSeededScenario() = runTest(dispatcher) {
        freshStore().use { store ->
            seedUserFrontedSplit(store)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onPersonDetailClicked(viewModel.state.value.people.single())
            advanceUntilIdle()

            viewModel.onCopyMessageClicked()

            val message = viewModel.state.value.detail!!.copyMessage!!
            assertEquals(DebtMessageDirection.PERSON_OWES_USER, message.direction)
            assertEquals(1, message.residuals.size)
            assertEquals(600L, message.residuals.single().remainingCents)
            assertEquals(0L, message.creditAllCents)
            assertEquals(600L, message.totalCents)
        }
    }

    @Test
    fun saveClickedPersistsChosenColor() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Laura", color = "#3344E0"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val created = viewModel.state.value.people.single()
            assertEquals("#3344E0", created.color)
        }
    }

    @Test
    fun settlementRequiresAnAccount() = runTest(dispatcher) {
        freshStore().use { store ->
            // No account seeded; only a person with a debt via an external split.
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.movements.create(
                personPaidExpense(
                    id = "ext",
                    payerPersonId = "laura",
                    amountCents = 600,
                    date = "2026-01-01",
                    name = null,
                    categoryId = null,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val person = viewModel.state.value.people.single()
            assertEquals(-600L, person.balanceCents)
            viewModel.onSettleUpClicked(person)
            val form = viewModel.state.value.settlementForm!!
            assertEquals(SettlementDirection.USER_TO_PERSON, form.direction)
            assertNull(form.accountId)

            viewModel.onSettlementSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.settlement_validation_account_required,
                viewModel.state.value.settlementForm!!.errorRes,
            )
            assertEquals(SettlementFormField.ACCOUNT, viewModel.state.value.settlementForm!!.errorField)
        }
    }

    private fun viewModel(store: TestStore): PeopleViewModel =
        PeopleViewModel(
            personRepository = store.people,
            movementRepository = store.movements,
            accountRepository = store.accounts,
            ioDispatcher = dispatcher,
        )

    private fun seedUserFrontedSplit(store: TestStore) {
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
        store.people.create(
            PersonDraft(
                id = "laura",
                name = "Laura",
                avatar = null,
                color = null,
                notes = null,
            ),
            createdAt = NOW,
        )
        store.movements.create(
            MovementDraft(
                id = "dinner",
                type = MovementType.EXPENSE,
                amountCents = 1_000,
                date = "2026-01-01",
                accountId = "checking",
                destinationAccountId = null,
                categoryId = null,
                name = "Sopar",
                payee = null,
                notes = null,
                isOneTime = false,
            ),
            createdAt = NOW,
        )
        store.driver.execute(
            null,
            """
            INSERT INTO splits(id, movement_id, entry_method, created_at, updated_at, archived_at)
            VALUES ('split-dinner', 'dinner', 'exact', '$NOW', '$NOW', NULL)
            """.trimIndent(),
            0,
        )
        store.driver.execute(
            null,
            """
            INSERT INTO split_lines(
                id, split_id, participant_kind, person_id, owed_amount_cents,
                owed_percent, created_at, updated_at, archived_at
            ) VALUES (
                'split-dinner-user', 'split-dinner', 'user', NULL, 400,
                NULL, '$NOW', '$NOW', NULL
            )
            """.trimIndent(),
            0,
        )
        store.driver.execute(
            null,
            """
            INSERT INTO split_lines(
                id, split_id, participant_kind, person_id, owed_amount_cents,
                owed_percent, created_at, updated_at, archived_at
            ) VALUES (
                'split-dinner-laura', 'split-dinner', 'person', 'laura', 600,
                NULL, '$NOW', '$NOW', NULL
            )
            """.trimIndent(),
            0,
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
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            people = PersonRepository(database.peopleQueries),
            splits = SplitRepository(database.splitsQueries),
        )
    }

    private class TestStore(
        val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val splits: SplitRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
