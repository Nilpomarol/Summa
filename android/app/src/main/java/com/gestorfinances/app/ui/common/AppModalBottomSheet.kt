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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/**
 * App-wide modal-sheet contract.
 *
 * Sheets move directly between hidden and expanded, measure to their content, and let Material own
 * safe-area, IME, nested-scroll, predictive-back, and user-driven dismissal behaviour. A caller
 * may cap its measured height or use an animated fixed height.
 *
 * [confirmDismiss] is asked before a swipe or a tap outside hides the sheet; returning false keeps
 * it open (a form with unsaved changes asks first instead).
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
    maxHeightFraction: Float? = null,
    fixedHeightFraction: Float? = null,
    fixedHeight: Dp? = null,
    keepDragHandleInside: Boolean = false,
    confirmDismiss: (() -> Boolean)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(maxHeightFraction == null || maxHeightFraction in 0f..1f)
    require(fixedHeightFraction == null || fixedHeightFraction in 0f..1f)
    require(fixedHeightFraction == null || maxHeightFraction == null)
    require(fixedHeight == null || (maxHeightFraction == null && fixedHeightFraction == null))

    val currentConfirmDismiss by rememberUpdatedState(confirmDismiss)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            target != SheetValue.Hidden ||
                currentConfirmDismiss?.invoke() != false
        },
    )
    val density = LocalDensity.current
    val screenHeightPx = LocalView.current.realDisplayHeightPx()
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
    val hasConstrainedHeight = maxHeight != null || targetFixedHeight != null
    val useInternalDragHandle = hasConstrainedHeight || keepDragHandleInside

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            sheetState.hide()
            onDismissRequest()
        }
    }

    // Material hides the sheet before asking the caller to close it. A caller that stays open
    // instead (a discard confirmation the user cancels) must get its sheet back, not an invisible
    // one: if this sheet is still composed on the next recomposition, it slides back up.
    var hiddenByUser by remember { mutableStateOf(false) }
    val onUserDismiss: () -> Unit = {
        hiddenByUser = true
        onDismissRequest()
    }
    if (hiddenByUser && !dismissRequested) {
        LaunchedEffect(Unit) {
            sheetState.show()
            hiddenByUser = false
        }
    }

    val sheetHandle: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BottomSheetDefaults.DragHandle()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onUserDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = SheetScrimAlpha),
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
                    else if (maxHeight != null) Modifier.heightIn(max = maxHeight)
                    else Modifier
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
