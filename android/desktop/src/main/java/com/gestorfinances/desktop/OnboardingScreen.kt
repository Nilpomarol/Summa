package com.gestorfinances.desktop

import com.gestorfinances.app.ui.common.OpenDialogs
import com.gestorfinances.desktop.resources.Res as DesktopRes
import com.gestorfinances.desktop.resources.onboarding_sync_body
import com.gestorfinances.desktop.resources.onboarding_sync_title
import com.gestorfinances.desktop.resources.onboarding_sync
import com.gestorfinances.ui.resources.common_ok
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.gestorfinances.app.ui.movements.AppTextField
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.HeroPanel
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.onboarding.OnboardingFormState
import com.gestorfinances.app.ui.onboarding.OnboardingUiState
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.account_field_name
import com.gestorfinances.ui.resources.account_field_starting_balance
import com.gestorfinances.ui.resources.account_validation_name_required
import com.gestorfinances.ui.resources.account_validation_starting_balance_invalid
import com.gestorfinances.ui.resources.failure_save_account
import com.gestorfinances.ui.resources.onboarding_account_title
import com.gestorfinances.ui.resources.onboarding_balance_help
import com.gestorfinances.ui.resources.onboarding_body
import com.gestorfinances.ui.resources.onboarding_create
import com.gestorfinances.ui.resources.onboarding_point_backup
import com.gestorfinances.ui.resources.onboarding_point_features
import com.gestorfinances.ui.resources.onboarding_point_private
import com.gestorfinances.ui.resources.onboarding_restore
import com.gestorfinances.ui.resources.onboarding_saving
import com.gestorfinances.ui.resources.onboarding_title
import com.gestorfinances.ui.resources.summa_mark
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The first run, on a wide window: what Summa is on the left, and on the right the one thing it
 * needs to begin, the first account, or a backup to restore for someone coming back.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onFormChange: (OnboardingFormState) -> Unit,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
    settings: DesktopSettings,
) {
    val colors = FinanceTheme.colors
    var showSync by remember { mutableStateOf(false) }
    if (showSync) {
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = { showSync = false },
            title = { Text(stringResource(DesktopRes.string.onboarding_sync_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(DesktopRes.string.onboarding_sync_body))
                    Text(settings.syncCode.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                }
            },
            confirmButton = { AppTextButton(onClick = { showSync = false }) { Text(stringResource(Res.string.common_ok)) } },
        )
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.widthIn(max = 960.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroPanel(Modifier.fillMaxWidth()) {
                    // The mark, oversized and faint, as on the phone's first screen.
                    Box(Modifier.matchParentSize()) {
                        Image(
                            painter = painterResource(Res.drawable.summa_mark),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.heroOnSurface.copy(alpha = 0.06f)),
                            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).requiredHeight(220.dp),
                        )
                    }
                    Column(Modifier.padding(horizontal = 26.dp, vertical = 30.dp)) {
                        Image(
                            painter = painterResource(Res.drawable.summa_mark),
                            contentDescription = null,
                            modifier = Modifier.height(40.dp),
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            stringResource(Res.string.onboarding_title),
                            color = colors.heroOnSurface,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.onboarding_body),
                            color = colors.heroOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                OnboardingPoint(Icons.Outlined.Lock, stringResource(Res.string.onboarding_point_private))
                OnboardingPoint(Icons.Outlined.CloudDone, stringResource(Res.string.onboarding_point_backup))
                OnboardingPoint(Icons.Outlined.Insights, stringResource(Res.string.onboarding_point_features))
            }
            FinanceCard(Modifier.weight(1f)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = stringResource(Res.string.onboarding_account_title))
                    if (state.form.errorMessage != null) {
                        InlineBanner(kind = BannerKind.Error, text = stringResource(Res.string.failure_save_account))
                    }
                    val nameError = state.form.errorRes == Res.string.account_validation_name_required
                    AppTextField(
                        value = state.form.accountName,
                        onValueChange = { onFormChange(state.form.copy(accountName = it)) },
                        label = { Text(stringResource(Res.string.account_field_name)) },
                        singleLine = true,
                        enabled = !state.isSaving,
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(stringResource(Res.string.account_validation_name_required)) }
                        } else {
                            null
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val balanceError = state.form.errorRes == Res.string.account_validation_starting_balance_invalid
                    AppTextField(
                        value = state.form.startingBalance,
                        onValueChange = { onFormChange(state.form.copy(startingBalance = it)) },
                        label = { Text(stringResource(Res.string.account_field_starting_balance)) },
                        prefix = { Text("€") },
                        singleLine = true,
                        enabled = !state.isSaving,
                        isError = balanceError,
                        supportingText = {
                            Text(
                                stringResource(
                                    if (balanceError) {
                                        Res.string.account_validation_starting_balance_invalid
                                    } else {
                                        Res.string.onboarding_balance_help
                                    },
                                ),
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = stringResource(if (state.isSaving) Res.string.onboarding_saving else Res.string.onboarding_create),
                        onClick = onCreate,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextButton(
                        onClick = onRestore,
                        enabled = !state.isSaving,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(Res.string.onboarding_restore))
                    }
                    AppTextButton(
                        onClick = {
                            if (settings.syncCode == null) settings.newSyncCode()
                            showSync = true
                        },
                        enabled = !state.isSaving,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(DesktopRes.string.onboarding_sync))
                    }
                }
            }
        }
    }
}

/** One thing worth knowing before starting: a quiet icon and a line. */
@Composable
private fun OnboardingPoint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
