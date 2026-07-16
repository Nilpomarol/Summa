package com.gestorfinances.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.ui.theme.FinanceTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One spoke of a [RadarChart]: a short label and a non-negative magnitude. */
data class RadarAxis(
    val label: String,
    val value: Float,
)

/**
 * A radar/spider chart over a fixed set of [axes] (e.g. the seven weekdays). Magnitudes are
 * normalised to the largest axis value; the filled polygon shows the relative shape.
 */
@Composable
fun RadarChart(
    axes: List<RadarAxis>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    accessibilitySummary: String,
    accessibilityRows: List<ChartDataRow> = emptyList(),
) {
    if (axes.size < 3) return
    val measurer = rememberTextMeasurer()
    val webColor = FinanceTheme.colors.cardBorder
    val labelColor = FinanceTheme.colors.mutedText
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val maxValue = axes.maxOf { it.value }.coerceAtLeast(1f)

    AccessibleChart(
        summary = accessibilitySummary,
        dataRows = accessibilityRows,
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (minOf(size.width, size.height) / 2f) - 26.dp.toPx()
        val n = axes.size

        fun pointAt(index: Int, scale: Float): Offset {
            val angle = -PI / 2 + index * 2 * PI / n
            return Offset(
                x = center.x + (radius * scale * cos(angle)).toFloat(),
                y = center.y + (radius * scale * sin(angle)).toFloat(),
            )
        }

        // Concentric web rings.
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { ring ->
            val ringPath = Path()
            for (i in 0 until n) {
                val p = pointAt(i, ring)
                if (i == 0) ringPath.moveTo(p.x, p.y) else ringPath.lineTo(p.x, p.y)
            }
            ringPath.close()
            drawPath(ringPath, color = webColor, style = Stroke(width = 1f))
        }
        // Spokes + labels.
        for (i in 0 until n) {
            val edge = pointAt(i, 1f)
            drawLine(webColor, start = center, end = edge, strokeWidth = 1f)
            val layout = measurer.measure(AnnotatedString(axes[i].label), style = labelStyle)
            val labelPoint = pointAt(i, 1.18f)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(labelPoint.x - layout.size.width / 2f, labelPoint.y - layout.size.height / 2f),
            )
        }
        // Data polygon.
        val dataPath = Path()
        for (i in 0 until n) {
            val p = pointAt(i, axes[i].value / maxValue)
            if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, color = color.copy(alpha = 0.22f))
        drawPath(dataPath, color = color, style = Stroke(width = 2.dp.toPx()))
        for (i in 0 until n) {
            val p = pointAt(i, axes[i].value / maxValue)
            drawCircle(color = color, radius = 3.dp.toPx(), center = p)
        }
        }
    }
}
