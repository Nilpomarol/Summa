package com.gestorfinances.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

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
        val label = runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
            appContext.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
            ?: uriString
        return BackupFolder(uriString = uriString, displayLabel = label)
    }

    private companion object {
        const val PREFS_NAME = "finance_backup"
        const val KEY_FOLDER_URI = "backup_folder_uri"
    }
}
