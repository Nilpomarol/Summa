package com.gestorfinances.app.notifications

import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

internal data class RecurringReminderCandidate(
    val template: TemplateSummary,
    val dueDate: LocalDate,
    val triggerDate: LocalDate,
    val key: String,
)

internal data class BudgetAlertCandidate(
    val evaluation: BudgetEvaluation,
    val month: YearMonth,
    val status: BudgetStatus,
    val key: String,
)

internal data class LowBalanceAlertCandidate(
    val account: AccountSummary,
    val thresholdCents: Long,
)

internal fun recurringReminderCandidates(
    settings: NotificationSettings,
    templates: List<TemplateSummary>,
    alreadyFired: (String) -> Boolean,
): List<RecurringReminderCandidate> =
    templates.mapNotNull { template ->
        if (template.status != TemplateStatus.ACTIVE) return@mapNotNull null
        val dueDate = parseDate(template.nextDueDate) ?: return@mapNotNull null
        val leadDays = (template.leadNotificationDays ?: settings.recurringLeadDays.toLong())
            .coerceAtLeast(0L)
        val key = recurringReminderKey(template.id, dueDate)
        if (alreadyFired(key)) return@mapNotNull null
        RecurringReminderCandidate(
            template = template,
            dueDate = dueDate,
            triggerDate = dueDate.minusDays(leadDays),
            key = key,
        )
    }

internal fun budgetAlertCandidates(
    evaluations: List<BudgetEvaluation>,
    month: YearMonth,
    alreadyFired: (String) -> Boolean,
): List<BudgetAlertCandidate> =
    evaluations.mapNotNull { evaluation ->
        val status = evaluation.status
        if (status == BudgetStatus.OK) return@mapNotNull null
        val key = budgetAlertKey(evaluation.budget.id, month, status)
        if (alreadyFired(key)) return@mapNotNull null
        BudgetAlertCandidate(
            evaluation = evaluation,
            month = month,
            status = status,
            key = key,
        )
    }

internal fun lowBalanceAlertCandidates(
    accounts: List<AccountSummary>,
    isAlreadyActive: (String) -> Boolean,
): List<LowBalanceAlertCandidate> =
    accounts.mapNotNull { account ->
        val threshold = account.lowBalanceThresholdCents ?: return@mapNotNull null
        if (account.currentBalanceCents >= threshold) return@mapNotNull null
        if (isAlreadyActive(account.id)) return@mapNotNull null
        LowBalanceAlertCandidate(account = account, thresholdCents = threshold)
    }

internal fun recurringReminderKey(
    templateId: String,
    dueDate: LocalDate,
): String = "$templateId:${dueDate}"

private fun budgetAlertKey(
    budgetId: String,
    month: YearMonth,
    status: BudgetStatus,
): String = "$budgetId:$month:${status.name}"

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }
