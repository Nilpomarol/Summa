package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.analysis.MonthAmount
import com.gestorfinances.app.ui.analysis.Pace
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatShortMonth
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.YearMonth

/**
 * This month's running total (solid, up to today) against the typical month's (dashed, the whole
 * month), with today marked; day numbers at the start, middle and end.
 */
@Composable
internal fun PaceChart(pace: Pace) {
    val typical = pace.typical ?: return
    val colors = FinanceTheme.colors
    val current = MaterialTheme.colorScheme.primary
    val usual = colors.mutedText
    val grid = colors.cardBorder
    val top = maxOf(pace.current.maxOrNull() ?: 0L, typical.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val last = (pace.daysInMonth - 1).coerceAtLeast(1)
            fun x(day: Int) = size.width * day / last
            fun y(cents: Long) = size.height - size.height * cents / top
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val usualPath = Path().apply {
                typical.forEachIndexed { day, cents -> if (day == 0) moveTo(x(day), y(cents)) else lineTo(x(day), y(cents)) }
            }
            drawPath(
                usualPath,
                usual,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
            val currentPath = Path().apply {
                pace.current.forEachIndexed { day, cents -> if (day == 0) moveTo(x(day), y(cents)) else lineTo(x(day), y(cents)) }
            }
            drawPath(currentPath, current, style = Stroke(width = 2.5.dp.toPx()))
            val today = pace.current.lastIndex
            drawLine(grid, Offset(x(today), 0f), Offset(x(today), size.height), strokeWidth = 1.dp.toPx())
            drawCircle(current, radius = 4.dp.toPx(), center = Offset(x(today), y(pace.current.last())))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(1, (pace.daysInMonth + 1) / 2, pace.daysInMonth).forEachIndexed { index, day ->
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        1 -> TextAlign.Center
                        else -> TextAlign.End
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(stringResource(R.string.analysis_pace_this_month), current, dashed = false)
            Legend(stringResource(R.string.analysis_pace_typical), usual, dashed = true)
        }
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.width(18.dp).height(4.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = FinanceTheme.colors.mutedText)
    }
}

/**
 * One bar per month under every other month's short name, a dashed line at their average, and a
 * readout of the selected bar (tap one to select it). [onOpen], when set, offers to open the
 * selected month.
 */
@Composable
internal fun MonthBars(
    months: List<YearMonth>,
    values: List<Long>,
    initialSelection: Int?,
    readout: @Composable (Int) -> String,
    onOpen: ((YearMonth) -> Unit)?,
) {
    if (months.isEmpty()) return
    var selected by remember(months, values) { mutableIntStateOf(initialSelection ?: values.indexOfLast { it != 0L }.coerceAtLeast(0)) }
    val colors = FinanceTheme.colors
    val strong = MaterialTheme.colorScheme.primary
    val quiet = strong.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.background)
    val top = values.max().coerceAtLeast(1L).toFloat()
    val nonZero = values.filter { it != 0L }
    val average = if (nonZero.isEmpty()) 0L else nonZero.sum() / nonZero.size
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = readout(selected), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (onOpen != null) {
                TextButton(onClick = { onOpen(months[selected]) }) { Text(stringResource(R.string.analysis_open_month)) }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                values.forEachIndexed { index, cents ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { selected = index },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight((cents / top).coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (index == selected) strong else quiet),
                        )
                    }
                }
            }
            if (average > 0L) {
                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    val y = size.height - size.height * average / top
                    drawLine(
                        colors.mutedText,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            months.forEachIndexed { index, month ->
                // Every other name, counting back from the last, so they never crowd.
                Text(
                    text = if ((months.lastIndex - index) % 2 == 0) formatShortMonth(month) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (average > 0L) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(modifier = Modifier.size(width = 14.dp, height = 4.dp)) {
                    drawLine(
                        colors.mutedText,
                        Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                }
                Text(
                    text = stringResource(R.string.analysis_average, formatEuroCents(average)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                )
            }
        }
    }
}

/** A line through month-end amounts, with a readout of the tapped point (the latest by default). */
@Composable
internal fun AmountLine(points: List<MonthAmount>) {
    if (points.size < 2) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val colors = FinanceTheme.colors
    val line = MaterialTheme.colorScheme.primary
    val low = points.minOf { it.cents }
    val high = points.maxOf { it.cents }
    val span = (high - low).coerceAtLeast(1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.analysis_net_worth_readout, formatMonthYear(points[selected].month), formatEuroCents(points[selected].cents)),
            style = MaterialTheme.typography.labelLarge,
        )
        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val pad = 6.dp.toPx()
                fun x(index: Int) = size.width * index / points.lastIndex
                fun y(cents: Long) = pad + (size.height - 2 * pad) * (1f - (cents - low) / span)
                val path = Path().apply {
                    points.forEachIndexed { index, point -> if (index == 0) moveTo(x(index), y(point.cents)) else lineTo(x(index), y(point.cents)) }
                }
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
                drawCircle(line, radius = 4.5.dp.toPx(), center = Offset(x(selected), y(points[selected].cents)))
            }
            // Tapping near a point selects it.
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                points.indices.forEach { index ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { selected = index })
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = formatShortMonth(points.first().month), style = MaterialTheme.typography.labelSmall, color = colors.mutedText, modifier = Modifier.weight(1f))
            Text(text = formatShortMonth(points.last().month), style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
        }
    }
}
