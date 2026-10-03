package com.gestorfinances.app.ui.movements

import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_validation_account_required
import com.gestorfinances.ui.resources.movement_validation_amount_positive
import com.gestorfinances.ui.resources.movement_validation_amount_required
import com.gestorfinances.ui.resources.movement_validation_category_invalid
import com.gestorfinances.ui.resources.movement_validation_context_required
import com.gestorfinances.ui.resources.movement_validation_date_invalid
import com.gestorfinances.ui.resources.movement_validation_date_required
import com.gestorfinances.ui.resources.movement_validation_destination_required
import com.gestorfinances.ui.resources.movement_validation_income_owner
import com.gestorfinances.ui.resources.movement_validation_preserved_payer_amount
import com.gestorfinances.ui.resources.movement_validation_refunded_expense_type
import com.gestorfinances.ui.resources.movement_validation_shared_transfer
import com.gestorfinances.ui.resources.movement_validation_transfer_same_account
import com.gestorfinances.ui.resources.refund_validation_actual_over
import com.gestorfinances.ui.resources.refund_validation_amount_required
import com.gestorfinances.ui.resources.settlement_validation_person_required
import com.gestorfinances.ui.resources.split_validation_reconcile
import com.gestorfinances.ui.resources.tag_validation_trip_required
import com.gestorfinances.app.data.repository.ContributionDraft
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.data.repository.ExpenseFunding
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.FinancialDataRevision
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
import com.gestorfinances.app.data.repository.personPaidExpense
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.RefundDraft
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
    fun severalMovementsAreFiledAndDeletedTogetherAndAmountBoundsNarrowTheLedger() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.categories.create(
                CategoryDraft("food", "Menjar", CategoryKind.EXPENSE, CategoryNature.VARIABLE, null, null, null, 0),
                createdAt = NOW,
            )
            store.movements.create(movementDraft("small", 500, "Cafe"), createdAt = NOW)
            store.movements.create(movementDraft("middle", 2_000, "Dinar"), createdAt = NOW)
            store.movements.create(movementDraft("big", 9_000, "Compra"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onFiltersChanged(MovementFilters(amountMin = "10", amountMax = "50,00"))
            assertEquals(listOf("middle"), viewModel.state.value.visibleMovements.map { it.id })
            assertEquals(1, viewModel.state.value.filters.activeFilterCount)
            viewModel.onClearFiltersClicked()

            val all = viewModel.state.value.movements
            viewModel.onCategoryChangedForMany(all.filter { it.id != "big" }, MovementType.EXPENSE, "food")
            advanceUntilIdle()
            assertEquals(
                mapOf("small" to "food", "middle" to "food", "big" to null),
                viewModel.state.value.movements.associate { it.id to it.categoryId },
            )

            viewModel.onArchiveMany(viewModel.state.value.movements.filter { it.id != "middle" })
            advanceUntilIdle()
            assertEquals(listOf("middle"), viewModel.state.value.movements.map { it.id })
            assertEquals(listOf("middle"), store.movements.listActive().map { it.id })
        }
    }

    @Test
    fun contextualFiltersSurviveRefreshAndClearingPreservesSearchUntilGlobalNavigation() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(movementDraft("expense", 100, "Cafe"), createdAt = NOW)
            val viewModel = viewModel(store)
            val contexts = listOf(
                MovementFilters(accountId = "checking"),
                MovementFilters(categoryId = "food"),
                MovementFilters(tripId = "trip", tagId = "tag"),
                MovementFilters(sourceMode = MovementSourceMode.ACTUAL,
                    oneTimeMode = MovementOneTimeMode.EXCLUDE, dateFrom = "2026-01-01", dateTo = "2026-01-31"),
                MovementFilters(type = MovementType.SETTLEMENT),
                MovementFilters(paidByPersonOnly = true),
            )
            contexts.forEach { context ->
                viewModel.onDrillDown(context)
                advanceUntilIdle()
                viewModel.onScreenShown()
                advanceUntilIdle()
                assertEquals(context, viewModel.state.value.filters)
                viewModel.onFiltersChanged(context.copy(query = "Cafe"))
                viewModel.onClearFiltersClicked()
                assertEquals(MovementFilters(query = "Cafe"), viewModel.state.value.filters)
                assertEquals(listOf("expense"), viewModel.state.value.visibleMovements.map { it.id })
                viewModel.resetForMenuNavigation()
                assertEquals(MovementFilters(), viewModel.state.value.filters)
            }
        }
    }

    @Test
    fun newPersonalExpenseUsesSafeDefaultsAndSavesWithoutOpeningDetails() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(accountDraft("cash", displayOrder = 1), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            val form = viewModel.form()

            assertEquals(MovementType.EXPENSE, form.type)
            assertEquals("checking", form.accountId)
            assertEquals(java.time.LocalDate.now().toString(), form.date)
            assertEquals(ExpenseKind.PERSONAL, form.expenseKind)
            assertNull(form.tripId)
            assertNull(form.tagId)
            assertNull(form.splitEditor)
            assertNull(form.forOtherPersonId)
            assertFalse(form.isRecurring)
            assertFalse(form.showOptional)

            viewModel.editor.onFormChanged(form.copy(amount = "12", name = "Dinar"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            assertEquals("checking", store.movements.listActive().single().accountId)
            assertNull(viewModel.editor.form.value)
        }
    }

    @Test
    fun aMovementRecordedByHandIsRecognisedAsTheRecurringItemsPendingOccurrence() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val today = java.time.LocalDate.now()
            store.templates.create(
                com.gestorfinances.app.data.repository.TemplateDraft(
                    id = "rent",
                    type = MovementType.EXPENSE,
                    amountCents = 80_000,
                    accountId = "checking",
                    destAccountId = null,
                    categoryId = null,
                    name = "Lloguer",
                    payee = null,
                    notes = null,
                    splitConfig = null,
                    frequency = com.gestorfinances.app.domain.rules.RecurrenceFrequency.MONTHLY,
                    intervalCount = null,
                    customUnit = null,
                    dayOfMonth = today.minusDays(2).dayOfMonth.toLong(),
                    weekday = null,
                    nextDueDate = today.minusDays(2).toString(),
                    amountIsVariable = false,
                    amountFlexCents = null,
                    dateFlexDays = null,
                    leadNotificationDays = null,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            // Far from what it expects, it is not the rent.
            viewModel.editor.onFormChanged(viewModel.form().copy(amount = "20", name = "Lloguer"))
            assertNull(viewModel.form().recurringMatch)

            // Two days late and 5 % dearer, it is — offered ticked.
            viewModel.editor.onFormChanged(viewModel.form().copy(amount = "840"))
            assertEquals("rent", viewModel.form().recurringMatch?.templateId)
            assertTrue(viewModel.form().linkToRecurring)
            assertFalse(viewModel.form().updateRecurringAmount) // the price changes only when asked
            viewModel.editor.onFormChanged(viewModel.form().copy(updateRecurringAmount = true))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals("rent", store.movements.listActive().single().templateId)
            assertEquals(84_000L, store.templates.getActive("rent")!!.amountCents)
            assertEquals(
                com.gestorfinances.app.domain.rules.RecurringAdvancer.nextOccurrence(
                    com.gestorfinances.app.domain.rules.RecurrenceRule(
                        com.gestorfinances.app.domain.rules.RecurrenceFrequency.MONTHLY,
                        dayOfMonth = today.minusDays(2).dayOfMonth,
                    ),
                    today.minusDays(2),
                ).toString(),
                store.templates.getActive("rent")!!.nextDueDate,
            )
        }
    }

    @Test
    fun accountContextOverridesTripAndGlobalDefaultsWithoutLosingTripContext() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(accountDraft("cash", displayOrder = 1), createdAt = NOW)
            store.trips.create(tripDraft("trip").copy(defaultAccountId = "cash"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked(tripId = "trip", accountId = "checking")
            advanceUntilIdle()

            assertEquals("checking", viewModel.form().accountId)
            assertEquals("trip", viewModel.form().tripId)
            assertTrue(viewModel.form().showOptional)

            val activeAccounts = store.accounts.listActive().filter { it.id != "cash" }
            val fallback = newMovementForm(activeAccounts, store.trips.listActive(), "trip", presetAccountId = "missing")
            assertEquals("checking", fallback.accountId)
            assertEquals("trip", fallback.tripId)
        }
    }

    @Test
    fun collapsingDetailsWhileEditingPreservesSharedRecurringMetadata() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("anna"), createdAt = NOW)
            store.trips.create(tripDraft("trip"), createdAt = NOW)
            store.tags.create(TagDraft("tag", "Menjar", null, null, "trip"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked(tripId = "trip")
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "20", name = "Sopar", payee = "Restaurant", notes = "Terrassa",
                    tagId = "tag", isOneTime = true, isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                    expenseKind = ExpenseKind.SHARED,
                    splitEditor = SplitEditorState().withPersonToggled("anna"),
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            val original = store.movements.listActive().single()
            val originalSplit = store.splits.getForMovement(original.id)!!

            viewModel.onEditClicked(original)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(showOptional = false, name = "Sopar revisat"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            val saved = store.movements.getActive(original.id)!!
            assertEquals("Sopar revisat", saved.name)
            assertEquals(original.tripId, saved.tripId)
            assertEquals(original.tagId, saved.tagId)
            assertEquals(original.payee, saved.payee)
            assertEquals(original.notes, saved.notes)
            assertTrue(saved.isOneTime)
            assertEquals(original.templateId, saved.templateId)
            assertEquals(RecurrenceFrequency.WEEKLY, store.templates.getActive(saved.templateId!!)!!.frequency)
            assertEquals(originalSplit.lines, store.splits.getForMovement(saved.id)!!.lines)
            assertEquals(2_000L, saved.amountCents)
            assertNull(viewModel.editor.form.value)
        }
    }

    @Test
    fun categoryManagementRefreshKeepsTheUnsavedMovementAndLoadsNewPickerOptions() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(name = "Sopar", amount = "25,50", notes = "Esborrany"))
            val draft = viewModel.form()

            store.categories.create(
                CategoryDraft("food", "Menjar", CategoryKind.EXPENSE, CategoryNature.VARIABLE, null, null, null, 0),
                createdAt = NOW,
            )
            viewModel.onScreenShown()
            advanceUntilIdle()

            assertEquals(draft, viewModel.form())
            assertEquals(listOf("food"), viewModel.state.value.categories.map { it.id })
            assertTrue(store.movements.listActive().isEmpty())
            viewModel.editor.onFormChanged(draft.copy(categoryId = "food"))
            assertEquals("food", viewModel.form().categoryId)
        }
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
            var undo: (suspend () -> Unit)? = null
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(type = MovementType.INCOME, amount = "60", date = "2026-01-01"),
            )
            // The expense form's split does not follow the form into an income.
            assertNull(viewModel.form().expenseKind)
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_income_owner, viewModel.form().errorRes)
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(type = MovementType.INCOME, amount = "60", date = "2026-01-01"),
            )

            viewModel.editor.onSharedToggled(true)
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(type = MovementType.EXPENSE, amount = "60", date = "2026-01-01"),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "25",
                    date = "2026-01-01",
                    accountId = "joint",
                    destinationAccountId = "holiday",
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "0", date = "2026-01-01", accountId = "checking"),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_amount_positive, viewModel.form().errorRes)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "", date = "2026-01-01", accountId = "checking"),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_amount_required, viewModel.form().errorRes)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "10", date = "", accountId = "checking"),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_date_required, viewModel.form().errorRes)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "10", date = "not-a-date", accountId = "checking"),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_date_invalid, viewModel.form().errorRes)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "10", date = "2026-01-01", accountId = null),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_account_required, viewModel.form().errorRes)
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
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.tag_validation_trip_required, viewModel.form().errorRes)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    expenseKind = ExpenseKind.FOR_OTHER,
                    forOtherPersonId = null,
                ),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.settlement_validation_person_required, viewModel.form().errorRes)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    expenseKind = ExpenseKind.DEBT,
                    forOtherPersonId = null,
                ),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.settlement_validation_person_required, viewModel.form().errorRes)
            assertEquals(MovementFormField.PERSON, viewModel.form().errorField)
        }
    }

    @Test
    fun relationalOperationsCannotBeCreatedThroughTheGenericEditor() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            for (type in listOf(MovementType.REFUND, MovementType.SETTLEMENT, MovementType.CONTRIBUTION)) {
                viewModel.editor.onFormChanged(
                    viewModel.form().copy(type = type, amount = "10", date = "2026-01-01"),
                )
                viewModel.editor.onSaveClicked()
                advanceUntilIdle()
                assertEquals(Res.string.movement_validation_context_required, viewModel.form().errorRes)
                assertEquals(MovementFormField.TYPE, viewModel.form().errorField)
                assertTrue(store.movements.listActive().isEmpty())
                assertEquals(0L, store.accounts.getActive("checking")!!.currentBalanceCents)
            }
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    destinationAccountId = "checking",
                ),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(
                Res.string.movement_validation_transfer_same_account,
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    destinationAccountId = null,
                ),
            )
            viewModel.editor.onSaveClicked()

            assertEquals(
                Res.string.movement_validation_destination_required,
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "2,50",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Cafè",
                ),
            )
            viewModel.editor.onSaveClicked()

            assertTrue(viewModel.form().duplicateWarning)
            assertEquals(1, store.movements.listActive().size)

            viewModel.editor.onDuplicateOverrideClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "2,50",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Cafè",
                ),
            )
            viewModel.editor.onSaveClicked()

            assertTrue(viewModel.form().duplicateWarning)

            viewModel.editor.onWarningDismissed()
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "9,99",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Llibre",
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_category_invalid, viewModel.form().errorRes)
            assertEquals(MovementFormField.CATEGORY, viewModel.form().errorField)
        }
    }

    // A transfer can never cross the personal/shared ownership line. The error must land on the
    // side that is actually shared, since that is the field the user has to change.
    @Test
    fun transferIntoASharedAccountRequiresTheAccountContributionFlow() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("common", "laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    destinationAccountId = "common",
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(Res.string.movement_validation_shared_transfer, viewModel.form().errorRes)
            assertEquals(MovementFormField.DESTINATION_ACCOUNT, viewModel.form().errorField)
            assertTrue(store.movements.listActive().isEmpty())
            assertEquals(0L, store.accounts.getActive("checking")!!.currentBalanceCents)
            assertEquals(0L, store.accounts.getActive("common")!!.currentBalanceCents)
        }
    }

    @Test
    fun transferOutOfASharedAccountRequiresTheAccountWithdrawalFlow() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("common", "laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "common",
                    destinationAccountId = "checking",
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(Res.string.movement_validation_shared_transfer, viewModel.form().errorRes)
            assertEquals(MovementFormField.ACCOUNT, viewModel.form().errorField)
            assertTrue(store.movements.listActive().isEmpty())
            assertEquals(0L, store.accounts.getActive("common")!!.currentBalanceCents)
            assertEquals(0L, store.accounts.getActive("checking")!!.currentBalanceCents)
        }
    }

    @Test
    fun aSavedTransferIsNotRewrittenIntoAContribution() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(accountDraft("savings", displayOrder = 1), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.accounts.create(sharedAccountDraft("common", "laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    type = MovementType.TRANSFER,
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    destinationAccountId = "savings",
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            val transfer = store.movements.listActive().single()

            viewModel.onEditClicked(transfer)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(destinationAccountId = "common"))
            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.movement_validation_shared_transfer, viewModel.form().errorRes)
            assertEquals(MovementFormField.DESTINATION_ACCOUNT, viewModel.form().errorField)
            assertEquals(MovementType.TRANSFER, store.movements.listActive().single().type)
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    expenseKind = ExpenseKind.SHARED,
                    splitEditor = splitEditor,
                ),
            )

            viewModel.editor.onSaveClicked()

            assertEquals(Res.string.split_validation_reconcile, viewModel.form().errorRes)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Sopar",
                    expenseKind = ExpenseKind.SHARED,
                    splitEditor = SplitEditorState().withPersonToggled("laura"),
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertTrue(movement.isShared)
            assertEquals(500L, store.people.getActive("laura")!!.balanceCents)
            assertNull(viewModel.editor.form.value)
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
            viewModel.editor.onFormChanged(viewModel.form().copy(type = MovementType.TRANSFER))
            viewModel.editor.onFormChanged(viewModel.form().copy(type = MovementType.EXPENSE))

            // The split must never have been nulled/latched for removal along the way.
            assertEquals(ExpenseKind.SHARED, viewModel.form().expenseKind)
            assertEquals(setOf("laura"), viewModel.form().splitEditor?.selectedPersonIds?.toSet())

            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull("no data-loss warning should fire -- the split was never actually removed", viewModel.editor.form.value?.pendingDataLossWarning)
            assertNull(viewModel.editor.form.value)
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
            viewModel.editor.onSharedToggled(false)
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            // First attempt only warns -- the split must still be there.
            assertEquals(DataLossWarning.SPLIT_REMOVED, viewModel.form().pendingDataLossWarning)
            assertTrue(store.movements.getActive("exp")!!.isShared)

            viewModel.editor.onSplitRemovalAcceptedClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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

            viewModel.editor.onFormChanged(
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
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    splitEditor = null,
                    existingSplit = true,
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Gimnàs",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                ),
            )
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
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
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Gimnàs",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            val created = store.movements.listActive().single()
            val templateId = requireNotNull(created.templateId)

            viewModel.onEditClicked(created)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(isRecurring = false))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            // First attempt only warns -- nothing is written yet.
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)
            assertEquals(TemplateStatus.ACTIVE, store.templates.getActive(templateId)!!.status)

            viewModel.editor.onRecurrenceStopEndClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "20",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Gimnàs",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.WEEKLY,
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            val created = store.movements.listActive().single()
            val templateId = requireNotNull(created.templateId)

            viewModel.onEditClicked(created)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(isRecurring = false))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)

            viewModel.editor.onRecurrenceStopUnlinkClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
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

    // Regression: after the recurrence-stop warning was answered, a duplicate warning appeared but
    // the recurrence warning stayed pending, so the visible action asked about recurrence again
    // and the save could never complete. Each warning must show once, and each answer must stick.
    @Test
    fun aDuplicateFoundAfterStoppingRecurrenceIsAskedOnceAndTheRecurrenceChoiceIsKept() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            val recurring = saveRecurringGym(viewModel, store)
            val templateId = requireNotNull(recurring.templateId)
            // An unlinked movement that the edited one will match once it is no longer recurring.
            store.movements.create(movementDraft(id = "dup", amountCents = 2_000, name = "Gimnàs"), createdAt = NOW)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onEditClicked(recurring)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(isRecurring = false, date = "2026-01-01"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)
            assertFalse(viewModel.form().duplicateWarning)

            viewModel.editor.onRecurrenceStopEndClicked()
            advanceUntilIdle()

            // Only the duplicate is left to answer.
            assertTrue(viewModel.form().duplicateWarning)
            assertNull(viewModel.form().pendingDataLossWarning)
            assertEquals(TemplateStatus.ACTIVE, store.templates.getActive(templateId)!!.status)

            viewModel.editor.onDuplicateOverrideClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            assertEquals(2, store.movements.listActive().size)
            assertNull(store.movements.getActive(recurring.id)!!.templateId)
            assertEquals(
                "the end-series answer given before the duplicate warning still applies",
                TemplateStatus.ENDED,
                store.templates.getActive(templateId)!!.status,
            )
        }
    }

    // Regression: accepting the split-removal warning also skipped the recurrence question and
    // silently detached the movement while its template kept running. The two are separate
    // questions and each needs its own answer.
    @Test
    fun removingASplitAndStoppingRecurrenceAsksBothQuestionsAndEndsTheSeriesWhenChosen() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            val recurring = saveSharedRecurringDinner(viewModel, store)
            val templateId = requireNotNull(recurring.templateId)

            unshareAndStopRecurrence(viewModel, recurring)
            assertEquals(DataLossWarning.SPLIT_REMOVED, viewModel.form().pendingDataLossWarning)

            viewModel.editor.onSplitRemovalAcceptedClicked()
            advanceUntilIdle()

            // Accepting the split removal did not answer what happens to the series.
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)
            assertTrue("nothing is written before both answers", store.movements.getActive(recurring.id)!!.isShared)
            assertEquals(templateId, store.movements.getActive(recurring.id)!!.templateId)

            viewModel.editor.onRecurrenceStopEndClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            val saved = store.movements.getActive(recurring.id)!!
            assertFalse(saved.isShared)
            assertNull(saved.templateId)
            assertEquals(TemplateStatus.ENDED, store.templates.getActive(templateId)!!.status)
            assertEquals(0L, store.people.getActive("laura")!!.balanceCents)
        }
    }

    @Test
    fun removingASplitAndStoppingRecurrenceCanDetachOnlyThisOccurrence() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            val recurring = saveSharedRecurringDinner(viewModel, store)
            val templateId = requireNotNull(recurring.templateId)

            unshareAndStopRecurrence(viewModel, recurring)
            viewModel.editor.onSplitRemovalAcceptedClicked()
            advanceUntilIdle()
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)

            viewModel.editor.onRecurrenceStopUnlinkClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            val saved = store.movements.getActive(recurring.id)!!
            assertFalse(saved.isShared)
            assertNull(saved.templateId)
            assertEquals(TemplateStatus.ACTIVE, store.templates.getActive(templateId)!!.status)
        }
    }

    // An answer belongs to the save attempt it was given in: editing the form afterwards asks again.
    @Test
    fun editingTheFormAfterAnsweringAWarningAsksItAgain() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            val recurring = saveSharedRecurringDinner(viewModel, store)

            unshareAndStopRecurrence(viewModel, recurring)
            viewModel.editor.onSplitRemovalAcceptedClicked()
            advanceUntilIdle()
            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)

            viewModel.editor.onFormChanged(viewModel.form().copy(name = "Sopar del mes"))
            assertNull(viewModel.form().pendingDataLossWarning)
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(DataLossWarning.SPLIT_REMOVED, viewModel.form().pendingDataLossWarning)
            assertTrue(store.movements.getActive(recurring.id)!!.isShared)
        }
    }

    /** Saves a weekly recurring "Gimnàs" of 20 EUR on 2026-01-05 through the form. */
    private fun kotlinx.coroutines.test.TestScope.saveRecurringGym(
        viewModel: MovementsViewModel,
        store: TestStore,
    ): com.gestorfinances.app.data.repository.MovementSummary {
        viewModel.onAddClicked()
        advanceUntilIdle()
        viewModel.editor.onFormChanged(
            viewModel.form().copy(
                amount = "20",
                date = "2026-01-05",
                accountId = "checking",
                name = "Gimnàs",
                isRecurring = true,
                recurringFrequency = RecurrenceFrequency.WEEKLY,
            ),
        )
        viewModel.editor.onSaveClicked()
        advanceUntilIdle()
        return store.movements.listActive().single()
    }

    /** Saves a monthly recurring "Sopar" of 10 EUR shared equally with laura through the form. */
    private fun kotlinx.coroutines.test.TestScope.saveSharedRecurringDinner(
        viewModel: MovementsViewModel,
        store: TestStore,
    ): com.gestorfinances.app.data.repository.MovementSummary {
        store.accounts.create(accountDraft("checking"), createdAt = NOW)
        store.people.create(personDraft("laura"), createdAt = NOW)
        viewModel.onAddClicked()
        advanceUntilIdle()
        viewModel.editor.onFormChanged(
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
        viewModel.editor.onSaveClicked()
        advanceUntilIdle()
        val saved = store.movements.listActive().single()
        assertTrue(saved.isShared)
        assertEquals(500L, store.people.getActive("laura")!!.balanceCents)
        return saved
    }

    /** Opens [movement], turns its sharing and its recurrence off, and presses Save. */
    private fun kotlinx.coroutines.test.TestScope.unshareAndStopRecurrence(
        viewModel: MovementsViewModel,
        movement: com.gestorfinances.app.data.repository.MovementSummary,
    ) {
        viewModel.onEditClicked(movement)
        advanceUntilIdle()
        viewModel.editor.onFormChanged(viewModel.form().copy(isRecurring = false))
        viewModel.editor.onSharedToggled(false)
        viewModel.editor.onSaveClicked()
        advanceUntilIdle()
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
            viewModel.editor.onFormChanged(
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
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            val movement = store.movements.listActive().single()
            val templateId = requireNotNull(movement.templateId)

            viewModel.onArchiveClicked(movement)
            advanceUntilIdle()
            var undo: (suspend () -> Unit)? = null
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "80",
                    date = "2026-01-05",
                    accountId = "checking",
                    name = "Lloguer",
                    isRecurring = true,
                    recurringFrequency = RecurrenceFrequency.MONTHLY,
                ),
            )
            viewModel.editor.onSaveClicked()
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "10",
                    date = "2026-01-01",
                    accountId = "checking",
                    name = "Entrades",
                    expenseKind = ExpenseKind.FOR_OTHER,
                    forOtherPersonId = "laura",
                ),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            val totals = store.analysis.periodTotals(
                fromDate = "2026-01-01",
                toDate = "2026-01-02",
            )

            assertTrue(movement.isShared)
            // The owner paid it all for Laura: she owes it, but she is not who paid.
            assertNull(movement.paidByPersonName)
            assertFalse(movement.paidByPerson)
            assertEquals(1_000L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(0L, totals.actualExpenseCents)
            assertEquals(-1_000L, totals.accountFlowCents)
            assertNull(viewModel.editor.form.value)
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

            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "12",
                    date = "2026-01-01",
                    name = "Dinar",
                ),
            )
            viewModel.editor.onSaveClicked()
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
            viewModel.editor.onTagSelected("beach")

            assertEquals("beach", viewModel.form().tagId)

            viewModel.editor.onTripSelected("lisboa")

            assertNull(viewModel.form().tagId)

            viewModel.editor.onTripSelected("mallorca")
            viewModel.editor.onTagSelected("food")
            viewModel.editor.onFormChanged(
                viewModel.form().copy(
                    amount = "8",
                    date = "2026-01-01",
                    name = "Esmorzar",
                ),
            )
            viewModel.editor.onSaveClicked()
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

            assertNull(viewModel.editor.form.value)
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
    fun cancellingAnEditOpenedFromDetailReturnsToTheSameDetail() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 5_000, name = "Sabates"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            val expense = store.movements.getActive("exp")!!

            // Detail -> Edit, as MovementSheets wires it.
            var sheet: MovementSheet? = MovementSheet.Detail
            viewModel.onDetailClicked(expense)
            viewModel.onEditClicked(expense) { sheet = MovementSheet.Form(fromDetail = true) }
            advanceUntilIdle()
            val form = sheet as MovementSheet.Form
            assertTrue(form.fromDetail)
            assertEquals("exp", viewModel.form().movementId)

            // Cancel -> back to the same detail.
            viewModel.editor.onFormDismissed()
            sheet = form.afterClose(saved = false)
            assertEquals(MovementSheet.Detail, sheet)
            assertEquals("exp", viewModel.state.value.detailMovement?.id)
            assertNull(viewModel.editor.form.value)

            // A saved edit closes the detail too.
            assertNull(form.afterClose(saved = true))
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
            var undo: (suspend () -> Unit)? = null
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
            assertEquals(Res.string.refund_validation_amount_required, updated.errorRes)
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
            assertEquals(Res.string.refund_validation_actual_over, updated.errorRes)
            assertEquals(RefundFormField.ACTUAL_AMOUNT, updated.errorField)
        }
    }

    // Whoever paid, an expense is one movement: switching "Qui ha pagat?" to "Jo" updates it in
    // place, drops what the owner owed, and moves the owner's account.
    @Test
    fun switchingAPersonPaidExpenseToSelfUpdatesTheSameMovement() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.movements.create(
                personPaidExpense(id = "ext1", payerPersonId = "laura", amountCents = 1_000, date = "2026-01-01", name = "Sopar"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val summary = store.movements.listActive().single()
            assertTrue(summary.paidByPerson)
            viewModel.onEditClicked(summary)
            advanceUntilIdle()
            assertEquals("ext1", viewModel.form().movementId)
            assertEquals(ExpenseKind.DEBT, viewModel.form().expenseKind)

            viewModel.editor.onFormChanged(
                viewModel.form().copy(expenseKind = ExpenseKind.PERSONAL, accountId = "checking"),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            val active = store.movements.listActive().single()
            assertEquals("ext1", active.id)
            assertEquals(MovementType.EXPENSE, active.type)
            assertFalse(active.paidByPerson)
            assertFalse(active.isShared)
            assertEquals("checking", active.accountId)
            assertEquals(0L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(-1_000L, store.accounts.getActive("checking")!!.currentBalanceCents)
            assertNull(viewModel.editor.form.value)
        }
    }

    // The reverse: switching to "Un altre" keeps the movement, its payee and notes, takes it out of
    // the owner's account, and leaves the owner owing the payer. Nothing is lost, so no warning.
    @Test
    fun switchingAnExpenseToSomeoneElseUpdatesTheSameMovementWithoutWarning() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "exp", amountCents = 1_000, name = "Entrades").copy(payee = "Teatre", notes = "Fila 3"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onEditClicked(store.movements.getActive("exp")!!)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(expenseKind = ExpenseKind.DEBT, forOtherPersonId = "laura"),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            val active = store.movements.listActive().single()
            assertEquals("exp", active.id)
            assertTrue(active.paidByPerson)
            assertEquals("laura", active.payerId)
            assertNull(active.accountId)
            assertEquals("Teatre", active.payee)
            assertEquals("Fila 3", active.notes)
            assertEquals(-1_000L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(0L, store.accounts.getActive("checking")!!.currentBalanceCents)
        }
    }

    // A recurring expense someone else paid cannot stay linked to its template, which is real
    // information to lose: the recurrence-stop choice still comes first.
    @Test
    fun switchingARecurringExpenseToSomeoneElseAsksWhatHappensToTheRecurrence() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("laura"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "20", date = "2026-01-05", accountId = "checking", name = "Gimnàs", isRecurring = true),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            val created = store.movements.listActive().single()
            val templateId = requireNotNull(created.templateId)

            viewModel.onEditClicked(created)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(expenseKind = ExpenseKind.DEBT, forOtherPersonId = "laura"),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(DataLossWarning.RECURRING_STOP, viewModel.form().pendingDataLossWarning)
            assertFalse(store.movements.getActive(created.id)!!.paidByPerson)

            viewModel.editor.onRecurrenceStopUnlinkClicked()
            advanceUntilIdle()

            val saved = store.movements.getActive(created.id)!!
            assertTrue(saved.paidByPerson)
            assertNull(saved.templateId)
            assertEquals(TemplateStatus.ACTIVE, store.templates.getActive(templateId)!!.status)
        }
    }

    @Test
    fun addingWithoutAnyAccountReportsItInsteadOfOpeningAForm() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            var noAccounts = false

            viewModel.onAddClicked(tripId = null, onNoAccounts = { noAccounts = true })
            advanceUntilIdle()

            assertTrue(noAccounts)
            assertNull(viewModel.editor.form.value)

            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            noAccounts = false
            viewModel.onAddClicked(tripId = null, onNoAccounts = { noAccounts = true })
            advanceUntilIdle()

            assertEquals(false, noAccounts)
            assertEquals("checking", viewModel.form().accountId)
        }
    }

    // Regression: a second Save tap before the first write finished created the movement twice.
    @Test
    fun repeatedSaveBeforeTheFirstFinishesRecordsOneMovement() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "12", date = "2026-01-01", name = "Cafè"),
            )

            viewModel.editor.onSaveClicked()
            assertTrue(viewModel.form().isSaving)
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(1, store.movements.listActive().size)
            assertEquals(-1_200L, store.accounts.getActive("checking")!!.currentBalanceCents)
            assertNull(viewModel.editor.form.value)

            // The guard is released once the write finishes: the next movement saves normally.
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "3", date = "2026-01-02", name = "Pa"),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()
            assertEquals(2, store.movements.listActive().size)
        }
    }

    // Regression: a second Save tap on the refund form recorded the refund twice.
    @Test
    fun repeatedRefundSaveRecordsOneRefund() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(movementDraft(id = "exp", amountCents = 5_000, name = "Sabates"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()
            val expense = store.movements.getActive("exp")!!
            viewModel.onDetailClicked(expense)
            advanceUntilIdle()
            viewModel.onAddRefundClicked(expense)
            viewModel.onRefundFormChanged(viewModel.state.value.refundForm!!.copy(amount = "20", date = "2026-01-02"))

            viewModel.onRefundSaveClicked()
            viewModel.onRefundSaveClicked()
            advanceUntilIdle()

            assertEquals(1, store.movements.refundsForExpense("exp").size)
        }
    }

    // Regression: the settlement toggle was offered while editing an income, and saving then
    // created a new settlement while the original income stayed active beside it.
    @Test
    fun editingAnIncomeNeverRecordsASettlementBesideIt() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("alba"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "inc", amountCents = 5_000, name = "Bizum").copy(type = MovementType.INCOME),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()
            viewModel.onEditClicked(store.movements.getActive("inc")!!)
            advanceUntilIdle()


            viewModel.editor.onFormChanged(
                viewModel.form().copy(name = "Bizum sopar"),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            val active = store.movements.listActive().single()
            assertEquals("inc", active.id)
            assertEquals(MovementType.INCOME, active.type)
            assertEquals("Bizum sopar", active.name)
            assertEquals(0L, store.people.getActive("alba")!!.balanceCents)
            assertEquals(5_000L, store.accounts.getActive("checking")!!.currentBalanceCents)
            assertNull(viewModel.editor.form.value)
        }
    }

    // The ordinary case: the owner owes the payer the whole amount. A rename leaves that debt as it
    // is, and a new amount is still what the owner owes.
    @Test
    fun editingAWhollyOwedPersonPaidExpenseKeepsItsDebtInStepWithTheAmount() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("alba"), createdAt = NOW)
            store.movements.create(
                personPaidExpense(id = "cinema", payerPersonId = "alba", amountCents = 1_500, date = "2026-01-01", name = "Cinema"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onEditClicked(store.movements.getActive("cinema")!!)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(name = "Cinema i crispetes"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals("Cinema i crispetes", store.movements.getActive("cinema")!!.name)
            assertEquals(-1_500L, store.people.getActive("alba")!!.balanceCents)

            viewModel.onEditClicked(store.movements.getActive("cinema")!!)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(amount = "20"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            assertEquals(2_000L, store.movements.getActive("cinema")!!.amountCents)
            assertEquals(-2_000L, store.people.getActive("alba")!!.balanceCents)
        }
    }

    // Regression: migration 20 keeps an expense someone else paid whose owner line had been
    // archived (the owner owes nothing). The edit form read "no active split lines" as "owes the
    // whole amount", so renaming it wrote a new owner line and created a debt.
    @Test
    fun renamingAPersonPaidExpenseWithNoActiveOwnerLineCreatesNoDebt() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("cesc"), createdAt = NOW)
            store.movements.create(
                personPaidExpense(id = "coffee", payerPersonId = "cesc", amountCents = 300, date = "2026-02-14", name = "Coffee"),
                createdAt = NOW,
            )
            // The migrated shape: the movement and its split are active, the owner line is archived.
            store.driver.execute(
                null,
                "UPDATE split_lines SET archived_at = '2026-02-14T01:00:00Z' " +
                    "WHERE split_id = (SELECT id FROM splits WHERE movement_id = 'coffee')",
                0,
            )
            assertEquals(0L, store.people.getActive("cesc")!!.balanceCents)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onEditClicked(store.movements.getActive("coffee")!!)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(name = "Cafè"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            val saved = store.movements.getActive("coffee")!!
            assertEquals("Cafè", saved.name)
            assertEquals(300L, saved.amountCents)
            assertEquals("cesc", saved.payerId)
            assertEquals(0L, store.people.getActive("cesc")!!.balanceCents)
            assertNull("no active owner line may appear", store.splits.getForMovement("coffee"))
            assertEquals(
                listOf("1 1"),
                store.driver.executeQuery(
                    null,
                    "SELECT COUNT(*), SUM(sl.archived_at IS NOT NULL) FROM split_lines sl " +
                        "JOIN splits s ON s.id = sl.split_id WHERE s.movement_id = 'coffee' AND s.archived_at IS NULL",
                    { cursor ->
                        val rows = mutableListOf<String>()
                        while (cursor.next().value) rows += "${cursor.getLong(0)} ${cursor.getLong(1)}"
                        app.cash.sqldelight.db.QueryResult.Value(rows)
                    },
                    0,
                ).value,
            )

            // Its amount cannot be changed here without inventing what the owner owes.
            viewModel.onEditClicked(saved)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(amount = "5"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(Res.string.movement_validation_preserved_payer_amount, viewModel.form().errorRes)
            assertEquals(MovementFormField.AMOUNT, viewModel.form().errorField)
            assertEquals(300L, store.movements.getActive("coffee")!!.amountCents)
            assertEquals(0L, store.people.getActive("cesc")!!.balanceCents)
        }
    }

    // Regression: editing an expense someone else paid, where the owner owes only part, rewrote
    // the owner's share as the whole amount, so a rename changed the debt.
    @Test
    fun renamingAPartlyOwedPersonPaidExpenseKeepsTheOwnersShare() = runTest(dispatcher) {
        freshStore().use { store ->
            store.people.create(personDraft("alba"), createdAt = NOW)
            store.people.create(personDraft("bernat"), createdAt = NOW)
            store.movements.create(
                personPaidExpense(id = "dinner", payerPersonId = "alba", amountCents = 9_000, date = "2026-01-01", name = "Sopar")
                    .copy(
                        splitWrite = MovementSplitWrite.Replace(
                            MovementSplitDraft(
                                SplitEntryMethod.EXACT,
                                listOf(
                                    SplitLineDraft(SplitParticipantKind.USER, null, 3_000),
                                    SplitLineDraft(SplitParticipantKind.PERSON, "bernat", 6_000),
                                ),
                            ),
                        ),
                    ),
                createdAt = NOW,
            )
            assertEquals(-3_000L, store.people.getActive("alba")!!.balanceCents)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onEditClicked(store.movements.getActive("dinner")!!)
            advanceUntilIdle()
            viewModel.editor.onFormChanged(viewModel.form().copy(name = "Sopar d'aniversari"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            val saved = store.movements.getActive("dinner")!!
            assertEquals("Sopar d'aniversari", saved.name)
            assertEquals(9_000L, saved.amountCents)
            assertEquals(3_000L, saved.userShareCents)
            val split = store.splits.getForMovement("dinner")!!
            assertEquals(3_000L, split.lines.single { it.participantKind == SplitParticipantKind.USER }.owedAmountCents)
            assertEquals(6_000L, split.lines.single { it.personId == "bernat" }.owedAmountCents)
            assertEquals(-3_000L, store.people.getActive("alba")!!.balanceCents)
            assertEquals(0L, store.people.getActive("bernat")!!.balanceCents)

            // The split opens in the editor, so a new total must be re-split before it saves.
            viewModel.onEditClicked(saved)
            advanceUntilIdle()
            assertTrue(viewModel.form().payerSplit)
            viewModel.editor.onFormChanged(viewModel.form().copy(amount = "100"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(MovementFormField.SPLIT, viewModel.form().errorField)
            assertEquals(9_000L, store.movements.getActive("dinner")!!.amountCents)

            viewModel.editor.onSplitEditorChanged(
                viewModel.form().splitEditor!!
                    .withExactAmount(USER_PARTICIPANT_ID, "40")
                    .withExactAmount("bernat", "60"),
            )
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            assertEquals(10_000L, store.movements.getActive("dinner")!!.amountCents)
            assertEquals(-4_000L, store.people.getActive("alba")!!.balanceCents)
            assertEquals(0L, store.people.getActive("bernat")!!.balanceCents)
        }
    }

    @Test
    fun anExpenseSomeoneElsePaidCanBeSplitWithThePayer() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(personDraft("anna"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onFormChanged(
                viewModel.form().copy(amount = "60,01", date = "2026-01-01", name = "Sopar", expenseKind = ExpenseKind.DEBT),
            )
            viewModel.editor.onOtherPersonSelected("anna")
            viewModel.editor.onPayerSplitToggled(true)

            val editor = viewModel.form().splitEditor!!
            assertEquals(listOf("anna"), editor.selectedPersonIds)
            assertEquals("anna", editor.payerParticipantId)

            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            val saved = store.movements.listActive().single()
            assertEquals("anna", saved.payerId)
            assertNull(saved.accountId)
            // Equal halves; the payer absorbs the odd cent, so the owner owes 30,00.
            assertEquals(3_000L, saved.userShareCents)
            assertEquals(-3_000L, store.people.getActive("anna")!!.balanceCents)
        }
    }

    @Test
    fun choosingATripKeepsAnAccountThePersonPicked() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.accounts.create(accountDraft("savings", displayOrder = 1), createdAt = NOW)
            store.accounts.create(accountDraft("travel", displayOrder = 2), createdAt = NOW)
            store.trips.create(tripDraft("mallorca").copy(defaultAccountId = "travel"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onTripSelected("mallorca")
            assertEquals("the trip replaces the app default", "travel", viewModel.form().accountId)

            viewModel.editor.onTripSelected(null)
            viewModel.editor.onFormChanged(viewModel.form().copy(accountId = "savings"))
            viewModel.editor.onTripSelected("mallorca")
            assertEquals("savings", viewModel.form().accountId)
        }
    }

    // Regression: an expense with an active refund could be retyped from the form, leaving the
    // refund attached to a movement that was no longer an expense.
    @Test
    fun anExpenseWithAnActiveRefundCannotBeRetypedFromTheForm() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.movements.create(movementDraft(id = "exp", amountCents = 5_000, name = "Sabates"), createdAt = NOW)
            store.movements.createRefund(
                RefundDraft("ref", "exp", 2_000, "checking", "2026-01-02", null, null, null, null),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()
            viewModel.onEditClicked(store.movements.getActive("exp")!!)
            advanceUntilIdle()
            assertTrue(viewModel.form().hasActiveRefunds)

            viewModel.editor.onFormChanged(viewModel.form().copy(type = MovementType.INCOME))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertEquals(Res.string.movement_validation_refunded_expense_type, viewModel.form().errorRes)
            assertEquals(MovementFormField.TYPE, viewModel.form().errorField)
            assertEquals(MovementType.EXPENSE, store.movements.getActive("exp")!!.type)

            // Ordinary edits of the same expense still save.
            viewModel.editor.onFormChanged(viewModel.form().copy(type = MovementType.EXPENSE, name = "Botes"))
            viewModel.editor.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.editor.form.value)
            assertEquals("Botes", store.movements.getActive("exp")!!.name)
            assertEquals(1, store.movements.refundsForExpense("exp").size)
        }
    }

    // Regression: a person created inside the split editor is saved at once, but nothing told
    // mounted pages, so People stayed stale when the movement was then cancelled.
    @Test
    fun aPersonCreatedInlineAdvancesTheSharedRevisionEvenIfTheMovementIsCancelled() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val revision = FinancialDataRevision()
            val viewModel = viewModel(store, revision)
            viewModel.onAddClicked()
            advanceUntilIdle()
            viewModel.editor.onSharedToggled(true)

            viewModel.editor.onCreatePersonInSplit("Marta")
            advanceUntilIdle()
            assertEquals(1L, revision.value.value)
            viewModel.editor.onFormDismissed()

            val people = com.gestorfinances.app.ui.people.PeopleViewModel(
                personRepository = store.people,
                movementRepository = store.movements,
                accountRepository = store.accounts,
                ioDispatcher = dispatcher,
            )
            people.onScreenShown()
            advanceUntilIdle()
            assertEquals(listOf("Marta"), people.state.value.people.map { it.name })
            assertEquals(1L, revision.value.value)
        }
    }

    private fun MovementsViewModel.form(): MovementFormState = editor.form.value!!

    private fun viewModel(store: TestStore, revision: FinancialDataRevision = FinancialDataRevision()): MovementsViewModel =
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
            financialDataRevision = revision,
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
            analysis = AnalysisRepository(database.analysisQueries, database.analysisInsightsQueries),
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
        val driver: JdbcSqliteDriver,
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
