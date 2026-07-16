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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A tiny axis-less trend line for inline use inside list rows (design §6 sparkline).
 * Values are plotted left→right and normalised to the component height.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    strokeWidth: Dp = 2.dp,
    accessibilitySummary: String? = null,
) {
    val semanticsModifier = if (accessibilitySummary == null) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = accessibilitySummary }
    }
    Canvas(
        modifier = modifier
            .then(semanticsModifier)
            .fillMaxWidth()
            .height(height),
    ) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it != 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            // Invert Y so larger values sit higher; pad 10% top/bottom.
            val normalized = (value - min) / span
            val y = size.height * (1f - normalized) * 0.8f + size.height * 0.1f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx()),
        )
        val lastX = stepX * (values.size - 1)
        val lastNorm = (values.last() - min) / span
        val lastY = size.height * (1f - lastNorm) * 0.8f + size.height * 0.1f
        drawCircle(color = color, radius = strokeWidth.toPx() * 1.6f, center = Offset(lastX, lastY))
    }
}
