package com.gestorfinances.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.gestorfinances.app.MainActivity
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

const val EXTRA_NOTIFICATION_DESTINATION = "com.gestorfinances.app.notifications.DESTINATION"
const val DESTINATION_RECURRING = "recurring"
const val DESTINATION_BUDGETS = "budgets"
const val DESTINATION_ACCOUNTS = "accounts"
const val DESTINATION_SETTINGS = "settings"

/** A destination naming one item: `recurring:<template id>` (to record it) or `account:<account id>`. */
const val DESTINATION_RECURRING_ITEM_PREFIX = "recurring:"
const val DESTINATION_ACCOUNT_PREFIX = "account:"

class FinanceNotificationCoordinator(
    private val context: Context,
    private val templateRepository: TemplateRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val tripRepository: TripRepository,
    private val preferences: NotificationPreferences,
    private val today: () -> LocalDate = { LocalDate.now() },
) : NotificationRefresher {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    override suspend fun refreshNotifications() {
        ensureNotificationChannel(appContext)
        val settings = preferences.loadSettings()
        refreshRecurringReminders(settings)
        if (appContext.canPostFinanceNotifications()) {
            refreshBudgetAlerts(settings)
            refreshLowBalanceAlerts(settings)
        } else {
            clearRecoveredLowBalanceStates()
        }
    }

    private fun refreshRecurringReminders(settings: NotificationSettings) {
        val templates = templateRepository.listActive()
        val candidates = recurringReminderCandidates(
            settings = settings,
            templates = templates,
            today = today(),
            alreadyFired = preferences::hasRecurringReminderFired,
        )
        val nextKeys = candidates.map { it.key }.toSet()
        val previousKeys = preferences.scheduledRecurringKeys()
        previousKeys.minus(nextKeys).forEach(::cancelRecurringReminder)
        // A reminder still showing for an occurrence that is no longer pending (recorded, skipped,
        // paused) has nothing left to say.
        val pendingOccurrences = templates.mapNotNull { template ->
            runCatching { recurringReminderKey(template.id, LocalDate.parse(template.nextDueDate)) }.getOrNull()
        }.toSet()
        previousKeys.map(::occurrenceKeyOf).toSet().minus(pendingOccurrences).forEach { occurrence ->
            notificationManager.cancel(stableId("recurring:$occurrence"))
        }
        candidates.forEach(::scheduleRecurringReminder)
        // A reminder that has fired stays known while its occurrence is pending, so recording the
        // occurrence later still takes it down.
        preferences.saveScheduledRecurringKeys(nextKeys + previousKeys.filter { occurrenceKeyOf(it) in pendingOccurrences })
    }

    private fun scheduleRecurringReminder(candidate: RecurringReminderCandidate) {
        val template = candidate.template
        val intent = FinanceNotificationReceiver.notificationIntent(
            context = appContext,
            notificationId = stableId("recurring:${candidate.occurrenceKey}"),
            title = recurringTitle(template),
            body = recurringBody(candidate),
            destination = "$DESTINATION_RECURRING_ITEM_PREFIX${template.id}",
            channelId = CHANNEL_RECURRING,
            recurringKey = candidate.key,
        )
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            stableId("alarm:${candidate.key}"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis(candidate.triggerDate),
            pendingIntent,
        )
    }

    /** "Netflix · 12,99 €", or "Nòmina · uns 1.530,00 €" when the amount is only expected. */
    private fun recurringTitle(template: TemplateSummary): String {
        val name = template.name ?: template.payee ?: template.categoryName ?: appContext.getString(R.string.nav_recurring)
        val amount = template.expectedAmountCents ?: return name
        return appContext.getString(
            if (template.amountIsVariable) R.string.notification_recurring_title_about else R.string.notification_recurring_title_amount,
            name,
            formatEuroCents(amount),
        )
    }

    private fun recurringBody(candidate: RecurringReminderCandidate): String {
        val income = candidate.template.type == MovementType.INCOME
        val date = formatCompactDateRelative(candidate.dueDate.toString(), today())
        return when (candidate.kind) {
            RecurringReminderKind.ADVANCE -> appContext.getString(
                if (income) R.string.notification_recurring_advance_income else R.string.notification_recurring_advance_expense,
                date,
            )
            RecurringReminderKind.DUE -> appContext.getString(
                if (income) R.string.notification_recurring_today_income else R.string.notification_recurring_today_expense,
            )
            RecurringReminderKind.OVERDUE -> appContext.getString(R.string.notification_recurring_overdue, date)
        }
    }

    private fun cancelRecurringReminder(key: String) {
        val intent = Intent(appContext, FinanceNotificationReceiver::class.java).apply {
            action = FinanceNotificationReceiver.ACTION_SHOW_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            stableId("alarm:$key"),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun refreshBudgetAlerts(settings: NotificationSettings) {
        if (!settings.budgetAlertsEnabled) return
        val month = YearMonth.from(today())
        refreshPlanAlerts(month)
        // Yearly limits and trip budgets have no forecast: they keep their threshold alerts.
        val evaluations = budgetRepository.evaluateAll(
            fromDate = month.atDay(1).toString(),
            toDate = month.atEndOfMonth().toString(),
        ).filterNot { it.budget.scope == BudgetScope.OVERALL_MONTH || it.budget.period == BudgetPeriod.MONTHLY }
        val today = today()
        val liveTripIds = tripRepository.listActive()
            .filter { trip ->
                trip.status != TripStatus.FINISHED &&
                    trip.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) != true
            }
            .map { it.id }
            .toSet()
        budgetAlertCandidates(evaluations, month, liveTripIds, preferences::hasBudgetAlertFired)
            .forEach { candidate ->
                val titleRes = when (candidate.status) {
                    BudgetStatus.WARN -> R.string.notification_budget_warn_title
                    BudgetStatus.OVER -> R.string.notification_budget_over_title
                    BudgetStatus.OK -> return@forEach
                }
                val budget = candidate.evaluation.budget
                val shown = postNotification(
                    notificationId = stableId("budget:${candidate.key}"),
                    title = appContext.getString(titleRes, budget.displayName ?: appContext.getString(R.string.common_no_category)),
                    body = appContext.getString(
                        R.string.notification_budget_body,
                        formatEuroCents(candidate.evaluation.actualCents),
                        formatEuroCents(budget.limitAmountCents),
                    ),
                    destination = DESTINATION_BUDGETS,
                    channelId = CHANNEL_PLAN,
                )
                if (shown) {
                    preferences.markBudgetAlertFired(candidate.key)
                }
            }
    }

    /** The monthly plan's parts, alerted when forecast to go over and when over. */
    private fun refreshPlanAlerts(month: YearMonth) {
        val plan = budgetRepository.monthPlan(
            month = month,
            today = today(),
            templates = templateRepository.listActive(),
            categoryParentById = categoryRepository.listActive().associate { it.id to it.parentId },
        )
        val parts = buildList {
            if (plan.total != null) add(NamedPlanPart("total", appContext.getString(R.string.budget_plan_eyebrow), plan.plan.total))
            plan.compartments.forEach { budget ->
                val name = budget.displayName ?: appContext.getString(R.string.common_no_category)
                add(NamedPlanPart(budget.id, name, plan.plan.compartments.getValue(budget.id)))
            }
            add(NamedPlanPart("others", appContext.getString(R.string.budget_plan_others), plan.plan.others))
        }
        planAlertCandidates(parts, month, preferences::hasBudgetAlertFired).forEach { candidate ->
            val part = candidate.named.part
            val planned = part.plannedCents ?: return@forEach
            val shown = postNotification(
                notificationId = stableId("budget:${candidate.key}"),
                title = appContext.getString(
                    if (candidate.status == PlanStatus.OVER) R.string.notification_plan_over_title else R.string.notification_plan_may_exceed_title,
                    candidate.named.name,
                ),
                body = if (candidate.status == PlanStatus.OVER) {
                    appContext.getString(
                        R.string.notification_budget_body,
                        formatEuroCents(part.actualCents),
                        formatEuroCents(planned),
                    )
                } else {
                    appContext.getString(
                        R.string.notification_plan_may_exceed_body,
                        formatEuroCents(part.forecastCents),
                        formatEuroCents(planned),
                    )
                },
                destination = DESTINATION_BUDGETS,
                channelId = CHANNEL_PLAN,
            )
            if (shown) preferences.markBudgetAlertFired(candidate.key)
        }
    }

    private fun refreshLowBalanceAlerts(settings: NotificationSettings) {
        val accounts = accountRepository.listActive()
        accounts.forEach { account ->
            val threshold = account.lowBalanceThresholdCents
            if (!settings.lowBalanceAlertsEnabled || threshold == null || account.currentBalanceCents >= threshold) {
                preferences.setLowBalanceActive(account.id, false)
            }
        }
        if (!settings.lowBalanceAlertsEnabled) return
        lowBalanceAlertCandidates(accounts, preferences::isLowBalanceActive)
            .forEach { candidate ->
                val shown = postNotification(
                    notificationId = stableId("low:${candidate.account.id}"),
                    title = appContext.getString(R.string.notification_low_balance_title, candidate.account.name),
                    body = appContext.getString(
                        R.string.notification_low_balance_body,
                        formatEuroCents(candidate.account.currentBalanceCents),
                        formatEuroCents(candidate.thresholdCents),
                    ),
                    destination = "$DESTINATION_ACCOUNT_PREFIX${candidate.account.id}",
                    channelId = CHANNEL_ACCOUNTS,
                )
                if (shown) {
                    preferences.setLowBalanceActive(candidate.account.id, true)
                }
            }
    }

    private fun clearRecoveredLowBalanceStates() {
        accountRepository.listActive().forEach { account ->
            val threshold = account.lowBalanceThresholdCents
            if (threshold == null || account.currentBalanceCents >= threshold) {
                preferences.setLowBalanceActive(account.id, false)
            }
        }
    }

    private fun triggerMillis(triggerDate: LocalDate): Long {
        val trigger = if (triggerDate <= today()) {
            System.currentTimeMillis() + IMMEDIATE_ALARM_DELAY_MS
        } else {
            triggerDate.atTime(LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        return trigger
    }

    private fun postNotification(
        notificationId: Int,
        title: String,
        body: String,
        destination: String,
        channelId: String,
    ): Boolean {
        if (!appContext.canPostFinanceNotifications()) return false
        notificationManager.notify(
            notificationId,
            buildFinanceNotification(
                context = appContext,
                title = title,
                body = body,
                destination = destination,
                channelId = channelId,
            ),
        )
        return true
    }

    private companion object {
        const val IMMEDIATE_ALARM_DELAY_MS = 5_000L
    }
}

fun Context.canPostFinanceNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Tells the owner, once per outage, that automatic backups have stopped: a backup failed and one
 * is overdue. Called from the backup worker, the only thing that knows a backup just failed.
 */
fun notifyBackupProblem(context: Context, lastSuccessfulBackupAt: Instant?, now: Instant) {
    val preferences = NotificationPreferences(context)
    val key = lastSuccessfulBackupAt?.toEpochMilli()?.toString() ?: "never"
    if (!preferences.loadSettings().backupAlertsEnabled || preferences.hasBackupAlertFired(key)) return
    if (!context.canPostFinanceNotifications()) return
    ensureNotificationChannel(context)
    val days = lastSuccessfulBackupAt?.let { java.time.Duration.between(it, now).toDays().toInt().coerceAtLeast(1) }
    context.getSystemService(NotificationManager::class.java).notify(
        stableId("backup"),
        buildFinanceNotification(
            context = context,
            title = days?.let { context.resources.getQuantityString(R.plurals.notification_backup_stale_title, it, it) }
                ?: context.getString(R.string.notification_backup_never_title),
            body = context.getString(R.string.notification_backup_body),
            destination = DESTINATION_SETTINGS,
            channelId = CHANNEL_ACCOUNTS,
        ),
    )
    preferences.markBackupAlertFired(key)
}

/** One channel per kind of notice, so each can be silenced on its own in the system settings. */
internal fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    listOf(
        CHANNEL_RECURRING to R.string.notification_channel_recurring,
        CHANNEL_PLAN to R.string.notification_channel_plan,
        CHANNEL_ACCOUNTS to R.string.notification_channel_accounts,
    ).forEach { (id, name) ->
        manager.createNotificationChannel(NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT))
    }
    manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
}

internal fun buildFinanceNotification(
    context: Context,
    title: String,
    body: String,
    destination: String,
    channelId: String,
): Notification {
    val contentIntent = PendingIntent.getActivity(
        context,
        stableId("open:$destination"),
        Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_DESTINATION, destination)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return Notification.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(NOTIFICATION_ACCENT)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()
}

internal fun stableId(seed: String): Int = seed.hashCode() and Int.MAX_VALUE

internal const val CHANNEL_RECURRING = "recurring_reminders"
internal const val CHANNEL_PLAN = "plan_alerts"
internal const val CHANNEL_ACCOUNTS = "account_alerts"
private const val LEGACY_CHANNEL_ID = "finance_alerts"

/** Summa's forest green, tinting the icon and the app name on every notification. */
private const val NOTIFICATION_ACCENT = 0xFF063E29.toInt()
