package com.gestorfinances.app.domain.rules

import java.time.LocalDate
import java.util.Locale

data class AutoCategorizeMovement(
    val name: String?,
    val payee: String?,
    val amountCents: Long,
    val date: LocalDate,
    val accountId: String,
)

data class AutoCategorizeConditions(
    val textContains: String? = null,
    val amountMinCents: Long? = null,
    val amountMaxCents: Long? = null,
    val accountId: String? = null,
    val dayOfMonthIn: Set<Int> = emptySet(),
)

data class AutoCategorizeAction(
    val categoryId: String? = null,
    val tripId: String? = null,
)

data class AutoCategorizeRule(
    val id: String,
    val priority: Int,
    val createdAt: String,
    val active: Boolean,
    val conditions: AutoCategorizeConditions,
    val action: AutoCategorizeAction,
)

data class AutoCategorizeMatch(
    val ruleId: String,
    val action: AutoCategorizeAction,
)

object AutoCategorizer {
    fun findMatch(
        movement: AutoCategorizeMovement,
        rules: List<AutoCategorizeRule>,
    ): AutoCategorizeMatch? =
        rules
            .asSequence()
            .filter { it.active }
            .filter { matches(movement, it.conditions) }
            .maxWithOrNull(compareBy<AutoCategorizeRule> { it.priority }.thenBy { it.createdAt })
            ?.let { AutoCategorizeMatch(ruleId = it.id, action = it.action) }

    private fun matches(
        movement: AutoCategorizeMovement,
        conditions: AutoCategorizeConditions,
    ): Boolean {
        val searchableText = "${movement.name.orEmpty()} ${movement.payee.orEmpty()}"
            .lowercase(Locale.ROOT)

        if (conditions.textContains != null &&
            !searchableText.contains(conditions.textContains.lowercase(Locale.ROOT))
        ) {
            return false
        }
        if (conditions.amountMinCents != null && movement.amountCents < conditions.amountMinCents) {
            return false
        }
        if (conditions.amountMaxCents != null && movement.amountCents > conditions.amountMaxCents) {
            return false
        }
        if (conditions.accountId != null && movement.accountId != conditions.accountId) {
            return false
        }
        if (conditions.dayOfMonthIn.isNotEmpty() &&
            movement.date.dayOfMonth !in conditions.dayOfMonthIn
        ) {
            return false
        }
        return true
    }
}
