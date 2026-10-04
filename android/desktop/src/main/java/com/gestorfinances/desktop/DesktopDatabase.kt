package com.gestorfinances.desktop

import java.nio.file.Files
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.backup.BACKUP_EXTENSION
import com.gestorfinances.app.data.backup.BackupException
import com.gestorfinances.app.data.backup.BackupFileOperations
import com.gestorfinances.app.data.backup.BackupMetadata
import com.gestorfinances.app.data.backup.BackupSnapshotValidator
import com.gestorfinances.app.data.backup.BackupValidationError
import com.gestorfinances.app.data.backup.BackupValidationResult
import com.gestorfinances.app.data.backup.PendingBackupRestore
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.db.sharedAccountIntegrityStatements
import com.gestorfinances.app.data.repository.MetaRepository
import com.gestorfinances.app.data.sync.ConflictChoice
import com.gestorfinances.app.data.sync.JdbcSqlSession
import com.gestorfinances.app.data.sync.MergeResult
import com.gestorfinances.app.data.sync.SyncDevice
import com.gestorfinances.app.data.sync.mergeDatabase
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.sqlite.SQLiteConfig

/** The file was written by a newer version of the app: this one cannot open it. */
class NewerSchemaException(current: Long, latest: Long) :
    IllegalStateException("Database schema $current is newer than this app supports ($latest)")

/**
 * The desktop app's database file: created on first run, migrated when an older file is opened,
 * and replaceable by a `.gfbackup` snapshot from this app or the phone.
 */
class DesktopDatabase(
    val file: File,
    private val inspector: JdbcBackupDatabaseInspector = JdbcBackupDatabaseInspector(),
    private val fileOperations: BackupFileOperations = BackupFileOperations(),
) : AutoCloseable, SyncDevice {
    private var driver: JdbcSqliteDriver = openDriver(file)

    /** Replaced by a restore: read it again rather than keeping it or its queries. */
    var database: GestorDatabase = GestorDatabase(driver)
        private set

    /** What the last restore replaced, so it can be undone by restoring this file. */
    val safetyCopy: File get() = File(file.parentFile, "before-restore$BACKUP_EXTENSION")

    fun snapshotVersion(): Long =
        MetaRepository(database.metaQueries).load().snapshotVersion.toLongOrNull() ?: 0L

    /** Copies [source] aside and validates the copy; nothing in the live database changes. */
    fun prepareRestore(source: File): PendingBackupRestore {
        val temp = File(file.parentFile, "restore-${System.nanoTime()}$BACKUP_EXTENSION")
        try {
            fileOperations.copySynced(source, temp)
            val inspection = inspector.inspect(temp.absolutePath)
            val result = BackupSnapshotValidator(GestorDatabase.Schema.version).validate(
                displayName = source.name,
                createdAtUtc = null,
                lastModifiedMillis = source.lastModified(),
                currentSnapshotVersion = snapshotVersion(),
                inspection = inspection,
            )
            return when (result) {
                is BackupValidationResult.Valid -> PendingBackupRestore(temp.absolutePath, result.metadata)
                is BackupValidationResult.Invalid -> throw BackupException(result.error)
            }
        } catch (error: Exception) {
            temp.delete()
            throw error as? BackupException ?: BackupException(BackupValidationError.UNREADABLE, error)
        }
    }

    fun discardRestore(pending: PendingBackupRestore) {
        File(pending.tempPath).delete()
    }

    /**
     * Replaces the database with [pending], keeping what it replaces as [safetyCopy]. A restored
     * file that cannot be opened (one whose migration fails) is replaced by that copy again, so the
     * app is left on the data it had, and the failure is thrown.
     */
    fun applyRestore(pending: PendingBackupRestore): BackupMetadata {
        val source = File(pending.tempPath)
        try {
            // A failed safety copy (a damaged current database) does not stop the restore.
            val saved = runCatching { fileOperations.createVacuumSnapshot(driver, safetyCopy) }.isSuccess
            var open = true
            fun reopen() {
                driver = openDriver(file)
                database = GestorDatabase(driver)
                open = true
            }
            try {
                try {
                    fileOperations.replaceDatabaseFromBackup(
                        source,
                        file,
                        closeDatabase = {
                            closeWatcher()
                            driver.close()
                            open = false
                        },
                    )
                } finally {
                    reopen()
                }
            } catch (error: Exception) {
                if (!open && saved) {
                    fileOperations.replaceDatabaseFromBackup(safetyCopy, file, closeDatabase = {})
                    reopen()
                }
                throw error
            }
        } finally {
            source.delete()
        }
        return pending.metadata
    }

    /**
     * Writes a validated snapshot of the database into [folder] and returns it. The name follows
     * the phone's (snapshot version, then when it was taken) under a prefix of its own, so in a
     * shared folder neither app prunes the other's backups.
     */
    fun exportBackup(folder: File, now: Instant = Instant.now()): File {
        folder.mkdirs()
        val meta = MetaRepository(database.metaQueries)
        val previous = snapshotVersion()
        val version = meta.incrementSnapshotVersion()
        val target = File(folder, "$BACKUP_PREFIX${"%012d".format(Locale.ROOT, version)}-${BACKUP_TIMESTAMP.format(now)}$BACKUP_EXTENSION")
        try {
            fileOperations.createVacuumSnapshot(driver, target)
            val result = BackupSnapshotValidator(GestorDatabase.Schema.version).validate(
                displayName = target.name,
                createdAtUtc = now,
                lastModifiedMillis = target.lastModified(),
                currentSnapshotVersion = previous,
                inspection = inspector.inspect(target.absolutePath),
            )
            if (result is BackupValidationResult.Invalid) throw BackupException(result.error)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        return target
    }

    /**
     * Deletes this app's own backups in [folder] beyond the newest [keep]; anything else there is
     * left alone. Newest is by the time in the name, not the snapshot version before it: restoring
     * an older backup takes the version back, and the next backup must not be the first to go.
     */
    fun pruneBackups(folder: File, keep: Int = 10) {
        folder.listFiles { file -> file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(BACKUP_EXTENSION) }
            .orEmpty()
            .sortedWith(compareByDescending<File> { it.name.substringAfterLast('-') }.thenByDescending { it.name })
            .drop(keep)
            .forEach { it.delete() }
    }

    override val schemaVersion: String get() = MetaRepository(database.metaQueries).load().schemaVersion

    override val tempDir: File get() = file.parentFile

    override fun snapshot(into: File) {
        fileOperations.createVacuumSnapshot(driver, into)
    }

    // A connection of its own: SQLite's data_version only counts what other connections write.
    private var watcher: JdbcSqlSession? = null

    @Synchronized
    override fun changeCounter(): Long {
        val session = watcher ?: JdbcSqlSession(file).also { watcher = it }
        return session.query("PRAGMA data_version").single().single()!!.toLong()
    }

    @Synchronized
    private fun closeWatcher() {
        watcher?.close()
        watcher = null
    }

    /** Merges another device's copy into this database, record by record; see [mergeDatabase]. */
    override fun merge(theirs: File, base: File?, choice: ConflictChoice?): MergeResult =
        JdbcSqlSession(file).use { mergeDatabase(it, ::JdbcSqlSession, theirs, base, choice) }

    /** When the database last changed on disk, its write-ahead log included. */
    fun modifiedStamp(): Long = maxOf(file.lastModified(), File(file.path + "-wal").lastModified())

    override fun close() {
        closeWatcher()
        driver.close()
    }

    companion object {
        const val BACKUP_PREFIX = "gestor-finances-desktop-backup-v1-"
        private val BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.ROOT).withZone(ZoneOffset.UTC)

        /** `%APPDATA%\Summa` on Windows; the home directory elsewhere. */
        fun defaultFile(): File {
            val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"))
            return File(File(base, "Summa"), "gestor-finances.db")
        }

        /**
         * Puts the backup [source] where [file], which cannot be opened, is. That file is not
         * deleted: it is moved beside itself under another name, which is returned.
         */
        fun restoreOver(file: File, source: File): File {
            val result = BackupSnapshotValidator(GestorDatabase.Schema.version).validate(
                displayName = source.name,
                createdAtUtc = null,
                lastModifiedMillis = source.lastModified(),
                currentSnapshotVersion = 0L,
                inspection = JdbcBackupDatabaseInspector().inspect(source.absolutePath),
            )
            if (result is BackupValidationResult.Invalid) throw BackupException(result.error)
            val kept = File(file.parentFile, "unreadable-${System.currentTimeMillis()}.db")
            listOf("", "-wal", "-shm").forEach { suffix ->
                val part = File(file.path + suffix)
                if (part.exists()) Files.move(part.toPath(), File(kept.path + suffix).toPath())
            }
            BackupFileOperations().copySynced(source, file)
            return kept
        }

        /** What [file] held before the last migration of its schema. */
        fun migrationCopy(file: File): File = File(file.parentFile, "before-migration$BACKUP_EXTENSION")

        private fun openDriver(file: File): JdbcSqliteDriver {
            file.parentFile.mkdirs()
            val properties = SQLiteConfig().apply {
                enforceForeignKeys(true)
                busyTimeout = 5000
            }.toProperties()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties)
            try {
                prepareSchema(driver, file)
            } catch (error: Exception) {
                driver.close()
                throw error
            }
            return driver
        }

        /**
         * Brings the file to the current schema. The version is read from `meta`, which every
         * migration updates, so a snapshot from the phone needs no separate stamping.
         */
        private fun prepareSchema(driver: JdbcSqliteDriver, file: File) {
            val latest = GestorDatabase.Schema.version
            val hasMeta = driver.queryString("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'meta'") != null
            val current = if (hasMeta) {
                driver.queryString("SELECT value FROM meta WHERE key = 'schema_version'")?.toLongOrNull()
                    ?: error("Database has no readable schema version")
            } else {
                null
            }
            val transacter = GestorDatabase(driver)
            when {
                current == null -> transacter.transaction {
                    GestorDatabase.Schema.create(driver)
                    sharedAccountIntegrityStatements().forEach { driver.execute(null, it, 0) }
                }
                current > latest -> throw NewerSchemaException(current, latest)
                current < latest -> {
                    // What the file held, restorable like any backup, should the migration go wrong
                    // in a way its transaction does not undo. A failed copy does not stop the app.
                    runCatching { BackupFileOperations().createVacuumSnapshot(driver, migrationCopy(file)) }
                    transacter.transaction {
                        GestorDatabase.Schema.migrate(driver, current, latest)
                    }
                }
            }
            driver.execute(null, "PRAGMA user_version = $latest", 0)
        }

        private fun SqlDriver.queryString(sql: String): String? =
            executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
                parameters = 0,
            ).value
    }
}
