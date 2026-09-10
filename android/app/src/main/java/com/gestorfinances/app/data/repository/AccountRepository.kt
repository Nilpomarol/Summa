package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.AccountsQueries
import com.gestorfinances.app.data.db.SharedAccountsQueries
import java.util.UUID

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
    val ownerValueCents: Long = currentBalanceCents,
    val ownerOwnershipBasisPoints: Long = 10_000L,
    val ownershipKind: AccountOwnershipKind = AccountOwnershipKind.PERSONAL,
    val members: List<AccountMember> = emptyList(),
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
    val ownershipKind: AccountOwnershipKind = AccountOwnershipKind.PERSONAL,
    val members: List<AccountMemberDraft> = emptyList(),
)

enum class AccountOwnershipKind(val dbValue: String) {
    PERSONAL("personal"),
    SHARED("shared");

    companion object {
        fun fromDb(value: String) = entries.first { it.dbValue == value }
    }
}

data class AccountMember(
    val id: String,
    val participantKind: SplitParticipantKind,
    val personId: String?,
    val personName: String?,
    val ownershipBasisPoints: Long,
    val defaultExpenseBasisPoints: Long,
)

data class AccountMemberDraft(
    val participantKind: SplitParticipantKind,
    val personId: String?,
    val ownershipBasisPoints: Long,
    val defaultExpenseBasisPoints: Long,
)

/** Which way member money moves between a shared account and the member's own pocket. */
enum class ContributionDirection(val dbValue: String) {
    IN("in"),
    OUT("out");

    companion object {
        fun fromDb(value: String) = entries.first { it.dbValue == value }
    }
}

data class ContributionDraft(
    val id: String,
    val sharedAccountId: String,
    val direction: ContributionDirection,
    val contributorKind: SplitParticipantKind,
    val personId: String?,
    val sourceAccountId: String?,
    val amountCents: Long,
    val date: String,
    val name: String?,
    val notes: String?,
)

class AccountRepository(
    private val queries: AccountsQueries,
    private val sharedQueries: SharedAccountsQueries? = null,
) {
    fun runInTransaction(block: () -> Unit) {
        queries.transaction { block() }
    }

    fun listActive(): List<AccountSummary> =
        queries.activeAccountSummaries(::mapAccountSummary).executeAsList().map(::withMembers)

    fun getActive(id: String): AccountSummary? =
        queries.accountById(id, ::mapAccountSummary).executeAsOneOrNull()?.let(::withMembers)

    fun create(
        draft: AccountDraft,
        createdAt: String,
    ) {
        validateOwnership(draft)
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
                ownership_kind = AccountOwnershipKind.PERSONAL.dbValue,
                created_at = createdAt,
                updated_at = createdAt,
            )
            replaceMembers(draft, createdAt)
            if (draft.ownershipKind == AccountOwnershipKind.SHARED) {
                queries.setAccountOwnership(
                    ownership_kind = AccountOwnershipKind.SHARED.dbValue,
                    updated_at = createdAt,
                    id = draft.id,
                )
            }
        }
    }

    fun update(
        draft: AccountDraft,
        updatedAt: String,
    ) {
        validateOwnership(draft)
        require(draft.ownershipKind == AccountOwnershipKind.SHARED || !hasSharedHistory(draft.id)) {
            "An account that financed shared expenses or received contributions stays shared."
        }
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
                ownership_kind = AccountOwnershipKind.PERSONAL.dbValue,
                updated_at = updatedAt,
            )
            replaceMembers(draft, updatedAt)
            if (draft.ownershipKind == AccountOwnershipKind.SHARED) {
                queries.setAccountOwnership(
                    ownership_kind = AccountOwnershipKind.SHARED.dbValue,
                    updated_at = updatedAt,
                    id = draft.id,
                )
            }
        }
    }

    fun createContribution(draft: ContributionDraft, createdAt: String) {
        validateContribution(draft)
        requireNotNull(sharedQueries) { "Shared-account queries are unavailable." }.insertContribution(
            id = draft.id,
            shared_account_id = draft.sharedAccountId,
            direction = draft.direction.dbValue,
            contributor_kind = draft.contributorKind.dbValue,
            person_id = draft.personId,
            source_account_id = draft.sourceAccountId,
            amount_cents = draft.amountCents,
            date = draft.date,
            name = draft.name,
            notes = draft.notes,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    /**
     * Corrects a contribution's amount, date, contributor, owner-side account, name and notes. The
     * shared account and direction are what it was recorded as, so a correction never changes them.
     */
    fun updateContribution(draft: ContributionDraft, updatedAt: String) {
        val recorded = requireNotNull(getContribution(draft.id)) { "Contribution not found." }
        require(recorded.sharedAccountId == draft.sharedAccountId && recorded.direction == draft.direction) {
            "A correction keeps the contribution's shared account and direction."
        }
        validateContribution(draft)
        requireNotNull(sharedQueries).updateContribution(
            contributor_kind = draft.contributorKind.dbValue,
            person_id = draft.personId,
            source_account_id = draft.sourceAccountId,
            amount_cents = draft.amountCents,
            date = draft.date,
            name = draft.name,
            notes = draft.notes,
            updated_at = updatedAt,
            id = draft.id,
        )
    }

    fun getContribution(id: String): ContributionDraft? =
        requireNotNull(sharedQueries).contributionById(id) { contributionId, sharedAccountId, direction, contributorKind, personId, sourceAccountId, amountCents, date, name, notes ->
            ContributionDraft(
                id = contributionId,
                sharedAccountId = sharedAccountId,
                direction = ContributionDirection.fromDb(direction),
                contributorKind = SplitParticipantKind.entries.first { it.dbValue == contributorKind },
                personId = personId,
                sourceAccountId = sourceAccountId,
                amountCents = amountCents,
                date = date,
                name = name,
                notes = notes,
            )
        }.executeAsOneOrNull()

    private fun validateContribution(draft: ContributionDraft) {
        require(draft.amountCents > 0) { "Contribution amount must be positive." }
        val account = requireNotNull(getActive(draft.sharedAccountId)) { "Shared account is required." }
        require(account.ownershipKind == AccountOwnershipKind.SHARED) { "Contribution target must be shared." }
        require((draft.contributorKind == SplitParticipantKind.USER) == (draft.personId == null)) {
            "Contribution participant is invalid."
        }
        require(account.members.any { it.participantKind == draft.contributorKind && it.personId == draft.personId }) {
            "The contributor must be an active account member."
        }
        // The other side is only ever the app owner's own account: the source of money coming in,
        // the destination of money going out. A person's own accounts are not tracked, so their
        // rows name no account in either direction.
        require(draft.sourceAccountId == null || draft.contributorKind == SplitParticipantKind.USER) {
            "Only the app owner's own account can be the other side."
        }
        require(draft.sourceAccountId == null || draft.sourceAccountId != draft.sharedAccountId) {
            "The two sides of the movement must differ."
        }
        require(draft.sourceAccountId == null || getActive(draft.sourceAccountId)?.ownershipKind == AccountOwnershipKind.PERSONAL) {
            "The owner's side must be an owner-controlled account."
        }
    }

    fun archiveContribution(id: String, archivedAt: String) {
        // Named for the same reason as archiveMembersForAccount: positionally, this matched no row.
        requireNotNull(sharedQueries).archiveContribution(archived_at = archivedAt, updated_at = archivedAt, id = id)
    }

    fun restoreContribution(id: String, deletedAt: String, restoredAt: String) {
        requireNotNull(sharedQueries).restoreContribution(updated_at = restoredAt, id = id, archived_at = deletedAt)
    }

    /**
     * Whether the account carries shared-account history: expenses it financed, or contributions
     * made into it. Both are meaningless once the account is personal, and the movement triggers
     * reject any later edit of such an expense, so un-sharing has to be refused while any exist.
     * Archived rows count because restoring one would reintroduce the same contradiction.
     */
    fun hasSharedHistory(accountId: String): Boolean =
        (sharedQueries?.sharedHistoryCount(accountId)?.executeAsOne() ?: 0L) > 0L

    private fun withMembers(account: AccountSummary): AccountSummary =
        if (account.ownershipKind == AccountOwnershipKind.SHARED && sharedQueries != null) {
            account.copy(
                members = sharedQueries.membersForAccount(account.id) { id, _, kind, personId, personName, ownership, expense ->
                    AccountMember(id, SplitParticipantKind.entries.first { it.dbValue == kind }, personId, personName, ownership, expense)
                }.executeAsList(),
            )
        } else account

    private fun validateOwnership(draft: AccountDraft) {
        if (draft.ownershipKind == AccountOwnershipKind.PERSONAL) {
            require(draft.members.isEmpty()) { "Personal accounts cannot have shared members." }
            return
        }
        require(sharedQueries != null) { "Shared-account queries are unavailable." }
        require(draft.members.count { it.participantKind == SplitParticipantKind.USER } == 1) {
            "A shared account requires exactly one app-owner member."
        }
        require(draft.members.any { it.participantKind == SplitParticipantKind.PERSON }) {
            "A shared account requires at least one person."
        }
        require(draft.members.map { it.personId }.filterNotNull().distinct().size == draft.members.count { it.personId != null }) {
            "A person can be an account member only once."
        }
        require(draft.members.sumOf { it.ownershipBasisPoints } == 10_000L) {
            "Ownership percentages must total 100%."
        }
        require(draft.members.sumOf { it.defaultExpenseBasisPoints } == 10_000L) {
            "Default expense percentages must total 100%."
        }
    }

    private fun replaceMembers(draft: AccountDraft, timestamp: String) {
        val shared = sharedQueries ?: return
        // Named: SQLDelight orders a query's parameters by first use in the statement, so a
        // positional call here once archived nothing and left the old members active.
        shared.archiveMembersForAccount(archived_at = timestamp, updated_at = timestamp, account_id = draft.id)
        if (draft.ownershipKind == AccountOwnershipKind.SHARED) {
            draft.members.forEach { member ->
                shared.insertMember(
                    id = UUID.randomUUID().toString(),
                    account_id = draft.id,
                    participant_kind = member.participantKind.dbValue,
                    person_id = member.personId,
                    ownership_basis_points = member.ownershipBasisPoints,
                    default_expense_basis_points = member.defaultExpenseBasisPoints,
                    created_at = timestamp,
                    updated_at = timestamp,
                )
            }
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

    fun restore(
        id: String,
        deletedAt: String,
        restoredAt: String,
        wasDefault: Boolean,
    ) {
        queries.transaction {
            queries.restoreAccount(
                id = id,
                archived_at = deletedAt,
                updated_at = restoredAt,
                is_default = if (wasDefault) 1L else 0L,
            )
        }
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
    ownershipKind: String,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
    physicalBalanceCents: Long,
    ownerOwnershipBasisPoints: Long,
    ownerValueCents: Long,
): AccountSummary =
    AccountSummary(
        id = id,
        name = name,
        startingBalanceCents = startingBalanceCents,
        currentBalanceCents = physicalBalanceCents,
        ownerValueCents = ownerValueCents,
        ownerOwnershipBasisPoints = ownerOwnershipBasisPoints,
        ownershipKind = AccountOwnershipKind.fromDb(ownershipKind),
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
