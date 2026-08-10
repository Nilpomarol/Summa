package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisPeriodRangeTest {
    @Test
    fun currentMonthComparisonStopsAtEquivalentDay() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 8),
            year = 2026,
        )
        val previous = previousAnalysisRange(current, AnalysisScope.MONTH)!!

        val comparable = comparablePreviousRange(
            currentRange = current,
            previousRange = previous,
            scope = AnalysisScope.MONTH,
            today = LocalDate.of(2026, 8, 9),
        )

        assertEquals("2026-07-01", comparable.fromDate.toString())
        assertEquals("2026-07-10", comparable.toDateExclusive.toString())
    }

    @Test
    fun currentYearComparisonStopsAtEquivalentCalendarDay() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.YEAR,
            month = YearMonth.of(2026, 8),
            year = 2026,
        )
        val previous = previousAnalysisRange(current, AnalysisScope.YEAR)!!

        val comparable = comparablePreviousRange(
            currentRange = current,
            previousRange = previous,
            scope = AnalysisScope.YEAR,
            today = LocalDate.of(2026, 8, 9),
        )

        assertEquals("2025-01-01", comparable.fromDate.toString())
        assertEquals("2025-08-10", comparable.toDateExclusive.toString())
    }

    @Test
    fun completedPeriodComparisonRemainsComplete() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )
        val previous = previousAnalysisRange(current, AnalysisScope.MONTH)!!

        val comparable = comparablePreviousRange(
            currentRange = current,
            previousRange = previous,
            scope = AnalysisScope.MONTH,
            today = LocalDate.of(2026, 8, 9),
        )

        assertEquals(previous, comparable)
    }

    @Test
    fun monthScopeUsesWholeMonthAndDailyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )

        assertEquals("2026-06-01", range.fromDate.toString())
        assertEquals("2026-07-01", range.toDateExclusive.toString())
        assertEquals(AnalysisBucket.DAY, range.bucket)
    }

    @Test
    fun yearScopeUsesWholeYearAndMonthlyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.YEAR,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )

        assertEquals("2026-01-01", range.fromDate.toString())
        assertEquals("2027-01-01", range.toDateExclusive.toString())
        assertEquals(AnalysisBucket.MONTH, range.bucket)
    }

    @Test
    fun allTimeScopeUsesMonthlyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.ALL_TIME,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )

        assertEquals(AnalysisBucket.MONTH, range.bucket)
        assertEquals(null, previousAnalysisRange(range, AnalysisScope.ALL_TIME))
    }

    @Test
    fun monthViewFillsEveryDayWithDailyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )
        val buckets = listOf(
            bucket("2026-06-03", income = 200_000L, expense = 1_500L),
            bucket("2026-06-18", income = 0L, expense = 4_000L),
        )

        val points = incomeExpensePoints(range, AnalysisScope.MONTH, buckets)

        assertEquals(30, points.size)
        assertEquals("1", points[0].label)
        assertEquals("30", points[29].label)
        assertEquals(200_000L, points[2].incomeCents)
        assertEquals(1_500L, points[2].expenseCents)
        assertEquals(0L, points[3].incomeCents)
        assertEquals(4_000L, points[17].expenseCents)
    }

    private fun bucket(date: String, income: Long, expense: Long) =
        AnalysisIncomeExpenseBucket(
            bucket = date,
            incomeCents = income,
            expenseCents = expense,
            netCents = income - expense,
            savingsRateBasisPoints = 0L,
        )
}
