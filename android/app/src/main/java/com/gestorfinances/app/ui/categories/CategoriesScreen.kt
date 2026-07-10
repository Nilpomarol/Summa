package com.gestorfinances.app.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IconPickerRow
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.sortedByDisplayOrderThenName
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onViewAnalysis: (categoryId: String, categoryName: String) -> Unit = { _, _ -> },
    onDefineBudget: (categoryId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    val form = state.form
    val flowDetail = state.flowDetail
    when {
        form != null -> {
            BackHandler(onBack = viewModel::onFormDismissed)
            CategoryFormScreen(
                form = form,
                categories = state.categories,
                onFormChange = viewModel::onFormChanged,
                onBack = viewModel::onFormDismissed,
                onSave = viewModel::onSaveClicked,
                modifier = modifier,
            )
        }
        flowDetail != null -> {
            BackHandler(onBack = viewModel::onFlowDismissed)
            CategoryFlowScreen(
                detail = flowDetail,
                onBack = viewModel::onFlowDismissed,
                onViewAnalysis = {
                    viewModel.onFlowDismissed()
                    onViewAnalysis(flowDetail.category.id, flowDetail.category.name)
                },
                onDefineBudget = {
                    viewModel.onFlowDismissed()
                    onDefineBudget(flowDetail.category.id)
                },
                modifier = modifier,
            )
        }
        else -> {
            CategoriesContent(
                state = state,
                modifier = modifier,
                onAdd = viewModel::onAddClicked,
                onEdit = viewModel::onEditClicked,
                onArchive = viewModel::onArchiveClicked,
                onFlow = viewModel::onFlowClicked,
            )
        }
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.category_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.category_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onArchiveConfirmed) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onArchiveDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoriesContent(
    state: CategoriesUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (CategoryRecord) -> Unit,
    onArchive: (CategoryRecord) -> Unit,
    onFlow: (CategoryRecord) -> Unit,
) {
    val expenseParents = state.categories.filter {
        it.parentId == null && (it.kind == CategoryKind.EXPENSE || it.kind == CategoryKind.BOTH)
    }.sortedForDisplay()
    val incomeParents = state.categories.filter {
        it.parentId == null && it.kind == CategoryKind.INCOME
    }.sortedForDisplay()
    val childrenByParent = state.categories.groupBy { it.parentId }

    // Section totals roll up each parent's own spend + all its children's spend
    val expenseMonthTotal = expenseParents.sumOf { parent ->
        val ch = childrenByParent[parent.id].orEmpty()
        (state.monthSpend[parent.id] ?: 0L) + ch.sumOf { state.monthSpend[it.id] ?: 0L }
    }
    val expenseYearTotal = expenseParents.sumOf { parent ->
        val ch = childrenByParent[parent.id].orEmpty()
        (state.yearSpend[parent.id] ?: 0L) + ch.sumOf { state.yearSpend[it.id] ?: 0L }
    }
    val incomeMonthTotal = incomeParents.sumOf { parent ->
        val ch = childrenByParent[parent.id].orEmpty()
        (state.monthSpend[parent.id] ?: 0L) + ch.sumOf { state.monthSpend[it.id] ?: 0L }
    }
    val incomeYearTotal = incomeParents.sumOf { parent ->
        val ch = childrenByParent[parent.id].orEmpty()
        (state.yearSpend[parent.id] ?: 0L) + ch.sumOf { state.yearSpend[it.id] ?: 0L }
    }

    var expenseSectionExpanded by remember { mutableStateOf(true) }
    var incomeSectionExpanded by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Title + add button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.category_list_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TopBarIconButton(
                    icon = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.category_list_add),
                    onClick = onAdd,
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { UncategorizedCard() }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.category_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            // Expense section
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.category_section_expense),
                    count = expenseParents.size,
                    expanded = expenseSectionExpanded,
                    onToggle = { expenseSectionExpanded = !expenseSectionExpanded },
                )
            }
            if (expenseSectionExpanded) {
                if (expenseParents.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.category_empty_title),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(items = expenseParents, key = { "expense-${it.id}" }) { parent ->
                        val children = childrenByParent[parent.id].orEmpty().sortedForDisplay()
                        CategoryParentCard(
                            category = parent,
                            children = children,
                            monthSpend = state.monthSpend,
                            yearSpend = state.yearSpend,
                            sectionMonthTotal = expenseMonthTotal,
                            sectionYearTotal = expenseYearTotal,
                            onEdit = { onEdit(parent) },
                            onArchive = { onArchive(parent) },
                            onFlow = { onFlow(parent) },
                            onEditChild = onEdit,
                            onArchiveChild = onArchive,
                            onFlowChild = onFlow,
                        )
                    }
                }
            }

            // Income section
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.category_section_income),
                    count = incomeParents.size,
                    expanded = incomeSectionExpanded,
                    onToggle = { incomeSectionExpanded = !incomeSectionExpanded },
                )
            }
            if (incomeSectionExpanded) {
                if (incomeParents.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.category_empty_title),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    items(items = incomeParents, key = { "income-${it.id}" }) { parent ->
                        val children = childrenByParent[parent.id].orEmpty().sortedForDisplay()
                        CategoryParentCard(
                            category = parent,
                            children = children,
                            monthSpend = state.monthSpend,
                            yearSpend = state.yearSpend,
                            sectionMonthTotal = incomeMonthTotal,
                            sectionYearTotal = incomeYearTotal,
                            onEdit = { onEdit(parent) },
                            onArchive = { onArchive(parent) },
                            onFlow = { onFlow(parent) },
                            onEditChild = onEdit,
                            onArchiveChild = onArchive,
                            onFlowChild = onFlow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryParentCard(
    category: CategoryRecord,
    children: List<CategoryRecord>,
    monthSpend: Map<String, Long>,
    yearSpend: Map<String, Long>,
    sectionMonthTotal: Long,
    sectionYearTotal: Long,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onFlow: () -> Unit,
    onEditChild: (CategoryRecord) -> Unit,
    onArchiveChild: (CategoryRecord) -> Unit,
    onFlowChild: (CategoryRecord) -> Unit,
) {
    var childrenExpanded by remember { mutableStateOf(true) }
    val hasChildren = children.isNotEmpty()

    val monthLabel = stringResource(R.string.category_spend_month)
    val yearLabel = stringResource(R.string.category_spend_year)

    // Aggregate parent's own spend + all children's spend
    val monthAmt = (monthSpend[category.id] ?: 0L) + children.sumOf { monthSpend[it.id] ?: 0L }
    val yearAmt = (yearSpend[category.id] ?: 0L) + children.sumOf { yearSpend[it.id] ?: 0L }
    val spendAmt = if (monthAmt > 0L) monthAmt else if (yearAmt > 0L) yearAmt else null
    val spendLabel = when {
        monthAmt > 0L -> monthLabel
        yearAmt > 0L -> yearLabel
        else -> null
    }
    val sectionTotal = if (monthAmt > 0L) sectionMonthTotal else sectionYearTotal
    val fraction = if (spendAmt != null && sectionTotal > 0L) {
        (spendAmt.toFloat() / sectionTotal.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val percentText = when {
        fraction <= 0f -> null
        fraction < 0.01f -> "<1%"
        else -> "${(fraction * 100).toInt()}%"
    }
    val catColor = categoryColor(category.color)

    // Secondary line: kind + optional child count, e.g. "Despesa · 3 subcategories"
    val kindLabel = category.kind.label()
    val secondaryLine = if (hasChildren) {
        "$kindLabel · " + pluralStringResource(
            R.plurals.category_child_count,
            children.size,
            children.size,
        )
    } else {
        kindLabel
    }

    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFlow),
    ) {
        Column {
            // Main row: icon + name/meta + expand + menu (icon centers against the 2-line text)
            Row(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 4.dp,
                    top = 14.dp,
                    bottom = if (spendAmt != null) 0.dp else 14.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(
                    icon = categoryIcon(category.icon),
                    contentDescription = null,
                    color = catColor,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (category.nature == CategoryNature.FIXED) {
                            FixedNatureTag()
                        }
                    }
                    Text(
                        text = secondaryLine,
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (hasChildren) {
                    IconButton(onClick = { childrenExpanded = !childrenExpanded }) {
                        Icon(
                            imageVector = if (childrenExpanded) Icons.Outlined.ExpandLess
                                         else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = FinanceTheme.colors.mutedText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                CategoryMenuDropdown(onEdit = onEdit, onArchive = onArchive)
            }

            // Spend block: amount + period on the left, bar fills, percentage at the end
            if (spendAmt != null && spendLabel != null) {
                Row(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 14.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        MoneyText(
                            cents = spendAmt,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = spendLabel,
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(3.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(catColor, RoundedCornerShape(3.dp)),
                        )
                    }
                    if (percentText != null) {
                        Text(
                            text = percentText,
                            style = MaterialTheme.typography.labelSmall,
                            color = FinanceTheme.colors.mutedText,
                        )
                    }
                }
            }

            // Children
            if (hasChildren && childrenExpanded) {
                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                children.forEachIndexed { index, child ->
                    CategoryChildRow(
                        category = child,
                        parentName = category.name,
                        monthSpend = monthSpend,
                        yearSpend = yearSpend,
                        monthLabel = monthLabel,
                        yearLabel = yearLabel,
                        onEdit = { onEditChild(child) },
                        onArchive = { onArchiveChild(child) },
                        onFlow = { onFlowChild(child) },
                    )
                    if (index < children.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 58.dp),
                            color = FinanceTheme.colors.cardBorder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedNatureTag() {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = FinanceTheme.colors.mutedText,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
            )
            Text(
                text = stringResource(R.string.category_nature_fixed),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CategoryChildRow(
    category: CategoryRecord,
    parentName: String,
    monthSpend: Map<String, Long>,
    yearSpend: Map<String, Long>,
    monthLabel: String,
    yearLabel: String,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onFlow: () -> Unit,
) {
    val monthAmt = monthSpend[category.id] ?: 0L
    val yearAmt = yearSpend[category.id] ?: 0L
    val (spendAmt, spendLabel) = when {
        monthAmt > 0L -> monthAmt to monthLabel
        yearAmt > 0L -> yearAmt to yearLabel
        else -> null to null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFlow)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        IconChip(
            icon = categoryIcon(category.icon),
            contentDescription = null,
            color = categoryColor(category.color),
            size = 32.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = parentName,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (spendAmt != null && spendLabel != null) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                MoneyText(
                    cents = spendAmt,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = spendLabel,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        CategoryMenuDropdown(onEdit = onEdit, onArchive = onArchive)
    }
}

@Composable
private fun UncategorizedCard() {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(
                icon = categoryIcon(null),
                contentDescription = null,
                color = categoryColor(null),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.common_no_category),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.category_uncategorized_body),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CategoryMenuDropdown(
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
                tint = FinanceTheme.colors.mutedText,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                onClick = { menuExpanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.common_archive),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { menuExpanded = false; onArchive() },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Category form — bottom sheet
// ---------------------------------------------------------------------------

@Composable
private fun CategoryFormScreen(
    form: CategoryFormState,
    categories: List<CategoryRecord>,
    onFormChange: (CategoryFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(
                if (form.id == null) R.string.category_form_new_title
                else R.string.category_form_edit_title,
            ),
        )

        form.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Live preview
        CategoryPreviewCard(form = form)

        // Name
        val nameError = form.errorField == CategoryFormField.NAME
        OutlinedTextField(
            value = form.name,
            onValueChange = {
                onFormChange(form.copy(name = it, errorRes = null, errorField = null, errorMessage = null))
            },
            label = { Text(text = stringResource(R.string.category_field_name)) },
            singleLine = true,
            isError = nameError,
            supportingText = if (nameError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(nameError),
        )

        // Kind selector
        LabeledSegmentedControl(
            label = stringResource(R.string.category_field_kind),
            options = CategoryKind.entries,
            selected = form.kind,
            optionLabel = { it.shortLabel() },
            onSelect = { kind ->
                onFormChange(
                    form.copy(kind = kind, parentId = null, errorRes = null, errorField = null, errorMessage = null),
                )
            },
        )

        // Nature selector
        LabeledSegmentedControl(
            label = stringResource(R.string.category_field_nature),
            options = CategoryNature.entries,
            selected = form.nature,
            optionLabel = { it.label() },
            onSelect = { nature ->
                onFormChange(form.copy(nature = nature, errorRes = null, errorField = null, errorMessage = null))
            },
        )

        // Color picker
        ColorPickerRow(
            label = stringResource(R.string.category_field_color),
            selectedHex = form.colorHex,
            onSelect = { onFormChange(form.copy(colorHex = it)) },
        )

        // Icon picker
        IconPickerRow(
            label = stringResource(R.string.category_field_icon),
            options = CategoryIconPalette,
            selectedKey = form.iconKey,
            onSelect = { onFormChange(form.copy(iconKey = it)) },
        )

        // Parent picker
        val parentError = form.errorField == CategoryFormField.PARENT
        CategoryParentPicker(
            form = form,
            categories = categories,
            onParentSelected = {
                onFormChange(form.copy(parentId = it, errorRes = null, errorField = null, errorMessage = null))
            },
            modifier = Modifier.scrollToWhen(parentError),
        )
        if (parentError && form.errorRes != null) {
            Text(
                text = stringResource(form.errorRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
            PrimaryButton(
                text = stringResource(
                    if (form.id == null) R.string.category_save_new
                    else R.string.category_save_changes,
                ),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryPreviewCard(form: CategoryFormState) {
    val color = categoryColor(form.colorHex)
    val icon = categoryIcon(form.iconKey)
    val nameText = form.name.ifBlank { stringResource(R.string.category_preview_placeholder) }

    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(icon = icon, contentDescription = null, color = color)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nameText,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (form.name.isBlank()) {
                        FinanceTheme.colors.mutedText
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = "${form.kind.label()} · ${form.nature.label()}",
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CategoryParentPicker(
    form: CategoryFormState,
    categories: List<CategoryRecord>,
    onParentSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActiveChildren = form.id != null && categories.any { it.parentId == form.id }

    if (hasActiveChildren) {
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
    val parentOptions = categories.filter {
        it.parentId == null && it.id != form.id && it.kind in compatibleKinds
    }

    var query by remember { mutableStateOf("") }
    val showSearch = parentOptions.size > 5
    val filtered = remember(query, parentOptions) {
        if (query.isBlank()) parentOptions
        else parentOptions.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.category_field_parent),
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(text = stringResource(R.string.category_parent_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Bordered, non-scrolling list — the whole sheet scrolls as one surface.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
        ) {
            Column {
                // "No parent" option, always first
                ParentOptionRow(
                    label = stringResource(R.string.category_parent_none),
                    icon = null,
                    iconColor = null,
                    selected = form.parentId == null,
                    onClick = { onParentSelected(null) },
                )
                if (query.isNotBlank() && filtered.isEmpty()) {
                    HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                    Text(
                        text = stringResource(R.string.category_parent_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.mutedText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                } else {
                    filtered.forEach { parent ->
                        HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                        ParentOptionRow(
                            label = parent.name,
                            icon = categoryIcon(parent.icon),
                            iconColor = categoryColor(parent.color),
                            selected = form.parentId == parent.id,
                            onClick = { onParentSelected(parent.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentOptionRow(
    label: String,
    icon: ImageVector?,
    iconColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null && iconColor != null) {
                IconChip(
                    icon = icon,
                    contentDescription = null,
                    color = iconColor,
                    size = 32.dp,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category flow sheet
// ---------------------------------------------------------------------------

@Composable
private fun CategoryFlowScreen(
    detail: CategoryFlowDetailState,
    onBack: () -> Unit,
    onViewAnalysis: () -> Unit,
    onDefineBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = detail.category

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        // Header: back + icon + name/count + analysis button
        val movementCountText = if (!detail.isLoading) {
            pluralStringResource(
                R.plurals.account_flow_movement_count,
                detail.entries.size,
                detail.entries.size,
            )
        } else null

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            IconChip(
                icon = categoryIcon(category.icon),
                contentDescription = null,
                color = categoryColor(category.color),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (movementCountText != null) {
                    Text(
                        text = movementCountText,
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TextButton(onClick = onViewAnalysis) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.category_flow_view_analysis))
            }
        }

        detail.budgetEvaluation?.let { evaluation ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BudgetProgressBar(
                    fraction = evaluation.progressFraction(),
                    color = evaluation.status.color(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.budget_progress,
                            formatEuroCents(evaluation.actualCents),
                            formatEuroCents(evaluation.budget.limitAmountCents),
                        ),
                        modifier = Modifier.weight(1f),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
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

        if (detail.budgetEvaluation == null &&
            (category.kind == CategoryKind.EXPENSE || category.kind == CategoryKind.BOTH)
        ) {
            TextButton(
                onClick = onDefineBudget,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(text = stringResource(R.string.category_flow_define_budget))
            }
        }

        HorizontalDivider()

        // Movement list
        when {
            detail.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.category_flow_loading),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            detail.entries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.category_flow_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    items(detail.entries) { movement ->
                        MovementListItem(movement = movement)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        detail.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
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
