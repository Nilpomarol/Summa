package com.gestorfinances.app.ui.onboarding

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.account_validation_name_required
import com.gestorfinances.ui.resources.account_validation_starting_balance_invalid
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** All setup and relaunch cases use an isolated in-memory ledger. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var accounts: AccountRepository
    private lateinit var categories: CategoryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        RepositoryTestSupport.createFreshInstallSchema(driver)
        val database = GestorDatabase(driver)
        accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        categories = CategoryRepository(database.categoriesQueries)
    }

    @After
    fun tearDown() {
        driver.close()
        Dispatchers.resetMain()
    }

    @Test
    fun firstAccountCompletesSetupWithDefaultsAndRelaunchSkipsIt() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        assertTrue(model.state.value.needsOnboarding)
        assertFalse(model.state.value.isLoading)
        model.onFormChanged(model.state.value.form.copy(accountName = "  Banc  "))
        model.onCreateClicked(seeds)
        model.onCreateClicked(seeds) // A fast second tap must not create another account.
        advanceUntilIdle()

        assertFalse(model.state.value.needsOnboarding)
        val account = accounts.listActive().single()
        assertEquals("Banc", account.name)
        assertEquals(0L, account.currentBalanceCents)
        assertEquals(AccountType.BANK, account.type)
        assertEquals(AccountOwnershipKind.PERSONAL, account.ownershipKind)
        assertTrue(account.isDefault)
        assertNull(account.lowBalanceThresholdCents)
        assertEquals("Menjar", categories.listActive().single().name)

        val relaunched = model()
        advanceUntilIdle()
        assertFalse(relaunched.state.value.needsOnboarding)
    }

    @Test
    fun archivingLastAccountDoesNotReopenFirstTimeSetup() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onFormChanged(model.state.value.form.copy(accountName = "Banc"))
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        accounts.archive(accounts.listActive().single().id, NOW)
        assertTrue(accounts.listActive().isEmpty())

        val relaunched = model()
        advanceUntilIdle()
        assertFalse(relaunched.state.value.needsOnboarding)
    }

    @Test
    fun existingAccountBypassesSetupWithoutChangingData() = runTest(dispatcher) {
        accounts.create(AccountDraft("existing", "Efectiu", 12345, AccountType.CASH,
            null, null, true, 0, null), NOW)
        val before = accounts.listActive()
        val model = model()
        advanceUntilIdle()
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        assertFalse(model.state.value.needsOnboarding)
        assertEquals(before, accounts.listActive())
        assertTrue(categories.listActive().isEmpty())
    }

    @Test
    fun validationKeepsSetupOpenAndPreservesEnteredValues() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onCreateClicked(seeds)
        assertEquals(Res.string.account_validation_name_required, model.state.value.form.errorRes)
        model.onFormChanged(model.state.value.form.copy(accountName = "Banc", startingBalance = "abc"))
        assertNull(model.state.value.form.errorRes)
        model.onCreateClicked(seeds)
        assertEquals(Res.string.account_validation_starting_balance_invalid, model.state.value.form.errorRes)
        assertEquals("Banc", model.state.value.form.accountName)
        assertTrue(model.state.value.needsOnboarding)
        assertFalse(accounts.hasAnyAccount())

        model.onFormChanged(model.state.value.form.copy(startingBalance = "-12,50"))
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        assertFalse(model.state.value.needsOnboarding)
        assertEquals(-1250L, accounts.listActive().single().currentBalanceCents)
    }

    @Test
    fun setupKeepsExistingCategories() = runTest(dispatcher) {
        categories.create(CategoryDraft("existing", "Personalitzada", CategoryKind.EXPENSE,
            CategoryNature.VARIABLE, null, null, null, 0), NOW)
        val before = categories.listActive()
        val model = model()
        advanceUntilIdle()
        model.onFormChanged(model.state.value.form.copy(accountName = "Banc"))
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        assertFalse(model.state.value.needsOnboarding)
        assertEquals(before, categories.listActive())
    }

    @Test
    fun categoryFailureRollsBackAccountAndAllowsRetry() = runTest(dispatcher) {
        driver.execute(null, "CREATE TRIGGER fail_seed BEFORE INSERT ON categories BEGIN SELECT RAISE(ABORT, 'test failure'); END", 0)
        val model = model()
        advanceUntilIdle()
        model.onFormChanged(model.state.value.form.copy(accountName = "Banc"))
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        assertTrue(model.state.value.needsOnboarding)
        assertFalse(model.state.value.isSaving)
        assertNotNull(model.state.value.form.errorMessage)
        assertEquals("Banc", model.state.value.form.accountName)
        assertFalse(accounts.hasAnyAccount())
        driver.execute(null, "DROP TRIGGER fail_seed", 0)
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        assertFalse(model.state.value.needsOnboarding)
        assertEquals(1, accounts.listActive().size)
    }

    private fun model() = OnboardingViewModel(accounts, categories, dispatcher)

    @Test
    fun unreadableAccountStateShowsRetryInsteadOfInvitingNewSetup() = runTest(dispatcher) {
        driver.execute(null, "ALTER TABLE accounts RENAME TO accounts_unavailable", 0)
        val model = model()
        advanceUntilIdle()
        assertFalse(model.state.value.isLoading)
        assertNotNull(model.state.value.loadErrorMessage)
        model.onFormChanged(model.state.value.form.copy(accountName = "Banc"))
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        assertFalse(model.state.value.isSaving)
        assertNull(model.state.value.form.errorMessage)

        driver.execute(null, "ALTER TABLE accounts_unavailable RENAME TO accounts", 0)
        model.onRetry()
        advanceUntilIdle()
        assertNull(model.state.value.loadErrorMessage)
        assertTrue(model.state.value.needsOnboarding)
        model.onCreateClicked(seeds)
        advanceUntilIdle()
        assertFalse(model.state.value.needsOnboarding)
        assertEquals(1, accounts.listActive().size)
    }

    private companion object {
        const val NOW = "2026-09-27T10:00:00Z"
        val seeds = listOf(DefaultCategorySeed("Menjar", CategoryKind.EXPENSE,
            CategoryNature.VARIABLE, "restaurant", "#B5614A", 0))
    }
}
