package com.gestorfinances.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The app's one "financial data changed" token. An Activity-wide overlay (the movement sheet,
 * recurring confirmations and reminders) advances it after each committed write, and mounted pages
 * reload when it changes. Only a successful write advances it; reading or reloading never does.
 */
class FinancialDataRevision {
    private val _value = MutableStateFlow(0L)
    val value: StateFlow<Long> = _value.asStateFlow()

    fun markChanged() {
        _value.update { it + 1 }
    }
}
