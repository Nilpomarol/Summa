package com.gestorfinances.app.ui.people

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI fixtures only: no personal database or app startup. */
class PeopleEmptyTest {
    @get:Rule val compose = createComposeRule()
    private var added = false

    @Test
    fun noPeopleShowsOneAddActionWithoutFinancialSummary() {
        show(PeopleUiState(isLoading = false))
        compose.onNodeWithText("Persones").assertIsDisplayed()
        compose.onNodeWithText("Encara no hi ha persones").assertIsDisplayed()
        compose.onAllNodesWithText("Nova persona").assertCountEquals(1)
        compose.onNodeWithText("Saldo net").assertDoesNotExist()
        compose.onNodeWithText("Et deuen").assertDoesNotExist()
        compose.onNodeWithText("Deus").assertDoesNotExist()
        compose.onNodeWithText("Persones actives").assertDoesNotExist()
        compose.onNodeWithText("Nova persona").performClick()
        compose.runOnIdle { assertEquals(true, added) }
    }

    @Test
    fun existingPersonKeepsSummaryAndListEvenWithZeroBalance() {
        val person = PersonSummary(
            id = "person", name = "Fixture person", avatar = null, color = null, notes = null,
            createdAt = "2026-09-27T00:00:00Z", updatedAt = "2026-09-27T00:00:00Z",
            archivedAt = null, balanceCents = 0L,
        )
        show(PeopleUiState(people = listOf(person), isLoading = false))
        compose.onNodeWithText("Saldo net").assertIsDisplayed()
        compose.onNodeWithText("Fixture person").assertIsDisplayed()
        compose.onNodeWithText("Encara no hi ha persones").assertDoesNotExist()
    }

    private fun show(state: PeopleUiState) {
        compose.setContent {
            GestorFinancesTheme {
                PeopleContent(
                    state = state, modifier = Modifier, onBack = {}, onAdd = { added = true },
                    onOpenDetail = {}, onRetry = {},
                )
            }
        }
    }
}
