package com.gestorfinances.app.domain.rules

import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Recognises a movement recorded by hand as the pending occurrence of a recurring item: the same
 * kind of movement, on the same account (and to the same one, for a transfer), in the item's
 * category when it has one, near its next date, and at an amount close to what it expects.
 */
object RecurringMatcher {
    /** Days beyond an item's own margin a movement may still fall and be recognised. */
    private const val SLACK_DAYS = 3L

    /** How far an amount may stray from what a fixed item expects (a price change) and still be it. */
    private const val FIXED_TOLERANCE_PERCENT = 20L

    /** The same for an approximate item, whose amounts vary by nature. */
    private const val APPROXIMATE_TOLERANCE_PERCENT = 50L

    /** The active item whose pending occurrence a new movement is, the nearest in date if several are. */
    fun pendingOccurrenceOf(
        templates: List<TemplateSummary>,
        type: MovementType,
        accountId: String?,
        destinationAccountId: String?,
        categoryId: String?,
        amountCents: Long,
        date: LocalDate,
    ): TemplateSummary? =
        templates
            .filter { it.describes(type, accountId, destinationAccountId, categoryId) && it.expects(amountCents) }
            .mapNotNull { template -> template.daysFrom(date)?.takeIf { it <= template.marginDays + SLACK_DAYS }?.let { template to it } }
            .minByOrNull { it.second }
            ?.first

    /**
     * Movements not yet linked to any item that could be [template]'s pending occurrence, nearest
     * first. The amount is judged leniently (twice the usual leeway), since the person picks.
     */
    fun recordedCandidates(template: TemplateSummary, movements: List<MovementSummary>): List<MovementSummary> =
        movements
            .filter {
                it.templateId == null &&
                    template.describes(it.type, it.accountId, it.destinationAccountId, it.categoryId) &&
                    template.expects(it.amountCents, leniency = 2)
            }
            .mapNotNull { movement ->
                val date = runCatching { LocalDate.parse(movement.date) }.getOrNull() ?: return@mapNotNull null
                template.daysFrom(date)?.takeIf { it <= template.marginDays + SLACK_DAYS }?.let { movement to it }
            }
            .sortedBy { it.second }
            .map { it.first }

    /**
     * Movements up to [today] not linked to any item that could be [template]'s past payments:
     * alike, and, for a monthly or yearly item, on a day that fits its rhythm (give or take its
     * margin and a few days; a yearly one in its month too). Their amount is not judged, since a
     * change of price or pay is exactly what leaves them unlinked. Newest first.
     */
    fun pastCandidates(template: TemplateSummary, movements: List<MovementSummary>, today: LocalDate): List<MovementSummary> {
        val anchor = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull()
        val anchorDay = template.dayOfMonth?.toInt() ?: anchor?.dayOfMonth
        return movements
            .filter { it.templateId == null && template.describes(it.type, it.accountId, it.destinationAccountId, it.categoryId, activeOnly = false) }
            .filter { movement ->
                val date = runCatching { LocalDate.parse(movement.date) }.getOrNull() ?: return@filter false
                if (date.isAfter(today)) return@filter false
                val slack = template.marginDays + SLACK_DAYS
                when (template.frequency) {
                    RecurrenceFrequency.MONTHLY -> anchorDay == null || monthDayDistance(date, anchorDay) <= slack
                    RecurrenceFrequency.YEARLY -> anchor == null ||
                        abs(ChronoUnit.DAYS.between(anchor.withYear(date.year), date)) <= slack
                    else -> true
                }
            }
            .sortedByDescending { it.date }
    }

    /** Days between [date] and the nearest day [anchorDay] around it (clamped to short months). */
    private fun monthDayDistance(date: LocalDate, anchorDay: Int): Long =
        abs(ChronoUnit.DAYS.between(nearestMonthDay(date, anchorDay), date))

    private fun nearestMonthDay(date: LocalDate, anchorDay: Int): LocalDate =
        listOf(date.minusMonths(1), date, date.plusMonths(1))
            .map { month -> month.withDayOfMonth(anchorDay.coerceAtMost(month.lengthOfMonth())) }
            .minBy { abs(ChronoUnit.DAYS.between(it, date)) }

    /**
     * The expected date a payment on [date] stands for: the nearest of a monthly or yearly item's
     * days; for any other rhythm, the date itself.
     */
    fun occurrenceFor(template: TemplateSummary, date: LocalDate): LocalDate {
        val anchor = runCatching { LocalDate.parse(template.nextDueDate) }.getOrNull() ?: return date
        return when (template.frequency) {
            RecurrenceFrequency.MONTHLY -> nearestMonthDay(date, template.dayOfMonth?.toInt() ?: anchor.dayOfMonth)
            RecurrenceFrequency.YEARLY -> (date.year - 1..date.year + 1)
                .map { anchor.withYear(it) }
                .minBy { abs(ChronoUnit.DAYS.between(it, date)) }
            else -> date
        }
    }

    /**
     * Of [candidates], one per expected date — the nearest to it — leaving out dates a payment
     * already [linked] to the item covers: what "mark all" ticks, so a period never gets two.
     */
    fun onePerOccurrence(template: TemplateSummary, candidates: List<MovementSummary>, linked: List<MovementSummary>): Set<String> {
        val covered = linked.mapNotNull { payment -> runCatching { occurrenceFor(template, LocalDate.parse(payment.date)) }.getOrNull() }.toSet()
        return candidates
            .mapNotNull { movement ->
                val date = runCatching { LocalDate.parse(movement.date) }.getOrNull() ?: return@mapNotNull null
                Triple(movement.id, occurrenceFor(template, date), date)
            }
            .filter { (_, occurrence, _) -> occurrence !in covered }
            .groupBy { it.second }
            .map { (occurrence, group) -> group.minBy { abs(ChronoUnit.DAYS.between(occurrence, it.third)) }.first }
            .toSet()
    }

    private fun TemplateSummary.describes(
        type: MovementType,
        accountId: String?,
        destinationAccountId: String?,
        categoryId: String?,
        activeOnly: Boolean = true,
    ): Boolean =
        (!activeOnly || status == TemplateStatus.ACTIVE) &&
            this.type == type &&
            type != MovementType.SETTLEMENT &&
            this.accountId == accountId &&
            (type != MovementType.TRANSFER || destAccountId == destinationAccountId) &&
            (this.categoryId == null || this.categoryId == categoryId)

    private fun TemplateSummary.expects(amountCents: Long, leniency: Long = 1): Boolean {
        val expected = expectedAmountCents ?: return true
        val tolerance = if (amountIsVariable) APPROXIMATE_TOLERANCE_PERCENT else FIXED_TOLERANCE_PERCENT
        return abs(amountCents - expected) * 100 <= expected * tolerance * leniency
    }

    private fun TemplateSummary.daysFrom(date: LocalDate): Long? =
        runCatching { abs(ChronoUnit.DAYS.between(LocalDate.parse(nextDueDate), date)) }.getOrNull()
}
