package com.gestorfinances.app.ui.recurring

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateDraft
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
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
                    frequency = RecurrenceFrequency.MONTHLY,
                    dayOfMonth = "1",
                    nextDueDate = "2026-02-01",
                ),
            )
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertNull(viewModel.state.value.form)
            val template = viewModel.state.value.templates.single()
            assertEquals(8_000L, template.amountCents)
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
        }
    }

    @Test
    fun confirmingDuePromptCreatesLinkedMovementAndAdvancesCursor() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(accountDraft("checking"), createdAt = NOW)
            store.templates.create(monthlyTemplateDraft("rent", nextDueDate = "2026-01-01"), createdAt = NOW)
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
            assertEquals("2026-02-01", store.templates.getActive("rent")!!.nextDueDate)
            assertTrue(viewModel.state.value.duePrompts.isEmpty())
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

    private fun viewModel(
        store: TestStore,
        today: LocalDate = LocalDate.parse("2026-01-15"),
    ): RecurringViewModel =
        RecurringViewModel(
            templateRepository = store.templates,
            accountRepository = store.accounts,
            categoryRepository = store.categories,
            movementRepository = store.movements,
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
            templates = TemplateRepository(database.templatesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val templates: TemplateRepository,
        val movements: MovementRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
