package com.gestorfinances.desktop

import com.gestorfinances.desktop.resources.column_trip
import com.gestorfinances.ui.resources.budget_history_over
import com.gestorfinances.ui.resources.budget_history_average
import com.gestorfinances.desktop.resources.budgets_month_under
import com.gestorfinances.desktop.resources.budgets_month_over
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.SpendingHistory
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.ui.budgets.BudgetArchiveDialog
import com.gestorfinances.app.ui.budgets.BudgetFormSheet
import com.gestorfinances.app.ui.budgets.BudgetsUiState
import com.gestorfinances.app.ui.budgets.BudgetsViewModel
import com.gestorfinances.app.ui.budgets.PlanHero
import com.gestorfinances.app.ui.budgets.PlanSetupSheet
import com.gestorfinances.app.ui.budgets.SavingSummary
import com.gestorfinances.app.ui.budgets.SpendingHistoryChart
import com.gestorfinances.app.ui.budgets.overlapNames
import com.gestorfinances.app.ui.common.AppIconButton
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatShortMonth
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.budgets_column_left
import com.gestorfinances.desktop.resources.budgets_column_limit
import com.gestorfinances.desktop.resources.budgets_column_pace
import com.gestorfinances.desktop.resources.budgets_column_spent
import com.gestorfinances.desktop.resources.budgets_column_state
import com.gestorfinances.desktop.resources.budgets_forecast
import com.gestorfinances.desktop.resources.budgets_history_by_part
import com.gestorfinances.desktop.resources.budgets_history_total
import com.gestorfinances.desktop.resources.budgets_part_movements
import com.gestorfinances.desktop.resources.budgets_part_movements_empty
import com.gestorfinances.desktop.resources.budgets_recurring_due
import com.gestorfinances.desktop.resources.column_account
import com.gestorfinances.desktop.resources.column_amount
import com.gestorfinances.desktop.resources.column_average
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.desktop.resources.column_date
import com.gestorfinances.desktop.resources.column_name
import com.gestorfinances.desktop.resources.column_plan
import com.gestorfinances.desktop.resources.movements_column_context
import com.gestorfinances.ui.resources.budget_add_yearly
import com.gestorfinances.ui.resources.budget_field_total
import com.gestorfinances.ui.resources.budget_history_title
import com.gestorfinances.ui.resources.budget_list_add
import com.gestorfinances.ui.resources.budget_list_title
import com.gestorfinances.ui.resources.budget_part_ahead
import com.gestorfinances.ui.resources.budget_part_may_exceed
import com.gestorfinances.ui.resources.budget_part_no_limit
import com.gestorfinances.ui.resources.budget_part_over
import com.gestorfinances.ui.resources.budget_plan_others
import com.gestorfinances.ui.resources.budget_plan_overlap
import com.gestorfinances.ui.resources.budget_plan_per_day
import com.gestorfinances.ui.resources.budget_plan_section
import com.gestorfinances.ui.resources.budget_saving_actual
import com.gestorfinances.ui.resources.budget_saving_planned
import com.gestorfinances.ui.resources.budget_status_ok
import com.gestorfinances.ui.resources.budget_status_over
import com.gestorfinances.ui.resources.budget_status_warn
import com.gestorfinances.ui.resources.budget_year_to_date
import com.gestorfinances.ui.resources.budget_yearly_categories
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.failure_load_budgets
import java.time.YearMonth
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/** The plan's rest, opened like a partida: it has no budget of its own to name it by. */
private const val OTHERS = "__others__"

/**
 * The month's plan, and the partida that is opened, each over the whole page. First the plan on
 * the dark panel across the top, what the month leaves to save beside the recent months, then the
 * partides as a table that says how each stands, the yearly limits as another, and what each
 * partida spent month by month. A partida's row opens it in the page's place: where it stands,
 * its recent months, and the month's movements in the Movements table's own rows.
 */
@Composable
fun BudgetsPage(
    viewModel: BudgetsViewModel,
    dataVersion: Long,
    movements: MovementsViewModel,
    onOpenRecurring: () -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown() }
    var open by remember { mutableStateOf<String?>(null) }
    val plan = state.plan
    val openBudget = plan?.compartments?.firstOrNull { it.id == open }

    if (plan != null && (openBudget != null || open == OTHERS)) {
        PartPane(state, viewModel, movements, openBudget, onBack = { open = null }, onOpenMovement)
    } else {
        PlanOverview(state, viewModel, onOpenRecurring, onOpen = { open = it })
    }

    BudgetFormSheet(state = state, viewModel = viewModel)
    PlanSetupSheet(state = state, viewModel = viewModel)
    BudgetArchiveDialog(state = state, viewModel = viewModel, onDeleteCommitted = { open = null })
}

@Composable
private fun PlanOverview(state: BudgetsUiState, viewModel: BudgetsViewModel, onOpenRecurring: () -> Unit, onOpen: (String) -> Unit) {
    ScrollPage {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(SharedRes.string.budget_list_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            // Changing the plan itself is on the panel below; a yearly limit starts from its own table.
            PrimaryButton(
                text = stringResource(SharedRes.string.budget_list_add),
                onClick = { viewModel.onAddClicked() },
                leadingIcon = Icons.Outlined.Add,
            )
        }
        if (state.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_budgets),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = viewModel::onScreenShown,
            )
        }
        val plan = state.plan ?: return@ScrollPage
        val monthIsOpen = !state.selectedMonth.isBefore(YearMonth.from(state.today))
        val hasPlan = plan.total != null || plan.compartments.isNotEmpty()
        PlanHero(
            plan = plan,
            month = state.selectedMonth,
            activityMonths = state.activityMonths,
            monthIsOpen = monthIsOpen,
            onMonthSelected = viewModel::onMonthSelected,
            onEditPlan = viewModel::onAddOverallClicked,
            onCreatePlan = viewModel::onCreatePlanClicked,
            onOpenRecurring = onOpenRecurring,
            onPartSelected = { onOpen(it ?: OTHERS) },
        )
        overlapNames(plan, state.categories)?.let { (child, parent) ->
            InlineBanner(kind = BannerKind.Alert, text = stringResource(SharedRes.string.budget_plan_overlap, parent, child))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val income = state.income
            val saving: (@Composable (Modifier) -> Unit)? = if (plan.total != null && income != null) {
                { modifier ->
                    HomeCard(
                        stringResource(if (monthIsOpen) SharedRes.string.budget_saving_planned else SharedRes.string.budget_saving_actual),
                        modifier,
                    ) {
                        SavingSummary(plan = plan, income = income, tripCents = state.tripCents, monthIsOpen = monthIsOpen, interactive = true)
                    }
                }
            } else {
                null
            }
            val months: (@Composable (Modifier) -> Unit)? = state.history?.takeIf { history -> history.total.any { it != 0L } }?.let { history ->
                { modifier ->
                    HomeCard(stringResource(Res.string.budgets_history_total), modifier) {
                        MonthBars(history.months, history.total, plan.plan.total.plannedCents, viewModel::onMonthSelected)
                    }
                }
            }
            if (maxWidth >= 900.dp && saving != null && months != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    saving(Modifier.weight(1f))
                    months(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    saving?.invoke(Modifier)
                    months?.invoke(Modifier)
                }
            }
        }
        if (hasPlan) PartsTable(state, monthIsOpen, onOpen)
        YearlyLimits(state, onAdd = viewModel::onAddYearlyClicked, onEdit = viewModel::onEditClicked)
        state.history?.takeIf { hasPlan && it.months.isNotEmpty() }?.let { HistoryTable(state, it) }
    }
}

/** Which columns the window has room for; the rest are dropped rather than squeezed. */
private data class LimitColumns(val bar: Boolean, val limit: Boolean, val left: Boolean)

private fun limitColumns(width: Dp) = LimitColumns(bar = width >= 900.dp, limit = width >= 700.dp, left = width >= 560.dp)

@Composable
private fun LimitTableRow(
    columns: LimitColumns,
    height: Dp,
    name: @Composable () -> Unit,
    bar: @Composable () -> Unit,
    spent: @Composable () -> Unit,
    limit: @Composable () -> Unit,
    left: @Composable () -> Unit,
    state: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1.6f)) { name() }
        if (columns.bar) Box(Modifier.weight(1.4f)) { bar() }
        Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterEnd) { spent() }
        if (columns.limit) Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterEnd) { limit() }
        if (columns.left) Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterEnd) { left() }
        Box(Modifier.width(200.dp)) { state() }
    }
}

@Composable
private fun LimitTableHeader(columns: LimitColumns, last: String) {
    val muted = FinanceTheme.colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    LimitTableRow(
        columns = columns,
        height = 36.dp,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        name = { Text(stringResource(Res.string.column_name), style = header, color = muted) },
        bar = {},
        spent = { Text(stringResource(Res.string.budgets_column_spent), style = header, color = muted) },
        limit = { Text(stringResource(Res.string.budgets_column_limit), style = header, color = muted) },
        left = { Text(stringResource(Res.string.budgets_column_left), style = header, color = muted) },
        state = { Text(last, style = header, color = muted) },
    )
}

@Composable
private fun TableTitle(title: String, count: Int, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = FinanceTheme.colors.mutedText, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** The partides and the rest as one table: what each has spent of what it has, what is left, and how it is going. */
@Composable
private fun PartsTable(state: BudgetsUiState, monthIsOpen: Boolean, onOpen: (String) -> Unit) {
    val plan = state.plan ?: return
    val colors = FinanceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TableTitle(stringResource(SharedRes.string.budget_plan_section), plan.compartments.size + 1)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = limitColumns(maxWidth)
            FinanceCard(Modifier.fillMaxWidth()) {
                LimitTableHeader(columns, stringResource(Res.string.budgets_column_pace))
                plan.compartments.forEach { budget ->
                    HorizontalDivider(color = colors.cardBorder)
                    PartRow(
                        columns = columns,
                        icon = categoryIcon(budget.categoryIcon),
                        tone = categoryColor(budget.categoryColor),
                        name = budget.displayName ?: stringResource(SharedRes.string.common_no_category),
                        part = plan.plan.compartments.getValue(budget.id),
                        monthIsOpen = monthIsOpen,
                        onOpen = { onOpen(budget.id) },
                    )
                }
                HorizontalDivider(color = colors.cardBorder)
                PartRow(
                    columns = columns,
                    icon = Icons.Outlined.MoreHoriz,
                    tone = colors.mutedText,
                    name = stringResource(SharedRes.string.budget_plan_others),
                    part = plan.plan.others,
                    monthIsOpen = monthIsOpen,
                    onOpen = { onOpen(OTHERS) },
                )
            }
        }
    }
}

/** How a part is going, in a word and its colour: over, heading over, ahead of its pace, or what it can still spend a day. */
@Composable
private fun PlanPart.pace(monthIsOpen: Boolean): Pair<String, Color> {
    val colors = FinanceTheme.colors
    val planned = plannedCents
    return when (status) {
        null -> stringResource(SharedRes.string.budget_part_no_limit) to colors.mutedText
        PlanStatus.OVER -> stringResource(SharedRes.string.budget_part_over, formatEuroCents(actualCents - (planned ?: 0L))) to colors.debt
        PlanStatus.MAY_EXCEED -> stringResource(SharedRes.string.budget_part_may_exceed, formatEuroCents(forecastCents - (planned ?: 0L))) to colors.alert
        PlanStatus.AHEAD_OF_PACE -> stringResource(SharedRes.string.budget_part_ahead, formatEuroCents(actualCents - (expectedByTodayCents ?: 0L))) to colors.alert
        PlanStatus.ON_TRACK -> when {
            !monthIsOpen -> stringResource(SharedRes.string.budget_status_ok) to colors.income
            dueCents > 0L -> stringResource(Res.string.budgets_recurring_due, formatEuroCents(dueCents)) to colors.mutedText
            perDayCents != null -> stringResource(SharedRes.string.budget_plan_per_day, formatEuroCents(perDayCents!!)) to colors.income
            else -> stringResource(SharedRes.string.budget_status_ok) to colors.income
        }
    }
}

@Composable
private fun PartRow(columns: LimitColumns, icon: ImageVector, tone: Color, name: String, part: PlanPart, monthIsOpen: Boolean, onOpen: () -> Unit) {
    val colors = FinanceTheme.colors
    val planned = part.plannedCents
    val over = part.status == PlanStatus.OVER
    val (pace, paceColor) = part.pace(monthIsOpen)
    LimitTableRow(
        columns = columns,
        height = 52.dp,
        modifier = Modifier.clickable(onClick = onOpen).pointerHoverIcon(PointerIcon.Hand),
        name = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityIconTile(icon = icon, color = tone, size = 32.dp)
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        bar = {
            if (planned != null && planned > 0L) {
                BudgetProgressBar(
                    fraction = (part.actualCents.toFloat() / planned).coerceIn(0f, 1f),
                    color = if (over) colors.debt else themedIdentityColor(tone),
                )
            }
        },
        spent = { MoneyText(cents = part.actualCents, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        limit = { Text(planned?.let(::formatEuroCents) ?: "—", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText, maxLines = 1) },
        left = {
            part.remainingCents?.let {
                MoneyText(cents = it, color = if (it < 0L) colors.debt else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
        },
        state = { Text(pace, style = MaterialTheme.typography.bodyMedium, color = paceColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** The yearly limits as a table of their own, started from here; a row opens its limit to change it. */
@Composable
private fun YearlyLimits(state: BudgetsUiState, onAdd: () -> Unit, onEdit: (BudgetSummary) -> Unit) {
    val yearly = state.evaluations.filter { it.budget.scope == BudgetScope.CATEGORY && it.budget.period == BudgetPeriod.YEARLY }
    val colors = FinanceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TableTitle(stringResource(SharedRes.string.budget_yearly_categories), yearly.size) {
            SecondaryButton(text = stringResource(SharedRes.string.budget_add_yearly), onClick = onAdd)
        }
        if (yearly.isEmpty()) return@Column
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = limitColumns(maxWidth)
            FinanceCard(Modifier.fillMaxWidth()) {
                LimitTableHeader(columns, stringResource(Res.string.budgets_column_state))
                yearly.forEach { evaluation -> YearlyRow(columns, evaluation, state, onEdit) }
            }
        }
    }
}

@Composable
private fun YearlyRow(columns: LimitColumns, evaluation: BudgetEvaluation, state: BudgetsUiState, onEdit: (BudgetSummary) -> Unit) {
    val colors = FinanceTheme.colors
    val budget = evaluation.budget
    val tone = categoryColor(budget.categoryColor)
    HorizontalDivider(color = colors.cardBorder)
    LimitTableRow(
        columns = columns,
        height = 56.dp,
        modifier = Modifier.clickable { onEdit(budget) }.pointerHoverIcon(PointerIcon.Hand),
        name = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityIconTile(icon = categoryIcon(budget.categoryIcon), color = tone, size = 32.dp)
                Column {
                    Text(budget.displayName ?: stringResource(SharedRes.string.common_no_category), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(SharedRes.string.budget_year_to_date, state.selectedMonth.year, formatMonth(state.selectedMonth).lowercase()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText,
                        maxLines = 1,
                    )
                }
            }
        },
        bar = { BudgetProgressBar(fraction = evaluation.progressFraction(), color = if (evaluation.status == BudgetStatus.OVER) colors.debt else themedIdentityColor(tone)) },
        spent = { MoneyText(cents = evaluation.actualCents, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        limit = { Text(formatEuroCents(budget.limitAmountCents), style = MaterialTheme.typography.bodyMedium, color = colors.mutedText, maxLines = 1) },
        left = {
            MoneyText(
                cents = evaluation.remainingCents,
                color = if (evaluation.remainingCents < 0L) colors.debt else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        state = {
            Text(
                stringResource(
                    when (evaluation.status) {
                        BudgetStatus.OK -> SharedRes.string.budget_status_ok
                        BudgetStatus.WARN -> SharedRes.string.budget_status_warn
                        BudgetStatus.OVER -> SharedRes.string.budget_status_over
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = evaluation.status.color(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        },
    )
}

/**
 * One partida (or the rest, when [budget] is null) over the whole page: where it stands straight
 * on the page beside its recent months, and the month's movements in it, scrolling under the rest.
 */
@Composable
private fun PartPane(
    state: BudgetsUiState,
    viewModel: BudgetsViewModel,
    movements: MovementsViewModel,
    budget: BudgetSummary?,
    onBack: () -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val plan = state.plan ?: return
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val part = budget?.let { plan.plan.compartments.getValue(it.id) } ?: plan.plan.others
    val monthIsOpen = !state.selectedMonth.isBefore(YearMonth.from(state.today))
    val name = budget?.let { it.displayName ?: stringResource(SharedRes.string.common_no_category) } ?: stringResource(SharedRes.string.budget_plan_others)
    val tone = budget?.let { categoryColor(it.categoryColor) } ?: muted
    val planned = part.plannedCents
    val (pace, paceColor) = part.pace(monthIsOpen)

    // The ledger is the Movements page's own; it loads here when that page has not been opened yet.
    LaunchedEffect(movements) { movements.onScreenShown() }
    val ledger by movements.state.collectAsState()
    // A partida covers its category and that category's subcategories; the rest, whatever no partida covers.
    val covered = remember(plan.compartments, state.categories) {
        plan.compartments.associate { partida ->
            partida.id to (state.categories.filter { it.parentId == partida.categoryId }.map { it.id } + partida.categoryId).toSet()
        }
    }
    val rows = remember(ledger.movements, state.selectedMonth, budget?.id, covered) {
        val everyPartida = covered.values.flatten().toSet()
        ledger.movements.filter { movement ->
            movement.type == MovementType.EXPENSE &&
                parseIsoDateOrNull(movement.date)?.let(YearMonth::from) == state.selectedMonth &&
                if (budget != null) movement.categoryId in covered.getValue(budget.id) else movement.categoryId !in everyPartida
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.budget_list_title))
            }
            IdentityIconTile(icon = budget?.let { categoryIcon(it.categoryIcon) } ?: Icons.Outlined.MoreHoriz, color = tone, size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMonthYear(state.selectedMonth).replaceFirstChar { it.uppercase() }, color = muted, style = MaterialTheme.typography.bodyMedium)
            }
            // The rest has no budget of its own: what it has is what the plan's total leaves it.
            SecondaryButton(
                text = stringResource(SharedRes.string.common_edit),
                onClick = { if (budget != null) viewModel.onEditClicked(budget) else viewModel.onAddOverallClicked() },
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val history = state.history
            val values = history?.let { if (budget != null) it.byCategory[budget.categoryId] else it.others }
            val charted = history != null && values != null && values.any { it != 0L } && maxWidth >= 900.dp
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Where it stands, straight on the page: no card around the figures.
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
                        Figure(stringResource(Res.string.budgets_column_spent)) {
                            MoneyText(cents = part.actualCents, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp))
                        }
                        if (planned != null) {
                            Figure(stringResource(Res.string.budgets_column_limit)) { MoneyText(cents = planned, style = MaterialTheme.typography.headlineSmall) }
                            Figure(stringResource(Res.string.budgets_column_left)) {
                                val left = planned - part.actualCents
                                MoneyText(cents = left, color = if (left < 0L) colors.debt else colors.income, style = MaterialTheme.typography.headlineSmall)
                            }
                            if (monthIsOpen) {
                                Figure(stringResource(Res.string.budgets_forecast)) {
                                    MoneyText(
                                        cents = part.forecastCents,
                                        color = if (part.forecastCents > planned) colors.alert else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                }
                            }
                        }
                    }
                    if (planned != null && planned > 0L) {
                        BudgetProgressBar(
                            fraction = (part.actualCents.toFloat() / planned).coerceIn(0f, 1f),
                            color = if (part.status == PlanStatus.OVER) colors.debt else themedIdentityColor(tone),
                            modifier = Modifier.height(10.dp),
                        )
                    }
                    Text(
                        listOfNotNull(
                            pace,
                            part.dueCents.takeIf { it > 0L && monthIsOpen && part.status != PlanStatus.ON_TRACK }
                                ?.let { stringResource(Res.string.budgets_recurring_due, formatEuroCents(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = paceColor,
                    )
                }
                if (charted) {
                    Column(Modifier.width(380.dp)) {
                        MonthBars(history!!.months, values!!, planned, viewModel::onMonthSelected)
                    }
                }
            }
        }
        Text(stringResource(Res.string.budgets_part_movements), style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            if (!ledger.isLoading) Text(stringResource(Res.string.budgets_part_movements_empty), color = muted)
        } else {
            PartMovements(rows, onOpenMovement, Modifier.weight(1f))
        }
    }
}

/**
 * The months before this one as bars against the plan's amount (a dashed line), to be explored
 * with the pointer: the month under it stands out and says what it spent and how far from the
 * plan that was, and a click opens that month.
 */
@Composable
private fun MonthBars(months: List<YearMonth>, values: List<Long>, planned: Long?, onMonth: (YearMonth) -> Unit) {
    if (months.isEmpty() || values.all { it == 0L }) return
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val limit = planned?.takeIf { it > 0L }
    HoverBars(months, values, limit, onMonth, overLineInDebt = true) { index ->
        if (index == null) {
            val average = formatEuroCents(values.sum() / values.size)
            Text(
                limit?.let { stringResource(SharedRes.string.budget_history_over, values.count { cents -> cents > it }, values.size, average) }
                    ?: stringResource(SharedRes.string.budget_history_average, average),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(formatMonthYear(months[index]).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = muted)
            Text(formatEuroCents(values[index]), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            limit?.let {
                val over = values[index] - it
                Text(
                    stringResource(if (over > 0L) Res.string.budgets_month_over else Res.string.budgets_month_under, formatEuroCents(kotlin.math.abs(over))),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over > 0L) colors.debt else colors.income,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun Figure(label: String, figure: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = FinanceTheme.colors.mutedText)
        figure()
    }
}

/** The month's movements of a partida in the Movements table's own rows, drawing only the ones on screen. */
@Composable
internal fun PartMovements(rows: List<MovementSummary>, onOpen: (MovementSummary) -> Unit, modifier: Modifier, withinTag: Boolean = false) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = LedgerColumns(tick = false, category = maxWidth >= 700.dp, account = maxWidth >= 560.dp, context = maxWidth >= 900.dp, delete = false)
        FinanceCard(Modifier.fillMaxWidth()) {
            TableRow(
                columns = columns,
                height = 36.dp,
                tick = {},
                leading = {},
                name = { Text(stringResource(Res.string.column_name), style = header, color = muted) },
                category = { Text(stringResource(Res.string.column_category), style = header, color = muted) },
                account = { Text(stringResource(Res.string.column_account), style = header, color = muted) },
                context = { Text(stringResource(if (withinTag) Res.string.column_trip else Res.string.movements_column_context), style = header, color = muted) },
                date = { Text(stringResource(Res.string.column_date), style = header, color = muted) },
                amount = { Text(stringResource(Res.string.column_amount), style = header, color = muted, textAlign = TextAlign.End) },
                trailing = {},
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
            ScrollList {
                items(rows, key = { it.id }) { movement ->
                    HorizontalDivider(color = colors.cardBorder)
                    MovementRow(movement = movement, columns = columns, ticked = false, onTick = {}, onOpen = { onOpen(movement) }, onDelete = {}, withinTag = withinTag)
                }
            }
        }
    }
}

/**
 * What each partida, the rest and the whole spent in each recent month, against what the plan gives
 * it now: a month over its amount reads in the debt colour.
 */
@Composable
private fun HistoryTable(state: BudgetsUiState, history: SpendingHistory) {
    val plan = state.plan ?: return
    val muted = FinanceTheme.colors.mutedText
    HomeCard(stringResource(Res.string.budgets_history_by_part)) {
        Text(
            stringResource(SharedRes.string.budget_history_title, history.months.size),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        Column {
            HistoryRow(
                name = "",
                cells = history.months.map { formatShortMonth(it) to muted },
                current = formatShortMonth(state.selectedMonth) to MaterialTheme.colorScheme.primary,
                average = stringResource(Res.string.column_average),
                planned = stringResource(Res.string.column_plan),
                header = true,
            )
            // Each row: its name, the months before, what the plan gives it, and the selected month so far.
            val rows = plan.compartments.map { budget ->
                HistoryLine(
                    budget.displayName ?: stringResource(SharedRes.string.common_no_category),
                    history.byCategory[budget.categoryId] ?: List(history.months.size) { 0L },
                    budget.limitAmountCents,
                    plan.plan.compartments.getValue(budget.id).actualCents,
                )
            } +
                HistoryLine(stringResource(SharedRes.string.budget_plan_others), history.others, plan.plan.others.plannedCents, plan.plan.others.actualCents) +
                HistoryLine(stringResource(SharedRes.string.budget_field_total), history.total, plan.plan.total.plannedCents, plan.plan.total.actualCents)
            rows.forEachIndexed { index, (name, values, planned, current) ->
                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                HistoryRow(
                    name = name,
                    cells = values.map { cents ->
                        formatEuroCents(cents) to
                            if (planned != null && cents > planned) FinanceTheme.colors.debt else MaterialTheme.colorScheme.onSurface
                    },
                    current = formatEuroCents(current) to
                        if (planned != null && current > planned) FinanceTheme.colors.debt else MaterialTheme.colorScheme.onSurface,
                    average = formatEuroCents(if (values.isEmpty()) 0L else values.sum() / values.size),
                    planned = planned?.let(::formatEuroCents) ?: "—",
                    strong = index == rows.lastIndex,
                )
            }
        }
    }
}

private data class HistoryLine(val name: String, val values: List<Long>, val planned: Long?, val current: Long)

@Composable
private fun HistoryRow(
    name: String,
    cells: List<Pair<String, Color>>,
    /** The selected month, still in hand: it stands apart from the finished ones and is left out of the average. */
    current: Pair<String, Color>,
    average: String,
    planned: String,
    header: Boolean = false,
    strong: Boolean = false,
) {
    val style = when {
        header -> MaterialTheme.typography.labelLarge
        strong -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyMedium
    }
    val muted = FinanceTheme.colors.mutedText
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(2f), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        cells.forEach { (text, color) ->
            Text(text, Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End, maxLines = 1)
        }
        Text(current.first, Modifier.weight(1f), style = style, color = current.second, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 1)
        Text(average, Modifier.weight(1f), style = style, color = muted, textAlign = TextAlign.End, maxLines = 1)
        Text(planned, Modifier.width(112.dp), style = style, color = muted, textAlign = TextAlign.End, maxLines = 1)
    }
}
