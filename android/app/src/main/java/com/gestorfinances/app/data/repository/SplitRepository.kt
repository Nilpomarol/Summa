package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.SplitsQueries

class SplitRepository(
    private val queries: SplitsQueries,
) {
    fun getForMovement(movementId: String): MovementSplitDraft? {
        val lines = queries.splitWithLinesByMovementId(movement_id = movementId).executeAsList()
        if (lines.isEmpty()) return null
        return MovementSplitDraft(
            entryMethod = SplitEntryMethod.entries.first { it.dbValue == lines.first().entry_method },
            lines = lines.map { line ->
                SplitLineDraft(
                    participantKind = SplitParticipantKind.entries.first { it.dbValue == line.participant_kind },
                    personId = line.person_id,
                    owedAmountCents = line.owed_amount_cents,
                    owedPercent = line.owed_percent,
                )
            },
        )
    }
}
