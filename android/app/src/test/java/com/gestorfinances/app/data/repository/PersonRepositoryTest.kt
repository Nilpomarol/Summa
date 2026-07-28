package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.domain.rules.SplitCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonRepositoryTest {
    @Test
    fun personRepositoryCreatesUpdatesAndSoftArchivesPeople() {
        freshStore().use { store ->
            store.people.create(
                PersonDraft(
                    id = "laura",
                    name = "Laura",
                    avatar = null,
                    color = null,
                    notes = "Sopars",
                ),
                createdAt = NOW,
            )

            assertEquals("Laura", store.people.getActive("laura")!!.name)

            store.people.update(
                PersonDraft(
                    id = "laura",
                    name = "Laura M.",
                    avatar = null,
                    color = null,
                    notes = "Viatges i sopars",
                ),
                updatedAt = LATER,
            )

            val updated = store.people.getActive("laura")!!
            assertEquals("Laura M.", updated.name)
            assertEquals("Viatges i sopars", updated.notes)

            store.people.archive("laura", archivedAt = LATER)

            assertNull(store.people.getActive("laura"))
            assertEquals(emptyList<PersonSummary>(), store.people.listActive())
        }
    }

    @Test
    fun personRepositoryReadsBalanceFromCanonicalView() {
        freshStore().use { store ->
            seedUserFrontedSplit(store)

            val person = store.people.getActive("laura")!!

            assertEquals(600L, person.balanceCents)
            assertEquals(listOf("laura" to 600L), store.people.listActive().map { it.id to it.balanceCents })
        }
    }

    @Test
    fun personRepositoryReadsItemizedBalanceBreakdown() {
        freshStore().use { store ->
            seedUserFrontedSplit(store)
            store.splits.createExternalPaidByPerson(
                ExternalSplitDraft(
                    id = "external-cinema",
                    payerPersonId = "laura",
                    totalAmountCents = 250,
                    userShareCents = 250,
                    date = "2026-01-03",
                    description = "Cinema",
                    categoryId = null,
                ),
                createdAt = NOW,
            )
            store.driver.execute(
                null,
                """
                INSERT INTO movements(
                    id, type, amount_cents, date, account_id, name, notes, is_one_time,
                    person_id, settlement_direction, created_at, updated_at
                ) VALUES (
                    'settlement-in', 'settlement', 100, '2026-01-04', 'checking', NULL, 'Laura paga',
                    0, 'laura', 'person_to_user', '$NOW', '$NOW'
                )
                """.trimIndent(),
                0,
            )
            store.driver.execute(
                null,
                """
                INSERT INTO movements(
                    id, type, amount_cents, date, account_id, name, notes, is_one_time,
                    person_id, settlement_direction, created_at, updated_at
                ) VALUES (
                    'settlement-out', 'settlement', 50, '2026-01-05', 'checking', NULL, 'Pagament a Laura',
                    0, 'laura', 'user_to_person', '$NOW', '$NOW'
                )
                """.trimIndent(),
                0,
            )

            val items = store.people.balanceItemsForPerson("laura")

            assertEquals(
                listOf(
                    PersonBalanceItemType.SETTLEMENT_OUT,
                    PersonBalanceItemType.SETTLEMENT_IN,
                    PersonBalanceItemType.PERSON_PAID,
                    PersonBalanceItemType.USER_PAID,
                ),
                items.map { it.type },
            )
            assertEquals(listOf(50L, -100L, -250L, 600L), items.map { it.effectCents })
            assertEquals(300L, store.people.getActive("laura")!!.balanceCents)
        }
    }

    @Test
    fun debtBalanceGoldenScenarioHoldsThroughRepositories() {
        // Mirrors shared/golden/debt_balance.json through the real SQLDelight-backed
        // repositories: a user-fronted split, a external-payer external split, and a settlement.
        freshStore().use { store ->
            store.accounts.create(
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
            store.people.create(PersonDraft("pA", "Anna", null, null, null), createdAt = NOW)
            store.people.create(PersonDraft("pB", "Bru", null, null, null), createdAt = NOW)

            // pA owes the user 2500 from a user-fronted shared expense.
            store.movements.create(
                MovementDraft(
                    id = "mv1",
                    type = MovementType.EXPENSE,
                    amountCents = 5_000,
                    date = "2026-03-01",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = null,
                    name = "Sopar",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                    splitWrite = MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EXACT,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, null, 2_500),
                                SplitLineDraft(SplitParticipantKind.PERSON, "pA", 2_500),
                            ),
                        ),
                    ),
                ),
                createdAt = NOW,
            )

            // The user owes pB 1500 from a external-payer external split.
            store.splits.createExternalPaidByPerson(
                ExternalSplitDraft(
                    id = "s2",
                    payerPersonId = "pB",
                    totalAmountCents = 1_500,
                    userShareCents = 1_500,
                    date = "2026-03-03",
                    description = "Taxi",
                    categoryId = null,
                ),
                createdAt = NOW,
            )

            // pA pays the user back 1000.
            store.movements.createSettlement(
                SettlementDraft(
                    id = "mv2",
                    personId = "pA",
                    direction = SettlementDirection.PERSON_TO_USER,
                    amountCents = 1_000,
                    accountId = "checking",
                    date = "2026-03-05",
                    notes = null,
                ),
                createdAt = NOW,
            )

            val balances = store.people.listActive().associate { it.id to it.balanceCents }
            assertEquals(mapOf("pA" to 1_500L, "pB" to -1_500L), balances)
        }
    }

    @Test
    fun equalSplitRemainderToPayerSurvivesSaveAndDerivedViews() {
        // Split-rounding edge case end-to-end: the SplitCalculator remainder-to-payer rule
        // must survive the save and read back identically from the canonical views.
        freshStore().use { store ->
            store.accounts.create(
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
            store.people.create(PersonDraft("laura", "Laura", null, null, null), createdAt = NOW)
            store.people.create(PersonDraft("marc", "Marc", null, null, null), createdAt = NOW)

            // 1001 split equally across [user, laura, marc]; remainder goes to the payer (user).
            val shares = SplitCalculator.equal(totalCents = 1_001, participantCount = 3, payerIndex = 0)
            assertEquals(listOf(335L, 333L, 333L), shares.sharesCents)

            store.movements.create(
                MovementDraft(
                    id = "dinner",
                    type = MovementType.EXPENSE,
                    amountCents = 1_001,
                    date = "2026-02-01",
                    accountId = "checking",
                    destinationAccountId = null,
                    categoryId = null,
                    name = "Sopar",
                    payee = null,
                    notes = null,
                    isOneTime = false,
                    splitWrite = MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EQUAL,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, null, shares.sharesCents[0]),
                                SplitLineDraft(SplitParticipantKind.PERSON, "laura", shares.sharesCents[1]),
                                SplitLineDraft(SplitParticipantKind.PERSON, "marc", shares.sharesCents[2]),
                            ),
                        ),
                    ),
                ),
                createdAt = NOW,
            )

            // Each person owes their floored 333; the payer keeps the 2-cent remainder as actual spend.
            val balances = store.people.listActive().associate { it.id to it.balanceCents }
            assertEquals(mapOf("laura" to 333L, "marc" to 333L), balances)
            assertEquals(
                335L,
                store.analysis.periodTotals(fromDate = "2026-02-01", toDate = "2026-02-02").actualExpenseCents,
            )
        }
    }

    private fun seedUserFrontedSplit(store: TestStore) {
        store.accounts.create(
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
        store.people.create(
            PersonDraft(
                id = "laura",
                name = "Laura",
                avatar = null,
                color = null,
                notes = null,
            ),
            createdAt = NOW,
        )
        store.movements.create(
            MovementDraft(
                id = "dinner",
                type = MovementType.EXPENSE,
                amountCents = 1_000,
                date = "2026-01-01",
                accountId = "checking",
                destinationAccountId = null,
                categoryId = null,
                name = "Sopar",
                payee = null,
                notes = null,
                isOneTime = false,
                splitWrite = MovementSplitWrite.Replace(
                    MovementSplitDraft(
                        entryMethod = SplitEntryMethod.EXACT,
                        lines = listOf(
                            SplitLineDraft(
                                participantKind = SplitParticipantKind.USER,
                                personId = null,
                                owedAmountCents = 400,
                            ),
                            SplitLineDraft(
                                participantKind = SplitParticipantKind.PERSON,
                                personId = "laura",
                                owedAmountCents = 600,
                            ),
                        ),
                    ),
                ),
            ),
            createdAt = NOW,
        )
        assertEquals(true, store.movements.getActive("dinner")!!.isShared)
    }

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            analysis = AnalysisRepository(database.analysisQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            people = PersonRepository(database.peopleQueries),
            splits = SplitRepository(database.splitsQueries),
        )
    }

    private class TestStore(
        val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val analysis: AnalysisRepository,
        val movements: MovementRepository,
        val people: PersonRepository,
        val splits: SplitRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val LATER = "2026-01-02T00:00:00Z"
    }
}
