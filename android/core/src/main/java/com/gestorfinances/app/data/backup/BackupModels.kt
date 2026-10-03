package com.gestorfinances.app.data.backup

import java.time.Instant

const val BACKUP_EXTENSION = ".gfbackup"
const val BACKUP_MIME_TYPE = "application/octet-stream"

fun isBackupFileName(displayName: String): Boolean =
    displayName.endsWith(BACKUP_EXTENSION, ignoreCase = true)

data class BackupFolder(
    val uriString: String,
    val displayLabel: String,
)

data class BackupFileCandidate(
    val id: String,
    val displayName: String,
    val parsedSnapshotVersion: Long?,
    val parsedCreatedAtUtc: Instant?,
    val lastModifiedMillis: Long?,
)

data class PendingBackupRestore(
    val tempPath: String,
    val metadata: BackupMetadata,
)

data class BackupMetadata(
    val displayName: String,
    val schemaVersion: Long,
    val snapshotVersion: Long,
    val createdAtUtc: Instant,
    val warning: BackupWarning? = null,
)

enum class BackupWarning {
    OLDER_OR_SAME_VERSION,
}

data class BackupExportResult(
    val metadata: BackupMetadata,
    val fallbackUsed: Boolean,
    val requiresAppReset: Boolean,
)

interface BackupFolderRepository {
    fun loadSelectedFolder(): BackupFolder?
    fun saveSelectedFolder(uriString: String): BackupFolder
}

interface BackupOperations {
    fun exportToFolder(folderUriString: String): BackupExportResult
    fun listBackups(folderUriString: String): List<BackupFileCandidate>
    fun prepareRestore(candidate: BackupFileCandidate): PendingBackupRestore
    /**
     * Replaces the database with [pending]. With [safetyCopyFolderUri], what is being replaced is
     * first backed up there, so a restore can itself be undone; that copy failing (no network, a
     * damaged database) does not stop the restore.
     */
    fun applyRestore(pending: PendingBackupRestore, safetyCopyFolderUri: String? = null): BackupMetadata
}

data class BackupInspection(
    val schemaVersion: String?,
    val snapshotVersion: String?,
    val integrityOk: Boolean,
    val hasRequiredAppObjects: Boolean = true,
    val foreignKeysOk: Boolean = true,
    val hasRequiredColumns: Boolean = true,
)

interface BackupDatabaseInspector {
    fun inspect(filePath: String): BackupInspection
}

class BackupSnapshotValidator(
    private val supportedSchemaVersion: Long,
) {
    fun validate(
        displayName: String,
        createdAtUtc: Instant?,
        lastModifiedMillis: Long?,
        currentSnapshotVersion: Long,
        inspection: BackupInspection,
    ): BackupValidationResult {
        if (!isBackupFileName(displayName)) {
            return BackupValidationResult.Invalid(BackupValidationError.UNSUPPORTED_FORMAT)
        }
        if (!inspection.integrityOk) {
            return BackupValidationResult.Invalid(BackupValidationError.INTEGRITY_FAILED)
        }
        if (!inspection.foreignKeysOk) return BackupValidationResult.Invalid(BackupValidationError.FOREIGN_KEYS_FAILED)
        if (!inspection.hasRequiredColumns) return BackupValidationResult.Invalid(BackupValidationError.INVALID_SCHEMA_SHAPE)
        val schemaVersionText = inspection.schemaVersion
            ?: return BackupValidationResult.Invalid(BackupValidationError.MISSING_META)
        val snapshotVersionText = inspection.snapshotVersion
            ?: return BackupValidationResult.Invalid(BackupValidationError.MISSING_META)
        val schemaVersion = schemaVersionText.toLongOrNull()
            ?: return BackupValidationResult.Invalid(BackupValidationError.NON_NUMERIC_META)
        val snapshotVersion = snapshotVersionText.toLongOrNull()
            ?: return BackupValidationResult.Invalid(BackupValidationError.NON_NUMERIC_META)
        if (schemaVersion !in 1..supportedSchemaVersion) {
            return BackupValidationResult.Invalid(BackupValidationError.UNSUPPORTED_SCHEMA)
        }
        if (!inspection.hasRequiredAppObjects) {
            return BackupValidationResult.Invalid(BackupValidationError.NOT_APP_DATABASE)
        }
        val warning = if (snapshotVersion <= currentSnapshotVersion) {
            BackupWarning.OLDER_OR_SAME_VERSION
        } else {
            null
        }
        return BackupValidationResult.Valid(
            BackupMetadata(
                displayName = displayName,
                schemaVersion = schemaVersion,
                snapshotVersion = snapshotVersion,
                createdAtUtc = createdAtUtc
                    ?: lastModifiedMillis?.let { Instant.ofEpochMilli(it) }
                    ?: Instant.EPOCH,
                warning = warning,
            ),
        )
    }
}

sealed interface BackupValidationResult {
    data class Valid(val metadata: BackupMetadata) : BackupValidationResult
    data class Invalid(val error: BackupValidationError) : BackupValidationResult
}

enum class BackupValidationError {
    UNSUPPORTED_FORMAT,
    UNREADABLE,
    MISSING_META,
    NON_NUMERIC_META,
    INTEGRITY_FAILED,
    UNSUPPORTED_SCHEMA,
    NOT_APP_DATABASE,
    FOREIGN_KEYS_FAILED,
    INVALID_SCHEMA_SHAPE,
    RESTORE_APPLY_FAILED,
    EXPORT_DESTINATION_UNAVAILABLE,
}

class BackupException(
    val reason: BackupValidationError,
    cause: Throwable? = null,
    val requiresAppReset: Boolean = false,
) : RuntimeException(reason.name, cause)
