package com.gestorfinances.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetStatus
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.isDark
import com.gestorfinances.app.ui.theme.TokenColor
import com.gestorfinances.app.ui.theme.categoryTint
import com.gestorfinances.app.ui.theme.themedIdentityColor

private val PillShape = RoundedCornerShape(percent = 50)

/**
 * Raised surface (design elevation e2): the default card across the app. Depth comes from three
 * cues that read as one lit object — a warm cast shadow underneath, a rim that catches light at
 * the top edge and settles into the hairline at the bottom, and a sheen down the face.
 */
@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FinanceTheme.colors
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier.shadow(
            elevation = 14.dp,
            shape = shape,
            clip = false,
            ambientColor = colors.cardShadow,
            spotColor = colors.cardShadow,
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(colors.cardRim, colors.cardBorder))),
    ) {
        Column(
            modifier = Modifier.background(
                Brush.verticalGradient(listOf(colors.cardSheen, Color.Transparent)),
            ),
            content = content,
        )
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

/** Rounded-square icon chip with a soft tint background (design baseline category identity). */
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
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/**
 * Back-button + optional title header, the first row of a full-page screen converted from a
 * bottom sheet (design baseline). [trailing] covers variants that carry extra content next to the
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
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
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

/** Labeled, wrapping row of chips — used for form selectors and list filters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlowSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

/** Filter chip (design baseline): active = ink fill + white; inactive = bordered, muted. */
@Composable
fun FinanceFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PillShape,
        color = if (selected) selectedColor else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) null else BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .heightIn(min = 20.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
        )
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

/** Segmented control (design baseline): N100 track, selected segment = white fill + e1 shadow. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 36.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                onClick = { if (!isSelected) onSelect(option) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraSmall,
                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    FinanceTheme.colors.mutedText
                },
                shadowElevation = if (isSelected) 1.dp else 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = itemHeight)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Labeled segmented control with a bordered track and an indigo-tint active segment
 * (design baseline — indigo carries active state). A calmer, flatter alternative to
 * [SegmentedControl] for form selectors: hairline-bordered container, the selected segment
 * filled with the indigo tint and ink-pressed label, the rest transparent and muted.
 */
@Composable
fun <T> LabeledSegmentedControl(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Surface(
                        onClick = { if (!isSelected) onSelect(option) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            FinanceTheme.colors.mutedText
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Primary action button (design baseline): indigo fill, white label, r2. */
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
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
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
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
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
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Compact visual edit affordance for dense cards; intentionally does not impose a 48dp row height. */
@Composable
fun CompactEditIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(
                onClickLabel = stringResource(R.string.common_edit),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.common_edit),
            modifier = Modifier.size(14.dp),
            tint = FinanceTheme.colors.mutedText,
        )
    }
}

/** Bordered icon button for top-bar actions (design baseline); 44dp touch target (design baseline). */
@Composable
fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

enum class BannerKind { Info, Alert, Error }

/** Inline banner for non-blocking notices (design baseline — never block, warn). */
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
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
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

