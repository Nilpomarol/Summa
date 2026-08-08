package com.gestorfinances.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

data class DistributionSegment(
    val color: Color,
    val fraction: Float,
)

/** Shared rounded segmented bar for compact category and account distributions. */
@Composable
fun SegmentedDistributionBar(
    segments: List<DistributionSegment>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val gap = 3.dp.toPx()
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        var startX = 0f
        segments.forEachIndexed { index, segment ->
            val rawWidth = size.width * segment.fraction.coerceIn(0f, 1f)
            val segmentWidth = if (index == segments.lastIndex) size.width - startX else rawWidth - gap
            if (segmentWidth > 0f) {
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(startX, 0f),
                    size = Size(segmentWidth.coerceAtLeast(size.height), size.height),
                    cornerRadius = radius,
                )
            }
            startX += rawWidth
        }
    }
}
