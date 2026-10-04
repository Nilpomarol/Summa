package com.gestorfinances.app.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Back-stack contracts of [openSection] and contextual pages, on a real NavController. */
class NavigationBackStackTest {

    @Test
    fun hubPagesStackAboveTheHubAndBackUnwindsThem() = onNav {
        openSection(TopLevelSection.MANAGEMENT)
        navigate(Route.Accounts())
        navigate(Route.AccountDetail("a1"))
        assertStack("Dashboard", "More", "Accounts", "AccountDetail")
        assertEquals("a1", currentBackStackEntry!!.toRoute<Route.AccountDetail>().accountId)

        popBackStack()
        popBackStack()
        assertStack("Dashboard", "More")
        popBackStack()
        assertStack("Dashboard")
    }

    @Test
    fun contextualPagesReturnToTheirOrigin() = onNav {
        // Ledger → Analysis returns to the ledger, not the account list.
        openSection(TopLevelSection.MANAGEMENT)
        navigate(Route.Accounts())
        navigate(Route.AccountDetail("a1"))
        navigate(Route.Analysis(accountId = "a1", accountName = "Compte"))
        popBackStack()
        assertStack("Dashboard", "More", "Accounts", "AccountDetail")

        // A person page survives a visit elsewhere.
        openSection(TopLevelSection.MANAGEMENT)
        navigate(Route.People)
        navigate(Route.PersonDetail("p1"))
        navigate(Route.Movements)
        popBackStack()
        assertStack("Dashboard", "More", "People", "PersonDetail")
        assertEquals("p1", currentBackStackEntry!!.toRoute<Route.PersonDetail>().personId)

        // Contextual management from a section page.
        openSection(TopLevelSection.ANALYSIS)
        navigate(Route.Budgets)
        assertStack("Dashboard", "Analysis", "Budgets")
        popBackStack()
        assertStack("Dashboard", "Analysis")

        openSection(TopLevelSection.MOVEMENTS)
        navigate(Route.Recurring)
        navigate(Route.Accounts())
        popBackStack()
        assertStack("Dashboard", "Movements", "Recurring")
    }

    @Test
    fun tripPagesUnwindOneLevelAtATime() = onNav {
        openSection(TopLevelSection.MANAGEMENT)
        navigate(Route.Trips)
        navigate(Route.TripDetail("t1"))
        navigate(Route.TagDetail("tag1"))
        assertStack("Dashboard", "More", "Trips", "TripDetail", "TagDetail")

        popBackStack()
        assertStack("Dashboard", "More", "Trips", "TripDetail")
        assertEquals("t1", currentBackStackEntry!!.toRoute<Route.TripDetail>().tripId)

        popBackStack()
        assertStack("Dashboard", "More", "Trips")
    }

    @Test
    fun theCategoryPickerVisitReturnsToTheFormContext() = onNav {
        openSection(TopLevelSection.MOVEMENTS)
        val formContextId = currentBackStackEntry!!.id
        navigate(Route.Categories)
        navigate(Route.Budgets)
        assertStack("Dashboard", "Movements", "Categories", "Budgets")
        popBackStack()
        popBackStack()
        assertEquals(formContextId, currentBackStackEntry!!.id)
    }

    @Test
    fun aBottomBarChoiceDropsStalePages() = onNav {
        openSection(TopLevelSection.MANAGEMENT)
        navigate(Route.Accounts())
        navigate(Route.Analysis(accountId = "a1", accountName = "Compte"))

        openSection(TopLevelSection.MOVEMENTS)
        assertStack("Dashboard", "Movements")

        openSection(TopLevelSection.ANALYSIS)
        assertStack("Dashboard", "Analysis")
        // The menu opens Analysis without the account context.
        assertEquals(Route.Analysis(), currentBackStackEntry!!.toRoute<Route.Analysis>())

        openSection(TopLevelSection.MANAGEMENT)
        assertStack("Dashboard", "More")

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
                        composable<Route.More> {}
                        composable<Route.Accounts> {}
                        composable<Route.AccountDetail> {}
                        composable<Route.Categories> {}
                        composable<Route.People> {}
                        composable<Route.PersonDetail> {}
                        composable<Route.Trips> {}
                        composable<Route.TripDetail> {}
                        composable<Route.Budgets> {}
                        composable<Route.TagDetail> {}
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
