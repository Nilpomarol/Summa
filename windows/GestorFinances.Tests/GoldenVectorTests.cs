using System.Text.Json;
using Dapper;
using GestorFinances.Domain.Rules;
using Microsoft.Data.Sqlite;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace GestorFinances.Tests;

[TestClass]
public sealed class GoldenVectorTests
{
    private static readonly string GoldenRoot = Path.Combine(SharedSql.RepositoryRoot, "shared", "golden");

    [TestMethod]
    public void AllGoldenFilesAreCovered()
    {
        var expected = new[]
        {
            "account_flow.json",
            "debt_balance.json",
            "debt_consumption.json",
            "duplicate_detection.json",
            "recurring_advance.json",
            "refund_actual.json",
            "split_rounding.json",
            "template_split_rescale.json"
        };
        var actual = Directory.GetFiles(GoldenRoot, "*.json")
            .Select(Path.GetFileName)
            .OrderBy(name => name)
            .ToArray();

        CollectionAssert.AreEqual(expected, actual);
    }

    [TestMethod]
    public void SplitRoundingMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("split_rounding.json").Cases())
        {
            var input = testCase.Obj("input");
            var expected = testCase.Obj("expected");
            var result = input.String("method") switch
            {
                "equal" => SplitCalculator.Equal(
                    totalCents: input.Long("total_cents"),
                    participantCount: input.Int("participant_count"),
                    payerIndex: input.Int("payer_index")),
                "percentage" => SplitCalculator.Percentage(
                    totalCents: input.Long("total_cents"),
                    basisPoints: input.IntArray("bps"),
                    payerIndex: input.Int("payer_index")),
                "exact" => SplitCalculator.Exact(
                    totalCents: input.Long("total_cents"),
                    amountsCents: input.LongArray("amounts_cents")),
                _ => throw new InvalidOperationException($"Unknown split method in {testCase.Name()}")
            };

            if (expected.OptionalBool("valid") is { } valid)
            {
                Assert.AreEqual(valid, result.Valid, testCase.Name());
            }

            if (expected.OptionalLongArray("shares_cents") is { } sharesCents)
            {
                CollectionAssert.AreEqual(sharesCents.ToArray(), result.SharesCents.ToArray(), testCase.Name());
            }

            if (expected.OptionalString("reason") is { } reason)
            {
                Assert.AreEqual(reason, result.Reason, testCase.Name());
            }
        }
    }

    [TestMethod]
    public void TemplateSplitRescaleMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("template_split_rescale.json").Cases())
        {
            var input = testCase.Obj("input");
            var result = SplitCalculator.Rescale(
                weightsCents: input.LongArray("weights_cents"),
                totalCents: input.Long("total_cents"),
                payerIndex: input.Int("payer_index"));

            var expected = testCase.Obj("expected");
            CollectionAssert.AreEqual(
                expected.LongArray("shares_cents").ToArray(),
                result.SharesCents.ToArray(),
                testCase.Name());
        }
    }

    [TestMethod]
    public void RecurringAdvanceMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("recurring_advance.json").Cases())
        {
            var input = testCase.Obj("input");
            var result = RecurringAdvancer.Advance(
                new RecurrenceRule(
                    Frequency: input.RecurrenceFrequency(),
                    DayOfMonth: input.OptionalInt("day_of_month"),
                    IntervalCount: input.OptionalLong("interval_count"),
                    CustomUnit: input.OptionalCustomUnit("custom_unit")),
                cursor: DateOnly.Parse(input.String("cursor")),
                today: DateOnly.Parse(input.String("today")));
            var expected = testCase.Obj("expected");

            CollectionAssert.AreEqual(
                expected.StringArray("due").ToArray(),
                result.DueDates.Select(date => date.ToString("yyyy-MM-dd")).ToArray(),
                testCase.Name());
            Assert.AreEqual(DateOnly.Parse(expected.String("new_cursor")), result.NewCursor, testCase.Name());
        }
    }

    [TestMethod]
    public void DuplicateDetectionMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("duplicate_detection.json").Cases())
        {
            var input = testCase.Obj("input");
            var result = DuplicateDetector.IsDuplicate(
                existing: input.Obj("existing").ToDuplicateMovement(),
                candidate: input.Obj("candidate").ToDuplicateMovement());

            Assert.AreEqual(testCase.Obj("expected").Bool("is_duplicate"), result, testCase.Name());
        }
    }

    [TestMethod]
    public void AccountFlowMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("account_flow.json").Cases())
        {
            using var connection = FreshConnection();
            var input = testCase.Obj("input");
            foreach (var account in input.Array("accounts"))
            {
                connection.InsertAccount(account);
            }

            connection.InsertPeopleReferencedBy(input.Array("movements"));
            foreach (var movement in input.Array("movements"))
            {
                connection.InsertMovement(movement);
            }

            var expected = testCase.Obj("expected");
            AssertLongMap(
                expected.Obj("current_balance_cents").LongMap(),
                connection.LongMap("SELECT account_id, current_balance_cents FROM v_account_balance"),
                testCase.Name());
            Assert.AreEqual(
                expected.Long("net_worth_cents"),
                connection.SingleLong("SELECT SUM(current_balance_cents) FROM v_account_balance"),
                testCase.Name());
        }
    }

    [TestMethod]
    public void DebtBalanceMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("debt_balance.json").Cases())
        {
            using var connection = FreshConnection();
            var input = testCase.Obj("input");
            foreach (var person in input.Array("people"))
            {
                connection.InsertPerson(person.String("id"));
            }

            connection.InsertAccountsReferencedBy(input.Array("movements"));
            foreach (var movement in input.Array("movements"))
            {
                connection.InsertMovement(movement);
            }

            foreach (var split in input.Array("splits"))
            {
                connection.InsertSplit(split);
            }

            var splitLineIndex = 0;
            foreach (var splitLine in input.Array("split_lines"))
            {
                connection.InsertSplitLine($"sl-{splitLineIndex}", splitLine);
                splitLineIndex++;
            }

            AssertLongMap(
                testCase.Obj("expected").Obj("balance_cents").LongMap(),
                connection.LongMap("SELECT person_id, balance_cents FROM v_person_balance"),
                testCase.Name());
        }
    }

    [TestMethod]
    public void DebtConsumptionMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("debt_consumption.json").Cases())
        {
            var items = testCase.Obj("input").Array("items").Select(ToDebtItem).ToList();
            var projection = DebtConsumption.Project(items);
            var expected = testCase.Obj("expected");

            Assert.AreEqual(expected.Long("balance_cents"), projection.TotalCents, testCase.Name());

            var expectedResiduals = expected.Array("residuals")
                .Select(row => (row.String("source_id"), row.Long("original_cents"), row.Long("remaining_cents")))
                .ToList();
            var actualResiduals = projection.Residuals
                .Select(row => (row.SourceId, row.OriginalCents, row.RemainingCents))
                .ToList();
            CollectionAssert.AreEqual(expectedResiduals, actualResiduals, testCase.Name());

            Assert.AreEqual(expected.Long("credit_all_cents"), projection.CreditAllCents, testCase.Name());
            Assert.AreEqual(
                expected.Long("credit_recurring_cents"),
                projection.CreditRecurringCents,
                testCase.Name());

            // The projection only ever explains the balance; v_person_balance owns the total.
            Assert.AreEqual(
                projection.TotalCents,
                projection.Residuals.Sum(row => row.RemainingCents)
                    + projection.CreditAllCents
                    + projection.CreditRecurringCents,
                $"{testCase.Name()} (residuals must reconcile with the balance)");
        }
    }

    private static DebtItem ToDebtItem(JsonElement row)
    {
        var type = row.String("type");
        var scope = row.OptionalString("scope");
        return new DebtItem(
            row.String("source_id"),
            row.String("date"),
            row.Long("effect_cents"),
            type.StartsWith("settlement", StringComparison.Ordinal),
            row.OptionalBool("is_recurring") ?? false,
            scope switch
            {
                null => null,
                "all" => SettlementScope.All,
                "recurring" => SettlementScope.Recurring,
                _ => throw new InvalidOperationException($"Unknown settlement scope: {scope}")
            });
    }

    [TestMethod]
    public void RefundActualMatchesGoldenVectors()
    {
        foreach (var testCase in Golden("refund_actual.json").Cases())
        {
            using var connection = FreshConnection();
            var input = testCase.Obj("input");
            connection.InsertAccount("acc-1", startingBalanceCents: 0);
            connection.InsertMovement(
                id: "expense-1",
                type: "expense",
                accountId: "acc-1",
                amountCents: 20_000,
                date: "2026-03-01");
            connection.InsertMovement(
                id: "refund-1",
                type: "refund",
                accountId: "acc-1",
                amountCents: input.Long("amount_cents"),
                date: "2026-03-02",
                refundsExpenseId: "expense-1",
                actualRefundCents: input.OptionalLong("actual_refund_cents"));
            var expected = testCase.Obj("expected");

            Assert.AreEqual(
                expected.Long("flow_delta_cents"),
                connection.SingleLong("SELECT delta_cents FROM v_account_flow WHERE movement_id = 'refund-1'"),
                testCase.Name());
            Assert.AreEqual(
                -expected.Long("actual_reduction_cents"),
                connection.SingleLong("SELECT amount_cents FROM v_actual_expense WHERE source_id = 'refund-1'"),
                testCase.Name());
        }
    }

    private static JsonElement Golden(string fileName) =>
        JsonDocument.Parse(File.ReadAllText(Path.Combine(GoldenRoot, fileName))).RootElement.Clone();

    private static SqliteConnection FreshConnection()
    {
        var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();
        SharedSql.ApplyBaseline(connection);
        return connection;
    }

    private static void AssertLongMap(
        IReadOnlyDictionary<string, long> expected,
        IReadOnlyDictionary<string, long> actual,
        string message)
    {
        CollectionAssert.AreEqual(
            expected.Select(pair => $"{pair.Key}={pair.Value}").OrderBy(value => value).ToArray(),
            actual.Select(pair => $"{pair.Key}={pair.Value}").OrderBy(value => value).ToArray(),
            message);
    }

}

internal static class GoldenJsonExtensions
{
    public static IEnumerable<JsonElement> Cases(this JsonElement element) => element.Array("cases");
    public static string Name(this JsonElement element) => element.String("name");
    public static JsonElement Obj(this JsonElement element, string key) => element.GetProperty(key);
    public static IEnumerable<JsonElement> Array(this JsonElement element, string key) => element.GetProperty(key).EnumerateArray();
    public static string String(this JsonElement element, string key) => element.GetProperty(key).GetString() ?? "";
    public static int Int(this JsonElement element, string key) => element.GetProperty(key).GetInt32();
    public static long Long(this JsonElement element, string key) => element.GetProperty(key).GetInt64();
    public static bool Bool(this JsonElement element, string key) => element.GetProperty(key).GetBoolean();

    public static string? OptionalString(this JsonElement element, string key) =>
        element.TryGetProperty(key, out var value) && value.ValueKind != JsonValueKind.Null
            ? value.GetString()
            : null;

    public static int? OptionalInt(this JsonElement element, string key) =>
        element.TryGetProperty(key, out var value) && value.ValueKind != JsonValueKind.Null
            ? value.GetInt32()
            : null;

    public static long? OptionalLong(this JsonElement element, string key) =>
        element.TryGetProperty(key, out var value) && value.ValueKind != JsonValueKind.Null
            ? value.GetInt64()
            : null;

    public static bool? OptionalBool(this JsonElement element, string key) =>
        element.TryGetProperty(key, out var value) && value.ValueKind != JsonValueKind.Null
            ? value.GetBoolean()
            : null;

    public static IReadOnlyList<int> IntArray(this JsonElement element, string key) =>
        element.Array(key).Select(value => value.GetInt32()).ToArray();

    public static IReadOnlyList<long> LongArray(this JsonElement element, string key) =>
        element.Array(key).Select(value => value.GetInt64()).ToArray();

    public static IReadOnlyList<string> StringArray(this JsonElement element, string key) =>
        element.Array(key).Select(value => value.GetString() ?? "").ToArray();

    public static IReadOnlyList<long>? OptionalLongArray(this JsonElement element, string key) =>
        element.TryGetProperty(key, out var value) && value.ValueKind != JsonValueKind.Null
            ? value.EnumerateArray().Select(item => item.GetInt64()).ToArray()
            : null;

    public static Dictionary<string, long> LongMap(this JsonElement element) =>
        element.EnumerateObject().ToDictionary(property => property.Name, property => property.Value.GetInt64());

    public static RecurrenceFrequency RecurrenceFrequency(this JsonElement element) =>
        element.String("frequency") switch
        {
            "weekly" => GestorFinances.Domain.Rules.RecurrenceFrequency.Weekly,
            "fortnightly" => GestorFinances.Domain.Rules.RecurrenceFrequency.Fortnightly,
            "monthly" => GestorFinances.Domain.Rules.RecurrenceFrequency.Monthly,
            "yearly" => GestorFinances.Domain.Rules.RecurrenceFrequency.Yearly,
            "custom" => GestorFinances.Domain.Rules.RecurrenceFrequency.Custom,
            var frequency => throw new InvalidOperationException($"Unknown frequency {frequency}")
        };

    public static CustomRecurrenceUnit? OptionalCustomUnit(this JsonElement element, string key) =>
        element.OptionalString(key) switch
        {
            null => null,
            "days" => CustomRecurrenceUnit.Days,
            "weeks" => CustomRecurrenceUnit.Weeks,
            "months" => CustomRecurrenceUnit.Months,
            "years" => CustomRecurrenceUnit.Years,
            var unit => throw new InvalidOperationException($"Unknown custom unit {unit}")
        };

    public static DuplicateMovement ToDuplicateMovement(this JsonElement element) =>
        new(
            AccountId: element.String("account_id"),
            AmountCents: element.Long("amount_cents"),
            Date: DateOnly.Parse(element.String("date")),
            Name: element.String("name"));
}

internal static class GoldenDatabaseExtensions
{
    public static void InsertAccountsReferencedBy(this SqliteConnection connection, IEnumerable<JsonElement> rows)
    {
        foreach (var row in rows)
        {
            if (row.OptionalString("account_id") is { } accountId)
            {
                connection.InsertAccount(accountId, startingBalanceCents: 0);
            }

            if (row.OptionalString("dest_account_id") is { } destinationAccountId)
            {
                connection.InsertAccount(destinationAccountId, startingBalanceCents: 0);
            }
        }
    }

    public static void InsertPeopleReferencedBy(this SqliteConnection connection, IEnumerable<JsonElement> rows)
    {
        foreach (var personId in rows.Select(row => row.OptionalString("person_id")).Where(id => id is not null))
        {
            connection.InsertPerson(personId!);
        }
    }

    public static void InsertAccount(this SqliteConnection connection, JsonElement row) =>
        connection.InsertAccount(row.String("id"), row.OptionalLong("starting_balance_cents") ?? 0);

    public static void InsertAccount(this SqliteConnection connection, string id, long startingBalanceCents) =>
        connection.Execute(
            """
            INSERT OR IGNORE INTO accounts
                (id, name, starting_balance_cents, type, created_at, updated_at)
            VALUES
                (@Id, @Id, @StartingBalanceCents, 'bank', @Now, @Now);
            """,
            new { Id = id, StartingBalanceCents = startingBalanceCents, Now });

    public static void InsertPerson(this SqliteConnection connection, string id) =>
        connection.Execute(
            """
            INSERT OR IGNORE INTO people
                (id, name, created_at, updated_at)
            VALUES
                (@Id, @Id, @Now, @Now);
            """,
            new { Id = id, Now });

    public static void InsertMovement(this SqliteConnection connection, JsonElement row) =>
        connection.InsertMovement(
            id: row.String("id"),
            type: row.String("type"),
            accountId: row.String("account_id"),
            amountCents: row.Long("amount_cents"),
            date: row.String("date"),
            destAccountId: row.OptionalString("dest_account_id"),
            personId: row.OptionalString("person_id"),
            settlementDirection: row.OptionalString("settlement_direction"),
            refundsExpenseId: row.OptionalString("refunds_expense_id"),
            actualRefundCents: row.OptionalLong("actual_refund_cents"),
            archivedAt: row.OptionalString("archived_at"));

    public static void InsertMovement(
        this SqliteConnection connection,
        string id,
        string type,
        string accountId,
        long amountCents,
        string date,
        string? destAccountId = null,
        string? personId = null,
        string? settlementDirection = null,
        string? refundsExpenseId = null,
        long? actualRefundCents = null,
        string? archivedAt = null) =>
        connection.Execute(
            """
            INSERT INTO movements
                (id, type, account_id, dest_account_id, amount_cents, date,
                 person_id, settlement_direction, refunds_expense_id, actual_refund_cents,
                 created_at, updated_at, archived_at)
            VALUES
                (@Id, @Type, @AccountId, @DestAccountId, @AmountCents, @Date,
                 @PersonId, @SettlementDirection, @RefundsExpenseId, @ActualRefundCents,
                 @Now, @Now, @ArchivedAt);
            """,
            new
            {
                Id = id,
                Type = type,
                AccountId = accountId,
                DestAccountId = destAccountId,
                AmountCents = amountCents,
                Date = date,
                PersonId = personId,
                SettlementDirection = settlementDirection,
                RefundsExpenseId = refundsExpenseId,
                ActualRefundCents = actualRefundCents,
                ArchivedAt = archivedAt,
                Now
            });

    public static void InsertSplit(this SqliteConnection connection, JsonElement row) =>
        connection.Execute(
            """
            INSERT INTO splits
                (id, movement_id, payer_person_id, entry_method,
                 total_amount_cents, date, created_at, updated_at, archived_at)
            VALUES
                (@Id, @MovementId, @PayerPersonId, 'equal',
                 @TotalAmountCents, @Date, @Now, @Now, @ArchivedAt);
            """,
            new
            {
                Id = row.String("id"),
                MovementId = row.OptionalString("movement_id"),
                PayerPersonId = row.OptionalString("payer_person_id"),
                TotalAmountCents = row.OptionalLong("total_amount_cents"),
                Date = row.OptionalString("date"),
                ArchivedAt = row.OptionalString("archived_at"),
                Now
            });

    public static void InsertSplitLine(this SqliteConnection connection, string id, JsonElement row) =>
        connection.Execute(
            """
            INSERT INTO split_lines
                (id, split_id, participant_kind, person_id,
                 owed_amount_cents, created_at, updated_at, archived_at)
            VALUES
                (@Id, @SplitId, @ParticipantKind, @PersonId,
                 @OwedAmountCents, @Now, @Now, @ArchivedAt);
            """,
            new
            {
                Id = id,
                SplitId = row.String("split_id"),
                ParticipantKind = row.String("participant_kind"),
                PersonId = row.OptionalString("person_id"),
                OwedAmountCents = row.Long("owed_amount_cents"),
                ArchivedAt = row.OptionalString("archived_at"),
                Now
            });

    public static Dictionary<string, long> LongMap(this SqliteConnection connection, string sql) =>
        connection.Query<(string Key, long Value)>(sql).ToDictionary(row => row.Key, row => row.Value);

    public static long SingleLong(this SqliteConnection connection, string sql) =>
        connection.QuerySingle<long>(sql);

    private const string Now = "2026-01-01T00:00:00Z";
}
