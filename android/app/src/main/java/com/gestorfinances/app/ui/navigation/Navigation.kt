package com.gestorfinances.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.management.ManagementDestination
import kotlinx.serialization.Serializable

/** The four bottom-bar destinations. */
enum class TopLevelSection(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    MOVEMENTS(R.string.nav_movements, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ANALYSIS(R.string.nav_analysis, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    MANAGEMENT(R.string.nav_management, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}

/**
 * The app's pages. Arguments carry the context a page is opened with, so every visit gets its own
 * ViewModel and Back simply pops the stack.
 */
sealed interface Route {
    @Serializable data object Dashboard : Route
    @Serializable data object Movements : Route
    @Serializable data class Analysis(
        val accountId: String? = null,
        val accountName: String? = null,
        val categoryId: String? = null,
        val categoryName: String? = null,
    ) : Route
    @Serializable data class Accounts(
        val openAddForm: Boolean = false,
        val editContributionId: String? = null,
    ) : Route
    @Serializable data object Categories : Route
    @Serializable data object People : Route
    @Serializable data object Trips : Route
    @Serializable data class TripDetail(val tripId: String) : Route
    @Serializable data class TripBudgets(val tripId: String) : Route
    @Serializable data object Tags : Route
    @Serializable data object Recurring : Route
    @Serializable data class Budgets(val addForCategoryId: String? = null) : Route
    @Serializable data class Goals(val accountId: String? = null) : Route
    @Serializable data object Settings : Route
}

fun ManagementDestination.route(): Route = when (this) {
    ManagementDestination.ACCOUNTS -> Route.Accounts()
    ManagementDestination.CATEGORIES -> Route.Categories
    ManagementDestination.PEOPLE -> Route.People
    ManagementDestination.EVENTS -> Route.Trips
    ManagementDestination.TAGS -> Route.Tags
    ManagementDestination.RECURRING -> Route.Recurring
    ManagementDestination.BUDGETS -> Route.Budgets()
    ManagementDestination.GOALS -> Route.Goals()
    ManagementDestination.SETTINGS -> Route.Settings
}

/** The bottom-bar section a page belongs to; every other page (Més and trip pages) is MANAGEMENT. */
fun NavDestination?.section(): TopLevelSection = when {
    this == null || hasRoute<Route.Dashboard>() -> TopLevelSection.DASHBOARD
    hasRoute<Route.Movements>() -> TopLevelSection.MOVEMENTS
    hasRoute<Route.Analysis>() -> TopLevelSection.ANALYSIS
    else -> TopLevelSection.MANAGEMENT
}

/** Trip pages are focused: they hide the global navigation and own their own actions. */
fun NavDestination?.isFocusedPage(): Boolean =
    this != null && (hasRoute<Route.TripDetail>() || hasRoute<Route.TripBudgets>())

/** A bottom-bar choice is a new visit: the stack becomes Dashboard, plus the chosen section. */
fun NavController.openSection(section: TopLevelSection) {
    val route = when (section) {
        TopLevelSection.DASHBOARD -> Route.Dashboard
        TopLevelSection.MOVEMENTS -> Route.Movements
        TopLevelSection.ANALYSIS -> Route.Analysis()
        TopLevelSection.MANAGEMENT -> error("Més opens its menu, not a page")
    }
    navigate(route) {
        popUpTo<Route.Dashboard> { inclusive = section == TopLevelSection.DASHBOARD }
    }
}

/**
 * Opens a Més page as a new visit on top of the section it was opened from: any Més or trip pages
 * above that section are replaced, so Back returns to the section.
 */
fun NavController.openManagement(route: Route) {
    while (currentDestination.section() == TopLevelSection.MANAGEMENT) {
        if (!popBackStack()) break
    }
    navigate(route)
}
