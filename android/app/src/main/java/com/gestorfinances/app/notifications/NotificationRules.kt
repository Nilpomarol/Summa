package com.gestorfinances.app.notifications

import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.domain.rules.PlanStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/** When a recurring item's occurrence is worth a reminder: ahead of it, on its day, once missed. */
internal enum class RecurringReminderKind { ADVANCE, DUE, OVERDUE }

internal data class RecurringReminderCandidate(
    val template: TemplateSummary,
    val dueDate: LocalDate,
    val triggerDate: LocalDate,
    val kind: RecurringReminderKind,
    val key: String,
) {
    /** Shared by the occurrence's reminders, so a later one replaces an earlier one still showing. */
    val occurrenceKey: String get() = recurringReminderKey(template.id, dueDate)
}

internal data class BudgetAlertCandidate(
    val evaluation: BudgetEvaluation,
    val month: YearMonth,
    val status: BudgetStatus,
    val key: String,
)

/** One part of the monthly plan to watch: the total, a partida (by budget id), or the rest. */
internal data class NamedPlanPart(
    val id: String,
    val name: String,
    val part: PlanPart,
)

internal data class PlanAlertCandidate(
    val named: NamedPlanPart,
    val status: PlanStatus,
    val key: String,
)

internal data class LowBalanceAlertCandidate(
    val account: AccountSummary,
    val thresholdCents: Long,
)

/**
 * The reminders still to give for each active item's next occurrence: ahead of its date (at the
 * start of its window, or the usual notice if that is earlier), on its date, and once its window
 * has passed unrecorded. A reminder whose moment has gone while a later one is already due is
 * skipped, so opening the app late never fires them all at once.
 */
internal fun recurringReminderCandidates(
    settings: NotificationSettings,
    templates: List<TemplateSummary>,
    today: LocalDate,
    alreadyFired: (String) -> Boolean,
): List<RecurringReminderCandidate> =
    templates.flatMap { template ->
        if (template.status != TemplateStatus.ACTIVE) return@flatMap emptyList()
        val dueDate = parseDate(template.nextDueDate) ?: return@flatMap emptyList()
        val leadDays = settings.recurringLeadDays.toLong()
        val stages = listOfNotNull(
            (RecurringReminderKind.ADVANCE to dueDate.minusDays(maxOf(template.marginDays, leadDays))).takeIf { leadDays > 0 },
            (RecurringReminderKind.DUE to dueDate).takeIf { settings.recurringDueTodayEnabled },
            (RecurringReminderKind.OVERDUE to dueDate.plusDays(template.marginDays + OVERDUE_GRACE_DAYS))
                .takeIf { settings.recurringOverdueEnabled },
        )
        stages.mapIndexedNotNull { index, (kind, triggerDate) ->
            val superseded = stages.drop(index + 1).any { (_, later) -> !later.isAfter(today) }
            val key = recurringReminderKey(template.id, dueDate, kind)
            if (superseded || alreadyFired(key)) null
            else RecurringReminderCandidate(template, dueDate, triggerDate, kind, key)
        }
    }

/** Days after an item's window closes before saying it was missed: time to record it first. */
private const val OVERDUE_GRACE_DAYS = 2L

/**
 * Threshold alerts for yearly limits and trip budgets, each given once for the period the budget
 * covers: a yearly limit once a year, a trip's budget once for the trip. A trip that is over (or
 * gone) no longer alerts: its budget counts its whole life, so it would otherwise repeat for ever.
 */
internal fun budgetAlertCandidates(
    evaluations: List<BudgetEvaluation>,
    month: YearMonth,
    liveTripIds: Set<String>,
    alreadyFired: (String) -> Boolean,
): List<BudgetAlertCandidate> =
    evaluations.mapNotNull { evaluation ->
        val status = evaluation.status
        if (status == BudgetStatus.OK) return@mapNotNull null
        val budget = evaluation.budget
        if (budget.scope == BudgetScope.TRIP && budget.tripId !in liveTripIds) return@mapNotNull null
        val key = budgetAlertKey(budget, month, status)
        if (alreadyFired(key)) return@mapNotNull null
        BudgetAlertCandidate(
            evaluation = evaluation,
            month = month,
            status = status,
            key = key,
        )
    }

/**
 * The plan's one alert rule: a part is worth a notification when its forecast goes over what it
 * plans, and again once it actually goes over, each once a month. A part planning nothing (the
 * rest, when partides take the whole total) has nothing to go over.
 */
internal fun planAlertCandidates(
    parts: List<NamedPlanPart>,
    month: YearMonth,
    alreadyFired: (String) -> Boolean,
): List<PlanAlertCandidate> =
    parts.mapNotNull { named ->
        if ((named.part.plannedCents ?: 0L) <= 0L) return@mapNotNull null
        val status = named.part.status?.takeIf { it == PlanStatus.MAY_EXCEED || it == PlanStatus.OVER }
            ?: return@mapNotNull null
        val key = "plan:${named.id}:$month:${status.name}"
        if (alreadyFired(key)) null else PlanAlertCandidate(named, status, key)
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

/** The ahead-of-date reminder keeps the plain occurrence key it has always had. */
internal fun recurringReminderKey(
    templateId: String,
    dueDate: LocalDate,
    kind: RecurringReminderKind = RecurringReminderKind.ADVANCE,
): String = when (kind) {
    RecurringReminderKind.ADVANCE -> "$templateId:$dueDate"
    RecurringReminderKind.DUE -> "$templateId:$dueDate:due"
    RecurringReminderKind.OVERDUE -> "$templateId:$dueDate:overdue"
}

/** The occurrence a reminder key belongs to, whichever of its reminders it is. */
internal fun occurrenceKeyOf(reminderKey: String): String =
    reminderKey.removeSuffix(":due").removeSuffix(":overdue")

private fun budgetAlertKey(
    budget: BudgetSummary,
    month: YearMonth,
    status: BudgetStatus,
): String {
    val period = when {
        budget.scope == BudgetScope.TRIP -> "trip"
        budget.period == BudgetPeriod.YEARLY -> month.year.toString()
        else -> month.toString()
    }
    return "${budget.id}:$period:${status.name}"
}

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }
