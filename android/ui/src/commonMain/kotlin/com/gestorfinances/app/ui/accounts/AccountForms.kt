package com.gestorfinances.app.ui.accounts

import com.gestorfinances.app.ui.common.OpenDialogs
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_type_contribution
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.ui.theme.heroIdentityColor
import java.time.YearMonth
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.CreatePersonDialog
import com.gestorfinances.app.ui.movements.personInitial
import com.gestorfinances.app.ui.movements.ShareField
import com.gestorfinances.app.ui.movements.PersonMonogram
import com.gestorfinances.app.ui.common.SegmentedControl
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.data.repository.ContributionDirection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.gestorfinances.app.ui.common.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.gestorfinances.app.ui.movements.AppTextField
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.account_archive_active_templates_warning
import com.gestorfinances.ui.resources.account_archive_confirm_title
import com.gestorfinances.ui.resources.account_archive_goals_warning
import com.gestorfinances.ui.resources.account_archive_pause_and_archive
import com.gestorfinances.ui.resources.account_archive_warning
import com.gestorfinances.ui.resources.account_contribution_changes_both
import com.gestorfinances.ui.resources.account_contribution_changes_shared
import com.gestorfinances.ui.resources.account_contribution_effect
import com.gestorfinances.ui.resources.account_contribution_external_source
import com.gestorfinances.ui.resources.account_contribution_member
import com.gestorfinances.ui.resources.account_contribution_save
import com.gestorfinances.ui.resources.account_contribution_source
import com.gestorfinances.ui.resources.account_contribution_title
import com.gestorfinances.ui.resources.account_field_default
import com.gestorfinances.ui.resources.account_field_low_balance_threshold
import com.gestorfinances.ui.resources.account_field_name
import com.gestorfinances.ui.resources.account_field_starting_balance
import com.gestorfinances.ui.resources.account_field_type
import com.gestorfinances.ui.resources.account_form_edit_title
import com.gestorfinances.ui.resources.account_form_new_title
import com.gestorfinances.ui.resources.account_member_column_balance
import com.gestorfinances.ui.resources.account_member_expense_split
import com.gestorfinances.ui.resources.account_member_expense_split_help
import com.gestorfinances.ui.resources.account_member_owner
import com.gestorfinances.ui.resources.account_member_ownership
import com.gestorfinances.ui.resources.account_member_ownership_help
import com.gestorfinances.ui.resources.account_member_ownership_preview
import com.gestorfinances.ui.resources.account_member_separate_expenses
import com.gestorfinances.ui.resources.account_member_total_excess
import com.gestorfinances.ui.resources.account_member_total_invalid
import com.gestorfinances.ui.resources.account_member_total_remaining
import com.gestorfinances.ui.resources.account_members_title
import com.gestorfinances.ui.resources.account_save_changes
import com.gestorfinances.ui.resources.account_save_new
import com.gestorfinances.ui.resources.account_shared_toggle
import com.gestorfinances.ui.resources.account_shared_toggle_help
import com.gestorfinances.ui.resources.account_withdrawal_destination
import com.gestorfinances.ui.resources.account_withdrawal_effect
import com.gestorfinances.ui.resources.account_withdrawal_member
import com.gestorfinances.ui.resources.account_withdrawal_save
import com.gestorfinances.ui.resources.account_withdrawal_title
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_list_and
import com.gestorfinances.ui.resources.failure_save_account
import com.gestorfinances.ui.resources.failure_save_movement
import com.gestorfinances.ui.resources.movement_create_person_title
import com.gestorfinances.ui.resources.movement_field_date
import com.gestorfinances.ui.resources.movement_field_name
import com.gestorfinances.ui.resources.movement_field_notes
import com.gestorfinances.ui.resources.split_add_person
import com.gestorfinances.ui.resources.split_method_equal
import com.gestorfinances.ui.resources.split_method_percentage
import com.gestorfinances.ui.resources.split_remove_person
import org.jetbrains.compose.resources.StringResource
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.FormReveal
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.common.formatBasisPointsCompact
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountGoalReservation
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.common.AccountIconPalette
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.data.repository.AccountMember
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.time.LocalDate
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/** The account create/edit sheet over whichever account page opened it. */
@Composable
fun AccountFormSheet(state: AccountsUiState, viewModel: AccountsViewModel) {
    var appearanceOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-account" },
        changed = { initial, current -> initial.comparable() != current.comparable() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) Res.string.account_form_new_title else Res.string.account_form_edit_title) },
        saveLabel = { stringResource(if (it.id == null) Res.string.account_save_new else Res.string.account_save_changes) },
    ) { form ->
        AccountFormFields(
            form = form,
            people = state.people,
            appearanceOpen = appearanceOpen,
            onToggleAppearance = { appearanceOpen = !appearanceOpen },
            onFormChange = { viewModel.onFormChanged(it.copy(errorRes = null, errorField = null, errorMessage = null)) },
            onOwnershipChange = viewModel::onOwnershipChanged,
            onCreatePerson = viewModel::onCreatePersonForAccount,
            onSave = viewModel::onSaveClicked,
        )
    }
}

/** What a discard check compares: validation and the open details group are not edits. */
fun AccountFormState.comparable(): AccountFormState =
    copy(showAdvanced = false, errorRes = null, errorField = null, errorMessage = null)

@Composable
fun AccountArchiveDialog(
    candidate: AccountArchiveCandidate,
    viewModel: AccountsViewModel,
    onArchived: DeleteUndoHandler,
) {
    OpenDialogs.Track()
    AlertDialog(
        onDismissRequest = viewModel::onArchiveDismissed,
        title = { Text(text = stringResource(Res.string.account_archive_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (candidate.activeTemplateCount == 0) {
                        stringResource(Res.string.account_archive_warning)
                    } else {
                        pluralStringResource(
                            Res.plurals.account_archive_active_templates_warning,
                            candidate.activeTemplateCount,
                            candidate.activeTemplateCount,
                        )
                    },
                )
                if (candidate.goalNames.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            Res.plurals.account_archive_goals_warning,
                            candidate.goalNames.size,
                            joinNames(candidate.goalNames),
                        ),
                        color = FinanceTheme.colors.alert,
                    )
                }
            }
        },
        confirmButton = {
            DestructiveTextButton(onClick = { viewModel.onArchiveConfirmed(onSuccess = onArchived) }) {
                Text(
                    text = if (candidate.activeTemplateCount == 0) {
                        stringResource(Res.string.common_archive)
                    } else {
                        stringResource(Res.string.account_archive_pause_and_archive)
                    },
                )
            }
        },
        dismissButton = {
            AppTextButton(onClick = viewModel::onArchiveDismissed) {
                Text(text = stringResource(Res.string.common_cancel))
            }
        },
    )
}

/** "A", "A i B", "A, B i C". */
@Composable
fun joinNames(names: List<String>): String =
    if (names.size == 1) names.single()
    else stringResource(Res.string.common_list_and, names.dropLast(1).joinToString(", "), names.last())

/** A shared account's contribution/withdrawal sheet over whichever account page opened it. */
@Composable
fun ContributionFormSheet(state: AccountsUiState, viewModel: AccountsViewModel) {
    var detailsOpen by rememberSaveable(state.contributionForm?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.contributionForm,
        key = { it.id ?: "new-contribution:${it.sharedAccountId}" },
        changed = { initial, current ->
            initial.copy(errorRes = null, errorMessage = null) != current.copy(errorRes = null, errorMessage = null)
        },
        onDiscard = viewModel::onContributionDismissed,
        onSave = viewModel::onContributionSaveClicked,
        title = { form ->
            stringResource(
                if (form.direction == ContributionDirection.OUT) Res.string.account_withdrawal_title else Res.string.account_contribution_title,
                state.accounts.firstOrNull { it.id == form.sharedAccountId }?.name.orEmpty(),
            )
        },
        saveLabel = { form ->
            stringResource(
                if (form.direction == ContributionDirection.OUT) Res.string.account_withdrawal_save else Res.string.account_contribution_save,
            )
        },
    ) { form ->
        ContributionFormFields(
            form = form,
            accounts = state.accounts,
            people = state.people.filter { person ->
                state.accounts.firstOrNull { it.id == form.sharedAccountId }
                    ?.members?.any { it.personId == person.id } == true
            },
            detailsOpen = detailsOpen,
            onToggleDetails = { detailsOpen = !detailsOpen },
            onFormChange = { viewModel.onContributionFormChanged(it.copy(errorRes = null, errorMessage = null)) },
        )
    }
}

/**
 * The account form's fields: its tile and name, type and starting balance, whether it is the
 * default, and whether it is shared (which opens who owns and pays what), with the low-balance
 * alert under Més detalls.
 */
@Composable
fun AccountFormFields(
    form: AccountFormState,
    people: List<PersonSummary>,
    appearanceOpen: Boolean,
    onToggleAppearance: () -> Unit,
    onFormChange: (AccountFormState) -> Unit,
    onOwnershipChange: (AccountOwnershipKind) -> Unit,
    onCreatePerson: (String) -> Unit,
    onSave: () -> Unit,
) {
    val errorText = form.errorRes?.let { stringResource(it) }
    form.errorMessage?.let {
        InlineFailureBanner(diagnostic = it, messageRes = Res.string.failure_save_account)
    }
    EntityFormHeader(
        icon = form.iconKey?.let(::accountIcon) ?: accountTypeIcon(form.type),
        color = categoryColor(form.colorHex),
        name = form.name,
        onNameChange = { onFormChange(form.copy(name = it)) },
        nameLabel = stringResource(Res.string.account_field_name),
        onTileClick = onToggleAppearance,
        nameError = errorText?.takeIf { form.errorField == AccountFormField.NAME },
    )
    EntityAppearancePickers(
        open = appearanceOpen,
        colorHex = form.colorHex,
        onColor = { onFormChange(form.copy(colorHex = it)) },
        iconOptions = AccountIconPalette,
        iconKey = form.iconKey,
        onIcon = { onFormChange(form.copy(iconKey = it)) },
    )
    FormSelect(
        label = stringResource(Res.string.account_field_type),
        options = AccountType.entries.map { SelectOption(id = it.name, label = it.label()) },
        selectedId = form.type.name,
        onSelect = { id -> id?.let { onFormChange(form.copy(type = AccountType.valueOf(it))) } },
    )
    run {
        val startingBalanceError = form.errorField == AccountFormField.STARTING_BALANCE
        AppTextField(
            value = form.startingBalance,
            onValueChange = { onFormChange(form.copy(startingBalance = it)) },
            label = { Text(text = stringResource(Res.string.account_field_starting_balance)) },
            prefix = { Text(text = "€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(onSave),
            singleLine = true,
            isError = startingBalanceError,
            supportingText = errorText?.takeIf { startingBalanceError }?.let { { Text(text = it) } },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(startingBalanceError),
        )
    }
    FormToggleRow(
        label = stringResource(Res.string.account_field_default),
        checked = form.isDefault,
        onCheckedChange = { onFormChange(form.copy(isDefault = it)) },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.account_shared_toggle), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.account_shared_toggle_help), style = MaterialTheme.typography.bodySmall, color = FinanceTheme.colors.mutedText)
        }
        FinanceSwitch(
            checked = form.ownershipKind == AccountOwnershipKind.SHARED,
            onCheckedChange = { onOwnershipChange(if (it) AccountOwnershipKind.SHARED else AccountOwnershipKind.PERSONAL) },
        )
    }
    if (form.errorField == AccountFormField.OWNERSHIP && errorText != null) {
        Text(text = errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    FormReveal(visible = form.ownershipKind == AccountOwnershipKind.SHARED) {
        AccountMembersSection(form = form, people = people, onFormChange = onFormChange, onCreatePerson = onCreatePerson)
    }

    // A threshold already set stays in view.
    val thresholdShown = form.showAdvanced || form.lowBalanceThreshold.isNotBlank()
    FormDisclosure(open = thresholdShown, onToggle = { onFormChange(form.copy(showAdvanced = !form.showAdvanced)) }) {
        val thresholdError = form.errorField == AccountFormField.LOW_BALANCE_THRESHOLD
        AppTextField(
            value = form.lowBalanceThreshold,
            onValueChange = { onFormChange(form.copy(lowBalanceThreshold = it)) },
            label = { Text(text = stringResource(Res.string.account_field_low_balance_threshold)) },
            prefix = { Text(text = "€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(onSave),
            singleLine = true,
            isError = thresholdError,
            supportingText = errorText?.takeIf { thresholdError }?.let { { Text(text = it) } },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().scrollToWhen(thresholdError),
        )
    }
}

/**
 * Who owns the shared account and who pays how much by default, in the split editor's shape: a row
 * per member with their share and a way out, a row to add someone, then equal parts or typed
 * percentages (one figure each, or one for the balance and one for expenses), and a line only when
 * a column does not reach 100%.
 */
@Composable
fun AccountMembersSection(
    form: AccountFormState,
    people: List<PersonSummary>,
    onFormChange: (AccountFormState) -> Unit,
    onCreatePerson: (String) -> Unit,
) {
    val colors = FinanceTheme.colors
    val members = form.members
    var custom by rememberSaveable(form.id) { mutableStateOf(members != members.splitEqually()) }
    var separate by rememberSaveable(form.id) {
        mutableStateOf(members.any { it.enabled && it.ownershipPercent != it.defaultExpensePercent })
    }
    var creatingPerson by remember { mutableStateOf(false) }
    val twoColumns = custom && separate
    fun change(next: List<AccountMemberFormState>) = onFormChange(form.copy(members = next))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.account_members_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(Res.string.account_member_ownership_help),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedText,
        )
        Column {
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(modifier = Modifier.weight(1f))
                    listOf(Res.string.account_member_column_balance, Res.string.account_member_expense_split).forEach {
                        Text(
                            text = stringResource(it),
                            modifier = Modifier.width(MEMBER_SHARE_WIDTH),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText,
                        )
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                }
            }
            members.forEachIndexed { index, member ->
                if (!member.enabled) return@forEachIndexed
                val name = member.name.ifBlank { stringResource(Res.string.account_member_owner) }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PersonMonogram(
                        label = personInitial(name),
                        colorHex = people.firstOrNull { it.id == member.personId }?.color,
                        size = 32.dp,
                    )
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        !custom -> Text(
                            text = listOf(member).basisPointTotal { it.ownershipPercent }?.let(::formatBasisPointsCompact).orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        !separate -> ShareField(
                            value = member.ownershipPercent,
                            onValueChange = { change(members.withPercent(index, it, ownership = true, expenses = true)) },
                            suffix = "%",
                        )
                        else -> {
                            ShareField(
                                value = member.ownershipPercent,
                                onValueChange = { change(members.withPercent(index, it, ownership = true, expenses = false)) },
                                suffix = "%",
                                width = MEMBER_SHARE_WIDTH,
                            )
                            ShareField(
                                value = member.defaultExpensePercent,
                                onValueChange = { change(members.withPercent(index, it, ownership = false, expenses = true)) },
                                suffix = "%",
                                width = MEMBER_SHARE_WIDTH,
                            )
                        }
                    }
                    if (member.personId != null) {
                        AppIconButton(onClick = { change(members.withMember(index, enabled = false, equal = !custom)) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(Res.string.split_remove_person),
                                tint = colors.mutedText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
                HorizontalDivider(color = colors.cardBorder)
            }
            Spacer(modifier = Modifier.height(10.dp))
            FormSelect(
                label = "",
                options = buildList {
                    members.forEach { member ->
                        if (member.enabled || member.personId == null) return@forEach
                        add(
                            SelectOption(
                                id = member.personId,
                                label = member.name,
                                leading = {
                                    PersonMonogram(
                                        label = personInitial(member.name),
                                        colorHex = people.firstOrNull { it.id == member.personId }?.color,
                                        size = 24.dp,
                                    )
                                },
                            ),
                        )
                    }
                    add(
                        SelectOption(
                            id = MEMBER_CREATE_PERSON,
                            label = stringResource(Res.string.movement_create_person_title),
                            leading = {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        ),
                    )
                },
                selectedId = null,
                onSelect = { id ->
                    when (id) {
                        MEMBER_CREATE_PERSON -> creatingPerson = true
                        null -> Unit
                        else -> change(members.withMember(members.indexOfFirst { it.personId == id }, enabled = true, equal = !custom))
                    }
                },
                placeholder = stringResource(Res.string.split_add_person),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SegmentedControl(
            options = listOf(false, true),
            selected = custom,
            label = { stringResource(if (it) Res.string.split_method_percentage else Res.string.split_method_equal) },
            onSelect = {
                custom = it
                if (!it) change(members.splitEqually())
            },
        )
        if (custom) {
            FormToggleRow(
                label = stringResource(Res.string.account_member_separate_expenses),
                checked = separate,
                onCheckedChange = { on ->
                    separate = on
                    if (!on) change(members.map { it.copy(defaultExpensePercent = it.ownershipPercent) })
                },
            )
            if (separate) {
                Text(
                    text = stringResource(Res.string.account_member_expense_split_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                )
            }
        }

        MemberPercentWarning(stringResource(Res.string.account_member_ownership), members.basisPointTotal { it.ownershipPercent })
        MemberPercentWarning(stringResource(Res.string.account_member_expense_split), members.basisPointTotal { it.defaultExpensePercent })
        OwnershipPreview(form)
        if (form.errorField == AccountFormField.MEMBERS && form.errorRes != null) {
            Text(stringResource(form.errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (creatingPerson) {
        CreatePersonDialog(
            onConfirm = { name ->
                creatingPerson = false
                onCreatePerson(name)
            },
            onDismiss = { creatingPerson = false },
        )
    }
}

const val MEMBER_CREATE_PERSON = "__create_person__"
private val MEMBER_SHARE_WIDTH = 88.dp

/**
 * What the owner's percentage means for this account, as a share of its balance. The euro value is
 * left to the canonical view, which rounds it, rather than being worked out again here.
 */
@Composable
fun OwnershipPreview(form: AccountFormState) {
    val ownerBasisPoints = form.members.filter { it.personId == null }.basisPointTotal { it.ownershipPercent } ?: return
    val balanceCents = form.currentBalanceCents ?: parseEuroCents(form.startingBalance, allowNegative = true) ?: return
    Text(
        text = stringResource(
            Res.string.account_member_ownership_preview,
            formatEuroCents(balanceCents),
            formatBasisPointsCompact(ownerBasisPoints),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = FinanceTheme.colors.mutedText,
    )
}

/** Said only when a percentage column does not reach exactly 100%: a balanced one needs no comment. */
@Composable
fun MemberPercentWarning(label: String, basisPoints: Long?) {
    val text = when {
        basisPoints == null -> stringResource(Res.string.account_member_total_invalid)
        basisPoints == 10_000L -> return
        basisPoints < 10_000L -> stringResource(Res.string.account_member_total_remaining, formatBasisPointsCompact(10_000L - basisPoints))
        else -> stringResource(Res.string.account_member_total_excess, formatBasisPointsCompact(basisPoints - 10_000L))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = FinanceTheme.colors.alert,
            modifier = Modifier.size(16.dp),
        )
        Text(text = "$label: $text", color = FinanceTheme.colors.alert, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * A contribution or withdrawal laid out like the movement form: the shared account's tile beside
 * the concept over the amount, what it does to each balance, who and where, then the concept and
 * notes under Més detalls.
 */
@Composable
fun ContributionFormFields(
    form: ContributionFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    detailsOpen: Boolean,
    onToggleDetails: () -> Unit,
    onFormChange: (ContributionFormState) -> Unit,
) {
    val shared = accounts.firstOrNull { it.id == form.sharedAccountId }
    val sharedAccountName = shared?.name.orEmpty()
    val withdrawal = form.direction == ContributionDirection.OUT
    form.errorMessage?.let { InlineFailureBanner(diagnostic = it, messageRes = Res.string.failure_save_movement) }
    MovementFormHeader(
        icon = shared?.let { it.icon?.let(::accountIcon) ?: accountTypeIcon(it.type) } ?: accountIcon(null),
        iconColor = categoryColor(shared?.color),
        title = form.name.ifBlank { sharedAccountName },
        titleIsPlaceholder = form.name.isBlank(),
        amount = form.amount,
        onAmountChange = { onFormChange(form.copy(amount = it)) },
        amountColor = MaterialTheme.colorScheme.onSurface,
        amountError = form.errorRes?.let { stringResource(it) },
    )
    Text(
        text = stringResource(if (withdrawal) Res.string.account_withdrawal_effect else Res.string.account_contribution_effect),
        style = MaterialTheme.typography.bodySmall,
        color = FinanceTheme.colors.mutedText,
    )
    // What each balance does, as the amount itself: the resulting balances and the owner's share
    // stay with the canonical views rather than being worked out here.
    parseEuroCents(form.amount, allowNegative = false)?.takeIf { it > 0 }?.let { cents ->
        val signed = { value: Long -> formatEuroCents(value).let { if (value > 0) "+$it" else it } }
        val sharedChange = signed(if (withdrawal) -cents else cents)
        val ownerAccountName = accounts.firstOrNull { it.id == form.sourceAccountId }?.name
            ?.takeIf { form.personId == null }
        Text(
            text = if (ownerAccountName != null) {
                stringResource(
                    Res.string.account_contribution_changes_both,
                    sharedAccountName,
                    sharedChange,
                    ownerAccountName,
                    signed(if (withdrawal) cents else -cents),
                )
            } else {
                stringResource(Res.string.account_contribution_changes_shared, sharedAccountName, sharedChange)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FormSelect(
            label = stringResource(if (withdrawal) Res.string.account_withdrawal_member else Res.string.account_contribution_member),
            options = buildList {
                add(SelectOption(id = null, label = stringResource(Res.string.account_member_owner)))
                people.forEach { add(SelectOption(id = it.id, label = it.name)) }
            },
            selectedId = form.personId,
            onSelect = { onFormChange(form.copy(personId = it, sourceAccountId = null)) },
            modifier = Modifier.weight(1f),
        )
        FormDatePicker(
            label = stringResource(Res.string.movement_field_date),
            date = form.date,
            onDateChange = { onFormChange(form.copy(date = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (form.personId == null) {
        val noSource = stringResource(Res.string.account_contribution_external_source)
        FormSelect(
            label = stringResource(if (withdrawal) Res.string.account_withdrawal_destination else Res.string.account_contribution_source),
            options = buildList {
                add(SelectOption(id = null, label = noSource))
                accounts.filter { it.id != form.sharedAccountId && it.ownershipKind == AccountOwnershipKind.PERSONAL }
                    .forEach { add(SelectOption(id = it.id, label = it.name)) }
            },
            selectedId = form.sourceAccountId,
            onSelect = { onFormChange(form.copy(sourceAccountId = it)) },
            placeholder = noSource,
        )
    }
    // What is already written stays in view.
    val detailsShown = detailsOpen || form.name.isNotBlank() || form.notes.isNotBlank()
    FormDisclosure(open = detailsShown, onToggle = onToggleDetails) {
        AppTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text(stringResource(Res.string.movement_field_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(stringResource(Res.string.movement_field_notes)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
