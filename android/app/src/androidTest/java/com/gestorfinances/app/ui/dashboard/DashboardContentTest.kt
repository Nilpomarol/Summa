package com.gestorfinances.app.ui.dashboard

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.gestorfinances.app.data.repository.*
import com.gestorfinances.app.domain.rules.MonthPlan
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Blank test Activity and in-memory UI fixtures: never opens the personal database. */
class DashboardContentTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(populated())
    private val dark = mutableStateOf(false)
    private var opened: String? = null
    private var filters: MovementFilters? = null

    @Test
    fun accountScopeMonthBudgetAndRecentActivityStayDistinct() {
        show(populated())
        compose.onNodeWithText("SALDO DEL COMPTE").assertIsDisplayed()
        compose.onNodeWithText("800,00\u00A0€").assertIsDisplayed()
        compose.onNodeWithText("Juliol 2026", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Queden 80,00\u00A0€").assertIsDisplayed()
        capture("phase4-home-light.png")
        compose.onNodeWithContentDescription("Compte seleccionat: Banc de prova. Toca per canviar de compte.").performClick()
        compose.onNodeWithText("Estalvis de prova").performClick()
        compose.runOnIdle { assertEquals("savings", state.value.mainAccount?.id) }
        compose.onNodeWithText("Queden 80,00\u00A0€").assertIsDisplayed()
        compose.onNodeWithContentDescription("Saldo del compte Estalvis de prova: 500,00\u00A0€. Toca per veure l'anàlisi del compte.").performClick()
        compose.runOnIdle { assertEquals("account:savings", opened) }
        compose.onNodeWithText("Pressupostos").performClick()
        compose.runOnIdle { assertEquals("budgets", opened) }
        compose.onNodeWithText("Queden 80,00\u00A0€").performClick()
        compose.runOnIdle { assertEquals("budgets", opened) }
        compose.onNodeWithText("Veure tots").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MovementFilters(), filters) }
        compose.onNodeWithText("refund").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("movement:refund", opened) }
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("SALDO DEL COMPTE").performScrollTo()
        capture("phase4-home-dark.png")
    }

    @Test
    fun budgetUsesEligibleActualsAndNamesExclusionsAndOverrun() {
        show(populated().copy(monthExpenseCents = 90_000,
            monthPlan = planOf(actualCents = 21_000, inclusions = PlanInclusions(includeTripExpenses = false, includeExtraordinaryExpenses = false))))
        compose.onNodeWithText("10,00\u00A0€ per sobre").assertIsDisplayed()
        compose.onNodeWithText("Sense viatges ni extraordinàries").assertIsDisplayed()
        compose.onNodeWithText("900,00\u00A0€").assertDoesNotExist()
        capture("phase4-budget-overrun.png")
    }

    @Test
    fun activeTripAndLowBalanceKeepTheirContextualActions() {
        val trip = TripSummary("t", "Viatge de prova", TripType.TRIP, TripStatus.ACTIVE,
            "2026-07-01", null, null, null, null, null, null, NOW, NOW, null, 3_000)
        show(populated().copy(accounts = listOf(account.copy(lowBalanceThresholdCents = 40_000)), activeTrip = trip))
        compose.onNodeWithText("Saldo baix a Banc de prova").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("open-account:a", opened) }
        compose.onNodeWithText("Viatge de prova").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("trip:t", opened) }
        compose.onNodeWithContentDescription("Afegeix moviment").performClick()
        compose.runOnIdle { assertEquals("trip-add:t", opened) }
    }

    @Test
    fun pendingGathersDueRecurringItemsAndOpenDebts() {
        val person = PersonSummary("p", "Persona de prova", null, null, null, NOW, NOW, null, balanceCents = 2_500)
        show(populated().copy(openDebts = listOf(person)), dueRecurring = 2)
        compose.onNodeWithText("Pendent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 recurrents per confirmar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("recurring", opened) }
        compose.onNodeWithText("Persona de prova").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("person:p", opened) }
        compose.onNodeWithText("Et deu").assertIsDisplayed()
        capture("home-pending.png")
    }

    @Test
    fun nothingPendingHidesTheSection() {
        show(populated())
        compose.onNodeWithText("Pendent").assertDoesNotExist()
    }

    @Test
    fun specialistRowsOpenTheOriginalMovement() {
        show(populated().copy(latestMovements = listOf(
            movement("settlement", MovementType.SETTLEMENT),
            movement("contribution", MovementType.CONTRIBUTION),
            movement("withdrawal", MovementType.CONTRIBUTION).copy(contributionDirection = ContributionDirection.OUT),
            movement("transfer", MovementType.TRANSFER),
            movement("external", MovementType.EXPENSE).copy(financingKind = ExpenseFunding.PERSON,
                payerId = "p", paidByPersonName = "Persona de prova", accountId = null, accountName = null),
        )))
        listOf("settlement", "contribution", "withdrawal", "transfer", "external").forEach { id ->
            compose.onNodeWithText(id).performScrollTo().performClick()
            compose.runOnIdle { assertEquals("movement:$id", opened) }
        }
    }

    @Test
    fun emptyHomeOffersOneActionWithoutEmptyFinanceCards() {
        show(DashboardUiState(hasLoaded = true, isLoading = false))
        compose.onNodeWithText("SALDO DEL COMPTE").assertDoesNotExist()
        compose.onNodeWithText("Pressupostos").assertDoesNotExist()
        compose.onNodeWithText("Veure tots").assertDoesNotExist()
        compose.onNodeWithText("Nou compte").performClick()
        compose.runOnIdle { assertEquals("add-account", opened) }
        compose.runOnIdle { state.value = state.value.copy(accounts = listOf(account)) }
        compose.onNodeWithText("Nou moviment").performClick()
        compose.runOnIdle { assertEquals("add", opened) }
        capture("phase4-home-empty.png")
    }

    @Test
    fun loadingDoesNotPretendTheLedgerIsEmptyAndNoBudgetStillHasAPath() {
        show(DashboardUiState())
        compose.onNodeWithText("Nou moviment").assertDoesNotExist()
        compose.onNodeWithText("SALDO DEL COMPTE").assertDoesNotExist()
        compose.runOnIdle { state.value = populated().copy(monthPlan = null) }
        compose.onNodeWithText("Despesa d’aquest mes").assertIsDisplayed()
        compose.onNodeWithText("Pressupostos").performClick()
        compose.runOnIdle { assertEquals("budgets", opened) }
        compose.onNodeWithText("Defineix un límit mensual").assertDoesNotExist()
        compose.onNodeWithText("Despesa d’aquest mes").performClick()
        compose.runOnIdle {
            assertEquals("2026-07-01", filters?.dateFrom)
            assertEquals("2026-07-31", filters?.dateTo)
            assertEquals(MovementSourceMode.ACTUAL, filters?.sourceMode)
        }
    }

    private fun show(initial: DashboardUiState, dueRecurring: Int = 0) {
        state.value = initial
        compose.setContent {
            GestorFinancesTheme(darkTheme = dark.value) {
                DashboardContent(
                    state = state.value, modifier = Modifier, dueRecurringCount = dueRecurring,
                    onOpenAccount = { opened = "open-account:${it.id}" }, onOpenPerson = { opened = "person:${it.id}" },
                    onOpenRecurring = { opened = "recurring" },
                    onDrillDown = { filters = it }, onMovementDetail = { opened = "movement:${it.id}" },
                    onAccountAnalysis = { opened = "account:${it.id}" },
                    onAccountSelected = { state.value = state.value.copy(selectedAccountId = it) },
                    onViewTrip = { opened = "trip:${it.id}" }, onAddTripMovement = { opened = "trip-add:${it.id}" },
                    onViewBudgets = { opened = "budgets" }, onAddMovement = { opened = "add" },
                    onAddAccount = { opened = "add-account" }, onRetry = {},
                )
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun populated() = DashboardUiState(
        today = LocalDate.parse("2026-07-15"), month = YearMonth.of(2026, 7), hasLoaded = true, isLoading = false,
        accounts = listOf(account, account.copy(id = "savings", name = "Estalvis de prova", currentBalanceCents = 50_000, isDefault = false)),
        netWorthCents = 80_000, monthExpenseCents = 12_000,
        latestMovements = listOf(movement("expense", MovementType.EXPENSE), movement("refund", MovementType.REFUND)),
        monthPlan = planOf(actualCents = 12_000),
    )

    private fun planOf(actualCents: Long, inclusions: PlanInclusions = PlanInclusions(true, true)): BudgetMonthPlan {
        val part = PlanPart(plannedCents = 20_000, actualCents = actualCents, expectedByTodayCents = 12_000,
            forecastCents = actualCents, perDayCents = null)
        return BudgetMonthPlan(
            total = BudgetSummary("b", BudgetScope.OVERALL_MONTH, null, null, tripId = null,
                tripName = null, period = BudgetPeriod.MONTHLY, limitAmountCents = 20_000, alertThresholdPercent = null),
            compartments = emptyList(),
            plan = MonthPlan(total = part, compartments = emptyMap(), others = part),
            inclusions = inclusions,
            overlappingBudgetIds = emptySet(),
        )
    }

    private fun movement(id: String, type: MovementType) = MovementSummary(
        id = id, type = type, amountCents = 100, date = "2026-07-15",
        accountId = "a", accountName = "Banc de prova", destinationAccountId = null, destinationAccountName = null,
        categoryId = "c", categoryName = "Menjar de prova", categoryNature = CategoryNature.VARIABLE,
        categoryIcon = null, categoryColor = null, name = id, payee = null, notes = null,
        isOneTime = false, isShared = false, userShareCents = 100, isRecurring = false,
        paidByPersonName = null, payerId = null, settlementDirection = null, settlementPersonName = null,
        createdAt = NOW, updatedAt = NOW, archivedAt = null,
    )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        val account = AccountSummary("a", "Banc de prova", 30_000, 30_000, AccountType.BANK,
            null, null, true, 0, null, NOW, NOW, null)
    }
}
