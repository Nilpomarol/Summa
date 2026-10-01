package com.gestorfinances.app.ui.people

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.domain.rules.SettlementScope
import com.gestorfinances.app.ui.common.ListPage
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.gestorfinances.app.ui.common.ListFilterBar
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.ListHero
import kotlin.math.abs
import com.gestorfinances.app.ui.common.EntityListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonBalanceItemType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.EntityColorPalette
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.dayGroupedRows
import com.gestorfinances.app.ui.theme.onIdentityColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import androidx.compose.material.icons.outlined.Add
import java.time.LocalDate
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.AccountSelect
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun peopleViewModel(appContainer: AppContainer): PeopleViewModel = viewModel {
    PeopleViewModel(
        personRepository = appContainer.personRepository,
        movementRepository = appContainer.movementRepository,
        accountRepository = appContainer.accountRepository,
        notificationRefresher = appContainer.notificationCoordinator,
    )
}

@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    viewModel: PeopleViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenDetail: (PersonSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    PeopleContent(
        onBack = onBack,
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onOpenDetail = onOpenDetail,
        onRetry = viewModel::onScreenShown,
    )

    PersonFormHost(state = state, viewModel = viewModel)
}

@Composable
private fun PersonArchiveDialog(
    person: PersonSummary,
    viewModel: PeopleViewModel,
    onArchived: DeleteUndoHandler,
) {
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
            DestructiveTextButton(onClick = { viewModel.onArchiveConfirmed(onSuccess = onArchived) }) {
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

/** A person's own page: balance, actions, and the movements behind the balance. */
@Composable
fun PersonDetailPage(
    personId: String,
    onBack: () -> Unit,
    viewModel: PeopleViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenDebtSource: (String) -> Unit,
    onAddDebtForPerson: (PersonSummary) -> Unit,
    onMessageCopied: (String) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, personId, dataVersion) {
        viewModel.onPersonDetailOpened(personId)
    }

    val detail = state.detail
    val settlementForm = state.settlementForm
    // Settling up is a sheet over the person's page, like every other "record something".
    if (settlementForm != null && detail != null) {
        val requestSettlementDismissal = rememberFormDismissGuard(
            formKey = settlementForm.personId,
            currentValue = settlementForm,
            hasMeaningfulChanges = { initial, current ->
                initial.copy(errorRes = null, errorField = null, errorMessage = null) !=
                    current.copy(errorRes = null, errorField = null, errorMessage = null)
            },
            onDiscard = viewModel::onSettlementDismissed,
        )
        BackHandler(onBack = requestSettlementDismissal)
        AppModalBottomSheet(onDismissRequest = requestSettlementDismissal) {
            SettlementSheetContent(
                form = settlementForm,
                tileColor = detail.person.color?.let(::categoryColor) ?: personFallbackColor(detail.person.id),
                accounts = state.accounts,
                onFormChange = viewModel::onSettlementFormChanged,
                onSave = viewModel::onSettlementSaveClicked,
            )
        }
    }
    when {
        detail != null -> PersonDetailScreen(
            detail = detail,
            // An action that fails here (archiving, say) reports on the list's state: show it too.
            errorMessage = state.detailErrorMessage ?: state.errorMessage,
            onBack = onBack,
            onRetry = { viewModel.onPersonDetailOpened(personId) },
            onOpenDebtSource = onOpenDebtSource,
            onAddPaidByPerson = { onAddDebtForPerson(detail.person) },
            onSettleUp = { viewModel.onSettleUpClicked(detail.person) },
            onEdit = { viewModel.onEditClicked(detail.person) },
            onArchive = { viewModel.onArchiveClicked(detail.person) },
            onCopyMessageClicked = viewModel::onCopyMessageClicked,
            onCopyMessageHandled = viewModel::onCopyMessageHandled,
            onMessageCopied = onMessageCopied,
            modifier = modifier,
        )
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeaderRow(onBack = onBack)
            val errorMessage = state.detailErrorMessage
            if (errorMessage != null) {
                InlineFailureBanner(
                    diagnostic = errorMessage,
                    messageRes = R.string.failure_load_people,
                    onRetry = { viewModel.onPersonDetailOpened(personId) },
                )
            } else {
                Text(
                    text = stringResource(R.string.person_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    PersonFormHost(state = state, viewModel = viewModel)

    state.archiveCandidate?.let { person ->
        PersonArchiveDialog(
            person = person,
            viewModel = viewModel,
            onArchived = { undo ->
                onDeleteCommitted(undo)
                onBack()
            },
        )
    }
}

/** The create/edit person sheet, above whichever people page opened it. */
@Composable
private fun PersonFormHost(state: PeopleUiState, viewModel: PeopleViewModel) {
    var appearanceOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    var detailsOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-person" },
        changed = { initial, current -> initial.withoutErrors() != current.withoutErrors() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) R.string.person_form_new_title else R.string.person_form_edit_title) },
        saveLabel = { stringResource(if (it.id == null) R.string.person_save_new else R.string.person_save_changes) },
    ) { form ->
        val edit: (PersonFormState) -> Unit = { viewModel.onFormChanged(it.withoutErrors()) }
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_person)
        }
        EntityFormHeader(
            tile = {
                PersonAvatar(
                    name = form.name,
                    color = form.color?.let { categoryColor(it) } ?: personFallbackColor(form.id ?: form.name),
                    size = 56.dp,
                )
            },
            name = form.name,
            onNameChange = { edit(form.copy(name = it)) },
            nameLabel = stringResource(R.string.person_field_name),
            onTileClick = { appearanceOpen = !appearanceOpen },
            nameError = form.errorRes?.takeIf { form.errorField == PersonFormField.NAME }?.let { stringResource(it) },
            imeAction = ImeAction.Done,
            keyboardActions = doneKeyboardActions(viewModel::onSaveClicked),
        )
        EntityAppearancePickers(
            open = appearanceOpen,
            colorHex = form.color,
            onColor = { edit(form.copy(color = it)) },
        )
        // Notes already written stay in view.
        val notesShown = detailsOpen || form.notes.isNotBlank()
        FormDisclosure(open = notesShown, onToggle = { detailsOpen = !detailsOpen }) {
            OutlinedTextField(
                value = form.notes,
                onValueChange = { edit(form.copy(notes = it)) },
                label = { Text(text = stringResource(R.string.person_field_notes)) },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun PersonFormState.withoutErrors(): PersonFormState = copy(errorRes = null, errorField = null, errorMessage = null)

@Composable
internal fun PeopleContent(
    onBack: () -> Unit,
    state: PeopleUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onOpenDetail: (PersonSummary) -> Unit,
    onRetry: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openOnly by rememberSaveable { mutableStateOf(false) }
    ListPage(
        title = stringResource(R.string.person_list_title),
        onBack = onBack,
        addLabel = stringResource(R.string.person_list_add),
        onAdd = onAdd,
        modifier = modifier,
    ) {
        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_people,
                    onRetry = onRetry,
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
                Text(
                    text = stringResource(R.string.person_empty_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            item { PeopleHero(state = state) }
            item {
                ListFilterBar(
                    query = query,
                    onQueryChange = { query = it },
                    searchPlaceholder = stringResource(R.string.person_search_placeholder),
                ) {
                    FinanceFilterChip(
                        selected = !openOnly,
                        label = stringResource(R.string.person_filter_all),
                        onClick = { openOnly = false },
                    )
                    FinanceFilterChip(
                        selected = openOnly,
                        label = stringResource(R.string.person_filter_open),
                        onClick = { openOnly = true },
                    )
                }
            }
            // Open balances first, largest first; settled people keep their order after them.
            val people = state.people
                .filter { (!openOnly || it.balanceCents != 0L) && it.name.contains(query.trim(), ignoreCase = true) }
                .sortedWith(compareBy<PersonSummary> { it.balanceCents == 0L }.thenByDescending { abs(it.balanceCents) })
            item {
                // One item, so the rows sit flush and read as one list between their dividers.
                Column {
                    if (people.isEmpty()) {
                        Text(
                            text = stringResource(R.string.person_filter_empty),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    people.forEachIndexed { index, person ->
                        PersonListRow(
                            person = person,
                            isLast = index == people.lastIndex,
                            onOpen = { onOpenDetail(person) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The net of every open balance on the forest hero, what others owe and what the owner owes side
 * by side, and the one balance that matters most.
 */
@Composable
private fun PeopleHero(state: PeopleUiState) {
    val colors = FinanceTheme.colors
    val netCents = state.netBalanceCents
    val largest = state.people.filter { it.balanceCents != 0L }.maxByOrNull { abs(it.balanceCents) }
    ListHero(
        eyebrow = stringResource(R.string.person_list_net_balance),
        watermark = Icons.Outlined.Groups,
        cents = netCents,
        figureColor = when {
            netCents > 0L -> colors.heroIncome
            netCents < 0L -> colors.heroDebt
            else -> colors.heroOnSurface
        },
        signed = true,
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStatBox(stringResource(R.string.person_list_total_owed_to_user), state.totalOwedToUserCents, colors.heroIncome)
            HeroStatBox(stringResource(R.string.person_list_total_you_owe), state.totalUserOwesCents, colors.heroDebt)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (largest != null) PersonAvatar(person = largest, size = 24.dp)
            HeroCaption(
                text = when {
                    largest == null -> stringResource(R.string.person_hero_all_settled)
                    largest.balanceCents > 0L -> stringResource(
                        R.string.person_hero_owes_you, largest.name, formatEuroCents(largest.balanceCents),
                    )
                    else -> stringResource(
                        R.string.person_hero_you_owe, largest.name, formatEuroCents(-largest.balanceCents),
                    )
                },
            )
        }
    }
}

@Composable
private fun PersonListRow(person: PersonSummary, isLast: Boolean, onOpen: () -> Unit) {
    EntityListRow(
        leading = { PersonAvatar(person = person, size = 40.dp) },
        title = person.name,
        isLast = isLast,
        onClick = onOpen,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = abs(person.balanceCents),
                    color = debtDirectionColor(person.balanceCents),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = person.balanceLabel(),
                    color = debtDirectionColor(person.balanceCents),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Person page
// ---------------------------------------------------------------------------

@Composable
private fun PersonDetailScreen(
    detail: PersonDetailState,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenDebtSource: (String) -> Unit,
    onAddPaidByPerson: () -> Unit,
    onSettleUp: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onCopyMessageClicked: () -> Unit,
    onCopyMessageHandled: () -> Unit,
    onMessageCopied: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val person = detail.person
    val today = remember { LocalDate.now() }

    detail.copyMessage?.let { message ->
        val text = message.toClipboardText(person.name)
        val successMessage = stringResource(R.string.person_copy_success)
        LaunchedEffect(message) {
            clipboardManager.setText(AnnotatedString(text))
            onMessageCopied(successMessage)
            onCopyMessageHandled()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            EntityDetailTopBar(
                onBack = onBack,
                menu = listOf(
                    EntityMenuAction(stringResource(R.string.person_action_copy_message), onCopyMessageClicked),
                    EntityMenuAction(stringResource(R.string.common_edit), onEdit),
                    EntityMenuAction(stringResource(R.string.common_archive), onArchive, destructive = true),
                ),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            EntityDetailHeader(
                leading = { PersonAvatar(person = person, size = EntityHeaderMarkSize) },
                name = person.name,
                subtitle = person.notes?.takeIf { it.isNotBlank() },
                figures = {
                    EntityFigure(label = person.balanceLabel()) {
                        MoneyText(
                            cents = kotlin.math.abs(person.balanceCents),
                            color = if (person.balanceCents == 0L) {
                                FinanceTheme.colors.mutedText
                            } else {
                                debtDirectionColor(person.balanceCents)
                            },
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                },
                actions = {
                    if (person.balanceCents != 0L) {
                        EntityActionPill(
                            text = stringResource(R.string.person_action_settle_up),
                            onClick = onSettleUp,
                            icon = Icons.Outlined.Handshake,
                        )
                    }
                    EntityActionPill(
                        text = stringResource(R.string.person_action_external_split),
                        onClick = onAddPaidByPerson,
                        icon = Icons.Outlined.Add,
                    )
                },
            )
        }
        errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_people,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        if (detail.history.isEmpty()) {
            item {
                Text(
                    text = if (person.balanceCents == 0L) {
                        stringResource(R.string.person_detail_settled_body)
                    } else {
                        stringResource(R.string.debt_breakdown_empty)
                    },
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            // Each row leads with what the movement did to the balance with this person.
            dayGroupedRows(
                rows = detail.history,
                dateOf = { it.movement.date },
                key = { "${it.item.type}-${it.item.sourceId}" },
                today = today,
            ) { entry, position ->
                MovementListItem(
                    movement = entry.movement,
                    onClick = { onOpenDebtSource(entry.item.sourceId) },
                    personEffectCents = entry.item.effectCents,
                    showDate = false,
                    position = position,
                )
            }
        }
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
    residuals.forEach { residual ->
        val item = residual.item
        val head = "- ${formatCompactDate(item.date)} ${item.displayTitle(personName)}: "
        lines += head + if (residual.isPartial) {
            stringResource(
                R.string.person_copy_partial_amount,
                formatEuroCents(kotlin.math.abs(residual.remainingCents)),
                formatEuroCents(kotlin.math.abs(item.effectCents)),
            )
        } else {
            formatEuroCents(kotlin.math.abs(residual.remainingCents))
        }
    }
    if (creditAllCents != 0L) {
        lines += "- " + stringResource(
            R.string.person_copy_credit,
            formatEuroCents(kotlin.math.abs(creditAllCents)),
        )
    }
    if (creditRecurringCents != 0L) {
        lines += "- " + stringResource(
            R.string.person_copy_credit_recurring,
            formatEuroCents(kotlin.math.abs(creditRecurringCents)),
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
internal fun PersonAvatar(person: PersonSummary, size: Dp = 42.dp) {
    PersonAvatar(
        name = person.name,
        color = person.color?.let { categoryColor(it) } ?: personFallbackColor(person.id),
        avatarGlyph = person.avatar,
        size = size,
    )
}

@Composable
private fun PersonAvatar(name: String, color: Color, avatarGlyph: String? = null, size: Dp = 42.dp) {
    // A person's mark is a circle, where things (accounts, categories) are rounded squares; filled
    // solid like every other identity mark.
    val fill = themedIdentityColor(color)
    Box(
        modifier = Modifier
            .size(size)
            .background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarGlyph?.takeIf { it.isNotBlank() } ?: name.firstInitial(),
            color = onIdentityColor(fill),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = (size.value * 0.4f).sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Deterministic fallback color for a person with no chosen [PersonSummary.color]. */
private fun personFallbackColor(id: String): Color {
    val index = (id.hashCode().mod(EntityColorPalette.size))
    return categoryColor(EntityColorPalette[index].hex)
}

// ---------------------------------------------------------------------------
// Settlement — bottom sheet
// ---------------------------------------------------------------------------

/**
 * Settling a debt, in the movement form's shape: who with over the amount typed in place, what is
 * outstanding and which way it goes, the account and date, which debt it pays, then the one action.
 */
@Composable
private fun SettlementSheetContent(
    form: SettlementFormState,
    tileColor: Color,
    accounts: List<AccountSummary>,
    onFormChange: (SettlementFormState) -> Unit,
    onSave: () -> Unit,
) {
    val parsedAmount = parseEuroCents(form.amount, allowNegative = false)
    val isOverpay = parsedAmount != null && parsedAmount > form.outstandingCents
    val finance = FinanceTheme.colors
    val personPays = form.direction == SettlementDirection.PERSON_TO_USER
    val directionColor = if (personPays) finance.income else finance.debt
    val errorText = form.errorRes?.let { stringResource(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_person)
        }
        val amountError = form.errorField == SettlementFormField.AMOUNT
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MovementFormHeader(
                icon = Icons.Outlined.Person,
                iconColor = tileColor,
                title = stringResource(R.string.settlement_title_with_person, form.personName),
                titleIsPlaceholder = false,
                amount = form.amount,
                onAmountChange = { onFormChange(form.copy(amount = it)) },
                amountColor = directionColor,
                amountError = errorText.takeIf { amountError },
                modifier = Modifier.scrollToWhen(amountError),
            )
            Text(
                text = stringResource(
                    if (personPays) R.string.settlement_outstanding_person_to_user else R.string.settlement_outstanding_user_to_person,
                    formatEuroCents(form.outstandingCents),
                ),
                color = finance.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isOverpay) {
            InlineBanner(kind = BannerKind.Alert, text = stringResource(R.string.settlement_warning_overpay))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val accountError = form.errorField == SettlementFormField.ACCOUNT
            AccountSelect(
                label = stringResource(R.string.settlement_field_account),
                selectedId = form.accountId,
                accounts = accounts,
                onSelect = { onFormChange(form.copy(accountId = it)) },
                modifier = Modifier
                    .weight(1f)
                    .scrollToWhen(accountError),
                isError = accountError,
                supportingText = errorText.takeIf { accountError },
            )
            val dateError = form.errorField == SettlementFormField.DATE
            FormDatePicker(
                label = stringResource(R.string.settlement_field_date),
                date = form.date,
                onDateChange = { onFormChange(form.copy(date = it)) },
                modifier = Modifier
                    .weight(1f)
                    .scrollToWhen(dateError),
                isError = dateError,
                supportingText = errorText.takeIf { dateError },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabeledSegmentedControl(
                label = stringResource(R.string.settlement_field_scope),
                options = SettlementScope.entries,
                selected = form.scope,
                optionLabel = { scope ->
                    stringResource(
                        when (scope) {
                            SettlementScope.ALL -> R.string.settlement_scope_all
                            SettlementScope.RECURRING -> R.string.settlement_scope_recurring
                        },
                    )
                },
                onSelect = { onFormChange(form.copy(scope = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settlement_scope_help),
                style = MaterialTheme.typography.bodySmall,
                color = finance.mutedText,
            )
        }
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(text = stringResource(R.string.settlement_field_notes)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = stringResource(R.string.settlement_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Person form — bottom sheet
// ---------------------------------------------------------------------------

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
