package com.gestorfinances.app.data.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidBackupDatabaseInspectorTest {

    @Test
    fun requiredAppObjectsMatchesFreshlyCreatedSchema() {
        // Drift guard (matches MigrationTest's pattern): REQUIRED_APP_OBJECTS is a hand-maintained
        // last-line-of-defense list used to reject a backup file that declares the current schema
        // version but is structurally incomplete. If a future migration adds a table/view and this
        // list isn't updated, a genuinely corrupted backup could slip past validation — fail CI
        // instead by diffing the constant against what GestorDatabase.Schema.create() actually
        // produces.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)

        val actualObjects = mutableSetOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type IN ('table','view')",
            mapper = { cursor ->
                while (cursor.next().value) {
                    actualObjects += cursor.getString(0)!!
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )

        assertEquals(
            "AndroidBackupDatabaseInspector.REQUIRED_APP_OBJECTS is out of sync with the tables/" +
                "views a fresh GestorDatabase.Schema.create() actually produces — update the " +
                "REQUIRED_APP_OBJECTS constant (and its duplicate in " +
                "tools/convert_legacy_json_backup.py) to match shared/schema/schema.sql",
            actualObjects.toSortedSet(),
            AndroidBackupDatabaseInspector.REQUIRED_APP_OBJECTS.toSortedSet(),
        )
    }
}
