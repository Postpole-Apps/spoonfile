package com.postpole.spoonfile.ui

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarsTest {
    @Test
    fun collectLatestShowsOnlyLastOfRepeatedEvents() = runTest {
        val host = SnackbarHostState()
        val events = Channel<String>(Channel.BUFFERED)
        val collector = launch { events.receiveAsFlow().collectLatest { host.showLatestSnackbar(it) } }

        (1..5).forEach { events.trySend("Added $it") }
        advanceUntilIdle()

        assertEquals("Added 5", host.currentSnackbarData?.visuals?.message)

        host.currentSnackbarData?.dismiss()
        advanceUntilIdle()
        // Nothing stays queued afterwards.
        assertNull(host.currentSnackbarData)

        collector.cancel()
    }
}
