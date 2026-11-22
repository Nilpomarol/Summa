package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.TagsQueries

data class TagSummary(
    val id: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val tripId: String?,
    val tripName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val tripType: TripType?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

/** The tag's own icon if set, else the icon of its associated category, else null. */
fun TagSummary.effectiveIcon(): String? = icon ?: categoryIcon

/** The tag's own color if set, else the color of its associated category, else null. */
fun TagSummary.effectiveColor(): String? = color ?: categoryColor

data class TagDraft(
    val id: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val tripId: String?,
    val categoryId: String? = null,
    val tripType: TripType? = null,
)

class TagRepository(
    private val queries: TagsQueries,
) {
    fun listActive(): List<TagSummary> =
        queries.activeTags(::mapTagSummary).executeAsList()

    fun getActive(id: String): TagSummary? =
        queries.tagById(id, ::mapTagSummary).executeAsOneOrNull()

    fun create(
        draft: TagDraft,
        createdAt: String,
    ) {
        queries.insertTag(
            id = draft.id,
            name = draft.name,
            icon = draft.icon,
            color = draft.color,
            trip_id = draft.tripId,
            category_id = draft.categoryId,
            trip_type = draft.tripType?.dbValue,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun update(
        draft: TagDraft,
        updatedAt: String,
    ) {
        queries.updateTag(
            id = draft.id,
            name = draft.name,
            icon = draft.icon,
            color = draft.color,
            trip_id = draft.tripId,
            category_id = draft.categoryId,
            trip_type = draft.tripType?.dbValue,
            updated_at = updatedAt,
        )
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archiveTag(
            id = id,
            archived_at = archivedAt,
            updated_at = archivedAt,
        )
    }

    fun restore(id: String, deletedAt: String, restoredAt: String) {
        queries.restoreTag(id = id, archived_at = deletedAt, updated_at = restoredAt)
    }
}

private fun mapTagSummary(
    id: String,
    name: String,
    icon: String?,
    color: String?,
    tripId: String?,
    tripName: String?,
    categoryId: String?,
    categoryName: String?,
    categoryIcon: String?,
    categoryColor: String?,
    tripType: String?,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
): TagSummary =
    TagSummary(
        id = id,
        name = name,
        icon = icon,
        color = color,
        tripId = tripId,
        tripName = tripName,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        tripType = tripType?.let(TripType::fromDb),
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )
