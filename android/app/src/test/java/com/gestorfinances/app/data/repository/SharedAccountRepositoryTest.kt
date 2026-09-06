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
            ContributionDraft("contribution", "shared", SplitParticipantKind.USER, null, "personal", 2_000, "2026-09-06", null, null),
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
