package com.postpole.spoonfile.pro

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProPromptRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var time = 1_000L

    private val repo by lazy {
        ProPromptRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "p.preferences_pb") },
            clock = { time },
        )
    }

    @After fun tearDown() = scope.cancel()

    @Test fun startsEmpty() = runBlocking {
        assertEquals(ProPromptHistory(), repo.history())
    }

    @Test fun engagedEventsCountEachCallOnce_soABulkImportIsOneEvent() = runBlocking {
        repo.recordEngaged() // a cook completion
        repo.recordEngaged() // a recipe save
        repo.recordEngaged() // one whole-file import = a single call, however many recipes it held
        assertEquals(3, repo.history().engagedEvents)
    }

    @Test fun importsCountOncePerCall_andSurviveASheet() = runBlocking {
        repo.recordImport()
        repo.recordImport() // a whole-file import is one call
        repo.recordShown(ProPromptTrigger.COOK_OR_SAVE_MILESTONE)
        assertEquals(2, repo.history().importCount)
    }

    @Test fun showingTheSheetStampsItAndRestartsTheEngagedCount() = runBlocking {
        repeat(3) { repo.recordEngaged() }
        repo.recordShown(ProPromptTrigger.COOK_OR_SAVE_MILESTONE)
        val h = repo.history()
        assertEquals(1_000L, h.lastSheetAt)
        assertEquals(0, h.engagedEvents)
    }

    @Test fun showingTheCardOnlyStampsTheCard() = runBlocking {
        repeat(3) { repo.recordEngaged() }
        repo.recordShown(ProPromptTrigger.RECIPES_MILESTONE)
        val h = repo.history()
        assertEquals(1_000L, h.lastCardAt)
        assertEquals(null, h.lastSheetAt)
        assertEquals(3, h.engagedEvents)
    }

    @Test fun dismissalsAreCountedPerSurface() = runBlocking {
        repo.recordDismissed(ProPromptTrigger.COOK_OR_SAVE_MILESTONE)
        repo.recordDismissed(ProPromptTrigger.COOK_OR_SAVE_MILESTONE)
        time = 5_000L
        repo.recordDismissed(ProPromptTrigger.RECIPES_MILESTONE)
        val h = repo.history()
        assertEquals(2, h.sheetDismissals)
        assertEquals(mapOf("recipes_milestone" to 1), h.cardDismissals)
        assertEquals(mapOf("recipes_milestone" to 5_000L), h.cardDismissedAt)
    }

    @Test fun hardGateIsStamped() = runBlocking {
        time = 42L
        repo.recordHardGate()
        assertEquals(42L, repo.history().lastHardGateAt)
    }
}
