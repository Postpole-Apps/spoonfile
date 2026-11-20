package com.postpole.spoonfile.ads

/** Everything that decides whether an ad may be requested or shown right now. */
data class AdInputs(
    val config: AdsConfig,
    /** UMP's `canRequestAds()`. */
    val canRequestAds: Boolean,
    /** `MobileAds.initialize` has completed (it only runs after [canRequestAds]). */
    val sdkReady: Boolean,
    /** Cached Pro entitlement; `null` while it is still loading, which counts as "no ads yet". */
    val isPro: Boolean?,
    /** The `remove_ads` product (sf-02). Always false until that issue lands. */
    val removeAds: Boolean,
)

/** Pure ad gating. Entitlement, kill switch, UMP and the per-format flag must all agree. */
object AdEligibility {
    private fun base(i: AdInputs) =
        i.config.adsEnabled && i.canRequestAds && i.sdkReady && i.isPro == false && !i.removeAds

    fun banners(i: AdInputs) = base(i) && i.config.bannerEnabled

    fun interstitials(i: AdInputs) = base(i) && i.config.interstitialEnabled
}
