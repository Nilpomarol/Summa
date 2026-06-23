package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.TagsQueries

data class TagSummary(
    val id: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val tripId: String?,
    val tripName: String?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

data class TagDraft(
    val id: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val tripId: String?,
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
}

private fun mapTagSummary(
    id: String,
    name: String,
    icon: String?,
    color: String?,
    tripId: String?,
    tripName: String?,
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
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )
