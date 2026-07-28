package com.gestorfinances.app.data.sync

/**
 * Whether this device may currently write to the shared database (docs/architecture.md).
 * A no-op seam for future synchronization: [AppContainer][com.gestorfinances.app.di.AppContainer] always
 * exposes [Writer] until the real token/handoff protocol lands, so write paths never actually
 * need to check this yet — only the shell reads it, to render the read-only banner slot.
 */
sealed interface DeviceAccessState {
    data object Writer : DeviceAccessState
    data class ReadOnly(val holderDeviceName: String) : DeviceAccessState
}
