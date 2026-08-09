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

    private fun candidate(version: Long): BackupFileCandidate =
        BackupFileCandidate(
            id = "backup-$version",
            displayName = "backup-$version.gfbackup",
            parsedSnapshotVersion = version,
            parsedCreatedAtUtc = Instant.ofEpochSecond(version),
            lastModifiedMillis = version,
        )
}
