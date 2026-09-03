package com.gestorfinances.app.ui.people

import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.domain.rules.DebtConsumption
import com.gestorfinances.app.domain.rules.DebtItem

/** Direction of a person's outstanding balance, for the copy-to-chat message. */
enum class DebtMessageDirection { PERSON_OWES_USER, USER_OWES_PERSON, SETTLED }

/** A source item settlements left open, with the part that is still pending. */
data class PersonDebtResidual(
    val item: PersonBalanceItem,
    val remainingCents: Long,
    /** True when settlements paid part of this item, so the message shows remaining of original. */
    val isPartial: Boolean,
)

/**
 * Structured content for the "copy pending debt to chat" feature: the source items that are still
 * open, any settlement money no debt has absorbed yet, and the authoritative total.
 *
 * [totalCents] comes from `v_person_balance`; the residuals and credits explain it and always
 * reconcile to it.
 */
data class PersonDebtMessage(
    val direction: DebtMessageDirection,
    val residuals: List<PersonDebtResidual>,
    val creditAllCents: Long,
    val creditRecurringCents: Long,
    val totalCents: Long,
)

/**
 * Builds the copy-to-chat message content by replaying settlements against the debt they could
 * actually consume ([DebtConsumption]), so every line names a real source item instead of collapsing
 * history into one opaque carry-forward figure.
 */
fun buildPersonDebtMessage(items: List<PersonBalanceItem>, balanceCents: Long): PersonDebtMessage {
    if (balanceCents == 0L) {
        return PersonDebtMessage(
            direction = DebtMessageDirection.SETTLED,
            residuals = emptyList(),
            creditAllCents = 0L,
            creditRecurringCents = 0L,
            totalCents = 0L,
        )
    }

    val projection = DebtConsumption.project(
        items.map { item ->
            DebtItem(
                sourceId = item.sourceId,
                date = item.date,
                effectCents = item.effectCents,
                isSettlement = item.isSettlement,
                isRecurring = item.isRecurring,
                scope = item.scope,
            )
        },
    )
    val itemsById = items.associateBy { it.sourceId }

    return PersonDebtMessage(
        direction = if (balanceCents > 0L) {
            DebtMessageDirection.PERSON_OWES_USER
        } else {
            DebtMessageDirection.USER_OWES_PERSON
        },
        residuals = projection.residuals.mapNotNull { residual ->
            itemsById[residual.sourceId]?.let { item ->
                PersonDebtResidual(
                    item = item,
                    remainingCents = residual.remainingCents,
                    isPartial = residual.isPartial,
                )
            }
        },
        creditAllCents = projection.creditAllCents,
        creditRecurringCents = projection.creditRecurringCents,
        totalCents = balanceCents,
    )
}
