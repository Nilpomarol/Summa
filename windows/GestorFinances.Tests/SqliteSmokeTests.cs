using Dapper;
using Microsoft.Data.Sqlite;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace GestorFinances.Tests;

[TestClass]
public sealed class SqliteSmokeTests
{
    [TestMethod]
    public void CanOpenInMemorySqliteAndMapRowsWithDapper()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();

        connection.Execute(
            """
            CREATE TABLE meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """);
        connection.Execute(
            "INSERT INTO meta (key, value) VALUES (@Key, @Value);",
            new { Key = "schema_version", Value = "1" });

        var row = connection.QuerySingle<MetaRow>(
            "SELECT key AS Key, value AS Value FROM meta WHERE key = @Key;",
            new { Key = "schema_version" });

        Assert.AreEqual("schema_version", row.Key);
        Assert.AreEqual("1", row.Value);
    }

    [TestMethod]
    public void CanApplySharedSchemaAndQueryCanonicalViews()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();

        SharedSql.ApplyBaseline(connection);

        var meta = connection.Query<MetaRow>("SELECT key AS Key, value AS Value FROM meta ORDER BY key;").ToList();
        CollectionAssert.AreEqual(
            new[] { "schema_version=14", "snapshot_version=0" },
            meta.Select(row => $"{row.Key}={row.Value}").ToArray());

        var budgetColumns = connection.Query<string>("SELECT name FROM pragma_table_info('budgets');").ToArray();
        CollectionAssert.IsSubsetOf(
            new[] { "include_trip_expenses", "include_extraordinary_expenses" },
            budgetColumns);

        var movementColumns = connection.Query<string>("SELECT name FROM pragma_table_info('movements');").ToArray();
        CollectionAssert.IsSubsetOf(new[] { "settlement_scope" }, movementColumns);

        // The v11 rebuild must leave templates able to schedule settlements, with no scratch table.
        var templateColumns = connection.Query<string>("SELECT name FROM pragma_table_info('templates');").ToArray();
        CollectionAssert.IsSubsetOf(
            new[] { "person_id", "settlement_direction", "settlement_scope" },
            templateColumns);
        Assert.AreEqual(
            0,
            connection.QuerySingle<int>(
                "SELECT COUNT(*) FROM sqlite_master WHERE name IN ('templates_new', 'templates_migration_backup');"));

        var viewNames = connection.Query<string>(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'view'
            ORDER BY name;
            """).ToArray();
        CollectionAssert.AreEqual(
            new[]
            {
                "v_account_allocation",
                "v_account_balance",
                "v_account_flow",
                "v_actual_expense",
                "v_actual_income",
                "v_goal_allocation",
                "v_goal_progress",
                "v_movement_shared",
                "v_movement_summary",
                "v_person_balance",
                "v_trip_actual_total"
            },
            viewNames);

        // Migration 012 must leave every goal view an upgrading database needs.
        CollectionAssert.IsSubsetOf(SharedSql.MigrationViews.ToArray(), viewNames);

        SeedAccountFlowScenario(connection);

        var balances = connection.Query<AccountBalanceRow>(
            """
            SELECT account_id AS AccountId, current_balance_cents AS CurrentBalanceCents
            FROM v_account_balance
            ORDER BY account_id;
            """).ToList();

        Assert.AreEqual(2, balances.Count);
        Assert.AreEqual("acc-main", balances[0].AccountId);
        Assert.AreEqual(11_500, balances[0].CurrentBalanceCents);
        Assert.AreEqual("acc-savings", balances[1].AccountId);
        Assert.AreEqual(1_000, balances[1].CurrentBalanceCents);
    }

    [TestMethod]
    public void SavingsGoalsDeriveProgressWithoutTouchingTheLedger()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();

        SharedSql.ApplyBaseline(connection);
        SeedAccountFlowScenario(connection);

        const string now = "2026-06-19T00:00:00Z";
        connection.Execute(
            """
            INSERT INTO goals
                (id, name, target_amount_cents, account_id, funding_mode, status, created_at, updated_at)
            VALUES
                ('g-dedicated', 'Car', 500000, 'acc-savings', 'dedicated_account', 'active', @Now, @Now),
                ('g-shared', 'Holiday', 80000, 'acc-main', 'allocations', 'active', @Now, @Now);

            INSERT INTO goal_allocations
                (id, goal_id, account_id, date, amount_cents, created_at, updated_at)
            VALUES
                ('al-1', 'g-shared', 'acc-main', '2026-06-05', 5000, @Now, @Now),
                ('al-2', 'g-shared', 'acc-main', '2026-06-10', -1000, @Now, @Now);
            """,
            new { Now = now });

        // A dedicated goal follows the account's canonical value; an allocation goal sums its
        // signed allocations. Neither reading changes the balances asserted above.
        Assert.AreEqual(
            1_000,
            connection.QuerySingle<long>("SELECT saved_cents FROM v_goal_progress WHERE goal_id = 'g-dedicated';"));
        Assert.AreEqual(
            4_000,
            connection.QuerySingle<long>("SELECT saved_cents FROM v_goal_progress WHERE goal_id = 'g-shared';"));
        Assert.AreEqual(
            76_000,
            connection.QuerySingle<long>("SELECT remaining_cents FROM v_goal_progress WHERE goal_id = 'g-shared';"));

        Assert.AreEqual(
            11_500,
            connection.QuerySingle<long>(
                "SELECT current_balance_cents FROM v_account_balance WHERE account_id = 'acc-main';"));
        Assert.AreEqual(
            7_500,
            connection.QuerySingle<long>(
                "SELECT unallocated_cents FROM v_account_allocation WHERE account_id = 'acc-main';"));

        // Archiving the goal releases its reservation without deleting the allocation rows.
        connection.Execute("UPDATE goals SET archived_at = @Now WHERE id = 'g-shared';", new { Now = now });
        Assert.AreEqual(
            11_500,
            connection.QuerySingle<long>(
                "SELECT unallocated_cents FROM v_account_allocation WHERE account_id = 'acc-main';"));
    }

    [TestMethod]
    public void GoalAccountReservationsExposeInvalidCrossAccountReleaseAndRetainedHistory()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();
        SharedSql.ApplyBaseline(connection);
        SeedAccountFlowScenario(connection);
        connection.Execute("""
            INSERT INTO goals (id, name, target_amount_cents, funding_mode, created_at, updated_at)
            VALUES ('goal', 'Goal', 10000, 'allocations', '2026-09-05T00:00:00Z', '2026-09-05T00:00:00Z');
            INSERT INTO goal_allocations (id, goal_id, account_id, date, amount_cents, created_at, updated_at)
            VALUES ('reserve', 'goal', 'acc-main', '2026-09-05', 10000, '2026-09-05T00:00:00Z', '2026-09-05T00:00:00Z'),
                   ('release', 'goal', 'acc-savings', '2026-09-05', -8000, '2026-09-05T00:00:00Z', '2026-09-05T00:00:00Z');
            """);
        var query = SharedSql.ReadAnalysisQuery("goal_account_allocations.sql");
        long[] Totals() => connection.Query(query, new { goal_id = "goal" }).Select(row => (long)row.allocated_cents).Order().ToArray();
        CollectionAssert.AreEqual(new long[] { -8000, 10000 }, Totals());
        connection.Execute("UPDATE goal_allocations SET account_id = 'acc-main' WHERE id = 'release';");
        CollectionAssert.AreEqual(new long[] { 2000 }, Totals());
        connection.Execute("UPDATE goal_allocations SET archived_at = '2026-09-05T01:00:00Z' WHERE id = 'reserve';");
        CollectionAssert.AreEqual(new long[] { -8000 }, Totals());
        connection.Execute("UPDATE goal_allocations SET archived_at = NULL WHERE id = 'reserve'; UPDATE goals SET archived_at = '2026-09-05T01:00:00Z';");
        CollectionAssert.AreEqual(new long[] { 2000 }, Totals());
    }

    private static void SeedAccountFlowScenario(SqliteConnection connection)
    {
        const string now = "2026-06-19T00:00:00Z";

        connection.Execute(
            """
            INSERT INTO accounts
                (id, name, starting_balance_cents, type, created_at, updated_at)
            VALUES
                ('acc-main', 'Main account', 10000, 'bank', @Now, @Now),
                ('acc-savings', 'Savings', 0, 'savings', @Now, @Now);

            INSERT INTO movements
                (id, type, amount_cents, date, account_id, dest_account_id, name, created_at, updated_at)
            VALUES
                ('mov-income', 'income', 2500, '2026-06-01', 'acc-main', NULL, 'Salary', @Now, @Now),
                ('mov-transfer', 'transfer', 1000, '2026-06-02', 'acc-main', 'acc-savings', 'Savings transfer', @Now, @Now);
            """,
            new { Now = now });
    }

    private sealed class MetaRow
    {
        public string Key { get; init; } = "";
        public string Value { get; init; } = "";
    }

    private sealed class AccountBalanceRow
    {
        public string AccountId { get; init; } = "";
        public long CurrentBalanceCents { get; init; }
    }
}
