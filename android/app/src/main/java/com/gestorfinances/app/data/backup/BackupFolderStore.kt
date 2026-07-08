package com.gestorfinances.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri

class BackupFolderStore(
    context: Context,
) : BackupFolderRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadSelectedFolder(): BackupFolder? =
        prefs.getString(KEY_FOLDER_URI, null)?.let(::toBackupFolder)

    override fun saveSelectedFolder(uriString: String): BackupFolder {
        val uri = Uri.parse(uriString)
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_FOLDER_URI, uriString).apply()
        return toBackupFolder(uriString)
    }

    private fun toBackupFolder(uriString: String): BackupFolder {
        val uri = Uri.parse(uriString)
        val label = uri.lastPathSegment
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: uriString
        return BackupFolder(uriString = uriString, displayLabel = label)
    }

    private companion object {
        const val PREFS_NAME = "finance_backup"
        const val KEY_FOLDER_URI = "backup_folder_uri"
    }
}
