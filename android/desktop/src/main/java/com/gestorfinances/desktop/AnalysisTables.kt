package com.gestorfinances.desktop

import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.AnalysisSpendingByKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.ui.common.rollUpToParents
import java.time.LocalDate
import java.time.YearMonth

/** One row of a month-by-month table: what it is and its cents in each of the table's months. */
data class MonthRow(
    val key: String,
    val name: String?,
    val icon: String? = null,
    val color: String? = null,
    val categoryId: String? = null,
    val isTrips: Boolean = false,
    val cents: List<Long>,
) {
    val total: Long get() = cents.sum()
}

/**
 * The month-by-month tables the desktop analysis adds to the phone's page. Every figure is a
 * canonical one, asked for a month at a time; nothing here is computed from movements.
 */
data class AnalysisTables(
    val months: List<YearMonth>,
    /** Months that have begun: the ones ahead of today have nothing to say yet. */
    val started: List<Boolean>,
    /** Months that have ended: the one still running would pull an average down. */
    val closed: List<Boolean>,
    /** Spending by top-level category (subcategories rolled up), all trips as one row; biggest first. */
    val categories: List<MonthRow>,
    val expense: List<Long>,
    val income: List<Long>,
    val byKind: List<AnalysisSpendingByKind>,
    /** Each account's net change in the month. */
    val accounts: List<MonthRow>,
)

/** [months] in order; [accountId] narrows every figure to one account, as on the page above. */
fun loadAnalysisTables(
    analysis: AnalysisRepository,
    categories: List<CategoryRecord>,
    months: List<YearMonth>,
    accountId: String?,
    today: LocalDate,
): AnalysisTables {
    val byId = categories.associateBy { it.id }
    val perMonth: List<List<AnalysisCategoryTotal>> = months.map { month ->
        analysis.actualBreakdown(
            fromDate = month.atDay(1).toString(),
            toDate = month.plusMonths(1).atDay(1).toString(),
            groupTrips = true,
            accountId = accountId,
        ).rollUpToParents(byId)
    }
    val identity = LinkedHashMap<String, AnalysisCategoryTotal>()
    val cents = HashMap<String, LongArray>()
    perMonth.forEachIndexed { index, rows ->
        rows.forEach { row ->
            val key = if (row.rowKind == AnalysisBreakdownKind.TRIP) TRIPS_KEY else "category:${row.categoryId ?: "none"}"
            identity.putIfAbsent(key, row)
            cents.getOrPut(key) { LongArray(months.size) }[index] += row.expenseCents
        }
    }
    val categoryRows = identity.map { (key, row) ->
        val trips = key == TRIPS_KEY
        MonthRow(
            key = key,
            name = if (trips) null else row.categoryName,
            icon = row.categoryIcon.takeIf { !trips },
            color = row.categoryColor.takeIf { !trips },
            categoryId = row.categoryId.takeIf { !trips },
            isTrips = trips,
            cents = cents.getValue(key).toList(),
        )
    }.filter { it.total != 0L }.sortedByDescending { it.total }

    val from = months.first().atDay(1).toString()
    val to = months.last().plusMonths(1).atDay(1).toString()
    val totals = analysis.incomeVsExpense(fromDate = from, toDate = to, bucket = AnalysisBucket.MONTH, accountId = accountId)
        .associateBy { it.bucket }
    val flows = analysis.accountFlowOverTime(fromDate = from, toDate = to, bucket = AnalysisBucket.MONTH, accountId = accountId)
    val accountRows = flows.groupBy { it.accountId to it.accountName }.map { (account, buckets) ->
        val byMonth = buckets.associate { it.bucket to it.deltaCents }
        MonthRow(key = "account:${account.first}", name = account.second, cents = months.map { byMonth[it.toString()] ?: 0L })
    }.filter { row -> row.cents.any { it != 0L } }.sortedBy { it.name?.lowercase() }

    return AnalysisTables(
        months = months,
        started = months.map { !it.atDay(1).isAfter(today) },
        closed = months.map { it < YearMonth.from(today) },
        categories = categoryRows,
        expense = months.map { totals[it.toString()]?.expenseCents ?: 0L },
        income = months.map { totals[it.toString()]?.incomeCents ?: 0L },
        byKind = months.map { month ->
            analysis.spendingByKind(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString(), accountId)
        },
        accounts = accountRows,
    )
}

/**
 * A typical month of [cents]: the average of the months that have ended, as the budget tables take
 * it. With none ended yet, what there is so far stands for it.
 */
internal fun closedAverage(cents: List<Long>, closed: List<Boolean>): Long {
    val ended = cents.filterIndexed { index, _ -> closed[index] }
    return if (ended.isEmpty()) cents.sum() else ended.sum() / ended.size
}

private const val TRIPS_KEY = "trips"
