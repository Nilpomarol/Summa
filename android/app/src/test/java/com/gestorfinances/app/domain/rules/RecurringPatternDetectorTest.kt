package com.gestorfinances.app.domain.rules

import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPatternDetectorTest {

    @Test
    fun `clean monthly pattern detected as new active candidate`() {
        val movements = listOf(
            candidate("m1", date = "2026-01-05", amountCents = 1200),
            candidate("m2", date = "2026-02-05", amountCents = 1200),
            candidate("m3", date = "2026-03-05", amountCents = 1200),
            candidate("m4", date = "2026-04-05", amountCents = 1200),
        )

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-04-10"))

        val only = result.single()
        assertEquals(DetectedTemplateAction.NEW, only.action)
        assertNull(only.matchedTemplateId)
        assertEquals(RecurrenceFrequency.MONTHLY, only.frequency)
        assertEquals(5, only.dayOfMonth)
        assertNull(only.weekday)
        assertEquals(4, only.occurrenceCount)
        assertEquals(false, only.amountIsVariable)
        assertEquals(1200L, only.amountCents)
        assertEquals(0L, only.amountFlexCents)
        assertEquals(TemplateStatus.ACTIVE, only.suggestedStatus)
        assertEquals(LocalDate.parse("2026-05-05"), only.suggestedNextDueDate)
    }

    @Test
    fun `weekly pattern detected with matching weekday`() {
        val dates = listOf("2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26")
        val movements = dates.mapIndexed { i, d -> candidate("gym-$i", date = d, amountCents = 500, name = "Gym") }

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-01-28"))

        val only = result.single()
        assertEquals(RecurrenceFrequency.WEEKLY, only.frequency)
        val expectedWeekday = LocalDate.parse("2026-01-26").dayOfWeek.value - 1
        assertEquals(expectedWeekday, only.weekday)
        assertNull(only.dayOfMonth)
        assertEquals(TemplateStatus.ACTIVE, only.suggestedStatus)
        assertEquals(LocalDate.parse("2026-02-02"), only.suggestedNextDueDate)
    }

    @Test
    fun `fortnightly pattern detected from three occurrences`() {
        val movements = listOf(
            candidate("f1", date = "2026-01-05", amountCents = 800, name = "Piscina"),
            candidate("f2", date = "2026-01-19", amountCents = 800, name = "Piscina"),
            candidate("f3", date = "2026-02-02", amountCents = 800, name = "Piscina"),
        )

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-02-05"))

        assertEquals(RecurrenceFrequency.FORTNIGHTLY, result.single().frequency)
        assertEquals(TemplateStatus.ACTIVE, result.single().suggestedStatus)
    }

    @Test
    fun `stale monthly pattern proposes ended status`() {
        val movements = listOf(
            candidate("s1", date = "2025-01-05", amountCents = 1500, name = "Gimnas antic"),
            candidate("s2", date = "2025-02-05", amountCents = 1500, name = "Gimnas antic"),
            candidate("s3", date = "2025-03-05", amountCents = 1500, name = "Gimnas antic"),
        )

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-04-10"))

        val only = result.single()
        assertEquals(TemplateStatus.ENDED, only.suggestedStatus)
        assertEquals(LocalDate.parse("2025-03-05"), only.suggestedNextDueDate)
    }

    @Test
    fun `amount within tolerance proposes fixed amount with flex margin`() {
        val movements = listOf(
            candidate("a1", date = "2026-01-05", amountCents = 1000, name = "Llum"),
            candidate("a2", date = "2026-02-05", amountCents = 1050, name = "Llum"),
            candidate("a3", date = "2026-03-05", amountCents = 950, name = "Llum"),
        )

        val only = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-03-10")).single()

        assertEquals(false, only.amountIsVariable)
        assertEquals(1000L, only.amountCents)
        assertEquals(50L, only.amountFlexCents)
    }

    @Test
    fun `amount outside tolerance proposes variable amount`() {
        val movements = listOf(
            candidate("v1", date = "2026-01-05", amountCents = 1000, name = "Factura variable"),
            candidate("v2", date = "2026-02-05", amountCents = 2000, name = "Factura variable"),
            candidate("v3", date = "2026-03-05", amountCents = 500, name = "Factura variable"),
        )

        val only = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-03-10")).single()

        assertTrue(only.amountIsVariable)
        assertNull(only.amountCents)
        assertNull(only.amountFlexCents)
    }

    @Test
    fun `fewer than minimum occurrences yields no candidate`() {
        val movements = listOf(
            candidate("o1", date = "2026-01-05", amountCents = 1000, name = "Nou servei"),
            candidate("o2", date = "2026-02-05", amountCents = 1000, name = "Nou servei"),
        )

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-02-10"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `irregular gaps never produce a false positive`() {
        val base = LocalDate.parse("2026-01-01")
        val movements = listOf(
            candidate("r1", date = base.toString(), amountCents = 1000, name = "Compres esporadiques"),
            candidate("r2", date = base.plusDays(5).toString(), amountCents = 1000, name = "Compres esporadiques"),
            candidate("r3", date = base.plusDays(25).toString(), amountCents = 1000, name = "Compres esporadiques"),
            candidate("r4", date = base.plusDays(70).toString(), amountCents = 1000, name = "Compres esporadiques"),
        )

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-04-01"))

        assertTrue("random-gap group must not be detected as recurring", result.isEmpty())
    }

    @Test
    fun `one missed occurrence at the exact 80 percent boundary still detects`() {
        // 6 occurrences, 5 gaps: four ~30-day gaps plus one ~60-day gap (a missed occurrence).
        // matchFraction = 4/5 = 0.80, exactly at MIN_GAP_MATCH_FRACTION — must still pass.
        val base = LocalDate.parse("2026-01-05")
        val dates = listOf(0L, 30L, 60L, 90L, 120L, 180L).map { base.plusDays(it) }
        val movements = dates.mapIndexed { i, d -> candidate("b$i", date = d.toString(), amountCents = 4000, name = "Assegurança") }

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-07-10"))

        val only = result.single()
        assertEquals(RecurrenceFrequency.MONTHLY, only.frequency)
        assertEquals(6, only.occurrenceCount)
    }

    @Test
    fun `a single missed occurrence is tolerated even in a short four-occurrence series`() {
        // 4 occurrences, 3 gaps: two ~30-day gaps plus one ~60-day gap (a missed occurrence).
        // matchFraction = 2/3 = 0.67, below MIN_GAP_MATCH_FRACTION -- but badCount = 1, so the
        // flat "at most one miss" floor must still let this through.
        val base = LocalDate.parse("2026-01-05")
        val dates = listOf(0L, 30L, 90L, 120L).map { base.plusDays(it) }
        val movements = dates.mapIndexed { i, d -> candidate("s$i", date = d.toString(), amountCents = 2500, name = "Streaming") }

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-05-20"))

        val only = result.single()
        assertEquals(RecurrenceFrequency.MONTHLY, only.frequency)
        assertEquals(4, only.occurrenceCount)
    }

    @Test
    fun `two missed occurrences out of five gaps is still rejected`() {
        // 6 occurrences, 5 gaps: three ~30-day gaps plus two ~60-day gaps.
        // matchFraction = 3/5 = 0.60 (below threshold) and badCount = 2 (above the one-miss floor)
        // -- neither tolerance condition is met, so this must not be detected.
        val base = LocalDate.parse("2026-01-05")
        val dates = listOf(0L, 30L, 90L, 150L, 180L, 210L).map { base.plusDays(it) }
        val movements = dates.mapIndexed { i, d -> candidate("q$i", date = d.toString(), amountCents = 2500, name = "Irregular") }

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-08-20"))

        assertTrue("two-miss group must not be detected as recurring", result.isEmpty())
    }

    @Test
    fun `movements already linked to a template are excluded from grouping`() {
        val movements = listOf(
            candidate("t1", date = "2026-01-05", amountCents = 1200, name = "Ja programat", templateId = "tpl-existing"),
            candidate("t2", date = "2026-02-05", amountCents = 1200, name = "Ja programat", templateId = "tpl-existing"),
            candidate("t3", date = "2026-03-05", amountCents = 1200, name = "Ja programat", templateId = "tpl-existing"),
        )

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-03-10"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matching an existing template of any status proposes an update not a duplicate`() {
        val movements = listOf(
            candidate("u1", date = "2026-01-05", amountCents = 1200, name = "Subscripcio", accountId = "acc1", categoryId = "cat1"),
            candidate("u2", date = "2026-02-05", amountCents = 1200, name = "Subscripcio", accountId = "acc1", categoryId = "cat1"),
            candidate("u3", date = "2026-03-05", amountCents = 1200, name = "Subscripcio", accountId = "acc1", categoryId = "cat1"),
        )
        val existing = listOf(
            ExistingTemplateSignature(
                templateId = "tpl-1",
                accountId = "acc1",
                type = MovementType.EXPENSE,
                categoryId = "cat1",
                name = "Subscripcio",
                payee = null,
            ),
        )

        val only = RecurringPatternDetector.detect(movements, existingTemplates = existing, today = LocalDate.parse("2026-03-10")).single()

        assertEquals(DetectedTemplateAction.UPDATE, only.action)
        assertEquals("tpl-1", only.matchedTemplateId)
    }

    @Test
    fun `an existing template with a different type is not treated as a match`() {
        // Same account+category+name, but the existing template is INCOME while the detected
        // group is EXPENSE -- must not be matched as an update against the wrong template.
        val movements = listOf(
            candidate("i1", date = "2026-01-05", amountCents = 1200, name = "Reemborsament", accountId = "acc1", categoryId = "cat1"),
            candidate("i2", date = "2026-02-05", amountCents = 1200, name = "Reemborsament", accountId = "acc1", categoryId = "cat1"),
            candidate("i3", date = "2026-03-05", amountCents = 1200, name = "Reemborsament", accountId = "acc1", categoryId = "cat1"),
        )
        val existing = listOf(
            ExistingTemplateSignature(
                templateId = "tpl-income",
                accountId = "acc1",
                type = MovementType.INCOME,
                categoryId = "cat1",
                name = "Reemborsament",
                payee = null,
            ),
        )

        val only = RecurringPatternDetector.detect(movements, existingTemplates = existing, today = LocalDate.parse("2026-03-10")).single()

        assertEquals(DetectedTemplateAction.NEW, only.action)
        assertNull(only.matchedTemplateId)
    }

    @Test
    fun `no matching existing template proposes a new candidate`() {
        val movements = listOf(
            candidate("n1", date = "2026-01-05", amountCents = 1200, name = "Diferent"),
            candidate("n2", date = "2026-02-05", amountCents = 1200, name = "Diferent"),
            candidate("n3", date = "2026-03-05", amountCents = 1200, name = "Diferent"),
        )
        val existing = listOf(
            ExistingTemplateSignature(
                templateId = "tpl-other",
                accountId = "acc-other",
                type = MovementType.EXPENSE,
                categoryId = null,
                name = "Un altre",
                payee = null,
            ),
        )

        val only = RecurringPatternDetector.detect(movements, existingTemplates = existing, today = LocalDate.parse("2026-03-10")).single()

        assertEquals(DetectedTemplateAction.NEW, only.action)
        assertNull(only.matchedTemplateId)
    }

    @Test
    fun `yearly-shaped gaps are never detected`() {
        val movements = listOf(
            candidate("y1", date = "2024-06-01", amountCents = 30000, name = "Assegurança anual"),
            candidate("y2", date = "2025-06-01", amountCents = 30000, name = "Assegurança anual"),
            candidate("y3", date = "2026-06-01", amountCents = 30000, name = "Assegurança anual"),
        )

        val result = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-06-05"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `day of month mode picks the most frequent day when occurrences drift slightly`() {
        val movements = listOf(
            candidate("d1", date = "2026-01-01", amountCents = 1200, name = "Lloguer petit"),
            candidate("d2", date = "2026-02-01", amountCents = 1200, name = "Lloguer petit"),
            candidate("d3", date = "2026-03-02", amountCents = 1200, name = "Lloguer petit"),
        )

        val only = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-03-10")).single()

        assertEquals(RecurrenceFrequency.MONTHLY, only.frequency)
        assertEquals(1, only.dayOfMonth)
    }

    @Test
    fun `month-end anchor projects the correctly clamped next due date`() {
        val movements = listOf(
            candidate("c1", date = "2025-10-31", amountCents = 2000, name = "Final de mes"),
            candidate("c2", date = "2025-11-30", amountCents = 2000, name = "Final de mes"),
            candidate("c3", date = "2025-12-31", amountCents = 2000, name = "Final de mes"),
            candidate("c4", date = "2026-01-31", amountCents = 2000, name = "Final de mes"),
        )

        val only = RecurringPatternDetector.detect(movements, existingTemplates = emptyList(), today = LocalDate.parse("2026-02-03")).single()

        assertEquals(31, only.dayOfMonth)
        assertEquals(TemplateStatus.ACTIVE, only.suggestedStatus)
        // February 2026 has 28 days (not a leap year) -> clamped from day 31.
        assertEquals(LocalDate.parse("2026-02-28"), only.suggestedNextDueDate)
    }

    private fun candidate(
        id: String,
        date: String,
        amountCents: Long,
        name: String = "Netflix",
        accountId: String = "acc1",
        categoryId: String? = "cat1",
        templateId: String? = null,
    ): RecurringCandidateMovement = RecurringCandidateMovement(
        movementId = id,
        accountId = accountId,
        type = MovementType.EXPENSE,
        categoryId = categoryId,
        name = name,
        payee = null,
        amountCents = amountCents,
        date = LocalDate.parse(date),
        templateId = templateId,
    )
}
