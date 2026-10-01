package com.gestorfinances.app.data.backup

import com.gestorfinances.app.BuildConfig
import java.time.Instant
import java.time.YearMonth
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

/**
 * Which backups to keep: the [RECENT_FILES] newest, plus the newest of each of up to
 * [MONTHLY_FILES] earlier months they do not already cover. So the folder never holds more than
 * eight backups, yet a problem noticed weeks later can still be undone from before it began.
 * Only this build's own backups are pruned: a copy the owner renamed or put there by hand, or one
 * from another build of the app sharing the folder, is never deleted.
 */
object BackupRetention {
    const val RECENT_FILES = 5
    const val MONTHLY_FILES = 3

    /**
     * Newest first, ordered by when each snapshot was taken rather than by snapshot version.
     * Restoring an older backup rewinds `meta.snapshot_version`, so version order stops tracking
     * recency: every backup made after such a restore would rank below the stale higher-numbered
     * files that outlived it, and retention would prune the fresh ones first.
     */
    fun newestFirst(candidates: List<BackupFileCandidate>): List<BackupFileCandidate> =
        candidates.sortedWith(
            compareByDescending<BackupFileCandidate> { it.recencyMillis() ?: Long.MIN_VALUE }
                .thenByDescending { it.parsedSnapshotVersion ?: Long.MIN_VALUE },
        )

    fun filesToDelete(candidatesNewestFirst: List<BackupFileCandidate>): List<BackupFileCandidate> {
        val own = candidatesNewestFirst.filter { BackupFileNames.isOwn(it.displayName) }
        val older = own.drop(RECENT_FILES)
        val covered = own.take(RECENT_FILES).mapNotNull { it.month() }.toMutableSet()
        val monthly = mutableListOf<BackupFileCandidate>()
        for (candidate in older) {
            if (monthly.size == MONTHLY_FILES) break
            val month = candidate.month() ?: continue
            if (covered.add(month)) monthly += candidate
        }
        return older.filterNot { it in monthly }
    }

    // The filename timestamp is when the snapshot was taken; the document's last-modified time
    // only says when it landed in the folder, so it is the fallback for unparseable names.
    private fun BackupFileCandidate.recencyMillis(): Long? =
        parsedCreatedAtUtc?.toEpochMilli() ?: lastModifiedMillis

    private fun BackupFileCandidate.month(): YearMonth? =
        recencyMillis()?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC)) }
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
    /**
     * Replaces the database with [pending]. With [safetyCopyFolderUri], what is being replaced is
     * first backed up there, so a restore can itself be undone; that copy failing (no network, a
     * damaged database) does not stop the restore.
     */
    fun applyRestore(pending: PendingBackupRestore, safetyCopyFolderUri: String? = null): BackupMetadata
}

object BackupFileNames {
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.ROOT)
            .withZone(ZoneOffset.UTC)

    const val RELEASE_PREFIX = "gestor-finances-backup-v1-"
    const val DEBUG_PREFIX = "gestor-finances-debug-backup-v1-"

    /** A debug build names its backups apart, so it never prunes the real app's in a shared folder. */
    val ownPrefix: String = if (BuildConfig.DEBUG) DEBUG_PREFIX else RELEASE_PREFIX

    private val backupPattern =
        Regex("""^gestor-finances(?:-debug)?-backup-v1-(\d{12})-(\d{8}T\d{6}Z)\Q$BACKUP_EXTENSION\E$""")

    fun build(
        snapshotVersion: Long,
        createdAtUtc: Instant,
        prefix: String = ownPrefix,
    ): String {
        val version = "%012d".format(Locale.ROOT, snapshotVersion)
        return "$prefix$version-${timestampFormatter.format(createdAtUtc)}$BACKUP_EXTENSION"
    }

    /** A backup this build made itself, under the name it gave it. */
    fun isOwn(displayName: String): Boolean =
        displayName.startsWith(ownPrefix) && parse(displayName) != null

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
        if (!BackupFileNames.isBackupFile(displayName)) {
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
