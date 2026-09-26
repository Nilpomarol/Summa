package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.RepositoryTestSupport
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Regression: a movement saved while Analysis stayed open left it showing the old totals,
    // because an unchanged filter and period reused the loaded result.
    @Test
    fun aMovementWriteReloadsTheSameAnalysisPeriod() = runTest(dispatcher) {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries)
        val movements = MovementRepository(database.movementsQueries, database.splitsQueries)
        accounts.create(
            AccountDraft(
                id = "checking",
                name = "Compte",
                startingBalanceCents = 0,
                type = AccountType.BANK,
                icon = null,
                color = null,
                isDefault = true,
                displayOrder = 0,
                lowBalanceThresholdCents = null,
            ),
            createdAt = NOW,
        )
        movements.create(expense("coffee", 1_000), createdAt = NOW)
        val viewModel = AnalysisViewModel(
            analysisRepository = AnalysisRepository(database.analysisQueries),
            accountRepository = accounts,
            categoryRepository = CategoryRepository(database.categoriesQueries),
            todayProvider = { LocalDate.parse("2026-03-20") },
            ioDispatcher = dispatcher,
        )

        viewModel.onScreenShown(dataVersion = 0L)
        advanceUntilIdle()
        assertEquals(1_000L, viewModel.state.value.resum!!.totals.actualExpenseCents)

        // Shown again with nothing changed, the loaded result stands.
        viewModel.onScreenShown(dataVersion = 0L)
        advanceUntilIdle()
        assertEquals(1_000L, viewModel.state.value.resum!!.totals.actualExpenseCents)

        movements.create(expense("lunch", 2_000), createdAt = NOW)
        viewModel.onScreenShown(dataVersion = 1L)
        advanceUntilIdle()

        assertEquals(3_000L, viewModel.state.value.resum!!.totals.actualExpenseCents)
    }

    private fun expense(id: String, amountCents: Long) = MovementDraft(
        id = id,
        type = MovementType.EXPENSE,
        amountCents = amountCents,
        date = "2026-03-10",
        accountId = "checking",
        destinationAccountId = null,
        categoryId = null,
        name = id,
        payee = null,
        notes = null,
        isOneTime = false,
    )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}
