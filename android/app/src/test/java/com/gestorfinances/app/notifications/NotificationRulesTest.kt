package com.gestorfinances.app.notifications

import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    @Test
    fun recurringCandidatesUseTemplateLeadAndSkipPausedOrAlreadyFired() {
        val dueDate = LocalDate.parse("2026-02-10")
        val firedKey = recurringReminderKey("fired", dueDate)

        val candidates = recurringReminderCandidates(
            settings = NotificationSettings(recurringLeadDays = 1),
            templates = listOf(
                template("active", dueDate.toString(), leadDays = 3),
                template("paused", dueDate.toString(), status = TemplateStatus.PAUSED),
                template("fired", dueDate.toString()),
            ),
            alreadyFired = { it == firedKey },
        )

        assertEquals(1, candidates.size)
        assertEquals("active", candidates.single().template.id)
        assertEquals(LocalDate.parse("2026-02-07"), candidates.single().triggerDate)
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
            alreadyFired = { it == "warn:2026-03:WARN" },
        )

        assertEquals(listOf(BudgetStatus.OVER), candidates.map { it.status })
        assertEquals("over", candidates.single().evaluation.budget.id)
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
        leadDays: Long? = null,
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
            dateFlexDays = null,
            leadNotificationDays = leadDays,
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
            categoryId = "food",
            categoryName = "Menjar",
            limitAmountCents = limit,
            alertThresholdPercent = threshold,
            startDate = null,
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
