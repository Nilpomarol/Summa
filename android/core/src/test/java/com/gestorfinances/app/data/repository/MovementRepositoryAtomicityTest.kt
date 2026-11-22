package com.gestorfinances.app.data.repository

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference test for the multi-write atomicity pattern (multi-write atomicity).
 *
 * `MovementRepository.create` wraps the movement insert and its split-line
 * inserts in a single SQLDelight transaction. We prove atomicity by making the
 * split-line insert fail INSIDE the transaction — a PERSON line whose
 * `person_id` does not exist, so the foreign-key constraint fires after the
 * movement row is already written. Atomicity holds iff the movement is rolled
 * back together with the failed split write.
 *
 * The C3/C4/C5 fixes (each collapsing two writes into one transaction) should
 * add an equivalent test: pass inputs that make the second inner write throw,
 * then assert the first write's effects are gone.
 */
class MovementRepositoryAtomicityTest {

    @Test
    fun `create rolls back the movement when its split write fails inside the transaction`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries)
        val movements = MovementRepository(database.movementsQueries, database.splitsQueries)

        accounts.create(
            AccountDraft(
                id = "acc-1",
                name = "Test",
                startingBalanceCents = 0L,
                type = AccountType.BANK,
                icon = null,
                color = null,
                isDefault = false,
                displayOrder = 0L,
                lowBalanceThresholdCents = null,
            ),
            createdAt = NOW,
        )

        val draft = MovementDraft(
            id = "mv-1",
            type = MovementType.EXPENSE,
            amountCents = 1000L,
            date = "2026-06-27",
            accountId = "acc-1",
            destinationAccountId = null,
            categoryId = null,
            name = "Shared dinner",
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
                            owedAmountCents = 500L,
                        ),
                        // References a person that was never created → FK violation on insert,
                        // raised AFTER the movement row has been written inside the transaction.
                        SplitLineDraft(
                            participantKind = SplitParticipantKind.PERSON,
                            personId = "person-missing",
                            owedAmountCents = 500L,
                        ),
                    ),
                ),
            ),
        )

        val thrown = runCatching { movements.create(draft, createdAt = NOW) }
        assertTrue(
            "expected the split-line FK violation to surface, got $thrown",
            thrown.isFailure,
        )

        // Atomicity: the movement must NOT persist after the split write failed.
        assertNull("movement must be rolled back with the failed split write", movements.getActive("mv-1"))
    }

    private companion object {
        const val NOW = "2026-06-27T00:00:00Z"
    }
}
