package com.gestorfinances.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor as categoryColorFromTheme

@Composable
fun MovementListItem(
    movement: MovementSummary,
    onClick: () -> Unit = {},
    personEffectCents: Long? = null,
    showDate: Boolean = true,
) {
    val visual = movement.chipVisual()
    val typeColor = FinanceTheme.colors.amountColor(movement.type)
    val eventLine = listOfNotNull(movement.tripName, movement.tagName).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Color rail: category color for expense/income, type hue otherwise — gives every row identity.
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 40.dp)
                .background(visual.second, RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        IconChip(
            icon = visual.first,
            contentDescription = null,
            color = visual.second,
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = movement.movementTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (movement.isShared || movement.type == MovementType.EXTERNAL_EXPENSE) {
                    val sharingIcon = if (movement.type == MovementType.EXTERNAL_EXPENSE) {
                        Icons.Outlined.Handshake
                    } else {
                        Icons.Outlined.Group
                    }
                    BadgeIcon(sharingIcon, stringResource(R.string.movement_shared_badge))
                }
                if (movement.isRecurring) {
                    BadgeIcon(Icons.Outlined.Repeat, stringResource(R.string.movement_recurring_badge))
                }
                if (movement.isOneTime) {
                    // Extraordinary expense: alert-tinted starburst so it clearly stands apart.
                    BadgeIcon(
                        icon = Icons.Outlined.NewReleases,
                        contentDescription = stringResource(R.string.movement_one_time_badge),
                        tint = FinanceTheme.colors.alert,
                        size = 17.dp,
                    )
                }
            }
            Text(
                text = movement.contextLine(showDate = showDate),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (eventLine.isNotEmpty()) {
                Text(
                    text = eventLine,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (personEffectCents != null) {
                MoneyText(
                    cents = personEffectCents,
                    color = when {
                        personEffectCents > 0L -> FinanceTheme.colors.income
                        personEffectCents < 0L -> FinanceTheme.colors.debt
                        else -> FinanceTheme.colors.mutedText
                    },
                    style = MaterialTheme.typography.titleMedium,
                    signed = true,
                )
            } else {
                val isShared = movement.isShared && movement.type == MovementType.EXPENSE
                val isExternal = movement.type == MovementType.EXTERNAL_EXPENSE

                if (isShared || isExternal) {
                    // Show my share as primary
                    MoneyText(
                        cents = if (isExternal) movement.amountCents else movement.userShareCents,
                        color = typeColor,
                        style = MaterialTheme.typography.titleMedium,
                        signed = isExternal, // External shows minus
                    )
                    Text(
                        text = stringResource(R.string.movement_total_short, formatEuroCents(movement.amountCents)),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                    )
                } else {
                    MoneyText(
                        cents = movement.signedAmountCents(),
                        color = typeColor,
                        style = MaterialTheme.typography.titleMedium,
                        signed = movement.type == MovementType.INCOME || movement.type == MovementType.SETTLEMENT,
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = FinanceTheme.colors.mutedText,
    size: Dp = 14.dp,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun MovementSummary.movementTitle(): String =
    name ?: payee ?: categoryName ?: type.label()

@Composable
internal fun MovementSummary.contextLine(showDate: Boolean = true): String {
    val datePrefix = if (showDate && date.isNotBlank()) formatMovementDate(date) else null
    val contextText = when (type) {
        MovementType.TRANSFER -> stringResource(
            R.string.movement_transfer_accounts,
            accountName.orEmpty(),
            destinationAccountName ?: stringResource(R.string.movement_destination_missing),
        )
        MovementType.SETTLEMENT ->
            listOfNotNull(settlementContext(), accountName).joinToString(separator = " · ")
        MovementType.EXTERNAL_EXPENSE ->
            stringResource(R.string.movement_label_paid_by, paidByPersonName.orEmpty())
        else -> {
            val sharingLabel = if (isShared) {
                if (userShareCents == 0L) {
                    stringResource(R.string.movement_label_i_paid_for, paidByPersonName.orEmpty())
                } else {
                    stringResource(R.string.movement_label_shared)
                }
            } else {
                null
            }
            val paidBy = paidByPersonName?.takeIf { it.isNotEmpty() && !isShared }
                ?.let { stringResource(R.string.movement_paid_by_person, it) }
            
            listOfNotNull(sharingLabel ?: paidBy ?: categoryName, accountName).joinToString(separator = " · ")
        }
    }
    return listOfNotNull(datePrefix, contextText.ifEmpty { null }).joinToString(separator = " · ")
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

internal fun MovementSummary.signedAmountCents(): Long =
    when (type) {
        MovementType.EXPENSE, MovementType.EXTERNAL_EXPENSE -> -amountCents
        MovementType.SETTLEMENT ->
            if (settlementDirection == SettlementDirection.USER_TO_PERSON) -amountCents else amountCents
        else -> amountCents
    }

@Composable
internal fun MovementSummary.chipVisual(): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    val finance = FinanceTheme.colors
    return when (type) {
        MovementType.EXPENSE, MovementType.INCOME ->
            if (categoryId != null) {
                categoryIcon(this.categoryIcon) to categoryColorFromTheme(this.categoryColor)
            } else if (type == MovementType.INCOME) {
                movementTypeIcon(type) to finance.income
            } else {
                categoryIcon(null) to categoryColorFromTheme(null)
            }
        MovementType.TRANSFER -> movementTypeIcon(type) to finance.transfer
        MovementType.SETTLEMENT -> movementTypeIcon(type) to finance.settlement
        MovementType.REFUND -> movementTypeIcon(type) to finance.refund
        MovementType.EXTERNAL_EXPENSE ->
            if (categoryId != null) {
                categoryIcon(this.categoryIcon) to finance.debt
            } else {
                movementTypeIcon(type) to finance.debt
            }
    }
}
