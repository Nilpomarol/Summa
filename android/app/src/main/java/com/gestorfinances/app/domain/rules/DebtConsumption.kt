package com.gestorfinances.app.domain.rules

/** Which debt a settlement is allowed to consume. */
enum class SettlementScope(val dbValue: String) {
    ALL("all"),
    RECURRING("recurring"),
    ;

    companion object {
        fun fromDb(value: String): SettlementScope =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown settlement scope: $value")
    }
}

/**
 * One row of a person's balance breakdown, in `v_person_balance` signs: a positive [effectCents]
 * means the person owes the user more.
 *
 * [isRecurring] marks debt that originated from a recurring template, which is what a
 * `RECURRING`-scoped settlement is allowed to consume. [scope] is set on settlements only.
 */
data class DebtItem(
    val sourceId: String,
    val date: String,
    val effectCents: Long,
    val isSettlement: Boolean,
    val isRecurring: Boolean,
    val scope: SettlementScope?,
)

/** A debt item that settlements have not fully consumed. */
data class DebtResidual(
    val sourceId: String,
    val originalCents: Long,
    val remainingCents: Long,
) {
    /** True when settlements consumed part but not all of this item. */
    val isPartial: Boolean get() = remainingCents != originalCents
}

/**
 * What still explains a person's balance, and the credit that no debt has absorbed yet.
 *
 * [totalCents] is the sum of every item's effect, which is exactly what `v_person_balance` derives.
 * The residuals plus both credit buckets always reconcile to it.
 */
data class DebtProjection(
    val residuals: List<DebtResidual>,
    val creditAllCents: Long,
    val creditRecurringCents: Long,
    val totalCents: Long,
)

/**
 * Explains a person's balance by consuming debt chronologically, oldest first.
 *
 * A settlement applies only to eligible debt that already exists on its date, so items are folded
 * in date order with debt ranked before settlements on the same day. Whatever a settlement cannot
 * spend stays as directional credit in its own scope bucket and is absorbed by later debt, which
 * keeps a recurring-scoped overpayment from silently paying off unrelated expenses.
 *
 * Locked by `shared/golden/debt_consumption.json`.
 */
object DebtConsumption {
    fun project(items: List<DebtItem>): DebtProjection {
        val ordered = items.sortedWith(
            compareBy({ it.date }, { if (it.isSettlement) 1 else 0 }, { it.sourceId }),
        )
        val open = mutableListOf<OpenItem>()
        var creditAll = 0L
        var creditRecurring = 0L

        for (item in ordered) {
            if (item.effectCents == 0L) continue
            if (item.isSettlement) {
                val scope = requireNotNull(item.scope) { "Settlement ${item.sourceId} has no scope." }
                var power = kotlin.math.abs(item.effectCents)
                val direction = item.effectCents.sign()
                for (candidate in open) {
                    if (power == 0L) break
                    if (candidate.remaining.sign() != -direction) continue
                    if (scope == SettlementScope.RECURRING && !candidate.isRecurring) continue
                    val take = minOf(power, kotlin.math.abs(candidate.remaining))
                    candidate.remaining += direction * take
                    power -= take
                }
                if (power > 0L) {
                    val leftover = direction * power
                    when (scope) {
                        SettlementScope.ALL -> creditAll += leftover
                        SettlementScope.RECURRING -> creditRecurring += leftover
                    }
                }
            } else {
                var remaining = item.effectCents
                // Spend the narrower recurring credit first so it cannot be stranded by an
                // 'all' credit that any later item could have absorbed instead.
                if (item.isRecurring) {
                    val (left, credit) = absorb(remaining, creditRecurring)
                    remaining = left
                    creditRecurring = credit
                }
                val (left, credit) = absorb(remaining, creditAll)
                remaining = left
                creditAll = credit
                if (remaining != 0L) {
                    open += OpenItem(item.sourceId, item.effectCents, remaining, item.isRecurring)
                }
            }
        }

        return DebtProjection(
            residuals = open
                .filter { it.remaining != 0L }
                .map { DebtResidual(it.sourceId, it.originalCents, it.remaining) },
            creditAllCents = creditAll,
            creditRecurringCents = creditRecurring,
            totalCents = items.sumOf { it.effectCents },
        )
    }

    /** Applies opposite-signed [credit] to [amount], returning both after the transfer. */
    private fun absorb(amount: Long, credit: Long): Pair<Long, Long> {
        if (amount == 0L || credit == 0L || credit.sign() != -amount.sign()) return amount to credit
        val take = minOf(kotlin.math.abs(amount), kotlin.math.abs(credit))
        return (amount + credit.sign() * take) to (credit - credit.sign() * take)
    }

    private fun Long.sign(): Long = when {
        this > 0L -> 1L
        this < 0L -> -1L
        else -> 0L
    }

    private class OpenItem(
        val sourceId: String,
        val originalCents: Long,
        var remaining: Long,
        val isRecurring: Boolean,
    )
}
