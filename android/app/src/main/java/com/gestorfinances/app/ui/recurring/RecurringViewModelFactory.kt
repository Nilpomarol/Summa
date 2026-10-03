package com.gestorfinances.app.ui.recurring

import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.data.repository.RecurringMonth
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth
import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.gestorfinances.app.ui.common.ListPage
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.userShareCents
import com.gestorfinances.app.domain.rules.DetectedRecurringCandidate
import com.gestorfinances.app.domain.rules.DetectedTemplateAction
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.movements.cadenceLabel
import com.gestorfinances.app.ui.movements.FieldFrame
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asFigures
import com.gestorfinances.app.ui.theme.amountColor
import java.time.LocalDate

/**
 * The app-wide recurring ViewModel: the due-reminders sheet can appear over any page, so it lives
 * as long as the Activity rather than one page visit.
 */
@Composable
fun recurringViewModel(appContainer: AppContainer): RecurringViewModel = viewModel {
    RecurringViewModel(
        templateRepository = appContainer.templateRepository,
        accountRepository = appContainer.accountRepository,
        categoryRepository = appContainer.categoryRepository,
        tripRepository = appContainer.tripRepository,
        tagRepository = appContainer.tagRepository,
        movementRepository = appContainer.movementRepository,
        splitRepository = appContainer.splitRepository,
        personRepository = appContainer.personRepository,
        budgetRepository = appContainer.budgetRepository,
        notificationRefresher = appContainer.notificationCoordinator,
        financialDataRevision = appContainer.financialDataRevision,
    )
}
