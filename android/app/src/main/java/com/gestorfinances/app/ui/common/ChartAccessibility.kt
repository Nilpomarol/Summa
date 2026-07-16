package com.gestorfinances.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asFigures

/** One exact value row used by a chart's accessible data alternative. */
data class ChartDataRow(
    val label: String,
    val value: String,
)

@Composable
fun chartTrendLabel(first: Long?, last: Long?): String = when {
    first == null || last == null || last == first -> stringResource(R.string.accessibility_chart_no_trend)
    last > first -> stringResource(R.string.accessibility_chart_trend_up)
    else -> stringResource(R.string.accessibility_chart_trend_down)
}

@Composable
fun chartBalanceLabel(incomeCents: Long, expenseCents: Long): String = when {
    incomeCents > expenseCents -> stringResource(R.string.accessibility_chart_income_exceed_expenses)
    expenseCents > incomeCents -> stringResource(R.string.accessibility_chart_expenses_exceed_income)
    else -> stringResource(R.string.accessibility_chart_balanced)
}

/**
 * Gives a custom visual chart one stable TalkBack node and an optional exact-value alternative.
 * The visual is deliberately cleared of child semantics: axes, legends, and canvas labels are
 * visual aids, not extra stops in the reading order. The action and data rows follow the summary.
 */
@Composable
fun AccessibleChart(
    summary: String,
    dataRows: List<ChartDataRow>,
    modifier: Modifier = Modifier,
    visual: @Composable () -> Unit,
) {
    var showData by rememberSaveable(summary) { mutableStateOf(false) }
    val dataStateDescription = stringResource(
        if (showData) R.string.accessibility_chart_data_visible
        else R.string.accessibility_chart_data_hidden,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = summary },
        ) {
            visual()
        }

        if (dataRows.isNotEmpty()) {
            TextButton(
                onClick = { showData = !showData },
                modifier = Modifier.semantics {
                    stateDescription = dataStateDescription
                },
            ) {
                Text(
                    text = stringResource(
                        if (showData) R.string.accessibility_chart_hide_data
                        else R.string.accessibility_chart_show_data,
                    ),
                )
            }
        }

        if (showData) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.accessibility_chart_data_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    dataRows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = row.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = row.value,
                                style = MaterialTheme.typography.bodySmall.asFigures(),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (index != dataRows.lastIndex) {
                            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                        }
                    }
                }
            }
        }
    }
}
