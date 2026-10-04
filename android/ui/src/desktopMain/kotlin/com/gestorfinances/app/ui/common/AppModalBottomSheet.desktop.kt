package com.gestorfinances.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    dismissRequested: Boolean,
    maxHeightFraction: Float?,
    fixedHeightFraction: Float?,
    fixedHeight: Dp?,
    keepDragHandleInside: Boolean,
    confirmDismiss: (() -> Boolean)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(dismissRequested) {
        if (dismissRequested) onDismissRequest()
    }
    OpenDialogs.Track()
    Dialog(
        onDismissRequest = { if (confirmDismiss?.invoke() != false) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 24.dp,
            modifier = modifier.width(520.dp).heightIn(max = 680.dp),
        ) {
            // The phone's sheets start under a drag handle; here the card's own top edge needs the room.
            Column(modifier = Modifier.padding(top = 20.dp), content = content)
        }
    }
}
