package com.gestorfinances.app.domain.rules

import java.time.LocalDate
import org.junit.Test

/**
 * Regression test for audit finding F3: `RecurringAdvancer.advance`'s while-loop had no upper
 * bound. A template whose cursor is decades stale (e.g. a daily custom recurrence never
 * confirmed) would previously accumulate an unbounded list on the IO dispatcher; now it fails
 * fast instead.
 */
class RecurringAdvancerTest {

    @Test(expected = IllegalArgumentException::class)
    fun `advance throws instead of hanging when occurrences exceed the ceiling`() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.CUSTOM,
            customUnit = CustomRecurrenceUnit.DAYS,
            intervalCount = 1L,
        )

        // ~36 years of daily occurrences (~13,000) comfortably exceeds the 10,000 ceiling.
        RecurringAdvancer.advance(
            rule,
            cursor = LocalDate.of(1990, 1, 1),
            today = LocalDate.of(2026, 7, 3),
        )
    }

    @Test
    fun `advance still returns ordinary occurrence counts under the ceiling`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, dayOfMonth = 1)

        val result = RecurringAdvancer.advance(
            rule,
            cursor = LocalDate.of(2026, 1, 1),
            today = LocalDate.of(2026, 6, 1),
        )

        assert(result.dueDates.size == 6) { "expected 6 monthly occurrences, got ${result.dueDates.size}" }
    }

    @Test
    fun `isImmediatePriorOccurrence is true for a clean one-step-back monthly date`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, dayOfMonth = 1)

        val result = RecurringAdvancer.isImmediatePriorOccurrence(
            rule,
            occurrenceDate = LocalDate.of(2026, 1, 1),
            nextDueDate = LocalDate.of(2026, 2, 1),
        )

        assert(result) { "expected the January occurrence to be the immediate prior step to February" }
    }

    @Test
    fun `isImmediatePriorOccurrence is false for an older occurrence two steps back`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, dayOfMonth = 1)

        val result = RecurringAdvancer.isImmediatePriorOccurrence(
            rule,
            occurrenceDate = LocalDate.of(2026, 1, 1),
            nextDueDate = LocalDate.of(2026, 3, 1),
        )

        assert(!result) { "a two-step-back occurrence must not be treated as the immediate prior one" }
    }

    @Test
    fun `isImmediatePriorOccurrence is false when a skip happened after the occurrence`() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY)

        // Confirmed on Jan 5, but the cursor was later skipped past Jan 12 to Jan 19 -- Jan 5 is
        // no longer the occurrence immediately preceding the current cursor.
        val result = RecurringAdvancer.isImmediatePriorOccurrence(
            rule,
            occurrenceDate = LocalDate.of(2026, 1, 5),
            nextDueDate = LocalDate.of(2026, 1, 19),
        )

        assert(!result) { "a skip interleaved after the occurrence must break the immediate-prior check" }
    }
}
