package com.gestorfinances.app.di

import android.content.Context
import com.gestorfinances.app.data.db.DatabaseDriverFactory
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.MetaRepository

class AppContainer(context: Context) {
    private val database: GestorDatabase by lazy {
        GestorDatabase(DatabaseDriverFactory(context.applicationContext).create())
    }

    val metaRepository: MetaRepository by lazy {
        MetaRepository(database.metaQueries)
    }
}
