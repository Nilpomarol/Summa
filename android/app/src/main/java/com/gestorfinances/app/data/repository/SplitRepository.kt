package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.SplitsQueries
import java.util.UUID

data class ExternalSplitDraft(
    val id: String,
    val payerPersonId: String,
    val totalAmountCents: Long,
    val userShareCents: Long,
    val date: String,
    val description: String?,
    val categoryId: String?,
    val tripId: String? = null,
    val tagId: String? = null,
)

class SplitRepository(
    private val queries: SplitsQueries,
) {
    fun createExternalPaidByPerson(
        draft: ExternalSplitDraft,
        createdAt: String,
    ) {
        require(draft.totalAmountCents > 0L) { "External split total must be positive." }
        require(draft.userShareCents >= 0L) { "External split user share must be non-negative." }
        require(draft.userShareCents == draft.totalAmountCents) {
            "External split total must equal the user share in v1."
        }
        require(draft.payerPersonId.isNotBlank()) { "External split payer person is required." }
        require(draft.tagId == null || draft.tripId != null) {
            "Tagged external splits must be attached to a trip."
        }

        queries.transaction {
            queries.insertExternalSplit(
                id = draft.id,
                payer_person_id = draft.payerPersonId,
                entry_method = SplitEntryMethod.EXACT.dbValue,
                total_amount_cents = draft.totalAmountCents,
                date = draft.date,
                description = draft.description,
                category_id = draft.categoryId,
                trip_id = draft.tripId,
                tag_id = draft.tagId,
                created_at = createdAt,
                updated_at = createdAt,
            )
            // One user line only. v_person_balance and v_actual_expense both read
            // participant_kind='user' for external splits; a person line was dead (O5).
            queries.insertSplitLine(
                id = UUID.randomUUID().toString(),
                split_id = draft.id,
                participant_kind = SplitParticipantKind.USER.dbValue,
                person_id = null,
                owed_amount_cents = draft.userShareCents,
                owed_percent = null,
                created_at = createdAt,
                updated_at = createdAt,
            )
        }
    }

    fun replaceExternalSplit(
        id: String,
        draft: ExternalSplitDraft,
        now: String,
    ) {
        require(draft.totalAmountCents > 0L) { "External split total must be positive." }
        require(draft.userShareCents >= 0L) { "External split user share must be non-negative." }
        require(draft.userShareCents == draft.totalAmountCents) {
            "External split total must equal the user share in v1."
        }
        require(draft.payerPersonId.isNotBlank()) { "External split payer person is required." }
        require(draft.tagId == null || draft.tripId != null) {
            "Tagged external splits must be attached to a trip."
        }

        queries.transaction {
            queries.archiveSplitLines(split_id = id, archived_at = now, updated_at = now)
            queries.archiveMovementSplit(id = id, archived_at = now, updated_at = now)
            queries.insertExternalSplit(
                id = draft.id,
                payer_person_id = draft.payerPersonId,
                entry_method = SplitEntryMethod.EXACT.dbValue,
                total_amount_cents = draft.totalAmountCents,
                date = draft.date,
                description = draft.description,
                category_id = draft.categoryId,
                trip_id = draft.tripId,
                tag_id = draft.tagId,
                created_at = now,
                updated_at = now,
            )
            queries.insertSplitLine(
                id = UUID.randomUUID().toString(),
                split_id = draft.id,
                participant_kind = SplitParticipantKind.USER.dbValue,
                person_id = null,
                owed_amount_cents = draft.userShareCents,
                owed_percent = null,
                created_at = now,
                updated_at = now,
            )
        }
    }

    fun archiveExternalSplit(
        id: String,
        archivedAt: String,
    ) {
        queries.transaction {
            queries.archiveSplitLines(
                split_id = id,
                archived_at = archivedAt,
                updated_at = archivedAt,
            )
            queries.archiveMovementSplit(
                id = id,
                archived_at = archivedAt,
                updated_at = archivedAt,
            )
        }
    }

    fun restoreExternalSplit(
        id: String,
        deletedAt: String,
        restoredAt: String,
    ) {
        queries.transaction {
            queries.restoreMovementSplit(id = id, archived_at = deletedAt, updated_at = restoredAt)
            queries.restoreSplitLines(split_id = id, archived_at = deletedAt, updated_at = restoredAt)
        }
    }

    fun getForMovement(movementId: String): MovementSplitDraft? =
        buildSplitDraft(
            queries.splitWithLinesByMovementId(movement_id = movementId, mapper = ::mapSplitLineRow).executeAsList(),
        )

    fun getForMovementById(id: String): MovementSplitDraft? =
        buildSplitDraft(
            queries.splitWithLinesById(id = id, mapper = ::mapSplitLineRow).executeAsList(),
        )

    private fun buildSplitDraft(lines: List<SplitLineRow>): MovementSplitDraft? {
        if (lines.isEmpty()) return null
        return MovementSplitDraft(
            entryMethod = SplitEntryMethod.entries.first { it.dbValue == lines.first().entryMethod },
            lines = lines.map { line ->
                SplitLineDraft(
                    participantKind = SplitParticipantKind.entries.first { it.dbValue == line.participantKind },
                    personId = line.personId,
                    owedAmountCents = line.owedAmountCents,
                    owedPercent = line.owedPercent,
                )
            },
        )
    }
}

/** Row shape shared by `splitWithLinesByMovementId`/`splitWithLinesById` (identical column lists). */
private data class SplitLineRow(
    val entryMethod: String,
    val participantKind: String,
    val personId: String?,
    val owedAmountCents: Long,
    val owedPercent: Double?,
)

private fun mapSplitLineRow(
    splitId: String,
    entryMethod: String,
    participantKind: String,
    personId: String?,
    owedAmountCents: Long,
    owedPercent: Double?,
): SplitLineRow = SplitLineRow(entryMethod, participantKind, personId, owedAmountCents, owedPercent)
