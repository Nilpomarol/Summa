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
)

class SplitRepository(
    private val queries: SplitsQueries,
) {
    fun createExternalPaidByPerson(
        draft: ExternalSplitDraft,
        createdAt: String,
    ) {
        require(draft.totalAmountCents > 0L) {
            "External split total must be positive."
        }
        require(draft.userShareCents >= 0L) {
            "External split user share must be non-negative."
        }
        require(draft.userShareCents <= draft.totalAmountCents) {
            "External split user share cannot exceed the total."
        }
        require(draft.payerPersonId.isNotBlank()) {
            "External split payer person is required."
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
                created_at = createdAt,
                updated_at = createdAt,
            )
            queries.insertSplitLine(
                id = UUID.randomUUID().toString(),
                split_id = draft.id,
                participant_kind = SplitParticipantKind.USER.dbValue,
                person_id = null,
                owed_amount_cents = draft.userShareCents,
                created_at = createdAt,
                updated_at = createdAt,
            )
            queries.insertSplitLine(
                id = UUID.randomUUID().toString(),
                split_id = draft.id,
                participant_kind = SplitParticipantKind.PERSON.dbValue,
                person_id = draft.payerPersonId,
                owed_amount_cents = draft.totalAmountCents - draft.userShareCents,
                created_at = createdAt,
                updated_at = createdAt,
            )
        }
    }
}
