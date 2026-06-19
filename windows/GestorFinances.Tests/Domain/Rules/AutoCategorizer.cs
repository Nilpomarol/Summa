namespace GestorFinances.Domain.Rules;

public sealed record AutoCategorizeMovement(
    string? Name,
    string? Payee,
    long AmountCents,
    DateOnly Date,
    string AccountId);

public sealed record AutoCategorizeConditions(
    string? TextContains = null,
    long? AmountMinCents = null,
    long? AmountMaxCents = null,
    string? AccountId = null,
    IReadOnlySet<int>? DayOfMonthIn = null);

public sealed record AutoCategorizeAction(
    string? CategoryId = null,
    string? TripId = null);

public sealed record AutoCategorizeRule(
    string Id,
    int Priority,
    string CreatedAt,
    bool Active,
    AutoCategorizeConditions Conditions,
    AutoCategorizeAction Action);

public sealed record AutoCategorizeMatch(
    string RuleId,
    AutoCategorizeAction Action);

public static class AutoCategorizer
{
    public static AutoCategorizeMatch? FindMatch(
        AutoCategorizeMovement movement,
        IEnumerable<AutoCategorizeRule> rules) =>
        rules
            .Where(rule => rule.Active)
            .Where(rule => Matches(movement, rule.Conditions))
            .OrderByDescending(rule => rule.Priority)
            .ThenByDescending(rule => rule.CreatedAt, StringComparer.Ordinal)
            .Select(rule => new AutoCategorizeMatch(rule.Id, rule.Action))
            .FirstOrDefault();

    private static bool Matches(AutoCategorizeMovement movement, AutoCategorizeConditions conditions)
    {
        var searchableText = $"{movement.Name ?? ""} {movement.Payee ?? ""}".ToLowerInvariant();

        if (conditions.TextContains is not null &&
            !searchableText.Contains(conditions.TextContains.ToLowerInvariant(), StringComparison.Ordinal))
        {
            return false;
        }

        if (conditions.AmountMinCents is not null && movement.AmountCents < conditions.AmountMinCents)
        {
            return false;
        }

        if (conditions.AmountMaxCents is not null && movement.AmountCents > conditions.AmountMaxCents)
        {
            return false;
        }

        if (conditions.AccountId is not null && movement.AccountId != conditions.AccountId)
        {
            return false;
        }

        if (conditions.DayOfMonthIn is { Count: > 0 } && !conditions.DayOfMonthIn.Contains(movement.Date.Day))
        {
            return false;
        }

        return true;
    }
}
