namespace GestorFinances.Domain.Rules;

public sealed record SplitCalculation(
    bool Valid,
    IReadOnlyList<long> SharesCents,
    string? Reason = null);

public static class SplitCalculator
{
    public static SplitCalculation Equal(long totalCents, int participantCount, int payerIndex)
    {
        var validationError = ValidateCommon(totalCents, participantCount, payerIndex);
        if (validationError is not null)
        {
            return validationError;
        }

        var baseShare = totalCents / participantCount;
        var remainder = totalCents % participantCount;
        var shares = Enumerable.Repeat(baseShare, participantCount).ToArray();
        shares[payerIndex] += remainder;
        return new SplitCalculation(true, shares);
    }

    public static SplitCalculation Percentage(long totalCents, IReadOnlyList<int> basisPoints, int payerIndex)
    {
        var validationError = ValidateCommon(totalCents, basisPoints.Count, payerIndex);
        if (validationError is not null)
        {
            return validationError;
        }

        if (basisPoints.Any(value => value < 0))
        {
            return new SplitCalculation(false, Array.Empty<long>(), "basis points must be non-negative");
        }

        var totalBasisPoints = basisPoints.Sum();
        if (totalBasisPoints != 10_000)
        {
            return new SplitCalculation(false, Array.Empty<long>(), $"basis points sum {totalBasisPoints} != 10000");
        }

        var shares = basisPoints.Select(value => totalCents * value / 10_000).ToArray();
        var leftover = totalCents - shares.Sum();
        shares[payerIndex] += leftover;
        return new SplitCalculation(true, shares);
    }

    /// <summary>
    /// Rescales <paramref name="weightsCents"/> (e.g. a template's stored split_config line
    /// amounts) proportionally to <paramref name="totalCents"/> when the two no longer match — a
    /// recurring occurrence's confirmed amount can differ from the split's own stored sum
    /// (variable-amount templates, an amount edited after detection, or a NEW-detected candidate
    /// whose split came from a single source movement while its amount is a group median). Floors
    /// each share, then adds the leftover to the payer's line, same convention as
    /// <see cref="Percentage"/>. Identity when the weights already sum to
    /// <paramref name="totalCents"/>. See shared/golden/template_split_rescale.json.
    /// </summary>
    public static SplitCalculation Rescale(IReadOnlyList<long> weightsCents, long totalCents, int payerIndex)
    {
        var validationError = ValidateCommon(totalCents, weightsCents.Count, payerIndex);
        if (validationError is not null)
        {
            return validationError;
        }

        if (weightsCents.Any(value => value < 0))
        {
            return new SplitCalculation(false, Array.Empty<long>(), "weights must be non-negative");
        }

        var weightSum = weightsCents.Sum();
        var shares = weightSum == 0L
            ? new long[weightsCents.Count]
            : weightsCents.Select(weight => totalCents * weight / weightSum).ToArray();
        var leftover = totalCents - shares.Sum();
        shares[payerIndex] += leftover;
        return new SplitCalculation(true, shares);
    }

    public static SplitCalculation Exact(long totalCents, IReadOnlyList<long> amountsCents)
    {
        if (totalCents <= 0)
        {
            return new SplitCalculation(false, Array.Empty<long>(), "total must be positive");
        }

        if (amountsCents.Any(value => value < 0))
        {
            return new SplitCalculation(false, Array.Empty<long>(), "amounts must be non-negative");
        }

        var sum = amountsCents.Sum();
        if (sum != totalCents)
        {
            return new SplitCalculation(false, Array.Empty<long>(), $"sum {sum} != total {totalCents}");
        }

        return new SplitCalculation(true, amountsCents.ToArray());
    }

    private static SplitCalculation? ValidateCommon(long totalCents, int participantCount, int payerIndex)
    {
        if (totalCents <= 0)
        {
            return new SplitCalculation(false, Array.Empty<long>(), "total must be positive");
        }

        if (participantCount <= 0)
        {
            return new SplitCalculation(false, Array.Empty<long>(), "participants required");
        }

        if (payerIndex < 0 || payerIndex >= participantCount)
        {
            return new SplitCalculation(false, Array.Empty<long>(), "payer index out of range");
        }

        return null;
    }
}
