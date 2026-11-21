package com.postpole.spoonfile.analytics

/** Event and parameter names. Values mirror Firebase's standard names where one exists. */
object AnalyticsEvents {
    const val APP_OPEN = "app_open"
    const val FIRST_LAUNCH = "first_launch"
    const val SCREEN_VIEW = "screen_view"
    const val PRO_SCREEN_VIEW = "pro_screen_view"
    const val PURCHASE_STARTED = "purchase_started"
    const val PURCHASE = "purchase"
    const val PURCHASE_RESTORED = "purchase_restored"
    const val BACKUP_EXPORTED = "backup_exported"
    const val BACKUP_IMPORTED = "backup_imported"

    // --- Remove ads (see docs/ads-analytics.md). Params: PRODUCT_ID, PRICE_MICROS, CURRENCY only. ---

    /** The Remove ads offer was shown to the user. */
    const val REMOVE_ADS_PAYWALL_VIEW = "remove_ads_paywall_view"

    /** The Play purchase sheet for `remove_ads` was launched. */
    const val REMOVE_ADS_PURCHASE_START = "remove_ads_purchase_start"

    /** A fresh `remove_ads` purchase completed. Never logged for Restore / refresh. */
    const val REMOVE_ADS_PURCHASE_SUCCESS = "remove_ads_purchase_success"

    /** Cancelled, errored or could-not-launch purchase flow. Adds [AnalyticsParams.RESULT]. */
    const val REMOVE_ADS_PURCHASE_FAIL = "remove_ads_purchase_fail"

    /** Entry point into the Remove ads offer. Param: [AnalyticsParams.SOURCE] (an [AdPaywallSource] value). */
    const val AD_PAYWALL_ENTRY = "ad_paywall_entry"

    /** Params: [AnalyticsParams.IMPORT_SOURCE] and [AnalyticsParams.RESULT] only. Never a URL, domain or recipe text. */
    const val RECIPE_IMPORT = "recipe_import"

    // --- Meal plan (Pro). Params: ITEM_COUNT only. Never recipe ids / titles, ingredient text or dates. ---

    /** A Pro user opened the meal plan. [AnalyticsParams.ITEM_COUNT] = entries in the visible week. */
    const val MEAL_PLAN_VIEW = "meal_plan_view"

    /** A recipe was added to the plan. */
    const val MEAL_PLAN_ADD = "meal_plan_add"

    /** Plan ingredients were added to the shopping list. [AnalyticsParams.ITEM_COUNT] = lines added. */
    const val MEAL_PLAN_SHOP = "meal_plan_shop"
}

object AnalyticsParams {
    const val SCREEN_NAME = "screen_name"
    const val VALUE = "value"
    const val CURRENCY = "currency"
    const val ITEM_ID = "item_id"
    const val ITEM_COUNT = "item_count"
    const val FORMAT = "format"
    const val IMPORT_SOURCE = "import_source"
    const val RESULT = "result"
    const val PRODUCT_ID = "product_id"
    const val PRICE_MICROS = "price_micros"
    const val SOURCE = "source"
}

/** Values for [AnalyticsParams.SOURCE] on `ad_paywall_entry`. Only the settings row exists today. */
object AdPaywallSource {
    const val SETTINGS_ROW = "settings_row"
    const val INTERSTITIAL_CLOSE = "interstitial_close"
    const val BANNER_OVERFLOW = "banner_overflow"
}

/** Values for [AnalyticsParams.RESULT] on `remove_ads_purchase_fail`. */
object PurchaseFailResult {
    const val CANCELED = "canceled"
    const val ERROR = "error"
    const val LAUNCH_FAILED = "launch_failed"
}

/**
 * App-facing analytics + crash reporting seam. Backed by Firebase when the app has a
 * google-services.json, otherwise [NoOpAnalytics]. Never put personal data in params.
 */
interface Analytics {
    /** Values may be String, Long, Int, Double or Boolean. */
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())

    fun setUserProperty(name: String, value: String?)

    fun logScreenView(screenName: String)

    /** Records a handled exception (Crashlytics non-fatal). */
    fun recordNonFatal(throwable: Throwable)
}

/** Used when Firebase isn't configured: every call is safe and does nothing. */
object NoOpAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}
