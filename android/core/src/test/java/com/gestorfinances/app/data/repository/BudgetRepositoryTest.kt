package com.gestorfinances.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetRepositoryTest {
    @Test
    fun overallMonthlyBudgetUsesAllCanonicalActualExpense() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft(
                    id = "overall",
                    categoryId = null,
                    limitAmountCents = 20_000,
                    alertThresholdPercent = null,
                    scope = BudgetScope.OVERALL_MONTH,
                    period = BudgetPeriod.MONTHLY,
                ),
                createdAt = NOW,
            )
            store.movements.create(expense("food", 7_000), createdAt = NOW)

            val evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(BudgetScope.OVERALL_MONTH, evaluation.budget.scope)
            assertEquals(7_000L, evaluation.actualCents)
        }
    }

    @Test
    fun overallBudgetCanExcludeTripAndExtraordinaryExpenses() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedTrip(store, "mallorca")
            store.budgets.create(
                BudgetDraft(
                    id = "overall",
                    categoryId = null,
                    limitAmountCents = 20_000,
                    alertThresholdPercent = null,
                    scope = BudgetScope.OVERALL_MONTH,
                    period = BudgetPeriod.MONTHLY,
                    includeTripExpenses = false,
                    includeExtraordinaryExpenses = false,
                ),
                createdAt = NOW,
            )
            store.movements.create(expense("regular", 1_000), createdAt = NOW)
            store.movements.create(expense("trip", 2_000).copy(tripId = "mallorca"), createdAt = NOW)
            store.movements.create(expense("extra", 3_000).copy(isOneTime = true), createdAt = NOW)

            assertEquals(1_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun yearlyCategoryBudgetEvaluatesFromTheStartOfTheReferenceYear() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft(
                    id = "food-yearly",
                    categoryId = "food",
                    limitAmountCents = 100_000,
                    alertThresholdPercent = null,
                    period = BudgetPeriod.YEARLY,
                ),
                createdAt = NOW,
            )
            store.movements.create(expense("jan", 2_000).copy(date = "2026-01-20"), createdAt = NOW)
            store.movements.create(expense("mar", 3_000), createdAt = NOW)

            assertEquals(5_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun categoryCanHaveMonthlyAndYearlyRulesButNotDuplicatesOfEither() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("food-monthly", "food", 30_000, null),
                createdAt = NOW,
            )
            store.budgets.create(
                BudgetDraft(
                    id = "food-yearly",
                    categoryId = "food",
                    limitAmountCents = 300_000,
                    alertThresholdPercent = null,
                    period = BudgetPeriod.YEARLY,
                ),
                createdAt = NOW,
            )

            assertThrows(DuplicateActiveBudgetException::class.java) {
                store.budgets.create(BudgetDraft("food-monthly-2", "food", 40_000, null), createdAt = NOW)
            }
            assertEquals(2, store.budgets.listActive().size)
        }
    }

    @Test
    fun evaluationReflectsActualSpendAndStatusBands() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft(
                    id = "b1",
                    categoryId = "food",
                    limitAmountCents = 10_000,
                    alertThresholdPercent = 80,
                ),
                createdAt = NOW,
            )

            // No spend yet → OK, full limit remaining.
            var evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(0L, evaluation.actualCents)
            assertEquals(10_000L, evaluation.remainingCents)
            assertEquals(BudgetStatus.OK, evaluation.status)

            // 90% of the limit → WARN (>= 80% threshold).
            store.movements.create(expense("e1", 9_000), createdAt = NOW)
            evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(9_000L, evaluation.actualCents)
            assertEquals(BudgetStatus.WARN, evaluation.status)

            // Over the limit → OVER, negative remaining.
            store.movements.create(expense("e2", 2_000), createdAt = NOW)
            evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(11_000L, evaluation.actualCents)
            assertEquals(-1_000L, evaluation.remainingCents)
            assertEquals(BudgetStatus.OVER, evaluation.status)
        }
    }

    @Test
    fun refundNetsDownBudgetActual() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null, null),
                createdAt = NOW,
            )
            store.movements.create(expense("e1", 9_000), createdAt = NOW)
            store.movements.createRefund(
                RefundDraft(
                    id = "r1",
                    refundsExpenseId = "e1",
                    amountCents = 3_000,
                    accountId = "checking",
                    date = "2026-03-10",
                    name = null,
                    payee = null,
                    notes = null,
                    actualRefundCents = null,
                ),
                createdAt = NOW,
            )

            assertEquals(6_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun partialActualRefundNetsOnlyUserShareFromBudgetActual() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null, null),
                createdAt = NOW,
            )
            store.movements.create(expense("e1", 9_000), createdAt = NOW)
            store.movements.createRefund(
                RefundDraft(
                    id = "r1",
                    refundsExpenseId = "e1",
                    amountCents = 5_000,
                    accountId = "checking",
                    date = "2026-03-10",
                    name = null,
                    payee = null,
                    notes = null,
                    actualRefundCents = 2_000,
                ),
                createdAt = NOW,
            )

            assertEquals(7_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun defaultThresholdWarnsAtEightyPercent() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null, null),
                createdAt = NOW,
            )

            store.movements.create(expense("e1", 7_999), createdAt = NOW)
            assertEquals(BudgetStatus.OK, store.budgets.evaluateAll(FROM, TO).single().status)

            store.movements.create(expense("e2", 1), createdAt = NOW)
            assertEquals(BudgetStatus.WARN, store.budgets.evaluateAll(FROM, TO).single().status)
        }
    }

    @Test
    fun recurringMonthlyBudgetCountsTheWholeMonth() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.budgets.create(
                BudgetDraft("b1", "food", 10_000, null),
                createdAt = NOW,
            )
            store.movements.create(expense("before", 4_000).copy(date = "2026-03-05"), createdAt = NOW)
            store.movements.create(expense("after", 3_000).copy(date = "2026-03-10"), createdAt = NOW)

            assertEquals(7_000L, store.budgets.evaluateAll(FROM, TO).single().actualCents)
        }
    }

    @Test
    fun tripBudgetEvaluatesActualSpendForTripOnly() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedTrip(store, "mallorca")
            store.budgets.create(
                BudgetDraft(
                    id = "trip-budget",
                    categoryId = null,
                    limitAmountCents = 10_000,
                    alertThresholdPercent = null,
                    tripId = "mallorca",
                    scope = BudgetScope.TRIP,
                    period = BudgetPeriod.ONE_OFF,
                ),
                createdAt = NOW,
            )

            store.movements.create(expense("trip-expense", 7_000).copy(tripId = "mallorca"), createdAt = NOW)
            store.movements.create(expense("regular-expense", 5_000), createdAt = NOW)

            val evaluation = store.budgets.evaluateAll(FROM, TO).single()
            assertEquals(BudgetScope.TRIP, evaluation.budget.scope)
            assertEquals(7_000L, evaluation.actualCents)
            assertEquals(3_000L, evaluation.remainingCents)
        }
    }

    @Test
    fun monthPlanSplitsSpendingIntoCompartmentsAndTheRestWithRecurringPaymentsByCategory() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedCategory(store, "restaurant", parentId = "food")
            seedCategory(store, "home")
            seedTrip(store, "mallorca")
            store.budgets.create(
                BudgetDraft(
                    id = "total",
                    categoryId = null,
                    limitAmountCents = 100_000,
                    alertThresholdPercent = null,
                    scope = BudgetScope.OVERALL_MONTH,
                    period = BudgetPeriod.MONTHLY,
                    includeTripExpenses = false,
                ),
                createdAt = NOW,
            )
            store.budgets.create(BudgetDraft("food-month", "food", 30_000, null, null), createdAt = NOW)
            // Rent was paid this month; the gym is still due later on.
            store.templates.create(monthlyTemplate("rent", "home", 6_000, nextDueDate = "2026-04-05"), createdAt = NOW)
            store.templates.create(monthlyTemplate("gym", "home", 1_000, nextDueDate = "2026-03-20"), createdAt = NOW)
            store.templates.create(monthlyTemplate("delivery", "restaurant", 1_500, nextDueDate = "2026-03-25"), createdAt = NOW)
            store.movements.create(expense("rent-mar", 6_000).copy(categoryId = "home", templateId = "rent"), createdAt = NOW)
            store.movements.create(expense("groceries", 5_000), createdAt = NOW)
            store.movements.create(expense("dinner", 2_000).copy(categoryId = "restaurant"), createdAt = NOW)
            store.movements.create(expense("lamp", 4_000).copy(categoryId = "home"), createdAt = NOW)
            store.movements.create(expense("beach", 3_000).copy(tripId = "mallorca"), createdAt = NOW)

            val result = store.budgets.monthPlan(
                month = YearMonth.of(2026, 3),
                today = LocalDate.of(2026, 3, 10),
                templates = store.templates.listActive(),
                categoryParentById = mapOf("food" to null, "restaurant" to "food", "home" to null),
            )
            val plan = result.plan

            val food = plan.compartments.getValue("food-month")
            assertEquals(7_000L, food.actualCents) // a subcategory counts in its parent
            assertEquals(1_500L, food.dueCents) // so does its recurring payment still due
            // Home has no partida: rent (paid), the gym (due) and the lamp all fall to the rest.
            assertEquals(10_000L, plan.others.actualCents)
            assertEquals(7_000L, plan.others.committedCents)
            assertEquals(1_000L, plan.others.dueCents)
            assertEquals(100_000L - 30_000L, plan.others.plannedCents)
            assertEquals(2_500L, plan.total.dueCents)
            // The parts partition the canonical figure under the plan's inclusions: the trip stays out.
            assertEquals(
                store.budgets.actualOverall(FROM, TO, includeTripExpenses = false),
                plan.total.actualCents,
            )
            assertTrue(result.overlappingBudgetIds.isEmpty())
        }
    }

    @Test
    fun aCompartmentMayNotOverlapItsParentOrChildren() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedCategory(store, "restaurant", parentId = "food")
            store.budgets.create(BudgetDraft("food-month", "food", 30_000, null, null), createdAt = NOW)

            assertThrows(OverlappingCompartmentException::class.java) {
                store.budgets.create(BudgetDraft("restaurant-month", "restaurant", 10_000, null, null), createdAt = NOW)
            }
            // A yearly limit is not a compartment of the monthly plan.
            store.budgets.create(
                BudgetDraft("restaurant-year", "restaurant", 100_000, null, null, period = BudgetPeriod.YEARLY),
                createdAt = NOW,
            )
        }
    }

    @Test
    fun recentSpendingAveragesTheLastThreeMonthsWithParentsIncludingChildren() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedCategory(store, "restaurant", parentId = "food")
            store.movements.create(expense("jan", 3_000).copy(date = "2026-01-10"), createdAt = NOW)
            store.movements.create(expense("feb", 1_500).copy(date = "2026-02-10", categoryId = "restaurant"), createdAt = NOW)
            store.movements.create(expense("loose", 600).copy(date = "2026-02-11", categoryId = null), createdAt = NOW)
            store.movements.create(expense("this-month", 9_000), createdAt = NOW)
            // Recurring payments count too.
            store.templates.create(monthlyTemplate("streaming", "food", 900, nextDueDate = "2026-04-05"), createdAt = NOW)
            store.movements.create(expense("streaming-feb", 900).copy(date = "2026-02-05", templateId = "streaming"), createdAt = NOW)

            val recent = store.budgets.recentSpending(
                month = YearMonth.of(2026, 3),
                categoryParentById = mapOf("food" to null, "restaurant" to "food"),
                inclusions = DefaultPlanInclusions,
            )

            assertEquals(1_800L, recent.byCategory["food"])
            assertEquals(500L, recent.byCategory["restaurant"])
            assertEquals(200L, recent.uncategorisedCents)
        }
    }

    @Test
    fun spendingHistorySplitsEachMonthByCategoryTheRestAndTheWhole() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedCategory(store, "restaurant", parentId = "food")
            seedCategory(store, "home")
            store.budgets.create(BudgetDraft("food-month", "food", 30_000, null), createdAt = NOW)
            store.movements.create(expense("jan", 3_000).copy(date = "2026-01-10"), createdAt = NOW)
            store.movements.create(expense("dinner", 1_000).copy(date = "2026-02-10", categoryId = "restaurant"), createdAt = NOW)
            store.movements.create(expense("lamp", 500).copy(date = "2026-02-11", categoryId = "home"), createdAt = NOW)

            val history = store.budgets.spendingHistory(
                month = YearMonth.of(2026, 3),
                count = 2,
                categoryParentById = mapOf("food" to null, "restaurant" to "food", "home" to null),
                inclusions = DefaultPlanInclusions,
            )

            assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2)), history.months)
            assertEquals(listOf(3_000L, 1_000L), history.byCategory["food"])
            assertEquals(listOf(0L, 1_000L), history.byCategory["restaurant"])
            assertEquals(listOf(0L, 500L), history.others) // outside today's partides
            assertEquals(listOf(3_000L, 1_500L), history.total)
        }
    }

    @Test
    fun planIncomeExpectsWhatCameInPlusRecurringIncomeStillDue() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.templates.create(
                monthlyTemplate("salary", "food", 200_000, nextDueDate = "2026-03-25").copy(type = MovementType.INCOME),
                createdAt = NOW,
            )
            store.templates.create(
                monthlyTemplate("rent-in", "food", 50_000, nextDueDate = "2026-04-02").copy(type = MovementType.INCOME),
                createdAt = NOW,
            )
            store.movements.create(income("rent-mar", 50_000).copy(templateId = "rent-in", date = "2026-03-02"), createdAt = NOW)
            store.movements.create(income("gift", 10_000), createdAt = NOW)

            val income = store.budgets.planIncome(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), store.templates.listActive())

            assertEquals(60_000L, income.actualCents)
            assertEquals(60_000L + 200_000L, income.expectedCents)
        }
    }

    @Test
    fun approximateRecurringIncomeIsExpectedAtItsStatedAmountNotItsRecentAverage() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.templates.create(
                monthlyTemplate("salary", "food", 200_000, nextDueDate = "2026-03-25").copy(type = MovementType.INCOME, amountIsVariable = true),
                createdAt = NOW,
            )
            // Its last occurrences came in lower, so its estimate is their average.
            listOf("2025-12-25", "2026-01-25", "2026-02-25").forEachIndexed { index, date ->
                store.movements.create(income("pay$index", 150_000).copy(templateId = "salary", date = date), createdAt = NOW)
            }
            val templates = store.templates.listActive()
            assertEquals(150_000L, templates.single().expectedAmountCents)

            val month = YearMonth.of(2026, 3)
            val today = LocalDate.of(2026, 3, 10)
            assertEquals(200_000L, store.budgets.planIncome(month, today, templates).expectedCents)
            assertEquals(200_000L, store.budgets.recurringMonth(month, today, templates).dueIncomeCents)
        }
    }

    @Test
    fun withoutRecurringIncomeTheRecentAverageIsExpected() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            store.movements.create(income("jan", 90_000).copy(date = "2026-01-15"), createdAt = NOW)
            store.movements.create(income("feb", 60_000).copy(date = "2026-02-15"), createdAt = NOW)
            store.movements.create(income("mar", 10_000), createdAt = NOW)
            store.movements.create(expense("feb-shop", 30_000).copy(date = "2026-02-20"), createdAt = NOW)

            val income = store.budgets.planIncome(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), emptyList())

            assertEquals(10_000L, income.actualCents)
            assertEquals(50_000L, income.expectedCents) // (0 + 90.000 + 60.000) / 3
            assertEquals(40_000L, income.recentSavingCents) // (0 + 90.000 + 30.000) / 3
        }
    }

    @Test
    fun aPlanIsCreatedWholeOrNotAtAll() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedCategory(store, "restaurant", parentId = "food")
            val total = BudgetDraft("total", null, 100_000, null, scope = BudgetScope.OVERALL_MONTH)

            assertThrows(OverlappingCompartmentException::class.java) {
                store.budgets.createPlan(
                    total = total,
                    partides = listOf(BudgetDraft("food", "food", 30_000, null), BudgetDraft("restaurant", "restaurant", 10_000, null)),
                    createdAt = NOW,
                )
            }
            assertTrue(store.budgets.listActive().isEmpty())

            store.budgets.createPlan(total = total, partides = listOf(BudgetDraft("food", "food", 30_000, null)), createdAt = NOW)
            assertEquals(setOf("total", "food"), store.budgets.listActive().map { it.id }.toSet())
        }
    }

    @Test
    fun rejectsInvalidBudgets() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            assertThrows(IllegalArgumentException::class.java) {
                store.budgets.create(BudgetDraft("b1", "food", 0, null, null), createdAt = NOW)
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.budgets.create(BudgetDraft("b2", "food", 10_000, 150, null), createdAt = NOW)
            }
        }
    }

    @Test
    fun earlierMonthsKeepThePlanTheyHad() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedCategory(store, "home")
            val march = "2026-03-15T12:00:00Z"
            val april = "2026-04-15T12:00:00Z"
            val total = BudgetDraft("overall", null, 100_000, null, scope = BudgetScope.OVERALL_MONTH, period = BudgetPeriod.MONTHLY)
            store.budgets.create(total, createdAt = march)
            store.budgets.create(BudgetDraft("food-month", "food", 30_000, null), createdAt = march)
            // In April the total goes up, food leaves the plan and home joins it.
            store.budgets.update(total.copy(limitAmountCents = 120_000), updatedAt = april)
            store.budgets.archive("food-month", archivedAt = april)
            store.budgets.create(BudgetDraft("home-month", "home", 50_000, null), createdAt = april)

            val planIn = { month: Int ->
                store.budgets.monthPlan(YearMonth.of(2026, month), LocalDate.of(2026, 4, 20), emptyList(), mapOf("food" to null, "home" to null))
            }
            val marchPlan = planIn(3)
            assertEquals(100_000L, marchPlan.total?.limitAmountCents)
            assertEquals(listOf("food-month" to 30_000L), marchPlan.compartments.map { it.id to it.limitAmountCents })
            assertEquals("food", marchPlan.compartments.single().categoryId)
            val aprilPlan = planIn(4)
            assertEquals(120_000L, aprilPlan.total?.limitAmountCents)
            assertEquals(listOf("home-month"), aprilPlan.compartments.map { it.id })
            // A month from before there was a plan is measured against the plan's first shape.
            assertEquals(marchPlan.compartments, planIn(1).compartments)
            assertEquals(100_000L, planIn(1).total?.limitAmountCents)

            // Undoing the removal in the same month puts April's plan back as it was.
            store.budgets.restore("food-month", deletedAt = april, restoredAt = april)
            val may = store.budgets.monthPlan(YearMonth.of(2026, 4), LocalDate.of(2026, 5, 2), emptyList(), mapOf("food" to null, "home" to null))
            assertEquals(setOf("food-month", "home-month"), may.compartments.map { it.id }.toSet())
        }
    }

    @Test
    fun tripsTakeFromTheMonthWhatTheySpentAndWhatTheirBudgetHasLeft() {
        freshStore().use { store ->
            seedAccountAndCategory(store)
            seedTrip(store, "mallorca")
            store.budgets.create(BudgetDraft("mallorca-budget", null, 50_000, null, tripId = "mallorca"), createdAt = NOW)
            store.movements.create(expense("regular", 1_000), createdAt = NOW)
            store.movements.create(expense("flights", 20_000).copy(tripId = "mallorca"), createdAt = NOW)
            val trip = store.trips.listActive().single()

            // 200 EUR spent on it this month; the 300 EUR its budget has left fall on its days.
            val planned = trip.copy(startDate = "2026-03-20", endDate = "2026-03-22", status = TripStatus.PLANNED)
            assertEquals(50_000L, store.budgets.tripSpendingIn(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), listOf(planned)))
            // Once finished, or without dates, only what was spent counts.
            assertEquals(20_000L, store.budgets.tripSpendingIn(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), listOf(planned.copy(status = TripStatus.FINISHED))))
            assertEquals(20_000L, store.budgets.tripSpendingIn(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10), listOf(planned.copy(startDate = null, endDate = null))))
        }
    }

    private fun expense(id: String, amountCents: Long): MovementDraft =
        MovementDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = amountCents,
            date = "2026-03-05",
            accountId = "checking",
            destinationAccountId = null,
            categoryId = "food",
            name = id,
            payee = null,
            notes = null,
            isOneTime = false,
        )

    private fun seedAccountAndCategory(store: TestStore) {
        store.accounts.create(
            AccountDraft(
                id = "checking",
                name = "Compte",
                startingBalanceCents = 0,
                type = AccountType.BANK,
                icon = null,
                color = null,
                isDefault = true,
                displayOrder = 0,
                lowBalanceThresholdCents = null,
            ),
            createdAt = NOW,
        )
        store.categories.create(
            CategoryDraft(
                id = "food",
                name = "Menjar",
                kind = CategoryKind.EXPENSE,
                nature = CategoryNature.VARIABLE,
                parentId = null,
                icon = null,
                color = null,
                displayOrder = 0,
            ),
            createdAt = NOW,
        )
    }

    private fun income(id: String, amountCents: Long): MovementDraft =
        expense(id, amountCents).copy(type = MovementType.INCOME, categoryId = null)

    private fun seedCategory(store: TestStore, id: String, parentId: String? = null) {
        store.categories.create(
            CategoryDraft(
                id = id,
                name = id,
                kind = CategoryKind.EXPENSE,
                nature = CategoryNature.VARIABLE,
                parentId = parentId,
                icon = null,
                color = null,
                displayOrder = 0,
            ),
            createdAt = NOW,
        )
    }

    private fun monthlyTemplate(id: String, categoryId: String, amountCents: Long, nextDueDate: String): TemplateDraft =
        TemplateDraft(
            id = id,
            type = MovementType.EXPENSE,
            amountCents = amountCents,
            accountId = "checking",
            destAccountId = null,
            categoryId = categoryId,
            name = id,
            payee = null,
            notes = null,
            splitConfig = null,
            frequency = RecurrenceFrequency.MONTHLY,
            intervalCount = null,
            customUnit = null,
            dayOfMonth = LocalDate.parse(nextDueDate).dayOfMonth.toLong(),
            weekday = null,
            nextDueDate = nextDueDate,
            amountIsVariable = false,
            amountFlexCents = null,
            dateFlexDays = null,
            leadNotificationDays = null,
        )

    private fun seedTrip(store: TestStore, id: String) {
        store.trips.create(
            TripDraft(
                id = id,
                name = id,
                type = TripType.TRIP,
                status = TripStatus.ACTIVE,
                startDate = "2026-03-01",
                endDate = "2026-03-31",
                icon = null,
                color = null,
                notes = null,
                defaultAccountId = null,
            ),
            createdAt = NOW,
        )
    }

    private fun freshStore(): TestStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        GestorDatabase.Schema.create(driver)
        val database = GestorDatabase(driver)
        return TestStore(
            driver = driver,
            accounts = AccountRepository(database.accountsQueries),
            categories = CategoryRepository(database.categoriesQueries),
            movements = MovementRepository(database.movementsQueries, database.splitsQueries),
            trips = TripRepository(database.tripsQueries),
            budgets = BudgetRepository(database.budgetsQueries),
            templates = TemplateRepository(database.templatesQueries),
        )
    }

    private class TestStore(
        private val driver: JdbcSqliteDriver,
        val accounts: AccountRepository,
        val categories: CategoryRepository,
        val movements: MovementRepository,
        val trips: TripRepository,
        val budgets: BudgetRepository,
        val templates: TemplateRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        const val FROM = "2026-03-01"
        const val TO = "2026-03-31"
    }
}
