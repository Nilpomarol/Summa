package com.gestorfinances.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One labelled, weighted block of a [Treemap]. [valueCents] must be positive. */
data class TreemapItem(
    val key: String,
    val label: String,
    val valueCents: Long,
    val color: Color,
    /** Optional secondary line (e.g. "23%"); only shown when the block has room. */
    val subLabel: String? = null,
)

/**
 * A treemap drawn with recursive slice-and-dice splitting, so block areas are proportional to
 * value without a heavyweight layout pass.
 */
@Composable
fun Treemap(
    items: List<TreemapItem>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    gap: Dp = 3.dp,
    accessibilitySummary: String,
    accessibilityRows: List<ChartDataRow> = emptyList(),
) {
    val ordered = items.filter { it.valueCents > 0 }.sortedByDescending { it.valueCents }
    if (ordered.isEmpty()) return
    AccessibleChart(
        summary = accessibilitySummary,
        dataRows = accessibilityRows,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            TreemapSplit(items = ordered, horizontal = true, gap = gap)
        }
    }
}

@Composable
private fun TreemapSplit(
    items: List<TreemapItem>,
    horizontal: Boolean,
    gap: Dp,
) {
    if (items.size == 1) {
        TreemapBlock(item = items.single())
        return
    }
    // Split the sorted list into two groups of roughly equal total value.
    val total = items.sumOf { it.valueCents }
    var acc = 0L
    var splitIndex = 0
    while (splitIndex < items.size - 1 && acc + items[splitIndex].valueCents < total / 2.0) {
        acc += items[splitIndex].valueCents
        splitIndex++
    }
    val first = items.subList(0, splitIndex + 1)
    val second = items.subList(splitIndex + 1, items.size)
    val firstWeight = first.sumOf { it.valueCents }.toFloat().coerceAtLeast(1f)
    val secondWeight = second.sumOf { it.valueCents }.toFloat().coerceAtLeast(1f)

    if (horizontal) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(firstWeight).fillMaxSize().padding(end = gap / 2)) {
                TreemapSplit(items = first, horizontal = false, gap = gap)
            }
            Box(modifier = Modifier.weight(secondWeight).fillMaxSize().padding(start = gap / 2)) {
                TreemapSplit(items = second, horizontal = false, gap = gap)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(firstWeight).fillMaxSize().padding(bottom = gap / 2)) {
                TreemapSplit(items = first, horizontal = true, gap = gap)
            }
            Box(modifier = Modifier.weight(secondWeight).fillMaxSize().padding(top = gap / 2)) {
                TreemapSplit(items = second, horizontal = true, gap = gap)
            }
        }
    }
}

@Composable
private fun TreemapBlock(item: TreemapItem) {
    val onColor = if (item.color.luminance() > 0.55f) Color.Black else Color.White
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(item.color, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        // Only show the secondary line when the block is roomy enough for two lines.
        val showSubLabel = item.subLabel != null && maxHeight >= 40.dp && maxWidth >= 48.dp
        Column {
            Text(
                text = item.label,
                color = onColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = if (showSubLabel) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showSubLabel) {
                Text(
                    text = item.subLabel!!,
                    color = onColor.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
