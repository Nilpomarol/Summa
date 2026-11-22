package com.gestorfinances.app.ui.dashboard

import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateDraft
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetDraft
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.domain.rules.PlanStatus
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
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TemplateRepository
import java.time.LocalDate
import java.time.YearMonth
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
class DashboardViewModelTest {
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
    fun theSummaryLeadsWithTheDefaultAccountAndOnlyTheFiveLatestMovements() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            store.categories.create(categoryDraft("food", "Menjar"), createdAt = NOW)
            repeat(6) { index ->
                store.movements.create(
                    expenseDraft(id = "e$index", date = "2026-07-0${index + 1}", cents = 1_000L),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store)

            viewModel.onScreenShown()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("main", state.mainAccount?.id)
            assertEquals(5, state.latestMovements.size)
            assertEquals(listOf("e5", "e4", "e3", "e2", "e1"), state.latestMovements.map { it.id })
            assertEquals(YearMonth.of(2026, 7), state.month)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun upcomingRecurringAreTheActiveOnesOverdueOrDueWithinTwoWeeksSoonestFirst() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            // Today is 15 July.
            store.templates.create(templateDraft("soon", nextDueDate = "2026-07-20"), createdAt = NOW)
            store.templates.create(templateDraft("overdue", nextDueDate = "2026-07-10"), createdAt = NOW)
            store.templates.create(templateDraft("edge", nextDueDate = "2026-07-29"), createdAt = NOW)
            store.templates.create(templateDraft("later", nextDueDate = "2026-07-30"), createdAt = NOW)
            store.templates.create(templateDraft("paused", nextDueDate = "2026-07-16").copy(status = TemplateStatus.PAUSED), createdAt = NOW)
            val viewModel = viewModel(store)

            viewModel.onScreenShown()
            advanceUntilIdle()

            assertEquals(listOf("overdue", "soon", "edge"), viewModel.state.value.upcomingRecurring.map { it.id })
        }
    }

    @Test
    fun monthlySpendingAndBudgetUseTheirCanonicalScopesAndCalendarBoundaries() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            store.categories.create(categoryDraft("food", "Menjar"), createdAt = NOW)
            listOf("2026-06-30", "2026-07-01", "2026-07-31", "2026-08-01").forEachIndexed { index, date ->
                store.movements.create(expenseDraft("e$index", date, 1_000), createdAt = NOW)
            }
            store.movements.create(
                expenseDraft("extra", "2026-07-15", 5_000).copy(isOneTime = true), createdAt = NOW,
            )
            store.budgets.create(
                BudgetDraft("monthly", null, 3_000, null, scope = BudgetScope.OVERALL_MONTH,
                    includeExtraordinaryExpenses = false), createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(7_000L, state.monthExpenseCents)
            val plan = requireNotNull(state.monthPlan)
            assertEquals(2_000L, plan.plan.total.actualCents)
            assertEquals(1_000L, plan.plan.total.remainingCents)
            assertFalse(plan.inclusions.includeExtraordinaryExpenses)
            assertEquals(141_000L, state.netWorthCents)
            viewModel.onAccountSelected("savings")
            assertEquals(7_000L, viewModel.state.value.monthExpenseCents)
        }
    }

    @Test
    fun overrunAndCategoryWarningsRemainAvailableWithoutAnOverallBudget() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            store.categories.create(categoryDraft("food", "Menjar"), createdAt = NOW)
            store.movements.create(expenseDraft("expense", "2026-07-15", 4_000), createdAt = NOW)
            store.budgets.create(BudgetDraft("food-budget", "food", 3_000, null), createdAt = NOW)
            val viewModel = viewModel(store)
            viewModel.refresh()
            advanceUntilIdle()
            assertNull(viewModel.state.value.monthPlan)
            val warning = viewModel.state.value.planWarnings.single()
            assertEquals("food-budget", warning.budget?.id)
            assertEquals(PlanStatus.OVER, warning.part.status)
            assertEquals(1_000L, warning.excessCents)
        }
    }

    @Test
    fun emptyStoreLoadsWithoutInventingAccountOrBudgetData() = runTest(dispatcher) {
        freshStore().use { store ->
            val viewModel = viewModel(store)
            assertFalse(viewModel.state.value.hasLoaded)
            viewModel.refresh()
            advanceUntilIdle()
            val state = viewModel.state.value
            assertTrue(state.hasLoaded)
            assertFalse(state.isLoading)
            assertNull(state.mainAccount)
            assertNull(state.monthPlan)
            assertTrue(state.latestMovements.isEmpty())
            assertEquals(0L, state.monthExpenseCents)
        }
    }

    @Test
    fun monthDrillDownUsesInclusiveHistoryDatesAndActualExpenseSemantics() {
        val filters = YearMonth.of(2026, 2).actualPeriodFilters(MovementType.EXPENSE)
        assertEquals("2026-02-01", filters.dateFrom)
        assertEquals("2026-02-28", filters.dateTo)
        assertEquals(com.gestorfinances.app.ui.movements.MovementSourceMode.ACTUAL, filters.sourceMode)
        assertEquals(MovementType.EXPENSE, filters.type)
    }

    @Test
    fun pickingAnAccountKeepsThatAccountLeadingAfterAReload() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAccountSelected("savings")
            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("savings", state.mainAccount?.id)
        }
    }

    @Test
    fun anAccountBelowItsOwnThresholdIsTheOnlyAttentionItem() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(
                accountDraft(
                    id = "main",
                    name = "Compte principal",
                    startingBalanceCents = 5_000L,
                    isDefault = true,
                    lowBalanceThresholdCents = 10_000L,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)

            viewModel.onScreenShown()
            advanceUntilIdle()

            assertEquals("main", viewModel.state.value.lowBalanceAccount?.id)
        }
    }

    @Test
    fun noAttentionItemWhenEveryAccountSitsAboveItsThreshold() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            val viewModel = viewModel(store)

            viewModel.onScreenShown()
            advanceUntilIdle()

            assertNull(viewModel.state.value.lowBalanceAccount)
        }
    }

    @Test
    fun theHeroAccountsMonthIsItsCanonicalIncomeAndExpense() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            store.categories.create(categoryDraft("food", "Menjar"), createdAt = NOW)
            store.people.create(PersonDraft("anna", "Anna", null, null, null), createdAt = NOW)
            store.movements.create(expenseDraft("june", "2026-06-30", 9_000), createdAt = NOW)
            store.movements.create(expenseDraft("july", "2026-07-02", 1_500), createdAt = NOW)
            store.movements.create(
                expenseDraft("dinner", "2026-07-04", 1_000).copy(
                    splitWrite = MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            SplitEntryMethod.EXACT,
                            listOf(
                                SplitLineDraft(SplitParticipantKind.USER, null, 500),
                                SplitLineDraft(SplitParticipantKind.PERSON, "anna", 500),
                            ),
                        ),
                    ),
                ),
                createdAt = NOW,
            )
            store.movements.create(
                expenseDraft("salary", "2026-07-01", 2_000).copy(type = MovementType.INCOME, categoryId = null),
                createdAt = NOW,
            )
            store.movements.create(
                expenseDraft("in", "2026-07-03", 700).copy(type = MovementType.TRANSFER, accountId = "savings",
                    destinationAccountId = "main", categoryId = null),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            // Only the owner's half of the dinner counts, and the transfer from savings never does.
            assertEquals(AccountMonthFlow("main", inCents = 2_000, outCents = 2_000), viewModel.state.value.mainAccountMonthFlow)

            viewModel.onAccountSelected("savings")
            assertNull("A flow never shows under another account", viewModel.state.value.mainAccountMonthFlow)
            advanceUntilIdle()
            assertEquals(AccountMonthFlow("savings", inCents = 0, outCents = 0), viewModel.state.value.mainAccountMonthFlow)
        }
    }

    @Test
    fun theHeroAccountIsRememberedAcrossVisits() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            var saved: String? = null
            val first = viewModel(store, load = { saved }, save = { saved = it })
            first.onAccountSelected("savings")
            advanceUntilIdle()

            val next = viewModel(store, load = { saved }, save = { saved = it })
            next.onScreenShown()
            advanceUntilIdle()

            assertEquals("savings", next.state.value.mainAccount?.id)
        }
    }

    private fun viewModel(
        store: TestStore,
        load: () -> String? = { null },
        save: (String) -> Unit = {},
    ): DashboardViewModel =
        DashboardViewModel(
            analysisRepository = store.analysis,
            accountRepository = store.accounts,
            movementRepository = store.movements,
            tripRepository = store.trips,
            categoryRepository = store.categories,
            budgetRepository = store.budgets,
            templateRepository = store.templates,
            personRepository = store.people,
            loadSelectedAccountId = load,
            saveSelectedAccountId = save,
            todayProvider = { LocalDate.parse("2026-07-15") },
            ioDispatcher = dispatcher,
        )

    private fun templateDraft(id: String, nextDueDate: String): TemplateDraft =
        TemplateDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = 8_000,
            accountId = "main",
            destAccountId = null,
            categoryId = null,
            name = id,
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

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            analysis = AnalysisRepository(database.analysisQueries, database.analysisInsightsQueries),
            accounts = AccountRepository(database.accountsQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            trips = TripRepository(database.tripsQueries),
            categories = CategoryRepository(database.categoriesQueries),
            budgets = BudgetRepository(database.budgetsQueries),
            templates = TemplateRepository(database.templatesQueries),
            people = PersonRepository(database.peopleQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val analysis: AnalysisRepository,
        val accounts: AccountRepository,
        val movements: MovementRepository,
        val trips: TripRepository,
        val categories: CategoryRepository,
        val budgets: BudgetRepository,
        val templates: TemplateRepository,
        val people: PersonRepository,
    ) : AutoCloseable {
        /** Two healthy accounts; the second one is the default so ordering cannot fake the pick. */
        fun seedAccounts() {
            accounts.create(
                accountDraft(
                    id = "savings",
                    name = "Estalvis",
                    startingBalanceCents = 100_000L,
                    isDefault = false,
                    displayOrder = 0,
                ),
                createdAt = NOW,
            )
            accounts.create(
                accountDraft(
                    id = "main",
                    name = "Compte principal",
                    startingBalanceCents = 50_000L,
                    isDefault = true,
                    displayOrder = 1,
                ),
                createdAt = NOW,
            )
        }

        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"

        fun accountDraft(
            id: String,
            name: String,
            startingBalanceCents: Long,
            isDefault: Boolean,
            displayOrder: Long = 0,
            lowBalanceThresholdCents: Long? = null,
        ): AccountDraft =
            AccountDraft(
                id = id,
                name = name,
                startingBalanceCents = startingBalanceCents,
                type = AccountType.BANK,
                icon = null,
                color = null,
                isDefault = isDefault,
                displayOrder = displayOrder,
                lowBalanceThresholdCents = lowBalanceThresholdCents,
            )

        fun categoryDraft(id: String, name: String): CategoryDraft =
            CategoryDraft(
                id = id,
                name = name,
                kind = CategoryKind.EXPENSE,
                nature = CategoryNature.VARIABLE,
                parentId = null,
                icon = null,
                color = null,
                displayOrder = 0,
            )

        fun expenseDraft(id: String, date: String, cents: Long): MovementDraft =
            MovementDraft(
                id = id,
                type = MovementType.EXPENSE,
                amountCents = cents,
                date = date,
                accountId = "main",
                destinationAccountId = null,
                categoryId = "food",
                name = id,
                payee = null,
                notes = null,
                isOneTime = false,
            )
    }
}
