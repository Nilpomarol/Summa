package com.gestorfinances.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.desktop.resources.column_last_movement
import com.gestorfinances.desktop.resources.column_person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.common.AppIconButton
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.people.PeopleUiState
import com.gestorfinances.app.ui.people.PeopleViewModel
import com.gestorfinances.app.ui.people.PersonArchiveDialog
import com.gestorfinances.app.ui.people.PersonAvatar
import com.gestorfinances.app.ui.people.PersonDetailState
import com.gestorfinances.app.ui.people.PersonFormHost
import com.gestorfinances.app.ui.people.PersonHistoryEntry
import com.gestorfinances.app.ui.people.SettlementSheetContent
import com.gestorfinances.app.ui.people.balanceLabel
import com.gestorfinances.app.ui.people.debtDirectionColor
import com.gestorfinances.app.ui.people.netBalanceCents
import com.gestorfinances.app.ui.people.personFallbackColor
import com.gestorfinances.app.ui.people.toClipboardText
import com.gestorfinances.app.ui.people.totalOwedToUserCents
import com.gestorfinances.app.ui.people.totalUserOwesCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_account
import com.gestorfinances.desktop.resources.column_balance_after
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.desktop.resources.column_date
import com.gestorfinances.desktop.resources.column_effect
import com.gestorfinances.desktop.resources.column_name
import com.gestorfinances.desktop.resources.movements_column_context
import com.gestorfinances.desktop.resources.people_history
import com.gestorfinances.desktop.resources.people_of_amount
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.debt_breakdown_empty
import com.gestorfinances.ui.resources.failure_load_people
import com.gestorfinances.ui.resources.person_action_copy_message
import com.gestorfinances.ui.resources.person_action_external_split
import com.gestorfinances.ui.resources.person_action_settle_up
import com.gestorfinances.ui.resources.person_balance_settled
import com.gestorfinances.ui.resources.person_copy_success
import com.gestorfinances.ui.resources.person_detail_settled_body
import com.gestorfinances.ui.resources.person_empty_title
import com.gestorfinances.ui.resources.person_filter_empty
import com.gestorfinances.ui.resources.person_filter_open
import com.gestorfinances.ui.resources.person_hero_all_settled
import com.gestorfinances.ui.resources.person_hero_owes_you
import com.gestorfinances.ui.resources.person_hero_you_owe
import com.gestorfinances.ui.resources.person_list_add
import com.gestorfinances.ui.resources.person_list_net_balance
import com.gestorfinances.ui.resources.person_list_title
import com.gestorfinances.ui.resources.person_list_total_owed_to_user
import com.gestorfinances.ui.resources.person_list_total_you_owe
import com.gestorfinances.ui.resources.person_search_placeholder
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * People as a page of who owes what: the net of it on the dark panel, then everyone with an open
 * balance and everyone settled, each as a card. A person opens over the whole page with the
 * balance, what can be done about it, and the movements behind it.
 */
@Composable
fun PeoplePage(
    viewModel: PeopleViewModel,
    repository: PersonRepository,
    dataVersion: Long,
    onOpenMovement: (sourceId: String) -> Unit,
    onAddPaidByPerson: (PersonSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown() }
    LaunchedEffect(viewModel, selectedId, dataVersion) { selectedId?.let(viewModel::onPersonDetailOpened) }
    val selected = state.people.firstOrNull { it.id == selectedId }

    if (selected == null) {
        PeopleOverview(state, viewModel, repository, dataVersion, onSelect = { selectedId = it.id })
    } else {
        PersonPane(
            // The list's own figures stand in until the person's page has loaded.
            detail = state.detail?.takeIf { it.person.id == selected.id } ?: PersonDetailState(person = selected),
            errorMessage = state.detailErrorMessage,
            viewModel = viewModel,
            onBack = { selectedId = null },
            onOpenMovement = onOpenMovement,
            onAddPaidByPerson = { onAddPaidByPerson(selected) },
        )
    }

    PersonFormHost(state = state, viewModel = viewModel)
    state.settlementForm?.let { settlement ->
        val person = state.people.firstOrNull { it.id == settlement.personId }
        AppModalBottomSheet(onDismissRequest = viewModel::onSettlementDismissed) {
            SettlementSheetContent(
                form = settlement,
                tileColor = person?.color?.let(::categoryColor) ?: personFallbackColor(settlement.personId),
                accounts = state.accounts,
                onFormChange = viewModel::onSettlementFormChanged,
                onSave = viewModel::onSettlementSaveClicked,
            )
        }
    }
    state.archiveCandidate?.let { person ->
        PersonArchiveDialog(person = person, viewModel = viewModel, onArchived = { if (selectedId == person.id) selectedId = null })
    }
}

@Composable
private fun PeopleOverview(
    state: PeopleUiState,
    viewModel: PeopleViewModel,
    people: PersonRepository,
    dataVersion: Long,
    onSelect: (PersonSummary) -> Unit,
) {
    val muted = FinanceTheme.colors.mutedText
    var query by remember { mutableStateOf("") }
    // When anything last counted between the owner and each person: the newest entry behind their balance.
    val lastDates by produceState<Map<String, String>?>(null, people, dataVersion, state.people) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                state.people.mapNotNull { person -> people.balanceItemsForPerson(person.id).maxOfOrNull { it.date }?.let { person.id to it } }.toMap()
            }.getOrNull()
        }
    }
    ScrollPage {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(SharedRes.string.person_list_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            PrimaryButton(text = stringResource(SharedRes.string.person_list_add), onClick = viewModel::onAddClicked, leadingIcon = Icons.Outlined.Add)
        }
        if (state.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_people),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = viewModel::onScreenShown,
            )
        }
        if (state.isLoading) return@ScrollPage
        if (state.people.isEmpty()) {
            Text(stringResource(SharedRes.string.person_empty_title), color = muted)
            return@ScrollPage
        }
        PeopleHero(state)
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(SharedRes.string.person_search_placeholder),
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        )
        // Open balances, largest first; then everyone settled, in their own order.
        val found = state.people.filter { it.name.contains(query.trim(), ignoreCase = true) }
        val groups = listOf(
            SharedRes.string.person_filter_open to found.filter { it.balanceCents != 0L }.sortedByDescending { abs(it.balanceCents) },
            SharedRes.string.person_balance_settled to found.filter { it.balanceCents == 0L },
        ).filter { it.second.isNotEmpty() }
        if (groups.isEmpty()) Text(stringResource(SharedRes.string.person_filter_empty), color = muted)
        groups.forEach { (title, members) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(members.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
                PeopleTable(members, lastDates, onOpen = onSelect, onSettle = viewModel::onSettleUpClicked)
            }
        }
    }
}

/**
 * The net of every open balance on the dark panel, as the phone's people page opens: what others
 * owe and what the owner owes side by side, and the one balance that matters most.
 */
@Composable
private fun PeopleHero(state: PeopleUiState) {
    val colors = FinanceTheme.colors
    val net = state.netBalanceCents
    val largest = state.people.filter { it.balanceCents != 0L }.maxByOrNull { abs(it.balanceCents) }
    ListHero(
        eyebrow = stringResource(SharedRes.string.person_list_net_balance),
        cents = net,
        watermark = Icons.Outlined.Groups,
        figureColor = when {
            net > 0L -> colors.heroIncome
            net < 0L -> colors.heroDebt
            else -> colors.heroOnSurface
        },
        signed = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatBox(stringResource(SharedRes.string.person_list_total_owed_to_user), state.totalOwedToUserCents, colors.heroIncome)
            HeroStatBox(stringResource(SharedRes.string.person_list_total_you_owe), state.totalUserOwesCents, colors.heroDebt)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (largest != null) PersonAvatar(person = largest, size = 24.dp)
            HeroCaption(
                when {
                    largest == null -> stringResource(SharedRes.string.person_hero_all_settled)
                    largest.balanceCents > 0L -> stringResource(SharedRes.string.person_hero_owes_you, largest.name, formatEuroCents(largest.balanceCents))
                    else -> stringResource(SharedRes.string.person_hero_you_owe, largest.name, formatEuroCents(-largest.balanceCents))
                },
            )
        }
    }
}

/**
 * People as rows across the page: who, when anything last counted between them and the owner, how
 * much is owed and which way, and the way to settle it without opening them. A row opens the person.
 */
@Composable
private fun PeopleTable(
    people: List<PersonSummary>,
    lastDates: Map<String, String>?,
    onOpen: (PersonSummary) -> Unit,
    onSettle: (PersonSummary) -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    FinanceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.column_person), Modifier.weight(1f), style = header, color = muted)
            Text(stringResource(Res.string.column_last_movement), Modifier.width(PERSON_DATE_WIDTH), style = header, color = muted)
            Text(stringResource(Res.string.column_balance_after), Modifier.width(PERSON_BALANCE_WIDTH), style = header, color = muted, textAlign = TextAlign.End)
            Spacer(Modifier.width(PERSON_ACTIONS_WIDTH))
        }
        people.forEach { person ->
            HorizontalDivider(color = colors.cardBorder)
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(role = Role.Button) { onOpen(person) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(person = person, size = 36.dp)
                    Column {
                        Text(person.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        person.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(
                    lastDates?.get(person.id)?.let { formatCompactDateRelative(it) } ?: if (lastDates == null) "" else "—",
                    Modifier.width(PERSON_DATE_WIDTH),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 1,
                )
                Column(Modifier.width(PERSON_BALANCE_WIDTH), horizontalAlignment = Alignment.End) {
                    Text(person.balanceLabel(), style = MaterialTheme.typography.labelMedium, color = debtDirectionColor(person.balanceCents), maxLines = 1)
                    if (person.balanceCents != 0L) {
                        MoneyText(cents = abs(person.balanceCents), color = debtDirectionColor(person.balanceCents), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Row(
                    Modifier.width(PERSON_ACTIONS_WIDTH),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (person.balanceCents != 0L) {
                        SecondaryButton(text = stringResource(SharedRes.string.person_action_settle_up), onClick = { onSettle(person) })
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private val PERSON_DATE_WIDTH = 132.dp
private val PERSON_BALANCE_WIDTH = 132.dp
private val PERSON_ACTIONS_WIDTH = 132.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonPane(
    detail: PersonDetailState,
    errorMessage: String?,
    viewModel: PeopleViewModel,
    onBack: () -> Unit,
    onOpenMovement: (String) -> Unit,
    onAddPaidByPerson: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val person = detail.person
    val clipboard = LocalClipboardManager.current
    var copied by remember(person.id) { mutableStateOf(false) }
    detail.copyMessage?.let { message ->
        val text = message.toClipboardText(person.name)
        LaunchedEffect(message) {
            clipboard.setText(AnnotatedString(text))
            viewModel.onCopyMessageHandled()
            copied = true
        }
    }
    if (copied) {
        LaunchedEffect(Unit) {
            delay(2500)
            copied = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.person_list_title))
            }
            PersonAvatar(person = person, size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(person.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                person.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.mutedText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SecondaryButton(text = stringResource(SharedRes.string.common_edit), onClick = { viewModel.onEditClicked(person) })
            DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { viewModel.onArchiveClicked(person) })
        }
        // Where it stands, straight on the page, beside what can be done about it.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Figure(person.balanceLabel()) {
                MoneyText(
                    cents = abs(person.balanceCents),
                    color = debtDirectionColor(person.balanceCents),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp),
                )
            }
            Spacer(Modifier.size(24.dp))
            Row(Modifier.align(Alignment.Bottom).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (person.balanceCents != 0L) {
                    PrimaryButton(
                        text = stringResource(SharedRes.string.person_action_settle_up),
                        onClick = { viewModel.onSettleUpClicked(person) },
                        leadingIcon = Icons.Outlined.Handshake,
                    )
                }
                SecondaryButton(text = stringResource(SharedRes.string.person_action_external_split), onClick = onAddPaidByPerson)
                if (person.balanceCents != 0L) {
                    SecondaryButton(
                        text = stringResource(if (copied) SharedRes.string.person_copy_success else SharedRes.string.person_action_copy_message),
                        onClick = viewModel::onCopyMessageClicked,
                    )
                }
            }
        }
        if (errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_people),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = { viewModel.onPersonDetailOpened(person.id) },
            )
        }
        if (detail.history.isEmpty()) {
            Text(
                stringResource(
                    if (person.balanceCents == 0L) SharedRes.string.person_detail_settled_body else SharedRes.string.debt_breakdown_empty,
                ),
                color = colors.mutedText,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.people_history), style = MaterialTheme.typography.titleMedium)
                Text(detail.history.size.toString(), style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            }
            History(person, detail.history, onOpenMovement, Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * The balance left with the person after each entry, newest first. Null unless the entries' canonical
 * effects add up to the balance: a column that did not reconcile would be worse than none.
 */
internal fun runningDebt(balanceCents: Long, newestFirst: List<Long>): List<Long>? {
    if (newestFirst.sum() != balanceCents) return null
    var balance = balanceCents
    return newestFirst.map { effect -> balance.also { balance -= effect } }
}

/**
 * The movements behind the balance in the Movements table's own rows: where the amount goes, what
 * each did to the balance with this person (and what it came to), then the balance it left.
 */
@Composable
private fun History(person: PersonSummary, history: List<PersonHistoryEntry>, onOpen: (String) -> Unit, modifier: Modifier) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    val list = rememberLazyListState()
    val sorted = remember(history) { history.sortedWith(compareByDescending<PersonHistoryEntry> { it.movement.date }.thenByDescending { it.movement.createdAt }) }
    val balances = remember(person.balanceCents, sorted) { runningDebt(person.balanceCents, sorted.map { it.item.effectCents }) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = LedgerColumns(
            tick = false,
            category = maxWidth >= 620.dp,
            account = maxWidth >= 760.dp,
            context = maxWidth >= 940.dp,
            balance = balances != null,
            delete = false,
        )
        FinanceCard(Modifier.fillMaxWidth()) {
            TableRow(
                columns = columns,
                height = 36.dp,
                tick = {},
                leading = {},
                name = { Text(stringResource(Res.string.column_name), style = header, color = muted) },
                category = { Text(stringResource(Res.string.column_category), style = header, color = muted) },
                account = { Text(stringResource(Res.string.column_account), style = header, color = muted) },
                context = { Text(stringResource(Res.string.movements_column_context), style = header, color = muted) },
                date = { Text(stringResource(Res.string.column_date), style = header, color = muted) },
                amount = { Text(stringResource(Res.string.column_effect), style = header, color = muted, maxLines = 1) },
                trailing = {},
                balance = { Text(stringResource(Res.string.column_balance_after), style = header, color = muted) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
            Box {
                LazyColumn(state = list) {
                    itemsIndexed(sorted, key = { _, entry -> "${entry.item.type}-${entry.item.sourceId}" }) { index, entry ->
                        HorizontalDivider(color = colors.cardBorder)
                        MovementRow(
                            movement = entry.movement,
                            columns = columns,
                            ticked = false,
                            onTick = {},
                            onOpen = { onOpen(entry.item.sourceId) },
                            onDelete = {},
                            amount = {
                                Column(horizontalAlignment = Alignment.End) {
                                    MoneyText(
                                        cents = entry.item.effectCents,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = debtDirectionColor(entry.item.effectCents),
                                        signed = true,
                                    )
                                    if (entry.movement.amountCents != abs(entry.item.effectCents)) {
                                        Text(
                                            stringResource(Res.string.people_of_amount, formatEuroCents(entry.movement.amountCents)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = muted,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            },
                            balance = {
                                balances?.let {
                                    Text(
                                        (if (it[index] > 0L) "+" else "") + formatEuroCents(it[index]),
                                        color = muted,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                }
                            },
                        )
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}
