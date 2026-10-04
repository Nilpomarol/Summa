package com.gestorfinances.app.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gestorfinances.app.data.db.GestorDatabase
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.ui.dashboard.DashboardContent
import com.gestorfinances.app.ui.dashboard.DashboardUiState
import com.gestorfinances.app.ui.theme.GestorFinancesTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Blank Activity, null-name in-memory database: never uses AppContainer or the user's ledger. */
class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var driver: AndroidSqliteDriver
    private lateinit var accounts: AccountRepository
    private lateinit var categories: CategoryRepository
    private lateinit var model: OnboardingViewModel
    private val launch = mutableIntStateOf(0)
    private var opened: String? = null

    @Before
    fun setUp() {
        driver = AndroidSqliteDriver(GestorDatabase.Schema,
            InstrumentationRegistry.getInstrumentation().targetContext, name = null)
        val database = GestorDatabase(driver)
        accounts = AccountRepository(database.accountsQueries, database.sharedAccountsQueries)
        categories = CategoryRepository(database.categoriesQueries)
    }

    @After
    fun tearDown() {
        driver.close()
    }

    @Test
    fun createFirstAccountOpensUsefulHomeAndRelaunchKeepsIt() {
        show()
        compose.onNodeWithText("Benvingut a Summa").assertIsDisplayed()
        compose.onNodeWithText("Les teves dades es queden al dispositiu.", substring = true).assertIsDisplayed()
        capture("onboarding.png")
        listOf("Tipus", "Compte compartit", "Opcions avançades", "Crear categories inicials").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNode(hasText("Nom") and hasSetTextAction()).performTextInput("Banc de prova")
        compose.onNodeWithText("Crea el primer compte").performScrollTo().performClick()
        waitForHome()
        compose.onNodeWithText("Registra el teu primer moviment.").assertIsDisplayed()
        compose.onNodeWithText("Pressupostos").assertDoesNotExist()
        compose.onNodeWithText("Nou moviment").performClick()
        compose.runOnIdle { assertEquals("movement", opened) }
        compose.runOnIdle { launch.intValue++ }
        waitForHome()
        compose.onNodeWithText("Benvingut a Summa").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, accounts.listActive().size)
            assertEquals(8, categories.listActive().size)
        }
    }

    @Test
    fun fieldErrorsStayWithTheirInputsAndCanBeCorrected() {
        show()
        compose.onNodeWithText("Crea el primer compte").performScrollTo().performClick()
        compose.onNodeWithText("Introdueix el nom del compte.").assertIsDisplayed()
        compose.onNode(hasText("Nom") and hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .performTextInput("Banc")
        compose.onNode(hasText("Saldo inicial") and hasSetTextAction()).performTextReplacement("abc")
        compose.onNodeWithText("Crea el primer compte").performScrollTo().performClick()
        compose.onNodeWithText("Introdueix un saldo inicial vàlid.").assertIsDisplayed()
        compose.onNode(hasText("Saldo inicial") and hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .performTextReplacement("0")
        compose.onNodeWithText("Crea el primer compte").performScrollTo().performClick()
        waitForHome()
    }

    @Test
    fun returningUserWithOnlyArchivedAccountsGetsOneAccountAction() {
        show()
        compose.onNode(hasText("Nom") and hasSetTextAction()).performTextInput("Banc")
        compose.onNodeWithText("Crea el primer compte").performScrollTo().performClick()
        waitForHome()
        compose.runOnIdle {
            accounts.archive(accounts.listActive().single().id, "2026-09-27T10:00:00Z")
            launch.intValue++
        }
        waitForHome()
        compose.onNodeWithText("Benvingut a Summa").assertDoesNotExist()
        compose.onNodeWithText("Nou moviment").assertDoesNotExist()
        compose.onNodeWithText("Nou compte").performClick()
        compose.runOnIdle { assertEquals("account", opened) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun waitForHome() {
        compose.waitForIdle()
        compose.waitUntil(5_000) { !model.state.value.isLoading && !model.state.value.needsOnboarding }
        compose.waitForIdle()
    }

    private fun show() {
        compose.setContent {
            model = remember(launch.intValue) { OnboardingViewModel(accounts, categories) }
            val state by model.state.collectAsState()
            GestorFinancesTheme {
                if (state.isLoading || state.needsOnboarding) {
                    OnboardingScreen(model)
                } else {
                    DashboardContent(
                        state = DashboardUiState(hasLoaded = true, isLoading = false,
                            accounts = accounts.listActive()), modifier = Modifier, dueRecurringCount = 0,
                        onOpenAccount = {}, onOpenPerson = {}, onOpenRecurring = {},
                        onDrillDown = {}, onMovementDetail = {}, onAccountAnalysis = {},
                        onAccountSelected = {}, onViewTrip = {}, onAddTripMovement = {},
                        onViewBudgets = {}, onAddMovement = { opened = "movement" },
                        onAddAccount = { opened = "account" }, onRetry = {},
                    )
                }
            }
        }
        compose.waitUntil(5_000) { !model.state.value.isLoading }
    }
}
