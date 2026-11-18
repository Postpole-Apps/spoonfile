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

/** When to show the monthly "back up your recipes" banner on the Recipes screen. */
object BackupNudge {
    const val MIN_RECIPES = 10
    const val INTERVAL_MS = 30L * 24 * 60 * 60 * 1000

    /**
     * True when reminders are on, there are at least [MIN_RECIPES] recipes, and the last export (zip
     * backup or recipe file) is more than 30 days old or has never happened. A dismissed banner stays
     * hidden for the same 30 days.
     */
    fun shouldShow(
        remindEnabled: Boolean,
        recipeCount: Int,
        lastExportAt: Long?,
        dismissedAt: Long?,
        now: Long,
    ): Boolean {
        if (!remindEnabled || recipeCount < MIN_RECIPES) return false
        if (lastExportAt != null && now - lastExportAt <= INTERVAL_MS) return false
        if (dismissedAt != null && now - dismissedAt <= INTERVAL_MS) return false
        return true
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    /** Time of the last "Back up now" `.zip`, or null if never. */
    val lastBackupAt: Flow<Long?> = dataStore.data.map { it[LAST_BACKUP_AT] }

    /** Time of the last backup or recipe-file export, whichever is newer, or null if never. */
    val lastExportAt: Flow<Long?> = dataStore.data.map { prefs ->
        listOfNotNull(prefs[LAST_BACKUP_AT], prefs[LAST_RECIPE_FILE_AT]).maxOrNull()
    }

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
        val NUDGE_DISMISSED_AT = longPreferencesKey("backup_nudge_dismissed_at")
    }
}
