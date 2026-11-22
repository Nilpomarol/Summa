package com.gestorfinances.desktop

import com.gestorfinances.app.data.backup.BackupDatabaseInspector
import com.gestorfinances.app.data.backup.BackupException
import com.gestorfinances.app.data.backup.BackupInspection
import com.gestorfinances.app.data.backup.BackupSchemaShape
import com.gestorfinances.app.data.backup.BackupValidationError
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import org.sqlite.SQLiteConfig

class JdbcBackupDatabaseInspector : BackupDatabaseInspector {
    override fun inspect(filePath: String): BackupInspection =
        try {
            val readOnly = SQLiteConfig().apply { setReadOnly(true) }.toProperties()
            DriverManager.getConnection("jdbc:sqlite:$filePath", readOnly).use { connection ->
                val integrityOk = connection.strings("PRAGMA integrity_check").firstOrNull().equals("ok", ignoreCase = true)
                val schemaVersion = connection.metaValue("schema_version")
                BackupInspection(
                    schemaVersion = schemaVersion,
                    snapshotVersion = connection.metaValue("snapshot_version"),
                    integrityOk = integrityOk,
                    hasRequiredAppObjects = BackupSchemaShape.hasRequiredObjects(
                        connection.strings("SELECT name FROM sqlite_master WHERE type IN ('table','view')").toSet(),
                        schemaVersion?.toLongOrNull(),
                    ),
                    foreignKeysOk = connection.strings("PRAGMA foreign_key_check").isEmpty(),
                    hasRequiredColumns = BackupSchemaShape.REQUIRED_TABLE_COLUMNS.all { (table, columns) ->
                        connection.strings("PRAGMA table_info($table)", column = 2).containsAll(columns)
                    },
                )
            }
        } catch (error: SQLException) {
            throw BackupException(BackupValidationError.UNREADABLE, error)
        }

    private fun Connection.metaValue(key: String): String? =
        try {
            strings("SELECT value FROM meta WHERE key = '$key'").firstOrNull()
        } catch (_: SQLException) {
            null
        }

    private fun Connection.strings(sql: String, column: Int = 1): List<String> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                buildList { while (rows.next()) add(rows.getString(column)) }
            }
        }
}
