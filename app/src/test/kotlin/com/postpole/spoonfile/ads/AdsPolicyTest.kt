package com.postpole.spoonfile.ads

import com.postpole.spoonfile.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneOffset

class AdsPolicyTest {
    private val zone = ZoneOffset.UTC
    private val config = AdsConfig.DEFAULTS
    private val day = 24L * 60 * 60 * 1000
    private val installed = 1_000_000_000_000L
    private val now = installed + 10 * day

    private fun due(history: InterstitialHistory, at: Long = now, cfg: AdsConfig = config) =
        InterstitialPolicy.isDue(cfg, history, installed, at, zone)

    private val eligible = InterstitialHistory(sessionCount = 5)

    @Test fun launchDefaultsMatchThePlan() {
        val d = AdsConfig.DEFAULTS.toDefaultsMap()
        assertEquals(
            mapOf<String, Any>(
                "ads_enabled" to true, "ads_banner_enabled" to true, "ads_interstitial_enabled" to true,
                "ads_rewarded_enabled" to false, "ads_native_enabled" to false, "ads_app_open_enabled" to false,
                "ads_interstitial_block_first_session" to true, "ads_interstitial_min_install_days" to 3L,
                "ads_interstitial_min_interval_sec" to 480L, "ads_interstitial_max_per_session" to 1L,
                "ads_interstitial_max_per_day" to 3L, "ads_max_content_rating" to "PG", "ads_rewarded_daily_limit" to 1L,
            ),
            d,
        )
    }

    @Test fun firstSessionNeverGetsAnInterstitial() {
        assertFalse(due(InterstitialHistory(sessionCount = 1)))
        assertTrue(due(InterstitialHistory(sessionCount = 2)))
    }

    @Test fun nothingInTheFirstThreeInstallDays() {
        assertFalse(due(eligible, at = installed + 3 * day - 1))
        assertTrue(due(eligible, at = installed + 3 * day))
    }

    @Test fun atLeastEightMinutesApart() {
        val shown = now - 479_000
        assertFalse(due(eligible.copy(lastShownAt = shown, day = "x")))
        assertTrue(due(eligible.copy(lastShownAt = now - 480_000, day = "x")))
    }

    @Test fun aClockThatWentBackwardsDoesNotLetAnAdThrough() {
        assertFalse(due(eligible.copy(lastShownAt = now + day)))
    }

    @Test fun oncePerSession() {
        assertFalse(due(eligible.copy(shownThisSession = 1)))
    }

    @Test fun threePerDayAndTheCountResetsTomorrow() {
        val today = InterstitialPolicy.dayKey(now, zone)
        assertFalse(due(eligible.copy(day = today, shownToday = 3, lastShownAt = now - 3_600_000)))
        assertTrue(due(eligible.copy(day = today, shownToday = 2, lastShownAt = now - 3_600_000)))
        assertTrue(due(eligible.copy(day = "1999-01-01", shownToday = 3, lastShownAt = now - 3_600_000)))
    }

    @Test fun recordingAShowCountsItEverywhere() {
        val after = InterstitialPolicy.recordShown(eligible, now, zone)
        assertEquals(now, after.lastShownAt)
        assertEquals(1, after.shownToday)
        assertEquals(1, after.shownThisSession)
        assertFalse(due(after, at = now + 10 * 60_000))
    }

    @Test fun remoteConfigCapsAreHonoured() {
        val relaxed = config.copy(interstitialMaxPerSession = 2, interstitialMinIntervalSec = 60)
        val h = InterstitialPolicy.recordShown(eligible, now, zone)
        assertTrue(due(h, at = now + 61_000, cfg = relaxed))
    }

    private fun inputs(
        config: AdsConfig = AdsConfig.DEFAULTS,
        canRequestAds: Boolean = true,
        sdkReady: Boolean = true,
        adsRemoved: Boolean? = false,
    ) = AdInputs(config, canRequestAds, sdkReady, adsRemoved)

    @Test fun adsShowWhenEverythingAllows() {
        assertTrue(AdEligibility.banners(inputs()))
        assertTrue(AdEligibility.interstitials(inputs()))
    }

    @Test fun killSwitchHidesEverything() {
        val off = inputs(AdsConfig.DEFAULTS.copy(adsEnabled = false))
        assertFalse(AdEligibility.banners(off))
        assertFalse(AdEligibility.interstitials(off))
    }

    @Test fun formatFlagsAreIndependent() {
        assertFalse(AdEligibility.banners(inputs(AdsConfig.DEFAULTS.copy(bannerEnabled = false))))
        assertTrue(AdEligibility.interstitials(inputs(AdsConfig.DEFAULTS.copy(bannerEnabled = false))))
        assertFalse(AdEligibility.interstitials(inputs(AdsConfig.DEFAULTS.copy(interstitialEnabled = false))))
    }

    @Test fun noAdsWithoutAUmpResultOrBeforeSdkInit() {
        assertFalse(AdEligibility.banners(inputs(canRequestAds = false)))
        assertFalse(AdEligibility.banners(inputs(sdkReady = false)))
    }

    @Test fun proAndRemoveAdsBuyersSeeNoAds_andUnknownCountsAsNo() {
        assertFalse(AdEligibility.banners(inputs(adsRemoved = true)))
        assertFalse(AdEligibility.interstitials(inputs(adsRemoved = true)))
        assertFalse(AdEligibility.banners(inputs(adsRemoved = null)))
    }

    @Test fun appOpenRewardedAndNativeNeverEnableAnything() {
        val flags = AdsConfig.DEFAULTS.copy(rewardedEnabled = true, nativeEnabled = true, appOpenEnabled = true)
        assertEquals(AdEligibility.banners(inputs()), AdEligibility.banners(inputs(flags)))
    }

    @Test fun adUnitsForThisBuildAreTheOfficialDemoUnits() {
        // Unit tests run the debug variant: demo units only, never production.
        val demo = "ca-app-pub-3940256099942544/9214589741"
        assertEquals(demo, BuildConfig.AD_UNIT_BANNER_RECIPES)
        assertEquals(demo, BuildConfig.AD_UNIT_BANNER_SHOPPING)
        assertEquals(demo, BuildConfig.AD_UNIT_BANNER_CATEGORIES)
        assertEquals("ca-app-pub-3940256099942544/1033173712", BuildConfig.AD_UNIT_INTERSTITIAL_AFTER_RECIPE)
        assertEquals("ca-app-pub-3940256099942544/5224354917", BuildConfig.AD_UNIT_REWARDED)
    }

    @Test fun releaseUnitsAndManifestAreExactlyAsSpecified() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        listOf(
            "ca-app-pub-2007361247232604/2557681771", "ca-app-pub-2007361247232604/8931518430",
            "ca-app-pub-2007361247232604/3735032070", "ca-app-pub-2007361247232604/1526516180",
        ).forEach { assertTrue("$it missing from the release units", gradle.contains(it)) }
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }.readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "") // the explanatory comment mentions it
        assertTrue(manifest.contains("ca-app-pub-2007361247232604~8774450811"))
        assertTrue(manifest.contains("com.google.android.gms.permission.AD_ID"))
        assertFalse(manifest.contains("tools:node=\"remove\""))
    }
}
