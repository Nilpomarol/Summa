package com.gestorfinances.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.TokenColor
import com.gestorfinances.app.ui.theme.categoryTint
import java.time.LocalDate

private val PillShape = RoundedCornerShape(percent = 50)

/** Flat, bordered surface (design elevation e0): the default card across the app. */
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

/** Rounded-square icon chip with a soft tint background (design §2.5 category identity). */
@Composable
fun IconChip(
    icon: ImageVector,
    contentDescription: String?,
    color: Color,
    modifier: Modifier = Modifier,
    tint: Color = categoryTint(color),
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
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

/** Screen/section heading with an optional trailing action slot. */
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
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
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

/** Filter chip (design §6): active = ink fill + white; inactive = bordered, muted. */
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
                IconButton(
                    onClick = { onClear() },
                    modifier = Modifier.size(24.dp)
                ) {
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

/** Segmented control (design §6): N100 track, selected segment = white fill + e1 shadow. */
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
                onClick = { onSelect(option) },
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
 * (design §2.3 — indigo carries active state). A calmer, flatter alternative to
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
                        onClick = { onSelect(option) },
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

/** Primary action button (design §6): indigo fill, white label, r2. */
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
            disabledContentColor = FinanceTheme.colors.mutedText,
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
    content: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        content()
    }
}

/** 40×40 bordered icon button for top-bar actions (design §6). */
@Composable
fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
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

/** Inline banner for non-blocking notices (design §6 — never block, warn). */
@Composable
fun InlineBanner(
    kind: BannerKind,
    text: String,
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
        }
    }
}

/** Uppercase Catalan date group label with Avui/Ahir prefixes (movements list). */
@Composable
fun DateGroupHeader(
    iso: String,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val date = parseIsoDateOrNull(iso)
    val formatted = formatLongDate(iso)
    val label = when (date) {
        null -> iso
        today -> "${stringResource(R.string.date_today)} · $formatted"
        today.minusDays(1) -> "${stringResource(R.string.date_yesterday)} · $formatted"
        else -> formatted
    }
    Text(
        text = label.uppercase(),
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = FinanceTheme.colors.mutedText,
    )
}

private data class BannerPalette(
    val background: Color,
    val border: Color,
    val foreground: Color,
)

@Composable
private fun bannerColors(kind: BannerKind): BannerPalette {
    if (!isSystemInDarkTheme()) {
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

