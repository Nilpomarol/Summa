package com.gestorfinances.app.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonBalanceItemType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.EntityColorPalette
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import androidx.compose.material3.rememberModalBottomSheetState
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatSlashDate
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.categoryTint

@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    onOpenDebtSource: (String) -> Unit,
    onAddDebtForPerson: (PersonSummary) -> Unit,
    onMessageCopied: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    val detail = state.detail
    val settlementForm = state.settlementForm
    when {
        settlementForm != null && detail != null -> {
            BackHandler(onBack = viewModel::onSettlementDismissed)
            SettlementScreen(
                form = settlementForm,
                accounts = state.accounts,
                onFormChange = viewModel::onSettlementFormChanged,
                onBack = viewModel::onSettlementDismissed,
                onSave = viewModel::onSettlementSaveClicked,
                modifier = modifier,
            )
        }
        detail != null -> {
            BackHandler(onBack = viewModel::onPersonDetailDismissed)
            PersonDetailScreen(
                detail = detail,
                onBack = viewModel::onPersonDetailDismissed,
                onOpenDebtSource = onOpenDebtSource,
                onExternalSplit = {
                    viewModel.onPersonDetailDismissed()
                    onAddDebtForPerson(detail.person)
                },
                onSettleUp = { viewModel.onSettleUpClicked(detail.person) },
                onEdit = {
                    viewModel.onPersonDetailDismissed()
                    viewModel.onEditClicked(detail.person)
                },
                onCopyMessageClicked = viewModel::onCopyMessageClicked,
                onCopyMessageHandled = viewModel::onCopyMessageHandled,
                onMessageCopied = onMessageCopied,
                modifier = modifier,
            )
        }
        else -> {
            PeopleContent(
                state = state,
                modifier = modifier,
                onAdd = viewModel::onAddClicked,
                onEdit = viewModel::onEditClicked,
                onArchive = viewModel::onArchiveClicked,
                onExternalSplit = onAddDebtForPerson,
                onOpenDetail = viewModel::onPersonDetailClicked,
            )
        }
    }

    state.form?.let { form ->
        PersonFormSheet(
            form = form,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
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

private const val PEOPLE_SUMMARY_MAX_ROWS = 5

@Composable
private fun PeopleSummaryCard(state: PeopleUiState) {
    val netCents = state.netBalanceCents
    val owedCents = state.totalOwedToUserCents
    val youOweCents = state.totalUserOwesCents
    val netColor = when {
        netCents > 0L -> FinanceTheme.colors.heroIncome
        netCents < 0L -> FinanceTheme.colors.heroDebt
        else -> FinanceTheme.colors.heroOnSurface
    }
    val nonZeroPeople = state.people.filter { it.balanceCents != 0L }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.person_list_net_balance),
                style = MaterialTheme.typography.labelMedium,
                color = FinanceTheme.colors.heroOnSurfaceMuted,
            )

            Spacer(modifier = Modifier.height(10.dp))

            MoneyText(
                cents = netCents,
                color = netColor,
                style = MaterialTheme.typography.displayMedium,
                signed = true,
            )

            if (owedCents > 0L || youOweCents > 0L) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PeopleSummaryStatChip(
                        label = stringResource(R.string.person_list_total_owed_to_user),
                        cents = owedCents,
                        color = FinanceTheme.colors.heroIncome,
                        modifier = Modifier.weight(1f),
                    )
                    PeopleSummaryStatChip(
                        label = stringResource(R.string.person_list_total_you_owe),
                        cents = youOweCents,
                        color = FinanceTheme.colors.heroDebt,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (nonZeroPeople.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = FinanceTheme.colors.heroOnSurface.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        nonZeroPeople.take(PEOPLE_SUMMARY_MAX_ROWS).forEach { person ->
                            PeopleSummaryPersonRow(person = person)
                        }
                        val remaining = nonZeroPeople.size - PEOPLE_SUMMARY_MAX_ROWS
                        if (remaining > 0) {
                            Text(
                                text = stringResource(R.string.person_summary_more, remaining),
                                style = MaterialTheme.typography.labelSmall,
                                color = FinanceTheme.colors.heroOnSurfaceMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleSummaryStatChip(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(categoryTint(color))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        Spacer(modifier = Modifier.height(2.dp))
        MoneyText(
            cents = cents,
            color = color,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PeopleSummaryPersonRow(person: PersonSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PersonAvatar(person = person, size = 26.dp)
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodyMedium,
            color = FinanceTheme.colors.heroOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MoneyText(
            cents = person.balanceCents,
            color = if (person.balanceCents > 0L) FinanceTheme.colors.heroIncome else FinanceTheme.colors.heroDebt,
            style = MaterialTheme.typography.titleSmall,
            signed = true,
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
    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(person = person)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = person.notes?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.person_latest_context_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    }
}

// ---------------------------------------------------------------------------
// Person detail — bottom sheet
// ---------------------------------------------------------------------------

@Composable
private fun PersonDetailScreen(
    detail: PersonDetailState,
    onBack: () -> Unit,
    onOpenDebtSource: (String) -> Unit,
    onExternalSplit: () -> Unit,
    onSettleUp: () -> Unit,
    onEdit: () -> Unit,
    onCopyMessageClicked: () -> Unit,
    onCopyMessageHandled: () -> Unit,
    onMessageCopied: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    detail.copyMessage?.let { message ->
        val text = message.toClipboardText(detail.person.name)
        val successMessage = stringResource(R.string.person_copy_success)
        LaunchedEffect(message) {
            clipboardManager.setText(AnnotatedString(text))
            onMessageCopied(successMessage)
            onCopyMessageHandled()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeaderRow(onBack = onBack)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonAvatar(person = detail.person)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.person.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail.person.balanceCents != 0L) {
                    PrimaryButton(
                        text = stringResource(R.string.person_action_settle_up),
                        onClick = onSettleUp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryActionButton(
                        icon = Icons.Outlined.Handshake,
                        label = stringResource(R.string.person_action_external_split),
                        onClick = onExternalSplit,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryActionButton(
                        icon = Icons.Outlined.ContentCopy,
                        label = stringResource(R.string.person_action_copy_message),
                        onClick = onCopyMessageClicked,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryActionButton(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.common_edit),
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    )
                }
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
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
            } else if (detail.history.isNotEmpty()) {
                Column {
                    detail.history.forEachIndexed { index, entry ->
                        MovementListItem(
                            movement = entry.movement,
                            onClick = { onOpenDebtSource(entry.item.sourceId) },
                            personEffectCents = entry.item.effectCents,
                        )
                        if (index < detail.history.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Resolves the copy-to-chat message content into the final Catalan text to place on the clipboard. */
@Composable
private fun PersonDebtMessage.toClipboardText(personName: String): String {
    if (direction == DebtMessageDirection.SETTLED) {
        return stringResource(R.string.person_copy_settled, personName)
    }

    val greeting = stringResource(
        if (direction == DebtMessageDirection.PERSON_OWES_USER) {
            R.string.person_copy_greeting_owed
        } else {
            R.string.person_copy_greeting_owe
        },
        personName,
    )

    val lines = mutableListOf<String>()
    items.forEach { item ->
        lines += "- ${formatSlashDate(item.date)} ${item.displayTitle(personName)}: " +
            formatEuroCents(kotlin.math.abs(item.effectCents))
    }
    carryForwardCents?.let { carryForward ->
        lines += "- " + stringResource(
            R.string.person_copy_carry_forward,
            formatEuroCents(kotlin.math.abs(carryForward)),
        )
    }

    val totalLine = stringResource(R.string.person_copy_total, formatEuroCents(kotlin.math.abs(totalCents)))

    return buildString {
        append(greeting)
        append("\n\n")
        append(lines.joinToString("\n"))
        append("\n\n")
        append(totalLine)
    }
}

@Composable
private fun PersonAvatar(person: PersonSummary, size: Dp = 42.dp) {
    PersonAvatar(
        name = person.name,
        color = person.color?.let { categoryColor(it) } ?: personFallbackColor(person.id),
        avatarGlyph = person.avatar,
        size = size,
    )
}

@Composable
private fun PersonAvatar(name: String, color: Color, avatarGlyph: String? = null, size: Dp = 42.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarGlyph?.takeIf { it.isNotBlank() } ?: name.firstInitial(),
            color = color,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Deterministic fallback color for a person with no chosen [PersonSummary.color]. */
private fun personFallbackColor(id: String): Color {
    val index = (id.hashCode().mod(EntityColorPalette.size))
    return categoryColor(EntityColorPalette[index].hex)
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

// ---------------------------------------------------------------------------
// Settlement — bottom sheet
// ---------------------------------------------------------------------------

@Composable
private fun SettlementScreen(
    form: SettlementFormState,
    accounts: List<AccountSummary>,
    onFormChange: (SettlementFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedAmount = parseEuroCents(form.amount, allowNegative = false)
    val isOverpay = parsedAmount != null && parsedAmount > form.outstandingCents

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(R.string.settlement_title_with_person, form.personName),
        )
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
        form.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val amountError = form.errorField == SettlementFormField.AMOUNT
        OutlinedTextField(
            value = form.amount,
            onValueChange = { onFormChange(form.copy(amount = it)) },
            label = { Text(text = stringResource(R.string.settlement_field_amount)) },
            prefix = { Text(text = "€") },
            singleLine = true,
            isError = amountError,
            supportingText = if (amountError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(amountError),
        )
        if (isOverpay) {
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(R.string.settlement_warning_overpay),
            )
        }
        val accountError = form.errorField == SettlementFormField.ACCOUNT
        ChipFlowSection(
            label = stringResource(R.string.settlement_field_account),
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
        val dateError = form.errorField == SettlementFormField.DATE
        FormDatePicker(
            label = stringResource(R.string.settlement_field_date),
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
            label = { Text(text = stringResource(R.string.settlement_field_notes)) },
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
                text = stringResource(R.string.settlement_save),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Person form — bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonFormSheet(
    form: PersonFormState,
    onFormChange: (PersonFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.person_form_new_title else R.string.person_form_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            form.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            PersonPreviewCard(form = form)

            val nameError = form.errorField == PersonFormField.NAME
            OutlinedTextField(
                value = form.name,
                onValueChange = {
                    onFormChange(form.copy(name = it, errorRes = null, errorField = null, errorMessage = null))
                },
                label = { Text(text = stringResource(R.string.person_field_name)) },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError && form.errorRes != null) {
                    { Text(text = stringResource(form.errorRes)) }
                } else null,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = nextFieldKeyboardActions(),
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(nameError),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it, errorRes = null, errorMessage = null)) },
                label = { Text(text = stringResource(R.string.person_field_notes)) },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            ColorPickerRow(
                label = stringResource(R.string.person_field_color),
                selectedHex = form.color,
                onSelect = { onFormChange(form.copy(color = it)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
                PrimaryButton(
                    text = stringResource(
                        if (form.id == null) R.string.person_save_new else R.string.person_save_changes,
                    ),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PersonPreviewCard(form: PersonFormState) {
    val color = form.color?.let { categoryColor(it) } ?: personFallbackColor(form.id ?: form.name)
    val nameText = form.name.ifBlank { stringResource(R.string.person_form_new_title) }

    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PersonAvatar(name = form.name, color = color)
            Text(
                text = nameText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (form.name.isBlank()) {
                    FinanceTheme.colors.mutedText
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
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
private fun PersonBalanceItem.sourceLabel(personName: String): String =
    when (type) {
        PersonBalanceItemType.USER_PAID -> stringResource(R.string.debt_source_user_paid)
        PersonBalanceItemType.PERSON_PAID -> stringResource(R.string.debt_source_person_paid, personName)
        PersonBalanceItemType.SETTLEMENT_IN -> stringResource(R.string.debt_source_settlement_in)
        PersonBalanceItemType.SETTLEMENT_OUT -> stringResource(R.string.debt_source_settlement_out)
    }

private fun String.firstInitial(): String =
    trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
