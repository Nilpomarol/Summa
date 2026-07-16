package com.gestorfinances.app.ui.trips

import com.gestorfinances.app.data.repository.TripDailyActual
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDailyChartStateTest {
    @Test
    fun nonZeroKpiWithoutDailySeriesIsNotReportedAsTrueEmpty() {
        assertEquals(
            TripDailyChartState.KPI_WITHOUT_SERIES,
            tripDailyChartState(actualCents = 12_000L, items = emptyList()),
        )
    }

    @Test
    fun trueNoDataIsAnExplicitEmptyState() {
        assertEquals(
            TripDailyChartState.TRUE_EMPTY,
            tripDailyChartState(actualCents = 0L, items = emptyList()),
        )
    }

    @Test
    fun dailySeriesMustReconcileToKpiBeforeItIsPlotted() {
        val items = listOf(
            TripDailyActual(date = "2026-08-01", actualCents = 7_000L),
            TripDailyActual(date = "2026-08-02", actualCents = 3_000L),
        )

        assertEquals(
            TripDailyChartState.DATA,
            tripDailyChartState(actualCents = 10_000L, items = items),
        )
        assertEquals(
            TripDailyChartState.KPI_SERIES_MISMATCH,
            tripDailyChartState(actualCents = 11_000L, items = items),
        )
    }

    @Test
    fun oneDaySeriesGetsAZeroBaselineForTheCumulativeLine() {
        val points = tripDailyChartPoints(
            listOf(TripDailyActual(date = "2026-08-01", actualCents = 5_000L)),
        )

        assertEquals(2, points.size)
        assertEquals(0L, points[0].expenseCents)
        assertEquals(5_000L, points[1].expenseCents)
        assertEquals("1", points[1].label)
    }
}
