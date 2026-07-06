package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.AutoCatRulesQueries
import com.gestorfinances.app.domain.rules.AutoCategorizeAction
import com.gestorfinances.app.domain.rules.AutoCategorizeConditions
import com.gestorfinances.app.domain.rules.AutoCategorizeRule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `auto_cat_rules.conditions` payload (shared/schemas/auto_cat_rules.conditions.schema.json). */
@Serializable
private data class AutoCatConditionsJson(
    @SerialName("text_contains") val textContains: String? = null,
    @SerialName("amount_min_cents") val amountMinCents: Long? = null,
    @SerialName("amount_max_cents") val amountMaxCents: Long? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("day_of_month_in") val dayOfMonthIn: Set<Int> = emptySet(),
)

/**
 * Read-only access to `auto_cat_rules` for the movement-form category suggestion (audit F1).
 * No CRUD yet — rules can only be inserted directly against the DB until a rules-management
 * screen ships (tracked separately, e.g. alongside Phase 6C CSV import).
 */
class AutoCatRuleRepository(
    private val queries: AutoCatRulesQueries,
) {
    fun listActive(): List<AutoCategorizeRule> =
        queries.activeAutoCatRules(::mapRule).executeAsList()

    private fun mapRule(
        id: String,
        priority: Long,
        conditions: String,
        actionCategoryId: String?,
        actionTripId: String?,
        active: Long,
        createdAt: String,
    ): AutoCategorizeRule {
        val decoded = autoCatJson.decodeFromString<AutoCatConditionsJson>(conditions)
        return AutoCategorizeRule(
            id = id,
            priority = priority.toInt(),
            createdAt = createdAt,
            active = active == 1L,
            conditions = AutoCategorizeConditions(
                textContains = decoded.textContains,
                amountMinCents = decoded.amountMinCents,
                amountMaxCents = decoded.amountMaxCents,
                accountId = decoded.accountId,
                dayOfMonthIn = decoded.dayOfMonthIn,
            ),
            action = AutoCategorizeAction(categoryId = actionCategoryId, tripId = actionTripId),
        )
    }
}

private val autoCatJson = Json { ignoreUnknownKeys = true }
