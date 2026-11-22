@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.settings

import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.IconButton
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.data.sync.ConflictChoice
import androidx.compose.material3.OutlinedTextField
import com.gestorfinances.app.di.AppContainer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.theme.ThemePreviewColors
import com.gestorfinances.app.ui.theme.themePreviewColors
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.BuildConfig
import com.gestorfinances.app.R
import com.gestorfinances.app.data.backup.AutoBackupInterval
import com.gestorfinances.app.data.backup.AutoBackupSettings
import com.gestorfinances.app.data.backup.BackupFileCandidate
import com.gestorfinances.app.data.backup.PendingBackupRestore
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The app-wide settings ViewModel. A restore closes the database and removes every page, so the
 * shell collects its effects and it must outlive the Settings page.
 */
@Composable
fun settingsViewModel(appContainer: AppContainer): SettingsViewModel = viewModel {
    SettingsViewModel(
        preferences = appContainer.notificationPreferences,
        backupFolderRepository = appContainer.backupFolderStore,
        backupOperations = appContainer.backupSnapshotService,
        autoBackupSettings = appContainer.autoBackupPreferences,
        autoBackupScheduler = appContainer.autoBackupScheduler,
        themePreferences = appContainer.themePreferences,
        notificationRefresher = appContainer.notificationCoordinator,
    )
}

/** The app-wide sync ViewModel: the shell runs its quiet rounds and shows its dialogs above every page. */
@Composable
fun syncViewModel(appContainer: AppContainer): SyncViewModel = viewModel {
    SyncViewModel(
        sync = appContainer.phoneSync,
        onDataChanged = appContainer.financialDataRevision::markChanged,
    )
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    syncViewModel: SyncViewModel,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // Again each time the page comes back to the front: a backup may have run meanwhile.
    LifecycleResumeEffect(viewModel) {
        viewModel.onBackupStatusChanged()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeaderRow(onBack = onBack, title = stringResource(R.string.settings_title)) }

        item { SectionHeader(title = stringResource(R.string.settings_theme_title), modifier = SectionGap) }
        item { ThemePicker(selected = state.themeMode, onSelect = viewModel::onThemeModeChanged) }

        item { SectionHeader(title = stringResource(R.string.settings_backup_title), modifier = SectionGap) }
        item {
            BackupSection(
                state = state,
                onChooseFolder = viewModel::onBackupChooseFolderClicked,
                onExport = viewModel::onBackupExportClicked,
                onImport = { viewModel.onBackupImportClicked() },
                onAutoBackupChanged = viewModel::onAutoBackupChanged,
                onAutoBackupIntervalChanged = viewModel::onAutoBackupIntervalChanged,
            )
        }

        item { SectionHeader(title = stringResource(R.string.settings_sync_section), modifier = SectionGap) }
        item { SyncSection(syncViewModel) }

        item { SectionHeader(title = stringResource(R.string.settings_notifications_section), modifier = SectionGap) }
        item {
            NotificationSection(
                state = state,
                permissionGranted = notificationPermissionGranted,
                onRequestPermission = onRequestNotificationPermission,
                onLeadDaysSelected = viewModel::onRecurringLeadDaysSelected,
                onDueTodayChanged = viewModel::onRecurringDueTodayChanged,
                onOverdueChanged = viewModel::onRecurringOverdueChanged,
                onBudgetAlertsChanged = viewModel::onBudgetAlertsChanged,
                onLowBalanceAlertsChanged = viewModel::onLowBalanceAlertsChanged,
                onBackupAlertsChanged = viewModel::onBackupAlertsChanged,
            )
        }

        item {
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, GestorDatabase.Schema.version),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }

    BackupRestoreDialogs(state = state, viewModel = viewModel)
}

/** Choosing a backup and confirming its restore: Settings' own, and the first-run screen's. */
@Composable
fun BackupRestoreDialogs(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.showBackupList) {
        BackupListSheet(
            candidates = state.backupCandidates,
            onSelect = viewModel::onBackupCandidateSelected,
            onDismiss = viewModel::onBackupListDismissed,
        )
    }

    state.pendingRestore?.let { pending ->
        RestoreConfirmDialog(
            pending = pending,
            isBusy = state.isBackupBusy,
            onConfirm = viewModel::onRestoreConfirmed,
            onDismiss = viewModel::onRestoreDismissed,
        )
    }
}

@Composable
fun BackupMessageBanner(message: SettingsMessage) {
    InlineBanner(
        kind = message.kind.toBannerKind(),
        text = message.arg?.let { stringResource(message.messageRes, it) } ?: stringResource(message.messageRes),
    )
}

/** Room above a section's heading, so sections read apart. */
private val SectionGap = Modifier.padding(top = 12.dp)

// ---------------------------------------------------------------------------
// Theme — three miniatures of the app, one per choice
// ---------------------------------------------------------------------------

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            ThemeOption(
                mode = mode,
                selected = mode == selected,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val label = stringResource(mode.labelRes())
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            AppMiniature(
                mode = mode,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.95f)
                    .clip(shape)
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) primary else FinanceTheme.colors.cardBorder,
                        shape = shape,
                    ),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else FinanceTheme.colors.mutedText,
        )
    }
}

/**
 * A tiny Summa: a page with its title and a few list rows. The
 * system choice shows both, light and dark, split on a diagonal.
 */
@Composable
private fun AppMiniature(mode: ThemeMode, modifier: Modifier) {
    val light = themePreviewColors(dark = false)
    val dark = themePreviewColors(dark = true)
    Canvas(modifier = modifier) {
        when (mode) {
            ThemeMode.LIGHT -> drawMiniature(light)
            ThemeMode.DARK -> drawMiniature(dark)
            ThemeMode.SYSTEM -> {
                drawMiniature(light)
                val darkHalf = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                clipPath(darkHalf) { drawMiniature(dark) }
            }
        }
    }
}

private fun DrawScope.drawMiniature(colors: ThemePreviewColors) {
    val w = size.width
    val h = size.height
    val pad = w * 0.1f
    val inner = w - 2 * pad
    val round = CornerRadius(w * 0.05f)
    fun bar(x: Float, y: Float, width: Float, height: Float, color: Color) =
        drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(height / 2))

    drawRect(colors.page)
    // Title
    bar(pad, h * 0.1f, inner * 0.5f, h * 0.055f, colors.ink)
    // Rows
    val rowHeight = h * 0.14f
    repeat(4) { index ->
        val top = h * 0.24f + index * (rowHeight + h * 0.035f)
        drawRoundRect(colors.card, Offset(pad, top), Size(inner, rowHeight), round)
        drawRoundRect(colors.border, Offset(pad, top), Size(inner, rowHeight), round, style = Stroke(1f))
        val dot = rowHeight * 0.5f
        drawRoundRect(
            if (index == 0) colors.accent else colors.muted,
            Offset(pad + rowHeight * 0.25f, top + rowHeight * 0.25f),
            Size(dot, dot),
            CornerRadius(dot * 0.35f),
        )
        bar(pad + rowHeight, top + rowHeight * 0.3f, inner * 0.38f, rowHeight * 0.16f, colors.ink)
        bar(pad + rowHeight, top + rowHeight * 0.58f, inner * 0.24f, rowHeight * 0.13f, colors.muted)
    }
}

// ---------------------------------------------------------------------------
// Backups — how they stand first, then where, how often, and by hand
// ---------------------------------------------------------------------------

private enum class BackupState { NO_FOLDER, OFF, NONE_YET, FAILING, OK }

@Composable
private fun BackupSection(
    state: SettingsUiState,
    onChooseFolder: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
    onAutoBackupIntervalChanged: (AutoBackupInterval) -> Unit,
) {
    val folder = state.backupFolder
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.backupMessage?.let { BackupMessageBanner(it) }
        BackupStatusCard(state = state, onChooseFolder = onChooseFolder, onExport = onExport, onImport = onImport)
        // Without a folder there is nothing to switch on yet: the card asks for one.
        if (folder != null) SettingsGroup {
            if (folder != null) {
                SettingRow(
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.settings_backup_folder_row),
                    subtitle = listOf(folder.displayLabel, folder.storageLabel()).joinToString(" · "),
                    onClick = onChooseFolder.takeUnless { state.isBackupBusy },
                    trailing = { Chevron() },
                )
                GroupDivider()
            }
            SettingRow(
                icon = Icons.Outlined.Autorenew,
                title = stringResource(R.string.settings_auto_backup_title),
                subtitle = stringResource(R.string.settings_auto_backup_keeps),
                onClick = { onAutoBackupChanged(!state.autoBackupEnabled) },
                trailing = { FinanceSwitch(checked = state.autoBackupEnabled, onCheckedChange = onAutoBackupChanged) },
            )
            if (folder != null && state.autoBackupEnabled) {
                GroupDivider()
                ChoiceRow(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.settings_auto_backup_frequency),
                    options = AutoBackupInterval.entries,
                    selected = state.autoBackupInterval,
                    label = { stringResource(R.string.settings_auto_backup_every, it.displayName()) },
                    onSelect = onAutoBackupIntervalChanged,
                )
            }
        }
    }
}

/**
 * The one thing to know about backups, large: when the last one was made, and whether automatic
 * backups are keeping up; with making one now and restoring one at hand.
 */
@Composable
private fun BackupStatusCard(
    state: SettingsUiState,
    onChooseFolder: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val folder = state.backupFolder
    val settings = AutoBackupSettings(state.autoBackupEnabled, state.autoBackupInterval, state.lastSuccessfulBackupAt)
    val now = Instant.now()
    val last = state.lastSuccessfulBackupAt
    val backupState = when {
        folder == null -> BackupState.NO_FOLDER
        !settings.enabled -> BackupState.OFF
        // Not "up to date" until one has actually been made: a first backup that keeps failing
        // must not read as fine.
        last == null -> BackupState.NONE_YET
        settings.isOverdue(now) -> BackupState.FAILING
        else -> BackupState.OK
    }
    val stateColor = if (backupState == BackupState.OK) colors.income else colors.alert
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (backupState == BackupState.OK) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        when (backupState) {
                            BackupState.OK -> R.string.settings_backup_state_ok
                            BackupState.OFF -> R.string.settings_backup_state_off
                            BackupState.NONE_YET -> R.string.settings_backup_state_pending
                            BackupState.NO_FOLDER, BackupState.FAILING -> R.string.settings_backup_state_attention
                        },
                    ),
                    color = stateColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.settings_backup_last_label),
                color = colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = last?.let { backupWhen(it, now).replaceFirstChar { c -> c.uppercase() } }
                    ?: stringResource(R.string.settings_backup_last_none),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (backupState) {
                    BackupState.NO_FOLDER -> stringResource(R.string.settings_backup_status_choose)
                    BackupState.OFF -> stringResource(R.string.settings_backup_status_off)
                    BackupState.NONE_YET -> stringResource(R.string.settings_backup_status_none_yet)
                    BackupState.FAILING -> stringResource(R.string.settings_backup_status_failing)
                    BackupState.OK -> stringResource(R.string.settings_auto_backup_every, state.autoBackupInterval.displayName())
                },
                color = if (backupState == BackupState.OK) colors.mutedText else colors.alert,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (folder == null) {
                PrimaryButton(
                    text = stringResource(R.string.settings_backup_choose_folder),
                    onClick = onChooseFolder,
                    enabled = !state.isBackupBusy,
                    leadingIcon = Icons.Outlined.FolderOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.settings_backup_export),
                        onClick = onExport,
                        enabled = !state.isBackupBusy,
                        leadingIcon = Icons.Outlined.CloudUpload,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.settings_backup_restore_short),
                        onClick = onImport,
                        enabled = !state.isBackupBusy,
                    )
                }
            }
            if (state.isBackupBusy) {
                Text(
                    text = stringResource(R.string.settings_backup_busy),
                    color = colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Alerts
// ---------------------------------------------------------------------------

@Composable
private fun NotificationSection(
    state: SettingsUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onLeadDaysSelected: (Int) -> Unit,
    onDueTodayChanged: (Boolean) -> Unit,
    onOverdueChanged: (Boolean) -> Unit,
    onBudgetAlertsChanged: (Boolean) -> Unit,
    onLowBalanceAlertsChanged: (Boolean) -> Unit,
    onBackupAlertsChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!permissionGranted) {
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(R.string.notification_permission_body),
                actionLabel = stringResource(R.string.notification_permission_action),
                onAction = onRequestPermission,
            )
        }
        GroupLabel(stringResource(R.string.settings_alerts_recurring))
        SettingsGroup {
            ChoiceRow(
                icon = Icons.Outlined.EventRepeat,
                title = stringResource(R.string.alert_recurring_advance_title),
                options = (LEAD_DAY_OPTIONS + state.recurringLeadDays).distinct().sorted(),
                selected = state.recurringLeadDays,
                label = { days -> leadDaysLabel(days) },
                onSelect = onLeadDaysSelected,
            )
            GroupDivider()
            SwitchRow(
                icon = Icons.Outlined.Today,
                title = stringResource(R.string.alert_recurring_today_title),
                subtitle = stringResource(R.string.alert_recurring_today_body),
                checked = state.recurringDueTodayEnabled,
                onCheckedChange = onDueTodayChanged,
            )
            GroupDivider()
            SwitchRow(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.alert_recurring_overdue_title),
                subtitle = stringResource(R.string.alert_recurring_overdue_body),
                checked = state.recurringOverdueEnabled,
                onCheckedChange = onOverdueChanged,
            )
        }
        GroupLabel(stringResource(R.string.settings_alerts_other))
        SettingsGroup {
            SwitchRow(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = stringResource(R.string.alert_budget_title),
                subtitle = stringResource(R.string.alert_budget_body),
                checked = state.budgetAlertsEnabled,
                onCheckedChange = onBudgetAlertsChanged,
            )
            GroupDivider()
            SwitchRow(
                icon = Icons.Outlined.WarningAmber,
                title = stringResource(R.string.alert_low_balance_title),
                subtitle = stringResource(R.string.alert_low_balance_body),
                checked = state.lowBalanceAlertsEnabled,
                onCheckedChange = onLowBalanceAlertsChanged,
            )
            GroupDivider()
            SwitchRow(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.alert_backup_title),
                subtitle = stringResource(R.string.alert_backup_body),
                checked = state.backupAlertsEnabled,
                onCheckedChange = onBackupAlertsChanged,
            )
        }
    }
}

/** A quiet label over a group of settings. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

private val LEAD_DAY_OPTIONS = (0..7).toList()

@Composable
private fun leadDaysLabel(days: Int): String =
    if (days == 0) stringResource(R.string.notification_lead_none)
    else pluralStringResource(R.plurals.notification_lead_days_before, days, days)

// ---------------------------------------------------------------------------
// Grouped rows
// ---------------------------------------------------------------------------

/** Related settings together in one card, a line between each. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 50.dp), color = FinanceTheme.colors.cardBorder)
}

/** One setting: a quiet icon, what it is (and a line about it), its control on the right. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    subtitleColor: Color = FinanceTheme.colors.mutedText,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = FinanceTheme.colors.mutedText, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(text = it, color = subtitleColor, style = MaterialTheme.typography.bodySmall)
            }
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { FinanceSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** A row whose value on the right opens a short list to pick from. */
@Composable
private fun <T> ChoiceRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingRow(
        icon = icon,
        title = title,
        onClick = { expanded = true },
        trailing = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = label(selected), style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = FinanceTheme.colors.mutedText,
                        modifier = Modifier.size(18.dp),
                    )
                }
                AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        AppDropdownMenuItem(
                            text = { Text(label(option)) },
                            selected = option == selected,
                            onClick = {
                                expanded = false
                                onSelect(option)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Chevron() {
    Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null, tint = FinanceTheme.colors.mutedText)
}

/** "avui a les 10:32", "ahir a les 22:05", "fa 3 dies". */
@Composable
private fun backupWhen(at: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val day = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(at)
    return when (val days = ChronoUnit.DAYS.between(day, today).toInt()) {
        0 -> stringResource(R.string.settings_time_today, time)
        1 -> stringResource(R.string.settings_time_yesterday, time)
        else -> pluralStringResource(R.plurals.settings_time_days_ago, days, days)
    }
}

private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

@Composable
private fun AutoBackupInterval.displayName(): String =
    stringResource(
        when (this) {
            AutoBackupInterval.DAILY -> R.string.settings_auto_backup_daily
            AutoBackupInterval.WEEKLY -> R.string.settings_auto_backup_weekly
            AutoBackupInterval.MONTHLY -> R.string.settings_auto_backup_monthly
            AutoBackupInterval.QUARTERLY -> R.string.settings_auto_backup_quarterly
        },
    )

@Composable
private fun com.gestorfinances.app.data.backup.BackupFolder.storageLabel(): String =
    when {
        uriString.contains("com.google.android.apps.docs.storage") ->
            stringResource(R.string.settings_backup_storage_google_drive)
        else -> stringResource(R.string.settings_backup_storage_local)
    }

@Composable
private fun BackupListSheet(
    candidates: List<BackupFileCandidate>,
    onSelect: (BackupFileCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_backup_list_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(candidates, key = { it.id }) { candidate ->
                EntityListRow(
                    leading = { IdentityIconTile(icon = Icons.Outlined.CloudDownload, color = FinanceTheme.colors.mutedText) },
                    title = candidate.displayName,
                    subtitle = candidate.describe(),
                    isLast = candidate == candidates.last(),
                    onClick = { onSelect(candidate) },
                )
            }
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    pending: PendingBackupRestore,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isBusy) onDismiss()
        },
        title = { Text(text = stringResource(R.string.settings_backup_restore_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        R.string.settings_backup_restore_confirm_body,
                        pending.metadata.displayName,
                        pending.metadata.snapshotVersion,
                    ),
                )
                if (pending.metadata.hasOlderOrSameVersionWarning()) {
                    InlineBanner(
                        kind = BannerKind.Alert,
                        text = stringResource(R.string.settings_backup_restore_version_warning),
                    )
                }
            }
        },
        confirmButton = {
            DestructiveTextButton(onClick = onConfirm, enabled = !isBusy) {
                Text(
                    text = stringResource(
                        if (isBusy) R.string.settings_backup_busy_short else R.string.settings_backup_restore_confirm_action,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun BackupFileCandidate.describe(): String {
    val versionText = parsedSnapshotVersion?.toString() ?: stringResource(R.string.settings_backup_version_unknown)
    val dateText = parsedCreatedAtUtc?.formatBackupInstant()
        ?: lastModifiedMillis?.let { Instant.ofEpochMilli(it).formatBackupInstant() }
        ?: stringResource(R.string.settings_backup_date_unknown)
    return stringResource(R.string.settings_backup_candidate_subtitle, versionText, dateText)
}

@Composable
private fun Instant.formatBackupInstant(): String =
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", java.util.Locale.forLanguageTag("ca"))
        .withZone(ZoneId.systemDefault())
        .format(this)

private fun SettingsMessageKind.toBannerKind(): BannerKind =
    when (this) {
        SettingsMessageKind.INFO -> BannerKind.Info
        SettingsMessageKind.ALERT -> BannerKind.Alert
        SettingsMessageKind.ERROR -> BannerKind.Error
    }

// ---------------------------------------------------------------------------
// Synchronization with the computer
// ---------------------------------------------------------------------------

@Composable
private fun SyncSection(viewModel: SyncViewModel) {
    val state by viewModel.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.message?.let { BackupMessageBanner(it) }
        SettingsGroup {
            // Each computer the phone keeps in step, wherever it lives; the cross takes it off the list.
            state.computers.forEach { computer ->
                val name = computer.name.ifBlank { stringResource(R.string.settings_sync_computer) }
                val last = computer.lastSyncAt?.let {
                    stringResource(R.string.settings_sync_last, backupWhen(it, Instant.now()))
                } ?: stringResource(R.string.settings_sync_never)
                // With one computer the state is its own; with several, only the last sync tells them apart.
                val live = state.computers.size == 1
                SettingRow(
                    icon = Icons.Outlined.Computer,
                    title = name,
                    subtitle = when {
                        live && state.busy -> stringResource(R.string.settings_sync_busy)
                        live && state.connected -> stringResource(R.string.settings_sync_connected) + " · " + last
                        live -> stringResource(R.string.settings_sync_absent) + " · " + last
                        else -> last
                    },
                    subtitleColor = if (live && state.connected && !state.busy) FinanceTheme.colors.income else FinanceTheme.colors.mutedText,
                    onClick = null,
                    trailing = {
                        IconButton(onClick = { viewModel.onUnpairClicked(computer.id) }, enabled = !state.busy) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.settings_sync_unpair, name),
                                tint = FinanceTheme.colors.mutedText,
                            )
                        }
                    },
                )
                GroupDivider()
            }
            if (state.computers.isNotEmpty()) {
                SettingRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(if (state.busy) R.string.settings_sync_busy else R.string.settings_sync_now),
                    onClick = viewModel::onSyncNowClicked.takeUnless { state.busy },
                    trailing = { Chevron() },
                )
                GroupDivider()
            }
            if (state.showPairing) {
                // The computer's code is typed here, in the page: no dialog over it.
                var code by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(R.string.settings_sync_pair_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.settings_sync_pair_body),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        singleLine = true,
                        placeholder = { Text(text = "XXXX-XXXX-XXXX") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = viewModel::onPairingDismissed) { Text(text = stringResource(R.string.common_cancel)) }
                        TextButton(onClick = { viewModel.onCodeEntered(code) }, enabled = code.isNotBlank()) {
                            Text(text = stringResource(R.string.settings_sync_pair_action))
                        }
                    }
                }
            } else {
                SettingRow(
                    icon = if (state.computers.isEmpty()) Icons.Outlined.Computer else Icons.Outlined.Add,
                    title = stringResource(if (state.computers.isEmpty()) R.string.settings_sync_pair else R.string.settings_sync_add),
                    subtitle = stringResource(R.string.settings_sync_pair_subtitle).takeIf { state.computers.isEmpty() },
                    onClick = viewModel::onPairClicked.takeUnless { state.busy },
                    trailing = { Chevron() },
                )
            }
        }
    }
}

/** Settling records changed on both devices. Hosted by the shell. */
@Composable
fun SyncDialogs(viewModel: SyncViewModel) {
    val state by viewModel.state.collectAsState()
    if (state.conflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::onConflictsDismissed,
            title = { Text(text = stringResource(R.string.settings_sync_conflict_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.settings_sync_conflict_body))
                    state.conflicts.take(6).forEach { conflict ->
                        Text(
                            text = listOfNotNull(
                                conflict.name ?: stringResource(R.string.settings_sync_conflict_unnamed),
                                conflict.date?.let(::formatCompactDate),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FinanceTheme.colors.mutedText,
                        )
                    }
                    if (state.conflicts.size > 6) {
                        Text(
                            text = stringResource(R.string.settings_sync_conflict_more, state.conflicts.size - 6),
                            style = MaterialTheme.typography.bodySmall,
                            color = FinanceTheme.colors.mutedText,
                        )
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { viewModel.onConflictChoice(ConflictChoice.NEWEST) }) {
                        Text(text = stringResource(R.string.settings_sync_conflict_newest))
                    }
                    TextButton(onClick = { viewModel.onConflictChoice(ConflictChoice.MINE) }) {
                        Text(text = stringResource(R.string.settings_sync_conflict_mine))
                    }
                    TextButton(onClick = { viewModel.onConflictChoice(ConflictChoice.THEIRS) }) {
                        Text(text = stringResource(R.string.settings_sync_conflict_theirs))
                    }
                    TextButton(onClick = viewModel::onConflictsDismissed) {
                        Text(text = stringResource(R.string.settings_sync_conflict_later))
                    }
                }
            },
        )
    }
}
