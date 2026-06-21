package com.gestorfinances.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.gestorfinances.app.ui.dashboard.DashboardScreen
import com.gestorfinances.app.ui.dashboard.DashboardViewModel
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementDialogHost
import com.gestorfinances.app.ui.movements.MovementsScreen
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.onboarding.OnboardingScreen
import com.gestorfinances.app.ui.onboarding.OnboardingViewModel
import com.gestorfinances.app.ui.people.PeopleScreen
import com.gestorfinances.app.ui.people.PeopleViewModel
import com.gestorfinances.app.ui.recurring.RecurringScreen
import com.gestorfinances.app.ui.recurring.RecurringViewModel
import com.gestorfinances.app.ui.settings.SettingsScreen
import com.gestorfinances.app.ui.settings.SettingsViewModel
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import com.gestorfinances.app.notifications.DESTINATION_ACCOUNTS
import com.gestorfinances.app.notifications.DESTINATION_BUDGETS
import com.gestorfinances.app.notifications.DESTINATION_RECURRING
import com.gestorfinances.app.notifications.EXTRA_NOTIFICATION_DESTINATION
import com.gestorfinances.app.notifications.canPostFinanceNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
    var selectedSection by remember { mutableStateOf(LedgerSection.DASHBOARD) }
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
                notificationRefresher = appContainer.notificationCoordinator,
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
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[BudgetsViewModel::class.java]
    }
    val settingsViewModel = remember(viewModelStoreOwner) {
        ViewModelProvider(
            viewModelStoreOwner,
            SettingsViewModel.Factory(
                preferences = appContainer.notificationPreferences,
                notificationRefresher = appContainer.notificationCoordinator,
            ),
        )[SettingsViewModel::class.java]
    }
    var showBudgets by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val onboardingState by onboardingViewModel.state.collectAsState()
    val movementsState by movementsViewModel.state.collectAsState()

    LaunchedEffect(movementsViewModel) {
        movementsViewModel.onScreenShown()
    }

    if (onboardingState.isLoading || onboardingState.needsOnboarding) {
        OnboardingScreen(
            viewModel = onboardingViewModel,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LaunchedEffect(notificationDestination) {
        when (notificationDestination) {
            DESTINATION_RECURRING -> {
                selectedSection = LedgerSection.RECURRING
                showBudgets = false
                showSettings = false
                onNotificationDestinationConsumed()
            }
            DESTINATION_BUDGETS -> {
                selectedSection = LedgerSection.ANALYSIS
                showBudgets = true
                showSettings = false
                onNotificationDestinationConsumed()
            }
            DESTINATION_ACCOUNTS -> {
                selectedSection = LedgerSection.ACCOUNTS
                showBudgets = false
                showSettings = false
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

    val canAddMovement = movementsState.accounts.isNotEmpty()
    val openMovements: (MovementFilters) -> Unit = { filters ->
        movementsViewModel.onDrillDown(filters)
        selectedSection = LedgerSection.MOVEMENTS
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!showBudgets && !showSettings) {
                FloatingActionButton(
                    onClick = {
                        if (canAddMovement) {
                            movementsViewModel.onAddClicked()
                        }
                    },
                containerColor = if (canAddMovement) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canAddMovement) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = CircleShape,
            ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.movement_list_add),
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            LedgerNavigationBar(
                selectedSection = selectedSection,
                onSelected = { selectedSection = it },
            )
        },
    ) { innerPadding ->
        if (showSettings) {
            SettingsScreen(
                viewModel = settingsViewModel,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onBack = { showSettings = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }
        if (showBudgets) {
            BudgetsScreen(
                viewModel = budgetsViewModel,
                onBack = { showBudgets = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }
        when (selectedSection) {
            LedgerSection.DASHBOARD -> DashboardScreen(
                viewModel = dashboardViewModel,
                onNewMovement = {
                    if (canAddMovement) {
                        movementsViewModel.onAddClicked()
                    }
                },
                onViewAnalysis = { selectedSection = LedgerSection.ANALYSIS },
                onSettings = { showSettings = true },
                onDrillDown = openMovements,
                onMovementDetail = movementsViewModel::onDetailClicked,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            LedgerSection.MOVEMENTS -> MovementsScreen(
                viewModel = movementsViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                showDialogs = false,
            )
            LedgerSection.ACCOUNTS -> AccountsScreen(
                viewModel = accountsViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            LedgerSection.ANALYSIS -> AnalysisScreen(
                viewModel = analysisViewModel,
                onDrillDown = openMovements,
                onManageBudgets = { showBudgets = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            LedgerSection.PEOPLE -> PeopleScreen(
                viewModel = peopleViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            LedgerSection.RECURRING -> RecurringScreen(
                viewModel = recurringViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
    MovementDialogHost(viewModel = movementsViewModel)
}

@Composable
private fun LedgerNavigationBar(
    selectedSection: LedgerSection,
    onSelected: (LedgerSection) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        LedgerSection.entries.forEach { section ->
            val selected = selectedSection == section
            NavigationBarItem(
                selected = selected,
                onClick = { onSelected(section) },
                icon = {
                    Icon(
                        imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(text = stringResource(section.labelRes))
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun DatabaseStatus(databaseState: DatabaseState) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.home_scaffold_status),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = databaseState.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private enum class LedgerSection(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    MOVEMENTS(R.string.nav_movements, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ACCOUNTS(R.string.nav_accounts, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    ANALYSIS(R.string.nav_analysis, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    PEOPLE(R.string.nav_people, Icons.Filled.Groups, Icons.Outlined.Groups),
    RECURRING(R.string.nav_recurring, Icons.Filled.Autorenew, Icons.Outlined.Autorenew),
}

private fun Intent?.notificationDestination(): String? =
    this?.getStringExtra(EXTRA_NOTIFICATION_DESTINATION)

@Preview(showBackground = true)
@Composable
private fun AppShellPreview() {
    GestorFinancesTheme {
        DatabaseStatus(databaseState = DatabaseState.Ready(DatabaseMeta("1", "0")))
    }
}
