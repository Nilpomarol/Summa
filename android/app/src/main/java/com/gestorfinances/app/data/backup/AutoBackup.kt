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
import com.gestorfinances.app.BuildConfig
import com.gestorfinances.app.GestorFinancesApp
import com.gestorfinances.app.notifications.notifyBackupProblem
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Automatic backups are on unless the owner turns them off; they run once a folder is chosen. */
data class AutoBackupSettings(
    val enabled: Boolean = true,
    val interval: AutoBackupInterval = AutoBackupInterval.DAILY,
    val lastSuccessfulBackupAt: Instant? = null,
) {
    /** A backup is due: none yet, or a whole interval has passed since the last one. */
    fun isDue(now: Instant): Boolean =
        lastSuccessfulBackupAt?.let { !it.plus(Duration.ofDays(interval.repeatDays)).isAfter(now) } ?: true

    /**
     * A due backup has not happened even with a day's grace (the system defers background work,
     * e.g. on low battery), so something is stopping it.
     */
    fun isOverdue(now: Instant): Boolean =
        lastSuccessfulBackupAt?.let { it.plus(Duration.ofDays(interval.repeatDays + OVERDUE_GRACE_DAYS)).isBefore(now) } ?: true
}

private const val OVERDUE_GRACE_DAYS = 1L

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
            // A debug build often shares the real app's folder, so it backs up only when asked.
            enabled = prefs.getBoolean(KEY_ENABLED, !BuildConfig.DEBUG),
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

/**
 * Keeps automatic backups running: schedules them when on and a folder is chosen, and backs up
 * straight away when one is due. Called whenever the app opens, so a phone that was off or a
 * schedule the system dropped catches up on its own.
 */
fun AutoBackupScheduler.ensureScheduled(settings: AutoBackupSettings, hasFolder: Boolean, now: Instant) {
    if (!hasFolder) return
    update(settings, runImmediately = settings.enabled && settings.isDue(now))
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
                ExistingWorkPolicy.KEEP,
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
        // The periodic run and the catch-up run can both fire for one due moment.
        val last = settings.lastSuccessfulBackupAt
        if (last != null && Duration.between(last, Instant.now()) < Duration.ofHours(1)) return Result.success()

        return runCatching {
            container.backupSnapshotService.exportAutomaticallyToFolder(folder.uriString)
            container.autoBackupPreferences.recordSuccessfulBackup(Instant.now())
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                // It will be tried again; say so only once backups have fallen behind.
                val now = Instant.now()
                if (settings.isOverdue(now)) {
                    runCatching { notifyBackupProblem(applicationContext, settings.lastSuccessfulBackupAt, now) }
                }
                Result.retry()
            },
        )
    }
}
