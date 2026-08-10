package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisPeriodRangeTest {
    @Test
    fun currentMonthComparisonStopsAtEquivalentDay() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 8),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!
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
            customFrom = "",
            customTo = "",
        ).range!!
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
            customFrom = "",
            customTo = "",
        ).range!!
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
        val result = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "",
            customTo = "",
        )

        val range = result.range!!
        assertEquals("2026-06-01", range.fromDate.toString())
        assertEquals("2026-07-01", range.toDateExclusive.toString())
        assertEquals(AnalysisBucket.DAY, range.bucket)
    }

    @Test
    fun yearScopeUsesWholeYearAndMonthlyBuckets() {
        val result = resolveAnalysisRange(
            scope = AnalysisScope.YEAR,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "",
            customTo = "",
        )

        val range = result.range!!
        assertEquals("2026-01-01", range.fromDate.toString())
        assertEquals("2027-01-01", range.toDateExclusive.toString())
        assertEquals(AnalysisBucket.MONTH, range.bucket)
    }

    @Test
    fun customRangeTreatsToDateAsInclusive() {
        val result = resolveAnalysisRange(
            scope = AnalysisScope.CUSTOM,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "2026-06-10",
            customTo = "2026-06-12",
        )

        val range = result.range!!
        assertEquals("2026-06-10", range.fromDate.toString())
        assertEquals("2026-06-13", range.toDateExclusive.toString())
        assertEquals(AnalysisBucket.DAY, range.bucket)
    }

    @Test
    fun customRangeRejectsInvalidDateOrder() {
        val result = resolveAnalysisRange(
            scope = AnalysisScope.CUSTOM,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "2026-06-12",
            customTo = "2026-06-10",
        )

        assertNull(result.range)
        assertEquals(R.string.movement_filter_date_order_invalid, result.errorRes)
    }

    @Test
    fun previousCustomRangeKeepsSameDurationImmediatelyBeforeCurrent() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.CUSTOM,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "2026-06-10",
            customTo = "2026-06-12",
        ).range!!

        val previous = previousAnalysisRange(current, AnalysisScope.CUSTOM)!!

        assertEquals("2026-06-07", previous.fromDate.toString())
        assertEquals("2026-06-10", previous.toDateExclusive.toString())
        assertEquals(AnalysisBucket.DAY, previous.bucket)
    }

    @Test
    fun comparisonMonthRangeUsesSelectedMonthAndCurrentBucket() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!

        val comparison = comparisonAnalysisRange(
            scope = AnalysisScope.MONTH,
            comparisonMonth = YearMonth.of(2025, 12),
            comparisonYear = 2025,
            comparisonCustomFrom = "",
            comparisonCustomTo = "",
            currentRange = current,
        ).range!!

        assertEquals("2025-12-01", comparison.fromDate.toString())
        assertEquals("2026-01-01", comparison.toDateExclusive.toString())
        assertEquals(AnalysisBucket.DAY, comparison.bucket)
    }

    @Test
    fun comparisonYearRangeUsesSelectedYearAndCurrentBucket() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.YEAR,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!

        val comparison = comparisonAnalysisRange(
            scope = AnalysisScope.YEAR,
            comparisonMonth = YearMonth.of(2026, 6),
            comparisonYear = 2024,
            comparisonCustomFrom = "",
            comparisonCustomTo = "",
            currentRange = current,
        ).range!!

        assertEquals("2024-01-01", comparison.fromDate.toString())
        assertEquals("2025-01-01", comparison.toDateExclusive.toString())
        assertEquals(AnalysisBucket.MONTH, comparison.bucket)
    }

    @Test
    fun comparisonCustomRangeRespectsSelectedDatesAndCurrentBucket() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.CUSTOM,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "2026-06-10",
            customTo = "2026-06-12",
        ).range!!

        val comparison = comparisonAnalysisRange(
            scope = AnalysisScope.CUSTOM,
            comparisonMonth = YearMonth.of(2026, 6),
            comparisonYear = 2026,
            comparisonCustomFrom = "2025-12-20",
            comparisonCustomTo = "2025-12-25",
            currentRange = current,
        ).range!!

        assertEquals("2025-12-20", comparison.fromDate.toString())
        assertEquals("2025-12-26", comparison.toDateExclusive.toString())
        // Bucket mirrors the current range so both periods align on the comparative chart.
        assertEquals(AnalysisBucket.DAY, comparison.bucket)
    }

    @Test
    fun comparisonAllTimeRangeIsNull() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.ALL_TIME,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!

        val comparison = comparisonAnalysisRange(
            scope = AnalysisScope.ALL_TIME,
            comparisonMonth = YearMonth.of(2026, 6),
            comparisonYear = 2026,
            comparisonCustomFrom = "",
            comparisonCustomTo = "",
            currentRange = current,
        )

        assertNull(comparison.range)
    }

    @Test
    fun deterministicDivisorUsesFullCurrentRangeForMonthAndYear() {
        val month = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 2),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!
        val year = resolveAnalysisRange(
            scope = AnalysisScope.YEAR,
            month = YearMonth.of(2026, 2),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!

        assertEquals(28L, deterministicDivisor(month))
        assertEquals(12L, deterministicDivisor(year))
    }

    @Test
    fun monthViewFillsEveryDayWithDailyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!
        val buckets = listOf(
            bucket("2026-06-03", income = 200_000L, expense = 1_500L),
            bucket("2026-06-18", income = 0L, expense = 4_000L),
        )

        val points = incomeExpensePoints(range, AnalysisScope.MONTH, buckets)

        // June has 30 days; every day is present (gaps filled) so the cumulative line stays continuous.
        assertEquals(30, points.size)
        assertEquals("1", points[0].label)
        assertEquals("30", points[29].label)
        // Day 3 (index 2) carries the income/expense; gap days are zero.
        assertEquals(200_000L, points[2].incomeCents)
        assertEquals(1_500L, points[2].expenseCents)
        assertEquals(0L, points[3].incomeCents)
        assertEquals(4_000L, points[17].expenseCents)
    }

    @Test
    fun shortCustomRangeKeepsDailyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.CUSTOM,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "2026-06-10",
            customTo = "2026-06-12",
        ).range!!

        val points = incomeExpensePoints(range, AnalysisScope.CUSTOM, emptyList())

        assertEquals(3, points.size)
        assertEquals("10", points[0].label)
        assertEquals("12", points[2].label)
    }

    @Test
    fun deterministicDivisorCountsDaysInCustomRange() {
        val custom = resolveAnalysisRange(
            scope = AnalysisScope.CUSTOM,
            month = YearMonth.of(2026, 6),
            year = 2026,
            customFrom = "2026-06-10",
            customTo = "2026-06-12",
        ).range!!

        assertEquals(3L, deterministicDivisor(custom))
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
