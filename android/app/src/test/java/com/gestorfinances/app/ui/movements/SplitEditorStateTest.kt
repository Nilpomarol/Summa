package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitParticipantKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitEditorStateTest {
    @Test
    fun equalSplitAssignsRemainderToPayer() {
        val state = SplitEditorState()
            .withPersonToggled("laura")
            .withPersonToggled("marc")
            .withPayer("laura")

        val calculation = state.calculation(totalCents = 1_001)

        assertTrue(calculation.valid)
        assertEquals(333L, calculation.sharesCentsByParticipantId[USER_PARTICIPANT_ID])
        assertEquals(335L, calculation.sharesCentsByParticipantId["laura"])
        assertEquals(333L, calculation.sharesCentsByParticipantId["marc"])
    }

    @Test
    fun percentageSplitUsesBasisPointsAndAssignsRemainderToPayer() {
        val state = SplitEditorState()
            .withPersonToggled("laura")
            .withPersonToggled("marc")
            .withMethod(SplitEntryMethod.PERCENTAGE)
            .withPercentage(USER_PARTICIPANT_ID, "33,33")
            .withPercentage("laura", "33,33")
            .withPercentage("marc", "33,34")

        val calculation = state.calculation(totalCents = 1_001)

        assertTrue(calculation.valid)
        assertEquals(335L, calculation.sharesCentsByParticipantId[USER_PARTICIPANT_ID])
        assertEquals(333L, calculation.sharesCentsByParticipantId["laura"])
        assertEquals(333L, calculation.sharesCentsByParticipantId["marc"])
    }

    @Test
    fun exactSplitReportsRemainingAmount() {
        val state = SplitEditorState()
            .withPersonToggled("laura")
            .withMethod(SplitEntryMethod.EXACT)
            .withExactAmount(USER_PARTICIPANT_ID, "3")
            .withExactAmount("laura", "4")

        val calculation = state.calculation(totalCents = 1_000)

        assertFalse(calculation.valid)
        assertEquals(300L, calculation.deltaCents)
    }

    @Test
    fun paidByOtherPresetMakesUserShareZeroAndPersonShareFullTotal() {
        val state = SplitEditorState()
            .withPaidByOther("laura")

        val calculation = state.calculation(totalCents = 1_000)
        val draft = state.toMovementSplitDraft(totalCents = 1_000)!!

        assertTrue(calculation.valid)
        assertEquals(0L, calculation.sharesCentsByParticipantId[USER_PARTICIPANT_ID])
        assertEquals(1_000L, calculation.sharesCentsByParticipantId["laura"])
        assertEquals(SplitEntryMethod.EXACT, draft.entryMethod)
        assertEquals(
            mapOf(
                SplitParticipantKind.USER to 0L,
                SplitParticipantKind.PERSON to 1_000L,
            ),
            draft.lines.associate { it.participantKind to it.owedAmountCents },
        )
    }
}
