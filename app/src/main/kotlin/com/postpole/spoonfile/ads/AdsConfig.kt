package com.postpole.spoonfile.ads

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remote Config keys shared across Postpole apps (ads plan §1.1). */
object AdsConfigKeys {
    const val ADS_ENABLED = "ads_enabled"
    const val BANNER_ENABLED = "ads_banner_enabled"
    const val INTERSTITIAL_ENABLED = "ads_interstitial_enabled"
    const val REWARDED_ENABLED = "ads_rewarded_enabled"
    const val NATIVE_ENABLED = "ads_native_enabled"
    const val APP_OPEN_ENABLED = "ads_app_open_enabled"
    const val INTERSTITIAL_BLOCK_FIRST_SESSION = "ads_interstitial_block_first_session"
    const val INTERSTITIAL_MIN_INSTALL_DAYS = "ads_interstitial_min_install_days"
    const val INTERSTITIAL_MIN_INTERVAL_SEC = "ads_interstitial_min_interval_sec"
    const val INTERSTITIAL_MAX_PER_SESSION = "ads_interstitial_max_per_session"
    const val INTERSTITIAL_MAX_PER_DAY = "ads_interstitial_max_per_day"
    const val MAX_CONTENT_RATING = "ads_max_content_rating"
    const val REWARDED_DAILY_LIMIT = "ads_rewarded_daily_limit"
}

/**
 * The Remote Config values that drive ads, with the launch defaults. [rewardedEnabled], [nativeEnabled] and
 * [appOpenEnabled] are carried for completeness but nothing in this app reads them: there is no rewarded,
 * native or app-open ad path.
 */
data class AdsConfig(
    val adsEnabled: Boolean = true,
    val bannerEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val rewardedEnabled: Boolean = false,
    val nativeEnabled: Boolean = false,
    val appOpenEnabled: Boolean = false,
    val interstitialBlockFirstSession: Boolean = true,
    val interstitialMinInstallDays: Int = 3,
    val interstitialMinIntervalSec: Int = 480,
    val interstitialMaxPerSession: Int = 1,
    val interstitialMaxPerDay: Int = 3,
    val maxContentRating: String = "PG",
    val rewardedDailyLimit: Int = 1,
) {
    companion object {
        val DEFAULTS = AdsConfig()
    }

    fun toDefaultsMap(): Map<String, Any> = mapOf(
        AdsConfigKeys.ADS_ENABLED to adsEnabled,
        AdsConfigKeys.BANNER_ENABLED to bannerEnabled,
        AdsConfigKeys.INTERSTITIAL_ENABLED to interstitialEnabled,
        AdsConfigKeys.REWARDED_ENABLED to rewardedEnabled,
        AdsConfigKeys.NATIVE_ENABLED to nativeEnabled,
        AdsConfigKeys.APP_OPEN_ENABLED to appOpenEnabled,
        AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION to interstitialBlockFirstSession,
        AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS to interstitialMinInstallDays.toLong(),
        AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to interstitialMinIntervalSec.toLong(),
        AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION to interstitialMaxPerSession.toLong(),
        AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY to interstitialMaxPerDay.toLong(),
        AdsConfigKeys.MAX_CONTENT_RATING to maxContentRating,
        AdsConfigKeys.REWARDED_DAILY_LIMIT to rewardedDailyLimit.toLong(),
    )
}

/** Source of the current [AdsConfig]; [refresh] fetches and activates new values. */
interface AdsConfigSource {
    val config: StateFlow<AdsConfig>
    fun refresh()
}

/** Always the launch defaults. Used when the app has no Firebase project (bare template / CI / tests). */
class StaticAdsConfig(initial: AdsConfig = AdsConfig.DEFAULTS) : AdsConfigSource {
    private val state = MutableStateFlow(initial)
    override val config: StateFlow<AdsConfig> = state.asStateFlow()
    override fun refresh() = Unit
}

/** Firebase Remote Config backed [AdsConfigSource]. In-app defaults are the launch defaults. */
class RemoteAdsConfig(private val remote: FirebaseRemoteConfig) : AdsConfigSource {
    private val state = MutableStateFlow(read())
    override val config: StateFlow<AdsConfig> = state.asStateFlow()

    init {
        remote.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = FETCH_INTERVAL_SEC })
        remote.setDefaultsAsync(AdsConfig.DEFAULTS.toDefaultsMap()).addOnCompleteListener { state.value = read() }
    }

    override fun refresh() {
        // Failures are silent: the last activated (or default) values stay in force.
        remote.fetchAndActivate().addOnCompleteListener { state.value = read() }
    }

    private fun read(): AdsConfig {
        val d = AdsConfig.DEFAULTS
        return AdsConfig(
            adsEnabled = remote.getBoolean(AdsConfigKeys.ADS_ENABLED),
            bannerEnabled = remote.getBoolean(AdsConfigKeys.BANNER_ENABLED),
            interstitialEnabled = remote.getBoolean(AdsConfigKeys.INTERSTITIAL_ENABLED),
            rewardedEnabled = remote.getBoolean(AdsConfigKeys.REWARDED_ENABLED),
            nativeEnabled = remote.getBoolean(AdsConfigKeys.NATIVE_ENABLED),
            appOpenEnabled = remote.getBoolean(AdsConfigKeys.APP_OPEN_ENABLED),
            interstitialBlockFirstSession = remote.getBoolean(AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION),
            interstitialMinInstallDays = remote.getLong(AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS).toInt(),
            interstitialMinIntervalSec = remote.getLong(AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC).toInt(),
            interstitialMaxPerSession = remote.getLong(AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION).toInt(),
            interstitialMaxPerDay = remote.getLong(AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY).toInt(),
            maxContentRating = remote.getString(AdsConfigKeys.MAX_CONTENT_RATING).ifBlank { d.maxContentRating },
            rewardedDailyLimit = remote.getLong(AdsConfigKeys.REWARDED_DAILY_LIMIT).toInt(),
        )
    }

    private companion object {
        const val FETCH_INTERVAL_SEC = 3_600L
    }
}

/** Remote Config when this app was built with a google-services.json, otherwise [StaticAdsConfig]. */
fun createAdsConfig(context: Context): AdsConfigSource {
    val configured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    if (!configured) return StaticAdsConfig()
    return RemoteAdsConfig(FirebaseRemoteConfig.getInstance())
}
