package com.gestorfinances.app

import android.app.Application
import com.gestorfinances.app.di.AppContainer

class GestorFinancesApp : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}
