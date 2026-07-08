package com.gestorfinances.app.ui.navigation

import com.gestorfinances.app.ui.management.ManagementDestination
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the [AppNavState.back] reducer, in particular the `returnTo`-carrying overlays
 * (`Tags`/`Budgets` opened from within [AppOverlay.TripDetail]) that keep Trip Detail on the
 * back-stack instead of losing it — see `docs/16-android-audit-findings.md` and
 * `docs/14-trips-tags-ui.md` §2.
 */
class AppNavStateTest {
    @Test
    fun `back from tags opened directly from the trips list clears straight to the management destination`() {
        val onTripsList = AppNavState.management(ManagementDestination.EVENTS)
        val withTags = onTripsList.copy(overlay = AppOverlay.Tags(tripId = null))

        val afterBack = withTags.back()

        assertEquals(onTripsList, afterBack)
    }

    @Test
    fun `back from budgets opened from a notification clears straight to the analysis section`() {
        val onAnalysis = AppNavState.topLevel(TopLevelSection.ANALYSIS)
        val withBudgets = onAnalysis.copy(overlay = AppOverlay.Budgets(tripId = null))

        val afterBack = withBudgets.back()

        assertEquals(onAnalysis, afterBack)
    }

    @Test
    fun `trips list to trip detail to tags back back lands on the trips list`() {
        val tripsList = AppNavState.management(ManagementDestination.EVENTS)
        val tripDetail = tripsList.copy(overlay = AppOverlay.TripDetail(tripId = "mallorca"))
        val tags = tripDetail.copy(
            overlay = AppOverlay.Tags(tripId = "mallorca", returnTo = tripDetail.overlay),
        )

        val afterFirstBack = tags.back()
        assertEquals(tripDetail, afterFirstBack)

        val afterSecondBack = afterFirstBack.back()
        assertEquals(tripsList, afterSecondBack)
    }

    @Test
    fun `dashboard to trip detail to budgets back back lands on the dashboard`() {
        val dashboard = AppNavState.Home
        val tripDetail = dashboard.copy(overlay = AppOverlay.TripDetail(tripId = "mallorca"))
        val budgets = tripDetail.copy(
            overlay = AppOverlay.Budgets(tripId = "mallorca", returnTo = tripDetail.overlay),
        )

        val afterFirstBack = budgets.back()
        assertEquals(tripDetail, afterFirstBack)

        val afterSecondBack = afterFirstBack.back()
        assertEquals(dashboard, afterSecondBack)
    }
}
