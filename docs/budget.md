# Budgets

## Purpose

Budgets are a central planning tool: they show whether actual personal expense is within a chosen limit and give a clearly labelled estimate for the rest of the current month. They inform spending; they never prevent a valid movement.

## Budget model

- An optional overall monthly target covers all actual personal expense, including spending without a category limit.
- A category can have an optional monthly limit, yearly limit, or both. Monthly limits reset each calendar month; yearly limits cover 1 January through 31 December and allow uneven monthly spending.
- Budget limits are managed from the Budget page. Categories remain classification only.
- Trips retain their existing independent, time-bound trip budget. Trip spending also contributes to its expense-category and overall budgets.
- There is no carry-over between months in the first version.

## Measurement and forecast

Budget actuals use canonical actual expense, net of refunds and adjusted for shared expense, rather than account outflow.

The current-month forecast is the sum of:

1. actual expense already recorded this month;
2. active fixed recurring expense occurrences due before month end that are not yet recorded; and
3. estimated variable expense for the remaining calendar days, using the average daily variable spending of the last three completed months.

The estimate excludes recurring-template occurrences to avoid counting them twice. When there is insufficient prior activity, only recorded and known recurring expense is forecast; the interface says that no behaviour estimate is available.

## Status and presentation

- **On track:** the current forecast is within the relevant limit.
- **May exceed:** actual spending is still within the limit, but the forecast exceeds it.
- **Over:** actual spending has reached or exceeds the limit.

The distinction between a fact and an estimate is always visible. Colour reinforces the named status but does not convey it alone.

## Information architecture

The Dashboard has one compact **Budget this month** card: actual versus overall monthly target, estimated month-end total, a named status, and up to three category exceptions (over or forecast to exceed). It opens Budget.

Budget is the home for planning and review. Its default view shows the current month summary, forecast breakdown, category exceptions, category limits, and an annual section for yearly limits. Users can select another month to review past actuals; forecasts apply only to the current month. The page also offers the only create/edit paths for monthly and yearly limits.
