package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.PeopleQueries
import com.gestorfinances.app.domain.rules.SettlementScope

data class PersonSummary(
    val id: String,
    val name: String,
    val avatar: String?,
    val color: String?,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
    val balanceCents: Long,
)

data class PersonDraft(
    val id: String,
    val name: String,
    val avatar: String?,
    val color: String?,
    val notes: String?,
)

enum class PersonBalanceItemType(val dbValue: String) {
    USER_PAID("user_paid"),
    PERSON_PAID("person_paid"),
    SETTLEMENT_IN("settlement_in"),
    SETTLEMENT_OUT("settlement_out"),
    ;

    companion object {
        fun fromDb(value: String): PersonBalanceItemType =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown person balance item type: $value")
    }
}

data class PersonBalanceItem(
    val sourceId: String,
    val type: PersonBalanceItemType,
    val date: String,
    val title: String?,
    val categoryId: String?,
    val categoryName: String?,
    val effectCents: Long,
    /** Debt raised by a recurring template, which is all a `RECURRING`-scoped settlement may consume. */
    val isRecurring: Boolean,
    /** Set on settlements only: the debt this settlement was allowed to consume. */
    val scope: SettlementScope?,
) {
    val isSettlement: Boolean
        get() = type == PersonBalanceItemType.SETTLEMENT_IN || type == PersonBalanceItemType.SETTLEMENT_OUT
}

class PersonRepository(
    private val queries: PeopleQueries,
) {
    fun listActive(): List<PersonSummary> =
        queries.activePeople(::mapPersonSummary).executeAsList()

    fun getActive(id: String): PersonSummary? =
        queries.personById(id, ::mapPersonSummary).executeAsOneOrNull()

    fun balanceItemsForPerson(personId: String): List<PersonBalanceItem> =
        queries.personBalanceItems(personId, ::mapPersonBalanceItem).executeAsList()

    fun create(
        draft: PersonDraft,
        createdAt: String,
    ) {
        queries.insertPerson(
            id = draft.id,
            name = draft.name,
            avatar = draft.avatar,
            color = draft.color,
            notes = draft.notes,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun update(
        draft: PersonDraft,
        updatedAt: String,
    ) {
        queries.updatePerson(
            id = draft.id,
            name = draft.name,
            avatar = draft.avatar,
            color = draft.color,
            notes = draft.notes,
            updated_at = updatedAt,
        )
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archivePerson(
            id = id,
            archived_at = archivedAt,
            updated_at = archivedAt,
        )
    }

    fun restore(id: String, deletedAt: String, restoredAt: String) {
        queries.restorePerson(id = id, archived_at = deletedAt, updated_at = restoredAt)
    }
}

private fun mapPersonSummary(
    id: String,
    name: String,
    avatar: String?,
    color: String?,
    notes: String?,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
    balanceCents: Long,
): PersonSummary =
    PersonSummary(
        id = id,
        name = name,
        avatar = avatar,
        color = color,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        balanceCents = balanceCents,
    )

private fun mapPersonBalanceItem(
    sourceId: String,
    sourceType: String,
    date: String?,
    title: String?,
    categoryId: String?,
    categoryName: String?,
    effectCents: Long,
    isRecurring: Long,
    settlementScope: String?,
): PersonBalanceItem =
    PersonBalanceItem(
        sourceId = sourceId,
        type = PersonBalanceItemType.fromDb(sourceType),
        date = requireNotNull(date) { "Person balance item date is required." },
        title = title,
        categoryId = categoryId,
        categoryName = categoryName,
        effectCents = effectCents,
        isRecurring = isRecurring != 0L,
        // Migration 011 backfills every pre-existing settlement to 'all'; defaulting here keeps a
        // row that somehow escaped it explaining the balance instead of failing the whole message.
        scope = when {
            settlementScope != null -> SettlementScope.fromDb(settlementScope)
            sourceType.startsWith("settlement") -> SettlementScope.ALL
            else -> null
        },
    )
