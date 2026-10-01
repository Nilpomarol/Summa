package com.gestorfinances.app.notifications

import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    @Test
    fun planPartsAlertWhenForecastOverAndWhenOverOnceAMonth() {
        val month = YearMonth.of(2026, 9)
        val part = { planned: Long?, actual: Long, forecast: Long ->
            PlanPart(planned, actual, expectedByTodayCents = planned?.div(2), forecastCents = forecast, perDayCents = null)
        }
        val parts = listOf(
            NamedPlanPart("food", "Menjar", part(40_000, 15_000, 45_000)), // heading over
            NamedPlanPart("fun", "Oci", part(10_000, 12_000, 12_000)), // over
            NamedPlanPart("ahead", "Transport", part(10_000, 6_000, 9_000)), // only ahead of pace
            NamedPlanPart("others", "Altres", part(-5_000, 1_000, 1_000)), // plans nothing
        )

        val candidates = planAlertCandidates(parts, month) { false }
        assertEquals(listOf("food" to PlanStatus.MAY_EXCEED, "fun" to PlanStatus.OVER), candidates.map { it.named.id to it.status })

        val fired = candidates.map { it.key }.toSet()
        assertTrue(planAlertCandidates(parts, month) { it in fired }.isEmpty())
    }

    @Test
    fun recurringCandidatesFireAtTheStartOfTheMarginAndSkipPausedOrAlreadyFired() {
        val dueDate = LocalDate.parse("2026-02-10")
        val firedKey = recurringReminderKey("fired", dueDate)

        val candidates = recurringReminderCandidates(
            settings = NotificationSettings(recurringLeadDays = 1, recurringDueTodayEnabled = false, recurringOverdueEnabled = false),
            templates = listOf(
                template("active", dueDate.toString(), marginDays = 3),
                template("paused", dueDate.toString(), status = TemplateStatus.PAUSED),
                template("fired", dueDate.toString()),
            ),
            today = LocalDate.parse("2026-02-01"),
            alreadyFired = { it == firedKey },
        )

        assertEquals(1, candidates.size)
        assertEquals("active", candidates.single().template.id)
        assertEquals(LocalDate.parse("2026-02-07"), candidates.single().triggerDate)
    }

    @Test
    fun anOccurrenceIsRemindedAheadOnItsDayAndOnceMissed() {
        val settings = NotificationSettings(recurringLeadDays = 2)
        val item = listOf(template("rent", "2026-02-10", marginDays = 1))
        fun stages(today: String, fired: Set<String> = emptySet()) =
            recurringReminderCandidates(settings, item, LocalDate.parse(today)) { it in fired }
                .map { it.kind to it.triggerDate.toString() }

        assertEquals(
            listOf(
                RecurringReminderKind.ADVANCE to "2026-02-08",
                RecurringReminderKind.DUE to "2026-02-10",
                RecurringReminderKind.OVERDUE to "2026-02-13",
            ),
            stages("2026-02-01"),
        )
        // Opening the app late gives only the reminder that still makes sense.
        assertEquals(
            listOf(RecurringReminderKind.DUE to "2026-02-10", RecurringReminderKind.OVERDUE to "2026-02-13"),
            stages("2026-02-10"),
        )
        assertEquals(listOf(RecurringReminderKind.OVERDUE to "2026-02-13"), stages("2026-02-20"))
        assertTrue(stages("2026-02-20", fired = setOf("rent:2026-02-10:overdue")).isEmpty())
    }

    @Test
    fun eachRecurringReminderCanBeTurnedOff() {
        val item = listOf(template("rent", "2026-02-10"))
        val today = LocalDate.parse("2026-02-01")
        val none = NotificationSettings(recurringLeadDays = 0, recurringDueTodayEnabled = false, recurringOverdueEnabled = false)

        assertTrue(recurringReminderCandidates(none, item, today) { false }.isEmpty())
        assertEquals(
            listOf(RecurringReminderKind.DUE),
            recurringReminderCandidates(none.copy(recurringDueTodayEnabled = true), item, today) { false }.map { it.kind },
        )
        assertEquals("rent:2026-02-10", occurrenceKeyOf("rent:2026-02-10:overdue"))
    }

    @Test
    fun budgetCandidatesOnlyIncludeWarnAndOverStatesOncePerMonth() {
        val month = YearMonth.parse("2026-03")
        val warn = BudgetEvaluation(budget("warn", limit = 10_000, threshold = 80), actualCents = 9_000)
        val over = BudgetEvaluation(budget("over", limit = 10_000, threshold = 80), actualCents = 11_000)
        val ok = BudgetEvaluation(budget("ok", limit = 10_000, threshold = 80), actualCents = 2_000)

        val candidates = budgetAlertCandidates(
            evaluations = listOf(warn, over, ok),
            month = month,
            liveTripIds = emptySet(),
            alreadyFired = { it == "warn:2026-03:WARN" },
        )

        assertEquals(listOf(BudgetStatus.OVER), candidates.map { it.status })
        assertEquals("over", candidates.single().evaluation.budget.id)
    }

    @Test
    fun aTripBudgetAlertsOnceForTheTripAndNeverOnceTheTripIsOver() {
        fun tripBudget(tripId: String) = budget("budget-$tripId", limit = 10_000, threshold = 80)
            .copy(scope = BudgetScope.TRIP, categoryId = null, tripId = tripId, tripName = tripId)
        val evaluations = listOf("running", "finished").map { BudgetEvaluation(tripBudget(it), actualCents = 11_000) }
        val fired = mutableSetOf<String>()
        fun alerts(month: String) = budgetAlertCandidates(evaluations, YearMonth.parse(month), setOf("running")) { it in fired }

        val first = alerts("2026-03")
        assertEquals(listOf("budget-running"), first.map { it.evaluation.budget.id })
        fired += first.map { it.key }

        assertTrue("the same alert must not come back next month", alerts("2026-04").isEmpty())
    }

    @Test
    fun aYearlyLimitAlertsOnceAYear() {
        val yearly = BudgetEvaluation(budget("gifts", limit = 10_000, threshold = 80).copy(period = BudgetPeriod.YEARLY), actualCents = 11_000)
        val fired = mutableSetOf<String>()
        fun alerts(month: String) = budgetAlertCandidates(listOf(yearly), YearMonth.parse(month), emptySet()) { it in fired }

        fired += alerts("2026-03").map { it.key }
        assertEquals(1, fired.size)
        assertTrue(alerts("2026-04").isEmpty())
        assertEquals(1, alerts("2027-01").size)
    }

    @Test
    fun lowBalanceCandidatesOnlyFireOnCrossingIntoLowState() {
        val candidates = lowBalanceAlertCandidates(
            accounts = listOf(
                account("below", balance = 4_000, threshold = 5_000),
                account("active", balance = 4_000, threshold = 5_000),
                account("above", balance = 6_000, threshold = 5_000),
                account("unset", balance = 4_000, threshold = null),
            ),
            isAlreadyActive = { it == "active" },
        )

        assertEquals(listOf("below"), candidates.map { it.account.id })
        assertTrue(candidates.single().thresholdCents == 5_000L)
    }

    private fun template(
        id: String,
        nextDueDate: String,
        marginDays: Long? = null,
        status: TemplateStatus = TemplateStatus.ACTIVE,
    ): TemplateSummary =
        TemplateSummary(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = 1_000,
            accountId = "checking",
            accountName = "Compte",
            destAccountId = null,
            destAccountName = null,
            categoryId = null,
            categoryName = null,
            name = id,
            payee = null,
            notes = null,
            splitConfig = null,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalCount = null,
            customUnit = null,
            dayOfMonth = 10,
            weekday = null,
            nextDueDate = nextDueDate,
            amountIsVariable = false,
            amountFlexCents = null,
            dateFlexDays = marginDays,
            leadNotificationDays = null,
            status = status,
            createdAt = NOW,
            updatedAt = NOW,
            archivedAt = null,
        )

    private fun budget(
        id: String,
        limit: Long,
        threshold: Long?,
    ): BudgetSummary =
        BudgetSummary(
            id = id,
            scope = BudgetScope.CATEGORY,
            categoryId = "food",
            categoryName = "Menjar",
            tripId = null,
            tripName = null,
            period = BudgetPeriod.MONTHLY,
            limitAmountCents = limit,
            alertThresholdPercent = threshold,
        )

    private fun account(
        id: String,
        balance: Long,
        threshold: Long?,
    ): AccountSummary =
        AccountSummary(
            id = id,
            name = id,
            startingBalanceCents = 0,
            currentBalanceCents = balance,
            type = AccountType.BANK,
            icon = null,
            color = null,
            isDefault = false,
            displayOrder = 0,
            lowBalanceThresholdCents = threshold,
            createdAt = NOW,
            updatedAt = NOW,
            archivedAt = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
