package com.gestorfinances.app.ui.budgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.R
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import com.gestorfinances.app.ui.common.formatShortMonth
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.gestorfinances.app.data.repository.PlanIncome
import com.gestorfinances.app.data.repository.RecentSpending
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetMonthPlan
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.ui.trips.icon
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroMonthPicker
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.inPickerHierarchyOrder
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.heroIdentityColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.YearMonth
import kotlin.math.abs

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun budgetsViewModel(appContainer: AppContainer): BudgetsViewModel = viewModel {
    BudgetsViewModel(
        budgetRepository = appContainer.budgetRepository,
        categoryRepository = appContainer.categoryRepository,
        tripRepository = appContainer.tripRepository,
        templateRepository = appContainer.templateRepository,
        analysisRepository = appContainer.analysisRepository,
        notificationRefresher = appContainer.notificationCoordinator,
    )
}

/** The Més Budgets page. */
@Composable
fun BudgetsPage(
    appContainer: AppContainer,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onBack: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenCategory: (categoryId: String) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    BudgetsScreen(
        viewModel = budgetsViewModel(appContainer),
        onBack = onBack,
        onOpenRecurring = onOpenRecurring,
        onOpenGoals = onOpenGoals,
        onOpenCategory = onOpenCategory,
        dataVersion = dataVersion,
        onDeleteCommitted = onDeleteCommitted,
        modifier = modifier,
    )
}
