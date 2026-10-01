namespace GestorFinances.Domain.Rules;

public enum RecurrenceFrequency
{
    Weekly,
    Fortnightly,
    Monthly,
    Yearly,
    Custom
}

public enum CustomRecurrenceUnit
{
    Days,
    Weeks,
    Months,
    Years
}

public sealed record RecurrenceRule(
    RecurrenceFrequency Frequency,
    int? DayOfMonth = null,
    long? IntervalCount = null,
    CustomRecurrenceUnit? CustomUnit = null);

public sealed record RecurrenceAdvance(
    IReadOnlyList<DateOnly> DueDates,
    DateOnly NewCursor);

public static class RecurringAdvancer
{
    public static RecurrenceAdvance Advance(RecurrenceRule rule, DateOnly cursor, DateOnly today)
    {
        var dueDates = new List<DateOnly>();
        var next = cursor;

        while (next <= today)
        {
            dueDates.Add(next);
            next = NextDate(rule, next);
        }

        return new RecurrenceAdvance(dueDates, next);
    }

    private static DateOnly NextDate(RecurrenceRule rule, DateOnly current) =>
        rule.Frequency switch
        {
            RecurrenceFrequency.Weekly => current.AddDays(7),
            RecurrenceFrequency.Fortnightly => current.AddDays(14),
            RecurrenceFrequency.Monthly => ClampDay(current.AddMonths(1), RequiredDayOfMonth(rule)),
            RecurrenceFrequency.Yearly => ClampDay(current.AddYears(1), RequiredDayOfMonth(rule)),
            RecurrenceFrequency.Custom => NextCustomDate(rule, current),
            _ => throw new ArgumentOutOfRangeException(nameof(rule), "Unknown recurrence frequency")
        };

    private static DateOnly NextCustomDate(RecurrenceRule rule, DateOnly current)
    {
        var interval = rule.IntervalCount ?? throw new ArgumentException("intervalCount is required", nameof(rule));
        if (interval <= 0)
        {
            throw new ArgumentException("intervalCount must be positive", nameof(rule));
        }

        return (rule.CustomUnit ?? throw new ArgumentException("customUnit is required", nameof(rule))) switch
        {
            CustomRecurrenceUnit.Days => current.AddDays((int)interval),
            CustomRecurrenceUnit.Weeks => current.AddDays((int)(interval * 7)),
            CustomRecurrenceUnit.Months => current.AddMonths((int)interval),
            CustomRecurrenceUnit.Years => current.AddYears((int)interval),
            _ => throw new ArgumentOutOfRangeException(nameof(rule), "Unknown custom recurrence unit")
        };
    }

    private static int RequiredDayOfMonth(RecurrenceRule rule) =>
        rule.DayOfMonth ?? throw new ArgumentException("dayOfMonth is required", nameof(rule));

    private static DateOnly ClampDay(DateOnly date, int anchorDay)
    {
        if (anchorDay is < 1 or > 31)
        {
            throw new ArgumentException("dayOfMonth must be 1..31", nameof(anchorDay));
        }

        var day = Math.Min(anchorDay, DateTime.DaysInMonth(date.Year, date.Month));
        return new DateOnly(date.Year, date.Month, day);
    }
}
