package com.gestorfinances.app.ui.trips

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.trip_type_celebration
import com.gestorfinances.ui.resources.trip_type_other
import com.gestorfinances.ui.resources.trip_type_trip
import com.gestorfinances.app.data.repository.TripType

/** Catalan label for a trip event type, shared by the Trips, Tags, and Dashboard screens. */
@Composable
fun TripType.label(): String =
    stringResource(
        when (this) {
            TripType.TRIP -> Res.string.trip_type_trip
            TripType.CELEBRATION -> Res.string.trip_type_celebration
            TripType.OTHER -> Res.string.trip_type_other
        },
    )

/** Icon for a trip event type, shared by the Trips and Dashboard screens. */
fun TripType.icon(): ImageVector =
    when (this) {
        TripType.TRIP -> Icons.Outlined.Flight
        TripType.CELEBRATION -> Icons.Outlined.Celebration
        TripType.OTHER -> Icons.Outlined.Event
    }
