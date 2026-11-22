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
            vm.onGoalOpened(goal.id)
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
            var undo: (suspend () -> Unit)? = null
            vm.onDeleteAllocationClicked(release) { undo = it }
            advanceUntilIdle()
            assertEquals(10_000L, goals.get(goal.id)!!.savedCents)
            assertNotNull(undo)
            undo!!()
            advanceUntilIdle()
            assertEquals(6_000L, goals.get(goal.id)!!.savedCents)
        }
    }

    @Test fun completingAGoalCanReleaseWhatItReserves() = runTest(dispatcher) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            GestorDatabase.Schema.create(driver)
            val db = GestorDatabase(driver)
            val goals = GoalRepository(db.goalsQueries, db.analysisQueries)
            val accounts = AccountRepository(db.accountsQueries)
            accounts.create(AccountDraft("a", "Savings", 100_000, AccountType.SAVINGS, null, null, true, 0, null), "2026-09-05T00:00:00Z")
            accounts.create(AccountDraft("b", "Bank", 50_000, AccountType.BANK, null, null, false, 1, null), "2026-09-05T00:00:00Z")
            goals.create(GoalDraft("g", "Bike", 50_000, null, null, com.gestorfinances.app.domain.rules.GoalFundingMode.ALLOCATIONS, null, null, notes = null), "2026-09-05T00:00:00Z")
            goals.allocate(GoalAllocationDraft("r1", "g", "a", "2026-09-05", 30_000), "2026-09-05T00:00:00Z")
            goals.allocate(GoalAllocationDraft("r2", "g", "b", "2026-09-06", 10_000), "2026-09-06T00:00:00Z")
            val vm = GoalsViewModel(goals, accounts, dispatcher, today = { java.time.LocalDate.parse("2026-09-29") })
            vm.onGoalOpened("g")
            advanceUntilIdle()

            vm.onCompleteClicked(goals.get("g")!!)
            assertNotNull(vm.state.value.completeCandidate)
            vm.onCompleteConfirmed(release = true)
            advanceUntilIdle()

            val completed = goals.get("g")!!
            assertEquals(GoalStatus.COMPLETED, completed.status)
            assertEquals(0L, completed.savedCents)
            assertEquals(mapOf("a" to 0L, "b" to 0L), goals.accountReservations("g"))
            assertTrue(goals.allocations("g").filter { it.amountCents < 0 }.all { it.date == "2026-09-29" })
            assertTrue(goals.accountAllocations().all { it.allocatedCents == 0L })
        }
    }
}
