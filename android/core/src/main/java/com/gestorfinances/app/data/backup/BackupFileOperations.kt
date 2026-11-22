package com.gestorfinances.app.data.backup

import app.cash.sqldelight.db.SqlDriver
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class BackupSnapshotCreation(
    val fallbackUsed: Boolean,
    val requiresAppReset: Boolean,
)

class BackupFileOperations(
    private val moveFile: (File, File) -> Unit = ::defaultMoveFile,
) {
    fun createVacuumSnapshot(
        driver: SqlDriver,
        destination: File,
    ): BackupSnapshotCreation {
        destination.delete()
        driver.execute(null, "VACUUM INTO ${sqlLiteral(destination.absolutePath)}", 0)
        return BackupSnapshotCreation(fallbackUsed = false, requiresAppReset = false)
    }

    fun createCheckpointCopySnapshot(
        databaseFile: File,
        destination: File,
        checkpoint: () -> Unit,
        closeDatabase: () -> Unit,
    ): BackupSnapshotCreation {
        destination.delete()
        checkpoint()
        return runAfterDatabaseClose(closeDatabase) {
            copySynced(source = databaseFile, destination = destination)
            BackupSnapshotCreation(fallbackUsed = true, requiresAppReset = true)
        }
    }

    fun replaceDatabaseFromBackup(
        preparedBackup: File,
        databaseFile: File,
        closeDatabase: () -> Unit,
    ) {
        val restoreTemp = File(databaseFile.parentFile, "${databaseFile.name}.restore.tmp")
        copySynced(source = preparedBackup, destination = restoreTemp)
        // No checkpoint before close: the live database file and its -wal/-shm sidecars are about
        // to be force-deleted/overwritten below regardless of whether their WAL content was ever
        // flushed, so checkpointing here would have no observable effect on the restored result.
        runAfterDatabaseClose(closeDatabase) {
            try {
                deleteSidecarsStrict(databaseFile)
                moveFile(restoreTemp, databaseFile)
                deleteSidecarsStrict(databaseFile)
            } catch (error: Exception) {
                restoreTemp.delete()
                throw error
            }
        }
    }

    fun copySynced(
        source: File,
        destination: File,
    ) {
        destination.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun <T> runAfterDatabaseClose(
        closeDatabase: () -> Unit,
        block: () -> T,
    ): T {
        return try {
            closeDatabase()
            block()
        } catch (error: BackupException) {
            if (error.requiresAppReset) throw error
            throw BackupException(
                reason = error.reason,
                cause = error,
                requiresAppReset = true,
            )
        } catch (error: Exception) {
            throw BackupException(
                reason = BackupValidationError.RESTORE_APPLY_FAILED,
                cause = error,
                requiresAppReset = true,
            )
        }
    }

    private fun deleteSidecarsStrict(databaseFile: File) {
        databaseFile.sidecars().forEach { sidecar ->
            if (sidecar.exists() && !sidecar.delete() && sidecar.exists()) {
                throw IOException("Unable to delete SQLite sidecar: ${sidecar.absolutePath}")
            }
        }
        val remaining = databaseFile.sidecars().firstOrNull { it.exists() }
        if (remaining != null) {
            throw IOException("SQLite sidecar still exists: ${remaining.absolutePath}")
        }
    }

    private fun File.sidecars(): List<File> =
        listOf(
            File("$absolutePath-wal"),
            File("$absolutePath-shm"),
        )
}

private fun defaultMoveFile(
    source: File,
    target: File,
) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

fun sqlLiteral(value: String): String =
    "'${value.replace("'", "''")}'"
