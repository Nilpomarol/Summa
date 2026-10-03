package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gestorfinances.app.ui.common.AppModalBottomSheet

/** [CreateCategoryContent] in a bottom sheet that animates away once it is done with. */
@Composable
internal fun CreateCategorySheet(
    onConfirm: (name: String, iconKey: String?, colorHex: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        dismissRequested = closing,
        maxHeightFraction = 0.9f,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false).navigationBarsPadding()) {
            CreateCategoryContent(
                onConfirm = { name, iconKey, colorHex ->
                    onConfirm(name, iconKey, colorHex)
                    closing = true
                },
                onCancel = { closing = true },
            )
        }
    }
}
