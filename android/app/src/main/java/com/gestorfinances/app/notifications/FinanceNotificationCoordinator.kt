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
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

const val EXTRA_NOTIFICATION_DESTINATION = "com.gestorfinances.app.notifications.DESTINATION"
const val DESTINATION_RECURRING = "recurring"
const val DESTINATION_BUDGETS = "budgets"
const val DESTINATION_ACCOUNTS = "accounts"

class FinanceNotificationCoordinator(
    private val context: Context,
    private val templateRepository: TemplateRepository,
    private val budgetRepository: BudgetRepository,
    private val accountRepository: AccountRepository,
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
        val candidates = recurringReminderCandidates(
            settings = settings,
            templates = templateRepository.listActive(),
            alreadyFired = preferences::hasRecurringReminderFired,
        )
        val nextKeys = candidates.map { it.key }.toSet()
        val previousKeys = preferences.scheduledRecurringKeys()
        previousKeys.minus(nextKeys).forEach(::cancelRecurringReminder)
        candidates.forEach(::scheduleRecurringReminder)
        preferences.saveScheduledRecurringKeys(nextKeys)
    }

    private fun scheduleRecurringReminder(candidate: RecurringReminderCandidate) {
        val title = appContext.getString(
            R.string.notification_recurring_due_title,
            candidate.template.name ?: candidate.template.payee ?: candidate.template.categoryName
                ?: appContext.getString(R.string.nav_recurring),
        )
        val body = appContext.getString(
            R.string.notification_recurring_due_body,
            formatCompactDate(candidate.dueDate.toString()),
        )
        val intent = FinanceNotificationReceiver.notificationIntent(
            context = appContext,
            notificationId = stableId("recurring:${candidate.key}"),
            title = title,
            body = body,
            destination = DESTINATION_RECURRING,
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
        val evaluations = budgetRepository.evaluateAll(
            fromDate = month.atDay(1).toString(),
            toDate = month.atEndOfMonth().toString(),
        )
        budgetAlertCandidates(evaluations, month, preferences::hasBudgetAlertFired)
            .forEach { candidate ->
                val titleRes = when (candidate.status) {
                    BudgetStatus.WARN -> R.string.notification_budget_warn_title
                    BudgetStatus.OVER -> R.string.notification_budget_over_title
                    BudgetStatus.OK -> return@forEach
                }
                val budget = candidate.evaluation.budget
                val shown = postNotification(
                    notificationId = stableId("budget:${candidate.key}"),
                    title = appContext.getString(titleRes),
                    body = appContext.getString(
                        R.string.notification_budget_body,
                        budget.displayName ?: appContext.getString(R.string.common_no_category),
                        formatEuroCents(candidate.evaluation.actualCents),
                        formatEuroCents(budget.limitAmountCents),
                    ),
                    destination = DESTINATION_BUDGETS,
                )
                if (shown) {
                    preferences.markBudgetAlertFired(candidate.key)
                }
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
                    title = appContext.getString(R.string.notification_low_balance_title),
                    body = appContext.getString(
                        R.string.notification_low_balance_body,
                        candidate.account.name,
                        formatEuroCents(candidate.account.currentBalanceCents),
                        formatEuroCents(candidate.thresholdCents),
                    ),
                    destination = DESTINATION_ACCOUNTS,
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
    ): Boolean {
        if (!appContext.canPostFinanceNotifications()) return false
        notificationManager.notify(
            notificationId,
            buildFinanceNotification(
                context = appContext,
                title = title,
                body = body,
                destination = destination,
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

internal fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        FINANCE_NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_alerts),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    manager.createNotificationChannel(channel)
}

internal fun buildFinanceNotification(
    context: Context,
    title: String,
    body: String,
    destination: String,
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
    return Notification.Builder(context, FINANCE_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()
}

internal fun stableId(seed: String): Int = seed.hashCode() and Int.MAX_VALUE

internal const val FINANCE_NOTIFICATION_CHANNEL_ID = "finance_alerts"
