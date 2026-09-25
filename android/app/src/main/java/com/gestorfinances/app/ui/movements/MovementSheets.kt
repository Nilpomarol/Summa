package com.gestorfinances.app.ui.movements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.rememberFormDismissGuard

/** The movement sheet open above the current page. Any page can open one. */
sealed interface MovementSheet {
    data object Detail : MovementSheet

    /** [fromDetail]: editing from the detail sheet, which closing without saving returns to. */
    data class Form(val fromDetail: Boolean = false) : MovementSheet {
        /** The sheet once this form closes: a saved edit closes the detail too; abandoning one returns to it. */
        fun afterClose(saved: Boolean): MovementSheet? = if (fromDetail && !saved) Detail else null
    }
}

/**
 * Movement detail and create/edit sheets. They own Back so they can animate closed before [sheet]
 * changes. Editing swaps the detail sheet for the form rather than stacking the two.
 */
@Composable
fun MovementSheets(
    sheet: MovementSheet?,
    onSheetChange: (MovementSheet?) -> Unit,
    viewModel: MovementsViewModel,
    onEditContribution: (movementId: String) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
) {
    when (sheet) {
        MovementSheet.Detail -> MovementDetailScreen(
            viewModel = viewModel,
            onBack = {
                viewModel.onDetailDismissed()
                onSheetChange(null)
            },
            onEdit = { movement ->
                if (movement.type == MovementType.CONTRIBUTION) {
                    // A contribution is corrected in its shared account's own form.
                    viewModel.onDetailDismissed()
                    onSheetChange(null)
                    onEditContribution(movement.id)
                } else {
                    viewModel.onEditClicked(movement) {
                        onSheetChange(MovementSheet.Form(fromDetail = true))
                    }
                }
            },
            onDeleteCommitted = onDeleteCommitted,
        )
        is MovementSheet.Form -> MovementFormSheet(sheet, onSheetChange, viewModel)
        null -> Unit
    }
}

@Composable
private fun MovementFormSheet(
    sheet: MovementSheet.Form,
    onSheetChange: (MovementSheet?) -> Unit,
    viewModel: MovementsViewModel,
) {
    val state by viewModel.state.collectAsState()
    // Keep the last form snapshot alive after a successful write. The ViewModel clears its form
    // immediately; retaining the rendered content lets the sheet finish its hide animation first.
    var retainedForm by remember(sheet) { mutableStateOf<MovementFormState?>(null) }
    LaunchedEffect(state.form) {
        state.form?.let { retainedForm = it }
    }
    val form = state.form ?: retainedForm ?: return
    val closeMovementForm: () -> Unit = {
        val saved = state.form == null
        viewModel.onFormDismissed()
        onSheetChange(sheet.afterClose(saved))
    }
    val requestMovementFormDismissal = rememberFormDismissGuard(
        formKey = form.movementId ?: form.externalSplitId ?: "new-movement",
        currentValue = form,
        hasMeaningfulChanges = { initial, current ->
            initial.withoutTransientUi() != current.withoutTransientUi()
        },
        onDiscard = closeMovementForm,
    )
    MovementFormScreen(
        form = form,
        accounts = state.accounts,
        categories = state.categories,
        people = state.people,
        trips = state.trips,
        tags = state.tags,
        onFormChange = viewModel::onFormChanged,
        onTripSelected = viewModel::onTripSelected,
        onTagSelected = viewModel::onTagSelected,
        onSharedToggled = viewModel::onSharedToggled,
        onSplitEditorChange = viewModel::onSplitEditorChanged,
        onSettlementToggled = viewModel::onSettlementToggled,
        onSettlementPersonSelected = viewModel::onSettlementPersonSelected,
        onOtherPersonSelected = viewModel::onOtherPersonSelected,
        onRecurringToggled = viewModel::onRecurringToggled,
        onRecurringFrequencyChanged = viewModel::onRecurringFrequencyChanged,
        onOptionalToggled = viewModel::onOptionalToggled,
        onAdvancedToggled = viewModel::onAdvancedToggled,
        onCreatePersonInSplit = viewModel::onCreatePersonInSplit,
        onDismiss = {
            if (state.form == null) closeMovementForm() else requestMovementFormDismissal()
        },
        onSave = viewModel::onSaveClicked,
        onOverride = viewModel::onDuplicateOverrideClicked,
        onDataLossOverride = viewModel::onDataLossOverrideClicked,
        onRecurrenceStopEnd = viewModel::onRecurrenceStopEndClicked,
        onRecurrenceStopUnlink = viewModel::onRecurrenceStopUnlinkClicked,
        onWarningDismissed = viewModel::onWarningDismissed,
        dismissRequested = state.form == null,
    )
}

/** Warnings, errors, and expanded sections are not edits worth confirming a discard for. */
private fun MovementFormState.withoutTransientUi(): MovementFormState = copy(
    duplicateWarning = false,
    pendingDataLossWarning = null,
    errorRes = null,
    errorField = null,
    errorMessage = null,
    showOptional = false,
    showAdvanced = false,
)
