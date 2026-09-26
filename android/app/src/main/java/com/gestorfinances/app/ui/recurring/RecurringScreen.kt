package com.gestorfinances.app.ui.recurring

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.userShareCents
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.DetectedRecurringCandidate
import com.gestorfinances.app.domain.rules.DetectedTemplateAction
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.movements.AccountSelect
import com.gestorfinances.app.ui.movements.CategorySelect
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.ui.movements.FormTripTagSection
import com.gestorfinances.app.ui.movements.MovementTypeSelector
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import java.time.LocalDate

/**
 * The app-wide recurring ViewModel: the due-reminders sheet can appear over any page, so it lives
 * as long as the Activity rather than one page visit.
 */
@Composable
fun recurringViewModel(appContainer: AppContainer): RecurringViewModel = viewModel {
    RecurringViewModel(
        templateRepository = appContainer.templateRepository,
        accountRepository = appContainer.accountRepository,
        categoryRepository = appContainer.categoryRepository,
        tripRepository = appContainer.tripRepository,
        tagRepository = appContainer.tagRepository,
        movementRepository = appContainer.movementRepository,
        splitRepository = appContainer.splitRepository,
        personRepository = appContainer.personRepository,
        notificationRefresher = appContainer.notificationCoordinator,
    )
}

@Composable
fun RecurringScreen(
    viewModel: RecurringViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onMovementDetail: (MovementSummary) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    val form = state.form
    if (form != null) {
        val requestFormDismissal = rememberFormDismissGuard(
            formKey = form.id ?: "new-template",
            currentValue = form,
            hasMeaningfulChanges = { initial, current ->
                initial.copy(
                    showOptional = false,
                    showAdvanced = false,
                    errorRes = null,
                    errorField = null,
                    errorMessage = null,
                ) != current.copy(
                    showOptional = false,
                    showAdvanced = false,
                    errorRes = null,
                    errorField = null,
                    errorMessage = null,
                )
            },
            onDiscard = viewModel::onFormDismissed,
        )
        BackHandler(onBack = requestFormDismissal)
        RecurringFormScreen(
            form = form,
            accounts = state.accounts,
            categories = state.categories,
            trips = state.trips,
            tags = state.tags,
            people = state.people,
            onFormChange = viewModel::onFormChanged,
            onBack = requestFormDismissal,
            onSave = viewModel::onSaveClicked,
            modifier = modifier,
        )
    } else {
        RecurringContent(
            state = state,
            modifier = modifier,
            onAdd = viewModel::onAddClicked,
            onEdit = viewModel::onEditClicked,
            onPause = viewModel::onPauseClicked,
            onResume = viewModel::onResumeClicked,
            onEnd = viewModel::onEndClicked,
            onDelete = viewModel::onDeleteClicked,
            onConfirm = viewModel::onConfirmClicked,
            onSkip = viewModel::onSkipClicked,
            onSkipAll = viewModel::onSkipAllClicked,
            onDetectRecurring = viewModel::onDetectRecurringClicked,
            onHistory = viewModel::onHistoryClicked,
            onRetry = viewModel::onScreenShown,
        )
    }

    state.historyDetail?.let { detail ->
        RecurringHistorySheet(
            detail = detail,
            onDismiss = viewModel::onHistoryDismissed,
            onRetry = { viewModel.onHistoryClicked(detail.template) },
            onMovementDetail = onMovementDetail,
        )
    }
}

/**
 * App-wide recurring UI, rendered once above every page: surfaces due items proactively (once per
 * app start) instead of requiring a visit to Més > Recurring, plus the dialogs those items open.
 * [otherSheetOpen] keeps the due sheet from stacking on an unrelated movement sheet.
 */
@Composable
fun RecurringReminders(
    viewModel: RecurringViewModel,
    otherSheetOpen: Boolean,
    onDeleteCommitted: DeleteUndoHandler,
) {
    val state by viewModel.state.collectAsState()
    // `remember` (not `rememberSaveable`) is deliberate: a real process restart is exactly what
    // "once per app cold start" means, so losing this on process death re-shows the sheet.
    var dueRemindersShown by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    RecurringOverlays(viewModel = viewModel, onDeleteCommitted = onDeleteCommitted)

    // `hasOpenDialog` makes the sheet step aside while one of its own actions (confirm, end) has a
    // sub-dialog open, then reappear once that closes.
    if (!dueRemindersShown && !state.hasOpenDialog && !otherSheetOpen && state.duePrompts.isNotEmpty()) {
        DueRemindersSheet(
            duePrompts = state.duePrompts,
            onConfirm = viewModel::onConfirmClicked,
            onSkip = viewModel::onSkipClicked,
            onSkipAll = viewModel::onSkipAllClicked,
            onEnd = viewModel::onEndClicked,
            onDismiss = { dueRemindersShown = true },
        )
    }
}

/** The remaining modals/dialogs driven by [RecurringViewModel]'s state that can be triggered
 * independent of which screen is currently showing (due-prompt confirm, detection review, end/
 * delete confirmations). Rendered once from [RecurringReminders] -- NOT called from
 * [RecurringScreen] itself, to avoid rendering every dialog twice when the user is actually on
 * that screen. The template create/edit form is the one piece of this ViewModel's state that's
 * only ever opened from [RecurringScreen] itself, so it renders as a local full-page swap there
 * instead (see [RecurringScreen]), not here. */
@Composable
private fun RecurringOverlays(
    viewModel: RecurringViewModel,
    onDeleteCommitted: DeleteUndoHandler = {},
) {
    val state by viewModel.state.collectAsState()

    state.confirmPrompt?.let { prompt ->
        ConfirmPromptDialog(
            prompt = prompt,
            people = state.people,
            onFormChange = viewModel::onConfirmFormChanged,
            onDismiss = viewModel::onConfirmDismissed,
            onSave = viewModel::onConfirmSaveClicked,
        )
    }

    state.detectionReview?.let { review ->
        DetectionReviewSheet(
            review = review,
            onToggle = viewModel::onDetectionItemToggled,
            onDismiss = viewModel::onDetectionReviewDismissed,
            onConfirmAll = viewModel::onDetectionConfirmAllClicked,
        )
    }

    state.endCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = viewModel::onEndDismissed,
            title = { Text(text = stringResource(R.string.template_end_confirm_title)) },
            text = { Text(text = stringResource(R.string.template_end_confirm_body)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onEndConfirmed) {
                    Text(text = stringResource(R.string.recurring_action_end))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onEndDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.deleteCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text(text = stringResource(R.string.template_delete_confirm_title)) },
            text = { Text(text = stringResource(R.string.template_delete_confirm_body)) },
            confirmButton = {
                DestructiveTextButton(
                    onClick = { viewModel.onDeleteConfirmed(onSuccess = onDeleteCommitted) },
                ) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun RecurringContent(
    state: RecurringUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (TemplateSummary) -> Unit,
    onPause: (TemplateSummary) -> Unit,
    onResume: (TemplateSummary) -> Unit,
    onEnd: (TemplateSummary) -> Unit,
    onDelete: (TemplateSummary) -> Unit,
    onConfirm: (DuePrompt) -> Unit,
    onSkip: (DuePrompt) -> Unit,
    onSkipAll: (DuePrompt) -> Unit,
    onDetectRecurring: () -> Unit,
    onHistory: (TemplateSummary) -> Unit,
    onRetry: () -> Unit,
) {
    val active = state.templates
        .filter { it.status == TemplateStatus.ACTIVE }
        .sortedWith(compareBy<TemplateSummary>({ it.nextDueDateSortKey() }, { it.name?.lowercase() ?: "" }))
    var endedExpanded by remember { mutableStateOf(false) }
    val paused = state.templates
        .filter { it.status == TemplateStatus.PAUSED }
        .sortedWith(compareBy<TemplateSummary>({ it.nextDueDateSortKey() }, { it.name?.lowercase() ?: "" }))
    val ended = state.templates
        .filter { it.status == TemplateStatus.ENDED }
        .sortedWith(compareBy<TemplateSummary>({ it.nextDueDateSortKey() }, { it.name?.lowercase() ?: "" }))

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RootPageHeader(
                title = stringResource(R.string.recurring_list_title),
                trailing = {
                    TopBarIconButton(
                        icon = Icons.Outlined.AutoAwesome,
                        contentDescription = stringResource(
                            if (state.isDetecting) {
                                R.string.recurring_detect_action_running
                            } else {
                                R.string.recurring_detect_action
                            },
                        ),
                        onClick = onDetectRecurring,
                        enabled = !state.isDetecting,
                    )
                },
            )
        }

        if (state.duePrompts.isNotEmpty()) {
            item(key = "due-header") {
                Text(
                    text = stringResource(R.string.recurring_due_section_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            items(items = state.duePrompts, key = { "due-${it.template.id}" }) { prompt ->
                DuePromptCard(
                    prompt = prompt,
                    onConfirm = { onConfirm(prompt) },
                    onSkip = { onSkip(prompt) },
                    onSkipAll = { onSkipAll(prompt) },
                    onEnd = { onEnd(prompt.template) },
                )
            }
        }

        if (state.hasMonthlySummary) {
            item(key = "monthly-summary") {
                MonthlySummaryCard(state = state)
            }
        }

        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_recurring,
                    onRetry = onRetry,
                )
            }
        }

        if (!state.isLoading && state.templates.isEmpty()) {
            item { EmptyRecurringCard(onAdd = onAdd) }
        } else {
            templateSection(
                titleRes = R.string.recurring_scheduled_section_title,
                orderHintRes = R.string.recurring_order_next_due,
                onHistory = onHistory,
                templates = active,
                occurrenceCounts = state.occurrenceCounts,
                paymentStates = state.monthlyPaymentStates,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onEnd = onEnd,
                onDelete = onDelete,
            )
            templateSection(
                titleRes = R.string.recurring_paused_section_title,
                onHistory = onHistory,
                templates = paused,
                occurrenceCounts = state.occurrenceCounts,
                paymentStates = state.monthlyPaymentStates,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onEnd = onEnd,
                onDelete = onDelete,
            )
            if (ended.isNotEmpty()) {
                item(key = "ended-header") {
                    CollapsibleSectionHeader(
                        title = stringResource(R.string.recurring_ended_section_title),
                        count = ended.size,
                        expanded = endedExpanded,
                        onToggle = { endedExpanded = !endedExpanded },
                    )
                }
                if (endedExpanded) {
                    items(items = ended, key = { it.id }) { template ->
                        TemplateRow(
                            template = template,
                            onHistory = { onHistory(template) },
                            occurrenceCount = state.occurrenceCounts[template.id] ?: 0L,
                            paymentState = state.monthlyPaymentStates[template.id] ?: TemplateMonthPaymentState.NONE,
                            onEdit = { onEdit(template) },
                            onPause = { onPause(template) },
                            onResume = { onResume(template) },
                            onEnd = { onEnd(template) },
                            onDelete = { onDelete(template) },
                        )
                    }
                }
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.recurring_list_add),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.templateSection(
    titleRes: Int,
    orderHintRes: Int? = null,
    onHistory: (TemplateSummary) -> Unit,
    templates: List<TemplateSummary>,
    occurrenceCounts: Map<String, Long>,
    paymentStates: Map<String, TemplateMonthPaymentState>,
    onEdit: (TemplateSummary) -> Unit,
    onPause: (TemplateSummary) -> Unit,
    onResume: (TemplateSummary) -> Unit,
    onEnd: (TemplateSummary) -> Unit,
    onDelete: (TemplateSummary) -> Unit,
) {
    if (templates.isEmpty()) return
    item(key = "header-$titleRes") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(titleRes),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            orderHintRes?.let { hintRes ->
                Text(
                    text = stringResource(hintRes),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    items(items = templates, key = { it.id }) { template ->
        TemplateRow(
            template = template,
            onHistory = { onHistory(template) },
            occurrenceCount = occurrenceCounts[template.id] ?: 0L,
            paymentState = paymentStates[template.id] ?: TemplateMonthPaymentState.NONE,
            onEdit = { onEdit(template) },
            onPause = { onPause(template) },
            onResume = { onResume(template) },
            onEnd = { onEnd(template) },
            onDelete = { onDelete(template) },
        )
    }
}

/** Amount figure shown for a template row / due-prompt card: variable amounts show a pill, a
 * shared template shows the user's own share as primary with the total as a secondary line
 * (matching how [com.gestorfinances.app.ui.common.MovementListItem] displays a shared movement),
 * and a plain template just shows the total. */
@Composable
private fun TemplateAmountDisplay(template: TemplateSummary, style: TextStyle = MaterialTheme.typography.titleSmall) {
    val amount = template.amountCents
    if (template.amountIsVariable || amount == null) {
        NeutralPill(text = stringResource(R.string.recurring_amount_variable))
        return
    }
    val userShare = template.signedUserShareCents()
    if (template.splitConfig != null && userShare != null) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            MoneyText(
                cents = userShare,
                color = FinanceTheme.colors.shared,
                style = style,
                signed = template.type != MovementType.EXPENSE,
            )
            Text(
                text = stringResource(R.string.movement_total_short, formatEuroCents(amount)),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
            )
        }
    } else {
        MoneyText(
            cents = template.signedAmountCents(),
            color = FinanceTheme.colors.amountColor(template.type),
            style = style,
            signed = template.type != MovementType.EXPENSE,
        )
    }
}

@Composable
private fun TemplateRow(
    template: TemplateSummary,
    onHistory: () -> Unit,
    occurrenceCount: Long,
    paymentState: TemplateMonthPaymentState,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHistory)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecurringDateBadge(template = template, paymentState = paymentState)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = template.name ?: template.type.label(),
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (template.type == MovementType.INCOME) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (template.splitConfig != null) {
                        SharedPill()
                    }
                }
                Text(
                    text = listOfNotNull(template.cadenceLabel(), template.personName, template.accountName)
                        .joinToString(separator = " · "),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = listOf(
                        stringResource(R.string.recurring_occurrence_count, occurrenceCount),
                        paymentState.label(template.frequency),
                    ).filter { it.isNotBlank() }.joinToString(separator = " · "),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TemplateAmountDisplay(template = template)
            TemplateRowMenu(
                status = template.status,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onEnd = onEnd,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun RecurringHistorySheet(
    detail: RecurringHistoryDetailState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.88f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            val movementCountText = if (!detail.isLoading) {
                pluralStringResource(
                    R.plurals.account_flow_movement_count,
                    detail.movements.size,
                    detail.movements.size,
                )
            } else {
                null
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Autorenew,
                    contentDescription = null,
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.template.name ?: detail.template.type.label(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    movementCountText?.let { count ->
                        Text(
                            text = count,
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            HorizontalDivider()

            when {
                detail.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.recurring_history_loading),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                detail.movements.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.recurring_history_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(
                        detail.movements,
                        key = { _, movement -> movement.id },
                    ) { index, movement ->
                        MovementListItem(
                            movement = movement,
                            onClick = { onMovementDetail(movement) },
                            position = movementRowPosition(index, detail.movements.size),
                        )
                    }
                }
            }

            detail.errorMessage?.let { message ->
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_recurring,
                    onRetry = onRetry,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SharedPill() {
    val sharedColor = FinanceTheme.colors.shared
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = sharedColor.copy(alpha = 0.14f),
        contentColor = sharedColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text = stringResource(R.string.movement_shared_badge), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecurringDateBadge(
    template: TemplateSummary,
    paymentState: TemplateMonthPaymentState,
) {
    val dateBadgeColor = when (paymentState) {
        TemplateMonthPaymentState.PAID -> FinanceTheme.colors.income.copy(alpha = 0.16f)
        TemplateMonthPaymentState.PENDING, TemplateMonthPaymentState.PARTIALLY_PAID -> FinanceTheme.colors.alert.copy(alpha = 0.16f)
        TemplateMonthPaymentState.NONE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val dateBadgeContent = when (paymentState) {
        TemplateMonthPaymentState.PAID -> FinanceTheme.colors.income
        TemplateMonthPaymentState.PENDING, TemplateMonthPaymentState.PARTIALLY_PAID -> FinanceTheme.colors.alert
        TemplateMonthPaymentState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.width(48.dp).height(52.dp),
        shape = MaterialTheme.shapes.small,
        color = dateBadgeColor,
        contentColor = dateBadgeContent,
    ) {
        val date = parseIsoDateOrNull(template.nextDueDate)
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (template.status == TemplateStatus.ACTIVE && date != null) {
                Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = date.month.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.forLanguageTag("ca"))
                        .replace(".", "")
                        .uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Icon(
                    imageVector = if (template.status == TemplateStatus.PAUSED) {
                        Icons.Outlined.PauseCircle
                    } else {
                        Icons.Outlined.Cancel
                    },
                    contentDescription = template.status.label(),
                    tint = if (template.status == TemplateStatus.PAUSED) FinanceTheme.colors.alert else FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TemplateMonthPaymentState.label(frequency: RecurrenceFrequency): String =
    when (this) {
        TemplateMonthPaymentState.NONE -> ""
        TemplateMonthPaymentState.PAID -> stringResource(
            if (frequency == RecurrenceFrequency.YEARLY) R.string.recurring_payment_paid_yearly else R.string.recurring_payment_paid,
        )
        TemplateMonthPaymentState.PENDING -> stringResource(
            if (frequency == RecurrenceFrequency.YEARLY) R.string.recurring_payment_pending_yearly else R.string.recurring_payment_pending,
        )
        TemplateMonthPaymentState.PARTIALLY_PAID -> stringResource(
            if (frequency == RecurrenceFrequency.YEARLY) R.string.recurring_payment_partial_yearly else R.string.recurring_payment_partial,
        )
    }

private fun TemplateSummary.nextDueDateSortKey(): LocalDate =
    parseIsoDateOrNull(nextDueDate) ?: LocalDate.MAX

@Composable
private fun TemplateRowMenu(
    status: TemplateStatus,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
                tint = FinanceTheme.colors.mutedText,
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            when (status) {
                TemplateStatus.ACTIVE -> AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_action_pause)) },
                    onClick = { expanded = false; onPause() },
                )
                TemplateStatus.PAUSED, TemplateStatus.ENDED -> AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_action_resume)) },
                    onClick = { expanded = false; onResume() },
                )
            }
            if (status != TemplateStatus.ENDED) {
                AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_action_end)) },
                    onClick = { expanded = false; onEnd() },
                )
            }
            AppDropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.common_archive),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun MonthlySummaryCard(state: RecurringUiState) {
    val expenseForecast = state.monthlyPaidExpenseCents + state.monthlyRemainingExpenseCents
    val incomeForecast = state.monthlyPaidIncomeCents + state.monthlyRemainingIncomeCents
    val expenseProgress = if (expenseForecast > 0L) {
        state.monthlyPaidExpenseCents.toFloat() / expenseForecast.toFloat()
    } else {
        0f
    }
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.recurring_month_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                NeutralPill(
                    text = stringResource(R.string.recurring_active_count, state.templates.count { it.status == TemplateStatus.ACTIVE }),
                )
            }
            Text(
                text = stringResource(R.string.recurring_expenses_section_title),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            MoneyText(
                cents = -expenseForecast,
                color = FinanceTheme.colors.expense,
                style = MaterialTheme.typography.headlineMedium,
            )
            BudgetProgressBar(fraction = expenseProgress, color = FinanceTheme.colors.expense)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthExpenseStat(
                    label = stringResource(R.string.recurring_month_registered_label),
                    cents = state.monthlyPaidExpenseCents,
                    color = FinanceTheme.colors.income,
                    modifier = Modifier.weight(1f),
                )
                MonthExpenseStat(
                    label = stringResource(R.string.recurring_month_pending_label),
                    cents = state.monthlyRemainingExpenseCents,
                    color = FinanceTheme.colors.alert,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(
                    R.string.recurring_month_income_summary,
                    formatEuroCents(incomeForecast),
                    formatEuroCents(state.monthlyPaidIncomeCents),
                ),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MonthExpenseStat(
    label: String,
    cents: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MoneyText(
                cents = -cents,
                color = color,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = label,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun DuePromptCard(
    prompt: DuePrompt,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onSkipAll: () -> Unit,
    onEnd: () -> Unit,
) {
    val template = prompt.template
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeutralPill(
                    text = stringResource(R.string.recurring_due_badge),
                    leadingIcon = Icons.Outlined.Warning,
                )
                if (template.splitConfig != null) {
                    SharedPill()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name ?: template.type.label(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.recurring_next_due, formatCompactDate(prompt.dueDate)),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TemplateAmountDisplay(template = template)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.recurring_action_add_payment),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSkip) {
                    Text(text = stringResource(R.string.recurring_action_skip))
                }
            }
            if (prompt.pendingCount > 1) {
                TextButton(onClick = onSkipAll) {
                    Text(text = stringResource(R.string.recurring_skip_all))
                }
            }
            DestructiveTextButton(onClick = onEnd) {
                Text(text = stringResource(R.string.recurring_action_end))
            }
        }
    }
}

/** Auto-triggered on app cold start (from [RecurringReminders], not from [RecurringScreen] itself) when
 * any template is due; the prompt appears once per cold
 * start, persists until acted on" rationale. Reuses [DuePromptCard] verbatim; every action here
 * (confirm/skip/skip-all/end) opens the same existing dialogs [RecurringOverlays] renders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueRemindersSheet(
    duePrompts: List<DuePrompt>,
    onConfirm: (DuePrompt) -> Unit,
    onSkip: (DuePrompt) -> Unit,
    onSkipAll: (DuePrompt) -> Unit,
    onEnd: (TemplateSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_due_section_title),
                style = MaterialTheme.typography.titleLarge,
            )
            duePrompts.forEach { prompt ->
                DuePromptCard(
                    prompt = prompt,
                    onConfirm = { onConfirm(prompt) },
                    onSkip = { onSkip(prompt) },
                    onSkipAll = { onSkipAll(prompt) },
                    onEnd = { onEnd(prompt.template) },
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmPromptDialog(
    prompt: ConfirmPromptState,
    people: List<PersonSummary>,
    onFormChange: (ConfirmPromptState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_action_add_payment),
                style = MaterialTheme.typography.titleLarge,
            )
            val subtitle = listOfNotNull(
                prompt.templateName.takeIf { it.isNotBlank() },
                prompt.settlementPersonName,
                prompt.accountName,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle.joinToString(separator = " · "),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            prompt.errorMessage?.let {
                InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_recurring)
            }
            val amountError = prompt.errorField == ConfirmPromptField.AMOUNT
            OutlinedTextField(
                value = prompt.amount,
                onValueChange = { onFormChange(prompt.copy(amount = it)) },
                label = { Text(text = stringResource(R.string.template_field_amount)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                isError = amountError,
                supportingText = if (amountError && prompt.errorRes != null) {
                    { Text(text = stringResource(prompt.errorRes)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldKeyboardActions(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(amountError),
            )
            if (prompt.splitConfig != null) {
                SplitPreviewCard(splitConfig = prompt.splitConfig, amountText = prompt.amount, people = people)
            }
            val dateError = prompt.errorField == ConfirmPromptField.DATE
            FormDatePicker(
                label = stringResource(R.string.template_field_next_due),
                date = prompt.date,
                onDateChange = { onFormChange(prompt.copy(date = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(dateError),
                isError = dateError,
                supportingText = if (dateError && prompt.errorRes != null) {
                    stringResource(prompt.errorRes)
                } else null,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
                PrimaryButton(
                    text = stringResource(R.string.recurring_action_add_payment),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !prompt.isSaving,
                )
            }
        }
    }
}

/** Live preview of how the confirmed amount would be split, so a shared template's confirm sheet
 * never books a rescaled or dropped split without the user seeing it first. Uses the
 * exact same rule [RecurringViewModel] applies at save time ([TemplateSplitConfig.previewShares]). */
@Composable
private fun SplitPreviewCard(
    splitConfig: TemplateSplitConfig,
    amountText: String,
    people: List<PersonSummary>,
) {
    val amountCents = parseEuroCents(amountText, allowNegative = false)
    val shares = amountCents?.takeIf { it > 0L }?.let { splitConfig.previewShares(it, people) }.orEmpty()
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_split_preview_title),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            if (shares.isEmpty()) {
                Text(
                    text = stringResource(R.string.recurring_split_preview_enter_amount),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                shares.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (line.isUser) {
                                stringResource(R.string.split_payer_user)
                            } else {
                                line.personName ?: stringResource(R.string.recurring_split_preview_person_unknown)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MoneyText(cents = line.amountCents, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecurringCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.recurring_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.recurring_list_add),
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun TemplateFormScreen(
    form: TemplateFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFormChange: (TemplateFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(
                if (form.id == null) R.string.template_new_title else R.string.template_edit_title,
            ),
        )
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_recurring)
        }

        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            MovementTypeSelector(
                selected = form.type,
                onSelect = { onFormChange(form.copy(type = it)) },
            )
        }

        FormSectionLabel(R.string.recurring_form_details_section)
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text(text = stringResource(R.string.template_field_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )

        FormSectionLabel(R.string.recurring_form_amount_section)
        FormToggleRow(
            label = stringResource(R.string.template_field_amount_variable),
            checked = form.amountIsVariable,
            onCheckedChange = { onFormChange(form.copy(amountIsVariable = it)) },
        )
        if (!form.amountIsVariable) {
            val amountError = form.errorField == TemplateFormField.AMOUNT
            OutlinedTextField(
                value = form.amount,
                onValueChange = { onFormChange(form.copy(amount = it)) },
                label = { Text(text = stringResource(R.string.template_field_amount)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                isError = amountError,
                supportingText = if (amountError && form.errorRes != null) {
                    { Text(text = stringResource(form.errorRes)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldKeyboardActions(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(amountError),
            )
            val amountFlexError = form.errorField == TemplateFormField.AMOUNT_FLEX
            OutlinedTextField(
                value = form.amountFlex,
                onValueChange = { onFormChange(form.copy(amountFlex = it)) },
                label = { Text(text = stringResource(R.string.template_field_amount_flex)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                isError = amountFlexError,
                supportingText = if (amountFlexError && form.errorRes != null) {
                    { Text(text = stringResource(form.errorRes)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = doneKeyboardActions(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(amountFlexError),
            )
        }

        // Row: next due date & category (transfers have no category — date spans full width)
        FormSectionLabel(R.string.recurring_form_account_section)
        val nextDueError = form.errorField == TemplateFormField.NEXT_DUE_DATE
        val nextDueErrorText = if (nextDueError && form.errorRes != null) stringResource(form.errorRes) else null
        if (form.type == MovementType.TRANSFER) {
            FormDatePicker(
                label = stringResource(R.string.template_field_next_due),
                date = form.nextDueDate,
                onDateChange = { onFormChange(form.copy(nextDueDate = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(nextDueError),
                isError = nextDueError,
                supportingText = nextDueErrorText,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FormDatePicker(
                    label = stringResource(R.string.template_field_next_due),
                    date = form.nextDueDate,
                    onDateChange = { onFormChange(form.copy(nextDueDate = it)) },
                    modifier = Modifier
                        .weight(1f)
                        .scrollToWhen(nextDueError),
                    isError = nextDueError,
                    supportingText = nextDueErrorText,
                )
                CategorySelect(
                    categories = categories,
                    type = form.type,
                    selectedId = form.categoryId,
                    onSelect = { onFormChange(form.copy(categoryId = it)) },
                    modifier = Modifier.weight(1.5f),
                )
            }
        }

        // Account(s)
        val accountError = form.errorField == TemplateFormField.ACCOUNT
        val destinationError = form.errorField == TemplateFormField.DESTINATION_ACCOUNT
        val accountErrorText = if (form.errorRes != null) stringResource(form.errorRes) else null
        if (form.type == MovementType.TRANSFER) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccountSelect(
                    label = stringResource(R.string.template_field_account),
                    selectedId = form.accountId,
                    accounts = accounts,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                    modifier = Modifier
                        .weight(1f)
                        .scrollToWhen(accountError),
                    isError = accountError,
                    supportingText = if (accountError) accountErrorText else null,
                )
                AccountSelect(
                    label = stringResource(R.string.template_field_dest_account),
                    selectedId = form.destinationAccountId,
                    accounts = accounts,
                    onSelect = { onFormChange(form.copy(destinationAccountId = it)) },
                    modifier = Modifier
                        .weight(1f)
                        .scrollToWhen(destinationError),
                    isError = destinationError,
                    supportingText = if (destinationError) accountErrorText else null,
                )
            }
        } else {
            AccountSelect(
                label = stringResource(R.string.template_field_account),
                selectedId = form.accountId,
                accounts = accounts,
                onSelect = { onFormChange(form.copy(accountId = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(accountError),
                isError = accountError,
                supportingText = if (accountError) accountErrorText else null,
            )
        }

        if (form.type != MovementType.TRANSFER) {
            FormTripTagSection(
                trips = trips,
                tags = tags,
                tripId = form.tripId,
                tagId = form.tagId,
                onTripSelected = { onFormChange(form.copy(tripId = it)) },
                onTagSelected = { onFormChange(form.copy(tagId = it)) },
            )
        }

        FormSectionLabel(R.string.recurring_form_schedule_section)
        ScheduleFields(form = form, onFormChange = onFormChange)

        val dateFlexError = form.errorField == TemplateFormField.DATE_FLEX
        OutlinedTextField(
            value = form.dateFlex,
            onValueChange = { onFormChange(form.copy(dateFlex = it)) },
            label = { Text(text = stringResource(R.string.template_field_date_flex)) },
            singleLine = true,
            isError = dateFlexError,
            supportingText = if (dateFlexError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(dateFlexError),
        )
        val leadDaysError = form.errorField == TemplateFormField.LEAD_DAYS
        OutlinedTextField(
            value = form.leadDays,
            onValueChange = { onFormChange(form.copy(leadDays = it)) },
            label = { Text(text = stringResource(R.string.template_field_lead_days)) },
            singleLine = true,
            isError = leadDaysError,
            supportingText = if (leadDaysError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(leadDaysError),
        )

        LabeledSegmentedControl(
            label = stringResource(R.string.template_field_status),
            options = TemplateStatus.entries,
            selected = form.status,
            optionLabel = { it.label() },
            onSelect = { onFormChange(form.copy(status = it)) },
        )

        FormSectionLabel(R.string.recurring_form_notes_section)
        OutlinedTextField(
            value = form.payee,
            onValueChange = { onFormChange(form.copy(payee = it)) },
            label = { Text(text = stringResource(R.string.template_field_payee)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(text = stringResource(R.string.template_field_notes)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
            PrimaryButton(
                text = stringResource(
                    if (form.id == null) R.string.template_save_new else R.string.template_save_changes,
                ),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FormSectionLabel(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.labelLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionReviewSheet(
    review: DetectionReviewState,
    onToggle: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirmAll: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_detect_review_title),
                style = MaterialTheme.typography.titleLarge,
            )
            review.errorMessage?.let {
                InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_recurring)
            }
            if (review.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.recurring_detect_review_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                review.items.forEachIndexed { index, item ->
                    DetectionCandidateRow(
                        item = item,
                        onToggle = { checked -> onToggle(index, checked) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
                if (review.items.isNotEmpty()) {
                    PrimaryButton(
                        text = stringResource(R.string.recurring_detect_confirm_selected),
                        onClick = onConfirmAll,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectionCandidateRow(
    item: DetectionReviewItem,
    onToggle: (Boolean) -> Unit,
) {
    val candidate = item.candidate
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = item.accepted, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = candidate.name?.takeIf { it.isNotBlank() } ?: candidate.payee.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.splitConfig != null) {
                        SharedPill()
                    }
                    NeutralPill(
                        text = stringResource(
                            if (candidate.action == DetectedTemplateAction.NEW) {
                                R.string.recurring_detect_badge_new
                            } else {
                                R.string.recurring_detect_badge_update
                            },
                        ),
                    )
                }
                Text(
                    text = listOf(
                        candidate.frequency.label(),
                        candidate.suggestedStatus.label(),
                        stringResource(R.string.recurring_detect_occurrences, candidate.occurrenceCount),
                    ).joinToString(separator = " · "),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                DetectionCandidateAmount(candidate = candidate, splitConfig = item.splitConfig)
            }
        }
    }
}

/** Mirrors [TemplateAmountDisplay]'s primary-share/secondary-total pattern for a not-yet-created
 * candidate: a shared candidate shows the user's own share with the total alongside, instead of
 * the group's raw total (which used to be the only figure shown, hiding sharing entirely). */
@Composable
private fun DetectionCandidateAmount(candidate: DetectedRecurringCandidate, splitConfig: TemplateSplitConfig?) {
    val amount = candidate.amountCents
    if (candidate.amountIsVariable || amount == null) {
        NeutralPill(text = stringResource(R.string.template_field_amount_variable))
        return
    }
    val userShare = splitConfig?.userShareCents(amount)
    if (splitConfig != null && userShare != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MoneyText(cents = userShare, color = FinanceTheme.colors.shared, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.movement_total_short, formatEuroCents(amount)),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    } else {
        MoneyText(cents = amount, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScheduleFields(
    form: TemplateFormState,
    onFormChange: (TemplateFormState) -> Unit,
) {
    FormSelect(
        label = stringResource(R.string.template_field_frequency),
        options = RecurrenceFrequency.entries.map { SelectOption(id = it.name, label = it.label()) },
        selectedId = form.frequency.name,
        onSelect = { id ->
            RecurrenceFrequency.entries.firstOrNull { it.name == id }?.let { frequency ->
                onFormChange(form.copy(frequency = frequency))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val scheduleError = form.errorField == TemplateFormField.SCHEDULE
    val scheduleErrorText = if (scheduleError && form.errorRes != null) stringResource(form.errorRes) else null
    when {
        form.frequency.usesDayOfMonth() -> OutlinedTextField(
            value = form.dayOfMonth,
            onValueChange = { onFormChange(form.copy(dayOfMonth = it)) },
            label = { Text(text = stringResource(R.string.template_field_anchor_day)) },
            singleLine = true,
            isError = scheduleError,
            supportingText = scheduleErrorText?.let { { Text(text = it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(scheduleError),
        )
        form.frequency.usesWeekday() -> {
            val labels = stringArrayResource(R.array.template_weekday_short)
            FormSelect(
                label = stringResource(R.string.template_field_weekday),
                options = labels.mapIndexed { index, label -> SelectOption(id = index.toString(), label = label) },
                selectedId = form.weekday?.toString(),
                onSelect = { id -> onFormChange(form.copy(weekday = id?.toIntOrNull())) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        form.frequency == RecurrenceFrequency.CUSTOM -> {
            OutlinedTextField(
                value = form.intervalCount,
                onValueChange = { onFormChange(form.copy(intervalCount = it)) },
                label = { Text(text = stringResource(R.string.template_field_interval)) },
                singleLine = true,
                isError = scheduleError,
                supportingText = scheduleErrorText?.let { { Text(text = it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = doneKeyboardActions(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(scheduleError),
            )
            FormSelect(
                label = stringResource(R.string.template_field_custom_unit),
                options = CustomRecurrenceUnit.entries.map { SelectOption(id = it.name, label = it.label()) },
                selectedId = form.customUnit.name,
                onSelect = { id ->
                    CustomRecurrenceUnit.entries.firstOrNull { it.name == id }?.let { unit ->
                        onFormChange(form.copy(customUnit = unit))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecurrenceFrequency.label(): String =
    when (this) {
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.recurring_cadence_weekly)
        RecurrenceFrequency.FORTNIGHTLY -> stringResource(R.string.recurring_cadence_fortnightly)
        RecurrenceFrequency.MONTHLY -> stringResource(R.string.recurring_cadence_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.recurring_cadence_yearly)
        RecurrenceFrequency.CUSTOM -> stringResource(R.string.recurring_cadence_custom)
    }

@Composable
private fun CustomRecurrenceUnit.label(): String =
    when (this) {
        CustomRecurrenceUnit.DAYS -> stringResource(R.string.template_unit_days)
        CustomRecurrenceUnit.WEEKS -> stringResource(R.string.template_unit_weeks)
        CustomRecurrenceUnit.MONTHS -> stringResource(R.string.template_unit_months)
        CustomRecurrenceUnit.YEARS -> stringResource(R.string.template_unit_years)
    }

@Composable
private fun TemplateStatus.label(): String =
    when (this) {
        TemplateStatus.ACTIVE -> stringResource(R.string.template_status_active)
        TemplateStatus.PAUSED -> stringResource(R.string.template_status_paused)
        TemplateStatus.ENDED -> stringResource(R.string.template_status_ended)
    }

@Composable
private fun TemplateSummary.cadenceLabel(): String =
    when (frequency) {
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.recurring_cadence_weekly)
        RecurrenceFrequency.FORTNIGHTLY -> stringResource(R.string.recurring_cadence_fortnightly)
        RecurrenceFrequency.MONTHLY -> dayOfMonth?.let {
            stringResource(R.string.recurring_cadence_monthly_day, it.toInt())
        } ?: stringResource(R.string.recurring_cadence_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.recurring_cadence_yearly)
        RecurrenceFrequency.CUSTOM -> stringResource(R.string.recurring_cadence_custom)
    }

private fun TemplateSummary.signedAmountCents(): Long {
    val amount = amountCents ?: 0L
    return if (type == MovementType.EXPENSE) -amount else amount
}

/** The user's own share, signed the same way as [signedAmountCents] — null when there's no split
 * to derive it from, or the amount is unset (variable-amount template). */
private fun TemplateSummary.signedUserShareCents(): Long? {
    val amount = amountCents ?: return null
    val share = splitConfig?.userShareCents(amount) ?: return null
    return if (type == MovementType.EXPENSE) -share else share
}

