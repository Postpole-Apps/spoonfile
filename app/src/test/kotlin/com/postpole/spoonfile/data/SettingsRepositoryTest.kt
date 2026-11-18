package com.postpole.spoonfile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private fun openStore(file: File): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    /** Closes the "running app" so the same file can be opened again, like a process restart. */
    private suspend fun restart() {
        scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = runBlocking { restart() }

    @Test
    fun defaultThemeIsSystemWhenNothingIsStored() = runBlocking {
        val repo = SettingsRepository(openStore(File(tmp.root, "prefs.preferences_pb")))
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }

    @Test
    fun savedThemeSurvivesARestart() = runBlocking {
        val file = File(tmp.root, "prefs.preferences_pb")
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            SettingsRepository(openStore(file)).setThemeMode(mode)
            restart()
            assertEquals(mode, SettingsRepository(openStore(file)).themeMode.first())
            restart()
        }
    }
}
