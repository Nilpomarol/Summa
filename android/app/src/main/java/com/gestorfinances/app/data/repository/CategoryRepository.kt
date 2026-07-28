package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.CategoriesQueries

enum class CategoryKind(val dbValue: String) {
    EXPENSE("expense"),
    INCOME("income"),
    BOTH("both"),
    ;

    companion object {
        fun fromDb(value: String): CategoryKind =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown category kind: $value")
    }
}

enum class CategoryNature(val dbValue: String) {
    FIXED("fixed"),
    VARIABLE("variable"),
    ;

    companion object {
        fun fromDb(value: String): CategoryNature =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown category nature: $value")
    }
}

data class CategoryRecord(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val nature: CategoryNature,
    val parentId: String?,
    val icon: String?,
    val color: String?,
    val displayOrder: Long,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

val CategoryRecord.supportsExpense: Boolean
    get() = kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH

val CategoryRecord.supportsIncome: Boolean
    get() = kind == CategoryKind.INCOME || kind == CategoryKind.BOTH

fun CategoryRecord.supports(type: MovementType): Boolean =
    when (type) {
        MovementType.EXPENSE, MovementType.EXTERNAL_EXPENSE -> supportsExpense
        MovementType.INCOME -> supportsIncome
        MovementType.TRANSFER, MovementType.SETTLEMENT, MovementType.REFUND -> false
    }

data class CategoryDraft(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val nature: CategoryNature,
    val parentId: String?,
    val icon: String?,
    val color: String?,
    val displayOrder: Long,
)

class CategoryRepository(
    private val queries: CategoriesQueries,
) {
    fun listActive(): List<CategoryRecord> =
        queries.activeCategories(::mapCategoryRecord).executeAsList()

    fun getActive(id: String): CategoryRecord? =
        queries.categoryById(id, ::mapCategoryRecord).executeAsOneOrNull()

    fun create(
        draft: CategoryDraft,
        createdAt: String,
    ) {
        queries.insertCategory(
            id = draft.id,
            name = draft.name,
            kind = draft.kind.dbValue,
            nature = draft.nature.dbValue,
            parent_id = draft.parentId,
            icon = draft.icon,
            color = draft.color,
            display_order = draft.displayOrder,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun createAll(
        drafts: List<CategoryDraft>,
        createdAt: String,
    ) {
        queries.transaction {
            drafts.forEach { draft ->
                queries.insertCategory(
                    id = draft.id,
                    name = draft.name,
                    kind = draft.kind.dbValue,
                    nature = draft.nature.dbValue,
                    parent_id = draft.parentId,
                    icon = draft.icon,
                    color = draft.color,
                    display_order = draft.displayOrder,
                    created_at = createdAt,
                    updated_at = createdAt,
                )
            }
        }
    }

    fun update(
        draft: CategoryDraft,
        updatedAt: String,
    ) {
        queries.updateCategory(
            id = draft.id,
            name = draft.name,
            kind = draft.kind.dbValue,
            nature = draft.nature.dbValue,
            parent_id = draft.parentId,
            icon = draft.icon,
            color = draft.color,
            display_order = draft.displayOrder,
            updated_at = updatedAt,
        )
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archiveCategory(
            id = id,
            archived_at = archivedAt,
            updated_at = archivedAt,
        )
    }
}

private fun mapCategoryRecord(
    id: String,
    name: String,
    kind: String,
    nature: String,
    parentId: String?,
    icon: String?,
    color: String?,
    displayOrder: Long,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
): CategoryRecord =
    CategoryRecord(
        id = id,
        name = name,
        kind = CategoryKind.fromDb(kind),
        nature = CategoryNature.fromDb(nature),
        parentId = parentId,
        icon = icon,
        color = color,
        displayOrder = displayOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )
