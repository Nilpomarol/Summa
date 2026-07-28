package com.gestorfinances.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.LocalDate

/** One day cell of the [SpendingHeatmap], keyed by date with its total expense. */
data class HeatmapCell(
    val date: LocalDate,
    val expenseCents: Long,
)

/**
 * A calendar/contribution-style spend grid: one square per day, darker = more spent.
 * Spend intensity is a neutral ink ramp (expense is ink, not a hue — design baseline).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingHeatmap(
    cells: List<HeatmapCell>,
    modifier: Modifier = Modifier,
    accessibilitySummary: String,
    accessibilityRows: List<ChartDataRow> = emptyList(),
) {
    val maxCents = cells.maxOfOrNull { it.expenseCents }?.coerceAtLeast(1L) ?: 1L
    val base = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    AccessibleChart(
        summary = accessibilitySummary,
        dataRows = accessibilityRows,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                cells.forEach { cell ->
                    val intensity = cell.expenseCents.toFloat() / maxCents.toFloat()
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(heatColor(intensity, base, ink), MaterialTheme.shapes.extraSmall),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.analysis_heatmap_legend_less),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
                listOf(0f, 0.33f, 0.66f, 1f).forEach { intensity ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(heatColor(intensity, base, ink), MaterialTheme.shapes.extraSmall),
                    )
                }
                Text(
                    text = stringResource(R.string.analysis_heatmap_legend_more),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun heatColor(intensity: Float, base: Color, ink: Color): Color {
    if (intensity <= 0f) return base
    return lerp(base, ink, (0.15f + 0.85f * intensity.coerceIn(0f, 1f)))
}
