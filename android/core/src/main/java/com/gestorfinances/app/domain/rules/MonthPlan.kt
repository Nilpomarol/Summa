package com.gestorfinances.app.domain.rules

import java.time.LocalDate
import java.time.YearMonth

/**
 * How one part of the monthly plan is going. [plannedCents] is null when the part has no plan
 * (there is no monthly total, so the remainder has nothing to be measured against).
 */
data class PlanPart(
    val plannedCents: Long?,
    val actualCents: Long,
    /** Where spending should stand by today if it keeps to the plan. */
    val expectedByTodayCents: Long?,
    /** Actual plus what is still expected this month. */
    val forecastCents: Long,
    /** What is left to spend each day for the rest of the month, today included, once recurring payments due are set aside. */
    val perDayCents: Long?,
    /** The part's recurring payments this month: recorded plus still due. */
    val committedCents: Long = 0L,
    /** Of those, what is still to pay (overdue included). */
    val dueCents: Long = 0L,
) {
    val remainingCents: Long? get() = plannedCents?.minus(actualCents)

    val status: PlanStatus?
        get() = when {
            plannedCents == null -> null
            actualCents > plannedCents -> PlanStatus.OVER
            forecastCents > plannedCents -> PlanStatus.MAY_EXCEED
            expectedByTodayCents != null && actualCents > expectedByTodayCents -> PlanStatus.AHEAD_OF_PACE
            else -> PlanStatus.ON_TRACK
        }
}

enum class PlanStatus { ON_TRACK, AHEAD_OF_PACE, MAY_EXCEED, OVER }

/**
 * What one part of the plan spends: this month's spending ([actualCents], of which
 * [recurringActualCents] came from recurring payments), the recurring payments still due this month
 * ([dueRemainingCents], overdue included, of which [dueFromTodayCents] fall today or later), and
 * the non-recurring spending of the months before ([historyCents]), which the forecast extends.
 */
data class PartSpending(
    val actualCents: Long = 0L,
    val recurringActualCents: Long = 0L,
    val dueRemainingCents: Long = 0L,
    val dueFromTodayCents: Long = 0L,
    val historyCents: Long = 0L,
) {
    operator fun plus(other: PartSpending) = PartSpending(
        actualCents = actualCents + other.actualCents,
        recurringActualCents = recurringActualCents + other.recurringActualCents,
        dueRemainingCents = dueRemainingCents + other.dueRemainingCents,
        dueFromTodayCents = dueFromTodayCents + other.dueFromTodayCents,
        historyCents = historyCents + other.historyCents,
    )
}

/** One compartment (partida): its planned amount and what it spends. */
data class CompartmentInput(
    val key: String,
    val plannedCents: Long,
    val spending: PartSpending,
)

/** Everything the monthly plan is worked out from; history covers [historyDays] days. */
data class PlanInputs(
    val month: YearMonth,
    val today: LocalDate,
    val totalLimitCents: Long?,
    val compartments: List<CompartmentInput>,
    val others: PartSpending,
    val historyDays: Int,
)

data class MonthPlan(
    val total: PlanPart,
    val compartments: Map<String, PlanPart>,
    val others: PlanPart,
)

/**
 * The monthly plan in parts that always add up to the total: the compartments, and everything else
 * as the remainder. Each part carries its own recurring payments as committed spending. Pure
 * arithmetic over canonical figures the caller has read; nothing here decides what counts as spending.
 */
fun buildMonthPlan(inputs: PlanInputs): MonthPlan {
    val clock = MonthClock(inputs.month, inputs.today)
    val part = { planned: Long?, spending: PartSpending -> planPart(planned, spending, inputs.historyDays, clock) }
    val compartments = inputs.compartments.associate { it.key to part(it.plannedCents, it.spending) }
    val othersPlanned = inputs.totalLimitCents?.let { total -> total - inputs.compartments.sumOf { it.plannedCents } }
    val all = inputs.compartments.fold(inputs.others) { sum, compartment -> sum + compartment.spending }
    return MonthPlan(
        total = part(inputs.totalLimitCents, all),
        compartments = compartments,
        others = part(othersPlanned, inputs.others),
    )
}

private fun planPart(planned: Long?, spending: PartSpending, historyDays: Int, clock: MonthClock): PlanPart {
    val committed = spending.recurringActualCents + spending.dueRemainingCents
    // The recent daily rate carried over the days still to come; today is still being spent.
    val estimatedRest = if (historyDays > 0) spending.historyCents * clock.daysAfterToday / historyDays else 0L
    return PlanPart(
        plannedCents = planned,
        actualCents = spending.actualCents,
        // Recurring payments are expected on their due dates; the rest of the plan evenly over the month.
        expectedByTodayCents = planned?.let {
            committed - spending.dueFromTodayCents + clock.expected((it - committed).coerceAtLeast(0L))
        },
        forecastCents = spending.actualCents + spending.dueRemainingCents + estimatedRest,
        perDayCents = planned?.let { clock.perDay(it - spending.actualCents - spending.dueRemainingCents) },
        committedCents = committed,
        dueCents = spending.dueRemainingCents,
    )
}

/**
 * The part of what a trip's budget has left ([remainingCents]) that falls in [month]: spread evenly
 * over the trip's days still to come, today included, by how many of those are in the month. A
 * trip without an end date lasts its one day; one already over has nothing left to come.
 */
fun tripBudgetLeftIn(month: YearMonth, today: LocalDate, remainingCents: Long, start: LocalDate, end: LocalDate?): Long {
    val last = end?.takeIf { !it.isBefore(start) } ?: start
    val first = maxOf(start, today)
    if (remainingCents <= 0L || last.isBefore(first)) return 0L
    val inMonthFrom = maxOf(first, month.atDay(1))
    val inMonthTo = minOf(last, month.atEndOfMonth())
    if (inMonthTo.isBefore(inMonthFrom)) return 0L
    val daysToCome = last.toEpochDay() - first.toEpochDay() + 1
    val daysInMonth = inMonthTo.toEpochDay() - inMonthFrom.toEpochDay() + 1
    return remainingCents * daysInMonth / daysToCome
}

/** Where today falls in the plan's month: before it, inside it, or after it. */
private class MonthClock(month: YearMonth, today: LocalDate) {
    private val length = month.lengthOfMonth()
    private val elapsedDays = when {
        today.isBefore(month.atDay(1)) -> 0
        today.isAfter(month.atEndOfMonth()) -> length
        else -> today.dayOfMonth
    }
    val daysAfterToday: Int = when {
        today.isBefore(month.atDay(1)) -> length
        else -> length - elapsedDays
    }
    private val daysLeftWithToday: Int = when {
        today.isBefore(month.atDay(1)) -> length
        today.isAfter(month.atEndOfMonth()) -> 0
        else -> length - elapsedDays + 1
    }

    /** A steady share of [plannedCents] by the end of today. */
    fun expected(plannedCents: Long): Long = plannedCents * elapsedDays / length

    /** [leftCents] spread over the days still open, never below zero; null once the month is over. */
    fun perDay(leftCents: Long): Long? =
        if (daysLeftWithToday == 0) null else (leftCents / daysLeftWithToday).coerceAtLeast(0L)
}
