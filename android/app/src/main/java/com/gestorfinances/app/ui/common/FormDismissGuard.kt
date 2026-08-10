package com.gestorfinances.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.gestorfinances.app.R

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
        AlertDialog(
            onDismissRequest = { discardConfirmationVisible = false },
            title = { Text(stringResource(R.string.form_discard_confirm_title)) },
            text = { Text(stringResource(R.string.form_discard_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardConfirmationVisible = false
                        onDiscard()
                    },
                ) {
                    Text(stringResource(R.string.form_discard_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirmationVisible = false }) {
                    Text(stringResource(R.string.common_cancel))
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
