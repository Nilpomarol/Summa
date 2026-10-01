package com.gestorfinances.app

import android.app.Application
import com.gestorfinances.app.di.AppContainer

class GestorFinancesApp : Application() {
    @Volatile
    private var currentContainer: AppContainer? = null
    private var pendingSnackbarMessage: PendingSnackbarMessage? = null

    val container: AppContainer
        get() = currentContainer ?: synchronized(this) {
            currentContainer ?: AppContainer(this).also { currentContainer = it }
        }

    fun resetContainer() {
        synchronized(this) {
            currentContainer?.close()
            currentContainer = AppContainer(this)
        }
    }

    fun postPendingSnackbarMessage(message: PendingSnackbarMessage) {
        synchronized(this) {
            pendingSnackbarMessage = message
        }
    }

    fun consumePendingSnackbarMessage(): PendingSnackbarMessage? =
        synchronized(this) {
            pendingSnackbarMessage.also { pendingSnackbarMessage = null }
        }

    override fun onTerminate() {
        currentContainer?.close()
        super.onTerminate()
    }
}

data class PendingSnackbarMessage(
    val messageRes: Int,
    val arg: String? = null,
)
