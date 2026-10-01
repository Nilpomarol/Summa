package com.gestorfinances.app

import androidx.compose.ui.draw.clip
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gestorfinances.app.data.backup.ensureScheduled
import com.gestorfinances.app.data.repository.DatabaseMeta
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.notifications.DESTINATION_ACCOUNTS
import com.gestorfinances.app.notifications.DESTINATION_ACCOUNT_PREFIX
import com.gestorfinances.app.notifications.DESTINATION_RECURRING_ITEM_PREFIX
import com.gestorfinances.app.notifications.DESTINATION_SETTINGS
import com.gestorfinances.app.notifications.DESTINATION_BUDGETS
import com.gestorfinances.app.notifications.DESTINATION_RECURRING
import com.gestorfinances.app.notifications.EXTRA_NOTIFICATION_DESTINATION
import com.gestorfinances.app.notifications.canPostFinanceNotifications
import com.gestorfinances.app.ui.accounts.AccountDetailPage
import com.gestorfinances.app.ui.accounts.AccountsScreen
import com.gestorfinances.app.ui.accounts.accountsViewModel
import com.gestorfinances.app.ui.analysis.AnalysisScreen
import com.gestorfinances.app.ui.analysis.analysisViewModel
import com.gestorfinances.app.ui.budgets.BudgetsPage
import com.gestorfinances.app.ui.budgets.BudgetSheetHost
import com.gestorfinances.app.ui.budgets.budgetsViewModel
import com.gestorfinances.app.ui.categories.CategoriesScreen
import com.gestorfinances.app.ui.categories.CategoryDetailPage
import com.gestorfinances.app.ui.categories.categoriesViewModel
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.dashboard.DashboardScreen
import com.gestorfinances.app.ui.dashboard.dashboardViewModel
import com.gestorfinances.app.ui.goals.GoalDetailPage
import com.gestorfinances.app.ui.goals.GoalsScreen
import com.gestorfinances.app.ui.goals.goalsViewModel
import com.gestorfinances.app.ui.management.ManagementDestination
import com.gestorfinances.app.ui.management.MoreScreen
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSheet
import com.gestorfinances.app.ui.movements.MovementSheets
import com.gestorfinances.app.ui.movements.MovementsScreen
import com.gestorfinances.app.ui.movements.movementsViewModel
import com.gestorfinances.app.ui.navigation.Route
import com.gestorfinances.app.ui.navigation.TopLevelSection
import com.gestorfinances.app.ui.navigation.isFocusedPage
import com.gestorfinances.app.ui.navigation.openSection
import com.gestorfinances.app.ui.navigation.route
import com.gestorfinances.app.ui.onboarding.OnboardingScreen
import com.gestorfinances.app.ui.onboarding.onboardingViewModel
import com.gestorfinances.app.ui.people.PeopleScreen
import com.gestorfinances.app.ui.people.PersonDetailPage
import com.gestorfinances.app.ui.people.peopleViewModel
import com.gestorfinances.app.ui.recurring.RecurringDetailPage
import com.gestorfinances.app.ui.recurring.RecurringReminders
import com.gestorfinances.app.ui.recurring.RecurringScreen
import com.gestorfinances.app.ui.recurring.recurringViewModel
import com.gestorfinances.app.ui.settings.SettingsEffect
import com.gestorfinances.app.ui.settings.SettingsMessage
import com.gestorfinances.app.ui.settings.SettingsScreen
import com.gestorfinances.app.ui.settings.settingsViewModel
import com.gestorfinances.app.ui.tags.TagDetailPage
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

            LaunchedEffect(databaseState) {
                if (databaseState is DatabaseState.Ready) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            appContainer.autoBackupScheduler.ensureScheduled(
                                settings = appContainer.autoBackupPreferences.load(),
                                hasFolder = appContainer.backupFolderStore.loadSelectedFolder() != null,
                                now = java.time.Instant.now(),
                            )
                        }
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
    val recurringState by recurringViewModel.state.collectAsState()
    // Mounted pages reload when any Activity-wide overlay commits a financial write.
    val dataVersion by appContainer.financialDataRevision.value.collectAsState()
    val navController = rememberNavController()
    val currentEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentEntry?.destination
    var movementSheet by remember { mutableStateOf<MovementSheet?>(null) }
    // The stack is always Inici plus the root of the last chosen tab, so the highlighted tab changes
    // only when a tab is chosen or Back returns to Inici; contextual pages keep their tab.
    var selectedSection by rememberSaveable { mutableStateOf(TopLevelSection.DASHBOARD) }
    LaunchedEffect(currentEntry?.id) {
        if (currentDestination?.hasRoute<Route.Dashboard>() == true) {
            selectedSection = TopLevelSection.DASHBOARD
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.common_deleted)
    val undoLabel = stringResource(R.string.common_undo)
    val createAccountMessage = stringResource(R.string.movement_no_accounts_title)
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    // The undo runs here, not in the page that offered it: that page is usually gone by now. The
    // list it went back to then reloads.
    fun undoSnackbar(message: String, onShowing: (Boolean) -> Unit = {}): DeleteUndoHandler = { undo ->
        coroutineScope.launch {
            onShowing(true)
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = undoLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    undo()
                    appContainer.financialDataRevision.markChanged()
                }
            } finally {
                onShowing(false)
            }
        }
    }
    val showDeleteUndo = undoSnackbar(deletedMessage)
    // The list of what is due waits while a skip can be undone: it would open over the snackbar.
    var skipUndoShowing by remember { mutableStateOf(false) }
    val showSkipUndo = undoSnackbar(stringResource(R.string.recurring_skipped)) { skipUndoShowing = it }

    // The movement detail and edit sheets read the ledger's accounts/categories/people.
    LaunchedEffect(movementsViewModel) {
        movementsViewModel.onScreenShown()
    }

    fun openMovementForm(
        tripId: String? = null,
        accountId: String? = null,
        categoryId: String? = null,
        tagId: String? = null,
    ) {
        movementSheet = MovementSheet.Form()
        movementsViewModel.onAddClicked(
            tripId = tripId,
            accountId = accountId,
            categoryId = categoryId,
            tagId = tagId,
            onNoAccounts = {
                movementSheet = null
                navController.navigate(Route.Accounts(openAddForm = true))
                showMessage(createAccountMessage)
            },
        )
    }

    fun openMovementDetail(movement: MovementSummary) {
        movementsViewModel.onDetailClicked(movement)
        movementSheet = MovementSheet.Detail
    }

    // The form opens above the current page rather than switching to the ledger.
    fun openPersonPaidForm(person: PersonSummary) {
        movementsViewModel.onAddClicked(tripId = null, debtPayerPersonId = person.id)
        movementSheet = MovementSheet.Form()
    }

    fun openMovements(filters: MovementFilters) {
        movementsViewModel.onDrillDown(filters)
        navController.navigate(Route.Movements)
    }

    fun showFromMenu(section: TopLevelSection) {
        // The ledger's ViewModel is app-wide, so a menu visit resets it explicitly; every other
        // page gets a fresh ViewModel with its fresh navigation entry.
        if (section == TopLevelSection.MOVEMENTS) movementsViewModel.resetForMenuNavigation()
        selectedSection = section
        navController.openSection(section)
    }

    fun showFromHub(destination: ManagementDestination) {
        // Recurring's ViewModel is app-wide, so a hub visit resets it like a menu visit.
        if (destination == ManagementDestination.RECURRING) recurringViewModel.resetForMenuNavigation()
        navController.navigate(destination.route())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!currentDestination.isFocusedPage()) FinanceBottomBar(
                selectedSection = selectedSection,
                onSelected = ::showFromMenu,
                onManagementClick = { showFromMenu(TopLevelSection.MANAGEMENT) },
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
                    dataVersion = dataVersion,
                    dueRecurringCount = recurringState.duePrompts.size,
                    onOpenAccount = { account -> navController.navigate(Route.AccountDetail(account.id)) },
                    onOpenPerson = { person -> navController.navigate(Route.PersonDetail(person.id)) },
                    onOpenRecurring = {
                        recurringViewModel.resetForMenuNavigation()
                        navController.navigate(Route.Recurring)
                    },
                    onAddAccount = { navController.navigate(Route.Accounts(openAddForm = true)) },
                    onDrillDown = ::openMovements,
                    onAddMovement = { openMovementForm() },
                    onMovementDetail = ::openMovementDetail,
                    onAccountAnalysis = { account ->
                        navController.navigate(Route.Analysis(accountId = account.id, accountName = account.name))
                    },
                    onViewTrip = { trip -> navController.navigate(Route.TripDetail(trip.id)) },
                    onAddTripMovement = { trip -> openMovementForm(tripId = trip.id) },
                    onViewBudgets = { navController.navigate(Route.Budgets) },
                    modifier = pageModifier,
                )
            }
            composable<Route.Movements> {
                MovementsScreen(
                    viewModel = movementsViewModel,
                    onAdd = { openMovementForm() },
                    onDetail = ::openMovementDetail,
                    onViewRecurring = {
                        recurringViewModel.resetForMenuNavigation()
                        navController.navigate(Route.Recurring)
                    },
                    modifier = pageModifier,
                )
            }
            composable<Route.Analysis> { entry ->
                AnalysisScreen(
                    viewModel = analysisViewModel(appContainer, entry.toRoute()),
                    dataVersion = dataVersion,
                    onOpenCategory = { categoryId -> navController.navigate(Route.CategoryDetail(categoryId)) },
                    onOpenTrip = { tripId -> navController.navigate(Route.TripDetail(tripId)) },
                    modifier = pageModifier,
                )
            }
            composable<Route.Accounts> { entry ->
                AccountsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = accountsViewModel(appContainer, entry.toRoute()),
                    dataVersion = dataVersion,
                    onOpenDetail = { account -> navController.navigate(Route.AccountDetail(account.id)) },
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.AccountDetail> { entry ->
                AccountDetailPage(
                    accountId = entry.toRoute<Route.AccountDetail>().accountId,
                    onBack = { navController.popBackStack() },
                    viewModel = accountsViewModel(appContainer, Route.Accounts()),
                    dataVersion = dataVersion,
                    onViewAnalysis = { accountId, accountName ->
                        navController.navigate(Route.Analysis(accountId = accountId, accountName = accountName))
                    },
                    onMovementDetail = ::openMovementDetail,
                    onAddMovement = { accountId -> openMovementForm(accountId = accountId) },
                    onOpenGoal = { goalId -> navController.navigate(Route.GoalDetail(goalId)) },
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Categories> {
                CategoriesScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = categoriesViewModel(appContainer),
                    dataVersion = dataVersion,
                    onOpenDetail = { category -> navController.navigate(Route.CategoryDetail(category.id)) },
                    modifier = pageModifier,
                )
            }
            composable<Route.CategoryDetail> { entry ->
                val categoryId = entry.toRoute<Route.CategoryDetail>().categoryId
                val categories = categoriesViewModel(appContainer)
                val budgets = budgetsViewModel(appContainer)
                CategoryDetailPage(
                    categoryId = categoryId,
                    onBack = { navController.popBackStack() },
                    viewModel = categories,
                    dataVersion = dataVersion,
                    onOpenCategory = { categoryId -> navController.navigate(Route.CategoryDetail(categoryId)) },
                    onViewAnalysis = { categoryId, categoryName ->
                        navController.navigate(Route.Analysis(categoryId = categoryId, categoryName = categoryName))
                    },
                    onBudget = { id, _ -> budgets.editBudgetFor(categoryId = id) },
                    onAddMovement = { id -> openMovementForm(categoryId = id) },
                    onMovementDetail = ::openMovementDetail,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
                BudgetSheetHost(
                    viewModel = budgets,
                    onChanged = { categories.onCategoryDetailOpened(categoryId) },
                    onDeleteCommitted = showDeleteUndo,
                )
            }
            composable<Route.People> {
                PeopleScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = peopleViewModel(appContainer),
                    dataVersion = dataVersion,
                    onOpenDetail = { person -> navController.navigate(Route.PersonDetail(person.id)) },
                    modifier = pageModifier,
                )
            }
            composable<Route.PersonDetail> { entry ->
                PersonDetailPage(
                    personId = entry.toRoute<Route.PersonDetail>().personId,
                    onBack = { navController.popBackStack() },
                    viewModel = peopleViewModel(appContainer),
                    dataVersion = dataVersion,
                    // The detail opens above the person, so closing it returns there.
                    onOpenDebtSource = { sourceId ->
                        movementsViewModel.onDetailSourceClicked(sourceId) {
                            movementSheet = MovementSheet.Detail
                        }
                    },
                    onAddDebtForPerson = ::openPersonPaidForm,
                    onMessageCopied = showMessage,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Trips> {
                TripsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = tripsViewModel(appContainer),
                    dataVersion = dataVersion,
                    onOpenDetail = { trip -> navController.navigate(Route.TripDetail(trip.id)) },
                    modifier = pageModifier,
                )
            }
            composable<Route.TripDetail> { entry ->
                val tripId = entry.toRoute<Route.TripDetail>().tripId
                val viewModel = tripsViewModel(appContainer)
                val budgets = budgetsViewModel(appContainer)
                // Movements added or edited from this page change the totals it shows.
                LaunchedEffect(viewModel, tripId, dataVersion) {
                    viewModel.onDetailOpened(tripId)
                }
                TripDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onDeleteCommitted = showDeleteUndo,
                    onManageBudget = { budgets.editBudgetFor(tripId = tripId) },
                    onAddMovement = { openMovementForm(tripId = tripId) },
                    onMovementDetail = ::openMovementDetail,
                    onOpenTag = { tagId -> navController.navigate(Route.TagDetail(tagId)) },
                    modifier = pageModifier,
                )
                BudgetSheetHost(
                    viewModel = budgets,
                    onChanged = { viewModel.onDetailOpened(tripId) },
                    onDeleteCommitted = showDeleteUndo,
                )
            }
            composable<Route.Budgets> {
                BudgetsPage(
                    appContainer = appContainer,
                    onBack = { navController.popBackStack() },
                    onOpenRecurring = { navController.navigate(Route.Recurring) },
                    onOpenGoals = { navController.navigate(Route.Goals) },
                    onOpenCategory = { categoryId -> navController.navigate(Route.CategoryDetail(categoryId)) },
                    dataVersion = dataVersion,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Goals> {
                GoalsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = goalsViewModel(appContainer),
                    dataVersion = dataVersion,
                    onOpenDetail = { goal -> navController.navigate(Route.GoalDetail(goal.id)) },
                    modifier = pageModifier,
                )
            }
            composable<Route.GoalDetail> { entry ->
                GoalDetailPage(
                    goalId = entry.toRoute<Route.GoalDetail>().goalId,
                    onBack = { navController.popBackStack() },
                    viewModel = goalsViewModel(appContainer),
                    dataVersion = dataVersion,
                    onOpenAccount = { accountId -> navController.navigate(Route.AccountDetail(accountId)) },
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Tags> {
                TagsScreen(
                    viewModel = tagsViewModel(appContainer),
                    contextTripId = null,
                    dataVersion = dataVersion,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { tag -> navController.navigate(Route.TagDetail(tag.id)) },
                    modifier = pageModifier,
                )
            }
            composable<Route.TagDetail> { entry ->
                TagDetailPage(
                    tagId = entry.toRoute<Route.TagDetail>().tagId,
                    onBack = { navController.popBackStack() },
                    viewModel = tagsViewModel(appContainer),
                    dataVersion = dataVersion,
                    onOpenTrip = { tripId -> navController.navigate(Route.TripDetail(tripId)) },
                    onAddMovement = { tagId, tripId -> openMovementForm(tripId = tripId, tagId = tagId) },
                    onMovementDetail = ::openMovementDetail,
                    onDeleteCommitted = showDeleteUndo,
                    modifier = pageModifier,
                )
            }
            composable<Route.Recurring> {
                RecurringScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = recurringViewModel,
                    onOpenItem = { templateId -> navController.navigate(Route.RecurringDetail(templateId)) },
                    modifier = pageModifier,
                )
            }
            composable<Route.RecurringDetail> { entry ->
                RecurringDetailPage(
                    templateId = entry.toRoute<Route.RecurringDetail>().templateId,
                    viewModel = recurringViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { accountId -> navController.navigate(Route.AccountDetail(accountId)) },
                    onMovementDetail = ::openMovementDetail,
                    modifier = pageModifier,
                )
            }
            composable<Route.More> {
                MoreScreen(onDestinationSelected = ::showFromHub, modifier = pageModifier)
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
            val destination = notificationDestination
            val route = when {
                destination == null -> null
                // A reminder opens straight on recording that item, over whatever page is showing.
                destination.startsWith(DESTINATION_RECURRING_ITEM_PREFIX) -> {
                    recurringViewModel.onConfirmRequested(destination.removePrefix(DESTINATION_RECURRING_ITEM_PREFIX))
                    onNotificationDestinationConsumed()
                    null
                }
                destination.startsWith(DESTINATION_ACCOUNT_PREFIX) ->
                    Route.AccountDetail(destination.removePrefix(DESTINATION_ACCOUNT_PREFIX))
                destination == DESTINATION_RECURRING -> Route.Recurring
                destination == DESTINATION_BUDGETS -> Route.Budgets
                destination == DESTINATION_ACCOUNTS -> Route.Accounts()
                destination == DESTINATION_SETTINGS -> Route.Settings
                else -> null
            }
            if (route != null) {
                navController.navigate(route) { launchSingleTop = true }
                onNotificationDestinationConsumed()
            }
        }
    }

    MovementSheets(
        sheet = movementSheet,
        onSheetChange = { movementSheet = it },
        viewModel = movementsViewModel,
        onEditContribution = { movementId ->
            navController.navigate(Route.Accounts(editContributionId = movementId))
        },
        onDeleteCommitted = showDeleteUndo,
    )
    RecurringReminders(
        viewModel = recurringViewModel,
        otherSheetOpen = movementsState.hasOpenDialog || movementForm != null || skipUndoShowing,
        openedFromReminder = notificationDestination?.startsWith(DESTINATION_RECURRING_ITEM_PREFIX) == true,
        onDeleteCommitted = showDeleteUndo,
        onSkipped = showSkipUndo,
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
        targetValue = if (selected) colors.bottomBarActiveTile else Color.Transparent,
        label = "navTile",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) colors.bottomBarActive else colors.bottomBarContent,
        label = "navIcon",
    )
    // The whole slot takes the tap, but the press shows only on the tile, in the tile's shape.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(NAV_TILE_SIZE)
                .clip(MaterialTheme.shapes.medium)
                .background(tileColor)
                .indication(interactionSource, ripple()),
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

/** The one filled thing in the bar: the action that adds a movement. */
@Composable
private fun AddMovementButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(NAV_ADD_TILE_SIZE),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.movement_list_add),
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
