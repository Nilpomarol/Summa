package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.data.repository.SplitEntryMethod
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
    fun exactAndPercentageFieldsOpenFilledWithAnEqualSplitThatAddsUp() {
        val state = SplitEditorState()
            .withPersonToggled("laura")
            .withPersonToggled("marc")
            .evenlyFilled(totalCents = 1_000)

        assertEquals(listOf("3,34", "3,33", "3,33"), state.participantIds.map { state.exactAmounts[it] })
        assertEquals(listOf("33,34", "33,33", "33,33"), state.participantIds.map { state.percentages[it] })
        assertTrue(state.withMethod(SplitEntryMethod.EXACT).calculation(1_000).valid)
        assertTrue(state.withMethod(SplitEntryMethod.PERCENTAGE).calculation(1_000).valid)
    }

    @Test
    fun betweenTwoPeopleTheOtherTakesWhatIsLeft() {
        val state = SplitEditorState().withPersonToggled("laura").evenlyFilled(totalCents = 1_000)

        val exact = state.withMethod(SplitEntryMethod.EXACT).withExactAmountBalanced(USER_PARTICIPANT_ID, "7", totalCents = 1_000)
        assertEquals("3,00", exact.exactAmounts["laura"])
        assertTrue(exact.calculation(1_000).valid)

        val percent = state.withMethod(SplitEntryMethod.PERCENTAGE).withPercentageBalanced("laura", "25")
        assertEquals("75", percent.percentages[USER_PARTICIPANT_ID])
        assertTrue(percent.calculation(1_000).valid)

        // With three, nobody is "the other": what is typed stays as typed.
        val three = state.withPersonToggled("marc").evenlyFilled(1_000)
            .withExactAmountBalanced(USER_PARTICIPANT_ID, "7", totalCents = 1_000)
        assertEquals("3,33", three.exactAmounts["laura"])
    }

}
