package com.gestorfinances.app.ui.navigation

import com.gestorfinances.app.ui.management.ManagementDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
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
    fun `every Més choice opens its own page without preset context`() {
        val routes = ManagementDestination.entries.map { it.route() }

        assertEquals(ManagementDestination.entries.size, routes.map { it::class }.distinct().size)
        assertEquals(Route.Accounts(), ManagementDestination.ACCOUNTS.route())
        assertEquals(
            listOf(Route.Accounts(), Route.People, Route.Trips, Route.Settings),
            routes,
        )
    }
}
