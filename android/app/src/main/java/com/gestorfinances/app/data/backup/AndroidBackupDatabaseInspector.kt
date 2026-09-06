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
            BackupInspection(
                schemaVersion = database.metaValue("schema_version"),
                snapshotVersion = database.metaValue("snapshot_version"),
                integrityOk = integrityOk,
                hasRequiredAppObjects = database.hasRequiredAppObjects(),
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

    private fun SQLiteDatabase.hasRequiredAppObjects(): Boolean {
        val found = mutableSetOf<String>()
        rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table','view')", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                found += cursor.getString(0)
            }
        }
        return REQUIRED_APP_OBJECTS.all(found::contains)
    }

    private fun SQLiteDatabase.foreignKeysOk(): Boolean =
        rawQuery("PRAGMA foreign_key_check", emptyArray()).use { !it.moveToFirst() }

    private fun SQLiteDatabase.hasRequiredColumns(): Boolean =
        REQUIRED_TABLE_COLUMNS.all { (table, columns) ->
            val found = mutableSetOf<String>()
            rawQuery("PRAGMA table_info($table)", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) found += cursor.getString(1)
            }
            columns.all(found::contains)
        }

    companion object {
        // Kept in sync with shared/schema/schema.sql (tables) and shared/queries/*.sql (views).
        // See BackupDatabaseInspectorTest for a drift guard against GestorDatabase.Schema.create().
        val REQUIRED_APP_OBJECTS = setOf(
            "meta",
            "accounts",
            "account_members",
            "account_contributions",
            "categories",
            "people",
            "trips",
            "tags",
            "templates",
            "budgets",
            "goals",
            "goal_allocations",
            "import_batches",
            "movements",
            "splits",
            "split_lines",
            "v_account_balance",
            "v_account_value",
            "v_actual_expense",
            "v_actual_income",
            "v_person_balance",
            "v_account_flow",
            "v_movement_shared",
            "v_movement_summary",
            "v_trip_actual_total",
            "v_goal_allocation",
            "v_goal_progress",
            "v_account_allocation",
        )

        val REQUIRED_TABLE_COLUMNS = mapOf(
            "meta" to setOf("key", "value"),
            "accounts" to setOf("id", "name", "starting_balance_cents", "ownership_kind", "archived_at"),
            "account_members" to setOf("id", "account_id", "participant_kind", "ownership_basis_points", "default_expense_basis_points", "archived_at"),
            "account_contributions" to setOf("id", "shared_account_id", "contributor_kind", "amount_cents", "date", "archived_at"),
            "categories" to setOf("id", "name", "kind", "archived_at"),
            "movements" to setOf("id", "type", "amount_cents", "date", "expense_funding", "archived_at"),
            "templates" to setOf("id", "type", "account_id", "status", "archived_at"),
            "budgets" to setOf("id", "scope", "archived_at"),
            "goals" to setOf("id", "name", "target_amount_cents", "funding_mode", "status", "archived_at"),
            "goal_allocations" to setOf("id", "goal_id", "account_id", "date", "amount_cents", "archived_at"),
            "import_batches" to setOf("id", "archived_at"),
        )
    }
}
