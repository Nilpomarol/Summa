package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.AccountsQueries

enum class AccountType(val dbValue: String) {
    BANK("bank"),
    CASH("cash"),
    SAVINGS("savings"),
    INVESTMENT("investment"),
    OTHER("other"),
    ;

    companion object {
        fun fromDb(value: String): AccountType =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown account type: $value")
    }
}

data class AccountSummary(
    val id: String,
    val name: String,
    val startingBalanceCents: Long,
    val currentBalanceCents: Long,
    val type: AccountType,
    val icon: String?,
    val color: String?,
    val isDefault: Boolean,
    val displayOrder: Long,
    val lowBalanceThresholdCents: Long?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

data class AccountDraft(
    val id: String,
    val name: String,
    val startingBalanceCents: Long,
    val type: AccountType,
    val icon: String?,
    val color: String?,
    val isDefault: Boolean,
    val displayOrder: Long,
    val lowBalanceThresholdCents: Long?,
)

class AccountRepository(
    private val queries: AccountsQueries,
) {
    fun runInTransaction(block: () -> Unit) {
        queries.transaction { block() }
    }

    fun listActive(): List<AccountSummary> =
        queries.activeAccountSummaries(::mapAccountSummary).executeAsList()

    fun getActive(id: String): AccountSummary? =
        queries.accountById(id, ::mapAccountSummary).executeAsOneOrNull()

    fun create(
        draft: AccountDraft,
        createdAt: String,
    ) {
        queries.transaction {
            if (draft.isDefault) {
                queries.clearDefaultAccounts(updated_at = createdAt)
            }
            queries.insertAccount(
                id = draft.id,
                name = draft.name,
                starting_balance_cents = draft.startingBalanceCents,
                type = draft.type.dbValue,
                icon = draft.icon,
                color = draft.color,
                is_default = draft.isDefault.toDbLong(),
                display_order = draft.displayOrder,
                low_balance_threshold_cents = draft.lowBalanceThresholdCents,
                created_at = createdAt,
                updated_at = createdAt,
            )
        }
    }

    fun update(
        draft: AccountDraft,
        updatedAt: String,
    ) {
        queries.transaction {
            if (draft.isDefault) {
                queries.clearDefaultAccounts(updated_at = updatedAt)
            }
            queries.updateAccount(
                id = draft.id,
                name = draft.name,
                starting_balance_cents = draft.startingBalanceCents,
                type = draft.type.dbValue,
                icon = draft.icon,
                color = draft.color,
                is_default = draft.isDefault.toDbLong(),
                display_order = draft.displayOrder,
                low_balance_threshold_cents = draft.lowBalanceThresholdCents,
                updated_at = updatedAt,
            )
        }
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archiveAccount(
            id = id,
            archived_at = archivedAt,
            updated_at = archivedAt,
        )
    }
}

private fun mapAccountSummary(
    id: String,
    name: String,
    startingBalanceCents: Long,
    type: String,
    icon: String?,
    color: String?,
    isDefault: Long,
    displayOrder: Long,
    lowBalanceThresholdCents: Long?,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
    currentBalanceCents: Long,
): AccountSummary =
    AccountSummary(
        id = id,
        name = name,
        startingBalanceCents = startingBalanceCents,
        currentBalanceCents = currentBalanceCents,
        type = AccountType.fromDb(type),
        icon = icon,
        color = color,
        isDefault = isDefault != 0L,
        displayOrder = displayOrder,
        lowBalanceThresholdCents = lowBalanceThresholdCents,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L
