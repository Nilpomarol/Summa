package com.gestorfinances.app.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class DatabaseDriverFactory(
    private val context: Context,
) {
    fun create(): AndroidSqliteDriver =
        AndroidSqliteDriver(
            schema = GestorDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(GestorDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                    db.enableWriteAheadLogging()
                    db.query("PRAGMA busy_timeout = 5000").close()
                }
            },
        )

    companion object {
        const val DATABASE_NAME: String = "gestor-finances.db"

        fun databaseFile(context: Context) =
            context.applicationContext.getDatabasePath(DATABASE_NAME)
    }
}
