package com.gestorfinances.app.data.backup

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRetentionTest {
    @Test
    fun filesToDeleteKeepsOnlyTheFiveNewestBackups() {
        val candidates = (7 downTo 1).map { version -> candidate(version.toLong()) }

        val filesToDelete = BackupRetention.filesToDelete(candidates)

        assertEquals(listOf(2L, 1L), filesToDelete.mapNotNull { it.parsedSnapshotVersion })
    }

    @Test
    fun newestFirstOrdersByCapturedInstantNotBySnapshotVersion() {
        // Restoring snapshot 3 rewinds the counter, so the backups taken afterwards carry lower
        // versions than the stale files that survived the restore.
        val stale = (7 downTo 4).map { version ->
            candidate(version.toLong(), takenAtEpochSecond = version.toLong())
        }
        val afterRestore = listOf(
            candidate(4, takenAtEpochSecond = 100),
            candidate(5, takenAtEpochSecond = 200),
        )

        val newestFirst = BackupRetention.newestFirst(stale + afterRestore)

        assertEquals(
            listOf("backup-5@200", "backup-4@100", "backup-7@7", "backup-6@6", "backup-5@5", "backup-4@4"),
            newestFirst.map { it.id },
        )
    }

    @Test
    fun backupsMadeAfterRestoringAnOlderSnapshotAreNotPrunedFirst() {
        val stale = (7 downTo 3).map { version ->
            candidate(version.toLong(), takenAtEpochSecond = version.toLong())
        }
        val afterRestore = candidate(4, takenAtEpochSecond = 100)

        val filesToDelete = BackupRetention.filesToDelete(
            BackupRetention.newestFirst(stale + afterRestore),
        )

        // The fresh backup outranks every stale one, so the genuinely oldest file is the casualty.
        assertEquals(listOf("backup-3@3"), filesToDelete.map { it.id })
    }

    @Test
    fun candidatesWithUnparseableNamesFallBackToLastModified() {
        val parsed = candidate(4, takenAtEpochSecond = 10)
        val unparsed = BackupFileCandidate(
            id = "manual-copy",
            displayName = "manual-copy.gfbackup",
            parsedSnapshotVersion = null,
            parsedCreatedAtUtc = null,
            lastModifiedMillis = 90_000L,
        )

        val newestFirst = BackupRetention.newestFirst(listOf(parsed, unparsed))

        assertEquals(listOf("manual-copy", parsed.id), newestFirst.map { it.id })
    }

    private fun candidate(
        version: Long,
        takenAtEpochSecond: Long = version,
    ): BackupFileCandidate =
        BackupFileCandidate(
            id = "backup-$version@$takenAtEpochSecond",
            displayName = "backup-$version.gfbackup",
            parsedSnapshotVersion = version,
            parsedCreatedAtUtc = Instant.ofEpochSecond(takenAtEpochSecond),
            lastModifiedMillis = takenAtEpochSecond * 1000L,
        )
}
