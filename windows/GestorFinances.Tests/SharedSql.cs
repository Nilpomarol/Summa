using Dapper;
using Microsoft.Data.Sqlite;

namespace GestorFinances.Tests;

internal static class SharedSql
{
    private static readonly string[] ViewFiles =
    [
        "v_movement_shared.sql",
        "v_account_flow.sql",
        "v_account_balance.sql",
        "v_actual_expense.sql",
        "v_actual_income.sql",
        "v_person_balance.sql"
    ];

    public static string RepositoryRoot { get; } = FindRepositoryRoot();

    public static void ApplyBaseline(SqliteConnection connection)
    {
        connection.Execute(ReadSharedFile("migrations", "001_initial.sql"));

        foreach (var viewFile in ViewFiles)
        {
            connection.Execute(ReadSharedFile("queries", viewFile));
        }
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
