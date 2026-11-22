package com.gestorfinances.app.data.backup

/** What a database file must contain to be accepted as a backup of this app. */
object BackupSchemaShape {
    // Kept in sync with shared/schema/schema.sql (tables) and shared/queries/*.sql (views).
    // See AndroidBackupDatabaseInspectorTest for a drift guard against GestorDatabase.Schema.create().
    val REQUIRED_APP_OBJECTS = setOf(
        "meta",
        "accounts",
        "account_members",
        "account_contributions",
        "categories",
        "people",
        "trips",
        "tags",
        "templates",
        "budgets",
        "budget_versions",
        "goals",
        "goal_allocations",
        "import_batches",
        "movements",
        "splits",
        "split_lines",
        "v_account_balance",
        "v_account_value",
        "v_actual_expense",
        "v_actual_income",
        "v_person_balance",
        "v_account_flow",
        "v_movement_shared",
        "v_movement_summary",
        "v_trip_actual_total",
        "v_goal_allocation",
        "v_goal_progress",
        "v_account_allocation",
    )

    /** Objects newer than the oldest backups still restorable, by the schema version that added them. */
    val ADDED_IN_SCHEMA_VERSION = mapOf("budget_versions" to 22L)

    /**
     * Whether a database at [schemaVersion] holding [found] has every object it should: a
     * backup from before an object existed gains it when it is migrated after the restore.
     */
    fun hasRequiredObjects(found: Set<String>, schemaVersion: Long?): Boolean =
        REQUIRED_APP_OBJECTS.all { name ->
            name in found || (schemaVersion != null && schemaVersion < (ADDED_IN_SCHEMA_VERSION[name] ?: 0L))
        }

    val REQUIRED_TABLE_COLUMNS = mapOf(
        "meta" to setOf("key", "value"),
        "accounts" to setOf("id", "name", "starting_balance_cents", "ownership_kind", "archived_at"),
        "account_members" to setOf("id", "account_id", "participant_kind", "ownership_basis_points", "default_expense_basis_points", "archived_at"),
        "account_contributions" to setOf("id", "shared_account_id", "contributor_kind", "amount_cents", "date", "archived_at"),
        "categories" to setOf("id", "name", "kind", "archived_at"),
        "movements" to setOf("id", "type", "amount_cents", "date", "expense_funding", "archived_at"),
        "templates" to setOf("id", "type", "account_id", "status", "archived_at"),
        "budgets" to setOf("id", "scope", "archived_at"),
        "goals" to setOf("id", "name", "target_amount_cents", "funding_mode", "status", "archived_at"),
        "goal_allocations" to setOf("id", "goal_id", "account_id", "date", "amount_cents", "archived_at"),
        "import_batches" to setOf("id", "archived_at"),
    )
}
