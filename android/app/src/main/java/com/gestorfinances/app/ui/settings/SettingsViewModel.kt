package com.gestorfinances.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.DataSeeder
import com.gestorfinances.app.notifications.NotificationPreferences
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.notifications.NotificationSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val preferences: NotificationPreferences,
    private val dataSeeder: DataSeeder,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onScreenShown() {
        val settings = preferences.loadSettings()
        _state.value = SettingsUiState.fromSettings(settings)
    }

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

    private fun saveSettings(settings: NotificationSettings) {
        preferences.saveSettings(settings)
        _state.value = SettingsUiState.fromSettings(settings)
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching { notificationRefresher.refreshNotifications() }
            }
        }
    }

    class Factory(
        private val preferences: NotificationPreferences,
        private val dataSeeder: DataSeeder,
        private val notificationRefresher: NotificationRefresher,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    preferences = preferences,
                    dataSeeder = dataSeeder,
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
