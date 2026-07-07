package com.gestorfinances.app.data.repository

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.gestorfinances.app.R
import com.gestorfinances.app.data.db.TripsQueries
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

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

/** Catalan label for a trip event type, shared by the Trips, Tags, and Dashboard screens. */
@Composable
fun TripType.label(): String =
    stringResource(
        when (this) {
            TripType.TRIP -> R.string.trip_type_trip
            TripType.CELEBRATION -> R.string.trip_type_celebration
            TripType.OTHER -> R.string.trip_type_other
        },
    )

/** Icon for a trip event type, shared by the Trips and Dashboard screens. */
fun TripType.icon(): ImageVector =
    when (this) {
        TripType.TRIP -> Icons.Outlined.Flight
        TripType.CELEBRATION -> Icons.Outlined.Celebration
        TripType.OTHER -> Icons.Outlined.Event
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
    val totalActualCents: Long,
)

/**
 * Number of calendar days the trip spans, for avg/day figures. Falls back to the range
 * covered by [fallbackStart]/[fallbackEnd] (e.g. the trip's actual-spend day range) when
 * either boundary date is missing, and finally to a single day when nothing is known.
 */
fun TripSummary.dayCount(
    fallbackStart: String? = null,
    fallbackEnd: String? = null,
): Long {
    val start = startDate?.let(::parseIsoDateOrNull) ?: fallbackStart?.let(::parseIsoDateOrNull)
    val end = endDate?.let(::parseIsoDateOrNull) ?: fallbackEnd?.let(::parseIsoDateOrNull) ?: start
    if (start == null || end == null || end < start) return 1L
    return ChronoUnit.DAYS.between(start, end).coerceAtLeast(0L) + 1L
}

private fun parseIsoDateOrNull(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }

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

    fun activeToday(date: String): TripSummary? =
        queries.tripActiveOn(date, ::mapTripSummary).executeAsOneOrNull()

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
    totalActualCents: Long,
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
        totalActualCents = totalActualCents,
    )
