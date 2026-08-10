package com.gestorfinances.app.ui.common

import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.CategoryRecord
import kotlin.math.abs

/** Maps a category id to its container id (its parent's id if the parent is active, else itself). */
private fun effectiveCategoryId(categoryId: String, categoriesById: Map<String, CategoryRecord>): String =
    categoriesById[categoryId]?.parentId?.takeIf { it in categoriesById } ?: categoryId

/**
 * Orders categories for a two-level picker: each top-level category immediately followed by its
 * active children. The [Boolean] is `true` for a child row, so callers can indent it under its
 * parent. A category whose parent is absent from this list is treated as top-level so it is never
 * dropped. Selectability (whether a container header is pickable) is left to the caller.
 */
fun List<CategoryRecord>.inPickerHierarchyOrder(): List<Pair<CategoryRecord, Boolean>> {
    val presentIds = mapTo(HashSet()) { it.id }
    val childrenByParent = filter { it.parentId != null && it.parentId in presentIds }
        .groupBy { it.parentId }
    val topLevel = filter { it.parentId == null || it.parentId !in presentIds }
        .sortedByDisplayOrderThenName({ it.displayOrder }, { it.name })
    return buildList {
        topLevel.forEach { parent ->
            add(parent to false)
            childrenByParent[parent.id].orEmpty()
                .sortedByDisplayOrderThenName({ it.displayOrder }, { it.name })
                .forEach { add(it to true) }
        }
    }
}
/**
 * Rolls each child category's totals up into its parent (container) row, so a parent appears
 * once with its own spend plus all of its children's. Trip rows and the uncategorized bucket
 * (`categoryId == null`) pass through untouched. A container with spend only via its children
 * still renders correctly because the merged row takes the parent's name/icon/color from the
 * category list. Rolling up is done here in app code because the breakdown query intentionally
 * returns one row per category so both the parent total and the individual children stay visible.
 *
 * A child whose parent is not in [categoriesById] (e.g. an archived parent) is left standalone.
 * The result is ordered by magnitude then name, mirroring the analysis breakdown query.
 */
@JvmName("rollUpCategoryTotals")
fun List<AnalysisCategoryTotal>.rollUpToParents(
    categoriesById: Map<String, CategoryRecord>,
): List<AnalysisCategoryTotal> {
    val merged = LinkedHashMap<String, AnalysisCategoryTotal>()
    val passthrough = mutableListOf<AnalysisCategoryTotal>()
    for (row in this) {
        val categoryId = row.categoryId
        if (row.rowKind == AnalysisBreakdownKind.TRIP || categoryId == null) {
            passthrough += row
            continue
        }
        val effectiveId = effectiveCategoryId(categoryId, categoriesById)
        val existing = merged[effectiveId]
        merged[effectiveId] = if (existing == null) {
            val identity = categoriesById[effectiveId]
            row.copy(
                categoryId = effectiveId,
                categoryName = identity?.name ?: row.categoryName,
                categoryIcon = identity?.icon ?: row.categoryIcon,
                categoryColor = identity?.color ?: row.categoryColor,
            )
        } else {
            existing.copy(
                expenseCents = existing.expenseCents + row.expenseCents,
                incomeCents = existing.incomeCents + row.incomeCents,
                netCents = existing.netCents + row.netCents,
            )
        }
    }
    return (passthrough + merged.values).sortedWith(
        compareByDescending<AnalysisCategoryTotal> { abs(it.expenseCents) + abs(it.incomeCents) }
            .thenBy { (it.tripName ?: it.categoryName ?: "").lowercase() },
    )
}
