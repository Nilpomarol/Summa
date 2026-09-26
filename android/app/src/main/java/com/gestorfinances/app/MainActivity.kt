package com.gestorfinances.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gestorfinances.app.data.repository.DatabaseMeta
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.notifications.DESTINATION_ACCOUNTS
import com.gestorfinances.app.notifications.DESTINATION_BUDGETS
import com.gestorfinances.app.notifications.DESTINATION_RECURRING
import com.gestorfinances.app.notifications.EXTRA_NOTIFICATION_DESTINATION
import com.gestorfinances.app.notifications.canPostFinanceNotifications
import com.gestorfinances.app.ui.accounts.AccountsScreen
import com.gestorfinances.app.ui.accounts.accountsViewModel
import com.gestorfinances.app.ui.analysis.AnalysisScreen
import com.gestorfinances.app.ui.analysis.analysisViewModel
import com.gestorfinances.app.ui.budgets.BudgetsPage
import com.gestorfinances.app.ui.budgets.BudgetsScreen
import com.gestorfinances.app.ui.budgets.budgetsViewModel
import com.gestorfinances.app.ui.categories.CategoriesScreen
import com.gestorfinances.app.ui.categories.categoriesViewModel
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.dashboard.DashboardScreen
import com.gestorfinances.app.ui.dashboard.dashboardViewModel
import com.gestorfinances.app.ui.goals.GoalsScreen
import com.gestorfinances.app.ui.goals.goalsViewModel
import com.gestorfinances.app.ui.management.ManagementDestination
import com.gestorfinances.app.ui.management.ManagementSheet
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSheet
import com.gestorfinances.app.ui.movements.MovementSheets
import com.gestorfinances.app.ui.movements.MovementsScreen
import com.gestorfinances.app.ui.movements.movementsViewModel
import com.gestorfinances.app.ui.navigation.Route
import com.gestorfinances.app.ui.navigation.TopLevelSection
import com.gestorfinances.app.ui.navigation.isFocusedPage
import com.gestorfinances.app.ui.navigation.openManagement
import com.gestorfinances.app.ui.navigation.openSection
import com.gestorfinances.app.ui.navigation.route
import com.gestorfinances.app.ui.navigation.section
import com.gestorfinances.app.ui.onboarding.OnboardingScreen
import com.gestorfinances.app.ui.onboarding.onboardingViewModel
import com.gestorfinances.app.ui.people.PeopleScreen
import com.gestorfinances.app.ui.people.peopleViewModel
import com.gestorfinances.app.ui.recurring.RecurringReminders
import com.gestorfinances.app.ui.recurring.RecurringScreen
import com.gestorfinances.app.ui.recurring.recurringViewModel
import com.gestorfinances.app.ui.settings.SettingsEffect
import com.gestorfinances.app.ui.settings.SettingsMessage
import com.gestorfinances.app.ui.settings.SettingsScreen
import com.gestorfinances.app.ui.settings.settingsViewModel
import com.gestorfinances.app.ui.tags.TagsScreen
import com.gestorfinances.app.ui.tags.tagsViewModel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import com.gestorfinances.app.ui.theme.ThemeMode
import com.gestorfinances.app.ui.trips.TripDetailScreen
import com.gestorfinances.app.ui.trips.TripsScreen
import com.gestorfinances.app.ui.trips.tripsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notificationDestinations = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationDestinations.value = intent.notificationDestination()
        val app = application as GestorFinancesApp
        val appContainer = app.container
        val pendingSnackbarMessage = app.consumePendingSnackbarMessage()
        setContent {
            val themeMode by appContainer.themePreferences.mode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var databaseState: DatabaseState by remember { mutableStateOf(DatabaseState.Checking) }
            var notificationPermissionGranted by remember {
                mutableStateOf(canPostFinanceNotifications())
            }
            val notificationDestination by notificationDestinations.collectAsState()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                notificationPermissionGranted = granted
            }

            LaunchedEffect(Unit) {
                databaseState = withContext(Dispatchers.IO) {
                    try {
                        DatabaseState.Ready(appContainer.metaRepository.load())
                    } catch (error: RuntimeException) {
                        DatabaseState.Failed(error.message ?: error.javaClass.simpleName)
                    }
                }
            }

            LaunchedEffect(databaseState, notificationPermissionGranted) {
                if (databaseState is DatabaseState.Ready) {
                    withContext(Dispatchers.IO) {
                        runCatching { appContainer.notificationCoordinator.refreshNotifications() }
                    }
                }
            }

            GestorFinancesTheme(darkTheme = darkTheme) {
                AppShell(
                    appContainer = appContainer,
                    databaseState = databaseState,
                    notificationDestination = notificationDestination,
                    onNotificationDestinationConsumed = { notificationDestinations.value = null },
                    notificationPermissionGranted = notificationPermissionGranted,
                    pendingSnackbarMessage = pendingSnackbarMessage,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationPermissionGranted = true
                        }
                    },
                    onRecreateApp = { message ->
                        app.postPendingSnackbarMessage(
                            PendingSnackbarMessage(
                                messageRes = message.messageRes,
                                arg = message.arg,
                            ),
                        )
                        app.resetContainer()
                        viewModelStore.clear()
                        recreate()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestinations.value = intent.notificationDestination()
    }
}

@Composable
private fun AppShell(
    appContainer: AppContainer,
    databaseState: DatabaseState,
    notificationDestination: String?,
    onNotificationDestinationConsumed: () -> Unit,
    notificationPermissionGranted: Boolean,
    pendingSnackbarMessage: PendingSnackbarMessage?,
    onRequestNotificationPermission: () -> Unit,
    onRecreateApp: (SettingsMessage) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (databaseState) {
            DatabaseState.Checking,
            is DatabaseState.Failed,
            -> DatabaseStatus(databaseState = databaseState)
            is DatabaseState.Ready -> LedgerShell(
                appContainer = appContainer,
                notificationDestination = notificationDestination,
                onNotificationDestinationConsumed = onNotificationDestinationConsumed,
                notificationPermissionGranted = notificationPermissionGranted,
                pendingSnackbarMessage = pendingSnackbarMessage,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRecreateApp = onRecreateApp,
            )
        }
    }
}

@Composable
private fun LedgerShell(
    appContainer: AppContainer,
    notificationDestination: String?,
    onNotificationDestinationConsumed: () -> Unit,
    notificationPermissionGranted: Boolean,
    pendingSnackbarMessage: PendingSnackbarMessage?,
    onRequestNotificationPermission: () -> Unit,
    onRecreateApp: (SettingsMessage) -> Unit,
) {
    // The restore flow (and the checkpoint-copy export fallback) close the single shared
    // SqlDriver that every repository/ViewModel below depends on, mid-operation, until the
    // Activity recreates with a fresh AppContainer. Gate the whole shell — not just Settings'
    // own buttons — the instant that happens so no other tab, the FAB, or a background
    // LaunchedEffect can touch the closed driver in the meantime.
    val isDatabaseBeingReplaced by appContainer.backupSnapshotService.isDatabaseBeingReplaced
        .collectAsState()

    // Settings owns the only effects that outlive its own screen: closing the shared driver pulls
    // SettingsScreen out of composition before the restore reports back, so collecting there lost
    // the RecreateApp effect (MutableSharedFlow drops emissions with no subscriber) and left the
    // overlay up forever on an otherwise successful restore. Collect above the early return, where
    // the subscriber survives the swap.
    val settingsViewModel = settingsViewModel(appContainer)
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        settingsViewModel.onBackupFolderSelected(uri?.toString())
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.PickBackupFolder -> backupFolderLauncher.launch(null)
                is SettingsEffect.RecreateApp -> onRecreateApp(effect.message)
            }
        }
    }

    if (isDatabaseBeingReplaced) {
        DatabaseReplacementOverlay(modifier = Modifier.fillMaxSize())
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val pendingSnackbarText = pendingSnackbarMessage?.let { message ->
        message.arg?.let { stringResource(message.messageRes, it) }
            ?: stringResource(message.messageRes)
    }
    LaunchedEffect(pendingSnackbarText) {
        if (pendingSnackbarText != null) {
            snackbarHostState.showSnackbar(pendingSnackbarText)
        }
    }

    val onboardingViewModel = onboardingViewModel(appContainer)
    val onboardingState by onboardingViewModel.state.collectAsState()
    if (onboardingState.isLoading || onboardingState.needsOnboarding) {
        OnboardingScreen(
            viewModel = onboardingViewModel,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // Movements and recurring outlive any one page: their sheets can open above every page.
    val movementsViewModel = movementsViewModel(appContainer)
    val movementsState by movementsViewModel.state.collectAsState()
    val movementForm by movementsViewModel.editor.form.collectAsState()
    val recurringViewModel = recurringViewModel(appContainer)
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    var movementSheet by remember { mutableStateOf<MovementSheet?>(null) }
    var managementMenuVisible by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.common_deleted)
    val undoLabel = stringResource(R.string.common_undo)
    val createAccountMessage = stringResource(R.string.movement_no_accounts_title)
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val showDeleteUndo: DeleteUndoHandler = { undo ->
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    // The movement detail and edit sheets read the ledger's accounts/categories/people.
    LaunchedEffect(movementsViewModel) {
        movementsViewModel.onScreenShown()
    }

    fun openMovementForm(tripId: String? = null, accountId: String? = null) {
        movementSheet = MovementSheet.Form()
        movementsViewModel.onAddClicked(
            tripId = tripId,
            accountId = accountId,
            onNoAccounts = {
                movementSheet = null
                navController.openManagement(Route.Accounts(openAddForm = true))
                showMessage(createAccountMessage)
            },
        )
    }

    fun openMovementDetail(movement: MovementSummary) {
        movementsViewModel.onDetailClicked(movement)
        movementSheet = MovementSheet.Detail
    }

    fun openMovements(filters: MovementFilters) {
        movementsViewModel.onDrillDown(filters)
        navController.navigate(Route.Movements)
    }

    fun showFromMenu(section: TopLevelSection) {
        // The ledger's ViewModel is app-wide, so a menu visit resets it explicitly; every other
        // page gets a fresh ViewModel with its fresh navigation entry.
        if (section == TopLevelSection.MOVEMENTS) movementsViewModel.resetForMenuNavigation()
        navController.openSection(section)
    }

    fun showFromMenu(destination: ManagementDestination) {
        if (destination == ManagementDestination.RECURRING) recurringViewModel.resetForMenuNavigation()
        navController.openManagement(destination.route())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!currentDestination.isFocusedPage() && movementSheet != MovementSheet.Detail) FinanceBottomBar(
                selectedSection = currentDestination.section(),
                onSelected = ::showFromMenu,
                onManagementClick = { managementMenuVisible = true },
                // Trip pages hide this bar and own a contextual add action instead.
                onAddMovement = { openMovementForm() },
            )
        },
    ) { innerPadding ->
        val pageModifier = Modifier.fillMaxSize()
        NavHost(
            navController = navController,
            startDestination = Route.Dashboard,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable<Route.Dashboard> {
                DashboardScreen(
                    viewModel = dashboardViewModel(appContainer),
                    dataVersion = movementsState.dataVersion,
                    onDrillDown = ::openMovements,
                    onMovementDetail = ::openMovementDetail,
                    onAccountAnalysis = { account ->
                        navController.navigate(Route.Analysis(accountId = account.id, accountName = account.name))
                    },
                    onViewTrip = { trip -> navController.navigate(Route.TripDetail(trip.id)) },
                    onAddTripMovement = { trip -> openMovementForm(tripId = trip.id) },
                    onViewBudgets = { navController.openManagement(Route.Budgets()) },
                    modifier = pageModifier,
                )
            }
            composable<Route.Movements> {
                MovementsScreen(
                    viewModel = movementsViewModel,
                    onAdd = { openMovementForm() },
                    onDetail = ::openMovementDetail,
                    modifier = pageModifier,
                )
            }
            composable<Route.Analysis> { entry ->
                AnalysisScreen(
                    viewModel = analysisViewModel(appContainer, entry.toRoute()),
                    dataVersion = movementsState.dataVersion,
                    modifier = pageModifier,
                )
            }
            composable<Route.Accounts> { entry ->
                AccountsScreen(
                    viewModel = accountsViewModel(appContainer, entry.toRoute()),
                    dataVersion = movementsState.dataVersion,
                    onViewGoals = { accountId -> navController.navigate(Route.Goals(accountId)) },
                    onViewAnalysis = { accountId, accountName ->
                        navController.navigate(Route.Analysis(accountId = accountId, accountName = accountName))
                    },
                    onMovementDetail = ::openMovementDetail,
                    onAddExpense = { accountId -> openMovementForm(accountId = accountId) },
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Categories> {
                CategoriesScreen(
                    viewModel = categoriesViewModel(appContainer),
                    dataVersion = movementsState.dataVersion,
                    onViewAnalysis = { categoryId, categoryName ->
                        navController.navigate(Route.Analysis(categoryId = categoryId, categoryName = categoryName))
                    },
                    onDefineBudget = { categoryId ->
                        navController.openManagement(Route.Budgets(addForCategoryId = categoryId))
                    },
                    onMovementDetail = ::openMovementDetail,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.People> {
                PeopleScreen(
                    viewModel = peopleViewModel(appContainer),
                    dataVersion = movementsState.dataVersion,
                    onOpenDebtSource = { sourceId ->
                        movementsViewModel.onDetailSourceClicked(sourceId)
                        navController.openSection(TopLevelSection.MOVEMENTS)
                        movementSheet = MovementSheet.Detail
                    },
                    onAddDebtForPerson = { person ->
                        // The form opens above People rather than switching to the ledger.
                        movementsViewModel.onAddClicked(tripId = null, debtPayerPersonId = person.id)
                        movementSheet = MovementSheet.Form()
                    },
                    onMessageCopied = showMessage,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Trips> {
                TripsScreen(
                    viewModel = tripsViewModel(appContainer),
                    dataVersion = movementsState.dataVersion,
                    onOpenDetail = { trip -> navController.navigate(Route.TripDetail(trip.id)) },
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.TripDetail> { entry ->
                val tripId = entry.toRoute<Route.TripDetail>().tripId
                val viewModel = tripsViewModel(appContainer)
                // Movements added or edited from this page change the totals it shows.
                LaunchedEffect(viewModel, tripId, movementsState.dataVersion) {
                    viewModel.onDetailOpened(tripId)
                }
                TripDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onManageBudget = { navController.navigate(Route.TripBudgets(tripId)) },
                    onAddMovement = { openMovementForm(tripId = tripId) },
                    onMovementDetail = ::openMovementDetail,
                    modifier = pageModifier,
                )
            }
            composable<Route.TripBudgets> { entry ->
                val tripId = entry.toRoute<Route.TripBudgets>().tripId
                BudgetsScreen(
                    viewModel = budgetsViewModel(appContainer),
                    onBack = { navController.popBackStack() },
                    contextTripId = tripId,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Budgets> { entry ->
                BudgetsPage(
                    appContainer = appContainer,
                    dataVersion = movementsState.dataVersion,
                    addForCategoryId = entry.toRoute<Route.Budgets>().addForCategoryId,
                    onBack = { navController.popBackStack() },
                    onViewCategoryAnalysis = { categoryId, categoryName ->
                        navController.navigate(Route.Analysis(categoryId = categoryId, categoryName = categoryName))
                    },
                    onMovementDetail = ::openMovementDetail,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Goals> { entry ->
                GoalsScreen(
                    viewModel = goalsViewModel(appContainer, entry.toRoute<Route.Goals>().accountId),
                    dataVersion = movementsState.dataVersion,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Tags> {
                TagsScreen(
                    viewModel = tagsViewModel(appContainer),
                    contextTripId = null,
                    onBack = { navController.popBackStack() },
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Recurring> {
                RecurringScreen(
                    viewModel = recurringViewModel,
                    dataVersion = movementsState.dataVersion,
                    onMovementDetail = ::openMovementDetail,
                    modifier = pageModifier,
                )
            }
            composable<Route.Settings> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    notificationPermissionGranted = notificationPermissionGranted,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onBack = { navController.popBackStack() },
                    modifier = pageModifier,
                )
            }
        }

        // Runs after the NavHost above has set its graph.
        LaunchedEffect(notificationDestination) {
            val route = when (notificationDestination) {
                DESTINATION_RECURRING -> Route.Recurring
                DESTINATION_BUDGETS -> Route.Budgets()
                DESTINATION_ACCOUNTS -> Route.Accounts()
                else -> null
            }
            if (route != null) {
                navController.openManagement(route)
                onNotificationDestinationConsumed()
            }
        }
    }

    MovementSheets(
        sheet = movementSheet,
        onSheetChange = { movementSheet = it },
        viewModel = movementsViewModel,
        onEditContribution = { movementId ->
            navController.openManagement(Route.Accounts(editContributionId = movementId))
        },
        onDeleteCommitted = showDeleteUndo,
    )
    if (managementMenuVisible) {
        ManagementSheet(
            onDestinationSelected = ::showFromMenu,
            onDismiss = { managementMenuVisible = false },
        )
    }
    RecurringReminders(
        viewModel = recurringViewModel,
        otherSheetOpen = movementsState.hasOpenDialog || movementForm != null,
        onDeleteCommitted = showDeleteUndo,
    )
}

/** Every section slot carries the same tile, so the row reads on one baseline. */
private val NAV_TILE_SIZE = 44.dp

/** The action tile is the bigger one, and carries no label of its own. */
private val NAV_ADD_TILE_SIZE = 60.dp

private val NAV_BAR_HEIGHT = 72.dp

/**
 * Global navigation: four section slots on the bottom edge of the page, each the app's
 * rounded-square tile with its name under it — empty for a section you are not in, ink-filled for
 * the one you are. The action that adds a movement is not a section, so it does not line up with
 * them: it is cut bigger and lit, and sits centred in the gap they leave.
 */
@Composable
private fun FinanceBottomBar(
    selectedSection: TopLevelSection,
    onSelected: (TopLevelSection) -> Unit,
    onManagementClick: () -> Unit,
    onAddMovement: () -> Unit,
) {
    val colors = FinanceTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.bottomBarSurface,
        contentColor = colors.bottomBarContent,
    ) {
        Column {
            HorizontalDivider(color = colors.bottomBarDivider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(NAV_BAR_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomBarItem(
                    section = TopLevelSection.DASHBOARD,
                    selected = selectedSection == TopLevelSection.DASHBOARD,
                    onClick = { onSelected(TopLevelSection.DASHBOARD) },
                )
                BottomBarItem(
                    section = TopLevelSection.MOVEMENTS,
                    selected = selectedSection == TopLevelSection.MOVEMENTS,
                    onClick = { onSelected(TopLevelSection.MOVEMENTS) },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    AddMovementButton(onClick = onAddMovement)
                }
                BottomBarItem(
                    section = TopLevelSection.ANALYSIS,
                    selected = selectedSection == TopLevelSection.ANALYSIS,
                    onClick = { onSelected(TopLevelSection.ANALYSIS) },
                )
                BottomBarItem(
                    section = TopLevelSection.MANAGEMENT,
                    selected = selectedSection == TopLevelSection.MANAGEMENT,
                    onClick = onManagementClick,
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    section: TopLevelSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val tileColor by animateColorAsState(
        targetValue = if (selected) colors.bottomBarActive else Color.Transparent,
        label = "navTile",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) colors.bottomBarSurface else colors.bottomBarContent,
        label = "navIcon",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(NAV_TILE_SIZE)
                .background(tileColor, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(section.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.bottomBarActive else colors.bottomBarContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The one lit thing in the bar. The hero's ink runs corner to corner through the glow that lights
 * the dashboard panel, a highlight sits where the light would fall, and a pale rim catches the
 * edge — at this size a flat plum would read as a plain square.
 */
@Composable
private fun AddMovementButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(NAV_ADD_TILE_SIZE)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow,
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.heroOnSurface.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.heroGlow,
                            colors.bottomBarActive,
                            colors.heroInkBottom,
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                )
                .drawBehind {
                    val center = Offset(size.width * 0.24f, size.height * 0.16f)
                    val radius = size.maxDimension * 0.75f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.heroGlow.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.movement_list_add),
                tint = colors.heroOnSurface,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun DatabaseStatus(databaseState: DatabaseState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (databaseState is DatabaseState.Failed) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = databaseState.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (databaseState is DatabaseState.Checking) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun DatabaseReplacementOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.settings_backup_replacing_database),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun DatabaseState.message(): String =
    when (this) {
        DatabaseState.Checking -> stringResource(R.string.home_database_checking)
        is DatabaseState.Ready -> stringResource(
            R.string.home_database_ready,
            meta.schemaVersion,
            meta.snapshotVersion,
        )
        is DatabaseState.Failed -> stringResource(R.string.home_database_failed)
    }

private sealed interface DatabaseState {
    data object Checking : DatabaseState
    data class Ready(val meta: DatabaseMeta) : DatabaseState
    data class Failed(val message: String) : DatabaseState
}

private fun Intent?.notificationDestination(): String? =
    this?.getStringExtra(EXTRA_NOTIFICATION_DESTINATION)

@Preview(showBackground = true)
@Composable
private fun DatabaseStatusPreview() {
    GestorFinancesTheme {
        DatabaseStatus(databaseState = DatabaseState.Checking)
    }
}
