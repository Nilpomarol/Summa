package com.gestorfinances.app.ui.settings

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gestorfinances.app.R
import com.gestorfinances.app.data.backup.BackupException
import com.gestorfinances.app.data.backup.BackupExportResult
import com.gestorfinances.app.data.backup.BackupFileCandidate
import com.gestorfinances.app.data.backup.BackupFolder
import com.gestorfinances.app.data.backup.BackupFolderRepository
import com.gestorfinances.app.data.backup.BackupMetadata
import com.gestorfinances.app.data.backup.BackupOperations
import com.gestorfinances.app.data.backup.BackupValidationError
import com.gestorfinances.app.data.backup.PendingBackupRestore
import com.gestorfinances.app.data.db.DataSeeder
import com.gestorfinances.app.notifications.NotificationSettings
import com.gestorfinances.app.notifications.NotificationSettingsRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var driver: JdbcSqliteDriver

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    }

    @After
    fun tearDown() {
        driver.close()
        Dispatchers.resetMain()
    }

    @Test
    fun exportWithoutFolderAsksForFolderPicker() = runTest(dispatcher) {
        val viewModel = viewModel(folderRepository = FakeFolderRepository())
        val effects = mutableListOf<SettingsEffect>()
        val job = launch { viewModel.effects.take(1).toList(effects) }

        viewModel.onBackupExportClicked()
        advanceUntilIdle()

        assertEquals(listOf(SettingsEffect.PickBackupFolder), effects)
        job.cancel()
    }

    @Test
    fun selectingFolderStoresItAndShowsTheFolderInState() = runTest(dispatcher) {
        val folderRepository = FakeFolderRepository()
        val viewModel = viewModel(folderRepository = folderRepository)

        viewModel.onBackupFolderSelected("content://folder")
        advanceUntilIdle()

        assertEquals("content://folder", folderRepository.folder?.uriString)
        assertEquals("content://folder", viewModel.state.value.backupFolder?.uriString)
        assertEquals(R.string.settings_backup_folder_saved, viewModel.state.value.backupMessage?.messageRes)
    }

    @Test
    fun exportWithFolderUsesBackupServiceAndSurfacesSuccess() = runTest(dispatcher) {
        val operations = FakeBackupOperations()
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        viewModel.onScreenShown()

        viewModel.onBackupExportClicked()
        advanceUntilIdle()

        assertEquals("content://folder", operations.exportedFolder)
        assertFalse(viewModel.state.value.isBackupBusy)
        assertEquals(R.string.settings_backup_export_success, viewModel.state.value.backupMessage?.messageRes)
    }

    @Test
    fun importListsBackupsThenSelectionCreatesPendingRestore() = runTest(dispatcher) {
        val candidate = BackupFileCandidate(
            id = "content://backup",
            displayName = "gestor-finances-backup-v1-000000000004-20260708T101112Z.gfbackup",
            parsedSnapshotVersion = 4,
            parsedCreatedAtUtc = CREATED_AT,
            lastModifiedMillis = null,
        )
        val operations = FakeBackupOperations(candidates = listOf(candidate))
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        viewModel.onScreenShown()

        viewModel.onBackupImportClicked()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showBackupList)
        viewModel.onBackupCandidateSelected(candidate)
        advanceUntilIdle()

        assertEquals("content://backup", operations.preparedCandidate?.id)
        assertNull(viewModel.state.value.backupMessage)
        assertEquals("pending.db", viewModel.state.value.pendingRestore?.tempPath)
    }

    @Test
    fun restoreConfirmationAppliesBackupAndEmitsRestoredEffect() = runTest(dispatcher) {
        val candidate = BackupFileCandidate(
            id = "content://backup",
            displayName = "gestor-finances-backup-v1-000000000004-20260708T101112Z.gfbackup",
            parsedSnapshotVersion = 4,
            parsedCreatedAtUtc = CREATED_AT,
            lastModifiedMillis = null,
        )
        val operations = FakeBackupOperations(candidates = listOf(candidate))
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        val effects = mutableListOf<SettingsEffect>()
        val job = launch { viewModel.effects.take(1).toList(effects) }
        viewModel.onScreenShown()
        viewModel.onBackupCandidateSelected(candidate)
        advanceUntilIdle()

        viewModel.onRestoreConfirmed()
        advanceUntilIdle()

        assertEquals("pending.db", operations.appliedPending?.tempPath)
        assertNull(viewModel.state.value.pendingRestore)
        // applyRestore() always closes the shared driver, so a successful restore always requires
        // an app recreate: isBackupBusy must stay true (never clear-then-recreate) all the way
        // through, since MainActivity's RecreateApp handling is what finally rebuilds the driver.
        assertTrue(viewModel.state.value.isBackupBusy)
        assertEquals(
            listOf(
                SettingsEffect.RecreateApp(
                    SettingsMessage(
                        kind = SettingsMessageKind.INFO,
                        messageRes = R.string.settings_backup_restore_success,
                        arg = candidate.displayName,
                    ),
                ),
            ),
            effects,
        )
        job.cancel()
    }

    @Test
    fun restoreFailureThatDoesNotRequireResetClearsBusyAndSkipsRecreate() = runTest(dispatcher) {
        val candidate = backupCandidate()
        val operations = FakeBackupOperations(
            candidates = listOf(candidate),
            restoreFailure = BackupException(
                reason = BackupValidationError.UNREADABLE,
                requiresAppReset = false,
            ),
        )
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        val effects = mutableListOf<SettingsEffect>()
        val job = launch { viewModel.effects.toList(effects) }
        viewModel.onScreenShown()
        viewModel.onBackupCandidateSelected(candidate)
        advanceUntilIdle()

        viewModel.onRestoreConfirmed()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isBackupBusy)
        assertTrue(effects.isEmpty())
        job.cancel()
    }

    @Test
    fun restoreFailureThatRequiresResetKeepsBusyTrueAndEmitsRecreateApp() = runTest(dispatcher) {
        val candidate = backupCandidate()
        val operations = FakeBackupOperations(
            candidates = listOf(candidate),
            restoreFailure = BackupException(
                reason = BackupValidationError.RESTORE_APPLY_FAILED,
                requiresAppReset = true,
            ),
        )
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        val effects = mutableListOf<SettingsEffect>()
        val job = launch { viewModel.effects.take(1).toList(effects) }
        viewModel.onScreenShown()
        viewModel.onBackupCandidateSelected(candidate)
        advanceUntilIdle()

        viewModel.onRestoreConfirmed()
        advanceUntilIdle()

        // The shared driver was already closed by the time this failure surfaced, so busy must
        // stay true (blocking re-taps) until MainActivity handles RecreateApp.
        assertTrue(viewModel.state.value.isBackupBusy)
        assertEquals(1, effects.size)
        assertTrue(effects.single() is SettingsEffect.RecreateApp)
        job.cancel()
    }

    @Test
    fun backupBusyStateSurvivesNotificationSettingChanges() = runTest(dispatcher) {
        val operations = FakeBackupOperations()
        lateinit var viewModel: SettingsViewModel
        var busyObservedDuringExport = false
        operations.onExport = {
            viewModel.onBudgetAlertsChanged(enabled = false)
            busyObservedDuringExport = viewModel.state.value.isBackupBusy
        }
        viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        viewModel.onScreenShown()

        viewModel.onBackupExportClicked()
        advanceUntilIdle()

        assertTrue(busyObservedDuringExport)
        assertFalse(viewModel.state.value.isBackupBusy)
    }

    @Test
    fun doubleRestoreConfirmationAppliesOnlyOnce() = runTest(dispatcher) {
        val candidate = backupCandidate()
        val operations = FakeBackupOperations(candidates = listOf(candidate))
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        viewModel.onScreenShown()
        viewModel.onBackupCandidateSelected(candidate)
        advanceUntilIdle()

        viewModel.onRestoreConfirmed()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()

        assertEquals(1, operations.applyCount)
    }

    @Test
    fun restoreDismissIsIgnoredWhileRestoreIsBusy() = runTest(dispatcher) {
        val candidate = backupCandidate()
        val operations = FakeBackupOperations(candidates = listOf(candidate))
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        viewModel.onScreenShown()
        viewModel.onBackupCandidateSelected(candidate)
        advanceUntilIdle()

        viewModel.onRestoreConfirmed()
        viewModel.onRestoreDismissed()

        assertTrue(viewModel.state.value.isBackupBusy)
        assertEquals("pending.db", viewModel.state.value.pendingRestore?.tempPath)
        advanceUntilIdle()
    }

    @Test
    fun fallbackExportSuccessEmitsRecreateAppEffect() = runTest(dispatcher) {
        val operations = FakeBackupOperations(
            exportResult = BackupExportResult(
                metadata = BACKUP_METADATA,
                fallbackUsed = true,
                requiresAppReset = true,
            ),
        )
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        val effects = mutableListOf<SettingsEffect>()
        val job = launch { viewModel.effects.take(1).toList(effects) }
        viewModel.onScreenShown()

        viewModel.onBackupExportClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(
                SettingsEffect.RecreateApp(
                    SettingsMessage(
                        kind = SettingsMessageKind.ALERT,
                        messageRes = R.string.settings_backup_export_success_fallback,
                        arg = BACKUP_METADATA.displayName,
                    ),
                ),
            ),
            effects,
        )
        job.cancel()
    }

    @Test
    fun resetRequiredFailureEmitsRecreateAppEffectWithErrorMessage() = runTest(dispatcher) {
        val operations = FakeBackupOperations(
            exportFailure = BackupException(
                reason = BackupValidationError.RESTORE_APPLY_FAILED,
                requiresAppReset = true,
            ),
        )
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        val effects = mutableListOf<SettingsEffect>()
        val job = launch { viewModel.effects.take(1).toList(effects) }
        viewModel.onScreenShown()

        viewModel.onBackupExportClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(
                SettingsEffect.RecreateApp(
                    SettingsMessage(
                        kind = SettingsMessageKind.ERROR,
                        messageRes = R.string.settings_backup_error_restore_apply,
                    ),
                ),
            ),
            effects,
        )
        job.cancel()
    }

    @Test
    fun exportDestinationFailureSurfacesCatalanMessageInsteadOfRawException() = runTest(dispatcher) {
        val operations = FakeBackupOperations(
            exportFailure = BackupException(reason = BackupValidationError.EXPORT_DESTINATION_UNAVAILABLE),
        )
        val viewModel = viewModel(
            folderRepository = FakeFolderRepository(BackupFolder("content://folder", "Finances")),
            operations = operations,
        )
        viewModel.onScreenShown()

        viewModel.onBackupExportClicked()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isBackupBusy)
        assertEquals(
            R.string.settings_backup_error_export_destination,
            viewModel.state.value.backupMessage?.messageRes,
        )
    }

    private fun viewModel(
        folderRepository: FakeFolderRepository = FakeFolderRepository(),
        operations: FakeBackupOperations = FakeBackupOperations(),
    ): SettingsViewModel =
        SettingsViewModel(
            preferences = FakeNotificationSettingsRepository(),
            dataSeeder = DataSeeder(driver),
            backupFolderRepository = folderRepository,
            backupOperations = operations,
            ioDispatcher = dispatcher,
        )

    private class FakeNotificationSettingsRepository : NotificationSettingsRepository {
        var settings = NotificationSettings()

        override fun loadSettings(): NotificationSettings = settings

        override fun saveSettings(settings: NotificationSettings) {
            this.settings = settings
        }
    }

    private class FakeFolderRepository(
        var folder: BackupFolder? = null,
    ) : BackupFolderRepository {
        override fun loadSelectedFolder(): BackupFolder? = folder

        override fun saveSelectedFolder(uriString: String): BackupFolder =
            BackupFolder(uriString = uriString, displayLabel = uriString).also { folder = it }
    }

    private class FakeBackupOperations(
        private val candidates: List<BackupFileCandidate> = emptyList(),
        private val exportResult: BackupExportResult = BackupExportResult(
            metadata = BACKUP_METADATA,
            fallbackUsed = false,
            requiresAppReset = false,
        ),
        private val exportFailure: Throwable? = null,
        private val restoreFailure: Throwable? = null,
    ) : BackupOperations {
        var exportedFolder: String? = null
        var preparedCandidate: BackupFileCandidate? = null
        var appliedPending: PendingBackupRestore? = null
        var applyCount: Int = 0
        var onExport: (() -> Unit)? = null

        override fun exportToFolder(folderUriString: String): BackupExportResult {
            exportedFolder = folderUriString
            onExport?.invoke()
            exportFailure?.let { throw it }
            return exportResult
        }

        override fun listBackups(folderUriString: String): List<BackupFileCandidate> = candidates

        override fun prepareRestore(candidate: BackupFileCandidate): PendingBackupRestore {
            preparedCandidate = candidate
            return PendingBackupRestore(tempPath = "pending.db", metadata = BACKUP_METADATA)
        }

        override fun applyRestore(pending: PendingBackupRestore): BackupMetadata {
            applyCount += 1
            appliedPending = pending
            restoreFailure?.let { throw it }
            return pending.metadata
        }
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-07-08T10:11:12Z")
        val BACKUP_METADATA = BackupMetadata(
            displayName = "gestor-finances-backup-v1-000000000004-20260708T101112Z.gfbackup",
            schemaVersion = 4,
            snapshotVersion = 4,
            createdAtUtc = CREATED_AT,
        )

        fun backupCandidate(): BackupFileCandidate =
            BackupFileCandidate(
                id = "content://backup",
                displayName = BACKUP_METADATA.displayName,
                parsedSnapshotVersion = BACKUP_METADATA.snapshotVersion,
                parsedCreatedAtUtc = CREATED_AT,
                lastModifiedMillis = null,
            )
    }
}
