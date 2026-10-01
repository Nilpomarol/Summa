package com.gestorfinances.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.isDark
import com.gestorfinances.app.ui.theme.TokenColor
import com.gestorfinances.app.ui.theme.categoryTint
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.app.ui.theme.onIdentityColor

/** Fully rounded ends: chips, the search field, and the navigation dock share it. */
val PillShape = RoundedCornerShape(percent = 50)

/** Buttons are soft but not bubbly: a fixed 16dp radius at every height. */
private val ButtonShape = RoundedCornerShape(16.dp)
private val ButtonMinHeight = 52.dp

/** The default card: paper on paper, separated from the page by a hairline rather than a shadow. */
@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
    ) {
        Column(content = content)
    }
}

/** Token-backed switch; enabled-off and disabled-off remain distinguishable in both themes. */
@Composable
fun FinanceSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FinanceTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = if (onCheckedChange == null) modifier.clearAndSetSemantics {} else modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = colors.switchOffThumb,
            uncheckedTrackColor = colors.switchOffTrack,
            uncheckedBorderColor = colors.switchOffBorder,
            disabledCheckedThumbColor = colors.switchDisabledThumb,
            disabledCheckedTrackColor = colors.switchDisabledTrack,
            disabledCheckedBorderColor = colors.switchDisabledBorder,
            disabledUncheckedThumbColor = colors.switchDisabledThumb,
            disabledUncheckedTrackColor = colors.switchDisabledTrack,
            disabledUncheckedBorderColor = colors.switchDisabledBorder,
        ),
    )
}

/** Rounded-square icon chip with a soft tint background (category identity). */
@Composable
fun IconChip(
    icon: ImageVector,
    contentDescription: String?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val visibleColor = themedIdentityColor(color)
    val visibleTint = categoryTint(visibleColor)
    Box(
        modifier = modifier
            .size(size)
            .background(visibleTint, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = visibleColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Small neutral pill for orthogonal badges (default account, one-time, shared). */
@Composable
fun NeutralPill(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            leadingIcon?.let {
                Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Primary heading for a root screen, with an optional contextual action. */
@Composable
fun RootPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** In-content section heading: the serif at 20sp, a step below a sub-page title. */
private val SectionTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp)

/** In-content section heading with an optional trailing action slot. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = SectionTitleStyle,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/**
 * Back-button + optional title header, the first row of a full-page screen converted from a
 * bottom sheet. [trailing] covers variants that carry extra content next to the
 * title (e.g. a save/analysis action); screens whose header needs more than a single title line
 * (an icon chip, a multi-line block) compose their own header instead of using this.
 */
@Composable
fun PageHeaderRow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        trailing?.invoke()
    }
}

/** Tappable section header with a count badge and an expand/collapse chevron. */
@Composable
fun CollapsibleSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = SectionTitleStyle,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (count > 0) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = FinanceTheme.colors.mutedText,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = FinanceTheme.colors.mutedText,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The app's search field: a paper pill on the page ground, carrying its own icon and clear action
 * instead of Material's field chrome, so it sits with the cards and pills around it rather than
 * looking borrowed. Search is live, so the keyboard's action only puts the keyboard away.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = FinanceTheme.colors
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = if (query.isEmpty()) 16.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.mutedText,
                modifier = Modifier.size(20.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.subtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.common_clear_search),
                        tint = colors.mutedText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** How far a label on the ink hero drops back from the figure it sits with. */
/**
 * A compact tinted pill with a chevron, for a section's way into its full page. Shorter than a
 * text button, so the section header stays tight under whatever sits above it.
 */
@Composable
fun LinkPill(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}

const val HERO_MUTED_ALPHA = 0.64f

/** The app's forest panel: the single high-contrast anchor a page opens with. The caller owns the padding inside. */
@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(FinanceTheme.colors.heroSurface),
        content = content,
    )
}

/**
 * A saved identity colour, filled rather than tinted, with the icon knocked out of it. The one
 * saturated thing in a row: a column of these is what a thumb-scroll navigates by.
 */
@Composable
fun IdentityIconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val fill = themedIdentityColor(color)
    Box(
        modifier = modifier
            .size(size)
            // The corner scales with the tile (14dp at the 40dp list size), so a small tile stays a
            // rounded square and never reads as a person's circle.
            .background(fill, RoundedCornerShape(size * 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onIdentityColor(fill),
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** Filter chip: active = primary fill; inactive = bordered, muted. */
@Composable
fun FinanceFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    trailingIcon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PillShape,
        color = if (selected) selectedColor else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 20.dp)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Compact selector field for filters.
 * active = emphasized border and text color.
 */
@Composable
fun FilterSelectorField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    val borderColor = if (active) MaterialTheme.colorScheme.primary else FinanceTheme.colors.cardBorder
    val contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, borderColor),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = FinanceTheme.colors.mutedText,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active && onClear != null) {
                IconButton(onClick = { onClear() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_remove),
                        modifier = Modifier.size(16.dp),
                        tint = FinanceTheme.colors.mutedText
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = FinanceTheme.colors.mutedText
                )
            }
        }
    }
}

/** A [SegmentedControl]'s colours: its track, the pill on the chosen option, and the labels. */
data class SegmentedControlColors(
    val track: Color,
    val pill: Color,
    val onPill: Color,
    val label: Color,
)

/**
 * The app's one "pick one of a few" control: a soft track of equal options with a solid pill that
 * slides to the chosen one. Every such choice uses it — a form's type, a filter between two views,
 * the switch on a hero ([compact], in the hero's colours).
 *
 * [optionColor] gives an option a colour of its own (a movement type's money colour), which the
 * pill takes while that option is chosen; otherwise the pill is the theme's primary.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 38.dp,
    optionColor: (@Composable (T) -> Color)? = null,
    /** As wide as its options need rather than the full width, for a switch that sits beside something. */
    compact: Boolean = false,
    colors: SegmentedControlColors = SegmentedControlColors(
        track = MaterialTheme.colorScheme.surfaceVariant,
        pill = MaterialTheme.colorScheme.primary,
        onPill = MaterialTheme.colorScheme.onPrimary,
        label = FinanceTheme.colors.mutedText,
    ),
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    if (options.isEmpty()) return
    val index = options.indexOf(selected).coerceAtLeast(0)
    val ownColor = optionColor?.invoke(options[index])
    val pill by animateColorAsState(ownColor ?: colors.pill, label = "segmented-pill")
    val onPill = ownColor?.let(::onIdentityColor) ?: colors.onPill

    var rowWidth by remember { mutableIntStateOf(0) }
    val segment = rowWidth.toFloat() / options.size
    // The pill appears in place the first time and slides from then on.
    val offset = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(index, segment) {
        if (segment == 0f) return@LaunchedEffect
        if (placed) {
            offset.animateTo(index * segment, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        } else {
            offset.snapTo(index * segment)
            placed = true
        }
    }

    Box(
        modifier = modifier
            .then(if (compact) Modifier.width(IntrinsicSize.Max) else Modifier.fillMaxWidth())
            .background(colors.track, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        Row(
            modifier = Modifier
                .onSizeChanged { rowWidth = it.width }
                .drawBehind {
                    if (placed) {
                        drawRoundRect(
                            color = pill,
                            topLeft = Offset(offset.value, 0f),
                            size = Size(segment, size.height),
                            cornerRadius = CornerRadius(9.dp.toPx()),
                        )
                    }
                },
        ) {
            options.forEachIndexed { optionIndex, option ->
                val isSelected = optionIndex == index
                val textColor by animateColorAsState(if (isSelected) onPill else colors.label, label = "segmented-label")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = itemHeight)
                        .clip(RoundedCornerShape(9.dp))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { if (!isSelected) onSelect(option) })
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        color = textColor,
                        style = textStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A [SegmentedControl] under the small muted label that names what it chooses, as form fields have. */
@Composable
fun <T> LabeledSegmentedControl(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** An option's own colour (e.g. the money colour of a type), filling the pill when it is chosen. */
    optionColor: (@Composable (T) -> Color)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        SegmentedControl(
            options = options,
            selected = selected,
            label = optionLabel,
            onSelect = onSelect,
            optionColor = optionColor,
        )
    }
}

/** Primary action button: forest fill, ivory label. At most one per view. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = FinanceTheme.colors.disabledText,
        ),
    ) {
        leadingIcon?.let {
            Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DestructiveTextButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        content()
    }
}

/** Outlined secondary action with the shared action-row height and shape. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Outlined destructive action with the same layout contract as [SecondaryButton]. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

enum class BannerKind { Info, Alert, Error }

/** Inline banner for non-blocking notices (never block, warn). */
@Composable
fun InlineBanner(
    kind: BannerKind,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = bannerColors(kind)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.background,
        contentColor = colors.foreground,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = bannerIcon(kind),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private data class BannerPalette(
    val background: Color,
    val border: Color,
    val foreground: Color,
)

@Composable
private fun bannerColors(kind: BannerKind): BannerPalette {
    if (!FinanceTheme.isDark) {
        return when (kind) {
            BannerKind.Info -> BannerPalette(
                TokenColor.BannerInfoBg,
                TokenColor.BannerInfoBorder,
                TokenColor.BannerInfoText,
            )
            BannerKind.Alert -> BannerPalette(
                TokenColor.BannerAlertBg,
                TokenColor.BannerAlertBorder,
                TokenColor.BannerAlertText,
            )
            BannerKind.Error -> BannerPalette(
                TokenColor.BannerErrorBg,
                TokenColor.BannerErrorBorder,
                TokenColor.BannerErrorText,
            )
        }
    }
    // Dark mode: tokens define only light banners, so derive from the functional color.
    val base = when (kind) {
        BannerKind.Info -> MaterialTheme.colorScheme.primary
        BannerKind.Alert -> FinanceTheme.colors.alert
        BannerKind.Error -> FinanceTheme.colors.debt
    }
    return BannerPalette(base.copy(alpha = 0.14f), base.copy(alpha = 0.34f), base)
}

private fun bannerIcon(kind: BannerKind): ImageVector =
    when (kind) {
        BannerKind.Info -> Icons.Outlined.Info
        BannerKind.Alert -> Icons.Outlined.Warning
        BannerKind.Error -> Icons.Outlined.Error
    }

/** Labeled compact selection block for filters (label + value + chevron). */
@Composable
fun FilterSelectorField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = FinanceTheme.colors.mutedText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Thin rounded progress track used for budget evaluation. Shared by Budgets and any
 * surface (e.g. category detail) that embeds a budget's progress against its limit. */
@Composable
fun BudgetProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(FinanceTheme.colors.progressTrack, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(color, RoundedCornerShape(50)),
        )
    }
}

@Composable
fun BudgetStatus.color(): Color =
    when (this) {
        BudgetStatus.OK -> FinanceTheme.colors.income
        BudgetStatus.WARN -> FinanceTheme.colors.alert
        BudgetStatus.OVER -> FinanceTheme.colors.debt
    }

@Composable
fun BudgetStatus.label(): String =
    when (this) {
        BudgetStatus.OK -> stringResource(R.string.budget_status_ok)
        BudgetStatus.WARN -> stringResource(R.string.budget_status_warn)
        BudgetStatus.OVER -> stringResource(R.string.budget_status_over)
    }

/** Fraction of the budget's limit consumed so far, clamped to [0, 1] for the progress bar. */
fun BudgetEvaluation.progressFraction(): Float {
    val limit = budget.limitAmountCents
    if (limit <= 0L) return 0f
    return (actualCents.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}

