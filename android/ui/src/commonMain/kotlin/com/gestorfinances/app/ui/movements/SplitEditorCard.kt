package com.gestorfinances.app.ui.movements

import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.gestorfinances.app.ui.common.AppIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_create_person_title
import com.gestorfinances.ui.resources.movement_forwhom_label
import com.gestorfinances.ui.resources.movement_forwhom_other
import com.gestorfinances.ui.resources.movement_forwhom_owes
import com.gestorfinances.ui.resources.movement_forwhom_personal
import com.gestorfinances.ui.resources.movement_forwhom_pick_person
import com.gestorfinances.ui.resources.movement_forwhom_shared
import com.gestorfinances.ui.resources.movement_split_summary_account
import com.gestorfinances.ui.resources.movement_split_summary_income
import com.gestorfinances.ui.resources.split_add_person
import com.gestorfinances.ui.resources.split_method_equal
import com.gestorfinances.ui.resources.split_method_exact
import com.gestorfinances.ui.resources.split_method_percentage
import com.gestorfinances.ui.resources.split_payer_user
import com.gestorfinances.ui.resources.split_reconcile_balanced
import com.gestorfinances.ui.resources.split_reconcile_over
import com.gestorfinances.ui.resources.split_reconcile_remaining
import com.gestorfinances.ui.resources.split_remainder_to_payer
import com.gestorfinances.ui.resources.split_remainder_to_user
import com.gestorfinances.ui.resources.split_remove_person
import com.gestorfinances.ui.resources.split_validation_reconcile
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.ui.common.CreatePersonDialog
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme

private const val ADD_CREATE_PERSON = "__create_person__"

/**
 * How a shared amount is divided: who takes part, each on a row with their share and a way out,
 * a row to add someone, then how it is divided (equal parts, exact amounts or percentages, the
 * fields already filled with an equal split to adjust) and a line only when it does not add up.
 *
 * [lockedIds] are people who cannot be taken out (whoever paid); the owner never can.
 */
@Composable
fun SplitEditorCard(
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    amountInput: String,
    onChange: (SplitEditorState) -> Unit,
    modifier: Modifier = Modifier,
    onCreatePerson: (String) -> Unit = {},
    // A shared account financed the expense: nobody paid it, so there is no payer to choose.
    fundedByAccount: Boolean = false,
    // An income into a shared account is being allocated: nobody paid either, and the header names
    // the owner's part of the income instead of an expense.
    accountIncome: Boolean = false,
    lockedIds: Set<String> = emptySet(),
) {
    val totalCents = remember(amountInput) { parseEuroCents(amountInput, allowNegative = false) }
    val calculation = splitEditor.calculation(totalCents)
    val colors = FinanceTheme.colors
    var showCreatePersonDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val userShare = calculation.sharesCentsByParticipantId[USER_PARTICIPANT_ID]
        // What a shared account's movement means for the owner, which the rows cannot say.
        when {
            fundedByAccount && userShare != null && totalCents != null -> stringResource(
                Res.string.movement_split_summary_account,
                formatEuroCents(-totalCents),
                formatEuroCents(userShare),
            )
            accountIncome && userShare != null -> stringResource(Res.string.movement_split_summary_income, formatEuroCents(userShare))
            else -> null
        }?.let { summary ->
            Text(text = summary, color = colors.mutedText, style = MaterialTheme.typography.bodyMedium)
        }

        Column {
            splitEditor.participantIds.forEach { participantId ->
                SplitParticipantRow(
                    participantId = participantId,
                    splitEditor = splitEditor,
                    people = people,
                    calculation = calculation,
                    totalCents = totalCents,
                    removable = participantId != USER_PARTICIPANT_ID && participantId !in lockedIds,
                    onChange = onChange,
                )
                HorizontalDivider(color = colors.cardBorder)
            }
            Spacer(modifier = Modifier.height(10.dp))
            AddParticipantSelect(
                splitEditor = splitEditor,
                people = people,
                onAdd = { onChange(splitEditor.withPersonToggled(it).evenlyFilled(totalCents)) },
                onCreatePerson = { showCreatePersonDialog = true },
            )
        }

        SegmentedControl(
            options = SplitEntryMethod.entries,
            selected = splitEditor.method,
            label = { it.label() },
            onSelect = { onChange(splitEditor.withMethod(it).evenlyFilled(totalCents)) },
        )

        // Said only when something is off: a balanced split needs no comment.
        if (!calculation.valid && (totalCents ?: 0L) > 0L) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = colors.alert,
                    modifier = Modifier.size(16.dp),
                )
                Text(text = calculation.reconcileText(), color = colors.alert, style = MaterialTheme.typography.bodySmall)
            }
        } else if (splitEditor.method == SplitEntryMethod.EQUAL && totalCents != null && totalCents % splitEditor.participantIds.size != 0L) {
            Text(
                text = stringResource(
                    if (fundedByAccount || accountIncome) Res.string.split_remainder_to_user else Res.string.split_remainder_to_payer,
                ),
                color = colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showCreatePersonDialog) {
        CreatePersonDialog(
            onConfirm = {
                onCreatePerson(it)
                showCreatePersonDialog = false
            },
            onDismiss = { showCreatePersonDialog = false },
        )
    }
}

/**
 * Whose expense it is, as three plain choices: only the owner's, shared (the split below then says
 * between whom), or all for one other person, who is picked right there and then owes it.
 */
@Composable
fun ForWhomFields(
    kind: ExpenseKind,
    otherPersonId: String?,
    people: List<PersonSummary>,
    amountInput: String,
    onKindSelected: (ExpenseKind) -> Unit,
    onOtherPersonSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LabeledSegmentedControl(
            label = stringResource(Res.string.movement_forwhom_label),
            // Paying for someone needs a someone.
            options = listOfNotNull(ExpenseKind.PERSONAL, ExpenseKind.SHARED, ExpenseKind.FOR_OTHER.takeIf { people.isNotEmpty() }),
            selected = kind,
            optionLabel = {
                stringResource(
                    when (it) {
                        ExpenseKind.SHARED -> Res.string.movement_forwhom_shared
                        ExpenseKind.FOR_OTHER -> Res.string.movement_forwhom_other
                        else -> Res.string.movement_forwhom_personal
                    },
                )
            },
            onSelect = onKindSelected,
        )
        if (kind == ExpenseKind.FOR_OTHER) {
            val person = people.firstOrNull { it.id == otherPersonId }
            FormSelect(
                label = "",
                options = people.map { option ->
                    SelectOption(
                        id = option.id,
                        label = option.name,
                        leading = { PersonMonogram(personInitial(option.name), option.color, size = 24.dp) },
                    )
                },
                selectedId = otherPersonId,
                onSelect = { id -> id?.let(onOtherPersonSelected) },
                placeholder = stringResource(Res.string.movement_forwhom_pick_person),
                isError = errorText != null,
                supportingText = errorText,
            )
            val total = parseEuroCents(amountInput, allowNegative = false)?.takeIf { it > 0L }
            if (person != null && total != null) {
                Text(
                    text = stringResource(Res.string.movement_forwhom_owes, person.name, formatEuroCents(total)),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else if (errorText != null) {
            Text(text = errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Adds a not-yet-included person, or opens the inline create-person dialog. No value held. */
@Composable
private fun AddParticipantSelect(
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    onAdd: (String) -> Unit,
    onCreatePerson: () -> Unit,
) {
    val addable = people.filter { it.id !in splitEditor.selectedPersonIds }
    FormSelect(
        label = "",
        options = buildList {
            addable.forEach { person ->
                add(
                    SelectOption(
                        id = person.id,
                        label = person.name,
                        leading = { PersonMonogram(personInitial(person.name), person.color, size = 24.dp) },
                    ),
                )
            }
            add(
                SelectOption(
                    id = ADD_CREATE_PERSON,
                    label = stringResource(Res.string.movement_create_person_title),
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                ),
            )
        },
        selectedId = null,
        onSelect = { id ->
            when (id) {
                ADD_CREATE_PERSON -> onCreatePerson()
                null -> Unit
                else -> onAdd(id)
            }
        },
        placeholder = stringResource(Res.string.split_add_person),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Someone in the split: who, their share (a field, already filled, unless parts are equal), and a way out. */
@Composable
private fun SplitParticipantRow(
    participantId: String,
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    calculation: SplitEditorCalculation,
    totalCents: Long?,
    removable: Boolean,
    onChange: (SplitEditorState) -> Unit,
) {
    val person = people.firstOrNull { it.id == participantId }
    val name = if (participantId == USER_PARTICIPANT_ID) stringResource(Res.string.split_payer_user) else person?.name ?: participantId
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PersonMonogram(label = personInitial(name), colorHex = person?.color, size = 32.dp)
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (splitEditor.method) {
            SplitEntryMethod.EQUAL -> MoneyText(
                cents = calculation.sharesCentsByParticipantId[participantId] ?: 0L,
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                val exact = splitEditor.method == SplitEntryMethod.EXACT
                ShareField(
                    value = if (exact) splitEditor.exactAmounts[participantId].orEmpty() else splitEditor.percentages[participantId].orEmpty(),
                    onValueChange = {
                        onChange(
                            if (exact) splitEditor.withExactAmountBalanced(participantId, it, totalCents)
                            else splitEditor.withPercentageBalanced(participantId, it),
                        )
                    },
                    suffix = if (exact) "€" else "%",
                )
            }
        }
        if (removable) {
            AppIconButton(
                onClick = { onChange(splitEditor.withPersonToggled(participantId).evenlyFilled(totalCents)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.split_remove_person),
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            // Keeps every share in one column whether or not its row can be removed.
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * A share typed in place: the figure as the row shows it when parts are equal, on a soft chip that
 * says it can be changed, rather than a form field of its own inside the row.
 */
@Composable
fun ShareField(value: String, onValueChange: (String) -> Unit, suffix: String, width: Dp = 104.dp) {
    val style = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = nextFieldKeyboardActions(),
        modifier = Modifier.width(width),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { innerTextField() }
                Text(text = suffix, style = style, color = FinanceTheme.colors.mutedText)
            }
        },
    )
}

@Composable
private fun SplitEntryMethod.label(): String =
    when (this) {
        SplitEntryMethod.EQUAL -> stringResource(Res.string.split_method_equal)
        SplitEntryMethod.EXACT -> stringResource(Res.string.split_method_exact)
        SplitEntryMethod.PERCENTAGE -> stringResource(Res.string.split_method_percentage)
    }

@Composable
private fun SplitEditorCalculation.reconcileText(): String =
    when {
        valid -> stringResource(Res.string.split_reconcile_balanced)
        errorRes != null -> stringResource(errorRes!!)
        deltaCents != null && deltaCents!! > 0L -> stringResource(
            Res.string.split_reconcile_remaining,
            formatEuroCents(deltaCents!!),
        )
        deltaCents != null && deltaCents!! < 0L -> stringResource(
            Res.string.split_reconcile_over,
            formatEuroCents(-deltaCents!!),
        )
        deltaBasisPoints != null && deltaBasisPoints!! > 0L -> stringResource(
            Res.string.split_reconcile_remaining,
            formatBasisPoints(deltaBasisPoints!!),
        )
        deltaBasisPoints != null && deltaBasisPoints!! < 0L -> stringResource(
            Res.string.split_reconcile_over,
            formatBasisPoints(-deltaBasisPoints!!),
        )
        else -> stringResource(Res.string.split_validation_reconcile)
    }
