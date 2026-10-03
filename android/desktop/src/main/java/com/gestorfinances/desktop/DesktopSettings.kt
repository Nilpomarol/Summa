package com.gestorfinances.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gestorfinances.app.data.sync.newPairingCode
import com.gestorfinances.app.ui.theme.ThemeMode
import java.awt.Rectangle
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.prefs.Preferences

/**
 * What this computer remembers about the app, apart from the finance data: the theme, where and
 * when backups are written, and the pairing with the phone. Kept in the user's preferences (the
 * registry on Windows), so a restored database does not change them.
 */
class DesktopSettings(
    // SUMMA_PROFILE gives a trial run settings of its own, so it cannot touch the real ones
    // (their backup folder, their pairing); pair it with another APPDATA for the data.
    private val prefs: Preferences = Preferences.userRoot().node("com/gestorfinances/summa" + System.getenv("SUMMA_PROFILE").orEmpty()),
) {
    var themeMode by mutableStateOf(
        prefs.get(THEME, null)?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.SYSTEM,
    )
        private set

    var backupFolder by mutableStateOf(prefs.get(BACKUP_FOLDER, null)?.let(::File))
        private set

    /** Writes a backup when the window closes, if anything changed since the last one. */
    var backupOnClose by mutableStateOf(prefs.getBoolean(BACKUP_ON_CLOSE, true))
        private set

    var lastBackupAt by mutableStateOf(prefs.get(LAST_BACKUP_AT, null)?.let { runCatching { Instant.parse(it) }.getOrNull() })
        private set

    /** Whether the last close-time backup could not be written; the next backup that works clears it. */
    var backupOnCloseFailed by mutableStateOf(prefs.getBoolean(BACKUP_ON_CLOSE_FAILED, false))
        private set

    fun recordBackupOnCloseFailed() {
        backupOnCloseFailed = true
        prefs.putBoolean(BACKUP_ON_CLOSE_FAILED, true)
    }

    /** The database file's modification time right after the last backup: later than this, there is something new to copy. */
    var lastBackupStamp: Long = prefs.getLong(LAST_BACKUP_STAMP, 0L)
        private set

    /** The code the phone was paired with; while there is one, the app listens for the phone. */
    var syncCode by mutableStateOf(prefs.get(SYNC_CODE, null))
        private set

    /** Where the window was left and how large, in the screen's own units; null until it has been closed once. */
    val windowBounds: Rectangle?
        get() = Rectangle(prefs.getInt(WINDOW_X, 0), prefs.getInt(WINDOW_Y, 0), prefs.getInt(WINDOW_WIDTH, 0), prefs.getInt(WINDOW_HEIGHT, 0))
            .takeIf { it.width > 0 && it.height > 0 }

    val windowMaximized: Boolean get() = prefs.getBoolean(WINDOW_MAXIMIZED, false)

    /** [bounds] is null for a maximized window: the size it goes back to stays the one from before. */
    fun recordWindow(bounds: Rectangle?, maximized: Boolean) {
        prefs.putBoolean(WINDOW_MAXIMIZED, maximized)
        if (bounds == null) return
        prefs.putInt(WINDOW_X, bounds.x)
        prefs.putInt(WINDOW_Y, bounds.y)
        prefs.putInt(WINDOW_WIDTH, bounds.width)
        prefs.putInt(WINDOW_HEIGHT, bounds.height)
    }

    /** With sync on, closing the window leaves the app in the tray, still listening for the phone. */
    var keepListening by mutableStateOf(prefs.getBoolean(KEEP_LISTENING, true))
        private set

    /** Whether the owner has been told, once, that closing the window leaves the app in the tray. */
    var trayNoticeShown: Boolean
        get() = prefs.getBoolean(TRAY_NOTICE_SHOWN, false)
        set(value) = prefs.putBoolean(TRAY_NOTICE_SHOWN, value)

    fun chooseKeepListening(enabled: Boolean) {
        keepListening = enabled
        prefs.putBoolean(KEEP_LISTENING, enabled)
    }

    /** Which computer this is to a phone paired with several; made once and kept. */
    val syncId: String
        get() = prefs.get(SYNC_ID, null) ?: UUID.randomUUID().toString().take(8).also { prefs.put(SYNC_ID, it) }

    var lastSyncAt by mutableStateOf(prefs.get(LAST_SYNC_AT, null)?.let { runCatching { Instant.parse(it) }.getOrNull() })
        private set

    /** Switches sync on under a fresh code; a phone paired with an earlier one has to be paired again. */
    fun newSyncCode() {
        syncCode = newPairingCode().also { prefs.put(SYNC_CODE, it) }
    }

    fun stopSync() {
        syncCode = null
        prefs.remove(SYNC_CODE)
    }

    fun recordSync(at: Instant) {
        lastSyncAt = at
        prefs.put(LAST_SYNC_AT, at.toString())
    }

    fun chooseTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.put(THEME, mode.name)
    }

    fun chooseBackupFolder(folder: File) {
        backupFolder = folder
        prefs.put(BACKUP_FOLDER, folder.absolutePath)
    }

    fun chooseBackupOnClose(enabled: Boolean) {
        backupOnClose = enabled
        prefs.putBoolean(BACKUP_ON_CLOSE, enabled)
    }

    fun recordBackup(at: Instant, databaseStamp: Long) {
        lastBackupAt = at
        lastBackupStamp = databaseStamp
        prefs.put(LAST_BACKUP_AT, at.toString())
        prefs.putLong(LAST_BACKUP_STAMP, databaseStamp)
        backupOnCloseFailed = false
        prefs.remove(BACKUP_ON_CLOSE_FAILED)
    }

    private companion object {
        const val THEME = "theme"
        const val BACKUP_FOLDER = "backupFolder"
        const val BACKUP_ON_CLOSE = "backupOnClose"
        const val LAST_BACKUP_AT = "lastBackupAt"
        const val LAST_BACKUP_STAMP = "lastBackupStamp"
        const val BACKUP_ON_CLOSE_FAILED = "backupOnCloseFailed"
        const val SYNC_CODE = "syncCode"
        const val SYNC_ID = "syncId"
        const val KEEP_LISTENING = "keepListening"
        const val TRAY_NOTICE_SHOWN = "trayNoticeShown"
        const val LAST_SYNC_AT = "lastSyncAt"
        const val WINDOW_X = "windowX"
        const val WINDOW_Y = "windowY"
        const val WINDOW_WIDTH = "windowWidth"
        const val WINDOW_HEIGHT = "windowHeight"
        const val WINDOW_MAXIMIZED = "windowMaximized"
    }
}

/** Writes a backup into the chosen folder, prunes the old ones, and notes it; returns the file written. */
fun DesktopSettings.backUp(db: DesktopDatabase, folder: File): File =
    // One at a time: the one written as the window goes to the tray and the one on quitting would
    // otherwise meet in the same second, under the same name.
    synchronized(this) {
        val now = Instant.now()
        val file = db.exportBackup(folder, now)
        db.pruneBackups(folder)
        recordBackup(now, db.modifiedStamp())
        file
    }

/**
 * The close-time backup: only with a folder chosen, the option on, and something changed since the
 * last one. Never throws: a failure is kept for Settings to show the next time the app is open.
 */
fun DesktopSettings.backUpOnClose(db: DesktopDatabase) {
    val folder = backupFolder ?: return
    // Whether anything changed is asked once the backup before this one has finished.
    synchronized(this) {
        if (!backupOnClose || db.modifiedStamp() <= lastBackupStamp) return
        runCatching { backUp(db, folder) }.onFailure { recordBackupOnCloseFailed() }
    }
}
