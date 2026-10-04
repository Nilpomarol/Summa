package com.gestorfinances.app.data.sync

import com.gestorfinances.app.data.backup.sqlLiteral
import java.io.File

/*
 * Record-level merge of two copies of the database. Each device keeps working on its own; a merge
 * brings in what the other one changed, record by record, against the state both had after their
 * last merge (the base). Nothing is ever merged inside a record, and records that only make sense
 * together travel together: a movement with its split, an account with its members, a budget with
 * its versions. Only a record changed on both sides is a conflict, and the owner settles those.
 */

/** Who wins the records changed on both devices. */
enum class ConflictChoice { MINE, THEIRS, NEWEST }

/** A record changed on both devices, described enough for the owner to recognise it. */
data class SyncConflict(
    /** The table the record belongs to: `movements`, `accounts`, `categories`… */
    val kind: String,
    val id: String,
    val name: String?,
    val date: String?,
    val amountCents: Long?,
)

data class MergeResult(
    /** Left unsettled: nothing was applied, and the merge must be run again with a choice. */
    val conflicts: List<SyncConflict>,
    /** How many rows of this database changed. */
    val changedRows: Int,
)

/** The merge would leave [records] against a shared-account rule: nothing was applied. */
class SyncRuleBreak(val records: List<String>) :
    RuntimeException("The merged records break a shared-account rule: ${records.joinToString()}")

class SyncSchemaMismatch(val mine: String?, val theirs: String?) :
    RuntimeException("Schema $mine cannot merge with schema $theirs")

private class MergeTable(val name: String, val key: List<String>, val family: String, val familyColumn: String?)

// Parents before children. `meta` and anything not listed stays this device's own.
private val MERGE_TABLES = listOf(
    MergeTable("accounts", listOf("id"), "accounts", "id"),
    MergeTable("categories", listOf("id"), "categories", "id"),
    MergeTable("people", listOf("id"), "people", "id"),
    MergeTable("trips", listOf("id"), "trips", "id"),
    MergeTable("tags", listOf("id"), "tags", "id"),
    MergeTable("templates", listOf("id"), "templates", "id"),
    MergeTable("import_batches", listOf("id"), "import_batches", "id"),
    MergeTable("budgets", listOf("id"), "budgets", "id"),
    MergeTable("budget_versions", listOf("budget_id", "from_month"), "budgets", "budget_id"),
    MergeTable("movements", listOf("id"), "movements", "id"),
    MergeTable("splits", listOf("id"), "movements", "movement_id"),
    // A line reaches its movement through its split: see splitOwners.
    MergeTable("split_lines", listOf("id"), "movements", null),
    MergeTable("account_members", listOf("id"), "accounts", "account_id"),
    MergeTable("account_contributions", listOf("id"), "account_contributions", "id"),
    MergeTable("goals", listOf("id"), "goals", "id"),
    MergeTable("goal_allocations", listOf("id"), "goal_allocations", "id"),
)

private data class RowRef(val table: MergeTable, val key: List<String>)

private class RowState(val signature: String, val updatedAt: String, val owner: String?)

private data class Family(val kind: String, val id: String)

private const val SEPARATOR = "\u001F"

/**
 * Merges the database file [theirs] into the one [session] is connected to. [base] is what both
 * held after their last merge, or null when they never merged. With conflicts and no [choice],
 * nothing changes and the conflicts are returned; otherwise the merge is applied in one
 * transaction, and a result that breaks the schema's rules is rolled back and thrown. [open] gives
 * a session on another database file; those are only read.
 */
fun mergeDatabase(
    session: SqlSession,
    open: (File) -> SqlSession,
    theirs: File,
    base: File?,
    choice: ConflictChoice?,
): MergeResult {
    val theirSession = open(theirs)
    val baseSession = base?.let(open)
    try {
        val mineVersion = session.schemaVersion()
        val theirVersion = theirSession.schemaVersion()
        if (mineVersion == null || mineVersion != theirVersion) throw SyncSchemaMismatch(mineVersion, theirVersion)

        val columns = MERGE_TABLES.associateWith { table ->
            session.query("SELECT name FROM pragma_table_info(${sqlLiteral(table.name)}) ORDER BY cid").map { it[0]!! }
        }
        val mine = session.rows(columns)
        val their = theirSession.rows(columns)
        // An older base would compare rows of another shape; without it, differences become conflicts.
        val baseRows = baseSession?.takeIf { it.schemaVersion() == mineVersion }?.rows(columns) ?: emptyMap()
        val splitOwners = (mine + their).filter { it.key.table.name == "splits" }.mapValues { it.value.owner }
            .mapKeys { it.key.key.single() }

        fun familyOf(ref: RowRef): Family {
            val state = mine[ref] ?: their.getValue(ref)
            val owner = if (ref.table.name == "split_lines") splitOwners[state.owner] else state.owner
            return Family(ref.table.family, owner ?: ref.key.joinToString(SEPARATOR))
        }

        val take = mutableListOf<RowRef>()
        val conflicts = mutableListOf<Family>()
        (mine.keys + their.keys).groupBy(::familyOf).forEach { (family, refs) ->
            val differing = refs.filter { mine[it]?.signature != their[it]?.signature }
            if (differing.isEmpty()) return@forEach
            fun changed(side: Map<RowRef, RowState>) = refs.any { side[it] != null && side[it]?.signature != baseRows[it]?.signature }
            fun newest(side: Map<RowRef, RowState>) = differing.mapNotNull { side[it]?.updatedAt }.maxOrNull().orEmpty()
            val takeTheirs = when {
                !changed(their) -> false
                // Their copy changed and this one did not: it is theirs, unless it is the older of the
                // two, which is what a device restored from an old backup looks like.
                !changed(mine) && newest(their) >= newest(mine) -> true
                else -> when (choice) {
                    null -> {
                        conflicts += family
                        false
                    }
                    ConflictChoice.MINE -> false
                    ConflictChoice.THEIRS -> true
                    ConflictChoice.NEWEST -> newest(their) > newest(mine)
                }
            }
            if (takeTheirs) take += differing
        }
        if (conflicts.isNotEmpty()) {
            val described = conflicts.map {
                describe(it, session) ?: describe(it, theirSession) ?: SyncConflict(it.kind, it.id, null, null, null)
            }
            return MergeResult(described, changedRows = 0)
        }
        if (take.isEmpty()) return MergeResult(emptyList(), changedRows = 0)

        session.transaction {
            session.execute("PRAGMA defer_foreign_keys = ON")
            // The triggers and unique indexes judge single statements; a merge is only right as a
            // whole. They come back before the commit, which fails if the whole is wrong: the
            // indexes and foreign keys say so themselves, and what the triggers keep is looked at
            // here, since a trigger says nothing of rows written while it was away.
            val brokenBefore = session.ruleBreaks()
            val guards = session.query(
                "SELECT type, name, sql FROM sqlite_master " +
                    "WHERE type = 'trigger' OR (type = 'index' AND sql LIKE 'CREATE UNIQUE INDEX%')",
            )
            guards.forEach { (type, name, _) -> session.execute("DROP ${type!!.uppercase()} \"$name\"") }
            for (ref in take) {
                val table = ref.table
                val names = columns.getValue(table).joinToString(", ") { "\"$it\"" }
                val where = table.key.joinToString(" AND ") { "\"$it\" = ?" }
                val key = ref.key.toTypedArray()
                // Values travel as SQLite renders them in text; each column's affinity types them again.
                val values = if (their[ref] == null) {
                    null
                } else {
                    theirSession.query("SELECT $names FROM ${table.name} WHERE $where", *key).single().toTypedArray()
                }
                when {
                    // Only here, and theirs won: it was part of the version that lost.
                    values == null -> session.execute("DELETE FROM ${table.name} WHERE $where", *key)
                    mine[ref] == null -> session.execute(
                        "INSERT INTO ${table.name} ($names) VALUES (${values.joinToString(", ") { "?" }})",
                        *values,
                    )
                    else -> session.execute(
                        "UPDATE ${table.name} SET ${columns.getValue(table).joinToString(", ") { "\"$it\" = ?" }} WHERE $where",
                        *values,
                        *key,
                    )
                }
            }
            // Each device may have named its own default account; the later choice stands.
            session.execute(
                "UPDATE accounts SET is_default = 0 WHERE is_default = 1 AND id <> " +
                    "(SELECT id FROM accounts WHERE is_default = 1 ORDER BY updated_at DESC, id LIMIT 1)",
            )
            guards.forEach { (_, _, sql) -> session.execute(sql!!) }
            check(session.query("PRAGMA foreign_key_check").isEmpty()) { "The merged records do not fit together" }
            val broken = session.ruleBreaks() - brokenBefore
            if (broken.isNotEmpty()) throw SyncRuleBreak(broken.map { session.recordName(it) })
        }
        return MergeResult(emptyList(), changedRows = take.size)
    } finally {
        baseSession?.close()
        theirSession.close()
    }
}

private fun SqlSession.schemaVersion(): String? =
    runCatching { query("SELECT value FROM meta WHERE key = 'schema_version'").firstOrNull()?.get(0) }.getOrNull()

/**
 * The records that stand against what the shared-account triggers allow, as states rather than as
 * the single writes the triggers judge: a shared account whose members do not add up, a movement
 * whose financing does not fit its type or its account, and an expense of a shared account left
 * without the split it is consumed through. A merge is refused only for the
 * ones it brings; any already there are this database's own business.
 */
private fun SqlSession.ruleBreaks(): Set<String> =
    query(
        """
        SELECT 'account:' || a.id FROM accounts a
        WHERE a.ownership_kind = 'shared' AND a.archived_at IS NULL AND (
            (SELECT COUNT(*) FROM account_members
             WHERE account_id = a.id AND participant_kind = 'user' AND archived_at IS NULL) <> 1
            OR (SELECT COUNT(*) FROM account_members am
                JOIN people p ON p.id = am.person_id AND p.archived_at IS NULL
                WHERE am.account_id = a.id AND am.participant_kind = 'person' AND am.archived_at IS NULL) = 0
            OR (SELECT COALESCE(SUM(ownership_basis_points), 0) FROM account_members
                WHERE account_id = a.id AND archived_at IS NULL) <> 10000
            OR (SELECT COALESCE(SUM(default_expense_basis_points), 0) FROM account_members
                WHERE account_id = a.id AND archived_at IS NULL) <> 10000
        )
        UNION ALL
        SELECT 'movement:' || m.id FROM movements m
        WHERE m.archived_at IS NULL AND m.expense_funding IS NOT NULL AND (
            m.type <> 'expense'
            OR (m.expense_funding = 'shared_account' AND (
                m.shared_split_id IS NULL
                OR NOT EXISTS (SELECT 1 FROM accounts WHERE id = m.account_id AND ownership_kind = 'shared')))
            OR (m.expense_funding = 'owner'
                AND EXISTS (SELECT 1 FROM accounts WHERE id = m.account_id AND ownership_kind = 'shared'))
        )
        UNION ALL
        SELECT 'movement:' || m.id FROM movements m
        WHERE m.archived_at IS NULL AND m.expense_funding = 'shared_account'
          AND NOT EXISTS (SELECT 1 FROM splits s WHERE s.movement_id = m.id AND s.archived_at IS NULL)
        """.trimIndent(),
    ).mapTo(HashSet()) { it[0]!! }

/** What the owner knows the record in [key] (`account:id`, `movement:id`) by. */
private fun SqlSession.recordName(key: String): String {
    val (kind, id) = key.split(":", limit = 2)
    val sql = if (kind == "account") "SELECT name FROM accounts WHERE id = ?" else "SELECT coalesce(name, payee, date) FROM movements WHERE id = ?"
    return query(sql, id).firstOrNull()?.get(0) ?: id
}

/** Every merged row: its content rendered by SQLite, when it last changed, and the record it belongs with. */
private fun SqlSession.rows(columns: Map<MergeTable, List<String>>): Map<RowRef, RowState> =
    buildMap {
        for ((table, names) in columns) {
            val key = table.key.joinToString(", ") { "\"$it\"" }
            val content = names.joinToString(" || char(31) || ") { "coalesce(CAST(\"$it\" AS TEXT), char(1))" }
            val updated = if ("updated_at" in names) "updated_at" else "''"
            val owner = when {
                table.name == "split_lines" -> "split_id"
                table.familyColumn != null -> table.familyColumn
                else -> "NULL"
            }
            query("SELECT $content, $updated, $owner, $key FROM ${table.name}").forEach { row ->
                put(RowRef(table, row.drop(3).map { it!! }), RowState(row[0]!!, row[1].orEmpty(), row[2]))
            }
        }
    }

/** The conflict as [session]'s database has the record, or null when it is not there. */
private fun describe(family: Family, session: SqlSession): SyncConflict? =
    when (family.kind) {
        "movements" -> session.query("SELECT coalesce(name, payee), date, amount_cents FROM movements WHERE id = ?", family.id)
            .firstOrNull()?.let { SyncConflict(family.kind, family.id, it[0], it[1], it[2]?.toLongOrNull()) }
        "accounts", "categories", "people", "trips", "tags", "templates", "goals" ->
            session.query("SELECT name FROM ${family.kind} WHERE id = ?", family.id)
                .firstOrNull()?.let { SyncConflict(family.kind, family.id, it[0], null, null) }
        "account_contributions", "goal_allocations" ->
            session.query("SELECT date, abs(amount_cents) FROM ${family.kind} WHERE id = ?", family.id)
                .firstOrNull()?.let { SyncConflict(family.kind, family.id, null, it[0], it[1]?.toLongOrNull()) }
        else -> null
    }
