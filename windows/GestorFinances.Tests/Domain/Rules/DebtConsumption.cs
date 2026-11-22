namespace GestorFinances.Domain.Rules;

/// <summary>Which debt a settlement is allowed to consume.</summary>
public enum SettlementScope
{
    All,
    Recurring
}

/// <summary>
/// One row of a person's balance breakdown, in v_person_balance signs: a positive
/// <paramref name="EffectCents"/> means the person owes the user more. <paramref name="IsRecurring"/>
/// marks debt originating from a recurring template, which is what a Recurring-scoped settlement is
/// allowed to consume. <paramref name="Scope"/> is set on settlements only.
/// </summary>
public sealed record DebtItem(
    string SourceId,
    string Date,
    long EffectCents,
    bool IsSettlement,
    bool IsRecurring,
    SettlementScope? Scope);

/// <summary>A debt item that settlements have not fully consumed.</summary>
public sealed record DebtResidual(string SourceId, long OriginalCents, long RemainingCents)
{
    /// <summary>True when settlements consumed part but not all of this item.</summary>
    public bool IsPartial => RemainingCents != OriginalCents;
}

/// <summary>
/// What still explains a person's balance, and the credit no debt has absorbed yet.
/// <paramref name="TotalCents"/> is the sum of every item's effect, exactly what v_person_balance
/// derives; residuals plus both credit buckets always reconcile to it.
/// </summary>
public sealed record DebtProjection(
    IReadOnlyList<DebtResidual> Residuals,
    long CreditAllCents,
    long CreditRecurringCents,
    long TotalCents);

/// <summary>
/// Explains a person's balance by consuming debt chronologically, oldest first.
///
/// A settlement applies only to eligible debt that already exists on its date, so items are folded
/// in date order with debt ranked before settlements on the same day. Whatever a settlement cannot
/// spend stays as directional credit in its own scope bucket and is absorbed by later debt, which
/// keeps a recurring-scoped overpayment from silently paying off unrelated expenses.
///
/// Locked by shared/golden/debt_consumption.json.
/// </summary>
public static class DebtConsumption
{
    public static DebtProjection Project(IReadOnlyList<DebtItem> items)
    {
        var ordered = items
            .OrderBy(item => item.Date, StringComparer.Ordinal)
            .ThenBy(item => item.IsSettlement ? 1 : 0)
            .ThenBy(item => item.SourceId, StringComparer.Ordinal)
            .ToList();

        var open = new List<OpenItem>();
        var creditAll = 0L;
        var creditRecurring = 0L;

        foreach (var item in ordered)
        {
            if (item.EffectCents == 0)
            {
                continue;
            }

            if (item.IsSettlement)
            {
                var scope = item.Scope ?? throw new InvalidOperationException(
                    $"Settlement {item.SourceId} has no scope.");
                var power = Math.Abs(item.EffectCents);
                var direction = Math.Sign(item.EffectCents);

                foreach (var candidate in open)
                {
                    if (power == 0)
                    {
                        break;
                    }

                    if (Math.Sign(candidate.Remaining) != -direction)
                    {
                        continue;
                    }

                    if (scope == SettlementScope.Recurring && !candidate.IsRecurring)
                    {
                        continue;
                    }

                    var take = Math.Min(power, Math.Abs(candidate.Remaining));
                    candidate.Remaining += direction * take;
                    power -= take;
                }

                if (power > 0)
                {
                    var leftover = direction * power;
                    if (scope == SettlementScope.All)
                    {
                        creditAll += leftover;
                    }
                    else
                    {
                        creditRecurring += leftover;
                    }
                }
            }
            else
            {
                var remaining = item.EffectCents;

                // Spend the narrower recurring credit first so it cannot be stranded by an
                // 'all' credit that any later item could have absorbed instead.
                if (item.IsRecurring)
                {
                    (remaining, creditRecurring) = Absorb(remaining, creditRecurring);
                }

                (remaining, creditAll) = Absorb(remaining, creditAll);

                if (remaining != 0)
                {
                    open.Add(new OpenItem(item.SourceId, item.EffectCents, remaining, item.IsRecurring));
                }
            }
        }

        var residuals = open
            .Where(row => row.Remaining != 0)
            .Select(row => new DebtResidual(row.SourceId, row.OriginalCents, row.Remaining))
            .ToList();

        return new DebtProjection(
            residuals,
            creditAll,
            creditRecurring,
            items.Sum(item => item.EffectCents));
    }

    /// <summary>Applies opposite-signed credit to an amount, returning both after the transfer.</summary>
    private static (long Amount, long Credit) Absorb(long amount, long credit)
    {
        if (amount == 0 || credit == 0 || Math.Sign(credit) != -Math.Sign(amount))
        {
            return (amount, credit);
        }

        var take = Math.Min(Math.Abs(amount), Math.Abs(credit));
        return (amount + Math.Sign(credit) * take, credit - Math.Sign(credit) * take);
    }

    private sealed class OpenItem(string sourceId, long originalCents, long remaining, bool isRecurring)
    {
        public string SourceId { get; } = sourceId;

        public long OriginalCents { get; } = originalCents;

        public long Remaining { get; set; } = remaining;

        public bool IsRecurring { get; } = isRecurring;
    }
}
