package com.gestorfinances.app.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {

    @Test
    fun `v1 to v2 migration adds tag_id to splits and updates schema_version`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // Build minimal v1 schema. FK enforcement is off so we don't need every
        // referenced table — this test is about schema shape, not data integrity.
        driver.execute(null, "PRAGMA foreign_keys = OFF", 0)

        driver.execute(
            null,
            "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            0,
        )
        driver.execute(null, "INSERT INTO meta VALUES ('schema_version', '1')", 0)

        // v1 splits table: no tag_id column, no tag_id CHECK.
        driver.execute(
            null,
            """
            CREATE TABLE splits (
                id                 TEXT    PRIMARY KEY,
                movement_id        TEXT,
                payer_person_id    TEXT,
                entry_method       TEXT    NOT NULL,
                total_amount_cents INTEGER,
                date               TEXT,
                description        TEXT,
                category_id        TEXT,
                trip_id            TEXT,
                created_at         TEXT    NOT NULL,
                updated_at         TEXT    NOT NULL,
                archived_at        TEXT
            )
            """.trimIndent(),
            0,
        )

        // Insert a type-4 (external-payer) split row using the v1 schema.
        driver.execute(
            null,
            """
            INSERT INTO splits (id, movement_id, payer_person_id, entry_method,
                total_amount_cents, date, description, created_at, updated_at)
            VALUES ('split-1', NULL, 'person-1', 'exact', 1000, '2026-01-01',
                'Sopar', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
            """.trimIndent(),
            0,
        )

        driver.execute(null, "PRAGMA user_version = 1", 0)

        // Apply the v1 → v2 migration (runs 1.sqm: ALTER TABLE + UPDATE meta).
        GestorDatabase.Schema.migrate(driver, 1, 2)

        // Assert tag_id now appears in the splits table.
        val columns = mutableListOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info(splits)",
            mapper = { cursor ->
                while (cursor.next().value) {
                    columns.add(cursor.getString(1)!!) // column index 1 = name
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        assertTrue("splits.tag_id must exist after migration", "tag_id" in columns)

        // Assert the pre-existing row has tag_id = NULL (new column default).
        val tagId = driver.executeQuery(
            identifier = null,
            sql = "SELECT tag_id FROM splits WHERE id = 'split-1'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0))
            },
            parameters = 0,
        ).value
        assertNull("pre-existing split row must have tag_id = NULL after migration", tagId)

        // Assert meta.schema_version was bumped to '2'.
        val schemaVersion = driver.executeQuery(
            identifier = null,
            sql = "SELECT value FROM meta WHERE key = 'schema_version'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0)!!)
            },
            parameters = 0,
        ).value
        assertEquals("schema_version must be '2' after migration", "2", schemaVersion)

        // Assert v_movement_summary view was created by the migration.
        val viewCount = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='view' AND name='v_movement_summary'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 0,
        ).value
        assertEquals("v_movement_summary view must exist after migration", 1L, viewCount)

        // Regression: the view text embedded in this migration (1.sqm, generated from
        // shared/migrations/002_add_splits_tag_id.sql) must be kept in sync with
        // shared/queries/v_movement_summary.sql. A v1 database only ever gets this migration's
        // hardcoded copy — it never sees a later edit to the "fresh install" view unless this
        // migration's copy is updated too, causing "no such column" at query time for anyone
        // upgrading from v1. Assert on a column added after the migration file was first written.
        val viewSql = driver.executeQuery(
            identifier = null,
            sql = "SELECT sql FROM sqlite_master WHERE type='view' AND name='v_movement_summary'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0)!!)
            },
            parameters = 0,
        ).value
        assertTrue(
            "v_movement_summary as recreated by the v1->v2 migration is missing refunds_expense_id " +
                "(shared/migrations/002_add_splits_tag_id.sql's embedded view copy is stale " +
                "relative to shared/queries/v_movement_summary.sql)",
            "refunds_expense_id" in viewSql,
        )
    }
}
