package com.gestorfinances.desktop

import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.movements.ExpenseKind
import com.gestorfinances.app.ui.onboarding.DefaultCategorySeed
import com.gestorfinances.app.ui.onboarding.OnboardingFormState
import com.gestorfinances.app.ui.onboarding.OnboardingViewModel
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared ViewModels on the desktop database and dispatchers: first run, then a movement's life. */
class DesktopLedgerTest {
    private val dir: File = Files.createTempDirectory("summa-desktop-ledger").toFile()
    private val db = DesktopDatabase(File(dir, "gestor-finances.db"))

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun firstAccountThenRecordEditAndDeleteAMovement() {
        val onboarding = OnboardingViewModel(
            accountRepository = AccountRepository(db.database.accountsQueries, db.database.sharedAccountsQueries),
            categoryRepository = CategoryRepository(db.database.categoriesQueries),
        )
        waitUntil { !onboarding.state.value.isLoading }
        assertTrue(onboarding.state.value.needsOnboarding)
        onboarding.onFormChanged(OnboardingFormState(accountName = "Principal", startingBalance = "1.000,00"))
        onboarding.onCreateClicked(
            listOf(DefaultCategorySeed("Mercat", CategoryKind.EXPENSE, CategoryNature.VARIABLE, "shopping_cart", "#66854B", 0)),
        )
        waitUntil { !onboarding.state.value.needsOnboarding }

        val ledger = movementsViewModel(db)
        ledger.onAddClicked()
        waitUntil { ledger.editor.form.value != null }
        val category = ledger.state.value.categories.single()
        ledger.editor.onFormChanged(ledger.editor.form.value!!.copy(amount = "12,50", name = "Pa", categoryId = category.id))
        ledger.editor.onSaveClicked()
        waitUntil { ledger.editor.form.value == null && ledger.state.value.movements.size == 1 }
        val saved = ledger.state.value.movements.single()
        assertEquals(MovementType.EXPENSE, saved.type)
        assertEquals(1250L, saved.amountCents)
        assertEquals("Principal", saved.accountName)
        assertEquals("Mercat", saved.categoryName)

        ledger.onEditClicked(saved)
        waitUntil { ledger.editor.form.value != null }
        ledger.editor.onFormChanged(ledger.editor.form.value!!.copy(amount = "13,00"))
        ledger.editor.onSaveClicked()
        waitUntil { ledger.editor.form.value == null && ledger.state.value.movements.single().amountCents == 1300L }

        // The account page's ledger reconciles with its balance, and the movement's detail opens from it.
        val accounts = accountsViewModel(db)
        accounts.onAccountDetailOpened(saved.accountId!!)
        waitUntil { accounts.state.value.flowDetail?.isLoading == false }
        val flow = accounts.state.value.flowDetail!!
        assertEquals(98_700L, flow.account.currentBalanceCents)
        assertEquals(-1300L, flow.entries.single { it.movement.id == saved.id }.deltaCents)
        // The statement's balance column counts back from the canonical balance.
        assertEquals(listOf(98_700L), runningBalances(flow.account, flow.entries))
        assertNull(runningBalances(flow.account.copy(startingBalanceCents = 1L), flow.entries))
        // The accounts table: what this month did to the account, and the day it last moved.
        val activity = loadAccountActivity(AnalysisRepository(db.database.analysisQueries, db.database.analysisInsightsQueries), LocalDate.now())
        assertEquals(-1300L, activity.monthChangeCents[flow.account.id])
        assertEquals(saved.date, activity.lastDate[flow.account.id])
        ledger.onDetailClicked(flow.entries.first().movement)
        assertEquals(saved.id, ledger.state.value.detailMovement?.id)
        ledger.onDetailDismissed()

        ledger.onArchiveClicked(ledger.state.value.movements.single())
        waitUntil { ledger.state.value.archiveCandidate != null }
        ledger.onArchiveConfirmed()
        waitUntil { ledger.state.value.movements.isEmpty() }
        assertNull(ledger.state.value.errorMessage)

        // A recurring item, and its first payment recorded into the ledger.
        val recurring = recurringViewModel(db)
        recurring.onScreenShown()
        waitUntil { !recurring.state.value.isLoading && recurring.state.value.accounts.isNotEmpty() }
        recurring.onAddClicked()
        recurring.onFormChanged(recurring.state.value.form!!.copy(name = "Gimnàs", amount = "30,00", categoryId = category.id))
        recurring.onSaveClicked()
        waitUntil { recurring.state.value.templates.size == 1 }
        recurring.onConfirmRequested(recurring.state.value.templates.single().id)
        waitUntil { recurring.state.value.confirmPrompt != null }
        recurring.onConfirmSaveClicked()
        waitUntil { recurring.state.value.confirmPrompt == null }
        ledger.onScreenShown()
        waitUntil { ledger.state.value.movements.size == 1 }
        assertEquals(3000L, ledger.state.value.movements.single().amountCents)
        assertTrue(ledger.state.value.movements.single().isRecurring)
        // A monthly item of 30,00 comes to eleven or twelve payments over the coming year.
        waitUntil { recurring.state.value.templates.single().nextDueDate > LocalDate.now().toString() }
        assertTrue(recurring.state.value.templates.single().yearlyCents(LocalDate.now()) in listOf(33_000L, 36_000L))

        // A partida for the category: the month's plan counts what the ledger holds against it.
        val budgets = budgetsViewModel(db)
        budgets.onScreenShown()
        waitUntil { !budgets.state.value.isLoading }
        budgets.onAddClicked(category.id)
        waitUntil { budgets.state.value.form != null }
        budgets.onFormChanged(budgets.state.value.form!!.copy(limit = "100,00"))
        budgets.onSaveClicked()
        waitUntil { budgets.state.value.form == null && budgets.state.value.plan?.compartments?.size == 1 }
        val plan = budgets.state.value.plan!!
        assertEquals(3000L, plan.plan.compartments.getValue(plan.compartments.single().id).actualCents)

        // The analysis tables: every month's categories add up to the month's canonical expense.
        val analysis = AnalysisRepository(db.database.analysisQueries, db.database.analysisInsightsQueries)
        val thisMonth = YearMonth.now()
        val tables = loadAnalysisTables(
            analysis = analysis,
            categories = CategoryRepository(db.database.categoriesQueries).listActive(),
            months = (11 downTo 0).map { thisMonth.minusMonths(it.toLong()) },
            accountId = null,
            today = LocalDate.now(),
        )
        assertEquals(3000L, tables.expense.last())
        // The month still running is in the table but not in its averages.
        assertEquals(List(11) { true } + false, tables.closed)
        assertEquals(0L, closedAverage(tables.expense, tables.closed))
        assertEquals(150L, closedAverage(listOf(100L, 200L, 9L), listOf(true, true, false)))
        assertEquals(9L, closedAverage(listOf(9L), listOf(false)))
        assertEquals(tables.expense, tables.months.indices.map { month -> tables.categories.sumOf { it.cents[month] } })
        assertEquals(tables.expense, tables.byKind.map { it.totalCents })
        assertEquals("Mercat", tables.categories.single().name)

        // A person, an expense paid for them, and settling it up.
        val people = peopleViewModel(db)
        people.onAddClicked()
        people.onFormChanged(people.state.value.form!!.copy(name = "Marc"))
        people.onSaveClicked()
        waitUntil { people.state.value.people.size == 1 }
        val marc = people.state.value.people.single()
        ledger.onAddClicked()
        waitUntil { ledger.editor.form.value != null }
        ledger.editor.onFormChanged(
            ledger.editor.form.value!!.copy(
                amount = "20,00",
                name = "Entrades",
                categoryId = category.id,
                expenseKind = ExpenseKind.FOR_OTHER,
                forOtherPersonId = marc.id,
            ),
        )
        ledger.editor.onSaveClicked()
        waitUntil { ledger.editor.form.value == null }
        people.onPersonDetailOpened(marc.id)
        waitUntil { people.state.value.detail?.history?.size == 1 }
        val owed = people.state.value.detail!!.person.balanceCents
        assertEquals(2000L, owed)
        people.onSettleUpClicked(people.state.value.detail!!.person)
        people.onSettlementSaveClicked()
        waitUntil { people.state.value.settlementForm == null && people.state.value.detail?.person?.balanceCents == 0L }
        assertEquals(2, people.state.value.detail!!.history.size)

        // A trip, and a movement recorded in it.
        val trips = tripsViewModel(db)
        trips.onAddClicked()
        trips.onFormChanged(trips.state.value.form!!.copy(name = "Lisboa"))
        trips.onSaveClicked()
        waitUntil { trips.state.value.trips.size == 1 }
        val trip = trips.state.value.trips.single()
        ledger.onAddClicked(tripId = trip.id)
        waitUntil { ledger.editor.form.value != null }
        assertEquals(trip.id, ledger.editor.form.value!!.tripId)
        ledger.editor.onFormChanged(ledger.editor.form.value!!.copy(amount = "45,00", name = "Sopar", categoryId = category.id))
        ledger.editor.onSaveClicked()
        waitUntil { ledger.editor.form.value == null }
        trips.onDetailOpened(trip.id)
        waitUntil { trips.state.value.detail?.movements?.size == 1 }
        assertEquals(4500L, trips.state.value.detail!!.summary.actualCents)

        // The category page reads the same ledger; a tag is created and listed.
        val categories = categoriesViewModel(db, analysis)
        categories.onCategoryDetailOpened(category.id)
        waitUntil { categories.state.value.flowDetail != null && !categories.state.value.isLoading }
        assertEquals(3, categories.state.value.flowDetail!!.entries.size)
        // What was paid for Marc is his, not the owner's spending.
        assertEquals(3000L + 4500L, categories.state.value.yearSpend[category.id])
        val tags = tagsViewModel(db)
        tags.onScreenShown(null)
        waitUntil { !tags.state.value.isLoading }
        tags.onAddClicked()
        tags.onFormChanged(tags.state.value.form!!.copy(name = "Àpats"))
        tags.onSaveClicked()
        waitUntil { tags.state.value.tags.size == 1 }

        // A goal, and money reserved for it from the account.
        val goals = goalsViewModel(db)
        goals.onScreenShown()
        waitUntil { !goals.state.value.isLoading }
        goals.onAddClicked()
        goals.onFormChanged(goals.state.value.form!!.copy(name = "Vacances", target = "500,00"))
        goals.onSaveClicked()
        waitUntil { goals.state.value.goals.size == 1 }
        val goal = goals.state.value.goals.single()
        goals.onGoalOpened(goal.id)
        goals.onAddAllocationClicked(goal)
        waitUntil { goals.state.value.allocationForm != null }
        goals.onAllocationFormChanged(goals.state.value.allocationForm!!.copy(accountId = saved.accountId, amount = "120,00"))
        goals.onSaveAllocationClicked()
        waitUntil { goals.state.value.allocationForm == null && goals.state.value.goals.single().savedCents == 12_000L }
        assertEquals(12_000L, goals.state.value.accountAllocations.getValue(saved.accountId!!).allocatedCents)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for the ViewModel" }
            Thread.sleep(20)
        }
    }
}
