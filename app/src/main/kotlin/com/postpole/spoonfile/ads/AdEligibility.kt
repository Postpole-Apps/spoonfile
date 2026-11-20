package com.postpole.spoonfile.ads

/** Everything that decides whether an ad may be requested or shown right now. */
data class AdInputs(
    val config: AdsConfig,
    /** UMP's `canRequestAds()`. */
    val canRequestAds: Boolean,
    /** `MobileAds.initialize` has completed (it only runs after [canRequestAds]). */
    val sdkReady: Boolean,
    /** Pro **or** `remove_ads` is owned; `null` while the cache is still loading, which counts as "no ads yet". */
    val adsRemoved: Boolean?,
)

/** Pure ad gating. Entitlement, kill switch, UMP and the per-format flag must all agree. */
object AdEligibility {
    private fun base(i: AdInputs) =
        i.config.adsEnabled && i.canRequestAds && i.sdkReady && i.adsRemoved == false

    fun banners(i: AdInputs) = base(i) && i.config.bannerEnabled

    fun interstitials(i: AdInputs) = base(i) && i.config.interstitialEnabled
}
