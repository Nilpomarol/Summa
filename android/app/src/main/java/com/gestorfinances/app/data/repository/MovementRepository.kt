package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.MovementsQueries
import com.gestorfinances.app.data.db.SplitsQueries
import java.util.UUID

enum class MovementType(val dbValue: String) {
    EXPENSE("expense"),
    INCOME("income"),
    TRANSFER("transfer"),
    SETTLEMENT("settlement"),
    REFUND("refund"),
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
    val accountId: String,
    val accountName: String,
    val destinationAccountId: String?,
    val destinationAccountName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryNature: CategoryNature?,
    val tripId: String? = null,
    val tripName: String? = null,
    val tagId: String? = null,
    val tagName: String? = null,
    val name: String?,
    val payee: String?,
    val notes: String?,
    val isOneTime: Boolean,
    val isShared: Boolean,
    val paidByPersonName: String?,
    val settlementDirection: SettlementDirection?,
    val settlementPersonName: String?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

data class MovementDraft(
    val id: String,
    val type: MovementType,
    val amountCents: Long,
    val date: String,
    val accountId: String,
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
)

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
    val amountCents: Long,
    val accountId: String,
    val date: String,
    val notes: String?,
)

data class RefundDraft(
    val id: String,
    val refundsExpenseId: String,
    val amountCents: Long,
    val accountId: String,
    val categoryId: String?,
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
)

data class AccountFlowEntry(
    val accountId: String,
    val date: String,
    val movementId: String,
    val deltaCents: Long,
    val type: MovementType,
    val name: String?,
    val payee: String?,
    val notes: String?,
    val categoryId: String?,
    val categoryName: String?,
    val originAccountId: String,
    val originAccountName: String,
    val destinationAccountId: String?,
    val destinationAccountName: String?,
)

class MovementRepository(
    private val queries: MovementsQueries,
    private val splitQueries: SplitsQueries? = null,
) {
    fun listActive(): List<MovementSummary> =
        queries.activeMovementSummaries(::mapMovementSummary).executeAsList()

    fun listActiveForAccount(accountId: String): List<MovementSummary> =
        queries.activeMovementSummariesForAccount(accountId, ::mapMovementSummary).executeAsList()

    fun getActive(id: String): MovementSummary? =
        queries.movementById(id, ::mapMovementSummary).executeAsOneOrNull()

    fun accountFlowForAccount(accountId: String): List<AccountFlowEntry> =
        queries.accountFlowForAccount(accountId, ::mapAccountFlowEntry).executeAsList()

    fun create(
        draft: MovementDraft,
        createdAt: String,
    ) {
        requireDirectMovementType(draft.type)
        validateSplitWrite(draft)
        queries.transaction {
            queries.insertMovement(
                id = draft.id,
                type = draft.type.dbValue,
                amount_cents = draft.amountCents,
                date = draft.date,
                account_id = draft.accountId,
                dest_account_id = draft.destinationAccountId,
                name = draft.name,
                payee = draft.payee,
                notes = draft.notes,
                is_one_time = draft.isOneTime.toDbLong(),
                category_id = draft.categoryId,
                trip_id = draft.tripId,
                tag_id = draft.tagId,
                template_id = draft.templateId,
                created_at = createdAt,
                updated_at = createdAt,
            )
            applySplitWrite(draft.id, draft.splitWrite, timestamp = createdAt)
        }
    }

    fun update(
        draft: MovementDraft,
        updatedAt: String,
    ) {
        requireDirectMovementType(draft.type)
        validateSplitWrite(draft)
        queries.transaction {
            queries.updateMovement(
                id = draft.id,
                type = draft.type.dbValue,
                amount_cents = draft.amountCents,
                date = draft.date,
                account_id = draft.accountId,
                dest_account_id = draft.destinationAccountId,
                name = draft.name,
                payee = draft.payee,
                notes = draft.notes,
                is_one_time = draft.isOneTime.toDbLong(),
                category_id = draft.categoryId,
                trip_id = draft.tripId,
                tag_id = draft.tagId,
                updated_at = updatedAt,
            )
            applySplitWrite(draft.id, draft.splitWrite, timestamp = updatedAt)
        }
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
            notes = draft.notes,
            person_id = draft.personId,
            settlement_direction = draft.direction.dbValue,
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
            category_id = draft.categoryId,
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
            archiveMovementSplit(id, timestamp = archivedAt)
        }
    }

    private fun applySplitWrite(
        movementId: String,
        splitWrite: MovementSplitWrite,
        timestamp: String,
    ) {
        when (splitWrite) {
            MovementSplitWrite.KeepExisting -> Unit
            MovementSplitWrite.Remove -> archiveMovementSplit(movementId, timestamp)
            is MovementSplitWrite.Replace -> replaceMovementSplit(
                movementId = movementId,
                draft = splitWrite.draft,
                timestamp = timestamp,
            )
        }
    }

    private fun replaceMovementSplit(
        movementId: String,
        draft: MovementSplitDraft,
        timestamp: String,
    ) {
        val splits = requireNotNull(splitQueries) {
            "Split queries are required to save shared expenses."
        }
        val splitId = splits.splitIdForMovement(movementId).executeAsOneOrNull()
        val activeSplitId = splitId ?: UUID.randomUUID().toString()

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
}

private fun mapMovementSummary(
    id: String,
    type: String,
    amountCents: Long,
    date: String,
    accountId: String,
    accountName: String,
    destinationAccountId: String?,
    destinationAccountName: String?,
    categoryId: String?,
    categoryName: String?,
    categoryNature: String?,
    tripId: String?,
    tripName: String?,
    tagId: String?,
    tagName: String?,
    name: String?,
    payee: String?,
    notes: String?,
    isOneTime: Long,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
    paidByPersonName: String?,
    isShared: Long,
    settlementDirection: String?,
    settlementPersonName: String?,
): MovementSummary =
    MovementSummary(
        id = id,
        type = MovementType.fromDb(type),
        amountCents = amountCents,
        date = date,
        accountId = accountId,
        accountName = accountName,
        destinationAccountId = destinationAccountId,
        destinationAccountName = destinationAccountName,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryNature = categoryNature?.let(CategoryNature::fromDb),
        tripId = tripId,
        tripName = tripName,
        tagId = tagId,
        tagName = tagName,
        name = name,
        payee = payee,
        notes = notes,
        isOneTime = isOneTime != 0L,
        isShared = isShared != 0L,
        paidByPersonName = paidByPersonName,
        settlementDirection = settlementDirection?.let(SettlementDirection::fromDb),
        settlementPersonName = settlementPersonName,
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

private fun mapAccountFlowEntry(
    accountId: String?,
    date: String,
    movementId: String,
    deltaCents: Long,
    type: String,
    name: String?,
    payee: String?,
    notes: String?,
    categoryId: String?,
    categoryName: String?,
    originAccountId: String,
    originAccountName: String,
    destinationAccountId: String?,
    destinationAccountName: String?,
): AccountFlowEntry =
    AccountFlowEntry(
        accountId = requireNotNull(accountId),
        date = date,
        movementId = movementId,
        deltaCents = deltaCents,
        type = MovementType.fromDb(type),
        name = name,
        payee = payee,
        notes = notes,
        categoryId = categoryId,
        categoryName = categoryName,
        originAccountId = originAccountId,
        originAccountName = originAccountName,
        destinationAccountId = destinationAccountId,
        destinationAccountName = destinationAccountName,
    )

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
    require(draft.type == MovementType.EXPENSE) {
        "Only expense movements can have a movement-backed split."
    }
    require(split.lines.sumOf { it.owedAmountCents } == draft.amountCents) {
        "Split lines must reconcile with the movement amount."
    }
    require(split.lines.any { it.participantKind == SplitParticipantKind.USER }) {
        "Movement-backed splits must include the user line."
    }
    require(split.lines.any { it.participantKind == SplitParticipantKind.PERSON }) {
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

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L
