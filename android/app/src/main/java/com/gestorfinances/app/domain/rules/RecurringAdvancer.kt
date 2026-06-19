package com.gestorfinances.app.domain.rules

import java.time.LocalDate
import java.time.YearMonth

enum class RecurrenceFrequency {
    WEEKLY,
    FORTNIGHTLY,
    MONTHLY,
    YEARLY,
    CUSTOM,
}

enum class CustomRecurrenceUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val dayOfMonth: Int? = null,
    val intervalCount: Long? = null,
    val customUnit: CustomRecurrenceUnit? = null,
)

data class RecurrenceAdvance(
    val dueDates: List<LocalDate>,
    val newCursor: LocalDate,
)

object RecurringAdvancer {
    fun advance(
        rule: RecurrenceRule,
        cursor: LocalDate,
        today: LocalDate,
    ): RecurrenceAdvance {
        val dueDates = mutableListOf<LocalDate>()
        var next = cursor

        while (!next.isAfter(today)) {
            dueDates += next
            next = nextDate(rule, next)
        }

        return RecurrenceAdvance(dueDates = dueDates, newCursor = next)
    }

    private fun nextDate(rule: RecurrenceRule, current: LocalDate): LocalDate =
        when (rule.frequency) {
            RecurrenceFrequency.WEEKLY -> current.plusDays(7)
            RecurrenceFrequency.FORTNIGHTLY -> current.plusDays(14)
            RecurrenceFrequency.MONTHLY -> {
                val anchorDay = requireNotNull(rule.dayOfMonth) { "dayOfMonth is required" }
                clampDay(current.plusMonths(1), anchorDay)
            }
            RecurrenceFrequency.YEARLY -> {
                val anchorDay = requireNotNull(rule.dayOfMonth) { "dayOfMonth is required" }
                clampDay(current.plusYears(1), anchorDay)
            }
            RecurrenceFrequency.CUSTOM -> nextCustomDate(rule, current)
        }

    private fun nextCustomDate(rule: RecurrenceRule, current: LocalDate): LocalDate {
        val interval = requireNotNull(rule.intervalCount) { "intervalCount is required" }
        require(interval > 0) { "intervalCount must be positive" }

        return when (requireNotNull(rule.customUnit) { "customUnit is required" }) {
            CustomRecurrenceUnit.DAYS -> current.plusDays(interval)
            CustomRecurrenceUnit.WEEKS -> current.plusWeeks(interval)
            CustomRecurrenceUnit.MONTHS -> current.plusMonths(interval)
            CustomRecurrenceUnit.YEARS -> current.plusYears(interval)
        }
    }

    private fun clampDay(date: LocalDate, anchorDay: Int): LocalDate {
        require(anchorDay in 1..31) { "dayOfMonth must be 1..31" }
        val yearMonth = YearMonth.from(date)
        return yearMonth.atDay(anchorDay.coerceAtMost(yearMonth.lengthOfMonth()))
    }
}
