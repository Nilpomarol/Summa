package com.gestorfinances.app.domain.rules

data class SplitCalculation(
    val valid: Boolean,
    val sharesCents: List<Long> = emptyList(),
    val reason: String? = null,
)

object SplitCalculator {
    fun equal(
        totalCents: Long,
        participantCount: Int,
        payerIndex: Int,
    ): SplitCalculation {
        val validationError = validateCommon(totalCents, participantCount, payerIndex)
        if (validationError != null) return validationError

        val base = totalCents / participantCount
        val remainder = totalCents % participantCount
        val shares = MutableList(participantCount) { base }
        shares[payerIndex] += remainder
        return SplitCalculation(valid = true, sharesCents = shares)
    }

    fun percentage(
        totalCents: Long,
        basisPoints: List<Int>,
        payerIndex: Int,
    ): SplitCalculation {
        val validationError = validateCommon(totalCents, basisPoints.size, payerIndex)
        if (validationError != null) return validationError
        if (basisPoints.any { it < 0 }) {
            return SplitCalculation(valid = false, reason = "basis points must be non-negative")
        }
        val totalBasisPoints = basisPoints.sum()
        if (totalBasisPoints != 10_000) {
            return SplitCalculation(valid = false, reason = "basis points sum $totalBasisPoints != 10000")
        }

        val shares = basisPoints.map { totalCents * it / 10_000 }.toMutableList()
        val leftover = totalCents - shares.sum()
        shares[payerIndex] += leftover
        return SplitCalculation(valid = true, sharesCents = shares)
    }

    fun exact(
        totalCents: Long,
        amountsCents: List<Long>,
    ): SplitCalculation {
        if (totalCents <= 0) {
            return SplitCalculation(valid = false, reason = "total must be positive")
        }
        if (amountsCents.any { it < 0 }) {
            return SplitCalculation(valid = false, reason = "amounts must be non-negative")
        }

        val sum = amountsCents.sum()
        if (sum != totalCents) {
            return SplitCalculation(valid = false, reason = "sum $sum != total $totalCents")
        }
        return SplitCalculation(valid = true, sharesCents = amountsCents)
    }

    private fun validateCommon(
        totalCents: Long,
        participantCount: Int,
        payerIndex: Int,
    ): SplitCalculation? =
        when {
            totalCents <= 0 -> SplitCalculation(valid = false, reason = "total must be positive")
            participantCount <= 0 -> SplitCalculation(valid = false, reason = "participants required")
            payerIndex !in 0 until participantCount -> {
                SplitCalculation(valid = false, reason = "payer index out of range")
            }
            else -> null
        }
}
