package com.gestorfinances.app.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.repository.TripAnalysisRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MigrationTest {

    @Test
    fun `v10 to v11 migration rebuilds templates without losing rows or movement links`() {
        // The rebuild drops and recreates `templates` while `movements.template_id` points at it,
        // so this guards the real hazard: a lost template, a severed recurring movement, or a
        // foreign-key violation under the enforcement the app actually runs with.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        driver.execute(null, "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "INSERT INTO meta VALUES ('schema_version', '10')", 0)
        // Foreign-key enforcement is on, so the rebuilt table's parents must exist to insert into.
        listOf("accounts", "categories", "tags", "trips", "people").forEach { table ->
            driver.execute(null, "CREATE TABLE $table (id TEXT PRIMARY KEY)", 0)
        }
        driver.execute(null, "INSERT INTO accounts VALUES ('acc')", 0)
        driver.execute(
            null,
            """
            CREATE TABLE templates (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL CHECK (type IN ('expense','income','transfer')),
                amount_cents INTEGER, account_id TEXT NOT NULL REFERENCES accounts(id),
                dest_account_id TEXT REFERENCES accounts(id),
                category_id TEXT REFERENCES categories(id), tag_id TEXT REFERENCES tags(id),
                trip_id TEXT REFERENCES trips(id), name TEXT, payee TEXT, notes TEXT,
                split_config TEXT, frequency TEXT NOT NULL, interval_count INTEGER,
                custom_unit TEXT, day_of_month INTEGER, weekday INTEGER,
                next_due_date TEXT NOT NULL, amount_is_variable INTEGER NOT NULL DEFAULT 0,
                amount_flex_cents INTEGER, date_flex_days INTEGER, lead_notification_days INTEGER,
                status TEXT NOT NULL DEFAULT 'active', created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL, archived_at TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE movements (
                id TEXT PRIMARY KEY, type TEXT NOT NULL, amount_cents INTEGER NOT NULL,
                date TEXT NOT NULL, account_id TEXT NOT NULL, person_id TEXT,
                settlement_direction TEXT, template_id TEXT REFERENCES templates(id),
                created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO templates
                (id, type, amount_cents, account_id, frequency, next_due_date, notes,
                 created_at, updated_at)
            VALUES
                ('lloguer', 'expense', 75000, 'acc', 'monthly', '2026-10-01', 'pis',
                 '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO movements
                (id, type, amount_cents, date, account_id, person_id, settlement_direction,
                 template_id, created_at, updated_at)
            VALUES
                ('mv-recurring', 'expense', 75000, '2026-09-01', 'acc', NULL, NULL, 'lloguer',
                 '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
                ('mv-settlement', 'settlement', 2000, '2026-09-02', 'acc', 'p1', 'person_to_user',
                 NULL, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
            """.trimIndent(),
            0,
        )
        driver.execute(null, "PRAGMA user_version = 10", 0)

        GestorDatabase.Schema.migrate(driver, 10, 11)

        assertEquals("pis", driver.selectString("SELECT notes FROM templates WHERE id = 'lloguer'"))
        assertEquals(
            "lloguer",
            driver.selectString("SELECT template_id FROM movements WHERE id = 'mv-recurring'"),
        )
        // Existing settlements consumed debt without restriction, so they backfill to 'all'.
        assertEquals(
            "all",
            driver.selectString("SELECT settlement_scope FROM movements WHERE id = 'mv-settlement'"),
        )
        assertNull(driver.selectString("SELECT settlement_scope FROM movements WHERE id = 'mv-recurring'"))
        assertEquals(0L, driver.selectLong("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        assertEquals(
            0L,
            driver.selectLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE name IN ('templates_new', 'templates_migration_backup')",
            ),
        )
        assertEquals(
            1L,
            driver.selectLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_templates_next_due'",
            ),
        )
        assertEquals("11", driver.selectString("SELECT value FROM meta WHERE key = 'schema_version'"))
    }

    @Test
    fun `v9 to v10 migration removes dormant auto-categorization rules`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GestorDatabase.Schema.create(driver)
        driver.execute(
            null,
            """
            CREATE TABLE auto_cat_rules (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                priority INTEGER NOT NULL,
                conditions TEXT NOT NULL,
                active INTEGER NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            "INSERT INTO auto_cat_rules VALUES ('rule', 'Rule', 1, '{}', 1, '2026-01-01T00:00:00Z')",
            0,
        )
        driver.execute(null, "UPDATE meta SET value = '9' WHERE key = 'schema_version'", 0)
        driver.execute(null, "PRAGMA user_version = 9", 0)

        GestorDatabase.Schema.migrate(driver, 9, 10)

        val tableCount = driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'auto_cat_rules'",
            { cursor -> cursor.next(); QueryResult.Value(cursor.getLong(0)!!) },
            0,
        ).value
        val schemaVersion = driver.executeQuery(
            null,
            "SELECT value FROM meta WHERE key = 'schema_version'",
            { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0)!!) },
            0,
        ).value
        assertEquals(0L, tableCount)
        assertEquals("10", schemaVersion)
    }

    @Test
    fun `v8 to v9 migration applies derived refund attribution view`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GestorDatabase.Schema.create(driver)
        driver.execute(null, "UPDATE meta SET value = '8' WHERE key = 'schema_version'", 0)
        driver.execute(null, "PRAGMA user_version = 8", 0)

        GestorDatabase.Schema.migrate(driver, 8, 9)

        val schemaVersion = driver.executeQuery(
            null,
            "SELECT value FROM meta WHERE key = 'schema_version'",
            { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0)!!) },
            0,
        ).value
        assertEquals("9", schemaVersion)
    }

    @Test
    fun `v7 to v8 migration adds budget inclusion rules with safe defaults`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = OFF", 0)
        driver.execute(null, "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "INSERT INTO meta VALUES ('schema_version', '7')", 0)
        driver.execute(
            null,
            """
            CREATE TABLE budgets (
                id TEXT PRIMARY KEY, scope TEXT NOT NULL, category_id TEXT, trip_id TEXT,
                period TEXT NOT NULL, limit_amount_cents INTEGER NOT NULL,
                alert_threshold_percent INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                archived_at TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO budgets VALUES
                ('food-monthly', 'category', 'food', NULL, 'monthly', 30000, NULL,
                 '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', NULL)
            """.trimIndent(),
            0,
        )
        driver.execute(null, "PRAGMA user_version = 7", 0)

        GestorDatabase.Schema.migrate(driver, 7, 8)

        val inclusion = driver.executeQuery(
            null,
            "SELECT include_trip_expenses, include_extraordinary_expenses FROM budgets WHERE id = 'food-monthly'",
            { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!! to cursor.getLong(1)!!)
            },
            0,
        ).value
        assertEquals(1L to 1L, inclusion)
        val schemaVersion = driver.executeQuery(
            null,
            "SELECT value FROM meta WHERE key = 'schema_version'",
            { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0)!!) },
            0,
        ).value
        assertEquals("8", schemaVersion)
    }

    @Test
    fun `v6 to v7 migration preserves rules and removes their legacy start date`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = OFF", 0)
        driver.execute(null, "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "INSERT INTO meta VALUES ('schema_version', '6')", 0)
        driver.execute(
            null,
            """
            CREATE TABLE budgets (
                id TEXT PRIMARY KEY, scope TEXT NOT NULL, category_id TEXT, trip_id TEXT,
                period TEXT NOT NULL, limit_amount_cents INTEGER NOT NULL, start_date TEXT,
                alert_threshold_percent INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                archived_at TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO budgets VALUES
                ('food-monthly', 'category', 'food', NULL, 'monthly', 30000, '2026-08-10', NULL,
                 '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', NULL)
            """.trimIndent(),
            0,
        )
        driver.execute(null, "PRAGMA user_version = 6", 0)

        GestorDatabase.Schema.migrate(driver, 6, 7)

        val columns = mutableListOf<String>()
        driver.executeQuery(
            null,
            "PRAGMA table_info(budgets)",
            { cursor ->
                while (cursor.next().value) columns += cursor.getString(1)!!
                QueryResult.Value(Unit)
            },
            0,
        )
        assertTrue("budgets.start_date must be removed", "start_date" !in columns)
        val limit = driver.executeQuery(
            null,
            "SELECT limit_amount_cents FROM budgets WHERE id = 'food-monthly'",
            { cursor -> cursor.next(); QueryResult.Value(cursor.getLong(0)!!) },
            0,
        ).value
        assertEquals(30_000L, limit)
    }

    @Test
    fun `v5 to v6 migration preserves budgets and permits yearly category limits`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = OFF", 0)
        driver.execute(null, "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "INSERT INTO meta VALUES ('schema_version', '5')", 0)
        driver.execute(
            null,
            """
            CREATE TABLE budgets (
                id TEXT PRIMARY KEY,
                scope TEXT NOT NULL,
                category_id TEXT,
                trip_id TEXT,
                period TEXT NOT NULL,
                limit_amount_cents INTEGER NOT NULL,
                start_date TEXT,
                alert_threshold_percent INTEGER,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                archived_at TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO budgets VALUES
                ('food-monthly', 'category', 'food', NULL, 'monthly', 30000, NULL, NULL,
                 '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', NULL)
            """.trimIndent(),
            0,
        )
        driver.execute(null, "PRAGMA user_version = 5", 0)

        GestorDatabase.Schema.migrate(driver, 5, 6)

        val preservedPeriod = driver.executeQuery(
            null,
            "SELECT period FROM budgets WHERE id = 'food-monthly'",
            { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0)!!) },
            0,
        ).value
        assertEquals("monthly", preservedPeriod)
        driver.execute(
            null,
            """
            INSERT INTO budgets VALUES
                ('food-yearly', 'category', 'food', NULL, 'yearly', 300000, NULL, NULL,
                 '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', NULL)
            """.trimIndent(),
            0,
        )
        val schemaVersion = driver.executeQuery(
            null,
            "SELECT value FROM meta WHERE key = 'schema_version'",
            { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0)!!) },
            0,
        ).value
        assertEquals("6", schemaVersion)
    }

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

    @Test
    fun `v2 to v3 migration adds category_id and trip_type to tags and updates schema_version`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // Build minimal v2 schema. FK enforcement is off so we don't need every
        // referenced table — this test is about schema shape, not data integrity.
        driver.execute(null, "PRAGMA foreign_keys = OFF", 0)

        driver.execute(
            null,
            "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            0,
        )
        driver.execute(null, "INSERT INTO meta VALUES ('schema_version', '2')", 0)

        // v2 tags table: no category_id/trip_type columns, no scope CHECK.
        driver.execute(
            null,
            """
            CREATE TABLE tags (
                id          TEXT PRIMARY KEY,
                name        TEXT NOT NULL,
                icon        TEXT,
                color       TEXT,
                trip_id     TEXT,
                created_at  TEXT NOT NULL,
                updated_at  TEXT NOT NULL,
                archived_at TEXT
            )
            """.trimIndent(),
            0,
        )

        // Insert a pre-existing global tag using the v2 schema.
        driver.execute(
            null,
            """
            INSERT INTO tags (id, name, trip_id, created_at, updated_at)
            VALUES ('tag-1', 'Menjar', NULL, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
            """.trimIndent(),
            0,
        )

        driver.execute(null, "PRAGMA user_version = 2", 0)

        // Apply the v2 → v3 migration (runs 2.sqm: two ALTER TABLE + UPDATE meta).
        GestorDatabase.Schema.migrate(driver, 2, 3)

        // Assert category_id and trip_type now appear in the tags table.
        val columns = mutableListOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info(tags)",
            mapper = { cursor ->
                while (cursor.next().value) {
                    columns.add(cursor.getString(1)!!) // column index 1 = name
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        assertTrue("tags.category_id must exist after migration", "category_id" in columns)
        assertTrue("tags.trip_type must exist after migration", "trip_type" in columns)

        // Assert the pre-existing row has both new columns = NULL (new column default).
        val (categoryId, tripType) = driver.executeQuery(
            identifier = null,
            sql = "SELECT category_id, trip_type FROM tags WHERE id = 'tag-1'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0) to cursor.getString(1))
            },
            parameters = 0,
        ).value
        assertNull("pre-existing tag row must have category_id = NULL after migration", categoryId)
        assertNull("pre-existing tag row must have trip_type = NULL after migration", tripType)

        // Assert a newly inserted tag can round-trip both new columns.
        driver.execute(
            null,
            """
            INSERT INTO tags (id, name, trip_id, category_id, trip_type, created_at, updated_at)
            VALUES ('tag-2', 'Celebracio', NULL, 'category-1', 'celebration',
                '2026-01-02T00:00:00Z', '2026-01-02T00:00:00Z')
            """.trimIndent(),
            0,
        )
        val (newCategoryId, newTripType) = driver.executeQuery(
            identifier = null,
            sql = "SELECT category_id, trip_type FROM tags WHERE id = 'tag-2'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0) to cursor.getString(1))
            },
            parameters = 0,
        ).value
        assertEquals("category-1", newCategoryId)
        assertEquals("celebration", newTripType)

        // Assert meta.schema_version was bumped to '3'.
        val schemaVersion = driver.executeQuery(
            identifier = null,
            sql = "SELECT value FROM meta WHERE key = 'schema_version'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0)!!)
            },
            parameters = 0,
        ).value
        assertEquals("schema_version must be '3' after migration", "3", schemaVersion)
    }

    @Test
    fun `v3 to v4 migration creates v_trip_actual_total view and updates schema_version`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // Build a minimal v3 schema: a v3 database has movements/trips but, prior to this
        // migration, had never had v_trip_actual_total created (it was only wired into
        // sharedViewFiles for fresh installs, not into a migration step — the trip-analysis work regression
        // this test guards against). Also includes splits/split_lines (empty) since the v3->v4
        // migration now recreates v_actual_expense too (a second post-close fix, see
        // MigrationTest's dedicated v_actual_expense case below), and its real definition joins
        // against them — SQLite resolves a view body lazily against the tables that exist at
        // query time, not at CREATE VIEW time, so a minimal fixture must have them for the
        // migrated view to be queryable at all.
        driver.execute(null, "PRAGMA foreign_keys = OFF", 0)

        driver.execute(
            null,
            "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            0,
        )
        driver.execute(null, "INSERT INTO meta VALUES ('schema_version', '3')", 0)

        driver.execute(
            null,
            """
            CREATE TABLE movements (
                id                   TEXT    PRIMARY KEY,
                type                 TEXT    NOT NULL,
                amount_cents         INTEGER NOT NULL,
                date                 TEXT    NOT NULL,
                account_id           TEXT    NOT NULL,
                dest_account_id      TEXT,
                category_id          TEXT,
                tag_id               TEXT,
                trip_id              TEXT,
                template_id          TEXT,
                person_id            TEXT,
                settlement_direction TEXT,
                refunds_expense_id   TEXT,
                actual_refund_cents  INTEGER,
                is_one_time          INTEGER NOT NULL DEFAULT 0,
                created_at           TEXT    NOT NULL,
                updated_at           TEXT    NOT NULL,
                archived_at          TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE splits (
                id                 TEXT PRIMARY KEY,
                movement_id        TEXT,
                payer_person_id    TEXT,
                date               TEXT,
                description        TEXT,
                category_id        TEXT,
                trip_id            TEXT,
                tag_id             TEXT,
                created_at         TEXT,
                updated_at         TEXT,
                archived_at        TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE split_lines (
                id                TEXT PRIMARY KEY,
                split_id          TEXT,
                participant_kind  TEXT,
                owed_amount_cents INTEGER,
                archived_at       TEXT
            )
            """.trimIndent(),
            0,
        )

        // Minimal stand-in for v_actual_expense: this test only needs some view by that name to
        // exist so v_trip_actual_total's SELECT resolves. (A real v3 database's actual
        // v_actual_expense shape — and whether the v3->v4 migration correctly recreates it — is
        // covered by the dedicated test below; don't assume this stand-in reflects it.)
        driver.execute(
            null,
            """
            CREATE VIEW v_actual_expense AS
            SELECT trip_id, amount_cents
            FROM movements
            WHERE type = 'expense' AND archived_at IS NULL
            """.trimIndent(),
            0,
        )

        driver.execute(
            null,
            """
            INSERT INTO movements (id, type, amount_cents, date, account_id, trip_id, created_at, updated_at)
            VALUES ('mv-1', 'expense', 1500, '2026-01-01', 'acc-1', 'trip-1',
                '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
            """.trimIndent(),
            0,
        )

        driver.execute(null, "PRAGMA user_version = 3", 0)

        // Apply the v3 → v4 migration (runs 3.sqm: DROP/CREATE VIEW + UPDATE meta).
        GestorDatabase.Schema.migrate(driver, 3, 4)

        // Assert v_trip_actual_total view now exists (this is exactly what Trips.sq's
        // activeTrips/tripById/tripActiveOn LEFT JOIN against; before this migration existed,
        // an existing v3 database migrating forward would hit "no such table: v_trip_actual_total"
        // at query time even though a fresh install had the view).
        val viewCount = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='view' AND name='v_trip_actual_total'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 0,
        ).value
        assertEquals("v_trip_actual_total view must exist after migration", 1L, viewCount)

        // Assert the view is actually queryable and returns correct aggregated data.
        val total = driver.executeQuery(
            identifier = null,
            sql = "SELECT total_actual_cents FROM v_trip_actual_total WHERE trip_id = 'trip-1'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 0,
        ).value
        assertEquals("v_trip_actual_total must aggregate actual expense cents per trip", 1500L, total)

        // Assert meta.schema_version was bumped to '4'.
        val schemaVersion = driver.executeQuery(
            identifier = null,
            sql = "SELECT value FROM meta WHERE key = 'schema_version'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0)!!)
            },
            parameters = 0,
        ).value
        assertEquals("schema_version must be '4' after migration", "4", schemaVersion)
    }

    @Test
    fun `v3 to v4 migration recreates v_actual_expense so upgraders that predate the tag_id fix can query tag_id`() {
        // v_actual_expense has existed since before schema_version existed at all and was
        // NEVER embedded in any migration before this fix (unlike v_movement_summary, which
        // migration 002/1.sqm has always created). Any database that was ever fresh-created
        // (Schema.create()) before the tag_id fix landed — at schema v1, v2, or v3 — therefore
        // carries the OLD view shape (no tag_id on any branch) forever, since only the v3->v4
        // migration now recreates it. Build exactly that: a full current (v4) database via
        // Schema.create(), then overwrite v_actual_expense with its verbatim pre-fix definition
        // (as it was from its first commit until this fix) and rewind to schema v3, simulating a
        // device whose last applied migration was v2->v3.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)

        driver.execute(null, "DROP VIEW v_actual_expense", 0)
        driver.execute(
            null,
            """
            CREATE VIEW v_actual_expense AS
            SELECT
                m.id AS source_id,
                m.date,
                m.category_id,
                m.trip_id,
                CASE
                    WHEN s.id IS NULL THEN m.amount_cents
                    ELSE COALESCE((
                        SELECT sl.owed_amount_cents
                        FROM split_lines sl
                        WHERE sl.split_id = s.id
                          AND sl.participant_kind = 'user'
                          AND sl.archived_at IS NULL
                    ), 0)
                END AS amount_cents,
                m.is_one_time
            FROM movements m
            LEFT JOIN splits s
                ON s.movement_id = m.id
               AND s.archived_at IS NULL
            WHERE m.type = 'expense'
              AND m.archived_at IS NULL

            UNION ALL

            SELECT
                m.id AS source_id,
                m.date,
                m.category_id,
                m.trip_id,
                -COALESCE(m.actual_refund_cents, m.amount_cents) AS amount_cents,
                COALESCE((
                    SELECT e.is_one_time
                    FROM movements e
                    WHERE e.id = m.refunds_expense_id
                ), 0) AS is_one_time
            FROM movements m
            WHERE m.type = 'refund'
              AND m.archived_at IS NULL

            UNION ALL

            SELECT
                s.id AS source_id,
                s.date,
                s.category_id,
                s.trip_id,
                COALESCE((
                    SELECT sl.owed_amount_cents
                    FROM split_lines sl
                    WHERE sl.split_id = s.id
                      AND sl.participant_kind = 'user'
                      AND sl.archived_at IS NULL
                ), 0) AS amount_cents,
                0 AS is_one_time
            FROM splits s
            WHERE s.payer_person_id IS NOT NULL
              AND s.movement_id IS NULL
              AND s.archived_at IS NULL
            """.trimIndent(),
            0,
        )
        driver.execute(null, "UPDATE meta SET value = '3' WHERE key = 'schema_version'", 0)
        driver.execute(null, "PRAGMA user_version = 3", 0)

        val now = "2026-01-01T00:00:00Z"
        driver.execute(
            null,
            """
            INSERT INTO accounts (id, name, starting_balance_cents, type, is_default, display_order, created_at, updated_at)
            VALUES ('acc-1', 'Checking', 0, 'bank', 1, 0, '$now', '$now')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO trips (id, name, type, status, created_at, updated_at)
            VALUES ('trip-1', 'Mallorca', 'trip', 'active', '$now', '$now')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO tags (id, name, trip_id, created_at, updated_at)
            VALUES ('restaurants', 'Restaurants', 'trip-1', '$now', '$now')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO tags (id, name, trip_id, created_at, updated_at)
            VALUES ('transport', 'Transport', 'trip-1', '$now', '$now')
            """.trimIndent(),
            0,
        )
        // A movement carrying its own tag_id directly (the "own" branch of v_actual_expense).
        driver.execute(
            null,
            """
            INSERT INTO movements (id, type, amount_cents, date, account_id, tag_id, trip_id, created_at, updated_at)
            VALUES ('dinner', 'expense', 1000, '2026-08-01', 'acc-1', 'restaurants', 'trip-1', '$now', '$now')
            """.trimIndent(),
            0,
        )
        // A external-payer external split (friend paid) carrying its own tag_id (the branch F7 fixed:
        // this used to be dropped into "Sense etiqueta" because the old tripActualByTag
        // re-joined movements on source_id, which doesn't resolve for a splits.id).
        driver.execute(
            null,
            """
            INSERT INTO people (id, name, created_at, updated_at)
            VALUES ('anna', 'Anna', '$now', '$now')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO splits (id, movement_id, payer_person_id, entry_method, total_amount_cents,
                date, description, trip_id, tag_id, created_at, updated_at)
            VALUES ('split-taxi', NULL, 'anna', 'exact', 1200, '2026-08-01', 'Taxi aeroport',
                'trip-1', 'transport', '$now', '$now')
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            INSERT INTO split_lines (id, split_id, participant_kind, owed_amount_cents, created_at, updated_at)
            VALUES ('sl-user', 'split-taxi', 'user', 1200, '$now', '$now')
            """.trimIndent(),
            0,
        )

        val repository = TripAnalysisRepository(database.tripAnalysisQueries)

        // Reproduce the bug for real: against the pre-fix (no tag_id) view, the same query the
        // app runs on Trip detail's "per tag" breakdown must fail exactly as it did in production.
        try {
            repository.actualByTag("trip-1")
            fail(
                "expected tripActualByTag to throw against the pre-fix v_actual_expense " +
                    "(no tag_id column) — if this doesn't throw, the stand-in view above no " +
                    "longer reproduces the reported crash",
            )
        } catch (e: Exception) {
            assertTrue(
                "expected a 'no such column' failure mentioning tag_id, got: ${e.message}",
                e.message?.contains("tag_id", ignoreCase = true) == true,
            )
        }

        // Apply the v3 -> v4 migration (runs 3.sqm: recreates v_actual_expense with tag_id,
        // recreates v_trip_actual_total, bumps schema_version).
        GestorDatabase.Schema.migrate(driver, 3, 4)

        // The real repository query must now succeed and correctly bucket both branches under
        // their own tag (not "Sense etiqueta").
        val tags = repository.actualByTag("trip-1").associate { it.tagId to it.actualCents }
        assertEquals(mapOf("restaurants" to 1_000L, "transport" to 1_200L), tags)
    }

    private fun JdbcSqliteDriver.selectString(sql: String): String? =
        executeQuery(null, sql, { cursor -> cursor.next(); QueryResult.Value(cursor.getString(0)) }, 0).value

    private fun JdbcSqliteDriver.selectLong(sql: String): Long =
        executeQuery(null, sql, { cursor -> cursor.next(); QueryResult.Value(cursor.getLong(0)!!) }, 0).value
}
