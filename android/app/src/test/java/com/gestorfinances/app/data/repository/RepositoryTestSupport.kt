package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase

/**
 * Shared foundation for repository-level integration tests.
 *
 * Builds an in-memory SQLite database with the current schema (including the
 * `meta` seed that `001_initial.sql` writes) and foreign keys enabled, matching
 * the runtime configuration applied by
 * [com.gestorfinances.app.data.db.DatabaseDriverFactory]. Use this for any test
 * that needs to exercise repositories against the real schema and constraints.
 *
 * ## Multi-write atomicity pattern
 *
 * Several audit findings (C3 recurring confirm, C4 quick-template create, C5
 * external-split edit) are about two logical writes that must share a single
 * transaction but currently do not. The fix for each is to collapse the two
 * writes into one `queries.transaction { }` (or a single repository method that
 * does so). The test for each fix follows the same shape:
 *
 * 1. Build a database via [newDatabase] and set up the minimum fixture the
 *    combined operation needs (an account, a person, a template, …).
 * 2. Call the combined operation with inputs that make the **second** inner
 *    write throw — typically a foreign key that does not exist or a value that
 *    violates a CHECK constraint. App-layer `require` checks that fire before
 *    the transaction opens do **not** exercise atomicity; the failure must come
 *    from the database inside the transaction.
 * 3. Assert that the **first** write's effects were rolled back (the row is
 *    absent when read back).
 *
 * Atomicity holds iff no partial state persists. See
 * [MovementRepositoryAtomicityTest] for a worked example over the existing
 * movement-plus-split write.
 */
object RepositoryTestSupport {

    fun newDatabase(): GestorDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        return GestorDatabase(driver)
    }
}
