package com.gestorfinances.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryCompatibilityTest {
    @Test
    fun `category compatibility matches the movement type matrix`() {
        val categories = listOf(
            category(CategoryKind.EXPENSE),
            category(CategoryKind.INCOME),
            category(CategoryKind.BOTH),
        )
        val expected = mapOf(
            MovementType.EXPENSE to listOf(true, false, true),
            MovementType.EXTERNAL_EXPENSE to listOf(true, false, true),
            MovementType.INCOME to listOf(false, true, true),
            MovementType.TRANSFER to listOf(false, false, false),
            MovementType.SETTLEMENT to listOf(false, false, false),
            MovementType.REFUND to listOf(false, false, false),
        )

        expected.forEach { (type, supported) ->
            assertEquals(type.name, supported, categories.map { it.supports(type) })
        }
    }

    private fun category(kind: CategoryKind) = CategoryRecord(
        id = kind.name,
        name = kind.name,
        kind = kind,
        nature = CategoryNature.VARIABLE,
        icon = null,
        color = null,
        parentId = null,
        displayOrder = 0,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        archivedAt = null,
    )
}
