package com.gestorfinances.app.ui.movements

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.io.File
import android.graphics.Bitmap

/** A blank test Activity with in-memory UI values: never opens or seeds the app database. */
class MovementsFiltersTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(MovementsUiState(isLoading = false))
    private var opened: String? = null

    @Test
    fun primaryChoicesSearchAndSpecialistRowsStayAvailableWithoutAccounts() {
        show(MovementsUiState(isLoading = false, movements = fixtures()))
        compose.onNodeWithText("Tots").assertIsSelected()
        compose.onNodeWithText("Més").assertDoesNotExist()
        compose.onNodeWithText("Despeses").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf("expense"), state.value.visibleMovements.map { it.id }) }
        compose.onNodeWithText("Ingressos").performClick()
        compose.runOnIdle { assertEquals(listOf("income"), state.value.visibleMovements.map { it.id }) }
        compose.onNodeWithText("Transferències").performClick()
        compose.runOnIdle { assertEquals(listOf("transfer"), state.value.visibleMovements.map { it.id }) }
        compose.onNodeWithText("Tots").performClick()
        listOf("refund", "settlement", "contribution", "withdrawal").forEach { id ->
            compose.onNode(hasSetTextAction()).performTextReplacement(id)
            compose.onNode(hasText(id) and !hasSetTextAction()).performClick()
            compose.runOnIdle { assertEquals(id, opened) }
        }
        compose.onNodeWithContentDescription("Esborra la cerca").performClick()
        compose.runOnIdle { assertEquals(7, state.value.visibleMovements.size) }
    }

    @Test
    fun flatFiltersApplyTogetherAndResetKeepsSearch() {
        show(MovementsUiState(isLoading = false, movements = fixtures(), accounts = listOf(account),
            categories = listOf(category), trips = listOf(trip), filters = MovementFilters(query = "expense")))
        capture("phase3-history.png")
        compose.onNodeWithContentDescription("Filtres").performClick()
        compose.onNodeWithText("Tots els comptes").performClick()
        compose.onAllNodesWithText("Banc de prova").onLast().performClick()
        compose.onNodeWithText("Totes les categories").performClick()
        compose.onAllNodesWithText("Menjar de prova").onLast().performClick()
        compose.onNodeWithText("Tots els viatges").performClick()
        compose.onAllNodesWithText("Viatge de prova").onLast().performClick()
        compose.onNodeWithText("Aquest mes").performScrollTo().performClick()
        capture("phase3-filters.png")
        compose.runOnIdle { assertEquals(MovementFilters(query = "expense"), state.value.filters) }
        compose.onNodeWithText("Aplica").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Obre filtres, 4 actius").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("expense"), state.value.visibleMovements.map { it.id }) }
        compose.onNodeWithContentDescription("Neteja filtres").performClick()
        compose.runOnIdle { assertEquals(MovementFilters(query = "expense"), state.value.filters) }
        compose.onNodeWithContentDescription("Filtres").performClick()
        compose.onNodeWithText("Mes anterior").performScrollTo().performClick()
        compose.onNodeWithText("Aplica").performScrollTo().performClick()
        compose.onNodeWithText("Cap moviment coincideix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Neteja filtres").performClick()
        compose.runOnIdle { assertEquals(listOf("expense"), state.value.visibleMovements.map { it.id }) }
    }

    @Test
    fun clearingTheFilterPillKeepsTheQuickTypeAndSearch() {
        show(MovementsUiState(isLoading = false, movements = fixtures(), accounts = listOf(account),
            filters = MovementFilters(query = "e", type = MovementType.EXPENSE, accountId = "a")))
        compose.onNodeWithContentDescription("Obre filtres, 1 actius").assertIsDisplayed()
        compose.onNodeWithContentDescription("Neteja filtres").performClick()
        compose.runOnIdle { assertEquals(MovementFilters(query = "e", type = MovementType.EXPENSE), state.value.filters) }
        compose.onNodeWithContentDescription("Filtres").assertIsDisplayed()
        compose.onNodeWithContentDescription("Neteja filtres").assertDoesNotExist()
    }

    @Test
    fun noDataAndSearchOnlyEmptyStatesDiffer() {
        show(MovementsUiState(isLoading = false))
        compose.onNodeWithText("Encara no hi ha moviments").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(movements = fixtures(), filters = MovementFilters(query = "missing")) }
        compose.onNodeWithText("Cap moviment coincideix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Neteja filtres").assertDoesNotExist()
        compose.onNodeWithContentDescription("Esborra la cerca").performClick()
        compose.runOnIdle { assertEquals(7, state.value.visibleMovements.size) }
    }

    @Test
    fun customDatePickerAppliesAndDismissCancelsDraftChanges() {
        val initial = MovementFilters(query = "expense", accountId = "a", dateFrom = "2026-05-12", dateTo = "2026-05-20")
        show(MovementsUiState(isLoading = false, movements = fixtures(), accounts = listOf(account), filters = initial))
        compose.onNodeWithContentDescription("Obre filtres, 2 actius").performClick()
        compose.onNodeWithText("Des de").performScrollTo().performClick()
        // Date picker days carry the full written date as their text, not the bare number.
        compose.onNodeWithText("13 de maig", substring = true).performClick()
        compose.onAllNodesWithText("Aplica")[1].performClick()
        compose.onNodeWithText("Aplica").performScrollTo().performClick()
        val applied = initial.copy(dateFrom = "2026-05-13")
        compose.runOnIdle { assertEquals(applied, state.value.filters) }
        compose.onNodeWithContentDescription("Obre filtres, 2 actius").performClick()
        compose.onNodeWithText("Neteja filtres").performScrollTo().performClick()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(applied, state.value.filters) }
    }

    @Test
    fun contextualCustomRangeStaysVisibleAndInvalidRangeBlocksApply() {
        val filters = MovementFilters(accountId = "a", categoryId = "c", tripId = "t", tagId = "tag",
            sourceMode = MovementSourceMode.ACTUAL, dateFrom = "2026-05-12", dateTo = "2026-04-01")
        show(MovementsUiState(isLoading = false, movements = fixtures(), accounts = listOf(account),
            categories = listOf(category), trips = listOf(trip), filters = filters))
        compose.onNodeWithContentDescription("Obre filtres, 6 actius").performClick()
        compose.onNodeWithText("Rang personalitzat").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Des de").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fins a").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aplica").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Aquest any").performScrollTo().performClick()
        compose.onNodeWithText("Aplica").performScrollTo().performClick()
        compose.runOnIdle {
            val expected = filters.withPeriod(MovementPeriodPreset.THIS_YEAR, LocalDate.now()).withDateValidation()
            assertEquals(expected, state.value.filters)
            assertTrue(state.value.filters.errorRes == null)
        }
    }

    private fun show(initial: MovementsUiState) {
        state.value = initial
        compose.setContent {
            GestorFinancesTheme {
                MovementsContent(
                    onViewRecurring = {}, state = state.value, modifier = Modifier,
                    onFiltersChange = { state.value = state.value.copy(filters = it.withDateValidation()) },
                    onClearFilters = { state.value = state.value.copy(filters = state.value.filters.cleared()) },
                    onDetail = { opened = it.id }, onAdd = {}, onRetry = {},
                )
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val roots = compose.onAllNodes(isRoot())
        val bitmap = roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun fixtures(): List<MovementSummary> = listOf(
        movement("expense", MovementType.EXPENSE), movement("income", MovementType.INCOME),
        movement("transfer", MovementType.TRANSFER), movement("refund", MovementType.REFUND),
        movement("settlement", MovementType.SETTLEMENT), movement("contribution", MovementType.CONTRIBUTION),
        movement("withdrawal", MovementType.CONTRIBUTION).copy(contributionDirection = ContributionDirection.OUT),
    )

    private fun movement(id: String, type: MovementType) = MovementSummary(
        id = id, type = type, amountCents = 100, date = LocalDate.now().toString(),
        accountId = "a", accountName = "Banc de prova", destinationAccountId = null, destinationAccountName = null,
        categoryId = "c", categoryName = "Menjar de prova", categoryNature = CategoryNature.VARIABLE,
        categoryIcon = null, categoryColor = null, tripId = "t", tripName = "Viatge de prova",
        name = id, payee = null, notes = null, isOneTime = false, isShared = false,
        userShareCents = 100, isRecurring = false, paidByPersonName = null, payerId = null,
        settlementDirection = null, settlementPersonName = null,
        createdAt = NOW, updatedAt = NOW, archivedAt = null,
    )

    private val account = AccountSummary("a", "Banc de prova", 0, 0, AccountType.BANK, null, null,
        true, 0, null, NOW, NOW, null)
    private val category = CategoryRecord("c", "Menjar de prova", CategoryKind.EXPENSE, CategoryNature.VARIABLE,
        null, null, null, 0, NOW, NOW, null)
    private val trip = TripSummary("t", "Viatge de prova", TripType.TRIP, TripStatus.ACTIVE,
        null, null, null, null, null, null, null, NOW, NOW, null, 0)
    private companion object { const val NOW = "2026-01-01T00:00:00Z" }
}
