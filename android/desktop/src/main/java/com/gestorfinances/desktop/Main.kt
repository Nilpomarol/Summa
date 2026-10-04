package com.gestorfinances.desktop

import org.jetbrains.compose.resources.StringResource
import com.gestorfinances.desktop.resources.settings_restore_title
import com.gestorfinances.desktop.resources.restore_error_newer
import com.gestorfinances.desktop.resources.restore_error_invalid
import com.gestorfinances.desktop.resources.restore_error_failed
import com.gestorfinances.desktop.resources.restore_dialog_title
import com.gestorfinances.desktop.resources.database_error_restore_hint
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.data.backup.BackupValidationError
import com.gestorfinances.app.data.backup.BackupException
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModelStore
import java.awt.Image
import javax.imageio.ImageIO
import androidx.compose.runtime.CompositionLocalProvider
import java.util.Locale
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.gestorfinances.app.data.FinancialDataRevision
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import com.gestorfinances.desktop.resources.tray_notice
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.math.roundToInt
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.onboarding.OnboardingViewModel
import com.gestorfinances.app.ui.onboarding.defaultCategorySeeds
import com.gestorfinances.app.ui.theme.ThemeMode
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.desktop.resources.database_error
import com.gestorfinances.desktop.resources.database_error_newer
import com.gestorfinances.desktop.resources.tray_open
import com.gestorfinances.desktop.resources.tray_quit
import com.gestorfinances.ui.resources.app_name
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.failure_load_accounts
import com.gestorfinances.desktop.resources.app_icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.desktop.resources.settings_open_folder
import java.awt.Desktop
import java.awt.Dimension
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.StateFlow
import javax.swing.UIManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

fun main(args: Array<String>) {
    // Opened again while it sits in the tray: the app already running shows its window instead.
    val showRequests = singleInstance() ?: return
    // The app speaks Catalan whatever Windows does: the calendar's months and weekdays too.
    Locale.setDefault(Locale.forLanguageTag("ca-ES"))
    // The folder chooser and other Swing pieces look like Windows rather than Java.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    application { Summa(startHidden = START_HIDDEN_ARGUMENT in args, showRequests = showRequests) }
}

@Composable
private fun ApplicationScope.Summa(startHidden: Boolean, showRequests: StateFlow<Int>) {
    val settings = remember { DesktopSettings() }
    var opened by remember { mutableStateOf(runCatching { DesktopDatabase(DesktopDatabase.defaultFile()) }) }
    val shortcuts = remember { AppShortcuts() }
    // With sync on, the app outlives its window: it stays in the tray, listening for the phone.
    val staysInTray = settings.syncCode != null && settings.keepListening
    var visible by remember { mutableStateOf(!(startHidden && staysInTray)) }
    val shown by showRequests.collectAsState()
    LaunchedEffect(shown) { if (shown > 0) visible = true }
    val windowState = remember { initialWindowState(settings) }
    val rememberWindow = {
        val maximized = windowState.placement == WindowPlacement.Maximized
        // A window never shown (started in the tray) has no place yet: its coordinates are not numbers.
        val bounds = if (maximized || !windowState.position.isSpecified) {
            null
        } else {
            Rectangle(
                windowState.position.x.value.roundToInt(),
                windowState.position.y.value.roundToInt(),
                windowState.size.width.value.roundToInt(),
                windowState.size.height.value.roundToInt(),
            )
        }
        settings.recordWindow(bounds, maximized)
    }
    val quit = {
        rememberWindow()
        opened.getOrNull()?.let { db ->
            settings.backUpOnClose(db)
            db.close()
        }
        exitApplication()
    }
    val trayState = rememberTrayState()
    val appName = stringResource(SharedRes.string.app_name)
    val trayNotice = stringResource(Res.string.tray_notice)
    if (staysInTray) {
        Tray(
            icon = painterResource(Res.drawable.app_icon),
            state = trayState,
            tooltip = stringResource(SharedRes.string.app_name),
            onAction = { visible = true },
            menu = {
                Item(stringResource(Res.string.tray_open), onClick = { visible = true })
                Item(stringResource(Res.string.tray_quit), onClick = quit)
            },
        )
    }
    Window(
        onCloseRequest = {
            rememberWindow()
            if (staysInTray) {
                // The app stays open, so the backup need not hold the window while it is written.
                opened.getOrNull()?.let { db -> thread(name = "summa-backup") { settings.backUpOnClose(db) } }
                visible = false
                // A window that closes and an app that does not: said once, where the app now is.
                if (!settings.trayNoticeShown) {
                    trayState.sendNotification(Notification(appName, trayNotice, Notification.Type.Info))
                    settings.trayNoticeShown = true
                }
            } else {
                quit()
            }
        },
        visible = visible,
        title = stringResource(SharedRes.string.app_name),
        state = windowState,
        onPreviewKeyEvent = { shortcuts.onKey(it) },
    ) {
        // Half a laptop's screen still fits.
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(640, 560)
            // Every size Windows asks for, each smoothed down from the full icon: one large image
            // left to the system to shrink comes out jagged in the taskbar.
            @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
            val full = ImageIO.read(Res.readBytes("drawable/app_icon.png").inputStream())
            window.iconImages = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256).map { full.getScaledInstance(it, it, Image.SCALE_AREA_AVERAGING) }
            keepPixelAligned(window)
        }
        val titleBar = remember { OwnTitleBar.install(window) }
        LaunchedEffect(shown) { if (shown > 0) window.toFront() }
        val dark = when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        LaunchedEffect(dark) {
            applyWindowChrome(window, dark, windowBackground(dark), if (dark) Color.White else Color(0xFF1B1B1B))
        }
        DesktopTheme(darkTheme = dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                CompositionLocalProvider(LocalTitleBarHeight provides if (titleBar != null) TitleBarHeight else 0.dp) {
                    opened.fold(
                        onSuccess = { App(it, settings, shortcuts) },
                        onFailure = {
                            Column(
                                Modifier.fillMaxSize().padding(32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (it is NewerSchemaException) {
                                    Text(stringResource(Res.string.database_error_newer))
                                } else {
                                    Text(stringResource(Res.string.database_error))
                                    // Not for the owner to read, but what whoever looks into it will ask for.
                                    Text(it.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = FinanceTheme.colors.mutedText)
                                }
                                Text(
                                    stringResource(Res.string.database_error_restore_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FinanceTheme.colors.mutedText,
                                )
                                var restoreError by remember { mutableStateOf<StringResource?>(null) }
                                restoreError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                                val chooserTitle = stringResource(Res.string.restore_dialog_title)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // The file, and the copy taken before a migration, are there to be reached by hand.
                                    SecondaryButton(
                                        text = stringResource(Res.string.settings_open_folder),
                                        onClick = { runCatching { Desktop.getDesktop().open(DesktopDatabase.defaultFile().parentFile) } },
                                    )
                                    PrimaryButton(
                                        text = stringResource(Res.string.settings_restore_title),
                                        onClick = {
                                            chooseBackupFile(chooserTitle)?.let { source ->
                                                try {
                                                    DesktopDatabase.restoreOver(DesktopDatabase.defaultFile(), source)
                                                    opened = runCatching { DesktopDatabase(DesktopDatabase.defaultFile()) }
                                                } catch (error: BackupException) {
                                                    restoreError = when (error.reason) {
                                                        BackupValidationError.UNSUPPORTED_SCHEMA -> Res.string.restore_error_newer
                                                        else -> Res.string.restore_error_invalid
                                                    }
                                                } catch (_: Exception) {
                                                    restoreError = Res.string.restore_error_failed
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
                if (titleBar != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) { CaptionButtons(titleBar, window, windowState) }
                }
            }
        }
    }
}

/**
 * The window as it was left, when that is still on a screen (a second monitor may be gone);
 * otherwise the usual size, no larger than the screen has room for.
 */
private fun initialWindowState(settings: DesktopSettings): WindowState {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val room = environment.maximumWindowBounds
    val saved = settings.windowBounds?.takeIf { bounds ->
        environment.screenDevices.any { it.defaultConfiguration.bounds.intersects(bounds) }
    }
    return WindowState(
        placement = if (settings.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
        width = (saved?.width ?: 1360).coerceAtMost(room.width).dp,
        height = (saved?.height ?: 860).coerceAtMost(room.height).dp,
    )
}

/** The app inside its window: the first-run gate, then the pages. */
@Composable
internal fun App(db: DesktopDatabase, settings: DesktopSettings, shortcuts: AppShortcuts = AppShortcuts(), startPage: Page = Page.HOME) {
    // Advanced after a restore: everything below is rebuilt on the replaced database.
    var revision by remember { mutableIntStateOf(0) }
    val startRestore = rememberRestoreFlow(db) { revision++ }
    var page by remember { mutableStateOf(startPage) }
    // Data arriving from the phone reloads the pages where they are, so an open form, the
    // selection and the page survive it. Only the first-run screen is rebuilt: the data may end it.
    val dataRevision = remember { FinancialDataRevision() }
    var shellShown by remember { mutableStateOf(false) }
    val sync = rememberSyncListener(db, settings) { if (shellShown) dataRevision.markChanged() else revision++ }
    key(revision) {
        // Ended when a restore rebuilds everything on the replaced database.
        val store = remember { ViewModelStore() }
        DisposableEffect(store) { onDispose { store.clear() } }
        val onboarding = remember {
            OnboardingViewModel(
                accountRepository = AccountRepository(db.database.accountsQueries, db.database.sharedAccountsQueries),
                categoryRepository = CategoryRepository(db.database.categoriesQueries),
            ).also { store.put("onboarding", it) }
        }
        val state by onboarding.state.collectAsState()
        val defaultCategories = defaultCategorySeeds()
        SideEffect { shellShown = !state.isLoading && state.loadErrorMessage == null && !state.needsOnboarding }
        when {
            state.isLoading -> Unit
            state.loadErrorMessage != null -> Box(Modifier.fillMaxSize().padding(32.dp)) {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(SharedRes.string.failure_load_accounts),
                    actionLabel = stringResource(SharedRes.string.common_retry),
                    onAction = onboarding::onRetry,
                )
            }
            state.needsOnboarding -> OnboardingScreen(
                state = state,
                onFormChange = onboarding::onFormChanged,
                onCreate = { onboarding.onCreateClicked(defaultCategories) },
                onRestore = startRestore,
                settings = settings,
            )
            else -> AppShell(db, settings, shortcuts, sync, dataRevision, onRestore = startRestore, page = page, onPage = { page = it })
        }
    }
}
