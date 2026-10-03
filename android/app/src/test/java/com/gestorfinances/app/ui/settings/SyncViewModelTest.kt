package com.gestorfinances.app.ui.settings

import com.gestorfinances.app.R
import com.gestorfinances.app.data.sync.ConflictChoice
import com.gestorfinances.app.data.sync.PairedComputer
import com.gestorfinances.app.data.sync.PhoneSyncOperations
import com.gestorfinances.app.data.sync.SyncConflict
import com.gestorfinances.app.data.sync.SyncLinkException
import com.gestorfinances.app.data.sync.SyncRound
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aWrongCodeIsNotKeptAndTheRightOnePairs() = runTest(dispatcher) {
        val sync = FakeSync()
        val viewModel = SyncViewModel(sync, onDataChanged = {}, ioDispatcher = dispatcher)

        // Unpaired, the app's own rounds do nothing.
        viewModel.onForeground()
        advanceTimeBy(10_000)
        viewModel.onBackground()
        assertEquals(0, sync.rounds)

        sync.failure = SyncLinkException(SyncLinkException.Reason.WRONG_CODE)
        viewModel.onCodeEntered("AAAA-AAAA-AAAA")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.computers.isEmpty())
        assertEquals(R.string.settings_sync_error_code, viewModel.state.value.message?.messageRes)

        sync.failure = null
        viewModel.onCodeEntered("BBBB-BBBB-BBBB")
        advanceUntilIdle()
        assertEquals(listOf("Casa"), viewModel.state.value.computers.map { it.name })
        assertEquals(R.string.settings_sync_done, viewModel.state.value.message?.messageRes)

        // A second computer joins the list; either can be taken off on its own.
        sync.nextName = "Poble"
        viewModel.onCodeEntered("CCCC-CCCC-CCCC")
        advanceUntilIdle()
        assertEquals(listOf("Casa", "Poble"), viewModel.state.value.computers.map { it.name })
        viewModel.onUnpairClicked("Casa")
        assertEquals(listOf("Poble"), viewModel.state.value.computers.map { it.name })
    }

    @Test
    fun quietRoundsStaySilentAndConflictsWaitForTheOwner() = runTest(dispatcher) {
        val sync = FakeSync().apply { pair("CODE") }
        sync.rounds = 0
        var reloads = 0
        val viewModel = SyncViewModel(sync, onDataChanged = { reloads++ }, ioDispatcher = dispatcher)

        // The computer is simply not there: nothing to say, and it is asked again later.
        sync.failure = SyncLinkException(SyncLinkException.Reason.NOT_FOUND)
        viewModel.onForeground()
        advanceTimeBy(1_000)
        assertEquals(1, sync.rounds)
        assertNull(viewModel.state.value.message)
        advanceTimeBy(31_000)
        assertEquals(2, sync.rounds)
        viewModel.onBackground()
        viewModel.onSyncNowClicked()
        advanceUntilIdle()
        assertEquals(R.string.settings_sync_error_not_found, viewModel.state.value.message?.messageRes)

        // With the computer there: one round to catch up, then nothing until someone changes.
        sync.failure = null
        sync.rounds = 0
        viewModel.onForeground()
        advanceTimeBy(20_000)
        assertEquals(1, sync.rounds)

        // A write here is noticed within seconds.
        sync.localChange = true
        advanceTimeBy(3_000)
        assertEquals(2, sync.rounds)

        // The computer's change arrives as soon as it answers, and what it brings reloads the pages.
        sync.result = SyncRound.Done(changedHere = true, changedThere = false)
        sync.remoteChange.complete(true)
        advanceTimeBy(3_000)
        assertEquals(3, sync.rounds)
        assertEquals(1, reloads)

        // A conflict is shown, holds the app's own rounds, and the choice goes into the next round.
        sync.result = SyncRound.Conflicts(listOf(SyncConflict("movements", "m1", "Mercat", "2026-10-01", 2380)))
        sync.localChange = true
        advanceTimeBy(3_000)
        assertEquals(1, viewModel.state.value.conflicts.size)
        val before = sync.rounds
        sync.localChange = true
        advanceTimeBy(20_000)
        assertEquals(before, sync.rounds)
        sync.result = SyncRound.Done(changedHere = false, changedThere = true)
        viewModel.onConflictChoice(ConflictChoice.NEWEST)
        advanceTimeBy(1_000)
        assertEquals(ConflictChoice.NEWEST, sync.lastChoice)
        assertTrue(viewModel.state.value.conflicts.isEmpty())
        viewModel.onBackground()
    }

    private class FakeSync : PhoneSyncOperations {
        private val paired = mutableListOf<PairedComputer>()
        var nextName = "Casa"
        var failure: Throwable? = null
        var result: SyncRound = SyncRound.Done(changedHere = false, changedThere = false)
        var rounds = 0
        var lastChoice: ConflictChoice? = null

        override val computers: List<PairedComputer> get() = paired.toList()

        override fun pair(code: String): SyncRound {
            val result = round(null)
            paired += PairedComputer(id = nextName, name = nextName, lastSyncAt = null)
            return result
        }

        override fun unpair(id: String) {
            paired.removeAll { it.id == id }
        }

        var localChange = false
        var remoteChange = CompletableDeferred<Boolean>()

        override fun round(choice: ConflictChoice?): SyncRound {
            rounds++
            lastChoice = choice
            failure?.let { throw it }
            localChange = false
            return result
        }

        override fun localChangePending(): Boolean = localChange

        override suspend fun awaitRemoteChange(): Boolean = remoteChange.await().also { remoteChange = CompletableDeferred() }
    }
}
