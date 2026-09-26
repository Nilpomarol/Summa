package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.MovementsQueries
import com.gestorfinances.app.data.db.SplitsQueries
import com.gestorfinances.app.domain.rules.SettlementScope
import java.util.UUID

enum class MovementType(val dbValue: String) {
    EXPENSE("expense"),
    INCOME("income"),
    TRANSFER("transfer"),
    SETTLEMENT("settlement"),
    REFUND("refund"),
    CONTRIBUTION("contribution"),
    ;

    companion object {
        fun fromDb(value: String): MovementType =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown movement type: $value")
    }
}

data class MovementSummary(
    val id: String,
    val type: MovementType,
    val amountCents: Long,
    val date: String,
    val accountId: String?,
    val accountName: String?,
    val accountColor: String?  = null,
    val destinationAccountId: String?,
    val destinationAccountName: String?,
    val destinationAccountColor: String? = null,
    val categoryId: String?,
    val categoryName: String?,
    val categoryNature: CategoryNature?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val tripId: String? = null,
    val tripName: String? = null,
    val tripColor: String? = null,
    val tagId: String? = null,
    val tagName: String? = null,
    val templateId: String? = null,
    val name: String?,
    val payee: String?,
    val notes: String?,
    val isOneTime: Boolean,
    val isShared: Boolean,
    val userShareCents: Long,
    val isRecurring: Boolean,
    val paidByPersonName: String?,
    val payerId: String?,
    val settlementDirection: SettlementDirection?,
    val settlementPersonName: String?,
    /** Set only when [type] is REFUND: the expense this refund refers to. */
    val refundsExpenseId: String? = null,
    val refundsExpenseName: String? = null,
    /** True when the linked [refundsExpenseId] expense has been archived (orphaned refund). */
    val refundsExpenseArchived: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
    val financingKind: ExpenseFunding? = null,
    /** Set only when [type] is CONTRIBUTION: whether money entered or left the shared account. */
    val contributionDirection: ContributionDirection? = null,
) {
    /**
     * An expense a person paid ([payerId]): no account moved, and [userShareCents] is what the owner owes them.
     * A contribution a person made also carries PERSON financing, but it is not one of these.
     */
    val paidByPerson: Boolean get() = type == MovementType.EXPENSE && financingKind == ExpenseFunding.PERSON
}

data class MovementDraft(
    val id: String,
    val type: MovementType,
    val amountCents: Long,
    val date: String,
    /** The owner's account; null only when [payerPersonId] paid the expense. */
    val accountId: String?,
    val destinationAccountId: String?,
    val categoryId: String?,
    val tripId: String? = null,
    val tagId: String? = null,
    val name: String?,
    val payee: String?,
    val notes: String?,
    val isOneTime: Boolean,
    val splitWrite: MovementSplitWrite = MovementSplitWrite.KeepExisting,
    val templateId: String? = null,
    val expenseFunding: ExpenseFunding = ExpenseFunding.OWNER,
    val sharedSplitId: String? = null,
    /** The person who paid an expense; its split's user line is then what the owner owes them. */
    val payerPersonId: String? = null,
)

enum class ExpenseFunding(val dbValue: String) {
    OWNER("owner"),
    PERSON("person"),
    SHARED_ACCOUNT("shared_account");

    companion object {
        fun fromDb(value: String) = entries.first { it.dbValue == value }
    }
}

sealed interface MovementSplitWrite {
    data object KeepExisting : MovementSplitWrite
    data object Remove : MovementSplitWrite
    data class Replace(val draft: MovementSplitDraft) : MovementSplitWrite
}

enum class SettlementDirection(val dbValue: String) {
    PERSON_TO_USER("person_to_user"),
    USER_TO_PERSON("user_to_person"),
    ;

    companion object {
        fun fromDb(value: String): SettlementDirection =
            entries.firstOrNull { it.dbValue == value }
                ?: error("Unknown settlement direction: $value")
    }
}

data class SettlementDraft(
    val id: String,
    val personId: String,
    val direction: SettlementDirection,
    /** The debt this settlement may consume, replayed by [com.gestorfinances.app.domain.rules.DebtConsumption]. */
    val scope: SettlementScope,
    val amountCents: Long,
    val accountId: String,
    val date: String,
    val name: String? = null,
    val notes: String?,
    /** Set when a recurring settlement template materialized this occurrence. */
    val templateId: String? = null,
)

data class RefundDraft(
    val id: String,
    val refundsExpenseId: String,
    val amountCents: Long,
    val accountId: String,
    val date: String,
    val name: String?,
    val payee: String?,
    val notes: String?,
    val actualRefundCents: Long?,
)

data class RefundSummary(
    val id: String,
    val amountCents: Long,
    val date: String,
    val actualRefundCents: Long?,
    val categoryId: String?,
    val categoryName: String?,
    val notes: String?,
)

enum class SplitEntryMethod(val dbValue: String) {
    EQUAL("equal"),
    EXACT("exact"),
    PERCENTAGE("percentage"),
}

enum class SplitParticipantKind(val dbValue: String) {
    USER("user"),
    PERSON("person"),
}

data class MovementSplitDraft(
    val entryMethod: SplitEntryMethod,
    val lines: List<SplitLineDraft>,
)

data class SplitLineDraft(
    val participantKind: SplitParticipantKind,
    val personId: String?,
    val owedAmountCents: Long,
    val owedPercent: Double? = null,
)

/**
 * A movement in one account's ledger, with what it did to that account's balance: the canonical
 * `v_account_flow` delta, so the ledger reconciles to the balance and never recomputes it.
 */
data class AccountLedgerEntry(
    val movement: MovementSummary,
    val deltaCents: Long,
)

class MovementRepository(
    private val queries: MovementsQueries,
    private val splitQueries: SplitsQueries? = null,
) {
    /**
     * Runs [block] inside this repository's transaction boundary so a caller can combine a
     * movement write with a write on another repository (e.g. advancing a recurring template's
     * cursor) atomically. SQLDelight transactions nest via savepoints, so any `queries.transaction
     * {}` call made by [block] (including [create]/[update] themselves) commits only when the
     * outermost transaction does.
     */
    fun runInTransaction(block: () -> Unit) {
        queries.transaction { block() }
    }

    fun listActive(): List<MovementSummary> =
        queries.activeMovementSummaries { id, type, amount_cents, date, account_id, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time, created_at, updated_at, archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived, paid_by_person_name, is_shared, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents, is_recurring, contribution_direction ->
            mapMovementSummary(id ?: "", type ?: "", amount_cents ?: 0L, date ?: "", account_id, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time ?: 0L, created_at ?: "", updated_at ?: "", archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived ?: 0L, paid_by_person_name, is_shared ?: 0L, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents ?: 0L, is_recurring ?: 0L, contribution_direction)
        }.executeAsList()

    fun listActiveForAccount(accountId: String): List<AccountLedgerEntry> =
        queries.activeMovementSummariesForAccount(accountId) { id, type, amount_cents, date, account_id_, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time, created_at, updated_at, archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived, paid_by_person_name, is_shared, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents, is_recurring, contribution_direction, account_delta_cents ->
            AccountLedgerEntry(
                movement = mapMovementSummary(id ?: "", type ?: "", amount_cents ?: 0L, date ?: "", account_id_, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time ?: 0L, created_at ?: "", updated_at ?: "", archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived ?: 0L, paid_by_person_name, is_shared ?: 0L, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents ?: 0L, is_recurring ?: 0L, contribution_direction),
                deltaCents = requireNotNull(account_delta_cents),
            )
        }.executeAsList()

    fun listActiveForCategory(categoryId: String): List<MovementSummary> =
        queries.activeMovementSummariesForCategory(categoryId) { id, type, amount_cents, date, account_id, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id_, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time, created_at, updated_at, archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived, paid_by_person_name, is_shared, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents, is_recurring, contribution_direction ->
            mapMovementSummary(id ?: "", type ?: "", amount_cents ?: 0L, date ?: "", account_id, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id_, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time ?: 0L, created_at ?: "", updated_at ?: "", archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived ?: 0L, paid_by_person_name, is_shared ?: 0L, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents ?: 0L, is_recurring ?: 0L, contribution_direction)
        }.executeAsList()

    fun getActive(id: String): MovementSummary? =
        queries.movementById(id) { id_, type, amount_cents, date, account_id, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time, created_at, updated_at, archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived, paid_by_person_name, is_shared, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents, is_recurring, contribution_direction ->
            mapMovementSummary(id_ ?: "", type ?: "", amount_cents ?: 0L, date ?: "", account_id, account_name, account_color, dest_account_id, destination_account_name, destination_account_color, category_id, category_name, category_nature, category_icon, category_color, trip_id, trip_name, trip_color, tag_id, tag_name, template_id, name, payee, notes, is_one_time ?: 0L, created_at ?: "", updated_at ?: "", archived_at, refunds_expense_id, refunds_expense_name, refunds_expense_archived ?: 0L, paid_by_person_name, is_shared ?: 0L, financing_kind, payer_id, settlement_direction, settlement_person_name, user_share_cents ?: 0L, is_recurring ?: 0L, contribution_direction)
        }.executeAsOneOrNull()

    /** Count of active movements generated by each recurring template, keyed by template id. */
    fun countsByTemplate(): Map<String, Long> =
        queries.movementCountsByTemplate().executeAsList()
            .mapNotNull { row -> row.template_id?.let { it to row.movement_count } }
            .toMap()

    fun create(
        draft: MovementDraft,
        createdAt: String,
    ) {
        val persistedDraft = prepareSharedFundingDraft(draft, existingMovement = false)
        requireDirectMovementType(persistedDraft.type)
        validatePayer(persistedDraft)
        validateSplitWrite(persistedDraft)
        validateIncomeAllocation(persistedDraft)
        validateSharedFundingSplit(persistedDraft, existingMovement = false, hasActiveSplit = false)
        queries.transaction {
            queries.insertMovement(
                id = persistedDraft.id,
                type = persistedDraft.type.dbValue,
                amount_cents = persistedDraft.amountCents,
                date = persistedDraft.date,
                account_id = persistedDraft.accountId,
                dest_account_id = persistedDraft.destinationAccountId,
                name = persistedDraft.name,
                payee = persistedDraft.payee,
                notes = persistedDraft.notes,
                is_one_time = persistedDraft.isOneTime.toDbLong(),
                category_id = persistedDraft.categoryId,
                trip_id = persistedDraft.tripId,
                tag_id = persistedDraft.tagId,
                template_id = persistedDraft.templateId,
                expense_funding = persistedDraft.expenseFundingDbValue(),
                shared_split_id = persistedDraft.sharedSplitId,
                payer_person_id = persistedDraft.payerPersonId,
                created_at = createdAt,
                updated_at = createdAt,
            )
            applySplitWrite(persistedDraft.id, persistedDraft.splitWrite, persistedDraft.sharedSplitId, timestamp = createdAt)
        }
    }

    fun update(
        draft: MovementDraft,
        updatedAt: String,
    ) {
        val persistedDraft = prepareSharedFundingDraft(draft, existingMovement = true)
        requireDirectMovementType(persistedDraft.type)
        validatePayer(persistedDraft)
        validateSplitWrite(persistedDraft)
        validateIncomeAllocation(persistedDraft)
        validateSharedFundingSplit(
            draft = persistedDraft,
            existingMovement = true,
            hasActiveSplit = persistedDraft.sharedSplitId != null,
        )
        queries.transaction {
            queries.updateMovement(
                id = persistedDraft.id,
                type = persistedDraft.type.dbValue,
                amount_cents = persistedDraft.amountCents,
                date = persistedDraft.date,
                account_id = persistedDraft.accountId,
                dest_account_id = persistedDraft.destinationAccountId,
                name = persistedDraft.name,
                payee = persistedDraft.payee,
                notes = persistedDraft.notes,
                is_one_time = persistedDraft.isOneTime.toDbLong(),
                category_id = persistedDraft.categoryId,
                trip_id = persistedDraft.tripId,
                tag_id = persistedDraft.tagId,
                template_id = persistedDraft.templateId,
                expense_funding = persistedDraft.expenseFundingDbValue(),
                shared_split_id = persistedDraft.sharedSplitId,
                payer_person_id = persistedDraft.payerPersonId,
                updated_at = updatedAt,
            )
            applySplitWrite(persistedDraft.id, persistedDraft.splitWrite, persistedDraft.sharedSplitId, timestamp = updatedAt)
        }
    }

    /** Links previously-unlinked movements to a template (e.g. history a detection scan picked
     * up as evidence for a newly confirmed template) so they stop being re-proposed by future
     * scans. A no-op for an empty [movementIds]. */
    fun linkToTemplate(movementIds: List<String>, templateId: String, updatedAt: String) {
        if (movementIds.isEmpty()) return
        queries.linkToTemplate(template_id = templateId, updated_at = updatedAt, ids = movementIds)
    }

    fun activeMovementIdsForTemplate(templateId: String): List<String> =
        queries.activeMovementIdsForTemplate(template_id = templateId).executeAsList()

    fun restoreTemplateLinksAfterDelete(
        movementIds: List<String>,
        templateId: String,
        deletedAt: String,
        restoredAt: String,
    ) {
        if (movementIds.isEmpty()) return
        queries.restoreTemplateLinksAfterDelete(
            ids = movementIds,
            template_id = templateId,
            deleted_at = deletedAt,
            updated_at = restoredAt,
        )
    }

    /** Severs every movement's link to a template (e.g. when the template itself is deleted) so
     * they stop showing as recurring and become eligible for detection again. */
    fun unlinkAllForTemplate(templateId: String, updatedAt: String) {
        queries.unlinkAllForTemplate(template_id = templateId, updated_at = updatedAt)
    }

    fun createSettlement(
        draft: SettlementDraft,
        createdAt: String,
    ) {
        require(draft.amountCents > 0L) { "Settlement amount must be positive." }
        require(draft.personId.isNotBlank()) { "Settlement person is required." }
        require(draft.accountId.isNotBlank()) { "Settlement account is required." }
        queries.insertSettlement(
            id = draft.id,
            amount_cents = draft.amountCents,
            date = draft.date,
            account_id = draft.accountId,
            name = draft.name,
            notes = draft.notes,
            person_id = draft.personId,
            settlement_direction = draft.direction.dbValue,
            settlement_scope = draft.scope.dbValue,
            template_id = draft.templateId,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun refundsForExpense(expenseId: String): List<RefundSummary> =
        queries.refundsForExpense(expenseId, ::mapRefundSummary).executeAsList()

    fun createRefund(
        draft: RefundDraft,
        createdAt: String,
    ) {
        require(draft.amountCents > 0L) { "Refund amount must be positive." }
        require(draft.refundsExpenseId.isNotBlank()) { "A refund must reference an expense." }
        require(draft.accountId.isNotBlank()) { "Refund account is required." }
        require(draft.actualRefundCents == null || draft.actualRefundCents >= 0L) {
            "Refund actual adjustment must be non-negative."
        }
        require(draft.actualRefundCents == null || draft.actualRefundCents <= draft.amountCents) {
            "Refund actual adjustment cannot exceed the refunded amount."
        }
        queries.insertRefund(
            id = draft.id,
            amount_cents = draft.amountCents,
            date = draft.date,
            account_id = draft.accountId,
            name = draft.name,
            payee = draft.payee,
            notes = draft.notes,
            refunds_expense_id = draft.refundsExpenseId,
            actual_refund_cents = draft.actualRefundCents,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.transaction {
            queries.archiveMovement(
                id = id,
                archived_at = archivedAt,
                updated_at = archivedAt,
            )
            queries.archiveRefundsForExpense(
                expense_id = id,
                archived_at = archivedAt,
                updated_at = archivedAt,
            )
            archiveMovementSplit(id, timestamp = archivedAt)
        }
    }

    fun restore(
        id: String,
        deletedAt: String,
        restoredAt: String,
    ) {
        queries.transaction {
            queries.restoreMovement(id = id, archived_at = deletedAt, updated_at = restoredAt)
            queries.restoreRefundsForExpense(
                expense_id = id,
                archived_at = deletedAt,
                updated_at = restoredAt,
            )
            restoreMovementSplit(id, deletedAt = deletedAt, restoredAt = restoredAt)
        }
    }

    /**
     * Settles the split identity a shared-account expense is stored with before its row is
     * written: the schema requires such a movement to name the split it is consumed through, while
     * the split itself is written after the movement inside the same transaction. A movement keeps
     * one stable split id, so an existing split always wins; a new one is minted only when a split
     * is actually being written. Nothing else carries a split identity.
     */
    private fun prepareSharedFundingDraft(
        draft: MovementDraft,
        existingMovement: Boolean,
    ): MovementDraft {
        if (draft.type != MovementType.EXPENSE ||
            draft.expenseFunding != ExpenseFunding.SHARED_ACCOUNT
        ) {
            return draft.copy(sharedSplitId = null)
        }
        val splits = splitQueries.takeIf { existingMovement }
        val activeSplitId = splits?.activeSplitIdForMovement(draft.id)?.executeAsOneOrNull()
        return draft.copy(
            sharedSplitId = when {
                activeSplitId != null -> activeSplitId
                // A split archived by an earlier funding change keeps its identifier when the
                // expense goes back to being financed by the shared account.
                draft.splitWrite is MovementSplitWrite.Replace ->
                    splits?.splitIdForMovement(draft.id)?.executeAsOneOrNull()
                        ?: draft.sharedSplitId
                        ?: UUID.randomUUID().toString()
                // No split, and none being written: the caller's validation rejects this.
                else -> null
            },
        )
    }

    /** An income is allocated between members only in a shared account; in a personal one it is the owner's. */
    private fun validateIncomeAllocation(draft: MovementDraft) {
        if (draft.type != MovementType.INCOME || draft.splitWrite !is MovementSplitWrite.Replace) return
        val accountId = requireNotNull(draft.accountId) { "An income needs the account it arrived in." }
        require(queries.accountOwnershipKind(accountId).executeAsOneOrNull() == AccountOwnershipKind.SHARED.dbValue) {
            "Only an income into a shared account can be allocated between members."
        }
    }

    private fun applySplitWrite(
        movementId: String,
        splitWrite: MovementSplitWrite,
        sharedSplitId: String?,
        timestamp: String,
    ) {
        when (splitWrite) {
            MovementSplitWrite.KeepExisting -> Unit
            MovementSplitWrite.Remove -> archiveMovementSplit(movementId, timestamp)
            is MovementSplitWrite.Replace -> replaceMovementSplit(
                movementId = movementId,
                draft = splitWrite.draft,
                forcedSplitId = sharedSplitId,
                timestamp = timestamp,
            )
        }
    }

    private fun replaceMovementSplit(
        movementId: String,
        draft: MovementSplitDraft,
        forcedSplitId: String?,
        timestamp: String,
    ) {
        val splits = requireNotNull(splitQueries) {
            "Split queries are required to save shared expenses."
        }
        val splitId = splits.splitIdForMovement(movementId).executeAsOneOrNull()
        val activeSplitId = forcedSplitId ?: splitId ?: UUID.randomUUID().toString()
        require(splitId == null || splitId == activeSplitId) {
            "A movement keeps one stable split identifier."
        }

        if (splitId == null) {
            splits.insertMovementSplit(
                id = activeSplitId,
                movement_id = movementId,
                entry_method = draft.entryMethod.dbValue,
                created_at = timestamp,
                updated_at = timestamp,
            )
        } else {
            splits.activateMovementSplit(
                id = activeSplitId,
                entry_method = draft.entryMethod.dbValue,
                updated_at = timestamp,
            )
            splits.archiveSplitLines(
                split_id = activeSplitId,
                archived_at = timestamp,
                updated_at = timestamp,
            )
        }

        draft.lines.forEach { line ->
            splits.insertSplitLine(
                id = UUID.randomUUID().toString(),
                split_id = activeSplitId,
                participant_kind = line.participantKind.dbValue,
                person_id = line.personId,
                owed_amount_cents = line.owedAmountCents,
                owed_percent = line.owedPercent,
                created_at = timestamp,
                updated_at = timestamp,
            )
        }
    }

    private fun archiveMovementSplit(
        movementId: String,
        timestamp: String,
    ) {
        val splits = splitQueries ?: return
        val splitId = splits.splitIdForMovement(movementId).executeAsOneOrNull() ?: return
        splits.archiveSplitLines(
            split_id = splitId,
            archived_at = timestamp,
            updated_at = timestamp,
        )
        splits.archiveMovementSplit(
            id = splitId,
            archived_at = timestamp,
            updated_at = timestamp,
        )
    }

    private fun restoreMovementSplit(
        movementId: String,
        deletedAt: String,
        restoredAt: String,
    ) {
        val splits = splitQueries ?: return
        val splitId = splits.splitIdForMovement(movementId).executeAsOneOrNull() ?: return
        splits.restoreMovementSplit(id = splitId, archived_at = deletedAt, updated_at = restoredAt)
        splits.restoreSplitLines(split_id = splitId, archived_at = deletedAt, updated_at = restoredAt)
    }
}

private fun mapMovementSummary(
    id: String,
    type: String,
    amountCents: Long,
    date: String,
    accountId: String?,
    accountName: String?,
    accountColor: String?,
    destinationAccountId: String?,
    destinationAccountName: String?,
    destinationAccountColor: String?,
    categoryId: String?,
    categoryName: String?,
    categoryNature: String?,
    categoryIcon: String?,
    categoryColor: String?,
    tripId: String?,
    tripName: String?,
    tripColor: String?,
    tagId: String?,
    tagName: String?,
    templateId: String?,
    name: String?,
    payee: String?,
    notes: String?,
    isOneTime: Long,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
    refundsExpenseId: String?,
    refundsExpenseName: String?,
    refundsExpenseArchived: Long,
    paidByPersonName: String?,
    isShared: Long,
    financingKind: String?,
    payerId: String?,
    settlementDirection: String?,
    settlementPersonName: String?,
    userShareCents: Long,
    isRecurring: Long,
    contributionDirection: String?,
): MovementSummary =
    MovementSummary(
        id = id,
        type = MovementType.fromDb(type),
        amountCents = amountCents,
        date = date,
        accountId = accountId,
        accountName = accountName,
        accountColor = accountColor,
        destinationAccountId = destinationAccountId,
        destinationAccountName = destinationAccountName,
        destinationAccountColor = destinationAccountColor,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryNature = categoryNature?.let(CategoryNature::fromDb),
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        tripId = tripId,
        tripName = tripName,
        tripColor = tripColor,
        tagId = tagId,
        tagName = tagName,
        templateId = templateId,
        name = name,
        payee = payee,
        notes = notes,
        isOneTime = isOneTime != 0L,
        isShared = isShared != 0L,
        financingKind = financingKind?.let(ExpenseFunding::fromDb),
        contributionDirection = contributionDirection?.let(ContributionDirection::fromDb),
        userShareCents = userShareCents,
        isRecurring = isRecurring != 0L,
        paidByPersonName = paidByPersonName,
        payerId = payerId,
        settlementDirection = settlementDirection?.let(SettlementDirection::fromDb),
        settlementPersonName = settlementPersonName,
        refundsExpenseId = refundsExpenseId,
        refundsExpenseName = refundsExpenseName,
        refundsExpenseArchived = refundsExpenseArchived != 0L,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )

private fun mapRefundSummary(
    id: String,
    amountCents: Long,
    date: String,
    actualRefundCents: Long?,
    categoryId: String?,
    categoryName: String?,
    notes: String?,
): RefundSummary =
    RefundSummary(
        id = id,
        amountCents = amountCents,
        date = date,
        actualRefundCents = actualRefundCents,
        categoryId = categoryId,
        categoryName = categoryName,
        notes = notes,
    )

/** A person-paid expense moves no account and records no financing of the owner's. */
private fun MovementDraft.expenseFundingDbValue(): String? =
    expenseFunding.dbValue.takeIf { type == MovementType.EXPENSE && payerPersonId == null }

/**
 * The owner pays from an account; a person who paid leaves the owner's accounts untouched and is
 * owed the split's user line, so their expense always carries that split and never recurs.
 */
private fun validatePayer(draft: MovementDraft) {
    if (draft.payerPersonId == null) {
        require(!draft.accountId.isNullOrBlank()) { "The owner pays from an account." }
        return
    }
    require(draft.type == MovementType.EXPENSE) { "Only an expense can be paid by a person." }
    require(draft.accountId == null) { "An expense a person paid moves none of the owner's accounts." }
    require(draft.expenseFunding == ExpenseFunding.OWNER) { "An expense a person paid is not financed by an account." }
    require(draft.templateId == null) { "An expense a person paid does not recur." }
    require(draft.splitWrite is MovementSplitWrite.Replace) { "An expense a person paid is saved with the split saying what is owed." }
}

private fun requireDirectMovementType(type: MovementType) {
    require(type == MovementType.EXPENSE || type == MovementType.INCOME || type == MovementType.TRANSFER) {
        "create/update support expense, income, and transfer; settlements use createSettlement."
    }
}

private fun validateSplitWrite(draft: MovementDraft) {
    require(draft.tagId == null || draft.tripId != null) {
        "Tagged movements must be attached to a trip."
    }
    val split = (draft.splitWrite as? MovementSplitWrite.Replace)?.draft ?: return
    require(draft.type == MovementType.EXPENSE || draft.type == MovementType.INCOME) {
        "Only expenses and incomes can have a movement-backed split."
    }
    require(split.lines.sumOf { it.owedAmountCents } == draft.amountCents) {
        "Split lines must reconcile with the movement amount."
    }
    require(split.lines.any { it.participantKind == SplitParticipantKind.USER }) {
        "Movement-backed splits must include the user line."
    }
    // The owner's line alone says what the owner owes a person who paid.
    require(draft.payerPersonId != null || split.lines.any { it.participantKind == SplitParticipantKind.PERSON }) {
        "Movement-backed splits must include at least one person line."
    }
    split.lines.forEach { line ->
        require(line.owedAmountCents >= 0L) {
            "Split line amounts must be non-negative."
        }
        when (line.participantKind) {
            SplitParticipantKind.USER -> require(line.personId == null) {
                "User split lines cannot reference a person."
            }
            SplitParticipantKind.PERSON -> require(line.personId != null) {
                "Person split lines must reference a person."
            }
        }
    }
}

private fun validateSharedFundingSplit(
    draft: MovementDraft,
    existingMovement: Boolean,
    hasActiveSplit: Boolean,
) {
    if (draft.expenseFunding != ExpenseFunding.SHARED_ACCOUNT) return
    require(draft.type == MovementType.EXPENSE) {
        "Shared-account financing is only valid for expenses."
    }
    when (draft.splitWrite) {
        is MovementSplitWrite.Replace -> Unit
        MovementSplitWrite.Remove -> error("A shared-account expense must retain its split.")
        MovementSplitWrite.KeepExisting -> {
            require(existingMovement && hasActiveSplit) {
                "A shared-account expense must have a split."
            }
        }
    }
}

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L
