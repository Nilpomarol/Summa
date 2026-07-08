package com.gestorfinances.app.data.backup

import java.io.File
import java.sql.DriverManager
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSnapshotValidatorTest {

    @Test
    fun filenameFormatRoundTripsAndFiltersOnlyPlainBackups() {
        val createdAt = Instant.parse("2026-07-08T10:11:12Z")
        val name = BackupFileNames.build(snapshotVersion = 42, createdAtUtc = createdAt)

        assertEquals("gestor-finances-backup-v1-000000000042-20260708T101112Z.gfbackup", name)
        assertTrue(BackupFileNames.isBackupFile(name))
        assertFalse(BackupFileNames.isBackupFile("gestor-finances-snapshot-v1-000000000042-20260708T101112Z.gfsnap"))

        val parsed = BackupFileNames.parse(name)
        assertEquals(42L, parsed?.snapshotVersion)
        assertEquals(createdAt, parsed?.createdAtUtc)
    }

    @Test
    fun validatesGenuineSqliteBackupMetadata() {
        val backup = createSqliteFile(schemaVersion = "4", snapshotVersion = "9")
        val inspection = JdbcInspector.inspect(backup.absolutePath)
        val result = validator().validate(
            displayName = BackupFileNames.build(9, CREATED_AT),
            createdAtUtc = CREATED_AT,
            lastModifiedMillis = null,
            currentSnapshotVersion = 3,
            inspection = inspection,
        )

        val metadata = (result as BackupValidationResult.Valid).metadata
        assertEquals(4L, metadata.schemaVersion)
        assertEquals(9L, metadata.snapshotVersion)
        assertEquals(CREATED_AT, metadata.createdAtUtc)
        assertEquals(null, metadata.warning)
    }

    @Test
    fun olderOrEqualSnapshotVersionWarnsButStaysValid() {
        val result = validator().validate(
            displayName = BackupFileNames.build(4, CREATED_AT),
            createdAtUtc = CREATED_AT,
            lastModifiedMillis = null,
            currentSnapshotVersion = 4,
            inspection = BackupInspection(schemaVersion = "4", snapshotVersion = "4", integrityOk = true),
        )

        val metadata = (result as BackupValidationResult.Valid).metadata
        assertEquals(BackupWarning.OLDER_OR_SAME_VERSION, metadata.warning)
    }

    @Test
    fun rejectsMissingNonNumericNewerSchemaAndFailedIntegrity() {
        assertEquals(
            BackupValidationError.MISSING_META,
            invalid(BackupInspection(schemaVersion = null, snapshotVersion = "1", integrityOk = true)),
        )
        assertEquals(
            BackupValidationError.NON_NUMERIC_META,
            invalid(BackupInspection(schemaVersion = "4", snapshotVersion = "oops", integrityOk = true)),
        )
        assertEquals(
            BackupValidationError.UNSUPPORTED_SCHEMA,
            invalid(BackupInspection(schemaVersion = "99", snapshotVersion = "1", integrityOk = true)),
        )
        assertEquals(
            BackupValidationError.INTEGRITY_FAILED,
            invalid(BackupInspection(schemaVersion = "4", snapshotVersion = "1", integrityOk = false)),
        )
        assertEquals(
            BackupValidationError.NOT_APP_DATABASE,
            invalid(
                BackupInspection(
                    schemaVersion = "4",
                    snapshotVersion = "1",
                    integrityOk = true,
                    hasRequiredAppObjects = false,
                ),
            ),
        )
    }

    private fun invalid(inspection: BackupInspection): BackupValidationError =
        (validator().validate(
            displayName = BackupFileNames.build(1, CREATED_AT),
            createdAtUtc = CREATED_AT,
            lastModifiedMillis = null,
            currentSnapshotVersion = 1,
            inspection = inspection,
        ) as BackupValidationResult.Invalid).error

    private fun validator(): BackupSnapshotValidator =
        BackupSnapshotValidator(supportedSchemaVersion = 4)

    private fun createSqliteFile(
        schemaVersion: String,
        snapshotVersion: String,
    ): File {
        val file = File.createTempFile("backup-validator", BACKUP_EXTENSION)
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                statement.execute("INSERT INTO meta VALUES ('schema_version', '$schemaVersion')")
                statement.execute("INSERT INTO meta VALUES ('snapshot_version', '$snapshotVersion')")
            }
        }
        file.deleteOnExit()
        return file
    }

    private object JdbcInspector : BackupDatabaseInspector {
        override fun inspect(filePath: String): BackupInspection {
            DriverManager.getConnection("jdbc:sqlite:$filePath").use { connection ->
                val integrityOk = connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA integrity_check").use { rows ->
                        rows.next() && rows.getString(1).equals("ok", ignoreCase = true)
                    }
                }
                fun metaValue(key: String): String? =
                    connection.prepareStatement("SELECT value FROM meta WHERE key = ?").use { statement ->
                        statement.setString(1, key)
                        statement.executeQuery().use { rows ->
                            if (rows.next()) rows.getString(1) else null
                        }
                    }
                return BackupInspection(
                    schemaVersion = metaValue("schema_version"),
                    snapshotVersion = metaValue("snapshot_version"),
                    integrityOk = integrityOk,
                )
            }
        }
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-07-08T10:11:12Z")
    }
}
