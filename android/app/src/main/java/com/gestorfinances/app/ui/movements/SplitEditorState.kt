package com.gestorfinances.app.ui.movements

import androidx.annotation.StringRes
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.domain.rules.SplitCalculator
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents

internal const val USER_PARTICIPANT_ID = "__user__"

data class SplitEditorState(
    val method: SplitEntryMethod = SplitEntryMethod.EQUAL,
    val payerParticipantId: String = USER_PARTICIPANT_ID,
    val selectedPersonIds: List<String> = emptyList(),
    val exactAmounts: Map<String, String> = emptyMap(),
    val percentages: Map<String, String> = emptyMap(),
) {
    val participantIds: List<String>
        get() = listOf(USER_PARTICIPANT_ID) + selectedPersonIds

    fun withMethod(nextMethod: SplitEntryMethod): SplitEditorState =
        copy(method = nextMethod)

    fun withPayer(nextPayerParticipantId: String): SplitEditorState =
        if (nextPayerParticipantId in participantIds) {
            copy(payerParticipantId = nextPayerParticipantId)
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
            exactAmounts = nextExactAmounts,
            percentages = nextPercentages,
        )
    }

    fun withExactAmount(
        participantId: String,
        amount: String,
    ): SplitEditorState =
        if (participantId in participantIds) {
            copy(exactAmounts = exactAmounts + (participantId to amount))
        } else {
            this
        }

    fun withPercentage(
        participantId: String,
        percentage: String,
    ): SplitEditorState =
        if (participantId in participantIds) {
            copy(percentages = percentages + (participantId to percentage))
        } else {
            this
        }

    /**
     * The exact amounts and percentages set to an equal split of [totalCents] (odd cents and
     * hundredths going to the first participants), so those fields open filled, ready to adjust.
     */
    fun evenlyFilled(totalCents: Long?): SplitEditorState {
        val ids = participantIds
        val exact = if (totalCents != null && totalCents > 0L) {
            ids.mapIndexed { index, id ->
                id to formatEuroInput(totalCents / ids.size + if (index < totalCents % ids.size) 1 else 0)
            }.toMap()
        } else {
            emptyMap()
        }
        val percent = ids.mapIndexed { index, id ->
            id to percentInput(10_000 / ids.size + if (index < 10_000 % ids.size) 1 else 0)
        }.toMap()
        return copy(exactAmounts = exact, percentages = percent)
    }

    /** [withExactAmount], and between two people the other takes what is left of [totalCents]. */
    fun withExactAmountBalanced(participantId: String, amount: String, totalCents: Long?): SplitEditorState {
        val next = withExactAmount(participantId, amount)
        val other = participantIds.singleOrNull { it != participantId } ?: return next
        val typed = parseEuroCents(amount, allowNegative = false) ?: return next
        if (totalCents == null || participantIds.size != 2 || typed > totalCents) return next
        return next.copy(exactAmounts = next.exactAmounts + (other to formatEuroInput(totalCents - typed)))
    }

    /** [withPercentage], and between two people the other takes the rest of the hundred. */
    fun withPercentageBalanced(participantId: String, percentage: String): SplitEditorState {
        val next = withPercentage(participantId, percentage)
        val other = participantIds.singleOrNull { it != participantId } ?: return next
        val typed = parsePercentBasisPoints(percentage) ?: return next
        if (participantIds.size != 2) return next
        return next.copy(percentages = next.percentages + (other to percentInput(10_000 - typed)))
    }

    fun calculation(totalCents: Long?): SplitEditorCalculation {
        if (totalCents == null || totalCents <= 0L) {
            return SplitEditorCalculation(
                valid = false,
                errorRes = R.string.split_validation_total_positive,
            )
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
            entryMethod = method,
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

/** This split (or a fresh one) with [personId] taking part and advancing the money. */
internal fun SplitEditorState?.withPayerPerson(personId: String): SplitEditorState {
    val editor = this ?: SplitEditorState()
    val included = if (personId in editor.selectedPersonIds) editor else editor.withPersonToggled(personId)
    return included.withPayer(personId)
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

/** Basis points as a percentage field holds them: "50", "33,33". */
private fun percentInput(basisPoints: Int): String =
    if (basisPoints % 100 == 0) "${basisPoints / 100}" else "${basisPoints / 100},${(basisPoints % 100).toString().padStart(2, '0')}"

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
