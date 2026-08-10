package com.gestorfinances.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.onboarding_loading),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val defaultCategories = defaultCategorySeeds()
    OnboardingContent(
        state = state,
        modifier = modifier,
        onFormChange = viewModel::onFormChanged,
        onCreate = { viewModel.onCreateClicked(defaultCategories) },
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    modifier: Modifier,
    onFormChange: (OnboardingFormState) -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RootPageHeader(title = stringResource(R.string.onboarding_title))
        Text(
            text = stringResource(R.string.onboarding_body),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodyLarge,
        )
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.form.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.form.errorMessage?.let {
                    InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_account)
                }
                OutlinedTextField(
                    value = state.form.accountName,
                    onValueChange = { onFormChange(state.form.copy(accountName = it)) },
                    label = { Text(text = stringResource(R.string.account_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.form.startingBalance,
                    onValueChange = { onFormChange(state.form.copy(startingBalance = it)) },
                    label = { Text(text = stringResource(R.string.account_field_starting_balance)) },
                    prefix = { Text(text = "€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipFlowSection(label = stringResource(R.string.account_field_type)) {
                    AccountType.entries.forEach { type ->
                        FinanceFilterChip(
                            selected = state.form.accountType == type,
                            label = type.label(),
                            onClick = { onFormChange(state.form.copy(accountType = type)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.form.seedCategories,
                        onCheckedChange = { onFormChange(state.form.copy(seedCategories = it)) },
                    )
                    Text(text = stringResource(R.string.onboarding_seed_categories))
                }
                PrimaryButton(
                    text = stringResource(
                        if (state.isSaving) R.string.onboarding_saving else R.string.onboarding_create,
                    ),
                    onClick = onCreate,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun defaultCategorySeeds(): List<DefaultCategorySeed> =
    listOf(
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_housing),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.FIXED,
            icon = "home",
            color = "#C77D4A",
            displayOrder = 0,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_groceries),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "shopping_cart",
            color = "#3F9E72",
            displayOrder = 1,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_restaurants),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "restaurant",
            color = "#C9554E",
            displayOrder = 2,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_transport),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "directions_car",
            color = "#4B7DC4",
            displayOrder = 3,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_leisure),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "sports_esports",
            color = "#8A6FD1",
            displayOrder = 4,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_subscriptions),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.FIXED,
            icon = "credit_card",
            color = "#2C9AA6",
            displayOrder = 5,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_salary),
            kind = CategoryKind.INCOME,
            nature = CategoryNature.FIXED,
            icon = "payments",
            color = "#3F9E72",
            displayOrder = 6,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_other_income),
            kind = CategoryKind.INCOME,
            nature = CategoryNature.VARIABLE,
            icon = "savings",
            color = "#4B7DC4",
            displayOrder = 7,
        ),
    )
