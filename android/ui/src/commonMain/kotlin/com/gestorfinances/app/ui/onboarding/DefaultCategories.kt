package com.gestorfinances.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.category_seed_groceries
import com.gestorfinances.ui.resources.category_seed_housing
import com.gestorfinances.ui.resources.category_seed_leisure
import com.gestorfinances.ui.resources.category_seed_other_income
import com.gestorfinances.ui.resources.category_seed_restaurants
import com.gestorfinances.ui.resources.category_seed_salary
import com.gestorfinances.ui.resources.category_seed_subscriptions
import com.gestorfinances.ui.resources.category_seed_transport
import org.jetbrains.compose.resources.stringResource

/** The categories a new database starts with, named in the app's language. */
@Composable
fun defaultCategorySeeds(): List<DefaultCategorySeed> =
    listOf(
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_housing),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.FIXED,
            icon = "home",
            color = "#C98553",
            displayOrder = 0,
        ),
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_groceries),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "shopping_cart",
            color = "#66854B",
            displayOrder = 1,
        ),
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_restaurants),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "restaurant",
            color = "#B5614A",
            displayOrder = 2,
        ),
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_transport),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "directions_car",
            color = "#4E6FA3",
            displayOrder = 3,
        ),
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_leisure),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "sports_esports",
            color = "#8574B3",
            displayOrder = 4,
        ),
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_subscriptions),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.FIXED,
            icon = "credit_card",
            color = "#3E8588",
            displayOrder = 5,
        ),
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_salary),
            kind = CategoryKind.INCOME,
            nature = CategoryNature.FIXED,
            icon = "payments",
            color = "#66854B",
            displayOrder = 6,
        ),
        DefaultCategorySeed(
            name = stringResource(Res.string.category_seed_other_income),
            kind = CategoryKind.INCOME,
            nature = CategoryNature.VARIABLE,
            icon = "savings",
            color = "#4E6FA3",
            displayOrder = 7,
        ),
    )
