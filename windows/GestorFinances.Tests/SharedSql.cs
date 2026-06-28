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
        "v_actual_expense.sql",
        "v_actual_income.sql",
        "v_person_balance.sql"
    ];

    private static readonly string[] AnalysisQueryFiles =
    [
        "analysis_actual_breakdown.sql",
        "analysis_actual_by_category.sql",
        "analysis_account_flow_over_time.sql",
        "analysis_income_vs_expense.sql",
        "analysis_period_totals.sql",
        "analysis_category_trends.sql",
        "analysis_largest_expenses.sql",
        "analysis_net_worth_over_time.sql",
        "analysis_top_merchants.sql"
    ];

    public static string RepositoryRoot { get; } = FindRepositoryRoot();

    public static IReadOnlyList<string> AnalysisFiles => AnalysisQueryFiles;

    public static void ApplyBaseline(SqliteConnection connection)
    {
        connection.Execute(ReadSharedFile("migrations", "001_initial.sql"));

        foreach (var viewFile in ViewFiles)
        {
            connection.Execute(ReadSharedFile("queries", viewFile));
        }
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
