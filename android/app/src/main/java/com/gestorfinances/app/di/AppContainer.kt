package com.gestorfinances.app.di

import android.content.Context
import com.gestorfinances.app.data.backup.BackupFolderStore
import com.gestorfinances.app.data.backup.BackupSnapshotService
import com.gestorfinances.app.data.backup.AutoBackupPreferences
import com.gestorfinances.app.data.backup.WorkManagerAutoBackupScheduler
import com.gestorfinances.app.data.db.DatabaseDriverFactory
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.GoalRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MetaRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TripAnalysisRepository
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.FinancialDataRevision
import com.gestorfinances.app.notifications.FinanceNotificationCoordinator
import com.gestorfinances.app.notifications.NotificationPreferences
import com.gestorfinances.app.ui.theme.ThemePreferences

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val driverLazy = lazy {
        DatabaseDriverFactory(appContext).create()
    }
    private val driver by driverLazy

    private val database: GestorDatabase by lazy {
        GestorDatabase(driver)
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
    }

    val analysisRepository: AnalysisRepository by lazy {
        AnalysisRepository(database.analysisQueries)
    }

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepository(database.budgetsQueries)
    }

    val goalRepository: GoalRepository by lazy {
        GoalRepository(database.goalsQueries, database.analysisQueries)
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database.categoriesQueries)
    }

    val metaRepository: MetaRepository by lazy {
        MetaRepository(database.metaQueries)
    }

    /** Advanced after each committed write from an Activity-wide overlay; see [FinancialDataRevision]. */
    val financialDataRevision = FinancialDataRevision()

    val movementRepository: MovementRepository by lazy {
        MovementRepository(database.movementsQueries, database.splitsQueries)
    }

    val personRepository: PersonRepository by lazy {
        PersonRepository(database.peopleQueries)
    }

    val splitRepository: SplitRepository by lazy {
        SplitRepository(database.splitsQueries)
    }

    val templateRepository: TemplateRepository by lazy {
        TemplateRepository(database.templatesQueries)
    }

    val tagRepository: TagRepository by lazy {
        TagRepository(database.tagsQueries)
    }

    val tripRepository: TripRepository by lazy {
        TripRepository(database.tripsQueries)
    }

    val tripAnalysisRepository: TripAnalysisRepository by lazy {
        TripAnalysisRepository(database.tripAnalysisQueries)
    }

    val notificationPreferences: NotificationPreferences by lazy {
        NotificationPreferences(appContext)
    }

    val themePreferences: ThemePreferences by lazy {
        ThemePreferences(appContext)
    }

    val backupFolderStore: BackupFolderStore by lazy {
        BackupFolderStore(appContext)
    }

    val autoBackupPreferences: AutoBackupPreferences by lazy {
        AutoBackupPreferences(appContext)
    }

    val autoBackupScheduler: WorkManagerAutoBackupScheduler by lazy {
        WorkManagerAutoBackupScheduler(appContext)
    }

    val backupSnapshotService: BackupSnapshotService by lazy {
        BackupSnapshotService(
            context = appContext,
            driver = driver,
            metaRepository = metaRepository,
            closeDatabase = ::close,
        )
    }

    val notificationCoordinator: FinanceNotificationCoordinator by lazy {
        FinanceNotificationCoordinator(
            context = appContext,
            templateRepository = templateRepository,
            budgetRepository = budgetRepository,
            accountRepository = accountRepository,
            preferences = notificationPreferences,
        )
    }

    fun close() {
        if (driverLazy.isInitialized()) {
            runCatching { driver.close() }
        }
    }
}
