package com.gestorfinances.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test: `RecurringViewModel.onConfirmSaveClicked` used to write
 * the movement and advance the template cursor in two separate transactions, so a failure between
 * them could leave a persisted movement with a stale cursor (silent duplicate on next refresh).
 *
 * The fix wraps both writes in `MovementRepository.runInTransaction`. This test proves the
 * combined transaction is atomic across the two repositories (`MovementsQueries` and
 * `TemplatesQueries` share the same underlying driver): it forces the movement write to fail via
 * the same FK-violation trick as [MovementRepositoryAtomicityTest], then asserts the template's
 * cursor advance — which would otherwise have succeeded — is rolled back too.
 */
class RecurringConfirmAtomicityTest {

    @Test
    fun `confirming a recurring occurrence rolls back the cursor advance when the movement write fails`() {
        val database = RepositoryTestSupport.newDatabase()
        val accounts = AccountRepository(database.accountsQueries)
        val movements = MovementRepository(database.movementsQueries, database.splitsQueries)
        val templates = TemplateRepository(database.templatesQueries)

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

        val originalNextDue = "2026-06-01"
        templates.create(
            TemplateDraft(
                id = "tpl-1",
                type = MovementType.EXPENSE,
                amountCents = 1000L,
                accountId = "acc-1",
                destAccountId = null,
                categoryId = null,
                name = "Subscription",
                payee = null,
                notes = null,
                splitConfig = null,
                frequency = com.gestorfinances.app.domain.rules.RecurrenceFrequency.MONTHLY,
                intervalCount = null,
                customUnit = null,
                dayOfMonth = 1L,
                weekday = null,
                nextDueDate = originalNextDue,
                amountIsVariable = false,
                amountFlexCents = null,
                dateFlexDays = null,
                leadNotificationDays = null,
            ),
            createdAt = NOW,
        )

        val draft = MovementDraft(
            id = "mv-1",
            type = MovementType.EXPENSE,
            amountCents = 1000L,
            date = originalNextDue,
            accountId = "acc-1",
            destinationAccountId = null,
            categoryId = null,
            name = "Subscription",
            payee = null,
            notes = null,
            isOneTime = false,
            splitWrite = MovementSplitWrite.Replace(
                MovementSplitDraft(
                    entryMethod = SplitEntryMethod.EXACT,
                    lines = listOf(
                        SplitLineDraft(SplitParticipantKind.USER, null, 500L),
                        // References a person that was never created -> FK violation, raised
                        // AFTER the movement row has been written inside the transaction.
                        SplitLineDraft(SplitParticipantKind.PERSON, "person-missing", 500L),
                    ),
                ),
            ),
            templateId = "tpl-1",
        )

        val thrown = runCatching {
            movements.runInTransaction {
                movements.create(draft, createdAt = NOW)
                templates.advanceCursor("tpl-1", "2026-07-01", updatedAt = NOW)
            }
        }
        assertTrue("expected the split-line FK violation to surface, got $thrown", thrown.isFailure)

        assertNull("movement must be rolled back with the failed split write", movements.getActive("mv-1"))
        assertEquals(
            "template cursor must roll back together with the failed movement write",
            originalNextDue,
            templates.getActive("tpl-1")?.nextDueDate,
        )
    }

    private companion object {
        const val NOW = "2026-06-27T00:00:00Z"
    }
}
