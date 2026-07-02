package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.AnalysisQueries

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

data class AnalysisLargestExpense(
    val sourceId: String,
    val date: String,
    val label: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val amountCents: Long,
)

data class AnalysisMerchantTotal(
    val merchantLabel: String?,
    val totalCents: Long,
    val movementCount: Long,
)

data class AnalysisCategoryTrendPoint(
    val categoryId: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val bucket: String,
    val expenseCents: Long,
)

data class AnalysisNetWorthPoint(
    val bucket: String,
    val netWorthCents: Long,
)

/** Total actual expense for a single weekday (0 = Sunday … 6 = Saturday), net of refunds. */
data class AnalysisWeekdaySpend(
    val weekday: Int,
    val expenseCents: Long,
)

/** Per-category purchase count and spend volume, for the frequency-vs-volume scatter. */
data class AnalysisCategoryFrequency(
    val categoryId: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val movementCount: Long,
    val totalCents: Long,
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

    fun dailyIncomeVsExpense(
        fromDate: String,
        toDate: String,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
    ): List<AnalysisIncomeExpenseBucket> =
        incomeVsExpense(
            fromDate = fromDate,
            toDate = toDate,
            oneTimeMode = oneTimeMode,
            categoryNature = categoryNature,
            bucket = AnalysisBucket.DAY,
        )

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

    fun weekdaySpend(
        fromDate: String,
        toDate: String,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
        accountId: String? = null,
        categoryId: String? = null,
    ): List<AnalysisWeekdaySpend> =
        queries.analysisWeekdaySpend(
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            account_id = accountId,
            category_id = categoryId,
            mapper = ::mapWeekdaySpend,
        ).executeAsList()

    fun categoryFrequency(
        fromDate: String,
        toDate: String,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
        accountId: String? = null,
        categoryId: String? = null,
    ): List<AnalysisCategoryFrequency> =
        queries.analysisCategoryFrequency(
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            account_id = accountId,
            category_id = categoryId,
            mapper = ::mapCategoryFrequency,
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

    fun largestExpenses(
        fromDate: String,
        toDate: String,
        limit: Long = WIDGET_LIMIT,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
    ): List<AnalysisLargestExpense> =
        queries.analysisLargestExpenses(
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            limit = limit,
            mapper = ::mapLargestExpense,
        ).executeAsList()

    fun topMerchants(
        fromDate: String,
        toDate: String,
        limit: Long = WIDGET_LIMIT,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
    ): List<AnalysisMerchantTotal> =
        queries.analysisTopMerchants(
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            limit = limit,
            mapper = ::mapMerchantTotal,
        ).executeAsList()

    fun categoryTrends(
        fromDate: String,
        toDate: String,
        bucket: AnalysisBucket = AnalysisBucket.MONTH,
        oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
        categoryNature: AnalysisCategoryNature? = null,
        accountId: String? = null,
        categoryId: String? = null,
    ): List<AnalysisCategoryTrendPoint> =
        queries.analysisCategoryTrends(
            bucket = bucket.queryValue,
            from_date = fromDate,
            to_date = toDate,
            one_time_mode = oneTimeMode.queryValue,
            category_nature = categoryNature?.queryValue,
            account_id = accountId,
            category_id = categoryId,
            mapper = ::mapCategoryTrendPoint,
        ).executeAsList()

    fun netWorthOverTime(
        fromDate: String,
        toDate: String,
        bucket: AnalysisBucket = AnalysisBucket.MONTH,
    ): List<AnalysisNetWorthPoint> =
        queries.analysisNetWorthOverTime(
            bucket = bucket.queryValue,
            from_date = fromDate,
            to_date = toDate,
            mapper = ::mapNetWorthPoint,
        ).executeAsList()

    private companion object {
        const val WIDGET_LIMIT = 8L
    }
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

private fun mapLargestExpense(
    sourceId: String,
    date: String?,
    label: String?,
    categoryId: String?,
    categoryName: String?,
    categoryIcon: String?,
    categoryColor: String?,
    amountCents: Long,
): AnalysisLargestExpense =
    AnalysisLargestExpense(
        sourceId = sourceId,
        date = date.orEmpty(),
        label = label,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        amountCents = amountCents,
    )

private fun mapMerchantTotal(
    merchantLabel: String?,
    totalCents: Long?,
    movementCount: Long,
): AnalysisMerchantTotal =
    AnalysisMerchantTotal(
        merchantLabel = merchantLabel,
        totalCents = totalCents ?: 0L,
        movementCount = movementCount,
    )

private fun mapCategoryTrendPoint(
    categoryId: String?,
    categoryName: String?,
    categoryColor: String?,
    bucket: String,
    expenseCents: Long?,
): AnalysisCategoryTrendPoint =
    AnalysisCategoryTrendPoint(
        categoryId = categoryId,
        categoryName = categoryName,
        categoryColor = categoryColor,
        bucket = bucket,
        expenseCents = expenseCents ?: 0L,
    )

private fun mapNetWorthPoint(
    bucket: String,
    netWorthCents: Long?,
): AnalysisNetWorthPoint =
    AnalysisNetWorthPoint(
        bucket = bucket,
        netWorthCents = netWorthCents ?: 0L,
    )

private fun mapWeekdaySpend(
    weekday: Long?,
    expenseCents: Long?,
): AnalysisWeekdaySpend =
    AnalysisWeekdaySpend(
        weekday = (weekday ?: 0L).toInt(),
        expenseCents = expenseCents ?: 0L,
    )

private fun mapCategoryFrequency(
    categoryId: String?,
    categoryName: String?,
    categoryColor: String?,
    movementCount: Long,
    totalCents: Long?,
): AnalysisCategoryFrequency =
    AnalysisCategoryFrequency(
        categoryId = categoryId,
        categoryName = categoryName,
        categoryColor = categoryColor,
        movementCount = movementCount,
        totalCents = totalCents ?: 0L,
    )
