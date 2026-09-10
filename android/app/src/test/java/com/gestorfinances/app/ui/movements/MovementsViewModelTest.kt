package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.data.repository.ContributionDraft
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.data.repository.ExpenseFunding
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountMemberDraft
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.ExternalSplitDraft
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TagDraft
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateSplitConfigLine
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TripDraft
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
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
    fun aContributionCanBeDeletedAndRestoredFromItsDetail() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("alba"), NOW)
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("joint", "alba"), createdAt = NOW)
            store.accounts.createContribution(
                ContributionDraft("c1", "joint", ContributionDirection.IN, SplitParticipantKind.USER, null, "checking", 2_000, "2026-01-01", null, null),
                NOW,
            )
            val viewModel = viewModel(store)
            val contribution = store.movements.getActive("c1")!!

            viewModel.onDetailClicked(contribution)
            viewModel.onArchiveClicked(contribution)
            advanceUntilIdle()
            var undo: (() -> Unit)? = null
            viewModel.onArchiveConfirmed(revertDueDate = false) { undo = it }
            advanceUntilIdle()

            assertNull(store.movements.getActive("c1"))
            assertEquals(0L, store.accounts.getActive("joint")!!.currentBalanceCents)

            // Undo brings it back, balance and all.
            requireNotNull(undo).invoke()
            advanceUntilIdle()
            assertTrue(store.movements.getActive("c1") != null)
            assertEquals(2_000L, store.accounts.getActive("joint")!!.currentBalanceCents)
        }
    }

    @Test
    fun incomeIntoASharedAccountMustSayWhoseItIs() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("alba"), NOW)
            store.accounts.create(sharedAccountDraft("joint", "alba"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked(tripId = null, accountId = "joint")
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(type = MovementType.INCOME, amount = "60", date = "2026-01-01"),
            )
            // The expense form's split does not follow the form into an income.
            assertNull(viewModel.form().expenseKind)
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_income_owner, viewModel.form().errorRes)
            assertEquals(MovementFormField.INCOME_OWNER, viewModel.form().errorField)
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    @Test
    fun sharedIncomeCountsOnlyTheOwnersPartAndCreatesNoDebt() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("alba"), NOW)
            store.accounts.create(sharedAccountDraft("joint", "alba"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked(tripId = null, accountId = "joint")
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(type = MovementType.INCOME, amount = "60", date = "2026-01-01"),
            )

            viewModel.onSharedToggled(true)
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val income = store.movements.listActive().single()
            val lines = store.splits.getForMovement(income.id)!!.lines
            assertEquals(3_000L, lines.single { it.participantKind == SplitParticipantKind.USER }.owedAmountCents)
            assertEquals(6_000L, store.accounts.getActive("joint")!!.currentBalanceCents)
            assertEquals(0L, store.people.getActive("alba")!!.balanceCents)

            // Reopened, it still says the income is shared.
            viewModel.onEditClicked(income)
            advanceUntilIdle()
            assertEquals(ExpenseKind.SHARED, viewModel.form().expenseKind)
        }
    }

    @Test
    fun expenseOnASharedAccountIsFundedByItAndCreatesNoDebt() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("alba"), NOW)
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("joint", "alba"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked(tripId = null, accountId = "joint")
            advanceUntilIdle()

            // Opened from the account's page, the form is on that account and already splits by
            // its default allocation.
            assertEquals("joint", viewModel.form().accountId)
            assertEquals(ExpenseKind.SHARED, viewModel.form().expenseKind)
            viewModel.onFormChanged(
                viewModel.form().copy(type = MovementType.EXPENSE, amount = "60", date = "2026-01-01"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val expense = store.movements.listActive().single()
            assertEquals(ExpenseFunding.SHARED_ACCOUNT, expense.financingKind)
            assertEquals(3_000L, expense.userShareCents)
            assertEquals(-6_000L, store.accounts.getActive("joint")!!.currentBalanceCents)
            assertEquals(0L, store.people.getActive("alba")!!.balanceCents)
        }
    }

    @Test
    fun transferBetweenTwoSharedAccountsIsAnOrdinaryTransfer() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("alba"), NOW)
            store.accounts.create(sharedAccountDraft("joint", "alba"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("holiday", "alba"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "25",
                    date = "2026-01-01",
                    accountId = "joint",
                    destinationAccountId = "holiday",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            assertEquals(-2_500L, store.accounts.getActive("joint")!!.currentBalanceCents)
            assertEquals(2_500L, store.accounts.getActive("holiday")!!.currentBalanceCents)
        }
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
            assertEquals(MovementFormField.AMOUNT, viewModel.form().errorField)
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    @Test
    fun blankAmountIsRejectedOnAmountField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(amount = "", date = "2026-01-01", accountId = "checking"),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_amount_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.AMOUNT, viewModel.form().errorField)
        }
    }

    @Test
    fun blankDateIsRejectedOnDateField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(amount = "10", date = "", accountId = "checking"),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_date_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.DATE, viewModel.form().errorField)
        }
    }

    @Test
    fun invalidDateIsRejectedOnDateField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(amount = "10", date = "not-a-date", accountId = "checking"),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_date_invalid, viewModel.form().errorRes)
            assertEquals(MovementFormField.DATE, viewModel.form().errorField)
        }
    }

    @Test
    fun missingAccountIsRejectedOnAccountField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(amount = "10", date = "2026-01-01", accountId = null),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_account_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.ACCOUNT, viewModel.form().errorField)
        }
    }

    @Test
    fun missingCategoryTagOnATripIsRejectedOnTagField() = runTest(dispatcher) {
        // A tag/trip mismatch can only reach attemptSave's validation via a direct edit-load
        // (MovementSummary.toFormState skips normalizeForm's compatibility nulling) -- any
        // onFormChanged call re-normalizes and nulls an incompatible tag before save. This
        // simulates a movement tagged while its trip was active, then the trip got archived.
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.tags.create(TagDraft("beach", "Platja", null, null, "mallorca"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 1_000, name = "Sopar").copy(
                    tripId = "mallorca",
                    tagId = "beach",
                ),
                createdAt = NOW,
            )
            store.trips.archive("mallorca", archivedAt = NOW)

            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val existing = store.movements.getActive("exp")!!
            viewModel.onEditClicked(existing)
            advanceUntilIdle()

            assertEquals("beach", viewModel.form().tagId)
            viewModel.onSaveClicked()

            assertEquals(R.string.tag_validation_trip_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.TAG, viewModel.form().errorField)
            assertEquals(true, viewModel.form().showOptional)
        }
    }

    @Test
    fun forOtherWithoutASelectedPersonIsRejectedOnPersonField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    expenseKind = ExpenseKind.FOR_OTHER,
                    forOtherPersonId = null,
                ),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.settlement_validation_person_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.PERSON, viewModel.form().errorField)
        }
    }

    @Test
    fun debtWithoutASelectedPayerIsRejectedOnPersonField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    expenseKind = ExpenseKind.DEBT,
                    forOtherPersonId = null,
                ),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.settlement_validation_person_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.PERSON, viewModel.form().errorField)
        }
    }

    @Test
    fun settlementWithoutASelectedPersonIsRejectedOnPersonField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.INCOME,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    isSettlement = true,
                    settlementPersonId = null,
                ),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.settlement_validation_person_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.PERSON, viewModel.form().errorField)
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
            assertEquals(MovementFormField.DESTINATION_ACCOUNT, viewModel.form().errorField)
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    @Test
    fun transferMissingDestinationIsRejectedOnDestinationField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    destinationAccountId = null,
                ),
            )
            viewModel.onSaveClicked()

            assertEquals(
                R.string.movement_validation_destination_required,
                viewModel.form().errorRes,
            )
            assertEquals(MovementFormField.DESTINATION_ACCOUNT, viewModel.form().errorField)
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
    fun onWarningDismissedClearsTheWarningWithoutSaving() = runTest(dispatcher) {
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

            viewModel.onWarningDismissed()
            advanceUntilIdle()

            assertFalse("Revisa clears the warning", viewModel.form().duplicateWarning)
            assertNull(viewModel.form().pendingDataLossWarning)
            assertEquals("nothing was saved", 1, store.movements.listActive().size)
            assertEquals("the in-progress edit is preserved", "2,50", viewModel.form().amount)
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
    fun incompatibleCategoryIsRejectedOnCategoryField() = runTest(dispatcher) {
        // Like the tag/trip case above, a category incompatible with the movement's type can only
        // reach attemptSave's validation via a direct edit-load -- normalizeForm nulls an
        // incompatible categoryId on any onFormChanged call before save. This simulates a category
        // that was reassigned from EXPENSE to INCOME-only after the movement was already tagged
        // with it.
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.categories.create(
                CategoryDraft(
                    id = "salary",
                    name = "Sou",
                    kind = CategoryKind.INCOME,
                    nature = CategoryNature.VARIABLE,
                    parentId = null,
                    icon = null,
                    color = null,
                    displayOrder = 0,
                ),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(id = "exp", amountCents = 1_000, name = "Sopar").copy(categoryId = "salary"),
                createdAt = NOW,
            )

            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val existing = store.movements.getActive("exp")!!
            viewModel.onEditClicked(existing)
            advanceUntilIdle()

            assertEquals("salary", viewModel.form().categoryId)
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_category_invalid, viewModel.form().errorRes)
            assertEquals(MovementFormField.CATEGORY, viewModel.form().errorField)
        }
    }

    // A transfer can never cross the personal/shared ownership line. The error must land on the
    // side that is actually shared, since that is the field the user has to change.
    @Test
    fun transferIntoASharedAccountBlamesTheDestinationField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("common", "laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    destinationAccountId = "common",
                ),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_shared_transfer, viewModel.form().errorRes)
            assertEquals(MovementFormField.DESTINATION_ACCOUNT, viewModel.form().errorField)
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    @Test
    fun transferOutOfASharedAccountBlamesTheOriginField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("common", "laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "common",
                    destinationAccountId = "checking",
                ),
            )
            viewModel.onSaveClicked()

            assertEquals(R.string.movement_validation_shared_transfer, viewModel.form().errorRes)
            assertEquals(MovementFormField.ACCOUNT, viewModel.form().errorField)
            assertTrue(store.movements.listActive().isEmpty())
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
                    expenseKind = ExpenseKind.SHARED,
                    splitEditor = splitEditor,
                ),
            )

            viewModel.onSaveClicked()

            assertEquals(R.string.split_validation_reconcile, viewModel.form().errorRes)
            assertEquals(MovementFormField.SPLIT, viewModel.form().errorField)
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
                    expenseKind = ExpenseKind.SHARED,
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

    // Regression (data-loss protection): switching the top-level movement type away from EXPENSE
    // and back used to null `splitEditor` unconditionally in `normalizeForm`, silently destroying
    // the split. The split must survive the round trip and still save correctly.
    @Test
    fun sharedExpenseSurvivesARoundTripThroughTransferTypeAndStillSavesItsSplit() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 1_000, name = "Sopar").copy(
                    splitWrite = MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EQUAL,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = 500L),
                                SplitLineDraft(SplitParticipantKind.PERSON, personId = "laura", owedAmountCents = 500L),
                            ),
                        ),
                    ),
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val existing = store.movements.getActive("exp")!!
            viewModel.onEditClicked(existing)
            advanceUntilIdle()
            assertTrue(viewModel.form().existingSplit)
            assertEquals(ExpenseKind.SHARED, viewModel.form().expenseKind)

            // Switch to TRANSFER (top-level MovementTypeSelector) and back to EXPENSE.
            viewModel.onFormChanged(viewModel.form().copy(type = MovementType.TRANSFER))
            viewModel.onFormChanged(viewModel.form().copy(type = MovementType.EXPENSE))

            // The split must never have been nulled/latched for removal along the way.
            assertEquals(ExpenseKind.SHARED, viewModel.form().expenseKind)
            assertEquals(setOf("laura"), viewModel.form().splitEditor?.selectedPersonIds?.toSet())

            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull("no data-loss warning should fire -- the split was never actually removed", viewModel.state.value.form?.pendingDataLossWarning)
            assertNull(viewModel.state.value.form)
            val movement = store.movements.getActive("exp")!!
            assertTrue("split must be preserved, never turned into Remove", movement.isShared)
            assertEquals(500L, store.people.getActive("laura")!!.balanceCents)
        }
    }

    // Regression (data-loss protection): explicitly turning off sharing on an existing shared
    // expense must warn before removing the stored split (never block, never silently proceed).
    @Test
    fun explicitlyUnsharingAnExistingSplitWarnsThenRemovesItOnAccept() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 1_000, name = "Sopar").copy(
                    splitWrite = MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EQUAL,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = 500L),
                                SplitLineDraft(SplitParticipantKind.PERSON, personId = "laura", owedAmountCents = 500L),
                            ),
                        ),
                    ),
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val existing = store.movements.getActive("exp")!!
            viewModel.onEditClicked(existing)
            advanceUntilIdle()
            assertTrue(viewModel.form().existingSplit)

            // Explicit un-share via the sharing toggle.
            viewModel.onSharedToggled(false)
            viewModel.onSaveClicked()
            advanceUntilIdle()

            // First attempt only warns -- the split must still be there.
            assertEquals(DataLossWarning.SPLIT_REMOVED, viewModel.form().pendingDataLossWarning)
            assertTrue(store.movements.getActive("exp")!!.isShared)

            viewModel.onDataLossOverrideClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val movement = store.movements.getActive("exp")!!
            assertFalse("split must be removed after accepting the warning", movement.isShared)
            assertEquals(0L, store.people.getActive("laura")!!.balanceCents)
        }
    }

    // Regression: a new shared+recurring expense's template must persist split_config so future
    // confirmed occurrences carry the split forward (RecurringViewModel.toSplitWrite reads it).
    // createQuickTemplate previously hardcoded splitConfig = null, so every quick-created template
    // silently lost its split and every subsequent occurrence was confirmed as a plain expense.
    @Test
    fun sharedAndRecurringExpenseCarriesSplitConfigIntoTheQuickCreatedTemplate() = runTest(dispatcher) {
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
                    expenseKind = ExpenseKind.SHARED,
                    splitEditor = SplitEditorState().withPersonToggled("laura"),
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId) { "movement must link to the quick-created template" }
            val splitConfig = store.templates.getActive(templateId)!!.splitConfig
            assertTrue("template.split_config must be persisted, not null", splitConfig != null)
            assertEquals(1_000L, splitConfig!!.lines.sumOf { it.owedAmountCents })
            assertEquals(
                setOf(
                    TemplateSplitConfigLine(party = "user", owedAmountCents = 500L),
                    TemplateSplitConfigLine(party = "laura", owedAmountCents = 500L),
                ),
                splitConfig.lines.toSet(),
            )
        }
    }

    // Regression: toggling recurring ON for an EDIT of an already-shared movement, when the split
    // itself isn't being touched (splitWrite resolves to KeepExisting for the movement's own
    // write), must still read the movement's actual split so the newly-created template gets one
    // too. Previously this fell through to splitConfig = null just like the create case above.
    @Test
    fun togglingRecurringOnAnExistingSharedMovementStillCarriesItsSplitIntoTheNewTemplate() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 1_000, name = "Sopar").copy(
                    splitWrite = MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EQUAL,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = 500L),
                                SplitLineDraft(SplitParticipantKind.PERSON, personId = "laura", owedAmountCents = 500L),
                            ),
                        ),
                    ),
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val existing = store.movements.getActive("exp")!!
            viewModel.onEditClicked(existing)
            advanceUntilIdle()

            // Simulate the "unchanged split" path: splitEditor stays null (the split itself isn't
            // being edited), only isRecurring flips on for the first time.
            viewModel.onFormChanged(
                viewModel.form().copy(
                    splitEditor = null,
                    existingSplit = true,
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.getActive("exp")!!
            assertTrue("edited movement must remain shared", movement.isShared)
            val templateId = requireNotNull(movement.templateId) { "movement must link to the quick-created template" }
            val splitConfig = store.templates.getActive(templateId)!!.splitConfig
            assertTrue("template.split_config must carry the existing split, not null", splitConfig != null)
            assertEquals(
                setOf(
                    TemplateSplitConfigLine(party = "user", owedAmountCents = 500L),
                    TemplateSplitConfigLine(party = "laura", owedAmountCents = 500L),
                ),
                splitConfig!!.lines.toSet(),
            )
        }
    }

    // Regression: editing a movement linked to a template previously always showed
    // MONTHLY (`toFormState` never read the actual template) regardless of the template's real
    // frequency, and never surfaced `templateId`/`templateStatus` for the FOR_OTHER kind at all --
    // the form silently misrepresented the movement's recurrence.
    @Test
    fun editingAMovementLinkedToAWeeklyTemplateLoadsTheFormWithItsRealFrequency() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Gimnàs",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)

            viewModel.onEditClicked(movement)
            advanceUntilIdle()

            assertEquals(RecurrenceFrequency.WEEKLY, viewModel.form().recurringFrequency)
            assertEquals(templateId, viewModel.form().templateId)
            assertEquals(TemplateStatus.ACTIVE, viewModel.form().templateStatus)
        }
    }

    // Same F12 bug, FOR_OTHER branch: toFormState previously dropped isRecurring/templateId
    // entirely for FOR_OTHER expenses even though createQuickTemplate supports that kind.
    @Test
    fun editingARecurringForOtherExpenseLoadsItsRealFrequencyToo() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(PersonDraft("anna", "Anna", null, null, null), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    name = "Entrades",
                    expenseKind = ExpenseKind.FOR_OTHER,
                    forOtherPersonId = "anna",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)

            viewModel.onEditClicked(movement)
            advanceUntilIdle()

            assertEquals(RecurrenceFrequency.WEEKLY, viewModel.form().recurringFrequency)
            assertEquals(templateId, viewModel.form().templateId)
            assertEquals(TemplateStatus.ACTIVE, viewModel.form().templateStatus)
        }
    }

    // Regression: toggling recurrence off on a movement still linked to a template
    // must warn (never silently unlink or silently keep the template alive) and, on the "end"
    // choice, end the template and unlink this movement atomically in the same save.
    @Test
    fun togglingRecurrenceOffAndChoosingEndEndsTheTemplateAndUnlinksTheMovementAtomically() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Gimnàs",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val created = store.movements.listActive().single()
            val templateId = requireNotNull(created.templateId)

            viewModel.onEditClicked(created)
            advanceUntilIdle()
            viewModel.onFormChanged(viewModel.form().copy(isRecurring = false))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            // First attempt only warns -- nothing is written yet.
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)
            assertEquals(TemplateStatus.ACTIVE, store.templates.getActive(templateId)!!.status)

            viewModel.onRecurrenceStopEndClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            assertEquals(TemplateStatus.ENDED, store.templates.getActive(templateId)!!.status)
            assertNull(
                "movement must be unlinked once its template is ended",
                store.movements.getActive(created.id)!!.templateId,
            )
        }
    }

    // Same toggle-off warning, but choosing "only unlink this movement" must leave the template
    // running for future occurrences while still detaching this one.
    @Test
    fun togglingRecurrenceOffAndChoosingUnlinkKeepsTheTemplateActive() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Gimnàs",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val created = store.movements.listActive().single()
            val templateId = requireNotNull(created.templateId)

            viewModel.onEditClicked(created)
            advanceUntilIdle()
            viewModel.onFormChanged(viewModel.form().copy(isRecurring = false))
            viewModel.onSaveClicked()
            advanceUntilIdle()
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)

            viewModel.onRecurrenceStopUnlinkClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            assertEquals(
                "unlinking must not touch the template's status",
                TemplateStatus.ACTIVE,
                store.templates.getActive(templateId)!!.status,
            )
            assertNull(
                "movement must be detached from the template",
                store.movements.getActive(created.id)!!.templateId,
            )
        }
    }

    // Regression: createQuickTemplate hardcoded notes = null, so a quick-created recurring
    // template silently dropped whatever the user typed in the form's notes field.
    @Test
    fun quickCreateViaTheFormCarriesFormNotesIntoTheCreatedTemplate() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(tripDraft("trip"), createdAt = NOW)
            store.tags.create(TagDraft("tag", "Etiqueta", null, null, "trip"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    accountId = "checking",
                    tripId = "trip",
                    tagId = "tag",
                    name = "Gimnàs",
                    notes = "Paga religiosament",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)
            val template = store.templates.getActive(templateId)!!
            assertEquals("Paga religiosament", template.notes)
            assertEquals("trip", template.tripId)
            assertEquals("tag", template.tagId)
        }
    }

    // Deleting a movement never touches its template's due date, except in one provably-safe
    // case: the movement is exactly the occurrence immediately preceding the template's current
    // next-due-date (no skip since). Only then does the archive dialog offer to roll it back.
    @Test
    fun archivingTheImmediatePriorOccurrenceOffersToRevertTheDueDate() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()
            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)
            assertEquals("2026-02-05", store.templates.getActive(templateId)!!.nextDueDate)

            viewModel.onArchiveClicked(movement)
            advanceUntilIdle()

            val candidate = requireNotNull(viewModel.state.value.archiveCandidate)
            assertEquals(templateId, candidate.revertibleTemplateId)
        }
    }

    // Regression (spec-guardian): a paused/ended template isn't scanned for due-prompts, so
    // silently offering to rewind its due date would be confusing, not useful -- only ACTIVE
    // templates should ever offer the revert checkbox.
    @Test
    fun archivingTheImmediatePriorOccurrenceOfAPausedTemplateDoesNotOfferToRevert() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()
            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)
            store.templates.setStatus(templateId, TemplateStatus.PAUSED, updatedAt = NOW)

            viewModel.onArchiveClicked(movement)
            advanceUntilIdle()

            val candidate = requireNotNull(viewModel.state.value.archiveCandidate)
            assertNull(candidate.revertibleTemplateId)
        }
    }

    @Test
    fun confirmingArchiveWithRevertRollsTheDueDateBackToTheMovementsDate() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()
            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)

            viewModel.onArchiveClicked(movement)
            advanceUntilIdle()
            var undo: (() -> Unit)? = null
            viewModel.onArchiveConfirmed(revertDueDate = true) { undo = it }
            advanceUntilIdle()

            assertEquals("2026-01-05", store.templates.getActive(templateId)!!.nextDueDate)
            assertNull(store.movements.getActive(movement.id))

            requireNotNull(undo).invoke()
            advanceUntilIdle()

            assertEquals("2026-02-05", store.templates.getActive(templateId)!!.nextDueDate)
            assertEquals(movement.id, store.movements.getActive(movement.id)!!.id)
        }
    }

    @Test
    fun confirmingArchiveWithoutRevertLeavesTheDueDateAdvanced() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()
            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)

            viewModel.onArchiveClicked(movement)
            advanceUntilIdle()
            viewModel.onArchiveConfirmed()
            advanceUntilIdle()

            assertEquals("2026-02-05", store.templates.getActive(templateId)!!.nextDueDate)
        }
    }

    @Test
    fun archivingAnOlderLinkedMovementDoesNotOfferToRevertTheDueDate() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()
            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)
            // Simulate a second cycle having passed (e.g. February's occurrence was separately
            // confirmed) without touching this specific movement -- January is now two steps
            // behind the current cursor, not the immediate prior occurrence.
            store.templates.advanceCursor(templateId, "2026-03-05", updatedAt = NOW)

            viewModel.onArchiveClicked(movement)
            advanceUntilIdle()

            val candidate = requireNotNull(viewModel.state.value.archiveCandidate)
            assertNull(candidate.revertibleTemplateId)
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
                    expenseKind = ExpenseKind.FOR_OTHER,
                    forOtherPersonId = "laura",
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
    fun tripDefaultAccountPrecedesGlobalDefaultForNewMovement() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(accountDraft("cash", displayOrder = 1), createdAt = NOW)
            store.trips.create(
                TripDraft(
                    id = "mallorca",
                    name = "Mallorca",
                    type = TripType.TRIP,
                    status = TripStatus.ACTIVE,
                    startDate = null,
                    endDate = null,
                    icon = null,
                    color = null,
                    notes = null,
                    defaultAccountId = "cash",
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)

            viewModel.onAddClicked("mallorca")
            advanceUntilIdle()

            assertEquals("mallorca", viewModel.form().tripId)
            assertEquals("cash", viewModel.form().accountId)
            assertEquals(true, viewModel.form().showOptional)

            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "12",
                    date = "2026-01-01",
                    name = "Dinar",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertEquals("mallorca", movement.tripId)
            assertEquals("Mallorca", movement.tripName)
            assertEquals("cash", movement.accountId)
        }
    }

    @Test
    fun movementTagRequiresCompatibleTripAndPersists() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(tripDraft("mallorca"), createdAt = NOW)
            store.trips.create(tripDraft("lisboa"), createdAt = NOW)
            store.tags.create(TagDraft("food", "Menjar", null, null, null), createdAt = NOW)
            store.tags.create(TagDraft("beach", "Platja", null, null, "mallorca"), createdAt = NOW)
            val viewModel = viewModel(store)

            viewModel.onAddClicked("mallorca")
            advanceUntilIdle()
            viewModel.onTagSelected("beach")

            assertEquals("beach", viewModel.form().tagId)

            viewModel.onTripSelected("lisboa")

            assertNull(viewModel.form().tagId)

            viewModel.onTripSelected("mallorca")
            viewModel.onTagSelected("food")
            viewModel.onFormChanged(
                viewModel.form().copy(
                    amount = "8",
                    date = "2026-01-01",
                    name = "Esmorzar",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertEquals("mallorca", movement.tripId)
            assertEquals("food", movement.tagId)
            assertEquals("Menjar", movement.tagName)
        }
    }

    @Test
    fun addClickedWithDebtPayerPrefillsDebtFormEvenWithoutAccounts() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("laura"), createdAt = NOW)
            val viewModel = viewModel(store)

            viewModel.onAddClicked(tripId = null, debtPayerPersonId = "laura")
            advanceUntilIdle()

            val form = viewModel.form()
            assertEquals(ExpenseKind.DEBT, form.expenseKind)
            assertEquals("laura", form.forOtherPersonId)
        }
    }

    @Test
    fun addClickedWithoutDebtPayerStillRequiresAnAccount() = runTest(dispatcher) {
        freshStore().use { store ->
            // No account seeded, and no debt payer — a regular new movement still can't open.
            val viewModel = viewModel(store)

            viewModel.onAddClicked(tripId = null)
            advanceUntilIdle()

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

    @Test
    fun deletingAnExpenseAndUndoingRestoresItsLinkedRefund() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 5_000, name = "Sabates"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val expense = store.movements.getActive("exp")!!
            viewModel.onDetailClicked(expense)
            advanceUntilIdle()
            viewModel.onAddRefundClicked(expense)
            val refundForm = viewModel.state.value.refundForm!!
            viewModel.onRefundFormChanged(refundForm.copy(amount = "20", date = "2026-01-02"))
            viewModel.onRefundSaveClicked()
            advanceUntilIdle()
            val refundId = store.movements.refundsForExpense("exp").single().id

            viewModel.onArchiveClicked(store.movements.getActive("exp")!!)
            advanceUntilIdle()
            var undo: (() -> Unit)? = null
            viewModel.onArchiveConfirmed(revertDueDate = false) { undo = it }
            advanceUntilIdle()

            assertNull(store.movements.getActive("exp"))
            assertNull(store.movements.getActive(refundId))

            requireNotNull(undo).invoke()
            advanceUntilIdle()

            assertEquals("exp", store.movements.getActive("exp")!!.id)
            assertEquals(refundId, store.movements.getActive(refundId)!!.id)
        }
    }

    @Test
    fun refundAmountRequiredIsRejectedOnAmountField() = runTest(dispatcher) {
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
            viewModel.onAddRefundClicked(expense)

            val form = viewModel.state.value.refundForm!!
            viewModel.onRefundFormChanged(form.copy(amount = "", date = "2026-01-02"))
            viewModel.onRefundSaveClicked()

            val updated = viewModel.state.value.refundForm!!
            assertEquals(R.string.refund_validation_amount_required, updated.errorRes)
            assertEquals(RefundFormField.AMOUNT, updated.errorField)
        }
    }

    @Test
    fun refundActualOverAmountIsRejectedOnActualAmountField() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 5_000, name = "Sabates").copy(
                    splitWrite = MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EQUAL,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = 2_500L),
                                SplitLineDraft(SplitParticipantKind.PERSON, personId = "laura", owedAmountCents = 2_500L),
                            ),
                        ),
                    ),
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()
            val expense = store.movements.listActive().single { it.id == "exp" }
            viewModel.onAddRefundClicked(expense)

            val form = viewModel.state.value.refundForm!!
            assertTrue(form.expenseIsShared)
            viewModel.onRefundFormChanged(form.copy(amount = "50", actualAmount = "60", date = "2026-01-02"))
            viewModel.onRefundSaveClicked()

            val updated = viewModel.state.value.refundForm!!
            assertEquals(R.string.refund_validation_actual_over, updated.errorRes)
            assertEquals(RefundFormField.ACTUAL_AMOUNT, updated.errorField)
        }
    }

    // Data-loss regression: MovementSummary.id is splits.id for an EXTERNAL_EXPENSE (DEBT,
    // "Un altre ha pagat") but movements.id for everything else. Editing an existing DEBT expense
    // and switching "Qui ha pagat?" to "Jo" must archive the old split and create a real movement
    // -- not silently no-op an UPDATE against a movements row that never existed.
    @Test
    fun editingDebtExpenseAndSwitchingPayerToSelfArchivesTheSplitAndCreatesAMovement() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.splits.createExternalPaidByPerson(
                ExternalSplitDraft(
                    id = "ext1",
                    payerPersonId = "laura",
                    totalAmountCents = 1_000,
                    userShareCents = 1_000,
                    date = "2026-01-01",
                    description = "Sopar",
                    categoryId = null,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val summary = store.movements.listActive().single()
            assertEquals(MovementType.EXTERNAL_EXPENSE, summary.type)
            viewModel.onEditClicked(summary)
            advanceUntilIdle()

            assertEquals("ext1", viewModel.form().externalSplitId)
            assertNull(viewModel.form().movementId)

            // Switch "Qui ha pagat?" from "Un altre" (laura) to "Jo".
            viewModel.onFormChanged(
                viewModel.form().copy(expenseKind = ExpenseKind.PERSONAL, accountId = "checking"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull("the original external split must be archived", store.movements.getActive("ext1"))
            val active = store.movements.listActive()
            assertEquals(1, active.size)
            assertEquals(MovementType.EXPENSE, active.single().type)
            assertEquals(1_000L, active.single().amountCents)
            assertEquals(0L, store.people.getActive("laura")!!.balanceCents)
            assertNull(viewModel.state.value.form)
        }
    }

    // Regression (data-loss regression): the reverse direction. Editing an existing
    // regular movement and switching "Qui ha pagat?" to "Un altre" must archive the old movements
    // row and create a real external split -- not silently insert a brand-new split while leaving
    // the original movement live, which would double-count the expense (balances/account flow
    // corruption). Crossing that boundary also drops fields `splits` can't carry (payee/notes/
    // recurrence), so the first save attempt must warn instead of saving; only the accepted retry
    // performs the archive+create.
    @Test
    fun editingRegularExpenseAndSwitchingPayerToSomeoneElseWarnsThenArchivesTheMovementAndCreatesASplit() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 1_000, name = "Entrades"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val existing = store.movements.getActive("exp")!!
            viewModel.onEditClicked(existing)
            advanceUntilIdle()

            assertEquals("exp", viewModel.form().movementId)
            assertNull(viewModel.form().externalSplitId)

            // Switch "Qui ha pagat?" from "Jo" to "Un altre" (laura).
            viewModel.onFormChanged(
                viewModel.form().copy(expenseKind = ExpenseKind.DEBT, forOtherPersonId = "laura"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            // First attempt only warns -- no write happens yet.
            assertEquals(DataLossWarning.PAYER_SWITCH, viewModel.form().pendingDataLossWarning)
            assertEquals("exp", store.movements.getActive("exp")?.id)
            assertEquals(0L, store.people.getActive("laura")!!.balanceCents)

            viewModel.onDataLossOverrideClicked()
            advanceUntilIdle()

            assertNull("the original movement must be archived", store.movements.getActive("exp"))
            val active = store.movements.listActive()
            assertEquals(1, active.size)
            assertEquals(MovementType.EXTERNAL_EXPENSE, active.single().type)
            assertEquals(1_000L, active.single().amountCents)
            assertEquals(-1_000L, store.people.getActive("laura")!!.balanceCents)
            assertNull(viewModel.state.value.form)
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
            templateRepository = store.templates,
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
            analysis = AnalysisRepository(database.analysisQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            people = PersonRepository(database.peopleQueries),
            tags = TagRepository(database.tagsQueries),
            trips = TripRepository(database.tripsQueries),
            templates = TemplateRepository(database.templatesQueries),
            splits = SplitRepository(database.splitsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val analysis: AnalysisRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val tags: TagRepository,
        val trips: TripRepository,
        val templates: TemplateRepository,
        val splits: SplitRepository,
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

    /** A 50/50 shared account between the app owner and [personId]. */
    private fun sharedAccountDraft(id: String, personId: String): AccountDraft =
        accountDraft(id, displayOrder = 9).copy(
            ownershipKind = AccountOwnershipKind.SHARED,
            members = listOf(
                AccountMemberDraft(SplitParticipantKind.USER, null, 5_000L, 5_000L),
                AccountMemberDraft(SplitParticipantKind.PERSON, personId, 5_000L, 5_000L),
            ),
        )

    private fun personDraft(id: String): PersonDraft =
        PersonDraft(
            id = id,
            name = id,
            avatar = null,
            color = null,
            notes = null,
        )

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
