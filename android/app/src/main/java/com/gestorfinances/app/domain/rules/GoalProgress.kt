package com.gestorfinances.app.domain.rules

import java.time.LocalDate

/** How a savings goal knows how much it has saved. */
enum class GoalFundingMode(val dbValue: String) {
    /** Progress follows the canonical value of one account reserved for this goal alone. */
    DEDICATED_ACCOUNT("dedicated_account"),

    /** Progress is the signed sum of dated planning allocations, so goals can share an account. */
    ALLOCATIONS("allocations"),
    ;

    companion object {
        fun fromDb(value: String): GoalFundingMode =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown goal funding mode: $value")
    }
}

/**
 * Progress and required monthly pace for one savings goal, locked by
 * `shared/golden/goal_progress.json`.
 *
 * Reading a goal never moves the ledger: an allocation reserves meaning only, so nothing here
 * touches account flow, actual income or expense, debt, or net worth. [savedCents] must equal
 * `v_goal_progress.saved_cents` for the same goal; this rule adds only the pace, which is
 * awkward to express in SQL.
 */
data class GoalProgress(
    val savedCents: Long,
    val remainingCents: Long,
    val reached: Boolean,
    val monthsRemaining: Int?,
    val monthlyPaceCents: Long?,
    val overdue: Boolean,
) {
    companion object {
        fun evaluate(
            fundingMode: GoalFundingMode,
            targetAmountCents: Long,
            accountBalanceCents: Long,
            allocationCents: List<Long>,
            targetDate: LocalDate?,
            today: LocalDate,
        ): GoalProgress {
            val saved = when (fundingMode) {
                GoalFundingMode.DEDICATED_ACCOUNT -> accountBalanceCents
                GoalFundingMode.ALLOCATIONS -> allocationCents.sum()
            }
            return of(saved, targetAmountCents, targetDate, today)
        }

        /** Pace and remainder for a goal whose saved amount already comes from `v_goal_progress`. */
        fun of(
            savedCents: Long,
            targetAmountCents: Long,
            targetDate: LocalDate?,
            today: LocalDate,
        ): GoalProgress {
            val remaining = (targetAmountCents - savedCents).coerceAtLeast(0)
            val reached = remaining == 0L

            if (targetDate == null) {
                return GoalProgress(
                    savedCents = savedCents,
                    remainingCents = remaining,
                    reached = reached,
                    monthsRemaining = null,
                    monthlyPaceCents = null,
                    overdue = false,
                )
            }

            // Calendar months counted inclusively from this month through the target's month, so a
            // target inside the current month still leaves one month to fund it.
            val months = (monthsBetween(today, targetDate) + 1).coerceAtLeast(0)
            if (reached) {
                return GoalProgress(
                    savedCents = savedCents,
                    remainingCents = 0,
                    reached = true,
                    monthsRemaining = months,
                    monthlyPaceCents = 0,
                    overdue = false,
                )
            }

            // Rounding up keeps the target reachable: exact division would leave a cent short.
            val pace = if (months == 0) remaining else ceilingDivide(remaining, months)
            return GoalProgress(
                savedCents = savedCents,
                remainingCents = remaining,
                reached = false,
                monthsRemaining = months,
                monthlyPaceCents = pace,
                overdue = months == 0,
            )
        }

        private fun monthsBetween(from: LocalDate, to: LocalDate): Int =
            (to.year * 12 + to.monthValue) - (from.year * 12 + from.monthValue)

        private fun ceilingDivide(value: Long, divisor: Int): Long =
            (value + divisor - 1) / divisor
    }
}
