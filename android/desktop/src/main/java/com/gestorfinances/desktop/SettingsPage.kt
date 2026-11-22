package com.gestorfinances.desktop

import com.gestorfinances.ui.resources.tag_list_title
import com.gestorfinances.desktop.resources.sync_state_connected
import kotlinx.coroutines.delay
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import com.gestorfinances.desktop.resources.sync_state_listening
import com.gestorfinances.desktop.resources.sync_state_error
import com.gestorfinances.desktop.resources.sync_copy
import com.gestorfinances.desktop.resources.sync_copied
import com.gestorfinances.desktop.resources.settings_yesterday
import com.gestorfinances.desktop.resources.settings_restore_title
import com.gestorfinances.desktop.resources.settings_backup_no_folder_warning
import com.gestorfinances.desktop.resources.recurring_days_today
import com.gestorfinances.desktop.resources.recurring_days_ago
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.DestructiveButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.ThemeMode
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.settings_backup_done
import com.gestorfinances.desktop.resources.settings_backup_failed
import com.gestorfinances.desktop.resources.settings_backup_no_folder
import com.gestorfinances.desktop.resources.settings_backup_on_close
import com.gestorfinances.desktop.resources.settings_backup_on_close_failed
import com.gestorfinances.desktop.resources.settings_backup_on_close_hint
import com.gestorfinances.desktop.resources.settings_data_location
import com.gestorfinances.desktop.resources.settings_data_title
import com.gestorfinances.desktop.resources.settings_open_folder
import com.gestorfinances.desktop.resources.settings_safety_copy
import com.gestorfinances.desktop.resources.settings_shortcut_new_movement
import com.gestorfinances.desktop.resources.settings_shortcut_pages
import com.gestorfinances.desktop.resources.settings_shortcuts_title
import com.gestorfinances.desktop.resources.sync_code
import com.gestorfinances.desktop.resources.sync_code_hint
import com.gestorfinances.desktop.resources.sync_hint
import com.gestorfinances.desktop.resources.sync_keep_listening
import com.gestorfinances.desktop.resources.sync_keep_listening_hint
import com.gestorfinances.desktop.resources.sync_start_with_windows
import com.gestorfinances.desktop.resources.sync_last
import com.gestorfinances.desktop.resources.sync_never
import com.gestorfinances.desktop.resources.sync_new_code
import com.gestorfinances.desktop.resources.sync_not_listening
import com.gestorfinances.desktop.resources.sync_not_listening_port
import java.net.BindException
import com.gestorfinances.desktop.resources.sync_start
import com.gestorfinances.desktop.resources.sync_stop
import com.gestorfinances.desktop.resources.sync_title
import com.gestorfinances.ui.resources.settings_backup_choose_folder
import com.gestorfinances.ui.resources.settings_backup_export
import com.gestorfinances.ui.resources.settings_backup_folder_row
import com.gestorfinances.ui.resources.settings_backup_last_label
import com.gestorfinances.ui.resources.settings_backup_last_none
import com.gestorfinances.ui.resources.settings_backup_restore_short
import com.gestorfinances.ui.resources.settings_backup_title
import com.gestorfinances.ui.resources.settings_theme_dark
import com.gestorfinances.ui.resources.settings_theme_light
import com.gestorfinances.ui.resources.settings_theme_system
import com.gestorfinances.ui.resources.settings_theme_title
import com.gestorfinances.ui.resources.settings_title
import com.gestorfinances.ui.resources.settings_version
import java.awt.Desktop
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * This computer's choices: how the app looks, where its backups go, syncing with the phone, and
 * where its data lives. Two columns on a wide window, one on a narrow one.
 */
@Composable
fun SettingsPage(db: DesktopDatabase, settings: DesktopSettings, sync: SyncListenerState, onRestore: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 980.dp
        ScrollPage {
            Text(stringResource(SharedRes.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            val appearance = @Composable {
                HomeCard(stringResource(SharedRes.string.settings_theme_title)) {
                    SegmentedControl(
                        options = ThemeMode.entries,
                        selected = settings.themeMode,
                        label = {
                            stringResource(
                                when (it) {
                                    ThemeMode.SYSTEM -> SharedRes.string.settings_theme_system
                                    ThemeMode.LIGHT -> SharedRes.string.settings_theme_light
                                    ThemeMode.DARK -> SharedRes.string.settings_theme_dark
                                },
                            )
                        },
                        onSelect = settings::chooseTheme,
                        compact = true,
                    )
                }
            }
            val backups = @Composable { BackupCard(db, settings, onRestore) }
            val syncing = @Composable {
                HomeCard(
                    stringResource(Res.string.sync_title),
                    trailing = settings.syncCode?.let {
                        {
                            when {
                                sync.error != null -> StateLine(stringResource(Res.string.sync_state_error), FinanceTheme.colors.debt)
                                sync.connected -> StateLine(stringResource(Res.string.sync_state_connected), FinanceTheme.colors.income)
                                else -> StateLine(stringResource(Res.string.sync_state_listening), FinanceTheme.colors.alert)
                            }
                        }
                    },
                ) { SyncCard(settings, sync) }
            }
            val data = @Composable {
                HomeCard(stringResource(Res.string.settings_data_title)) {
                    PathRow(stringResource(Res.string.settings_data_location), db.file, db.file.parentFile)
                    Text(
                        stringResource(SharedRes.string.settings_version, APP_VERSION, GestorDatabase.Schema.version.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
            }
            val shortcuts = @Composable {
                HomeCard(stringResource(Res.string.settings_shortcuts_title)) {
                    Shortcut(stringResource(Res.string.settings_shortcut_new_movement), "Ctrl", "N")
                    Shortcut(stringResource(Res.string.settings_shortcut_pages), "Ctrl", "1 … 0")
                    Shortcut(stringResource(SharedRes.string.tag_list_title), "Ctrl", "E")
                    Shortcut(stringResource(SharedRes.string.settings_title), "Ctrl", ",")
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        appearance()
                        backups()
                        data()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        syncing()
                        shortcuts()
                    }
                }
            } else {
                Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    appearance()
                    backups()
                    syncing()
                    data()
                    shortcuts()
                }
            }
        }
    }
}

/** Backups older than this many days read as overdue. */
private const val STALE_BACKUP_DAYS = 7L

/**
 * Where backups go and how old the last one is (in the colour of how old), the choice to make one
 * on closing, the button to make one now, and, set apart from them, restoring one.
 */
@Composable
private fun BackupCard(db: DesktopDatabase, settings: DesktopSettings, onRestore: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    // The last manual backup's outcome: the file written, or why it could not be.
    var outcome by remember { mutableStateOf<Result<File>?>(null) }
    var working by remember { mutableStateOf(false) }
    val folder = settings.backupFolder
    HomeCard(stringResource(SharedRes.string.settings_backup_title)) {
        if (folder == null) {
            InlineBanner(kind = BannerKind.Alert, text = stringResource(Res.string.settings_backup_no_folder_warning))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(SharedRes.string.settings_backup_folder_row), style = MaterialTheme.typography.labelLarge, color = muted)
                if (folder == null) Text(stringResource(Res.string.settings_backup_no_folder), color = muted) else PathText(folder)
            }
            if (folder != null) {
                SecondaryButton(text = stringResource(Res.string.settings_open_folder), onClick = { runCatching { Desktop.getDesktop().open(folder) } })
            }
            SecondaryButton(
                text = stringResource(SharedRes.string.settings_backup_choose_folder),
                onClick = { chooseFolder(folder)?.let(settings::chooseBackupFolder) },
            )
        }
        Column {
            Text(stringResource(SharedRes.string.settings_backup_last_label), style = MaterialTheme.typography.labelLarge, color = muted)
            val last = settings.lastBackupAt
            if (last == null) {
                Text(stringResource(SharedRes.string.settings_backup_last_none), color = colors.alert, fontWeight = FontWeight.Medium)
            } else {
                val days = ChronoUnit.DAYS.between(last.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DATE_TIME.format(last.atZone(ZoneId.systemDefault())))
                    StateLine(
                        when (days) {
                            0L -> stringResource(Res.string.recurring_days_today)
                            1L -> stringResource(Res.string.settings_yesterday)
                            else -> stringResource(Res.string.recurring_days_ago, days)
                        },
                        if (days >= STALE_BACKUP_DAYS) colors.alert else colors.income,
                    )
                }
            }
        }
        ToggleRow(
            title = stringResource(Res.string.settings_backup_on_close),
            hint = stringResource(Res.string.settings_backup_on_close_hint),
            // Without a folder nothing is backed up, whatever was chosen: the switch says so.
            checked = settings.backupOnClose && folder != null,
            onChange = settings::chooseBackupOnClose,
            enabled = folder != null,
        )
        if (settings.backupOnCloseFailed) {
            InlineBanner(kind = BannerKind.Error, text = stringResource(Res.string.settings_backup_on_close_failed))
        }
        outcome?.let { result ->
            result.fold(
                onSuccess = { InlineBanner(kind = BannerKind.Info, text = stringResource(Res.string.settings_backup_done, it.name)) },
                onFailure = {
                    InlineBanner(kind = BannerKind.Error, text = stringResource(Res.string.settings_backup_failed))
                },
            )
        }
        PrimaryButton(
            text = stringResource(SharedRes.string.settings_backup_export),
            enabled = !working,
            onClick = {
                // Without a folder yet, the button asks for one first.
                val target = folder ?: chooseFolder(null)?.also(settings::chooseBackupFolder) ?: return@PrimaryButton
                working = true
                scope.launch {
                    outcome = withContext(Dispatchers.IO) { runCatching { settings.backUp(db, target) } }
                    working = false
                }
            },
        )
        // Restoring replaces everything: it sits apart, under its own line, with what it does first.
        HorizontalDivider(color = colors.cardBorder)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_restore_title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.settings_safety_copy), style = MaterialTheme.typography.bodySmall, color = muted)
                PathText(db.safetyCopy, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            DestructiveButton(text = stringResource(SharedRes.string.settings_backup_restore_short), onClick = onRestore)
        }
    }
}

/** A choice as a line with its switch at the end; what it means waits under the pointer. */
@Composable
private fun ToggleRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1f)) {
            val name = @Composable { Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else FinanceTheme.colors.mutedText) }
            if (hint != null) Hinted(hint, name) else name()
        }
        FinanceSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A state in a word, behind a dot of its colour. */
@Composable
private fun StateLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

/** A path cut in its middle when it is longer than [chars]: its start and its end are what tell it. */
private fun shortPath(file: File, chars: Int): String {
    val path = file.absolutePath
    return if (path.length <= chars) path else path.take(chars / 2 - 1) + "…" + path.takeLast(chars / 2)
}

/** A path on one line, cut in its middle to what the line has room for, whole under the pointer. */
@Composable
private fun PathText(file: File, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    Hinted(file.absolutePath) {
        BoxWithConstraints {
            // ponytail: the room is counted in average characters, not measured; measure the text
            // if a path is ever still cut at its end.
            val chars = (maxWidth.value / (style.fontSize.value * 0.6f)).toInt().coerceAtLeast(12)
            Text(shortPath(file, chars), maxLines = 1, overflow = TextOverflow.Ellipsis, style = style, color = color)
        }
    }
}

@Composable
private fun PathRow(label: String, file: File, folder: File?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = FinanceTheme.colors.mutedText)
            PathText(file)
        }
        if (folder != null) {
            SecondaryButton(text = stringResource(Res.string.settings_open_folder), onClick = { runCatching { Desktop.getDesktop().open(folder) } })
        }
    }
}

/** A shortcut as the keys to press, drawn as keys, before what they do. */
@Composable
private fun Shortcut(action: String, vararg keys: String) {
    val colors = FinanceTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.width(120.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEachIndexed { index, key ->
                if (index > 0) Text("+", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                Text(
                    key,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(action)
    }
}

private fun chooseFolder(current: File?): File? {
    val chooser = JFileChooser(current).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.forLanguageTag("ca-ES"))

/** The packaged app's version; a development run has none. */
private val APP_VERSION: String = System.getProperty("jpackage.app-version") ?: "dev"

/** Pairing with the phone: the code to type there once (and to copy), when it last synced, and how it keeps listening. */
@Composable
internal fun SyncCard(settings: DesktopSettings, sync: SyncListenerState) {
    val muted = FinanceTheme.colors.mutedText
    val code = settings.syncCode
    if (code == null) {
        Text(stringResource(Res.string.sync_hint), style = MaterialTheme.typography.bodySmall, color = muted)
        PrimaryButton(text = stringResource(Res.string.sync_start), onClick = settings::newSyncCode)
        return
    }
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(Unit) {
            delay(2500)
            copied = false
        }
    }
    Column {
        Hinted(stringResource(Res.string.sync_code_hint)) {
            Text(stringResource(Res.string.sync_code), style = MaterialTheme.typography.labelLarge, color = muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(code, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = 3.sp))
            SecondaryButton(
                text = stringResource(if (copied) Res.string.sync_copied else Res.string.sync_copy),
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
            )
        }
    }
    Column {
        Text(stringResource(Res.string.sync_last), style = MaterialTheme.typography.labelLarge, color = muted)
        Text(
            settings.lastSyncAt?.let { DATE_TIME.format(it.atZone(ZoneId.systemDefault())) }
                ?: stringResource(Res.string.sync_never),
        )
    }
    sync.error?.let {
        val reason = if (it is BindException) Res.string.sync_not_listening_port else Res.string.sync_not_listening
        InlineBanner(kind = BannerKind.Error, text = stringResource(reason))
    }
    ToggleRow(
        title = stringResource(Res.string.sync_keep_listening),
        hint = stringResource(Res.string.sync_keep_listening_hint),
        checked = settings.keepListening,
        onChange = settings::chooseKeepListening,
    )
    if (StartWithWindows.available) {
        var startsWithWindows by remember { mutableStateOf(StartWithWindows.isOn()) }
        ToggleRow(
            title = stringResource(Res.string.sync_start_with_windows),
            hint = null,
            checked = startsWithWindows,
            onChange = { wanted -> if (StartWithWindows.set(wanted)) startsWithWindows = wanted },
            enabled = settings.keepListening,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(text = stringResource(Res.string.sync_new_code), onClick = settings::newSyncCode)
        SecondaryButton(text = stringResource(Res.string.sync_stop), onClick = settings::stopSync)
    }
}
