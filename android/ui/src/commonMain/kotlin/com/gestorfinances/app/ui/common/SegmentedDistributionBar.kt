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
        val visible = segments.filter { it.fraction > 0f }
        val widths = segmentWidths(
            fractions = visible.map { it.fraction },
            available = size.width - gap * (visible.size - 1).coerceAtLeast(0),
            minWidth = size.height,
        )
        var startX = 0f
        visible.forEachIndexed { index, segment ->
            drawRoundRect(
                color = segment.color,
                topLeft = Offset(startX, 0f),
                size = Size(widths[index], size.height),
                cornerRadius = radius,
            )
            startX += widths[index] + gap
        }
    }
}

/**
 * Splits [available] between [fractions] (normalised to their sum), keeping every segment at
 * least [minWidth] wide so a small share still reads as a dot, and taking that room from the
 * larger segments so the bar never runs past its end. When there are too many segments for the
 * minimum, they share the width equally.
 */
fun segmentWidths(fractions: List<Float>, available: Float, minWidth: Float): List<Float> {
    if (fractions.isEmpty() || available <= 0f) return fractions.map { 0f }
    if (minWidth * fractions.size >= available) return fractions.map { available / fractions.size }
    val pinned = BooleanArray(fractions.size)
    // Pinning one segment to the minimum shrinks the others, which can push another under it.
    while (true) {
        val freeFraction = fractions.filterIndexed { i, _ -> !pinned[i] }.sum()
        val freeWidth = available - pinned.count { it } * minWidth
        val tooSmall = fractions.indices.filter { i -> !pinned[i] && fractions[i] / freeFraction * freeWidth < minWidth }
        if (tooSmall.isEmpty()) {
            return fractions.mapIndexed { i, f -> if (pinned[i]) minWidth else f / freeFraction * freeWidth }
        }
        tooSmall.forEach { pinned[it] = true }
    }
}
