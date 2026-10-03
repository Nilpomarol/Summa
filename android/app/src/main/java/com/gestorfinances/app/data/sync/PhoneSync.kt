package com.gestorfinances.app.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import com.gestorfinances.app.data.backup.BackupFileOperations
import com.gestorfinances.app.data.db.DatabaseDriverFactory
import java.io.File
import java.net.InetSocketAddress
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A computer this phone synchronizes with. */
data class PairedComputer(val id: String, val name: String, val lastSyncAt: Instant?)

/** What the app asks of synchronization with the owner's computers; see [PhoneSync]. */
interface PhoneSyncOperations {
    val computers: List<PairedComputer>

    /**
     * Pairs with the computer on this network that [code] opens and runs the first round with it.
     * Blocking; throws [SyncLinkException]: no computer here, or none that takes the code.
     */
    fun pair(code: String): SyncRound

    fun unpair(id: String)

    /** One round with the paired computer on this network. Blocking; throws [SyncLinkException]. */
    fun round(choice: ConflictChoice?): SyncRound

    /** Something was written here since the last round. */
    fun localChangePending(): Boolean

    /**
     * Waits until the computer of the last round says its data changed (true) or has nothing to
     * say for now (false). Throws [SyncLinkException] when it cannot be reached.
     */
    suspend fun awaitRemoteChange(): Boolean
}

/**
 * The phone's side of synchronization. It finds a paired computer on the local network, merges
 * that computer's data into this database record by record, and hands the result back. The phone
 * can be paired with several computers on different networks: it keeps, for each, what both held
 * after their last round, and carries every change from one to the others as it meets them.
 */
class PhoneSync(
    context: Context,
    private val driver: SqlDriver,
    private val schemaVersionProvider: () -> String,
    private val fileOperations: BackupFileOperations = BackupFileOperations(),
) : PhoneSyncOperations, SyncDevice {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("finance_sync", Context.MODE_PRIVATE)

    /** The computer of the last round and where it was, for [awaitRemoteChange]. */
    @Volatile
    private var current: SyncComputer? = null

    private val ids: Set<String> get() = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()

    override val computers: List<PairedComputer>
        get() = ids.sorted().map { id ->
            PairedComputer(
                id = id,
                name = prefs.getString(KEY_NAME + id, null).orEmpty(),
                lastSyncAt = prefs.getLong(KEY_LAST + id, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli),
            )
        }

    @Synchronized
    override fun pair(code: String): SyncRound {
        val found = discoverSyncComputers()
        if (found.isEmpty()) throw SyncLinkException(SyncLinkException.Reason.NOT_FOUND)
        val key = key(code)
        for (computer in found) {
            // A computer paired again starts from nothing in common: its old base says nothing true.
            if (computer.id !in ids) baseFile(computer.id).delete()
            val result = try {
                syncRound(computer.address, key, this, baseFile(computer.id), choice = null)
            } catch (error: SyncLinkException) {
                if (error.reason == SyncLinkException.Reason.WRONG_CODE) continue
                throw error
            }
            prefs.edit()
                .putStringSet(KEY_IDS, ids + computer.id)
                .putString(KEY_CODE + computer.id, code)
                .putString(KEY_NAME + computer.id, computer.name)
                .apply()
            remember(computer, result)
            return result
        }
        throw SyncLinkException(SyncLinkException.Reason.WRONG_CODE)
    }

    override fun unpair(id: String) {
        prefs.edit()
            .putStringSet(KEY_IDS, ids - id)
            .remove(KEY_CODE + id).remove(KEY_NAME + id).remove(KEY_HOST + id).remove(KEY_PORT + id).remove(KEY_LAST + id)
            .apply()
        baseFile(id).delete()
        if (current?.id == id) current = null
    }

    @Synchronized
    override fun round(choice: ConflictChoice?): SyncRound {
        val paired = ids
        // A computer's address changes with the network; where one last was is only the fallback,
        // for networks that do not carry the question to everybody.
        val computer = discoverSyncComputers().firstOrNull { it.id in paired }
            ?: computers.maxByOrNull { it.lastSyncAt ?: Instant.EPOCH }?.let { last ->
                prefs.getString(KEY_HOST + last.id, null)?.let { host ->
                    SyncComputer(InetSocketAddress(host, prefs.getInt(KEY_PORT + last.id, SYNC_PORT)), last.id, last.name)
                }
            }
            ?: throw SyncLinkException(SyncLinkException.Reason.NOT_FOUND)
        val code = prefs.getString(KEY_CODE + computer.id, null) ?: throw SyncLinkException(SyncLinkException.Reason.NOT_FOUND)
        val result = syncRound(computer.address, key(code), this, baseFile(computer.id), choice)
        remember(computer, result)
        return result
    }

    private fun remember(computer: SyncComputer, result: SyncRound) {
        current = computer
        syncedCounter = changeCounter()
        prefs.edit()
            .putString(KEY_HOST + computer.id, computer.address.address.hostAddress)
            .putInt(KEY_PORT + computer.id, computer.address.port)
            .also { if (result is SyncRound.Done) it.putLong(KEY_LAST + computer.id, System.currentTimeMillis()) }
            .apply()
    }

    override fun localChangePending(): Boolean = changeCounter() != syncedCounter

    override suspend fun awaitRemoteChange(): Boolean = withContext(Dispatchers.IO) {
        val computer = current ?: throw SyncLinkException(SyncLinkException.Reason.NOT_FOUND)
        val code = prefs.getString(KEY_CODE + computer.id, null) ?: throw SyncLinkException(SyncLinkException.Reason.NOT_FOUND)
        awaitSyncChange(computer.address, key(code))
    }

    private fun baseFile(id: String) = File(appContext.filesDir, "sync-base-$id.db")

    // Deriving a key is deliberately slow; once per code is enough.
    private val keys = HashMap<String, SyncKey>()

    @Synchronized
    private fun key(code: String): SyncKey = keys.getOrPut(code) { SyncKey(code) }

    // A connection of its own: SQLite's data_version only counts what other connections write.
    private var watcher: AndroidSqlSession? = null
    private var syncedCounter = -1L

    @Synchronized
    override fun changeCounter(): Long {
        val session = watcher ?: AndroidSqlSession(DatabaseDriverFactory.databaseFile(appContext)).also { watcher = it }
        return session.query("PRAGMA data_version").single().single()!!.toLong()
    }

    override val schemaVersion: String get() = schemaVersionProvider()

    override val tempDir: File get() = appContext.cacheDir

    override fun snapshot(into: File) {
        fileOperations.createVacuumSnapshot(driver, into)
    }

    override fun merge(theirs: File, base: File?, choice: ConflictChoice?): MergeResult =
        AndroidSqlSession(DatabaseDriverFactory.databaseFile(appContext)).use { mergeDatabase(it, ::AndroidSqlSession, theirs, base, choice) }

    private companion object {
        const val KEY_IDS = "computers"
        const val KEY_CODE = "code_"
        const val KEY_NAME = "name_"
        const val KEY_HOST = "host_"
        const val KEY_PORT = "port_"
        const val KEY_LAST = "last_sync_at_"
    }
}

/** A [SqlSession] on a connection of its own to a database file. */
private class AndroidSqlSession(file: File) : SqlSession {
    private val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).apply {
        setForeignKeyConstraintsEnabled(true)
    }

    override fun execute(sql: String, vararg args: String?) {
        database.execSQL(sql, args)
    }

    override fun query(sql: String, vararg args: String?): List<List<String?>> =
        database.rawQuery(sql, args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(List(cursor.columnCount) { cursor.getString(it) }) }
        }

    override fun <T> transaction(block: () -> T): T {
        database.beginTransaction()
        try {
            return block().also { database.setTransactionSuccessful() }
        } finally {
            database.endTransaction()
        }
    }

    override fun close() = database.close()
}
