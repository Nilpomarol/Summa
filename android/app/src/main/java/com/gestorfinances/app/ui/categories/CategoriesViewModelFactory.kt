package com.gestorfinances.app.ui.categories

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.theme.heroIdentityColor
import java.time.YearMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.heroTint
import androidx.compose.material.icons.outlined.Category
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.ListFilterBar
import com.gestorfinances.app.ui.common.HeroToggle
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.ListHero
import androidx.compose.runtime.saveable.rememberSaveable
import com.gestorfinances.app.ui.common.EntityListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.sortedByDisplayOrderThenName
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityBudgetBar
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.dayGroupedRows
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.draw.clip
import java.time.LocalDate
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun categoriesViewModel(appContainer: AppContainer): CategoriesViewModel = viewModel {
    CategoriesViewModel(
        categoryRepository = appContainer.categoryRepository,
        analysisRepository = appContainer.analysisRepository,
        movementRepository = appContainer.movementRepository,
        budgetRepository = appContainer.budgetRepository,
        templateRepository = appContainer.templateRepository,
    )
}
