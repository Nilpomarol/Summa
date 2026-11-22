package com.gestorfinances.app.ui.people

import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonBalanceItemType
import com.gestorfinances.app.domain.rules.SettlementScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonDebtMessageTest {
    @Test
    fun withoutSettlementsEveryItemIsListedInFull() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_850),
            item("taxi", "2026-01-05", PersonBalanceItemType.USER_PAID, 720),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 2_570)

        assertEquals(DebtMessageDirection.PERSON_OWES_USER, message.direction)
        assertEquals(listOf("dinner" to 1_850L, "taxi" to 720L), message.residualPairs())
        assertTrue(message.residuals.none { it.isPartial })
        assertEquals(2_570L, message.totalCents)
    }

    @Test
    fun aFullySettledItemDisappearsAndLaterItemsRemain() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            settlement("settle-1", "2026-01-02", -1_000),
            item("taxi", "2026-01-10", PersonBalanceItemType.USER_PAID, 500),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 500)

        assertEquals(listOf("taxi" to 500L), message.residualPairs())
        assertEquals(500L, message.totalCents)
    }

    @Test
    fun aPartiallyPaidItemKeepsItsOwnLineInsteadOfACarryForward() {
        // dinner 1850 + taxi 720, settled by 1570: FIFO pays dinner down to 280 and leaves taxi
        // untouched, so the message names both instead of collapsing them into one figure.
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_850),
            item("taxi", "2026-01-02", PersonBalanceItemType.USER_PAID, 720),
            settlement("settle-1", "2026-01-05", -1_570),
            item("coffee", "2026-01-10", PersonBalanceItemType.USER_PAID, 2_570),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 3_570)

        assertEquals(
            listOf("dinner" to 280L, "taxi" to 720L, "coffee" to 2_570L),
            message.residualPairs(),
        )
        assertEquals(listOf(true, false, false), message.residuals.map { it.isPartial })
        assertEquals(3_570L, message.totalCents)
    }

    @Test
    fun anEarlierFullSettlementDoesNotHideDebtLeftOpenByALaterPartialOne() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            settlement("settle-1", "2026-01-02", -1_000),
            item("taxi", "2026-01-10", PersonBalanceItemType.USER_PAID, 500),
            settlement("settle-2", "2026-01-15", -300),
            item("movie", "2026-01-20", PersonBalanceItemType.USER_PAID, 800),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 1_000)

        assertEquals(listOf("taxi" to 200L, "movie" to 800L), message.residualPairs())
        assertEquals(1_000L, message.totalCents)
    }

    @Test
    fun anExceptionalExpenseExplainsTheSameWhetherItPrecedesOrFollowsTheSettlement() {
        val monthly = item("monthly", "2026-01-05", PersonBalanceItemType.USER_PAID, 1_000, recurring = true)
        val settle = settlement("settle-1", "2026-01-20", -1_000, scope = SettlementScope.RECURRING)
        val exceptionalBefore = item("trip", "2026-01-10", PersonBalanceItemType.USER_PAID, 4_000)
        val exceptionalAfter = item("trip", "2026-01-25", PersonBalanceItemType.USER_PAID, 4_000)

        val before = buildPersonDebtMessage(listOf(monthly, exceptionalBefore, settle), balanceCents = 4_000)
        val after = buildPersonDebtMessage(listOf(monthly, settle, exceptionalAfter), balanceCents = 4_000)

        assertEquals(listOf("trip" to 4_000L), before.residualPairs())
        assertEquals(listOf("trip" to 4_000L), after.residualPairs())
    }

    @Test
    fun aRecurringScopedSettlementLeavesUnrelatedDebtUntouched() {
        val items = listOf(
            item("trip", "2026-01-05", PersonBalanceItemType.USER_PAID, 3_000),
            item("monthly", "2026-01-10", PersonBalanceItemType.USER_PAID, 2_000, recurring = true),
            settlement("settle-1", "2026-01-20", -2_000, scope = SettlementScope.RECURRING),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 3_000)

        assertEquals(listOf("trip" to 3_000L), message.residualPairs())
        assertEquals(0L, message.creditRecurringCents)
    }

    @Test
    fun overSettlementFlipsDirectionAndKeepsTheExcessAsCredit() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            settlement("settle-1", "2026-01-05", -1_500),
        )

        val message = buildPersonDebtMessage(items, balanceCents = -500)

        assertEquals(DebtMessageDirection.USER_OWES_PERSON, message.direction)
        assertTrue(message.residuals.isEmpty())
        assertEquals(-500L, message.creditAllCents)
        assertEquals(-500L, message.totalCents)
    }

    @Test
    fun residualsAndCreditAlwaysReconcileWithTheAuthoritativeTotal() {
        val items = listOf(
            item("a", "2026-01-01", PersonBalanceItemType.USER_PAID, 2_500),
            item("b", "2026-01-03", PersonBalanceItemType.PERSON_PAID, -900),
            settlement("s1", "2026-01-08", -2_000),
            item("c", "2026-01-12", PersonBalanceItemType.USER_PAID, 1_400, recurring = true),
            settlement("s2", "2026-01-18", -3_000, scope = SettlementScope.RECURRING),
        )
        val balance = items.sumOf { it.effectCents }

        val message = buildPersonDebtMessage(items, balanceCents = balance)

        assertEquals(
            balance,
            message.residuals.sumOf { it.remainingCents } +
                message.creditAllCents +
                message.creditRecurringCents,
        )
    }

    @Test
    fun zeroBalanceReturnsSettledRegardlessOfItems() {
        val items = listOf(
            item("dinner", "2026-01-01", PersonBalanceItemType.USER_PAID, 1_000),
            settlement("settle-1", "2026-01-02", -1_000),
        )

        val message = buildPersonDebtMessage(items, balanceCents = 0)

        assertEquals(DebtMessageDirection.SETTLED, message.direction)
        assertTrue(message.residuals.isEmpty())
        assertEquals(0L, message.totalCents)
    }

    private fun PersonDebtMessage.residualPairs(): List<Pair<String, Long>> =
        residuals.map { it.item.sourceId to it.remainingCents }

    private fun item(
        sourceId: String,
        date: String,
        type: PersonBalanceItemType,
        effectCents: Long,
        recurring: Boolean = false,
    ): PersonBalanceItem =
        PersonBalanceItem(
            sourceId = sourceId,
            type = type,
            date = date,
            title = null,
            categoryId = null,
            categoryName = null,
            effectCents = effectCents,
            isRecurring = recurring,
            scope = null,
        )

    private fun settlement(
        sourceId: String,
        date: String,
        effectCents: Long,
        scope: SettlementScope = SettlementScope.ALL,
    ): PersonBalanceItem =
        PersonBalanceItem(
            sourceId = sourceId,
            type = if (effectCents < 0L) {
                PersonBalanceItemType.SETTLEMENT_IN
            } else {
                PersonBalanceItemType.SETTLEMENT_OUT
            },
            date = date,
            title = null,
            categoryId = null,
            categoryName = null,
            effectCents = effectCents,
            isRecurring = false,
            scope = scope,
        )
}
