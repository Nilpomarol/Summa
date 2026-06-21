package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
fun SplitEditorCard(
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    amountInput: String,
    onChange: (SplitEditorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCents = remember(amountInput) {
        parseEuroCents(amountInput, allowNegative = false)
    }
    val calculation = splitEditor.calculation(totalCents)

    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SplitEditorHeader(splitEditor = splitEditor, calculation = calculation)
            SplitVariantSection(
                splitEditor = splitEditor,
                people = people,
                onChange = onChange,
            )
            if (splitEditor.paidByPersonId == null) {
                SegmentedControl(
                    options = SplitEntryMethod.entries,
                    selected = splitEditor.method,
                    label = { it.label() },
                    onSelect = { onChange(splitEditor.withMethod(it)) },
                )
                ChipFlowSection(label = stringResource(R.string.split_participants_title)) {
                    people.forEach { person ->
                        FinanceFilterChip(
                            selected = person.id in splitEditor.selectedPersonIds,
                            label = person.name,
                            onClick = { onChange(splitEditor.withPersonToggled(person.id)) },
                        )
                    }
                }
                ChipFlowSection(label = stringResource(R.string.split_payer_title)) {
                    splitEditor.participantIds.forEach { participantId ->
                        FinanceFilterChip(
                            selected = splitEditor.payerParticipantId == participantId,
                            label = participantLabel(participantId, people),
                            onClick = { onChange(splitEditor.withPayer(participantId)) },
                        )
                    }
                }
            } else {
                val paidByName = participantLabel(splitEditor.paidByPersonId, people)
                InlineBanner(
                    kind = BannerKind.Info,
                    text = stringResource(R.string.split_variant_paid_by_other, paidByName),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                splitEditor.participantIds.forEach { participantId ->
                    SplitParticipantRow(
                        participantId = participantId,
                        splitEditor = splitEditor,
                        people = people,
                        calculation = calculation,
                        onChange = onChange,
                    )
                }
            }
            if (splitEditor.method == SplitEntryMethod.EQUAL && splitEditor.paidByPersonId == null) {
                Text(
                    text = stringResource(R.string.split_remainder_to_payer),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InlineBanner(
                kind = if (calculation.valid) BannerKind.Info else BannerKind.Alert,
                text = calculation.reconcileText(),
            )
        }
    }
}

@Composable
private fun SplitVariantSection(
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    onChange: (SplitEditorState) -> Unit,
) {
    if (people.isEmpty()) return

    ChipFlowSection(label = stringResource(R.string.split_paid_by_other_title)) {
        FinanceFilterChip(
            selected = splitEditor.paidByPersonId == null,
            label = stringResource(R.string.split_variant_manual),
            onClick = { onChange(splitEditor.withManualSplit()) },
        )
        people.forEach { person ->
            FinanceFilterChip(
                selected = splitEditor.paidByPersonId == person.id,
                label = stringResource(R.string.split_variant_paid_by_other, person.name),
                onClick = { onChange(splitEditor.withPaidByOther(person.id)) },
            )
        }
    }
}

@Composable
private fun SplitEditorHeader(
    splitEditor: SplitEditorState,
    calculation: SplitEditorCalculation,
) {
    val userShare = calculation.sharesCentsByParticipantId[USER_PARTICIPANT_ID]
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.split_editor_shared_expense_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (userShare != null) {
            Text(
                text = stringResource(
                    R.string.movement_split_summary,
                    splitEditor.participantIds.size,
                    formatEuroCents(userShare),
                ),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(R.string.movement_field_shared_support),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SplitParticipantRow(
    participantId: String,
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    calculation: SplitEditorCalculation,
    onChange: (SplitEditorState) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = participantLabel(participantId, people),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (splitEditor.paidByPersonId != null) {
            MoneyText(
                cents = calculation.sharesCentsByParticipantId[participantId] ?: 0L,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else when (splitEditor.method) {
            SplitEntryMethod.EQUAL -> MoneyText(
                cents = calculation.sharesCentsByParticipantId[participantId] ?: 0L,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            SplitEntryMethod.EXACT -> OutlinedTextField(
                value = splitEditor.exactAmounts[participantId].orEmpty(),
                onValueChange = { onChange(splitEditor.withExactAmount(participantId, it)) },
                label = {
                    Text(text = participantShareLabel(participantId, people))
                },
                prefix = { Text(text = "€") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(148.dp),
            )
            SplitEntryMethod.PERCENTAGE -> OutlinedTextField(
                value = splitEditor.percentages[participantId].orEmpty(),
                onValueChange = { onChange(splitEditor.withPercentage(participantId, it)) },
                label = { Text(text = stringResource(R.string.split_field_percent)) },
                suffix = { Text(text = "%") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(116.dp),
            )
        }
        if (participantId != USER_PARTICIPANT_ID) {
            IconButton(
                onClick = { onChange(splitEditor.withPersonToggled(participantId)) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.split_remove_person),
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun SplitEntryMethod.label(): String =
    when (this) {
        SplitEntryMethod.EQUAL -> stringResource(R.string.split_method_equal)
        SplitEntryMethod.EXACT -> stringResource(R.string.split_method_exact)
        SplitEntryMethod.PERCENTAGE -> stringResource(R.string.split_method_percentage)
    }

@Composable
private fun participantLabel(
    participantId: String,
    people: List<PersonSummary>,
): String =
    if (participantId == USER_PARTICIPANT_ID) {
        stringResource(R.string.split_payer_user)
    } else {
        people.firstOrNull { it.id == participantId }?.name ?: participantId
    }

@Composable
private fun participantShareLabel(
    participantId: String,
    people: List<PersonSummary>,
): String =
    if (participantId == USER_PARTICIPANT_ID) {
        stringResource(R.string.split_field_user_share)
    } else {
        stringResource(R.string.split_field_person_share, participantLabel(participantId, people))
    }

@Composable
private fun SplitEditorCalculation.reconcileText(): String =
    when {
        valid -> stringResource(R.string.split_reconcile_balanced)
        errorRes != null -> stringResource(errorRes)
        deltaCents != null && deltaCents > 0L -> stringResource(
            R.string.split_reconcile_remaining,
            formatEuroCents(deltaCents),
        )
        deltaCents != null && deltaCents < 0L -> stringResource(
            R.string.split_reconcile_over,
            formatEuroCents(-deltaCents),
        )
        deltaBasisPoints != null && deltaBasisPoints > 0L -> stringResource(
            R.string.split_reconcile_remaining,
            formatBasisPoints(deltaBasisPoints),
        )
        deltaBasisPoints != null && deltaBasisPoints < 0L -> stringResource(
            R.string.split_reconcile_over,
            formatBasisPoints(-deltaBasisPoints),
        )
        else -> stringResource(R.string.split_validation_reconcile)
    }
