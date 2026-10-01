package com.gestorfinances.app.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.GoalAllocation
import com.gestorfinances.app.data.repository.GoalStatus
import com.gestorfinances.app.data.repository.GoalSummary
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.domain.rules.GoalProgress
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.LocalDate

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun goalsViewModel(appContainer: AppContainer): GoalsViewModel = viewModel {
    GoalsViewModel(
        goalRepository = appContainer.goalRepository,
        accountRepository = appContainer.accountRepository,
    )
}

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    viewModel: GoalsViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenDetail: (GoalSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    GoalsContent(
        onBack = onBack,
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onOpenDetail = onOpenDetail,
        onRetry = viewModel::onScreenShown,
    )

    GoalFormSheet(state = state, viewModel = viewModel)
}

@Composable
private fun GoalsContent(
    onBack: () -> Unit,
    state: GoalsUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onOpenDetail: (GoalSummary) -> Unit,
    onRetry: () -> Unit,
) {
    ListPage(
        title = stringResource(R.string.goal_list_title),
        onBack = onBack,
        addLabel = stringResource(R.string.goal_list_add),
        onAdd = onAdd,
        modifier = modifier,
    ) {
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
        if (state.isLoading) return@ListPage
        if (state.goals.isEmpty()) {
            if (state.errorMessage == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = stringResource(R.string.goal_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.goal_empty_body),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            return@ListPage
        }

        item { GoalsHero(state) }
        listOf(
            R.string.goal_section_active to state.activeGoals,
            R.string.goal_section_paused to state.pausedGoals,
            R.string.goal_section_completed to state.completedGoals,
        ).forEach { (titleRes, goals) ->
            if (goals.isEmpty()) return@forEach
            item(key = "section-$titleRes") { SectionHeader(title = stringResource(titleRes)) }
            item(key = "rows-$titleRes") {
                // One item, so the rows sit flush and read as one list between their dividers.
                Column {
                    goals.forEachIndexed { index, goal ->
                        GoalRow(
                            goal = goal,
                            today = state.today,
                            fundingAccountNames = state.fundingAccounts[goal.id].orEmpty()
                                .mapNotNull { id -> state.accounts.firstOrNull { it.id == id }?.name },
                            isLast = index == goals.lastIndex,
                            onOpen = { onOpenDetail(goal) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the active goals hold on the forest hero against what they aim for, what is still missing,
 * what is left unreserved where they save, and the next goal with a date.
 */
@Composable
private fun GoalsHero(state: GoalsUiState) {
    val colors = FinanceTheme.colors
    val active = state.activeGoals
    val savedCents = active.sumOf { it.savedCents }
    val targetCents = active.sumOf { it.targetAmountCents }
    val next = active
        .filter { it.targetDate != null && it.remainingCents > 0L }
        .minByOrNull { it.targetDate.orEmpty() }
    ListHero(
        eyebrow = stringResource(R.string.goal_hero_reserved),
        cents = savedCents,
        watermark = Icons.Outlined.Savings,
    ) {
        HeroCaption(
            text = pluralStringResource(R.plurals.goal_hero_caption, active.size, formatEuroCents(targetCents), active.size),
        )
        if (targetCents > 0L) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(heroTint(colors.heroOnSurface, 0.18f), RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction(savedCents, targetCents))
                        .background(colors.heroIncome, RoundedCornerShape(50)),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStatBox(stringResource(R.string.goal_hero_remaining), active.sumOf { it.remainingCents }, colors.heroOnSurface)
            if (state.accountAllocations.values.any { it.allocatedCents != 0L }) {
                val free = state.freeInReservedAccountsCents
                HeroStatBox(stringResource(R.string.goal_hero_free), free, if (free < 0L) colors.heroDebt else colors.heroIncome)
            }
        }
        next?.let { goal ->
            val progress = goal.progress(state.today)
            Spacer(modifier = Modifier.height(12.dp))
            HeroCaption(
                text = if (progress.overdue) {
                    stringResource(R.string.goal_hero_next_overdue, goal.name, formatCompactDate(goal.targetDate.orEmpty()))
                } else {
                    stringResource(
                        R.string.goal_hero_next,
                        goal.name,
                        formatEuroCents(progress.monthlyPaceCents ?: 0L),
                        formatCompactDate(goal.targetDate.orEmpty()),
                    )
                },
            )
        }
    }
}

/** A goal: its mark, how it is going in a line, what it holds of its target, and a bar of that. */
@Composable
private fun GoalRow(
    goal: GoalSummary,
    today: LocalDate,
    fundingAccountNames: List<String>,
    isLast: Boolean,
    onOpen: () -> Unit,
) {
    val progress = goal.progress(today)
    EntityListRow(
        leading = { IdentityIconTile(icon = goalIcon(goal.icon), color = categoryColor(goal.color)) },
        title = goal.name,
        subtitle = goalLine(goal, progress, fundingAccountNames),
        footer = {
            BudgetProgressBar(
                fraction = progressFraction(goal.savedCents, goal.targetAmountCents),
                color = themedIdentityColor(categoryColor(goal.color)),
            )
        },
        isLast = isLast,
        onClick = onOpen,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = goal.savedCents,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.goal_row_of_target, formatEuroCents(goal.targetAmountCents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.mutedText,
                )
            }
        },
    )
}

/** The one line that says how an active goal is going, or where a quieter one keeps its money. */
@Composable
private fun goalLine(goal: GoalSummary, progress: GoalProgress, fundingAccountNames: List<String>): String? =
    when {
        goal.status == GoalStatus.COMPLETED -> null
        progress.reached -> stringResource(R.string.goal_state_reached)
        goal.status == GoalStatus.ACTIVE && goal.targetDate != null && progress.overdue ->
            stringResource(R.string.goal_row_overdue, formatCompactDate(goal.targetDate))
        goal.status == GoalStatus.ACTIVE && goal.targetDate != null ->
            stringResource(R.string.goal_row_pace, formatEuroCents(progress.monthlyPaceCents ?: 0L), formatCompactDate(goal.targetDate))
        goal.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT -> goal.accountName
        fundingAccountNames.isNotEmpty() -> fundingAccountNames.joinToString(", ")
        else -> stringResource(R.string.goal_row_no_reservations)
    }

/** The goal create/edit sheet over whichever goal page opened it. */
@Composable
internal fun GoalFormSheet(state: GoalsUiState, viewModel: GoalsViewModel) {
    var appearanceOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-goal" },
        changed = { initial, current -> initial.compareValues() != current.compareValues() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) R.string.goal_new_title else R.string.goal_edit_title) },
        saveLabel = { stringResource(if (it.id == null) R.string.goal_save_new else R.string.goal_save_changes) },
        saving = { it.isSaving },
    ) { form ->
        val onFormChange = viewModel::onFormChanged
        val dedicatedAccountIds = state.dedicatedAccountIds - setOfNotNull(state.goals.firstOrNull { it.id == form.id }?.accountId)
        val errorText = form.errorRes?.let { stringResource(it) }
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_goal)
        }
        if (form.errorField == null && errorText != null) {
            InlineBanner(kind = BannerKind.Error, text = errorText)
        }
        EntityFormHeader(
            icon = goalIcon(form.icon.ifBlank { null }),
            color = categoryColor(form.color.ifBlank { null }),
            name = form.name,
            onNameChange = { onFormChange(form.copy(name = it)) },
            nameLabel = stringResource(R.string.goal_field_name),
            onTileClick = { appearanceOpen = !appearanceOpen },
            nameError = errorText?.takeIf { form.errorField == GoalFormField.NAME },
        )
        EntityAppearancePickers(
            open = appearanceOpen,
            colorHex = form.color.ifBlank { null },
            onColor = { onFormChange(form.copy(color = it)) },
            iconOptions = CategoryIconPalette,
            iconKey = form.icon.ifBlank { null },
            onIcon = { onFormChange(form.copy(icon = it)) },
        )
        run {
            val targetError = form.errorField == GoalFormField.TARGET
            OutlinedTextField(
                value = form.target,
                onValueChange = { onFormChange(form.copy(target = it)) },
                label = { Text(text = stringResource(R.string.goal_field_target)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                isError = targetError,
                supportingText = errorText?.takeIf { targetError }?.let { { Text(text = it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = doneKeyboardActions(viewModel::onSaveClicked),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().scrollToWhen(targetError),
            )
            // The date sets the monthly pace, so it sits beside the amount rather than behind details.
            FormDatePicker(
                label = stringResource(R.string.goal_field_target_date),
                date = form.targetDate,
                onDateChange = { onFormChange(form.copy(targetDate = it)) },
                onClear = { onFormChange(form.copy(targetDate = "")) },
            )
        }
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
        val dedicated = form.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT
        val accountError = form.errorField == GoalFormField.ACCOUNT
        FormSelect(
            label = stringResource(if (dedicated) R.string.goal_field_dedicated_account else R.string.goal_field_default_account),
            options = buildList {
                if (!dedicated) add(SelectOption(id = null, label = stringResource(R.string.goal_account_none)))
                state.accounts.forEach { account ->
                    add(SelectOption(id = account.id, label = account.name, enabled = account.id !in dedicatedAccountIds))
                }
            },
            selectedId = form.accountId,
            onSelect = { onFormChange(form.copy(accountId = it)) },
            placeholder = stringResource(R.string.goal_account_none),
            modifier = Modifier.scrollToWhen(accountError),
            isError = accountError,
            supportingText = errorText?.takeIf { accountError }
                ?: stringResource(if (dedicated) R.string.goal_dedicated_hint else R.string.goal_allocations_hint),
        )
        // Notes already written stay in view.
        val notesShown = form.showOptional || form.notes.isNotBlank()
        FormDisclosure(open = notesShown, onToggle = { onFormChange(form.copy(showOptional = !form.showOptional)) }) {
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

/** The reserve/release sheet over a goal page, guarding unsaved changes. */
@Composable
internal fun AllocationFormHost(
    form: AllocationFormState,
    state: GoalsUiState,
    viewModel: GoalsViewModel,
    onDelete: (GoalAllocation) -> Unit,
) {
    val requestFormDismissal = rememberFormDismissGuard(
        formKey = form.id ?: "new-allocation",
        currentValue = form,
        hasMeaningfulChanges = { initial, current -> initial.compareValues() != current.compareValues() },
        onDiscard = viewModel::onAllocationFormDismissed,
    )
    BackHandler(onBack = requestFormDismissal)
    val editing = state.detail?.allocations?.firstOrNull { it.id == form.id }
    AllocationFormSheet(
        form = form,
        goal = state.goals.firstOrNull { it.id == form.goalId },
        accounts = state.accounts.filter { it.id !in state.dedicatedAccountIds },
        accountAllocations = state.accountAllocations,
        reservations = state.detail?.reservations.orEmpty(),
        editingAllocation = editing,
        onFormChange = viewModel::onAllocationFormChanged,
        onDismiss = requestFormDismissal,
        onSave = { viewModel.onSaveAllocationClicked() },
        onConfirmOverAllocation = { viewModel.onSaveAllocationClicked(confirmOverAllocation = true) },
        onDelete = editing?.let { { onDelete(it) } },
    )
}

/**
 * Reserving or releasing money for a goal, in the movement form's shape: which of the two, the
 * goal's tile and name over the amount typed in place, what the account can still take or give
 * back, the account and date, then the one action.
 */
@Composable
private fun AllocationFormSheet(
    form: AllocationFormState,
    goal: GoalSummary?,
    accounts: List<AccountSummary>,
    accountAllocations: Map<String, AccountAllocation>,
    reservations: Map<String, Long>,
    editingAllocation: GoalAllocation?,
    onFormChange: (AllocationFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onConfirmOverAllocation: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val finance = FinanceTheme.colors
    val errorText = form.errorRes?.let { stringResource(it) }
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.84f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            form.errorMessage?.let {
                InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_goal_allocation)
            }
            if (form.errorField == null && errorText != null) {
                InlineBanner(kind = BannerKind.Error, text = errorText)
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

            SegmentedControl(
                options = listOf(false, true),
                selected = form.release,
                label = { stringResource(if (it) R.string.goal_action_release else R.string.goal_action_reserve) },
                onSelect = { onFormChange(form.copy(release = it)) },
            )

            // What the chosen account can still take (or give back), under the amount it limits.
            val available = form.accountId?.let { accountId ->
                val replaced = editingAllocation?.takeIf { it.accountId == accountId }?.amountCents ?: 0L
                val cents = if (form.release) {
                    (reservations[accountId] ?: 0L) - replaced
                } else {
                    (accountAllocations[accountId]?.unallocatedCents ?: 0L) + replaced
                }
                stringResource(
                    if (form.release) R.string.goal_available_release else R.string.goal_allocation_available,
                    formatEuroCents(cents),
                )
            }
            val amountError = form.errorField == AllocationFormField.AMOUNT
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MovementFormHeader(
                    icon = goalIcon(goal?.icon),
                    iconColor = categoryColor(goal?.color),
                    title = goal?.name ?: stringResource(R.string.goal_allocation_new_title),
                    titleIsPlaceholder = goal == null,
                    amount = form.amount,
                    onAmountChange = { onFormChange(form.copy(amount = it)) },
                    amountColor = MaterialTheme.colorScheme.onSurface,
                    amountError = errorText.takeIf { amountError },
                    modifier = Modifier.scrollToWhen(amountError),
                )
                Text(
                    text = available ?: stringResource(R.string.goal_allocation_supporting),
                    color = finance.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val accountError = form.errorField == AllocationFormField.ACCOUNT
                FormSelect(
                    label = stringResource(R.string.goal_field_allocation_account),
                    options = accounts.map { account ->
                        SelectOption(
                            id = account.id,
                            label = account.name,
                            enabled = !form.release || (reservations[account.id] ?: 0L) > 0 || editingAllocation?.accountId == account.id,
                        )
                    },
                    selectedId = form.accountId,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                    modifier = Modifier.weight(1f).scrollToWhen(accountError),
                    isError = accountError,
                    supportingText = errorText.takeIf { accountError },
                )
                val dateError = form.errorField == AllocationFormField.DATE
                FormDatePicker(
                    label = stringResource(R.string.goal_field_allocation_date),
                    date = form.date,
                    onDateChange = { onFormChange(form.copy(date = it)) },
                    modifier = Modifier.weight(1f).scrollToWhen(dateError),
                    isError = dateError,
                    supportingText = errorText.takeIf { dateError },
                )
            }

            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it)) },
                label = { Text(text = stringResource(R.string.goal_field_notes)) },
                minLines = 2,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            if (available != null) {
                Text(
                    text = stringResource(R.string.goal_allocation_supporting),
                    color = finance.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) {
                    DestructiveButton(
                        text = stringResource(R.string.common_archive),
                        onClick = onDelete,
                        enabled = !form.isSaving,
                    )
                }
                PrimaryButton(
                    text = stringResource(
                        when {
                            form.isSaving -> R.string.goal_saving
                            form.release -> R.string.goal_action_release
                            else -> R.string.goal_action_reserve
                        },
                    ),
                    onClick = onSave,
                    enabled = !form.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A goal's chosen icon, or a piggy bank when it has none. */
internal fun goalIcon(key: String?): ImageVector = key?.let(::categoryIcon) ?: Icons.Outlined.Savings

internal fun progressFraction(savedCents: Long, targetCents: Long): Float =
    if (targetCents <= 0) 0f else (savedCents.toFloat() / targetCents.toFloat()).coerceIn(0f, 1f)

/** Values a dismiss guard compares; validation state must not count as an edit. */
private fun GoalFormState.compareValues(): List<Any?> =
    listOf(name, target, targetDate, accountId, fundingMode, icon, color, notes)

private fun AllocationFormState.compareValues(): List<Any?> =
    listOf(accountId, date, amount, notes, release)
