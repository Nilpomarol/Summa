package com.gestorfinances.app.ui.trips

import com.gestorfinances.app.data.repository.TripDailyActual
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TripDailyBarsTest {
    private val trip = TripSummary(
        id = "trip",
        name = "Amsterdam",
        type = TripType.TRIP,
        status = TripStatus.FINISHED,
        startDate = "2026-06-06",
        endDate = "2026-06-08",
        icon = null,
        color = null,
        notes = null,
        defaultAccountId = null,
        defaultAccountName = null,
        createdAt = "2026-05-01T00:00:00Z",
        updatedAt = "2026-05-01T00:00:00Z",
        archivedAt = null,
        totalActualCents = 0L,
    )

    @Test
    fun spendSplitsIntoBeforeEachTripDayAndAfter() {
        val bars = tripDailyBars(
            trip,
            listOf(
                TripDailyActual("2026-05-01", 42_000L),
                TripDailyActual("2026-06-06", 7_160L),
                TripDailyActual("2026-06-08", 2_000L),
                TripDailyActual("2026-06-10", 1_500L),
            ),
        )

        assertEquals(42_000L, bars.beforeCents)
        assertEquals(listOf(7_160L, 0L, 2_000L), bars.days.map { it.cents })
        assertEquals(listOf(1L, 2L, 3L), bars.days.map { it.ordinal })
        assertEquals(9_160L, bars.duringCents)
        assertEquals(1_500L, bars.afterCents)
    }

    @Test
    fun averageCountsOnlyTheDaysSoFarWhileTheTripRuns() {
        val bars = tripDailyBars(trip, listOf(TripDailyActual("2026-06-06", 6_000L)))

        assertEquals(6_000L, bars.averagePerElapsedDay(LocalDate.parse("2026-06-06")))
        assertEquals(2_000L, bars.averagePerElapsedDay(LocalDate.parse("2026-07-01")))
    }

    @Test
    fun aTripWithoutDatesChartsTheDaysItHasSpend() {
        val bars = tripDailyBars(
            trip.copy(startDate = null, endDate = null),
            listOf(TripDailyActual("2026-06-07", 300L), TripDailyActual("2026-06-05", 100L)),
        )

        assertEquals(listOf("2026-06-05", "2026-06-07"), bars.days.map { it.date })
        assertEquals(0L, bars.beforeCents)
    }
}
