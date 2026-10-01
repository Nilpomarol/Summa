package com.gestorfinances.app.domain.rules

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthPlanTest {
    private val september = YearMonth.of(2026, 9)
    private val foodInput = CompartmentInput("food", plannedCents = 40_000, spending = PartSpending(actualCents = 15_000, historyCents = 92_000))

    // Subscriptions: 30 € already charged, 10 € more due later this month.
    private val subscriptionsInput = CompartmentInput(
        "subscriptions",
        plannedCents = 5_000,
        spending = PartSpending(actualCents = 3_000, recurringActualCents = 3_000, dueRemainingCents = 1_000, dueFromTodayCents = 1_000),
    )
    private val inputs = PlanInputs(
        month = september,
        today = LocalDate.of(2026, 9, 10),
        totalLimitCents = 150_000,
        compartments = listOf(foodInput, subscriptionsInput),
        // Rent paid, plus 300 € of recurring payments still due: 100 € overdue, 200 € from today.
        others = PartSpending(actualCents = 5_000, recurringActualCents = 5_000, dueRemainingCents = 30_000, dueFromTodayCents = 20_000),
        historyDays = 92,
    )

    @Test
    fun partsAddUpToTheTotalAndTrackPace() {
        val plan = buildMonthPlan(inputs)

        // A compartment: a third of the month gone, spending slightly ahead of that pace.
        val food = plan.compartments.getValue("food")
        assertEquals(13_333L, food.expectedByTodayCents)
        assertEquals(15_000L + 20_000L, food.forecastCents) // 1.000/day recently, 20 days to come
        assertEquals(25_000L / 21, food.perDayCents) // 21 days left, today included
        assertEquals(PlanStatus.AHEAD_OF_PACE, food.status)

        // Recurring payments count where their category does, expected on their due dates: an
        // early charge is not running ahead, and what is still due is committed in the forecast.
        val subscriptions = plan.compartments.getValue("subscriptions")
        assertEquals(4_000L, subscriptions.committedCents)
        assertEquals(1_000L, subscriptions.dueCents)
        assertEquals(3_000L + 333L, subscriptions.expectedByTodayCents)
        assertEquals(4_000L, subscriptions.forecastCents)
        assertEquals(PlanStatus.ON_TRACK, subscriptions.status)

        // The rest is whatever the total leaves, with its own recurring payments.
        val others = plan.others
        assertEquals(105_000L, others.plannedCents)
        assertEquals(15_000L + 23_333L, others.expectedByTodayCents) // paid + overdue, then 700 € spread
        assertEquals(35_000L, others.forecastCents)
        assertEquals(70_000L / 21, others.perDayCents) // what is due set aside
        assertEquals(PlanStatus.ON_TRACK, others.status)

        val total = plan.total
        assertEquals(23_000L, total.actualCents)
        assertEquals(31_000L, total.dueCents)
        assertEquals(18_000L + 37_000L, total.expectedByTodayCents)
        assertEquals(food.forecastCents + subscriptions.forecastCents + others.forecastCents, total.forecastCents)
        assertEquals(96_000L / 21, total.perDayCents)
    }

    @Test
    fun forecastBeyondThePlanWarnsBeforeItIsOver() {
        val plan = buildMonthPlan(
            inputs.copy(compartments = listOf(CompartmentInput("food", 30_000, PartSpending(actualCents = 15_000, historyCents = 184_000)))),
        )
        assertEquals(PlanStatus.MAY_EXCEED, plan.compartments.getValue("food").status)
    }

    @Test
    fun recurringPaymentsStillDueCanTakeAPartOverItsPlan() {
        val plan = buildMonthPlan(
            inputs.copy(
                compartments = listOf(
                    subscriptionsInput.copy(spending = subscriptionsInput.spending.copy(dueRemainingCents = 3_000, dueFromTodayCents = 3_000)),
                ),
            ),
        )
        assertEquals(PlanStatus.MAY_EXCEED, plan.compartments.getValue("subscriptions").status)
    }

    @Test
    fun withoutAMonthlyTotalTheRestHasNoPlan() {
        val plan = buildMonthPlan(inputs.copy(totalLimitCents = null))
        assertNull(plan.others.plannedCents)
        assertNull(plan.others.status)
        assertNull(plan.total.plannedCents)
        assertNull(plan.total.perDayCents)
    }

    @Test
    fun aPastMonthIsWholeAndHasNothingLeftPerDay() {
        val plan = buildMonthPlan(inputs.copy(today = LocalDate.of(2026, 10, 3)))
        val food = plan.compartments.getValue("food")
        assertEquals(40_000L, food.expectedByTodayCents)
        assertEquals(15_000L, food.forecastCents)
        assertNull(food.perDayCents)
    }

    @Test
    fun tripBudgetLeftFallsInAMonthByTheDaysStillToCome() {
        val october = YearMonth.of(2026, 10)
        val start = LocalDate.of(2026, 10, 28)
        val end = LocalDate.of(2026, 11, 3)
        // Seven days, four of them in October.
        assertEquals(400L, tripBudgetLeftIn(october, LocalDate.of(2026, 10, 1), 700L, start, end))
        assertEquals(300L, tripBudgetLeftIn(YearMonth.of(2026, 11), LocalDate.of(2026, 10, 1), 700L, start, end))
        // Under way on the 30th: five days to come, two of them in October.
        assertEquals(200L, tripBudgetLeftIn(october, LocalDate.of(2026, 10, 30), 500L, start, end))
        // Over, overspent, or in another month: nothing.
        assertEquals(0L, tripBudgetLeftIn(october, LocalDate.of(2026, 11, 4), 700L, start, end))
        assertEquals(0L, tripBudgetLeftIn(october, LocalDate.of(2026, 10, 1), -50L, start, end))
        assertEquals(0L, tripBudgetLeftIn(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1), 700L, start, end))
        // Without an end date it lasts its one day.
        assertEquals(700L, tripBudgetLeftIn(october, LocalDate.of(2026, 10, 1), 700L, start, null))
    }
}
