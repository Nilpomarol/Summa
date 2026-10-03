package com.gestorfinances.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.gestorfinances.app.ui.movements.AppTextField
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_save
import com.gestorfinances.ui.resources.movement_create_person_name_hint
import com.gestorfinances.ui.resources.movement_create_person_title

/** Names a new person without leaving the form that needs them. */
@Composable
fun CreatePersonDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var personName by remember { mutableStateOf("") }
    OpenDialogs.Track()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.movement_create_person_title)) },
        text = {
            AppTextField(
                value = personName,
                onValueChange = { personName = it },
                label = { Text(stringResource(Res.string.movement_create_person_name_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AppTextButton(
                onClick = { if (personName.isNotBlank()) onConfirm(personName.trim()) },
            ) { Text(stringResource(Res.string.common_save)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
