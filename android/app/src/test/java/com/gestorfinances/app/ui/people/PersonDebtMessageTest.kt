package com.gestorfinances.app.ui.people

import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonBalanceItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonDebtMessageTest {
    @Test
    fun noSettlementShowsAllItemsWithoutCarryForward() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_850),
            item("taxi", "2026-01-05", PersonBalanceItemType.USER_PAID, 720),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 2_570)

        assertEquals(DebtMessageDirection.PERSON_OWES_USER, message.direction)
        assertEquals(listOf("dinner", "taxi"), message.items.map { it.sourceId })
        assertNull(message.carryForwardCents)
        assertEquals(2_570L, message.totalCents)
    }

    @Test
    fun fullSettlementClearsOlderItemsAndShowsOnlyNewerOnes() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            item("settle-1", "2026-01-02", PersonBalanceItemType.SETTLEMENT_IN, -1_000),
            item("taxi", "2026-01-10", PersonBalanceItemType.USER_PAID, 500),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 500)

        assertEquals(DebtMessageDirection.PERSON_OWES_USER, message.direction)
        assertEquals(listOf("taxi"), message.items.map { it.sourceId })
        assertNull(message.carryForwardCents)
        assertEquals(500L, message.totalCents)
    }

    @Test
    fun partialSettlementCarriesForwardRemainingBalance() {
        // Worked example: dinner 1850 + taxi 720, partial settlement leaves 1000 owed, plus a new
        // item after the settlement — carry-forward line covers the pre-settlement remainder.
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_850),
            item("taxi", "2026-01-02", PersonBalanceItemType.USER_PAID, 720),
            item("settle-1", "2026-01-05", PersonBalanceItemType.SETTLEMENT_IN, -1_570),
            item("coffee", "2026-01-10", PersonBalanceItemType.USER_PAID, 2_570),
        )
        // Running total after settle-1 = 1850 + 720 - 1570 = 1000 (non-zero carry-forward).
        // Final balance = 1000 + 2570 = 3570.

        val message = buildPersonDebtMessage(items, balanceCents = 3_570)

        assertEquals(DebtMessageDirection.PERSON_OWES_USER, message.direction)
        assertEquals(listOf("coffee"), message.items.map { it.sourceId })
        assertEquals(1_000L, message.carryForwardCents)
        assertEquals(3_570L, message.totalCents)
    }

    @Test
    fun laterPartialSettlementIsCutPointEvenAfterAnEarlierFullSettlement() {
        // dinner(+1000), settle-1 fully clears it (running=0), taxi(+500), settle-2 only partially
        // repays it (running=200). settle-2 — not settle-1 — must be the cut point: it should not be
        // re-listed as a plain item, and the 200 leftover must surface as a carry-forward line.
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            item("settle-1", "2026-01-02", PersonBalanceItemType.SETTLEMENT_IN, -1_000),
            item("taxi", "2026-01-10", PersonBalanceItemType.USER_PAID, 500),
            item("settle-2", "2026-01-15", PersonBalanceItemType.SETTLEMENT_IN, -300),
            item("movie", "2026-01-20", PersonBalanceItemType.USER_PAID, 800),
        )
        // Running total after settle-2 = 500 - 300 = 200 (carry-forward). Final = 200 + 800 = 1000.

        val message = buildPersonDebtMessage(items, balanceCents = 1_000)

        assertEquals(DebtMessageDirection.PERSON_OWES_USER, message.direction)
        assertEquals(listOf("movie"), message.items.map { it.sourceId })
        assertEquals(200L, message.carryForwardCents)
        assertEquals(1_000L, message.totalCents)
    }

    @Test
    fun overSettlementFlipsDirectionAndCarriesForwardFlippedAmount() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            item("settle-1", "2026-01-05", PersonBalanceItemType.SETTLEMENT_IN, -1_500),
        )
        // Running total after settle-1 = 1000 - 1500 = -500 (non-zero, flips direction).

        val message = buildPersonDebtMessage(items, balanceCents = -500)

        assertEquals(DebtMessageDirection.USER_OWES_PERSON, message.direction)
        assertTrue(message.items.isEmpty())
        assertEquals(-500L, message.carryForwardCents)
        assertEquals(-500L, message.totalCents)
    }

    @Test
    fun zeroBalanceReturnsSettledRegardlessOfItems() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            item("settle-1", "2026-01-02", PersonBalanceItemType.SETTLEMENT_IN, -1_000),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 0)

        assertEquals(DebtMessageDirection.SETTLED, message.direction)
        assertTrue(message.items.isEmpty())
        assertNull(message.carryForwardCents)
        assertEquals(0L, message.totalCents)
    }

    private fun item(
        sourceId: String,
        date: String,
        type: PersonBalanceItemType,
        effectCents: Long,
    ): PersonBalanceItem =
        PersonBalanceItem(
            sourceId = sourceId,
            type = type,
            date = date,
            title = null,
            categoryId = null,
            categoryName = null,
            effectCents = effectCents,
        )
}
