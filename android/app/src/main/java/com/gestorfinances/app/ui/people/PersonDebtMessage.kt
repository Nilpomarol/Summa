package com.gestorfinances.app.ui.people

import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonBalanceItemType

/** Direction of a person's outstanding balance, for the copy-to-chat message. */
enum class DebtMessageDirection { PERSON_OWES_USER, USER_OWES_PERSON, SETTLED }

/**
 * Structured content for the "copy pending debt to chat" feature: the items to list, an optional
 * carry-forward line for a prior partial settlement, and the authoritative total.
 */
data class PersonDebtMessage(
    val direction: DebtMessageDirection,
    val items: List<PersonBalanceItem>,
    val carryForwardCents: Long?,
    val totalCents: Long,
)

/**
 * Builds the copy-to-chat message content from a person's balance breakdown.
 *
 * Finds the most recent settlement (if any) and treats it as the cut point: only items after it
 * are listed individually. If that settlement didn't zero the balance, its leftover becomes a
 * single "carry forward" line instead of re-listing every older item.
 */
fun buildPersonDebtMessage(items: List<PersonBalanceItem>, balanceCents: Long): PersonDebtMessage {
    if (balanceCents == 0L) {
        return PersonDebtMessage(
            direction = DebtMessageDirection.SETTLED,
            items = emptyList(),
            carryForwardCents = null,
            totalCents = 0L,
        )
    }

    val sorted = items.sortedWith(compareBy({ it.date }, { it.type }, { it.sourceId }))

    var running = 0L
    val runningAfter = LongArray(sorted.size)
    sorted.forEachIndexed { index, item ->
        running += item.effectCents
        runningAfter[index] = running
    }

    fun isSettlement(item: PersonBalanceItem) =
        item.type == PersonBalanceItemType.SETTLEMENT_IN || item.type == PersonBalanceItemType.SETTLEMENT_OUT

    // The cut point is always the most recent settlement chronologically, not merely the most
    // recent one that happened to zero the balance — an earlier full settlement can be followed by
    // a later partial one, and that later settlement must still be the cut point rather than being
    // re-listed as a plain item.
    val lastSettlementIndex = sorted.indices.lastOrNull { index -> isSettlement(sorted[index]) }
    val cutIndex: Int
    val carryForwardCents: Long?
    if (lastSettlementIndex != null) {
        cutIndex = lastSettlementIndex
        val remainder = runningAfter[lastSettlementIndex]
        carryForwardCents = if (remainder == 0L) null else remainder
    } else {
        cutIndex = -1
        carryForwardCents = null
    }

    val direction = if (balanceCents > 0L) {
        DebtMessageDirection.PERSON_OWES_USER
    } else {
        DebtMessageDirection.USER_OWES_PERSON
    }

    return PersonDebtMessage(
        direction = direction,
        items = sorted.subList(cutIndex + 1, sorted.size),
        carryForwardCents = carryForwardCents,
        totalCents = balanceCents,
    )
}
