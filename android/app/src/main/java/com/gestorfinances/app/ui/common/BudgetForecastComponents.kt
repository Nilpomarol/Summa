package com.gestorfinances.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetForecastStatus
import com.gestorfinances.app.data.repository.BudgetProjection
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/** Shared current-month budget card for the Dashboard and the Budget page. */
@Composable
fun BudgetForecastCard(
    title: String,
    projection: BudgetProjection,
    modifier: Modifier = Modifier,
    showBreakdown: Boolean = false,
    exceptions: List<BudgetProjection> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val statusColor = projection.status.color()
    FinanceCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BudgetForecastStatusPill(
                    status = projection.status,
                    remainingCents = projection.remainingForecastCents,
                    color = statusColor,
                )
            }
            Text(
                text = stringResource(
                    R.string.budget_progress,
                    formatEuroCents(projection.evaluation.actualCents),
                    formatEuroCents(projection.evaluation.budget.limitAmountCents),
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            BudgetForecastProgressBar(
                actualFraction = projection.actualProgressFraction(),
                recurringFraction = projection.recurringProgressFraction(),
                forecastFraction = projection.forecastProgressFraction(),
                forecastColor = statusColor,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.budget_forecast_final_amount,
                        formatEuroCents(projection.forecastCents),
                    ),
                    modifier = Modifier.weight(1f),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BudgetForecastLegend(statusColor = statusColor)
            }
            if (showBreakdown) {
                BudgetForecastBreakdown(projection)
            }
            if (exceptions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    exceptions.forEach { exception ->
                        BudgetForecastExceptionRow(exception)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetForecastStatusPill(
    status: BudgetForecastStatus,
    remainingCents: Long,
    color: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = FORECAST_TONE_ALPHA),
        contentColor = color,
    ) {
        Text(
            text = status.label(remainingCents),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun BudgetForecastProgressBar(
    actualFraction: Float,
    recurringFraction: Float,
    forecastFraction: Float,
    forecastColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(forecastFraction)
                .height(6.dp)
                .background(forecastColor.copy(alpha = FORECAST_TONE_ALPHA)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(recurringFraction)
                .height(6.dp)
                .background(forecastColor.copy(alpha = RECURRING_TONE_ALPHA)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(actualFraction)
                .height(6.dp)
                .background(FinanceTheme.colors.expense),
        )
    }
}

/**
 * Recorded spending uses the normal expense tone. Forecast-only spending is a lighter status
 * tone, so the projected part of the bar cannot be mistaken for a confirmed movement.
 */
@Composable
private fun BudgetForecastLegend(statusColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BudgetForecastLegendItem(
            color = FinanceTheme.colors.expense,
            label = stringResource(R.string.budget_forecast_legend_recorded),
        )
        BudgetForecastLegendItem(
            color = statusColor.copy(alpha = RECURRING_TONE_ALPHA),
            label = stringResource(R.string.budget_forecast_legend_recurring),
        )
        BudgetForecastLegendItem(
            color = statusColor.copy(alpha = FORECAST_TONE_ALPHA),
            label = stringResource(R.string.budget_forecast_legend_estimated),
        )
    }
}

@Composable
private fun BudgetForecastLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
        )
        Text(
            text = label,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BudgetForecastBreakdown(projection: BudgetProjection) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ForecastBreakdownRow(
            label = stringResource(R.string.budget_forecast_recorded),
            cents = projection.evaluation.actualCents,
        )
        ForecastBreakdownRow(
            label = stringResource(R.string.budget_forecast_pending_recurring),
            cents = projection.pendingRecurringCents,
        )
        if (projection.hasBehaviourEstimate) {
            ForecastBreakdownRow(
                label = stringResource(R.string.budget_forecast_variable_estimate),
                cents = projection.estimatedVariableCents,
            )
        } else {
            Text(
                text = stringResource(R.string.budget_forecast_no_history),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ForecastBreakdownRow(label: String, cents: Long) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodySmall,
        )
        MoneyText(cents = cents, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BudgetForecastExceptionRow(projection: BudgetProjection) {
    val name = projection.evaluation.budget.categoryName ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconChip(
            icon = categoryIcon(projection.evaluation.budget.categoryIcon),
            contentDescription = null,
            color = categoryColor(projection.evaluation.budget.categoryColor),
            size = 28.dp,
        )
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = projection.status.label(projection.remainingForecastCents),
            color = projection.status.color(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

fun BudgetProjection.forecastProgressFraction(): Float {
    val limit = evaluation.budget.limitAmountCents
    if (limit <= 0L) return 0f
    return (forecastCents.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}

fun BudgetProjection.actualProgressFraction(): Float {
    val limit = evaluation.budget.limitAmountCents
    if (limit <= 0L) return 0f
    return (evaluation.actualCents.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}

fun BudgetProjection.recurringProgressFraction(): Float {
    val limit = evaluation.budget.limitAmountCents
    if (limit <= 0L) return 0f
    return ((evaluation.actualCents + pendingRecurringCents).toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}

private const val FORECAST_TONE_ALPHA = 0.42f
private const val RECURRING_TONE_ALPHA = 0.7f

@Composable
fun BudgetForecastStatus.color() = when (this) {
    BudgetForecastStatus.ON_TRACK -> FinanceTheme.colors.income
    BudgetForecastStatus.MAY_EXCEED -> FinanceTheme.colors.alert
    BudgetForecastStatus.OVER -> FinanceTheme.colors.debt
}

@Composable
fun BudgetForecastStatus.label(remainingCents: Long): String =
    when (this) {
        BudgetForecastStatus.ON_TRACK -> stringResource(R.string.budget_forecast_on_track)
        BudgetForecastStatus.MAY_EXCEED -> stringResource(
            R.string.budget_forecast_may_exceed,
            formatEuroCents(-remainingCents),
        )
        BudgetForecastStatus.OVER -> stringResource(
            R.string.budget_forecast_over,
            formatEuroCents(-remainingCents),
        )
    }
