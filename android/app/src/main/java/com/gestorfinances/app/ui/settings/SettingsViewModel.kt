package com.gestorfinances.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.backup.BackupException
import com.gestorfinances.app.data.backup.BackupFileCandidate
import com.gestorfinances.app.data.backup.BackupFolder
import com.gestorfinances.app.data.backup.BackupFolderRepository
import com.gestorfinances.app.data.backup.BackupMetadata
import com.gestorfinances.app.data.backup.BackupOperations
import com.gestorfinances.app.data.backup.BackupValidationError
import com.gestorfinances.app.data.backup.BackupWarning
import com.gestorfinances.app.data.backup.PendingBackupRestore
import com.gestorfinances.app.data.backup.AutoBackupScheduler
import com.gestorfinances.app.data.backup.AutoBackupSettingsRepository
import com.gestorfinances.app.data.backup.AutoBackupInterval
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.notifications.NotificationSettings
import com.gestorfinances.app.notifications.NotificationSettingsRepository
import com.gestorfinances.app.ui.theme.ThemeMode
import com.gestorfinances.app.ui.theme.ThemeSettingsRepository
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val preferences: NotificationSettingsRepository,
    private val backupFolderRepository: BackupFolderRepository,
    private val backupOperations: BackupOperations,
    private val autoBackupSettings: AutoBackupSettingsRepository,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val themePreferences: ThemeSettingsRepository,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    /** A restore asked for with no folder chosen: it carries on once one is. */
    private var importAfterFolderPick = false

    /**
     * The restore under way started from the first-run screen. Nothing is in the database yet, so
     * no copy of it goes to the folder: an empty backup would only push a real one out.
     */
    private var restoringIntoEmptyDatabase = false

    /** Re-reads when the last backup succeeded: the automatic one runs outside this page. */
    fun onBackupStatusChanged() {
        _state.value = _state.value.copy(lastSuccessfulBackupAt = autoBackupSettings.load().lastSuccessfulBackupAt)
    }

    fun onScreenShown() {
        val settings = preferences.loadSettings()
        val folder = backupFolderRepository.loadSelectedFolder()
        val autoBackup = autoBackupSettings.load()
        _state.value = SettingsUiState.fromSettings(settings).copy(
            themeMode = themePreferences.mode.value,
            backupFolder = folder,
            autoBackupEnabled = autoBackup.enabled,
            autoBackupInterval = autoBackup.interval,
            lastSuccessfulBackupAt = autoBackup.lastSuccessfulBackupAt,
        )
    }

    fun onRecurringLeadDaysSelected(days: Int) {
        saveSettings(preferences.loadSettings().copy(recurringLeadDays = days.coerceAtLeast(0)))
    }

    fun onRecurringDueTodayChanged(enabled: Boolean) {
        saveSettings(preferences.loadSettings().copy(recurringDueTodayEnabled = enabled))
    }

    fun onRecurringOverdueChanged(enabled: Boolean) {
        saveSettings(preferences.loadSettings().copy(recurringOverdueEnabled = enabled))
    }

    fun onBackupAlertsChanged(enabled: Boolean) {
        saveSettings(preferences.loadSettings().copy(backupAlertsEnabled = enabled))
    }

    fun onBudgetAlertsChanged(enabled: Boolean) {
        saveSettings(preferences.loadSettings().copy(budgetAlertsEnabled = enabled))
    }

    fun onLowBalanceAlertsChanged(enabled: Boolean) {
        saveSettings(preferences.loadSettings().copy(lowBalanceAlertsEnabled = enabled))
    }

    fun onThemeModeChanged(mode: ThemeMode) {
        themePreferences.setMode(mode)
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun onBackupChooseFolderClicked() {
        if (_state.value.isBackupBusy) return
        viewModelScope.launch {
            _effects.emit(SettingsEffect.PickBackupFolder)
        }
    }

    fun onAutoBackupChanged(enabled: Boolean) {
        val settings = autoBackupSettings.load().copy(enabled = enabled)
        autoBackupSettings.save(settings)
        autoBackupScheduler.update(settings, runImmediately = enabled)
        _state.value = _state.value.copy(autoBackupEnabled = enabled, autoBackupInterval = settings.interval)
    }

    fun onAutoBackupIntervalChanged(interval: AutoBackupInterval) {
        val settings = autoBackupSettings.load().copy(interval = interval)
        autoBackupSettings.save(settings)
        autoBackupScheduler.update(settings, runImmediately = false)
        _state.value = _state.value.copy(autoBackupInterval = interval)
    }

    fun onBackupFolderSelected(uriString: String?) {
        val thenImport = importAfterFolderPick
        importAfterFolderPick = false
        if (uriString == null) return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { backupFolderRepository.saveSelectedFolder(uriString) }
            }
            result.onSuccess { folder ->
                // A new folder starts with a backup of its own, whatever the old one held.
                val autoBackup = autoBackupSettings.load()
                if (autoBackup.enabled && !restoringIntoEmptyDatabase) {
                    autoBackupScheduler.update(autoBackup, runImmediately = true)
                }
                _state.value = _state.value.copy(
                    backupFolder = folder,
                    backupMessage = SettingsMessage(
                        kind = SettingsMessageKind.INFO,
                        messageRes = R.string.settings_backup_folder_saved,
                    ).takeUnless { thenImport },
                )
                if (thenImport) listBackups(folder)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    backupMessage = genericBackupError(error),
                )
            }
        }
    }

    fun onBackupExportClicked() {
        if (_state.value.isBackupBusy) return
        val folder = _state.value.backupFolder ?: return requestBackupFolder()
        _state.value = _state.value.copy(isBackupBusy = true, backupMessage = null)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { backupOperations.exportToFolder(folder.uriString) }
            }
            // isBackupBusy stays true whenever a reset is required: the shared driver is already
            // closed at this point (or about to be), and the Activity is about to recreate. It
            // must not flip back to false — even briefly — before RecreateApp is actually
            // handled, or a fast re-tap could race a closed driver.
            _state.value = result.fold(
                onSuccess = { export ->
                    val message = SettingsMessage(
                        kind = if (export.fallbackUsed) SettingsMessageKind.ALERT else SettingsMessageKind.INFO,
                        messageRes = if (export.fallbackUsed) {
                            R.string.settings_backup_export_success_fallback
                        } else {
                            R.string.settings_backup_export_success
                        },
                        arg = export.metadata.displayName,
                    )
                    val completedAt = Instant.now()
                    autoBackupSettings.recordSuccessfulBackup(completedAt)
                    if (export.requiresAppReset) {
                        _effects.emit(SettingsEffect.RecreateApp(message))
                        _state.value.copy(backupMessage = message, lastSuccessfulBackupAt = completedAt)
                    } else {
                        _state.value.copy(
                            isBackupBusy = false,
                            backupMessage = message,
                            lastSuccessfulBackupAt = completedAt,
                        )
                    }
                },
                onFailure = { error ->
                    val message = backupError(error)
                    if ((error as? BackupException)?.requiresAppReset == true) {
                        _effects.emit(SettingsEffect.RecreateApp(message))
                        _state.value.copy(backupMessage = message)
                    } else {
                        _state.value.copy(isBackupBusy = false, backupMessage = message)
                    }
                },
            )
        }
    }

    /**
     * Lists the folder's backups to restore one. [intoEmptyDatabase] is the first-run screen's
     * restore: it asks for the folder each time, since a wrong one cannot be changed from there.
     */
    fun onBackupImportClicked(intoEmptyDatabase: Boolean = false) {
        if (_state.value.isBackupBusy) return
        restoringIntoEmptyDatabase = intoEmptyDatabase
        val folder = _state.value.backupFolder.takeUnless { intoEmptyDatabase }
        if (folder == null) {
            importAfterFolderPick = true
            return requestBackupFolder()
        }
        listBackups(folder)
    }

    private fun listBackups(folder: BackupFolder) {
        _state.value = _state.value.copy(isBackupBusy = true, backupMessage = null)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { backupOperations.listBackups(folder.uriString) }
            }
            _state.value = result.fold(
                onSuccess = { candidates ->
                    _state.value.copy(
                        isBackupBusy = false,
                        backupCandidates = candidates,
                        showBackupList = candidates.isNotEmpty(),
                        backupMessage = if (candidates.isEmpty()) {
                            SettingsMessage(
                                kind = SettingsMessageKind.ALERT,
                                messageRes = R.string.settings_backup_import_empty,
                            )
                        } else {
                            null
                        },
                    )
                },
                onFailure = { error ->
                    _state.value.copy(isBackupBusy = false, backupMessage = backupError(error))
                },
            )
        }
    }

    fun onBackupListDismissed() {
        if (_state.value.isBackupBusy) return
        _state.value = _state.value.copy(showBackupList = false)
    }

    fun onBackupCandidateSelected(candidate: BackupFileCandidate) {
        if (_state.value.isBackupBusy) return
        _state.value = _state.value.copy(
            isBackupBusy = true,
            showBackupList = false,
            backupMessage = null,
        )
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { backupOperations.prepareRestore(candidate) }
            }
            _state.value = result.fold(
                onSuccess = { pending ->
                    _state.value.copy(isBackupBusy = false, pendingRestore = pending)
                },
                onFailure = { error ->
                    _state.value.copy(isBackupBusy = false, backupMessage = backupError(error))
                },
            )
        }
    }

    fun onRestoreDismissed() {
        if (_state.value.isBackupBusy) return
        _state.value.pendingRestore?.let { runCatching { File(it.tempPath).delete() } }
        _state.value = _state.value.copy(pendingRestore = null)
    }

    fun onRestoreConfirmed() {
        if (_state.value.isBackupBusy) return
        val pending = _state.value.pendingRestore ?: return
        _state.value = _state.value.copy(isBackupBusy = true, backupMessage = null)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    backupOperations.applyRestore(
                        pending,
                        _state.value.backupFolder?.uriString.takeUnless { restoringIntoEmptyDatabase },
                    )
                }
            }
            // isBackupBusy stays true here (never explicitly cleared) on success — applyRestore()
            // always requires an app reset, so the shell's own database-replacement overlay (and
            // the eventual Activity recreate) take over from this point. Only a failure that
            // never touched the shared driver clears it back to false.
            result.onSuccess { metadata ->
                val message = SettingsMessage(
                    kind = SettingsMessageKind.INFO,
                    messageRes = R.string.settings_backup_restore_success,
                    arg = metadata.displayName,
                )
                _state.value = _state.value.copy(
                    pendingRestore = null,
                    backupMessage = message,
                )
                _effects.emit(SettingsEffect.RecreateApp(message))
            }.onFailure { error ->
                val message = backupError(error)
                val requiresAppReset = (error as? BackupException)?.requiresAppReset == true
                _state.value = _state.value.copy(
                    isBackupBusy = requiresAppReset,
                    pendingRestore = null,
                    backupMessage = message,
                )
                if (requiresAppReset) {
                    _effects.emit(SettingsEffect.RecreateApp(message))
                }
            }
        }
    }

    private fun saveSettings(settings: NotificationSettings) {
        preferences.saveSettings(settings)
        _state.value = SettingsUiState.fromSettings(settings).copy(
            themeMode = _state.value.themeMode,
            backupFolder = _state.value.backupFolder,
            isBackupBusy = _state.value.isBackupBusy,
            backupMessage = _state.value.backupMessage,
            backupCandidates = _state.value.backupCandidates,
            showBackupList = _state.value.showBackupList,
            pendingRestore = _state.value.pendingRestore,
            autoBackupEnabled = _state.value.autoBackupEnabled,
            autoBackupInterval = _state.value.autoBackupInterval,
            lastSuccessfulBackupAt = _state.value.lastSuccessfulBackupAt,
        )
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching { notificationRefresher.refreshNotifications() }
            }
        }
    }

    private fun requestBackupFolder() {
        viewModelScope.launch {
            _effects.emit(SettingsEffect.PickBackupFolder)
        }
    }

    private fun backupError(error: Throwable): SettingsMessage =
        when ((error as? BackupException)?.reason) {
            BackupValidationError.UNSUPPORTED_FORMAT -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_format,
            )
            BackupValidationError.UNREADABLE -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_unreadable,
            )
            BackupValidationError.MISSING_META -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_meta_missing,
            )
            BackupValidationError.NON_NUMERIC_META -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_meta_invalid,
            )
            BackupValidationError.INTEGRITY_FAILED -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_integrity,
            )
            BackupValidationError.FOREIGN_KEYS_FAILED,
            BackupValidationError.INVALID_SCHEMA_SHAPE -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_not_app_database,
            )
            BackupValidationError.UNSUPPORTED_SCHEMA -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_schema,
            )
            BackupValidationError.NOT_APP_DATABASE -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_not_app_database,
            )
            BackupValidationError.RESTORE_APPLY_FAILED -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_restore_apply,
            )
            BackupValidationError.EXPORT_DESTINATION_UNAVAILABLE -> SettingsMessage(
                SettingsMessageKind.ERROR,
                R.string.settings_backup_error_export_destination,
            )
            null -> genericBackupError(error)
        }

    private fun genericBackupError(error: Throwable): SettingsMessage =
        SettingsMessage(
            kind = SettingsMessageKind.ERROR,
            messageRes = R.string.settings_backup_error_generic,
            arg = error.message ?: error.javaClass.simpleName,
        )
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recurringLeadDays: Int = 0,
    val recurringDueTodayEnabled: Boolean = true,
    val recurringOverdueEnabled: Boolean = true,
    val budgetAlertsEnabled: Boolean = true,
    val lowBalanceAlertsEnabled: Boolean = true,
    val backupAlertsEnabled: Boolean = true,
    val backupFolder: BackupFolder? = null,
    val isBackupBusy: Boolean = false,
    val backupMessage: SettingsMessage? = null,
    val backupCandidates: List<BackupFileCandidate> = emptyList(),
    val showBackupList: Boolean = false,
    val pendingRestore: PendingBackupRestore? = null,
    val autoBackupEnabled: Boolean = true,
    val autoBackupInterval: AutoBackupInterval = AutoBackupInterval.DAILY,
    val lastSuccessfulBackupAt: Instant? = null,
) {
    companion object {
        fun fromSettings(settings: NotificationSettings): SettingsUiState =
            SettingsUiState(
                recurringLeadDays = settings.recurringLeadDays,
                recurringDueTodayEnabled = settings.recurringDueTodayEnabled,
                recurringOverdueEnabled = settings.recurringOverdueEnabled,
                budgetAlertsEnabled = settings.budgetAlertsEnabled,
                lowBalanceAlertsEnabled = settings.lowBalanceAlertsEnabled,
                backupAlertsEnabled = settings.backupAlertsEnabled,
            )
    }
}

data class SettingsMessage(
    val kind: SettingsMessageKind,
    val messageRes: Int,
    val arg: String? = null,
)

enum class SettingsMessageKind {
    INFO,
    ALERT,
    ERROR,
}

sealed interface SettingsEffect {
    data object PickBackupFolder : SettingsEffect
    data class RecreateApp(val message: SettingsMessage) : SettingsEffect
}

fun BackupMetadata.hasOlderOrSameVersionWarning(): Boolean =
    warning == BackupWarning.OLDER_OR_SAME_VERSION
