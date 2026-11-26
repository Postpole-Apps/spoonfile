package com.postpole.spoonfile.pro

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Source of the current [ProPromptConfig]; [refresh] fetches and activates new values. */
interface ProPromptConfigSource {
    val config: StateFlow<ProPromptConfig>
    fun refresh()
}

/** Always the launch defaults. Used when the app has no Firebase project (bare template / CI / tests). */
class StaticProPromptConfig(initial: ProPromptConfig = ProPromptConfig.DEFAULTS) : ProPromptConfigSource {
    private val state = MutableStateFlow(initial)
    override val config: StateFlow<ProPromptConfig> = state.asStateFlow()
    override fun refresh() = Unit
}

/**
 * Firebase Remote Config backed source. Only values that really came from the server are used; anything else
 * falls back to the launch default. (In-app defaults are not set here: `setDefaultsAsync` replaces the whole
 * defaults map, which would wipe the ads defaults registered on the same instance.)
 */
class RemoteProPromptConfig(private val remote: FirebaseRemoteConfig) : ProPromptConfigSource {
    private val state = MutableStateFlow(read())
    override val config: StateFlow<ProPromptConfig> = state.asStateFlow()

    override fun refresh() {
        // Failures are silent: the last activated (or default) values stay in force.
        remote.fetchAndActivate().addOnCompleteListener { state.value = read() }
    }

    private fun read(): ProPromptConfig {
        val d = ProPromptConfig.DEFAULTS
        return ProPromptConfig.of(
            enabled = remoteValue(ProPromptConfigKeys.ENABLED) { it.asBoolean() } ?: d.enabled,
            minSession = remoteValue(ProPromptConfigKeys.MIN_SESSION) { it.asLong() } ?: d.minSession.toLong(),
            recipeCount = remoteValue(ProPromptConfigKeys.RECIPE_COUNT) { it.asLong() } ?: d.recipeCount.toLong(),
            cookOrImportCount = remoteValue(ProPromptConfigKeys.COOK_OR_IMPORT_COUNT) { it.asLong() }
                ?: d.cookOrImportCount.toLong(),
            capNearCount = remoteValue(ProPromptConfigKeys.CAP_NEAR_COUNT) { it.asLong() } ?: d.capNearCount.toLong(),
            importCount = remoteValue(ProPromptConfigKeys.IMPORT_COUNT) { it.asLong() } ?: d.importCount.toLong(),
        )
    }

    private fun <T> remoteValue(key: String, parse: (com.google.firebase.remoteconfig.FirebaseRemoteConfigValue) -> T): T? =
        runCatching {
            val value = remote.getValue(key)
            if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) parse(value) else null
        }.getOrNull()
}

/** Remote Config when this app was built with a google-services.json, otherwise [StaticProPromptConfig]. */
fun createProPromptConfig(context: Context): ProPromptConfigSource {
    val configured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    if (!configured) return StaticProPromptConfig()
    return RemoteProPromptConfig(FirebaseRemoteConfig.getInstance())
}
