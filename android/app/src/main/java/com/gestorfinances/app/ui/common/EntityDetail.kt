package com.gestorfinances.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.LocalDate

/** One entry in an entity page's overflow menu. */
data class EntityMenuAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/** An entity page's top bar: back, and the entity's rarer actions (edit, delete) behind an overflow. */
@Composable
fun EntityDetailTopBar(
    onBack: () -> Unit,
    menu: List<EntityMenuAction>,
    modifier: Modifier = Modifier,
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
        Box(modifier = Modifier.weight(1f))
        OverflowMenu(menu)
    }
}

/** The ⋮ button for a page's rarer actions; nothing when [menu] is empty. */
@Composable
fun OverflowMenu(menu: List<EntityMenuAction>) {
    if (menu.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu.forEach { action ->
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            color = if (action.destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/**
 * The top of an entity page: who it is ([leading], its solid identity mark, and the name), then the figures
 * the page is about ([figures], usually one or two [EntityFigure]s), anything that qualifies them,
 * and a row of [EntityActionPill]s with [link], the way into a deeper view, pinned right.
 */
@Composable
fun EntityDetailHeader(
    leading: @Composable () -> Unit,
    name: String,
    subtitle: String?,
    figures: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    details: (@Composable () -> Unit)? = null,
    link: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom,
            content = figures,
        )
        if (details != null) {
            Box(modifier = Modifier.padding(top = 12.dp)) { details() }
        }
        if (actions != null || link != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions?.invoke(this)
                Box(modifier = Modifier.weight(1f))
                link?.invoke()
            }
        }
    }
}

/** The identity mark an entity header leads with. */
val EntityHeaderMarkSize = 48.dp

/** One labelled figure in an entity header: the label small and muted over the amount. */
@Composable
fun EntityFigure(
    label: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    figure: @Composable () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = FinanceTheme.colors.mutedText,
        )
        figure()
    }
}

/** A budget as a bar under an entity's figures: the limit on the left, what is left (or over) on the right. */
@Composable
fun EntityBudgetBar(evaluation: BudgetEvaluation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetProgressBar(
            fraction = evaluation.progressFraction(),
            color = evaluation.status.color(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.entity_budget_limit, formatEuroCents(evaluation.budget.limitAmountCents)),
                modifier = Modifier.weight(1f),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = if (evaluation.remainingCents >= 0L) {
                    stringResource(R.string.budget_remaining, formatEuroCents(evaluation.remainingCents))
                } else {
                    stringResource(R.string.budget_over, formatEuroCents(-evaluation.remainingCents))
                },
                color = evaluation.status.color(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * A compact tonal pill for an entity page's actions. [chevron] marks the one that leads to another
 * page rather than acting here.
 */
@Composable
fun EntityActionPill(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    chevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(start = if (icon != null) 10.dp else 12.dp, end = if (chevron) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val tint = MaterialTheme.colorScheme.onSecondaryContainer
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = text, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (chevron) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/**
 * A hub list page: the title row stays pinned with the page's create action on its right, always
 * in the same spot, and the list scrolls beneath it. [actions] sit just before the create pill,
 * [menu] (the page's rarer actions) just after it.
 */
@Composable
fun ListPage(
    title: String,
    onBack: () -> Unit,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    menu: List<EntityMenuAction> = emptyList(),
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PageHeaderRow(
            onBack = onBack,
            title = title,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    actions()
                    EntityActionPill(text = addLabel, onClick = onAdd, icon = Icons.Outlined.Add)
                    OverflowMenu(menu)
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * One entity on a list page, as a flat row: its identity mark, name and a qualifying line, and
 * its figure on the right. Rows in a run are parted by a hairline inset under the mark, like
 * movement rows; the last one ([isLast]) draws none.
 */
@Composable
fun EntityListRow(
    leading: @Composable () -> Unit,
    title: String,
    isLast: Boolean,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    below: (@Composable () -> Unit)? = null,
    dividerInset: Dp = 52.dp,
    /** Spans the row under the name and the figure, e.g. a bar that should line up across rows. */
    footer: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(top = 10.dp, bottom = if (footer != null) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                below?.invoke()
            }
            trailing()
        }
        if (footer != null) {
            Box(modifier = Modifier.padding(start = dividerInset, bottom = 10.dp)) { footer() }
        }
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(start = dividerInset), color = FinanceTheme.colors.cardBorder)
        }
    }
}

/**
 * A ledger split into days, newest first as the rows arrive, under sticky day headings. The rows
 * of a day form one run; the day carries no total (sums come from canonical SQL, not the list).
 */
@OptIn(ExperimentalFoundationApi::class)
fun <T> LazyListScope.dayGroupedRows(
    rows: List<T>,
    dateOf: (T) -> String,
    key: (T) -> Any,
    today: LocalDate,
    row: @Composable (T, MovementRowPosition) -> Unit,
) {
    rows.groupBy(dateOf).forEach { (date, dayRows) ->
        stickyHeader(key = "day-$date") { MovementDayHeader(date = date, today = today) }
        itemsIndexed(dayRows, key = { _, item -> key(item) }) { index, item ->
            row(item, movementRowPosition(index, dayRows.size))
        }
    }
}

/** A day in a ledger: today and yesterday by name, the year only when it is not this one. */
@Composable
fun MovementDayHeader(date: String, today: LocalDate) {
    val day = parseIsoDateOrNull(date)
    val text = when {
        day == null -> date
        day == today -> stringResource(R.string.common_today)
        day == today.minusDays(1) -> stringResource(R.string.common_yesterday)
        day.year == today.year -> formatWeekdayLongDate(day)
        else -> "${formatWeekdayLongDate(day)} ${day.year}"
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 14.dp, bottom = 4.dp)
            .semantics { heading() },
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.labelLarge,
    )
}
