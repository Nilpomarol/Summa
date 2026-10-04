package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.ui.theme.LocalControlStyle
import com.gestorfinances.app.ui.theme.formAction
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.account_contribution_external_source
import com.gestorfinances.ui.resources.account_contribution_member
import com.gestorfinances.ui.resources.account_contribution_source
import com.gestorfinances.ui.resources.account_member_owner
import com.gestorfinances.ui.resources.account_withdrawal_destination
import com.gestorfinances.ui.resources.account_withdrawal_member
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.common_yes
import com.gestorfinances.ui.resources.failure_save_movement
import com.gestorfinances.ui.resources.movement_detail_account_movement_short
import com.gestorfinances.ui.resources.movement_detail_action_archive
import com.gestorfinances.ui.resources.movement_detail_action_edit
import com.gestorfinances.ui.resources.movement_detail_contribution_destination
import com.gestorfinances.ui.resources.movement_detail_debt
import com.gestorfinances.ui.resources.movement_detail_debt_none_shared_account
import com.gestorfinances.ui.resources.movement_detail_financing
import com.gestorfinances.ui.resources.movement_detail_financing_shared_account
import com.gestorfinances.ui.resources.movement_detail_no_edit_hint
import com.gestorfinances.ui.resources.movement_detail_one_time_short
import com.gestorfinances.ui.resources.movement_detail_owed_title
import com.gestorfinances.ui.resources.movement_detail_paid_by
import com.gestorfinances.ui.resources.movement_detail_paid_for_many
import com.gestorfinances.ui.resources.movement_detail_paid_for_one
import com.gestorfinances.ui.resources.movement_detail_refund_action_compact
import com.gestorfinances.ui.resources.movement_detail_refunds_title
import com.gestorfinances.ui.resources.movement_detail_section_extra
import com.gestorfinances.ui.resources.movement_detail_settlement_paid_to
import com.gestorfinances.ui.resources.movement_detail_settlement_received_from
import com.gestorfinances.ui.resources.movement_field_account
import com.gestorfinances.ui.resources.movement_field_category
import com.gestorfinances.ui.resources.movement_field_date
import com.gestorfinances.ui.resources.movement_field_destination_account
import com.gestorfinances.ui.resources.movement_field_notes
import com.gestorfinances.ui.resources.movement_field_payee
import com.gestorfinances.ui.resources.movement_field_recurring
import com.gestorfinances.ui.resources.movement_field_tag
import com.gestorfinances.ui.resources.movement_field_trip
import com.gestorfinances.ui.resources.movement_total_short
import com.gestorfinances.ui.resources.refund_add_title
import com.gestorfinances.ui.resources.refund_edit_title
import com.gestorfinances.ui.resources.refund_field_account
import com.gestorfinances.ui.resources.refund_field_actual
import com.gestorfinances.ui.resources.refund_field_date
import com.gestorfinances.ui.resources.refund_field_linked_expense
import com.gestorfinances.ui.resources.refund_field_notes
import com.gestorfinances.ui.resources.refund_orphaned_warning
import com.gestorfinances.ui.resources.refund_remaining
import com.gestorfinances.ui.resources.refund_save
import com.gestorfinances.ui.resources.refund_warning_over
import com.gestorfinances.ui.resources.settlement_field_person
import com.gestorfinances.ui.resources.split_editor_title
import com.gestorfinances.ui.resources.split_payer_user
import org.jetbrains.compose.resources.StringResource
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

@Composable
fun MovementDetailContent(
    movement: MovementSummary,
    refunds: List<RefundSummary>,
    accounts: List<AccountSummary>,
    split: MovementSplitDraft?,
    people: List<PersonSummary>,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onAddRefund: () -> Unit,
    onRefundClick: (RefundSummary) -> Unit,
) {
    val visual = movement.chipVisual()
    // Shared/external expenses: the header amount is the user's own share -- the total (what the
    // expense actually cost) is a separate number and gets called out as a caption, mirroring the
    // list-row convention in MovementListItem.kt.
    val isSharedExpense = movement.isShared && movement.type == MovementType.EXPENSE
    val isSharedIncome = movement.isShared && movement.type == MovementType.INCOME
    val isExternal = movement.paidByPerson
    val fundedBySharedAccount = movement.type == MovementType.EXPENSE &&
        movement.financingKind == ExpenseFunding.SHARED_ACCOUNT
    // Paid entirely for someone else: none of it is the user's own spending, so it reads as money
    // that left the account and is owed back, not as a shared expense with a zero share.
    val paidForOthers = isSharedExpense && movement.userShareCents == 0L && !isExternal && !fundedBySharedAccount
    val debtors = split?.lines.orEmpty()
        .filter { it.participantKind == SplitParticipantKind.PERSON && it.owedAmountCents > 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: Icon chip, Title, Large amount
        MovementSheetHeader(
            title = movement.movementTitle(),
            amountCents = when {
                paidForOthers -> -movement.amountCents
                // Someone else paid: it costs the owner their own part, not the whole of it.
                movement.paidByPerson -> -movement.netUserShareCents.coerceAtLeast(0L)
                isSharedExpense -> -movement.netUserShareCents
                isSharedIncome -> movement.userShareCents
                else -> movement.signedAmountCents()
            },
            type = movement.type,
            icon = visual.first,
            iconColor = visual.second,
            amountColor = if (isSharedExpense && !paidForOthers) FinanceTheme.colors.shared else FinanceTheme.colors.movementAmountColor(movement),
            totalCaption = when {
                paidForOthers -> debtors.singleOrNull()?.let { line ->
                    stringResource(
                        Res.string.movement_detail_paid_for_one,
                        people.firstOrNull { it.id == line.personId }?.name ?: "—",
                        formatEuroCents(line.owedAmountCents),
                    )
                } ?: stringResource(Res.string.movement_detail_paid_for_many, formatEuroCents(movement.amountCents))
                // The shared account paid, so the other figure is what left that account.
                fundedBySharedAccount -> stringResource(
                    Res.string.movement_detail_account_movement_short,
                    formatEuroCents(-movement.amountCents),
                )
                isSharedExpense || isSharedIncome || isExternal ->
                    stringResource(Res.string.movement_total_short, formatEuroCents(movement.amountCents))
                else -> null
            },
        )

        if (movement.type == MovementType.REFUND && movement.refundsExpenseArchived) {
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(Res.string.refund_orphaned_warning),
            )
        }

        // Core group: structural fields (when/where/category-or-counterparty), essentially
        // always present. buildList{} is inline, so stringResource() calls work directly
        // inside it -- no remember{} needed for this cheap, non-lazy list.
        val coreItems = buildList {
            // Date
            add(
                GridItemData(
                    icon = Icons.Outlined.CalendarMonth,
                    label = stringResource(Res.string.movement_field_date),
                    value = formatExpandedDate(movement.date)
                )
            )

            if (movement.type == MovementType.CONTRIBUTION) {
                // A contribution names who put the money in and, for the owner, where it came from.
                add(
                    GridItemData(
                        icon = Icons.Outlined.Person,
                        label = stringResource(
                            if (movement.contributionDirection == ContributionDirection.OUT) {
                                Res.string.account_withdrawal_member
                            } else {
                                Res.string.account_contribution_member
                            },
                        ),
                        value = movement.paidByPersonName ?: stringResource(Res.string.account_member_owner),
                    )
                )
                if (movement.payerId == null) {
                    val sourceAcc = accounts.firstOrNull { it.id == movement.accountId }
                    add(
                        GridItemData(
                            icon = accountIcon(sourceAcc?.icon),
                            label = stringResource(
                                if (movement.contributionDirection == ContributionDirection.OUT) {
                                    Res.string.account_withdrawal_destination
                                } else {
                                    Res.string.account_contribution_source
                                },
                            ),
                            value = movement.accountName ?: stringResource(Res.string.account_contribution_external_source),
                        )
                    )
                }
            } else {
                // Origin Account
                val originAcc = accounts.firstOrNull { it.id == movement.accountId }
                add(
                    GridItemData(
                        icon = accountIcon(originAcc?.icon),
                        label = stringResource(Res.string.movement_field_account),
                        value = movement.accountName ?: "—"
                    )
                )
            }

            // Destination account for transfers and contributions.
            if (movement.type == MovementType.TRANSFER || movement.type == MovementType.CONTRIBUTION) {
                val destAcc = accounts.firstOrNull { it.id == movement.destinationAccountId }
                add(
                    GridItemData(
                        icon = accountIcon(destAcc?.icon),
                        label = stringResource(
                            if (movement.type == MovementType.CONTRIBUTION) {
                                Res.string.movement_detail_contribution_destination
                            } else {
                                Res.string.movement_field_destination_account
                            },
                        ),
                        value = movement.destinationAccountName ?: "—"
                    )
                )
            }

            if (fundedBySharedAccount) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.AccountBalance,
                        label = stringResource(Res.string.movement_detail_financing),
                        value = stringResource(Res.string.movement_detail_financing_shared_account),
                    ),
                )
                // The split says who consumed it; with the account paying, nobody owes anybody.
                add(
                    GridItemData(
                        icon = Icons.Outlined.Handshake,
                        label = stringResource(Res.string.movement_detail_debt),
                        value = stringResource(Res.string.movement_detail_debt_none_shared_account),
                    ),
                )
            }

            // Category for non-transfer and non-settlement
            if (movement.type != MovementType.TRANSFER && movement.type != MovementType.SETTLEMENT && movement.type != MovementType.CONTRIBUTION) {
                add(
                    GridItemData(
                        icon = categoryIcon(movement.categoryIcon),
                        label = stringResource(Res.string.movement_field_category),
                        value = movement.categoryName ?: stringResource(Res.string.common_no_category)
                    )
                )
            }

            // Settlement Person Direction details
            if (movement.type == MovementType.SETTLEMENT) {
                val label = when (movement.settlementDirection) {
                    SettlementDirection.PERSON_TO_USER -> stringResource(Res.string.movement_detail_settlement_received_from)
                    SettlementDirection.USER_TO_PERSON -> stringResource(Res.string.movement_detail_settlement_paid_to)
                    null -> stringResource(Res.string.settlement_field_person)
                }
                add(
                    GridItemData(
                        icon = Icons.Outlined.Handshake,
                        label = label,
                        value = movement.settlementPersonName ?: "—"
                    )
                )
            }
        }

        // Extra group: optional/contextual fields, only rendered when present.
        val extraItems = buildList {
            // Trip info
            if (movement.tripId != null) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Flight,
                        label = stringResource(Res.string.movement_field_trip),
                        value = movement.tripName ?: "—"
                    )
                )
            }

            // Tag info
            if (movement.tagId != null) {
                add(
                    GridItemData(
                        icon = Icons.AutoMirrored.Outlined.Label,
                        label = stringResource(Res.string.movement_field_tag),
                        value = movement.tagName ?: "—"
                    )
                )
            }

            // Recurring details
            if (movement.isRecurring) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Autorenew,
                        label = stringResource(Res.string.movement_field_recurring),
                        value = stringResource(Res.string.common_yes)
                    )
                )
            }

            // One-off detail
            if (movement.isOneTime) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Info,
                        label = stringResource(Res.string.movement_detail_one_time_short),
                        value = stringResource(Res.string.common_yes)
                    )
                )
            }

            // Paid by other person details
            if (movement.paidByPersonName != null) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Person,
                        label = stringResource(Res.string.movement_detail_paid_by),
                        value = movement.paidByPersonName!!
                    )
                )
            }

            // Linked expense for a refund
            if (movement.type == MovementType.REFUND && movement.refundsExpenseName != null) {
                add(
                    GridItemData(
                        icon = Icons.Outlined.Storefront,
                        label = stringResource(Res.string.refund_field_linked_expense),
                        value = movement.refundsExpenseName!!
                    )
                )
            }

            // Payee
            movement.payee?.takeIf { it.isNotBlank() }?.let { payee ->
                add(
                    GridItemData(
                        icon = Icons.Outlined.Storefront,
                        label = stringResource(Res.string.movement_field_payee),
                        value = payee,
                    )
                )
            }

            // Notes
            movement.notes?.takeIf { notes -> notes.isNotBlank() }?.let { notes ->
                add(
                    GridItemData(
                        icon = Icons.AutoMirrored.Outlined.Notes,
                        label = stringResource(Res.string.movement_field_notes),
                        value = notes,
                    )
                )
            }
        }

        DetailGroupCard(rows = coreItems)
        DetailGroupCard(rows = extraItems, title = stringResource(Res.string.movement_detail_section_extra))

        if (split != null) {
            SplitBreakdownCard(split = split, people = people, owedToYou = paidForOthers)
        }

        // Refunds list for an expense the owner paid; money back from one a person paid is theirs.
        if (movement.type == MovementType.EXPENSE && !movement.paidByPerson) {
            if (refunds.isNotEmpty()) {
                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                Text(
                    text = stringResource(Res.string.movement_detail_refunds_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Column {
                    refunds.forEach { refund ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRefundClick(refund) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatExpandedDate(refund.date),
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

        }

        // Action footer: Edit leads, refund sits beside a quieter delete.
        val canEdit = movement.type != MovementType.SETTLEMENT && movement.type != MovementType.REFUND
        val canRefund = movement.type == MovementType.EXPENSE && !movement.paidByPerson
        if (LocalControlStyle.current.pointer) {
            // With a pointer the actions line up at the trailing edge, the leading one last.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DestructiveTextButton(onClick = onArchive) {
                    Text(text = stringResource(Res.string.movement_detail_action_archive))
                }
                if (canRefund) {
                    SecondaryButton(text = stringResource(Res.string.movement_detail_refund_action_compact), onClick = onAddRefund)
                }
                if (canEdit) {
                    PrimaryButton(text = stringResource(Res.string.movement_detail_action_edit), onClick = onEdit)
                }
            }
        } else Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canEdit) {
                PrimaryButton(
                    text = stringResource(Res.string.movement_detail_action_edit),
                    onClick = onEdit,
                    modifier = Modifier.formAction(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canRefund) {
                    SecondaryButton(
                        text = stringResource(Res.string.movement_detail_refund_action_compact),
                        onClick = onAddRefund,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DestructiveTextButton(onClick = onArchive) {
                        Text(text = stringResource(Res.string.movement_detail_action_archive))
                    }
                }
            }
        }
        if (!canEdit) {
            // Product rule: explain why Edit is absent instead of leaving the user to
            // wonder -- settlements/refunds carry no editable fields of their own.
            Text(
                text = stringResource(Res.string.movement_detail_no_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.mutedText,
            )
        }
    }
}

/**
 * Recording a refund, in the movement form's shape: the expense's tile and name over the amount
 * typed in place, what is left to return, where it lands and when, then the one action.
 */
@Composable
fun RefundFormContent(
    form: RefundFormState,
    expense: MovementSummary?,
    accounts: List<AccountSummary>,
    onFormChange: (RefundFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val parsedAmount = parseEuroCents(form.amount, allowNegative = false)
    val isOverRefund = parsedAmount != null && parsedAmount > form.remainingCents
    val finance = FinanceTheme.colors

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
            title = stringResource(if (form.refundId == null) Res.string.refund_add_title else Res.string.refund_edit_title),
        )
        // Top-of-form text is reserved for save/repository failures -- field-level validation
        // errors render next to the offending control instead.
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = Res.string.failure_save_movement)
        }
        val errorText = form.errorRes?.let { stringResource(it) }
        val amountError = form.errorField == RefundFormField.AMOUNT
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MovementFormHeader(
                icon = categoryIcon(expense?.categoryIcon),
                iconColor = categoryColor(expense?.categoryColor),
                title = form.expenseName.ifBlank { stringResource(Res.string.refund_field_linked_expense) },
                titleIsPlaceholder = form.expenseName.isBlank(),
                amount = form.amount,
                onAmountChange = { onFormChange(form.copy(amount = it)) },
                amountColor = finance.refund,
                amountError = errorText.takeIf { amountError },
                modifier = Modifier.scrollToWhen(amountError),
            )
            Text(
                text = stringResource(Res.string.refund_remaining, formatEuroCents(form.remainingCents)),
                color = finance.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isOverRefund) {
            InlineBanner(kind = BannerKind.Alert, text = stringResource(Res.string.refund_warning_over))
        }
        if (form.expenseIsShared) {
            val actualError = form.errorField == RefundFormField.ACTUAL_AMOUNT
            AppTextField(
                value = form.actualAmount,
                onValueChange = { onFormChange(form.copy(actualAmount = it)) },
                label = { Text(text = stringResource(Res.string.refund_field_actual)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = actualError,
                supportingText = errorText?.takeIf { actualError }?.let { { Text(text = it) } },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(actualError),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val accountError = form.errorField == RefundFormField.ACCOUNT
            AccountSelect(
                label = stringResource(Res.string.refund_field_account),
                selectedId = form.accountId,
                accounts = accounts,
                onSelect = { onFormChange(form.copy(accountId = it)) },
                modifier = Modifier
                    .weight(1f)
                    .scrollToWhen(accountError),
                isError = accountError,
                supportingText = errorText.takeIf { accountError },
            )
            val dateError = form.errorField == RefundFormField.DATE
            FormDatePicker(
                label = stringResource(Res.string.refund_field_date),
                date = form.date,
                onDateChange = { onFormChange(form.copy(date = it)) },
                modifier = Modifier
                    .weight(1f)
                    .scrollToWhen(dateError),
                isError = dateError,
                supportingText = errorText.takeIf { dateError },
            )
        }
        AppTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(text = stringResource(Res.string.refund_field_notes)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = stringResource(Res.string.refund_save),
            onClick = onSave,
            modifier = Modifier.formAction(),
        )
        if (form.refundId != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DestructiveTextButton(onClick = onDelete) {
                    Text(text = stringResource(Res.string.movement_detail_action_archive))
                }
            }
        }
    }
}

/** Who-owes-what breakdown for a shared or externally-paid expense: one row per split line
 * (the user's own line labeled "Jo", each person line with their monogram/name), amount
 * right-aligned. */
@Composable
fun SplitBreakdownCard(
    split: MovementSplitDraft,
    people: List<PersonSummary>,
    /** Paid for others: list only who owes it back, as money coming to the user. */
    owedToYou: Boolean = false,
) {
    val lines = if (owedToYou) split.lines.filter { it.participantKind == SplitParticipantKind.PERSON } else split.lines
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(if (owedToYou) Res.string.movement_detail_owed_title else Res.string.split_editor_title),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        FinanceCard {
            lines.forEachIndexed { index, line ->
                val person = line.personId?.let { id -> people.firstOrNull { it.id == id } }
                val name = when (line.participantKind) {
                    SplitParticipantKind.USER -> stringResource(Res.string.split_payer_user)
                    SplitParticipantKind.PERSON -> person?.name ?: "—"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PersonMonogram(
                        label = personInitial(name),
                        colorHex = person?.color,
                        size = 32.dp,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyText(
                        cents = line.owedAmountCents,
                        color = if (owedToYou) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (index != lines.lastIndex) {
                    HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                }
            }
        }
    }
}
