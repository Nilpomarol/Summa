package com.gestorfinances.app.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gestorfinances.app.GestorFinancesApp
import java.time.Instant
import java.util.concurrent.TimeUnit

data class AutoBackupSettings(
    val enabled: Boolean = false,
    val lastSuccessfulBackupAt: Instant? = null,
)

interface AutoBackupSettingsRepository {
    fun load(): AutoBackupSettings
    fun setEnabled(enabled: Boolean)
    fun recordSuccessfulBackup(at: Instant)
}

class AutoBackupPreferences(context: Context) : AutoBackupSettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): AutoBackupSettings =
        AutoBackupSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            lastSuccessfulBackupAt = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L)
                .takeIf { it > 0L }
                ?.let(Instant::ofEpochMilli),
        )

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    override fun recordSuccessfulBackup(at: Instant) {
        prefs.edit().putLong(KEY_LAST_SUCCESS_AT, at.toEpochMilli()).apply()
    }

    private companion object {
        const val PREFS_NAME = "finance_backup"
        const val KEY_ENABLED = "auto_backup_enabled"
        const val KEY_LAST_SUCCESS_AT = "last_successful_backup_at"
    }
}

interface AutoBackupScheduler {
    fun update(enabled: Boolean)
}

class WorkManagerAutoBackupScheduler(context: Context) : AutoBackupScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun update(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "automatic_database_backup"
    }
}

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GestorFinancesApp
        val container = app.container
        val settings = container.autoBackupPreferences.load()
        val folder = container.backupFolderStore.loadSelectedFolder()
        if (!settings.enabled || folder == null) return Result.success()

        return runCatching {
            container.backupSnapshotService.exportAutomaticallyToFolder(folder.uriString)
            container.autoBackupPreferences.recordSuccessfulBackup(Instant.now())
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
