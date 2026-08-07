package com.gestorfinances.app.ui.common

import android.util.DisplayMetrics
import android.view.View
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.gestorfinances.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-wide modal-sheet contract.
 *
 * Sheets move directly between hidden and expanded, measure to their content, and let Material own
 * safe-area, IME, nested-scroll, predictive-back, and user-driven dismissal behaviour. A caller
 * may cap its measured height, use an animated fixed height, or require dismissal to begin at
 * the Material drag handle.
 *
 * Set [dismissRequested] after a successful action that should close the sheet. The sheet finishes
 * its hide animation before invoking [onDismissRequest], allowing callers to remove navigation or
 * backing state without an abrupt visual cut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissRequested: Boolean = false,
    minHeightFraction: Float? = null,
    maxHeightFraction: Float? = null,
    fixedHeightFraction: Float? = null,
    fixedHeight: Dp? = null,
    keepDragHandleInside: Boolean = false,
    dismissFromDragHandleOnly: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(minHeightFraction == null || minHeightFraction in 0f..1f)
    require(maxHeightFraction == null || maxHeightFraction in 0f..1f)
    require(fixedHeightFraction == null || fixedHeightFraction in 0f..1f)
    require(minHeightFraction == null || maxHeightFraction == null || minHeightFraction <= maxHeightFraction)
    require(fixedHeightFraction == null || (minHeightFraction == null && maxHeightFraction == null))
    require(fixedHeight == null || (minHeightFraction == null && maxHeightFraction == null && fixedHeightFraction == null))

    var handleDismissEnabled by remember(dismissFromDragHandleOnly) {
        mutableStateOf(!dismissFromDragHandleOnly)
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            target != SheetValue.Hidden || handleDismissEnabled
        },
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = LocalView.current.realDisplayHeightPx()
    val minHeight = minHeightFraction?.let { fraction ->
        with(density) { screenHeightPx.toDp() * fraction }
    }
    val maxHeight = maxHeightFraction?.let { fraction ->
        with(density) { screenHeightPx.toDp() * fraction }
    }
    val fractionFixedHeight = fixedHeightFraction?.let { fraction ->
        with(density) { screenHeightPx.toDp() * fraction }
    }
    val targetFixedHeight = fixedHeight ?: fractionFixedHeight
    val animatedFixedHeight by animateDpAsState(
        targetValue = targetFixedHeight ?: 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "sheetHeight",
    )
    val hasConstrainedHeight = minHeight != null || maxHeight != null || targetFixedHeight != null
    val useInternalDragHandle = hasConstrainedHeight || keepDragHandleInside

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            handleDismissEnabled = true
            sheetState.hide()
            onDismissRequest()
        }
    }

    val sheetHandle: @Composable () -> Unit = {
        if (dismissFromDragHandleOnly) {
            val closeLabel = stringResource(R.string.sheet_close_from_handle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSheetHandleTouchHeight)
                    .pointerInput(sheetState) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            handleDismissEnabled = true
                            waitForUpOrCancellation()
                            scope.launch {
                                delay(100)
                                if (sheetState.targetValue != SheetValue.Hidden) {
                                    handleDismissEnabled = false
                                }
                            }
                        }
                    }
                    .clickable(onClickLabel = closeLabel) {
                        handleDismissEnabled = true
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) onDismissRequest()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
        } else {
            BottomSheetDefaults.DragHandle()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!dismissFromDragHandleOnly || handleDismissEnabled) {
                onDismissRequest()
            }
        },
        modifier = modifier,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = SheetScrimAlpha),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !dismissFromDragHandleOnly,
        ),
        contentWindowInsets = {
            if (useInternalDragHandle) WindowInsets(0) else BottomSheetDefaults.windowInsets
        },
        // Capping the outer Material modifier changes the anchor coordinate space. Keep the
        // Surface unconstrained and cap a single inner column instead so it remains bottom-aligned.
        dragHandle = if (!useInternalDragHandle) sheetHandle else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (targetFixedHeight != null) Modifier.height(animatedFixedHeight)
                    else if (minHeight != null || maxHeight != null) Modifier.heightIn(
                        min = minHeight ?: Dp.Unspecified,
                        max = maxHeight ?: Dp.Unspecified,
                    ) else Modifier
                ),
        ) {
            if (useInternalDragHandle) {
                sheetHandle()
            }
            content()
        }
    }
}

private const val SheetScrimAlpha = 0.55f
internal val AppSheetHandleTouchHeight = 48.dp

@Suppress("DEPRECATION")
private fun View.realDisplayHeightPx(): Int {
    val metrics = DisplayMetrics()
    display?.getRealMetrics(metrics)
    return metrics.heightPixels.takeIf { it > 0 } ?: height
}
