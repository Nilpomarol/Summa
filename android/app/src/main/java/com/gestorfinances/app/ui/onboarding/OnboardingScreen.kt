package com.gestorfinances.app.ui.onboarding

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroPanel
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.theme.FinanceTheme

/** The first-run gate's ViewModel; it decides whether the app opens into onboarding. */
@Composable
fun onboardingViewModel(appContainer: AppContainer): OnboardingViewModel = viewModel {
    OnboardingViewModel(
        accountRepository = appContainer.accountRepository,
        categoryRepository = appContainer.categoryRepository,
    )
}

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

    state.loadErrorMessage?.let { message ->
        Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
            InlineFailureBanner(
                diagnostic = message,
                messageRes = R.string.failure_load_accounts,
                onRetry = viewModel::onRetry,
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

/**
 * The first screen: Summa on its forest panel saying what it is, what it keeps to itself, and the
 * one thing it needs to begin, the first account.
 */
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    modifier: Modifier,
    onFormChange: (OnboardingFormState) -> Unit,
    onCreate: () -> Unit,
) {
    val colors = FinanceTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HeroPanel(modifier = Modifier.fillMaxWidth()) {
            // The mark, oversized and faint, as on Home's hero.
            Box(modifier = Modifier.matchParentSize()) {
                Image(
                    painter = painterResource(R.drawable.summa_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.heroOnSurface.copy(alpha = 0.06f)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 12.dp)
                        .requiredHeight(220.dp),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {
                Image(
                    painter = painterResource(R.drawable.summa_mark),
                    contentDescription = null,
                    modifier = Modifier.height(40.dp),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    color = colors.heroOnSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_body),
                    color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingPoint(Icons.Outlined.Lock, stringResource(R.string.onboarding_point_private))
            OnboardingPoint(Icons.Outlined.CloudDone, stringResource(R.string.onboarding_point_backup))
            OnboardingPoint(Icons.Outlined.Insights, stringResource(R.string.onboarding_point_features))
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = stringResource(R.string.onboarding_account_title))
            state.form.errorMessage?.let {
                InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_account)
            }
            OutlinedTextField(
                value = state.form.accountName,
                onValueChange = { onFormChange(state.form.copy(accountName = it)) },
                label = { Text(text = stringResource(R.string.account_field_name)) },
                singleLine = true,
                enabled = !state.isSaving,
                isError = state.form.errorRes == R.string.account_validation_name_required,
                supportingText = state.form.errorRes?.takeIf {
                    it == R.string.account_validation_name_required
                }?.let { { Text(stringResource(it)) } },
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
                enabled = !state.isSaving,
                isError = state.form.errorRes == R.string.account_validation_starting_balance_invalid,
                supportingText = {
                    Text(stringResource(
                        state.form.errorRes?.takeIf {
                            it == R.string.account_validation_starting_balance_invalid
                        } ?: R.string.onboarding_balance_help,
                    ))
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
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

/** One thing worth knowing before starting: a quiet icon and a line. */
@Composable
private fun OnboardingPoint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
            color = "#C98553",
            displayOrder = 0,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_groceries),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "shopping_cart",
            color = "#66854B",
            displayOrder = 1,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_restaurants),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "restaurant",
            color = "#B5614A",
            displayOrder = 2,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_transport),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "directions_car",
            color = "#4E6FA3",
            displayOrder = 3,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_leisure),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.VARIABLE,
            icon = "sports_esports",
            color = "#8574B3",
            displayOrder = 4,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_subscriptions),
            kind = CategoryKind.EXPENSE,
            nature = CategoryNature.FIXED,
            icon = "credit_card",
            color = "#3E8588",
            displayOrder = 5,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_salary),
            kind = CategoryKind.INCOME,
            nature = CategoryNature.FIXED,
            icon = "payments",
            color = "#66854B",
            displayOrder = 6,
        ),
        DefaultCategorySeed(
            name = stringResource(R.string.category_seed_other_income),
            kind = CategoryKind.INCOME,
            nature = CategoryNature.VARIABLE,
            icon = "savings",
            color = "#4E6FA3",
            displayOrder = 7,
        ),
    )
