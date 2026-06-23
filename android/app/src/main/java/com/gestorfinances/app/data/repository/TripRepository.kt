package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.TripsQueries

enum class TripType(val dbValue: String) {
    TRIP("trip"),
    CELEBRATION("celebration"),
    OTHER("other"),
    ;

    companion object {
        fun fromDb(value: String): TripType =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown trip type: $value")
    }
}

enum class TripStatus(val dbValue: String) {
    PLANNED("planned"),
    ACTIVE("active"),
    FINISHED("finished"),
    ;

    companion object {
        fun fromDb(value: String): TripStatus =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown trip status: $value")
    }
}

data class TripSummary(
    val id: String,
    val name: String,
    val type: TripType,
    val status: TripStatus,
    val startDate: String?,
    val endDate: String?,
    val icon: String?,
    val color: String?,
    val notes: String?,
    val defaultAccountId: String?,
    val defaultAccountName: String?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

data class TripDraft(
    val id: String,
    val name: String,
    val type: TripType,
    val status: TripStatus,
    val startDate: String?,
    val endDate: String?,
    val icon: String?,
    val color: String?,
    val notes: String?,
    val defaultAccountId: String?,
)

class TripRepository(
    private val queries: TripsQueries,
) {
    fun listActive(): List<TripSummary> =
        queries.activeTrips(::mapTripSummary).executeAsList()

    fun getActive(id: String): TripSummary? =
        queries.tripById(id, ::mapTripSummary).executeAsOneOrNull()

    fun create(
        draft: TripDraft,
        createdAt: String,
    ) {
        queries.insertTrip(
            id = draft.id,
            name = draft.name,
            type = draft.type.dbValue,
            status = draft.status.dbValue,
            start_date = draft.startDate,
            end_date = draft.endDate,
            icon = draft.icon,
            color = draft.color,
            notes = draft.notes,
            default_account_id = draft.defaultAccountId,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun update(
        draft: TripDraft,
        updatedAt: String,
    ) {
        queries.updateTrip(
            id = draft.id,
            name = draft.name,
            type = draft.type.dbValue,
            status = draft.status.dbValue,
            start_date = draft.startDate,
            end_date = draft.endDate,
            icon = draft.icon,
            color = draft.color,
            notes = draft.notes,
            default_account_id = draft.defaultAccountId,
            updated_at = updatedAt,
        )
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archiveTrip(
            id = id,
            archived_at = archivedAt,
            updated_at = archivedAt,
        )
    }
}

private fun mapTripSummary(
    id: String,
    name: String,
    type: String,
    status: String,
    startDate: String?,
    endDate: String?,
    icon: String?,
    color: String?,
    notes: String?,
    defaultAccountId: String?,
    defaultAccountName: String?,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
): TripSummary =
    TripSummary(
        id = id,
        name = name,
        type = TripType.fromDb(type),
        status = TripStatus.fromDb(status),
        startDate = startDate,
        endDate = endDate,
        icon = icon,
        color = color,
        notes = notes,
        defaultAccountId = defaultAccountId,
        defaultAccountName = defaultAccountName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )
