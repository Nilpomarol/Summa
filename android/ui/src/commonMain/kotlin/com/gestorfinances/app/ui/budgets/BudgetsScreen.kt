package com.gestorfinances.app.ui.budgets

import com.gestorfinances.app.ui.common.OpenDialogs
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.gestorfinances.app.ui.movements.AppTextField
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.gestorfinances.app.ui.theme.colorToHsl
import com.gestorfinances.app.ui.theme.hslToColor
import androidx.compose.ui.graphics.lerp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.budget_add_yearly
import com.gestorfinances.ui.resources.budget_archive_confirm_title
import com.gestorfinances.ui.resources.budget_archive_warning
import com.gestorfinances.ui.resources.budget_edit_part_title
import com.gestorfinances.ui.resources.budget_edit_plan
import com.gestorfinances.ui.resources.budget_edit_yearly_title
import com.gestorfinances.ui.resources.budget_field_category
import com.gestorfinances.ui.resources.budget_field_limit
import com.gestorfinances.ui.resources.budget_field_total
import com.gestorfinances.ui.resources.budget_field_trip
import com.gestorfinances.ui.resources.budget_forecast_of_limit
import com.gestorfinances.ui.resources.budget_history_average
import com.gestorfinances.ui.resources.budget_history_over
import com.gestorfinances.ui.resources.budget_history_title
import com.gestorfinances.ui.resources.budget_include_extraordinary_expenses
import com.gestorfinances.ui.resources.budget_include_trip_expenses
import com.gestorfinances.ui.resources.budget_list_add
import com.gestorfinances.ui.resources.budget_list_title
import com.gestorfinances.ui.resources.budget_part_ahead
import com.gestorfinances.ui.resources.budget_part_left_due
import com.gestorfinances.ui.resources.budget_part_left_per_day
import com.gestorfinances.ui.resources.budget_part_may_exceed
import com.gestorfinances.ui.resources.budget_part_no_limit
import com.gestorfinances.ui.resources.budget_part_over
import com.gestorfinances.ui.resources.budget_plan_create
import com.gestorfinances.ui.resources.budget_plan_define_total
import com.gestorfinances.ui.resources.budget_plan_due
import com.gestorfinances.ui.resources.budget_plan_empty
import com.gestorfinances.ui.resources.budget_plan_ended_over
import com.gestorfinances.ui.resources.budget_plan_ended_under
import com.gestorfinances.ui.resources.budget_plan_eyebrow_this_month
import com.gestorfinances.ui.resources.budget_plan_heading_over
import com.gestorfinances.ui.resources.budget_plan_heading_under
import com.gestorfinances.ui.resources.budget_plan_left
import com.gestorfinances.ui.resources.budget_plan_no_total
import com.gestorfinances.ui.resources.budget_plan_others
import com.gestorfinances.ui.resources.budget_plan_over_by
import com.gestorfinances.ui.resources.budget_plan_overlap
import com.gestorfinances.ui.resources.budget_plan_per_day
import com.gestorfinances.ui.resources.budget_plan_section
import com.gestorfinances.ui.resources.budget_remaining
import com.gestorfinances.ui.resources.budget_save_changes
import com.gestorfinances.ui.resources.budget_save_new
import com.gestorfinances.ui.resources.budget_saving_actual
import com.gestorfinances.ui.resources.budget_saving_goals
import com.gestorfinances.ui.resources.budget_saving_legend_plan_left
import com.gestorfinances.ui.resources.budget_saving_legend_saved
import com.gestorfinances.ui.resources.budget_saving_legend_spent
import com.gestorfinances.ui.resources.budget_saving_legend_trips
import com.gestorfinances.ui.resources.budget_saving_of_income
import com.gestorfinances.ui.resources.budget_saving_planned
import com.gestorfinances.ui.resources.budget_saving_rate
import com.gestorfinances.ui.resources.budget_saving_vs_average
import com.gestorfinances.ui.resources.budget_setup_intro
import com.gestorfinances.ui.resources.budget_spend_eyebrow
import com.gestorfinances.ui.resources.budget_suggestion_monthly
import com.gestorfinances.ui.resources.budget_suggestion_use
import com.gestorfinances.ui.resources.budget_suggestion_yearly
import com.gestorfinances.ui.resources.budget_total_breakdown
import com.gestorfinances.ui.resources.budget_total_title
import com.gestorfinances.ui.resources.budget_trip_title
import com.gestorfinances.ui.resources.budget_year_to_date
import com.gestorfinances.ui.resources.budget_yearly_categories
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.failure_load_budgets
import com.gestorfinances.ui.resources.failure_save_budget
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import com.gestorfinances.app.ui.common.formatShortMonth
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.gestorfinances.app.data.repository.PlanIncome
import com.gestorfinances.app.data.repository.RecentSpending
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetMonthPlan
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.ui.trips.icon
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroMonthPicker
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.inPickerHierarchyOrder
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.heroIdentityColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onBack: () -> Unit,
    onOpenRecurring: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onOpenCategory: (categoryId: String) -> Unit = {},
    /** Changes after every committed financial write, so the page reloads while it stays visible. */
    dataVersion: Long = 0L,
    onDeleteCommitted: DeleteUndoHandler = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    BudgetsContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onAddPart = { viewModel.onAddClicked() },
        onEditPlan = viewModel::onAddOverallClicked,
        onCreatePlan = viewModel::onCreatePlanClicked,
        onAddYearly = viewModel::onAddYearlyClicked,
        onMonthSelected = viewModel::onMonthSelected,
        onEdit = viewModel::onEditClicked,
        onOpenRecurring = onOpenRecurring,
        onOpenGoals = onOpenGoals,
        onOpenCategory = onOpenCategory,
        onRetry = viewModel::onScreenShown,
    )

    BudgetFormSheet(state = state, viewModel = viewModel)
    PlanSetupSheet(state = state, viewModel = viewModel)
    BudgetArchiveDialog(state = state, viewModel = viewModel, onDeleteCommitted = onDeleteCommitted)
}

/**
 * The budget sheet over a category or trip page, opened by [BudgetsViewModel.editBudgetFor].
 * [onChanged] reloads the page once a budget is saved, deleted or restored.
 */
@Composable
fun BudgetSheetHost(
    viewModel: BudgetsViewModel,
    onChanged: () -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
) {
    val state by viewModel.state.collectAsState()
    // Loaded with the page, so its budget action opens the sheet at once.
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }
    LaunchedEffect(state.revision) {
        if (state.revision > 0) onChanged()
    }
    BudgetFormSheet(state = state, viewModel = viewModel)
    BudgetArchiveDialog(state = state, viewModel = viewModel, onDeleteCommitted = onDeleteCommitted)
}

@Composable
fun BudgetArchiveDialog(state: BudgetsUiState, viewModel: BudgetsViewModel, onDeleteCommitted: DeleteUndoHandler) {
    state.archiveCandidate ?: return
    OpenDialogs.Track()
    AlertDialog(
        onDismissRequest = viewModel::onArchiveDismissed,
        title = { Text(text = stringResource(Res.string.budget_archive_confirm_title)) },
        text = { Text(text = stringResource(Res.string.budget_archive_warning)) },
        confirmButton = {
            DestructiveTextButton(onClick = { viewModel.onArchiveConfirmed(onSuccess = onDeleteCommitted) }) {
                Text(text = stringResource(Res.string.common_archive))
            }
        },
        dismissButton = {
            AppTextButton(onClick = viewModel::onArchiveDismissed) {
                Text(text = stringResource(Res.string.common_cancel))
            }
        },
    )
}

/**
 * The month's plan: a forest hero with what has been spent against it, split into its parts,
 * then a row per part (each partida, opening its category, and the rest), what the month leaves to
 * save, and yearly limits. Trip budgets live on their trips.
 */
@Composable
fun BudgetsContent(
    state: BudgetsUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAddPart: () -> Unit,
    onEditPlan: () -> Unit,
    onCreatePlan: () -> Unit,
    onAddYearly: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onEdit: (BudgetSummary) -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenCategory: (categoryId: String) -> Unit,
    onRetry: () -> Unit,
) {
    ListPage(
        title = stringResource(Res.string.budget_list_title),
        onBack = onBack,
        addLabel = stringResource(Res.string.budget_list_add),
        onAdd = onAddPart,
        modifier = modifier,
        menu = listOf(
            EntityMenuAction(stringResource(Res.string.budget_edit_plan), onEditPlan),
            EntityMenuAction(stringResource(Res.string.budget_add_yearly), onAddYearly),
        ),
    ) {
        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = Res.string.failure_load_budgets,
                    onRetry = onRetry,
                )
            }
        }
        val plan = state.plan ?: return@ListPage
        val monthIsOpen = !state.selectedMonth.isBefore(YearMonth.from(state.today))

        item {
            PlanHero(
                plan = plan,
                month = state.selectedMonth,
                activityMonths = state.activityMonths,
                monthIsOpen = monthIsOpen,
                onMonthSelected = onMonthSelected,
                onEditPlan = onEditPlan,
                onCreatePlan = onCreatePlan,
                onOpenRecurring = onOpenRecurring,
            )
        }

        overlapNames(plan, state.categories)?.let { (child, parent) ->
            item { InlineBanner(kind = BannerKind.Alert, text = stringResource(Res.string.budget_plan_overlap, parent, child)) }
        }

        val hasPlan = plan.total != null || plan.compartments.isNotEmpty()
        if (hasPlan) {
            item { SectionHeader(title = stringResource(Res.string.budget_plan_section)) }
            item(key = "plan-rows") {
                // One item, so the rows sit flush and read as one list between their dividers.
                Column {
                    plan.compartments.forEach { budget ->
                        PlanPartRow(
                            leading = {
                                IdentityIconTile(icon = categoryIcon(budget.categoryIcon), color = categoryColor(budget.categoryColor))
                            },
                            title = budget.displayName ?: stringResource(Res.string.common_no_category),
                            part = plan.plan.compartments.getValue(budget.id),
                            color = themedIdentityColor(categoryColor(budget.categoryColor)),
                            monthIsOpen = monthIsOpen,
                            isLast = false,
                            onClick = { budget.categoryId?.let(onOpenCategory) },
                        )
                    }
                    PlanPartRow(
                        leading = { IdentityIconTile(icon = Icons.Outlined.MoreHoriz, color = FinanceTheme.colors.mutedText) },
                        title = stringResource(Res.string.budget_plan_others),
                        part = plan.plan.others,
                        color = FinanceTheme.colors.mutedText,
                        monthIsOpen = monthIsOpen,
                        isLast = true,
                        onClick = onEditPlan,
                    )
                }
            }
        }

        val income = state.income
        if (plan.total != null && income != null) {
            item {
                SectionHeader(
                    title = stringResource(if (monthIsOpen) Res.string.budget_saving_planned else Res.string.budget_saving_actual),
                    trailing = { LinkPill(text = stringResource(Res.string.budget_saving_goals), onClick = onOpenGoals) },
                )
            }
            item(key = "saving") { SavingSummary(plan = plan, income = income, tripCents = state.tripCents, monthIsOpen = monthIsOpen) }
        }

        val yearly = state.evaluations.filter { it.budget.scope == BudgetScope.CATEGORY && it.budget.period == BudgetPeriod.YEARLY }
        if (yearly.isNotEmpty()) {
            item { SectionHeader(title = stringResource(Res.string.budget_yearly_categories)) }
            item(key = "yearly-rows") {
                Column {
                    yearly.forEachIndexed { index, evaluation ->
                        LimitRow(
                            evaluation = evaluation,
                            leading = {
                                IdentityIconTile(
                                    icon = categoryIcon(evaluation.budget.categoryIcon),
                                    color = categoryColor(evaluation.budget.categoryColor),
                                )
                            },
                            subtitle = stringResource(
                                Res.string.budget_year_to_date,
                                state.selectedMonth.year,
                                formatMonth(state.selectedMonth).lowercase(),
                            ),
                            isLast = index == yearly.lastIndex,
                            onClick = { onEdit(evaluation.budget) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The month on the forest hero: what has been spent of the plan, the plan as one bar split into
 * its parts, under it what is left and what that leaves per day while the month is open, and one
 * sentence on where the month is heading or how it ended. Without a plan it shows the spending
 * and offers one.
 */
@Composable
fun PlanHero(
    plan: BudgetMonthPlan,
    month: YearMonth,
    activityMonths: List<YearMonth>,
    monthIsOpen: Boolean,
    onMonthSelected: (YearMonth) -> Unit,
    onEditPlan: () -> Unit,
    onCreatePlan: () -> Unit,
    onOpenRecurring: () -> Unit,
    /**
     * With a pointer: the bar's sections answer it, each saying what it is under the pointer and
     * opening on a click (the rest is a null id). Null where the bar is only looked at.
     */
    onPartSelected: ((budgetId: String?) -> Unit)? = null,
) {
    val colors = FinanceTheme.colors
    val total = plan.plan.total
    val planned = total.plannedCents
    val hasPlan = plan.total != null || plan.compartments.isNotEmpty()
    ListHero(
        // The page already says budgets and the picker names the month.
        eyebrow = stringResource(if (monthIsOpen) Res.string.budget_plan_eyebrow_this_month else Res.string.budget_spend_eyebrow),
        cents = total.actualCents,
        watermark = Icons.Outlined.AccountBalanceWallet,
        eyebrowTrailing = {
            HeroMonthPicker(month = month, months = activityMonths.ifEmpty { listOf(month) }, onMonthSelected = onMonthSelected)
        },
    ) {
        HeroCaption(
            text = when {
                planned != null -> stringResource(Res.string.budget_forecast_of_limit, formatEuroCents(planned))
                hasPlan -> stringResource(Res.string.budget_plan_no_total)
                else -> stringResource(Res.string.budget_plan_empty)
            },
        )
        if (!hasPlan || planned == null) {
            Spacer(modifier = Modifier.height(12.dp))
            HeroPill(
                text = stringResource(if (hasPlan) Res.string.budget_plan_define_total else Res.string.budget_plan_create),
                onClick = if (hasPlan) onEditPlan else onCreatePlan,
            )
        }
        if (hasPlan) {
            Spacer(modifier = Modifier.height(14.dp))
            PlanBar(plan = plan, onPartSelected = onPartSelected)
        }
        if (planned != null && monthIsOpen) {
            // What is left, the figure to act on, right under the bar it reads from.
            val left = planned - total.actualCents
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(
                        if (left < 0L) Res.string.budget_plan_over_by else Res.string.budget_plan_left,
                        formatEuroCents(abs(left)),
                    ),
                    color = if (left < 0L) colors.heroDebt else colors.heroOnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                total.perDayCents?.takeIf { left > 0L }?.let { perDay ->
                    Text(
                        text = stringResource(Res.string.budget_plan_per_day, formatEuroCents(perDay)),
                        color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (planned != null) {
            Spacer(modifier = Modifier.height(12.dp))
            // Where the month ends: its forecast while it is open, what it spent once closed.
            val end = if (monthIsOpen) total.forecastCents else total.actualCents
            val over = end > planned
            HeroVerdict(
                good = !over,
                text = stringResource(
                    when {
                        monthIsOpen && over -> Res.string.budget_plan_heading_over
                        monthIsOpen -> Res.string.budget_plan_heading_under
                        over -> Res.string.budget_plan_ended_over
                        else -> Res.string.budget_plan_ended_under
                    },
                    formatEuroCents(end),
                    formatEuroCents(abs(planned - end)),
                ),
            )
        }
        if (monthIsOpen && total.dueCents > 0L) {
            Spacer(modifier = Modifier.height(10.dp))
            HeroLink(text = stringResource(Res.string.budget_plan_due, formatEuroCents(total.dueCents)), onClick = onOpenRecurring)
        }
    }
}

/** The one thing to take from the plan, in a sentence on a soft band: fine, or heading over. */
@Composable
private fun HeroVerdict(good: Boolean, text: String) {
    val colors = FinanceTheme.colors
    val accent = if (good) colors.heroIncome else colors.heroDebt
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(heroTint(colors.heroOnSurface, 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (good) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, color = colors.heroOnSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A quiet line on the hero that opens another page. */
@Composable
private fun HeroLink(text: String, onClick: () -> Unit) {
    val color = FinanceTheme.colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(imageVector = Icons.Outlined.Repeat, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
        Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    }
}

/** A light pill on the hero for the one thing to do there. */
@Composable
private fun HeroPill(text: String, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    Text(
        text = text,
        color = colors.heroSurface,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(colors.heroIncome, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/**
 * The plan as one bar: each partida in its category's colour, then the rest, each as wide as what
 * it plans, in a darkened shade of its colour (fading it into the green panel would grey it) and
 * filled in the full colour by what it has spent (all of it, in the debt colour,
 * once over).
 */
@Composable
private fun PlanBar(plan: BudgetMonthPlan, onPartSelected: ((budgetId: String?) -> Unit)? = null) {
    val colors = FinanceTheme.colors
    val othersColor = heroTint(colors.heroOnSurface, 0.8f)
    val noCategory = stringResource(Res.string.common_no_category)
    val othersName = stringResource(Res.string.budget_plan_others)
    data class Segment(val weight: Long, val part: PlanPart, val color: Color, val track: Color, val id: String?, val name: String)
    val segments = buildList {
        plan.compartments.forEach { budget ->
            val part = plan.plan.compartments.getValue(budget.id)
            val color = heroIdentityColor(categoryColor(budget.categoryColor))
            // Spent stands out bright against a quieter, deeper shade of the same hue.
            add(Segment(budget.limitAmountCents, part, lerp(color, Color.White, 0.15f), color.subdued(), budget.id, budget.displayName ?: noCategory))
        }
        plan.plan.others.takeIf { (it.plannedCents ?: 0L) > 0L }
            ?.let { add(Segment(it.plannedCents!!, it, othersColor, heroTint(colors.heroOnSurface, 0.16f), null, othersName)) }
    }
    if (segments.isEmpty()) return
    val weightSum = segments.sumOf { it.weight }.toFloat()
    val debt = colors.heroDebt
    if (onPartSelected != null) {
        // The same bar as sections of its own, so each can answer the pointer.
        var pointed by remember(plan) { mutableStateOf<Int?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                segments.forEachIndexed { index, segment ->
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    LaunchedEffect(hovered) { if (hovered) pointed = index else if (pointed == index) pointed = null }
                    val planned = segment.part.plannedCents ?: 0L
                    val over = planned > 0L && segment.part.actualCents > planned
                    val filled = if (planned > 0L) (segment.part.actualCents.toFloat() / planned).coerceIn(0f, 1f) else 0f
                    val faded = pointed != null && !hovered
                    Box(
                        modifier = Modifier
                            .weight(segment.weight.coerceAtLeast(1L).toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(segment.track.copy(alpha = if (faded) 0.45f else 1f))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(interaction)
                            .clickable(interactionSource = interaction, indication = null) { onPartSelected(segment.id) },
                    ) {
                        if (filled > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(filled)
                                    .background((if (over) debt else segment.color).copy(alpha = if (faded) 0.45f else 1f)),
                            )
                        }
                    }
                }
            }
            // One line that keeps its room, so the panel does not jump as the pointer moves.
            val segment = pointed?.let(segments::getOrNull)
            Text(
                text = segment?.let {
                    listOfNotNull(
                        it.name,
                        formatEuroCents(it.part.actualCents) + " " + stringResource(Res.string.budget_forecast_of_limit, formatEuroCents(it.part.plannedCents ?: 0L)),
                    ).joinToString(" · ")
                }.orEmpty(),
                color = colors.heroOnSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.height(18.dp),
            )
        }
        return
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        val gap = 3.dp.toPx()
        val barTop = 0f
        val barHeight = size.height
        val usable = size.width - gap * (segments.size - 1)
        var x = 0f
        segments.forEach { segment ->
            val width = usable * segment.weight / weightSum
            val radius = CornerRadius(3.dp.toPx())
            drawRoundRect(segment.track, topLeft = Offset(x, barTop), size = Size(width, barHeight), cornerRadius = radius)
            val planned = segment.part.plannedCents ?: 0L
            val over = planned > 0L && segment.part.actualCents > planned
            val filled = if (planned > 0L) (segment.part.actualCents.toFloat() / planned).coerceIn(0f, 1f) else 0f
            if (filled > 0f) {
                drawRoundRect(
                    color = if (over) debt else segment.color,
                    topLeft = Offset(x, barTop),
                    size = Size(width * filled, barHeight),
                    cornerRadius = radius,
                )
            }
            x += width + gap
        }
    }
}

/** The colour's hue with less saturation and a fixed, lowish lightness: a section not yet spent. */
private fun Color.subdued(): Color {
    val hsl = colorToHsl(this)
    hsl[1] *= 0.55f
    hsl[2] = 0.36f
    return hslToColor(hsl)
}

/**
 * A partida (or the rest): what it has spent of what it plans, and the one thing worth saying
 * about it now — over, heading over, ahead of pace, or what is left and either its recurring
 * payments still to pay or what that leaves per day. The bar shows those payments still to pay.
 */
@Composable
fun PlanPartRow(
    leading: @Composable () -> Unit,
    title: String,
    part: PlanPart,
    color: Color,
    monthIsOpen: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val planned = part.plannedCents
    val (subtitle, subtitleColor) = when (part.status) {
        null -> stringResource(Res.string.budget_part_no_limit) to colors.mutedText
        PlanStatus.OVER -> stringResource(Res.string.budget_part_over, formatEuroCents(part.actualCents - planned!!)) to colors.debt
        PlanStatus.MAY_EXCEED -> stringResource(Res.string.budget_part_may_exceed, formatEuroCents(part.forecastCents - planned!!)) to colors.alert
        PlanStatus.AHEAD_OF_PACE -> stringResource(
            Res.string.budget_part_ahead,
            formatEuroCents(part.actualCents - (part.expectedByTodayCents ?: 0L)),
        ) to colors.alert
        PlanStatus.ON_TRACK -> {
            val left = formatEuroCents(planned!! - part.actualCents)
            when {
                monthIsOpen && part.dueCents > 0L ->
                    stringResource(Res.string.budget_part_left_due, left, formatEuroCents(part.dueCents))
                part.perDayCents != null -> stringResource(Res.string.budget_part_left_per_day, left, formatEuroCents(part.perDayCents!!))
                else -> stringResource(Res.string.budget_remaining, left)
            } to colors.mutedText
        }
    }
    val over = planned != null && part.actualCents > planned
    EntityListRow(
        leading = leading,
        title = title,
        below = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        footer = if (planned != null && planned > 0L) {
            {
                PlanRowBar(
                    fraction = progressOf(part.actualCents, planned),
                    color = if (over) colors.debt else color,
                    dueFraction = if (monthIsOpen) progressOf(part.dueCents, planned) else 0f,
                )
            }
        } else {
            null
        },
        isLast = isLast,
        onClick = onClick,
        trailing = { SpentOfPlanned(actualCents = part.actualCents, plannedCents = planned) },
    )
}

/**
 * What the month leaves to save: the figure against income and its share of it, how it compares with
 * recent months, and one bar of the month's income — spent, left in the plan, saved. While the month is open
 * the saving is expected income less the plan (or what has been spent, once past it) and less
 * [tripCents], what trips outside the plan have spent or still plan to; once closed, what came in
 * less everything spent (trips included).
 */
@Composable
fun SavingSummary(
    plan: BudgetMonthPlan,
    income: PlanIncome,
    tripCents: Long,
    monthIsOpen: Boolean,
    /** With a pointer: a section under it stands out, with its line of the legend. */
    interactive: Boolean = false,
) {
    val colors = FinanceTheme.colors
    val planned = plan.plan.total.plannedCents ?: return
    val spent = if (monthIsOpen) plan.plan.total.actualCents else income.allExpenseCents
    val toSpend = if (monthIsOpen) (planned - spent).coerceAtLeast(0L) else 0L
    val incomeCents = if (monthIsOpen) income.expectedCents else income.actualCents
    val trips = if (monthIsOpen) tripCents else 0L
    val saving = incomeCents - spent - toSpend - trips
    val savingColor = if (saving < 0L) colors.debt else colors.income
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                MoneyText(cents = saving, color = savingColor, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(Res.string.budget_saving_of_income, formatEuroCents(incomeCents)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
                )
            }
            if (saving > 0L && incomeCents > 0L) {
                Text(
                    text = stringResource(Res.string.budget_saving_rate, (saving * 100 / incomeCents).toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.income,
                    modifier = Modifier
                        .background(colors.income.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        income.recentSavingCents?.let { recent ->
            val difference = saving - recent
            val color = if (difference >= 0L) colors.income else colors.mutedText
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (difference >= 0L) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(
                        Res.string.budget_saving_vs_average,
                        (if (difference > 0L) "+" else "") + formatEuroCents(difference),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
        }
        val spentColor = colors.mutedText
        val toSpendColor = colors.mutedText.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.background)
        val tripColor = colors.shared
        val segments = listOf(spent to spentColor, toSpend to toSpendColor, trips to tripColor, saving.coerceAtLeast(0L) to colors.income)
            .filter { it.first > 0L }
        var pointed by remember(segments) { mutableStateOf<Color?>(null) }
        if (segments.isNotEmpty() && interactive) {
            Row(modifier = Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                segments.forEach { (cents, color) ->
                    val interaction = remember(color) { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    LaunchedEffect(hovered) { if (hovered) pointed = color else if (pointed == color) pointed = null }
                    Box(
                        modifier = Modifier
                            .weight(cents.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = if (pointed != null && !hovered) 0.4f else 1f))
                            .hoverable(interaction),
                    )
                }
            }
        }
        if (segments.isNotEmpty()) {
            if (!interactive) Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                val gap = 3.dp.toPx()
                val usable = size.width - gap * (segments.size - 1)
                val whole = segments.sumOf { it.first }.toFloat()
                var x = 0f
                segments.forEach { (cents, color) ->
                    val width = usable * cents / whole
                    drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                    x += width + gap
                }
            }
            SavingLegend(
                listOfNotNull(
                    stringResource(Res.string.budget_saving_legend_spent, formatEuroCents(spent)) to spentColor,
                    (stringResource(Res.string.budget_saving_legend_plan_left, formatEuroCents(toSpend)) to toSpendColor).takeIf { toSpend > 0L },
                    (stringResource(Res.string.budget_saving_legend_trips, formatEuroCents(trips)) to tripColor).takeIf { trips > 0L },
                    (stringResource(Res.string.budget_saving_legend_saved) to colors.income).takeIf { saving > 0L },
                ),
                pointed = pointed,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavingLegend(items: List<Pair<String, Color>>, pointed: Color? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (label, color) ->
            // The section under the pointer is named in full strength; the others step back.
            val chosen = pointed == color
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(if (pointed != null && !chosen) 0.45f else 1f),
            ) {
                Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) MaterialTheme.colorScheme.onSurface else FinanceTheme.colors.mutedText,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** A yearly limit: what it has spent of its limit, with a bar in its status colour. */
@Composable
fun LimitRow(
    evaluation: BudgetEvaluation,
    leading: @Composable () -> Unit,
    subtitle: String?,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val remaining = evaluation.remainingCents
    EntityListRow(
        leading = leading,
        title = evaluation.budget.displayName ?: stringResource(Res.string.common_no_category),
        below = {
            Text(
                text = subtitle ?: if (remaining >= 0L) {
                    stringResource(Res.string.budget_remaining, formatEuroCents(remaining))
                } else {
                    stringResource(Res.string.budget_part_over, formatEuroCents(-remaining))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (subtitle == null && remaining < 0L) colors.debt else colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        footer = { PlanRowBar(fraction = evaluation.progressFraction(), color = evaluation.status.color()) },
        isLast = isLast,
        onClick = onClick,
        trailing = { SpentOfPlanned(actualCents = evaluation.actualCents, plannedCents = evaluation.budget.limitAmountCents) },
    )
}

@Composable
private fun SpentOfPlanned(actualCents: Long, plannedCents: Long?) {
    Column(horizontalAlignment = Alignment.End) {
        MoneyText(cents = actualCents, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        if (plannedCents != null) {
            Text(
                text = stringResource(Res.string.budget_forecast_of_limit, formatEuroCents(plannedCents)),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.mutedText,
            )
        }
    }
}

/**
 * A row's thin bar: what has been spent and, after it, a lighter stretch for recurring payments
 * still to pay.
 */
@Composable
private fun PlanRowBar(fraction: Float, color: Color, dueFraction: Float = 0f) {
    val track = FinanceTheme.colors.progressTrack
    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        val barTop = 0f
        val barHeight = size.height
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(track, topLeft = Offset(0f, barTop), size = Size(size.width, barHeight), cornerRadius = radius)
        val due = dueFraction.coerceAtMost(1f - fraction)
        if (due > 0f) {
            drawRoundRect(
                color.copy(alpha = 0.35f).compositeOver(track),
                topLeft = Offset(0f, barTop),
                size = Size(size.width * (fraction + due), barHeight),
                cornerRadius = radius,
            )
        }
        if (fraction > 0f) {
            drawRoundRect(color, topLeft = Offset(0f, barTop), size = Size(size.width * fraction, barHeight), cornerRadius = radius)
        }
    }
}

private fun progressOf(actualCents: Long, plannedCents: Long?): Float =
    if (plannedCents == null || plannedCents <= 0L) 0f else (actualCents.toFloat() / plannedCents).coerceIn(0f, 1f)

/** The first partida whose parent category is a partida too, as (child, parent) names. */
fun overlapNames(plan: BudgetMonthPlan, categories: List<CategoryRecord>): Pair<String, String>? {
    if (plan.overlappingBudgetIds.isEmpty()) return null
    val byCategory = plan.compartments.associateBy { it.categoryId }
    val parentOf = categories.associate { it.id to it.parentId }
    val child = plan.compartments.firstOrNull { parentOf[it.categoryId]?.let(byCategory::containsKey) == true } ?: return null
    val parent = byCategory.getValue(parentOf[child.categoryId])
    return (child.displayName.orEmpty()) to (parent.displayName.orEmpty())
}

/**
 * A budget's create/edit sheet, shaped by what it is: the month's total (with what it counts), a
 * partida or yearly limit (a category and its amount, with what recent months suggest), or a trip.
 */
@Composable
fun BudgetFormSheet(state: BudgetsUiState, viewModel: BudgetsViewModel) {
    var detailsOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-${it.scope}-${it.period}" },
        changed = { initial, current -> initial.withoutErrors() != current.withoutErrors() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(it.titleRes()) },
        saveLabel = { stringResource(if (it.id == null) Res.string.budget_save_new else Res.string.budget_save_changes) },
        onDelete = state.form?.id?.let { { _: BudgetFormState -> viewModel.onDeleteEditingBudgetClicked() } },
    ) { form ->
        val edit = viewModel::onFormChanged
        form.errorMessage?.let { InlineFailureBanner(diagnostic = it, messageRes = Res.string.failure_save_budget) }
        if (form.errorField == null && form.errorRes != null) {
            InlineBanner(kind = BannerKind.Error, text = stringResource(form.errorRes))
        }
        when (form.scope) {
            BudgetScope.OVERALL_MONTH -> {
                LimitField(form = form, label = stringResource(Res.string.budget_field_total), onChange = edit)
                TotalBreakdown(form = form, plan = state.plan)
                state.history?.let { history ->
                    SpendingHistoryChart(months = history.months, values = history.total, plannedText = form.limit)
                }
                val summary = listOfNotNull(
                    stringResource(Res.string.budget_include_trip_expenses).takeIf { form.includeTripExpenses },
                ).joinToString(" · ")
                FormDisclosure(open = detailsOpen, onToggle = { detailsOpen = !detailsOpen }, summary = summary) {
                    FormToggleRow(
                        label = stringResource(Res.string.budget_include_trip_expenses),
                        checked = form.includeTripExpenses,
                        onCheckedChange = { edit(form.copy(includeTripExpenses = it)) },
                    )
                    FormToggleRow(
                        label = stringResource(Res.string.budget_include_extraordinary_expenses),
                        checked = form.includeExtraordinaryExpenses,
                        onCheckedChange = { edit(form.copy(includeExtraordinaryExpenses = it)) },
                    )
                }
            }
            BudgetScope.CATEGORY -> {
                val categoryError = form.errorField == BudgetFormField.CATEGORY
                FormSelect(
                    label = stringResource(Res.string.budget_field_category),
                    // A parent stays selectable, counting its subcategories; they sit indented beneath it.
                    options = state.categories.inPickerHierarchyOrder().map { (category, indented) ->
                        SelectOption(
                            id = category.id,
                            label = category.name,
                            leading = {
                                IconChip(
                                    icon = categoryIcon(category.icon),
                                    contentDescription = null,
                                    color = categoryColor(category.color),
                                    size = 24.dp,
                                )
                            },
                            indented = indented,
                        )
                    },
                    selectedId = form.categoryId,
                    onSelect = { edit(form.copy(categoryId = it)) },
                    modifier = Modifier.scrollToWhen(categoryError),
                    isError = categoryError,
                    supportingText = form.errorRes?.takeIf { categoryError }?.let { stringResource(it) },
                )
                LimitField(form = form, label = stringResource(Res.string.budget_field_limit), onChange = edit)
                SpendingSuggestion(form = form, recent = state.recentSpending, onUse = { edit(form.copy(limit = it)) })
                val history = state.history
                val categoryId = form.categoryId
                if (history != null && categoryId != null && form.period == BudgetPeriod.MONTHLY) {
                    SpendingHistoryChart(
                        months = history.months,
                        values = history.byCategory[categoryId] ?: List(history.months.size) { 0L },
                        plannedText = form.limit,
                    )
                }
            }
            BudgetScope.TRIP -> {
                val tripError = form.errorField == BudgetFormField.TRIP
                FormSelect(
                    label = stringResource(Res.string.budget_field_trip),
                    options = state.trips.map { trip ->
                        SelectOption(
                            id = trip.id,
                            label = trip.name,
                            leading = {
                                IconChip(
                                    icon = trip.type.icon(),
                                    contentDescription = null,
                                    color = categoryColor(trip.color),
                                    size = 24.dp,
                                )
                            },
                        )
                    },
                    selectedId = form.tripId,
                    onSelect = { edit(form.copy(tripId = it)) },
                    modifier = Modifier.scrollToWhen(tripError),
                    isError = tripError,
                    supportingText = form.errorRes?.takeIf { tripError }?.let { stringResource(it) },
                )
                LimitField(form = form, label = stringResource(Res.string.budget_field_limit), onChange = edit)
            }
        }
    }
}

@Composable
private fun LimitField(form: BudgetFormState, label: String, onChange: (BudgetFormState) -> Unit) {
    val limitError = form.errorField == BudgetFormField.LIMIT
    AppTextField(
        value = form.limit,
        onValueChange = { onChange(form.copy(limit = it)) },
        label = { Text(text = label) },
        prefix = { Text(text = "€") },
        singleLine = true,
        isError = limitError,
        supportingText = form.errorRes?.takeIf { limitError }?.let { { Text(text = stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().scrollToWhen(limitError),
    )
}

/** How the total being typed splits: the partides, and what is left for the rest. */
@Composable
private fun TotalBreakdown(form: BudgetFormState, plan: BudgetMonthPlan?) {
    plan ?: return
    val total = parseEuroCents(form.limit, allowNegative = false) ?: return
    val partides = plan.compartments.sumOf { it.limitAmountCents }
    val others = total - partides
    Text(
        text = stringResource(
            Res.string.budget_total_breakdown,
            formatEuroCents(partides),
            formatEuroCents(others),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (others < 0L) FinanceTheme.colors.debt else FinanceTheme.colors.mutedText,
    )
}

/**
 * The months before this one against the amount being set: a bar per month (in the debt colour
 * where it went over), a dashed line at the amount, and how often it went over and the average.
 * The line moves as the amount is typed.
 */
@Composable
fun SpendingHistoryChart(months: List<YearMonth>, values: List<Long>, plannedText: String) {
    if (months.isEmpty() || values.all { it == 0L }) return
    val colors = FinanceTheme.colors
    val planned = parseEuroCents(plannedText, allowNegative = false)?.takeIf { it > 0L }
    val barColor = MaterialTheme.colorScheme.primary
    val lineColor = colors.mutedText
    val debt = colors.debt
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.budget_history_title, months.size),
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedText,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
            val top = maxOf(values.max(), planned ?: 0L).coerceAtLeast(1L).toFloat()
            // Headroom, so a line at the very top stays clear of the title above it.
            val chartHeight = size.height - 6.dp.toPx()
            val slot = size.width / values.size
            val barWidth = slot * 0.5f
            values.forEachIndexed { index, cents ->
                val height = chartHeight * cents / top
                drawRoundRect(
                    color = if (planned != null && cents > planned) debt else barColor,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            planned?.let {
                val y = size.height - chartHeight * it / top
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            months.forEach { month ->
                Text(
                    text = formatShortMonth(month),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val average = formatEuroCents(values.sum() / values.size)
        Text(
            text = planned?.let { limit ->
                stringResource(Res.string.budget_history_over, values.count { it > limit }, values.size, average)
            } ?: stringResource(Res.string.budget_history_average, average),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedText,
        )
    }
}

/** What the chosen category spent recently, per month (or at that pace over a year), one tap from being the amount. */
@Composable
private fun SpendingSuggestion(form: BudgetFormState, recent: RecentSpending?, onUse: (String) -> Unit) {
    val monthly = form.categoryId?.let { recent?.byCategory?.get(it) }?.takeIf { it > 0L } ?: return
    val yearly = form.period == BudgetPeriod.YEARLY
    val suggested = roundUpToTen(if (yearly) monthly * 12 else monthly)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (yearly) Res.string.budget_suggestion_yearly else Res.string.budget_suggestion_monthly,
                formatEuroCents(suggested),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = FinanceTheme.colors.mutedText,
            modifier = Modifier.weight(1f),
        )
        AppTextButton(onClick = { onUse(formatEuroInput(suggested)) }) {
            Text(text = stringResource(Res.string.budget_suggestion_use))
        }
    }
}

/**
 * A proposed plan to adjust before creating it: the categories that spend most as partides (each
 * can be left out or changed), what that leaves for the rest, and the total.
 */
@Composable
fun PlanSetupSheet(state: BudgetsUiState, viewModel: BudgetsViewModel) {
    EntityFormSheet(
        form = state.planSetup,
        key = { "plan-setup" },
        changed = { initial, current -> initial.rows != current.rows || initial.total != current.total },
        onDiscard = viewModel::onPlanSetupDismissed,
        onSave = viewModel::onPlanSetupSaved,
        title = { stringResource(Res.string.budget_plan_create) },
        saveLabel = { stringResource(Res.string.budget_plan_create) },
        saving = { it.isSaving },
    ) { setup ->
        val edit = viewModel::onPlanSetupChanged
        setup.errorMessage?.let { InlineFailureBanner(diagnostic = it, messageRes = Res.string.failure_save_budget) }
        setup.errorRes?.let { InlineBanner(kind = BannerKind.Error, text = stringResource(it)) }
        Text(
            text = stringResource(Res.string.budget_setup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = FinanceTheme.colors.mutedText,
        )
        setup.rows.forEachIndexed { index, row ->
            val category = state.categories.firstOrNull { it.id == row.categoryId } ?: return@forEachIndexed
            val update = { changed: PlanSetupRow -> edit(setup.copy(rows = setup.rows.toMutableList().also { it[index] = changed })) }
            SetupLine(
                leading = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = row.selected, onCheckedChange = { update(row.copy(selected = it)) })
                        IdentityIconTile(icon = categoryIcon(category.icon), color = categoryColor(category.color), size = 36.dp)
                    }
                },
                title = category.name,
            ) {
                AppTextField(
                    value = row.amount,
                    onValueChange = { update(row.copy(amount = it)) },
                    enabled = row.selected,
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.width(124.dp),
                )
            }
        }
        val total = parseEuroCents(setup.total, allowNegative = false) ?: 0L
        val others = total - setup.rows.filter { it.selected }.sumOf { parseEuroCents(it.amount, allowNegative = false) ?: 0L }
        SetupLine(
            leading = { IdentityIconTile(icon = Icons.Outlined.MoreHoriz, color = FinanceTheme.colors.mutedText, size = 36.dp) },
            title = stringResource(Res.string.budget_plan_others),
        ) {
            MoneyText(
                cents = others,
                style = MaterialTheme.typography.titleMedium,
                color = if (others < 0L) FinanceTheme.colors.debt else MaterialTheme.colorScheme.onSurface,
                signed = others < 0L,
            )
        }
        AppTextField(
            value = setup.total,
            onValueChange = { edit(setup.copy(total = it)) },
            label = { Text(text = stringResource(Res.string.budget_field_total)) },
            prefix = { Text(text = "€") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SetupLine(leading: @Composable () -> Unit, title: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading()
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

private fun BudgetFormState.titleRes(): StringResource = when {
    scope == BudgetScope.OVERALL_MONTH -> Res.string.budget_total_title
    scope == BudgetScope.TRIP -> Res.string.budget_trip_title
    period == BudgetPeriod.YEARLY -> if (id == null) Res.string.budget_add_yearly else Res.string.budget_edit_yearly_title
    else -> if (id == null) Res.string.budget_list_add else Res.string.budget_edit_part_title
}

private fun BudgetFormState.withoutErrors(): BudgetFormState = copy(errorRes = null, errorField = null, errorMessage = null)
