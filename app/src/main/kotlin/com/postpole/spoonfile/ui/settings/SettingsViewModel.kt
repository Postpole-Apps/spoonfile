package com.postpole.spoonfile.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.BackupDao
import com.postpole.spoonfile.data.BackupRepository
import com.postpole.spoonfile.data.ImageStore
import com.postpole.spoonfile.data.InvalidBackupException
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.SettingsRepository
import com.postpole.spoonfile.data.ThemeMode
import com.postpole.spoonfile.exporter.PaprikaArchiveWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** One-shot results shown as a snackbar. */
sealed interface SettingsEvent {
    data class BackedUp(val count: Int) : SettingsEvent
    data object BackupFailed : SettingsEvent
    data class RecipeFileSaved(val count: Int) : SettingsEvent
    data object RecipeFileFailed : SettingsEvent
    data class Restored(val count: Int) : SettingsEvent
    data object RestoreInvalid : SettingsEvent
    data object RestoreFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val backupDao: BackupDao,
    private val images: ImageStore,
    private val analytics: Analytics,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val lastBackupAt: StateFlow<Long?> = settings.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val remindBackup: StateFlow<Boolean> = settings.remindBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setRemindBackup(enabled: Boolean) {
        viewModelScope.launch { settings.setRemindBackup(enabled) }
    }

    /** Back up now: a `.zip` with all data and photos, written to the file the user picked. */
    fun backUpNow(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri, "wt") ?: error("Could not open $uri for writing")
                    out.use { backup.exportZip(it) }
                }
                settings.setLastBackupAt(System.currentTimeMillis())
                analytics.logEvent(
                    AnalyticsEvents.BACKUP_EXPORTED,
                    mapOf(AnalyticsParams.FORMAT to "zip", AnalyticsParams.ITEM_COUNT to count),
                )
                _events.trySend(SettingsEvent.BackedUp(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.recordNonFatal(BackupException("Backup failed", e))
                _events.trySend(SettingsEvent.BackupFailed)
            }
        }
    }

    /** Export every recipe as a recipe file another app can import. */
    fun exportRecipeFile(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val recipes = loadAllRecipes()
                    val out = resolver.openOutputStream(uri, "wt") ?: error("Could not open $uri for writing")
                    out.use {
                        PaprikaArchiveWriter().write(it, recipes.asSequence()) { d ->
                            d.recipe.imageFile?.let { name -> File(images.dir, File(name).name).takeIf(File::isFile)?.readBytes() }
                        }
                    }
                }
                settings.setLastRecipeFileAt(System.currentTimeMillis())
                analytics.logEvent(
                    AnalyticsEvents.BACKUP_EXPORTED,
                    mapOf(AnalyticsParams.FORMAT to "recipe_file", AnalyticsParams.ITEM_COUNT to count),
                )
                _events.trySend(SettingsEvent.RecipeFileSaved(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.recordNonFatal(BackupException("Recipe file export failed", e))
                _events.trySend(SettingsEvent.RecipeFileFailed)
            }
        }
    }

    private suspend fun loadAllRecipes(): List<RecipeWithDetails> {
        val ingredients = backupDao.ingredients().groupBy { it.recipeId }
        val steps = backupDao.steps().groupBy { it.recipeId }
        val categories = backupDao.categories().associateBy { it.id }
        val links = backupDao.recipeCategories().groupBy { it.recipeId }
        return backupDao.recipes().map { r ->
            RecipeWithDetails(
                recipe = r,
                ingredients = ingredients[r.id].orEmpty(),
                steps = steps[r.id].orEmpty(),
                categories = links[r.id].orEmpty().mapNotNull { categories[it.categoryId] },
            ).ordered()
        }
    }

    /** Restore from a backup `.zip`. Never limited by the free recipe cap. */
    fun restoreBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Could not open $uri for reading")
                    input.use { backup.importZip(it) }
                }
                analytics.logEvent(AnalyticsEvents.BACKUP_IMPORTED, mapOf(AnalyticsParams.ITEM_COUNT to count))
                _events.trySend(SettingsEvent.Restored(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidBackupException) {
                // User picked a wrong/newer file: expected, not a crash-worthy event.
                _events.trySend(SettingsEvent.RestoreInvalid)
            } catch (e: Exception) {
                analytics.recordNonFatal(BackupException("Restore failed", e))
                _events.trySend(SettingsEvent.RestoreFailed)
            }
        }
    }
}

/** Wraps a backup I/O failure so Crashlytics groups them as one non-fatal type. */
class BackupException(message: String, cause: Throwable) : Exception(message, cause)
