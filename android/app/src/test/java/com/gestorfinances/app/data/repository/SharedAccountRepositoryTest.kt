package com.gestorfinances.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAccountRepositoryTest {
    @Test
    fun `shared account keeps physical value ownership expense and debt separate`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        val people = PersonRepository(database.peopleQueries)
        val movements = MovementRepository(database.movementsQueries, database.splitsQueries)
        val now = "2026-09-06T10:00:00Z"
        people.create(PersonDraft("person", "Alba", null, null, null), now)
        accounts.create(account("personal", AccountOwnershipKind.PERSONAL, 10_000), now)
        accounts.create(
            account(
                "shared",
                AccountOwnershipKind.SHARED,
                10_000,
                listOf(
                    AccountMemberDraft(SplitParticipantKind.USER, null, 4_000, 4_000),
                    AccountMemberDraft(SplitParticipantKind.PERSON, "person", 6_000, 6_000),
                ),
            ),
            now,
        )
        accounts.createContribution(
            ContributionDraft("contribution", "shared", ContributionDirection.IN, SplitParticipantKind.USER, null, "personal", 2_000, "2026-09-06", null, null),
            now,
        )
        movements.create(
            MovementDraft(
                id = "expense",
                type = MovementType.EXPENSE,
                amountCents = 5_000,
                date = "2026-09-06",
                accountId = "shared",
                destinationAccountId = null,
                categoryId = null,
                name = "Compra",
                payee = null,
                notes = null,
                isOneTime = false,
                expenseFunding = ExpenseFunding.SHARED_ACCOUNT,
                splitWrite = MovementSplitWrite.Replace(
                    MovementSplitDraft(
                        SplitEntryMethod.EXACT,
                        listOf(
                            SplitLineDraft(SplitParticipantKind.USER, null, 2_000),
                            SplitLineDraft(SplitParticipantKind.PERSON, "person", 3_000),
                        ),
                    ),
                ),
            ),
            now,
        )

        val personal = accounts.getActive("personal")!!
        val shared = accounts.getActive("shared")!!
        assertEquals(8_000, personal.currentBalanceCents)
        assertEquals(7_000, shared.currentBalanceCents)
        assertEquals(2_800, shared.ownerValueCents)
        assertEquals(0, people.getActive("person")!!.balanceCents)
        assertTrue(movements.getActive("expense")!!.financingKind == ExpenseFunding.SHARED_ACCOUNT)
    }

    @Test
    fun `money leaves a shared account the same way it enters`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        val people = PersonRepository(database.peopleQueries)
        val now = "2026-09-06T10:00:00Z"
        people.create(PersonDraft("person", "Alba", null, null, null), now)
        accounts.create(account("personal", AccountOwnershipKind.PERSONAL, 10_000), now)
        accounts.create(sharedAccount(), now)
        accounts.createContribution(
            ContributionDraft("withdrawal", "shared", ContributionDirection.OUT, SplitParticipantKind.USER, null, "personal", 2_000, "2026-09-06", null, null),
            now,
        )
        accounts.createContribution(
            ContributionDraft("person-withdrawal", "shared", ContributionDirection.OUT, SplitParticipantKind.PERSON, "person", null, 1_000, "2026-09-07", null, null),
            now,
        )

        val personal = accounts.getActive("personal")!!
        val shared = accounts.getActive("shared")!!
        assertEquals(12_000, personal.currentBalanceCents)
        assertEquals(7_000, shared.currentBalanceCents)
        assertEquals(2_800, shared.ownerValueCents)
        // Taking money out is not a settlement: nobody owes anybody for it.
        assertEquals(0, people.getActive("person")!!.balanceCents)
    }

    @Test
    fun `an account ledger reconciles to its balance through each movement's own delta`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        val people = PersonRepository(database.peopleQueries)
        val movements = MovementRepository(database.movementsQueries, database.splitsQueries)
        val now = "2026-09-06T10:00:00Z"
        people.create(PersonDraft("person", "Alba", null, null, null), now)
        accounts.create(account("personal", AccountOwnershipKind.PERSONAL, 10_000), now)
        accounts.create(sharedAccount(), now)
        accounts.createContribution(
            ContributionDraft("contribution", "shared", ContributionDirection.IN, SplitParticipantKind.USER, null, "personal", 2_000, "2026-09-06", null, null),
            now,
        )
        movements.create(
            MovementDraft(
                id = "expense",
                type = MovementType.EXPENSE,
                amountCents = 5_000,
                date = "2026-09-06",
                accountId = "shared",
                destinationAccountId = null,
                categoryId = null,
                name = "Compra",
                payee = null,
                notes = null,
                isOneTime = false,
                expenseFunding = ExpenseFunding.SHARED_ACCOUNT,
                splitWrite = MovementSplitWrite.Replace(
                    MovementSplitDraft(
                        SplitEntryMethod.EXACT,
                        listOf(
                            SplitLineDraft(SplitParticipantKind.USER, null, 2_000),
                            SplitLineDraft(SplitParticipantKind.PERSON, "person", 3_000),
                        ),
                    ),
                ),
            ),
            now,
        )

        val shared = movements.listActiveForAccount("shared")
        // The whole expense left the shared account, whatever the user's own share of it was, and
        // the contribution reads as money in on one side and money out on the other.
        assertEquals(
            mapOf("contribution" to 2_000L, "expense" to -5_000L),
            shared.associate { it.movement.id to it.deltaCents },
        )
        assertEquals(
            mapOf("contribution" to -2_000L),
            movements.listActiveForAccount("personal").associate { it.movement.id to it.deltaCents },
        )
        assertEquals(accounts.getActive("shared")!!.currentBalanceCents, 10_000 + shared.sumOf { it.deltaCents })
    }

    @Test
    fun `a contribution can be corrected but keeps its account and direction`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        val people = PersonRepository(database.peopleQueries)
        val now = "2026-09-06T10:00:00Z"
        people.create(PersonDraft("person", "Alba", null, null, null), now)
        accounts.create(account("personal", AccountOwnershipKind.PERSONAL, 10_000), now)
        accounts.create(sharedAccount(), now)
        val recorded = ContributionDraft("contribution", "shared", ContributionDirection.IN, SplitParticipantKind.USER, null, "personal", 2_000, "2026-09-06", null, null)
        accounts.createContribution(recorded, now)

        // It was Alba's money after all: the owner's personal account gets its 2 000 back.
        val corrected = recorded.copy(
            contributorKind = SplitParticipantKind.PERSON,
            personId = "person",
            sourceAccountId = null,
            amountCents = 2_500,
            name = "Nòmina",
        )
        accounts.updateContribution(corrected, "2026-09-07T10:00:00Z")

        assertEquals(corrected, accounts.getContribution("contribution"))
        assertEquals(10_000, accounts.getActive("personal")!!.currentBalanceCents)
        assertEquals(12_500, accounts.getActive("shared")!!.currentBalanceCents)
        assertTrue(
            runCatching { accounts.updateContribution(corrected.copy(direction = ContributionDirection.OUT), now) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        // A person's money never names one of the owner's accounts.
        assertTrue(
            runCatching { accounts.updateContribution(corrected.copy(sourceAccountId = "personal"), now) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `shared percentages must reconcile`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        accounts.create(
            account(
                "shared",
                AccountOwnershipKind.SHARED,
                0,
                listOf(
                    AccountMemberDraft(SplitParticipantKind.USER, null, 5_000, 5_000),
                    AccountMemberDraft(SplitParticipantKind.PERSON, "missing", 4_000, 5_000),
                ),
            ),
            "2026-09-06T10:00:00Z",
        )
    }

    // Un-sharing an account that financed shared expenses or received contributions would strand
    // those rows: they keep naming a shared account, and the movement triggers then reject every
    // later edit of them. The account has to stay shared instead.
    @Test
    fun `an account that received a contribution cannot become personal again`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        val people = PersonRepository(database.peopleQueries)
        val now = "2026-09-06T10:00:00Z"
        people.create(PersonDraft("person", "Alba", null, null, null), now)
        accounts.create(sharedAccount(), now)
        accounts.createContribution(
            ContributionDraft("contribution", "shared", ContributionDirection.IN, SplitParticipantKind.USER, null, null, 2_000, "2026-09-06", null, null),
            now,
        )

        assertTrue(accounts.hasSharedHistory("shared"))
        val failure = runCatching {
            accounts.update(account("shared", AccountOwnershipKind.PERSONAL, 10_000), now)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(AccountOwnershipKind.SHARED, accounts.getActive("shared")!!.ownershipKind)
    }

    @Test
    fun `an account that financed a shared expense cannot become personal again`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        val people = PersonRepository(database.peopleQueries)
        val movements = MovementRepository(database.movementsQueries, database.splitsQueries)
        val now = "2026-09-06T10:00:00Z"
        people.create(PersonDraft("person", "Alba", null, null, null), now)
        accounts.create(sharedAccount(), now)
        movements.create(
            MovementDraft(
                id = "expense",
                type = MovementType.EXPENSE,
                amountCents = 5_000,
                date = "2026-09-06",
                accountId = "shared",
                destinationAccountId = null,
                categoryId = null,
                name = "Compra",
                payee = null,
                notes = null,
                isOneTime = false,
                expenseFunding = ExpenseFunding.SHARED_ACCOUNT,
                splitWrite = MovementSplitWrite.Replace(
                    MovementSplitDraft(
                        SplitEntryMethod.EXACT,
                        listOf(
                            SplitLineDraft(SplitParticipantKind.USER, null, 2_000),
                            SplitLineDraft(SplitParticipantKind.PERSON, "person", 3_000),
                        ),
                    ),
                ),
            ),
            now,
        )
        // Archiving does not shed the history: the expense can still be restored.
        movements.archive("expense", archivedAt = now)

        assertTrue(accounts.hasSharedHistory("shared"))
        assertTrue(
            runCatching {
                accounts.update(account("shared", AccountOwnershipKind.PERSONAL, 10_000), now)
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `a shared account without history can still become personal`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        val people = PersonRepository(database.peopleQueries)
        val now = "2026-09-06T10:00:00Z"
        people.create(PersonDraft("person", "Alba", null, null, null), now)
        accounts.create(sharedAccount(), now)

        accounts.update(account("shared", AccountOwnershipKind.PERSONAL, 10_000), now)

        assertEquals(AccountOwnershipKind.PERSONAL, accounts.getActive("shared")!!.ownershipKind)
    }

    private fun sharedAccount() = account(
        "shared",
        AccountOwnershipKind.SHARED,
        10_000,
        listOf(
            AccountMemberDraft(SplitParticipantKind.USER, null, 4_000, 4_000),
            AccountMemberDraft(SplitParticipantKind.PERSON, "person", 6_000, 6_000),
        ),
    )

    private fun account(
        id: String,
        ownership: AccountOwnershipKind,
        starting: Long,
        members: List<AccountMemberDraft> = emptyList(),
    ) = AccountDraft(
        id = id,
        name = id,
        startingBalanceCents = starting,
        type = AccountType.BANK,
        icon = null,
        color = null,
        isDefault = false,
        displayOrder = 0,
        lowBalanceThresholdCents = null,
        ownershipKind = ownership,
        members = members,
    )
}
