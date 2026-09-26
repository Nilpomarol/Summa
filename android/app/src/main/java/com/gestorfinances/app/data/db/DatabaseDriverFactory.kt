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
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    context.assets.open(SHARED_ACCOUNT_INTEGRITY_ASSET).bufferedReader().use { reader ->
                        sharedAccountIntegrityStatements(reader.readText()).forEach(db::execSQL)
                    }
                }

                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                    db.enableWriteAheadLogging()
                    db.query("PRAGMA busy_timeout = 5000").close()
                }
            },
        )

    companion object {
        const val DATABASE_NAME: String = "gestor-finances.db"
        private const val SHARED_ACCOUNT_INTEGRITY_ASSET = "shared_account_integrity.sql"

        fun databaseFile(context: Context) =
            context.applicationContext.getDatabasePath(DATABASE_NAME)

        /** The trigger statements of the shared-account integrity asset a fresh install runs,
         * whichever line endings the file was checked out with. */
        internal fun sharedAccountIntegrityStatements(sql: String): List<String> =
            sql.replace("\r\n", "\n")
                .splitToSequence("\nEND;")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { "$it\nEND;" }
                .toList()
    }
}
