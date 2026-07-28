package com.gestorfinances.app.ui.dashboard

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TripRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theSummaryLeadsWithTheDefaultAccountAndOnlyTheFiveLatestMovements() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            store.categories.create(categoryDraft("food", "Menjar"), createdAt = NOW)
            repeat(6) { index ->
                store.movements.create(
                    expenseDraft(id = "e$index", date = "2026-07-0${index + 1}", cents = 1_000L),
                    createdAt = NOW,
                )
            }
            val viewModel = viewModel(store)

            viewModel.onScreenShown()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("main", state.mainAccount?.id)
            assertEquals(5, state.latestMovements.size)
            assertEquals(YearMonth.of(2026, 7), state.month)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun pickingAnAccountKeepsThatAccountLeadingAfterAReload() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            val viewModel = viewModel(store)
            viewModel.onScreenShown()
            advanceUntilIdle()

            viewModel.onAccountSelected("savings")
            viewModel.onCategoryModeChanged(CategoryDisplayMode.INCOME)
            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("savings", state.mainAccount?.id)
            assertEquals(CategoryDisplayMode.INCOME, state.categoryMode)
        }
    }

    @Test
    fun anAccountBelowItsOwnThresholdIsTheOnlyAttentionItem() = runTest(dispatcher) {
        freshStore().use { store ->
            store.accounts.create(
                accountDraft(
                    id = "main",
                    name = "Compte principal",
                    startingBalanceCents = 5_000L,
                    isDefault = true,
                    lowBalanceThresholdCents = 10_000L,
                ),
                createdAt = NOW,
            )
            val viewModel = viewModel(store)

            viewModel.onScreenShown()
            advanceUntilIdle()

            assertEquals("main", viewModel.state.value.lowBalanceAccount?.id)
        }
    }

    @Test
    fun noAttentionItemWhenEveryAccountSitsAboveItsThreshold() = runTest(dispatcher) {
        freshStore().use { store ->
            store.seedAccounts()
            val viewModel = viewModel(store)

            viewModel.onScreenShown()
            advanceUntilIdle()

            assertNull(viewModel.state.value.lowBalanceAccount)
        }
    }

    private fun viewModel(store: TestStore): DashboardViewModel =
        DashboardViewModel(
            analysisRepository = store.analysis,
            accountRepository = store.accounts,
            movementRepository = store.movements,
            tripRepository = store.trips,
            categoryRepository = store.categories,
            todayProvider = { LocalDate.parse("2026-07-15") },
            ioDispatcher = dispatcher,
        )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            analysis = AnalysisRepository(database.analysisQueries),
            accounts = AccountRepository(database.accountsQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            trips = TripRepository(database.tripsQueries),
            categories = CategoryRepository(database.categoriesQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val analysis: AnalysisRepository,
        val accounts: AccountRepository,
        val movements: MovementRepository,
        val trips: TripRepository,
        val categories: CategoryRepository,
    ) : AutoCloseable {
        /** Two healthy accounts; the second one is the default so ordering cannot fake the pick. */
        fun seedAccounts() {
            accounts.create(
                accountDraft(
                    id = "savings",
                    name = "Estalvis",
                    startingBalanceCents = 100_000L,
                    isDefault = false,
                    displayOrder = 0,
                ),
                createdAt = NOW,
            )
            accounts.create(
                accountDraft(
                    id = "main",
                    name = "Compte principal",
                    startingBalanceCents = 50_000L,
                    isDefault = true,
                    displayOrder = 1,
                ),
                createdAt = NOW,
            )
        }

        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"

        fun accountDraft(
            id: String,
            name: String,
            startingBalanceCents: Long,
            isDefault: Boolean,
            displayOrder: Long = 0,
            lowBalanceThresholdCents: Long? = null,
        ): AccountDraft =
            AccountDraft(
                id = id,
                name = name,
                startingBalanceCents = startingBalanceCents,
                type = AccountType.BANK,
                icon = null,
                color = null,
                isDefault = isDefault,
                displayOrder = displayOrder,
                lowBalanceThresholdCents = lowBalanceThresholdCents,
            )

        fun categoryDraft(id: String, name: String): CategoryDraft =
            CategoryDraft(
                id = id,
                name = name,
                kind = CategoryKind.EXPENSE,
                nature = CategoryNature.VARIABLE,
                parentId = null,
                icon = null,
                color = null,
                displayOrder = 0,
            )

        fun expenseDraft(id: String, date: String, cents: Long): MovementDraft =
            MovementDraft(
                id = id,
                type = MovementType.EXPENSE,
                amountCents = cents,
                date = date,
                accountId = "main",
                destinationAccountId = null,
                categoryId = "food",
                name = id,
                payee = null,
                notes = null,
                isOneTime = false,
            )
    }
}
