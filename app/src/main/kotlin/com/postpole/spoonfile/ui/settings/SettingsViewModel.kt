package com.postpole.spoonfile.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AdPaywallSource
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingProduct
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.BackupRepository
import com.postpole.spoonfile.data.InvalidBackupException
import com.postpole.spoonfile.data.RecipeRepository
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
import javax.inject.Inject

/** One-shot results shown as a snackbar. */
sealed interface SettingsEvent {
    data class Exported(val count: Int) : SettingsEvent
    data object ExportFailed : SettingsEvent
    data class Imported(val count: Int) : SettingsEvent
    data object ImportInvalid : SettingsEvent
    data object ImportFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val recipes: RecipeRepository,
    private val analytics: Analytics,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro
    val removeAdsOwned: StateFlow<Boolean> = billing.removeAdsOwned
    val removeAdsPrice: StateFlow<String?> = billing.removeAdsPrice
    val removeAdsUnavailable: StateFlow<Boolean> = billing.removeAdsUnavailable
    val removeAdsPending: StateFlow<Boolean> = billing.removeAdsPending

    /** The Remove ads row is on screen. The screen calls this once per visit, not per recomposition. */
    fun onRemoveAdsPaywallViewed() {
        analytics.logEvent(AnalyticsEvents.REMOVE_ADS_PAYWALL_VIEW)
    }

    fun buyRemoveAds(activity: android.app.Activity) {
        analytics.logEvent(
            AnalyticsEvents.AD_PAYWALL_ENTRY,
            mapOf(AnalyticsParams.SOURCE to AdPaywallSource.SETTINGS_ROW),
        )
        billing.launchPurchase(activity, BillingProduct.REMOVE_ADS)
    }

    /** Retry after "unavailable" (e.g. the product was not reachable yet). */
    fun retryProducts() {
        viewModelScope.launch { billing.refresh() }
    }

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

    /** "Back up now": writes the `.zip` (data + photos) to [uri] and remembers the date. */
    fun backUpNow(resolver: ContentResolver, uri: Uri) = export(resolver, uri, "zip") {
        val count = backup.exportZip(it)
        settings.setLastBackupAt(System.currentTimeMillis())
        count
    }

    /** "Export recipe file": all recipes with photos as a file another app can open. */
    fun exportRecipeFile(resolver: ContentResolver, uri: Uri) = export(resolver, uri, "recipe_file") { out ->
        val all = recipes.allDetails()
        val count = PaprikaArchiveWriter().write(out, all) { name -> recipes.photoFile(name)?.readBytes() }
        settings.setLastRecipeFileAt(System.currentTimeMillis())
        count
    }

    private fun export(
        resolver: ContentResolver,
        uri: Uri,
        format: String,
        write: suspend (java.io.OutputStream) -> Int,
    ) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri, "wt") ?: error("Could not open $uri for writing")
                    out.use { write(it) }
                }
                analytics.logEvent(
                    AnalyticsEvents.BACKUP_EXPORTED,
                    mapOf(AnalyticsParams.FORMAT to format, AnalyticsParams.ITEM_COUNT to count),
                )
                _events.trySend(SettingsEvent.Exported(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.recordNonFatal(BackupException("Export ($format) failed", e))
                _events.trySend(SettingsEvent.ExportFailed)
            }
        }
    }

    /** Restore is never limited by the free recipe cap. */
    fun restoreBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Could not open $uri for reading")
                    input.use { backup.importZip(it) }
                }
                analytics.logEvent(AnalyticsEvents.BACKUP_IMPORTED, mapOf(AnalyticsParams.ITEM_COUNT to count))
                _events.trySend(SettingsEvent.Imported(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidBackupException) {
                // User picked a wrong/newer file: expected, not a crash-worthy event.
                _events.trySend(SettingsEvent.ImportInvalid)
            } catch (e: Exception) {
                analytics.recordNonFatal(BackupException("Import failed", e))
                _events.trySend(SettingsEvent.ImportFailed)
            }
        }
    }
}

/** Wraps a backup I/O failure so Crashlytics groups them as one non-fatal type. */
class BackupException(message: String, cause: Throwable) : Exception(message, cause)
