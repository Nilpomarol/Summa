package com.gestorfinances.app.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisSpendingByKind
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.ui.analysis.components.AmountLine
import com.gestorfinances.app.ui.analysis.components.MonthBars
import com.gestorfinances.app.ui.analysis.components.PaceChart
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroMonthPicker
import com.gestorfinances.app.ui.common.HeroToggle
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.navigation.Route
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** This page visit's ViewModel, starting from the account or category [route] was opened with. */
@Composable
fun analysisViewModel(appContainer: AppContainer, route: Route.Analysis): AnalysisViewModel = viewModel {
    AnalysisViewModel(
        analysisRepository = appContainer.analysisRepository,
        accountRepository = appContainer.accountRepository,
        categoryRepository = appContainer.categoryRepository,
    ).apply {
        if (route.accountId != null && route.accountName != null) setAccountFilter(route.accountId, route.accountName)
        route.categoryId?.let(::openCategoryOnLoad)
    }
}
