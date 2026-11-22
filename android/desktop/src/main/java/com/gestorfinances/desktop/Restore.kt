package com.gestorfinances.desktop

import com.gestorfinances.app.ui.common.OpenDialogs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.backup.BackupException
import com.gestorfinances.app.data.backup.BackupValidationError
import com.gestorfinances.app.data.backup.BackupWarning
import com.gestorfinances.app.data.backup.PendingBackupRestore
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.restore_confirm
import com.gestorfinances.desktop.resources.restore_confirm_body
import com.gestorfinances.desktop.resources.restore_confirm_older
import com.gestorfinances.desktop.resources.restore_confirm_title
import com.gestorfinances.desktop.resources.restore_dialog_title
import com.gestorfinances.desktop.resources.restore_done
import com.gestorfinances.desktop.resources.restore_error_failed
import com.gestorfinances.desktop.resources.restore_error_invalid
import com.gestorfinances.desktop.resources.restore_error_newer
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_ok
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * Restoring a `.gfbackup`: choose the file, confirm, replace the database. It shows its own
 * dialogs; the returned function starts the flow, and [onRestored] runs once the database has been
 * replaced, whether or not the restore succeeded, because the old handle is gone either way.
 */
@Composable
fun rememberRestoreFlow(db: DesktopDatabase, onRestored: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingBackupRestore?>(null) }
    var message by remember { mutableStateOf<StringResource?>(null) }
    val chooserTitle = stringResource(Res.string.restore_dialog_title)

    pending?.let { restore ->
        val discard = {
            db.discardRestore(restore)
            pending = null
        }
        val metadata = restore.metadata
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = discard,
            title = { Text(stringResource(Res.string.restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            Res.string.restore_confirm_body,
                            metadata.displayName,
                            formatCompactDate(metadata.createdAtUtc.atZone(ZoneId.systemDefault()).toLocalDate()),
                            db.safetyCopy.absolutePath,
                        ),
                    )
                    if (metadata.warning == BackupWarning.OLDER_OR_SAME_VERSION) {
                        Text(stringResource(Res.string.restore_confirm_older), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        pending = null
                        scope.launch {
                            message = try {
                                withContext(Dispatchers.IO) { db.applyRestore(restore) }
                                Res.string.restore_done
                            } catch (_: Exception) {
                                Res.string.restore_error_failed
                            }
                            onRestored()
                        }
                    },
                ) { Text(stringResource(Res.string.restore_confirm)) }
            },
            dismissButton = { AppTextButton(onClick = discard) { Text(stringResource(SharedRes.string.common_cancel)) } },
        )
    }
    message?.let {
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { AppTextButton(onClick = { message = null }) { Text(stringResource(SharedRes.string.common_ok)) } },
            text = { Text(stringResource(it)) },
        )
    }

    return {
        chooseBackupFile(chooserTitle)?.let { source ->
            scope.launch {
                try {
                    pending = withContext(Dispatchers.IO) { db.prepareRestore(source) }
                } catch (error: BackupException) {
                    message = when (error.reason) {
                        BackupValidationError.UNSUPPORTED_SCHEMA -> Res.string.restore_error_newer
                        else -> Res.string.restore_error_invalid
                    }
                }
            }
        }
    }
}

internal fun chooseBackupFile(title: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        file = "*.gfbackup"
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}
