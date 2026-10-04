package com.gestorfinances.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.sync.ConflictChoice
import com.gestorfinances.app.data.sync.PairedComputer
import com.gestorfinances.app.data.sync.PhoneSyncOperations
import com.gestorfinances.app.data.sync.SyncConflict
import com.gestorfinances.app.data.sync.SyncLinkException
import com.gestorfinances.app.data.sync.SyncRound
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncUiState(
    /** The computers this phone synchronizes with, each on whatever network it lives. */
    val computers: List<PairedComputer> = emptyList(),
    val busy: Boolean = false,
    /** Whether the last round reached a computer: the phone is in step with it and listening. */
    val connected: Boolean = false,
    /** Asking for a computer's pairing code. */
    val showPairing: Boolean = false,
    /** Records changed on both devices, waiting for the owner to say which version stays. */
    val conflicts: List<SyncConflict> = emptyList(),
    val message: SettingsMessage? = null,
)

/**
 * Synchronization with the owner's computers as the app shows it. Rounds the app runs by itself
 * are quiet: no computer being around is the normal case, and only what the owner must settle
 * interrupts. [onDataChanged] runs after a round that brought something in, so the pages reload.
 */
class SyncViewModel(
    private val sync: PhoneSyncOperations,
    private val onDataChanged: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(SyncUiState(computers = sync.computers))
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var watching: Job? = null

    /** Whether the last round found a computer. */
    private var reachable = false

    /**
     * While the app is in front: catch up, then wait for either device to change and catch up
     * again at once. The computer holds the phone's question open, so its changes arrive in a
     * moment; this phone's are noticed within a second. With no computer around it asks again
     * every half minute.
     */
    fun onForeground() {
        watching?.cancel()
        watching = viewModelScope.launch {
            while (true) {
                if (_state.value.computers.isEmpty() || _state.value.conflicts.isNotEmpty() || _state.value.busy) {
                    delay(IDLE_MILLIS)
                    continue
                }
                run(quiet = true) { sync.round(null) }.join()
                if (!reachable) {
                    delay(ABSENT_MILLIS)
                    continue
                }
                // Up to date: now wait for either device to change.
                while (true) {
                    val remote = async { runCatching { sync.awaitRemoteChange() } }
                    while (remote.isActive && !withContext(ioDispatcher) { sync.localChangePending() }) delay(IDLE_MILLIS)
                    if (remote.isActive) {
                        remote.cancel()
                        break
                    }
                    val answer = remote.await()
                    if (answer.isFailure) {
                        _state.value = _state.value.copy(connected = false)
                        delay(ABSENT_MILLIS)
                    }
                    // "Nothing yet" only means the computer stopped holding the question: ask again.
                    if (answer.getOrNull() != false) break
                }
            }
        }
    }

    fun onBackground() {
        watching?.cancel()
        watching = null
    }

    fun onSyncNowClicked() {
        run(quiet = false) { sync.round(null) }
    }

    fun onPairClicked() {
        _state.value = _state.value.copy(showPairing = true, message = null)
    }

    fun onPairingDismissed() {
        _state.value = _state.value.copy(showPairing = false)
    }

    fun onCodeEntered(code: String) {
        if (code.isBlank()) return
        _state.value = _state.value.copy(showPairing = false)
        run(quiet = false) { sync.pair(code.trim()) }
    }

    fun onUnpairClicked(id: String) {
        sync.unpair(id)
        _state.value = _state.value.copy(computers = sync.computers, message = null)
    }

    fun onConflictChoice(choice: ConflictChoice) {
        _state.value = _state.value.copy(conflicts = emptyList())
        run(quiet = false) { sync.round(choice) }
    }

    /** Left for later: the next round asks again. */
    fun onConflictsDismissed() {
        _state.value = _state.value.copy(conflicts = emptyList())
    }

    private fun run(quiet: Boolean, action: () -> SyncRound): Job {
        if (_state.value.busy) return Job().apply { complete() }
        _state.value = _state.value.copy(busy = true, message = _state.value.message.takeIf { quiet })
        return viewModelScope.launch {
            val result = withContext(ioDispatcher) { runCatching(action) }
            reachable = result.isSuccess
            val current = _state.value.copy(busy = false, computers = sync.computers, connected = result.isSuccess)
            _state.value = result.fold(
                onSuccess = { round ->
                    when (round) {
                        is SyncRound.Conflicts -> current.copy(conflicts = round.conflicts)
                        is SyncRound.Done -> {
                            if (round.changedHere) onDataChanged()
                            current.copy(
                                message = if (quiet) current.message else SettingsMessage(SettingsMessageKind.INFO, R.string.settings_sync_done),
                            )
                        }
                    }
                },
                onFailure = { error ->
                    val reason = (error as? SyncLinkException)?.reason ?: SyncLinkException.Reason.FAILED
                    val silent = quiet && (reason == SyncLinkException.Reason.NOT_FOUND || reason == SyncLinkException.Reason.FAILED)
                    current.copy(
                        message = if (silent) {
                            current.message
                        } else {
                            SettingsMessage(
                                kind = SettingsMessageKind.ERROR,
                                messageRes = when (reason) {
                                    SyncLinkException.Reason.NOT_FOUND -> R.string.settings_sync_error_not_found
                                    SyncLinkException.Reason.WRONG_CODE -> R.string.settings_sync_error_code
                                    SyncLinkException.Reason.SCHEMA -> R.string.settings_sync_error_version
                                    SyncLinkException.Reason.FAILED -> R.string.settings_sync_error_failed
                                    SyncLinkException.Reason.RULES -> R.string.settings_sync_error_rules
                                },
                                arg = if (reason == SyncLinkException.Reason.RULES) {
                                    (error as SyncLinkException).detail.orEmpty().ifEmpty { "—" }
                                } else {
                                    null
                                },
                            )
                        },
                    )
                },
            )
        }
    }

    private companion object {
        const val IDLE_MILLIS = 1_000L
        const val ABSENT_MILLIS = 30_000L
    }
}
