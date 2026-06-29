package com.gestorfinances.app.ui.analysis.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisAccountFlowBucket
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisCategoryTrendPoint
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisLargestExpense
import com.gestorfinances.app.data.repository.AnalysisMerchantTotal
import com.gestorfinances.app.data.repository.AnalysisNetWorthPoint
import com.gestorfinances.app.data.repository.AnalysisOneTimeMode
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IncomeExpenseChart
import com.gestorfinances.app.ui.common.IncomeExpenseChartPoint
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.TrendLineChart
import com.gestorfinances.app.ui.common.TrendSeries
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatLongDate
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementOneTimeMode as MovementFilterOneTimeMode
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

import com.gestorfinances.app.ui.analysis.*

@Composable
internal fun AnalysisModeControls(
    state: AnalysisUiState,
    onAnalysisModeSelected: (AnalysisMode) -> Unit,
    onValueModeSelected: (AnalysisValueMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedControl(
            options = AnalysisMode.entries,
            selected = state.analysisMode,
            label = { stringResource(it.labelRes()) },
            onSelect = onAnalysisModeSelected,
        )
        SegmentedControl(
            options = AnalysisValueMode.entries,
            selected = state.valueMode,
            label = { stringResource(it.labelRes()) },
            onSelect = onValueModeSelected,
        )
    }
}

@Composable
internal fun ActualFilterControls(
    state: AnalysisUiState,
    onNatureFilterSelected: (AnalysisNatureFilter) -> Unit,
    onOneTimeModeSelected: (AnalysisOneTimeMode) -> Unit,
    onGroupTripsAsBlocksChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.groupTripsAsBlocks,
                    onCheckedChange = onGroupTripsAsBlocksChange,
                )
                Text(
                    text = stringResource(R.string.trip_analysis_group_as_block),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        ChipFlowSection(label = stringResource(R.string.analysis_filter_nature)) {
            AnalysisNatureFilter.entries.forEach { filter ->
                FinanceFilterChip(
                    selected = state.natureFilter == filter,
                    label = stringResource(filter.labelRes()),
                    onClick = { onNatureFilterSelected(filter) },
                )
            }
        }
        ChipFlowSection(label = stringResource(R.string.movement_field_one_time)) {
            AnalysisOneTimeMode.entries.forEach { mode ->
                FinanceFilterChip(
                    selected = state.oneTimeMode == mode,
                    label = stringResource(mode.labelRes()),
                    onClick = { onOneTimeModeSelected(mode) },
                )
            }
        }
    }
}

@Composable
internal fun PeriodSelector(
    state: AnalysisUiState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canMovePeriod) {
                TextButton(onClick = onPreviousPeriod) {
                    Text(text = stringResource(R.string.common_back))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.analysis_period_current),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = state.currentRange?.formatForScope(state.scope)
                        ?: state.fallbackPeriodLabel(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.canMovePeriod) {
                TextButton(onClick = onNextPeriod) {
                    Text(text = stringResource(R.string.common_next))
                }
            }
        }
    }
}

@Composable
internal fun CustomDateFields(
    state: AnalysisUiState,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.customFrom,
                    onValueChange = onCustomFromChange,
                    label = { Text(text = stringResource(R.string.movement_filter_date_from)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.customTo,
                    onValueChange = onCustomToChange,
                    label = { Text(text = stringResource(R.string.movement_filter_date_to)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun ComparePreviousRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            Text(
                text = stringResource(R.string.analysis_compare_previous),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
