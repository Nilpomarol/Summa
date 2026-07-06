package com.gestorfinances.app.ui.movements

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.AutoCatRulesQueries
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.AutoCatRuleRepository
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRepository
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
    fun formSuggestsCategoryFromActiveAutoCatRuleButNeverAppliesItAutomatically() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.categories.create(
                CategoryDraft(
                    id = "groceries",
                    name = "Alimentació",
                    kind = CategoryKind.EXPENSE,
                    nature = CategoryNature.VARIABLE,
                    parentId = null,
                    icon = null,
                    color = null,
                    displayOrder = 0,
                ),
                createdAt = NOW,
            )
            store.autoCatRulesQueries.insertAutoCatRule(
                id = "rule-1",
                name = "Supermarket",
                priority = 10,
                conditions = "{\"text_contains\":\"super\"}",
                action_category_id = "groceries",
                action_trip_id = null,
                source = "user",
                active = 1,
                created_at = NOW,
                updated_at = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onAddClicked()
            advanceUntilIdle()

            viewModel.onFormChanged(
                viewModel.form().copy(
                    name = "Super Mercat",
                    amount = "20",
                    date = "2026-01-01",
                    accountId = "checking",
                ),
            )

            // Suggested, but not applied to categoryId (read-only hint, never auto-applies).
            assertEquals("groceries", viewModel.form().suggestedCategoryId)
            assertNull(viewModel.form().categoryId)

            // Tapping the suggestion is the form's onFormChange with categoryId set explicitly.
            viewModel.onFormChanged(viewModel.form().copy(categoryId = "groceries"))
            assertEquals("groceries", viewModel.form().categoryId)
            assertEquals("groceries", viewModel.form().suggestedCategoryId)
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
                    expenseKind = ExpenseKind.SHARED,
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
            viewModel.onArchiveConfirmed(revertDueDate = true)
            advanceUntilIdle()

            assertEquals("2026-01-05", store.templates.getActive(templateId)!!.nextDueDate)
            assertNull(store.movements.getActive(movement.id))
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
            autoCatRuleRepository = store.autoCatRules,
            templateRepository = store.templates,
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
            tags = TagRepository(database.tagsQueries),
            trips = TripRepository(database.tripsQueries),
            templates = TemplateRepository(database.templatesQueries),
            splits = SplitRepository(database.splitsQueries),
            autoCatRules = AutoCatRuleRepository(database.autoCatRulesQueries),
            autoCatRulesQueries = database.autoCatRulesQueries,
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
        val autoCatRules: AutoCatRuleRepository,
        val autoCatRulesQueries: AutoCatRulesQueries,
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
