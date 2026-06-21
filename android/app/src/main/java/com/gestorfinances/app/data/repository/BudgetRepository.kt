package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.BudgetsQueries

/** Default alert band when a budget defines no explicit threshold. */
const val DEFAULT_BUDGET_ALERT_PERCENT = 80L

enum class BudgetStatus { OK, WARN, OVER }

data class BudgetDraft(
    val id: String,
    val categoryId: String,
    val limitAmountCents: Long,
    val alertThresholdPercent: Long?,
    val startDate: String?,
)

data class BudgetSummary(
    val id: String,
    val categoryId: String,
    val categoryName: String?,
    val limitAmountCents: Long,
    val alertThresholdPercent: Long?,
    val startDate: String?,
)

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

    fun evaluateAll(
        fromDate: String,
        toDate: String,
    ): List<BudgetEvaluation> =
        listActive().map { budget ->
            BudgetEvaluation(
                budget = budget,
                actualCents = actualForCategory(budget.categoryId, fromDate, toDate),
            )
        }

    fun create(
        draft: BudgetDraft,
        createdAt: String,
    ) {
        validate(draft)
        queries.insertBudget(
            id = draft.id,
            category_id = draft.categoryId,
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
            category_id = draft.categoryId,
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

private fun validate(draft: BudgetDraft) {
    require(draft.categoryId.isNotBlank()) { "A category budget needs a category." }
    require(draft.limitAmountCents > 0L) { "Budget limit must be positive." }
    require(draft.alertThresholdPercent == null || draft.alertThresholdPercent in 1L..100L) {
        "Alert threshold must be between 1 and 100."
    }
}

private fun mapBudgetSummary(
    id: String,
    categoryId: String?,
    categoryName: String?,
    limitAmountCents: Long,
    alertThresholdPercent: Long?,
    startDate: String?,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
): BudgetSummary =
    BudgetSummary(
        id = id,
        categoryId = requireNotNull(categoryId) { "A category budget must reference a category." },
        categoryName = categoryName,
        limitAmountCents = limitAmountCents,
        alertThresholdPercent = alertThresholdPercent,
        startDate = startDate,
    )
