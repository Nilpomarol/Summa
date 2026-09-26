package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An expense another person paid is an ordinary movement with a payer instead of an account. */
class PersonPaidExpenseTest {
    @Test
    fun aPersonPaidExpenseIsDebtAndActualExpenseButMovesNoAccount() {
        freshStore().use { store ->
            store.people.create(PersonDraft(id = "laura", name = "Laura", avatar = null, color = null, notes = null), createdAt = NOW)
            store.categories.create(
                CategoryDraft(
                    id = "food",
                    name = "Menjar",
                    kind = CategoryKind.EXPENSE,
                    nature = CategoryNature.VARIABLE,
                    parentId = null,
                    icon = null,
                    color = null,
                    displayOrder = 0,
                ),
                createdAt = NOW,
            )

            store.movements.create(
                personPaidExpense(id = "laura-paid", payerPersonId = "laura", amountCents = 400, date = "2026-01-01", name = "Sopar", categoryId = "food"),
                createdAt = NOW,
            )

            val totals = store.analysis.periodTotals(fromDate = "2026-01-01", toDate = "2026-01-02")
            val categories = store.analysis.actualByCategory(fromDate = "2026-01-01", toDate = "2026-01-02")
                .associateBy { it.categoryId }

            val expense = store.movements.listActive().single()
            assertEquals("laura-paid", expense.id)
            assertEquals(MovementType.EXPENSE, expense.type)
            assertTrue(expense.paidByPerson)
            assertFalse(expense.isShared)
            assertEquals("laura", expense.payerId)
            assertEquals("Laura", expense.paidByPersonName)
            assertEquals(400L, expense.userShareCents)
            assertNull(expense.accountId)

            assertEquals(-400L, store.people.getActive("laura")!!.balanceCents)
            assertEquals(400L, totals.actualExpenseCents)
            assertEquals(0L, totals.accountFlowCents)
            assertEquals(400L, categories.getValue("food").expenseCents)
        }
    }

    @Test
    fun aPersonPaidExpenseCarriesItsTripAndTag() {
        freshStore().use { store ->
            store.people.create(PersonDraft(id = "anna", name = "Anna", avatar = null, color = null, notes = null), createdAt = NOW)
            store.trips.create(
                TripDraft(
                    id = "trip-bcn",
                    name = "Barcelona",
                    type = TripType.TRIP,
                    status = TripStatus.ACTIVE,
                    startDate = null,
                    endDate = null,
                    icon = null,
                    color = null,
                    notes = null,
                    defaultAccountId = null,
                ),
                createdAt = NOW,
            )
            store.tags.create(TagDraft(id = "tag-1", name = "Transport", icon = null, color = null, tripId = "trip-bcn"), createdAt = NOW)

            store.movements.create(
                personPaidExpense(
                    id = "taxi",
                    payerPersonId = "anna",
                    amountCents = 1_200,
                    date = "2026-06-01",
                    name = "Taxi aeroport",
                    tripId = "trip-bcn",
                    tagId = "tag-1",
                ),
                createdAt = NOW,
            )

            val expense = store.movements.listActive().single()
            assertEquals("trip-bcn", expense.tripId)
            assertEquals("tag-1", expense.tagId)
            assertEquals(-1_200L, store.people.getActive("anna")!!.balanceCents)
        }
    }

    @Test
    fun aPayerChangeUpdatesTheSameMovementBothWays() {
        freshStore().use { store ->
            store.people.create(PersonDraft(id = "marc", name = "Marc", avatar = null, color = null, notes = null), createdAt = NOW)
            store.accounts.create(checking(), createdAt = NOW)
            val paidByMe = MovementDraft(
                id = "cafe",
                type = MovementType.EXPENSE,
                amountCents = 500,
                date = "2026-01-01",
                accountId = "checking",
                destinationAccountId = null,
                categoryId = null,
                name = "Cafè",
                payee = null,
                notes = null,
                isOneTime = false,
            )
            store.movements.create(paidByMe, createdAt = NOW)

            store.movements.update(
                personPaidExpense(id = "cafe", payerPersonId = "marc", amountCents = 500, date = "2026-01-01", name = "Cafè"),
                updatedAt = NOW2,
            )
            assertTrue(store.movements.getActive("cafe")!!.paidByPerson)
            assertEquals(-500L, store.people.getActive("marc")!!.balanceCents)
            assertEquals(10_000L, store.accounts.getActive("checking")!!.currentBalanceCents)

            store.movements.update(paidByMe.copy(splitWrite = MovementSplitWrite.Remove), updatedAt = NOW3)
            val back = store.movements.listActive().single()
            assertEquals("cafe", back.id)
            assertFalse(back.paidByPerson)
            assertFalse(back.isShared)
            assertEquals(0L, store.people.getActive("marc")!!.balanceCents)
            assertEquals(9_500L, store.accounts.getActive("checking")!!.currentBalanceCents)
        }
    }

    @Test
    fun aFailedPayerChangeLeavesTheMovementUntouched() {
        freshStore().use { store ->
            store.people.create(PersonDraft(id = "marc", name = "Marc", avatar = null, color = null, notes = null), createdAt = NOW)
            store.movements.create(
                personPaidExpense(id = "cafe", payerPersonId = "marc", amountCents = 500, date = "2026-01-01", name = "Cafè"),
                createdAt = NOW,
            )

            // The payer does not exist, so the database rejects the write after the split was touched.
            val thrown = runCatching {
                store.movements.update(
                    personPaidExpense(id = "cafe", payerPersonId = "ghost", amountCents = 600, date = "2026-01-01", name = "Cafè"),
                    updatedAt = NOW2,
                )
            }
            assertTrue("expected the foreign key to reject the payer", thrown.isFailure)

            val after = store.movements.listActive().single()
            assertEquals("marc", after.payerId)
            assertEquals(500L, after.amountCents)
            assertEquals(-500L, store.people.getActive("marc")!!.balanceCents)
        }
    }

    @Test
    fun aPersonPaidExpenseIsSavedOnlyAsAnExpenseWithItsOwedLineAndNoAccountOrRecurrence() {
        freshStore().use { store ->
            store.people.create(PersonDraft(id = "marc", name = "Marc", avatar = null, color = null, notes = null), createdAt = NOW)
            store.accounts.create(checking(), createdAt = NOW)
            val valid = personPaidExpense(id = "x", payerPersonId = "marc", amountCents = 500, date = "2026-01-01", name = "Cafè")

            listOf(
                valid.copy(accountId = "checking"),
                valid.copy(type = MovementType.INCOME),
                valid.copy(splitWrite = MovementSplitWrite.KeepExisting),
                valid.copy(expenseFunding = ExpenseFunding.SHARED_ACCOUNT),
                valid.copy(accountId = null, payerPersonId = null),
            ).forEach { invalid ->
                assertTrue(invalid.toString(), runCatching { store.movements.create(invalid, createdAt = NOW) }.isFailure)
            }
            assertTrue(store.movements.listActive().isEmpty())
        }
    }

    private fun checking() = AccountDraft(
        id = "checking",
        name = "Compte",
        startingBalanceCents = 10_000,
        type = AccountType.BANK,
        icon = null,
        color = null,
        isDefault = true,
        displayOrder = 0,
        lowBalanceThresholdCents = null,
    )

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            analysis = AnalysisRepository(database.analysisQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            people = PersonRepository(database.peopleQueries),
            trips = TripRepository(database.tripsQueries),
            tags = TagRepository(database.tagsQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val analysis: AnalysisRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val trips: TripRepository,
        val tags: TagRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val NOW2 = "2026-01-02T00:00:00Z"
        const val NOW3 = "2026-01-03T00:00:00Z"
    }
}
