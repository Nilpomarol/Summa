package com.gestorfinances.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FinanceNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> refreshAfterBoot(context)
            ACTION_SHOW_NOTIFICATION -> showScheduledNotification(context, intent)
        }
    }

    private fun refreshAfterBoot(context: Context) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? com.gestorfinances.app.GestorFinancesApp
                app?.container?.notificationCoordinator?.refreshNotifications()
            } finally {
                result.finish()
            }
        }
    }

    private fun showScheduledNotification(
        context: Context,
        intent: Intent,
    ) {
        ensureNotificationChannel(context)
        if (!context.canPostFinanceNotifications()) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: DESTINATION_RECURRING
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, stableId(title + body))
        context.getSystemService(android.app.NotificationManager::class.java).notify(
            notificationId,
            buildFinanceNotification(
                context = context,
                title = title,
                body = body,
                destination = destination,
            ),
        )
        intent.getStringExtra(EXTRA_RECURRING_KEY)?.let { key ->
            NotificationPreferences(context).markRecurringReminderFired(key)
        }
    }

    companion object {
        const val ACTION_SHOW_NOTIFICATION = "com.gestorfinances.app.notifications.SHOW"

        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_DESTINATION = "destination"
        private const val EXTRA_RECURRING_KEY = "recurring_key"

        fun notificationIntent(
            context: Context,
            notificationId: Int,
            title: String,
            body: String,
            destination: String,
            recurringKey: String?,
        ): Intent =
            Intent(context, FinanceNotificationReceiver::class.java).apply {
                action = ACTION_SHOW_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_RECURRING_KEY, recurringKey)
            }
    }
}
