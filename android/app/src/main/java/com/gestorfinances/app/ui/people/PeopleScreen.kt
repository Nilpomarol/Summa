package com.gestorfinances.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonBalanceItemType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.formatLongDate
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    PeopleContent(
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onEdit = viewModel::onEditClicked,
        onArchive = viewModel::onArchiveClicked,
        onExternalSplit = viewModel::onPersonPaidForMeClicked,
        onOpenDetail = viewModel::onPersonDetailClicked,
    )

    state.form?.let { form ->
        PersonFormDialog(
            form = form,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
        )
    }

    state.externalSplitForm?.let { form ->
        ExternalSplitDialog(
            form = form,
            categories = state.categories,
            onFormChange = viewModel::onExternalSplitFormChanged,
            onDismiss = viewModel::onExternalSplitDismissed,
            onSave = viewModel::onExternalSplitSaveClicked,
        )
    }

    state.detail?.let { detail ->
        PersonDetailDialog(
            detail = detail,
            onDismiss = viewModel::onPersonDetailDismissed,
            onExternalSplit = {
                viewModel.onPersonDetailDismissed()
                viewModel.onPersonPaidForMeClicked(detail.person)
            },
            onSettleUp = {
                viewModel.onPersonDetailDismissed()
                viewModel.onSettleUpClicked(detail.person)
            },
        )
    }

    state.settlementForm?.let { form ->
        SettlementDialog(
            form = form,
            accounts = state.accounts,
            onFormChange = viewModel::onSettlementFormChanged,
            onDismiss = viewModel::onSettlementDismissed,
            onSave = viewModel::onSettlementSaveClicked,
        )
    }

    state.archiveCandidate?.let { person ->
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.person_archive_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = stringResource(R.string.person_archive_warning))
                    if (person.balanceCents != 0L) {
                        InlineBanner(
                            kind = BannerKind.Alert,
                            text = stringResource(R.string.person_archive_warning_nonzero),
                        )
                    }
                }
            },
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
private fun PeopleContent(
    state: PeopleUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (PersonSummary) -> Unit,
    onArchive: (PersonSummary) -> Unit,
    onExternalSplit: (PersonSummary) -> Unit,
    onOpenDetail: (PersonSummary) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.person_list_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item {
            PeopleSummaryCard(state = state)
        }

        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.person_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.people.isEmpty()) {
            item {
                EmptyPeopleCard(onAdd = onAdd)
            }
        } else {
            items(items = state.people, key = { it.id }) { person ->
                PersonRow(
                    person = person,
                    onEdit = { onEdit(person) },
                    onArchive = { onArchive(person) },
                    onExternalSplit = { onExternalSplit(person) },
                    onOpenDetail = { onOpenDetail(person) },
                )
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.person_list_add),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PeopleSummaryCard(state: PeopleUiState) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DebtMetric(
                label = stringResource(R.string.person_list_total_owed_to_user),
                cents = state.totalOwedToUserCents,
                color = FinanceTheme.colors.income,
            )
            DebtMetric(
                label = stringResource(R.string.person_list_total_you_owe),
                cents = state.totalUserOwesCents,
                color = FinanceTheme.colors.debt,
            )
            DebtMetric(
                label = stringResource(R.string.person_list_net_balance),
                cents = state.netBalanceCents,
                color = debtDirectionColor(state.netBalanceCents),
                signed = true,
            )
        }
    }
}

@Composable
private fun DebtMetric(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        MoneyText(
            cents = cents,
            color = color,
            style = MaterialTheme.typography.titleSmall,
            signed = signed,
        )
    }
}

@Composable
private fun EmptyPeopleCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.person_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.person_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.person_list_add),
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun PersonRow(
    person: PersonSummary,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onExternalSplit: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person = person)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = person.notes?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.person_latest_context_empty),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                cents = kotlin.math.abs(person.balanceCents),
                color = debtDirectionColor(person.balanceCents),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = person.balanceLabel(),
                color = debtDirectionColor(person.balanceCents),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        PersonRowMenu(
            onExternalSplit = onExternalSplit,
            onEdit = onEdit,
            onArchive = onArchive,
        )
    }
}

@Composable
private fun PersonDetailDialog(
    detail: PersonDetailState,
    onDismiss: () -> Unit,
    onExternalSplit: () -> Unit,
    onSettleUp: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.person_detail_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PersonAvatar(person = detail.person)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detail.person.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        detail.person.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                            Text(
                                text = notes,
                                color = FinanceTheme.colors.mutedText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(
                            cents = kotlin.math.abs(detail.person.balanceCents),
                            color = debtDirectionColor(detail.person.balanceCents),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = detail.person.balanceLabel(),
                            color = debtDirectionColor(detail.person.balanceCents),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.person_detail_balance),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )

                if (detail.person.balanceCents != 0L) {
                    PrimaryButton(
                        text = stringResource(R.string.person_action_settle_up),
                        onClick = onSettleUp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (detail.isLoading) {
                    Text(
                        text = stringResource(R.string.person_loading),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                detail.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = stringResource(R.string.person_detail_breakdown),
                    style = MaterialTheme.typography.titleSmall,
                )

                if (!detail.isLoading && detail.items.isEmpty()) {
                    Text(
                        text = if (detail.person.balanceCents == 0L) {
                            stringResource(R.string.person_detail_settled_body)
                        } else {
                            stringResource(R.string.debt_breakdown_empty)
                        },
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    PersonBalanceSection(
                        title = stringResource(R.string.debt_group_user_paid),
                        items = detail.items.filter { it.type == PersonBalanceItemType.USER_PAID },
                        personName = detail.person.name,
                    )
                    PersonBalanceSection(
                        title = stringResource(R.string.debt_group_person_paid, detail.person.name),
                        items = detail.items.filter { it.type == PersonBalanceItemType.PERSON_PAID },
                        personName = detail.person.name,
                    )
                    PersonBalanceSection(
                        title = stringResource(R.string.debt_group_settlements),
                        items = detail.items.filter {
                            it.type == PersonBalanceItemType.SETTLEMENT_IN ||
                                it.type == PersonBalanceItemType.SETTLEMENT_OUT
                        },
                        personName = detail.person.name,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onExternalSplit) {
                Text(text = stringResource(R.string.person_action_external_split))
            }
        },
    )
}

@Composable
private fun PersonBalanceSection(
    title: String,
    items: List<PersonBalanceItem>,
    personName: String,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        items.forEachIndexed { index, item ->
            PersonBalanceItemRow(item = item, personName = personName)
            if (index < items.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun PersonBalanceItemRow(
    item: PersonBalanceItem,
    personName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = formatLongDate(item.date),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = item.displayTitle(personName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = item.contextLabel(personName),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        MoneyText(
            cents = item.effectCents,
            color = debtDirectionColor(item.effectCents),
            style = MaterialTheme.typography.bodyLarge,
            signed = true,
        )
    }
}

@Composable
private fun PersonAvatar(person: PersonSummary) {
    val background = person.color?.let(::parseColorOrNull) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(background.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = person.avatar?.takeIf { it.isNotBlank() } ?: person.name.firstInitial(),
            color = background,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PersonRowMenu(
    onExternalSplit: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.person_action_external_split)) },
                onClick = { expanded = false; onExternalSplit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.common_archive),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { expanded = false; onArchive() },
            )
        }
    }
}

@Composable
private fun ExternalSplitDialog(
    form: ExternalSplitFormState,
    categories: List<CategoryRecord>,
    onFormChange: (ExternalSplitFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.split_editor_external_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.split_payer_person, form.payerPersonName),
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
                    value = form.totalAmount,
                    onValueChange = { onFormChange(form.copy(totalAmount = it)) },
                    label = { Text(text = stringResource(R.string.split_field_total)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.userShare,
                    onValueChange = { onFormChange(form.copy(userShare = it)) },
                    label = { Text(text = stringResource(R.string.split_field_user_share)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.date,
                    onValueChange = { onFormChange(form.copy(date = it)) },
                    label = { Text(text = stringResource(R.string.split_field_date)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { onFormChange(form.copy(description = it)) },
                    label = { Text(text = stringResource(R.string.split_field_description)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipFlowSection(label = stringResource(R.string.split_field_category)) {
                    FinanceFilterChip(
                        selected = form.categoryId == null,
                        label = stringResource(R.string.common_no_category),
                        onClick = { onFormChange(form.copy(categoryId = null)) },
                    )
                    categories.filter { it.supportsExpense }.forEach { category ->
                        FinanceFilterChip(
                            selected = form.categoryId == category.id,
                            label = category.name,
                            onClick = { onFormChange(form.copy(categoryId = category.id)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(text = stringResource(R.string.split_save))
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
private fun SettlementDialog(
    form: SettlementFormState,
    accounts: List<AccountSummary>,
    onFormChange: (SettlementFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val parsedAmount = parseEuroCents(form.amount, allowNegative = false)
    val isOverpay = parsedAmount != null && parsedAmount > form.outstandingCents

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settlement_title_with_person, form.personName))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(
                            when (form.direction) {
                                SettlementDirection.PERSON_TO_USER -> R.string.settlement_direction_person_to_user
                                SettlementDirection.USER_TO_PERSON -> R.string.settlement_direction_user_to_person
                            },
                        ),
                        modifier = Modifier.weight(1f),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MoneyText(
                        cents = form.outstandingCents,
                        color = when (form.direction) {
                            SettlementDirection.PERSON_TO_USER -> FinanceTheme.colors.income
                            SettlementDirection.USER_TO_PERSON -> FinanceTheme.colors.debt
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
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
                    label = { Text(text = stringResource(R.string.settlement_field_amount)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isOverpay) {
                    InlineBanner(
                        kind = BannerKind.Alert,
                        text = stringResource(R.string.settlement_warning_overpay),
                    )
                }
                ChipFlowSection(label = stringResource(R.string.settlement_field_account)) {
                    accounts.forEach { account ->
                        FinanceFilterChip(
                            selected = form.accountId == account.id,
                            label = account.name,
                            onClick = { onFormChange(form.copy(accountId = account.id)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = form.date,
                    onValueChange = { onFormChange(form.copy(date = it)) },
                    label = { Text(text = stringResource(R.string.settlement_field_date)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = { Text(text = stringResource(R.string.settlement_field_notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(text = stringResource(R.string.settlement_save))
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
private fun PersonFormDialog(
    form: PersonFormState,
    onFormChange: (PersonFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.person_form_new_title else R.string.person_form_edit_title,
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
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it, errorRes = null, errorMessage = null)) },
                    label = { Text(text = stringResource(R.string.person_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it, errorRes = null, errorMessage = null)) },
                    label = { Text(text = stringResource(R.string.person_field_notes)) },
                    minLines = 3,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(
                    text = stringResource(
                        if (form.id == null) R.string.person_save_new else R.string.person_save_changes,
                    ),
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
private fun PersonSummary.balanceLabel(): String =
    when {
        balanceCents > 0L -> stringResource(R.string.person_balance_owes_you)
        balanceCents < 0L -> stringResource(R.string.person_balance_you_owe)
        else -> stringResource(R.string.person_balance_settled)
    }

@Composable
private fun debtDirectionColor(cents: Long): Color =
    when {
        cents > 0L -> FinanceTheme.colors.income
        cents < 0L -> FinanceTheme.colors.debt
        else -> FinanceTheme.colors.mutedText
    }

@Composable
private fun PersonBalanceItem.displayTitle(personName: String): String =
    title?.takeIf { it.isNotBlank() } ?: sourceLabel(personName)

@Composable
private fun PersonBalanceItem.contextLabel(personName: String): String =
    listOfNotNull(
        sourceLabel(personName),
        categoryName?.takeIf { it.isNotBlank() } ?: when (type) {
            PersonBalanceItemType.USER_PAID, PersonBalanceItemType.PERSON_PAID ->
                stringResource(R.string.common_no_category)
            PersonBalanceItemType.SETTLEMENT_IN, PersonBalanceItemType.SETTLEMENT_OUT -> null
        },
    ).joinToString(" · ")

@Composable
private fun PersonBalanceItem.sourceLabel(personName: String): String =
    when (type) {
        PersonBalanceItemType.USER_PAID -> stringResource(R.string.debt_source_user_paid)
        PersonBalanceItemType.PERSON_PAID -> stringResource(R.string.debt_source_person_paid, personName)
        PersonBalanceItemType.SETTLEMENT_IN -> stringResource(R.string.debt_source_settlement_in)
        PersonBalanceItemType.SETTLEMENT_OUT -> stringResource(R.string.debt_source_settlement_out)
    }

private fun String.firstInitial(): String =
    trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

private fun parseColorOrNull(hex: String): Color? =
    try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: IllegalArgumentException) {
        null
    }

private val CategoryRecord.supportsExpense: Boolean
    get() = kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH
