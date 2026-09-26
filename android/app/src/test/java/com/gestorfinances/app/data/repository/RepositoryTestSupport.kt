package com.gestorfinances.app.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.DatabaseDriverFactory
import com.gestorfinances.app.data.db.GestorDatabase
import java.io.File

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
 * Operations such as recurring confirmation, quick-template creation, and
 * movement-plus-split editing combine logical writes that must share one transaction.
 * Their tests follow the same shape:
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
        createFreshInstallSchema(driver)
        return GestorDatabase(driver)
    }

    /**
     * Creates the schema the way [DatabaseDriverFactory] creates a fresh install: the SQLDelight
     * schema, then the shared-account integrity triggers from the same generated asset, split by
     * the same function.
     */
    fun createFreshInstallSchema(driver: SqlDriver) {
        GestorDatabase.Schema.create(driver)
        // Unit tests run from the module directory; the build writes this asset before compiling.
        val asset = File("src/main/assets/shared_account_integrity.sql")
        check(asset.isFile) { "Missing generated asset ${asset.absolutePath}; build the app module first." }
        DatabaseDriverFactory.sharedAccountIntegrityStatements(asset.readText())
            .forEach { driver.execute(null, it, 0) }
    }
}

/** An expense [payerPersonId] paid, the whole of which the owner owes them, as the movement form saves it. */
fun personPaidExpense(
    id: String,
    payerPersonId: String,
    amountCents: Long,
    date: String,
    name: String?,
    categoryId: String? = null,
    tripId: String? = null,
    tagId: String? = null,
): MovementDraft = MovementDraft(
    id = id,
    type = MovementType.EXPENSE,
    amountCents = amountCents,
    date = date,
    accountId = null,
    destinationAccountId = null,
    categoryId = categoryId,
    tripId = tripId,
    tagId = tagId,
    name = name,
    payee = null,
    notes = null,
    isOneTime = false,
    splitWrite = MovementSplitWrite.Replace(
        MovementSplitDraft(SplitEntryMethod.EXACT, listOf(SplitLineDraft(SplitParticipantKind.USER, null, amountCents))),
    ),
    payerPersonId = payerPersonId,
)
