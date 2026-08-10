package com.gestorfinances.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

interface ThemeSettingsRepository {
    val mode: StateFlow<ThemeMode>
    fun setMode(mode: ThemeMode)
}

class ThemePreferences(context: Context) : ThemeSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(loadMode())
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun setMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun loadMode(): ThemeMode =
        preferences.getString(KEY_MODE, null)
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.SYSTEM

    private companion object {
        const val PREFERENCES_NAME = "finance_theme"
        const val KEY_MODE = "mode"
    }
}
