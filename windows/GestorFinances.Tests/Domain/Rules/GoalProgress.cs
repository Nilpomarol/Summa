namespace GestorFinances.Domain.Rules;

public enum GoalFundingMode
{
    DedicatedAccount,
    Allocations
}

public sealed record GoalProgressInput(
    GoalFundingMode FundingMode,
    long TargetAmountCents,
    long AccountBalanceCents,
    IReadOnlyList<long> AllocationCents,
    DateOnly? TargetDate,
    DateOnly Today);

public sealed record GoalProgressResult(
    long SavedCents,
    long RemainingCents,
    bool Reached,
    int? MonthsRemaining,
    long? MonthlyPaceCents,
    bool Overdue);

/// <summary>
/// Savings-goal progress and required monthly pace. Locked by shared/golden/goal_progress.json.
/// Reading a goal never moves the ledger: allocations reserve meaning only.
/// </summary>
public static class GoalProgress
{
    public static GoalProgressResult Evaluate(GoalProgressInput input)
    {
        var saved = input.FundingMode switch
        {
            GoalFundingMode.DedicatedAccount => input.AccountBalanceCents,
            GoalFundingMode.Allocations => input.AllocationCents.Sum(),
            _ => throw new ArgumentOutOfRangeException(nameof(input), "Unknown goal funding mode")
        };

        var remaining = Math.Max(input.TargetAmountCents - saved, 0);
        var reached = remaining == 0;

        if (input.TargetDate is not { } targetDate)
        {
            return new GoalProgressResult(saved, remaining, reached, null, null, Overdue: false);
        }

        // Calendar months are counted inclusively from today's month through the target's month,
        // so a target today or later this month leaves one period; past dates leave none.
        var monthsRemaining = targetDate < input.Today ? 0 : MonthsBetween(input.Today, targetDate) + 1;
        if (monthsRemaining < 0)
        {
            monthsRemaining = 0;
        }

        if (reached)
        {
            return new GoalProgressResult(saved, remaining, Reached: true, monthsRemaining, 0, Overdue: false);
        }

        // Rounding up keeps the target reachable: an exact division would leave a cent short.
        var pace = monthsRemaining == 0 ? remaining : CeilingDivide(remaining, monthsRemaining);
        return new GoalProgressResult(saved, remaining, Reached: false, monthsRemaining, pace, monthsRemaining == 0);
    }

    private static int MonthsBetween(DateOnly from, DateOnly to) =>
        ((to.Year * 12) + to.Month) - ((from.Year * 12) + from.Month);

    private static long CeilingDivide(long value, int divisor) =>
        (value + divisor - 1) / divisor;
}
