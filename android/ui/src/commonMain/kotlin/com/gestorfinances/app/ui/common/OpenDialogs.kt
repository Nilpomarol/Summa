package com.gestorfinances.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** How many of the app's dialogs are open: the desktop window's shortcuts stand down while any is. */
object OpenDialogs {
    var count by mutableIntStateOf(0)
        private set

    /** Called by a dialog for as long as it is shown. */
    @Composable
    fun Track() {
        DisposableEffect(Unit) {
            count++
            onDispose { count-- }
        }
    }
}
