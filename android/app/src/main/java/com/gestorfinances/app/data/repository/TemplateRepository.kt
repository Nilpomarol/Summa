package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.TemplatesQueries
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class TemplateStatus(val dbValue: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    ENDED("ended"),
    ;

    companion object {
        fun fromDb(value: String): TemplateStatus =
            entries.firstOrNull { it.dbValue == value } ?: error("Unknown template status: $value")
    }
}

/**
 * `templates.split_config` payload (shared/schemas/templates.split_config.schema.json): the
 * carried-forward split that pre-fills each shared-recurring occurrence. Persisted as JSON text.
 */
@Serializable
data class TemplateSplitConfig(
    @SerialName("entry_method") val entryMethod: String,
    val payer: String,
    val lines: List<TemplateSplitConfigLine>,
)

@Serializable
data class TemplateSplitConfigLine(
    val party: String,
    @SerialName("owed_amount_cents") val owedAmountCents: Long,
)

data class TemplateDraft(
    val id: String,
    val type: MovementType,
    val amountCents: Long?,
    val accountId: String,
    val destAccountId: String?,
    val categoryId: String?,
    val name: String?,
    val payee: String?,
    val notes: String?,
    val splitConfig: TemplateSplitConfig?,
    val frequency: RecurrenceFrequency,
    val intervalCount: Long?,
    val customUnit: CustomRecurrenceUnit?,
    val dayOfMonth: Long?,
    val weekday: Long?,
    val nextDueDate: String,
    val amountIsVariable: Boolean,
    val amountFlexCents: Long?,
    val dateFlexDays: Long?,
    val leadNotificationDays: Long?,
    val status: TemplateStatus = TemplateStatus.ACTIVE,
)

data class TemplateSummary(
    val id: String,
    val type: MovementType,
    val amountCents: Long?,
    val accountId: String,
    val accountName: String,
    val destAccountId: String?,
    val destAccountName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val name: String?,
    val payee: String?,
    val notes: String?,
    val splitConfig: TemplateSplitConfig?,
    val frequency: RecurrenceFrequency,
    val intervalCount: Long?,
    val customUnit: CustomRecurrenceUnit?,
    val dayOfMonth: Long?,
    val weekday: Long?,
    val nextDueDate: String,
    val amountIsVariable: Boolean,
    val amountFlexCents: Long?,
    val dateFlexDays: Long?,
    val leadNotificationDays: Long?,
    val status: TemplateStatus,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

class TemplateRepository(
    private val queries: TemplatesQueries,
) {
    fun listActive(): List<TemplateSummary> =
        queries.activeTemplates(::mapTemplateSummary).executeAsList()

    fun getActive(id: String): TemplateSummary? =
        queries.templateById(id, ::mapTemplateSummary).executeAsOneOrNull()

    fun create(
        draft: TemplateDraft,
        createdAt: String,
    ) {
        validate(draft)
        queries.insertTemplate(
            id = draft.id,
            type = draft.type.dbValue,
            amount_cents = draft.amountCents,
            account_id = draft.accountId,
            dest_account_id = draft.destAccountId,
            category_id = draft.categoryId,
            name = draft.name,
            payee = draft.payee,
            notes = draft.notes,
            split_config = draft.splitConfig?.encode(),
            frequency = draft.frequency.toDbValue(),
            interval_count = draft.intervalCount,
            custom_unit = draft.customUnit?.toDbValue(),
            day_of_month = draft.dayOfMonth,
            weekday = draft.weekday,
            next_due_date = draft.nextDueDate,
            amount_is_variable = draft.amountIsVariable.toDbLong(),
            amount_flex_cents = draft.amountFlexCents,
            date_flex_days = draft.dateFlexDays,
            lead_notification_days = draft.leadNotificationDays,
            status = draft.status.dbValue,
            created_at = createdAt,
            updated_at = createdAt,
        )
    }

    fun update(
        draft: TemplateDraft,
        updatedAt: String,
    ) {
        validate(draft)
        queries.updateTemplate(
            id = draft.id,
            type = draft.type.dbValue,
            amount_cents = draft.amountCents,
            account_id = draft.accountId,
            dest_account_id = draft.destAccountId,
            category_id = draft.categoryId,
            name = draft.name,
            payee = draft.payee,
            notes = draft.notes,
            split_config = draft.splitConfig?.encode(),
            frequency = draft.frequency.toDbValue(),
            interval_count = draft.intervalCount,
            custom_unit = draft.customUnit?.toDbValue(),
            day_of_month = draft.dayOfMonth,
            weekday = draft.weekday,
            next_due_date = draft.nextDueDate,
            amount_is_variable = draft.amountIsVariable.toDbLong(),
            amount_flex_cents = draft.amountFlexCents,
            date_flex_days = draft.dateFlexDays,
            lead_notification_days = draft.leadNotificationDays,
            status = draft.status.dbValue,
            updated_at = updatedAt,
        )
    }

    fun setStatus(
        id: String,
        status: TemplateStatus,
        updatedAt: String,
    ) {
        queries.setTemplateStatus(status = status.dbValue, updated_at = updatedAt, id = id)
    }

    fun advanceCursor(
        id: String,
        nextDueDate: String,
        updatedAt: String,
    ) {
        queries.advanceTemplateCursor(next_due_date = nextDueDate, updated_at = updatedAt, id = id)
    }

    fun archive(
        id: String,
        archivedAt: String,
    ) {
        queries.archiveTemplate(id = id, archived_at = archivedAt, updated_at = archivedAt)
    }
}

private val templateJson = Json { encodeDefaults = true }

private fun TemplateSplitConfig.encode(): String = templateJson.encodeToString(this)

private fun decodeSplitConfig(raw: String?): TemplateSplitConfig? =
    raw?.takeIf { it.isNotBlank() }?.let { templateJson.decodeFromString<TemplateSplitConfig>(it) }

/** Mirrors the templates CHECK constraints so app code fails fast with a clear message. */
private fun validate(draft: TemplateDraft) {
    require(draft.type == MovementType.EXPENSE || draft.type == MovementType.INCOME || draft.type == MovementType.TRANSFER) {
        "Templates support expense, income, and transfer only."
    }
    require((draft.type == MovementType.TRANSFER) == (draft.destAccountId != null)) {
        "A transfer template needs a destination account; other types must not set one."
    }
    require(draft.destAccountId == null || draft.destAccountId != draft.accountId) {
        "A transfer template's destination must differ from its origin."
    }
    require(draft.categoryId == null || draft.type == MovementType.EXPENSE || draft.type == MovementType.INCOME) {
        "Only expense and income templates carry a category."
    }
    require(draft.amountIsVariable || draft.amountCents != null) {
        "A fixed-amount template needs an amount."
    }
    require(draft.amountCents == null || draft.amountCents > 0L) {
        "Template amount must be positive."
    }
    val isCustom = draft.frequency == RecurrenceFrequency.CUSTOM
    require(isCustom == (draft.intervalCount != null && draft.customUnit != null)) {
        "Custom frequency needs an interval and unit; other frequencies must not set them."
    }
    require(draft.intervalCount == null || draft.intervalCount > 0L) {
        "Interval count must be positive."
    }
    require(draft.dayOfMonth == null || draft.dayOfMonth in 1L..31L) {
        "Day of month must be between 1 and 31."
    }
    require(draft.weekday == null || draft.weekday in 0L..6L) {
        "Weekday must be between 0 and 6."
    }
    require(draft.amountFlexCents == null || draft.amountFlexCents >= 0L) {
        "Amount flexibility must be non-negative."
    }
    require(draft.dateFlexDays == null || draft.dateFlexDays >= 0L) {
        "Date flexibility must be non-negative."
    }
    require(draft.leadNotificationDays == null || draft.leadNotificationDays >= 0L) {
        "Lead notification days must be non-negative."
    }
}

@Suppress("LongParameterList")
private fun mapTemplateSummary(
    id: String,
    type: String,
    amountCents: Long?,
    accountId: String,
    accountName: String,
    destAccountId: String?,
    destAccountName: String?,
    categoryId: String?,
    categoryName: String?,
    name: String?,
    payee: String?,
    notes: String?,
    splitConfig: String?,
    frequency: String,
    intervalCount: Long?,
    customUnit: String?,
    dayOfMonth: Long?,
    weekday: Long?,
    nextDueDate: String,
    amountIsVariable: Long,
    amountFlexCents: Long?,
    dateFlexDays: Long?,
    leadNotificationDays: Long?,
    status: String,
    createdAt: String,
    updatedAt: String,
    archivedAt: String?,
): TemplateSummary =
    TemplateSummary(
        id = id,
        type = MovementType.fromDb(type),
        amountCents = amountCents,
        accountId = accountId,
        accountName = accountName,
        destAccountId = destAccountId,
        destAccountName = destAccountName,
        categoryId = categoryId,
        categoryName = categoryName,
        name = name,
        payee = payee,
        notes = notes,
        splitConfig = decodeSplitConfig(splitConfig),
        frequency = recurrenceFrequencyFromDb(frequency),
        intervalCount = intervalCount,
        customUnit = customUnitFromDb(customUnit),
        dayOfMonth = dayOfMonth,
        weekday = weekday,
        nextDueDate = nextDueDate,
        amountIsVariable = amountIsVariable != 0L,
        amountFlexCents = amountFlexCents,
        dateFlexDays = dateFlexDays,
        leadNotificationDays = leadNotificationDays,
        status = TemplateStatus.fromDb(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )

private fun RecurrenceFrequency.toDbValue(): String =
    when (this) {
        RecurrenceFrequency.WEEKLY -> "weekly"
        RecurrenceFrequency.FORTNIGHTLY -> "fortnightly"
        RecurrenceFrequency.MONTHLY -> "monthly"
        RecurrenceFrequency.YEARLY -> "yearly"
        RecurrenceFrequency.CUSTOM -> "custom"
    }

private fun recurrenceFrequencyFromDb(value: String): RecurrenceFrequency =
    when (value) {
        "weekly" -> RecurrenceFrequency.WEEKLY
        "fortnightly" -> RecurrenceFrequency.FORTNIGHTLY
        "monthly" -> RecurrenceFrequency.MONTHLY
        "yearly" -> RecurrenceFrequency.YEARLY
        "custom" -> RecurrenceFrequency.CUSTOM
        else -> error("Unknown template frequency: $value")
    }

private fun CustomRecurrenceUnit.toDbValue(): String =
    when (this) {
        CustomRecurrenceUnit.DAYS -> "days"
        CustomRecurrenceUnit.WEEKS -> "weeks"
        CustomRecurrenceUnit.MONTHS -> "months"
        CustomRecurrenceUnit.YEARS -> "years"
    }

private fun customUnitFromDb(value: String?): CustomRecurrenceUnit? =
    when (value) {
        null -> null
        "days" -> CustomRecurrenceUnit.DAYS
        "weeks" -> CustomRecurrenceUnit.WEEKS
        "months" -> CustomRecurrenceUnit.MONTHS
        "years" -> CustomRecurrenceUnit.YEARS
        else -> error("Unknown template custom unit: $value")
    }

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L
