package com.gestorfinances.app.ui.goals

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun repeatedSaveCreatesOneGoalAndOneReservation() = runTest(dispatcher) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            GestorDatabase.Schema.create(driver)
            val db = GestorDatabase(driver)
            val goals = GoalRepository(db.goalsQueries, db.analysisQueries)
            val accounts = AccountRepository(db.accountsQueries)
            accounts.create(AccountDraft("a", "Savings", 100_000, AccountType.SAVINGS, null, null, true, 0, null), "2026-09-05T00:00:00Z")
            val vm = GoalsViewModel(goals, accounts, dispatcher)
            vm.onScreenShown()
            advanceUntilIdle()
            vm.onAddClicked()
            vm.onFormChanged(vm.state.value.form!!.copy(name = "Holiday", target = "1000", accountId = "a"))
            vm.onSaveClicked()
            assertTrue(vm.state.value.form!!.isSaving)
            vm.onSaveClicked()
            advanceUntilIdle()
            val goal = goals.listActive().single()
            vm.onGoalClicked(goal)
            advanceUntilIdle()
            vm.onAddAllocationClicked(goal)
            vm.onAllocationFormChanged(vm.state.value.allocationForm!!.copy(amount = "100"))
            vm.onSaveAllocationClicked()
            vm.onSaveAllocationClicked()
            advanceUntilIdle()
            assertEquals(1, goals.allocations(goal.id).size)
            assertEquals(10_000L, goals.get(goal.id)!!.savedCents)

            vm.onAddAllocationClicked(goals.get(goal.id)!!, release = true)
            vm.onAllocationFormChanged(vm.state.value.allocationForm!!.copy(amount = "150"))
            vm.onSaveAllocationClicked()
            advanceUntilIdle()
            assertFalse(vm.state.value.allocationForm!!.isSaving)
            assertEquals("150", vm.state.value.allocationForm!!.amount)
            vm.onAllocationFormChanged(vm.state.value.allocationForm!!.copy(amount = "40"))
            vm.onSaveAllocationClicked()
            advanceUntilIdle()
            assertEquals(6_000L, goals.get(goal.id)!!.savedCents)
            val release = goals.allocations(goal.id).single { it.amountCents < 0 }
            var undo: (() -> Unit)? = null
            vm.onDeleteAllocationClicked(release) { undo = it }
            advanceUntilIdle()
            assertEquals(10_000L, goals.get(goal.id)!!.savedCents)
            assertNotNull(undo)
            undo!!()
            advanceUntilIdle()
            assertEquals(6_000L, goals.get(goal.id)!!.savedCents)
        }
    }
}
