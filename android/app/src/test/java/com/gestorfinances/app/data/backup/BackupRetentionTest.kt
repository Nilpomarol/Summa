package com.gestorfinances.app.data.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun dailyBackupsKeepTheLastFiveAndTheLastOfEachOfThreeEarlierMonths() {
        // A daily backup from 1 June to 10 October, newest first.
        val start = LocalDate.parse("2026-06-01")
        val days = (0L..131L).map { start.plusDays(it) }.reversed()
        val candidates = days.mapIndexed { index, day ->
            candidate(version = (days.size - index).toLong(), takenAtEpochSecond = day.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
        }

        val kept = candidates - BackupRetention.filesToDelete(candidates).toSet()

        assertEquals(
            listOf("2026-10-10", "2026-10-09", "2026-10-08", "2026-10-07", "2026-10-06", "2026-09-30", "2026-08-31", "2026-07-31"),
            kept.map { it.parsedCreatedAtUtc!!.atZone(ZoneOffset.UTC).toLocalDate().toString() },
        )
    }

    @Test
    fun onlyThisBuildsOwnBackupsArePruned() {
        val own = (9 downTo 1).map { candidate(it.toLong()) }
        val foreign = listOf(
            own.last().copy(id = "other-build", displayName = BackupFileNames.build(0, Instant.EPOCH, prefix = BackupFileNames.RELEASE_PREFIX)),
            own.last().copy(id = "renamed", displayName = "before-moving.gfbackup"),
        ).map { it.copy(parsedCreatedAtUtc = Instant.EPOCH, lastModifiedMillis = 0L) }

        val deleted = BackupRetention.filesToDelete(BackupRetention.newestFirst(own + foreign))

        assertEquals(listOf(4L, 3L, 2L, 1L), deleted.map { it.parsedSnapshotVersion })
    }

    @Test
    fun aBackupIsDueAfterAWholeIntervalAndOverdueADayLater() {
        val last = Instant.parse("2026-09-01T10:00:00Z")
        val weekly = AutoBackupSettings(enabled = true, interval = AutoBackupInterval.WEEKLY, lastSuccessfulBackupAt = last)

        assertFalse(weekly.isDue(Instant.parse("2026-09-08T09:59:00Z")))
        assertTrue(weekly.isDue(Instant.parse("2026-09-08T10:00:00Z")))
        assertFalse(weekly.isOverdue(Instant.parse("2026-09-09T09:59:00Z")))
        assertTrue(weekly.isOverdue(Instant.parse("2026-09-09T10:01:00Z")))
        assertTrue(AutoBackupSettings(lastSuccessfulBackupAt = null).isDue(last))
    }

    @Test
    fun openingTheAppBacksUpOnlyWhenOnWithAFolderAndDue() {
        val now = Instant.parse("2026-09-10T10:00:00Z")
        val calls = mutableListOf<Pair<Boolean, Boolean>>()
        val scheduler = object : AutoBackupScheduler {
            override fun update(settings: AutoBackupSettings, runImmediately: Boolean) {
                calls += settings.enabled to runImmediately
            }
        }
        val due = AutoBackupSettings(lastSuccessfulBackupAt = now.minusSeconds(2 * 86_400))
        val fresh = AutoBackupSettings(lastSuccessfulBackupAt = now.minusSeconds(3_600))

        scheduler.ensureScheduled(due, hasFolder = false, now = now)
        scheduler.ensureScheduled(due, hasFolder = true, now = now)
        scheduler.ensureScheduled(fresh, hasFolder = true, now = now)
        scheduler.ensureScheduled(due.copy(enabled = false), hasFolder = true, now = now)

        assertEquals(listOf(true to true, true to false, false to false), calls)
    }

    private fun candidate(
        version: Long,
        takenAtEpochSecond: Long = version,
    ): BackupFileCandidate =
        BackupFileCandidate(
            id = "backup-$version@$takenAtEpochSecond",
            displayName = BackupFileNames.build(version, Instant.ofEpochSecond(takenAtEpochSecond)),
            parsedSnapshotVersion = version,
            parsedCreatedAtUtc = Instant.ofEpochSecond(takenAtEpochSecond),
            lastModifiedMillis = takenAtEpochSecond * 1000L,
        )
}
