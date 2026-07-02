package com.gestorfinances.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asFigures

/** A node (vertical bar) in one column of a [SankeyDiagram]. */
data class SankeyNode(
    val id: String,
    val label: String,
    val valueCents: Long,
    val color: Color,
)

/** A flow band from one node to another (must reference node ids in adjacent columns). */
data class SankeyLink(
    val fromId: String,
    val toId: String,
    val valueCents: Long,
)

/**
 * A compact Sankey: columns of proportional, rounded nodes joined by translucent flow bands whose
 * width encodes value. Each node shows its name and its amount ([formatEuroCompact], Geist Mono
 * tabular figures) so the flow is self-explanatory without tapping — a closest-safe rendering (no
 * native Sankey in the chart lib), not a publication-grade diagram.
 */
@Composable
fun SankeyDiagram(
    columns: List<List<SankeyNode>>,
    links: List<SankeyLink>,
    modifier: Modifier = Modifier,
    height: Dp = 280.dp,
) {
    if (columns.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 11.sp,
    )
    val valueStyle = MaterialTheme.typography.labelSmall.asFigures().copy(
        color = FinanceTheme.colors.mutedText,
        fontSize = 10.sp,
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val nodeWidth = 10.dp.toPx()
        val nodeCornerRadius = CornerRadius(2.dp.toPx())
        val vGap = 8.dp.toPx()
        val labelReserve = 92.dp.toPx()
        val usableWidth = size.width - labelReserve
        val maxColumnTotal = columns.maxOf { col -> col.sumOf { it.valueCents } }.coerceAtLeast(1L)

        // Lay each node out: x by column, y stacked, height ∝ value.
        data class Placed(val node: SankeyNode, val x: Float, val top: Float, val height: Float)
        val placed = HashMap<String, Placed>()
        // Same nodes, grouped by column and kept in top-to-bottom order — used below to avoid
        // stacking overlapping labels when several nodes in a column are small/close together.
        val placedColumns = ArrayList<List<Placed>>(columns.size)
        val columnX = FloatArray(columns.size) { i ->
            if (columns.size == 1) 0f else (usableWidth - nodeWidth) * i / (columns.size - 1)
        }
        columns.forEachIndexed { ci, col ->
            val colTotal = col.sumOf { it.valueCents }.coerceAtLeast(1L)
            val gaps = vGap * (col.size - 1).coerceAtLeast(0)
            val available = size.height - gaps
            val scale = available * (colTotal.toFloat() / maxColumnTotal) / colTotal
            var y = (size.height - (col.sumOf { it.valueCents } * scale + gaps)) / 2f
            val colPlaced = ArrayList<Placed>(col.size)
            col.forEach { node ->
                val h = (node.valueCents * scale).coerceAtLeast(2f)
                val entry = Placed(node, columnX[ci], y, h)
                placed[node.id] = entry
                colPlaced.add(entry)
                y += h + vGap
            }
            placedColumns.add(colPlaced)
        }

        // Flow bands first, so node bars sit on top.
        val outOffset = HashMap<String, Float>()
        val inOffset = HashMap<String, Float>()
        links.sortedByDescending { it.valueCents }.forEach { link ->
            val from = placed[link.fromId] ?: return@forEach
            val to = placed[link.toId] ?: return@forEach
            val fromTotal = from.node.valueCents.coerceAtLeast(1L)
            val toTotal = to.node.valueCents.coerceAtLeast(1L)
            val fromH = from.height * (link.valueCents.toFloat() / fromTotal)
            val toH = to.height * (link.valueCents.toFloat() / toTotal)
            val fo = outOffset.getOrDefault(link.fromId, 0f)
            val io = inOffset.getOrDefault(link.toId, 0f)
            val x0 = from.x + nodeWidth
            val x1 = to.x
            val y0t = from.top + fo
            val y1t = to.top + io
            val midX = (x0 + x1) / 2f
            val band = Path().apply {
                moveTo(x0, y0t)
                cubicTo(midX, y0t, midX, y1t, x1, y1t)
                lineTo(x1, y1t + toH)
                cubicTo(midX, y1t + toH, midX, y0t + fromH, x0, y0t + fromH)
                close()
            }
            drawPath(band, color = from.node.color.copy(alpha = 0.22f))
            outOffset[link.fromId] = fo + fromH
            inOffset[link.toId] = io + toH
        }

        // Node bars (rounded, matching the app's card/pill radius language).
        placed.values.forEach { p ->
            drawRoundRect(
                color = p.node.color,
                topLeft = Offset(p.x, p.top),
                size = Size(nodeWidth, p.height),
                cornerRadius = nodeCornerRadius,
            )
        }

        // Labels: name + amount, one column at a time, top to bottom, so tightly-packed small
        // nodes never draw overlapping text. The name always draws — every node must stay
        // identifiable — but the amount line is dropped when a node is too small/close to its
        // neighbors to fit both without colliding (the category breakdown cards elsewhere on the
        // tab still carry the exact value).
        val minLabelGap = 2.dp.toPx()
        placedColumns.forEach { colPlaced ->
            var nextAvailableTop = 0f
            colPlaced.forEach { p ->
                val textX = p.x + nodeWidth + 6f
                val ownBottom = p.top + p.height + vGap / 2f
                val nameLayout = measurer.measure(
                    AnnotatedString(p.node.label),
                    style = nameStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = labelReserve.toInt()),
                )
                val valueLayout = measurer.measure(
                    AnnotatedString(formatEuroCompact(p.node.valueCents)),
                    style = valueStyle,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = labelReserve.toInt()),
                )
                val twoLineHeight = nameLayout.size.height + valueLayout.size.height
                val twoLineTop = (p.top + p.height / 2f - twoLineHeight / 2f)
                    .coerceAtLeast(nextAvailableTop)
                if (twoLineTop + twoLineHeight <= ownBottom && twoLineTop + twoLineHeight <= size.height) {
                    drawText(textLayoutResult = nameLayout, topLeft = Offset(textX, twoLineTop))
                    drawText(
                        textLayoutResult = valueLayout,
                        topLeft = Offset(textX, twoLineTop + nameLayout.size.height),
                    )
                    nextAvailableTop = twoLineTop + twoLineHeight + minLabelGap
                    return@forEach
                }
                // No room for both lines — always draw the name, pushed down just enough to clear
                // the previous label and clamped to the canvas bottom so it never draws off-screen.
                val nameOnlyHeight = nameLayout.size.height
                val nameOnlyTop = (p.top + p.height / 2f - nameOnlyHeight / 2f)
                    .coerceAtLeast(nextAvailableTop)
                    .coerceAtMost((size.height - nameOnlyHeight).coerceAtLeast(0f))
                drawText(textLayoutResult = nameLayout, topLeft = Offset(textX, nameOnlyTop))
                nextAvailableTop = nameOnlyTop + nameOnlyHeight + minLabelGap
            }
        }
    }
}
