package com.gestorfinances.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.gestorfinances.app.data.sync.SyncKey
import com.gestorfinances.app.data.sync.SyncServer
import java.net.InetAddress
import java.time.Instant
import kotlinx.coroutines.launch

/** Whether the computer is reachable by the phone, for Settings to show. */
class SyncListenerState {
    /** Why the app could not start listening (the port is taken, as when the app is open twice). */
    var error by mutableStateOf<Throwable?>(null)
        internal set

    /** Whether the phone is there right now, in step and waiting for changes. */
    var connected by mutableStateOf(false)
        internal set
}

/**
 * Listens for the phone for as long as there is a pairing code. The phone does the merging and
 * hands back the result; [onMerged] runs after a round that changed this computer's data.
 */
@Composable
fun rememberSyncListener(db: DesktopDatabase, settings: DesktopSettings, onMerged: () -> Unit): SyncListenerState {
    val state = remember { SyncListenerState() }
    val scope = rememberCoroutineScope()
    val merged by rememberUpdatedState(onMerged)
    DisposableEffect(settings.syncCode) {
        val server = settings.syncCode?.let { code ->
            runCatching {
                val name = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("PC")
                SyncServer(SyncKey(code), db, settings.syncId, name, onRound = { changed ->
                    // Off the server's thread: preferences and the rebuild belong to the UI.
                    scope.launch {
                        settings.recordSync(Instant.now())
                        if (changed) merged()
                    }
                }, onPresence = { present -> scope.launch { state.connected = present } })
            }
        }
        state.error = server?.exceptionOrNull()
        onDispose {
            server?.getOrNull()?.close()
            state.connected = false
        }
    }
    return state
}
