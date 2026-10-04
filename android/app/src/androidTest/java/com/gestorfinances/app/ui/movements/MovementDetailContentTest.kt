package com.gestorfinances.app.ui.movements

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** In-memory fixtures only: never opens the app database. */
class MovementDetailContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun anExpensePaidEntirelyForSomeoneReadsAsOwedBackNotAsAShare() {
        show(expense(userShareCents = 0), split(userCents = 0, personCents = 2_000))
        compose.onNodeWithText("-20,00\u00A0€").assertIsDisplayed()
        compose.onNodeWithText("Persona de prova et deu 20,00\u00A0€").assertIsDisplayed()
        compose.onNodeWithText("Qui t’ho deu").assertIsDisplayed()
        compose.onNodeWithText("Total 20,00\u00A0€").assertDoesNotExist()
        compose.onNodeWithText("Jo").assertDoesNotExist()
        capture("detail-paid-for.png")
    }

    @Test
    fun aRealSharedExpenseStillLeadsWithTheUsersShare() {
        show(expense(userShareCents = 1_000), split(userCents = 1_000, personCents = 1_000))
        compose.onNodeWithText("-10,00\u00A0€").assertIsDisplayed()
        compose.onNodeWithText("Total 20,00\u00A0€").assertIsDisplayed()
        compose.onNodeWithText("Qui t’ho deu").assertDoesNotExist()
    }

    private fun show(movement: MovementSummary, split: MovementSplitDraft) {
        compose.setContent {
            GestorFinancesTheme {
                MovementDetailContent(
                    movement = movement, refunds = emptyList(), accounts = emptyList(), split = split,
                    people = listOf(person), onEdit = {}, onArchive = {}, onAddRefund = {}, onRefundClick = {},
                )
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun split(userCents: Long, personCents: Long) = MovementSplitDraft(
        entryMethod = SplitEntryMethod.EXACT,
        lines = listOf(
            SplitLineDraft(SplitParticipantKind.USER, null, userCents),
            SplitLineDraft(SplitParticipantKind.PERSON, person.id, personCents),
        ),
    )

    private fun expense(userShareCents: Long) = MovementSummary(
        id = "e", type = MovementType.EXPENSE, amountCents = 2_000, date = "2026-07-15",
        accountId = "a", accountName = "Banc de prova", destinationAccountId = null, destinationAccountName = null,
        categoryId = "c", categoryName = "Menjar de prova", categoryNature = CategoryNature.VARIABLE,
        categoryIcon = null, categoryColor = null, name = "Sopar", payee = null, notes = null,
        isOneTime = false, isShared = true, userShareCents = userShareCents, isRecurring = false,
        paidByPersonName = null, payerId = null, settlementDirection = null, settlementPersonName = null,
        createdAt = NOW, updatedAt = NOW, archivedAt = null,
    )

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
        val person = PersonSummary("p", "Persona de prova", null, null, null, NOW, NOW, null, balanceCents = 2_000)
    }
}
