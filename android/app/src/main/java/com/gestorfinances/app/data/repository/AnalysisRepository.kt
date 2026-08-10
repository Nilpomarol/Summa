package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.AnalysisQueries
import java.time.YearMonth

data class AnalysisPeriodTotals(
    val netWorthCents: Long,
    val actualIncomeCents: Long,
    val actualExpenseCents: Long,
    val netActualCents: Long,
    val accountFlowCents: Long,
    val savingsRateBasisPoints: Long,
)

data class AnalysisCategoryTotal(
    val rowKind: AnalysisBreakdownKind = AnalysisBreakdownKind.CATEGORY,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val tripId: String? = null,
    val tripName: String? = null,
    val expenseCents: Long,
    val incomeCents: Long,
    val netCents: Long,
)

data class AnalysisIncomeExpenseBucket(
    val bucket: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val netCents: Long,
    val savingsRateBasisPoints: Long,
)

data class AnalysisAccountFlowBucket(
    val bucket: String,
    val accountId: String?,
    val accountName: String,
    val deltaCents: Long,
    val bucketDeltaCents: Long,
)

enum class AnalysisBucket(val queryValue: String) {
    DAY("day"),
    MONTH("month"),
    YEAR("year"),
}

enum class AnalysisOneTimeMode(val queryValue: String) {
    INCLUDE("include"),
    EXCLUDE("exclude"),
    ONLY("only"),
}

enum class AnalysisCategoryNature(val queryValue: String) {
    FIXED("fixed"),
    VARIABLE("variable"),
}

enum class AnalysisBreakdownKind {
    CATEGORY,
    TRIP,
    ;

    companion object {
        fun fromDb(value: String): AnalysisBreakdownKind =
            when (value) {
                "trip" -> TRIP
                else -> CATEGORY
            }
    }
}

class AnalysisRepository(
    private val queries: AnalysisQueries,
) {
    fun activityMonths(): List<YearMonth> =
        queries.activityMonths().executeAsList().mapNotNull { row ->
            row.month?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        }

    fun periodTotals(
        fromDate: String,
        toDate: String,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
        accountId: String? = null,
        categoryId: String? = null,
    ): AnalysisPeriodTotals =
        queries.analysisPeriodTotals(
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            account_id = accountId,
            category_id = categoryId,
            mapper = ::mapPeriodTotals,
        ).executeAsOne()

    fun actualBreakdown(
        fromDate: String,
        toDate: String,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
        groupTrips: Boolean = true,
        accountId: String? = null,
        categoryId: String? = null,
    ): List<AnalysisCategoryTotal> =
        queries.analysisActualBreakdown(
            from_date = fromDate,
            to_date = toDate,
            account_id = accountId,
            category_id = categoryId,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            group_trips = if (groupTrips) 1L else 0L,
            mapper = ::mapBreakdownTotal,
        ).executeAsList()

    fun actualByCategory(
        fromDate: String,
        toDate: String,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
        accountId: String? = null,
        categoryId: String? = null,
    ): List<AnalysisCategoryTotal> =
        queries.analysisActualByCategory(
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            account_id = accountId,
            category_id = categoryId,
            mapper = ::mapCategoryTotal,
        ).executeAsList()

    fun incomeVsExpense(
        fromDate: String,
        toDate: String,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
        bucket: AnalysisBucket = AnalysisBucket.DAY,
        accountId: String? = null,
        categoryId: String? = null,
    ): List<AnalysisIncomeExpenseBucket> =
        queries.analysisIncomeVsExpense(
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            account_id = accountId,
            category_id = categoryId,
            bucket = bucket.queryValue,
            mapper = ::mapIncomeExpenseBucket,
        ).executeAsList()

    fun accountFlowOverTime(
        fromDate: String,
        toDate: String,
        bucket: AnalysisBucket = AnalysisBucket.DAY,
        accountId: String? = null,
    ): List<AnalysisAccountFlowBucket> =
        queries.analysisAccountFlowOverTime(
            from_date = fromDate,
            to_date = toDate,
            account_id = accountId,
            bucket = bucket.queryValue,
            mapper = ::mapAccountFlowBucket,
        ).executeAsList()

}

private fun mapPeriodTotals(
    netWorthCents: Long,
    actualIncomeCents: Long,
    actualExpenseCents: Long,
    netActualCents: Long,
    accountFlowCents: Long,
    savingsRateBasisPoints: Long,
): AnalysisPeriodTotals =
    AnalysisPeriodTotals(
        netWorthCents = netWorthCents,
        actualIncomeCents = actualIncomeCents,
        actualExpenseCents = actualExpenseCents,
        netActualCents = netActualCents,
        accountFlowCents = accountFlowCents,
        savingsRateBasisPoints = savingsRateBasisPoints,
    )
private fun mapBreakdownTotal(
    rowKind: String,
    categoryId: String?,
    categoryName: String?,
    categoryKind: String?,
    categoryNature: String?,
    categoryIcon: String?,
    categoryColor: String?,
    tripId: String?,
    tripName: String?,
    expenseCents: Long?,
    incomeCents: Long?,
    netCents: Long?,
): AnalysisCategoryTotal =
    AnalysisCategoryTotal(
        rowKind = AnalysisBreakdownKind.fromDb(rowKind),
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        tripId = tripId,
        tripName = tripName,
        expenseCents = expenseCents ?: 0L,
        incomeCents = incomeCents ?: 0L,
        netCents = netCents ?: 0L,
    )

private fun mapCategoryTotal(
    categoryId: String?,
    categoryName: String?,
    categoryKind: String?,
    categoryNature: String?,
    categoryIcon: String?,
    categoryColor: String?,
    expenseCents: Long?,
    incomeCents: Long?,
    netCents: Long?,
): AnalysisCategoryTotal =
    AnalysisCategoryTotal(
        rowKind = AnalysisBreakdownKind.CATEGORY,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        expenseCents = expenseCents ?: 0L,
        incomeCents = incomeCents ?: 0L,
        netCents = netCents ?: 0L,
    )

private fun mapIncomeExpenseBucket(
    bucket: String,
    incomeCents: Long?,
    expenseCents: Long?,
    netCents: Long?,
    savingsRateBasisPoints: Long?,
): AnalysisIncomeExpenseBucket =
    AnalysisIncomeExpenseBucket(
        bucket = bucket,
        incomeCents = incomeCents ?: 0L,
        expenseCents = expenseCents ?: 0L,
        netCents = netCents ?: 0L,
        savingsRateBasisPoints = savingsRateBasisPoints ?: 0L,
    )

private fun mapAccountFlowBucket(
    bucket: String,
    accountId: String?,
    accountName: String,
    deltaCents: Long?,
    bucketDeltaCents: Long?,
): AnalysisAccountFlowBucket =
    AnalysisAccountFlowBucket(
        bucket = bucket,
        accountId = accountId,
        accountName = accountName,
        deltaCents = deltaCents ?: 0L,
        bucketDeltaCents = bucketDeltaCents ?: 0L,
    )
