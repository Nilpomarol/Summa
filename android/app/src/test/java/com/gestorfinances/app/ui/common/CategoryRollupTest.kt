package com.gestorfinances.app.ui.common

import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit coverage for the parent/child rollup helpers used by the Dashboard and Analysis screens. */
class CategoryRollupTest {

    private fun category(id: String, name: String, parentId: String? = null) = CategoryRecord(
        id = id,
        name = name,
        kind = CategoryKind.EXPENSE,
        nature = CategoryNature.VARIABLE,
        parentId = parentId,
        icon = null,
        color = null,
        displayOrder = 0,
        createdAt = "t",
        updatedAt = "t",
        archivedAt = null,
    )

    private fun total(categoryId: String?, name: String?, expense: Long) = AnalysisCategoryTotal(
        categoryId = categoryId,
        categoryName = name,
        categoryIcon = null,
        categoryColor = null,
        expenseCents = expense,
        incomeCents = 0,
        netCents = -expense,
    )

    private val categoriesById = listOf(
        category("food", "Food"),
        category("restaurants", "Restaurants", parentId = "food"),
        category("groceries", "Groceries", parentId = "food"),
        category("transport", "Transport"),
    ).associateBy { it.id }

    @Test
    fun rollsChildrenIntoTheirParent() {
        val rows = listOf(
            total("food", "Food", 1000),
            total("restaurants", "Restaurants", 3000),
            total("groceries", "Groceries", 2000),
            total("transport", "Transport", 500),
        )

        val rolled = rows.rollUpToParents(categoriesById)

        // Children collapse into 'food'; 'transport' stays on its own; no child rows survive.
        assertEquals(setOf("food", "transport"), rolled.mapNotNull { it.categoryId }.toSet())
        val food = rolled.first { it.categoryId == "food" }
        assertEquals(6000, food.expenseCents)
        assertEquals("Food", food.categoryName)
        assertEquals(500, rolled.first { it.categoryId == "transport" }.expenseCents)
    }

    @Test
    fun synthesizesParentRowWhenOnlyChildrenHaveSpend() {
        val rows = listOf(
            total("restaurants", "Restaurants", 3000),
            total("groceries", "Groceries", 2000),
        )

        val rolled = rows.rollUpToParents(categoriesById)

        assertEquals(1, rolled.size)
        assertEquals("food", rolled.single().categoryId)
        assertEquals("Food", rolled.single().categoryName)
        assertEquals(5000, rolled.single().expenseCents)
    }

    @Test
    fun leavesUncategorizedBucketUntouched() {
        val rows = listOf(
            total(null, null, 900),
            total("restaurants", "Restaurants", 100),
        )

        val rolled = rows.rollUpToParents(categoriesById)

        assertTrue(rolled.any { it.categoryId == null && it.expenseCents == 900L })
        assertTrue(rolled.any { it.categoryId == "food" && it.expenseCents == 100L })
    }

    @Test
    fun pickerHierarchyOrdersChildrenUnderParentsAndFlagsIndent() {
        val ordered = categoriesById.values.toList().inPickerHierarchyOrder()

        val ids = ordered.map { it.first.id }
        // Parent precedes its children (children sorted by name); each child is flagged indented,
        // parents are not.
        assertEquals(listOf("food", "groceries", "restaurants", "transport"), ids)
        assertFalse(ordered.first { it.first.id == "food" }.second)
        assertTrue(ordered.first { it.first.id == "restaurants" }.second)
        assertFalse(ordered.first { it.first.id == "transport" }.second)
    }
}
