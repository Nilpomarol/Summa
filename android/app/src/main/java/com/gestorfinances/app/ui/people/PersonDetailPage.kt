package com.gestorfinances.app.ui.people

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.domain.rules.SettlementScope
import com.gestorfinances.app.ui.common.ListPage
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.gestorfinances.app.ui.common.ListFilterBar
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.ListHero
import kotlin.math.abs
import com.gestorfinances.app.ui.common.EntityListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonBalanceItemType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.EntityColorPalette
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.dayGroupedRows
import com.gestorfinances.app.ui.theme.onIdentityColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import androidx.compose.material.icons.outlined.Add
import java.time.LocalDate
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.AccountSelect
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun peopleViewModel(appContainer: AppContainer): PeopleViewModel = viewModel {
    PeopleViewModel(
        personRepository = appContainer.personRepository,
        movementRepository = appContainer.movementRepository,
        accountRepository = appContainer.accountRepository,
        notificationRefresher = appContainer.notificationCoordinator,
    )
}

/** A person's own page: balance, actions, and the movements behind the balance. */
@Composable
fun PersonDetailPage(
    personId: String,
    onBack: () -> Unit,
    viewModel: PeopleViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenDebtSource: (String) -> Unit,
    onAddDebtForPerson: (PersonSummary) -> Unit,
    onMessageCopied: (String) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, personId, dataVersion) {
        viewModel.onPersonDetailOpened(personId)
    }

    val detail = state.detail
    val settlementForm = state.settlementForm
    // Settling up is a sheet over the person's page, like every other "record something".
    if (settlementForm != null && detail != null) {
        val requestSettlementDismissal = rememberFormDismissGuard(
            formKey = settlementForm.personId,
            currentValue = settlementForm,
            hasMeaningfulChanges = { initial, current ->
                initial.copy(errorRes = null, errorField = null, errorMessage = null) !=
                    current.copy(errorRes = null, errorField = null, errorMessage = null)
            },
            onDiscard = viewModel::onSettlementDismissed,
        )
        BackHandler(onBack = requestSettlementDismissal)
        AppModalBottomSheet(onDismissRequest = requestSettlementDismissal) {
            SettlementSheetContent(
                form = settlementForm,
                tileColor = detail.person.color?.let(::categoryColor) ?: personFallbackColor(detail.person.id),
                accounts = state.accounts,
                onFormChange = viewModel::onSettlementFormChanged,
                onSave = viewModel::onSettlementSaveClicked,
            )
        }
    }
    when {
        detail != null -> PersonDetailScreen(
            detail = detail,
            // An action that fails here (archiving, say) reports on the list's state: show it too.
            errorMessage = state.detailErrorMessage ?: state.errorMessage,
            onBack = onBack,
            onRetry = { viewModel.onPersonDetailOpened(personId) },
            onOpenDebtSource = onOpenDebtSource,
            onAddPaidByPerson = { onAddDebtForPerson(detail.person) },
            onSettleUp = { viewModel.onSettleUpClicked(detail.person) },
            onEdit = { viewModel.onEditClicked(detail.person) },
            onArchive = { viewModel.onArchiveClicked(detail.person) },
            onCopyMessageClicked = viewModel::onCopyMessageClicked,
            onCopyMessageHandled = viewModel::onCopyMessageHandled,
            onMessageCopied = onMessageCopied,
            modifier = modifier,
        )
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeaderRow(onBack = onBack)
            val errorMessage = state.detailErrorMessage
            if (errorMessage != null) {
                InlineFailureBanner(
                    diagnostic = errorMessage,
                    messageRes = R.string.failure_load_people,
                    onRetry = { viewModel.onPersonDetailOpened(personId) },
                )
            } else {
                Text(
                    text = stringResource(R.string.person_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    PersonFormHost(state = state, viewModel = viewModel)

    state.archiveCandidate?.let { person ->
        PersonArchiveDialog(
            person = person,
            viewModel = viewModel,
            onArchived = { undo ->
                onDeleteCommitted(undo)
                onBack()
            },
        )
    }
}
