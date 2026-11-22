package com.gestorfinances.app.domain.rules

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class DuplicateMovement(
    val accountId: String,
    val amountCents: Long,
    val date: LocalDate,
    val name: String,
)

object DuplicateDetector {
    fun isDuplicate(
        existing: DuplicateMovement,
        candidate: DuplicateMovement,
        dateWindowDays: Long = 1,
    ): Boolean =
        existing.accountId == candidate.accountId &&
            existing.amountCents == candidate.amountCents &&
            abs(ChronoUnit.DAYS.between(existing.date, candidate.date)) <= dateWindowDays &&
            normalizeMovementName(existing.name) == normalizeMovementName(candidate.name)
}
