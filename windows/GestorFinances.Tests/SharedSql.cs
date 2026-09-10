using Dapper;
using Microsoft.Data.Sqlite;

namespace GestorFinances.Tests;

internal static class SharedSql
{
    private static readonly string[] ViewFiles =
    [
        "v_movement_shared.sql",
        "v_movement_summary.sql",
        "v_account_flow.sql",
        "v_account_balance.sql",
        "v_account_value.sql",
        "v_actual_expense.sql",
        "v_actual_income.sql",
        "v_person_balance.sql",
        "v_trip_actual_total.sql"
    ];

    // Views introduced by a later migration. A v6 baseline has no goals table, so these cannot be
    // applied alongside the views above; migration 012 creates them exactly as an upgrader gets them.
    private static readonly string[] MigrationViewFiles =
    [
        "v_goal_allocation.sql",
        "v_goal_progress.sql",
        "v_account_allocation.sql"
    ];

    private static readonly string[] AnalysisQueryFiles =
    [
        "goal_account_allocations.sql",
        "analysis_activity_months.sql",
        "analysis_actual_breakdown.sql",
        "analysis_actual_by_category.sql",
        "analysis_account_flow_over_time.sql",
        "analysis_income_vs_expense.sql",
        "analysis_period_totals.sql"
    ];

    public static string RepositoryRoot { get; } = FindRepositoryRoot();

    public static IReadOnlyList<string> AnalysisFiles => AnalysisQueryFiles;

    public static IReadOnlyList<string> MigrationViews =>
        MigrationViewFiles.Select(file => file[..^".sql".Length]).ToArray();

    public static void ApplyBaseline(SqliteConnection connection)
    {
        connection.Execute(ReadSharedFile("schema", "schema.sql"));
        connection.Execute("INSERT INTO meta(key,value) VALUES ('schema_version','17'),('snapshot_version','0');");
        connection.Execute(SharedAccountIntegrityTriggers());

        foreach (var viewFile in ViewFiles)
        {
            connection.Execute(ReadSharedFile("queries", viewFile));
        }

        foreach (var viewFile in MigrationViewFiles)
        {
            connection.Execute(ReadSharedFile("queries", viewFile));
        }
    }

    /// <summary>
    /// The part of the shared-account integrity migration a fresh database needs. What comes
    /// before it belongs to an upgrade alone: the structure it adds is already in schema.sql, and
    /// the guard that validates existing rows has nothing to validate. Android slices the same
    /// migration the same way when it generates the asset it applies on create.
    /// </summary>
    public static string SharedAccountIntegrityTriggers()
    {
        var migration = ReadSharedFile("migrations", "016_enforce_shared_account_integrity.sql");
        var afterGuard = migration.Split("DROP TABLE shared_account_integrity_guard;")[^1];
        return afterGuard.Split("UPDATE meta SET value = '16' WHERE key = 'schema_version';")[0].Trim();
    }

    public static string ReadAnalysisQuery(string fileName)
    {
        if (!AnalysisQueryFiles.Contains(fileName))
        {
            throw new ArgumentException($"Unknown analysis query file: {fileName}", nameof(fileName));
        }

        return ReadSharedFile("queries", fileName);
    }

    private static string ReadSharedFile(params string[] pathParts)
    {
        var parts = new[] { RepositoryRoot, "shared" }.Concat(pathParts).ToArray();
        return File.ReadAllText(Path.Combine(parts));
    }

    private static string FindRepositoryRoot()
    {
        var current = new DirectoryInfo(AppContext.BaseDirectory);

        while (current is not null)
        {
            if (File.Exists(Path.Combine(current.FullName, "shared", "migrations", "001_initial.sql")))
            {
                return current.FullName;
            }

            current = current.Parent;
        }

        throw new InvalidOperationException("Could not find repository root from the test output directory.");
    }
}
