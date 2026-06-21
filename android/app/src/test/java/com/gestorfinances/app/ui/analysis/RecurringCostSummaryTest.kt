package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurringCostSummaryTest {
    @Test
    fun summaryConvertsActiveFixedExpensesToMonthlyEquivalent() {
        val summary = buildRecurringCostSummary(
            listOf(
                template("monthly", amount = 10_000, frequency = RecurrenceFrequency.MONTHLY),
                template("weekly", amount = 1_200, frequency = RecurrenceFrequency.WEEKLY),
                template("fortnightly", amount = 2_400, frequency = RecurrenceFrequency.FORTNIGHTLY),
                template("yearly", amount = 12_000, frequency = RecurrenceFrequency.YEARLY),
            ),
        )

        assertEquals(21_400L, summary.monthlyExpenseCents)
        assertEquals(listOf("monthly", "weekly", "fortnightly", "yearly"), summary.items.map { it.templateId })
        assertEquals(listOf(10_000L, 5_200L, 5_200L, 1_000L), summary.items.map { it.monthlyExpenseCents })
    }

    @Test
    fun summaryIgnoresTemplatesThatAreNotKnownActiveFixedExpenses() {
        val summary = buildRecurringCostSummary(
            listOf(
                template("active", amount = 2_000, frequency = RecurrenceFrequency.MONTHLY),
                template("income", amount = 3_000, frequency = RecurrenceFrequency.MONTHLY, type = MovementType.INCOME),
                template("transfer", amount = 4_000, frequency = RecurrenceFrequency.MONTHLY, type = MovementType.TRANSFER),
                template(
                    "paused",
                    amount = 5_000,
                    frequency = RecurrenceFrequency.MONTHLY,
                    status = TemplateStatus.PAUSED,
                ),
                template(
                    "variable",
                    amount = null,
                    frequency = RecurrenceFrequency.MONTHLY,
                    amountIsVariable = true,
                ),
            ),
        )

        assertEquals(2_000L, summary.monthlyExpenseCents)
        assertEquals(listOf("active"), summary.items.map { it.templateId })
    }

    @Test
    fun summarySupportsCustomIntervals() {
        val summary = buildRecurringCostSummary(
            listOf(
                template(
                    "two-months",
                    amount = 6_000,
                    frequency = RecurrenceFrequency.CUSTOM,
                    intervalCount = 2,
                    customUnit = CustomRecurrenceUnit.MONTHS,
                ),
                template(
                    "two-weeks",
                    amount = 2_400,
                    frequency = RecurrenceFrequency.CUSTOM,
                    intervalCount = 2,
                    customUnit = CustomRecurrenceUnit.WEEKS,
                ),
            ),
        )

        assertEquals(8_200L, summary.monthlyExpenseCents)
        assertEquals(listOf("two-weeks", "two-months"), summary.items.map { it.templateId })
    }

    private fun template(
        id: String,
        amount: Long?,
        frequency: RecurrenceFrequency,
        type: MovementType = MovementType.EXPENSE,
        status: TemplateStatus = TemplateStatus.ACTIVE,
        amountIsVariable: Boolean = false,
        intervalCount: Long? = null,
        customUnit: CustomRecurrenceUnit? = null,
    ): TemplateSummary =
        TemplateSummary(
            id = id,
            type = type,
            amountCents = amount,
            accountId = "checking",
            accountName = "Checking",
            destAccountId = if (type == MovementType.TRANSFER) "savings" else null,
            destAccountName = if (type == MovementType.TRANSFER) "Savings" else null,
            categoryId = "subscriptions",
            categoryName = "Subscriptions",
            name = id,
            payee = null,
            notes = null,
            splitConfig = null,
            frequency = frequency,
            intervalCount = intervalCount,
            customUnit = customUnit,
            dayOfMonth = 1,
            weekday = null,
            nextDueDate = "2026-01-01",
            amountIsVariable = amountIsVariable,
            amountFlexCents = null,
            dateFlexDays = null,
            leadNotificationDays = null,
            status = status,
            createdAt = NOW,
            updatedAt = NOW,
            archivedAt = null,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
