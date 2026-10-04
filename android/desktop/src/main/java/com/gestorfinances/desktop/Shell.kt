package com.gestorfinances.desktop

import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.movements.MovementFilters
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.imageResource
import com.gestorfinances.app.ui.theme.LocalControlStyle
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.runtime.CompositionLocalProvider
import com.gestorfinances.ui.resources.movement_list_add
import com.gestorfinances.ui.resources.management_group_organization
import com.gestorfinances.ui.resources.management_group_planning
import com.gestorfinances.ui.resources.management_group_money
import com.gestorfinances.desktop.resources.app_icon
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.app.ui.common.OpenDialogs
import com.gestorfinances.app.ui.common.PrimaryButton
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.FinancialDataRevision
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.GoalRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TripAnalysisRepository
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.ui.accounts.AccountArchiveDialog
import com.gestorfinances.app.ui.accounts.AccountFormSheet
import com.gestorfinances.app.ui.accounts.AccountsViewModel
import com.gestorfinances.app.ui.accounts.ContributionFormSheet
import com.gestorfinances.app.ui.analysis.AnalysisViewModel
import com.gestorfinances.app.ui.budgets.BudgetsViewModel
import com.gestorfinances.app.ui.categories.CategoriesViewModel
import com.gestorfinances.app.ui.dashboard.DashboardViewModel
import com.gestorfinances.app.ui.goals.GoalsViewModel
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.people.PeopleViewModel
import com.gestorfinances.app.ui.recurring.RecurringReminders
import com.gestorfinances.app.ui.recurring.RecurringViewModel
import com.gestorfinances.app.ui.tags.TagsViewModel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.trips.TripsViewModel
import com.gestorfinances.ui.resources.account_list_title
import com.gestorfinances.ui.resources.app_name
import com.gestorfinances.ui.resources.budget_list_title
import com.gestorfinances.ui.resources.category_list_title
import com.gestorfinances.ui.resources.goal_list_title
import com.gestorfinances.ui.resources.nav_analysis
import com.gestorfinances.ui.resources.nav_home
import com.gestorfinances.ui.resources.nav_movements
import com.gestorfinances.ui.resources.person_list_title
import com.gestorfinances.ui.resources.recurring_list_title
import com.gestorfinances.ui.resources.settings_title
import com.gestorfinances.ui.resources.tag_list_title
import com.gestorfinances.ui.resources.trip_list_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/** The pages in the pane's order, under the groups the phone's menu has; [group] is null for the everyday ones. */
internal enum class Page(val label: StringResource, val icon: ImageVector, val group: StringResource? = null) {
    HOME(SharedRes.string.nav_home, Icons.Outlined.Home),
    MOVEMENTS(SharedRes.string.nav_movements, Icons.AutoMirrored.Filled.List),
    ANALYSIS(SharedRes.string.nav_analysis, Icons.Outlined.BarChart),
    ACCOUNTS(SharedRes.string.account_list_title, Icons.Outlined.AccountBalanceWallet, SharedRes.string.management_group_money),
    GOALS(SharedRes.string.goal_list_title, Icons.Outlined.Savings, SharedRes.string.management_group_money),
    PEOPLE(SharedRes.string.person_list_title, Icons.Outlined.Groups, SharedRes.string.management_group_money),
    BUDGETS(SharedRes.string.budget_list_title, Icons.Outlined.PieChart, SharedRes.string.management_group_planning),
    RECURRING(SharedRes.string.recurring_list_title, Icons.Outlined.Autorenew, SharedRes.string.management_group_planning),
    CATEGORIES(SharedRes.string.category_list_title, Icons.Outlined.Category, SharedRes.string.management_group_organization),
    TRIPS(SharedRes.string.trip_list_title, Icons.Outlined.Flight, SharedRes.string.management_group_organization),
    TAGS(SharedRes.string.tag_list_title, Icons.AutoMirrored.Outlined.Label, SharedRes.string.management_group_organization),
    SETTINGS(SharedRes.string.settings_title, Icons.Outlined.Settings),
}

/** The window's keyboard shortcuts, set by whatever is showing and asked by the window for each key. */
internal class AppShortcuts {
    var onKey: (KeyEvent) -> Boolean = { false }
}

/** The pages beside a navigation pane. Ctrl+N records a movement; Ctrl+1…9 and 0 open the first ten pages, Ctrl+E the tags, Ctrl+, the settings. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AppShell(
    db: DesktopDatabase,
    settings: DesktopSettings,
    shortcuts: AppShortcuts,
    sync: SyncListenerState,
    revision: FinancialDataRevision,
    onRestore: () -> Unit,
    page: Page,
    onPage: (Page) -> Unit,
) {
    // The pages' ViewModels live as long as the shell: a restore rebuilds it on the replaced
    // database, and what the old ones were doing ends with them.
    val store = remember { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    fun <T : ViewModel> kept(name: String, viewModel: T): T = viewModel.also { store.put(name, it) }
    val dataVersion by revision.value.collectAsState()
    val home = remember { kept("home", dashboardViewModel(db)) }
    val movements = remember { kept("movements", movementsViewModel(db, revision)) }
    val accounts = remember { kept("accounts", accountsViewModel(db)) }
    val recurring = remember { kept("recurring", recurringViewModel(db, revision)) }
    val budgets = remember { kept("budgets", budgetsViewModel(db)) }
    val analysisRepository = remember { AnalysisRepository(db.database.analysisQueries, db.database.analysisInsightsQueries) }
    val analysis = remember { kept("analysis", analysisViewModel(db, analysisRepository)) }
    val people = remember { kept("people", peopleViewModel(db)) }
    val personRepository = remember { PersonRepository(db.database.peopleQueries) }
    val trips = remember { kept("trips", tripsViewModel(db)) }
    val tripAnalysis = remember { TripAnalysisRepository(db.database.tripAnalysisQueries) }
    val categories = remember { kept("categories", categoriesViewModel(db, analysisRepository)) }
    val tags = remember { kept("tags", tagsViewModel(db)) }
    val goals = remember { kept("goals", goalsViewModel(db)) }
    var openGoalId by remember { mutableStateOf<String?>(null) }
    var openTripId by remember { mutableStateOf<String?>(null) }
    var openCategoryId by remember { mutableStateOf<String?>(null) }
    var openTagId by remember { mutableStateOf<String?>(null) }
    var openAccountId by remember { mutableStateOf<String?>(null) }
    var openTemplateId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(shortcuts) {
        shortcuts.onKey = { event ->
            // Under a form or a detail the shortcuts would restart it or change the page behind it.
            when (val shortcut = if (OpenDialogs.count > 0) null else shortcutOf(event)) {
                null -> false
                Shortcut.NewMovement -> {
                    movements.onAddClicked()
                    true
                }
                is Shortcut.Open -> {
                    onPage(shortcut.page)
                    openTripId = null
                    true
                }
            }
        }
        onDispose { shortcuts.onKey = { false } }
    }

    Row(Modifier.fillMaxSize()) {
        NavigationPane(
            selected = page,
            onSelect = {
                onPage(it)
                openTripId = null
            },
            onAddMovement = { movements.onAddClicked() },
            // On half a screen the pane keeps only its icons and the page gets the room.
            compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } < 1000.dp,
        )
        // The page on its own panel, a step above the window's ground, as Windows apps lay it out.
        Box(
            Modifier
                .padding(top = paneTop())
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 8.dp))
                .background(layerColor)
                .border(1.dp, layerBorder, RoundedCornerShape(topStart = 8.dp)),
        ) {
        when (page) {
            Page.HOME -> HomePage(
                viewModel = home,
                dataVersion = dataVersion,
                onViewMovements = { onPage(Page.MOVEMENTS) },
                onOpenMovement = movements::onDetailClicked,
                onOpenAccounts = { onPage(Page.ACCOUNTS) },
                onOpenBudgets = { onPage(Page.BUDGETS) },
                onOpenPeople = { onPage(Page.PEOPLE) },
                onOpenRecurring = { templateId ->
                    templateId?.let { openTemplateId = it }
                    onPage(Page.RECURRING)
                },
            )
            Page.MOVEMENTS -> MovementsPage(movements)
            Page.ACCOUNTS -> AccountsPage(
                viewModel = accounts,
                analysis = analysisRepository,
                dataVersion = dataVersion,
                selectedId = openAccountId,
                onSelect = { openAccountId = it },
                onAddMovement = { accountId -> movements.onAddClicked(tripId = null, accountId = accountId) },
                onMovementDetail = movements::onDetailClicked,
                onOpenGoal = { goalId ->
                    openGoalId = goalId
                    onPage(Page.GOALS)
                },
                onViewAnalysis = { account ->
                    analysis.setAccountFilter(account.id, account.name)
                    onPage(Page.ANALYSIS)
                },
            )
            Page.ANALYSIS -> AnalysisPage(
                viewModel = analysis,
                analysis = analysisRepository,
                dataVersion = dataVersion,
                movements = movements,
                onOpenMovement = movements::onDetailClicked,
                onOpenMovements = { filters ->
                    movements.onDrillDown(filters)
                    onPage(Page.MOVEMENTS)
                },
            )
            Page.PEOPLE -> PeoplePage(
                viewModel = people,
                repository = personRepository,
                dataVersion = dataVersion,
                onOpenMovement = { movements.onDetailSourceClicked(it) },
                onAddPaidByPerson = { person -> movements.onAddClicked(tripId = null, debtPayerPersonId = person.id) },
            )
            Page.TRIPS -> TripsPage(
                viewModel = trips,
                tripAnalysis = tripAnalysis,
                budgets = budgets,
                dataVersion = dataVersion,
                openTripId = openTripId,
                onOpenTrip = { openTripId = it },
                onOpenTag = { tagId ->
                    openTagId = tagId
                    onPage(Page.TAGS)
                },
                onAddMovement = { tripId -> movements.onAddClicked(tripId = tripId) },
                onOpenMovement = movements::onDetailClicked,
            )
            Page.SETTINGS -> SettingsPage(db, settings, sync, onRestore)
            Page.GOALS -> GoalsPage(
                viewModel = goals,
                dataVersion = dataVersion,
                selectedId = openGoalId,
                onSelect = { openGoalId = it },
                onOpenAccount = { accountId ->
                    openAccountId = accountId
                    onPage(Page.ACCOUNTS)
                },
            )
            Page.CATEGORIES -> CategoriesPage(
                viewModel = categories,
                budgets = budgets,
                analysis = analysisRepository,
                dataVersion = dataVersion,
                selectedId = openCategoryId,
                onSelect = { openCategoryId = it },
                onViewAnalysis = { categoryId, month ->
                    month?.let(analysis::onMonthSelected)
                    analysis.onCategoryClicked(categoryId)
                    onPage(Page.ANALYSIS)
                },
                onOpenUncategorized = {
                    movements.onDrillDown(MovementFilters(type = MovementType.EXPENSE, uncategorizedOnly = true))
                    onPage(Page.MOVEMENTS)
                },
                onAddMovement = { categoryId -> movements.onAddClicked(tripId = null, categoryId = categoryId) },
                onOpenMovement = movements::onDetailClicked,
            )
            Page.TAGS -> TagsPage(
                viewModel = tags,
                dataVersion = dataVersion,
                selectedId = openTagId,
                onSelect = { openTagId = it },
                onOpenTrip = { tripId ->
                    openTripId = tripId
                    onPage(Page.TRIPS)
                },
                onAddMovement = { tagId, tripId -> movements.onAddClicked(tripId = tripId, tagId = tagId) },
                onOpenMovement = movements::onDetailClicked,
            )
            Page.BUDGETS -> BudgetsPage(
                viewModel = budgets,
                dataVersion = dataVersion,
                movements = movements,
                onOpenRecurring = { onPage(Page.RECURRING) },
                onOpenMovement = movements::onDetailClicked,
            )
            Page.RECURRING -> RecurringPage(
                viewModel = recurring,
                selectedId = openTemplateId,
                onSelect = { openTemplateId = it },
                onOpenAccount = { accountId ->
                    openAccountId = accountId
                    onPage(Page.ACCOUNTS)
                },
                onOpenMovement = movements::onDetailClicked,
            )
        }
        }
    }

    // Above every page: any of them can open a movement or an account form.
    MovementDialogs(movements, onEditContribution = accounts::editContribution)
    // The recurring forms and confirmations, and once per start what is due.
    val movementsState by movements.state.collectAsState()
    val movementForm by movements.editor.form.collectAsState()
    RecurringReminders(
        viewModel = recurring,
        otherSheetOpen = movementForm != null || movementsState.detailMovement != null,
        openedFromReminder = false,
        onDeleteCommitted = {},
        onSkipped = {},
    )
    val accountsState by accounts.state.collectAsState()
    AccountFormSheet(accountsState, accounts)
    ContributionFormSheet(accountsState, accounts)
    accountsState.archiveCandidate?.let {
        AccountArchiveDialog(candidate = it, viewModel = accounts, onArchived = { openAccountId = null })
    }
}

// The first ten pages in the pane's order: 1 to 9, then 0, as the keys lie on the keyboard.
private val PageKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine, Key.Zero)

internal sealed interface Shortcut {
    data object NewMovement : Shortcut
    data class Open(val page: Page) : Shortcut
}

/** What a key pressed in the window asks for, if anything. */
internal fun shortcutOf(event: KeyEvent): Shortcut? =
    shortcutOf(event.key, down = event.type == KeyEventType.KeyDown, ctrl = event.isCtrlPressed, alt = event.isAltPressed)

internal fun shortcutOf(key: Key, down: Boolean, ctrl: Boolean, alt: Boolean): Shortcut? {
    // AltGr is Ctrl+Alt to Windows: typing @ or # in a field is not a shortcut.
    if (!down || !ctrl || alt) return null
    val digit = PageKeys.indexOf(key)
    return when {
        key == Key.N -> Shortcut.NewMovement
        digit >= 0 -> Shortcut.Open(Page.entries[digit])
        // The eleventh page has no digit left: E for "etiquetes".
        key == Key.E -> Shortcut.Open(Page.TAGS)
        // Where Windows apps keep their settings.
        key == Key.Comma -> Shortcut.Open(Page.SETTINGS)
        else -> null
    }
}

/** Where the content panel and the app's mark start: under the title bar, the app's own or Windows'. */
@Composable
private fun paneTop(): Dp = maxOf(8.dp, LocalTitleBarHeight.current)

/**
 * The window's left edge: the app's mark and name, the one action wanted from every page, the
 * pages by name under the phone's groups, and settings at its foot.
 */
@Composable
private fun NavigationPane(selected: Page, onSelect: (Page) -> Unit, onAddMovement: () -> Unit, compact: Boolean) {
    Column(
        Modifier.width(if (compact) 64.dp else 260.dp).fillMaxHeight().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            // The mark's top sits level with the content panel's.
            Modifier.padding(start = if (compact) 8.dp else 15.dp, top = paneTop(), bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                imageResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                filterQuality = FilterQuality.High,
            )
            if (!compact) {
                Text(stringResource(SharedRes.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        run {
            if (compact) {
                Hinted(stringResource(SharedRes.string.movement_list_add)) {
                    Box(
                        Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button, onClick = onAddMovement),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(SharedRes.string.movement_list_add),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                PrimaryButton(
                    text = stringResource(SharedRes.string.movement_list_add),
                    onClick = onAddMovement,
                    leadingIcon = Icons.Outlined.Add,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
        }
        // The pages scroll between the head and the foot when the window is short.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            var group: StringResource? = null
            Page.entries.filter { it != Page.SETTINGS }.forEach { item ->
                if (item.group != group) {
                    group = item.group
                    if (compact) {
                        if (item.group != null) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = FinanceTheme.colors.cardBorder)
                    } else item.group?.let {
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.labelMedium,
                            color = FinanceTheme.colors.mutedText,
                            modifier = Modifier.padding(start = 15.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                PaneItem(item.icon, stringResource(item.label), selected = item == selected, compact = compact, onClick = { onSelect(item) })
            }
        }
        PaneItem(
            Page.SETTINGS.icon,
            stringResource(Page.SETTINGS.label),
            selected = selected == Page.SETTINGS,
            compact = compact,
            onClick = { onSelect(Page.SETTINGS) },
        )
    }
}

@Composable
private fun PaneItem(icon: ImageVector, label: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    if (compact) {
        Hinted(label) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The selected page's mark on the pane's edge.
        val content = if (selected) accent else MaterialTheme.colorScheme.onSurface
        Box(Modifier.width(3.dp).height(20.dp).background(if (selected) accent else Color.Transparent, RoundedCornerShape(2.dp)))
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An icon standing alone says its name when the pointer rests on it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Hinted(label: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
                shadowElevation = 4.dp,
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        },
        content = content,
    )
}

internal fun movementsViewModel(db: DesktopDatabase, revision: FinancialDataRevision = FinancialDataRevision()): MovementsViewModel {
    val database = db.database
    return MovementsViewModel(
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
        categoryRepository = CategoryRepository(database.categoriesQueries),
        personRepository = PersonRepository(database.peopleQueries),
        tripRepository = TripRepository(database.tripsQueries),
        tagRepository = TagRepository(database.tagsQueries),
        splitRepository = SplitRepository(database.splitsQueries),
        templateRepository = TemplateRepository(database.templatesQueries),
        financialDataRevision = revision,
    )
}

internal fun accountsViewModel(db: DesktopDatabase): AccountsViewModel {
    val database = db.database
    return AccountsViewModel(
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
        goalRepository = GoalRepository(database.goalsQueries, database.analysisQueries),
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        templateRepository = TemplateRepository(database.templatesQueries),
        personRepository = PersonRepository(database.peopleQueries),
    )
}

internal fun recurringViewModel(db: DesktopDatabase, revision: FinancialDataRevision = FinancialDataRevision()): RecurringViewModel {
    val database = db.database
    return RecurringViewModel(
        templateRepository = TemplateRepository(database.templatesQueries),
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
        categoryRepository = CategoryRepository(database.categoriesQueries),
        tripRepository = TripRepository(database.tripsQueries),
        tagRepository = TagRepository(database.tagsQueries),
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        splitRepository = SplitRepository(database.splitsQueries),
        personRepository = PersonRepository(database.peopleQueries),
        budgetRepository = BudgetRepository(database.budgetsQueries),
        financialDataRevision = revision,
    )
}

internal fun budgetsViewModel(db: DesktopDatabase): BudgetsViewModel {
    val database = db.database
    return BudgetsViewModel(
        budgetRepository = BudgetRepository(database.budgetsQueries),
        categoryRepository = CategoryRepository(database.categoriesQueries),
        tripRepository = TripRepository(database.tripsQueries),
        templateRepository = TemplateRepository(database.templatesQueries),
        analysisRepository = AnalysisRepository(database.analysisQueries, database.analysisInsightsQueries),
    )
}

internal fun analysisViewModel(db: DesktopDatabase, analysisRepository: AnalysisRepository): AnalysisViewModel {
    val database = db.database
    return AnalysisViewModel(
        analysisRepository = analysisRepository,
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
        categoryRepository = CategoryRepository(database.categoriesQueries),
    )
}

internal fun peopleViewModel(db: DesktopDatabase): PeopleViewModel {
    val database = db.database
    return PeopleViewModel(
        personRepository = PersonRepository(database.peopleQueries),
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
    )
}

internal fun tripsViewModel(db: DesktopDatabase): TripsViewModel {
    val database = db.database
    return TripsViewModel(
        tripRepository = TripRepository(database.tripsQueries),
        tripAnalysisRepository = TripAnalysisRepository(database.tripAnalysisQueries),
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
        budgetRepository = BudgetRepository(database.budgetsQueries),
        tagRepository = TagRepository(database.tagsQueries),
    )
}

internal fun categoriesViewModel(db: DesktopDatabase, analysisRepository: AnalysisRepository): CategoriesViewModel {
    val database = db.database
    return CategoriesViewModel(
        categoryRepository = CategoryRepository(database.categoriesQueries),
        analysisRepository = analysisRepository,
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        budgetRepository = BudgetRepository(database.budgetsQueries),
        templateRepository = TemplateRepository(database.templatesQueries),
    )
}

internal fun tagsViewModel(db: DesktopDatabase): TagsViewModel {
    val database = db.database
    return TagsViewModel(
        tagRepository = TagRepository(database.tagsQueries),
        tripRepository = TripRepository(database.tripsQueries),
        categoryRepository = CategoryRepository(database.categoriesQueries),
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        tripAnalysisRepository = TripAnalysisRepository(database.tripAnalysisQueries),
    )
}

internal fun goalsViewModel(db: DesktopDatabase): GoalsViewModel {
    val database = db.database
    return GoalsViewModel(
        goalRepository = GoalRepository(database.goalsQueries, database.analysisQueries),
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
    )
}

internal fun dashboardViewModel(db: DesktopDatabase): DashboardViewModel {
    val database = db.database
    return DashboardViewModel(
        analysisRepository = AnalysisRepository(database.analysisQueries, database.analysisInsightsQueries),
        accountRepository = AccountRepository(database.accountsQueries, database.sharedAccountsQueries),
        movementRepository = MovementRepository(database.movementsQueries, database.splitsQueries),
        tripRepository = TripRepository(database.tripsQueries),
        categoryRepository = CategoryRepository(database.categoriesQueries),
        budgetRepository = BudgetRepository(database.budgetsQueries),
        templateRepository = TemplateRepository(database.templatesQueries),
        personRepository = PersonRepository(database.peopleQueries),
    )
}
