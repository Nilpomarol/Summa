package com.gestorfinances.app.notifications

import android.content.Context

const val DEFAULT_RECURRING_LEAD_DAYS = 1

data class NotificationSettings(
    val recurringLeadDays: Int = DEFAULT_RECURRING_LEAD_DAYS,
    val budgetAlertsEnabled: Boolean = true,
    val lowBalanceAlertsEnabled: Boolean = true,
)

interface NotificationSettingsRepository {
    fun loadSettings(): NotificationSettings
    fun saveSettings(settings: NotificationSettings)
}

interface NotificationRefresher {
    suspend fun refreshNotifications()

    companion object {
        val NoOp: NotificationRefresher = object : NotificationRefresher {
            override suspend fun refreshNotifications() = Unit
        }
    }
}

class NotificationPreferences(context: Context) : NotificationSettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadSettings(): NotificationSettings =
        NotificationSettings(
            recurringLeadDays = prefs.getInt(KEY_RECURRING_LEAD_DAYS, DEFAULT_RECURRING_LEAD_DAYS),
            budgetAlertsEnabled = prefs.getBoolean(KEY_BUDGET_ALERTS, true),
            lowBalanceAlertsEnabled = prefs.getBoolean(KEY_LOW_BALANCE_ALERTS, true),
        )

    override fun saveSettings(settings: NotificationSettings) {
        prefs.edit()
            .putInt(KEY_RECURRING_LEAD_DAYS, settings.recurringLeadDays)
            .putBoolean(KEY_BUDGET_ALERTS, settings.budgetAlertsEnabled)
            .putBoolean(KEY_LOW_BALANCE_ALERTS, settings.lowBalanceAlertsEnabled)
            .apply()
    }

    fun scheduledRecurringKeys(): Set<String> =
        prefs.getStringSet(KEY_SCHEDULED_RECURRING, emptySet()).orEmpty().toSet()

    fun saveScheduledRecurringKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_SCHEDULED_RECURRING, keys).apply()
    }

    fun hasRecurringReminderFired(key: String): Boolean =
        prefs.getBoolean("$KEY_RECURRING_FIRED_PREFIX$key", false)

    fun markRecurringReminderFired(key: String) {
        prefs.edit().putBoolean("$KEY_RECURRING_FIRED_PREFIX$key", true).apply()
    }

    fun hasBudgetAlertFired(key: String): Boolean =
        prefs.getBoolean("$KEY_BUDGET_FIRED_PREFIX$key", false)

    fun markBudgetAlertFired(key: String) {
        prefs.edit().putBoolean("$KEY_BUDGET_FIRED_PREFIX$key", true).apply()
    }

    fun isLowBalanceActive(accountId: String): Boolean =
        prefs.getBoolean("$KEY_LOW_BALANCE_ACTIVE_PREFIX$accountId", false)

    fun setLowBalanceActive(
        accountId: String,
        active: Boolean,
    ) {
        prefs.edit().putBoolean("$KEY_LOW_BALANCE_ACTIVE_PREFIX$accountId", active).apply()
    }

    private companion object {
        const val PREFS_NAME = "finance_notifications"
        const val KEY_RECURRING_LEAD_DAYS = "recurring_lead_days"
        const val KEY_BUDGET_ALERTS = "budget_alerts_enabled"
        const val KEY_LOW_BALANCE_ALERTS = "low_balance_alerts_enabled"
        const val KEY_SCHEDULED_RECURRING = "scheduled_recurring_keys"
        const val KEY_RECURRING_FIRED_PREFIX = "recurring_fired:"
        const val KEY_BUDGET_FIRED_PREFIX = "budget_fired:"
        const val KEY_LOW_BALANCE_ACTIVE_PREFIX = "low_balance_active:"
    }
}
