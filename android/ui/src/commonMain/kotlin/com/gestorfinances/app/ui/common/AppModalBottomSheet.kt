package com.gestorfinances.app.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 *
 * On the desktop it is a dialog: the height and drag-handle parameters do not apply there.
 */
@Composable
expect fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissRequested: Boolean = false,
    maxHeightFraction: Float? = null,
    fixedHeightFraction: Float? = null,
    fixedHeight: Dp? = null,
    keepDragHandleInside: Boolean = false,
    confirmDismiss: (() -> Boolean)? = null,
    content: @Composable ColumnScope.() -> Unit,
)
