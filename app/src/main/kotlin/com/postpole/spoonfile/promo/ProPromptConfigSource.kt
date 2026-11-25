package com.postpole.spoonfile.promo

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

/** Current [ProPromptConfig]. Values are fetched by the shared Remote Config refresh in `AdsController`. */
interface ProPromptConfigSource {
    fun current(): ProPromptConfig
}

/** Launch defaults. Used when the app has no Firebase project (bare template / CI / tests). */
class StaticProPromptConfig(private val config: ProPromptConfig = ProPromptConfig()) : ProPromptConfigSource {
    override fun current() = config
}

/**
 * Reads the Firebase Remote Config singleton. A key with no fetched value falls back to the default here
 * (rather than via `setDefaultsAsync`, which would replace the ads defaults set on the same instance).
 */
class RemoteProPromptConfig(private val remote: FirebaseRemoteConfig) : ProPromptConfigSource {
    override fun current(): ProPromptConfig {
        val d = ProPromptConfig()
        return ProPromptConfig(
            enabled = bool(ProPromptConfigKeys.ENABLED, d.enabled),
            minSession = int(ProPromptConfigKeys.MIN_SESSION, d.minSession),
            recipeCount = int(ProPromptConfigKeys.RECIPE_COUNT, d.recipeCount),
            cookOrImportCount = int(ProPromptConfigKeys.COOK_OR_IMPORT_COUNT, d.cookOrImportCount),
        ).sanitized()
    }

    private fun bool(key: String, default: Boolean): Boolean {
        val v = remote.getValue(key)
        return if (v.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) default else v.asBoolean()
    }

    private fun int(key: String, default: Int): Int {
        val v = remote.getValue(key)
        return if (v.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) default else v.asLong().toInt()
    }
}

/** Remote Config when this app was built with a google-services.json, otherwise [StaticProPromptConfig]. */
fun createProPromptConfig(context: Context): ProPromptConfigSource {
    val configured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    if (!configured) return StaticProPromptConfig()
    return RemoteProPromptConfig(FirebaseRemoteConfig.getInstance())
}
