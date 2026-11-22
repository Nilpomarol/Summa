package com.gestorfinances.desktop

import com.gestorfinances.desktop.resources.recurring_hero_split
import com.gestorfinances.desktop.resources.recurring_days_tomorrow
import com.gestorfinances.desktop.resources.recurring_days_today
import com.gestorfinances.desktop.resources.recurring_days_in
import com.gestorfinances.desktop.resources.recurring_days_ago
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.toRecurrenceRule
import com.gestorfinances.app.ui.common.AppIconButton
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.movements.cadenceLabel
import com.gestorfinances.app.ui.recurring.DuePrompt
import com.gestorfinances.app.ui.recurring.PaymentAmountsChart
import com.gestorfinances.app.ui.recurring.RecurringDetails
import com.gestorfinances.app.data.repository.RecurringMonth
import com.gestorfinances.app.ui.recurring.RecurringUiState
import com.gestorfinances.app.ui.recurring.RecurringViewModel
import com.gestorfinances.app.ui.recurring.TemplateAmountDisplay
import com.gestorfinances.app.ui.recurring.TemplateMonthPaymentState
import com.gestorfinances.app.ui.recurring.cadenceLabel
import com.gestorfinances.app.ui.recurring.displayName
import com.gestorfinances.app.ui.recurring.label
import com.gestorfinances.app.ui.recurring.nextDueDateSortKey
import com.gestorfinances.app.ui.recurring.signedAmountCents
import com.gestorfinances.app.ui.recurring.signedUserShareCents
import com.gestorfinances.app.ui.recurring.visual
import com.gestorfinances.app.ui.recurring.whenLabel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_account
import com.gestorfinances.desktop.resources.column_amount
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.desktop.resources.column_date
import com.gestorfinances.desktop.resources.column_frequency
import com.gestorfinances.desktop.resources.column_name
import com.gestorfinances.desktop.resources.column_next
import com.gestorfinances.desktop.resources.column_per_year
import com.gestorfinances.desktop.resources.recurring_column_monthly
import com.gestorfinances.desktop.resources.recurring_column_this_month
import com.gestorfinances.desktop.resources.recurring_month_average
import com.gestorfinances.desktop.resources.recurring_month_due
import com.gestorfinances.desktop.resources.recurring_month_income
import com.gestorfinances.desktop.resources.recurring_month_paid
import com.gestorfinances.desktop.resources.recurring_state_partial
import com.gestorfinances.desktop.resources.recurring_state_pending
import com.gestorfinances.desktop.resources.recurring_year_expense
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.failure_load_recurring
import com.gestorfinances.ui.resources.recurring_action_end
import com.gestorfinances.ui.resources.recurring_action_pause
import com.gestorfinances.ui.resources.recurring_action_register
import com.gestorfinances.ui.resources.recurring_action_resume
import com.gestorfinances.ui.resources.recurring_bulk_link_action
import com.gestorfinances.ui.resources.recurring_detail_amount
import com.gestorfinances.ui.resources.recurring_detail_amount_approximate
import com.gestorfinances.ui.resources.recurring_detail_chart
import com.gestorfinances.ui.resources.recurring_detail_chart_income
import com.gestorfinances.ui.resources.recurring_detail_history
import com.gestorfinances.ui.resources.recurring_detail_history_income
import com.gestorfinances.ui.resources.recurring_detail_margin
import com.gestorfinances.ui.resources.recurring_detail_next
import com.gestorfinances.ui.resources.recurring_detect_action
import com.gestorfinances.ui.resources.recurring_detect_action_running
import com.gestorfinances.ui.resources.recurring_ended_section_title
import com.gestorfinances.ui.resources.recurring_hero_eyebrow
import com.gestorfinances.ui.resources.recurring_history_empty
import com.gestorfinances.ui.resources.recurring_list_add
import com.gestorfinances.ui.resources.recurring_list_title
import com.gestorfinances.ui.resources.recurring_paused_section_title
import com.gestorfinances.ui.resources.recurring_row_paid
import com.gestorfinances.ui.resources.recurring_row_received
import com.gestorfinances.ui.resources.recurring_section_due
import com.gestorfinances.ui.resources.recurring_section_next
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * The recurring items, and the one that is opened, each over the whole page. First this month's
 * recurring spending on the dark panel, as on the phone, over one table that answers what there
 * is, what each comes to a month, whether this month's is paid and when the next falls. A row
 * opens its item in the table's place; the arrow goes back. The forms and confirmations are the
 * shell's, shared with the reminders that open on start.
 */
@Composable
fun RecurringPage(
    viewModel: RecurringViewModel,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpenAccount: (accountId: String) -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }
    val template = state.templates.firstOrNull { it.id == selectedId }
    LaunchedEffect(viewModel, selectedId, template != null) { selectedId?.let(viewModel::onDetailOpened) }

    when {
        selectedId == null -> RecurringList(state, viewModel, onSelect)
        // Opened from another page before the items have loaded: the page waits rather than flash the list.
        template == null -> Unit
        else -> RecurringPane(template, state, viewModel, onBack = { onSelect(null) }, onOpenAccount, onOpenMovement)
    }
}

@Composable
private fun RecurringList(state: RecurringUiState, viewModel: RecurringViewModel, onSelect: (String?) -> Unit) {
    val due = state.duePrompts
    val dueIds = due.map { it.template.id }.toSet()
    val byNextDate = compareBy<TemplateSummary>({ it.nextDueDateSortKey() }, { it.name?.lowercase() ?: "" })
    val active = state.templates.filter { it.status == TemplateStatus.ACTIVE }
    ScrollPage {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(SharedRes.string.recurring_list_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            SecondaryButton(
                text = stringResource(
                    if (state.isDetecting) SharedRes.string.recurring_detect_action_running else SharedRes.string.recurring_detect_action,
                ),
                onClick = viewModel::onDetectRecurringClicked,
                enabled = !state.isDetecting,
            )
            PrimaryButton(text = stringResource(SharedRes.string.recurring_list_add), onClick = viewModel::onAddClicked, leadingIcon = Icons.Outlined.Add)
        }
        if (state.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_recurring),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = viewModel::onScreenShown,
            )
        }
        if (state.isLoading) return@ScrollPage
        // What the active expenses come to over the coming year, at today's amounts.
        val yearly = active.filter { it.type == MovementType.EXPENSE }.sumOf { it.yearlyCents(state.today) ?: 0L }
        state.month?.let { RecurringHero(it, state.today, yearly) }
        val sections = listOf(
            SharedRes.string.recurring_section_due to due.map { it.template },
            SharedRes.string.recurring_section_next to active.filter { it.id !in dueIds }.sortedWith(byNextDate),
            SharedRes.string.recurring_paused_section_title to state.templates.filter { it.status == TemplateStatus.PAUSED }.sortedWith(byNextDate),
            SharedRes.string.recurring_ended_section_title to state.templates.filter { it.status == TemplateStatus.ENDED }.sortedWith(byNextDate),
        ).filter { it.second.isNotEmpty() }
        if (sections.isEmpty()) return@ScrollPage
        val dueByTemplate = due.associateBy { it.template.id }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = RecurringColumns(frequency = maxWidth >= 880.dp, monthly = maxWidth >= 620.dp, yearly = maxWidth >= 760.dp)
            val muted = FinanceTheme.colors.mutedText
            val header = MaterialTheme.typography.labelMedium
            // Each kind is a table of its own, under its name and how many it holds.
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                sections.forEach { (title, rows) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                            Text(rows.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
                        }
                        FinanceCard(Modifier.fillMaxWidth()) {
                            RecurringRow(
                                columns = columns,
                                height = 36.dp,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                name = { Text(stringResource(Res.string.column_name), style = header, color = muted) },
                                frequency = { Text(stringResource(Res.string.column_frequency), style = header, color = muted) },
                                thisMonth = { Text(stringResource(Res.string.recurring_column_this_month), style = header, color = muted) },
                                next = { Text(stringResource(Res.string.column_next), style = header, color = muted) },
                                amount = { Text(stringResource(Res.string.column_amount), style = header, color = muted) },
                                monthly = { Text(stringResource(Res.string.recurring_column_monthly), style = header, color = muted) },
                                yearly = { Text(stringResource(Res.string.column_per_year), style = header, color = muted) },
                            )
                            rows.forEach { template ->
                                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                                TemplateRow(template, state, columns, dueByTemplate[template.id], viewModel::onConfirmClicked, onOpen = { onSelect(template.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * This month's recurring spending on the dark panel, as the phone's page opens: all of it, how
 * much is already paid as a bar, and beside the phone's figures what it averages a month and
 * comes to in a year.
 */
@Composable
private fun RecurringHero(month: RecurringMonth, today: LocalDate, yearlyCents: Long) {
    val colors = FinanceTheme.colors
    val total = month.paidExpenseCents + month.dueExpenseCents
    ListHero(
        eyebrow = stringResource(SharedRes.string.recurring_hero_eyebrow, formatMonth(YearMonth.from(today)).lowercase()),
        cents = total,
        watermark = Icons.Outlined.Autorenew,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (total > 0L) {
            Spacer(Modifier.height(12.dp))
            val paid = stringResource(Res.string.recurring_month_paid)
            val due = stringResource(Res.string.recurring_month_due)
            HeroSegments(
                listOf(
                    HeroSegment(paid, month.paidExpenseCents, colors.heroIncome),
                    HeroSegment(due, month.dueExpenseCents, colors.heroDebt),
                ),
                totalCents = total,
                rest = stringResource(Res.string.recurring_hero_split, formatEuroCents(month.paidExpenseCents), formatEuroCents(month.dueExpenseCents)),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatBox(stringResource(Res.string.recurring_month_paid), month.paidExpenseCents, colors.heroOnSurface)
            HeroStatBox(stringResource(Res.string.recurring_month_due), month.dueExpenseCents, if (month.dueExpenseCents > 0L) colors.heroDebt else colors.heroOnSurface)
            HeroStatBox(stringResource(Res.string.recurring_month_income), month.receivedIncomeCents + month.dueIncomeCents, colors.heroIncome)
            HeroStatBox(stringResource(Res.string.recurring_month_average), yearlyCents / 12, colors.heroOnSurface)
            HeroStatBox(stringResource(Res.string.recurring_year_expense), yearlyCents, colors.heroOnSurface)
        }
    }
}

/** Which columns the window has room for; the rest are dropped rather than squeezed. */
private data class RecurringColumns(val frequency: Boolean, val monthly: Boolean, val yearly: Boolean)

@Composable
private fun RecurringRow(
    columns: RecurringColumns,
    height: androidx.compose.ui.unit.Dp,
    name: @Composable () -> Unit,
    frequency: @Composable () -> Unit,
    thisMonth: @Composable () -> Unit,
    next: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    monthly: @Composable () -> Unit,
    yearly: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(2.2f)) { name() }
        if (columns.frequency) Box(Modifier.weight(1f)) { frequency() }
        Box(Modifier.width(116.dp)) { thisMonth() }
        Box(Modifier.width(112.dp)) { next() }
        Box(Modifier.width(108.dp), contentAlignment = Alignment.CenterEnd) { amount() }
        if (columns.monthly) Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) { monthly() }
        if (columns.yearly) Box(Modifier.width(100.dp), contentAlignment = Alignment.CenterEnd) { yearly() }
    }
}

@Composable
private fun TemplateRow(
    template: TemplateSummary,
    state: RecurringUiState,
    columns: RecurringColumns,
    due: DuePrompt?,
    onRegister: (DuePrompt) -> Unit,
    onOpen: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val (icon, color) = template.visual(state.categories)
    val live = template.status == TemplateStatus.ACTIVE
    val yearly = template.yearlyCents(state.today)?.takeIf { live }
    RecurringRow(
        columns = columns,
        height = 56.dp,
        modifier = Modifier.clickable(onClick = onOpen).pointerHoverIcon(PointerIcon.Hand),
        name = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityIconTile(icon = icon, color = color, size = 32.dp)
                Column {
                    Text(template.displayName(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(template.categoryName, template.personName, template.accountName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        frequency = { Text(template.frequency.cadenceLabel(), color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        thisMonth = {
            val income = template.type == MovementType.INCOME
            when {
                !live -> Unit
                due != null -> SecondaryButton(text = stringResource(SharedRes.string.recurring_action_register), onClick = { onRegister(due) })
                else -> when (state.monthlyPaymentStates[template.id]) {
                    TemplateMonthPaymentState.PAID -> StateMark(
                        Icons.Outlined.Check,
                        stringResource(if (income) SharedRes.string.recurring_row_received else SharedRes.string.recurring_row_paid),
                        colors.income,
                    )
                    TemplateMonthPaymentState.PARTIALLY_PAID -> StateMark(Icons.Outlined.Schedule, stringResource(Res.string.recurring_state_partial), colors.alert)
                    TemplateMonthPaymentState.PENDING -> StateMark(Icons.Outlined.Schedule, stringResource(Res.string.recurring_state_pending), muted)
                    else -> Unit
                }
            }
        },
        next = {
            if (template.status != TemplateStatus.ENDED) {
                Column {
                    Text(
                        formatCompactDateRelative(template.nextDueDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (live) MaterialTheme.colorScheme.onSurface else muted,
                        maxLines = 1,
                    )
                    // How far off it is, in the colour of how soon: late, this week, later.
                    val days = runCatching { ChronoUnit.DAYS.between(state.today, LocalDate.parse(template.nextDueDate)) }.getOrNull()
                    if (live && days != null) {
                        Text(
                            when {
                                days < 0L -> stringResource(Res.string.recurring_days_ago, -days)
                                days == 0L -> stringResource(Res.string.recurring_days_today)
                                days == 1L -> stringResource(Res.string.recurring_days_tomorrow)
                                else -> stringResource(Res.string.recurring_days_in, days)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                days < 0L -> colors.debt
                                days <= SOON_DAYS -> colors.alert
                                days <= THIS_MONTH_DAYS -> colors.income
                                else -> muted
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        amount = { TemplateAmountDisplay(template = template) },
        // An even share of the year, so a yearly bill and a monthly one can be told apart at a glance.
        monthly = { Text(yearly?.let { formatEuroCents(it / 12) } ?: "—", color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
        yearly = { Text(yearly?.let(::formatEuroCents) ?: "—", color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
    )
}

/** Within these many days a payment is close; within the second, it is this month's business. */
private const val SOON_DAYS = 3L
private const val THIS_MONTH_DAYS = 30L

/** Where this month's occurrence stands, as a small tinted mark with its word. */
@Composable
private fun StateMark(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * What the item comes to over the year from [today], at its current amount: the owner's share of a
 * shared one, an estimate for an approximate one. Null when it has no amount to project.
 */
internal fun TemplateSummary.yearlyCents(today: LocalDate): Long? {
    val amount = (signedUserShareCents()?.takeIf { splitConfig != null && it != 0L } ?: signedAmountCents())
        .takeIf { expectedAmountCents != null } ?: return null
    val first = runCatching { LocalDate.parse(nextDueDate) }.getOrNull() ?: return null
    val until = today.plusYears(1)
    val rule = toRecurrenceRule()
    val occurrences = runCatching {
        generateSequence(first) { RecurringAdvancer.nextOccurrence(rule, it) }.takeWhile { it.isBefore(until) }.take(MAX_YEARLY_OCCURRENCES).count()
    }.getOrDefault(0)
    return abs(amount) * occurrences
}

/** A daily item has 366; nothing recurs more often than that. */
private const val MAX_YEARLY_OCCURRENCES = 366

/**
 * One item over the whole page: what it is and what can be done to it, its amount, next date and
 * cost over time straight on the page beside its last payments as a chart, and every payment in
 * the Movements table's own rows, scrolling under the rest.
 */
@Composable
private fun RecurringPane(
    template: TemplateSummary,
    state: RecurringUiState,
    viewModel: RecurringViewModel,
    onBack: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val history = state.historyDetail?.takeIf { it.template.id == template.id }
    val due = state.duePrompts.firstOrNull { it.template.id == template.id }
    val neverPaid = (state.occurrenceCounts[template.id] ?: 0L) == 0L
    val payments = remember(history?.movements) { history?.movements.orEmpty().sortedByDescending { it.date } }
    // Income comes in: its occurrences are "cobraments", not "pagaments".
    val isIncome = template.type == MovementType.INCOME
    val live = template.status == TemplateStatus.ACTIVE
    val yearly = template.yearlyCents(state.today)?.takeIf { live }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val (icon, color) = template.visual(state.categories)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.recurring_list_title))
            }
            IdentityIconTile(icon = icon, color = color, size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(template.displayName(), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        template.categoryName ?: template.type.label(),
                        template.cadenceLabel(),
                        template.marginDays.takeIf { it > 0L }?.let { stringResource(SharedRes.string.recurring_detail_margin, it.toInt()) },
                        template.status.takeIf { it != TemplateStatus.ACTIVE }?.label(),
                    ).joinToString(" · "),
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SecondaryButton(text = stringResource(SharedRes.string.common_edit), onClick = { viewModel.onEditClicked(template) })
            // Once paid, an item is paused or ended, never deleted: that would unlink its history.
            if (neverPaid) DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { viewModel.onDeleteClicked(template) })
        }
        // Pausing, resuming or ending that fails reports on the list's state: show it too.
        if ((history?.errorMessage ?: state.errorMessage) != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_recurring),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = { viewModel.onDetailOpened(template.id) },
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val recent = payments.take(6).reversed()
            val charted = recent.size >= 2 && maxWidth >= 900.dp
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // What it costs and when, straight on the page: no card around the figures.
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text(
                                stringResource(
                                    if (template.amountIsVariable) SharedRes.string.recurring_detail_amount_approximate else SharedRes.string.recurring_detail_amount,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = muted,
                            )
                            TemplateAmountDisplay(
                                template = template,
                                style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp),
                                unsigned = true,
                            )
                        }
                        if (live) {
                            Column {
                                Text(stringResource(SharedRes.string.recurring_detail_next), style = MaterialTheme.typography.labelLarge, color = muted)
                                Text(
                                    due?.whenLabel(state.today) ?: formatCompactDateRelative(template.nextDueDate),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (due != null) colors.alert else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (yearly != null) {
                            Column {
                                Text(stringResource(Res.string.recurring_column_monthly), style = MaterialTheme.typography.labelLarge, color = muted)
                                MoneyText(cents = yearly / 12, style = MaterialTheme.typography.headlineSmall)
                            }
                            Column {
                                Text(stringResource(Res.string.column_per_year), style = MaterialTheme.typography.labelLarge, color = muted)
                                MoneyText(cents = yearly, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    RecurringDetails(template = template)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (due != null) {
                            PrimaryButton(
                                text = stringResource(SharedRes.string.recurring_action_register),
                                onClick = { viewModel.onConfirmClicked(due) },
                                leadingIcon = Icons.Outlined.Check,
                            )
                        }
                        if (live) {
                            SecondaryButton(text = stringResource(SharedRes.string.recurring_action_pause), onClick = { viewModel.onPauseClicked(template) })
                        } else {
                            SecondaryButton(text = stringResource(SharedRes.string.recurring_action_resume), onClick = { viewModel.onResumeClicked(template) })
                        }
                        if (template.status != TemplateStatus.ENDED) {
                            SecondaryButton(text = stringResource(SharedRes.string.recurring_action_end), onClick = { viewModel.onEndClicked(template) })
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.accounts.any { it.id == template.accountId }) {
                            LinkPill(text = template.accountName, onClick = { onOpenAccount(template.accountId) })
                        }
                    }
                }
                if (charted) {
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(if (isIncome) SharedRes.string.recurring_detail_chart_income else SharedRes.string.recurring_detail_chart),
                            style = MaterialTheme.typography.labelLarge,
                            color = muted,
                        )
                        PaymentAmountsChart(payments = recent)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (isIncome) SharedRes.string.recurring_detail_history_income else SharedRes.string.recurring_detail_history),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(SharedRes.string.recurring_bulk_link_action),
                onClick = { viewModel.onLinkPaymentsClicked(template) },
            )
        }
        if (payments.isEmpty()) {
            if (history?.isLoading == false) Text(stringResource(SharedRes.string.recurring_history_empty), color = muted)
        } else {
            Payments(payments, onOpenMovement, Modifier.weight(1f))
        }
    }
}

/** Every payment of an item in the Movements table's own rows, drawing only the ones on screen. */
@Composable
private fun Payments(payments: List<MovementSummary>, onOpen: (MovementSummary) -> Unit, modifier: Modifier) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = LedgerColumns(tick = false, category = maxWidth >= 700.dp, account = maxWidth >= 560.dp, context = false, delete = false)
        FinanceCard(Modifier.fillMaxWidth()) {
            TableRow(
                columns = columns,
                height = 36.dp,
                tick = {},
                leading = {},
                name = { Text(stringResource(Res.string.column_name), style = header, color = muted) },
                category = { Text(stringResource(Res.string.column_category), style = header, color = muted) },
                account = { Text(stringResource(Res.string.column_account), style = header, color = muted) },
                context = {},
                date = { Text(stringResource(Res.string.column_date), style = header, color = muted) },
                amount = { Text(stringResource(Res.string.column_amount), style = header, color = muted, textAlign = TextAlign.End) },
                trailing = {},
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
            ScrollList {
                items(payments, key = { it.id }) { movement ->
                    HorizontalDivider(color = colors.cardBorder)
                    MovementRow(
                        movement = movement,
                        columns = columns,
                        ticked = false,
                        onTick = {},
                        onOpen = { onOpen(movement) },
                        onDelete = {},
                        withinRecurring = true,
                    )
                }
            }
        }
    }
}
