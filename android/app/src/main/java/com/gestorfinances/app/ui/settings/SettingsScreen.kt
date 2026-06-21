package com.gestorfinances.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
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
            FinanceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.notification_recurring_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.notification_recurring_lead_help),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.errorRes?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = state.recurringLeadDays,
                            onValueChange = viewModel::onRecurringLeadDaysChanged,
                            label = { Text(text = stringResource(R.string.notification_recurring_lead_default)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.common_save),
                            onClick = viewModel::onSaveRecurringLeadDays,
                        )
                    }
                }
            }
        }

        item {
            SettingsSwitchCard(
                title = stringResource(R.string.alert_budget_title),
                body = stringResource(R.string.alert_budget_body),
                checked = state.budgetAlertsEnabled,
                onCheckedChange = viewModel::onBudgetAlertsChanged,
            )
        }

        item {
            SettingsSwitchCard(
                title = stringResource(R.string.alert_low_balance_title),
                body = stringResource(R.string.alert_low_balance_body),
                checked = state.lowBalanceAlertsEnabled,
                onCheckedChange = viewModel::onLowBalanceAlertsChanged,
            )
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
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
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
