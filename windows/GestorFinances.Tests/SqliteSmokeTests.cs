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
            new[] { "schema_version=5", "snapshot_version=0" },
            meta.Select(row => $"{row.Key}={row.Value}").ToArray());

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
                "v_account_balance",
                "v_account_flow",
                "v_actual_expense",
                "v_actual_income",
                "v_movement_shared",
                "v_movement_summary",
                "v_person_balance",
                "v_trip_actual_total"
            },
            viewNames);

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
