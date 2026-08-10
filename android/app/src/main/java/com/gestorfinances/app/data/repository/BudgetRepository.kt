package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.BudgetsQueries
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.toRecurrenceRule
import java.time.LocalDate
import java.time.YearMonth

/** Default alert band when a budget defines no explicit threshold. */
const val DEFAULT_BUDGET_ALERT_PERCENT = 80L

enum class BudgetStatus { OK, WARN, OVER }

enum class BudgetScope(val dbValue: String) {
    OVERALL_MONTH("overall_month"),
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
    YEARLY("yearly"),
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
    val tripId: String? = null,
    val scope: BudgetScope = if (tripId != null) BudgetScope.TRIP else BudgetScope.CATEGORY,
    val period: BudgetPeriod = if (scope == BudgetScope.TRIP) BudgetPeriod.ONE_OFF else BudgetPeriod.MONTHLY,
    val includeTripExpenses: Boolean = true,
    val includeExtraordinaryExpenses: Boolean = true,
)

data class BudgetSummary(
    val id: String,
    val scope: BudgetScope,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String? = null,
    val categoryColor: String? = null,
    val tripId: String?,
    val tripName: String?,
    val period: BudgetPeriod,
    val limitAmountCents: Long,
    val alertThresholdPercent: Long?,
    val includeTripExpenses: Boolean = true,
    val includeExtraordinaryExpenses: Boolean = true,
) {
    val displayName: String?
        get() = when (scope) {
            BudgetScope.OVERALL_MONTH -> null
            BudgetScope.CATEGORY -> categoryName
            BudgetScope.TRIP -> tripName
        }
}

/** A budget paired with its evaluated actual spend for a period. */
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

enum class BudgetForecastStatus { ON_TRACK, MAY_EXCEED, OVER }

/** Raised when a reusable budget rule would duplicate an active rule with the same scope. */
class DuplicateActiveBudgetException : IllegalArgumentException()

/** Current-month budget truth plus the two inputs that make its estimate explainable. */
data class BudgetProjection(
    val evaluation: BudgetEvaluation,
    val pendingRecurringCents: Long,
    val estimatedVariableCents: Long,
    val hasBehaviourEstimate: Boolean,
) {
    val forecastCents: Long get() = evaluation.actualCents + pendingRecurringCents + estimatedVariableCents
    val remainingForecastCents: Long get() = evaluation.budget.limitAmountCents - forecastCents
    val status: BudgetForecastStatus
        get() = when {
            evaluation.actualCents >= evaluation.budget.limitAmountCents -> BudgetForecastStatus.OVER
            forecastCents > evaluation.budget.limitAmountCents -> BudgetForecastStatus.MAY_EXCEED
            else -> BudgetForecastStatus.ON_TRACK
        }
}

private data class BudgetVariableHistory(
    val actualCents: Long,
    val occurrenceCount: Long,
)

class BudgetRepository(
    internal val queries: BudgetsQueries,
) {
    fun listActive(): List<BudgetSummary> =
        queries.activeBudgets(::mapBudgetSummary).executeAsList()

    fun getActive(id: String): BudgetSummary? =
        queries.budgetById(id, ::mapBudgetSummary).executeAsOneOrNull()

    /** Sum of actual expenses, with refunds netted, for a category over [fromDate, toDate]. */
    fun actualForCategory(
        categoryId: String,
        fromDate: String,
        toDate: String,
        includeTripExpenses: Boolean = true,
        includeExtraordinaryExpenses: Boolean = true,
    ): Long =
        queries.budgetActualForCategory(
            category_id = categoryId,
            from_date = fromDate,
            to_date = toDate,
            include_trip_expenses = includeTripExpenses.toDbLong(),
            include_extraordinary_expenses = includeExtraordinaryExpenses.toDbLong(),
        ).executeAsOne()

    /** Sum of actual expenses, with refunds netted, for a trip over [fromDate, toDate]. */
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

    fun actualOverall(
        fromDate: String,
        toDate: String,
        includeTripExpenses: Boolean = true,
        includeExtraordinaryExpenses: Boolean = true,
    ): Long =
        queries.budgetActualOverall(
            from_date = fromDate,
            to_date = toDate,
            include_trip_expenses = includeTripExpenses.toDbLong(),
            include_extraordinary_expenses = includeExtraordinaryExpenses.toDbLong(),
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

    /**
     * Produces the current-month projections consumed by both the Dashboard and Budget page.
     * Historical variable spending is intentionally read from canonical actual expense SQL;
     * scheduled fixed templates are added separately so they are never double-counted.
     */
    fun currentMonthProjections(
        today: LocalDate,
        templates: List<TemplateSummary>,
        categoryParentById: Map<String, String?>,
    ): List<BudgetProjection> {
        val month = YearMonth.from(today)
        val monthStart = month.atDay(1).toString()
        val monthEnd = month.atEndOfMonth().toString()
        return listActive()
            .filter { budget ->
                budget.scope == BudgetScope.OVERALL_MONTH ||
                    (budget.scope == BudgetScope.CATEGORY && budget.period == BudgetPeriod.MONTHLY)
            }
            .map { budget ->
                val evaluation = BudgetEvaluation(
                    budget = budget,
                    actualCents = actualForBudget(budget, monthStart, monthEnd),
                )
                val history = variableHistory(
                    budget = budget,
                    month = month,
                )
                val hasBehaviourEstimate = history.sumOf(BudgetVariableHistory::occurrenceCount) > 0L
                val historyDays = (1..BUDGET_HISTORY_MONTHS).sumOf { offset ->
                    month.minusMonths(offset.toLong()).lengthOfMonth()
                }
                val estimatedVariable = if (hasBehaviourEstimate) {
                    history.sumOf(BudgetVariableHistory::actualCents) *
                        (month.lengthOfMonth() - today.dayOfMonth) / historyDays
                } else {
                    0L
                }
                BudgetProjection(
                    evaluation = evaluation,
                    pendingRecurringCents = pendingRecurringForBudget(
                        budget = budget,
                        templates = templates,
                        today = today,
                        monthEnd = month.atEndOfMonth(),
                        categoryParentById = categoryParentById,
                    ),
                    estimatedVariableCents = estimatedVariable,
                    hasBehaviourEstimate = hasBehaviourEstimate,
                )
            }
    }

    fun create(
        draft: BudgetDraft,
        createdAt: String,
    ) {
        validate(draft)
        requireNoDuplicate(draft)
        queries.insertBudget(
            id = draft.id,
            scope = draft.scope.dbValue,
            category_id = draft.categoryId,
            trip_id = draft.tripId,
            period = draft.period.dbValue,
            limit_amount_cents = draft.limitAmountCents,
            alert_threshold_percent = draft.alertThresholdPercent,
            include_trip_expenses = draft.includeTripExpenses.toDbLong(),
            include_extraordinary_expenses = draft.includeExtraordinaryExpenses.toDbLong(),
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun update(
        draft: BudgetDraft,
        updatedAt: String,
    ) {
        validate(draft)
        requireNoDuplicate(draft)
        queries.updateBudget(
            id = draft.id,
            scope = draft.scope.dbValue,
            category_id = draft.categoryId,
            trip_id = draft.tripId,
            period = draft.period.dbValue,
            limit_amount_cents = draft.limitAmountCents,
            alert_threshold_percent = draft.alertThresholdPercent,
            include_trip_expenses = draft.includeTripExpenses.toDbLong(),
            include_extraordinary_expenses = draft.includeExtraordinaryExpenses.toDbLong(),
            updated_at = updatedAt,
        )
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archiveBudget(id = id, archived_at = archivedAt, updated_at = archivedAt)
    }

    fun restore(id: String, deletedAt: String, restoredAt: String) {
        queries.restoreBudget(id = id, archived_at = deletedAt, updated_at = restoredAt)
    }

    private fun requireNoDuplicate(draft: BudgetDraft) {
        val duplicate = listActive().any { existing ->
            existing.id != draft.id &&
                when (draft.scope) {
                    BudgetScope.OVERALL_MONTH -> existing.scope == BudgetScope.OVERALL_MONTH
                    BudgetScope.CATEGORY ->
                        existing.scope == BudgetScope.CATEGORY &&
                            existing.categoryId == draft.categoryId &&
                            existing.period == draft.period
                    BudgetScope.TRIP -> existing.scope == BudgetScope.TRIP && existing.tripId == draft.tripId
                }
        }
        if (duplicate) throw DuplicateActiveBudgetException()
    }
}

private fun BudgetRepository.actualForBudget(
    budget: BudgetSummary,
    fromDate: String,
    toDate: String,
): Long =
    when (budget.scope) {
        BudgetScope.OVERALL_MONTH -> actualOverall(
            fromDate = fromDate,
            toDate = toDate,
            includeTripExpenses = budget.includeTripExpenses,
            includeExtraordinaryExpenses = budget.includeExtraordinaryExpenses,
        )
        BudgetScope.CATEGORY -> actualForCategory(
            categoryId = requireNotNull(budget.categoryId),
            fromDate = budget.periodStart(fromDate),
            toDate = toDate,
            includeTripExpenses = budget.includeTripExpenses,
            includeExtraordinaryExpenses = budget.includeExtraordinaryExpenses,
        )
        // TRIP-scope budgets are one-off: they track a trip's whole life, not the
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

private fun BudgetRepository.variableHistory(
    budget: BudgetSummary,
    month: YearMonth,
): List<BudgetVariableHistory> =
    (1..BUDGET_HISTORY_MONTHS).map { offset ->
        val historicalMonth = month.minusMonths(offset.toLong())
        val fromDate = historicalMonth.atDay(1).toString()
        val toDate = historicalMonth.atEndOfMonth().toString()
        when (budget.scope) {
            BudgetScope.CATEGORY -> queries.budgetVariableActualForCategory(
                category_id = requireNotNull(budget.categoryId),
                from_date = fromDate,
                to_date = toDate,
                include_trip_expenses = budget.includeTripExpenses.toDbLong(),
                include_extraordinary_expenses = budget.includeExtraordinaryExpenses.toDbLong(),
                mapper = { actualCents, occurrenceCount ->
                    BudgetVariableHistory(actualCents, occurrenceCount)
                },
            ).executeAsOne()
            BudgetScope.OVERALL_MONTH -> queries.budgetVariableActualOverall(
                from_date = fromDate,
                to_date = toDate,
                include_trip_expenses = budget.includeTripExpenses.toDbLong(),
                include_extraordinary_expenses = budget.includeExtraordinaryExpenses.toDbLong(),
                mapper = { actualCents, occurrenceCount ->
                    BudgetVariableHistory(actualCents, occurrenceCount)
                },
            ).executeAsOne()
            BudgetScope.TRIP -> error("Trip budgets do not have a monthly forecast.")
        }
    }

private fun pendingRecurringForBudget(
    budget: BudgetSummary,
    templates: List<TemplateSummary>,
    today: LocalDate,
    monthEnd: LocalDate,
    categoryParentById: Map<String, String?>,
): Long =
    templates.asSequence()
        .filter { template ->
            template.status == TemplateStatus.ACTIVE &&
                template.type == MovementType.EXPENSE &&
                !template.amountIsVariable &&
                template.amountCents != null &&
                (budget.scope == BudgetScope.OVERALL_MONTH ||
                    template.categoryId.matchesBudgetCategory(budget.categoryId, categoryParentById))
        }
        .sumOf { template ->
            val cursor = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull() ?: return@sumOf 0L
            val count = runCatching {
                RecurringAdvancer.advance(template.toRecurrenceRule(), cursor, monthEnd).dueDates
                    .count { dueDate -> !dueDate.isBefore(today) }
            }.getOrDefault(0)
            template.userActualAmountCents() * count
        }

private fun String?.matchesBudgetCategory(
    budgetCategoryId: String?,
    categoryParentById: Map<String, String?>,
): Boolean =
    this != null && (this == budgetCategoryId || categoryParentById[this] == budgetCategoryId)

private fun TemplateSummary.userActualAmountCents(): Long =
    splitConfig?.lines?.firstOrNull { it.party == "user" }?.owedAmountCents ?: requireNotNull(amountCents)

private const val BUDGET_HISTORY_MONTHS = 3
private const val TRIP_BUDGET_RANGE_START = "0001-01-01"
private const val TRIP_BUDGET_RANGE_END = "9999-12-31"

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L

private fun BudgetSummary.periodStart(referenceStart: String): String =
    when (period) {
        BudgetPeriod.YEARLY -> "${referenceStart.take(4)}-01-01"
        BudgetPeriod.MONTHLY, BudgetPeriod.ONE_OFF -> referenceStart
    }

private fun validate(draft: BudgetDraft) {
    when (draft.scope) {
        BudgetScope.OVERALL_MONTH -> {
            require(draft.categoryId == null && draft.tripId == null) { "An overall budget has no category or trip." }
            require(draft.period == BudgetPeriod.MONTHLY) { "An overall budget must be monthly." }
        }
        BudgetScope.CATEGORY -> {
            require(!draft.categoryId.isNullOrBlank()) { "A category budget needs a category." }
            require(draft.tripId == null) { "A category budget cannot reference a trip." }
            require(draft.period in setOf(BudgetPeriod.MONTHLY, BudgetPeriod.YEARLY)) {
                "A category budget must be monthly or yearly."
            }
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
    categoryIcon: String?,
    categoryColor: String?,
    tripId: String?,
    tripName: String?,
    period: String,
    limitAmountCents: Long,
    alertThresholdPercent: Long?,
    includeTripExpenses: Long,
    includeExtraordinaryExpenses: Long,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
): BudgetSummary =
    BudgetSummary(
        id = id,
        scope = BudgetScope.fromDb(scope),
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        tripId = tripId,
        tripName = tripName,
        period = BudgetPeriod.fromDb(period),
        limitAmountCents = limitAmountCents,
        alertThresholdPercent = alertThresholdPercent,
        includeTripExpenses = includeTripExpenses != 0L,
        includeExtraordinaryExpenses = includeExtraordinaryExpenses != 0L,
    )
