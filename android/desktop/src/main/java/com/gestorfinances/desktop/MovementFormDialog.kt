package com.gestorfinances.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import com.gestorfinances.app.ui.common.AppIconButton
import com.gestorfinances.app.ui.common.OpenDialogs
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.movements.CreateCategoryContent
import com.gestorfinances.app.ui.movements.MovementDetailContent
import com.gestorfinances.app.ui.movements.RefundFormContent
import com.gestorfinances.app.ui.movements.MovementFormBody
import com.gestorfinances.app.ui.movements.MovementFormState
import com.gestorfinances.app.ui.movements.MovementSaveActions
import com.gestorfinances.app.ui.movements.MovementsUiState
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_close
import org.jetbrains.compose.resources.stringResource

/** A movement's dialogs, over whichever page opened them: its detail, the form, the delete confirmation. */
@Composable
fun MovementDialogs(viewModel: MovementsViewModel, onEditContribution: (movementId: String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val form by viewModel.editor.form.collectAsState()
    MovementDetailDialog(state, viewModel, onEditContribution)
    form?.let { MovementFormDialog(form = it, references = state, viewModel = viewModel) }
    state.archiveCandidate?.let { candidate ->
        ArchiveConfirmation(
            candidate,
            onDismiss = viewModel::onArchiveDismissed,
            onConfirm = { revertDueDate -> viewModel.onArchiveConfirmed(revertDueDate) { viewModel.onDetailDismissed() } },
        )
    }
}

/** The phone's movement detail sheet as a dialog; recording a refund swaps its content. */
@Composable
private fun MovementDetailDialog(state: MovementsUiState, viewModel: MovementsViewModel, onEditContribution: (String) -> Unit) {
    val movement = state.detailMovement ?: return
    val refundForm = state.refundForm
    FormDialog(onDismiss = if (refundForm != null) viewModel::onRefundDismissed else viewModel::onDetailDismissed, width = 480) {
        if (refundForm != null) {
            Spacer(Modifier.height(16.dp))
            RefundFormContent(
                form = refundForm,
                expense = movement,
                accounts = state.accounts,
                onFormChange = viewModel::onRefundFormChanged,
                onBack = viewModel::onRefundDismissed,
                onSave = viewModel::onRefundSaveClicked,
                onDelete = viewModel::onRefundDeleteClicked,
            )
        } else {
            CloseRow(viewModel::onDetailDismissed)
            MovementDetailContent(
                movement = movement,
                refunds = state.detailRefunds,
                accounts = state.accounts,
                split = state.detailSplit,
                people = state.people,
                onEdit = {
                    if (movement.type == MovementType.CONTRIBUTION) {
                        // A contribution is corrected in its shared account's own form.
                        viewModel.onDetailDismissed()
                        onEditContribution(movement.id)
                    } else {
                        // ponytail: the form replaces the detail; abandoning it does not come back here.
                        viewModel.onEditClicked(movement) { viewModel.onDetailDismissed() }
                    }
                },
                onArchive = { viewModel.onArchiveClicked(movement) },
                onAddRefund = { viewModel.onAddRefundClicked(movement) },
                onRefundClick = { viewModel.onRefundClicked(movement, it) },
            )
        }
    }
}

@Composable
private fun CloseRow(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp)) {
        AppIconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.common_close),
                tint = FinanceTheme.colors.mutedText,
            )
        }
    }
}

/** The shared movement form, the same one the phone shows in a sheet, as a dialog. */
@Composable
fun MovementFormDialog(form: MovementFormState, references: MovementsUiState, viewModel: MovementsViewModel) {
    val editor = viewModel.editor
    FormDialog(onDismiss = editor::onFormDismissed, width = 560) {
        CloseRow(editor::onFormDismissed)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                // Ctrl+Enter saves from any field; Enter alone is left to the field it is typed in.
                .onPreviewKeyEvent { event ->
                    val save = event.type == KeyEventType.KeyDown && event.isCtrlPressed && !event.isAltPressed &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter)
                    if (save) editor.onSaveClicked()
                    save
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MovementFormBody(
                form = form,
                accounts = references.accounts,
                categories = references.categories,
                people = references.people,
                trips = references.trips,
                tags = references.tags,
                onFormChange = editor::onFormChanged,
                onTripSelected = editor::onTripSelected,
                onTagSelected = editor::onTagSelected,
                onSharedToggled = editor::onSharedToggled,
                onPayerSplitToggled = editor::onPayerSplitToggled,
                onSplitEditorChange = editor::onSplitEditorChanged,
                onOtherPersonSelected = editor::onOtherPersonSelected,
                onRecurringToggled = editor::onRecurringToggled,
                onRecurringFrequencyChanged = editor::onRecurringFrequencyChanged,
                onOptionalToggled = editor::onOptionalToggled,
                onCreatePersonInSplit = editor::onCreatePersonInSplit,
                onCreateCategory = viewModel::onCreateCategory,
                onSubmit = editor::onSaveClicked,
                createCategory = { onConfirm, onDismiss ->
                    FormDialog(onDismiss = onDismiss, width = 440) {
                        Column(Modifier.padding(top = 24.dp)) {
                            CreateCategoryContent(
                                onConfirm = { name, iconKey, colorHex ->
                                    onConfirm(name, iconKey, colorHex)
                                    onDismiss()
                                },
                                onCancel = onDismiss,
                            )
                        }
                    }
                },
            )
        }
        MovementSaveActions(
            form = form,
            onFormChange = editor::onFormChanged,
            onSave = editor::onSaveClicked,
            onOverride = editor::onDuplicateOverrideClicked,
            onSplitRemovalAccepted = editor::onSplitRemovalAcceptedClicked,
            onRecurrenceStopEnd = editor::onRecurrenceStopEndClicked,
            onRecurrenceStopUnlink = editor::onRecurrenceStopUnlinkClicked,
            onWarningDismissed = editor::onWarningDismissed,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

/** A form on its own card over the page: as tall as its content, up to most of the window. */
@Composable
fun FormDialog(onDismiss: () -> Unit, width: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    OpenDialogs.Track()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 24.dp,
            modifier = Modifier.width(width.dp).heightIn(max = 680.dp),
        ) {
            Column(content = content)
        }
    }
}
