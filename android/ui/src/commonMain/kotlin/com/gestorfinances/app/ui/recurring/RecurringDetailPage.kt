package com.gestorfinances.app.ui.recurring

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.failure_load_recurring
import com.gestorfinances.ui.resources.recurring_action_end
import com.gestorfinances.ui.resources.recurring_action_pause
import com.gestorfinances.ui.resources.recurring_action_register
import com.gestorfinances.ui.resources.recurring_action_resume
import com.gestorfinances.ui.resources.recurring_bulk_link_action
import com.gestorfinances.ui.resources.recurring_detail_after
import com.gestorfinances.ui.resources.recurring_detail_amount
import com.gestorfinances.ui.resources.recurring_detail_amount_approximate
import com.gestorfinances.ui.resources.recurring_detail_chart
import com.gestorfinances.ui.resources.recurring_detail_chart_income
import com.gestorfinances.ui.resources.recurring_detail_estimate_average
import com.gestorfinances.ui.resources.recurring_detail_estimate_initial
import com.gestorfinances.ui.resources.recurring_detail_history
import com.gestorfinances.ui.resources.recurring_detail_history_income
import com.gestorfinances.ui.resources.recurring_detail_margin
import com.gestorfinances.ui.resources.recurring_detail_next
import com.gestorfinances.ui.resources.recurring_history_empty
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.toRecurrenceRule
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatShortMonth
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * A recurring item's own page: what it comes to and when, its next dates, what its payments have
 * come to lately, and the payments themselves. Its rarer actions (pause, end, delete) sit in ⋮.
 */
@Composable
fun RecurringDetailPage(
    templateId: String,
    viewModel: RecurringViewModel,
    onBack: () -> Unit,
    onOpenAccount: (accountId: String) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val template = state.templates.firstOrNull { it.id == templateId }

    LaunchedEffect(viewModel, templateId, template != null) {
        viewModel.onDetailOpened(templateId)
    }
    if (template == null) {
        // Deleted from here, or gone: the page has nothing left to show.
        if (!state.isLoading) LaunchedEffect(Unit) { onBack() }
        return
    }

    val history = state.historyDetail?.takeIf { it.template.id == templateId }
    val due = state.duePrompts.firstOrNull { it.template.id == templateId }
    val neverPaid = (state.occurrenceCounts[templateId] ?: 0L) == 0L
    val payments = history?.movements.orEmpty()
    // Income comes in: its occurrences are "cobraments", not "pagaments".
    val isIncome = template.type == MovementType.INCOME

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            EntityDetailTopBar(
                onBack = onBack,
                menu = buildList {
                    if (template.status == TemplateStatus.ACTIVE) {
                        add(EntityMenuAction(stringResource(Res.string.recurring_action_pause), onClick = { viewModel.onPauseClicked(template) }))
                    } else {
                        add(EntityMenuAction(stringResource(Res.string.recurring_action_resume), onClick = { viewModel.onResumeClicked(template) }))
                    }
                    if (template.status != TemplateStatus.ENDED) {
                        add(EntityMenuAction(stringResource(Res.string.recurring_action_end), onClick = { viewModel.onEndClicked(template) }))
                    }
                    // Once paid, an item is paused or ended, never deleted: that would unlink its history.
                    if (neverPaid) {
                        add(EntityMenuAction(stringResource(Res.string.common_archive), onClick = { viewModel.onDeleteClicked(template) }, destructive = true))
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            val (icon, color) = template.visual(state.categories)
            EntityDetailHeader(
                leading = { IdentityIconTile(icon = icon, color = color, size = EntityHeaderMarkSize) },
                name = template.displayName(),
                subtitle = listOfNotNull(
                    // The category says what it is; without one, its kind does.
                    template.categoryName ?: template.type.label(),
                    template.cadenceLabel(),
                    template.marginDays.takeIf { it > 0L }?.let { stringResource(Res.string.recurring_detail_margin, it.toInt()) },
                    template.status.takeIf { it != TemplateStatus.ACTIVE }?.label(),
                ).joinToString(" · "),
                figures = {
                    EntityFigure(
                        label = stringResource(
                            if (template.amountIsVariable) Res.string.recurring_detail_amount_approximate else Res.string.recurring_detail_amount,
                        ),
                    ) {
                        TemplateAmountDisplay(template = template, style = MaterialTheme.typography.headlineMedium, unsigned = true)
                    }
                    Box(modifier = Modifier.weight(1f))
                    if (template.status == TemplateStatus.ACTIVE) {
                        EntityFigure(label = stringResource(Res.string.recurring_detail_next), horizontalAlignment = Alignment.End) {
                            Text(text = formatCompactDateRelative(template.nextDueDate), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                },
                details = { RecurringDetails(template = template) },
                actions = {
                    if (due != null) {
                        EntityActionPill(
                            text = stringResource(Res.string.recurring_action_register),
                            onClick = { viewModel.onConfirmClicked(due) },
                            icon = Icons.Outlined.Check,
                        )
                    }
                    EntityActionPill(text = stringResource(Res.string.common_edit), onClick = { viewModel.onEditClicked(template) }, icon = Icons.Outlined.Edit)
                },
                link = if (state.accounts.any { it.id == template.accountId }) {
                    { EntityActionPill(text = template.accountName, onClick = { onOpenAccount(template.accountId) }, chevron = true) }
                } else {
                    null
                },
            )
        }
        // Pausing, resuming or ending that fails reports on the list's state: show it too.
        (history?.errorMessage ?: state.errorMessage)?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = Res.string.failure_load_recurring,
                    onRetry = if (history?.errorMessage != null) {
                        { viewModel.onDetailOpened(templateId) }
                    } else {
                        viewModel::onScreenShown
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        val recent = payments.sortedBy { it.date }.takeLast(CHART_PAYMENTS)
        if (recent.size >= 2) {
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SectionHeader(
                    title = stringResource(if (isIncome) Res.string.recurring_detail_chart_income else Res.string.recurring_detail_chart),
                )
            }
            item(key = "chart") { PaymentAmountsChart(payments = recent) }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
        item {
            SectionHeader(
                title = stringResource(if (isIncome) Res.string.recurring_detail_history_income else Res.string.recurring_detail_history),
                trailing = {
                    LinkPill(text = stringResource(Res.string.recurring_bulk_link_action), onClick = { viewModel.onLinkPaymentsClicked(template) })
                },
            )
        }
        if (payments.isEmpty()) {
            if (history?.isLoading == false) {
                item {
                    Text(
                        text = stringResource(Res.string.recurring_history_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            // One payment per period: a flat list, each row carrying its date, reads better than a heading per day.
            val rows = payments.sortedByDescending { it.date }
            itemsIndexed(rows, key = { _, movement -> movement.id }) { index, movement ->
                MovementListItem(
                    movement = movement,
                    onClick = { onMovementDetail(movement) },
                    position = movementRowPosition(index, rows.size),
                )
            }
        }
    }
}

/** The dates after the next one, and where an approximate amount comes from. */
@Composable
fun RecurringDetails(template: TemplateSummary) {
    val lines = buildList {
        if (template.status == TemplateStatus.ACTIVE) {
            val next = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull()
            if (next != null) {
                val rule = template.toRecurrenceRule()
                val after = runCatching {
                    generateSequence(RecurringAdvancer.nextOccurrence(rule, next)) { RecurringAdvancer.nextOccurrence(rule, it) }
                        .take(UPCOMING_DATES)
                        .toList()
                }.getOrDefault(emptyList())
                if (after.isNotEmpty()) {
                    add(stringResource(Res.string.recurring_detail_after, after.joinToString(" · ") { formatCompactDateRelative(it.toString()) }))
                }
            }
        }
        if (template.amountIsVariable) {
            add(
                stringResource(
                    if (template.recentAmountCents != null) Res.string.recurring_detail_estimate_average else Res.string.recurring_detail_estimate_initial,
                ),
            )
        }
    }
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = FinanceTheme.colors.mutedText) }
    }
}

/**
 * What the last payments came to, oldest first, a bar each under its month, with a readout of
 * the selected one (the latest until another is tapped): a price change or an estimate's spread
 * shows at a glance.
 */
@Composable
fun PaymentAmountsChart(payments: List<MovementSummary>) {
    var selected by remember(payments) { mutableIntStateOf(payments.lastIndex) }
    // The owner's share of a shared payment, as the header shows the item's.
    val amountOf = { payment: MovementSummary -> payment.userShareCents.takeIf { payment.isShared && it != 0L } ?: payment.amountCents }
    val top = payments.maxOf(amountOf).coerceAtLeast(1L).toFloat()
    val colors = FinanceTheme.colors
    val strong = MaterialTheme.colorScheme.primary
    val quiet = strong.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.background)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        val chosen = payments[selected]
        Text(
            text = "${formatCompactDateRelative(chosen.date)} · ${formatEuroCents(amountOf(chosen))}",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            payments.forEachIndexed { index, payment ->
                // A slim bar centred in its month's slot, the whole slot tappable.
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { selected = index },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .fillMaxHeight((amountOf(payment) / top).coerceIn(0.04f, 1f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (index == selected) strong else quiet),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            payments.forEach { payment ->
                Text(
                    text = runCatching { formatShortMonth(YearMonth.from(LocalDate.parse(payment.date))) }.getOrDefault(""),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val CHART_PAYMENTS = 6
private const val UPCOMING_DATES = 2
