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
import com.gestorfinances.app.ui.movements.AppTextField
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
    val appearanceLabel = stringResource(Res.string.entity_form_appearance)
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
        AppTextField(
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
            label = stringResource(Res.string.entity_form_color),
            selectedHex = colorHex,
            onSelect = onColor,
        )
        if (iconOptions != null) {
            IconPickerRow(
                label = stringResource(Res.string.entity_form_icon),
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
    label: String = stringResource(Res.string.movement_form_optional),
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
    val expandedLabel = stringResource(if (open) Res.string.common_expanded else Res.string.common_collapsed)
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
