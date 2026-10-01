using Dapper;
using Microsoft.Data.Sqlite;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace GestorFinances.Tests;

/// <summary>
/// Executes the shared analysis queries verbatim against a fixed dataset and asserts the
/// results, so the Windows runner validates the same analysis contract the Android repository
/// tests and tools/validate_shared_sql.py assert. The fixture mirrors that validator, keeping
/// Python, Android and Windows in agreement on the canonical numbers.
/// </summary>
[TestClass]
public sealed class AnalysisQueryTests
{
    private const string Now = "2026-06-01T00:00:00Z";
    private const string From = "2026-06-01";
    private const string To = "2026-07-01";

    [TestMethod]
    public void EveryParameterizedQueryFileIsCovered()
    {
        // Guards against adding a shared analysis query that the harness does not wire in:
        // the files on disk must match the AnalysisFiles list this suite executes.
        var onDisk = Directory
            .GetFiles(Path.Combine(SharedSql.RepositoryRoot, "shared", "queries"), "*.sql")
            .Where(path => !Path.GetFileName(path).StartsWith("v_", StringComparison.Ordinal))
            .Select(Path.GetFileName)
            .ToArray();

        CollectionAssert.AreEquivalent(onDisk, SharedSql.AnalysisFiles.ToArray());
    }

    [TestMethod]
    public void ActivityMonthsListsActiveLedgerMonths()
    {
        using var connection = SeededConnection();

        var months = connection.Query<string>(
            SharedSql.ReadAnalysisQuery("analysis_activity_months.sql")).ToArray();

        CollectionAssert.AreEqual(new[] { "2026-06" }, months);
    }

    [TestMethod]
    public void PeriodTotalsMatchFixedDataset()
    {
        using var connection = SeededConnection();

        var row = connection.QuerySingle(
            SharedSql.ReadAnalysisQuery("analysis_period_totals.sql"),
            new
            {
                from_date = From,
                to_date = To,
                one_time_mode = "include",
                category_nature = (string?)null,
                account_id = (string?)null,
                category_id = (string?)null
            });

        Assert.AreEqual(258_500L, (long)row.net_worth_cents);
        Assert.AreEqual(250_000L, (long)row.actual_income_cents);
        Assert.AreEqual(6_500L, (long)row.actual_expense_cents);
        Assert.AreEqual(243_500L, (long)row.net_actual_cents);
        Assert.AreEqual(243_500L, (long)row.account_flow_cents);
        Assert.AreEqual(9_740L, (long)row.savings_rate_basis_points);
    }

    [TestMethod]
    public void ActualByCategoryHonoursNatureAndOneTimeFilters()
    {
        using var connection = SeededConnection();

        var included = ByCategory(connection, "include", categoryNature: null);
        Assert.AreEqual((0L, 250_000L, 250_000L), included["salary"]);
        Assert.AreEqual((5_000L, 0L, -5_000L), included["electronics"]);
        Assert.AreEqual((1_500L, 0L, -1_500L), included["groceries"]); // 2000 expense - 500 refund

        var variable = ByCategory(connection, "include", categoryNature: "variable");
        CollectionAssert.AreEquivalent(new[] { "electronics", "groceries" }, variable.Keys.ToArray());

        var oneTimeOnly = ByCategory(connection, "only", categoryNature: null);
        CollectionAssert.AreEquivalent(new[] { "electronics" }, oneTimeOnly.Keys.ToArray());
    }

    [TestMethod]
    public void ActualBreakdownCanGroupTripsAsPresentationBlocks()
    {
        using var connection = SeededConnection();

        var groupedRows = connection.Query(
            SharedSql.ReadAnalysisQuery("analysis_actual_breakdown.sql"),
            new
            {
                from_date = From,
                to_date = To,
                one_time_mode = "include",
                category_nature = (string?)null,
                account_id = (string?)null,
                category_id = (string?)null,
                group_trips = 1
            }).ToList();

        var tripRow = groupedRows.Single(r => (string)r.row_kind == "trip");
        Assert.AreEqual("mallorca", (string)tripRow.trip_id);
        Assert.AreEqual("Mallorca", (string)tripRow.trip_name);
        Assert.AreEqual(5_000L, (long)tripRow.expense_cents);
        Assert.IsFalse(groupedRows.Any(r => (string?)r.category_id == "electronics"));

        var ungroupedRows = connection.Query(
            SharedSql.ReadAnalysisQuery("analysis_actual_breakdown.sql"),
            new
            {
                from_date = From,
                to_date = To,
                one_time_mode = "include",
                category_nature = (string?)null,
                account_id = (string?)null,
                category_id = (string?)null,
                group_trips = 0
            }).ToList();

        Assert.IsFalse(ungroupedRows.Any(r => (string)r.row_kind == "trip"));
        Assert.AreEqual(5_000L, (long)ungroupedRows.Single(r => (string?)r.category_id == "electronics").expense_cents);
    }

    [TestMethod]
    public void IncomeVsExpenseBucketsByMonth()
    {
        using var connection = SeededConnection();

        var rows = connection.Query(
            SharedSql.ReadAnalysisQuery("analysis_income_vs_expense.sql"),
            new
            {
                from_date = From,
                to_date = To,
                one_time_mode = "include",
                category_nature = (string?)null,
                account_id = (string?)null,
                category_id = (string?)null,
                bucket = "month"
            }).ToList();

        Assert.AreEqual(1, rows.Count);
        Assert.AreEqual("2026-06", (string)rows[0].bucket);
        Assert.AreEqual(250_000L, (long)rows[0].income_cents);
        Assert.AreEqual(6_500L, (long)rows[0].expense_cents);
        Assert.AreEqual(243_500L, (long)rows[0].net_cents);
    }

    [TestMethod]
    public void AccountFlowOverTimeSplitsTransferLegsAndNetsTheBucket()
    {
        using var connection = SeededConnection();

        var rows = connection.Query(
            SharedSql.ReadAnalysisQuery("analysis_account_flow_over_time.sql"),
            new { from_date = From, to_date = To, account_id = (string?)null, bucket = "day" }).ToList();

        var byKey = rows.ToDictionary(r => ((string)r.bucket, (string)r.account_id), r => (long)r.delta_cents);
        Assert.AreEqual(-10_000L, byKey[("2026-06-15", "checking")]);
        Assert.AreEqual(10_000L, byKey[("2026-06-15", "savings")]);

        var transferBucket = rows.Where(r => (string)r.bucket == "2026-06-15").ToList();
        Assert.IsTrue(transferBucket.All(r => (long)r.bucket_delta_cents == 0));
    }

    [TestMethod]
    public void ActualByCategoryHonoursAccountAndCategoryFilters()
    {
        using var connection = SeededConnection();

        // Every actual row lives on 'checking', so 'savings' narrows to nothing.
        Assert.AreEqual(0, ByCategory(connection, "include", null, accountId: "savings", categoryId: null).Count);

        var groceriesOnly = ByCategory(connection, "include", null, accountId: null, categoryId: "groceries");
        CollectionAssert.AreEquivalent(new[] { "groceries" }, groceriesOnly.Keys.ToArray());
        Assert.AreEqual((1_500L, 0L, -1_500L), groceriesOnly["groceries"]);
    }

    [TestMethod]
    public void ActualByCategoryFilterRollsUpChildrenOfAParent()
    {
        using var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();
        SharedSql.ApplyBaseline(connection);
        connection.Execute(
            """
            INSERT INTO accounts
                (id, name, starting_balance_cents, type, display_order, created_at, updated_at)
            VALUES
                ('checking', 'Checking', 0, 'bank', 0, @Now, @Now);

            INSERT INTO categories
                (id, name, kind, nature, parent_id, display_order, created_at, updated_at)
            VALUES
                ('food', 'Food', 'expense', 'variable', NULL, 0, @Now, @Now),
                ('restaurants', 'Restaurants', 'expense', 'variable', 'food', 1, @Now, @Now);

            INSERT INTO movements
                (id, type, amount_cents, date, account_id, name, is_one_time, category_id, created_at, updated_at)
            VALUES
                ('food-own', 'expense', 1000, '2026-06-03', 'checking', 'Food own', 0, 'food', @Now, @Now),
                ('rest-1', 'expense', 3000, '2026-06-04', 'checking', 'Dinner', 0, 'restaurants', @Now, @Now);
            """,
            new { Now });

        // Filtering by the parent 'food' (a container) includes its child 'restaurants' rows.
        // The query still returns one row per category_id; rolling them into a single parent
        // total is a presentation concern handled in app code.
        var byParent = ByCategory(connection, "include", null, accountId: null, categoryId: "food");
        CollectionAssert.AreEquivalent(new[] { "food", "restaurants" }, byParent.Keys.ToArray());
        Assert.AreEqual((1_000L, 0L, -1_000L), byParent["food"]);
        Assert.AreEqual((3_000L, 0L, -3_000L), byParent["restaurants"]);

        // Filtering by the leaf child stays scoped to itself.
        var byChild = ByCategory(connection, "include", null, accountId: null, categoryId: "restaurants");
        CollectionAssert.AreEquivalent(new[] { "restaurants" }, byChild.Keys.ToArray());
    }

    private static Dictionary<string, (long Expense, long Income, long Net)> ByCategory(
        SqliteConnection connection,
        string oneTimeMode,
        string? categoryNature)
    {
        return ByCategory(connection, oneTimeMode, categoryNature, accountId: null, categoryId: null);
    }

    private static Dictionary<string, (long Expense, long Income, long Net)> ByCategory(
        SqliteConnection connection,
        string oneTimeMode,
        string? categoryNature,
        string? accountId,
        string? categoryId)
    {
        return connection.Query(
                SharedSql.ReadAnalysisQuery("analysis_actual_by_category.sql"),
                new
                {
                    from_date = From,
                    to_date = To,
                    one_time_mode = oneTimeMode,
                    category_nature = categoryNature,
                    account_id = accountId,
                    category_id = categoryId
                })
            .Where(r => r.category_id != null)
            .ToDictionary(
                r => (string)r.category_id,
                r => ((long)r.expense_cents, (long)r.income_cents, (long)r.net_cents));
    }

    private static SqliteConnection SeededConnection()
    {
        var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();
        SharedSql.ApplyBaseline(connection);
        SeedFixture(connection);
        return connection;
    }

    private static void SeedFixture(SqliteConnection connection)
    {
        connection.Execute(
            """
            INSERT INTO accounts
                (id, name, starting_balance_cents, type, display_order, created_at, updated_at)
            VALUES
                ('checking', 'Checking', 10000, 'bank', 0, @Now, @Now),
                ('savings', 'Savings', 5000, 'savings', 1, @Now, @Now);

            INSERT INTO categories
                (id, name, kind, nature, display_order, created_at, updated_at)
            VALUES
                ('salary', 'Salary', 'income', 'fixed', 0, @Now, @Now),
                ('groceries', 'Groceries', 'expense', 'variable', 1, @Now, @Now),
                ('electronics', 'Electronics', 'expense', 'variable', 2, @Now, @Now);

            INSERT INTO trips
                (id, name, type, status, start_date, end_date, created_at, updated_at)
            VALUES
                ('mallorca', 'Mallorca', 'trip', 'active', '2026-06-01', '2026-06-30', @Now, @Now);

            INSERT INTO movements
                (id, type, amount_cents, date, account_id, dest_account_id, name, is_one_time, category_id, trip_id,
                 refunds_expense_id, created_at, updated_at)
            VALUES
                ('salary-june', 'income', 250000, '2026-06-01', 'checking', NULL, 'Salary', 0, 'salary', NULL,
                 NULL, @Now, @Now),
                ('groceries-1', 'expense', 2000, '2026-06-05', 'checking', NULL, 'Groceries', 0, 'groceries', NULL,
                 NULL, @Now, @Now),
                ('laptop', 'expense', 5000, '2026-06-10', 'checking', NULL, 'Laptop', 1, 'electronics', 'mallorca',
                 NULL, @Now, @Now),
                ('grocery-refund', 'refund', 500, '2026-06-12', 'checking', NULL, 'Refund', 0, 'electronics', NULL,
                 'groceries-1', @Now, @Now),
                ('to-savings', 'transfer', 10000, '2026-06-15', 'checking', 'savings', 'Savings transfer', 0, NULL, NULL,
                 NULL, @Now, @Now);
            """,
            new { Now });
    }
}
