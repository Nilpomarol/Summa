package com.gestorfinances.app.ui.movements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.RefundSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.chipVisual
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatLongDate
import com.gestorfinances.app.ui.common.movementTitle
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.signedAmountCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

private data class GridItemData(
    val icon: ImageVector,
    val iconColor: androidx.compose.ui.graphics.Color,
    val label: String,
    val value: String,
)

/**
 * Movement detail page (formerly a bottom sheet). Reached via `AppOverlay.MovementDetail`
 * (MainActivity), since a movement can be viewed from any screen. [onBack] pops that overlay;
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
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val refundForm = state.refundForm
    val movement = state.detailMovement

    if (refundForm != null) {
        // System/gesture back must reveal the movement detail again, not exit the whole
        // AppOverlay.MovementDetail page -- the global BackHandler in MainActivity only pops
        // the overlay, so this nested swap needs its own handler (mirrors TripFormScreen nested
        // in TripDetailScreen, SettlementScreen nested in PersonDetailScreen).
        BackHandler(onBack = viewModel::onRefundDismissed)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            refundForm != null -> RefundFormContent(
                form = refundForm,
                accounts = state.accounts,
                categories = state.categories,
                onFormChange = viewModel::onRefundFormChanged,
                onBack = viewModel::onRefundDismissed,
                onSave = viewModel::onRefundSaveClicked,
            )
            movement != null -> MovementDetailContent(
                movement = movement,
                refunds = state.detailRefunds,
                accounts = state.accounts,
                onBack = onBack,
                onEdit = { onEdit(movement) },
                onArchive = { viewModel.onArchiveClicked(movement) },
                onAddRefund = { viewModel.onAddRefundClicked(movement) },
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
                        viewModel.onArchiveConfirmed(revertDueDate = revertDueDate, onSuccess = onBack)
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

@Composable
private fun MovementDetailContent(
    movement: MovementSummary,
    refunds: List<RefundSummary>,
    accounts: List<AccountSummary>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onAddRefund: () -> Unit,
) {
    val visual = movement.chipVisual()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(onBack = onBack)

        // Header: Icon chip, Title, Large amount
        MovementSheetHeader(
            title = movement.movementTitle(),
            amountCents = movement.signedAmountCents(),
            type = movement.type,
            icon = visual.first,
            iconColor = visual.second,
        )

        HorizontalDivider(color = FinanceTheme.colors.cardBorder)

        if (movement.type == MovementType.REFUND && movement.refundsExpenseArchived) {
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(R.string.refund_orphaned_warning),
            )
        }

        // Grid items data builder. buildList{} is an inline function, so stringResource()
        // calls work directly inside it — no remember{} needed for this cheap, non-lazy list.
        val gridItems = buildList {
            // Date
            add(
                GridItemData(
                    icon = Icons.Outlined.CalendarMonth,
                    iconColor = visual.second,
                    label = stringResource(R.string.movement_field_date),
                    value = formatLongDate(movement.date)
                )
            )

            // Origin Account
            val originAcc = accounts.firstOrNull { it.id == movement.accountId }
            add(
                GridItemData(
                    icon = accountIcon(originAcc?.icon),
                    iconColor = originAcc?.color?.let { categoryColor(it) } ?: visual.second,
                    label = stringResource(R.string.movement_field_account),
                    value = movement.accountName ?: "—"
                )
            )

            // Destination Account for Transfer
            if (movement.type == MovementType.TRANSFER) {
                val destAcc = accounts.firstOrNull { it.id == movement.destinationAccountId }
                add(
                    GridItemData(
                        icon = accountIcon(destAcc?.icon),
                        iconColor = destAcc?.color?.let { categoryColor(it) } ?: visual.second,
                        label = stringResource(R.string.movement_field_destination_account),
                        value = movement.destinationAccountName ?: "—"
                    )
                )
            }

            // Category for non-transfer and non-settlement
            if (movement.type != MovementType.TRANSFER && movement.type != MovementType.SETTLEMENT) {
                add(
                    GridItemData(
                        icon = categoryIcon(movement.categoryIcon),
                        iconColor = categoryColor(movement.categoryColor),
                        label = stringResource(R.string.movement_field_category),
                        value = movement.categoryName ?: stringResource(R.string.common_no_category)
                    )
                )
            }

            // Settlement Person Direction details
            if (movement.type == MovementType.SETTLEMENT) {
                val label = when (movement.settlementDirection) {
                    SettlementDirection.PERSON_TO_USER -> stringResource(R.string.movement_detail_settlement_received_from)
                    SettlementDirection.USER_TO_PERSON -> stringResource(R.string.movement_detail_settlement_paid_to)
                    null -> stringResource(R.string.settlement_field_person)
                }
                add(
                    GridItemData(
                        icon = Icons.Outlined.Handshake,
                        iconColor = visual.second,
                        label = label,
                        value = movement.settlementPersonName ?: "—"
                    )
                )
            }

            // Trip info
            if (movement.tripId != null) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Flight,
                        iconColor = visual.second,
                        label = stringResource(R.string.movement_field_trip),
                        value = movement.tripName ?: "—"
                    )
                )
            }

            // Tag info
            if (movement.tagId != null) {
                add(
                    GridItemData(
                        icon = Icons.AutoMirrored.Outlined.Label,
                        iconColor = visual.second,
                        label = stringResource(R.string.movement_field_tag),
                        value = movement.tagName ?: "—"
                    )
                )
            }

            // Recurring details
            if (movement.isRecurring) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Autorenew,
                        iconColor = visual.second,
                        label = stringResource(R.string.movement_field_recurring),
                        value = stringResource(R.string.common_yes)
                    )
                )
            }

            // One-off detail
            if (movement.isOneTime) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Info,
                        iconColor = visual.second,
                        label = stringResource(R.string.movement_detail_one_time_short),
                        value = stringResource(R.string.common_yes)
                    )
                )
            }

            // Paid by other person details
            if (movement.paidByPersonName != null) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Person,
                        iconColor = visual.second,
                        label = stringResource(R.string.movement_detail_paid_by),
                        value = movement.paidByPersonName
                    )
                )
            }

            // Linked expense for a refund
            if (movement.type == MovementType.REFUND && movement.refundsExpenseName != null) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Storefront,
                        iconColor = visual.second,
                        label = stringResource(R.string.refund_field_linked_expense),
                        value = movement.refundsExpenseName
                    )
                )
            }
        }

        // Grid items layout (2 columns)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val chunked = gridItems.chunked(2)
            chunked.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        DetailGridItem(
                            icon = item.icon,
                            iconColor = item.iconColor,
                            label = item.label,
                            value = item.value,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Full width items for Payee and Notes
        movement.payee?.takeIf { it.isNotBlank() }?.let { payee ->
            FullWidthDetailItem(
                icon = Icons.Outlined.Storefront,
                iconColor = visual.second,
                label = stringResource(R.string.movement_field_payee),
                value = payee,
            )
        }

        movement.notes?.takeIf { notes -> notes.isNotBlank() }?.let { notes ->
            FullWidthDetailItem(
                icon = Icons.AutoMirrored.Outlined.Notes,
                iconColor = visual.second,
                label = stringResource(R.string.movement_field_notes),
                value = notes,
            )
        }

        // Refunds list for Expense
        if (movement.type == MovementType.EXPENSE) {
            if (refunds.isNotEmpty()) {
                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                Text(
                    text = stringResource(R.string.movement_detail_refunds_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    refunds.forEach { refund ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatLongDate(refund.date),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            refund.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                Text(
                                    text = "($notes)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FinanceTheme.colors.mutedText,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            MoneyText(
                                cents = refund.amountCents,
                                color = FinanceTheme.colors.refund,
                                style = MaterialTheme.typography.bodyMedium,
                                signed = true,
                            )
                        }
                    }
                }
            }

            PrimaryButton(
                text = stringResource(R.string.movement_detail_add_refund_action),
                onClick = onAddRefund,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(color = FinanceTheme.colors.cardBorder)

        // Action footer (Archive and Edit buttons)
        val canEdit = movement.type != MovementType.SETTLEMENT && movement.type != MovementType.REFUND
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DestructiveTextButton(onClick = onArchive) {
                Text(text = stringResource(R.string.movement_detail_action_archive))
            }
            if (canEdit) {
                Spacer(modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = stringResource(R.string.movement_detail_action_edit),
                    onClick = onEdit,
                )
            }
        }
        if (!canEdit) {
            // Audit U11/`docs/17` WP6a: explain why Edit is absent instead of leaving the user to
            // wonder -- settlements/refunds carry no editable fields of their own (§WP6b, deferred).
            Text(
                text = stringResource(R.string.movement_detail_no_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.mutedText,
            )
        }
    }
}

@Composable
private fun RefundFormContent(
    form: RefundFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    onFormChange: (RefundFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val parsedAmount = parseEuroCents(form.amount, allowNegative = false)
    val isOverRefund = parsedAmount != null && parsedAmount > form.remainingCents

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(R.string.refund_add_title),
        )
        form.expenseName.takeIf { it.isNotBlank() }?.let {
            DetailLine(label = stringResource(R.string.refund_field_linked_expense), value = it)
        }
        Text(
            text = stringResource(R.string.refund_remaining, formatEuroCents(form.remainingCents)),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Top-of-form text is reserved for save/repository failures -- field-level validation
        // errors render next to the offending control instead (audit U8, `docs/17` WP2).
        form.errorMessage?.let {
            InlineBanner(kind = BannerKind.Error, text = it)
        }
        val amountError = form.errorField == RefundFormField.AMOUNT
        OutlinedTextField(
            value = form.amount,
            onValueChange = { onFormChange(form.copy(amount = it)) },
            label = { Text(text = stringResource(R.string.refund_field_amount)) },
            prefix = { Text(text = "€") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError,
            supportingText = if (amountError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(amountError),
        )
        if (isOverRefund) {
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(R.string.refund_warning_over),
            )
        }
        if (form.expenseIsShared) {
            val actualError = form.errorField == RefundFormField.ACTUAL_AMOUNT
            OutlinedTextField(
                value = form.actualAmount,
                onValueChange = { onFormChange(form.copy(actualAmount = it)) },
                label = { Text(text = stringResource(R.string.refund_field_actual)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = actualError,
                supportingText = if (actualError && form.errorRes != null) {
                    { Text(text = stringResource(form.errorRes)) }
                } else null,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(actualError),
            )
        }
        val accountError = form.errorField == RefundFormField.ACCOUNT
        ChipFlowSection(
            label = stringResource(R.string.refund_field_account),
            modifier = Modifier.scrollToWhen(accountError),
        ) {
            accounts.forEach { account ->
                FinanceFilterChip(
                    selected = form.accountId == account.id,
                    label = account.name,
                    onClick = { onFormChange(form.copy(accountId = account.id)) },
                )
            }
        }
        if (accountError && form.errorRes != null) {
            Text(
                text = stringResource(form.errorRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
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
        val dateError = form.errorField == RefundFormField.DATE
        FormDatePicker(
            label = stringResource(R.string.refund_field_date),
            date = form.date,
            onDateChange = { onFormChange(form.copy(date = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(dateError),
            isError = dateError,
            supportingText = if (dateError && form.errorRes != null) {
                stringResource(form.errorRes)
            } else null,
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(text = stringResource(R.string.refund_field_notes)) },
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
                text = stringResource(R.string.refund_save),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
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

private fun CategoryRecord.supports(type: MovementType): Boolean =
    when (type) {
        MovementType.EXPENSE -> kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH
        MovementType.INCOME -> kind == CategoryKind.INCOME || kind == CategoryKind.BOTH
        else -> false
    }
