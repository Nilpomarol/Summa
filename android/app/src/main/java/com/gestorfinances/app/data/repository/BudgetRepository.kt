package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.BudgetsQueries

/** Default alert band when a budget defines no explicit threshold. */
const val DEFAULT_BUDGET_ALERT_PERCENT = 80L

enum class BudgetStatus { OK, WARN, OVER }

enum class BudgetScope(val dbValue: String) {
    CATEGORY("category"),
    TRIP("trip"),
    ;

    companion object {
        fun fromDb(value: String): BudgetScope =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown budget scope: $value")
    }
}

enum class BudgetPeriod(val dbValue: String) {
    MONTHLY("monthly"),
    ONE_OFF("one_off"),
    ;

    companion object {
        fun fromDb(value: String): BudgetPeriod =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown budget period: $value")
    }
}

data class BudgetDraft(
    val id: String,
    val categoryId: String?,
    val limitAmountCents: Long,
    val alertThresholdPercent: Long?,
    val startDate: String?,
    val tripId: String? = null,
    val scope: BudgetScope = if (tripId != null) BudgetScope.TRIP else BudgetScope.CATEGORY,
    val period: BudgetPeriod = if (scope == BudgetScope.TRIP) BudgetPeriod.ONE_OFF else BudgetPeriod.MONTHLY,
)

data class BudgetSummary(
    val id: String,
    val scope: BudgetScope,
    val categoryId: String?,
    val categoryName: String?,
    val tripId: String?,
    val tripName: String?,
    val period: BudgetPeriod,
    val limitAmountCents: Long,
    val alertThresholdPercent: Long?,
    val startDate: String?,
) {
    val displayName: String?
        get() = when (scope) {
            BudgetScope.CATEGORY -> categoryName
            BudgetScope.TRIP -> tripName
        }
}

/** A budget paired with its evaluated actual spend for a period (§4.5). */
data class BudgetEvaluation(
    val budget: BudgetSummary,
    val actualCents: Long,
) {
    val remainingCents: Long get() = budget.limitAmountCents - actualCents
    val status: BudgetStatus
        get() {
            val threshold = budget.alertThresholdPercent ?: DEFAULT_BUDGET_ALERT_PERCENT
            return when {
                actualCents >= budget.limitAmountCents -> BudgetStatus.OVER
                actualCents * 100 >= budget.limitAmountCents * threshold -> BudgetStatus.WARN
                else -> BudgetStatus.OK
            }
        }
}

class BudgetRepository(
    private val queries: BudgetsQueries,
) {
    fun listActive(): List<BudgetSummary> =
        queries.activeBudgets(::mapBudgetSummary).executeAsList()

    fun getActive(id: String): BudgetSummary? =
        queries.budgetById(id, ::mapBudgetSummary).executeAsOneOrNull()

    /** Sum of actual expenses (§4.2, refunds netted) for a category over [fromDate, toDate]. */
    fun actualForCategory(
        categoryId: String,
        fromDate: String,
        toDate: String,
    ): Long =
        queries.budgetActualForCategory(
            category_id = categoryId,
            from_date = fromDate,
            to_date = toDate,
        ).executeAsOne()

    /** Sum of actual expenses (§4.2, refunds netted) for a trip over [fromDate, toDate]. */
    fun actualForTrip(
        tripId: String,
        fromDate: String,
        toDate: String,
    ): Long =
        queries.budgetActualForTrip(
            trip_id = tripId,
            from_date = fromDate,
            to_date = toDate,
        ).executeAsOne()

    fun evaluateAll(
        fromDate: String,
        toDate: String,
    ): List<BudgetEvaluation> =
        listActive().map { budget ->
            BudgetEvaluation(
                budget = budget,
                actualCents = actualForBudget(budget, fromDate = fromDate, toDate = toDate),
            )
        }

    fun create(
        draft: BudgetDraft,
        createdAt: String,
    ) {
        validate(draft)
        queries.insertBudget(
            id = draft.id,
            scope = draft.scope.dbValue,
            category_id = draft.categoryId,
            trip_id = draft.tripId,
            period = draft.period.dbValue,
            limit_amount_cents = draft.limitAmountCents,
            start_date = draft.startDate,
            alert_threshold_percent = draft.alertThresholdPercent,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun update(
        draft: BudgetDraft,
        updatedAt: String,
    ) {
        validate(draft)
        queries.updateBudget(
            id = draft.id,
            scope = draft.scope.dbValue,
            category_id = draft.categoryId,
            trip_id = draft.tripId,
            period = draft.period.dbValue,
            limit_amount_cents = draft.limitAmountCents,
            start_date = draft.startDate,
            alert_threshold_percent = draft.alertThresholdPercent,
            updated_at = updatedAt,
        )
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archiveBudget(id = id, archived_at = archivedAt, updated_at = archivedAt)
    }
}

private fun BudgetRepository.actualForBudget(
    budget: BudgetSummary,
    fromDate: String,
    toDate: String,
): Long =
    when (budget.scope) {
        BudgetScope.CATEGORY -> actualForCategory(
            categoryId = requireNotNull(budget.categoryId),
            fromDate = budget.effectiveFromDate(fromDate),
            toDate = toDate,
        )
        // TRIP-scope budgets are one-off (§4.5): they track a trip's whole life, not the
        // caller-supplied period. Evaluate them fully unbounded, matching how
        // TripAnalysisRepository's trip-scoped queries filter by trip_id alone with no date
        // bound — so advance-booking spend recorded before the trip's own start_date (or
        // after its end_date) still counts.
        BudgetScope.TRIP -> actualForTrip(
            tripId = requireNotNull(budget.tripId),
            fromDate = TRIP_BUDGET_RANGE_START,
            toDate = TRIP_BUDGET_RANGE_END,
        )
    }

private const val TRIP_BUDGET_RANGE_START = "0001-01-01"
private const val TRIP_BUDGET_RANGE_END = "9999-12-31"

private fun BudgetSummary.effectiveFromDate(periodStart: String): String {
    val start = startDate ?: return periodStart
    return if (start > periodStart) start else periodStart
}

private fun validate(draft: BudgetDraft) {
    when (draft.scope) {
        BudgetScope.CATEGORY -> {
            require(!draft.categoryId.isNullOrBlank()) { "A category budget needs a category." }
            require(draft.tripId == null) { "A category budget cannot reference a trip." }
            require(draft.period == BudgetPeriod.MONTHLY) { "A category budget must be monthly." }
        }
        BudgetScope.TRIP -> {
            require(!draft.tripId.isNullOrBlank()) { "A trip budget needs a trip." }
            require(draft.categoryId == null) { "A trip budget cannot reference a category." }
            require(draft.period == BudgetPeriod.ONE_OFF) { "A trip budget must be one-off." }
        }
    }
    require(draft.limitAmountCents > 0L) { "Budget limit must be positive." }
    require(draft.alertThresholdPercent == null || draft.alertThresholdPercent in 1L..100L) {
        "Alert threshold must be between 1 and 100."
    }
}

private fun mapBudgetSummary(
    id: String,
    scope: String,
    categoryId: String?,
    categoryName: String?,
    tripId: String?,
    tripName: String?,
    period: String,
    limitAmountCents: Long,
    alertThresholdPercent: Long?,
    startDate: String?,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
): BudgetSummary =
    BudgetSummary(
        id = id,
        scope = BudgetScope.fromDb(scope),
        categoryId = categoryId,
        categoryName = categoryName,
        tripId = tripId,
        tripName = tripName,
        period = BudgetPeriod.fromDb(period),
        limitAmountCents = limitAmountCents,
        alertThresholdPercent = alertThresholdPercent,
        startDate = startDate,
    )
