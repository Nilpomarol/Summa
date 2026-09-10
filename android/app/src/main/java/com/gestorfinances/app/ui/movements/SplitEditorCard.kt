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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme

private const val ADD_CREATE_PERSON = "__create_person__"

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
) {
    val totalCents = remember(amountInput) {
        parseEuroCents(amountInput, allowNegative = false)
    }
    val calculation = splitEditor.calculation(totalCents)
    var showCreatePersonDialog by remember { mutableStateOf(false) }

    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SplitSummaryHeader(
                splitEditor = splitEditor,
                calculation = calculation,
                totalCents = totalCents,
                fundedByAccount = fundedByAccount,
            )

            SegmentedControl(
                options = SplitEntryMethod.entries,
                selected = splitEditor.method,
                label = { it.label() },
                onSelect = { onChange(splitEditor.withMethod(it)) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.split_participants_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.mutedText,
                )
                splitEditor.participantIds.forEach { participantId ->
                    SplitParticipantRow(
                        participantId = participantId,
                        splitEditor = splitEditor,
                        people = people,
                        calculation = calculation,
                        onChange = onChange,
                        readOnly = false,
                    )
                }
            }

            AddParticipantSelect(
                splitEditor = splitEditor,
                people = people,
                onAdd = { onChange(splitEditor.withPersonToggled(it)) },
                onCreatePerson = { showCreatePersonDialog = true },
            )

            if (!fundedByAccount && splitEditor.participantIds.size > 1) {
                PayerSelect(
                    splitEditor = splitEditor,
                    people = people,
                    onChange = onChange,
                )
            }

            if (splitEditor.method == SplitEntryMethod.EQUAL) {
                Text(
                    text = stringResource(
                        if (fundedByAccount) R.string.split_remainder_to_user else R.string.split_remainder_to_payer,
                    ),
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

@Composable
private fun SplitSummaryHeader(
    splitEditor: SplitEditorState,
    calculation: SplitEditorCalculation,
    totalCents: Long?,
    fundedByAccount: Boolean,
) {
    val userShare = calculation.sharesCentsByParticipantId[USER_PARTICIPANT_ID]
    Text(
        text = when {
            // The two numbers a shared-account expense means: what leaves the account, and how much
            // of it is the user's own spending.
            fundedByAccount && userShare != null && totalCents != null -> stringResource(
                R.string.movement_split_summary_account,
                formatEuroCents(-totalCents),
                formatEuroCents(userShare),
            )
            userShare != null -> stringResource(
                R.string.movement_split_summary,
                splitEditor.participantIds.size,
                formatEuroCents(userShare),
            )
            else -> stringResource(R.string.movement_field_shared_support)
        },
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** "Qui ha pagat": which participant advanced the money (rounding remainder lands on them). */
@Composable
private fun PayerSelect(
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    onChange: (SplitEditorState) -> Unit,
) {
    FormSelect(
        label = stringResource(R.string.split_payer_title),
        options = splitEditor.participantIds.map { participantId ->
            SelectOption(
                id = participantId,
                label = participantLabel(participantId, people),
                leading = {
                    PersonMonogram(
                        label = participantInitial(participantId, people),
                        colorHex = participantColor(participantId, people),
                        size = 24.dp,
                    )
                },
            )
        },
        selectedId = splitEditor.payerParticipantId,
        onSelect = { id -> id?.let { onChange(splitEditor.withPayer(it)) } },
    )
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
                        leading = {
                            PersonMonogram(
                                label = personInitial(person.name),
                                colorHex = person.color,
                                size = 24.dp,
                            )
                        },
                    ),
                )
            }
            add(
                SelectOption(
                    id = ADD_CREATE_PERSON,
                    label = stringResource(R.string.movement_create_person_title),
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
        placeholder = stringResource(R.string.split_add_person),
    )
}

@Composable
private fun SplitParticipantRow(
    participantId: String,
    splitEditor: SplitEditorState,
    people: List<PersonSummary>,
    calculation: SplitEditorCalculation,
    onChange: (SplitEditorState) -> Unit,
    readOnly: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PersonMonogram(
            label = participantInitial(participantId, people),
            colorHex = participantColor(participantId, people),
            size = 32.dp,
        )
        Text(
            text = participantLabel(participantId, people),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (readOnly || splitEditor.method == SplitEntryMethod.EQUAL) {
            MoneyText(
                cents = calculation.sharesCentsByParticipantId[participantId] ?: 0L,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else when (splitEditor.method) {
            SplitEntryMethod.EXACT -> OutlinedTextField(
                value = splitEditor.exactAmounts[participantId].orEmpty(),
                onValueChange = { onChange(splitEditor.withExactAmount(participantId, it)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldKeyboardActions(),
                modifier = Modifier.width(120.dp),
            )
            SplitEntryMethod.PERCENTAGE -> OutlinedTextField(
                value = splitEditor.percentages[participantId].orEmpty(),
                onValueChange = { onChange(splitEditor.withPercentage(participantId, it)) },
                suffix = { Text(text = "%") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldKeyboardActions(),
                modifier = Modifier.width(96.dp),
            )
            SplitEntryMethod.EQUAL -> Unit
        }
        if (!readOnly && participantId != USER_PARTICIPANT_ID) {
            IconButton(onClick = { onChange(splitEditor.withPersonToggled(participantId)) }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.split_remove_person),
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else if (!readOnly) {
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun CreatePersonDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var personName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.movement_create_person_title)) },
        text = {
            OutlinedTextField(
                value = personName,
                onValueChange = { personName = it },
                label = { Text(stringResource(R.string.movement_create_person_name_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (personName.isNotBlank()) onConfirm(personName.trim()) },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
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
private fun participantInitial(
    participantId: String,
    people: List<PersonSummary>,
): String = personInitial(participantLabel(participantId, people))

private fun participantColor(
    participantId: String,
    people: List<PersonSummary>,
): String? =
    if (participantId == USER_PARTICIPANT_ID) {
        null
    } else {
        people.firstOrNull { it.id == participantId }?.color
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
