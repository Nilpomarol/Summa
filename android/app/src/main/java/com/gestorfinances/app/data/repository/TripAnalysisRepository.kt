package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.TripAnalysisQueries

data class TripAnalysisSummary(
    val actualCents: Long,
    val accountOutflowCents: Long,
)

data class TripDailyActual(
    val date: String,
    val actualCents: Long,
)

data class TripCategoryActual(
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val actualCents: Long,
)

data class TripTagActual(
    val tagId: String?,
    val tagName: String?,
    val tagColor: String?,
    val actualCents: Long,
)

class TripAnalysisRepository(
    private val queries: TripAnalysisQueries,
) {
    fun summary(tripId: String): TripAnalysisSummary =
        queries.tripAnalysisSummary(trip_id = tripId, mapper = ::mapSummary).executeAsOne()

    fun actualByDay(tripId: String): List<TripDailyActual> =
        queries.tripActualByDay(trip_id = tripId, mapper = ::mapDailyActual).executeAsList()

    fun actualByCategory(tripId: String): List<TripCategoryActual> =
        queries.tripActualByCategory(trip_id = tripId, mapper = ::mapCategoryActual).executeAsList()

    fun actualByTag(tripId: String): List<TripTagActual> =
        queries.tripActualByTag(trip_id = tripId, mapper = ::mapTagActual).executeAsList()
}

private fun mapSummary(
    actualCents: Long?,
    flowCents: Long?,
): TripAnalysisSummary =
    TripAnalysisSummary(
        actualCents = actualCents ?: 0L,
        accountOutflowCents = flowCents ?: 0L,
    )

private fun mapDailyActual(
    date: String?,
    actualCents: Long?,
): TripDailyActual =
    TripDailyActual(
        date = date.orEmpty(),
        actualCents = actualCents ?: 0L,
    )

private fun mapCategoryActual(
    categoryId: String?,
    categoryName: String?,
    categoryIcon: String?,
    categoryColor: String?,
    actualCents: Long?,
): TripCategoryActual =
    TripCategoryActual(
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        actualCents = actualCents ?: 0L,
    )

private fun mapTagActual(
    tagId: String?,
    tagName: String?,
    tagColor: String?,
    actualCents: Long?,
): TripTagActual =
    TripTagActual(
        tagId = tagId,
        tagName = tagName,
        tagColor = tagColor,
        actualCents = actualCents ?: 0L,
    )
