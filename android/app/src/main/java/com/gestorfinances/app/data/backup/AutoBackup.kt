package com.gestorfinances.app.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gestorfinances.app.GestorFinancesApp
import java.time.Instant
import java.util.concurrent.TimeUnit

data class AutoBackupSettings(
    val enabled: Boolean = false,
    val interval: AutoBackupInterval = AutoBackupInterval.DAILY,
    val lastSuccessfulBackupAt: Instant? = null,
)

enum class AutoBackupInterval(val repeatDays: Long) {
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
    QUARTERLY(90),
}

interface AutoBackupSettingsRepository {
    fun load(): AutoBackupSettings
    fun save(settings: AutoBackupSettings)
    fun recordSuccessfulBackup(at: Instant)
}

class AutoBackupPreferences(context: Context) : AutoBackupSettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): AutoBackupSettings =
        AutoBackupSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            interval = prefs.getString(KEY_INTERVAL, null)
                ?.let { value -> AutoBackupInterval.entries.firstOrNull { it.name == value } }
                ?: AutoBackupInterval.DAILY,
            lastSuccessfulBackupAt = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L)
                .takeIf { it > 0L }
                ?.let(Instant::ofEpochMilli),
        )

    override fun save(settings: AutoBackupSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_INTERVAL, settings.interval.name)
            .apply()
    }

    override fun recordSuccessfulBackup(at: Instant) {
        prefs.edit().putLong(KEY_LAST_SUCCESS_AT, at.toEpochMilli()).apply()
    }

    private companion object {
        const val PREFS_NAME = "finance_backup"
        const val KEY_ENABLED = "auto_backup_enabled"
        const val KEY_INTERVAL = "auto_backup_interval"
        const val KEY_LAST_SUCCESS_AT = "last_successful_backup_at"
    }
}

interface AutoBackupScheduler {
    fun update(settings: AutoBackupSettings, runImmediately: Boolean)
}

class WorkManagerAutoBackupScheduler(context: Context) : AutoBackupScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun update(settings: AutoBackupSettings, runImmediately: Boolean) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(INITIAL_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(settings.interval.repeatDays, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        if (runImmediately) {
            workManager.enqueueUniqueWork(
                INITIAL_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
            )
        }
    }

    companion object {
        const val WORK_NAME = "automatic_database_backup"
        const val INITIAL_WORK_NAME = "initial_automatic_database_backup"
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
