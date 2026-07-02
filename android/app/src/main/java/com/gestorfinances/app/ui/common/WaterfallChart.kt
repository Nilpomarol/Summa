package com.gestorfinances.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asFigures

/** A single floating step of a [WaterfallChart] — a labelled change between two running totals. */
data class WaterfallStep(
    val label: String,
    val deltaCents: Long,
    val onClick: (() -> Unit)? = null,
)

/**
 * A waterfall that bridges a [startCents] total to an [endCents] total through signed [steps].
 * The two endpoint bars are full-height totals (neutral ink); each step floats between the running
 * totals, colored by [increaseColor]/[decreaseColor] and connected by dashed bridge guides so the
 * "flow" from one bar to the next reads clearly. Steps may opt into tap handling via
 * [WaterfallStep.onClick].
 */
@Composable
fun WaterfallChart(
    startLabel: String,
    startCents: Long,
    steps: List<WaterfallStep>,
    endLabel: String,
    endCents: Long,
    increaseColor: Color,
    decreaseColor: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val totalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mutedColor = FinanceTheme.colors.mutedText
    val guideColor = FinanceTheme.colors.cardBorder
    val nameStyle = MaterialTheme.typography.labelSmall.copy(color = mutedColor, fontSize = 9.sp)
    val valueStyle = MaterialTheme.typography.labelSmall.asFigures().copy(fontSize = 9.sp)

    // Cumulative levels at each bar edge, used to scale the vertical axis. running[i] is also the
    // bridging level between bar i and bar i+1 (0=start-total, 1..steps.size=floating steps,
    // steps.size+1=end-total) — reused directly to draw the dashed connector guides. The last
    // entry is pinned to endCents (rather than trusting startCents + sum(steps) to reconcile
    // exactly) so the final connector always meets the end-total bar precisely, even if the
    // steps don't sum perfectly to the caller-supplied total.
    val running = ArrayList<Long>(steps.size + 1)
    var acc = startCents
    running.add(startCents)
    steps.forEach { acc += it.deltaCents; running.add(acc) }
    running[running.lastIndex] = endCents
    val levels = buildList {
        add(0L); add(startCents); add(endCents); addAll(running)
    }
    val maxLevel = levels.max().coerceAtLeast(1L)
    val minLevel = levels.min().coerceAtMost(0L)
    val span = (maxLevel - minLevel).toFloat().coerceAtLeast(1f)
    val barCount = steps.size + 2
    val tapModifier = if (steps.any { it.onClick != null }) {
        Modifier.pointerInput(steps) {
            detectTapGestures { offset ->
                if (barCount == 0 || size.width <= 0) return@detectTapGestures
                val index = ((offset.x / size.width) * barCount).toInt().coerceIn(0, barCount - 1)
                if (index in 1..steps.size) steps[index - 1].onClick?.invoke()
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(bottom = 18.dp)
            .then(tapModifier),
    ) {
        val valueLabelSpace = 18.dp.toPx()
        val drawableHeight = size.height - valueLabelSpace
        val slot = size.width / barCount
        val barWidth = slot * 0.6f
        val cornerRadius = CornerRadius(3.dp.toPx())
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

        fun yOf(value: Long): Float = valueLabelSpace + drawableHeight * (1f - (value - minLevel) / span)
        fun leftOf(index: Int): Float = slot * index + (slot - barWidth) / 2f

        // Zero baseline — a fixed reference so bar heights read as absolute amounts, not just deltas.
        val zeroY = yOf(0L)
        drawLine(
            color = guideColor,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect,
        )

        // Dashed bridges between consecutive bars, at the running total they share — the visual
        // cue that makes a waterfall read as "flowing" rather than a row of disconnected columns.
        for (i in 0 until barCount - 1) {
            val y = yOf(running[i])
            drawLine(
                color = guideColor,
                start = Offset(leftOf(i) + barWidth, y),
                end = Offset(leftOf(i + 1), y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
            )
        }

        fun drawBar(index: Int, top: Long, bottom: Long, color: Color, label: String, valueText: String) {
            val left = leftOf(index)
            val yTop = yOf(maxOf(top, bottom))
            val yBottom = yOf(minOf(top, bottom))
            drawRoundRect(
                color = color,
                topLeft = Offset(left, yTop),
                size = Size(barWidth, (yBottom - yTop).coerceAtLeast(2f)),
                cornerRadius = cornerRadius,
            )

            val valueLayout = measurer.measure(
                AnnotatedString(valueText),
                style = valueStyle,
                maxLines = 1,
                constraints = Constraints(maxWidth = slot.toInt().coerceAtLeast(1)),
            )
            drawText(
                textLayoutResult = valueLayout,
                topLeft = Offset(
                    left + barWidth / 2f - valueLayout.size.width / 2f,
                    (yTop - valueLayout.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )

            val nameLayout = measurer.measure(
                AnnotatedString(label),
                style = nameStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = slot.toInt().coerceAtLeast(1)),
            )
            drawText(
                textLayoutResult = nameLayout,
                topLeft = Offset(left + barWidth / 2f - nameLayout.size.width / 2f, size.height + 3f),
            )
        }

        // Start total.
        drawBar(0, 0L, startCents, totalColor, startLabel, formatEuroCompact(startCents))
        // Floating steps — read both edges from `running` (not a separately-tracked cumulative
        // sum) so a step bar's edge always matches the connector drawn at that same junction,
        // even for the last step where `running`'s last entry is pinned to endCents above.
        steps.forEachIndexed { i, step ->
            val color = if (step.deltaCents >= 0) increaseColor else decreaseColor
            val sign = if (step.deltaCents > 0) "+" else ""
            drawBar(i + 1, running[i], running[i + 1], color, step.label, "$sign${formatEuroCompact(step.deltaCents)}")
        }
        // End total.
        drawBar(barCount - 1, 0L, endCents, totalColor, endLabel, formatEuroCompact(endCents))
    }
}
