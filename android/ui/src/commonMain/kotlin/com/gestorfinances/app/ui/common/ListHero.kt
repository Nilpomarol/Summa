package com.gestorfinances.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.gestorfinances.app.ui.common.AppIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.abs
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import java.time.YearMonth
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asEyebrow

/** The small wide-tracked label that opens a hero panel. */
@Composable
fun HeroEyebrow(text: String) {
    Text(
        text = text.uppercase(),
        color = FinanceTheme.colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
        style = MaterialTheme.typography.labelSmall.asEyebrow(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A list page's hero: the forest panel with an eyebrow, the page's one figure in the serif, and
 * whatever makes that figure mean something ([content]) under it. The page's own [watermark]
 * icon sits oversized and faint in the corner, as the logo does on Home's hero.
 */
@Composable
fun ListHero(
    eyebrow: String,
    cents: Long,
    watermark: ImageVector,
    modifier: Modifier = Modifier,
    figureColor: Color = FinanceTheme.colors.heroOnSurface,
    signed: Boolean = false,
    eyebrowTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    HeroPanel(modifier = modifier.fillMaxWidth()) {
        // In a box matched to the panel, so the mark never sets the panel's height itself.
        Box(modifier = Modifier.matchParentSize()) {
            Icon(
                imageVector = watermark,
                contentDescription = null,
                tint = FinanceTheme.colors.heroOnSurface.copy(alpha = 0.05f),
                // Mostly inside the panel, so the icon still reads as what it is.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = 6.dp)
                    .requiredSize(136.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { HeroEyebrow(text = eyebrow) }
                eyebrowTrailing?.invoke()
            }
            Spacer(modifier = Modifier.height(6.dp))
            MoneyText(
                cents = cents,
                color = figureColor,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp),
                signed = signed,
            )
            content()
        }
    }
}

/** The month a hero covers, as a small chip on it that opens the month picker. */
@Composable
fun HeroMonthPicker(month: YearMonth, months: List<YearMonth>, onMonthSelected: (YearMonth) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val onHero = FinanceTheme.colors.heroOnSurface
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(heroTint(onHero, 0.12f))
                .clickable { expanded = true }
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = formatMonthYear(month), color = onHero, style = MaterialTheme.typography.labelMedium)
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = onHero.copy(alpha = HERO_MUTED_ALPHA),
                modifier = Modifier.size(16.dp),
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MonthPickerContent(
                initial = month,
                availableMonths = months,
                onSelect = {
                    expanded = false
                    onMonthSelected(it)
                },
            )
        }
    }
}

/** The [SegmentedControl] as it sits on the hero: compact, in the hero's own colours. */
@Composable
fun <T> HeroToggle(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val onHero = FinanceTheme.colors.heroOnSurface
    SegmentedControl(
        options = options,
        selected = selected,
        label = label,
        onSelect = onSelect,
        itemHeight = 21.dp,
        compact = true,
        colors = SegmentedControlColors(
            track = heroTint(onHero, 0.08f),
            pill = heroTint(onHero, 0.2f),
            onPill = onHero,
            label = onHero.copy(alpha = HERO_MUTED_ALPHA),
        ),
        textStyle = MaterialTheme.typography.labelMedium,
    )
}

/**
 * [color] at [alpha] over the hero, flattened to a solid colour: anything filled on the hero is
 * opaque, so the watermark never shows through it.
 */
@Composable
fun heroTint(color: Color, alpha: Float): Color = color.copy(alpha = alpha).compositeOver(FinanceTheme.colors.heroSurface)

/** A muted line of text on the hero. */
@Composable
fun HeroCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = FinanceTheme.colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A figure in a tinted box on the hero, for two quantities side by side. */
@Composable
fun RowScope.HeroStatBox(label: String, cents: Long, color: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(heroTint(color, 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = FinanceTheme.colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
            style = MaterialTheme.typography.labelMedium,
        )
        MoneyText(cents = cents, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * One bar per month for how much a figure moved in it, up in the income colour and down in the
 * debt colour from a shared zero line. A line above names the selected month and its change in
 * full (the current month until another bar is tapped); every other bar, counting back from the
 * current one, carries its short month name. The selected bar is drawn at full strength and the
 * rest quieter, but all solid.
 */
@Composable
fun HeroMonthChangeBars(title: String, changes: List<Pair<YearMonth, Long>>, modifier: Modifier = Modifier) {
    if (changes.isEmpty()) return
    val colors = FinanceTheme.colors
    val up = colors.heroIncome
    val down = colors.heroDebt
    val upQuiet = heroTint(up, 0.5f)
    val downQuiet = heroTint(down, 0.5f)
    val baseline = heroTint(colors.heroOnSurface, 0.2f)
    val muted = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA)
    var selected by remember(changes) { mutableStateOf(changes.lastIndex) }
    val (selectedMonth, selectedCents) = changes[selected]
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = muted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMonthYear(selectedMonth) + " ",
                color = muted,
                style = MaterialTheme.typography.labelMedium,
            )
            MoneyText(
                cents = selectedCents,
                color = when {
                    selectedCents > 0L -> up
                    selectedCents < 0L -> down
                    else -> muted
                },
                style = MaterialTheme.typography.labelLarge,
                signed = selectedCents != 0L,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(changes) {
                    detectTapGestures { offset ->
                        selected = (offset.x / (size.width.toFloat() / changes.size)).toInt().coerceIn(0, changes.lastIndex)
                    }
                },
        ) {
            val maxUp = changes.maxOf { it.second }.coerceAtLeast(0L).toFloat()
            val maxDown = (-changes.minOf { it.second }).coerceAtLeast(0L).toFloat()
            val span = (maxUp + maxDown).takeIf { it > 0f } ?: 1f
            val zeroY = size.height * maxUp / span
            val slot = size.width / changes.size
            val barWidth = slot * 0.56f
            val minHeight = 2.dp.toPx()
            changes.forEachIndexed { index, (_, cents) ->
                val strong = index == selected
                val height = (size.height * abs(cents) / span).coerceAtLeast(if (cents != 0L) minHeight else 0f)
                val top = if (cents >= 0L) zeroY - height else zeroY
                drawRoundRect(
                    color = when {
                        cents >= 0L -> if (strong) up else upQuiet
                        else -> if (strong) down else downQuiet
                    },
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, top),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            drawLine(baseline, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            changes.forEachIndexed { index, (month, _) ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Every other month, so the short names have room to spill into the gap beside them.
                    if ((changes.lastIndex - index) % 2 == 0) {
                        Text(
                            text = formatShortMonth(month),
                            color = if (index == selected) colors.heroOnSurface else muted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A list page's filter row: quick [chips] on the left and a search button on the right, never
 * wrapping. Search opens a field in place of the row; closing it clears the query.
 */
@Composable
fun ListFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    modifier: Modifier = Modifier,
    chips: @Composable RowScope.() -> Unit = {},
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    if (searching || query.isNotEmpty()) {
        val focus = remember { FocusRequester() }
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = searchPlaceholder,
                focusRequester = focus,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(onClick = { onQueryChange(""); searching = false }) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
        LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
            Spacer(modifier = Modifier.weight(1f))
            AppIconButton(onClick = { searching = true }) {
                Icon(Icons.Outlined.Search, contentDescription = searchPlaceholder)
            }
        }
    }
}
