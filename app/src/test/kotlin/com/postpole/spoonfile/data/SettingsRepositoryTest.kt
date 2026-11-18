package com.postpole.spoonfile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Opens the DataStore file the way a fresh process would: new scope, new instance, same file. */
    private fun open(scope: CoroutineScope) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") })

    @Test
    fun defaultThemeIsSystemWhenNothingIsStored() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        assertEquals(ThemeMode.SYSTEM, open(scope).themeMode.first())
        scope.cancel()
    }

    @Test
    fun savedThemeSurvivesARestart() = runBlocking {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)) {
            val before = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            open(before).setThemeMode(mode)
            before.cancel() // "process death": the first DataStore instance is gone

            val after = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            assertEquals(mode, open(after).themeMode.first())
            after.cancel()
        }
    }
}
