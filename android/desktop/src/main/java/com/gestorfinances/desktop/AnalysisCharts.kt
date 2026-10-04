package com.gestorfinances.desktop

import androidx.compose.foundation.layout.Spacer
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.HeroCaption
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.AnalysisSpendingByKind
import com.gestorfinances.app.ui.analysis.MonthAmount
import com.gestorfinances.app.ui.analysis.Pace
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatShortMonth
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.analysis_day
import com.gestorfinances.desktop.resources.analysis_typical_amount
import com.gestorfinances.desktop.resources.analysis_vs_previous_month
import com.gestorfinances.ui.resources.analysis_kind_one_off
import com.gestorfinances.ui.resources.analysis_kind_recurring
import com.gestorfinances.ui.resources.analysis_kind_variable
import com.gestorfinances.ui.resources.analysis_pace_above
import com.gestorfinances.ui.resources.analysis_pace_below
import com.gestorfinances.ui.resources.analysis_pace_this_month
import com.gestorfinances.ui.resources.analysis_pace_typical
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/** Marks [index] as the one under the pointer while the pointer is over this. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.pointing(index: Int, pointed: MutableState<Int?>): Modifier =
    onPointerEvent(PointerEventType.Enter) { pointed.value = index }
        .onPointerEvent(PointerEventType.Exit) { if (pointed.value == index) pointed.value = null }

/** Marks which of the points 0..[last], spread evenly across this, the pointer is nearest to. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.pointingAlong(last: Int, pointed: MutableState<Int?>): Modifier {
    val nearest: AwaitPointerEventScope.(PointerEvent) -> Unit = {
        pointed.value = (it.changes.first().position.x / size.width * last).roundToInt().coerceIn(0, last)
    }
    return onPointerEvent(PointerEventType.Enter, onEvent = nearest)
        .onPointerEvent(PointerEventType.Move, onEvent = nearest)
        .onPointerEvent(PointerEventType.Exit) { pointed.value = null }
}

/**
 * One bar per label against a dashed [line], to be explored with the pointer: the bar under it
 * stands out and [readout] says what it has to say about it (or about them all, given null), and a
 * click picks it. With [current], that bar stands out while none is pointed at.
 */
@Composable
internal fun HoverBars(
    labels: List<String>,
    values: List<Long>,
    line: Long?,
    onPick: ((Int) -> Unit)?,
    overLineInDebt: Boolean = false,
    current: Int? = null,
    /** A bar's own colour, where it has one (null for the usual). */
    tones: List<Color?>? = null,
    sparseLabels: Boolean = labels.size > 8,
    readout: @Composable RowScope.(Int?) -> Unit,
) {
    if (labels.isEmpty()) return
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val accent = MaterialTheme.colorScheme.primary
    val pointed = remember(labels) { mutableStateOf<Int?>(null) }
    val top = maxOf(values.max(), line ?: 0L).coerceAtLeast(1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // One line that never changes height: the pointed month, or the months together.
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            readout(pointed.value)
        }
        Row(
            Modifier.fillMaxWidth().height(110.dp).drawWithContent {
                drawContent()
                line?.let {
                    val y = size.height * (1f - BAR_ROOM * it / top)
                    drawLine(
                        color = muted,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                }
            },
        ) {
            values.forEachIndexed { index, cents ->
                val hovered = pointed.value == index
                val full = if (pointed.value != null) hovered else current == null || index == current
                val tone = if (overLineInDebt && line != null && cents > line) colors.debt else tones?.get(index) ?: accent
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointing(index, pointed)
                        .then(
                            if (onPick == null) {
                                Modifier
                            } else {
                                Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(index) }
                            },
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (hovered) 0.62f else 0.5f)
                            .widthIn(max = if (hovered) 56.dp else 48.dp)
                            .fillMaxHeight((BAR_ROOM * cents / top).coerceIn(0.012f, 1f))
                            .background(tone.copy(alpha = tone.alpha * if (full) 1f else 0.4f), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                // With many bars every other name, counting back from the last, so they never crowd.
                val named = !sparseLabels || (labels.lastIndex - index) % 2 == 0 || pointed.value == index
                Text(
                    if (named) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pointed.value == index) MaterialTheme.colorScheme.onSurface else muted,
                    fontWeight = if (pointed.value == index) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** [HoverBars] over months, under their short names: a click opens the month. */
@Composable
internal fun HoverBars(
    months: List<YearMonth>,
    values: List<Long>,
    line: Long?,
    onMonth: ((YearMonth) -> Unit)?,
    overLineInDebt: Boolean = false,
    current: YearMonth? = null,
    readout: @Composable RowScope.(Int?) -> Unit,
) = HoverBars(
    labels = months.map { formatShortMonth(it) },
    values = values,
    line = line,
    onPick = onMonth?.let { open -> { index: Int -> open(months[index]) } },
    overLineInDebt = overLineInDebt,
    current = current?.let(months::indexOf)?.takeIf { it >= 0 },
    readout = readout,
)

/** How much of the chart's height the tallest bar takes: the rest is headroom for the line. */
private const val BAR_ROOM = 0.94f

/**
 * A line through month-end amounts. The month under the pointer (the latest when none) is marked
 * and read out above: its amount and how it moved from the month before.
 */
@Composable
internal fun HoverLine(points: List<MonthAmount>) {
    val colors = FinanceTheme.colors
    HoverLine(
        titles = points.map { formatMonthYear(it.month).replaceFirstChar { first -> first.uppercase() } },
        values = points.map { it.cents },
        startLabel = points.firstOrNull()?.let { formatShortMonth(it.month) },
        endLabel = points.lastOrNull()?.let { formatShortMonth(it.month) },
        change = { index ->
            if (index == 0) {
                null
            } else {
                val change = points[index].cents - points[index - 1].cents
                stringResource(Res.string.analysis_vs_previous_month, (if (change > 0L) "+" else "") + formatEuroCents(change)) to
                    if (change < 0L) colors.debt else colors.income
            }
        },
    )
}

/**
 * A line through [values], evenly spaced. The point under the pointer (the last when none) is
 * marked and read out above: its title, its amount and, while pointed at, what [change] says of it.
 */
@Composable
internal fun HoverLine(
    titles: List<String>,
    values: List<Long>,
    startLabel: String? = titles.firstOrNull(),
    endLabel: String? = titles.lastOrNull(),
    change: @Composable (Int) -> Pair<String, Color>?,
) {
    if (values.size < 2) return
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val line = MaterialTheme.colorScheme.primary
    val guide = colors.cardBorder
    val pointed = remember(values) { mutableStateOf<Int?>(null) }
    val shown = pointed.value ?: values.lastIndex
    val low = values.min()
    val span = (values.max() - low).coerceAtLeast(1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titles[shown], style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1)
            Text(formatEuroCents(values[shown]), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
            if (pointed.value != null) {
                change(shown)?.let { (text, color) ->
                    Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .pointingAlong(values.lastIndex, pointed),
        ) {
            val pad = 6.dp.toPx()
            fun x(index: Int) = size.width * index / values.lastIndex
            fun y(cents: Long) = pad + (size.height - 2 * pad) * (1f - (cents - low) / span)
            if (pointed.value != null) drawLine(guide, Offset(x(shown), 0f), Offset(x(shown), size.height), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                values.forEachIndexed { index, cents -> if (index == 0) moveTo(x(index), y(cents)) else lineTo(x(index), y(cents)) }
            }
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
            drawCircle(line, radius = 4.5.dp.toPx(), center = Offset(x(shown), y(values[shown])))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(startLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            Text(endLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

/** The day of the month from which it is compared with a typical one. */
private const val COMPARISON_FROM_DAY = 5

/**
 * This month's running total (solid, up to today) against the typical month's (dashed). The day
 * under the pointer reads out both; with none, how far apart they are today.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HoverPace(pace: Pace) {
    val typical = pace.typical ?: return
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val current = MaterialTheme.colorScheme.primary
    val grid = colors.cardBorder
    val today = pace.current.lastIndex
    val last = (pace.daysInMonth - 1).coerceAtLeast(1)
    val top = maxOf(pace.current.maxOrNull() ?: 0L, typical.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val pointed = remember(pace) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val day = pointed.value
            if (day == null) {
                val gap = pace.current.last() - (typical.getOrNull(today) ?: 0L)
                if (today + 1 >= COMPARISON_FROM_DAY) {
                    Text(
                        stringResource(if (gap > 0L) SharedRes.string.analysis_pace_above else SharedRes.string.analysis_pace_below, formatEuroCents(abs(gap))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (gap > 0L) colors.debt else colors.income,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(stringResource(Res.string.analysis_day, day + 1), style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1)
                pace.current.getOrNull(day)?.let {
                    Text(formatEuroCents(it), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                typical.getOrNull(day)?.let {
                    Text(
                        stringResource(Res.string.analysis_typical_amount, formatEuroCents(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .pointingAlong(last, pointed),
        ) {
            fun x(day: Int) = size.width * day / last
            fun y(cents: Long) = size.height - size.height * cents / top
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val marked = pointed.value ?: today
            drawLine(grid, Offset(x(marked), 0f), Offset(x(marked), size.height), strokeWidth = 1.dp.toPx())
            val usualPath = Path().apply {
                typical.forEachIndexed { day, cents -> if (day == 0) moveTo(x(day), y(cents)) else lineTo(x(day), y(cents)) }
            }
            drawPath(usualPath, muted, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            val currentPath = Path().apply {
                pace.current.forEachIndexed { day, cents -> if (day == 0) moveTo(x(day), y(cents)) else lineTo(x(day), y(cents)) }
            }
            drawPath(currentPath, current, style = Stroke(width = 2.5.dp.toPx()))
            pointed.value?.let { day -> typical.getOrNull(day)?.let { drawCircle(muted, radius = 3.5.dp.toPx(), center = Offset(x(day), y(it))) } }
            val dot = marked.coerceAtMost(today)
            drawCircle(current, radius = 4.dp.toPx(), center = Offset(x(dot), y(pace.current[dot])))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(1, (pace.daysInMonth + 1) / 2, pace.daysInMonth).forEachIndexed { index, day ->
                Text(
                    day.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
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
            LegendMark(stringResource(SharedRes.string.analysis_pace_this_month), current)
            LegendMark(stringResource(SharedRes.string.analysis_pace_typical), muted)
        }
    }
}

@Composable
private fun LegendMark(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).background(color, RoundedCornerShape(50)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = FinanceTheme.colors.mutedText)
    }
}

/**
 * How the spending came about (recurring payments, ordinary spending, one-offs) as one split bar
 * over its lines: the part under the pointer, on the bar or on its line, stands out in both.
 */
@Composable
internal fun HowBars(byKind: AnalysisSpendingByKind) {
    val total = byKind.totalCents
    if (total <= 0L) return
    val colors = FinanceTheme.colors
    val parts = listOf(
        Triple(SharedRes.string.analysis_kind_recurring, byKind.recurringCents, MaterialTheme.colorScheme.primary),
        Triple(SharedRes.string.analysis_kind_variable, byKind.variableCents, colors.mutedText),
        Triple(SharedRes.string.analysis_kind_one_off, byKind.oneOffCents, colors.alert),
    ).filter { it.second > 0L }
    val pointed = remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            parts.forEachIndexed { index, (_, cents, color) ->
                Box(
                    Modifier
                        .weight(cents.toFloat())
                        .fillMaxHeight()
                        .pointing(index, pointed)
                        .background(color.copy(alpha = if (pointed.value == null || pointed.value == index) 1f else 0.3f), RoundedCornerShape(50)),
                )
            }
        }
        parts.forEachIndexed { index, (label, cents, color) ->
            val dim = pointed.value != null && pointed.value != index
            val weight = if (pointed.value == index) FontWeight.Medium else FontWeight.Normal
            Row(
                Modifier.pointing(index, pointed),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).background(color.copy(alpha = if (dim) 0.3f else 1f), RoundedCornerShape(3.dp)))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = weight,
                    color = if (dim) colors.mutedText else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    shareLabel(cents, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
                MoneyText(cents = cents, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = weight))
            }
        }
    }
}

/** A part of a hero's bar: what it is, what it comes to, its colour, and what a click on it opens. */
internal class HeroSegment(val label: String, val cents: Long, val color: Color, val onClick: (() -> Unit)? = null)

/**
 * A hero's bar cut into what it is made of, out of [totalCents]: the part under the pointer stands
 * out and names itself with its amount in the line below, which otherwise says [rest].
 */
@Composable
internal fun HeroSegments(segments: List<HeroSegment>, totalCents: Long, rest: String) {
    val colors = FinanceTheme.colors
    val pointed = remember { mutableStateOf<Int?>(null) }
    val shown = segments.filter { it.cents > 0L }
    Row(
        Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(heroTint(colors.heroOnSurface, 0.16f)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        shown.forEachIndexed { index, segment ->
            val dimmed = pointed.value != null && pointed.value != index
            Box(
                Modifier
                    .weight(segment.cents.toFloat())
                    .fillMaxHeight()
                    .pointing(index, pointed)
                    .then(if (segment.onClick != null) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = segment.onClick) else Modifier)
                    .background(segment.color.copy(alpha = segment.color.alpha * if (dimmed) 0.45f else 1f)),
            )
        }
        val left = totalCents - shown.sumOf { it.cents }
        if (left > 0L) Spacer(Modifier.weight(left.toFloat()))
    }
    Spacer(Modifier.height(8.dp))
    HeroCaption(pointed.value?.let(shown::getOrNull)?.let { "${it.label} · ${formatEuroCents(it.cents)}" } ?: rest)
}
