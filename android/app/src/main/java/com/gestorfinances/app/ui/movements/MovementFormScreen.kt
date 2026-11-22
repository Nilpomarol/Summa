package com.gestorfinances.app.ui.movements

import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.parseEuroCents
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.FormReveal
import com.gestorfinances.app.ui.common.AppSheetHandleTouchHeight
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor

/**
 * The create/edit movement sheet. The category tile, title and amount lead, the concept field
 * follows; then two lines of fields that every type shares until "Més detalls" opens, so switching type never resizes it.
 */
@Composable
fun MovementFormScreen(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    people: List<PersonSummary>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onTripSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onPayerSplitToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
    onOptionalToggled: () -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
    onCreateCategory: (name: String, iconKey: String?, colorHex: String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onOverride: () -> Unit,
    onSplitRemovalAccepted: () -> Unit,
    onRecurrenceStopEnd: () -> Unit,
    onRecurrenceStopUnlink: () -> Unit,
    onWarningDismissed: () -> Unit,
    dismissRequested: Boolean = false,
    confirmDismiss: (() -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The collapsed sheet is its body plus the fixed chrome around it (save button, paddings),
    // tracked while collapsed so late font loading or an error line resize it; every type has the
    // same body, so switching type keeps the height. It is also where "Més detalls" animates from.
    var chromeHeight by remember { mutableStateOf<Dp?>(null) }
    var collapsedBodyHeight by remember { mutableStateOf<Dp?>(null) }
    val compactContentHeight = chromeHeight?.let { chrome -> collapsedBodyHeight?.plus(chrome) }
    val expanded = form.showOptional
    val pinned = expanded || compactContentHeight != null
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        dismissRequested = dismissRequested,
        fixedHeightFraction = MovementSheetExpandedHeightFraction.takeIf { expanded },
        fixedHeight = if (expanded) null else compactContentHeight?.plus(AppSheetHandleTouchHeight),
        keepDragHandleInside = true,
        confirmDismiss = confirmDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (pinned) Modifier.weight(1f) else Modifier)
                .onSizeChanged { size ->
                    val body = collapsedBodyHeight
                    if (!pinned && body != null) {
                        chromeHeight = with(density) { size.height.toDp() } - body
                    }
                }
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (pinned) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    // The content's own height: a pinned viewport would otherwise be its minimum.
                    .wrapContentHeight(Alignment.Top)
                    .onSizeChanged { size ->
                        if (!expanded) collapsedBodyHeight = with(density) { size.height.toDp() }
                    }
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MovementFormBody(
                    form = form,
                    accounts = accounts,
                    categories = categories,
                    people = people,
                    trips = trips,
                    tags = tags,
                    onFormChange = onFormChange,
                    onTripSelected = onTripSelected,
                    onTagSelected = onTagSelected,
                    onSharedToggled = onSharedToggled,
                    onPayerSplitToggled = onPayerSplitToggled,
                    onSplitEditorChange = onSplitEditorChange,
                    onOtherPersonSelected = onOtherPersonSelected,
                    onRecurringToggled = onRecurringToggled,
                    onRecurringFrequencyChanged = onRecurringFrequencyChanged,
                    onOptionalToggled = onOptionalToggled,
                    onCreatePersonInSplit = onCreatePersonInSplit,
                    onCreateCategory = onCreateCategory,
                    // The sheet animates away after creating, then reports it is gone.
                    createCategory = { onConfirm, onDismiss -> CreateCategorySheet(onConfirm, onDismiss) },
                )
            }
            MovementSaveActions(
                form = form,
                onFormChange = onFormChange,
                onSave = onSave,
                onOverride = onOverride,
                onSplitRemovalAccepted = onSplitRemovalAccepted,
                onRecurrenceStopEnd = onRecurrenceStopEnd,
                onRecurrenceStopUnlink = onRecurrenceStopUnlink,
                onWarningDismissed = onWarningDismissed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
            )
        }
    }
}

private const val MovementSheetExpandedHeightFraction = 0.84f
