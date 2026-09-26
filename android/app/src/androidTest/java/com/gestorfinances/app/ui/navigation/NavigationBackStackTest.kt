package com.gestorfinances.app.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Back-stack contracts of [openSection], [openManagement], and contextual pages, on a real NavController. */
class NavigationBackStackTest {

    @Test
    fun aMesPageReturnsToTheSectionItWasOpenedFrom() = onNav {
        openManagement(Route.Accounts())
        assertStack("Dashboard", "Accounts")
        popBackStack()
        assertStack("Dashboard")

        openSection(TopLevelSection.MOVEMENTS)
        openManagement(Route.Accounts())
        // A second Més page replaces the first, so Back still returns to the section.
        openManagement(Route.People)
        assertStack("Dashboard", "Movements", "People")
        popBackStack()
        assertStack("Dashboard", "Movements")

        // Contextual management from a section page.
        openSection(TopLevelSection.ANALYSIS)
        openManagement(Route.Budgets(addForCategoryId = "food"))
        assertStack("Dashboard", "Analysis", "Budgets")
        popBackStack()
        assertStack("Dashboard", "Analysis")
    }

    @Test
    fun contextualAnalysisIsAStackEntryThatBackLeaves() = onNav {
        openManagement(Route.Accounts())
        navigate(Route.Analysis(accountId = "a1", accountName = "Compte"))

        assertStack("Dashboard", "Accounts", "Analysis")
        assertEquals(Route.Analysis(accountId = "a1", accountName = "Compte"), currentBackStackEntry!!.toRoute<Route.Analysis>())
        assertEquals(TopLevelSection.ANALYSIS, currentDestination.section())

        popBackStack()
        assertStack("Dashboard", "Accounts")
        assertEquals(TopLevelSection.MANAGEMENT, currentDestination.section())
    }

    @Test
    fun tripPagesUnwindOneLevelAtATime() = onNav {
        openManagement(Route.Trips)
        navigate(Route.TripDetail("t1"))
        navigate(Route.TripBudgets("t1"))
        assertStack("Dashboard", "Trips", "TripDetail", "TripBudgets")

        popBackStack()
        assertStack("Dashboard", "Trips", "TripDetail")
        assertEquals("t1", currentBackStackEntry!!.toRoute<Route.TripDetail>().tripId)

        popBackStack()
        assertStack("Dashboard", "Trips")
    }

    @Test
    fun relocatedManagementReturnsToItsContext() = onNav {
        openManagement(Route.Accounts())
        navigate(Route.Goals())
        assertStack("Dashboard", "Accounts", "Goals")
        popBackStack()
        navigate(Route.Goals(accountId = "a1"))
        assertEquals("a1", currentBackStackEntry!!.toRoute<Route.Goals>().accountId)
        popBackStack()
        assertStack("Dashboard", "Accounts")

        openManagement(Route.Trips)
        navigate(Route.Tags)
        assertStack("Dashboard", "Trips", "Tags")
        popBackStack()
        assertStack("Dashboard", "Trips")

        openSection(TopLevelSection.MOVEMENTS)
        navigate(Route.Recurring)
        assertEquals(TopLevelSection.MOVEMENTS, currentDestination.section())
        assertStack("Dashboard", "Movements", "Recurring")
        popBackStack()
        assertStack("Dashboard", "Movements")

        // The movement sheet's category picker pushes a page above its original context.
        val formContextId = currentBackStackEntry!!.id
        navigate(Route.Categories)
        navigate(Route.Budgets(addForCategoryId = "food"))
        assertStack("Dashboard", "Movements", "Categories", "Budgets")
        popBackStack()
        assertStack("Dashboard", "Movements", "Categories")
        popBackStack()
        assertEquals(formContextId, currentBackStackEntry!!.id)

        openSection(TopLevelSection.DASHBOARD)
        navigate(Route.Budgets())
        assertStack("Dashboard", "Budgets")
        popBackStack()
        assertStack("Dashboard")

        openManagement(Route.Settings)
        popBackStack()
        assertStack("Dashboard")
    }

    @Test
    fun mesReplacesRecurringRatherThanTreatingItAsARoot() = onNav {
        openSection(TopLevelSection.MOVEMENTS)
        navigate(Route.Recurring)
        openManagement(Route.Accounts())
        assertStack("Dashboard", "Movements", "Accounts")
        popBackStack()
        assertStack("Dashboard", "Movements")
    }

    @Test
    fun aBottomBarChoiceDropsStaleMesPages() = onNav {
        openManagement(Route.Accounts())
        navigate(Route.Analysis(accountId = "a1", accountName = "Compte"))

        openSection(TopLevelSection.MOVEMENTS)
        assertStack("Dashboard", "Movements")

        openSection(TopLevelSection.ANALYSIS)
        assertStack("Dashboard", "Analysis")
        // The menu opens Analysis without the account context.
        assertEquals(Route.Analysis(), currentBackStackEntry!!.toRoute<Route.Analysis>())

        openSection(TopLevelSection.DASHBOARD)
        assertStack("Dashboard")
    }

    /** Runs [block] on the main thread against a controller with the app's start destination. */
    private fun onNav(block: NavHostController.() -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            runCatching {
                NavHostController(instrumentation.targetContext).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                    graph = createGraph(startDestination = Route.Dashboard) {
                        composable<Route.Dashboard> {}
                        composable<Route.Movements> {}
                        composable<Route.Analysis> {}
                        composable<Route.Accounts> {}
                        composable<Route.Categories> {}
                        composable<Route.People> {}
                        composable<Route.Trips> {}
                        composable<Route.TripDetail> {}
                        composable<Route.TripBudgets> {}
                        composable<Route.Budgets> {}
                        composable<Route.Goals> {}
                        composable<Route.Tags> {}
                        composable<Route.Recurring> {}
                        composable<Route.Settings> {}
                    }
                    block()
                }
            }.onFailure { failure = it }
        }
        failure?.let { throw it }
    }

    /** The page names on the stack, bottom first. */
    private fun NavHostController.assertStack(vararg pages: String) {
        val stack = currentBackStack.value.mapNotNull { entry ->
            entry.destination.route?.substringBefore('?')?.substringBefore('/')?.substringAfterLast('.')
        }
        assertEquals(pages.toList(), stack)
    }
}
