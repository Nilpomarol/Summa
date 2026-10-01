package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.MovementType
import java.time.LocalDate

internal val primaryMovementFilterTypes = listOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.TRANSFER,
)

internal enum class MovementPeriodPreset(val labelRes: Int) {
    ALL(R.string.movement_filter_period_all),
    THIS_MONTH(R.string.movement_filter_this_month),
    PREVIOUS_MONTH(R.string.movement_filter_previous_month),
    THIS_YEAR(R.string.movement_filter_this_year),
    CUSTOM(R.string.movement_filter_custom_range_title),
}

internal fun MovementFilters.withPeriod(preset: MovementPeriodPreset, today: LocalDate): MovementFilters =
    when (preset) {
        MovementPeriodPreset.ALL -> copy(dateFrom = "", dateTo = "")
        MovementPeriodPreset.THIS_MONTH -> copy(
            dateFrom = today.withDayOfMonth(1).toString(),
            dateTo = today.withDayOfMonth(today.lengthOfMonth()).toString(),
        )
        MovementPeriodPreset.PREVIOUS_MONTH -> today.minusMonths(1).let { previous ->
            copy(
                dateFrom = previous.withDayOfMonth(1).toString(),
                dateTo = previous.withDayOfMonth(previous.lengthOfMonth()).toString(),
            )
        }
        MovementPeriodPreset.THIS_YEAR -> copy(
            dateFrom = today.withDayOfYear(1).toString(),
            dateTo = today.withMonth(12).withDayOfMonth(31).toString(),
        )
        MovementPeriodPreset.CUSTOM -> this
    }

internal fun MovementFilters.periodPreset(today: LocalDate): MovementPeriodPreset =
    MovementPeriodPreset.entries.firstOrNull { preset ->
        preset != MovementPeriodPreset.CUSTOM && withPeriod(preset, today).let {
            dateFrom == it.dateFrom && dateTo == it.dateTo
        }
    } ?: MovementPeriodPreset.CUSTOM
