package com.postpole.spoonfile.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.postpole.spoonfile.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether UMP says ads may be requested. Nothing may request an ad while this is false. */
interface AdConsent {
    val canRequestAds: StateFlow<Boolean>
}

/** The Settings "Privacy options" row is shown only when UMP reports REQUIRED (UNKNOWN and NOT_REQUIRED hide it). */
internal fun isPrivacyOptionsRequired(status: ConsentInformation.PrivacyOptionsRequirementStatus): Boolean =
    status == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

/**
 * User Messaging Platform wrapper. [gather] runs on every launch: it refreshes the consent state and shows the
 * form when the user's region requires one. [canRequestAds] only turns true from UMP's own answer
 * (`ConsentInformation.canRequestAds()`), so in the EEA / UK / Switzerland nothing is requested without a result.
 * The child-directed tag is never set.
 */
@Singleton
class ConsentManager @Inject constructor() : AdConsent {
    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** True when the region requires a privacy options entry point (Settings shows it only then). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var gathering = false

    fun gather(activity: Activity) {
        if (gathering) return
        gathering = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        // A previous session's result may already allow ads; the refresh below can still revoke it.
        publish(info)
        info.requestConsentInfoUpdate(
            activity,
            requestParameters(activity),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.errorCode} ${error.message}")
                    gathering = false
                    publish(info)
                }
            },
            { error ->
                // Offline or UMP unavailable: keep whatever UMP last stored (false if it never had a result).
                Log.w(TAG, "Consent info update: ${error.errorCode} ${error.message}")
                gathering = false
                publish(info)
            },
        )
    }

    /** Re-opens the privacy options form (Settings → Privacy options). */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options form: ${error.errorCode} ${error.message}")
            publish(UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun publish(info: ConsentInformation) {
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value = isPrivacyOptionsRequired(info.privacyOptionsRequirementStatus)
    }

    private fun requestParameters(context: Context): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        // Debug builds only: -PumpDebugGeography=EEA|NOT_EEA|REGULATED_US_STATE plus -PumpTestDeviceHash=<hash>.
        if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_HASH.isNotBlank()) {
            val geography = when (BuildConfig.UMP_DEBUG_GEOGRAPHY) {
                "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                "NOT_EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA
                "REGULATED_US_STATE" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
                else -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
            }
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(context)
                    .setDebugGeography(geography)
                    .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_HASH)
                    .build(),
            )
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "ConsentManager"
    }
}
