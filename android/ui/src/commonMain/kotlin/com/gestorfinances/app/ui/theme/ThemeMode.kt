package com.gestorfinances.app.ui.theme

import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

interface ThemeSettingsRepository {
    val mode: StateFlow<ThemeMode>
    fun setMode(mode: ThemeMode)
}
