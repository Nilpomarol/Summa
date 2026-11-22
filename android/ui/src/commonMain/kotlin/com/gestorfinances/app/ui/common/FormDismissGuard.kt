package com.gestorfinances.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.form_discard_confirm_action
import com.gestorfinances.ui.resources.form_discard_confirm_body
import com.gestorfinances.ui.resources.form_discard_confirm_title

/**
 * Keeps a snapshot of a form when it opens and funnels every dismissal route through one guard.
 * Callers provide a semantic comparison so validation and disclosure-only state never creates a
 * spurious discard warning.
 */
@Composable
fun <T> rememberFormDismissGuard(
    formKey: Any?,
    currentValue: T,
    hasMeaningfulChanges: (initial: T, current: T) -> Boolean,
    onDiscard: () -> Unit,
): () -> Unit {
    val initialValue = remember(formKey) { currentValue }
    var discardConfirmationVisible by remember(formKey) { mutableStateOf(false) }

    if (discardConfirmationVisible) {
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = { discardConfirmationVisible = false },
            title = { Text(stringResource(Res.string.form_discard_confirm_title)) },
            text = { Text(stringResource(Res.string.form_discard_confirm_body)) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        discardConfirmationVisible = false
                        onDiscard()
                    },
                ) {
                    Text(stringResource(Res.string.form_discard_confirm_action))
                }
            },
            dismissButton = {
                AppTextButton(onClick = { discardConfirmationVisible = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    return {
        if (hasMeaningfulChanges(initialValue, currentValue)) {
            discardConfirmationVisible = true
        } else {
            onDiscard()
        }
    }
}
