package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.AnalysisQueries
import com.gestorfinances.app.data.db.GoalsQueries
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.domain.rules.GoalProgress
import java.time.LocalDate

enum class GoalStatus(val dbValue: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    COMPLETED("completed"),
    ;

    companion object {
        fun fromDb(value: String): GoalStatus =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown goal status: $value")
    }
}

data class GoalDraft(
    val id: String,
    val name: String,
    val targetAmountCents: Long,
    val targetDate: String?,
    val accountId: String?,
    val fundingMode: GoalFundingMode,
    val icon: String?,
    val color: String?,
    val displayOrder: Long = 0,
    val notes: String?,
)

/** A goal with its canonical saved/remaining amounts from `v_goal_progress`. */
data class GoalSummary(
    val id: String,
    val name: String,
    val targetAmountCents: Long,
    val targetDate: String?,
    val accountId: String?,
    val accountName: String?,
    val accountIcon: String?,
    val accountColor: String?,
    val fundingMode: GoalFundingMode,
    val status: GoalStatus,
    val icon: String?,
    val color: String?,
    val displayOrder: Long,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
    val savedCents: Long,
    val remainingCents: Long,
) {
    /** Progress plus the pace implied by [targetDate], evaluated against [today]. */
    fun progress(today: LocalDate): GoalProgress =
        GoalProgress.of(
            savedCents = savedCents,
            targetAmountCents = targetAmountCents,
            targetDate = targetDate?.let(LocalDate::parse),
            today = today,
        )
}

data class GoalAllocation(
    val id: String,
    val goalId: String,
    val accountId: String,
    val accountName: String,
    val date: String,
    val amountCents: Long,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class GoalAllocationDraft(
    val id: String,
    val goalId: String,
    val accountId: String,
    val date: String,
    val amountCents: Long,
    val notes: String? = null,
)

/**
 * How much of an account's canonical value is already reserved by allocation-funded goals.
 * [unallocatedCents] is what remains assignable and may go negative if the balance later drops.
 */
data class AccountAllocation(
    val accountId: String,
    val balanceCents: Long,
    val allocatedCents: Long,
    val unallocatedCents: Long,
)

/**
 * A dismissible warning: assigning this much would reserve more than the account still holds.
 * The action stays valid because an account value can legitimately drop after the plan was made.
 */
data class OverAllocationWarning(
    val accountId: String,
    val requestedCents: Long,
    val availableCents: Long,
) {
    val excessCents: Long get() = requestedCents - availableCents
}

/** Raised when releasing more than a goal holds, which would leave it with negative progress. */
class NegativeGoalAllocationException : IllegalArgumentException()
class GoalFundingConflictException : IllegalArgumentException()

/**
 * Savings goals and their planning allocations.
 *
 * Allocations sit outside the ledger: they never create movements, account flow, actual income or
 * expense, debt, or net-worth change. Saved and remaining amounts always come from the canonical
 * views, never from arithmetic here.
 */
class GoalRepository(
    private val queries: GoalsQueries,
    private val contractQueries: AnalysisQueries,
) {
    fun listActive(): List<GoalSummary> =
        queries.activeGoalSummaries(::mapGoalSummary).executeAsList()

    fun listArchived(): List<GoalSummary> =
        queries.archivedGoalSummaries(::mapGoalSummary).executeAsList()

    fun get(id: String): GoalSummary? =
        queries.goalById(id, ::mapGoalSummary).executeAsOneOrNull()

    fun allocations(goalId: String): List<GoalAllocation> =
        queries.allocationsForGoal(goalId, ::mapAllocation).executeAsList()

    fun allocation(id: String): GoalAllocation? =
        queries.allocationById(id, ::mapAllocation).executeAsOneOrNull()

    fun accountAllocation(accountId: String): AccountAllocation? =
        queries.accountAllocation(accountId, ::mapAccountAllocation).executeAsOneOrNull()

    fun accountAllocations(): List<AccountAllocation> =
        queries.accountAllocations(::mapAccountAllocation).executeAsList()

    /**
     * Accounts already dedicated to an active goal, which therefore cannot host allocations or a
     * second dedicated goal. [excludingGoalId] keeps a goal from blocking its own account while
     * being edited.
     */
    fun dedicatedAccountIds(excludingGoalId: String? = null): Set<String> =
        queries.dedicatedAccountGoals { id, accountId -> id to accountId }
            .executeAsList()
            .filterNot { (id, _) -> id == excludingGoalId }
            .mapNotNull { (_, accountId) -> accountId }
            .toSet()

    fun accountReservations(goalId: String): Map<String, Long> =
        contractQueries.goalAccountAllocations(goalId) { accountId, cents -> accountId to (cents ?: 0L) }
            .executeAsList().toMap()

    private fun validateReservations(goalId: String) {
        if (accountReservations(goalId).values.any { it < 0 }) throw NegativeGoalAllocationException()
    }

    private fun validateFunding() {
        val goals = listActive()
        val dedicated = goals.filter { it.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT }
        if (dedicated.map { it.accountId }.distinct().size != dedicated.size) throw GoalFundingConflictException()
        dedicated.forEach { goal ->
            if (accountReservations(goal.id).values.any { it != 0L } ||
                (accountAllocation(requireNotNull(goal.accountId))?.allocatedCents ?: 0L) != 0L
            ) throw GoalFundingConflictException()
        }
    }

    fun create(draft: GoalDraft, createdAt: String) = queries.transaction {
        queries.insertGoal(
            id = draft.id,
            name = draft.name,
            target_amount_cents = draft.targetAmountCents,
            target_date = draft.targetDate,
            account_id = draft.accountId,
            funding_mode = draft.fundingMode.dbValue,
            status = GoalStatus.ACTIVE.dbValue,
            icon = draft.icon,
            color = draft.color,
            display_order = draft.displayOrder,
            notes = draft.notes,
            created_at = createdAt,
            updated_at = createdAt,
        )

        validateFunding()
    }

    fun update(draft: GoalDraft, updatedAt: String) = queries.transaction {
        if (get(draft.id)?.fundingMode != draft.fundingMode && accountReservations(draft.id).values.any { it != 0L }) throw GoalFundingConflictException()

        queries.updateGoal(
            id = draft.id,
            name = draft.name,
            target_amount_cents = draft.targetAmountCents,
            target_date = draft.targetDate,
            account_id = draft.accountId,
            funding_mode = draft.fundingMode.dbValue,
            icon = draft.icon,
            color = draft.color,
            display_order = draft.displayOrder,
            notes = draft.notes,
            updated_at = updatedAt,
        )

        validateFunding()
    }

    fun setStatus(id: String, status: GoalStatus, updatedAt: String) {
        queries.updateGoalStatus(id = id, status = status.dbValue, updated_at = updatedAt)
    }

    fun archive(id: String, archivedAt: String) {
        queries.archiveGoal(id = id, archived_at = archivedAt, updated_at = archivedAt)
    }

    fun restore(id: String, deletedAt: String, restoredAt: String) = queries.transaction {
        queries.restoreGoal(id = id, archived_at = deletedAt, updated_at = restoredAt)

        validateFunding()
    }

    /**
     * Over-allocation warning for reserving [amountCents] against [accountId], or null when the
     * account still holds enough. [replacingAllocationId] excludes the allocation being edited so
     * its own current reservation is not counted twice.
     */
    fun overAllocationWarning(
        accountId: String,
        amountCents: Long,
        replacingAllocationId: String? = null,
    ): OverAllocationWarning? {
        val account = accountAllocation(accountId) ?: return null
        val replaced = replacingAllocationId
            ?.let(::allocation)
            ?.takeIf { it.accountId == accountId }
            ?.amountCents
            ?: 0
        val additionalReservation = amountCents - replaced
        if (additionalReservation <= 0) return null
        val available = account.unallocatedCents
        return if (additionalReservation > available) {
            OverAllocationWarning(accountId, additionalReservation, available)
        } else {
            null
        }
    }

    /**
     * Adds a dated allocation. A negative amount releases part of the reservation and may not take
     * the goal below zero, which would be structurally meaningless rather than merely risky.
     */
    fun allocate(draft: GoalAllocationDraft, createdAt: String) = queries.transaction {
        if (get(draft.goalId)?.fundingMode != GoalFundingMode.ALLOCATIONS || draft.accountId in dedicatedAccountIds()) throw GoalFundingConflictException()

        queries.insertAllocation(
            id = draft.id,
            goal_id = draft.goalId,
            account_id = draft.accountId,
            date = draft.date,
            amount_cents = draft.amountCents,
            notes = draft.notes,
            created_at = createdAt,
            updated_at = createdAt,
        )

        validateReservations(draft.goalId)
        validateFunding()
    }

    fun updateAllocation(draft: GoalAllocationDraft, updatedAt: String) = queries.transaction {
        if (get(draft.goalId)?.fundingMode != GoalFundingMode.ALLOCATIONS || draft.accountId in dedicatedAccountIds()) throw GoalFundingConflictException()

        val existing = requireNotNull(allocation(draft.id))
        require(existing.goalId == draft.goalId)
        queries.updateAllocation(
            id = draft.id,
            account_id = draft.accountId,
            date = draft.date,
            amount_cents = draft.amountCents,
            notes = draft.notes,
            updated_at = updatedAt,
        )

        validateReservations(draft.goalId)
        validateFunding()
    }

    fun archiveAllocation(id: String, archivedAt: String) = queries.transaction {
        queries.archiveAllocation(id = id, archived_at = archivedAt, updated_at = archivedAt)

        queries.allocationGoalId(id).executeAsOneOrNull()?.let(::validateReservations)
        validateFunding()
    }

    fun restoreAllocation(id: String, deletedAt: String, restoredAt: String) = queries.transaction {
        queries.restoreAllocation(id = id, archived_at = deletedAt, updated_at = restoredAt)

        queries.allocationGoalId(id).executeAsOneOrNull()?.let(::validateReservations)
        validateFunding()
    }


}

@Suppress("LongParameterList")
private fun mapGoalSummary(
    id: String,
    name: String,
    targetAmountCents: Long,
    targetDate: String?,
    accountId: String?,
    accountName: String?,
    accountIcon: String?,
    accountColor: String?,
    fundingMode: String,
    status: String,
    icon: String?,
    color: String?,
    displayOrder: Long,
    notes: String?,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
    savedCents: Long,
    // MAX() makes SQLDelight infer a nullable column; the view can only ever return a value.
    remainingCents: Long?,
): GoalSummary =
    GoalSummary(
        id = id,
        name = name,
        targetAmountCents = targetAmountCents,
        targetDate = targetDate,
        accountId = accountId,
        accountName = accountName,
        accountIcon = accountIcon,
        accountColor = accountColor,
        fundingMode = GoalFundingMode.fromDb(fundingMode),
        status = GoalStatus.fromDb(status),
        icon = icon,
        color = color,
        displayOrder = displayOrder,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        savedCents = savedCents,
        remainingCents = remainingCents ?: 0,
    )

private fun mapAllocation(
    id: String,
    goalId: String,
    accountId: String,
    accountName: String,
    date: String,
    amountCents: Long,
    notes: String?,
    createdAt: String,
    updatedAt: String,
): GoalAllocation =
    GoalAllocation(
        id = id,
        goalId = goalId,
        accountId = accountId,
        accountName = accountName,
        date = date,
        amountCents = amountCents,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun mapAccountAllocation(
    accountId: String,
    balanceCents: Long,
    allocatedCents: Long,
    unallocatedCents: Long,
): AccountAllocation =
    AccountAllocation(
        accountId = accountId,
        balanceCents = balanceCents,
        allocatedCents = allocatedCents,
        unallocatedCents = unallocatedCents,
    )
