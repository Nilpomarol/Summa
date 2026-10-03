package com.gestorfinances.app.ui.movements

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_type_expense
import com.gestorfinances.ui.resources.movement_type_income
import com.gestorfinances.ui.resources.movement_type_settlement
import com.gestorfinances.ui.resources.movement_type_transfer
import com.gestorfinances.ui.resources.recurring_cadence_custom
import com.gestorfinances.ui.resources.recurring_cadence_fortnightly
import com.gestorfinances.ui.resources.recurring_cadence_monthly
import com.gestorfinances.ui.resources.recurring_cadence_weekly
import com.gestorfinances.ui.resources.recurring_cadence_yearly
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency

val formMovementTypes = listOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.TRANSFER,
)

/** Recurring templates additionally schedule settlements, which the movement form never creates. */
val recurringTemplateTypes = formMovementTypes + MovementType.SETTLEMENT

@Composable
fun MovementType.formLabel(): String = when (this) {
    MovementType.EXPENSE -> stringResource(Res.string.movement_type_expense)
    MovementType.INCOME -> stringResource(Res.string.movement_type_income)
    MovementType.TRANSFER -> stringResource(Res.string.movement_type_transfer)
    MovementType.SETTLEMENT -> stringResource(Res.string.movement_type_settlement)
    else -> ""
}

@Composable
fun RecurrenceFrequency.cadenceLabel(): String = when (this) {
    RecurrenceFrequency.WEEKLY -> stringResource(Res.string.recurring_cadence_weekly)
    RecurrenceFrequency.FORTNIGHTLY -> stringResource(Res.string.recurring_cadence_fortnightly)
    RecurrenceFrequency.MONTHLY -> stringResource(Res.string.recurring_cadence_monthly)
    RecurrenceFrequency.YEARLY -> stringResource(Res.string.recurring_cadence_yearly)
    RecurrenceFrequency.CUSTOM -> stringResource(Res.string.recurring_cadence_custom)
}

fun TagSummary.supportsTrip(trip: TripSummary?): Boolean =
    trip != null && (this.tripId == trip.id || (this.tripId == null && (this.tripType == null || this.tripType == trip.type)))
