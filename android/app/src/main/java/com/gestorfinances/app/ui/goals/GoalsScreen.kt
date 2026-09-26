package com.gestorfinances.app.ui.goals

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.GoalAllocation
import com.gestorfinances.app.data.repository.GoalStatus
import com.gestorfinances.app.data.repository.GoalSummary
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.domain.rules.GoalProgress
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IconPickerRow
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate

/** This page visit's ViewModel; [accountId] narrows it to one account's goals. */
@Composable
fun goalsViewModel(appContainer: AppContainer, accountId: String? = null): GoalsViewModel = viewModel {
    GoalsViewModel(
        goalRepository = appContainer.goalRepository,
        accountRepository = appContainer.accountRepository,
    ).apply {
        accountId?.let(::showForAccount)
    }
}

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onDeleteCommitted: DeleteUndoHandler = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    GoalsContent(
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onGoalClicked = viewModel::onGoalClicked,
        onRetry = viewModel::onScreenShown,
    )

    state.detail?.let { detail ->
        val goal = state.goals.firstOrNull { it.id == detail.goalId }
        if (goal != null) {
            GoalDetailSheet(
                goal = goal,
                progress = goal.progress(state.today),
                allocations = detail.allocations,
                accountAllocation = goal.accountId?.let { state.accountAllocations[it] },
                onDismiss = viewModel::onDetailDismissed,
                onEdit = { viewModel.onEditClicked(goal) },
                onStatusChange = { viewModel.onStatusChanged(goal, it) },
                onAddAllocation = { viewModel.onAddAllocationClicked(goal) },
                onRelease = { viewModel.onAddAllocationClicked(goal, release = true) },
                detail = detail,
                accountAllocations = state.accountAllocations,
                onRetry = { viewModel.onGoalClicked(goal) },
                onEditAllocation = viewModel::onEditAllocationClicked,
                onDeleteAllocation = { viewModel.onDeleteAllocationClicked(it, onDeleteCommitted) },
            )
        }
    }

    state.form?.let { form ->
        val requestFormDismissal = rememberFormDismissGuard(
            formKey = form.id ?: "new-goal",
            currentValue = form,
            hasMeaningfulChanges = { initial, current -> initial.compareValues() != current.compareValues() },
            onDiscard = viewModel::onFormDismissed,
        )
        GoalFormSheet(
            form = form,
            accounts = state.accounts,
            dedicatedAccountIds = state.dedicatedAccountIds - setOfNotNull(
                state.goals.firstOrNull { it.id == form.id }?.accountId,
            ),
            onFormChange = viewModel::onFormChanged,
            onDismiss = requestFormDismissal,
            onSave = viewModel::onSaveClicked,
            onDelete = viewModel::onDeleteEditingGoalClicked,
        )
    }

    state.allocationForm?.let { form ->
        val requestFormDismissal = rememberFormDismissGuard(
            formKey = form.id ?: "new-allocation",
            currentValue = form,
            hasMeaningfulChanges = { initial, current -> initial.compareValues() != current.compareValues() },
            onDiscard = viewModel::onAllocationFormDismissed,
        )
        AllocationFormSheet(
            form = form,
            accounts = state.accounts.filter { it.id !in state.dedicatedAccountIds },
            accountAllocations = state.accountAllocations,
            reservations = state.detail?.reservations.orEmpty(),
            editingAllocation = state.detail?.allocations?.firstOrNull { it.id == form.id },
            onFormChange = viewModel::onAllocationFormChanged,
            onDismiss = requestFormDismissal,
            onSave = { viewModel.onSaveAllocationClicked() },
            onConfirmOverAllocation = { viewModel.onSaveAllocationClicked(confirmOverAllocation = true) },
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.goal_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.goal_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(
                    onClick = { viewModel.onArchiveConfirmed(onSuccess = onDeleteCommitted) },
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

@Composable
private fun GoalsContent(
    state: GoalsUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onGoalClicked: (GoalSummary) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RootPageHeader(
                title = stringResource(R.string.goal_list_title),
                trailing = { TextButton(onClick = onAdd) { Text(stringResource(R.string.goal_list_add)) } },
            )
        }

        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_goals,
                    onRetry = onRetry,
                )
            }
        }

        state.actionErrorRes?.let { error ->
            item { InlineBanner(kind = BannerKind.Error, text = stringResource(error)) }
        }
        if (state.isLoading) {
            item { CircularProgressIndicator() }
        }
        state.accountFilterId?.let { id ->
            item { Text(text = state.accounts.firstOrNull { it.id == id }?.name.orEmpty(), style = MaterialTheme.typography.titleMedium) }
        }
        goalSection(
            titleRes = null,
            goals = state.activeGoals,
            today = state.today,
            onGoalClicked = onGoalClicked,
        )
        val allocatedAccounts = state.allocatedAccounts.filter { state.accountFilterId == null || it.accountId == state.accountFilterId }
        if (allocatedAccounts.isNotEmpty()) {
            item {
                UnallocatedCard(
                    allocations = allocatedAccounts,
                    accounts = state.accounts,
                )
            }
        }

        goalSection(
            titleRes = R.string.goal_section_paused,
            goals = state.pausedGoals,
            today = state.today,
            onGoalClicked = onGoalClicked,
        )
        goalSection(
            titleRes = R.string.goal_section_completed,
            goals = state.completedGoals,
            today = state.today,
            onGoalClicked = onGoalClicked,
        )

        if (state.visibleGoals.isEmpty() && !state.isLoading && state.errorMessage == null) {
            item { GoalsEmptyCard(onAdd = onAdd) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.goalSection(
    titleRes: Int?,
    goals: List<GoalSummary>,
    today: LocalDate,
    onGoalClicked: (GoalSummary) -> Unit,
) {
    if (goals.isEmpty()) return
    if (titleRes != null) {
        item { SectionHeader(title = stringResource(titleRes)) }
    }
    items(items = goals, key = { it.id }) { goal ->
        GoalCard(goal = goal, progress = goal.progress(today), onClick = { onGoalClicked(goal) })
    }
}

@Composable
private fun GoalCard(
    goal: GoalSummary,
    progress: GoalProgress,
    onClick: () -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(
                    icon = categoryIcon(goal.icon),
                    contentDescription = null,
                    color = categoryColor(goal.color),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = goal.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = goal.fundingModeLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
                if (goal.status == GoalStatus.COMPLETED) {
                    NeutralPill(text = stringResource(R.string.goal_section_completed))
                } else if (progress.reached) {
                    NeutralPill(text = stringResource(R.string.goal_state_reached))
                }
            }
            BudgetProgressBar(
                fraction = progressFraction(progress.savedCents, goal.targetAmountCents),
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.goal_saved_of_target,
                        formatEuroCents(progress.savedCents),
                        formatEuroCents(goal.targetAmountCents),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!progress.reached && goal.status == GoalStatus.ACTIVE) {
                    Text(
                        text = stringResource(
                            R.string.goal_remaining,
                            formatEuroCents(progress.remainingCents),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
            }
            paceText(goal, progress)?.let { pace ->
                Text(
                    text = pace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.overdue) FinanceTheme.colors.debt else FinanceTheme.colors.mutedText,
                )
            }
        }
    }
}

@Composable
private fun UnallocatedCard(
    allocations: List<AccountAllocation>,
    accounts: List<AccountSummary>,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.goal_unallocated_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.goal_unallocated_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.mutedText,
            )
            allocations.forEach { allocation ->
                val name = accounts.firstOrNull { it.id == allocation.accountId }?.name.orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatEuroCents(allocation.unallocatedCents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (allocation.unallocatedCents < 0) {
                            FinanceTheme.colors.debt
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalsEmptyCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.goal_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.goal_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceTheme.colors.mutedText,
            )
            PrimaryButton(text = stringResource(R.string.goal_list_add), onClick = onAdd)
        }
    }
}

@Composable
private fun GoalDetailSheet(
    goal: GoalSummary,
    progress: GoalProgress,
    allocations: List<GoalAllocation>,
    accountAllocation: AccountAllocation?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onStatusChange: (GoalStatus) -> Unit,
    onAddAllocation: () -> Unit,
    onRelease: () -> Unit,
    detail: GoalDetailState,
    accountAllocations: Map<String, AccountAllocation>,
    onRetry: () -> Unit,
    onEditAllocation: (GoalAllocation) -> Unit,
    onDeleteAllocation: (GoalAllocation) -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.84f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = goal.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = goal.fundingModeLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceTheme.colors.mutedText,
            )
            BudgetProgressBar(
                fraction = progressFraction(progress.savedCents, goal.targetAmountCents),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    R.string.goal_saved_of_target,
                    formatEuroCents(progress.savedCents),
                    formatEuroCents(goal.targetAmountCents),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!progress.reached && goal.status == GoalStatus.ACTIVE) {
                Text(
                    text = stringResource(
                        R.string.goal_remaining,
                        formatEuroCents(progress.remainingCents),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinanceTheme.colors.mutedText,
                )
            }
            paceText(goal, progress)?.let { pace ->
                InlineBanner(
                    kind = if (progress.overdue) BannerKind.Alert else BannerKind.Info,
                    text = pace,
                )
            }
            if (goal.status != GoalStatus.ACTIVE) {
                InlineBanner(kind = BannerKind.Info, text = stringResource(R.string.goal_inactive_explainer))
            }
            detail.errorRes?.let {
                InlineBanner(kind = BannerKind.Error, text = stringResource(it), actionLabel = stringResource(R.string.common_retry), onAction = onRetry)
            }
            if (detail.isLoading) CircularProgressIndicator()
            detail.reservations.filterValues { it > 0L }.keys.forEach { accountId ->
                accountAllocations[accountId]?.takeIf { it.unallocatedCents < 0 }?.let { shortage ->
                    InlineBanner(kind = BannerKind.Alert, text = stringResource(
                        R.string.goal_shortfall, allocations.first { it.accountId == accountId }.accountName,
                        formatEuroCents(-shortage.unallocatedCents),
                    ))
                }
            }
            if (goal.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT) {
                Text(
                    text = stringResource(
                        R.string.goal_dedicated_explainer,
                        goal.accountName.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.mutedText,
                )
            } else {
                accountAllocation?.let {
                    Text(
                        text = stringResource(
                            R.string.goal_account_unallocated,
                            goal.accountName.orEmpty(),
                            formatEuroCents(it.unallocatedCents),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
                if (goal.status == GoalStatus.ACTIVE) {
                    PrimaryButton(text = stringResource(R.string.goal_allocation_add), onClick = onAddAllocation, modifier = Modifier.fillMaxWidth())
                }
                SecondaryButton(text = stringResource(R.string.goal_release), onClick = onRelease, modifier = Modifier.fillMaxWidth(), enabled = progress.savedCents > 0)
                SectionHeader(title = stringResource(R.string.goal_allocations_title))
                if (allocations.isEmpty() && !detail.isLoading) {
                    Text(
                        text = stringResource(R.string.goal_allocations_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
                allocations.forEach { allocation ->
                    AllocationRow(
                        allocation = allocation,
                        onEdit = { onEditAllocation(allocation) },
                        onDelete = { onDeleteAllocation(allocation) },
                    )
                }

            }
            Text(stringResource(R.string.goal_status_change_explainer), style = MaterialTheme.typography.bodySmall, color = FinanceTheme.colors.mutedText)
            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryButton(
                    text = stringResource(goal.status.nextStatusLabel()),
                    onClick = { onStatusChange(goal.status.next()) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.goal_edit),
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                )
            }
            if (goal.status != GoalStatus.COMPLETED) {
                SecondaryButton(
                    text = stringResource(R.string.goal_mark_completed),
                    onClick = { onStatusChange(GoalStatus.COMPLETED) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AllocationRow(
    allocation: GoalAllocation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatCompactDate(allocation.date),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = allocation.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.mutedText,
                )
            }
            Text(
                text = formatEuroCents(allocation.amountCents),
                style = MaterialTheme.typography.bodyMedium,
                color = if (allocation.amountCents < 0) {
                    FinanceTheme.colors.mutedText
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            TextButton(onClick = onDelete) {
                Text(text = stringResource(R.string.common_archive))
            }
        }
    }
}

@Composable
private fun GoalFormSheet(
    form: GoalFormState,
    accounts: List<AccountSummary>,
    dedicatedAccountIds: Set<String>,
    onFormChange: (GoalFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.84f) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (form.id == null) R.string.goal_new_title else R.string.goal_edit_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                form.errorMessage?.let {
                    InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_goal)
                }
                if (form.errorField == null && form.errorRes != null) {
                    InlineBanner(kind = BannerKind.Error, text = stringResource(form.errorRes))
                }

                val nameError = form.errorField == GoalFormField.NAME
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text(text = stringResource(R.string.goal_field_name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError && form.errorRes != null) {
                        { Text(text = stringResource(form.errorRes)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = nextFieldKeyboardActions(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().scrollToWhen(nameError),
                )

                val targetError = form.errorField == GoalFormField.TARGET
                OutlinedTextField(
                    value = form.target,
                    onValueChange = { onFormChange(form.copy(target = it)) },
                    label = { Text(text = stringResource(R.string.goal_field_target)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    isError = targetError,
                    supportingText = if (targetError && form.errorRes != null) {
                        { Text(text = stringResource(form.errorRes)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = doneKeyboardActions(onSave),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().scrollToWhen(targetError),
                )

                GoalFundingModeSelector(
                    selected = form.fundingMode,
                    onSelect = { mode ->
                        onFormChange(
                            form.copy(
                                fundingMode = mode,
                                accountId = form.accountId.takeIf { mode != GoalFundingMode.DEDICATED_ACCOUNT || it !in dedicatedAccountIds },
                            ),
                        )
                    },
                )

                val accountError = form.errorField == GoalFormField.ACCOUNT
                val accountOptions = buildList {
                    if (form.fundingMode == GoalFundingMode.ALLOCATIONS) {
                        add(SelectOption(id = null, label = stringResource(R.string.goal_account_none)))
                    }
                    accounts.forEach { account ->
                        add(
                            SelectOption(
                                id = account.id,
                                label = account.name,
                                enabled = account.id !in dedicatedAccountIds,
                            ),
                        )
                    }
                }
                FormSelect(
                    label = stringResource(
                        if (form.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT) {
                            R.string.goal_field_dedicated_account
                        } else {
                            R.string.goal_field_default_account
                        },
                    ),
                    options = accountOptions,
                    selectedId = form.accountId,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                    placeholder = stringResource(R.string.goal_account_none),
                    modifier = Modifier.scrollToWhen(accountError),
                    isError = accountError,
                    supportingText = if (accountError && form.errorRes != null) {
                        stringResource(form.errorRes)
                    } else {
                        stringResource(
                            if (form.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT) {
                                R.string.goal_dedicated_hint
                            } else {
                                R.string.goal_allocations_hint
                            },
                        )
                    },
                )

                TextButton(onClick = { onFormChange(form.copy(showOptional = !form.showOptional)) }) {
                    Text(stringResource(R.string.goal_optional_options))
                }
                if (form.showOptional) {
                    FormDatePicker(
                        label = stringResource(R.string.goal_field_target_date),
                        date = form.targetDate,
                        onDateChange = { onFormChange(form.copy(targetDate = it)) },
                        onClear = { onFormChange(form.copy(targetDate = "")) },
                    )

                    ColorPickerRow(
                        label = stringResource(R.string.goal_field_color),
                        selectedHex = form.color.ifBlank { null },
                        onSelect = { onFormChange(form.copy(color = it)) },
                    )
                    IconPickerRow(
                        label = stringResource(R.string.goal_field_icon),
                        options = CategoryIconPalette,
                        selectedKey = form.icon.ifBlank { null },
                        onSelect = { onFormChange(form.copy(icon = it)) },
                    )
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = { onFormChange(form.copy(notes = it)) },
                        label = { Text(text = stringResource(R.string.goal_field_notes)) },
                        minLines = 2,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )

                }
            }
            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (form.id != null) {
                    DestructiveButton(
                        text = stringResource(R.string.common_archive),
                        onClick = onDelete,
                        enabled = !form.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(
                        if (form.isSaving) R.string.goal_saving else if (form.id == null) R.string.goal_save_new else R.string.goal_save_changes,
                    ),
                    onClick = onSave,
                    enabled = !form.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GoalFundingModeSelector(
    selected: GoalFundingMode,
    onSelect: (GoalFundingMode) -> Unit,
) {
    com.gestorfinances.app.ui.common.SegmentedControl(
        options = listOf(GoalFundingMode.ALLOCATIONS, GoalFundingMode.DEDICATED_ACCOUNT),
        selected = selected,
        label = {
            stringResource(
                when (it) {
                    GoalFundingMode.ALLOCATIONS -> R.string.goal_mode_allocations
                    GoalFundingMode.DEDICATED_ACCOUNT -> R.string.goal_mode_dedicated
                },
            )
        },
        onSelect = onSelect,
    )
}

@Composable
private fun AllocationFormSheet(
    form: AllocationFormState,
    accounts: List<AccountSummary>,
    accountAllocations: Map<String, AccountAllocation>,
    reservations: Map<String, Long>,
    editingAllocation: GoalAllocation?,
    onFormChange: (AllocationFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onConfirmOverAllocation: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.84f) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (form.release) {
                            R.string.goal_release
                        } else if (form.id == null) {
                            R.string.goal_allocation_new_title
                        } else {
                            R.string.goal_allocation_edit_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.goal_allocation_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.mutedText,
                )
                form.errorMessage?.let {
                    InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_goal_allocation)
                }
                if (form.errorField == null && form.errorRes != null) {
                    InlineBanner(kind = BannerKind.Error, text = stringResource(form.errorRes))
                }
                form.overAllocation?.let { warning ->
                    InlineBanner(
                        kind = BannerKind.Alert,
                        text = stringResource(
                            R.string.goal_over_allocation_warning,
                            formatEuroCents(warning.availableCents),
                            formatEuroCents(warning.excessCents),
                        ),
                        actionLabel = stringResource(R.string.goal_over_allocation_confirm),
                        onAction = onConfirmOverAllocation,
                        modifier = Modifier.scrollToWhen(true),
                    )
                }

                if (form.id != null) {
                    com.gestorfinances.app.ui.common.SegmentedControl(
                        options = listOf(false, true), selected = form.release,
                        label = { stringResource(if (it) R.string.goal_release else R.string.goal_allocation_add) },
                        onSelect = { onFormChange(form.copy(release = it)) },
                    )
                }
                val accountError = form.errorField == AllocationFormField.ACCOUNT
                FormSelect(
                    label = stringResource(R.string.goal_field_allocation_account),
                    options = accounts.map { account ->
                        SelectOption(id = account.id, label = account.name,
                            enabled = !form.release || (reservations[account.id] ?: 0L) > 0 || editingAllocation?.accountId == account.id)
                    },
                    selectedId = form.accountId,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                    modifier = Modifier.scrollToWhen(accountError),
                    isError = accountError,
                    supportingText = if (accountError && form.errorRes != null) {
                        stringResource(form.errorRes)
                    } else {
                        form.accountId?.let { accountId ->
                            val replaced = editingAllocation?.takeIf { it.accountId == accountId }?.amountCents ?: 0L
                            val amount = if (form.release) (reservations[accountId] ?: 0L) - replaced
                                else (accountAllocations[accountId]?.unallocatedCents ?: 0L) + replaced
                            stringResource(if (form.release) R.string.goal_available_release else R.string.goal_allocation_available, formatEuroCents(amount))
                        }
                    },
                )

                val amountError = form.errorField == AllocationFormField.AMOUNT
                OutlinedTextField(
                    value = form.amount,
                    onValueChange = { onFormChange(form.copy(amount = it)) },
                    label = { Text(text = stringResource(R.string.goal_field_allocation_amount)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    isError = amountError,
                    supportingText = if (amountError && form.errorRes != null) {
                        { Text(text = stringResource(form.errorRes)) }
                    } else {
                        { Text(text = stringResource(R.string.goal_allocation_amount_hint)) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = doneKeyboardActions(onSave),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().scrollToWhen(amountError),
                )

                val dateError = form.errorField == AllocationFormField.DATE
                FormDatePicker(
                    label = stringResource(R.string.goal_field_allocation_date),
                    date = form.date,
                    onDateChange = { onFormChange(form.copy(date = it)) },
                    modifier = Modifier.scrollToWhen(dateError),
                    isError = dateError,
                    supportingText = if (dateError && form.errorRes != null) {
                        stringResource(form.errorRes)
                    } else null,
                )

                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = { Text(text = stringResource(R.string.goal_field_notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

            }
            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(if (form.isSaving) R.string.goal_saving else R.string.common_save),
                    onClick = onSave,
                    enabled = !form.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GoalSummary.fundingModeLabel(): String =
    when (fundingMode) {
        GoalFundingMode.DEDICATED_ACCOUNT -> stringResource(
            R.string.goal_mode_dedicated_with_account,
            accountName.orEmpty(),
        )
        GoalFundingMode.ALLOCATIONS -> accountName
            ?.let { stringResource(R.string.goal_mode_allocations_with_account, it) }
            ?: stringResource(R.string.goal_mode_allocations)
    }

@Composable
private fun paceText(goal: GoalSummary, progress: GoalProgress): String? {
    if (goal.targetDate == null || progress.reached || goal.status != GoalStatus.ACTIVE) return null
    val targetDate = formatCompactDate(goal.targetDate)
    return if (progress.overdue) {
        stringResource(R.string.goal_pace_overdue, targetDate, formatEuroCents(progress.remainingCents))
    } else {
        stringResource(
            R.string.goal_pace,
            formatEuroCents(progress.monthlyPaceCents ?: 0),
            progress.monthsRemaining ?: 0,
            targetDate,
        )
    }
}

private fun progressFraction(savedCents: Long, targetCents: Long): Float =
    if (targetCents <= 0) 0f else (savedCents.toFloat() / targetCents.toFloat()).coerceIn(0f, 1f)

private fun GoalStatus.next(): GoalStatus =
    when (this) {
        GoalStatus.ACTIVE -> GoalStatus.PAUSED
        GoalStatus.PAUSED, GoalStatus.COMPLETED -> GoalStatus.ACTIVE
    }

private fun GoalStatus.nextStatusLabel(): Int =
    when (this) {
        GoalStatus.ACTIVE -> R.string.goal_pause
        GoalStatus.PAUSED, GoalStatus.COMPLETED -> R.string.goal_resume
    }

/** Values a dismiss guard compares; validation state must not count as an edit. */
private fun GoalFormState.compareValues(): List<Any?> =
    listOf(name, target, targetDate, accountId, fundingMode, icon, color, notes)

private fun AllocationFormState.compareValues(): List<Any?> =
    listOf(accountId, date, amount, notes, release)
