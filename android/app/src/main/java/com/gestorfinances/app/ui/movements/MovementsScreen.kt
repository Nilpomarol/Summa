package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.DateGroupHeader
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun MovementsScreen(
    viewModel: MovementsViewModel,
    modifier: Modifier = Modifier,
    showDialogs: Boolean = true,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    MovementsContent(
        state = state,
        modifier = modifier,
        onFiltersChange = viewModel::onFiltersChanged,
        onClearFilters = viewModel::onClearFiltersClicked,
        onDetail = viewModel::onDetailClicked,
        onAdd = viewModel::onAddClicked,
    )

    if (showDialogs) {
        MovementDialogs(
            state = state,
            viewModel = viewModel,
        )
    }
}

@Composable
fun MovementDialogHost(
    viewModel: MovementsViewModel,
) {
    val state by viewModel.state.collectAsState()

    MovementDialogs(
        state = state,
        viewModel = viewModel,
    )
}

@Composable
private fun MovementDialogs(
    state: MovementsUiState,
    viewModel: MovementsViewModel,
) {
    state.detailMovement?.let { movement ->
        MovementDetailDialog(
            movement = movement,
            categoriesById = state.categories.associateBy { it.id },
            refunds = state.detailRefunds,
            onDismiss = viewModel::onDetailDismissed,
            onEdit = { viewModel.onEditClicked(movement) },
            onArchive = { viewModel.onArchiveClicked(movement) },
            onAddRefund = { viewModel.onAddRefundClicked(movement) },
        )
    }

    state.refundForm?.let { form ->
        RefundFormDialog(
            form = form,
            accounts = state.accounts,
            categories = state.categories,
            onFormChange = viewModel::onRefundFormChanged,
            onDismiss = viewModel::onRefundDismissed,
            onSave = viewModel::onRefundSaveClicked,
        )
    }

    state.form?.let { form ->
        MovementFormDialog(
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
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
            onOverride = viewModel::onDuplicateOverrideClicked,
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.movement_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.movement_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onArchiveConfirmed) {
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
private fun MovementsContent(
    state: MovementsUiState,
    modifier: Modifier,
    onFiltersChange: (MovementFilters) -> Unit,
    onClearFilters: () -> Unit,
    onDetail: (MovementSummary) -> Unit,
    onAdd: () -> Unit,
) {
    var filtersExpanded by remember { mutableStateOf(false) }
    val visibleMovements = state.visibleMovements
    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }
    val grouped = remember(visibleMovements) {
        visibleMovements.groupBy { it.date }.toList()
    }

    LaunchedEffect(state.filters.hasAdvancedFilters) {
        if (state.filters.hasAdvancedFilters) {
            filtersExpanded = true
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.movement_list_title),
                trailing = {
                    if (state.accounts.isNotEmpty()) {
                        TopBarIconButton(
                            icon = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.movement_filter_title),
                            onClick = { filtersExpanded = !filtersExpanded },
                        )
                    }
                },
            )
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
            }
        }

        if (!state.isLoading && state.accounts.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = state.filters.query,
                    onValueChange = { onFiltersChange(state.filters.copy(query = it)) },
                    placeholder = { Text(text = stringResource(R.string.movement_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                MovementTypeFilterRow(
                    selected = state.filters.type,
                    onSelected = { onFiltersChange(state.filters.copy(type = it)) },
                )
            }
            if (filtersExpanded) {
                item {
                    MovementFiltersCard(
                        filters = state.filters,
                        accounts = state.accounts,
                        categories = state.categories,
                        trips = state.trips,
                        tags = state.tags,
                        onFiltersChange = onFiltersChange,
                        onClearFilters = onClearFilters,
                    )
                }
            }
        }

        if (!state.isLoading && state.accounts.isEmpty()) {
            item {
                InlineBanner(
                    kind = BannerKind.Info,
                    text = stringResource(R.string.movement_no_accounts_body),
                )
            }
        }

        when {
            state.isLoading -> item {
                Text(
                    text = stringResource(R.string.movement_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.movements.isEmpty() && state.accounts.isNotEmpty() -> item {
                EmptyMovementsCard(onAdd = onAdd)
            }
            visibleMovements.isEmpty() && state.accounts.isNotEmpty() -> item {
                NoFilteredMovementsCard(onClearFilters = onClearFilters)
            }
            else -> grouped.forEach { (date, movements) ->
                item(key = "header-$date") {
                    DateGroupHeader(iso = date)
                }
                items(items = movements, key = { it.id }) { movement ->
                    MovementRow(
                        movement = movement,
                        category = movement.categoryId?.let { categoriesById[it] },
                        onClick = { onDetail(movement) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementTypeFilterRow(
    selected: MovementType?,
    onSelected: (MovementType?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FinanceFilterChip(
            selected = selected == null,
            label = stringResource(R.string.movement_filter_all_types),
            onClick = { onSelected(null) },
        )
        phaseOneTypes.forEach { type ->
            FinanceFilterChip(
                selected = selected == type,
                label = type.filterLabel(),
                onClick = { onSelected(type) },
            )
        }
    }
}

@Composable
private fun MovementFiltersCard(
    filters: MovementFilters,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFiltersChange: (MovementFilters) -> Unit,
    onClearFilters: () -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.movement_filter_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearFilters) {
                    Text(text = stringResource(R.string.common_clear_filters))
                }
            }
            filters.errorRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ChipFlowSection(label = stringResource(R.string.movement_filter_account)) {
                FinanceFilterChip(
                    selected = filters.accountId == null,
                    label = stringResource(R.string.movement_filter_all_accounts),
                    onClick = { onFiltersChange(filters.copy(accountId = null)) },
                )
                accounts.forEach { account ->
                    FinanceFilterChip(
                        selected = filters.accountId == account.id,
                        label = account.name,
                        onClick = { onFiltersChange(filters.copy(accountId = account.id)) },
                    )
                }
            }
            ChipFlowSection(label = stringResource(R.string.movement_filter_category)) {
                FinanceFilterChip(
                    selected = filters.categoryId == null && !filters.uncategorizedOnly,
                    label = stringResource(R.string.movement_filter_all_categories),
                    onClick = {
                        onFiltersChange(
                            filters.copy(
                                categoryId = null,
                                uncategorizedOnly = false,
                            ),
                        )
                    },
                )
                FinanceFilterChip(
                    selected = filters.uncategorizedOnly,
                    label = stringResource(R.string.common_no_category),
                    onClick = {
                        onFiltersChange(
                            filters.copy(
                                categoryId = null,
                                uncategorizedOnly = true,
                            ),
                        )
                    },
                )
                categories.forEach { category ->
                    FinanceFilterChip(
                        selected = filters.categoryId == category.id,
                        label = category.name,
                        onClick = {
                            onFiltersChange(
                                filters.copy(
                                    categoryId = category.id,
                                    uncategorizedOnly = false,
                                ),
                            )
                        },
                    )
                }
            }
            ChipFlowSection(label = stringResource(R.string.movement_field_trip)) {
                FinanceFilterChip(
                    selected = filters.tripId == null,
                    label = stringResource(R.string.trip_filter_all),
                    onClick = { onFiltersChange(filters.copy(tripId = null, tagId = null)) },
                )
                trips.forEach { trip ->
                    FinanceFilterChip(
                        selected = filters.tripId == trip.id,
                        label = trip.name,
                        onClick = { onFiltersChange(filters.copy(tripId = trip.id, tagId = null)) },
                    )
                }
            }
            if (filters.tripId != null) {
                ChipFlowSection(label = stringResource(R.string.movement_field_tag)) {
                    FinanceFilterChip(
                        selected = filters.tagId == null,
                        label = stringResource(R.string.tag_picker_none),
                        onClick = { onFiltersChange(filters.copy(tagId = null)) },
                    )
                    tags.filter { it.supportsTrip(filters.tripId) }.forEach { tag ->
                        FinanceFilterChip(
                            selected = filters.tagId == tag.id,
                            label = tag.name,
                            onClick = { onFiltersChange(filters.copy(tagId = tag.id)) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filters.dateFrom,
                    onValueChange = { onFiltersChange(filters.copy(dateFrom = it)) },
                    label = { Text(text = stringResource(R.string.movement_filter_date_from)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = filters.dateTo,
                    onValueChange = { onFiltersChange(filters.copy(dateTo = it)) },
                    label = { Text(text = stringResource(R.string.movement_filter_date_to)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MovementRow(
    movement: MovementSummary,
    category: CategoryRecord?,
    onClick: () -> Unit,
) {
    val visual = movement.chipVisual(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = visual.first,
            contentDescription = null,
            color = visual.second,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = movement.title(),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (movement.isShared) {
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = stringResource(R.string.movement_shared_badge),
                        tint = FinanceTheme.colors.mutedText,
                        modifier = Modifier.size(15.dp),
                    )
                }
                if (movement.isOneTime) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = stringResource(R.string.movement_one_time_badge),
                        tint = FinanceTheme.colors.mutedText,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Text(
                text = movement.subtitle(),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        MoneyText(
            cents = movement.signedAmountCents(),
            color = FinanceTheme.colors.amountColor(movement.type),
            style = MaterialTheme.typography.titleMedium,
            signed = movement.type != MovementType.EXPENSE && movement.type != MovementType.TRANSFER,
        )
    }
}

@Composable
private fun EmptyMovementsCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.movement_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onAdd) {
                Text(text = stringResource(R.string.movement_list_add))
            }
        }
    }
}

@Composable
private fun NoFilteredMovementsCard(onClearFilters: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_filter_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.movement_filter_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onClearFilters) {
                Text(text = stringResource(R.string.common_clear_filters))
            }
        }
    }
}

@Composable
private fun MovementDetailDialog(
    movement: MovementSummary,
    categoriesById: Map<String, CategoryRecord>,
    refunds: List<com.gestorfinances.app.data.repository.RefundSummary>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onAddRefund: () -> Unit,
) {
    val category = movement.categoryId?.let { categoriesById[it] }
    val visual = movement.chipVisual(category)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(
                    icon = visual.first,
                    contentDescription = null,
                    color = visual.second,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = movement.title(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    MoneyText(
                        cents = movement.signedAmountCents(),
                        color = FinanceTheme.colors.amountColor(movement.type),
                        style = MaterialTheme.typography.titleLarge,
                        signed = movement.type != MovementType.EXPENSE &&
                            movement.type != MovementType.TRANSFER,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailLine(
                    label = stringResource(R.string.movement_field_type),
                    value = movement.type.label(),
                )
                DetailLine(
                    label = stringResource(R.string.movement_field_date),
                    value = movement.date,
                )
                DetailLine(
                    label = stringResource(R.string.movement_detail_account),
                    value = movement.accountName,
                )
                movement.destinationAccountName?.let {
                    DetailLine(
                        label = stringResource(R.string.movement_detail_destination_account),
                        value = it,
                    )
                }
                if (movement.type != MovementType.TRANSFER && movement.type != MovementType.SETTLEMENT) {
                    DetailLine(
                        label = stringResource(R.string.movement_detail_category),
                        value = movement.categoryName ?: stringResource(R.string.common_no_category),
                    )
                }
                movement.tripName?.let {
                    DetailLine(
                        label = stringResource(R.string.movement_field_trip),
                        value = it,
                    )
                }
                movement.tagName?.let {
                    DetailLine(
                        label = stringResource(R.string.movement_field_tag),
                        value = it,
                    )
                }
                if (movement.type == MovementType.SETTLEMENT) {
                    movement.settlementPersonName?.let { person ->
                        DetailLine(
                            label = stringResource(
                                when (movement.settlementDirection) {
                                    SettlementDirection.PERSON_TO_USER -> R.string.settlement_direction_person_to_user
                                    SettlementDirection.USER_TO_PERSON -> R.string.settlement_direction_user_to_person
                                    null -> R.string.settlement_field_person
                                },
                            ),
                            value = person,
                        )
                    }
                }
                movement.paidByPersonName?.let {
                    DetailLine(
                        label = stringResource(R.string.split_payer_title),
                        value = stringResource(R.string.movement_paid_by_person, it),
                    )
                }
                movement.payee?.let {
                    DetailLine(label = stringResource(R.string.movement_detail_payee), value = it)
                }
                movement.notes?.let {
                    DetailLine(label = stringResource(R.string.movement_detail_notes), value = it)
                }
                if (movement.type == MovementType.EXPENSE) {
                    if (refunds.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.refund_list_title),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        refunds.forEach { refund ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = refund.date,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                MoneyText(
                                    cents = refund.amountCents,
                                    color = FinanceTheme.colors.refund,
                                    style = MaterialTheme.typography.bodyMedium,
                                    signed = true,
                                )
                            }
                        }
                    }
                    PrimaryButton(
                        text = stringResource(R.string.refund_add_title),
                        onClick = onAddRefund,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Text(text = stringResource(R.string.common_edit))
            }
        },
        dismissButton = {
            DestructiveTextButton(onClick = onArchive) {
                Text(text = stringResource(R.string.common_archive))
            }
        },
    )
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RefundFormDialog(
    form: RefundFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    onFormChange: (RefundFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val parsedAmount = parseEuroCents(form.amount, allowNegative = false)
    val isOverRefund = parsedAmount != null && parsedAmount > form.remainingCents

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.refund_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                form.expenseName.takeIf { it.isNotBlank() }?.let {
                    DetailLine(label = stringResource(R.string.refund_field_linked_expense), value = it)
                }
                Text(
                    text = stringResource(R.string.refund_remaining, formatEuroCents(form.remainingCents)),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                form.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                form.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = form.amount,
                    onValueChange = { onFormChange(form.copy(amount = it)) },
                    label = { Text(text = stringResource(R.string.refund_field_amount)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isOverRefund) {
                    InlineBanner(
                        kind = BannerKind.Alert,
                        text = stringResource(R.string.refund_warning_over),
                    )
                }
                if (form.expenseIsShared) {
                    OutlinedTextField(
                        value = form.actualAmount,
                        onValueChange = { onFormChange(form.copy(actualAmount = it)) },
                        label = { Text(text = stringResource(R.string.refund_field_actual)) },
                        prefix = { Text(text = "€") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ChipFlowSection(label = stringResource(R.string.refund_field_account)) {
                    accounts.forEach { account ->
                        FinanceFilterChip(
                            selected = form.accountId == account.id,
                            label = account.name,
                            onClick = { onFormChange(form.copy(accountId = account.id)) },
                        )
                    }
                }
                ChipFlowSection(label = stringResource(R.string.refund_field_category)) {
                    FinanceFilterChip(
                        selected = form.categoryId == null,
                        label = stringResource(R.string.common_no_category),
                        onClick = { onFormChange(form.copy(categoryId = null)) },
                    )
                    categories.filter { it.supports(MovementType.EXPENSE) }.forEach { category ->
                        FinanceFilterChip(
                            selected = form.categoryId == category.id,
                            label = category.name,
                            onClick = { onFormChange(form.copy(categoryId = category.id)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = form.date,
                    onValueChange = { onFormChange(form.copy(date = it)) },
                    label = { Text(text = stringResource(R.string.refund_field_date)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = { Text(text = stringResource(R.string.refund_field_notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(text = stringResource(R.string.refund_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun MovementFormDialog(
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
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onOverride: () -> Unit,
) {
    val sharedEnabled = form.splitEditor != null || (form.existingSplit && !form.removeExistingSplit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.movement_add_title else R.string.movement_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                form.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                form.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (form.duplicateWarning) {
                    InlineBanner(
                        kind = BannerKind.Alert,
                        text = stringResource(R.string.movement_duplicate_warning),
                    )
                }
                SegmentedControl(
                    options = phaseOneTypes,
                    selected = form.type,
                    label = { it.label() },
                    onSelect = { onFormChange(form.copy(type = it)) },
                )
                OutlinedTextField(
                    value = form.amount,
                    onValueChange = { onFormChange(form.copy(amount = it)) },
                    label = { Text(text = stringResource(R.string.movement_field_amount)) },
                    prefix = { Text(text = "€") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.date,
                    onValueChange = { onFormChange(form.copy(date = it)) },
                    label = { Text(text = stringResource(R.string.movement_field_date)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipFlowSection(
                    label = if (form.type == MovementType.TRANSFER) {
                        stringResource(R.string.movement_field_origin_account)
                    } else {
                        stringResource(R.string.movement_field_account)
                    },
                ) {
                    if (accounts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.movement_no_accounts_title),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    accounts.forEach { account ->
                        FinanceFilterChip(
                            selected = form.accountId == account.id,
                            label = account.name,
                            onClick = { onFormChange(form.copy(accountId = account.id)) },
                        )
                    }
                }
                ChipFlowSection(label = stringResource(R.string.movement_field_trip)) {
                    FinanceFilterChip(
                        selected = form.tripId == null,
                        label = stringResource(R.string.trip_filter_all),
                        onClick = { onTripSelected(null) },
                    )
                    trips.forEach { trip ->
                        FinanceFilterChip(
                            selected = form.tripId == trip.id,
                            label = trip.name,
                            onClick = { onTripSelected(trip.id) },
                        )
                    }
                }
                val tagOptions = tags.filter { it.supportsTrip(form.tripId) }
                if (form.tripId == null) {
                    InlineBanner(
                        kind = BannerKind.Info,
                        text = stringResource(R.string.movement_tag_disabled_no_trip),
                    )
                } else {
                    ChipFlowSection(label = stringResource(R.string.movement_field_tag)) {
                        FinanceFilterChip(
                            selected = form.tagId == null,
                            label = stringResource(R.string.tag_picker_none),
                            onClick = { onTagSelected(null) },
                        )
                        tagOptions.forEach { tag ->
                            FinanceFilterChip(
                                selected = form.tagId == tag.id,
                                label = tag.name,
                                onClick = { onTagSelected(tag.id) },
                            )
                        }
                    }
                }
                if (form.type == MovementType.TRANSFER) {
                    ChipFlowSection(
                        label = stringResource(R.string.movement_field_destination_account),
                    ) {
                        accounts.forEach { account ->
                            FinanceFilterChip(
                                selected = form.destinationAccountId == account.id,
                                label = account.name,
                                onClick = { onFormChange(form.copy(destinationAccountId = account.id)) },
                            )
                        }
                    }
                } else {
                    val options = categories.filter { it.supports(form.type) }
                    ChipFlowSection(label = stringResource(R.string.movement_field_category)) {
                        FinanceFilterChip(
                            selected = form.categoryId == null,
                            label = stringResource(R.string.common_no_category),
                            onClick = { onFormChange(form.copy(categoryId = null)) },
                        )
                        options.forEach { category ->
                            FinanceFilterChip(
                                selected = form.categoryId == category.id,
                                label = category.name,
                                onClick = { onFormChange(form.copy(categoryId = category.id)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text(text = stringResource(R.string.movement_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.payee,
                    onValueChange = { onFormChange(form.copy(payee = it)) },
                    label = { Text(text = stringResource(R.string.movement_field_payee)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = { Text(text = stringResource(R.string.movement_field_notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (form.type == MovementType.EXPENSE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = form.isOneTime,
                            onCheckedChange = { onFormChange(form.copy(isOneTime = it)) },
                        )
                        Text(text = stringResource(R.string.movement_field_one_time))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sharedEnabled,
                            onCheckedChange = onSharedToggled,
                        )
                        Column {
                            Text(text = stringResource(R.string.movement_field_shared))
                            Text(
                                text = stringResource(R.string.movement_field_shared_support),
                                color = FinanceTheme.colors.mutedText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (form.splitEditor != null) {
                        SplitEditorCard(
                            splitEditor = form.splitEditor,
                            people = people,
                            amountInput = form.amount,
                            onChange = onSplitEditorChange,
                        )
                    } else if (sharedEnabled) {
                        InlineBanner(
                            kind = BannerKind.Info,
                            text = stringResource(R.string.movement_existing_split_unchanged),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = if (form.duplicateWarning) onOverride else onSave) {
                Text(
                    text = when {
                        form.duplicateWarning -> stringResource(R.string.movement_duplicate_override)
                        form.id == null -> stringResource(R.string.movement_save_new)
                        else -> stringResource(R.string.movement_save_changes)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun MovementSummary.title(): String =
    name ?: payee ?: categoryName ?: type.label()

@Composable
private fun MovementSummary.subtitle(): String =
    when (type) {
        MovementType.TRANSFER -> listOfNotNull(
            stringResource(
                R.string.movement_transfer_accounts,
                accountName,
                destinationAccountName ?: stringResource(R.string.movement_destination_missing),
            ),
            tripName,
            tagName,
        ).joinToString(separator = " · ")
        MovementType.SETTLEMENT ->
            listOfNotNull(settlementContext(), accountName).joinToString(separator = " · ")
        else -> {
            val category = categoryName ?: stringResource(R.string.common_no_category)
            val paidBy = paidByPersonName?.let { stringResource(R.string.movement_paid_by_person, it) }
            listOfNotNull(paidBy, tripName, tagName, category, accountName).joinToString(separator = " · ")
        }
    }

@Composable
private fun MovementSummary.settlementContext(): String? {
    val person = settlementPersonName ?: return null
    return when (settlementDirection) {
        SettlementDirection.PERSON_TO_USER ->
            stringResource(R.string.movement_settlement_person_to_user, person)
        SettlementDirection.USER_TO_PERSON ->
            stringResource(R.string.movement_settlement_user_to_person, person)
        null -> null
    }
}

private fun MovementSummary.signedAmountCents(): Long =
    when (type) {
        MovementType.EXPENSE -> -amountCents
        MovementType.SETTLEMENT ->
            if (settlementDirection == SettlementDirection.USER_TO_PERSON) -amountCents else amountCents
        else -> amountCents
    }

@Composable
private fun MovementSummary.chipVisual(category: CategoryRecord?): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> {
    val finance = FinanceTheme.colors
    return when (type) {
        MovementType.EXPENSE, MovementType.INCOME ->
            if (category != null) {
                categoryIcon(category.icon) to categoryColor(category.color)
            } else if (type == MovementType.INCOME) {
                movementTypeIcon(type) to finance.income
            } else {
                categoryIcon(null) to categoryColor(null)
            }
        MovementType.TRANSFER -> movementTypeIcon(type) to finance.transfer
        MovementType.SETTLEMENT -> movementTypeIcon(type) to finance.settlement
        MovementType.REFUND -> movementTypeIcon(type) to finance.refund
    }
}

@Composable
private fun MovementType.filterLabel(): String =
    when (this) {
        MovementType.EXPENSE -> stringResource(R.string.movement_filter_expenses)
        MovementType.INCOME -> stringResource(R.string.movement_filter_income)
        MovementType.TRANSFER -> stringResource(R.string.movement_filter_transfers)
        MovementType.SETTLEMENT -> stringResource(R.string.movement_type_settlement)
        MovementType.REFUND -> stringResource(R.string.movement_type_refund)
    }

private fun CategoryRecord.supports(type: MovementType): Boolean =
    when (type) {
        MovementType.EXPENSE -> kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH
        MovementType.INCOME -> kind == CategoryKind.INCOME || kind == CategoryKind.BOTH
        else -> false
    }

private fun TagSummary.supportsTrip(tripId: String?): Boolean =
    tripId != null && (this.tripId == null || this.tripId == tripId)

private val phaseOneTypes = listOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.TRANSFER,
)
