package com.gestorfinances.app.ui.trips

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI fixtures only: no personal database or app startup. */
class TripsEmptyTest {
    @get:Rule val compose = createComposeRule()
    private var opened: String? = null

    @Test
    fun noTripsShowsTheEmptyLineAndTheCreateAction() {
        show(TripsUiState(isLoading = false))
        compose.onAllNodesWithText("Viatges").assertCountEquals(1)
        compose.onNodeWithText("Encara no tens viatges").assertIsDisplayed()
        compose.onNodeWithText("Actius").assertDoesNotExist()
        compose.onNodeWithText("Planificats").assertDoesNotExist()
        compose.onNodeWithText("Finalitzats").assertDoesNotExist()
        compose.onAllNodesWithText("Nou viatge").assertCountEquals(1)
        compose.onNodeWithText("Nou viatge").performClick()
        compose.runOnIdle { assertEquals("add", opened) }
    }

    @Test
    fun tripsAreGroupedUnderTheirStatus() {
        show(TripsUiState(trips = listOf(trip("planned", TripStatus.PLANNED), trip("done", TripStatus.FINISHED)), isLoading = false))
        compose.onNodeWithText("Planificats").assertIsDisplayed()
        compose.onNodeWithText("Finalitzats").assertIsDisplayed()
        compose.onNodeWithText("Actius").assertDoesNotExist()
        compose.onNodeWithText("Trip planned").assertIsDisplayed()
        compose.onNodeWithText("Trip done").assertIsDisplayed()
        compose.onNodeWithText("Encara no tens viatges").assertDoesNotExist()
    }

    private fun trip(id: String, status: TripStatus) = TripSummary(
        id = id, name = "Trip $id", type = TripType.TRIP,
        status = status, startDate = null, endDate = null,
        icon = null, color = null, notes = null, defaultAccountId = null,
        defaultAccountName = null, createdAt = "2026-09-27T00:00:00Z",
        updatedAt = "2026-09-27T00:00:00Z", archivedAt = null, totalActualCents = 0L,
    )

    private fun show(state: TripsUiState) {
        compose.setContent {
            GestorFinancesTheme {
                TripsContent(
                    state = state, modifier = Modifier, onBack = {},
                    onAdd = { opened = "add" }, onDetail = {}, onRetry = {},
                )
            }
        }
    }
}
