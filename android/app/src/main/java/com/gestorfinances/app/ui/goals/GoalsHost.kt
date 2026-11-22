package com.gestorfinances.app.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.GoalAllocation
import com.gestorfinances.app.data.repository.GoalStatus
import com.gestorfinances.app.data.repository.GoalSummary
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.domain.rules.GoalProgress
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.LocalDate

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun goalsViewModel(appContainer: AppContainer): GoalsViewModel = viewModel {
    GoalsViewModel(
        goalRepository = appContainer.goalRepository,
        accountRepository = appContainer.accountRepository,
    )
}

/** The reserve/release sheet over a goal page, guarding unsaved changes. */
@Composable
internal fun AllocationFormHost(
    form: AllocationFormState,
    state: GoalsUiState,
    viewModel: GoalsViewModel,
    onDelete: (GoalAllocation) -> Unit,
) {
    val requestFormDismissal = rememberFormDismissGuard(
        formKey = form.id ?: "new-allocation",
        currentValue = form,
        hasMeaningfulChanges = { initial, current -> initial.compareValues() != current.compareValues() },
        onDiscard = viewModel::onAllocationFormDismissed,
    )
    BackHandler(onBack = requestFormDismissal)
    val editing = state.detail?.allocations?.firstOrNull { it.id == form.id }
    AllocationFormSheet(
        form = form,
        goal = state.goals.firstOrNull { it.id == form.goalId },
        accounts = state.accounts.filter { it.id !in state.dedicatedAccountIds },
        accountAllocations = state.accountAllocations,
        reservations = state.detail?.reservations.orEmpty(),
        editingAllocation = editing,
        onFormChange = viewModel::onAllocationFormChanged,
        onDismiss = requestFormDismissal,
        onSave = { viewModel.onSaveAllocationClicked() },
        onConfirmOverAllocation = { viewModel.onSaveAllocationClicked(confirmOverAllocation = true) },
        onDelete = editing?.let { { onDelete(it) } },
    )
}
