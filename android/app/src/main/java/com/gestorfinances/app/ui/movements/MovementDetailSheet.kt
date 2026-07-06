@file:OptIn(ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.RefundSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.chipVisual
import com.gestorfinances.app.ui.common.formatLongDate
import com.gestorfinances.app.ui.common.movementTitle
import com.gestorfinances.app.ui.common.signedAmountCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate

private data class GridItemData(
    val icon: ImageVector,
    val iconColor: androidx.compose.ui.graphics.Color,
    val label: String,
    val value: String,
)

@Composable
fun MovementDetailSheet(
    movement: MovementSummary,
    refunds: List<RefundSummary>,
    accounts: List<AccountSummary>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onAddRefund: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visual = movement.chipVisual()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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

            // Grid items data builder
            val linkedExpenseLabel = stringResource(R.string.refund_field_linked_expense)
            val gridItems = remember(movement, accounts, linkedExpenseLabel) {
                buildList {
                    // Date
                    add(
                        GridItemData(
                            icon = Icons.Outlined.CalendarMonth,
                            iconColor = visual.second,
                            label = "Data",
                            value = formatLongDate(movement.date)
                        )
                    )

                    // Origin Account
                    val originAcc = accounts.firstOrNull { it.id == movement.accountId }
                    add(
                        GridItemData(
                            icon = accountIcon(originAcc?.icon),
                            iconColor = originAcc?.color?.let { categoryColor(it) } ?: visual.second,
                            label = "Compte",
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
                                label = "Compte de destí",
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
                                label = "Categoria",
                                value = movement.categoryName ?: "Sense categoria"
                            )
                        )
                    }

                    // Settlement Person Direction details
                    if (movement.type == MovementType.SETTLEMENT) {
                        val label = when (movement.settlementDirection) {
                            SettlementDirection.PERSON_TO_USER -> "Rebut de"
                            SettlementDirection.USER_TO_PERSON -> "Pagat a"
                            null -> "Persona"
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
                                label = "Viatge",
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
                                label = "Etiqueta",
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
                                label = "Recurrent",
                                value = "Sí"
                            )
                        )
                    }

                    // One-off detail
                    if (movement.isOneTime) {
                        add(
                            GridItemData(
                                icon = Icons.Outlined.Info,
                                iconColor = visual.second,
                                label = "Extraordinari",
                                value = "Sí"
                            )
                        )
                    }

                    // Paid by other person details
                    if (movement.paidByPersonName != null) {
                        add(
                            GridItemData(
                                icon = Icons.Outlined.Person,
                                iconColor = visual.second,
                                label = "Pagat per",
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
                                label = linkedExpenseLabel,
                                value = movement.refundsExpenseName
                            )
                        )
                    }
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
                    label = "Beneficiari",
                    value = payee,
                )
            }

            movement.notes?.takeIf { notes -> notes.isNotBlank() }?.let { notes ->
                FullWidthDetailItem(
                    icon = Icons.AutoMirrored.Outlined.Notes,
                    iconColor = visual.second,
                    label = "Notes",
                    value = notes,
                )
            }

            // Refunds list for Expense
            if (movement.type == MovementType.EXPENSE) {
                if (refunds.isNotEmpty()) {
                    HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                    Text(
                        text = "Reemborsaments",
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
                    text = "Afegeix reemborsament",
                    onClick = onAddRefund,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(color = FinanceTheme.colors.cardBorder)

            // Action footer (Archive and Edit buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DestructiveTextButton(onClick = onArchive) {
                    Text(text = "Arxivar")
                }
                val canEdit = movement.type != MovementType.SETTLEMENT && movement.type != MovementType.REFUND
                if (canEdit) {
                    Spacer(modifier = Modifier.weight(1f))
                    PrimaryButton(
                        text = "Editar",
                        onClick = onEdit,
                    )
                }
            }
        }
    }
}
