package com.gestorfinances.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.gestorfinances.app.data.db.DataSeeder
import com.gestorfinances.app.notifications.NotificationPreferences
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.notifications.NotificationSettings
import com.gestorfinances.app.notifications.NotificationSettingsRepository
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
    private val dataSeeder: DataSeeder,
    private val backupFolderRepository: BackupFolderRepository,
    private val backupOperations: BackupOperations,
    private val autoBackupSettings: AutoBackupSettingsRepository,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    fun onScreenShown() {
        val settings = preferences.loadSettings()
        val folder = backupFolderRepository.loadSelectedFolder()
        val autoBackup = autoBackupSettings.load()
        _state.value = SettingsUiState.fromSettings(settings).copy(
            backupFolder = folder,
            autoBackupEnabled = autoBackup.enabled,
            autoBackupInterval = autoBackup.interval,
            lastSuccessfulBackupAt = autoBackup.lastSuccessfulBackupAt,
        )
    }

    fun resetForMenuNavigation() = onScreenShown()

    fun onRecurringLeadDaysChanged(value: String) {
        _state.value = _state.value.copy(recurringLeadDays = value, errorRes = null)
    }

    fun onSaveRecurringLeadDays() {
        val leadDays = _state.value.recurringLeadDays.trim().toIntOrNull()
        if (leadDays == null || leadDays < 0) {
            _state.value = _state.value.copy(errorRes = R.string.notification_validation_lead_days)
            return
        }
        saveSettings(preferences.loadSettings().copy(recurringLeadDays = leadDays))
    }

    fun onBudgetAlertsChanged(enabled: Boolean) {
        saveSettings(preferences.loadSettings().copy(budgetAlertsEnabled = enabled))
    }

    fun onLowBalanceAlertsChanged(enabled: Boolean) {
        saveSettings(preferences.loadSettings().copy(lowBalanceAlertsEnabled = enabled))
    }

    fun onSeedDataClicked() {
        _state.value = _state.value.copy(seedDataConfirmationPending = true)
    }

    fun onSeedDataDismissed() {
        _state.value = _state.value.copy(seedDataConfirmationPending = false)
    }

    fun onSeedDataConfirmed(onFinished: () -> Unit) {
        _state.value = _state.value.copy(seedDataConfirmationPending = false)
        viewModelScope.launch {
            withContext(ioDispatcher) {
                dataSeeder.seed()
                notificationRefresher.refreshNotifications()
            }
            onFinished()
        }
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
        if (uriString == null) return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { backupFolderRepository.saveSelectedFolder(uriString) }
            }
            result.onSuccess { folder ->
                _state.value = _state.value.copy(
                    backupFolder = folder,
                    backupMessage = SettingsMessage(
                        kind = SettingsMessageKind.INFO,
                        messageRes = R.string.settings_backup_folder_saved,
                    ),
                )
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

    fun onBackupImportClicked() {
        if (_state.value.isBackupBusy) return
        val folder = _state.value.backupFolder ?: return requestBackupFolder()
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
                runCatching { backupOperations.applyRestore(pending) }
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

    class Factory(
        private val preferences: NotificationPreferences,
        private val dataSeeder: DataSeeder,
        private val backupFolderRepository: BackupFolderRepository,
        private val backupOperations: BackupOperations,
        private val autoBackupSettings: AutoBackupSettingsRepository,
        private val autoBackupScheduler: AutoBackupScheduler,
        private val notificationRefresher: NotificationRefresher,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    preferences = preferences,
                    dataSeeder = dataSeeder,
                    backupFolderRepository = backupFolderRepository,
                    backupOperations = backupOperations,
                    autoBackupSettings = autoBackupSettings,
                    autoBackupScheduler = autoBackupScheduler,
                    notificationRefresher = notificationRefresher,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class SettingsUiState(
    val recurringLeadDays: String = "",
    val budgetAlertsEnabled: Boolean = true,
    val lowBalanceAlertsEnabled: Boolean = true,
    val errorRes: Int? = null,
    val seedDataConfirmationPending: Boolean = false,
    val backupFolder: BackupFolder? = null,
    val isBackupBusy: Boolean = false,
    val backupMessage: SettingsMessage? = null,
    val backupCandidates: List<BackupFileCandidate> = emptyList(),
    val showBackupList: Boolean = false,
    val pendingRestore: PendingBackupRestore? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupInterval: AutoBackupInterval = AutoBackupInterval.DAILY,
    val lastSuccessfulBackupAt: Instant? = null,
) {
    companion object {
        fun fromSettings(settings: NotificationSettings): SettingsUiState =
            SettingsUiState(
                recurringLeadDays = settings.recurringLeadDays.toString(),
                budgetAlertsEnabled = settings.budgetAlertsEnabled,
                lowBalanceAlertsEnabled = settings.lowBalanceAlertsEnabled,
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
