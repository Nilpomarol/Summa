package com.gestorfinances.app.data.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.MetaRepository
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileOperationsTest {

    @Test
    fun vacuumSnapshotContainsIncrementedSnapshotVersion() {
        val database = createDatabaseFile(snapshotVersion = 0)
        val snapshot = tempFile(requireNotNull(database.file.parentFile), "snapshot.gfbackup")
        try {
            val metaRepository = MetaRepository(database.db.metaQueries)

            val nextVersion = metaRepository.incrementSnapshotVersion()
            val result = BackupFileOperations().createVacuumSnapshot(database.driver, snapshot)

            assertEquals(1L, nextVersion)
            assertFalse(result.fallbackUsed)
            assertFalse(result.requiresAppReset)
            assertEquals("1", metaValue(snapshot, "snapshot_version"))
        } finally {
            database.driver.close()
        }
    }

    @Test
    fun checkpointCopyFallbackClosesDatabaseAndExportsValidSnapshot() {
        val database = createDatabaseFile(snapshotVersion = 7)
        val snapshot = tempFile(requireNotNull(database.file.parentFile), "fallback.gfbackup")
        var checkpointed = false
        var closed = false

        val result = BackupFileOperations().createCheckpointCopySnapshot(
            databaseFile = database.file,
            destination = snapshot,
            checkpoint = {
                checkpointed = true
                database.driver.executeQuery(
                    identifier = null,
                    sql = "PRAGMA wal_checkpoint(TRUNCATE)",
                    mapper = { cursor ->
                        cursor.next()
                        app.cash.sqldelight.db.QueryResult.Value(Unit)
                    },
                    parameters = 0,
                )
            },
            closeDatabase = {
                closed = true
                database.driver.close()
            },
        )

        assertTrue(checkpointed)
        assertTrue(closed)
        assertTrue(result.fallbackUsed)
        assertTrue(result.requiresAppReset)
        assertEquals("7", metaValue(snapshot, "snapshot_version"))
    }

    @Test
    fun restoreValidationFailureDoesNotTouchLiveDatabase() {
        val database = createDatabaseFile(snapshotVersion = 5)
        val validator = BackupSnapshotValidator(supportedSchemaVersion = 4)
        val invalidInspection = BackupInspection(
            schemaVersion = "4",
            snapshotVersion = "6",
            integrityOk = true,
            hasRequiredAppObjects = false,
        )

        val result = validator.validate(
            displayName = BackupFileNames.build(6, CREATED_AT),
            createdAtUtc = CREATED_AT,
            lastModifiedMillis = null,
            currentSnapshotVersion = 5,
            inspection = invalidInspection,
        )

        assertEquals(BackupValidationResult.Invalid(BackupValidationError.NOT_APP_DATABASE), result)
        assertEquals("5", metaValue(database.file, "snapshot_version"))
        database.driver.close()
    }

    @Test
    fun postCloseMoveFailureRequiresResetAndLeavesOldDatabaseReadable() {
        val database = createDatabaseFile(snapshotVersion = 8)
        val preparedBackup = tempFile(requireNotNull(database.file.parentFile), "prepared.gfbackup")
        BackupFileOperations().copySynced(database.file, preparedBackup)
        File("${database.file.absolutePath}-wal").writeText("stale wal")
        File("${database.file.absolutePath}-shm").writeText("stale shm")

        val failingOperations = BackupFileOperations(
            moveFile = { _, _ -> throw IOException("forced move failure") },
        )
        val error = assertThrows(BackupException::class.java) {
            failingOperations.replaceDatabaseFromBackup(
                preparedBackup = preparedBackup,
                databaseFile = database.file,
                closeDatabase = {
                    database.driver.close()
                },
            )
        }

        assertEquals(BackupValidationError.RESTORE_APPLY_FAILED, error.reason)
        assertTrue(error.requiresAppReset)
        assertEquals("8", metaValue(database.file, "snapshot_version"))
        assertFalse(File("${database.file.absolutePath}-wal").exists())
        assertFalse(File("${database.file.absolutePath}-shm").exists())
    }

    private fun createDatabaseFile(snapshotVersion: Long): TestDatabase {
        val dir = Files.createTempDirectory("gestor-backup-test").toFile()
        dir.deleteOnExit()
        val file = File(dir, "gestor-finances.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        GestorDatabase.Schema.create(driver)
        driver.execute(null, "UPDATE meta SET value = ? WHERE key = 'snapshot_version'", 1) {
            bindString(0, snapshotVersion.toString())
        }
        return TestDatabase(
            file = file,
            driver = driver,
            db = GestorDatabase(driver),
        )
    }

    private fun metaValue(
        file: File,
        key: String,
    ): String =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.prepareStatement("SELECT value FROM meta WHERE key = ?").use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }
        }

    private fun tempFile(
        parent: File,
        name: String,
    ): File =
        File(parent, name).also { it.deleteOnExit() }

    private data class TestDatabase(
        val file: File,
        val driver: JdbcSqliteDriver,
        val db: GestorDatabase,
    )

    private companion object {
        val CREATED_AT = java.time.Instant.parse("2026-07-08T10:11:12Z")
    }
}
