@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.BuildConfig
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.data.backup.BackupFileCandidate
import com.gestorfinances.app.data.backup.PendingBackupRestore
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SHOW_DEBUG_SEED_DATA = false

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onRecreateApp: (SettingsMessage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.PickBackupFolder -> onPickBackupFolder()
                is SettingsEffect.RecreateApp -> onRecreateApp(effect.message)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RootPageHeader(title = stringResource(R.string.settings_title))
        }

        item {
            BackupSettingsCard(
                state = state,
                onChooseFolder = viewModel::onBackupChooseFolderClicked,
                onExport = viewModel::onBackupExportClicked,
                onImport = viewModel::onBackupImportClicked,
                onAutoBackupChanged = viewModel::onAutoBackupChanged,
            )
        }

        if (!notificationPermissionGranted) {
            item {
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InlineBanner(
                            kind = BannerKind.Alert,
                            text = stringResource(R.string.notification_permission_body),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.notification_permission_action),
                            onClick = onRequestNotificationPermission,
                            leadingIcon = Icons.Outlined.Notifications,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            NotificationSettingsCard(
                state = state,
                onRecurringLeadDaysChanged = viewModel::onRecurringLeadDaysChanged,
                onSaveRecurringLeadDays = viewModel::onSaveRecurringLeadDays,
                onBudgetAlertsChanged = viewModel::onBudgetAlertsChanged,
                onLowBalanceAlertsChanged = viewModel::onLowBalanceAlertsChanged,
            )
        }

        if (BuildConfig.DEBUG && SHOW_DEBUG_SEED_DATA) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.settings_debug_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.settings_seed_data_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_seed_data_body),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.settings_seed_data_action),
                            onClick = viewModel::onSeedDataClicked,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

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

    if (state.seedDataConfirmationPending) {
        AlertDialog(
            onDismissRequest = viewModel::onSeedDataDismissed,
            title = { Text(text = stringResource(R.string.settings_seed_data_confirm_title)) },
            text = { Text(text = stringResource(R.string.settings_seed_data_confirm_body)) },
            confirmButton = {
                DestructiveTextButton(
                    onClick = {
                        viewModel.onSeedDataConfirmed {
                            onBack() // Navigate back to refresh
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.settings_seed_data_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onSeedDataDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun BackupSettingsCard(
    state: SettingsUiState,
    onChooseFolder: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAutoBackupChanged: (Boolean) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconChip(
                    icon = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    color = MaterialTheme.colorScheme.primary,
                    size = 36.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                NeutralPill(text = stringResource(R.string.settings_backup_frequency_manual))
            }
            state.backupMessage?.let { message ->
                InlineBanner(
                    kind = message.kind.toBannerKind(),
                    text = message.arg?.let { stringResource(message.messageRes, it) }
                        ?: stringResource(message.messageRes),
                )
            }
            if (state.backupFolder == null) {
                InlineBanner(
                    kind = BannerKind.Alert,
                    text = stringResource(R.string.settings_backup_folder_missing),
                )
                PrimaryButton(
                    text = stringResource(R.string.settings_backup_choose_folder),
                    onClick = onChooseFolder,
                    enabled = !state.isBackupBusy,
                    leadingIcon = Icons.Outlined.FolderOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                BackupLocationRow(folder = state.backupFolder, onChange = onChooseFolder, enabled = !state.isBackupBusy)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AutoBackupRow(
                    enabled = state.autoBackupEnabled,
                    lastSuccessfulBackupAt = state.lastSuccessfulBackupAt,
                    onEnabledChange = onAutoBackupChanged,
                )
            }
            if (state.backupFolder != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.settings_backup_export),
                        onClick = onExport,
                        enabled = !state.isBackupBusy,
                        leadingIcon = Icons.Outlined.CloudUpload,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.settings_backup_import),
                        onClick = onImport,
                        enabled = !state.isBackupBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.isBackupBusy) {
                Text(
                    text = stringResource(R.string.settings_backup_busy),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AutoBackupRow(
    enabled: Boolean,
    lastSuccessfulBackupAt: Instant?,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.settings_auto_backup_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.settings_auto_backup_daily),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = lastSuccessfulBackupAt?.let {
                    stringResource(R.string.settings_backup_last_success, it.formatBackupInstant())
                } ?: stringResource(R.string.settings_backup_last_missing),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FinanceSwitch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun BackupLocationRow(
    folder: com.gestorfinances.app.data.backup.BackupFolder,
    onChange: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.displayLabel,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NeutralPill(text = folder.storageLabel())
        SecondaryButton(
            text = stringResource(R.string.settings_backup_change),
            onClick = onChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun com.gestorfinances.app.data.backup.BackupFolder.storageLabel(): String =
    when {
        uriString.contains("com.google.android.apps.docs.storage") ->
            stringResource(R.string.settings_backup_storage_google_drive)
        else -> stringResource(R.string.settings_backup_storage_local)
    }

@Composable
private fun NotificationSettingsCard(
    state: SettingsUiState,
    onRecurringLeadDaysChanged: (String) -> Unit,
    onSaveRecurringLeadDays: () -> Unit,
    onBudgetAlertsChanged: (Boolean) -> Unit,
    onLowBalanceAlertsChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.settings_notifications_section))
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Outlined.Notifications, null, MaterialTheme.colorScheme.primary, size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.notification_recurring_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.notification_recurring_lead_help),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                state.errorRes?.let { InlineBanner(kind = BannerKind.Error, text = stringResource(it)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.recurringLeadDays,
                        onValueChange = onRecurringLeadDaysChanged,
                        label = { Text(stringResource(R.string.notification_recurring_lead_default)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.common_save),
                        onClick = onSaveRecurringLeadDays,
                        modifier = Modifier.height(56.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    title = stringResource(R.string.alert_budget_title),
                    body = stringResource(R.string.alert_budget_body),
                    checked = state.budgetAlertsEnabled,
                    onCheckedChange = onBudgetAlertsChanged,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    title = stringResource(R.string.alert_low_balance_title),
                    body = stringResource(R.string.alert_low_balance_body),
                    checked = state.lowBalanceAlertsEnabled,
                    onCheckedChange = onLowBalanceAlertsChanged,
                )
            }
        }
    }
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
                Surface(
                    onClick = { onSelect(candidate) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = candidate.describe(),
                                color = FinanceTheme.colors.mutedText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
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
private fun SettingsSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FinanceSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
