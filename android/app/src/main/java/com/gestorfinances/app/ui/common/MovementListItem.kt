package com.gestorfinances.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor as categoryColorFromTheme
import com.gestorfinances.app.ui.theme.dataMarkColor
import kotlin.math.abs

private val MOVEMENT_ICON_SIZE = 40.dp

/** Gap between the icon and the text block; also how far the separator is inset. */
private val MOVEMENT_ICON_GAP = 12.dp

/** The row breathes only as much as a scannable list allows. */
private val MOVEMENT_ROW_VERTICAL_PADDING = 10.dp

/** A saved identity colour reads as a small solid mark, not as a tinted container. */
private val IDENTITY_DOT_SIZE = 7.dp

private val IDENTITY_ICON_SIZE = 13.dp

/** The qualifying line may wrap once; past that the movement page is the place to look. */
private const val META_MAX_LINES = 2

internal enum class MovementAmountRole {
    YOUR_SHARE,
    MOVEMENT,
    TOTAL,
}

/**
 * Personal views lead with the user's own share of a shared or external expense, or of an income
 * allocated between members. An account's own
 * ledger leads with what the movement did to that account instead, so the list reconciles to its
 * balance, and captions the user's share beneath a shared expense.
 */
internal fun MovementSummary.primaryAmountRole(inAccount: Boolean = false): MovementAmountRole =
    if (!inAccount && ((isShared && (type == MovementType.EXPENSE || type == MovementType.INCOME)) || type == MovementType.EXTERNAL_EXPENSE)) {
        MovementAmountRole.YOUR_SHARE
    } else {
        MovementAmountRole.MOVEMENT
    }

internal fun MovementSummary.secondaryAmountRole(inAccount: Boolean = false): MovementAmountRole? = when {
    primaryAmountRole(inAccount) == MovementAmountRole.YOUR_SHARE -> MovementAmountRole.TOTAL
    inAccount && isShared && (type == MovementType.EXPENSE || type == MovementType.INCOME) -> MovementAmountRole.YOUR_SHARE
    else -> null
}

/**
 * Where a row sits in its run of movements. The run shares one surface, so only its ends are
 * rounded and only the rows with a neighbour below them carry a rule.
 */
enum class MovementRowPosition {
    FIRST,
    MIDDLE,
    LAST,
    ONLY,
}

fun movementRowPosition(index: Int, count: Int): MovementRowPosition = when {
    count <= 1 -> MovementRowPosition.ONLY
    index == 0 -> MovementRowPosition.FIRST
    index == count - 1 -> MovementRowPosition.LAST
    else -> MovementRowPosition.MIDDLE
}

/**
 * One movement, as a flat row: what it was and what it cost on top, everything that qualifies it
 * underneath. The qualifying line takes a second line when the movement carries enough to need
 * one, so a plain expense stays two lines tall and a trip expense with a tag can reach three.
 *
 * The row sits straight on the page and is parted from the next by a hairline inset under the
 * icon, so a run of movements reads as one ledger rather than as a stack of cards. Colour comes
 * from the filled identity tile at the head of the row, not from the surface behind it.
 * [position] tells the row where it sits in that run; pass [movementRowPosition] for an indexed
 * list. [accountDeltaCents] is what the movement did to the balance of the account whose ledger
 * holds the row, from canonical account flow; pass it only there, and the row leads with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovementListItem(
    movement: MovementSummary,
    onClick: () -> Unit = {},
    personEffectCents: Long? = null,
    accountDeltaCents: Long? = null,
    showDate: Boolean = true,
    position: MovementRowPosition = MovementRowPosition.ONLY,
) {
    val visual = movement.chipVisual()
    val typeColor = FinanceTheme.colors.amountColor(movement.type)
    val detail = movement.detailLine()
    val amountAccessibilityDescription = movement.primaryAmountContentDescription()
    val totalAccessibilityDescription = stringResource(
        R.string.movement_amount_accessibility_total,
        formatEuroCents(movement.amountCents),
    )

    val isLast = position == MovementRowPosition.LAST || position == MovementRowPosition.ONLY

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = MOVEMENT_ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdentityIconTile(
                icon = visual.first,
                color = visual.second,
                size = MOVEMENT_ICON_SIZE,
            )
            Spacer(modifier = Modifier.width(MOVEMENT_ICON_GAP))

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
                    // A person's mark already says the money was someone else's. When the user
                    // paid for a shared expense there is no such mark, so the badge is the only
                    // thing left to say it was split.
                    if (movement.isShared && movement.userShareCents != 0L) {
                        BadgeIcon(
                            Icons.Outlined.Group,
                            stringResource(R.string.movement_shared_badge),
                        )
                    }
                    if (movement.isRecurring) {
                        BadgeIcon(
                            Icons.Outlined.Repeat,
                            stringResource(R.string.movement_recurring_badge),
                        )
                    }
                    if (movement.isOneTime) {
                        // Extraordinary expense: alert-tinted starburst so it stands apart.
                        BadgeIcon(
                            icon = Icons.Outlined.NewReleases,
                            contentDescription = stringResource(R.string.movement_one_time_badge),
                            tint = FinanceTheme.colors.alert,
                            size = 15.dp,
                        )
                    }
                }

                // The qualifying line: when it happened, whose money moved, and whatever else the
                // movement carries. It wraps once when the segments do not fit, and stops there;
                // anything past two lines is detail the movement page shows in full.
                val showsDate = showDate && movement.date.isNotBlank()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxLines = META_MAX_LINES,
                ) {
                    if (showsDate) {
                        Text(
                            text = formatCompactDateRelative(movement.date),
                            color = FinanceTheme.colors.subtleText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                    val showsSource = MovementSource(movement)
                    if (detail.isNotEmpty()) {
                        if (showsDate || showsSource) MetaSeparator()
                        Text(
                            text = detail,
                            color = FinanceTheme.colors.subtleText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        style = MaterialTheme.typography.titleSmall,
                        signed = true,
                    )
                } else if (accountDeltaCents != null) {
                    AccountDeltaAmount(movement, accountDeltaCents, typeColor)
                } else {
                    val isShared = movement.isShared && movement.type == MovementType.EXPENSE
                    val isSharedIncome = movement.isShared && movement.type == MovementType.INCOME
                    val isExternal = movement.type == MovementType.EXTERNAL_EXPENSE

                    if (isShared || isSharedIncome || isExternal) {
                        // My share leads. An expense's share is money leaving, so it carries the
                        // leading minus every other expense row shows; an income's share arrives.
                        val shareCents = when {
                            isExternal -> -movement.amountCents
                            isSharedIncome -> movement.userShareCents
                            else -> -movement.userShareCents
                        }
                        MoneyText(
                            cents = shareCents,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = amountAccessibilityDescription
                            },
                            color = when {
                                isExternal -> FinanceTheme.colors.debt
                                isSharedIncome -> FinanceTheme.colors.income
                                else -> FinanceTheme.colors.shared
                            },
                            style = MaterialTheme.typography.titleSmall,
                            signed = isSharedIncome,
                        )
                        // The total only earns a line when it differs from the share above it.
                        if (abs(shareCents) != movement.amountCents) {
                            Text(
                                text = stringResource(
                                    R.string.movement_total_short,
                                    formatEuroCents(movement.amountCents),
                                ),
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = totalAccessibilityDescription
                                },
                                color = FinanceTheme.colors.mutedText,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.End,
                            )
                        }
                    } else {
                        MoneyText(
                            cents = movement.signedAmountCents(),
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = amountAccessibilityDescription
                            },
                            color = typeColor,
                            style = MaterialTheme.typography.titleSmall,
                            signed = movement.type == MovementType.INCOME ||
                                movement.type == MovementType.SETTLEMENT,
                        )
                    }
                }
            }
        }

        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = MOVEMENT_ICON_SIZE + MOVEMENT_ICON_GAP),
                color = FinanceTheme.colors.cardBorder,
            )
        }
    }
}

/**
 * An account ledger's figure: what the movement did to that account's balance, signed like any
 * other flow, with the user's own share captioned beneath a shared expense when it differs.
 */
@Composable
private fun AccountDeltaAmount(movement: MovementSummary, deltaCents: Long, color: Color) {
    val deltaText = formatEuroCents(deltaCents).let { if (deltaCents > 0) "+$it" else it }
    val deltaDescription = stringResource(R.string.movement_amount_accessibility_account, deltaText)
    MoneyText(
        cents = deltaCents,
        modifier = Modifier.clearAndSetSemantics { contentDescription = deltaDescription },
        color = color,
        style = MaterialTheme.typography.titleSmall,
        signed = true,
    )
    val shareCents = movement.userShareCents
    if (movement.secondaryAmountRole(inAccount = true) == MovementAmountRole.YOUR_SHARE &&
        shareCents >= 0 && shareCents != abs(deltaCents)
    ) {
        val shareText = formatEuroCents(shareCents)
        val shareDescription = stringResource(R.string.movement_amount_accessibility_your_share_short, shareText)
        Text(
            text = stringResource(R.string.movement_your_share_short, shareText),
            modifier = Modifier.clearAndSetSemantics { contentDescription = shareDescription },
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Where the money sat. One slot, whatever filled it: the user's own account, both accounts of a
 * transfer, or the person who paid instead of the user. A person wears an icon rather than the
 * words "paid by": the mark says it is not an account of yours, and the tint says the money is
 * owed. Returns whether anything was drawn, so the line knows if a separator is due.
 */
@Composable
private fun MovementSource(movement: MovementSummary): Boolean {
    when (movement.type) {
        MovementType.TRANSFER -> {
            MovementRoute(
                fromText = movement.accountName.orEmpty(),
                fromTint = movement.accountColor,
                toText = movement.destinationAccountName
                    ?: stringResource(R.string.movement_destination_missing),
                toTint = movement.destinationAccountColor,
            )
            return true
        }
        MovementType.CONTRIBUTION -> {
            // A contribution runs from the member into the shared account and a withdrawal runs back
            // out. A person wears a person's mark; the owner is named by their own account on the
            // other side, or plainly when the money came from or went outside their accounts.
            val person = movement.paidByPersonName?.takeIf { movement.payerId != null }
            val memberText = person ?: movement.accountName ?: stringResource(R.string.account_member_owner)
            val memberTint = if (person == null) movement.accountColor else null
            val memberIcon = if (person != null) Icons.Outlined.Group else null
            val memberFallbackTint = if (person != null) FinanceTheme.colors.shared else null
            val sharedText = movement.destinationAccountName
                ?: stringResource(R.string.movement_destination_missing)
            if (movement.contributionDirection == ContributionDirection.OUT) {
                MovementRoute(
                    fromText = sharedText,
                    fromTint = movement.destinationAccountColor,
                    toText = memberText,
                    toTint = memberTint,
                    toIcon = memberIcon,
                    toFallbackTint = memberFallbackTint,
                )
            } else {
                MovementRoute(
                    fromText = memberText,
                    fromTint = memberTint,
                    toText = sharedText,
                    toTint = movement.destinationAccountColor,
                    fromIcon = memberIcon,
                    fromFallbackTint = memberFallbackTint,
                )
            }
            return true
        }
        MovementType.EXTERNAL_EXPENSE -> {
            IdentityLabel(
                text = movement.paidByPersonName.orEmpty(),
                tint = null,
                fallbackTint = FinanceTheme.colors.debt,
                icon = Icons.Outlined.Handshake,
            )
            return true
        }
        else -> {
            // A shared expense the user did not pay for is the same situation as an external one:
            // a person's money, so it wears a person's mark.
            val payer = movement.paidByPersonName?.takeIf {
                it.isNotEmpty() && movement.isShared && movement.userShareCents == 0L
            }
            if (payer != null) {
                IdentityLabel(
                    text = payer,
                    tint = null,
                    fallbackTint = FinanceTheme.colors.shared,
                    icon = Icons.Outlined.Group,
                )
                return true
            }
            val account = movement.accountName?.takeIf { it.isNotEmpty() } ?: return false
            IdentityLabel(text = account, tint = movement.accountColor)
            return true
        }
    }
}

/** Where money went from and to, as two identity labels either side of an arrow. */
@Composable
private fun MovementRoute(
    fromText: String,
    fromTint: String?,
    toText: String,
    toTint: String?,
    fromIcon: ImageVector? = null,
    fromFallbackTint: Color? = null,
    toIcon: ImageVector? = null,
    toFallbackTint: Color? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        IdentityLabel(
            text = fromText,
            tint = fromTint,
            fallbackTint = fromFallbackTint,
            icon = fromIcon,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowRightAlt,
            contentDescription = null,
            tint = FinanceTheme.colors.mutedText,
            modifier = Modifier.size(14.dp),
        )
        IdentityLabel(
            text = toText,
            tint = toTint,
            fallbackTint = toFallbackTint,
            icon = toIcon,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * A named entity on the qualifying line: a small mark in the entity's own saved colour followed
 * by its plain name. The mark is what tells two accounts apart at a glance; the name stays plain
 * text so the line reads as one phrase instead of a run of containers.
 */
@Composable
private fun IdentityLabel(
    text: String,
    tint: String?,
    modifier: Modifier = Modifier,
    fallbackTint: Color? = null,
    icon: ImageVector? = null,
) {
    val base = tint?.takeIf { it.isNotBlank() }?.let { categoryColorFromTheme(it) }
        ?: fallbackTint
        ?: FinanceTheme.colors.mutedText
    val mark = dataMarkColor(base)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mark,
                modifier = Modifier.size(IDENTITY_ICON_SIZE),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(IDENTITY_DOT_SIZE)
                    .background(mark, CircleShape),
            )
        }
        Text(
            text = text,
            color = FinanceTheme.colors.subtleText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The middle dot that parts the qualifying line's segments. */
@Composable
private fun MetaSeparator() {
    Text(
        text = "·",
        color = FinanceTheme.colors.subtleText,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun MovementSummary.primaryAmountContentDescription(): String {
    if (primaryAmountRole() == MovementAmountRole.YOUR_SHARE) {
        val shareCents = if (type == MovementType.EXTERNAL_EXPENSE) amountCents else userShareCents
        val shareText = formatEuroCents(shareCents)
        val direction = when {
            type == MovementType.EXTERNAL_EXPENSE ->
                stringResource(R.string.movement_amount_accessibility_owes, shareText)
            // An income is shared, not owed: say whose larger income the part comes from.
            type == MovementType.INCOME ->
                stringResource(R.string.movement_amount_accessibility_income_part, formatEuroCents(amountCents))
            userShareCents == 0L ->
                stringResource(R.string.movement_amount_accessibility_owed, formatEuroCents(amountCents))
            else ->
                stringResource(R.string.movement_amount_accessibility_assumed)
        }
        return stringResource(
            R.string.movement_amount_accessibility_your_share,
            shareText,
            direction,
        )
    }

    val amountText = formatEuroCents(signedAmountCents())
    return when (type) {
        MovementType.EXPENSE ->
            stringResource(R.string.movement_amount_accessibility_expense, amountText)
        MovementType.INCOME ->
            stringResource(R.string.movement_amount_accessibility_income, amountText)
        MovementType.TRANSFER ->
            stringResource(R.string.movement_amount_accessibility_transfer, amountText)
        MovementType.SETTLEMENT -> if (settlementDirection == SettlementDirection.USER_TO_PERSON) {
            stringResource(R.string.movement_amount_accessibility_settlement_out, amountText)
        } else {
            stringResource(R.string.movement_amount_accessibility_settlement_in, amountText)
        }
        MovementType.REFUND ->
            stringResource(R.string.movement_amount_accessibility_refund, amountText)
        MovementType.EXTERNAL_EXPENSE -> error("External expense is a shared amount")
        MovementType.CONTRIBUTION ->
            stringResource(R.string.movement_amount_accessibility_transfer, amountText)
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
    name ?: payee ?: categoryName ?: if (contributionDirection == ContributionDirection.OUT) {
        stringResource(R.string.movement_type_withdrawal)
    } else {
        type.label()
    }

/**
 * The rest of the qualifying line: everything only some movements carry - the category, the trip,
 * the tag, and the person a settlement was with. The category is named but never tinted, because
 * its icon is already sitting in that exact colour.
 */
@Composable
internal fun MovementSummary.detailLine(): String {
    val parts = mutableListOf<String>()
    if (type == MovementType.SETTLEMENT) settlementContext()?.let { parts += it }
    categoryName?.takeIf { it.isNotEmpty() }?.let { parts += it }
    tripName?.takeIf { it.isNotEmpty() }?.let { parts += it }
    tagName?.takeIf { it.isNotEmpty() }?.let { parts += it }
    return parts.joinToString(DETAIL_SEPARATOR)
}

/** The line never wraps, so the separator only has to read as a pause between names. */
private const val DETAIL_SEPARATOR = " · "

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
                // Someone else's money is said by the person's mark and the amount's colour; the
                // tile keeps naming the category, exactly as it does for an expense you paid.
                categoryIcon(this.categoryIcon) to categoryColorFromTheme(this.categoryColor)
            } else {
                movementTypeIcon(type) to finance.debt
            }
        MovementType.CONTRIBUTION -> movementTypeIcon(type) to finance.transfer
    }
}
