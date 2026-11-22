package com.gestorfinances.app.data.backup

import com.gestorfinances.app.BuildConfig
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    fun isBackupFile(displayName: String): Boolean = isBackupFileName(displayName)

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
