package com.gestorfinances.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.gestorfinances.app.data.repository.DatabaseMeta
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.ui.accounts.AccountsScreen
import com.gestorfinances.app.ui.accounts.AccountsViewModel
import com.gestorfinances.app.ui.analysis.AnalysisScreen
import com.gestorfinances.app.ui.analysis.AnalysisViewModel
import com.gestorfinances.app.ui.budgets.BudgetsScreen
import com.gestorfinances.app.ui.budgets.BudgetsViewModel
import com.gestorfinances.app.ui.categories.CategoriesScreen
import com.gestorfinances.app.ui.categories.CategoriesViewModel
import com.gestorfinances.app.ui.dashboard.DashboardScreen
import com.gestorfinances.app.ui.dashboard.DashboardViewModel
import com.gestorfinances.app.ui.management.ManagementDestination
import com.gestorfinances.app.ui.management.ManagementScreen
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementDialogHost
import com.gestorfinances.app.ui.movements.MovementsScreen
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.navigation.AppNavState
import com.gestorfinances.app.ui.navigation.AppOverlay
import com.gestorfinances.app.ui.navigation.TopLevelSection
import com.gestorfinances.app.ui.onboarding.OnboardingScreen
import com.gestorfinances.app.ui.onboarding.OnboardingViewModel
import com.gestorfinances.app.ui.people.PeopleScreen
import com.gestorfinances.app.ui.people.PeopleViewModel
import com.gestorfinances.app.ui.recurring.RecurringScreen
import com.gestorfinances.app.ui.recurring.RecurringViewModel
import com.gestorfinances.app.ui.settings.SettingsScreen
import com.gestorfinances.app.ui.settings.SettingsViewModel
import com.gestorfinances.app.ui.tags.TagsScreen
import com.gestorfinances.app.ui.tags.TagsViewModel
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import com.gestorfinances.app.ui.trips.TripsScreen
import com.gestorfinances.app.ui.trips.TripsViewModel
import com.gestorfinances.app.notifications.DESTINATION_ACCOUNTS
import com.gestorfinances.app.notifications.DESTINATION_BUDGETS
import com.gestorfinances.app.notifications.DESTINATION_RECURRING
import com.gestorfinances.app.notifications.EXTRA_NOTIFICATION_DESTINATION
import com.gestorfinances.app.notifications.canPostFinanceNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notificationDestinations = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationDestinations.value = intent.notificationDestination()
        val appContainer = (application as GestorFinancesApp).container
        setContent {
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

            GestorFinancesTheme {
                AppShell(
                    appContainer = appContainer,
                    databaseState = databaseState,
                    viewModelStoreOwner = this@MainActivity,
                    notificationDestination = notificationDestination,
                    onNotificationDestinationConsumed = { notificationDestinations.value = null },
                    notificationPermissionGranted = notificationPermissionGranted,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationPermissionGranted = true
                        }
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
    viewModelStoreOwner: ViewModelStoreOwner,
    notificationDestination: String?,
    onNotificationDestinationConsumed: () -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
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
                viewModelStoreOwner = viewModelStoreOwner,
                notificationDestination = notificationDestination,
                onNotificationDestinationConsumed = onNotificationDestinationConsumed,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
        }
    }
}

@Composable
private fun LedgerShell(
    appContainer: AppContainer,
    viewModelStoreOwner: ViewModelStoreOwner,
    notificationDestination: String?,
    onNotificationDestinationConsumed: () -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
) {
    var nav by remember { mutableStateOf(AppNavState.Home) }
    val onboardingViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            OnboardingViewModel.Factory(
                accountRepository = appContainer.accountRepository,
                categoryRepository = appContainer.categoryRepository,
            ),
        )[OnboardingViewModel::class.java]
    }
    val accountsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            AccountsViewModel.Factory(
                accountRepository = appContainer.accountRepository,
                movementRepository = appContainer.movementRepository,
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[AccountsViewModel::class.java]
    }
    val categoriesViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            CategoriesViewModel.Factory(
                categoryRepository = appContainer.categoryRepository,
                analysisRepository = appContainer.analysisRepository,
                movementRepository = appContainer.movementRepository,
            ),
        )[CategoriesViewModel::class.java]
    }
    val dashboardViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            DashboardViewModel.Factory(
                analysisRepository = appContainer.analysisRepository,
                accountRepository = appContainer.accountRepository,
                movementRepository = appContainer.movementRepository,
            ),
        )[DashboardViewModel::class.java]
    }
    val analysisViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            AnalysisViewModel.Factory(
                analysisRepository = appContainer.analysisRepository,
                templateRepository = appContainer.templateRepository,
            ),
        )[AnalysisViewModel::class.java]
    }
    val movementsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            MovementsViewModel.Factory(
                movementRepository = appContainer.movementRepository,
                accountRepository = appContainer.accountRepository,
                categoryRepository = appContainer.categoryRepository,
                personRepository = appContainer.personRepository,
                tripRepository = appContainer.tripRepository,
                tagRepository = appContainer.tagRepository,
                splitRepository = appContainer.splitRepository,
                notificationRefresher = appContainer.notificationCoordinator,
                templateRepository = appContainer.templateRepository,
            ),
        )[MovementsViewModel::class.java]
    }
    val peopleViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            PeopleViewModel.Factory(
                personRepository = appContainer.personRepository,
                categoryRepository = appContainer.categoryRepository,
                splitRepository = appContainer.splitRepository,
                movementRepository = appContainer.movementRepository,
                accountRepository = appContainer.accountRepository,
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[PeopleViewModel::class.java]
    }
    val recurringViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            RecurringViewModel.Factory(
                templateRepository = appContainer.templateRepository,
                accountRepository = appContainer.accountRepository,
                categoryRepository = appContainer.categoryRepository,
                movementRepository = appContainer.movementRepository,
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[RecurringViewModel::class.java]
    }
    val budgetsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            BudgetsViewModel.Factory(
                budgetRepository = appContainer.budgetRepository,
                categoryRepository = appContainer.categoryRepository,
                tripRepository = appContainer.tripRepository,
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[BudgetsViewModel::class.java]
    }
    val settingsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            SettingsViewModel.Factory(
                preferences = appContainer.notificationPreferences,
                dataSeeder = appContainer.dataSeeder,
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[SettingsViewModel::class.java]
    }
    val tripsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            TripsViewModel.Factory(
                tripRepository = appContainer.tripRepository,
                tripAnalysisRepository = appContainer.tripAnalysisRepository,
                movementRepository = appContainer.movementRepository,
                accountRepository = appContainer.accountRepository,
            ),
        )[TripsViewModel::class.java]
    }
    val tagsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            TagsViewModel.Factory(
                tagRepository = appContainer.tagRepository,
                tripRepository = appContainer.tripRepository,
            ),
        )[TagsViewModel::class.java]
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val onboardingState by onboardingViewModel.state.collectAsState()
    val movementsState by movementsViewModel.state.collectAsState()
    val accountsState by accountsViewModel.state.collectAsState()
    val createAccountMessage = stringResource(R.string.movement_no_accounts_title)

    LaunchedEffect(movementsViewModel) {
        movementsViewModel.onScreenShown()
    }

    LaunchedEffect(accountsViewModel) {
        accountsViewModel.onScreenShown()
    }

    LaunchedEffect(accountsState.accounts.size) {
        if (accountsState.accounts.isNotEmpty()) {
            movementsViewModel.onScreenShown()
        }
    }

    if (onboardingState.isLoading || onboardingState.needsOnboarding) {
        OnboardingScreen(
            viewModel = onboardingViewModel,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    fun showTopLevel(section: TopLevelSection) {
        nav = AppNavState.topLevel(section)
    }

    fun showManagement(destination: ManagementDestination? = null) {
        nav = AppNavState.management(destination)
    }

    fun openMovementForm(tripId: String? = null) {
        val hasAccount = movementsState.accounts.isNotEmpty() || accountsState.accounts.isNotEmpty()
        if (hasAccount) {
            movementsViewModel.onAddClicked(tripId)
        } else {
            showManagement(ManagementDestination.ACCOUNTS)
            accountsViewModel.onAddClicked()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(createAccountMessage)
            }
        }
    }

    BackHandler(enabled = nav.canNavigateBack) { nav = nav.back() }

    LaunchedEffect(notificationDestination) {
        when (notificationDestination) {
            DESTINATION_RECURRING -> {
                showManagement(ManagementDestination.RECURRING)
                onNotificationDestinationConsumed()
            }
            DESTINATION_BUDGETS -> {
                nav = AppNavState.topLevel(TopLevelSection.ANALYSIS)
                    .copy(overlay = AppOverlay.Budgets(tripId = null))
                onNotificationDestinationConsumed()
            }
            DESTINATION_ACCOUNTS -> {
                showManagement(ManagementDestination.ACCOUNTS)
                onNotificationDestinationConsumed()
            }
        }
    }

    LaunchedEffect(movementsState.dataVersion) {
        if (movementsState.dataVersion > 0L) {
            dashboardViewModel.refresh()
            analysisViewModel.refresh()
            accountsViewModel.onScreenShown()
        }
    }

    val openMovements: (MovementFilters) -> Unit = { filters ->
        movementsViewModel.onDrillDown(filters)
        showTopLevel(TopLevelSection.MOVEMENTS)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            FinanceBottomBar(
                selectedSection = nav.section,
                onSelected = ::showTopLevel,
                onAddMovement = { openMovementForm() },
            )
        },
    ) { innerPadding ->
        when (val overlay = nav.overlay) {
            is AppOverlay.Tags -> {
                TagsScreen(
                    viewModel = tagsViewModel,
                    contextTripId = overlay.tripId,
                    onBack = { nav = nav.back() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                return@Scaffold
            }
            is AppOverlay.Budgets -> {
                BudgetsScreen(
                    viewModel = budgetsViewModel,
                    onBack = { nav = nav.back() },
                    contextTripId = overlay.tripId,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                return@Scaffold
            }
            null -> Unit
        }
        when (nav.section) {
            TopLevelSection.DASHBOARD -> DashboardScreen(
                viewModel = dashboardViewModel,
                onNewMovement = { openMovementForm() },
                onViewAnalysis = { showTopLevel(TopLevelSection.ANALYSIS) },
                onSettings = { showManagement(ManagementDestination.SETTINGS) },
                onDrillDown = openMovements,
                onMovementDetail = movementsViewModel::onDetailClicked,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            TopLevelSection.MOVEMENTS -> MovementsScreen(
                viewModel = movementsViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                showDialogs = false,
            )
            TopLevelSection.ANALYSIS -> AnalysisScreen(
                viewModel = analysisViewModel,
                onDrillDown = openMovements,
                onTripDetail = { tripId ->
                    tripsViewModel.onDetailClicked(tripId)
                    showManagement(ManagementDestination.EVENTS)
                },
                onManageBudgets = {
                    nav = nav.copy(overlay = AppOverlay.Budgets(tripId = null))
                },
                onClearCategoryFilter = analysisViewModel::clearCategoryFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            TopLevelSection.MANAGEMENT -> when (nav.managementDestination) {
                null -> ManagementScreen(
                    onDestinationSelected = ::showManagement,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.ACCOUNTS -> AccountsScreen(
                    viewModel = accountsViewModel,
                    onViewAnalysis = { accountId, accountName ->
                        analysisViewModel.setAccountFilter(accountId, accountName)
                        showTopLevel(TopLevelSection.ANALYSIS)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.CATEGORIES -> CategoriesScreen(
                    viewModel = categoriesViewModel,
                    onViewAnalysis = { categoryId, categoryName ->
                        analysisViewModel.setCategoryFilter(categoryId, categoryName)
                        showTopLevel(TopLevelSection.ANALYSIS)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.PEOPLE -> PeopleScreen(
                    viewModel = peopleViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.EVENTS -> TripsScreen(
                    viewModel = tripsViewModel,
                    onNewMovement = { trip ->
                        tripsViewModel.onDetailDismissed()
                        openMovementForm(trip.id)
                    },
                    onManageTags = { tripId ->
                        tripsViewModel.onDetailDismissed()
                        nav = nav.copy(overlay = AppOverlay.Tags(tripId = tripId))
                    },
                    onManageBudget = { tripId ->
                        tripsViewModel.onDetailDismissed()
                        nav = nav.copy(overlay = AppOverlay.Budgets(tripId = tripId))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.RECURRING -> RecurringScreen(
                    viewModel = recurringViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    notificationPermissionGranted = notificationPermissionGranted,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onBack = { nav = nav.back() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
    MovementDialogHost(viewModel = movementsViewModel)
}

@Composable
private fun FinanceBottomBar(
    selectedSection: TopLevelSection,
    onSelected: (TopLevelSection) -> Unit,
    onAddMovement: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(74.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomBarItem(
                        section = TopLevelSection.DASHBOARD,
                        selected = selectedSection == TopLevelSection.DASHBOARD,
                        onClick = { onSelected(TopLevelSection.DASHBOARD) },
                        modifier = Modifier.weight(1f),
                    )
                    BottomBarItem(
                        section = TopLevelSection.MOVEMENTS,
                        selected = selectedSection == TopLevelSection.MOVEMENTS,
                        onClick = { onSelected(TopLevelSection.MOVEMENTS) },
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.weight(1f))
                    BottomBarItem(
                        section = TopLevelSection.ANALYSIS,
                        selected = selectedSection == TopLevelSection.ANALYSIS,
                        onClick = { onSelected(TopLevelSection.ANALYSIS) },
                        modifier = Modifier.weight(1f),
                    )
                    BottomBarItem(
                        section = TopLevelSection.MANAGEMENT,
                        selected = selectedSection == TopLevelSection.MANAGEMENT,
                        onClick = { onSelected(TopLevelSection.MANAGEMENT) },
                        modifier = Modifier.weight(1f),
                    )
                }
                FloatingActionButton(
                    onClick = onAddMovement,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.movement_list_add),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    section: TopLevelSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .padding(horizontal = 2.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(section.labelRes),
                style = MaterialTheme.typography.labelSmall,
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
private fun DatabaseState.message(): String =
    when (this) {
        DatabaseState.Checking -> stringResource(R.string.home_database_checking)
        is DatabaseState.Ready -> stringResource(
            R.string.home_database_ready,
            meta.schemaVersion,
            meta.snapshotVersion,
        )
        is DatabaseState.Failed -> stringResource(R.string.home_database_failed, message)
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
