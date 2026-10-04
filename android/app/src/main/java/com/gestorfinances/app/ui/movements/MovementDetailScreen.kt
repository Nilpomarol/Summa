package com.gestorfinances.app.ui.movements

import org.jetbrains.compose.resources.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.ExpenseFunding
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.RefundSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.chipVisual
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatExpandedDate
import com.gestorfinances.app.ui.common.movementTitle
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.signedAmountCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.movementAmountColor
import com.gestorfinances.app.ui.theme.categoryColor

private const val MovementDetailSheetMaxHeightFraction = 0.88f

/**
 * Movement detail sheet, opened through [MovementSheets] since a movement can be viewed from any
 * screen. [onBack] closes the sheet;
 * "Edit" and "Add refund" are local swaps within this same page — refund reuses
 * `state.detailMovement` (kept set while the refund form is open, see
 * [MovementsViewModel.onAddRefundClicked]) so cancelling it reveals the detail content again,
 * and archiving successfully calls [onBack] itself (via `onArchiveConfirmed`'s `onSuccess`).
 */
@Composable
fun MovementDetailScreen(
    viewModel: MovementsViewModel,
    onBack: () -> Unit,
    onEdit: (MovementSummary) -> Unit,
    onDeleteCommitted: DeleteUndoHandler = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val refundForm = state.refundForm
    val movement = state.detailMovement
    val requestRefundDismissal = refundForm?.let { form ->
        rememberFormDismissGuard(
            formKey = form.expenseId,
            currentValue = form,
            hasMeaningfulChanges = { initial, current ->
                initial.copy(errorRes = null, errorField = null, errorMessage = null) !=
                    current.copy(errorRes = null, errorField = null, errorMessage = null)
            },
            onDiscard = viewModel::onRefundDismissed,
        )
    }

    if (refundForm != null) {
        // System/gesture back must reveal the movement detail again, not close the whole
        // detail sheet, so this nested swap needs its own handler (mirrors TripFormScreen nested
        // in TripDetailScreen, SettlementScreen nested in PersonDetailScreen).
        BackHandler(onBack = requireNotNull(requestRefundDismissal))
    }

    AppModalBottomSheet(
        onDismissRequest = requestRefundDismissal ?: onBack,
        modifier = modifier,
        maxHeightFraction = MovementDetailSheetMaxHeightFraction,
    ) {
        when {
            refundForm != null -> RefundFormContent(
                form = refundForm,
                expense = movement,
                accounts = state.accounts,
                onFormChange = viewModel::onRefundFormChanged,
                onBack = requireNotNull(requestRefundDismissal),
                onSave = viewModel::onRefundSaveClicked,
                onDelete = viewModel::onRefundDeleteClicked,
            )
            movement != null -> MovementDetailContent(
                movement = movement,
                refunds = state.detailRefunds,
                accounts = state.accounts,
                split = state.detailSplit,
                people = state.people,
                onEdit = { onEdit(movement) },
                onArchive = { viewModel.onArchiveClicked(movement) },
                onAddRefund = { viewModel.onAddRefundClicked(movement) },
                onRefundClick = { viewModel.onRefundClicked(movement, it) },
            )
            else -> Unit
        }
    }

    state.archiveCandidate?.let { candidate ->
        var revertDueDate by remember(candidate) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.movement_archive_confirm_title)) },
            text = {
                Column {
                    Text(text = stringResource(R.string.movement_archive_warning))
                    if (candidate.activeRefundCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.movement_archive_refunds_warning,
                                candidate.activeRefundCount,
                                candidate.activeRefundCount,
                            ),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (candidate.revertibleTemplateId != null) {
                        val label = candidate.movement.name?.takeIf { it.isNotBlank() }
                            ?: candidate.movement.payee.orEmpty()
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = revertDueDate, onCheckedChange = { revertDueDate = it })
                            Text(text = stringResource(R.string.movement_archive_revert_due_checkbox, label))
                        }
                    }
                }
            },
            confirmButton = {
                DestructiveTextButton(
                    onClick = {
                        viewModel.onArchiveConfirmed(revertDueDate = revertDueDate) { undo ->
                            onDeleteCommitted(undo)
                            onBack()
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onArchiveDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
