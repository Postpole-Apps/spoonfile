package com.postpole.spoonfile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Monthly nudge to save a backup: shown on the Recipes screen, never as a notification. */
object BackupNudge {
    const val INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
    const val MIN_RECIPES = 10

    /**
     * [lastExportAt] is the most recent backup or recipe-file export (null if never). The banner shows when that
     * is more than 30 days ago (or never), the user has at least 10 recipes, reminders are on, and it was not
     * dismissed within the last 30 days.
     */
    fun shouldShow(
        now: Long,
        lastExportAt: Long?,
        recipeCount: Int,
        remindEnabled: Boolean,
        dismissedAt: Long?,
    ): Boolean =
        remindEnabled &&
            recipeCount >= MIN_RECIPES &&
            (lastExportAt == null || now - lastExportAt > INTERVAL_MS) &&
            (dismissedAt == null || now - dismissedAt > INTERVAL_MS)
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    /** When the user last saved a backup file (epoch ms), or null. */
    val lastBackupAt: Flow<Long?> = dataStore.data.map { it[LAST_BACKUP_AT] }

    /** The most recent backup file or recipe-file export, or null if there has been none. */
    val lastExportAt: Flow<Long?> = dataStore.data.map { prefs ->
        listOfNotNull(prefs[LAST_BACKUP_AT], prefs[LAST_RECIPE_FILE_AT]).maxOrNull()
    }

    /** "Remind me to back up"; on by default. */
    val remindBackup: Flow<Boolean> = dataStore.data.map { it[REMIND_BACKUP] ?: true }

    val nudgeDismissedAt: Flow<Long?> = dataStore.data.map { it[NUDGE_DISMISSED_AT] }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setLastBackupAt(time: Long) {
        dataStore.edit { it[LAST_BACKUP_AT] = time }
    }

    suspend fun setLastRecipeFileAt(time: Long) {
        dataStore.edit { it[LAST_RECIPE_FILE_AT] = time }
    }

    suspend fun setRemindBackup(enabled: Boolean) {
        dataStore.edit { it[REMIND_BACKUP] = enabled }
    }

    suspend fun dismissNudge(time: Long) {
        dataStore.edit { it[NUDGE_DISMISSED_AT] = time }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_RECIPE_FILE_AT = longPreferencesKey("last_recipe_file_at")
        val REMIND_BACKUP = booleanPreferencesKey("remind_backup")
        val NUDGE_DISMISSED_AT = longPreferencesKey("nudge_dismissed_at")
    }
}
