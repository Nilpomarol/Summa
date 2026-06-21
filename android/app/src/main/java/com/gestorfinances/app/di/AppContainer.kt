package com.gestorfinances.app.di

import android.content.Context
import com.gestorfinances.app.data.db.DatabaseDriverFactory
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MetaRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.notifications.FinanceNotificationCoordinator
import com.gestorfinances.app.notifications.NotificationPreferences

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: GestorDatabase by lazy {
        GestorDatabase(DatabaseDriverFactory(appContext).create())
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(database.accountsQueries)
    }

    val analysisRepository: AnalysisRepository by lazy {
        AnalysisRepository(database.analysisQueries)
    }

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepository(database.budgetsQueries)
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database.categoriesQueries)
    }

    val metaRepository: MetaRepository by lazy {
        MetaRepository(database.metaQueries)
    }

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

    val notificationPreferences: NotificationPreferences by lazy {
        NotificationPreferences(appContext)
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
}
