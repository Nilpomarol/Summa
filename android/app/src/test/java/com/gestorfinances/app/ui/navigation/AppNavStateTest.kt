package com.gestorfinances.app.ui.navigation

import com.gestorfinances.app.ui.management.ManagementDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the [AppNavState.back] reducer, in particular the `returnTo`-carrying overlays
 * (`Tags`/`Budgets` opened from within [AppOverlay.TripDetail]) that keep Trip Detail on the
 * back-stack instead of losing it — see `docs/16-android-audit-findings.md` and
 * `docs/14-trips-tags-ui.md` §2.
 */
class AppNavStateTest {
    @Test
    fun `global navigation keeps all four destinations visible around the FAB`() {
        assertEquals(
            listOf(
                TopLevelSection.DASHBOARD,
                TopLevelSection.MOVEMENTS,
                TopLevelSection.ANALYSIS,
                TopLevelSection.MANAGEMENT,
            ),
            TopLevelSection.entries.toList(),
        )
        assertTrue(TopLevelSection.entries.all { it.labelRes != 0 })
    }

    @Test
    fun `root and management child routes expose the global navigation chrome`() {
        assertEquals(RouteChrome.ROOT_BOTTOM_NAV, AppNavState.Home.routeChrome)
        assertEquals(
            RouteChrome.ROOT_BOTTOM_NAV,
            AppNavState.topLevel(TopLevelSection.MOVEMENTS).routeChrome,
        )
        assertEquals(
            RouteChrome.ROOT_BOTTOM_NAV,
            AppNavState.management().routeChrome,
        )
        assertEquals(
            RouteChrome.MANAGEMENT_CHILD_BOTTOM_NAV,
            AppNavState.management(ManagementDestination.EVENTS).routeChrome,
        )
    }

    @Test
    fun `focused overlays hide global navigation chrome`() {
        val root = AppNavState.Home

        listOf(
            AppOverlay.TripDetail(tripId = "mallorca"),
            AppOverlay.MovementDetail(movementId = "movement-1"),
            AppOverlay.MovementForm(),
            AppOverlay.Tags(tripId = "mallorca"),
            AppOverlay.Budgets(tripId = "mallorca"),
        ).forEach { overlay ->
            assertEquals(RouteChrome.FOCUSED_PAGE, root.copy(overlay = overlay).routeChrome)
            check(!root.copy(overlay = overlay).routeChrome.showsGlobalNavigation)
        }
    }

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

    @Test
    fun `trip detail to movement form back returns to trip detail`() {
        val tripDetail = AppNavState.management(ManagementDestination.EVENTS).copy(
            overlay = AppOverlay.TripDetail(tripId = "mallorca"),
        )
        val movementForm = tripDetail.copy(
            overlay = AppOverlay.MovementForm(
                tripId = "mallorca",
                returnTo = tripDetail.overlay,
            ),
        )

        assertEquals(tripDetail, movementForm.back())
    }

    @Test
    fun `movement detail edit back returns to movement detail`() {
        val movementDetail = AppNavState.topLevel(TopLevelSection.MOVEMENTS).copy(
            overlay = AppOverlay.MovementDetail(movementId = "movement-1"),
        )
        val movementForm = movementDetail.copy(
            overlay = AppOverlay.MovementForm(returnTo = movementDetail.overlay),
        )

        assertEquals(movementDetail, movementForm.back())
    }
}
