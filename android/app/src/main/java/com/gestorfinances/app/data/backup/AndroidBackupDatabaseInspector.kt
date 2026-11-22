package com.gestorfinances.app.data.backup

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

class AndroidBackupDatabaseInspector : BackupDatabaseInspector {
    override fun inspect(filePath: String): BackupInspection {
        val database = try {
            SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (error: SQLiteException) {
            throw BackupException(BackupValidationError.UNREADABLE, error)
        }
        return try {
            val integrityOk = database.rawQuery("PRAGMA integrity_check", emptyArray()).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            val schemaVersion = database.metaValue("schema_version")
            BackupInspection(
                schemaVersion = schemaVersion,
                snapshotVersion = database.metaValue("snapshot_version"),
                integrityOk = integrityOk,
                hasRequiredAppObjects = database.hasRequiredAppObjects(schemaVersion?.toLongOrNull()),
                foreignKeysOk = database.foreignKeysOk(),
                hasRequiredColumns = database.hasRequiredColumns(),
            )
        } catch (error: SQLiteException) {
            throw BackupException(BackupValidationError.UNREADABLE, error)
        } finally {
            database.close()
        }
    }

    private fun SQLiteDatabase.metaValue(key: String): String? =
        try {
            rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: SQLiteException) {
            null
        }

    private fun SQLiteDatabase.hasRequiredAppObjects(schemaVersion: Long?): Boolean {
        val found = mutableSetOf<String>()
        rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table','view')", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                found += cursor.getString(0)
            }
        }
        return BackupSchemaShape.hasRequiredObjects(found, schemaVersion)
    }

    private fun SQLiteDatabase.foreignKeysOk(): Boolean =
        rawQuery("PRAGMA foreign_key_check", emptyArray()).use { !it.moveToFirst() }

    private fun SQLiteDatabase.hasRequiredColumns(): Boolean =
        BackupSchemaShape.REQUIRED_TABLE_COLUMNS.all { (table, columns) ->
            val found = mutableSetOf<String>()
            rawQuery("PRAGMA table_info($table)", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) found += cursor.getString(1)
            }
            columns.all(found::contains)
        }
}
