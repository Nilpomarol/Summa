package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.BudgetsQueries
import com.gestorfinances.app.domain.rules.CompartmentInput
import com.gestorfinances.app.domain.rules.MonthPlan
import com.gestorfinances.app.domain.rules.PartSpending
import com.gestorfinances.app.domain.rules.PlanInputs
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.buildMonthPlan
import com.gestorfinances.app.domain.rules.toRecurrenceRule
import com.gestorfinances.app.domain.rules.tripBudgetLeftIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
                actualCents > budget.limitAmountCents -> BudgetStatus.OVER
                actualCents * 100 >= budget.limitAmountCents * threshold -> BudgetStatus.WARN
                else -> BudgetStatus.OK
            }
        }
}

/** Raised when a reusable budget rule would duplicate an active rule with the same scope. */
class DuplicateActiveBudgetException : IllegalArgumentException()

/**
 * Raised when a monthly category limit would overlap another: a category and its parent (or a
 * child) cannot both be compartments of the monthly plan, or their spending would count twice.
 */
class OverlappingCompartmentException : IllegalArgumentException()

/** Which spending the monthly plan counts; set on its monthly total and shared by every part. */
data class PlanInclusions(
    val includeTripExpenses: Boolean,
    val includeExtraordinaryExpenses: Boolean,
)

/** Trips have budgets of their own, so a plan without a monthly total leaves them out. */
val DefaultPlanInclusions = PlanInclusions(includeTripExpenses = false, includeExtraordinaryExpenses = true)

/**
 * The monthly plan for [month]: its monthly total (if set), the monthly category limits acting as
 * its compartments, and how every part is going. [overlappingBudgetIds] are compartments saved
 * before overlaps were refused, which the page asks the owner to resolve.
 */
data class BudgetMonthPlan(
    val total: BudgetSummary?,
    val compartments: List<BudgetSummary>,
    val plan: MonthPlan,
    val inclusions: PlanInclusions,
    val overlappingBudgetIds: Set<String>,
)

/**
 * What recent months spent, recurring payments included, per month on average: for each category
 * (a parent including its subcategories) and for spending with no category. The plan's
 * suggestions start from these.
 */
data class RecentSpending(
    val byCategory: Map<String, Long>,
    val uncategorisedCents: Long,
)

/**
 * What each of the [months] before the plan's month spent, oldest first: per category (a parent
 * including its subcategories), the rest (spending outside today's partides), and the whole month.
 * Measured against today's plan, since the plan keeps no history.
 */
data class SpendingHistory(
    val months: List<YearMonth>,
    val byCategory: Map<String, List<Long>>,
    val others: List<Long>,
    val total: List<Long>,
)

/**
 * The month's income for the plan's saving: what has come in so far, and what the month is
 * expected to bring — that plus recurring income still due, or without any recurring income, the
 * recent monthly average (never less than what has already come in).
 */
data class PlanIncome(
    val actualCents: Long,
    val expectedCents: Long,
    /** Every expense of the month, trips and one-offs included: what a closed month's saving is less. */
    val allExpenseCents: Long,
    /** What the months before saved on average (income less every expense); null without any history. */
    val recentSavingCents: Long?,
)

/**
 * This month's recurring money, the owner's share: what recurring payments and income have
 * recorded (canonical actual expense and income, trips and one-offs included) and what is still
 * due at their expected amounts (overdue included).
 */
data class RecurringMonth(
    val paidExpenseCents: Long,
    val dueExpenseCents: Long,
    val receivedIncomeCents: Long,
    val dueIncomeCents: Long,
)

private data class PlanSpendRow(val categoryId: String?, val recurring: Boolean, val cents: Long)

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
     * The monthly plan for [month] as seen on [today]: today's plan for this month and later, and
     * for an earlier month the plan that month had. Spending belongs to its category's
     * compartment (or its parent's), else to the rest; so do recurring payments still due, by
     * their category. Every part reads the same canonical actual expense under the plan's
     * inclusions, so the parts add up to the plan's total spending.
     */
    fun monthPlan(
        month: YearMonth,
        today: LocalDate,
        templates: List<TemplateSummary>,
        categoryParentById: Map<String, String?>,
    ): BudgetMonthPlan {
        val budgets = if (month.isBefore(YearMonth.from(today))) planBudgetsIn(month) else listActive()
        val total = budgets.firstOrNull { it.scope == BudgetScope.OVERALL_MONTH }
        val compartments = budgets.filter { it.scope == BudgetScope.CATEGORY && it.period == BudgetPeriod.MONTHLY }
        val inclusions = total?.let { PlanInclusions(it.includeTripExpenses, it.includeExtraordinaryExpenses) }
            ?: DefaultPlanInclusions
        val byCategory = compartments.associateBy { requireNotNull(it.categoryId) }
        // The compartment a category's spending belongs to; null for the rest.
        val partOf = { categoryId: String? ->
            categoryId?.let { byCategory[it] ?: categoryParentById[it]?.let(byCategory::get) }?.id
        }

        val parts = mutableMapOf<String?, PartSpending>()
        val add = { key: String?, spending: PartSpending -> parts.merge(key, spending, PartSpending::plus) }
        planSpend(month.atDay(1), month.atEndOfMonth(), inclusions).forEach { row ->
            add(partOf(row.categoryId), PartSpending(actualCents = row.cents, recurringActualCents = if (row.recurring) row.cents else 0L))
        }
        val historyStart = month.minusMonths(BUDGET_HISTORY_MONTHS.toLong())
        planSpend(historyStart.atDay(1), month.minusMonths(1).atEndOfMonth(), inclusions)
            .filterNot { it.recurring }
            .forEach { row -> add(partOf(row.categoryId), PartSpending(historyCents = row.cents)) }
        templates
            .filter { it.status == TemplateStatus.ACTIVE && it.type == MovementType.EXPENSE }
            .filter { inclusions.includeTripExpenses || it.tripId == null }
            .forEach { template ->
                val (due, dueFromToday) = dueInMonth(template, month, today)
                add(partOf(template.categoryId), PartSpending(dueRemainingCents = due, dueFromTodayCents = dueFromToday))
            }

        val plan = buildMonthPlan(
            PlanInputs(
                month = month,
                today = today,
                totalLimitCents = total?.limitAmountCents,
                compartments = compartments.map { budget ->
                    CompartmentInput(
                        key = budget.id,
                        plannedCents = budget.limitAmountCents,
                        spending = parts[budget.id] ?: PartSpending(),
                    )
                },
                others = parts[null] ?: PartSpending(),
                historyDays = (1..BUDGET_HISTORY_MONTHS).sumOf { month.minusMonths(it.toLong()).lengthOfMonth() },
            ),
        )
        val overlapping = compartments.flatMap { budget ->
            val parent = categoryParentById[budget.categoryId]?.let(byCategory::get)
            if (parent != null) listOf(budget.id, parent.id) else emptyList()
        }.toSet()
        return BudgetMonthPlan(
            total = total,
            compartments = compartments,
            plan = plan,
            inclusions = inclusions,
            overlappingBudgetIds = overlapping,
        )
    }

    /** Recent spending per month, over the [BUDGET_HISTORY_MONTHS] months before [month]. */
    fun recentSpending(
        month: YearMonth,
        categoryParentById: Map<String, String?>,
        inclusions: PlanInclusions,
    ): RecentSpending {
        val totals = mutableMapOf<String, Long>()
        var uncategorised = 0L
        planSpend(
            month.minusMonths(BUDGET_HISTORY_MONTHS.toLong()).atDay(1),
            month.minusMonths(1).atEndOfMonth(),
            inclusions,
        ).forEach { row ->
            val categoryId = row.categoryId
            if (categoryId == null) {
                uncategorised += row.cents
            } else {
                totals.merge(categoryId, row.cents, Long::plus)
                categoryParentById[categoryId]?.let { parent -> totals.merge(parent, row.cents, Long::plus) }
            }
        }
        return RecentSpending(
            byCategory = totals.mapValues { it.value / BUDGET_HISTORY_MONTHS },
            uncategorisedCents = uncategorised / BUDGET_HISTORY_MONTHS,
        )
    }

    /** The [count] months before [month], each split as [SpendingHistory] describes. */
    fun spendingHistory(
        month: YearMonth,
        count: Int,
        categoryParentById: Map<String, String?>,
        inclusions: PlanInclusions,
    ): SpendingHistory {
        val months = (count downTo 1).map { month.minusMonths(it.toLong()) }
        val compartmentCategories = listActive()
            .filter { it.scope == BudgetScope.CATEGORY && it.period == BudgetPeriod.MONTHLY }
            .mapNotNull { it.categoryId }
            .toSet()
        val byCategory = mutableMapOf<String, LongArray>()
        val others = LongArray(count)
        val total = LongArray(count)
        months.forEachIndexed { index, historical ->
            planSpend(historical.atDay(1), historical.atEndOfMonth(), inclusions).forEach { row ->
                total[index] += row.cents
                val categoryId = row.categoryId
                val parent = categoryId?.let(categoryParentById::get)
                categoryId?.let { byCategory.getOrPut(it) { LongArray(count) }[index] += row.cents }
                parent?.let { byCategory.getOrPut(it) { LongArray(count) }[index] += row.cents }
                if (categoryId !in compartmentCategories && parent !in compartmentCategories) others[index] += row.cents
            }
        }
        return SpendingHistory(
            months = months,
            byCategory = byCategory.mapValues { it.value.toList() },
            others = others.toList(),
            total = total.toList(),
        )
    }

    /**
     * Sets up the monthly plan in one go: its total (replacing the limit of an existing one) and
     * its first partides. Any rule a budget breaks undoes the whole plan.
     */
    fun createPlan(total: BudgetDraft, partides: List<BudgetDraft>, createdAt: String) = queries.transaction {
        val existing = listActive().firstOrNull { it.scope == BudgetScope.OVERALL_MONTH }
        if (existing == null) create(total, createdAt) else update(total.copy(id = existing.id), createdAt)
        partides.forEach { create(it, createdAt) }
    }

    fun planIncome(month: YearMonth, today: LocalDate, templates: List<TemplateSummary>): PlanIncome {
        val actual = incomeIn(month)
        val recurring = templates.filter { it.status == TemplateStatus.ACTIVE && it.type == MovementType.INCOME }
        val expected = if (recurring.isNotEmpty()) {
            actual + recurring.sumOf { dueInMonth(it, month, today).first }
        } else {
            val average = (1..BUDGET_HISTORY_MONTHS).sumOf { incomeIn(month.minusMonths(it.toLong())) } / BUDGET_HISTORY_MONTHS
            maxOf(actual, average)
        }
        val before = (1..BUDGET_HISTORY_MONTHS).map { month.minusMonths(it.toLong()) }
        val beforeIncome = before.map(::incomeIn)
        val beforeExpense = before.map(::allExpenseIn)
        return PlanIncome(
            actualCents = actual,
            expectedCents = expected,
            allExpenseCents = allExpenseIn(month),
            recentSavingCents = (beforeIncome.sum() - beforeExpense.sum())
                .takeIf { beforeIncome.any { it != 0L } || beforeExpense.any { it != 0L } }
                ?.div(BUDGET_HISTORY_MONTHS),
        )
    }

    /**
     * What trips take from [month]'s saving when the plan leaves them out: what they have spent in
     * it, plus, for each trip with dates and a budget that is not over yet, the part of what its
     * budget has left that falls in the month.
     */
    fun tripSpendingIn(month: YearMonth, today: LocalDate, trips: List<TripSummary>): Long {
        val from = month.atDay(1).toString()
        val to = month.atEndOfMonth().toString()
        val spent = actualOverall(from, to) - actualOverall(from, to, includeTripExpenses = false)
        val budgets = listActive().filter { it.scope == BudgetScope.TRIP }.associateBy { it.tripId }
        val toCome = trips.sumOf { trip ->
            val budget = budgets[trip.id]
            val start = trip.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (budget == null || start == null || trip.status == TripStatus.FINISHED) return@sumOf 0L
            tripBudgetLeftIn(
                month = month,
                today = today,
                remainingCents = budget.limitAmountCents - trip.totalActualCents,
                start = start,
                end = trip.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }
        return spent + toCome
    }

    private fun allExpenseIn(month: YearMonth): Long =
        actualOverall(month.atDay(1).toString(), month.atEndOfMonth().toString())

    fun recurringMonth(month: YearMonth, today: LocalDate, templates: List<TemplateSummary>): RecurringMonth {
        val everything = PlanInclusions(includeTripExpenses = true, includeExtraordinaryExpenses = true)
        val paid = planSpend(month.atDay(1), month.atEndOfMonth(), everything).filter { it.recurring }.sumOf { it.cents }
        val received = queries.planIncome(month.atDay(1).toString(), month.atEndOfMonth().toString()) { recurring, cents -> recurring to cents }
            .executeAsList()
            .filter { it.first == 1L }
            .sumOf { it.second }
        val active = templates.filter { it.status == TemplateStatus.ACTIVE }
        val due = { type: MovementType -> active.filter { it.type == type }.sumOf { dueInMonth(it, month, today).first } }
        return RecurringMonth(
            paidExpenseCents = paid,
            dueExpenseCents = due(MovementType.EXPENSE),
            receivedIncomeCents = received,
            dueIncomeCents = due(MovementType.INCOME),
        )
    }

    private fun incomeIn(month: YearMonth): Long =
        queries.planIncome(month.atDay(1).toString(), month.atEndOfMonth().toString()) { _, cents -> cents }
            .executeAsList()
            .sum()

    /**
     * The owner's share of [template]'s occurrences still due in [month] (overdue ones included),
     * and of those falling on [today] or later, each at its expected amount; one with no amount to
     * go on counts nothing.
     */
    private fun dueInMonth(template: TemplateSummary, month: YearMonth, today: LocalDate): Pair<Long, Long> {
        val amount = template.expectedAmountCents ?: return 0L to 0L
        val cursor = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull() ?: return 0L to 0L
        val dueDates = runCatching {
            RecurringAdvancer.advance(template.toRecurrenceRule(), cursor, month.atEndOfMonth()).dueDates
        }.getOrDefault(emptyList()).filterNot { it.isBefore(month.atDay(1)) }
        val share = template.userOccurrenceAmountCents(amount)
        return share * dueDates.size to share * dueDates.count { !it.isBefore(today) }
    }

    private fun planSpend(from: LocalDate, to: LocalDate, inclusions: PlanInclusions): List<PlanSpendRow> =
        queries.planSpend(
            from_date = from.toString(),
            to_date = to.toString(),
            include_trip_expenses = inclusions.includeTripExpenses.toDbLong(),
            include_extraordinary_expenses = inclusions.includeExtraordinaryExpenses.toDbLong(),
        ) { categoryId, recurring, cents -> PlanSpendRow(categoryId, recurring == 1L, cents) }.executeAsList()

    /** The monthly plan's budgets as [month] had them (see `budgetsAsOfMonth`). */
    private fun planBudgetsIn(month: YearMonth): List<BudgetSummary> =
        queries.budgetsAsOfMonth(month.toString()) { id, scope, categoryId, categoryName, categoryIcon, categoryColor, limit, trips, extraordinary ->
            BudgetSummary(
                id = id,
                scope = BudgetScope.fromDb(scope),
                categoryId = categoryId,
                categoryName = categoryName,
                categoryIcon = categoryIcon,
                categoryColor = categoryColor,
                tripId = null,
                tripName = null,
                period = BudgetPeriod.MONTHLY,
                limitAmountCents = requireNotNull(limit),
                alertThresholdPercent = null,
                includeTripExpenses = trips != 0L,
                includeExtraordinaryExpenses = extraordinary != 0L,
            )
        }.executeAsList()

    /**
     * Writes what the budget is from the month of [at] on, so the months before keep the plan they
     * had: its values while it is a live monthly budget, or its leaving the plan (archived, or made
     * yearly). A change within a month replaces that month's version. Budgets that were never part
     * of the monthly plan keep no versions.
     */
    private fun recordVersion(id: String, at: String) {
        val budget = queries.budgetVersionSource(id).executeAsOneOrNull() ?: return
        val inPlan = budget.archived_at == null && budget.period == BudgetPeriod.MONTHLY.dbValue
        if (!inPlan && queries.budgetVersionCount(id).executeAsOne() == 0L) return
        queries.upsertBudgetVersion(
            budget_id = id,
            from_month = localMonthOf(at),
            category_id = budget.category_id,
            limit_amount_cents = budget.limit_amount_cents.takeIf { inPlan },
            include_trip_expenses = budget.include_trip_expenses,
            include_extraordinary_expenses = budget.include_extraordinary_expenses,
        )
    }

    fun create(
        draft: BudgetDraft,
        createdAt: String,
    ) = queries.transaction {
        validate(draft)
        requireNoDuplicate(draft)
        requireNoOverlap(draft)
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
        recordVersion(draft.id, createdAt)
    }

    fun update(
        draft: BudgetDraft,
        updatedAt: String,
    ) = queries.transaction {
        validate(draft)
        requireNoDuplicate(draft)
        requireNoOverlap(draft)
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
        recordVersion(draft.id, updatedAt)
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) = queries.transaction {
        queries.archiveBudget(id = id, archived_at = archivedAt, updated_at = archivedAt)
        recordVersion(id, archivedAt)
    }

    fun restore(id: String, deletedAt: String, restoredAt: String) = queries.transaction {
        queries.restoreBudget(id = id, archived_at = deletedAt, updated_at = restoredAt)
        recordVersion(id, restoredAt)
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

    private fun requireNoOverlap(draft: BudgetDraft) {
        if (draft.scope != BudgetScope.CATEGORY || draft.period != BudgetPeriod.MONTHLY) return
        val categoryId = requireNotNull(draft.categoryId)
        val otherCompartments = listActive()
            .filter { it.id != draft.id && it.scope == BudgetScope.CATEGORY && it.period == BudgetPeriod.MONTHLY }
            .mapNotNull { it.categoryId }
            .toSet()
        val parent = queries.categoryParent(categoryId).executeAsOneOrNull()?.parent_id
        val children = queries.categoryChildren(categoryId).executeAsList()
        if (parent in otherCompartments || children.any { it in otherCompartments }) {
            throw OverlappingCompartmentException()
        }
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

private const val BUDGET_HISTORY_MONTHS = 3
private const val TRIP_BUDGET_RANGE_START = "0001-01-01"
private const val TRIP_BUDGET_RANGE_END = "9999-12-31"

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L

/** The owner's calendar month ("2026-10") a UTC instant falls in. */
private fun localMonthOf(instant: String): String =
    YearMonth.from(Instant.parse(instant).atZone(ZoneId.systemDefault())).toString()

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
