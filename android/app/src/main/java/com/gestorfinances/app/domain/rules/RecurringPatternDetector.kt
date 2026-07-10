package com.gestorfinances.app.domain.rules

import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A single ledger movement projected for pattern-detection purposes (spec §3.10). [type] is
 * expected to already be [MovementType.EXPENSE] or [MovementType.INCOME] — the caller (movement
 * repository -> ViewModel mapping) is responsible for that filter, since transfers need a
 * destination-account dimension this detector does not track (v1 scope).
 */
data class RecurringCandidateMovement(
    val movementId: String,
    val accountId: String,
    val type: MovementType,
    val categoryId: String?,
    val name: String?,
    val payee: String?,
    val amountCents: Long,
    val date: LocalDate,
    /** Non-null means this movement is already linked to a template — excluded from grouping. */
    val templateId: String?,
)

/** The subset of an existing template needed to decide whether a detected group already has one. */
data class ExistingTemplateSignature(
    val templateId: String,
    val accountId: String,
    val type: MovementType,
    val categoryId: String?,
    val name: String?,
    val payee: String?,
)

enum class DetectedTemplateAction { NEW, UPDATE }

data class DetectedRecurringCandidate(
    val action: DetectedTemplateAction,
    /** Set iff [action] is [DetectedTemplateAction.UPDATE]. */
    val matchedTemplateId: String?,
    val accountId: String,
    val type: MovementType,
    val categoryId: String?,
    val name: String?,
    val payee: String?,
    val occurrenceCount: Int,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    /** Always WEEKLY, FORTNIGHTLY, or MONTHLY — yearly/custom cadences are out of detection scope. */
    val frequency: RecurrenceFrequency,
    /** Set iff [frequency] is MONTHLY. */
    val dayOfMonth: Int?,
    /** Set iff [frequency] is WEEKLY/FORTNIGHTLY. 0=Monday..6=Sunday (matches `template_weekday_short`). */
    val weekday: Int?,
    val amountIsVariable: Boolean,
    val amountCents: Long?,
    val amountFlexCents: Long?,
    val suggestedStatus: TemplateStatus,
    val suggestedNextDueDate: LocalDate,
    /** The movements that formed this group, so confirming the candidate can retroactively link
     * them to the resulting template — otherwise they'd stay unlinked and be re-proposed by
     * every future scan even though nothing about them changed. */
    val sourceMovementIds: List<String>,
)

/**
 * Detects likely recurring expense/income patterns from ledger history (spec §3.10: "Pattern
 * detection scans history and proposes likely recurring items ... which the user can then confirm
 * as templates"). Pure, stateless, and read-only — it only ever proposes candidates; nothing is
 * created or updated until the caller writes an accepted candidate through the normal
 * `TemplateRepository.create`/`update` path.
 */
object RecurringPatternDetector {
    private const val MIN_OCCURRENCES = 3
    private const val STALE_MULTIPLIER = 1.5

    /** Amount is treated as "variable" once the spread exceeds both this fraction of the median... */
    private const val AMOUNT_TOLERANCE_FRACTION = 0.10

    /** ...and this absolute floor (1 euro), so tiny amounts don't get a degenerate near-zero tolerance. */
    private const val AMOUNT_TOLERANCE_FLOOR_CENTS = 100L

    /** Monthly anchor-day tolerance: an occurrence may land up to this many days from the modal
     * day-of-month (month-end clamped) and still count — absorbs weekend/bank-processing drift. */
    private const val MONTH_DAY_TOLERANCE = 3

    /** Weekly/fortnightly anchor tolerance, in days, wrap-aware around the period. */
    private const val PERIOD_DAY_TOLERANCE = 1L

    /** At most one whole period (month/week/fortnight) may be skipped across the series — a single
     * missed occurrence stays tolerated; two or more do not. */
    private const val MAX_SKIPPED_PERIODS = 1

    private val WEEKLY_RANGE = 6L..8L
    private val FORTNIGHTLY_RANGE = 13L..15L
    private val MONTHLY_RANGE = 27L..31L

    fun detect(
        movements: List<RecurringCandidateMovement>,
        existingTemplates: List<ExistingTemplateSignature>,
        today: LocalDate,
    ): List<DetectedRecurringCandidate> {
        val eligible = movements
            .filter { it.templateId == null }
            .filter { !it.name.isNullOrBlank() || !it.payee.isNullOrBlank() }
        return eligible.groupBy(::groupKey).values
            .mapNotNull { group -> detectGroup(group, existingTemplates, today) }
            .sortedWith(compareBy({ it.action }, { it.name?.lowercase() ?: it.payee?.lowercase() ?: "" }))
    }

    private data class GroupKey(
        val accountId: String,
        val type: MovementType,
        val categoryId: String?,
        val normalizedNameOrPayee: String,
    )

    private fun groupKey(m: RecurringCandidateMovement): GroupKey = GroupKey(
        accountId = m.accountId,
        type = m.type,
        categoryId = m.categoryId,
        normalizedNameOrPayee = normalizeMovementName(m.name?.takeIf { it.isNotBlank() } ?: m.payee.orEmpty()),
    )

    private fun detectGroup(
        group: List<RecurringCandidateMovement>,
        existingTemplates: List<ExistingTemplateSignature>,
        today: LocalDate,
    ): DetectedRecurringCandidate? {
        if (group.size < MIN_OCCURRENCES) return null
        val sorted = group.sortedBy { it.date }
        val dates = sorted.map { it.date }
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        val frequency = classifyCadenceFamily(gaps) ?: return null
        if (!isAnchoredSeries(frequency, dates)) return null

        val last = sorted.last()
        val key = groupKey(last)
        val matched = existingTemplates.firstOrNull { sig ->
            sig.accountId == key.accountId &&
                sig.type == key.type &&
                sig.categoryId == key.categoryId &&
                normalizeMovementName(sig.name?.takeIf { it.isNotBlank() } ?: sig.payee.orEmpty()) ==
                    key.normalizedNameOrPayee
        }

        val cadenceDays = cadenceDaysFor(frequency)
        val daysSinceLast = ChronoUnit.DAYS.between(last.date, today)
        val isStale = daysSinceLast > (cadenceDays * STALE_MULTIPLIER)
        val suggestedStatus = if (isStale) TemplateStatus.ENDED else TemplateStatus.ACTIVE

        val (amountCents, amountFlexCents, isVariable) = deriveAmount(sorted.map { it.amountCents })

        val dayOfMonth = if (frequency == RecurrenceFrequency.MONTHLY) modeDayOfMonth(dates) else null
        val weekday = if (frequency != RecurrenceFrequency.MONTHLY) modeWeekday(dates) else null
        val nextDue = if (isStale) last.date else projectNextDue(frequency, dayOfMonth, last.date)

        return DetectedRecurringCandidate(
            action = if (matched != null) DetectedTemplateAction.UPDATE else DetectedTemplateAction.NEW,
            matchedTemplateId = matched?.templateId,
            accountId = key.accountId,
            type = key.type,
            categoryId = key.categoryId,
            name = last.name,
            payee = last.payee,
            occurrenceCount = sorted.size,
            firstDate = sorted.first().date,
            lastDate = last.date,
            frequency = frequency,
            dayOfMonth = dayOfMonth,
            weekday = weekday,
            amountIsVariable = isVariable,
            amountCents = amountCents,
            amountFlexCents = amountFlexCents,
            suggestedStatus = suggestedStatus,
            suggestedNextDueDate = nextDue,
            sourceMovementIds = sorted.map { it.movementId },
        )
    }

    /**
     * Picks the broad cadence family from the *median* gap (resistant to a single long gap from a
     * missed occurrence) — just enough to choose which anchor check applies next. Returns null —
     * no detection — when the median doesn't land in any known band, rather than guessing.
     */
    private fun classifyCadenceFamily(gaps: List<Long>): RecurrenceFrequency? {
        if (gaps.isEmpty()) return null
        val sortedGaps = gaps.sorted()
        val medianGap = if (sortedGaps.size % 2 == 1) {
            sortedGaps[sortedGaps.size / 2].toDouble()
        } else {
            (sortedGaps[sortedGaps.size / 2 - 1] + sortedGaps[sortedGaps.size / 2]) / 2.0
        }

        return when {
            medianGap in WEEKLY_RANGE.first.toDouble()..WEEKLY_RANGE.last.toDouble() -> RecurrenceFrequency.WEEKLY
            medianGap in FORTNIGHTLY_RANGE.first.toDouble()..FORTNIGHTLY_RANGE.last.toDouble() -> RecurrenceFrequency.FORTNIGHTLY
            medianGap in MONTHLY_RANGE.first.toDouble()..MONTHLY_RANGE.last.toDouble() -> RecurrenceFrequency.MONTHLY
            else -> null
        }
    }

    /**
     * Validates [dates] against an inferred calendar anchor instead of neighbor-to-neighbor gaps:
     * infers the expected day-of-month (monthly) or day-offset within the period (weekly/
     * fortnightly), then checks each occurrence's distance from that anchor. Unlike comparing
     * consecutive gaps against a fixed day-count band, this tolerates a billing date that drifts by
     * a day or two (weekends, bank processing) without corrupting multiple gaps at once — the
     * documented limitation this replaces (docs/13-recurring-refunds-budgets-ui.md).
     */
    private fun isAnchoredSeries(frequency: RecurrenceFrequency, dates: List<LocalDate>): Boolean =
        when (frequency) {
            RecurrenceFrequency.MONTHLY -> isValidMonthlySeries(dates)
            RecurrenceFrequency.WEEKLY -> isValidPeriodicSeries(dates, periodDays = 7L)
            RecurrenceFrequency.FORTNIGHTLY -> isValidPeriodicSeries(dates, periodDays = 14L)
            RecurrenceFrequency.YEARLY, RecurrenceFrequency.CUSTOM -> false
        }

    private fun isValidMonthlySeries(dates: List<LocalDate>): Boolean {
        val anchorDay = modeDayOfMonth(dates)
        val firstMonth = dates.first().withDayOfMonth(1)
        val monthIndices = dates.map { ChronoUnit.MONTHS.between(firstMonth, it.withDayOfMonth(1)) }
        if (!hasValidCadenceIndices(monthIndices)) return false
        return dates.all { date ->
            val effectiveAnchor = minOf(anchorDay, date.lengthOfMonth())
            abs(date.dayOfMonth - effectiveAnchor) <= MONTH_DAY_TOLERANCE
        }
    }

    private fun isValidPeriodicSeries(dates: List<LocalDate>, periodDays: Long): Boolean {
        val first = dates.first()
        val daysSinceFirst = dates.map { ChronoUnit.DAYS.between(first, it) }
        val residuals = daysSinceFirst.map { it.mod(periodDays) }
        val anchorResidual = residuals
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .first()
            .key
        val cycleIndices = daysSinceFirst.map { Math.round(it / periodDays.toDouble()) }
        if (!hasValidCadenceIndices(cycleIndices)) return false
        return residuals.all { residual ->
            val diff = abs(residual - anchorResidual)
            minOf(diff, periodDays - diff) <= PERIOD_DAY_TOLERANCE
        }
    }

    /** No two occurrences may land in the same period (index collision), and at most one gap
     * between consecutive period indices may skip a period ([MAX_SKIPPED_PERIODS]) — a single
     * missed occurrence stays tolerated; two or more, or a gap skipping more than one period at
     * once, do not. */
    private fun hasValidCadenceIndices(indices: List<Long>): Boolean {
        val sorted = indices.sorted()
        if (sorted.toSet().size != sorted.size) return false
        val diffs = sorted.zipWithNext { a, b -> b - a }
        if (diffs.any { it > 1 + MAX_SKIPPED_PERIODS }) return false
        return diffs.count { it > 1 } <= MAX_SKIPPED_PERIODS
    }

    private fun cadenceDaysFor(frequency: RecurrenceFrequency): Long = when (frequency) {
        RecurrenceFrequency.WEEKLY -> 7L
        RecurrenceFrequency.FORTNIGHTLY -> 14L
        RecurrenceFrequency.MONTHLY -> 30L
        RecurrenceFrequency.YEARLY, RecurrenceFrequency.CUSTOM -> error("out of detection scope")
    }

    /** Median amount + observed spread; falls back to "variable" once the spread is too wide to
     * be a meaningful fixed-amount + flex-margin pair. */
    private fun deriveAmount(amounts: List<Long>): Triple<Long?, Long?, Boolean> {
        val sorted = amounts.sorted()
        val median = sorted[sorted.size / 2]
        val maxDeviation = amounts.maxOf { abs(it - median) }
        val tolerance = maxOf((median * AMOUNT_TOLERANCE_FRACTION).toLong(), AMOUNT_TOLERANCE_FLOOR_CENTS)
        return if (maxDeviation <= tolerance) {
            Triple(median, maxDeviation, false)
        } else {
            Triple(null, null, true)
        }
    }

    private fun modeDayOfMonth(dates: List<LocalDate>): Int =
        dates.map { it.dayOfMonth }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .first()
            .key

    /** 0=Monday..6=Sunday, matching the `template_weekday_short` string-array order. */
    private fun modeWeekday(dates: List<LocalDate>): Int =
        dates.map { it.dayOfWeek.value - 1 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .first()
            .key

    /** Advances exactly one step past [lastDate], reusing [RecurringAdvancer]'s own clamping
     * logic rather than reimplementing month-end clamping here. */
    private fun projectNextDue(frequency: RecurrenceFrequency, dayOfMonth: Int?, lastDate: LocalDate): LocalDate {
        val rule = RecurrenceRule(frequency = frequency, dayOfMonth = dayOfMonth)
        return RecurringAdvancer.advance(rule, cursor = lastDate, today = lastDate).newCursor
    }
}
