package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisAccountFlowBucket
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisPeriodRangeTest {
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
    fun averageDivisorUsesFullCurrentRangeForMonthAndYear() {
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

        assertEquals(
            28L,
            averageDivisor(
                range = month,
                scope = AnalysisScope.MONTH,
                analysisMode = AnalysisMode.ACTUAL,
                chartBuckets = emptyList(),
                flowBuckets = emptyList(),
            ),
        )
        assertEquals(
            12L,
            averageDivisor(
                range = year,
                scope = AnalysisScope.YEAR,
                analysisMode = AnalysisMode.ACTUAL,
                chartBuckets = emptyList(),
                flowBuckets = emptyList(),
            ),
        )
    }

    @Test
    fun allTimeAverageDivisorUsesReturnedBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.ALL_TIME,
            month = YearMonth.of(2026, 2),
            year = 2026,
            customFrom = "",
            customTo = "",
        ).range!!

        assertEquals(
            2L,
            averageDivisor(
                range = range,
                scope = AnalysisScope.ALL_TIME,
                analysisMode = AnalysisMode.ACTUAL,
                chartBuckets = listOf(
                    actualBucket("2026-01"),
                    actualBucket("2026-02"),
                ),
                flowBuckets = emptyList(),
            ),
        )
        assertEquals(
            2L,
            averageDivisor(
                range = range,
                scope = AnalysisScope.ALL_TIME,
                analysisMode = AnalysisMode.FLOW,
                chartBuckets = emptyList(),
                flowBuckets = listOf(
                    flowBucket("2026-01", "checking"),
                    flowBucket("2026-01", "cash"),
                    flowBucket("2026-02", "checking"),
                ),
            ),
        )
    }

    private fun actualBucket(bucket: String): AnalysisIncomeExpenseBucket =
        AnalysisIncomeExpenseBucket(
            bucket = bucket,
            incomeCents = 0,
            expenseCents = 0,
            netCents = 0,
            savingsRateBasisPoints = 0,
        )

    private fun flowBucket(bucket: String, accountId: String): AnalysisAccountFlowBucket =
        AnalysisAccountFlowBucket(
            bucket = bucket,
            accountId = accountId,
            accountName = accountId,
            deltaCents = 0,
            bucketDeltaCents = 0,
        )
}
