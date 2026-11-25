package com.postpole.spoonfile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.data.SettingsRepository
import com.postpole.spoonfile.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ThemeState(val mode: ThemeMode = ThemeMode.SYSTEM)

/** Activity-level state: theme for [com.postpole.spoonfile.ui.theme.SpoonfileTheme] and screen-view logging. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val analytics: Analytics,
) : ViewModel() {

    val theme: StateFlow<ThemeState> = settings.themeMode.map(::ThemeState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeState())

    /** [trigger] is the Pro screen's `trigger` argument (e.g. `auto_recipes_milestone`); empty or null when none. */
    fun onScreen(route: String, trigger: String? = null) {
        analytics.logScreenView(route)
        if (route == Routes.PRO) {
            val params = trigger?.takeIf { it.isNotEmpty() }?.let { mapOf<String, Any>(AnalyticsParams.TRIGGER to it) }
            analytics.logEvent(AnalyticsEvents.PRO_SCREEN_VIEW, params.orEmpty())
        }
    }
}
