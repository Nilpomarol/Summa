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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.theme.FinanceTheme

/**
 * An entity's create/edit sheet: a title, the scrolling [content], and Cancel·la / save pinned
 * under it. [form] is the view model's live form, null once saved or discarded; the sheet keeps
 * drawing the last one while it animates away, so it is called whether or not a form is open.
 * Every way out (Cancel·la, Back, a swipe, a tap outside) asks before losing [changed] edits.
 */
@Composable
fun <T : Any> EntityFormSheet(
    form: T?,
    key: (T) -> Any,
    changed: (initial: T, current: T) -> Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    title: @Composable (T) -> String,
    saveLabel: @Composable (T) -> String,
    saving: (T) -> Boolean = { false },
    /** Deletes what is being edited, for a record with no page (and so no menu) of its own. */
    onDelete: ((T) -> Unit)? = null,
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
                        text = stringResource(R.string.common_archive),
                        onClick = { if (form != null) onDelete(current) },
                        enabled = form != null && !saving(current),
                        modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { if (form != null) requestDismiss() },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = if (saving(current)) stringResource(R.string.common_saving) else saveLabel(current),
                    onClick = onSave,
                    enabled = form != null && !saving(current),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** [EntityFormHeader] for an entity marked by an icon on its colour. */
@Composable
fun EntityFormHeader(
    icon: ImageVector,
    color: Color,
    name: String,
    onNameChange: (String) -> Unit,
    nameLabel: String,
    onTileClick: () -> Unit,
    modifier: Modifier = Modifier,
    nameError: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = nextFieldKeyboardActions(),
) = EntityFormHeader(
    tile = { IdentityIconTile(icon = icon, color = color, size = 56.dp) },
    name = name,
    onNameChange = onNameChange,
    nameLabel = nameLabel,
    onTileClick = onTileClick,
    modifier = modifier,
    nameError = nameError,
    imeAction = imeAction,
    keyboardActions = keyboardActions,
)

/**
 * The top of an entity form: the entity's mark ([tile], 56dp), showing its look as it is chosen,
 * beside its name. The mark carries a small pencil and opens the appearance pickers ([onTileClick]).
 */
@Composable
fun EntityFormHeader(
    tile: @Composable () -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    nameLabel: String,
    onTileClick: () -> Unit,
    modifier: Modifier = Modifier,
    nameError: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = nextFieldKeyboardActions(),
) {
    val appearanceLabel = stringResource(R.string.entity_form_appearance)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onTileClick)
                .semantics { contentDescription = appearanceLabel },
        ) {
            tile()
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.dp, FinanceTheme.colors.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = nameLabel) },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { { Text(text = it) } },
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = keyboardActions,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f).scrollToWhen(nameError != null),
        )
    }
}

/**
 * The colour swatches and, when the entity has one, the icon grid, unfolding while [open]: what
 * the header tile opens.
 */
@Composable
fun EntityAppearancePickers(
    open: Boolean,
    colorHex: String?,
    onColor: (String) -> Unit,
    iconOptions: List<EntityIconOption>? = null,
    iconKey: String? = null,
    onIcon: (String) -> Unit = {},
) {
    FormReveal(visible = open) {
        ColorPickerRow(
            label = stringResource(R.string.entity_form_color),
            selectedHex = colorHex,
            onSelect = onColor,
        )
        if (iconOptions != null) {
            IconPickerRow(
                label = stringResource(R.string.entity_form_icon),
                options = iconOptions,
                selectedKey = iconKey,
                onSelect = onIcon,
            )
        }
    }
}

/** Form fields that unfold into place when shown and fold away when hidden, rather than jumping. */
@Composable
fun FormReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/**
 * A disclosure for a form's less common fields: its [label], what the closed group already holds
 * ([summary]), and a chevron that turns when [open]; the fields ([content]) unfold beneath it.
 */
@Composable
fun FormDisclosure(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.movement_form_optional),
    summary: String = "",
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DisclosureRow(open = open, onToggle = onToggle, label = label, summary = summary)
        if (content != null) {
            FormReveal(visible = open, modifier = Modifier.padding(top = 12.dp), content = content)
        }
    }
}

@Composable
private fun DisclosureRow(open: Boolean, onToggle: () -> Unit, label: String, summary: String) {
    val chevronTurn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val expandedLabel = stringResource(if (open) R.string.common_expanded else R.string.common_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .semantics { stateDescription = expandedLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = FinanceTheme.colors.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronTurn),
        )
    }
}
