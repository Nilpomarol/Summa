package com.gestorfinances.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.YearMonth

/**
 * Year stepper over a 3×4 grid of months, the shared body of every month selector menu
 * (analysis period navigator, dashboard header). Callers own the anchor and the menu itself.
 */
@Composable
fun MonthPickerContent(
    initial: YearMonth,
    availableMonths: List<YearMonth>,
    onSelect: (YearMonth) -> Unit,
) {
    val availableYears = availableMonths.map { it.year }.distinct().sorted()
    var displayYear by remember(initial, availableYears) {
        mutableStateOf(initial.year.takeIf { it in availableYears } ?: availableYears.lastOrNull() ?: initial.year)
    }
    val monthLabels = stringArrayResource(R.array.analysis_month_short)
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { displayYear = availableYears.previousOf(displayYear) ?: displayYear },
                enabled = availableYears.previousOf(displayYear) != null,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            Text(
                text = displayYear.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { displayYear = availableYears.nextOf(displayYear) ?: displayYear },
                enabled = availableYears.nextOf(displayYear) != null,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.common_next),
                )
            }
        }
        for (row in 0 until 4) {
            Row {
                for (col in 0 until 3) {
                    val monthIndex = row * 3 + col + 1
                    val month = YearMonth.of(displayYear, monthIndex)
                    val selected = month == initial
                    val available = month in availableMonths
                    TextButton(
                        onClick = { onSelect(month) },
                        modifier = Modifier
                            .width(60.dp)
                            .height(40.dp),
                        enabled = available,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = FinanceTheme.colors.disabledText,
                        ),
                    ) {
                        Text(
                            text = monthLabels.getOrElse(monthIndex - 1) { monthIndex.toString() },
                            color = when {
                                !available -> FinanceTheme.colors.disabledText
                                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun List<Int>.previousOf(value: Int): Int? = filter { it < value }.maxOrNull()

private fun List<Int>.nextOf(value: Int): Int? = filter { it > value }.minOrNull()
