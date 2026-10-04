package com.gestorfinances.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_saving
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun <T : Any> EntityFormSheet(
    form: T?,
    key: (T) -> Any,
    changed: (initial: T, current: T) -> Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    title: @Composable (T) -> String,
    saveLabel: @Composable (T) -> String,
    saving: (T) -> Boolean,
    onDelete: ((T) -> Unit)?,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val current = form ?: return
    val requestDismiss = rememberFormDismissGuard(
        formKey = key(current),
        currentValue = current,
        hasMeaningfulChanges = changed,
        onDiscard = onDiscard,
    )
    OpenDialogs.Track()
    Dialog(onDismissRequest = requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 24.dp,
            modifier = Modifier.width(560.dp).heightIn(max = 680.dp),
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(text = title(current), style = MaterialTheme.typography.titleLarge)
                    content(current)
                }
                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    if (onDelete != null) {
                        DestructiveButton(
                            text = stringResource(Res.string.common_archive),
                            onClick = { onDelete(current) },
                            enabled = !saving(current),
                        )
                    }
                    SecondaryButton(text = stringResource(Res.string.common_cancel), onClick = requestDismiss)
                    PrimaryButton(
                        text = if (saving(current)) stringResource(Res.string.common_saving) else saveLabel(current),
                        onClick = onSave,
                        enabled = !saving(current),
                    )
                }
            }
        }
    }
}
