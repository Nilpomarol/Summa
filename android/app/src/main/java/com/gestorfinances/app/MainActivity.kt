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
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.key
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
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.sync.DeviceAccessState
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.ui.accounts.AccountsScreen
import com.gestorfinances.app.ui.accounts.AccountsViewModel
import com.gestorfinances.app.ui.analysis.AnalysisScreen
import com.gestorfinances.app.ui.analysis.AnalysisViewModel
import com.gestorfinances.app.ui.budgets.BudgetsScreen
import com.gestorfinances.app.ui.budgets.BudgetsViewModel
import com.gestorfinances.app.ui.categories.CategoriesScreen
import com.gestorfinances.app.ui.categories.CategoriesViewModel
import com.gestorfinances.app.ui.categories.CategoryFlowSheet
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.dashboard.DashboardScreen
import com.gestorfinances.app.ui.dashboard.DashboardViewModel
import com.gestorfinances.app.ui.management.ManagementDestination
import com.gestorfinances.app.ui.management.ManagementSheet
import com.gestorfinances.app.ui.movements.MovementDetailScreen
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementFormState
import com.gestorfinances.app.ui.movements.MovementFormScreen
import com.gestorfinances.app.ui.movements.MovementsScreen
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.navigation.AppNavState
import com.gestorfinances.app.ui.navigation.AppOverlay
import com.gestorfinances.app.ui.navigation.TopLevelSection
import com.gestorfinances.app.ui.onboarding.OnboardingScreen
import com.gestorfinances.app.ui.onboarding.OnboardingViewModel
import com.gestorfinances.app.ui.people.PeopleScreen
import com.gestorfinances.app.ui.people.PeopleViewModel
import com.gestorfinances.app.ui.recurring.DueRemindersSheet
import com.gestorfinances.app.ui.recurring.RecurringOverlays
import com.gestorfinances.app.ui.recurring.RecurringScreen
import com.gestorfinances.app.ui.recurring.RecurringViewModel
import com.gestorfinances.app.ui.settings.SettingsMessage
import com.gestorfinances.app.ui.settings.SettingsScreen
import com.gestorfinances.app.ui.settings.SettingsViewModel
import com.gestorfinances.app.ui.tags.TagsScreen
import com.gestorfinances.app.ui.tags.TagsViewModel
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.trips.TripDetailScreen
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
        val app = application as GestorFinancesApp
        val appContainer = app.container
        val pendingSnackbarMessage = app.consumePendingSnackbarMessage()
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
    viewModelStoreOwner: ViewModelStoreOwner,
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
                viewModelStoreOwner = viewModelStoreOwner,
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
    viewModelStoreOwner: ViewModelStoreOwner,
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
    if (isDatabaseBeingReplaced) {
        DatabaseReplacementOverlay(modifier = Modifier.fillMaxSize())
        return
    }

    // No-op today (AppContainer always reports Writer) — a seam for the real sync/token
    // protocol so the shell doesn't need shape changes once it lands (docs/architecture.md).
    val deviceAccessState by appContainer.deviceAccessState.collectAsState()

    var nav by remember { mutableStateOf(AppNavState.Home) }
    var managementMenuVisible by remember { mutableStateOf(false) }
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
                budgetRepository = appContainer.budgetRepository,
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
                tripRepository = appContainer.tripRepository,
                categoryRepository = appContainer.categoryRepository,
                budgetRepository = appContainer.budgetRepository,
                templateRepository = appContainer.templateRepository,
            ),
        )[DashboardViewModel::class.java]
    }
    val analysisViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            AnalysisViewModel.Factory(
                analysisRepository = appContainer.analysisRepository,
                templateRepository = appContainer.templateRepository,
                accountRepository = appContainer.accountRepository,
                categoryRepository = appContainer.categoryRepository,
                movementRepository = appContainer.movementRepository,
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
                autoCatRuleRepository = appContainer.autoCatRuleRepository,
            ),
        )[MovementsViewModel::class.java]
    }
    val peopleViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            PeopleViewModel.Factory(
                personRepository = appContainer.personRepository,
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
                splitRepository = appContainer.splitRepository,
                personRepository = appContainer.personRepository,
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
                templateRepository = appContainer.templateRepository,
                analysisRepository = appContainer.analysisRepository,
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
                backupFolderRepository = appContainer.backupFolderStore,
                backupOperations = appContainer.backupSnapshotService,
                autoBackupSettings = appContainer.autoBackupPreferences,
                autoBackupScheduler = appContainer.autoBackupScheduler,
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[SettingsViewModel::class.java]
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        settingsViewModel.onBackupFolderSelected(uri?.toString())
    }
    val tripsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            TripsViewModel.Factory(
                tripRepository = appContainer.tripRepository,
                tripAnalysisRepository = appContainer.tripAnalysisRepository,
                movementRepository = appContainer.movementRepository,
                accountRepository = appContainer.accountRepository,
                budgetRepository = appContainer.budgetRepository,
                tagRepository = appContainer.tagRepository,
            ),
        )[TripsViewModel::class.java]
    }
    val tagsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            TagsViewModel.Factory(
                tagRepository = appContainer.tagRepository,
                tripRepository = appContainer.tripRepository,
                categoryRepository = appContainer.categoryRepository,
            ),
        )[TagsViewModel::class.java]
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pendingSnackbarText = pendingSnackbarMessage?.let { message ->
        message.arg?.let { stringResource(message.messageRes, it) }
            ?: stringResource(message.messageRes)
    }
    val onboardingState by onboardingViewModel.state.collectAsState()
    val movementsState by movementsViewModel.state.collectAsState()
    val accountsState by accountsViewModel.state.collectAsState()
    val recurringState by recurringViewModel.state.collectAsState()
    val categoriesState by categoriesViewModel.state.collectAsState()
    val createAccountMessage = stringResource(R.string.movement_no_accounts_title)
    // `remember` (not `rememberSaveable`) is deliberate: a real process restart is exactly what
    // "once per app cold start" means, so losing this on process death re-shows the sheet, which
    // is correct, not a bug.
    var dueRemindersShown by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSnackbarText) {
        if (pendingSnackbarText != null) {
            snackbarHostState.showSnackbar(pendingSnackbarText)
        }
    }

    LaunchedEffect(movementsViewModel) {
        movementsViewModel.onScreenShown()
    }

    LaunchedEffect(accountsViewModel) {
        accountsViewModel.onScreenShown()
    }

    LaunchedEffect(recurringViewModel) {
        recurringViewModel.onScreenShown()
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

    /** A bottom-bar choice is a new visit, not a continuation of a contextual flow. */
    fun showTopLevelFromMenu(section: TopLevelSection) {
        when (section) {
            TopLevelSection.MOVEMENTS -> movementsViewModel.resetForMenuNavigation()
            TopLevelSection.ANALYSIS -> analysisViewModel.resetForMenuNavigation()
            TopLevelSection.DASHBOARD,
            TopLevelSection.MANAGEMENT,
            -> Unit
        }
        nav = AppNavState.topLevel(section).copy(menuVisit = nav.menuVisit + 1)
    }

    /** Contextual links keep their destination state; their caller owns the Back path. */
    fun showTopLevel(section: TopLevelSection) {
        nav = AppNavState.contextualTopLevel(section, previous = nav)
    }

    fun showManagement(destination: ManagementDestination? = null) {
        val returnSection = if (nav.section == TopLevelSection.MANAGEMENT) {
            nav.managementReturnSection ?: TopLevelSection.DASHBOARD
        } else {
            nav.section
        }
        nav = AppNavState.management(destination, returnSection)
    }

    /** A Més-menu choice is a new visit; contextual routes use [showManagement] instead. */
    fun showManagementFromMenu(destination: ManagementDestination) {
        when (destination) {
            ManagementDestination.ACCOUNTS -> accountsViewModel.resetForMenuNavigation()
            ManagementDestination.CATEGORIES -> categoriesViewModel.resetForMenuNavigation()
            ManagementDestination.PEOPLE -> peopleViewModel.resetForMenuNavigation()
            ManagementDestination.EVENTS -> tripsViewModel.resetForMenuNavigation()
            ManagementDestination.RECURRING -> recurringViewModel.resetForMenuNavigation()
            ManagementDestination.BUDGETS -> budgetsViewModel.resetForMenuNavigation()
            ManagementDestination.TAGS -> tagsViewModel.resetForMenuNavigation()
            ManagementDestination.SETTINGS -> settingsViewModel.resetForMenuNavigation()
        }
        showManagement(destination)
        nav = nav.copy(menuVisit = nav.menuVisit + 1)
    }

    fun openMovementForm(
        tripId: String? = null,
        returnTo: AppOverlay? = null,
    ) {
        val hasAccount = movementsState.accounts.isNotEmpty() || accountsState.accounts.isNotEmpty()
        if (hasAccount) {
            movementsViewModel.onAddClicked(tripId)
            nav = nav.copy(
                overlay = AppOverlay.MovementForm(
                    tripId = tripId,
                    returnTo = returnTo,
                ),
            )
        } else {
            showManagement(ManagementDestination.ACCOUNTS)
            accountsViewModel.onAddClicked()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(createAccountMessage)
            }
        }
    }

    // Movement sheets own Back so they can animate closed before navigation state changes.
    BackHandler(
        enabled = nav.canNavigateBack &&
            nav.overlay !is AppOverlay.MovementForm &&
            nav.overlay !is AppOverlay.MovementDetail,
    ) {
        nav = nav.back()
    }

    LaunchedEffect(notificationDestination) {
        when (notificationDestination) {
            DESTINATION_RECURRING -> {
                showManagement(ManagementDestination.RECURRING)
                onNotificationDestinationConsumed()
            }
            DESTINATION_BUDGETS -> {
                showManagement(ManagementDestination.BUDGETS)
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

    val openDebtSource: (String) -> Unit = { sourceId ->
        peopleViewModel.onPersonDetailDismissed()
        movementsViewModel.onDetailSourceClicked(sourceId)
        nav = AppNavState.topLevel(TopLevelSection.MOVEMENTS)
            .copy(overlay = AppOverlay.MovementDetail(movementId = sourceId))
    }

    val openMovementDetail: (MovementSummary) -> Unit = { movement ->
        movementsViewModel.onDetailClicked(movement)
        nav = nav.copy(
            overlay = AppOverlay.MovementDetail(
                movementId = movement.id,
                returnTo = nav.overlay,
            ),
        )
    }

    val openExternalExpenseForm: (PersonSummary) -> Unit = { person ->
        peopleViewModel.onPersonDetailDismissed()
        movementsViewModel.onAddClicked(tripId = null, debtPayerPersonId = person.id)
        // Stay on the current tab instead of switching to Moviments; the modal form can be shown
        // above any section while keeping that page visible underneath.
        nav = nav.copy(overlay = AppOverlay.MovementForm(debtPayerPersonId = person.id))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val access = deviceAccessState
            if (access is DeviceAccessState.ReadOnly) {
                InlineBanner(
                    kind = BannerKind.Alert,
                    text = stringResource(R.string.sync_read_only_banner, access.holderDeviceName),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        bottomBar = {
            if (nav.routeChrome.showsGlobalNavigation) FinanceBottomBar(
                selectedSection = nav.section,
                onSelected = ::showTopLevelFromMenu,
                onManagementClick = { managementMenuVisible = true },
                // The global FAB is available only on root/Management child routes. Trip Detail
                // owns its local contextual add action because focused routes hide this bar.
                onAddMovement = { openMovementForm() },
            )
        },
    ) { innerPadding ->
        val movementFormOverlay = nav.overlay as? AppOverlay.MovementForm
        val movementDetailOverlay = nav.overlay as? AppOverlay.MovementDetail
        val pageOverlay = when {
            // Editing swaps the detail sheet for the form, so the page behind the form is the
            // detail's own background, not a second sheet stacked underneath it.
            movementFormOverlay?.returnTo is AppOverlay.MovementDetail ->
                (movementFormOverlay.returnTo as AppOverlay.MovementDetail).returnTo
            movementFormOverlay != null -> movementFormOverlay.returnTo
            movementDetailOverlay != null -> movementDetailOverlay.returnTo
            else -> nav.overlay
        }

        key(nav.menuVisit) {
        when (val overlay = pageOverlay) {
            is AppOverlay.Tags -> {
                TagsScreen(
                    viewModel = tagsViewModel,
                    contextTripId = overlay.tripId,
                    onBack = { nav = nav.back() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                if (movementFormOverlay == null) return@Scaffold
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
                if (movementFormOverlay == null) return@Scaffold
            }
            is AppOverlay.TripDetail -> {
                LaunchedEffect(overlay.tripId) {
                    tripsViewModel.onDetailOpened(overlay.tripId)
                }
                TripDetailScreen(
                    viewModel = tripsViewModel,
                    onBack = {
                        tripsViewModel.onDetailDismissed()
                        nav = nav.back()
                    },
                    onManageBudget = { tripId ->
                        nav = nav.copy(overlay = AppOverlay.Budgets(tripId = tripId, returnTo = overlay))
                    },
                    onAddMovement = {
                        openMovementForm(tripId = overlay.tripId, returnTo = overlay)
                    },
                    onMovementDetail = openMovementDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                if (movementFormOverlay == null) return@Scaffold
            }
            is AppOverlay.MovementForm -> Unit
            is AppOverlay.MovementDetail -> Unit
            null -> Unit
        }
        if (pageOverlay == null) when (nav.section) {
            TopLevelSection.DASHBOARD -> DashboardScreen(
                viewModel = dashboardViewModel,
                onDrillDown = openMovements,
                onMovementDetail = openMovementDetail,
                onAccountAnalysis = { account ->
                    analysisViewModel.setAccountFilter(account.id, account.name)
                    showTopLevel(TopLevelSection.ANALYSIS)
                },
                onViewTrip = { trip -> nav = nav.copy(overlay = AppOverlay.TripDetail(tripId = trip.id)) },
                onAddTripMovement = { trip -> openMovementForm(trip.id) },
                onViewBudgets = { showManagement(ManagementDestination.BUDGETS) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            TopLevelSection.MOVEMENTS -> MovementsScreen(
                viewModel = movementsViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onAdd = { openMovementForm() },
                onDetail = openMovementDetail,
            )
            TopLevelSection.ANALYSIS -> AnalysisScreen(
                viewModel = analysisViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            TopLevelSection.MANAGEMENT -> when (nav.managementDestination) {
                null -> DashboardScreen(
                    viewModel = dashboardViewModel,
                    onDrillDown = openMovements,
                    onMovementDetail = openMovementDetail,
                    onAccountAnalysis = { account ->
                        analysisViewModel.setAccountFilter(account.id, account.name)
                        showTopLevel(TopLevelSection.ANALYSIS)
                    },
                    onViewTrip = { trip -> nav = nav.copy(overlay = AppOverlay.TripDetail(tripId = trip.id)) },
                    onAddTripMovement = { trip -> openMovementForm(trip.id) },
                    onViewBudgets = { showManagement(ManagementDestination.BUDGETS) },
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
                    onMovementDetail = openMovementDetail,
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
                    onDefineBudget = { categoryId ->
                        budgetsViewModel.onAddClicked(categoryId)
                        showManagement(ManagementDestination.BUDGETS)
                    },
                    onMovementDetail = openMovementDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.PEOPLE -> PeopleScreen(
                    viewModel = peopleViewModel,
                    onOpenDebtSource = openDebtSource,
                    onAddDebtForPerson = openExternalExpenseForm,
                    onMessageCopied = { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.EVENTS -> TripsScreen(
                    viewModel = tripsViewModel,
                    onOpenDetail = { trip -> nav = nav.copy(overlay = AppOverlay.TripDetail(tripId = trip.id)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.TAGS -> TagsScreen(
                    viewModel = tagsViewModel,
                    contextTripId = null,
                    onBack = { nav = nav.back() },
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
                ManagementDestination.BUDGETS -> BudgetsScreen(
                    viewModel = budgetsViewModel,
                    onBack = { nav = nav.back() },
                    onOpenCategoryMovements = { category ->
                        categoriesViewModel.onFlowClicked(category)
                    },
                    contextTripId = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                ManagementDestination.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    notificationPermissionGranted = notificationPermissionGranted,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onPickBackupFolder = { backupFolderLauncher.launch(null) },
                    onRecreateApp = onRecreateApp,
                    onBack = { nav = nav.back() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }

        if (
            pageOverlay == null &&
            movementDetailOverlay == null &&
            nav.section == TopLevelSection.MANAGEMENT &&
            nav.managementDestination == ManagementDestination.BUDGETS
        ) {
            categoriesState.flowDetail?.let { detail ->
                CategoryFlowSheet(
                    detail = detail,
                    onDismiss = categoriesViewModel::onFlowDismissed,
                    onViewAnalysis = {
                        categoriesViewModel.onFlowDismissed()
                        analysisViewModel.setCategoryFilter(detail.category.id, detail.category.name)
                        showTopLevel(TopLevelSection.ANALYSIS)
                    },
                    onDefineBudget = {
                        categoriesViewModel.onFlowDismissed()
                        budgetsViewModel.onAddClicked(detail.category.id)
                    },
                    onMovementDetail = openMovementDetail,
                )
            }
        }

        movementDetailOverlay?.let { overlay ->
            MovementDetailScreen(
                viewModel = movementsViewModel,
                onBack = {
                    movementsViewModel.onDetailDismissed()
                    nav = nav.back()
                },
                onEdit = { movement ->
                    movementsViewModel.onEditClicked(movement) {
                        nav = nav.copy(
                            overlay = AppOverlay.MovementForm(returnTo = overlay),
                        )
                    }
                },
            )
        }

        movementFormOverlay?.let { overlay ->
            // Keep the last form snapshot alive after a successful write. The ViewModel clears
            // its form immediately; retaining the rendered content lets the sheet finish its
            // hide animation before this overlay is removed.
            var retainedForm by remember(overlay) { mutableStateOf<MovementFormState?>(null) }
            LaunchedEffect(movementsState.form) {
                movementsState.form?.let { retainedForm = it }
            }
            (movementsState.form ?: retainedForm)?.let { form ->
                MovementFormScreen(
                    form = form,
                    accounts = movementsState.accounts,
                    categories = movementsState.categories,
                    people = movementsState.people,
                    trips = movementsState.trips,
                    tags = movementsState.tags,
                    onFormChange = movementsViewModel::onFormChanged,
                    onTripSelected = movementsViewModel::onTripSelected,
                    onTagSelected = movementsViewModel::onTagSelected,
                    onSharedToggled = movementsViewModel::onSharedToggled,
                    onSplitEditorChange = movementsViewModel::onSplitEditorChanged,
                    onSettlementToggled = movementsViewModel::onSettlementToggled,
                    onSettlementPersonSelected = movementsViewModel::onSettlementPersonSelected,
                    onOtherPersonSelected = movementsViewModel::onOtherPersonSelected,
                    onRecurringToggled = movementsViewModel::onRecurringToggled,
                    onRecurringFrequencyChanged = movementsViewModel::onRecurringFrequencyChanged,
                    onOptionalToggled = movementsViewModel::onOptionalToggled,
                    onAdvancedToggled = movementsViewModel::onAdvancedToggled,
                    onCreatePersonInSplit = movementsViewModel::onCreatePersonInSplit,
                    onDismiss = {
                        val savedEdit = movementsState.form == null &&
                            overlay.returnTo is AppOverlay.MovementDetail
                        movementsViewModel.onFormDismissed()
                        // A completed edit returns to the source page rather than reopening a
                        // stale detail sheet. Cancelling still restores the untouched detail.
                        nav = if (savedEdit) {
                            nav.copy(overlay = (overlay.returnTo as AppOverlay.MovementDetail).returnTo)
                        } else {
                            nav.back()
                        }
                    },
                    onSave = movementsViewModel::onSaveClicked,
                    onOverride = movementsViewModel::onDuplicateOverrideClicked,
                    onDataLossOverride = movementsViewModel::onDataLossOverrideClicked,
                    onRecurrenceStopEnd = movementsViewModel::onRecurrenceStopEndClicked,
                    onRecurrenceStopUnlink = movementsViewModel::onRecurrenceStopUnlinkClicked,
                    onWarningDismissed = movementsViewModel::onWarningDismissed,
                    dismissRequested = movementsState.form == null,
                )
            }
        }
        }
    }
    if (managementMenuVisible) {
        ManagementSheet(
            onDestinationSelected = ::showManagementFromMenu,
            onDismiss = { managementMenuVisible = false },
        )
    }
    RecurringOverlays(viewModel = recurringViewModel)

    // Surfaces due recurring items proactively instead of requiring a manual visit to
    // Management > Recurring. `recurringState.hasOpenDialog` makes the sheet step aside whenever
    // one of its own actions (confirm, end) opens a sub-dialog, then reappear once that closes;
    // `movementsState.hasOpenDialog` avoids stacking on top of an unrelated movement-form sheet
    // (e.g. the FAB's "add movement" form) that happens to be open at the same moment.
    if (!dueRemindersShown && !recurringState.hasOpenDialog && !movementsState.hasOpenDialog &&
        recurringState.duePrompts.isNotEmpty()
    ) {
        DueRemindersSheet(
            duePrompts = recurringState.duePrompts,
            onConfirm = recurringViewModel::onConfirmClicked,
            onSkip = recurringViewModel::onSkipClicked,
            onSkipAll = recurringViewModel::onSkipAllClicked,
            onEnd = recurringViewModel::onEndClicked,
            onDismiss = { dueRemindersShown = true },
        )
    }
}

@Composable
private fun FinanceBottomBar(
    selectedSection: TopLevelSection,
    onSelected: (TopLevelSection) -> Unit,
    onManagementClick: () -> Unit,
    onAddMovement: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FinanceTheme.colors.bottomBarSurface,
        contentColor = FinanceTheme.colors.bottomBarContent,
    ) {
        Column {
            HorizontalDivider(color = FinanceTheme.colors.bottomBarDivider)
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
                        onClick = onManagementClick,
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
    val itemShape = MaterialTheme.shapes.small
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .padding(horizontal = 2.dp)
            .clip(itemShape),
        shape = itemShape,
        color = FinanceTheme.colors.bottomBarSurface,
        contentColor = if (selected) {
            FinanceTheme.colors.bottomBarActive
        } else {
            FinanceTheme.colors.bottomBarContent
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
