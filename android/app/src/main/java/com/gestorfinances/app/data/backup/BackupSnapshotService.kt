package com.gestorfinances.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.cash.sqldelight.db.SqlDriver
import com.gestorfinances.app.data.db.DatabaseDriverFactory
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.MetaRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackupSnapshotService(
    context: Context,
    private val driver: SqlDriver,
    private val metaRepository: MetaRepository,
    private val closeDatabase: () -> Unit,
    private val inspector: BackupDatabaseInspector = AndroidBackupDatabaseInspector(),
    private val fileOperations: BackupFileOperations = BackupFileOperations(),
    private val now: () -> Instant = { Instant.now() },
) : BackupOperations {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val validator = BackupSnapshotValidator(
        supportedSchemaVersion = GestorDatabase.Schema.version.toLong(),
    )

    // Flips to true the instant the shared driver is actually closed for a destructive file swap
    // (restore, or the checkpoint-copy export fallback) and stays true for the rest of this
    // service instance's life — the app always recreates the Activity (and this service, via a
    // fresh AppContainer) once that happens, so it never needs to flip back. The shell observes
    // this to block all interactive UI, not just the Settings screen's own buttons, while the
    // shared SqlDriver is unusable.
    private val _isDatabaseBeingReplaced = MutableStateFlow(false)
    val isDatabaseBeingReplaced: StateFlow<Boolean> = _isDatabaseBeingReplaced.asStateFlow()

    private val guardedCloseDatabase: () -> Unit = {
        _isDatabaseBeingReplaced.value = true
        closeDatabase()
    }

    override fun exportToFolder(folderUriString: String): BackupExportResult =
        exportToFolder(folderUriString, allowDatabaseReset = true)

    /** Background work must never close the active app database as a compatibility fallback. */
    fun exportAutomaticallyToFolder(folderUriString: String): BackupExportResult =
        exportToFolder(folderUriString, allowDatabaseReset = false)

    private fun exportToFolder(
        folderUriString: String,
        allowDatabaseReset: Boolean,
    ): BackupExportResult {
        val folderUri = Uri.parse(folderUriString)
        val snapshotVersion = metaRepository.incrementSnapshotVersion()
        // The snapshot version already on record before this export, so self-validating the just-
        // produced file below correctly reports it as newer (no OLDER_OR_SAME_VERSION warning)
        // instead of comparing the export against its own version.
        val previousSnapshotVersion = snapshotVersion - 1
        val createdAtUtc = now()
        val displayName = BackupFileNames.build(snapshotVersion, createdAtUtc)
        val snapshotFile = File(appContext.cacheDir, "backup-export-$snapshotVersion.db")
        var snapshotCreation: BackupSnapshotCreation? = null
        var documentUri: Uri? = null
        val exportResult = try {
            snapshotCreation = createSnapshot(snapshotFile, allowDatabaseReset)
            documentUri = createBackupDocument(folderUri, displayName)
            resolver.openOutputStream(documentUri, "w")?.use { output ->
                FileInputStream(snapshotFile).use { input -> input.copyTo(output) }
            } ?: throw BackupException(BackupValidationError.EXPORT_DESTINATION_UNAVAILABLE)
            val metadata = validateSnapshotFile(
                file = createValidationCopy(documentUri, displayName),
                displayName = displayName,
                createdAtUtc = createdAtUtc,
                lastModifiedMillis = null,
                currentSnapshotVersion = previousSnapshotVersion,
            ).also { it.deleteSourceFile() }.metadata
            BackupExportResult(
                metadata = metadata,
                fallbackUsed = snapshotCreation?.fallbackUsed ?: false,
                requiresAppReset = snapshotCreation?.requiresAppReset ?: false,
            )
        } catch (error: Exception) {
            documentUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            if (snapshotCreation?.requiresAppReset == true && (error as? BackupException)?.requiresAppReset != true) {
                throw BackupException(
                    reason = BackupValidationError.RESTORE_APPLY_FAILED,
                    cause = error,
                    requiresAppReset = true,
                )
            }
            throw error
        } finally {
            snapshotFile.delete()
        }
        // Retention is housekeeping, and it runs against a provider that may refuse to delete an
        // old document. Never let that failure roll back — and delete — the backup just written.
        runCatching { pruneBackups(folderUri) }
        return exportResult
    }

    override fun listBackups(folderUriString: String): List<BackupFileCandidate> {
        val folderUri = Uri.parse(folderUriString)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = resolver.query(childrenUri, projection, null, null, null) ?: return emptyList()
        return cursor.use {
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex) ?: continue
                    if (!BackupFileNames.isBackupFile(displayName)) continue
                    val documentId = cursor.getString(idIndex) ?: continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                    val parsed = BackupFileNames.parse(displayName)
                    add(
                        BackupFileCandidate(
                            id = documentUri.toString(),
                            displayName = displayName,
                            parsedSnapshotVersion = parsed?.snapshotVersion,
                            parsedCreatedAtUtc = parsed?.createdAtUtc,
                            lastModifiedMillis = cursor.getLongOrNull(modifiedIndex),
                        ),
                    )
                }
            }.let(BackupRetention::newestFirst)
        }
    }

    private fun pruneBackups(folderUri: Uri) {
        BackupRetention.filesToDelete(listBackups(folderUri.toString())).forEach { candidate ->
            DocumentsContract.deleteDocument(resolver, Uri.parse(candidate.id))
        }
    }

    override fun prepareRestore(candidate: BackupFileCandidate): PendingBackupRestore {
        val documentUri = Uri.parse(candidate.id)
        val tempFile = createValidationCopy(documentUri, candidate.displayName)
        val validation = validateSnapshotFile(
            file = tempFile,
            displayName = candidate.displayName,
            createdAtUtc = candidate.parsedCreatedAtUtc,
            lastModifiedMillis = candidate.lastModifiedMillis,
            currentSnapshotVersion = currentSnapshotVersion(),
        )
        return PendingBackupRestore(
            tempPath = tempFile.absolutePath,
            metadata = validation.metadata,
        )
    }

    override fun applyRestore(pending: PendingBackupRestore): BackupMetadata {
        val source = File(pending.tempPath)
        val validation = validateSnapshotFile(
            file = source,
            displayName = pending.metadata.displayName,
            createdAtUtc = pending.metadata.createdAtUtc,
            lastModifiedMillis = null,
            currentSnapshotVersion = currentSnapshotVersion(),
        )

        try {
            stampSchemaVersion(source, validation.metadata.schemaVersion)
            fileOperations.replaceDatabaseFromBackup(
                preparedBackup = source,
                databaseFile = DatabaseDriverFactory.databaseFile(appContext),
                closeDatabase = guardedCloseDatabase,
            )
        } finally {
            // Covers the preparation step too: without this a failure before the swap would strand
            // a full copy of the database in the cache, with no pending restore left to clean it.
            source.delete()
        }
        return validation.metadata
    }

    /**
     * Prevents Android's SQLiteOpenHelper from falsely assuming the DB is empty (user_version = 0)
     * and attempting to run onCreate(), which would crash with "table already exists".
     *
     * Runs before anything touches the live database, so a failure here leaves the app untouched
     * and must not be reported as a half-applied restore.
     */
    private fun stampSchemaVersion(
        source: File,
        schemaVersion: Long,
    ) {
        val database = try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                source.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
            )
        } catch (error: Exception) {
            throw BackupException(BackupValidationError.UNREADABLE, error)
        }
        try {
            database.disableWriteAheadLogging()
            database.version = schemaVersion.toInt()
        } catch (error: Exception) {
            throw BackupException(BackupValidationError.UNREADABLE, error)
        } finally {
            database.close()
        }
    }

    private fun createSnapshot(
        destination: File,
        allowDatabaseReset: Boolean,
    ): BackupSnapshotCreation =
        if (!allowDatabaseReset) {
            fileOperations.createVacuumSnapshot(driver, destination)
        } else {
            try {
                fileOperations.createVacuumSnapshot(driver, destination)
            } catch (_: Exception) {
                fileOperations.createCheckpointCopySnapshot(
                    databaseFile = DatabaseDriverFactory.databaseFile(appContext),
                    destination = destination,
                    checkpoint = ::checkpointMainDatabase,
                    closeDatabase = guardedCloseDatabase,
                )
            }
        }

    private fun checkpointMainDatabase() {
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA wal_checkpoint(TRUNCATE)",
            mapper = { cursor ->
                cursor.next()
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            },
            parameters = 0,
        )
    }

    private fun createBackupDocument(
        folderUri: Uri,
        displayName: String,
    ): Uri {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri),
        )
        return DocumentsContract.createDocument(resolver, parentUri, BACKUP_MIME_TYPE, displayName)
            ?: throw BackupException(BackupValidationError.EXPORT_DESTINATION_UNAVAILABLE)
    }

    private fun createValidationCopy(
        documentUri: Uri,
        displayName: String,
    ): File {
        val tempFile = File(appContext.cacheDir, "backup-restore-${System.nanoTime()}$BACKUP_EXTENSION")
        try {
            resolver.openInputStream(documentUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: throw BackupException(BackupValidationError.UNREADABLE)
        } catch (error: BackupException) {
            tempFile.delete()
            throw error
        } catch (error: Exception) {
            tempFile.delete()
            throw BackupException(BackupValidationError.UNREADABLE, error)
        }
        if (!BackupFileNames.isBackupFile(displayName)) {
            tempFile.delete()
            throw BackupException(BackupValidationError.UNSUPPORTED_FORMAT)
        }
        return tempFile
    }

    private fun validateSnapshotFile(
        file: File,
        displayName: String,
        createdAtUtc: Instant?,
        lastModifiedMillis: Long?,
        currentSnapshotVersion: Long,
    ): ValidatedBackupFile {
        val inspection = try {
            inspector.inspect(file.absolutePath)
        } catch (error: BackupException) {
            file.delete()
            throw error
        } catch (error: Exception) {
            file.delete()
            throw BackupException(BackupValidationError.UNREADABLE, error)
        }
        return when (
            val result = validator.validate(
                displayName = displayName,
                createdAtUtc = createdAtUtc,
                lastModifiedMillis = lastModifiedMillis,
                currentSnapshotVersion = currentSnapshotVersion,
                inspection = inspection,
            )
        ) {
            is BackupValidationResult.Valid -> ValidatedBackupFile(file, result.metadata)
            is BackupValidationResult.Invalid -> {
                file.delete()
                throw BackupException(result.error)
            }
        }
    }

    private fun currentSnapshotVersion(): Long =
        metaRepository.load().snapshotVersion.toLongOrNull() ?: 0L

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private data class ValidatedBackupFile(
        val sourceFile: File,
        val metadata: BackupMetadata,
    ) {
        fun deleteSourceFile() {
            sourceFile.delete()
        }
    }

}
