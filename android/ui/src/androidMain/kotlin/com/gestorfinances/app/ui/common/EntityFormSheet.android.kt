package com.gestorfinances.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_collapsed
import com.gestorfinances.ui.resources.common_expanded
import com.gestorfinances.ui.resources.common_saving
import com.gestorfinances.ui.resources.entity_form_appearance
import com.gestorfinances.ui.resources.entity_form_color
import com.gestorfinances.ui.resources.entity_form_icon
import com.gestorfinances.ui.resources.movement_form_optional
import com.gestorfinances.app.ui.theme.FinanceTheme

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
    var retained by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(form) { if (form != null) retained = form }
    val current = form ?: retained ?: return
    val requestDismiss = rememberFormDismissGuard(
        formKey = key(current),
        currentValue = current,
        hasMeaningfulChanges = changed,
        onDiscard = onDiscard,
    )
    val initial = remember(key(current)) { current }
    AppModalBottomSheet(
        onDismissRequest = {
            // A swipe or a tap outside that got past confirmDismiss leaves the form open.
            if (form != null) onDiscard()
            retained = null
        },
        maxHeightFraction = 0.92f,
        dismissRequested = form == null,
        confirmDismiss = {
            val hasChanges = form != null && changed(initial, current)
            if (hasChanges) requestDismiss()
            !hasChanges
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = title(current), style = MaterialTheme.typography.titleLarge)
                content(current)
            }
            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onDelete != null) {
                    DestructiveButton(
                        text = stringResource(Res.string.common_archive),
                        onClick = { if (form != null) onDelete(current) },
                        enabled = form != null && !saving(current),
                        modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = { if (form != null) requestDismiss() },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = if (saving(current)) stringResource(Res.string.common_saving) else saveLabel(current),
                    onClick = onSave,
                    enabled = form != null && !saving(current),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
