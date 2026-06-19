using System.Text.RegularExpressions;

namespace GestorFinances.Domain.Rules;

public sealed record DuplicateMovement(
    string AccountId,
    long AmountCents,
    DateOnly Date,
    string Name);

public static partial class DuplicateDetector
{
    public static bool IsDuplicate(
        DuplicateMovement existing,
        DuplicateMovement candidate,
        int dateWindowDays = 1) =>
        existing.AccountId == candidate.AccountId &&
        existing.AmountCents == candidate.AmountCents &&
        Math.Abs(candidate.Date.DayNumber - existing.Date.DayNumber) <= dateWindowDays &&
        Normalize(existing.Name) == Normalize(candidate.Name);

    private static string Normalize(string value) =>
        Whitespace().Replace(value.Trim().ToLowerInvariant(), " ");

    [GeneratedRegex("\\s+")]
    private static partial Regex Whitespace();
}
