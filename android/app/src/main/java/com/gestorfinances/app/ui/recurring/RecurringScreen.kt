package com.gestorfinances.app.ui.recurring

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.data.repository.RecurringMonth
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth
import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.gestorfinances.app.ui.common.ListPage
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.userShareCents
import com.gestorfinances.app.domain.rules.DetectedRecurringCandidate
import com.gestorfinances.app.domain.rules.DetectedTemplateAction
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.movements.cadenceLabel
import com.gestorfinances.app.ui.movements.FieldFrame
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asFigures
import com.gestorfinances.app.ui.theme.amountColor
import java.time.LocalDate

/**
 * The app-wide recurring ViewModel: the due-reminders sheet can appear over any page, so it lives
 * as long as the Activity rather than one page visit.
 */
@Composable
fun recurringViewModel(appContainer: AppContainer): RecurringViewModel = viewModel {
    RecurringViewModel(
        templateRepository = appContainer.templateRepository,
        accountRepository = appContainer.accountRepository,
        categoryRepository = appContainer.categoryRepository,
        tripRepository = appContainer.tripRepository,
        tagRepository = appContainer.tagRepository,
        movementRepository = appContainer.movementRepository,
        splitRepository = appContainer.splitRepository,
        personRepository = appContainer.personRepository,
        budgetRepository = appContainer.budgetRepository,
        notificationRefresher = appContainer.notificationCoordinator,
        financialDataRevision = appContainer.financialDataRevision,
    )
}

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel,
    onOpenItem: (templateId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    RecurringContent(
        state = state,
        onBack = onBack,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onRegister = viewModel::onConfirmClicked,
        onOpenItem = onOpenItem,
        onDetectRecurring = viewModel::onDetectRecurringClicked,
        onRetry = viewModel::onScreenShown,
    )
}

/**
 * App-wide recurring UI, rendered once above every page: surfaces due items proactively (once per
 * app start) instead of requiring a visit to Moviments > Recurrents, plus the dialogs those items open.
 * [otherSheetOpen] keeps the due sheet from stacking on an unrelated movement sheet.
 * [openedFromReminder]: the app was opened by tapping one item's reminder, which is answer enough;
 * the list of everything due does not then follow it.
 */
@Composable
fun RecurringReminders(
    viewModel: RecurringViewModel,
    otherSheetOpen: Boolean,
    openedFromReminder: Boolean,
    onDeleteCommitted: DeleteUndoHandler,
    onSkipped: DeleteUndoHandler,
) {
    val state by viewModel.state.collectAsState()
    // `remember` (not `rememberSaveable`) is deliberate: a real process restart is exactly what
    // "once per app cold start" means, so losing this on process death re-shows the sheet.
    var dueRemindersShown by remember { mutableStateOf(openedFromReminder) }
    LaunchedEffect(openedFromReminder) {
        if (openedFromReminder) dueRemindersShown = true
    }

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    RecurringOverlays(viewModel = viewModel, onDeleteCommitted = onDeleteCommitted, onSkipped = onSkipped)

    // `hasOpenDialog` makes the sheet step aside while one of its own actions (confirm, end) has a
    // sub-dialog open, then reappear once that closes.
    if (!dueRemindersShown && !state.hasOpenDialog && !otherSheetOpen && state.duePrompts.isNotEmpty()) {
        DueRemindersSheet(
            duePrompts = state.duePrompts,
            categories = state.categories,
            today = state.today,
            onConfirm = viewModel::onConfirmClicked,
            onDismiss = { dueRemindersShown = true },
        )
    }
}

/**
 * Every sheet and dialog [RecurringViewModel]'s state can open over whichever page is showing: the
 * item form, the due-prompt confirm, the detection review, and the end and delete confirmations.
 * Rendered once, from [RecurringReminders], never from [RecurringScreen], so none appears twice.
 */
@Composable
private fun RecurringOverlays(
    viewModel: RecurringViewModel,
    onDeleteCommitted: DeleteUndoHandler,
    onSkipped: DeleteUndoHandler,
) {
    val state by viewModel.state.collectAsState()

    RecurringFormSheet(state = state, viewModel = viewModel)

    state.confirmPrompt?.let { prompt ->
        ConfirmPromptDialog(
            prompt = prompt,
            template = state.templates.firstOrNull { it.id == prompt.templateId },
            categories = state.categories,
            people = state.people,
            onFormChange = viewModel::onConfirmFormChanged,
            onDismiss = viewModel::onConfirmDismissed,
            onSave = viewModel::onConfirmSaveClicked,
            onSkip = { viewModel.onConfirmSkipClicked(onSkipped) },
            onSkipAll = { viewModel.onConfirmSkipAllClicked(onSkipped) },
            onAlreadyRecorded = viewModel::onConfirmAlreadyRecordedClicked,
        )
    }

    state.bulkLink?.let { link ->
        BulkLinkSheet(
            link = link,
            onToggle = viewModel::onLinkPaymentToggled,
            onMarkAll = viewModel::onLinkPaymentsMarkAllClicked,
            onConfirm = viewModel::onLinkPaymentsConfirmed,
            onDismiss = viewModel::onLinkPaymentsDismissed,
        )
    }

    state.linkPicker?.let { picker ->
        LinkPickerSheet(
            picker = picker,
            onPick = viewModel::onRecordedMovementPicked,
            onDismiss = viewModel::onLinkPickerDismissed,
        )
    }

    state.detectionReview?.let { review ->
        DetectionReviewSheet(
            review = review,
            onToggle = viewModel::onDetectionItemToggled,
            onDismiss = viewModel::onDetectionReviewDismissed,
            onConfirmAll = viewModel::onDetectionConfirmAllClicked,
        )
    }

    state.endCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = viewModel::onEndDismissed,
            title = { Text(text = stringResource(R.string.template_end_confirm_title)) },
            text = { Text(text = stringResource(R.string.template_end_confirm_body)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onEndConfirmed) {
                    Text(text = stringResource(R.string.recurring_action_end))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onEndDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.deleteCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text(text = stringResource(R.string.template_delete_confirm_title)) },
            text = { Text(text = stringResource(R.string.template_delete_confirm_body)) },
            confirmButton = {
                DestructiveTextButton(
                    onClick = { viewModel.onDeleteConfirmed(onSuccess = onDeleteCommitted) },
                ) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * The Recurrents page: this month's recurring money on the hero, what is due now (one tap from
 * being recorded), then every item by its next date, the paused ones, and the ended ones folded.
 */
@Composable
private fun RecurringContent(
    state: RecurringUiState,
    onBack: () -> Unit,
    modifier: Modifier,
    onAdd: () -> Unit,
    onRegister: (DuePrompt) -> Unit,
    onOpenItem: (templateId: String) -> Unit,
    onDetectRecurring: () -> Unit,
    onRetry: () -> Unit,
) {
    val due = state.duePrompts
    val dueIds = due.map { it.template.id }.toSet()
    val byNextDate = compareBy<TemplateSummary>({ it.nextDueDateSortKey() }, { it.name?.lowercase() ?: "" })
    val upcoming = state.templates.filter { it.status == TemplateStatus.ACTIVE && it.id !in dueIds }.sortedWith(byNextDate)
    val paused = state.templates.filter { it.status == TemplateStatus.PAUSED }.sortedWith(byNextDate)
    val ended = state.templates.filter { it.status == TemplateStatus.ENDED }.sortedWith(byNextDate)
    var endedExpanded by remember { mutableStateOf(false) }

    ListPage(
        title = stringResource(R.string.recurring_list_title),
        onBack = onBack,
        addLabel = stringResource(R.string.recurring_list_add),
        onAdd = onAdd,
        modifier = modifier,
        menu = listOf(
            EntityMenuAction(
                stringResource(if (state.isDetecting) R.string.recurring_detect_action_running else R.string.recurring_detect_action),
                onDetectRecurring,
            ),
        ),
    ) {
        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(diagnostic = message, messageRes = R.string.failure_load_recurring, onRetry = onRetry)
            }
        }
        if (!state.isLoading && state.templates.isEmpty()) {
            item { EmptyRecurringCard() }
            return@ListPage
        }
        state.month?.let { month -> item(key = "hero") { RecurringHero(month = month, today = state.today) } }

        if (due.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.recurring_section_due)) }
            item(key = "due") {
                Column {
                    due.forEachIndexed { index, prompt ->
                        DueRow(
                            prompt = prompt,
                            categories = state.categories,
                            today = state.today,
                            isLast = index == due.lastIndex,
                            onRegister = { onRegister(prompt) },
                            onOpen = { onOpenItem(prompt.template.id) },
                        )
                    }
                }
            }
        }
        templateRows(R.string.recurring_section_next, upcoming, state, onOpenItem)
        templateRows(R.string.recurring_paused_section_title, paused, state, onOpenItem)
        if (ended.isNotEmpty()) {
            item(key = "ended-header") {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.recurring_ended_section_title),
                    count = ended.size,
                    expanded = endedExpanded,
                    onToggle = { endedExpanded = !endedExpanded },
                )
            }
            if (endedExpanded) templateRows(null, ended, state, onOpenItem)
        }
    }
}

private fun LazyListScope.templateRows(
    titleRes: Int?,
    templates: List<TemplateSummary>,
    state: RecurringUiState,
    onOpenItem: (String) -> Unit,
) {
    if (templates.isEmpty()) return
    titleRes?.let { item { SectionHeader(title = stringResource(it)) } }
    item(key = "rows-${titleRes ?: "ended"}") {
        Column {
            templates.forEachIndexed { index, template ->
                TemplateRow(
                    template = template,
                    categories = state.categories,
                    paymentState = state.monthlyPaymentStates[template.id] ?: TemplateMonthPaymentState.NONE,
                    isLast = index == templates.lastIndex,
                    onClick = { onOpenItem(template.id) },
                )
            }
        }
    }
}

/**
 * This month's recurring spending on the forest hero: all of it, what is paid and what is still
 * to pay as a line and a bar, and recurring income as a line of its own. Canonical figures, the
 * owner's share.
 */
@Composable
private fun RecurringHero(month: RecurringMonth, today: LocalDate) {
    val colors = FinanceTheme.colors
    val total = month.paidExpenseCents + month.dueExpenseCents
    ListHero(
        eyebrow = stringResource(R.string.recurring_hero_eyebrow, formatMonth(YearMonth.from(today)).lowercase()),
        cents = total,
        watermark = Icons.Outlined.Autorenew,
    ) {
        HeroCaption(
            text = stringResource(
                R.string.recurring_hero_paid,
                formatEuroCents(month.paidExpenseCents),
                formatEuroCents(month.dueExpenseCents),
            ),
        )
        if (total > 0L) {
            Spacer(modifier = Modifier.height(12.dp))
            val track = heroTint(colors.heroOnSurface, 0.16f)
            val fill = colors.heroOnSurface
            val paid = month.paidExpenseCents.toFloat() / total
            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                if (paid > 0f) drawRoundRect(fill, size = Size(size.width * paid, size.height), cornerRadius = radius)
            }
        }
        val income = month.receivedIncomeCents + month.dueIncomeCents
        if (income > 0L) {
            Spacer(modifier = Modifier.height(12.dp))
            HeroCaption(
                text = stringResource(
                    R.string.recurring_hero_income,
                    formatEuroCents(income),
                    formatEuroCents(month.receivedIncomeCents),
                ),
            )
        }
    }
}

/** An occurrence due now: when it is expected, and one tap to record it (the rest is in its sheet). */
@Composable
private fun DueRow(
    prompt: DuePrompt,
    categories: List<CategoryRecord>,
    today: LocalDate,
    isLast: Boolean,
    onRegister: () -> Unit,
    /** Where tapping the row leads: the item's page on the list, its confirm sheet elsewhere. */
    onOpen: () -> Unit = onRegister,
) {
    val template = prompt.template
    EntityListRow(
        leading = { RecurringDateBadge(date = prompt.dueDate, status = TemplateStatus.ACTIVE, tone = FinanceTheme.colors.alert) },
        dividerInset = DATE_BADGE_INSET,
        title = template.displayName(),
        subtitle = listOfNotNull(
            template.expectedAmountLabel(),
            prompt.whenLabel(today),
            if (prompt.pendingCount > 1) stringResource(R.string.recurring_pending_count, prompt.pendingCount) else null,
        ).joinToString(" · "),
        isLast = isLast,
        onClick = onOpen,
        trailing = { EntityActionPill(text = stringResource(R.string.recurring_action_register), onClick = onRegister) },
    )
}

/** One recurring item: its cadence and account, what it comes to, and when it next falls (or that it is paid). */
@Composable
private fun TemplateRow(
    template: TemplateSummary,
    categories: List<CategoryRecord>,
    paymentState: TemplateMonthPaymentState,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val paid = paymentState == TemplateMonthPaymentState.PAID
    EntityListRow(
        leading = {
            RecurringDateBadge(
                date = template.nextDueDate,
                status = template.status,
                tone = when (paymentState) {
                    TemplateMonthPaymentState.PAID -> colors.income
                    TemplateMonthPaymentState.PENDING, TemplateMonthPaymentState.PARTIALLY_PAID -> colors.alert
                    TemplateMonthPaymentState.NONE -> null
                },
            )
        },
        dividerInset = DATE_BADGE_INSET,
        title = template.displayName(),
        // The badge already says the day, so the cadence goes without it.
        subtitle = listOfNotNull(template.categoryName, template.frequency.cadenceLabel(), template.personName, template.accountName).joinToString(" · "),
        isLast = isLast,
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                TemplateAmountDisplay(template = template)
                if (paid && template.status == TemplateStatus.ACTIVE) {
                    Text(
                        text = stringResource(if (template.type == MovementType.INCOME) R.string.recurring_row_received else R.string.recurring_row_paid),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.income,
                    )
                }
            }
        },
    )
}

/**
 * An item's date as its mark, the day large over the month: tinted by how this month stands
 * ([tone]: paid, pending, or nothing yet when null), or a pause/ended mark once it has stopped.
 */
@Composable
private fun RecurringDateBadge(date: String, status: TemplateStatus, tone: Color?) {
    val colors = FinanceTheme.colors
    val content = tone ?: MaterialTheme.colorScheme.onSurfaceVariant
    val fill = tone?.copy(alpha = 0.18f)?.compositeOver(MaterialTheme.colorScheme.background)
        ?: MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .width(DATE_BADGE_WIDTH)
            .height(52.dp)
            .background(fill, MaterialTheme.shapes.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val day = parseIsoDateOrNull(date)
        if (status == TemplateStatus.ACTIVE && day != null) {
            Text(text = day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = content)
            Text(
                text = day.month.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.forLanguageTag("ca"))
                    .replace(".", "")
                    .uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        } else {
            Icon(
                imageVector = if (status == TemplateStatus.PAUSED) Icons.Outlined.PauseCircle else Icons.Outlined.Cancel,
                contentDescription = status.label(),
                tint = if (status == TemplateStatus.PAUSED) colors.alert else colors.mutedText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val DATE_BADGE_WIDTH = 48.dp
private val DATE_BADGE_INSET = DATE_BADGE_WIDTH + 12.dp

/**
 * What an item comes to: approximate ones marked as such, a shared one as the owner's share, a
 * plain one as its amount; an item with nothing to go on says it varies.
 */
@Composable
internal fun TemplateAmountDisplay(
    template: TemplateSummary,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    /** Shown as a magnitude, as a page's headline figure is. */
    unsigned: Boolean = false,
) {
    val amount = template.expectedAmountCents
    if (amount == null) {
        NeutralPill(text = stringResource(R.string.recurring_amount_variable))
        return
    }
    // An item paid wholly for others shows what it moves, never a share of nothing.
    val share = template.signedUserShareCents()?.takeIf { it != 0L }
    val signed = if (template.splitConfig != null && share != null) share else template.signedAmountCents()
    val cents = if (unsigned) kotlin.math.abs(signed) else signed
    val color = if (template.splitConfig != null) FinanceTheme.colors.shared else FinanceTheme.colors.amountColor(template.type)
    if (template.amountIsVariable) {
        Text(
            text = stringResource(R.string.recurring_amount_approximate, formatEuroCents(cents)),
            color = color,
            style = style.asFigures(),
        )
    } else {
        MoneyText(cents = cents, color = color, style = style, signed = !unsigned && template.type != MovementType.EXPENSE && cents > 0L)
    }
}

/** The mark an item leads with: its category's, else its kind's. */
@Composable
internal fun TemplateSummary.visual(categories: List<CategoryRecord>): Pair<ImageVector, Color> {
    val finance = FinanceTheme.colors
    val category = categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    return when {
        category != null -> categoryIcon(category.icon) to categoryColor(category.color)
        type == MovementType.INCOME -> movementTypeIcon(type) to finance.income
        type == MovementType.TRANSFER -> movementTypeIcon(type) to finance.transfer
        type == MovementType.SETTLEMENT -> movementTypeIcon(type) to finance.settlement
        else -> categoryIcon(null) to categoryColor(null)
    }
}

@Composable
internal fun TemplateSummary.displayName(): String = name ?: payee ?: categoryName ?: type.label()

/** "~45,00 €" for an approximate item, the amount for a fixed one, nothing when unknown. */
@Composable
private fun TemplateSummary.expectedAmountLabel(): String? {
    val amount = expectedAmountCents ?: return null
    return if (amountIsVariable) stringResource(R.string.recurring_amount_approximate, formatEuroCents(amount)) else formatEuroCents(amount)
}

/** When a due occurrence is expected: its window, its day, or since when it is late. */
@Composable
private fun DuePrompt.whenLabel(today: LocalDate): String {
    val date = LocalDate.parse(dueDate)
    val margin = template.marginDays
    return when {
        today.isAfter(date.plusDays(margin)) -> stringResource(R.string.recurring_overdue, formatCompactDateRelative(dueDate))
        margin > 0L -> stringResource(
            R.string.recurring_due_window,
            formatCompactDateRelative(date.minusDays(margin).toString()),
            formatCompactDateRelative(date.plusDays(margin).toString()),
        )
        else -> stringResource(R.string.recurring_due_on, formatCompactDateRelative(dueDate))
    }
}

@Composable
private fun SharedPill() {
    val sharedColor = FinanceTheme.colors.shared
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = sharedColor.copy(alpha = 0.14f),
        contentColor = sharedColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text = stringResource(R.string.movement_shared_badge), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun TemplateSummary.nextDueDateSortKey(): LocalDate =
    parseIsoDateOrNull(nextDueDate) ?: LocalDate.MAX

/**
 * Auto-triggered once per cold start (from [RecurringReminders]) when anything is due: the due
 * rows, each opening its confirm sheet, which holds every choice about it.
 */
@Composable
internal fun DueRemindersSheet(
    duePrompts: List<DuePrompt>,
    categories: List<CategoryRecord>,
    today: LocalDate,
    onConfirm: (DuePrompt) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_due_section_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            duePrompts.forEachIndexed { index, prompt ->
                DueRow(
                    prompt = prompt,
                    categories = categories,
                    today = today,
                    isLast = index == duePrompts.lastIndex,
                    onRegister = { onConfirm(prompt) },
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    }
}

/** The occurrence skipping would pass over: its month, its year, or its day for any other rhythm. */
@Composable
private fun ConfirmPromptState.skippedPeriod(): String {
    val date = parseIsoDateOrNull(dueDate) ?: return formatCompactDateRelative(dueDate)
    return when (frequency) {
        RecurrenceFrequency.MONTHLY -> formatMonth(YearMonth.from(date)).lowercase()
        RecurrenceFrequency.YEARLY -> date.year.toString()
        else -> formatCompactDateRelative(dueDate)
    }
}

/**
 * An item's unlinked past payments, whatever they came to, to tick and link at once: the way to
 * bring in payments recorded before the item existed, or that a change of price kept apart.
 */
@Composable
private fun BulkLinkSheet(
    link: BulkLinkState,
    onToggle: (String) -> Unit,
    onMarkAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.88f) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.recurring_bulk_link_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (link.onePerOccurrenceIds.isNotEmpty()) {
                        TextButton(onClick = onMarkAll) {
                            Text(text = stringResource(if (link.allMarked) R.string.recurring_bulk_link_unmark_all else R.string.recurring_bulk_link_mark_all))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.recurring_bulk_link_subtitle, link.template.displayName()),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            link.errorMessage?.let {
                InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_load_recurring, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            when {
                link.isLoading -> Unit
                link.candidates.isEmpty() -> Text(
                    text = stringResource(R.string.recurring_bulk_link_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 8.dp, end = 20.dp, top = 8.dp),
                ) {
                    itemsIndexed(link.candidates, key = { _, movement -> movement.id }) { index, movement ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = movement.id in link.selectedIds, onCheckedChange = { onToggle(movement.id) })
                            Box(modifier = Modifier.weight(1f)) {
                                MovementListItem(
                                    movement = movement,
                                    onClick = { onToggle(movement.id) },
                                    position = movementRowPosition(index, link.candidates.size),
                                )
                            }
                        }
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.recurring_bulk_link_confirm, link.selectedIds.size),
                onClick = onConfirm,
                enabled = link.selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** The movements that could be a due occurrence already recorded by hand; tapping one links it. */
@Composable
private fun LinkPickerSheet(picker: LinkPickerState, onPick: (MovementSummary) -> Unit, onDismiss: () -> Unit) {
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.88f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text = stringResource(R.string.recurring_link_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(
                        R.string.recurring_link_subtitle,
                        picker.prompt.template.name ?: picker.prompt.template.type.label(),
                        formatCompactDateRelative(picker.prompt.dueDate),
                    ),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            picker.errorMessage?.let {
                InlineFailureBanner(
                    diagnostic = it,
                    messageRes = R.string.failure_load_recurring,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            when {
                picker.isLoading -> Unit
                picker.candidates.isEmpty() -> Text(
                    text = stringResource(R.string.recurring_link_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(picker.candidates, key = { _, movement -> movement.id }) { index, movement ->
                        MovementListItem(
                            movement = movement,
                            onClick = { onPick(movement) },
                            position = movementRowPosition(index, picker.candidates.size),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recording a recurring item's occurrence, in the movement form's shape: the item's tile and name
 * over the amount typed in place, where it goes and when, then the one action; the quieter ways
 * out (already recorded, skip) sit underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmPromptDialog(
    prompt: ConfirmPromptState,
    template: TemplateSummary?,
    categories: List<CategoryRecord>,
    people: List<PersonSummary>,
    onFormChange: (ConfirmPromptState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onSkipAll: () -> Unit,
    onAlreadyRecorded: () -> Unit,
) {
    val finance = FinanceTheme.colors
    val actionLabel = stringResource(
        if (prompt.type == MovementType.INCOME) R.string.recurring_action_add_income else R.string.recurring_action_add_payment,
    )
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            prompt.errorMessage?.let {
                InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_recurring)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = actionLabel,
                    color = finance.mutedText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                template?.let { RecurringPill(cadence = it.cadenceLabel()) }
            }
            val (icon, iconColor) = template?.visual(categories) ?: (categoryIcon(null) to categoryColor(null))
            val amountError = prompt.errorField == ConfirmPromptField.AMOUNT
            MovementFormHeader(
                icon = icon,
                iconColor = iconColor,
                title = template?.displayName() ?: prompt.templateName,
                titleIsPlaceholder = false,
                amount = prompt.amount,
                onAmountChange = { onFormChange(prompt.copy(amount = it)) },
                amountColor = finance.amountColor(prompt.type),
                amountError = prompt.errorRes?.takeIf { amountError }?.let { stringResource(it) },
                modifier = Modifier.scrollToWhen(amountError),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadOnlyField(
                    label = stringResource(R.string.movement_field_account),
                    value = prompt.accountName,
                    modifier = Modifier.weight(1f),
                )
                val dateError = prompt.errorField == ConfirmPromptField.DATE
                FormDatePicker(
                    label = stringResource(R.string.movement_field_date),
                    date = prompt.date,
                    onDateChange = { onFormChange(prompt.copy(date = it)) },
                    modifier = Modifier
                        .weight(1f)
                        .scrollToWhen(dateError),
                    isError = dateError,
                    supportingText = prompt.errorRes?.takeIf { dateError }?.let { stringResource(it) },
                )
            }
            prompt.settlementPersonName?.let { person ->
                ReadOnlyField(
                    label = stringResource(R.string.template_field_person),
                    value = person,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (prompt.splitConfig != null) {
                SplitPreviewCard(splitConfig = prompt.splitConfig, amountText = prompt.amount, people = people)
            }
            val confirmedAmount = parseEuroCents(prompt.amount, allowNegative = false)?.takeIf { it > 0L }
            if (prompt.fixedAmountCents != null && confirmedAmount != null && confirmedAmount != prompt.fixedAmountCents) {
                FormToggleRow(
                    label = stringResource(
                        R.string.recurring_update_amount,
                        formatEuroCents(confirmedAmount),
                        formatEuroCents(prompt.fixedAmountCents),
                    ),
                    checked = prompt.updateAmount,
                    onCheckedChange = { onFormChange(prompt.copy(updateAmount = it)) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PrimaryButton(
                    text = actionLabel,
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !prompt.isSaving,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    TextButton(onClick = onAlreadyRecorded) { Text(text = stringResource(R.string.recurring_action_already_recorded)) }
                    TextButton(onClick = onSkip) { Text(text = stringResource(R.string.recurring_action_skip_period, prompt.skippedPeriod())) }
                    if (prompt.pendingCount > 1) {
                        TextButton(onClick = onSkipAll) { Text(text = stringResource(R.string.recurring_skip_all)) }
                    }
                }
            }
        }
    }
}

/** Says the movement being recorded belongs to a recurring item, and its rhythm. */
@Composable
private fun RecurringPill(cadence: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(imageVector = Icons.Outlined.Autorenew, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = stringResource(R.string.recurring_confirm_badge, cadence), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A field that only shows what the item already fixes, in the same frame as the editable ones. */
@Composable
private fun ReadOnlyField(label: String, value: String, modifier: Modifier = Modifier) {
    FieldFrame(label = label, focused = false, modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = FinanceTheme.colors.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Live preview of how the confirmed amount would be split, so a shared template's confirm sheet
 * never books a rescaled or dropped split without the user seeing it first. Uses the
 * exact same rule [RecurringViewModel] applies at save time ([TemplateSplitConfig.previewShares]). */
@Composable
private fun SplitPreviewCard(
    splitConfig: TemplateSplitConfig,
    amountText: String,
    people: List<PersonSummary>,
) {
    val amountCents = parseEuroCents(amountText, allowNegative = false)
    val shares = amountCents?.takeIf { it > 0L }?.let { splitConfig.previewShares(it, people) }.orEmpty()
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_split_preview_title),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            if (shares.isEmpty()) {
                Text(
                    text = stringResource(R.string.recurring_split_preview_enter_amount),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                shares.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (line.isUser) {
                                stringResource(R.string.split_payer_user)
                            } else {
                                line.personName ?: stringResource(R.string.recurring_split_preview_person_unknown)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MoneyText(cents = line.amountCents, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecurringCard() {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * What the history looks like it repeats, to tick and start following at once: a flat row each
 * (what, how often and how many times seen, how much), with the buttons held under the list.
 */
@Composable
private fun DetectionReviewSheet(
    review: DetectionReviewState,
    onToggle: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirmAll: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.88f) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.recurring_detect_review_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(
                        if (review.items.isEmpty()) R.string.recurring_detect_review_empty else R.string.recurring_detect_review_subtitle,
                    ),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            review.errorMessage?.let {
                InlineFailureBanner(
                    diagnostic = it,
                    messageRes = R.string.failure_save_recurring,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                contentPadding = PaddingValues(start = 8.dp, end = 20.dp, top = 8.dp),
            ) {
                itemsIndexed(review.items) { index, item ->
                    DetectionCandidateRow(item = item, onToggle = { checked -> onToggle(index, checked) })
                    if (index != review.items.lastIndex) {
                        HorizontalDivider(color = FinanceTheme.colors.cardBorder, modifier = Modifier.padding(start = 48.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                    Text(text = stringResource(if (review.items.isEmpty()) R.string.common_close else R.string.common_cancel))
                }
                if (review.items.isNotEmpty()) {
                    val selected = review.items.count { it.accepted }
                    PrimaryButton(
                        text = stringResource(R.string.recurring_detect_confirm_selected, selected),
                        onClick = onConfirmAll,
                        enabled = selected > 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectionCandidateRow(
    item: DetectionReviewItem,
    onToggle: (Boolean) -> Unit,
) {
    val candidate = item.candidate
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!item.accepted) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.accepted, onCheckedChange = onToggle)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = candidate.name?.takeIf { it.isNotBlank() } ?: candidate.payee.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.splitConfig != null) {
                    SharedPill()
                }
                // Only the unusual case is named: a pattern that would change an item already followed.
                if (candidate.action != DetectedTemplateAction.NEW) {
                    NeutralPill(text = stringResource(R.string.recurring_detect_badge_update))
                }
            }
            Text(
                text = listOfNotNull(
                    candidate.frequency.cadenceLabel(),
                    // A pattern that stopped showing up comes in as already ended.
                    candidate.suggestedStatus.takeIf { it != TemplateStatus.ACTIVE }?.label(),
                    stringResource(R.string.recurring_detect_occurrences, candidate.occurrenceCount),
                ).joinToString(separator = " · "),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        DetectionCandidateAmount(candidate = candidate, splitConfig = item.splitConfig)
    }
}

/** A candidate's amount as an item's is shown: the owner's share first, with the total beside it when shared. */
@Composable
private fun DetectionCandidateAmount(candidate: DetectedRecurringCandidate, splitConfig: TemplateSplitConfig?) {
    val amount = candidate.amountCents
    if (amount == null) {
        NeutralPill(text = stringResource(R.string.recurring_amount_variable))
        return
    }
    if (candidate.amountIsVariable) {
        Text(
            text = stringResource(R.string.recurring_amount_approximate, formatEuroCents(amount)),
            style = MaterialTheme.typography.bodyMedium.asFigures(),
        )
        return
    }
    val userShare = splitConfig?.userShareCents(amount)
    if (splitConfig != null && userShare != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MoneyText(cents = userShare, color = FinanceTheme.colors.shared, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.movement_total_short, formatEuroCents(amount)),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    } else {
        MoneyText(cents = amount, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun TemplateStatus.label(): String =
    when (this) {
        TemplateStatus.ACTIVE -> stringResource(R.string.template_status_active)
        TemplateStatus.PAUSED -> stringResource(R.string.template_status_paused)
        TemplateStatus.ENDED -> stringResource(R.string.template_status_ended)
    }

@Composable
internal fun TemplateSummary.cadenceLabel(): String =
    when (frequency) {
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.recurring_cadence_weekly)
        RecurrenceFrequency.FORTNIGHTLY -> stringResource(R.string.recurring_cadence_fortnightly)
        RecurrenceFrequency.MONTHLY -> dayOfMonth?.let {
            stringResource(R.string.recurring_cadence_monthly_day, it.toInt())
        } ?: stringResource(R.string.recurring_cadence_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.recurring_cadence_yearly)
        RecurrenceFrequency.CUSTOM -> stringResource(R.string.recurring_cadence_custom)
    }

private fun TemplateSummary.signedAmountCents(): Long {
    val amount = amountCents ?: 0L
    return if (type == MovementType.EXPENSE) -amount else amount
}

/** The user's own share, signed the same way as [signedAmountCents] — null when there's no split
 * to derive it from, or the amount is unset (variable-amount template). */
private fun TemplateSummary.signedUserShareCents(): Long? {
    val amount = amountCents ?: return null
    val share = splitConfig?.userShareCents(amount) ?: return null
    return if (type == MovementType.EXPENSE) -share else share
}
