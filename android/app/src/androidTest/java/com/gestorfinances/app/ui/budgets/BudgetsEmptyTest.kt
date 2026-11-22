package com.gestorfinances.app.ui.budgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gestorfinances.app.data.repository.BudgetMonthPlan
import com.gestorfinances.app.data.repository.DefaultPlanInclusions
import com.gestorfinances.app.domain.rules.PartSpending
import com.gestorfinances.app.domain.rules.PlanInputs
import com.gestorfinances.app.domain.rules.buildMonthPlan
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI fixtures only: no personal database or app startup. */
class BudgetsEmptyTest {
    @get:Rule val compose = createComposeRule()
    private var opened: String? = null

    @Test
    fun withoutAPlanTheHeroShowsTheMonthsSpendingAndOffersOne() {
        show(YearMonth.now())
        compose.onNodeWithText("Reparteix el mes en partides i segueix-ne el ritme.").assertIsDisplayed()
        compose.onNodeWithText("Partides").assertDoesNotExist()
        compose.onNodeWithText("Crea el pressupost").performClick()
        compose.runOnIdle { assertEquals("plan", opened) }
        compose.onNodeWithText("Nova partida").performClick()
        compose.runOnIdle { assertEquals("part", opened) }
    }

    private fun show(month: YearMonth) {
        val plan = BudgetMonthPlan(
            total = null,
            compartments = emptyList(),
            plan = buildMonthPlan(
                PlanInputs(
                    month = month,
                    today = LocalDate.now(),
                    totalLimitCents = null,
                    compartments = emptyList(),
                    others = PartSpending(actualCents = 12_000),
                    historyDays = 92,
                ),
            ),
            inclusions = DefaultPlanInclusions,
            overlappingBudgetIds = emptySet(),
        )
        compose.setContent {
            GestorFinancesTheme {
                BudgetsContent(
                    state = BudgetsUiState(isLoading = false, selectedMonth = month, plan = plan),
                    modifier = Modifier,
                    onBack = {},
                    onAddPart = { opened = "part" },
                    onEditPlan = {},
                    onCreatePlan = { opened = "plan" },
                    onAddYearly = {},
                    onMonthSelected = {},
                    onEdit = {},
                    onOpenRecurring = {},
                    onOpenGoals = {},
                    onOpenCategory = {},
                    onRetry = {},
                )
            }
        }
    }
}
