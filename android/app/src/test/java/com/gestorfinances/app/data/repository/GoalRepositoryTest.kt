package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.domain.rules.GoalFundingMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal progress and allocation consistency against the canonical views.
 *
 * The central invariant of the feature is that allocations stay outside the ledger: they must not
 * move balances, actual income or expense, debt, or net worth. Everything else here follows from
 * that plus the canonical `v_goal_progress` / `v_account_allocation` definitions.
 */
class GoalRepositoryTest {

    @Test
    fun allocatingMoneyLeavesTheLedgerUntouched() {
        freshStore().use { store ->
            seedAccount(store)
            val balanceBefore = store.accounts.listActive().single().currentBalanceCents

            store.goals.create(allocationGoal("holiday", targetCents = 80_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 30_000), createdAt = NOW)

            assertEquals(balanceBefore, store.accounts.listActive().single().currentBalanceCents)
            assertEquals(0L, store.analysisMovementCount())
            assertEquals(30_000L, store.goals.get("holiday")!!.savedCents)
        }
    }

    @Test
    fun dedicatedAccountGoalFollowsTheAccountValue() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(
                GoalDraft(
                    id = "car",
                    name = "Cotxe",
                    targetAmountCents = 500_000,
                    targetDate = null,
                    accountId = ACCOUNT_ID,
                    fundingMode = GoalFundingMode.DEDICATED_ACCOUNT,
                    icon = null,
                    color = null,
                    notes = null,
                ),
                createdAt = NOW,
            )

            assertEquals(100_000L, store.goals.get("car")!!.savedCents)

            store.movements.create(expense("spend", 40_000), createdAt = NOW)

            assertEquals(60_000L, store.goals.get("car")!!.savedCents)
            assertEquals(440_000L, store.goals.get("car")!!.remainingCents)
        }
    }

    @Test
    fun severalGoalsShareOneAccountAndTheRemainderStaysVisible() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 80_000), createdAt = NOW)
            store.goals.create(allocationGoal("laptop", 120_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 30_000), createdAt = NOW)
            store.goals.allocate(allocation("al-2", "laptop", 20_000), createdAt = NOW)

            val account = store.goals.accountAllocation(ACCOUNT_ID)!!
            assertEquals(100_000L, account.balanceCents)
            assertEquals(50_000L, account.allocatedCents)
            assertEquals(50_000L, account.unallocatedCents)
        }
    }

    @Test
    fun editingAnAllocationRepricesProgressWithoutDoubleCounting() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 80_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 30_000), createdAt = NOW)

            store.goals.updateAllocation(
                allocation("al-1", "holiday", 45_000),
                updatedAt = NOW,
            )

            assertEquals(45_000L, store.goals.get("holiday")!!.savedCents)
            assertEquals(45_000L, store.goals.accountAllocation(ACCOUNT_ID)!!.allocatedCents)
        }
    }

    @Test
    fun aNegativeAllocationReleasesMoneyButCannotGoBelowZero() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 80_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 30_000), createdAt = NOW)
            store.goals.allocate(allocation("al-2", "holiday", -10_000), createdAt = NOW)

            assertEquals(20_000L, store.goals.get("holiday")!!.savedCents)
            assertThrows(NegativeGoalAllocationException::class.java) {
                store.goals.allocate(allocation("al-3", "holiday", -25_000), createdAt = NOW)
            }
            assertEquals(20_000L, store.goals.get("holiday")!!.savedCents)
        }
    }

    @Test
    fun overAllocationWarnsButRemainsAllowed() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 200_000), createdAt = NOW)

            assertNull(store.goals.overAllocationWarning(ACCOUNT_ID, 100_000))

            val warning = store.goals.overAllocationWarning(ACCOUNT_ID, 130_000)
            assertNotNull(warning)
            assertEquals(100_000L, warning!!.availableCents)
            assertEquals(30_000L, warning.excessCents)

            // The action stays valid: the user may confirm it, and the remainder goes negative.
            store.goals.allocate(allocation("al-1", "holiday", 130_000), createdAt = NOW)
            assertEquals(-30_000L, store.goals.accountAllocation(ACCOUNT_ID)!!.unallocatedCents)
        }
    }

    @Test
    fun editingAnAllocationDoesNotCountItsOwnReservationTwice() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 200_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 100_000), createdAt = NOW)

            // Nothing is free, yet re-saving the same allocation unchanged must not warn.
            assertEquals(0L, store.goals.accountAllocation(ACCOUNT_ID)!!.unallocatedCents)
            assertNull(
                store.goals.overAllocationWarning(ACCOUNT_ID, 100_000, replacingAllocationId = "al-1"),
            )
            assertNotNull(
                store.goals.overAllocationWarning(ACCOUNT_ID, 100_001, replacingAllocationId = "al-1"),
            )
        }
    }

    @Test
    fun pausingAndCompletingKeepReservationsInPlace() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 80_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 30_000), createdAt = NOW)

            store.goals.setStatus("holiday", GoalStatus.PAUSED, updatedAt = NOW)
            assertEquals(GoalStatus.PAUSED, store.goals.get("holiday")!!.status)
            assertEquals(30_000L, store.goals.accountAllocation(ACCOUNT_ID)!!.allocatedCents)

            // Reaching the target does not free the money: it is still set aside for the goal.
            store.goals.setStatus("holiday", GoalStatus.COMPLETED, updatedAt = NOW)
            assertEquals(30_000L, store.goals.accountAllocation(ACCOUNT_ID)!!.allocatedCents)
        }
    }

    @Test
    fun archivingAGoalReleasesItsReservationAndRestoringBringsItBack() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 80_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 30_000), createdAt = NOW)

            store.goals.archive("holiday", archivedAt = DELETED_AT)
            assertTrue(store.goals.listActive().isEmpty())
            assertEquals(0L, store.goals.accountAllocation(ACCOUNT_ID)!!.allocatedCents)
            assertEquals(100_000L, store.goals.accountAllocation(ACCOUNT_ID)!!.unallocatedCents)

            store.goals.restore("holiday", deletedAt = DELETED_AT, restoredAt = NOW)
            assertEquals(1, store.goals.listActive().size)
            assertEquals(30_000L, store.goals.accountAllocation(ACCOUNT_ID)!!.allocatedCents)
            assertEquals(30_000L, store.goals.get("holiday")!!.savedCents)
        }
    }

    @Test
    fun archivingAnAllocationReleasesOnlyThatReservation() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("holiday", 80_000), createdAt = NOW)
            store.goals.allocate(allocation("al-1", "holiday", 30_000), createdAt = NOW)
            store.goals.allocate(allocation("al-2", "holiday", 20_000), createdAt = NOW)

            store.goals.archiveAllocation("al-1", archivedAt = DELETED_AT)

            assertEquals(20_000L, store.goals.get("holiday")!!.savedCents)
            assertEquals(listOf("al-2"), store.goals.allocations("holiday").map { it.id })

            store.goals.restoreAllocation("al-1", deletedAt = DELETED_AT, restoredAt = NOW)
            assertEquals(50_000L, store.goals.get("holiday")!!.savedCents)
        }
    }

    @Test
    fun anAccountDedicatedToAGoalIsReportedSoItCannotAlsoHostAllocations() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(
                GoalDraft(
                    id = "car",
                    name = "Cotxe",
                    targetAmountCents = 500_000,
                    targetDate = null,
                    accountId = ACCOUNT_ID,
                    fundingMode = GoalFundingMode.DEDICATED_ACCOUNT,
                    icon = null,
                    color = null,
                    notes = null,
                ),
                createdAt = NOW,
            )

            assertEquals(setOf(ACCOUNT_ID), store.goals.dedicatedAccountIds())
            // The goal being edited must not block its own account.
            assertEquals(emptySet<String>(), store.goals.dedicatedAccountIds(excludingGoalId = "car"))
        }
    }

    @Test
    fun repositoryProgressMatchesTheCanonicalViewAndAddsThePace() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(
                allocationGoal("holiday", 100_000).copy(targetDate = "2026-11-15"),
                createdAt = NOW,
            )

            val goal = store.goals.get("holiday")!!
            val progress = goal.progress(LocalDate.parse("2026-09-03"))

            assertEquals(goal.savedCents, progress.savedCents)
            assertEquals(goal.remainingCents, progress.remainingCents)
            assertEquals(3, progress.monthsRemaining)
            assertEquals(33_334L, progress.monthlyPaceCents)
        }
    }

    @Test
    fun deletingConsumedReservationRollsBackAndRestoringReleaseCannotGoNegative() {
        freshStore().use { store ->
            seedAccount(store)
            store.goals.create(allocationGoal("g", 100_000), NOW)
            store.goals.allocate(allocation("reserve", "g", 10_000), NOW)
            store.goals.allocate(allocation("release", "g", -8_000), NOW)
            assertThrows(NegativeGoalAllocationException::class.java) { store.goals.archiveAllocation("reserve", DELETED_AT) }
            assertEquals(2_000L, store.goals.get("g")!!.savedCents)
            assertNotNull(store.goals.allocation("reserve"))
            store.goals.archiveAllocation("release", DELETED_AT)
            store.goals.archiveAllocation("reserve", DELETED_AT)
            assertThrows(NegativeGoalAllocationException::class.java) { store.goals.restoreAllocation("release", DELETED_AT, NOW) }
            assertNull(store.goals.allocation("release"))
            store.goals.restoreAllocation("reserve", DELETED_AT, NOW)
            store.goals.restoreAllocation("release", DELETED_AT, NOW)
            assertEquals(2_000L, store.goals.get("g")!!.savedCents)
        }
    }

    @Test
    fun releasesAndAccountChangingEditsCannotBorrowAnotherAccountsReservation() {
        freshStore().use { store ->
            seedAccount(store)
            store.accounts.create(AccountDraft("other", "Other", 50_000, AccountType.SAVINGS, null, null, false, 1, null), NOW)
            store.goals.create(allocationGoal("g", 100_000), NOW)
            store.goals.allocate(allocation("reserve", "g", 10_000), NOW)
            assertThrows(NegativeGoalAllocationException::class.java) {
                store.goals.allocate(allocation("release", "g", -8_000).copy(accountId = "other"), NOW)
            }
            store.goals.allocate(allocation("release", "g", -8_000), NOW)
            assertThrows(NegativeGoalAllocationException::class.java) {
                store.goals.updateAllocation(allocation("reserve", "g", 10_000).copy(accountId = "other"), NOW)
            }
            assertEquals(mapOf(ACCOUNT_ID to 2_000L), store.goals.accountReservations("g"))
            assertEquals(50_000L, store.goals.accountAllocation("other")!!.unallocatedCents)
        }
    }

    @Test
    fun dedicatedAccountConflictsAndModeChangesRollBack() {
        freshStore().use { store ->
            seedAccount(store)
            val draft = allocationGoal("g", 100_000)
            store.goals.create(draft, NOW)
            store.goals.allocate(allocation("reserve", "g", 10_000), NOW)
            assertThrows(GoalFundingConflictException::class.java) { store.goals.update(draft.copy(fundingMode = GoalFundingMode.DEDICATED_ACCOUNT), NOW) }
            assertThrows(GoalFundingConflictException::class.java) { store.goals.create(draft.copy(id = "d", fundingMode = GoalFundingMode.DEDICATED_ACCOUNT), NOW) }
            store.goals.archiveAllocation("reserve", DELETED_AT)
            store.goals.create(draft.copy(id = "d", fundingMode = GoalFundingMode.DEDICATED_ACCOUNT), NOW)
            assertThrows(GoalFundingConflictException::class.java) { store.goals.create(draft.copy(id = "d2", fundingMode = GoalFundingMode.DEDICATED_ACCOUNT), NOW) }
            assertThrows(GoalFundingConflictException::class.java) { store.goals.restoreAllocation("reserve", DELETED_AT, NOW) }
            assertNull(store.goals.allocation("reserve"))
        }
    }

    private fun allocationGoal(id: String, targetCents: Long): GoalDraft =
        GoalDraft(
            id = id,
            name = id,
            targetAmountCents = targetCents,
            targetDate = null,
            accountId = ACCOUNT_ID,
            fundingMode = GoalFundingMode.ALLOCATIONS,
            icon = null,
            color = null,
            notes = null,
        )

    private fun allocation(id: String, goalId: String, amountCents: Long): GoalAllocationDraft =
        GoalAllocationDraft(
            id = id,
            goalId = goalId,
            accountId = ACCOUNT_ID,
            date = "2026-03-01",
            amountCents = amountCents,
        )

    private fun expense(id: String, amountCents: Long): MovementDraft =
        MovementDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = amountCents,
            date = "2026-03-10",
            accountId = ACCOUNT_ID,
            destinationAccountId = null,
            categoryId = null,
            name = id,
            payee = null,
            notes = null,
            isOneTime = false,
        )

    private fun seedAccount(store: TestStore) {
        store.accounts.create(
            AccountDraft(
                id = ACCOUNT_ID,
                name = "Estalvi",
                startingBalanceCents = 100_000,
                type = AccountType.SAVINGS,
                icon = null,
                color = null,
                isDefault = true,
                displayOrder = 0,
                lowBalanceThresholdCents = null,
            ),
            createdAt = NOW,
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
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            goals = GoalRepository(database.goalsQueries, database.analysisQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val movements: MovementRepository,
        val goals: GoalRepository,
    ) : AutoCloseable {
        fun analysisMovementCount(): Long =
            driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM movements",
                mapper = { cursor ->
                    cursor.next()
                    app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
            ).value

        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val ACCOUNT_ID = "savings"
        const val NOW = "2026-01-01T00:00:00Z"
        const val DELETED_AT = "2026-04-01T00:00:00Z"
    }
}
