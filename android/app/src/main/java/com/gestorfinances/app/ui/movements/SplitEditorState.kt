package com.gestorfinances.app.ui.movements

import androidx.annotation.StringRes
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.domain.rules.SplitCalculator
import com.gestorfinances.app.ui.common.parseEuroCents

internal const val USER_PARTICIPANT_ID = "__user__"

data class SplitEditorState(
    val method: SplitEntryMethod = SplitEntryMethod.EQUAL,
    val payerParticipantId: String = USER_PARTICIPANT_ID,
    val selectedPersonIds: List<String> = emptyList(),
    val paidByPersonId: String? = null,
    val exactAmounts: Map<String, String> = emptyMap(),
    val percentages: Map<String, String> = emptyMap(),
) {
    val participantIds: List<String>
        get() = listOf(USER_PARTICIPANT_ID) + selectedPersonIds

    fun withMethod(nextMethod: SplitEntryMethod): SplitEditorState =
        copy(method = nextMethod, paidByPersonId = null)

    fun withManualSplit(): SplitEditorState =
        copy(paidByPersonId = null, payerParticipantId = USER_PARTICIPANT_ID)

    fun withPaidByOther(personId: String): SplitEditorState =
        copy(
            method = SplitEntryMethod.EXACT,
            payerParticipantId = personId,
            selectedPersonIds = listOf(personId),
            paidByPersonId = personId,
            exactAmounts = emptyMap(),
            percentages = emptyMap(),
        )

    fun withPayer(nextPayerParticipantId: String): SplitEditorState =
        if (nextPayerParticipantId in participantIds) {
            copy(payerParticipantId = nextPayerParticipantId, paidByPersonId = null)
        } else {
            this
        }

    fun withPersonToggled(personId: String): SplitEditorState {
        val selected = selectedPersonIds.toMutableList()
        val nextExactAmounts = exactAmounts.toMutableMap()
        val nextPercentages = percentages.toMutableMap()
        val nextPayer = if (personId == payerParticipantId) USER_PARTICIPANT_ID else payerParticipantId

        if (personId in selected) {
            selected.remove(personId)
            nextExactAmounts.remove(personId)
            nextPercentages.remove(personId)
        } else {
            selected += personId
        }

        return copy(
            selectedPersonIds = selected,
            payerParticipantId = nextPayer,
            paidByPersonId = null,
            exactAmounts = nextExactAmounts,
            percentages = nextPercentages,
        )
    }

    fun withExactAmount(
        participantId: String,
        amount: String,
    ): SplitEditorState =
        if (participantId in participantIds) {
            copy(
                paidByPersonId = null,
                exactAmounts = exactAmounts + (participantId to amount),
            )
        } else {
            this
        }

    fun withPercentage(
        participantId: String,
        percentage: String,
    ): SplitEditorState =
        if (participantId in participantIds) {
            copy(
                paidByPersonId = null,
                percentages = percentages + (participantId to percentage),
            )
        } else {
            this
        }

    fun calculation(totalCents: Long?): SplitEditorCalculation {
        if (totalCents == null || totalCents <= 0L) {
            return SplitEditorCalculation(
                valid = false,
                errorRes = R.string.split_validation_total_positive,
            )
        }
        paidByPersonId?.takeIf { it in selectedPersonIds }?.let { paidByPersonId ->
            return paidByOtherCalculation(totalCents, paidByPersonId)
        }
        if (selectedPersonIds.isEmpty()) {
            return SplitEditorCalculation(
                valid = false,
                errorRes = R.string.split_validation_participant_required,
            )
        }
        val participantIds = participantIds
        val payerIndex = participantIds.indexOf(payerParticipantId)
        if (payerIndex < 0) {
            return SplitEditorCalculation(
                valid = false,
                errorRes = R.string.split_validation_payer_required,
            )
        }

        return when (method) {
            SplitEntryMethod.EQUAL -> {
                val result = SplitCalculator.equal(totalCents, participantIds.size, payerIndex)
                result.toEditorCalculation(participantIds)
            }
            SplitEntryMethod.EXACT -> exactCalculation(totalCents, participantIds)
            SplitEntryMethod.PERCENTAGE -> percentageCalculation(totalCents, participantIds, payerIndex)
        }
    }

    fun toMovementSplitDraft(totalCents: Long?): MovementSplitDraft? {
        val calculation = calculation(totalCents)
        if (!calculation.valid) return null

        return MovementSplitDraft(
            entryMethod = if (paidByPersonId == null) method else SplitEntryMethod.EXACT,
            lines = participantIds.map { participantId ->
                val amount = requireNotNull(calculation.sharesCentsByParticipantId[participantId])
                if (participantId == USER_PARTICIPANT_ID) {
                    SplitLineDraft(
                        participantKind = SplitParticipantKind.USER,
                        personId = null,
                        owedAmountCents = amount,
                    )
                } else {
                    SplitLineDraft(
                        participantKind = SplitParticipantKind.PERSON,
                        personId = participantId,
                        owedAmountCents = amount,
                    )
                }
            },
        )
    }

    private fun paidByOtherCalculation(
        totalCents: Long,
        paidByPersonId: String,
    ): SplitEditorCalculation =
        SplitEditorCalculation(
            valid = true,
            sharesCentsByParticipantId = participantIds.associateWith { participantId ->
                if (participantId == paidByPersonId) totalCents else 0L
            },
        )

    private fun exactCalculation(
        totalCents: Long,
        participantIds: List<String>,
    ): SplitEditorCalculation {
        val amounts = participantIds.map { participantId ->
            val raw = exactAmounts[participantId].orEmpty()
            if (raw.isBlank()) {
                0L
            } else {
                parseEuroCents(raw, allowNegative = false)
                    ?: return SplitEditorCalculation(
                        valid = false,
                        errorRes = R.string.split_validation_reconcile,
                    )
            }
        }

        val delta = totalCents - amounts.sum()
        if (delta != 0L) {
            return SplitEditorCalculation(
                valid = false,
                sharesCentsByParticipantId = participantIds.zip(amounts).toMap(),
                deltaCents = delta,
            )
        }

        val result = SplitCalculator.exact(totalCents, amounts)
        return result.toEditorCalculation(participantIds)
    }

    private fun percentageCalculation(
        totalCents: Long,
        participantIds: List<String>,
        payerIndex: Int,
    ): SplitEditorCalculation {
        val basisPoints = participantIds.map { participantId ->
            val raw = percentages[participantId].orEmpty()
            if (raw.isBlank()) {
                0
            } else {
                parsePercentBasisPoints(raw)
                    ?: return SplitEditorCalculation(
                        valid = false,
                        errorRes = R.string.split_validation_reconcile,
                    )
            }
        }

        val basisDelta = 10_000 - basisPoints.sum()
        if (basisDelta != 0) {
            return SplitEditorCalculation(
                valid = false,
                deltaBasisPoints = basisDelta.toLong(),
            )
        }

        val result = SplitCalculator.percentage(totalCents, basisPoints, payerIndex)
        return result.toEditorCalculation(participantIds)
    }
}

data class SplitEditorCalculation(
    val valid: Boolean,
    val sharesCentsByParticipantId: Map<String, Long> = emptyMap(),
    val deltaCents: Long? = null,
    val deltaBasisPoints: Long? = null,
    @StringRes val errorRes: Int? = null,
)

private fun com.gestorfinances.app.domain.rules.SplitCalculation.toEditorCalculation(
    participantIds: List<String>,
): SplitEditorCalculation =
    if (valid) {
        SplitEditorCalculation(
            valid = true,
            sharesCentsByParticipantId = participantIds.zip(sharesCents).toMap(),
        )
    } else {
        SplitEditorCalculation(
            valid = false,
            errorRes = R.string.split_validation_reconcile,
        )
    }

internal fun parsePercentBasisPoints(raw: String): Int? {
    var value = raw.trim()
        .replace("%", "")
        .replace(" ", "")
    if (value.isEmpty()) return null
    if (value.startsWith("-")) return null
    if (value.any { !it.isDigit() && it != ',' && it != '.' }) return null

    val separatorIndex = maxOf(value.lastIndexOf(','), value.lastIndexOf('.'))
    val wholeText: String
    val fractionalText: String
    if (separatorIndex >= 0) {
        val decimalSeparator = value[separatorIndex]
        val thousandsSeparator = if (decimalSeparator == ',') '.' else ','
        wholeText = value.substring(0, separatorIndex)
        if (wholeText.any { !it.isDigit() && it != thousandsSeparator }) return null
        val decimalText = value.substring(separatorIndex + 1)
        if (decimalText.length > 2 || decimalText.any { !it.isDigit() }) return null
        fractionalText = decimalText.padEnd(2, '0')
    } else {
        wholeText = value
        fractionalText = "00"
    }

    val wholePart = wholeText.filter { it.isDigit() }
    if (wholePart.isEmpty()) return null
    val basisPoints = (wholePart.toLongOrNull() ?: return null) * 100L +
        (fractionalText.toLongOrNull() ?: return null)
    return basisPoints.takeIf { it in 0..10_000 }?.toInt()
}
