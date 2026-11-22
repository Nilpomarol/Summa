package com.gestorfinances.desktop

import com.gestorfinances.app.data.sync.SyncRuleBreak
import com.gestorfinances.app.data.backup.BackupException
import com.gestorfinances.app.data.backup.BackupValidationError
import com.gestorfinances.app.data.backup.BackupWarning
import com.gestorfinances.app.data.sync.ConflictChoice
import com.gestorfinances.app.data.sync.SyncKey
import com.gestorfinances.app.data.sync.SyncLinkException
import com.gestorfinances.app.data.sync.SyncRound
import com.gestorfinances.app.data.sync.SyncServer
import com.gestorfinances.app.data.sync.awaitSyncChange
import com.gestorfinances.app.data.sync.syncRound
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.MetaRepository
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDatabaseTest {
    private val dir: File = Files.createTempDirectory("summa-desktop-test").toFile()
    private val opened = mutableListOf<DesktopDatabase>()

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun open(name: String) = DesktopDatabase(File(File(dir, name), "gestor-finances.db")).also { opened += it }

    @Test
    fun firstRunCreatesTheCurrentSchemaWithTheIntegrityTriggers() {
        val db = open("fresh")

        val meta = MetaRepository(db.database.metaQueries).load()
        assertEquals(GestorDatabase.Schema.version.toString(), meta.schemaVersion)
        DriverManager.getConnection("jdbc:sqlite:${db.file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use { rows ->
                    rows.next()
                    assertTrue(rows.getInt(1) > 0)
                }
            }
        }
    }

    @Test
    fun reopeningKeepsTheData() {
        val first = open("reopen")
        MetaRepository(first.database.metaQueries).incrementSnapshotVersion()
        first.close()

        assertEquals(1L, DesktopDatabase(first.file).also { opened += it }.snapshotVersion())
    }

    @Test
    fun restoreReplacesTheDatabaseAndKeepsWhatItReplaced() {
        val backup = backupWithSnapshotVersion(2)
        val db = open("target")
        MetaRepository(db.database.metaQueries).incrementSnapshotVersion()

        val pending = db.prepareRestore(backup)
        assertEquals(2L, pending.metadata.snapshotVersion)
        assertNull(pending.metadata.warning)
        db.applyRestore(pending)

        assertEquals(2L, db.snapshotVersion())
        assertFalse(File(pending.tempPath).exists())
        assertTrue(db.safetyCopy.isFile)
        assertEquals(1L, db.prepareRestore(db.safetyCopy).metadata.snapshotVersion)
    }

    @Test
    fun aRestoredFileThatCannotBeOpenedGivesWayToWhatItReplaced() {
        val db = open("survivor")
        MetaRepository(db.database.metaQueries).incrementSnapshotVersion()
        val pending = db.prepareRestore(backupWithSnapshotVersion(2))
        // What was validated is swapped for a file from the schema before, short of the view its
        // migration starts by dropping: the migration fails.
        DriverManager.getConnection("jdbc:sqlite:${pending.tempPath}").use {
            it.createStatement().execute("UPDATE meta SET value = '${GestorDatabase.Schema.version - 1}' WHERE key = 'schema_version'")
            it.createStatement().execute("DROP VIEW v_actual_expense")
        }

        assertTrue(runCatching { db.applyRestore(pending) }.isFailure)

        assertEquals(1L, db.snapshotVersion())
    }

    @Test
    fun aFileThatCannotBeOpenedGivesWayToABackupAndIsKept() {
        val backup = backupWithSnapshotVersion(3)
        val file = File(File(dir, "broken"), "gestor-finances.db").apply {
            parentFile.mkdirs()
            writeText("not a database".repeat(100))
        }
        assertTrue(runCatching { DesktopDatabase(file).close() }.isFailure)

        val kept = DesktopDatabase.restoreOver(file, backup)

        assertTrue(kept.readText().startsWith("not a database"))
        assertEquals(3L, DesktopDatabase(file).also { opened += it }.snapshotVersion())
        // What is not a backup leaves the file where it was.
        assertThrows(BackupException::class.java) { DesktopDatabase.restoreOver(file, kept) }
        assertTrue(file.isFile)
    }

    @Test
    fun restoringAnOlderSnapshotWarns() {
        val backup = backupWithSnapshotVersion(1)
        val db = open("newer")
        repeat(3) { MetaRepository(db.database.metaQueries).incrementSnapshotVersion() }

        assertEquals(BackupWarning.OLDER_OR_SAME_VERSION, db.prepareRestore(backup).metadata.warning)
    }

    @Test
    fun anExportedBackupRestoresIntoAnotherDatabaseAndOldOnesArePruned() {
        val db = open("export")
        val folder = File(dir, "backups")
        val start = Instant.parse("2026-10-02T10:00:00Z")
        val files = (0L until 4L).map { db.exportBackup(folder, start.plusSeconds(it)) }
        assertEquals(4L, db.snapshotVersion())
        assertTrue(files.all { it.name.startsWith(DesktopDatabase.BACKUP_PREFIX) })

        val other = open("other")
        val pending = other.prepareRestore(files.last())
        assertNull(pending.metadata.warning)
        other.applyRestore(pending)
        assertEquals(4L, other.snapshotVersion())

        File(folder, "kept-by-hand.gfbackup").writeText("not ours")
        db.pruneBackups(folder, keep = 2)
        assertEquals(setOf(files[2].name, files[3].name, "kept-by-hand.gfbackup"), folder.list()!!.toSet())
    }

    @Test
    fun aBackupMadeAfterRestoringAnOlderOneIsNotPruned() {
        val db = open("rewound")
        val folder = File(dir, "rewound-backups")
        val start = Instant.parse("2026-10-02T10:00:00Z")
        val files = (0L until 4L).map { db.exportBackup(folder, start.plusSeconds(it)) }

        db.applyRestore(db.prepareRestore(files.first()))
        assertEquals(1L, db.snapshotVersion())
        val after = db.exportBackup(folder, start.plusSeconds(10))
        db.pruneBackups(folder, keep = 2)

        assertEquals(setOf(files[3].name, after.name), folder.list()!!.toSet())
    }

    @Test
    fun anOlderFileIsCopiedBeforeItIsMigrated() {
        val file = open("old").also { it.close() }.file
        val older = GestorDatabase.Schema.version - 1
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use {
            it.createStatement().execute("UPDATE meta SET value = '$older' WHERE key = 'schema_version'")
        }

        // The last migration over a schema that already had it may fail; the copy comes first either way.
        runCatching { DesktopDatabase(file).close() }

        DriverManager.getConnection("jdbc:sqlite:${DesktopDatabase.migrationCopy(file).absolutePath}").use {
            val rows = it.createStatement().executeQuery("SELECT value FROM meta WHERE key = 'schema_version'")
            assertTrue(rows.next())
            assertEquals(older.toString(), rows.getString(1))
        }
    }

    @Test
    fun closingBacksUpOnlyWhenThereIsAFolderAndSomethingChanged() {
        val node = Preferences.userRoot().node("com/gestorfinances/summa-test-${System.nanoTime()}")
        try {
            val settings = DesktopSettings(node)
            val db = open("close")
            val folder = File(dir, "on-close")

            settings.backUpOnClose(db)
            assertFalse(folder.exists())

            settings.chooseBackupFolder(folder)
            settings.backUpOnClose(db)
            assertEquals(1, folder.list()!!.size)
            settings.backUpOnClose(db)
            assertEquals(1, folder.list()!!.size)

            // A backup that cannot be written is remembered until one works.
            MetaRepository(db.database.metaQueries).incrementSnapshotVersion()
            settings.chooseBackupFolder(File(File(dir, "a-file").apply { writeText("") }, "inside"))
            settings.backUpOnClose(db)
            assertTrue(DesktopSettings(node).backupOnCloseFailed)
            settings.chooseBackupFolder(folder)
            settings.backUpOnClose(db)
            assertFalse(DesktopSettings(node).backupOnCloseFailed)

            // What it remembers survives a restart.
            assertEquals(folder, DesktopSettings(node).backupFolder)
            assertEquals(settings.lastBackupAt, DesktopSettings(node).lastBackupAt)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun twoDevicesMergeTheirRecordsOverTheLink() {
        val phone = open("phone")
        val pc = open("pc")
        val base = File(dir, "phone-base.db")
        val key = SyncKey("ABCD-EFGH-JKLM")
        var rounds = 0
        SyncServer(key, pc, "pc-1", "Casa", onRound = { rounds++ }, port = 0).use { server ->
            val address = InetSocketAddress("127.0.0.1", server.port)
            fun round(choice: ConflictChoice? = null) = syncRound(address, key, phone, base, choice)

            // The phone's data reaches an empty computer.
            phone.sql("INSERT INTO accounts (id, name, type, created_at, updated_at) VALUES ('a1', 'Principal', 'bank', '$T0', '$T0')")
            phone.movement("m1", "Lloguer", 85000, T0)
            assertEquals(SyncRound.Done(changedHere = false, changedThere = true), round())
            assertEquals("Lloguer", pc.value("SELECT name FROM movements WHERE id = 'm1'"))
            assertEquals(SyncRound.Done(changedHere = false, changedThere = false), round())

            // Each device records something: both end with everything, and nobody is asked.
            pc.movement("m2", "Mercat", 2380, T1)
            phone.movement("m3", "Cafe", 240, T1)
            assertEquals(SyncRound.Done(changedHere = true, changedThere = true), round())
            assertEquals("m1,m2,m3", phone.value("SELECT group_concat(id) FROM (SELECT id FROM movements ORDER BY id)"))
            assertEquals("m1,m2,m3", pc.value("SELECT group_concat(id) FROM (SELECT id FROM movements ORDER BY id)"))

            // An edit on one side only is taken by the other.
            pc.sql("UPDATE movements SET amount_cents = 90000, updated_at = '$T2' WHERE id = 'm1'")
            assertEquals(SyncRound.Done(changedHere = true, changedThere = false), round())
            assertEquals("90000", phone.value("SELECT amount_cents FROM movements WHERE id = 'm1'"))

            // The same record edited on both: nothing changes until the owner chooses.
            pc.sql("UPDATE movements SET name = 'Mercat PC', updated_at = '$T3' WHERE id = 'm2'")
            phone.sql("UPDATE movements SET name = 'Mercat mobil', updated_at = '$T2' WHERE id = 'm2'")
            phone.movement("m4", "Farmacia", 1612, T3)
            val conflict = round() as SyncRound.Conflicts
            assertEquals(listOf("movements" to "m2"), conflict.conflicts.map { it.kind to it.id })
            assertEquals("Mercat mobil", conflict.conflicts.single().name)
            assertNull(pc.value("SELECT name FROM movements WHERE id = 'm4'"))
            assertEquals(SyncRound.Done(changedHere = true, changedThere = true), round(ConflictChoice.NEWEST))
            assertEquals("Mercat PC", phone.value("SELECT name FROM movements WHERE id = 'm2'"))
            assertEquals("Farmacia", pc.value("SELECT name FROM movements WHERE id = 'm4'"))

            // A movement and its split are one record: a split added here against an edit there is
            // a conflict, and the side that loses leaves nothing of its version behind.
            phone.sql("INSERT INTO splits (id, movement_id, entry_method, created_at, updated_at) VALUES ('s3', 'm3', 'equal', '$T4', '$T4')")
            phone.sql("INSERT INTO split_lines (id, split_id, participant_kind, owed_amount_cents, created_at, updated_at) VALUES ('l3', 's3', 'user', 240, '$T4', '$T4')")
            pc.sql("UPDATE movements SET amount_cents = 300, updated_at = '$T4' WHERE id = 'm3'")
            assertEquals(listOf("m3"), (round() as SyncRound.Conflicts).conflicts.map { it.id })
            round(ConflictChoice.THEIRS)
            assertEquals("300", phone.value("SELECT amount_cents FROM movements WHERE id = 'm3'"))
            assertEquals("0", phone.value("SELECT count(*) FROM splits"))
            assertEquals("0", phone.value("SELECT count(*) FROM split_lines"))

            // A computer put back to an old copy does not undo what the phone has.
            pc.sql("UPDATE movements SET amount_cents = 85000, updated_at = '$T0' WHERE id = 'm1'")
            assertEquals(listOf("m1"), (round() as SyncRound.Conflicts).conflicts.map { it.id })
            round(ConflictChoice.MINE)
            assertEquals("90000", pc.value("SELECT amount_cents FROM movements WHERE id = 'm1'"))

            // A held question is answered the moment the computer's data changes.
            val heard = CompletableFuture.supplyAsync { awaitSyncChange(address, key) }
            Thread.sleep(1500)
            assertFalse(heard.isDone)
            pc.movement("m9", "Nou", 100, T4)
            assertTrue(heard.get(10, TimeUnit.SECONDS))

            // Another pairing code opens nothing.
            val wrong = assertThrows(SyncLinkException::class.java) { syncRound(address, SyncKey("WRONG-CODE-0000"), phone, base, null) }
            assertEquals(SyncLinkException.Reason.WRONG_CODE, wrong.reason)
            assertTrue(rounds >= 6)
        }
        // The integrity triggers and unique indexes are back after every merge.
        assertEquals(phone.value("SELECT count(*) FROM sqlite_master WHERE type = 'trigger'"), open("fresh-reference").value("SELECT count(*) FROM sqlite_master WHERE type = 'trigger'"))
    }

    @Test
    fun aMergeThatWouldBreakASharedAccountRuleIsRefusedWhole() {
        val one = open("one")
        one.sql("INSERT INTO accounts (id, name, type, created_at, updated_at) VALUES ('a1', 'Casa', 'bank', '$T0', '$T0')")
        one.sql("INSERT INTO people (id, name, created_at, updated_at) VALUES ('p1', 'Marc', '$T0', '$T0')")
        one.sql(
            "INSERT INTO account_members (id, account_id, participant_kind, person_id, ownership_basis_points, default_expense_basis_points, created_at, updated_at) " +
                "VALUES ('u', 'a1', 'user', NULL, 5000, 5000, '$T0', '$T0'), ('p', 'a1', 'person', 'p1', 5000, 5000, '$T0', '$T0')",
        )
        val base = File(dir, "rules-base.db").also(one::snapshot)
        val two = DesktopDatabase(File(File(dir, "two"), "gestor-finances.db").also { base.copyTo(it) }).also { opened += it }

        // One device makes the account shared; the other, still seeing it personal, pays from it as its owner.
        one.sql("UPDATE accounts SET ownership_kind = 'shared', updated_at = '$T1' WHERE id = 'a1'")
        two.sql(
            "INSERT INTO movements (id, type, amount_cents, date, account_id, name, expense_funding, created_at, updated_at) " +
                "VALUES ('mx', 'expense', 100, '2026-10-01', 'a1', 'Pa', 'owner', '$T1', '$T1')",
        )
        val theirs = File(dir, "rules-theirs.db").also(two::snapshot)

        // Refused whole, naming the record as the owner knows it.
        assertEquals(listOf("Pa"), assertThrows(SyncRuleBreak::class.java) { one.merge(theirs, base, null) }.records)
        assertEquals("0", one.value("SELECT count(*) FROM movements"))
        assertEquals(one.value("SELECT count(*) FROM sqlite_master WHERE type = 'trigger'"), two.value("SELECT count(*) FROM sqlite_master WHERE type = 'trigger'"))
    }

    private fun DesktopDatabase.sql(statement: String) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { it.executeUpdate(statement) }
        }
    }

    private fun DesktopDatabase.value(query: String): String? =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement -> statement.executeQuery(query).use { if (it.next()) it.getString(1) else null } }
        }

    private fun DesktopDatabase.movement(id: String, name: String, cents: Int, at: String) =
        sql(
            "INSERT INTO movements (id, type, amount_cents, date, account_id, name, created_at, updated_at) " +
                "VALUES ('$id', 'expense', $cents, '2026-10-01', 'a1', '$name', '$at', '$at')",
        )

    @Test
    fun aStrangerAnnouncingALargeMessageIsHungUpOn() {
        val db = open("guarded")
        SyncServer(SyncKey("AAAA-BBBB-CCCC"), db, "pc", "PC", onRound = {}, port = 0).use { server ->
            Socket("127.0.0.1", server.port).use { socket ->
                socket.soTimeout = 3000
                DataOutputStream(socket.getOutputStream()).apply { writeInt(200 * 1024 * 1024) }.flush()
                // Without the limit the computer would wait for those bytes; with it, it has gone.
                assertEquals(-1, socket.getInputStream().read())
            }
        }
    }

    @Test
    fun aPortHeldByAnotherProgramIsPassedOverAndASecondAppDefersToTheFirst() {
        ServerSocket(0).use { stranger ->
            val free = ServerSocket(0).use { it.localPort }
            val ports = listOf(stranger.localPort, free)

            val first = singleInstance(ports)!!
            assertNull(singleInstance(ports))

            val deadline = System.currentTimeMillis() + 2000
            while (first.value == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(1, first.value)
        }
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        val db = open("reject")
        val garbage = File(dir, "garbage.gfbackup").apply { writeText("not a database, just some text".repeat(50)) }
        val wrongName = File(dir, "backup.txt").apply { writeBytes(backupWithSnapshotVersion(1).readBytes()) }

        assertEquals(
            BackupValidationError.UNREADABLE,
            assertThrows(BackupException::class.java) { db.prepareRestore(garbage) }.reason,
        )
        assertEquals(
            BackupValidationError.UNSUPPORTED_FORMAT,
            assertThrows(BackupException::class.java) { db.prepareRestore(wrongName) }.reason,
        )
        assertEquals(0L, db.snapshotVersion())
        assertEquals(listOf("gestor-finances.db"), db.file.parentFile.list()!!.toList())
    }

    private companion object {
        const val T0 = "2026-10-01T08:00:00Z"
        const val T1 = "2026-10-01T09:00:00Z"
        const val T2 = "2026-10-01T10:00:00Z"
        const val T3 = "2026-10-01T11:00:00Z"
        const val T4 = "2026-10-01T12:00:00Z"
    }

    private fun backupWithSnapshotVersion(version: Int): File {
        val source = open("source-$version-${System.nanoTime()}")
        repeat(version) { MetaRepository(source.database.metaQueries).incrementSnapshotVersion() }
        source.close()
        return File(dir, "phone-$version-${System.nanoTime()}.gfbackup").also { source.file.copyTo(it) }
    }
}
