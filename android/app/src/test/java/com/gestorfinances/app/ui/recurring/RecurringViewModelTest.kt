package com.gestorfinances.app.ui.recurring

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
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
import com.gestorfinances.app.data.repository.TemplateDraft
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateSplitConfigLine
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TagDraft
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripDraft
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.DetectedTemplateAction
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
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
class RecurringViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Regression: template rows / due-prompt cards used to show the movement's full total no
    // matter the split, unlike MovementListItem (which shows the user's own share as the primary
    // figure and the total as a secondary line for a shared movement). userShareCents is the pure
    // rule TemplateAmountDisplay now uses to derive that primary figure.
    @Test
    fun `userShareCents returns the rescaled user line when the config reconciles`() {
        val config = TemplateSplitConfig(
            entryMethod = "equal",
            payer = "user",
            lines = listOf(
                TemplateSplitConfigLine(party = "user", owedAmountCents = 500),
                TemplateSplitConfigLine(party = "laura", owedAmountCents = 500),
            ),
        )

        assertEquals(500L, config.userShareCents(1_000))
    }

    @Test
    fun `userShareCents rescales the user's share when the amount has since changed`() {
        val config = TemplateSplitConfig(
            entryMethod = "equal",
            payer = "user",
            lines = listOf(
                TemplateSplitConfigLine(party = "user", owedAmountCents = 500),
                TemplateSplitConfigLine(party = "laura", owedAmountCents = 500),
            ),
        )

        // Confirmed amount (1200) differs from the stored config sum (1000) -- the user's share
        // must scale proportionally (600), not stay pinned to the stale stored value (500).
        assertEquals(600L, config.userShareCents(1_200))
    }

    @Test
    fun createMonthlyTemplateViaFormSavesAndLists() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    type = MovementType.EXPENSE,
                    amount = "80",
                    accountId = "checking",
                    name = "Lloguer",
                    payee = "Propietat",
                    frequency = RecurrenceFrequency.MONTHLY,
                    dayOfMonth = "1",
                    nextDueDate = "2026-02-01",
                    amountFlex = "5",
                    dateFlex = "2",
                    leadDays = "1",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val template = viewModel.state.value.templates.single()
            assertEquals(8_000L, template.amountCents)
            assertEquals("Propietat", template.payee)
            assertEquals(500L, template.amountFlexCents)
            assertEquals(2L, template.dateFlexDays)
            assertEquals(1L, template.leadNotificationDays)
            assertEquals(RecurrenceFrequency.MONTHLY, template.frequency)
            assertEquals(1L, template.dayOfMonth)
            assertEquals(TemplateStatus.ACTIVE, template.status)
        }
    }

    @Test
    fun customFrequencyWithoutIntervalShowsError() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    amount = "10",
                    accountId = "checking",
                    frequency = RecurrenceFrequency.CUSTOM,
                    intervalCount = "",
                    nextDueDate = "2026-02-01",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.template_validation_interval_required,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(TemplateFormField.SCHEDULE, viewModel.state.value.form!!.errorField)
            assertTrue(store.templates.listActive().isEmpty())
        }
    }

    @Test
    fun invalidFlexibilityFieldsShowErrors() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    amount = "10",
                    amountFlex = "oops",
                    accountId = "checking",
                    dayOfMonth = "1",
                    nextDueDate = "2026-02-01",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                R.string.template_validation_amount_flex_invalid,
                viewModel.state.value.form!!.errorRes,
            )
            assertEquals(TemplateFormField.AMOUNT_FLEX, viewModel.state.value.form!!.errorField)
            assertTrue(store.templates.listActive().isEmpty())
        }
    }

    @Test
    fun pauseEndAndResumeChangeStatus() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    amount = "80",
                    accountId = "checking",
                    frequency = RecurrenceFrequency.MONTHLY,
                    dayOfMonth = "1",
                    nextDueDate = "2026-02-01",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val template = viewModel.state.value.templates.single()
            viewModel.onPauseClicked(template)
            advanceUntilIdle()
            assertEquals(TemplateStatus.PAUSED, viewModel.state.value.templates.single().status)

            viewModel.onResumeClicked(viewModel.state.value.templates.single())
            advanceUntilIdle()
            assertEquals(TemplateStatus.ACTIVE, viewModel.state.value.templates.single().status)

            viewModel.onEndClicked(viewModel.state.value.templates.single())
            viewModel.onEndConfirmed()
            advanceUntilIdle()
            assertEquals(TemplateStatus.ENDED, viewModel.state.value.templates.single().status)
        }
    }

    // Regression: deleting a template used to only archive the `templates` row, leaving its
    // movements permanently linked (`template_id` unchanged) -- they kept showing the recurring
    // badge forever and stayed excluded from `RecurringPatternDetector` (which only considers
    // `templateId == null`), orphaned from both systems with no way back.
    @Test
    fun `deleting a template unlinks its movements so they stop being recurring`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("rent", nextDueDate = "2026-02-01"), createdAt = NOW)
            monthlyMovementDates("2026-01-01", months = 3).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "rent-$i", date = date, amountCents = 8_000, name = "Lloguer").copy(templateId = "rent"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store)
            val template = store.templates.listActive().single()

            viewModel.onDeleteClicked(template)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            assertNull(store.templates.getActive("rent"))
            val linkedMovements = store.movements.listActive().filter { it.name == "Lloguer" }
            assertEquals(3, linkedMovements.size)
            assertTrue(linkedMovements.all { it.templateId == null })
        }
    }

    @Test
    fun `dismissing the delete confirmation leaves the template and its links untouched`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("rent", nextDueDate = "2026-02-01"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "rent-0", date = "2026-01-01", amountCents = 8_000, name = "Lloguer").copy(templateId = "rent"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            val template = store.templates.listActive().single()

            viewModel.onDeleteClicked(template)
            viewModel.onDeleteDismissed()
            advanceUntilIdle()

            assertEquals(TemplateStatus.ACTIVE, store.templates.getActive("rent")!!.status)
            assertEquals("rent", store.movements.getActive("rent-0")!!.templateId)
        }
    }

    @Test
    fun `ending a template does not unlink its movements`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("rent", nextDueDate = "2026-02-01"), createdAt = NOW)
            store.movements.create(
                movementDraft(id = "rent-0", date = "2026-01-01", amountCents = 8_000, name = "Lloguer").copy(templateId = "rent"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            val template = store.templates.listActive().single()

            viewModel.onEndClicked(template)
            viewModel.onEndConfirmed()
            advanceUntilIdle()

            assertEquals(TemplateStatus.ENDED, store.templates.getActive("rent")!!.status)
            assertEquals("rent", store.movements.getActive("rent-0")!!.templateId)
        }
    }

    @Test
    fun monthlyTotalSumsFixedActiveTemplatesForCurrentMonth() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("rent", nextDueDate = "2026-01-01"), createdAt = NOW)
            store.templates.create(
                monthlyTemplateDraft("salary", nextDueDate = "2026-01-01")
                    .copy(type = MovementType.INCOME, amountCents = 200_000, name = "Nòmina"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store, today = LocalDate.parse("2026-01-15"))
            viewModel.onScreenShown()
            advanceUntilIdle()

            assertEquals(8_000L, viewModel.state.value.monthlyExpenseCents)
            assertEquals(200_000L, viewModel.state.value.monthlyIncomeCents)
            assertEquals(192_000L, viewModel.state.value.monthlyNetCents)
            assertEquals(0L, viewModel.state.value.monthlyPaidCents)
            assertEquals(208_000L, viewModel.state.value.monthlyRemainingCents)
        }
    }

    @Test
    fun confirmingDuePromptCreatesLinkedMovementAndAdvancesCursor() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.trips.create(
                TripDraft("trip", "Viatge", TripType.TRIP, TripStatus.ACTIVE, null, null, null, null, null, "checking"),
                createdAt = NOW,
            )
            store.tags.create(TagDraft("tag", "Platja", null, null, "trip"), createdAt = NOW)
            store.templates.create(
                monthlyTemplateDraft("rent", nextDueDate = "2026-01-01").copy(tripId = "trip", tagId = "tag"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store, today = LocalDate.parse("2026-01-15"))
            viewModel.onScreenShown()
            advanceUntilIdle()

            val prompt = viewModel.state.value.duePrompts.single()
            assertEquals("2026-01-01", prompt.dueDate)

            viewModel.onConfirmClicked(prompt)
            assertEquals("80,00", viewModel.state.value.confirmPrompt!!.amount)
            viewModel.onConfirmSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertEquals(8_000L, movement.amountCents)
            assertEquals("2026-01-01", movement.date)
            assertEquals("trip", movement.tripId)
            assertEquals("tag", movement.tagId)
            assertEquals("2026-02-01", store.templates.getActive("rent")!!.nextDueDate)
            assertTrue(viewModel.state.value.duePrompts.isEmpty())
            assertEquals(8_000L, viewModel.state.value.monthlyPaidCents)
            assertEquals(0L, viewModel.state.value.monthlyRemainingCents)
            assertEquals(8_000L, viewModel.state.value.monthlyPaidExpenseCents)
            assertEquals(0L, viewModel.state.value.monthlyPaidIncomeCents)
            assertEquals(TemplateMonthPaymentState.PAID, viewModel.state.value.monthlyPaymentStates["rent"])
            assertEquals(1L, viewModel.state.value.occurrenceCounts["rent"])
        }
    }

    // End-to-end regression for the reported bug: confirming a due occurrence of a shared
    // recurring template must carry the split forward (RecurringViewModel.toSplitWrite), not
    // silently create a plain expense. This exercises the read/carry-forward side against a
    // template whose split_config is actually populated, as createQuickTemplate now does.
    @Test
    fun confirmingDuePromptOnASharedTemplateCarriesTheSplitForward() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.templates.create(
                monthlyTemplateDraft("dinner", nextDueDate = "2026-01-01").copy(
                    amountCents = 1_000,
                    splitConfig = TemplateSplitConfig(
                        entryMethod = "equal",
                        payer = "user",
                        lines = listOf(
                            TemplateSplitConfigLine(party = "user", owedAmountCents = 500),
                            TemplateSplitConfigLine(party = "laura", owedAmountCents = 500),
                        ),
                    ),
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store, today = LocalDate.parse("2026-01-15"))
            viewModel.onScreenShown()
            advanceUntilIdle()

            assertEquals(500L, viewModel.state.value.monthlyExpenseCents)
            assertEquals(500L, viewModel.state.value.monthlyRemainingExpenseCents)

            val prompt = viewModel.state.value.duePrompts.single()
            viewModel.onConfirmClicked(prompt)
            viewModel.onConfirmSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertTrue("confirmed occurrence must be shared, not a plain expense", movement.isShared)
            assertEquals(500L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(500L, viewModel.state.value.monthlyPaidCents)
            assertEquals(500L, viewModel.state.value.monthlyPaidExpenseCents)
        }
    }

    // Regression: toSplitWrite used to drop the split entirely (KeepExisting -> plain personal
    // expense) whenever the confirmed amount didn't exactly equal the stored split_config sum --
    // which is the common case for a variable-amount template or one whose amount was edited since
    // the split was set. It must now rescale the split's line weights to the confirmed amount
    // (SplitCalculator.rescale) instead of silently losing the share.
    @Test
    fun confirmingADuePromptWithAnEditedAmountRescalesTheSplitInsteadOfDroppingIt() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.templates.create(
                monthlyTemplateDraft("dinner", nextDueDate = "2026-01-01").copy(
                    amountCents = 1_000,
                    splitConfig = TemplateSplitConfig(
                        entryMethod = "equal",
                        payer = "user",
                        lines = listOf(
                            TemplateSplitConfigLine(party = "user", owedAmountCents = 500),
                            TemplateSplitConfigLine(party = "laura", owedAmountCents = 500),
                        ),
                    ),
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store, today = LocalDate.parse("2026-01-15"))
            viewModel.onScreenShown()
            advanceUntilIdle()

            val prompt = viewModel.state.value.duePrompts.single()
            viewModel.onConfirmClicked(prompt)
            viewModel.onConfirmFormChanged(viewModel.state.value.confirmPrompt!!.copy(amount = "12"))
            viewModel.onConfirmSaveClicked()
            advanceUntilIdle()

            val movement = store.movements.listActive().single()
            assertTrue("edited-amount occurrence must still be shared, not dropped to personal", movement.isShared)
            assertEquals(12_00L, movement.amountCents)
            assertEquals(600L, store.people.getActive("laura")!!.balanceCents)
        }
    }

    // Regression: unlike the pattern-detection confirm
    // path (toTemplateDraft(existing), above), the manual Edit-template form has no split_config
    // field of its own -- onSaveClicked used to always pass splitConfig = null, and
    // TemplateRepository.update is a full-row overwrite, so saving an unrelated edit (e.g. the
    // name) through "Més > Recurrents > Edit" silently wiped a shared template's split forever.
    @Test
    fun editingATemplateWithAnExistingSplitConfigPreservesIt() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            val splitConfig = TemplateSplitConfig(
                entryMethod = "equal",
                payer = "user",
                lines = listOf(
                    TemplateSplitConfigLine(party = "user", owedAmountCents = 500),
                    TemplateSplitConfigLine(party = "laura", owedAmountCents = 500),
                ),
            )
            store.templates.create(
                monthlyTemplateDraft("dinner", nextDueDate = "2026-02-01").copy(
                    amountCents = 1_000,
                    splitConfig = splitConfig,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            val template = viewModel.state.value.templates.single()
            viewModel.onEditClicked(template)
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(name = "Sopar amb Laura"),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val updated = store.templates.getActive("dinner")!!
            assertEquals("Sopar amb Laura", updated.name)
            assertEquals(splitConfig, updated.splitConfig)
        }
    }

    @Test
    fun skippingDuePromptAdvancesCursorWithoutMovement() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("rent", nextDueDate = "2026-01-01"), createdAt = NOW)
            val viewModel = viewModel(store, today = LocalDate.parse("2026-01-15"))
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onSkipClicked(viewModel.state.value.duePrompts.single())
            advanceUntilIdle()

            assertTrue(store.movements.listActive().isEmpty())
            assertEquals("2026-02-01", store.templates.getActive("rent")!!.nextDueDate)
            assertTrue(viewModel.state.value.duePrompts.isEmpty())
        }
    }

    // Regression for spec-guardian's F3 follow-up: RecurringAdvancer.advance's occurrence ceiling
    // must never throw on the caller's (UI) thread. onSkipAllClicked used to evaluate
    // advancedToToday(...) eagerly as an argument before entering the ioDispatcher/runCatching
    // block. In practice `toDuePrompts` already runCatching-guards the *listing* computation, so
    // the DuePrompt is built directly here (bypassing that list) to exercise onSkipAllClicked's
    // own advance() call in isolation, the way a `today()` value that moved on between listing
    // and tapping (e.g. crossing midnight) would in production.
    @Test
    fun skippingAllOnADecadesStaleTemplateSurfacesErrorInsteadOfCrashing() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(
                customDailyTemplateDraft("ancient", nextDueDate = "1900-01-01"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store, today = LocalDate.parse("2026-01-15"))
            val template = store.templates.getActive("ancient")!!
            val prompt = DuePrompt(template = template, dueDate = template.nextDueDate, pendingCount = 1)

            // Must not throw synchronously here (the bug: advancedToToday() used to run on this
            // thread, outside any try/catch, before the ceiling's require() could be caught).
            viewModel.onSkipAllClicked(prompt)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.errorMessage?.isNotBlank() == true)
            // Cursor must be unchanged: the failed advance must not silently no-op or partially apply.
            assertEquals("1900-01-01", store.templates.getActive("ancient")!!.nextDueDate)
        }
    }

    @Test
    fun `detecting recurring patterns populates the review list from unlinked history`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "netflix-$i", date = date, amountCents = 1200, name = "Netflix"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))

            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()

            val review = viewModel.state.value.detectionReview!!
            assertEquals(1, review.items.size)
            assertEquals("Netflix", review.items.single().candidate.name)
            assertTrue(review.items.single().accepted)
        }
    }

    @Test
    fun `confirming detected candidates only creates templates for the accepted ones`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "netflix-$i", date = date, amountCents = 1200, name = "Netflix"),
                    createdAt = NOW,
                )
            }
            monthlyMovementDates("2026-01-10", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "spotify-$i", date = date, amountCents = 999, name = "Spotify"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-15"))
            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.detectionReview!!.items.size)

            val spotifyIndex = viewModel.state.value.detectionReview!!.items
                .indexOfFirst { it.candidate.name == "Spotify" }
            viewModel.onDetectionItemToggled(spotifyIndex, false)
            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            val created = store.templates.listActive()
            assertEquals(1, created.size)
            assertEquals("Netflix", created.single().name)
            assertNull(viewModel.state.value.detectionReview)
        }
    }

    // Regression: `onDetectionConfirmAllClicked` used to wrap the whole accepted-items loop in a
    // single `runCatching`, so one item failing silently abandoned every item after it, and
    // retrying could resubmit an already-succeeded NEW candidate with a fresh UUID -- a duplicate
    // template. A SQL trigger that rejects every insert past the first deterministically forces
    // the second candidate (sorted alphabetically after the first) to fail without needing to
    // predict its generated id.
    @Test
    fun `a failure applying one candidate does not abandon or duplicate the others on retry`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "netflix-$i", date = date, amountCents = 1200, name = "Netflix"),
                    createdAt = NOW,
                )
            }
            monthlyMovementDates("2026-01-10", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "spotify-$i", date = date, amountCents = 999, name = "Spotify"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-15"))
            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.detectionReview!!.items.size)

            // "Netflix" sorts before "Spotify", so it's applied first; this trigger lets the
            // first insert through and rejects every insert after it.
            store.driver.execute(
                null,
                """
                CREATE TRIGGER reject_second_template
                BEFORE INSERT ON templates
                WHEN (SELECT COUNT(*) FROM templates) >= 1
                BEGIN
                    SELECT RAISE(ABORT, 'simulated failure for regression test');
                END;
                """.trimIndent(),
                0,
            )

            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            val afterFirstAttempt = store.templates.listActive()
            assertEquals(1, afterFirstAttempt.size)
            assertEquals("Netflix", afterFirstAttempt.single().name)
            val remainingReview = viewModel.state.value.detectionReview
            assertEquals(1, remainingReview?.items?.size)
            assertEquals("Spotify", remainingReview?.items?.single()?.candidate?.name)
            assertTrue(remainingReview?.items?.single()?.accepted == true)
            assertTrue(remainingReview?.errorMessage != null)

            // The blocker is resolved; retrying must only (re)apply the still-pending candidate,
            // never resubmit "Netflix" as a duplicate.
            store.driver.execute(null, "DROP TRIGGER reject_second_template", 0)
            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            val afterRetry = store.templates.listActive()
            assertEquals(2, afterRetry.size)
            assertEquals(1, afterRetry.count { it.name == "Netflix" })
            assertEquals(1, afterRetry.count { it.name == "Spotify" })
            assertNull(viewModel.state.value.detectionReview)
        }
    }

    @Test
    fun `confirming a new candidate links its source movements to the created template`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "netflix-$i", date = date, amountCents = 1200, name = "Netflix"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))
            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()
            val createdTemplateId = store.templates.listActive().single().id

            val netflixMovements = store.movements.listActive().filter { it.name == "Netflix" }
            assertEquals(4, netflixMovements.size)
            assertTrue(netflixMovements.all { it.templateId == createdTemplateId })

            // Re-running detection finds no leftover evidence for the same group -- the source
            // movements are now linked, so they're excluded up front rather than re-proposed as
            // an "Actualitza" candidate forever.
            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.detectionReview!!.items.isEmpty())
            assertEquals(1, store.templates.listActive().size)
        }
    }

    // Regression: the pattern detector never carried split info through its NEW-candidate path —
    // RecurringCandidateMovement/DetectedRecurringCandidate had no split field at all, and
    // toTemplateDraft(existing) only ever read existing?.splitConfig (always null for NEW, since
    // there's no existing template to read from). Confirming a detected NEW candidate for a
    // recurring SHARED expense silently created a plain personal template. Fixed by resolving the
    // split from the most recent source movement (the same one the detector already treats as
    // representative for name/payee/lastDate) when applying a NEW candidate.
    @Test
    fun `confirming a new detected candidate for a shared recurring expense carries the split forward`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "dinner-$i", date = date, amountCents = 1_000, name = "Sopar").copy(
                        splitWrite = MovementSplitWrite.Replace(
                            MovementSplitDraft(
                                entryMethod = SplitEntryMethod.EQUAL,
                                lines = listOf(
                                    SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = 500),
                                    SplitLineDraft(SplitParticipantKind.PERSON, personId = "laura", owedAmountCents = 500),
                                ),
                            ),
                        ),
                    ),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))
            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            val created = store.templates.listActive().single()
            assertEquals(
                TemplateSplitConfig(
                    entryMethod = "equal",
                    payer = "user",
                    lines = listOf(
                        TemplateSplitConfigLine(party = "user", owedAmountCents = 500),
                        TemplateSplitConfigLine(party = "laura", owedAmountCents = 500),
                    ),
                ),
                created.splitConfig,
            )
        }
    }

    // Regression: the detection *review sheet* (before confirming anything) used to show only the
    // candidate's raw group total with no split awareness at all -- DetectedRecurringCandidate is
    // deliberately split-blind (correct layering), but that meant the review row for a shared
    // group looked identical to a personal one, hiding sharing from the user until after they'd
    // already confirmed it. The split preview must now be resolved and attached to the review item
    // itself, before any confirm action.
    @Test
    fun `the detection review sheet resolves a shared candidate's split before confirming`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "dinner-$i", date = date, amountCents = 1_000, name = "Sopar").copy(
                        splitWrite = MovementSplitWrite.Replace(
                            MovementSplitDraft(
                                entryMethod = SplitEntryMethod.EQUAL,
                                lines = listOf(
                                    SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = 500),
                                    SplitLineDraft(SplitParticipantKind.PERSON, personId = "laura", owedAmountCents = 500),
                                ),
                            ),
                        ),
                    ),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))
            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()

            val item = viewModel.state.value.detectionReview!!.items.single()
            assertEquals(
                TemplateSplitConfig(
                    entryMethod = "equal",
                    payer = "user",
                    lines = listOf(
                        TemplateSplitConfigLine(party = "user", owedAmountCents = 500),
                        TemplateSplitConfigLine(party = "laura", owedAmountCents = 500),
                    ),
                ),
                item.splitConfig,
            )
            // The group's amount (1000) already matches the split's stored sum here, so the
            // user's share is the unscaled stored value.
            assertEquals(500L, item.splitConfig?.userShareCents(item.candidate.amountCents!!))

            // Confirming still applies correctly -- the preview and the actual apply must agree.
            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()
            assertEquals(item.splitConfig, store.templates.listActive().single().splitConfig)
        }
    }

    // Companion to the above: a NEW candidate for a plain (non-shared) recurring expense must still
    // get a null split_config -- guards against the fix above over-eagerly resolving a split that
    // doesn't exist.
    @Test
    fun `confirming a new detected candidate for a plain recurring expense leaves split_config null`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "netflix-$i", date = date, amountCents = 1200, name = "Netflix"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))
            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            assertNull(store.templates.listActive().single().splitConfig)
        }
    }

    // Regression (spec-guardian + simplicity-guardian, independently): TemplateRepository.update
    // is a full-row overwrite. Confirming an "Actualitza" (UPDATE) candidate against a manually
    // configured template must not silently wipe fields the detector doesn't derive.
    @Test
    fun `confirming an update candidate preserves the existing template's untouched fields`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(
                TemplateDraft(
                    id = "existing-tpl",
                    type = MovementType.EXPENSE,
                    amountCents = 1200,
                    accountId = "checking",
                    destAccountId = null,
                    categoryId = null,
                    name = "Netflix",
                    payee = null,
                    notes = "Pla familiar",
                    splitConfig = null,
                    frequency = RecurrenceFrequency.MONTHLY,
                    intervalCount = null,
                    customUnit = null,
                    dayOfMonth = 5,
                    weekday = null,
                    nextDueDate = "2026-05-05",
                    amountIsVariable = false,
                    amountFlexCents = null,
                    dateFlexDays = 3,
                    leadNotificationDays = 2,
                    status = TemplateStatus.ACTIVE,
                ),
                createdAt = NOW,
            )
            monthlyMovementDates("2026-01-05", months = 4).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "netflix-$i", date = date, amountCents = 1200, name = "Netflix"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))

            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            val only = viewModel.state.value.detectionReview!!.items.single().candidate
            assertEquals(DetectedTemplateAction.UPDATE, only.action)
            assertEquals("existing-tpl", only.matchedTemplateId)

            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            val updated = store.templates.getActive("existing-tpl")!!
            assertEquals("Pla familiar", updated.notes)
            assertEquals(3L, updated.dateFlexDays)
            assertEquals(2L, updated.leadNotificationDays)
            assertTrue(store.movements.listActive().filter { it.name == "Netflix" }.all { it.templateId == "existing-tpl" })
        }
    }

    // Regression: `TemplateRepository.update`'s SQL omitted `status`, so confirming an
    // "Actualitza" candidate against a stale-but-still-ACTIVE template silently left it ACTIVE
    // even though the review row showed "Finalitzat" as the proposed status.
    @Test
    fun `confirming a stale update candidate actually ends the matched template`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(
                TemplateDraft(
                    id = "existing-tpl",
                    type = MovementType.EXPENSE,
                    amountCents = 1500,
                    accountId = "checking",
                    destAccountId = null,
                    categoryId = null,
                    name = "Gimnas antic",
                    payee = null,
                    notes = null,
                    splitConfig = null,
                    frequency = RecurrenceFrequency.MONTHLY,
                    intervalCount = null,
                    customUnit = null,
                    dayOfMonth = 5,
                    weekday = null,
                    nextDueDate = "2025-04-05",
                    amountIsVariable = false,
                    amountFlexCents = null,
                    dateFlexDays = null,
                    leadNotificationDays = null,
                    status = TemplateStatus.ACTIVE,
                ),
                createdAt = NOW,
            )
            monthlyMovementDates("2025-01-05", months = 3).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "old-$i", date = date, amountCents = 1500, name = "Gimnas antic"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))

            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            val only = viewModel.state.value.detectionReview!!.items.single().candidate
            assertEquals(DetectedTemplateAction.UPDATE, only.action)
            assertEquals(TemplateStatus.ENDED, only.suggestedStatus)

            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            assertEquals(TemplateStatus.ENDED, store.templates.getActive("existing-tpl")!!.status)
        }
    }

    @Test
    fun `a stale detected pattern proposes ended status when confirmed`() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            monthlyMovementDates("2025-01-05", months = 3).forEachIndexed { i, date ->
                store.movements.create(
                    movementDraft(id = "old-$i", date = date, amountCents = 1500, name = "Gimnas antic"),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store, today = LocalDate.parse("2026-04-10"))

            viewModel.onDetectRecurringClicked()
            advanceUntilIdle()
            assertEquals(TemplateStatus.ENDED, viewModel.state.value.detectionReview!!.items.single().candidate.suggestedStatus)

            viewModel.onDetectionConfirmAllClicked()
            advanceUntilIdle()

            assertEquals(TemplateStatus.ENDED, store.templates.listActive().single { it.name == "Gimnas antic" }.status)
        }
    }

    @Test
    fun openingHistoryShowsOnlyMovementsLinkedToTheSelectedTemplate() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("rent", "2026-02-01"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("gym", "2026-02-05"), createdAt = NOW)
            store.movements.create(
                movementDraft("rent-jan", "2026-01-01", 8_000, "Lloguer").copy(templateId = "rent"),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft("gym-jan", "2026-01-05", 1_500, "Gimnàs").copy(templateId = "gym"),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onHistoryClicked(viewModel.state.value.templates.single { it.id == "rent" })
            advanceUntilIdle()

            val detail = viewModel.state.value.historyDetail!!
            assertEquals("rent", detail.template.id)
            assertEquals(listOf("rent-jan"), detail.movements.map { it.id })

            viewModel.onHistoryDismissed()
            assertNull(viewModel.state.value.historyDetail)

            viewModel.onHistoryClicked(viewModel.state.value.templates.single { it.id == "rent" })
            viewModel.onHistoryDismissed()
            advanceUntilIdle()
            assertNull(viewModel.state.value.historyDetail)
        }
    }

    @Test
    fun newTemplateStartsWithACompleteMonthlySchedule() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store, today = LocalDate.parse("2026-08-10"))

            viewModel.onAddClicked()

            val form = viewModel.state.value.form!!
            assertEquals("2026-08-10", form.nextDueDate)
            assertEquals("10", form.dayOfMonth)
            assertEquals(0, form.weekday)
        }
    }

    @Test
    fun changingTemplateTypeClearsFieldsThatNoLongerApply() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onAddClicked()

            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    type = MovementType.TRANSFER,
                    categoryId = "old-category",
                    tripId = "old-trip",
                    tagId = "old-tag",
                    destinationAccountId = "savings",
                ),
            )
            val transfer = viewModel.state.value.form!!
            assertNull(transfer.categoryId)
            assertNull(transfer.tripId)
            assertNull(transfer.tagId)
            assertEquals("savings", transfer.destinationAccountId)

            viewModel.onFormChanged(transfer.copy(type = MovementType.EXPENSE))
            assertNull(viewModel.state.value.form!!.destinationAccountId)
        }
    }

    @Test
    fun advancedValidationErrorExpandsItsSection() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()
            viewModel.onAddClicked()
            viewModel.onFormChanged(
                viewModel.state.value.form!!.copy(
                    amount = "10",
                    accountId = "checking",
                    dayOfMonth = "1",
                    nextDueDate = "2026-02-01",
                    leadDays = "-1",
                    showAdvanced = false,
                ),
            )

            viewModel.onSaveClicked()

            assertEquals(TemplateFormField.LEAD_DAYS, viewModel.state.value.form!!.errorField)
            assertTrue(viewModel.state.value.form!!.showAdvanced)
        }
    }

    /** [months] consecutive monthly dates starting at [startDate], same day-of-month. */
    private fun monthlyMovementDates(startDate: String, months: Int): List<String> {
        val start = LocalDate.parse(startDate)
        return (0 until months).map { start.plusMonths(it.toLong()).toString() }
    }

    private fun movementDraft(id: String, date: String, amountCents: Long, name: String): MovementDraft =
        MovementDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = amountCents,
            date = date,
            accountId = "checking",
            destinationAccountId = null,
            categoryId = null,
            name = name,
            payee = null,
            notes = null,
            isOneTime = false,
        )

    private fun customDailyTemplateDraft(id: String, nextDueDate: String): TemplateDraft =
        TemplateDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = 100,
            accountId = "checking",
            destAccountId = null,
            categoryId = null,
            name = "Ancient",
            payee = null,
            notes = null,
            splitConfig = null,
            frequency = RecurrenceFrequency.CUSTOM,
            intervalCount = 1,
            customUnit = CustomRecurrenceUnit.DAYS,
            dayOfMonth = null,
            weekday = null,
            nextDueDate = nextDueDate,
            amountIsVariable = false,
            amountFlexCents = null,
            dateFlexDays = null,
            leadNotificationDays = null,
            status = TemplateStatus.ACTIVE,
        )

    private fun viewModel(
        store: TestStore,
        today: LocalDate = LocalDate.parse("2026-01-15"),
    ): RecurringViewModel =
        RecurringViewModel(
            templateRepository = store.templates,
            accountRepository = store.accounts,
            categoryRepository = store.categories,
            tripRepository = store.trips,
            tagRepository = store.tags,
            movementRepository = store.movements,
            splitRepository = store.splits,
            personRepository = store.people,
            ioDispatcher = dispatcher,
            today = { today },
        )

    private fun monthlyTemplateDraft(id: String, nextDueDate: String): TemplateDraft =
        TemplateDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = 8_000,
            accountId = "checking",
            destAccountId = null,
            categoryId = null,
            name = "Lloguer",
            payee = null,
            notes = null,
            splitConfig = null,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalCount = null,
            customUnit = null,
            dayOfMonth = 1,
            weekday = null,
            nextDueDate = nextDueDate,
            amountIsVariable = false,
            amountFlexCents = null,
            dateFlexDays = null,
            leadNotificationDays = null,
            status = TemplateStatus.ACTIVE,
        )

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

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            categories = CategoryRepository(database.categoriesQueries),
            trips = TripRepository(database.tripsQueries),
            tags = TagRepository(database.tagsQueries),
            templates = TemplateRepository(database.templatesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            people = PersonRepository(database.peopleQueries),
            splits = SplitRepository(database.splitsQueries),
        )
    }

    private class TestStore(
        val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val trips: TripRepository,
        val tags: TagRepository,
        val templates: TemplateRepository,
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
