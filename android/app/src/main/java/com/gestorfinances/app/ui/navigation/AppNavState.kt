package com.gestorfinances.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.management.ManagementDestination

/** The four bottom-bar destinations (docs/07 §1). */
enum class TopLevelSection(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    MOVEMENTS(R.string.nav_movements, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ANALYSIS(R.string.nav_analysis, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    MANAGEMENT(R.string.nav_management, Icons.Filled.Tune, Icons.Outlined.Tune),
}

/** The route/chrome choices used by the app shell (docs/18 WP1.2). */
enum class RouteChrome(
    val showsGlobalNavigation: Boolean,
) {
    ROOT_BOTTOM_NAV(showsGlobalNavigation = true),
    MANAGEMENT_CHILD_BOTTOM_NAV(showsGlobalNavigation = true),
    FOCUSED_PAGE(showsGlobalNavigation = false),
}

/**
 * A child page layered above the current [TopLevelSection], reached from within a section:
 * Budgets from Analysis or from an event, Tags from an event. It carries the optional trip
 * context it was opened with so Back returns to the right place.
 *
 * Focused overlays carry an optional [returnTo] overlay: when a movement form/detail or
 * [Budgets]/[Tags] is opened from [TripDetail], `returnTo` holds that page so
 * [AppNavState.back] restores it instead of clearing straight to the underlying section. Opened
 * any other way (from the Trips list, Analysis, or a notification), `returnTo` stays null and
 * Back behaves exactly as before.
 */
sealed interface AppOverlay {
    val tripId: String?

    /** Optional focused page to reveal when this overlay is dismissed. */
    val returnTo: AppOverlay?
        get() = null

    data class Budgets(
        override val tripId: String?,
        override val returnTo: AppOverlay? = null,
    ) : AppOverlay

    data class Tags(
        override val tripId: String?,
        override val returnTo: AppOverlay? = null,
    ) : AppOverlay

    data class TripDetail(override val tripId: String) : AppOverlay

    /**
     * Movement create/edit, reachable from any screen (Dashboard FAB, Trip Detail's "new
     * movement", a person's debt-payment action, Movements itself) — previously rendered by a
     * global `MovementDialogHost` regardless of `section`; now a page like the others here.
     */
    data class MovementForm(
        override val tripId: String? = null,
        val debtPayerPersonId: String? = null,
        override val returnTo: AppOverlay? = null,
    ) : AppOverlay

    /** Movement detail, reachable from the same range of screens as [MovementForm]. */
    data class MovementDetail(
        val movementId: String,
        override val returnTo: AppOverlay? = null,
    ) : AppOverlay {
        override val tripId: String? = null
    }
}

/**
 * The whole shell navigation as a single value: the selected bottom-bar [section], the optional
 * Gestió child page, and an optional [overlay] on top. This replaces the previous set of
 * overlapping boolean flags (`showBudgets`/`showTags` + context ids); Back is the pure [back]
 * reducer that encodes the documented behaviour (docs/07 §1).
 */
data class AppNavState(
    val section: TopLevelSection,
    val managementDestination: ManagementDestination? = null,
    val overlay: AppOverlay? = null,
) {
    /** Route/chrome matrix used by the shell (docs/18 WP1.2). */
    val routeChrome: RouteChrome
        get() = when {
            overlay != null -> RouteChrome.FOCUSED_PAGE
            section == TopLevelSection.MANAGEMENT && managementDestination != null ->
                RouteChrome.MANAGEMENT_CHILD_BOTTOM_NAV
            else -> RouteChrome.ROOT_BOTTOM_NAV
        }

    val canNavigateBack: Boolean
        get() = overlay != null ||
            managementDestination != null ||
            section != TopLevelSection.DASHBOARD

    /** Pop one level: overlay → Gestió child → top-level → Inici. */
    fun back(): AppNavState = when {
        overlay?.returnTo != null -> copy(overlay = overlay.returnTo)
        overlay != null -> copy(overlay = null)
        managementDestination != null -> copy(managementDestination = null)
        section != TopLevelSection.DASHBOARD -> Home
        else -> this
    }

    companion object {
        val Home = AppNavState(TopLevelSection.DASHBOARD)

        /** Switch to a top-level section, clearing any Gestió child and overlay. */
        fun topLevel(section: TopLevelSection) = AppNavState(section = section)

        /** Open the Gestió hub (null) or one of its child pages. */
        fun management(destination: ManagementDestination? = null) =
            AppNavState(section = TopLevelSection.MANAGEMENT, managementDestination = destination)
    }
}
