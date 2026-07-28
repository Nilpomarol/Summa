package com.gestorfinances.app.data.repository

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test: `MovementsViewModel.attemptSave` used to create a quick
 * recurring template and its first movement in two separate transactions (despite a comment
 * claiming the link was atomic). A failure between them left an orphaned active template with no
 * backing movement, generating due prompts for nothing.
 *
 * The fix wraps both writes in `MovementRepository.runInTransaction`. This test creates the
 * template first (as production code does), then forces the movement write to fail via the same
 * FK-violation trick as [MovementRepositoryAtomicityTest], and asserts the template — which would
 * otherwise have committed — is rolled back too.
 */
class QuickTemplateCreateAtomicityTest {

    @Test
    fun `creating a quick template rolls back when the linked movement write fails`() {
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

        val templateDraft = TemplateDraft(
            id = "tpl-quick-1",
            type = MovementType.EXPENSE,
            amountCents = 1000L,
            accountId = "acc-1",
            destAccountId = null,
            categoryId = null,
            name = "New subscription",
            payee = null,
            notes = null,
            splitConfig = null,
            frequency = com.gestorfinances.app.domain.rules.RecurrenceFrequency.MONTHLY,
            intervalCount = null,
            customUnit = null,
            dayOfMonth = 1L,
            weekday = null,
            nextDueDate = "2026-07-01",
            amountIsVariable = false,
            amountFlexCents = null,
            dateFlexDays = null,
            leadNotificationDays = null,
        )

        val draft = MovementDraft(
            id = "mv-quick-1",
            type = MovementType.EXPENSE,
            amountCents = 1000L,
            date = "2026-06-01",
            accountId = "acc-1",
            destinationAccountId = null,
            categoryId = null,
            name = "New subscription",
            payee = null,
            notes = null,
            isOneTime = false,
            splitWrite = MovementSplitWrite.Replace(
                MovementSplitDraft(
                    entryMethod = SplitEntryMethod.EXACT,
                    lines = listOf(
                        SplitLineDraft(SplitParticipantKind.USER, null, 500L),
                        // References a person that was never created -> FK violation, raised
                        // AFTER the template row has already been written inside the transaction.
                        SplitLineDraft(SplitParticipantKind.PERSON, "person-missing", 500L),
                    ),
                ),
            ),
            templateId = "tpl-quick-1",
        )

        val thrown = runCatching {
            movements.runInTransaction {
                templates.create(templateDraft, createdAt = NOW)
                movements.create(draft, createdAt = NOW)
            }
        }
        assertTrue("expected the split-line FK violation to surface, got $thrown", thrown.isFailure)

        assertNull("movement must not persist when the quick-template write is rolled back", movements.getActive("mv-quick-1"))
        assertNull("template must roll back together with the failed movement write", templates.getActive("tpl-quick-1"))
    }

    private companion object {
        const val NOW = "2026-06-27T00:00:00Z"
    }
}
