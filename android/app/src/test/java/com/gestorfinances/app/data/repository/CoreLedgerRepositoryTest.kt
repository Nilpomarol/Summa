package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLedgerRepositoryTest {
    @Test
    fun accountRepositoryKeepsOneDefaultAccount() {
        freshStore().use { store ->
            store.accounts.create(accountDraft(id = "checking", isDefault = true), createdAt = NOW)
            store.accounts.create(accountDraft(id = "cash", isDefault = true, displayOrder = 1), createdAt = LATER)

            val afterCreate = store.accounts.listActive().associateBy { it.id }
            assertFalse(afterCreate.getValue("checking").isDefault)
            assertTrue(afterCreate.getValue("cash").isDefault)

            store.accounts.update(
                accountDraft(id = "checking", isDefault = true, displayOrder = 0),
                updatedAt = LATER,
            )

            val afterUpdate = store.accounts.listActive().associateBy { it.id }
            assertTrue(afterUpdate.getValue("checking").isDefault)
            assertFalse(afterUpdate.getValue("cash").isDefault)
        }
    }

    @Test
    fun refundAddsAccountInflowAndNetsDownActualExpenseInItsPeriod() {
        freshStore().use { store ->
            store.accounts.create(accountDraft(id = "checking", startingBalanceCents = 0), createdAt = NOW)
            store.categories.create(categoryDraft(id = "food"), createdAt = NOW)
            store.movements.create(
                movementDraft("exp", MovementType.EXPENSE, 20_000, "2026-03-01", "checking", categoryId = "food"),
                createdAt = NOW,
            )
            store.movements.createRefund(
                RefundDraft(
                    id = "ref",
                    refundsExpenseId = "exp",
                    amountCents = 5_000,
                    accountId = "checking",
                    categoryId = "food",
                    date = "2026-03-02",
                    name = null,
                    payee = null,
                    notes = null,
                    actualRefundCents = null,
                ),
                createdAt = NOW,
            )

            // Inflow on the account, and actual nets down by the cash amount.
            assertEquals(-15_000L, store.accounts.getActive("checking")!!.currentBalanceCents)
            assertEquals(
                15_000L,
                store.analysis.periodTotals(fromDate = "2026-03-01", toDate = "2026-03-31").actualExpenseCents,
            )
        }
    }

    @Test
    fun sharedRefundUsesActualAdjustmentDistinctFromCashInflow() {
        freshStore().use { store ->
            store.accounts.create(accountDraft(id = "checking", startingBalanceCents = 0), createdAt = NOW)
            store.categories.create(categoryDraft(id = "food"), createdAt = NOW)
            store.movements.create(
                movementDraft("exp", MovementType.EXPENSE, 20_000, "2026-03-01", "checking", categoryId = "food"),
                createdAt = NOW,
            )
            store.movements.createRefund(
                RefundDraft(
                    id = "ref",
                    refundsExpenseId = "exp",
                    amountCents = 5_000,
                    accountId = "checking",
                    categoryId = "food",
                    date = "2026-03-02",
                    name = null,
                    payee = null,
                    notes = null,
                    actualRefundCents = 3_000,
                ),
                createdAt = NOW,
            )

            // Cash inflow is the full 5000; the actual adjustment is only the user's 3000 share.
            assertEquals(-15_000L, store.accounts.getActive("checking")!!.currentBalanceCents)
            assertEquals(
                17_000L,
                store.analysis.periodTotals(fromDate = "2026-03-01", toDate = "2026-03-31").actualExpenseCents,
            )
        }
    }

    @Test
    fun refundExposesLinkedExpenseNameAndArchivedFlagForOrphanWarning() {
        freshStore().use { store ->
            store.accounts.create(accountDraft(id = "checking", startingBalanceCents = 0), createdAt = NOW)
            store.categories.create(categoryDraft(id = "food"), createdAt = NOW)
            store.movements.create(
                movementDraft("exp", MovementType.EXPENSE, 20_000, "2026-03-01", "checking", categoryId = "food")
                    .copy(name = "Weekly shop"),
                createdAt = NOW,
            )
            store.movements.createRefund(
                RefundDraft(
                    id = "ref",
                    refundsExpenseId = "exp",
                    amountCents = 5_000,
                    accountId = "checking",
                    categoryId = "food",
                    date = "2026-03-02",
                    name = null,
                    payee = null,
                    notes = null,
                    actualRefundCents = null,
                ),
                createdAt = NOW,
            )

            val beforeArchive = store.movements.getActive("ref")!!
            assertEquals("exp", beforeArchive.refundsExpenseId)
            assertEquals("Weekly shop", beforeArchive.refundsExpenseName)
            assertFalse(beforeArchive.refundsExpenseArchived)

            store.movements.archive("exp", archivedAt = LATER)

            val afterArchive = store.movements.getActive("ref")!!
            assertEquals("Weekly shop", afterArchive.refundsExpenseName)
            assertTrue(afterArchive.refundsExpenseArchived)
        }
    }

    @Test
    fun settlementMovementExposesDirectionAndPersonNameForDisplay() {
        freshStore().use { store ->
            store.accounts.create(accountDraft(id = "checking", startingBalanceCents = 0), createdAt = NOW)
            store.people.create(
                PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null),
                createdAt = NOW,
            )
            store.movements.createSettlement(
                SettlementDraft(
                    id = "settle",
                    personId = "laura",
                    direction = SettlementDirection.USER_TO_PERSON,
                    amountCents = 1_500,
                    accountId = "checking",
                    date = "2026-03-05",
                    notes = "Tornada",
                ),
                createdAt = NOW,
            )

            val settlement = store.movements.listActive().single()
            assertEquals(MovementType.SETTLEMENT, settlement.type)
            assertEquals(SettlementDirection.USER_TO_PERSON, settlement.settlementDirection)
            assertEquals("Laura", settlement.settlementPersonName)
            assertNull(settlement.categoryId)
            // user_to_person is money leaving the account.
            assertEquals(-1_500L, store.accounts.getActive("checking")!!.currentBalanceCents)
        }
    }

    @Test
    fun movementWritesRefreshDerivedAccountBalancesAndFlow() {
        freshStore().use { store ->
            store.accounts.create(
                accountDraft(id = "checking", name = "Compte", startingBalanceCents = 10_000),
                createdAt = NOW,
            )
            store.accounts.create(
                accountDraft(id = "savings", name = "Estalvi", startingBalanceCents = 5_000, displayOrder = 1),
                createdAt = NOW,
            )
            store.categories.create(
                categoryDraft(id = "groceries", name = "Supermercat"),
                createdAt = NOW,
            )

            store.movements.create(
                movementDraft(
                    id = "expense",
                    type = MovementType.EXPENSE,
                    amountCents = 2_500,
                    date = "2026-01-01",
                    accountId = "checking",
                    categoryId = "groceries",
                    isOneTime = true,
                ),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(
                    id = "income",
                    type = MovementType.INCOME,
                    amountCents = 750,
                    date = "2026-01-02",
                    accountId = "checking",
                ),
                createdAt = LATER,
            )
            store.movements.create(
                movementDraft(
                    id = "transfer",
                    type = MovementType.TRANSFER,
                    amountCents = 1_000,
                    date = "2026-01-03",
                    accountId = "checking",
                    destinationAccountId = "savings",
                ),
                createdAt = LATER,
            )

            val accounts = store.accounts.listActive().associateBy { it.id }
            assertEquals(7_250L, accounts.getValue("checking").currentBalanceCents)
            assertEquals(6_000L, accounts.getValue("savings").currentBalanceCents)

            val checkingFlow = store.movements.accountFlowForAccount("checking")
            assertEquals(listOf(-1_000L, 750L, -2_500L), checkingFlow.map { it.deltaCents })
            assertEquals("Supermercat", checkingFlow.last().categoryName)
            assertTrue(store.movements.getActive("expense")!!.isOneTime)

            // movementsForCategory derives its sign from v_account_flow (M14) rather than a
            // hand-rolled CASE, so an expense must come back negative like accountFlowForAccount.
            val groceriesFlow = store.movements.movementsForCategory("groceries")
            assertEquals(listOf(-2_500L), groceriesFlow.map { it.deltaCents })
            assertEquals("Supermercat", groceriesFlow.single().categoryName)

            store.movements.archive("expense", archivedAt = LATER)

            assertNull(store.movements.getActive("expense"))
            assertEquals(9_750L, store.accounts.getActive("checking")!!.currentBalanceCents)
            assertEquals(listOf("transfer", "income"), store.movements.listActive().map { it.id })
        }
    }

    @Test
    fun analysisRepositoryReadsCurrentPeriodDashboardTotals() {
        freshStore().use { store ->
            store.accounts.create(
                accountDraft(id = "checking", name = "Compte", startingBalanceCents = 10_000),
                createdAt = NOW,
            )
            store.categories.create(
                categoryDraft(
                    id = "salary",
                    name = "Sou",
                    kind = CategoryKind.INCOME,
                    nature = CategoryNature.FIXED,
                ),
                createdAt = NOW,
            )
            store.categories.create(
                categoryDraft(id = "groceries", name = "Supermercat", displayOrder = 1),
                createdAt = NOW,
            )

            store.movements.create(
                movementDraft(
                    id = "salary-june",
                    type = MovementType.INCOME,
                    amountCents = 250_000,
                    date = "2026-06-01",
                    accountId = "checking",
                    categoryId = "salary",
                ),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(
                    id = "groceries",
                    type = MovementType.EXPENSE,
                    amountCents = 3_500,
                    date = "2026-06-05",
                    accountId = "checking",
                    categoryId = "groceries",
                ),
                createdAt = NOW,
            )
            store.movements.create(
                movementDraft(
                    id = "one-time",
                    type = MovementType.EXPENSE,
                    amountCents = 10_000,
                    date = "2026-06-10",
                    accountId = "checking",
                    categoryId = "groceries",
                    isOneTime = true,
                ),
                createdAt = NOW,
            )

            val totals = store.analysis.periodTotals(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
            )
            assertEquals(246_500L, totals.netWorthCents)
            assertEquals(250_000L, totals.actualIncomeCents)
            assertEquals(13_500L, totals.actualExpenseCents)
            assertEquals(236_500L, totals.netActualCents)

            val withoutOneTime = store.analysis.periodTotals(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
                oneTimeMode = AnalysisOneTimeMode.EXCLUDE,
            )
            assertEquals(3_500L, withoutOneTime.actualExpenseCents)

            val variableTotals = store.analysis.periodTotals(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
                categoryNature = AnalysisCategoryNature.VARIABLE,
            )
            assertEquals(0L, variableTotals.actualIncomeCents)
            assertEquals(13_500L, variableTotals.actualExpenseCents)
            assertEquals(-13_500L, variableTotals.netActualCents)

            val oneTimeOnlyTotals = store.analysis.periodTotals(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
                oneTimeMode = AnalysisOneTimeMode.ONLY,
            )
            assertEquals(0L, oneTimeOnlyTotals.actualIncomeCents)
            assertEquals(10_000L, oneTimeOnlyTotals.actualExpenseCents)

            val categories = store.analysis.actualByCategory(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
            ).associateBy { it.categoryId }
            assertEquals(250_000L, categories.getValue("salary").incomeCents)
            assertEquals(13_500L, categories.getValue("groceries").expenseCents)

            val variableCategories = store.analysis.actualByCategory(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
                categoryNature = AnalysisCategoryNature.VARIABLE,
            ).associateBy { it.categoryId }
            assertEquals(setOf("groceries"), variableCategories.keys)

            val daily = store.analysis.incomeVsExpense(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
            ).associateBy { it.bucket }
            assertEquals(250_000L, daily.getValue("2026-06-01").incomeCents)
            assertEquals(3_500L, daily.getValue("2026-06-05").expenseCents)
            assertEquals(10_000L, daily.getValue("2026-06-10").expenseCents)

            val flow = store.analysis.accountFlowOverTime(
                fromDate = "2026-06-01",
                toDate = "2026-07-01",
                bucket = AnalysisBucket.DAY,
            ).associateBy { it.bucket }
            assertEquals(250_000L, flow.getValue("2026-06-01").deltaCents)
            assertEquals(250_000L, flow.getValue("2026-06-01").bucketDeltaCents)
            assertEquals(-10_000L, flow.getValue("2026-06-10").bucketDeltaCents)
        }
    }

    @Test
    fun categoryRepositorySoftArchivesOnlyArchivedRows() {
        freshStore().use { store ->
            store.categories.create(categoryDraft(id = "food", name = "Menjar"), createdAt = NOW)
            store.categories.create(
                categoryDraft(id = "groceries", name = "Supermercat", parentId = "food", displayOrder = 1),
                createdAt = NOW,
            )

            assertEquals(listOf("food", "groceries"), store.categories.listActive().map { it.id })

            store.categories.archive("groceries", archivedAt = LATER)

            assertEquals(listOf("food"), store.categories.listActive().map { it.id })
            assertNull(store.categories.getActive("groceries"))
        }
    }

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries),
            people = PersonRepository(database.peopleQueries),
            analysis = AnalysisRepository(database.analysisQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val analysis: AnalysisRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private fun accountDraft(
        id: String,
        name: String = id,
        startingBalanceCents: Long = 0,
        isDefault: Boolean = false,
        displayOrder: Long = 0,
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
            lowBalanceThresholdCents = null,
        )

    private fun categoryDraft(
        id: String,
        name: String = id,
        kind: CategoryKind = CategoryKind.EXPENSE,
        nature: CategoryNature = CategoryNature.VARIABLE,
        parentId: String? = null,
        displayOrder: Long = 0,
    ): CategoryDraft =
        CategoryDraft(
            id = id,
            name = name,
            kind = kind,
            nature = nature,
            parentId = parentId,
            icon = null,
            color = null,
            displayOrder = displayOrder,
        )

    private fun movementDraft(
        id: String,
        type: MovementType,
        amountCents: Long,
        date: String,
        accountId: String,
        destinationAccountId: String? = null,
        categoryId: String? = null,
        isOneTime: Boolean = false,
    ): MovementDraft =
        MovementDraft(
            id = id,
            type = type,
            amountCents = amountCents,
            date = date,
            accountId = accountId,
            destinationAccountId = destinationAccountId,
            categoryId = categoryId,
            name = id,
            payee = null,
            notes = null,
            isOneTime = isOneTime,
        )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val LATER = "2026-01-02T00:00:00Z"
    }
}
