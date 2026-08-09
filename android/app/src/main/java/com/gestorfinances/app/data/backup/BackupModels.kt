package com.gestorfinances.app.data.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val BACKUP_EXTENSION = ".gfbackup"
const val BACKUP_MIME_TYPE = "application/octet-stream"

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

object BackupRetention {
    const val MAX_FILES = 5

    fun filesToDelete(candidatesNewestFirst: List<BackupFileCandidate>): List<BackupFileCandidate> =
        candidatesNewestFirst.drop(MAX_FILES)
}

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
    fun applyRestore(pending: PendingBackupRestore): BackupMetadata
}

object BackupFileNames {
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.ROOT)
            .withZone(ZoneOffset.UTC)

    private val backupPattern =
        Regex("""^gestor-finances-backup-v1-(\d{12})-(\d{8}T\d{6}Z)\Q$BACKUP_EXTENSION\E$""")

    fun build(
        snapshotVersion: Long,
        createdAtUtc: Instant,
    ): String {
        val version = "%012d".format(Locale.ROOT, snapshotVersion)
        return "gestor-finances-backup-v1-$version-${timestampFormatter.format(createdAtUtc)}$BACKUP_EXTENSION"
    }

    fun isBackupFile(displayName: String): Boolean =
        displayName.endsWith(BACKUP_EXTENSION, ignoreCase = true)

    fun parse(displayName: String): BackupFileNameInfo? {
        val match = backupPattern.matchEntire(displayName) ?: return null
        val snapshotVersion = match.groupValues[1].toLongOrNull() ?: return null
        val createdAt = runCatching { Instant.from(timestampFormatter.parse(match.groupValues[2])) }.getOrNull()
            ?: return null
        return BackupFileNameInfo(snapshotVersion = snapshotVersion, createdAtUtc = createdAt)
    }
}

data class BackupFileNameInfo(
    val snapshotVersion: Long,
    val createdAtUtc: Instant,
)

data class BackupInspection(
    val schemaVersion: String?,
    val snapshotVersion: String?,
    val integrityOk: Boolean,
    val hasRequiredAppObjects: Boolean = true,
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
        if (!BackupFileNames.isBackupFile(displayName)) {
            return BackupValidationResult.Invalid(BackupValidationError.UNSUPPORTED_FORMAT)
        }
        if (!inspection.integrityOk) {
            return BackupValidationResult.Invalid(BackupValidationError.INTEGRITY_FAILED)
        }
        val schemaVersionText = inspection.schemaVersion
            ?: return BackupValidationResult.Invalid(BackupValidationError.MISSING_META)
        val snapshotVersionText = inspection.snapshotVersion
            ?: return BackupValidationResult.Invalid(BackupValidationError.MISSING_META)
        val schemaVersion = schemaVersionText.toLongOrNull()
            ?: return BackupValidationResult.Invalid(BackupValidationError.NON_NUMERIC_META)
        val snapshotVersion = snapshotVersionText.toLongOrNull()
            ?: return BackupValidationResult.Invalid(BackupValidationError.NON_NUMERIC_META)
        if (schemaVersion > supportedSchemaVersion) {
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
    RESTORE_APPLY_FAILED,
    EXPORT_DESTINATION_UNAVAILABLE,
}

class BackupException(
    val reason: BackupValidationError,
    cause: Throwable? = null,
    val requiresAppReset: Boolean = false,
) : RuntimeException(reason.name, cause)
