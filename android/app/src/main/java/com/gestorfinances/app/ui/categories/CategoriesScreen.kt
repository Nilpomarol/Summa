package com.gestorfinances.app.ui.categories

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.theme.heroIdentityColor
import java.time.YearMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.heroTint
import androidx.compose.material.icons.outlined.Category
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.ListFilterBar
import com.gestorfinances.app.ui.common.HeroToggle
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.ListHero
import androidx.compose.runtime.saveable.rememberSaveable
import com.gestorfinances.app.ui.common.EntityListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.sortedByDisplayOrderThenName
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityBudgetBar
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.dayGroupedRows
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.draw.clip
import java.time.LocalDate
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun categoriesViewModel(appContainer: AppContainer): CategoriesViewModel = viewModel {
    CategoriesViewModel(
        categoryRepository = appContainer.categoryRepository,
        analysisRepository = appContainer.analysisRepository,
        movementRepository = appContainer.movementRepository,
        budgetRepository = appContainer.budgetRepository,
        templateRepository = appContainer.templateRepository,
    )
}

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenDetail: (CategoryRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    CategoriesContent(
        onBack = onBack,
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onOpen = onOpenDetail,
        onRetry = viewModel::onScreenShown,
    )
    CategoryFormSheet(state = state, viewModel = viewModel)
}

/** A category's own page: what it has cost, its budget, its subcategories, and its movements. */
@Composable
fun CategoryDetailPage(
    categoryId: String,
    onBack: () -> Unit,
    viewModel: CategoriesViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenCategory: (categoryId: String) -> Unit,
    onViewAnalysis: (categoryId: String, categoryName: String) -> Unit,
    onBudget: (categoryId: String, hasBudget: Boolean) -> Unit,
    onAddMovement: (categoryId: String) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, categoryId, dataVersion) {
        viewModel.onCategoryDetailOpened(categoryId)
    }

    val detail = state.flowDetail
    when {
        detail != null -> CategoryFlowScreen(
            detail = detail,
            state = state,
            onBack = onBack,
            onRetry = { viewModel.onCategoryDetailOpened(categoryId) },
            onOpenCategory = onOpenCategory,
            onViewAnalysis = { onViewAnalysis(detail.category.id, detail.category.name) },
            onBudget = { onBudget(detail.category.id, detail.budgetEvaluation != null) },
            onAddMovement = { onAddMovement(detail.category.id) },
            onEdit = { viewModel.onEditClicked(detail.category) },
            onArchive = { viewModel.onArchiveClicked(detail.category) },
            onMovementDetail = onMovementDetail,
            modifier = modifier,
        )
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeaderRow(onBack = onBack)
            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                InlineFailureBanner(
                    diagnostic = errorMessage,
                    messageRes = R.string.failure_load_categories,
                    onRetry = { viewModel.onCategoryDetailOpened(categoryId) },
                )
            } else {
                Text(
                    text = stringResource(R.string.category_flow_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    CategoryFormSheet(state = state, viewModel = viewModel)

    state.archiveCandidate?.let {
        CategoryArchiveDialog(
            candidate = it,
            viewModel = viewModel,
            onArchived = { undo ->
                onDeleteCommitted(undo)
                onBack()
            },
        )
    }
}

@Composable
private fun CategoryArchiveDialog(
    candidate: CategoryArchiveCandidate,
    viewModel: CategoriesViewModel,
    onArchived: DeleteUndoHandler,
) {
    val hasDependencies = candidate.activeTemplateCount + candidate.budgetCount + candidate.childCount != 0
    AlertDialog(
        onDismissRequest = viewModel::onArchiveDismissed,
        title = { Text(text = stringResource(R.string.category_archive_confirm_title)) },
        text = {
            Text(
                text = if (!hasDependencies) {
                    stringResource(R.string.category_archive_warning)
                } else {
                    stringResource(
                        R.string.category_archive_dependencies_warning,
                        candidate.activeTemplateCount,
                        candidate.budgetCount,
                        candidate.childCount,
                    )
                },
            )
        },
        confirmButton = {
            DestructiveTextButton(onClick = { viewModel.onArchiveConfirmed(onSuccess = onArchived) }) {
                Text(
                    text = if (!hasDependencies) {
                        stringResource(R.string.common_archive)
                    } else {
                        stringResource(R.string.category_archive_resolve_and_archive)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onArchiveDismissed) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun CategoriesContent(
    onBack: () -> Unit,
    state: CategoriesUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onOpen: (CategoryRecord) -> Unit,
    onRetry: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(CategorySection.EXPENSE) }
    var period by rememberSaveable { mutableStateOf(CategoryPeriod.YEAR) }
    var query by rememberSaveable { mutableStateOf("") }
    val spend = when (section) {
        CategorySection.EXPENSE -> if (period == CategoryPeriod.MONTH) state.monthSpend else state.yearSpend
        CategorySection.INCOME -> if (period == CategoryPeriod.MONTH) state.monthIncome else state.yearIncome
    }
    val allParents = state.categories.filter {
        it.parentId == null && when (section) {
            CategorySection.EXPENSE -> it.kind == CategoryKind.EXPENSE || it.kind == CategoryKind.BOTH
            CategorySection.INCOME -> it.kind == CategoryKind.INCOME || it.kind == CategoryKind.BOTH
        }
    }.sortedForDisplay()
    val childrenByParent = state.categories.groupBy { it.parentId }
    // A parent's figure rolls up its own and its subcategories', as on its page.
    val parentCents = allParents.associate { parent ->
        parent.id to (spend[parent.id] ?: 0L) + childrenByParent[parent.id].orEmpty().sumOf { spend[it.id] ?: 0L }
    }
    val largest = parentCents.values.maxOrNull() ?: 0L
    val needle = query.trim()
    // A parent that matches shows all its subcategories; otherwise only the ones that match.
    val visible = allParents.mapNotNull { parent ->
        val children = childrenByParent[parent.id].orEmpty().sortedForDisplay()
        when {
            needle.isEmpty() || parent.name.contains(needle, ignoreCase = true) -> parent to children
            else -> children.filter { it.name.contains(needle, ignoreCase = true) }
                .takeIf { it.isNotEmpty() }?.let { parent to it }
        }
    }

    ListPage(
        title = stringResource(R.string.category_list_title),
        onBack = onBack,
        addLabel = stringResource(R.string.category_list_add),
        onAdd = onAdd,
        modifier = modifier,
    ) {
        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_categories,
                    onRetry = onRetry,
                )
            }
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.category_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@ListPage
        }

        state.totals?.let { totals ->
            item {
                CategoriesHero(
                    section = section,
                    period = period,
                    onPeriodChange = { period = it },
                    totalCents = when (section) {
                        CategorySection.EXPENSE ->
                            if (period == CategoryPeriod.MONTH) totals.monthExpenseCents else totals.yearExpenseCents
                        CategorySection.INCOME ->
                            if (period == CategoryPeriod.MONTH) totals.monthIncomeCents else totals.yearIncomeCents
                    },
                    top = allParents.filter { (parentCents[it.id] ?: 0L) > 0L }
                        .sortedByDescending { parentCents[it.id] }
                        .take(HERO_TOP_CATEGORIES)
                        .map { it to parentCents.getValue(it.id) },
                )
            }
        }

        item {
            ListFilterBar(
                query = query,
                onQueryChange = { query = it },
                searchPlaceholder = stringResource(R.string.category_search_placeholder),
            ) {
                CategorySection.entries.forEach { option ->
                    FinanceFilterChip(
                        selected = section == option,
                        label = stringResource(option.labelRes),
                        onClick = { section = option },
                    )
                }
            }
        }

        item {
            // One item, so the rows sit flush and read as one list between their dividers.
            Column {
                if (visible.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (needle.isEmpty()) R.string.category_empty_title else R.string.category_search_empty,
                        ),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val showUncategorized = section == CategorySection.EXPENSE && needle.isEmpty()
                visible.forEachIndexed { index, (parent, children) ->
                    val lastParent = index == visible.lastIndex && !showUncategorized
                    CategoryParentRow(
                        category = parent,
                        childCount = childrenByParent[parent.id].orEmpty().size,
                        cents = parentCents.getValue(parent.id),
                        largestCents = largest,
                        isLast = lastParent && children.isEmpty(),
                        onOpen = { onOpen(parent) },
                    )
                    children.forEachIndexed { childIndex, child ->
                        CategoryChildRow(
                            category = child,
                            cents = spend[child.id] ?: 0L,
                            isLast = lastParent && childIndex == children.lastIndex,
                            onOpen = { onOpen(child) },
                        )
                    }
                }
                if (showUncategorized) UncategorizedRow()
            }
        }
    }
}

private const val HERO_TOP_CATEGORIES = 3

private enum class CategorySection(val labelRes: Int) {
    EXPENSE(R.string.category_section_expense),
    INCOME(R.string.category_section_income),
}

private enum class CategoryPeriod(val labelRes: Int) {
    MONTH(R.string.category_period_month),
    YEAR(R.string.category_period_year),
}

/**
 * The period's canonical total on the forest hero (uncategorised included), its average, and a
 * bar of the biggest categories against the rest.
 */
@Composable
private fun CategoriesHero(
    section: CategorySection,
    period: CategoryPeriod,
    onPeriodChange: (CategoryPeriod) -> Unit,
    totalCents: Long,
    top: List<Pair<CategoryRecord, Long>>,
) {
    val colors = FinanceTheme.colors
    val today = LocalDate.now()
    ListHero(
        eyebrow = stringResource(
            when (section) {
                CategorySection.EXPENSE -> R.string.category_hero_expense
                CategorySection.INCOME -> R.string.category_hero_income
            },
        ) + " · " + when (period) {
            CategoryPeriod.MONTH -> formatMonthYear(YearMonth.from(today))
            CategoryPeriod.YEAR -> today.year.toString()
        },
        cents = totalCents,
        watermark = Icons.Outlined.Category,
        eyebrowTrailing = {
            HeroToggle(
                options = CategoryPeriod.entries,
                selected = period,
                label = { stringResource(it.labelRes) },
                onSelect = onPeriodChange,
            )
        },
    ) {
        HeroCaption(
            text = when (period) {
                CategoryPeriod.YEAR -> stringResource(
                    R.string.category_hero_per_month, formatEuroCents(totalCents / today.monthValue),
                )
                CategoryPeriod.MONTH -> stringResource(
                    R.string.category_hero_per_day, formatEuroCents(totalCents / today.dayOfMonth),
                )
            },
        )
        if (totalCents > 0L && top.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            val restCents = (totalCents - top.sumOf { it.second }).coerceAtLeast(0L)
            SegmentedDistributionBar(
                segments = top.map { (category, cents) ->
                    DistributionSegment(
                        color = heroIdentityColor(categoryColor(category.color)),
                        fraction = cents.toFloat() / totalCents,
                    )
                } + listOfNotNull(
                    DistributionSegment(
                        color = heroTint(colors.heroOnSurface, 0.22f),
                        fraction = restCents.toFloat() / totalCents,
                    ).takeIf { restCents > 0L },
                ),
                contentDescription = stringResource(R.string.category_hero_split_accessibility),
            )
            Spacer(modifier = Modifier.height(8.dp))
            HeroCaption(
                text = top.joinToString(" · ") { (category, cents) ->
                    "${category.name} ${percentLabel(cents, totalCents)}"
                },
            )
        }
    }
}

private fun percentLabel(part: Long, whole: Long): String {
    val percent = part * 100 / whole
    return if (percent < 1) "<1%" else "$percent%"
}

/**
 * A top-level category: its figure for the period, and under it a thin bar of that figure
 * against the section's largest, so the big ones stand out without reading every number.
 */
@Composable
private fun CategoryParentRow(
    category: CategoryRecord,
    childCount: Int,
    cents: Long,
    largestCents: Long,
    isLast: Boolean,
    onOpen: () -> Unit,
) {
    val color = themedIdentityColor(categoryColor(category.color))
    EntityListRow(
        leading = { IdentityIconTile(icon = categoryIcon(category.icon), color = categoryColor(category.color)) },
        title = category.name,
        subtitle = listOfNotNull(
            stringResource(R.string.category_nature_fixed).takeIf { category.nature == CategoryNature.FIXED },
            pluralStringResource(R.plurals.category_child_count, childCount, childCount).takeIf { childCount > 0 },
        ).joinToString(" · ").ifEmpty { null },
        footer = if (cents > 0L && largestCents > 0L) {
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(FinanceTheme.colors.progressTrack, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((cents.toFloat() / largestCents).coerceIn(0.02f, 1f))
                            .background(color, RoundedCornerShape(2.dp)),
                    )
                }
            }
        } else {
            null
        },
        isLast = isLast,
        onClick = onOpen,
        trailing = { CategoryFigure(cents = cents, emphasised = true) },
    )
}

/** A subcategory, indented under its parent with a smaller mark. */
@Composable
private fun CategoryChildRow(category: CategoryRecord, cents: Long, isLast: Boolean, onOpen: () -> Unit) {
    EntityListRow(
        leading = {
            Row {
                Spacer(modifier = Modifier.width(CHILD_INDENT))
                IdentityIconTile(icon = categoryIcon(category.icon), color = categoryColor(category.color), size = 32.dp)
            }
        },
        title = category.name,
        isLast = isLast,
        onClick = onOpen,
        dividerInset = CHILD_INDENT + 32.dp + 12.dp,
        trailing = { CategoryFigure(cents = cents, emphasised = false) },
    )
}

private val CHILD_INDENT = 16.dp

@Composable
private fun CategoryFigure(cents: Long, emphasised: Boolean) {
    if (cents <= 0L) return
    MoneyText(
        cents = cents,
        color = if (emphasised) MaterialTheme.colorScheme.onSurface else FinanceTheme.colors.mutedText,
        style = if (emphasised) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
    )
}

/** Movements without a category: explained, not a category you can open. */
@Composable
private fun UncategorizedRow() {
    EntityListRow(
        leading = { IdentityIconTile(icon = categoryIcon(null), color = categoryColor(null)) },
        title = stringResource(R.string.common_no_category),
        subtitle = stringResource(R.string.category_uncategorized_body),
        isLast = true,
        onClick = null,
    )
}

// ---------------------------------------------------------------------------
// Category form — bottom sheet
// ---------------------------------------------------------------------------

/** The category create/edit sheet over whichever category page opened it. */
@Composable
private fun CategoryFormSheet(state: CategoriesUiState, viewModel: CategoriesViewModel) {
    var appearanceOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-category" },
        changed = { initial, current -> initial.withoutErrors() != current.withoutErrors() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) R.string.category_form_new_title else R.string.category_form_edit_title) },
        saveLabel = { stringResource(if (it.id == null) R.string.category_save_new else R.string.category_save_changes) },
    ) { form ->
        val edit: (CategoryFormState) -> Unit = { viewModel.onFormChanged(it.withoutErrors()) }
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_category)
        }
        EntityFormHeader(
            icon = categoryIcon(form.iconKey),
            color = categoryColor(form.colorHex),
            name = form.name,
            onNameChange = { edit(form.copy(name = it)) },
            nameLabel = stringResource(R.string.category_field_name),
            onTileClick = { appearanceOpen = !appearanceOpen },
            nameError = form.errorRes?.takeIf { form.errorField == CategoryFormField.NAME }?.let { stringResource(it) },
            imeAction = ImeAction.Done,
            keyboardActions = doneKeyboardActions(viewModel::onSaveClicked),
        )
        EntityAppearancePickers(
            open = appearanceOpen,
            colorHex = form.colorHex,
            onColor = { edit(form.copy(colorHex = it)) },
            iconOptions = CategoryIconPalette,
            iconKey = form.iconKey,
            onIcon = { edit(form.copy(iconKey = it)) },
        )
        LabeledSegmentedControl(
            label = stringResource(R.string.category_field_kind),
            options = CategoryKind.entries,
            selected = form.kind,
            optionLabel = { it.shortLabel() },
            optionColor = {
                FinanceTheme.colors.amountColor(
                    when (it) {
                        CategoryKind.EXPENSE -> MovementType.EXPENSE
                        CategoryKind.INCOME -> MovementType.INCOME
                        CategoryKind.BOTH -> MovementType.TRANSFER
                    },
                )
            },
            // A parent of another kind would no longer fit.
            onSelect = { edit(form.copy(kind = it, parentId = null)) },
        )
        LabeledSegmentedControl(
            label = stringResource(R.string.category_field_nature),
            options = CategoryNature.entries,
            selected = form.nature,
            optionLabel = { it.label() },
            onSelect = { edit(form.copy(nature = it)) },
        )
        CategoryParentField(form = form, categories = state.categories, onParentSelected = { edit(form.copy(parentId = it)) })
    }
}

/** Where the category sits: at the top, or under a top-level category of a compatible kind. */
@Composable
private fun CategoryParentField(
    form: CategoryFormState,
    categories: List<CategoryRecord>,
    onParentSelected: (String?) -> Unit,
) {
    if (form.id != null && categories.any { it.parentId == form.id }) {
        Text(
            text = stringResource(R.string.category_parent_disabled_has_children),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val compatibleKinds = when (form.kind) {
        CategoryKind.EXPENSE -> setOf(CategoryKind.EXPENSE, CategoryKind.BOTH)
        CategoryKind.INCOME -> setOf(CategoryKind.INCOME, CategoryKind.BOTH)
        CategoryKind.BOTH -> setOf(CategoryKind.BOTH)
    }
    val parentError = form.errorField == CategoryFormField.PARENT
    FormSelect(
        label = stringResource(R.string.category_field_parent),
        options = listOf(SelectOption(id = null, label = stringResource(R.string.category_parent_none))) +
            categories
                .filter { it.parentId == null && it.id != form.id && it.kind in compatibleKinds }
                .sortedForDisplay()
                .map { SelectOption(id = it.id, label = it.name) },
        selectedId = form.parentId,
        onSelect = onParentSelected,
        isError = parentError,
        supportingText = form.errorRes?.takeIf { parentError }?.let { stringResource(it) },
        modifier = Modifier.scrollToWhen(parentError),
    )
}

private fun CategoryFormState.withoutErrors(): CategoryFormState =
    copy(errorRes = null, errorField = null, errorMessage = null)

// ---------------------------------------------------------------------------
// Category page
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFlowScreen(
    detail: CategoryFlowDetailState,
    state: CategoriesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenCategory: (categoryId: String) -> Unit,
    onViewAnalysis: () -> Unit,
    onBudget: () -> Unit,
    onAddMovement: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = detail.category
    val today = remember { LocalDate.now() }
    val children = state.categories.filter { it.parentId == category.id }.sortedForDisplay()
    val parent = category.parentId?.let { id -> state.categories.firstOrNull { it.id == id } }
    // A parent's figures take in its subcategories, as its row on the Categories page does.
    val monthCents = (state.monthSpend[category.id] ?: 0L) + children.sumOf { state.monthSpend[it.id] ?: 0L }
    val yearCents = (state.yearSpend[category.id] ?: 0L) + children.sumOf { state.yearSpend[it.id] ?: 0L }
    val budgetable = category.kind == CategoryKind.EXPENSE || category.kind == CategoryKind.BOTH

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            EntityDetailTopBar(
                onBack = onBack,
                menu = listOf(
                    EntityMenuAction(stringResource(R.string.common_edit), onEdit),
                    EntityMenuAction(stringResource(R.string.common_archive), onArchive, destructive = true),
                ),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            EntityDetailHeader(
                leading = {
                    IdentityIconTile(
                        icon = categoryIcon(category.icon),
                        color = categoryColor(category.color),
                        size = EntityHeaderMarkSize,
                    )
                },
                name = category.name,
                subtitle = listOfNotNull(
                    parent?.name ?: category.kind.label(),
                    stringResource(R.string.category_nature_fixed).takeIf { category.nature == CategoryNature.FIXED },
                ).joinToString(" · "),
                figures = {
                    EntityFigure(label = stringResource(R.string.category_spend_month)) {
                        MoneyText(cents = monthCents, style = MaterialTheme.typography.headlineLarge)
                    }
                    Box(modifier = Modifier.weight(1f))
                    EntityFigure(
                        label = stringResource(R.string.category_spend_year),
                        horizontalAlignment = Alignment.End,
                    ) {
                        MoneyText(cents = yearCents, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                details = detail.budgetEvaluation?.let { evaluation -> { EntityBudgetBar(evaluation) } },
                link = {
                    EntityActionPill(
                        text = stringResource(R.string.category_flow_view_analysis),
                        onClick = onViewAnalysis,
                        chevron = true,
                    )
                },
                actions = {
                    EntityActionPill(
                        text = stringResource(R.string.entity_add_movement),
                        onClick = onAddMovement,
                        icon = Icons.Outlined.Add,
                    )
                    if (budgetable) {
                        EntityActionPill(
                            text = stringResource(R.string.trip_action_budget),
                            onClick = onBudget,
                        )
                    }
                },
            )
        }
        if (children.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.category_detail_subcategories),
                        style = MaterialTheme.typography.labelLarge,
                        color = FinanceTheme.colors.mutedText,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        children.forEach { child ->
                            SubcategoryLink(child, onClick = { onOpenCategory(child.id) })
                        }
                    }
                }
            }
        }
        detail.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_categories,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        if (detail.entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.category_flow_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            dayGroupedRows(
                rows = detail.entries,
                dateOf = { it.date },
                key = { it.id },
                today = today,
            ) { movement, position ->
                MovementListItem(
                    movement = movement,
                    onClick = { onMovementDetail(movement) },
                    showDate = false,
                    position = position,
                )
            }
        }
    }
}

/** A subcategory as a small outlined pill with its own tile, opening its page. */
@Composable
private fun SubcategoryLink(category: CategoryRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, FinanceTheme.colors.cardBorder, CircleShape)
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IdentityIconTile(icon = categoryIcon(category.icon), color = categoryColor(category.color), size = 24.dp)
        Text(text = category.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun CategoryKind.label(): String =
    when (this) {
        CategoryKind.EXPENSE -> stringResource(R.string.category_kind_expense)
        CategoryKind.INCOME -> stringResource(R.string.category_kind_income)
        CategoryKind.BOTH -> stringResource(R.string.category_kind_both)
    }

/** Compact label so all three kinds fit a single segmented row. */
@Composable
private fun CategoryKind.shortLabel(): String =
    when (this) {
        CategoryKind.EXPENSE -> stringResource(R.string.category_kind_expense)
        CategoryKind.INCOME -> stringResource(R.string.category_kind_income)
        CategoryKind.BOTH -> stringResource(R.string.category_kind_both_short)
    }

@Composable
private fun CategoryNature.label(): String =
    when (this) {
        CategoryNature.FIXED -> stringResource(R.string.category_nature_fixed)
        CategoryNature.VARIABLE -> stringResource(R.string.category_nature_variable)
    }

private fun List<CategoryRecord>.sortedForDisplay(): List<CategoryRecord> =
    sortedByDisplayOrderThenName(displayOrder = { it.displayOrder }, name = { it.name })
