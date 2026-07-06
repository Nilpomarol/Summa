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
    private const val MIN_GAP_MATCH_FRACTION = 0.8
    private const val STALE_MULTIPLIER = 1.5

    /** Amount is treated as "variable" once the spread exceeds both this fraction of the median... */
    private const val AMOUNT_TOLERANCE_FRACTION = 0.10

    /** ...and this absolute floor (1 euro), so tiny amounts don't get a degenerate near-zero tolerance. */
    private const val AMOUNT_TOLERANCE_FLOOR_CENTS = 100L

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
        val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val frequency = classifyCadence(gaps) ?: return null

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

        val dayOfMonth = if (frequency == RecurrenceFrequency.MONTHLY) modeDayOfMonth(sorted.map { it.date }) else null
        val weekday = if (frequency != RecurrenceFrequency.MONTHLY) modeWeekday(sorted.map { it.date }) else null
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
     * Classifies the [gaps] (consecutive day-differences between sorted occurrences) into a
     * cadence. Uses the median gap (resistant to a single long gap from a missed occurrence) to
     * pick a tolerance band, then requires the gaps to actually fall in that band: either at
     * least [MIN_GAP_MATCH_FRACTION] of them (scales with series length), or all but one (a flat
     * floor so a single missed occurrence is always tolerated even in a short, e.g. 3-5
     * occurrence, series where 80% of a handful of gaps is stricter than "one miss"). Returns
     * null — no detection — when nothing fits, rather than guessing.
     */
    private fun classifyCadence(gaps: List<Long>): RecurrenceFrequency? {
        if (gaps.isEmpty()) return null
        val sortedGaps = gaps.sorted()
        val medianGap = if (sortedGaps.size % 2 == 1) {
            sortedGaps[sortedGaps.size / 2].toDouble()
        } else {
            (sortedGaps[sortedGaps.size / 2 - 1] + sortedGaps[sortedGaps.size / 2]) / 2.0
        }

        val frequency = when {
            medianGap in WEEKLY_RANGE.first.toDouble()..WEEKLY_RANGE.last.toDouble() -> RecurrenceFrequency.WEEKLY
            medianGap in FORTNIGHTLY_RANGE.first.toDouble()..FORTNIGHTLY_RANGE.last.toDouble() -> RecurrenceFrequency.FORTNIGHTLY
            medianGap in MONTHLY_RANGE.first.toDouble()..MONTHLY_RANGE.last.toDouble() -> RecurrenceFrequency.MONTHLY
            else -> return null
        }

        val range = when (frequency) {
            RecurrenceFrequency.WEEKLY -> WEEKLY_RANGE
            RecurrenceFrequency.FORTNIGHTLY -> FORTNIGHTLY_RANGE
            RecurrenceFrequency.MONTHLY -> MONTHLY_RANGE
            else -> return null
        }
        val badCount = gaps.count { it !in range }
        val matchFraction = (gaps.size - badCount) / gaps.size.toDouble()
        return frequency.takeIf { matchFraction >= MIN_GAP_MATCH_FRACTION || badCount <= 1 }
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
