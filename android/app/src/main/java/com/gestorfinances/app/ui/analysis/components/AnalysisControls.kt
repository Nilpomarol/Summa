package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.analysis.AnalysisScope
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.AnalysisValueMode
import com.gestorfinances.app.ui.analysis.fallbackPeriodLabel
import com.gestorfinances.app.ui.analysis.formatForScope
import com.gestorfinances.app.ui.analysis.labelRes
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FilterSelectorField
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MonthPickerContent
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.common.formatExpandedDate
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** The persistent two-row analysis header: labeled scope/value selectors + filters, then period navigator. */
@Composable
internal fun AnalysisHeader(
    state: AnalysisUiState,
    onScopeSelected: (AnalysisScope) -> Unit,
    onValueModeSelected: (AnalysisValueMode) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onYearSelected: (Int) -> Unit,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Row 1 — labeled scope/value selectors + filter button.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScopeSelector(
                scope = state.scope,
                onScopeSelected = onScopeSelected,
                modifier = Modifier.weight(1f),
            )
            ValueModeSelector(
                valueMode = state.valueMode,
                onValueModeSelected = onValueModeSelected,
                modifier = Modifier.weight(1f),
            )
            FilterButton(active = state.hasActiveFilters, onClick = onOpenFilters)
        }
        // Row 2 — period navigator.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PeriodNavigator(
                    state = state,
                    onPreviousPeriod = onPreviousPeriod,
                    onNextPeriod = onNextPeriod,
                    onMonthSelected = onMonthSelected,
                    onYearSelected = onYearSelected,
                    onCustomFromChange = onCustomFromChange,
                    onCustomToChange = onCustomToChange,
                )
            }
        }
        state.customErrorRes?.let { res ->
            InlineBanner(kind = BannerKind.Error, text = stringResource(res))
        }
    }
}

@Composable
private fun ScopeSelector(
    scope: AnalysisScope,
    onScopeSelected: (AnalysisScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterSelectorField(
            label = stringResource(R.string.analysis_scope_label),
            value = stringResource(scope.labelRes()),
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AnalysisScope.entries.forEach { option ->
                AppDropdownMenuItem(
                    text = { Text(text = stringResource(option.labelRes())) },
                    selected = option == scope,
                    onClick = {
                        expanded = false
                        onScopeSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ValueModeSelector(
    valueMode: AnalysisValueMode,
    onValueModeSelected: (AnalysisValueMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterSelectorField(
            label = stringResource(R.string.analysis_value_mode_label),
            value = stringResource(valueMode.labelRes()),
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AnalysisValueMode.entries.forEach { option ->
                AppDropdownMenuItem(
                    text = { Text(text = stringResource(option.labelRes())) },
                    selected = option == valueMode,
                    onClick = {
                        expanded = false
                        onValueModeSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    Box {
        TopBarIconButton(
            icon = Icons.Outlined.FilterList,
            contentDescription = stringResource(R.string.analysis_filters_open),
            onClick = onClick,
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun PeriodNavigator(
    state: AnalysisUiState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onYearSelected: (Int) -> Unit,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
) {
    when (state.scope) {
        AnalysisScope.CUSTOM -> CustomDateRow(
            state = state,
            onCustomFromChange = onCustomFromChange,
            onCustomToChange = onCustomToChange,
        )
        AnalysisScope.ALL_TIME -> Text(
            text = stringResource(R.string.analysis_scope_all_time),
            style = MaterialTheme.typography.titleMedium,
        )
        AnalysisScope.MONTH, AnalysisScope.YEAR -> SteppedPeriod(
            state = state,
            onPreviousPeriod = onPreviousPeriod,
            onNextPeriod = onNextPeriod,
            onMonthSelected = onMonthSelected,
            onYearSelected = onYearSelected,
        )
    }
}

/**
 * The comparison-period selector. Mirrors the active scope's granularity: a month picker, a year
 * picker, or a custom date pair. Defaults to the immediately preceding period and tracks it until
 * the user manually picks, after which a "Restableix" action re-seeds the default.
 */
@Composable
internal fun ComparisonNavigator(
    state: AnalysisUiState,
    onComparisonMonthSelected: (YearMonth) -> Unit,
    onComparisonYearSelected: (Int) -> Unit,
    onComparisonCustomFromChange: (String) -> Unit,
    onComparisonCustomToChange: (String) -> Unit,
    onResetComparison: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.analysis_compare_with_label),
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        when (state.scope) {
            AnalysisScope.MONTH -> ComparisonMonthRow(
                state = state,
                onComparisonMonthSelected = onComparisonMonthSelected,
                onResetComparison = onResetComparison,
            )
            AnalysisScope.YEAR -> ComparisonYearRow(
                state = state,
                onComparisonYearSelected = onComparisonYearSelected,
                onResetComparison = onResetComparison,
            )
            AnalysisScope.CUSTOM -> ComparisonCustomRow(
                state = state,
                onComparisonCustomFromChange = onComparisonCustomFromChange,
                onComparisonCustomToChange = onComparisonCustomToChange,
                onResetComparison = onResetComparison,
            )
            AnalysisScope.ALL_TIME -> {}
        }
    }
}

@Composable
private fun ComparisonMonthRow(
    state: AnalysisUiState,
    onComparisonMonthSelected: (YearMonth) -> Unit,
    onResetComparison: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FilterSelectorField(
                label = stringResource(R.string.analysis_period_previous),
                value = formatMonthYear(state.comparisonMonth),
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
            AppDropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                MonthPickerContent(
                    initial = state.comparisonMonth,
                    availableMonths = state.activityMonths,
                    onSelect = { onComparisonMonthSelected(it); showPicker = false },
                )
            }
        }
        if (state.comparisonTouched) {
            TextButton(onClick = onResetComparison) {
                Text(stringResource(R.string.analysis_compare_reset))
            }
        }
    }
}

@Composable
private fun ComparisonYearRow(
    state: AnalysisUiState,
    onComparisonYearSelected: (Int) -> Unit,
    onResetComparison: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            FilterSelectorField(
                label = stringResource(R.string.analysis_period_previous),
                value = state.comparisonYear.toString(),
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
            AppDropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                YearPickerContent(
                    initial = state.comparisonYear,
                    years = state.activityMonths.map { it.year }.distinct(),
                    onSelect = { onComparisonYearSelected(it); showPicker = false },
                )
            }
        }
        if (state.comparisonTouched) {
            TextButton(onClick = onResetComparison) {
                Text(stringResource(R.string.analysis_compare_reset))
            }
        }
    }
}

@Composable
private fun ComparisonCustomRow(
    state: AnalysisUiState,
    onComparisonCustomFromChange: (String) -> Unit,
    onComparisonCustomToChange: (String) -> Unit,
    onResetComparison: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateTrigger(
            label = stringResource(R.string.movement_filter_date_from),
            iso = state.comparisonCustomFrom,
            onChange = onComparisonCustomFromChange,
            modifier = Modifier.weight(1f),
        )
        DateTrigger(
            label = stringResource(R.string.movement_filter_date_to),
            iso = state.comparisonCustomTo,
            onChange = onComparisonCustomToChange,
            modifier = Modifier.weight(1f),
        )
        if (state.comparisonTouched) {
            TextButton(onClick = onResetComparison) {
                Text(stringResource(R.string.analysis_compare_reset))
            }
        }
    }
}

@Composable
private fun SteppedPeriod(
    state: AnalysisUiState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onYearSelected: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val label = state.currentRange?.formatForScope(state.scope) ?: state.fallbackPeriodLabel()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onPreviousPeriod) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(20.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable { showPicker = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(16.dp),
                )
            }
            AppDropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                if (state.scope == AnalysisScope.MONTH) {
                    MonthPickerContent(
                        initial = state.month,
                        availableMonths = state.activityMonths,
                        onSelect = { onMonthSelected(it); showPicker = false },
                    )
                } else {
                    YearPickerContent(
                        initial = state.year,
                        years = state.activityMonths.map { it.year }.distinct(),
                        onSelect = { onYearSelected(it); showPicker = false },
                    )
                }
            }
        }
        IconButton(onClick = onNextPeriod) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.common_next),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun YearPickerContent(
    initial: Int,
    years: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        years.distinct().sortedDescending().forEach { year ->
            TextButton(onClick = { onSelect(year) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = year.toString(),
                    color = if (year == initial) MaterialTheme.colorScheme.onSurface else FinanceTheme.colors.mutedText,
                    fontWeight = if (year == initial) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CustomDateRow(
    state: AnalysisUiState,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateTrigger(
            label = stringResource(R.string.movement_filter_date_from),
            iso = state.customFrom,
            onChange = onCustomFromChange,
            modifier = Modifier.weight(1f),
        )
        DateTrigger(
            label = stringResource(R.string.movement_filter_date_to),
            iso = state.customTo,
            onChange = onCustomToChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTrigger(
    label: String,
    iso: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val parsed = runCatching { LocalDate.parse(iso) }.getOrNull()
    FilterSelectorField(
        label = label,
        value = parsed?.let { formatExpandedDate(it.toString()) } ?: "—",
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = parsed
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(date.toString())
                    }
                    showDialog = false
                }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
