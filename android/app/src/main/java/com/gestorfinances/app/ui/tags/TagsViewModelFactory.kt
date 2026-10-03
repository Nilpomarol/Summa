package com.gestorfinances.app.ui.tags

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.IdentityIconTile
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import androidx.compose.runtime.saveable.rememberSaveable
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import com.gestorfinances.app.ui.trips.label
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun tagsViewModel(appContainer: AppContainer): TagsViewModel = viewModel {
    TagsViewModel(
        tagRepository = appContainer.tagRepository,
        tripRepository = appContainer.tripRepository,
        categoryRepository = appContainer.categoryRepository,
        movementRepository = appContainer.movementRepository,
        tripAnalysisRepository = appContainer.tripAnalysisRepository,
    )
}
